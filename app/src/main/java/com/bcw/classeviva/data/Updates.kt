package com.bcw.classeviva.data

import android.content.Context
import android.content.SharedPreferences
import com.bcw.classeviva.BuildConfig
import com.bcw.classeviva.util.Dates
import com.bcw.classeviva.util.bool
import com.bcw.classeviva.util.int
import com.bcw.classeviva.util.list
import com.bcw.classeviva.util.parseJson
import com.bcw.classeviva.util.str
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import okhttp3.Request
import java.io.File
import java.time.LocalDateTime

/** Numeri della versione ("v3.3.1", "3.3.1-debug", "4.0" → [3, 3, 1], [3, 3, 1], [4, 0]). */
fun versionParts(text: String): List<Int> {
    val core = text.trim().trimStart { !it.isDigit() }.substringBefore('-')
    return core.split('.').map { part -> part.takeWhile { it.isDigit() }.toIntOrNull() ?: 0 }
}

/** Confronto tra versioni: negativo se `a` è più vecchia di `b`. Le parti mancanti valgono 0. */
fun compareVersions(a: String, b: String): Int {
    val x = versionParts(a)
    val y = versionParts(b)
    for (i in 0 until maxOf(x.size, y.size)) {
        val diff = x.getOrElse(i) { 0 }.compareTo(y.getOrElse(i) { 0 })
        if (diff != 0) return diff
    }
    return 0
}

data class ReleaseAsset(val name: String, val url: String, val size: Long, val sha256: String?)

/** Una release di BCW per Android su GitHub: le note sono il suo `body` in Markdown. */
data class ReleaseInfo(
    val tag: String,
    val notes: String,
    val url: String,
    val publishedAt: LocalDateTime?,
    val draft: Boolean,
    val prerelease: Boolean,
    val assets: List<ReleaseAsset>,
) {
    val version: String get() = versionParts(tag).joinToString(".")
    val apk: ReleaseAsset? get() = assets.firstOrNull { it.name.endsWith(".apk", ignoreCase = true) }
}

fun parseRelease(c: JsonObject): ReleaseInfo? {
    val tag = c.str("tag_name") ?: return null
    return ReleaseInfo(
        tag = tag,
        notes = c.str("body") ?: "",
        url = c.str("html_url") ?: "https://github.com/kubos5/BCW-android/releases",
        publishedAt = Dates.parseDateTime(c.str("published_at")),
        draft = c.bool("draft"),
        prerelease = c.bool("prerelease"),
        assets = c.list("assets") { asset ->
            val name = asset.str("name") ?: return@list null
            val url = asset.str("browser_download_url") ?: return@list null
            val digest = asset.str("digest")?.takeIf { it.startsWith("sha256:") }?.removePrefix("sha256:")
            ReleaseAsset(name, url, asset.int("size") ?: 0L, digest)
        },
    )
}

/** Le release pubblicate (né bozze né prerelease), dalla più recente. */
fun List<ReleaseInfo>.published(): List<ReleaseInfo> =
    filter { !it.draft && !it.prerelease }.sortedWith { a, b -> compareVersions(b.version, a.version) }

object GitHubReleases {
    const val ENDPOINT = "https://api.github.com/repos/kubos5/BCW-android/releases?per_page=20"

    /**
     * Nelle build di prova (`assembleDebug`) un file `update-endpoint.txt` nei dati dell'app può
     * sostituire l'indirizzo: serve per provare l'aggiornamento senza pubblicare una release.
     */
    fun endpoint(context: Context): String {
        if (!BuildConfig.DEBUG) return ENDPOINT
        return File(context.filesDir, "update-endpoint.txt").takeIf { it.exists() }?.readText()?.trim()?.ifEmpty { null } ?: ENDPOINT
    }

    /** Elenco delle release, o `null` se GitHub non risponde. */
    suspend fun list(url: String): List<ReleaseInfo>? = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder().url(url)
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "BCW-Android")
                .build()
            Http.client.newCall(request).execute().use { response ->
                if (response.code != 200) return@use null
                val array = parseJson(response.body?.string().orEmpty()) as? JsonArray ?: return@use null
                array.mapNotNull { (it as? JsonObject)?.let(::parseRelease) }
            }
        }.getOrNull()
    }
}

/** Ricordi dell'aggiornamento: versione dell'ultima apertura, note in attesa, controlli. */
class UpdateStore(private val prefs: SharedPreferences) {
    var lastRunVersion: String?
        get() = prefs.getString("lastRunVersion", null)
        set(value) = prefs.edit().putString("lastRunVersion", value).apply()

    /** Versione appena installata di cui mostrare le note al prossimo avvio. */
    var pendingNotesVersion: String?
        get() = prefs.getString("pendingNotesVersion", null)
        set(value) = prefs.edit().putString("pendingNotesVersion", value).apply()
}

/** Note di rilascio dopo un aggiornamento e in Impostazioni › Generale, dalle release di GitHub. */
class UpdateCenter(private val context: Context) {
    private val store = UpdateStore(context.getSharedPreferences("bcw.updates", Context.MODE_PRIVATE))
    val installed: String get() = BuildConfig.VERSION_NAME

    /** All'apertura: se la versione è cambiata, le note della nuova vanno mostrate (mai alla prima apertura). */
    fun onLaunch(firstRun: Boolean) {
        val last = store.lastRunVersion
        store.lastRunVersion = installed
        if (firstRun) return
        if (last == null || compareVersions(last, installed) < 0) store.pendingNotesVersion = installed
    }

    /** Le note da mostrare dopo l'aggiornamento, se ci sono; una sola volta. */
    suspend fun pendingNotes(): ReleaseInfo? {
        val pending = store.pendingNotesVersion ?: return null
        if (compareVersions(pending, installed) != 0) {
            store.pendingNotesVersion = null
            return null
        }
        val releases = GitHubReleases.list(GitHubReleases.endpoint(context)) ?: return null // si riprova alla prossima apertura
        store.pendingNotesVersion = null
        return releases.releaseFor(installed)
    }

    /** "Note di rilascio" in Impostazioni: quelle della versione installata o, se manca, dell'ultima. */
    suspend fun latestNotes(): Result<ReleaseInfo?> {
        val releases = GitHubReleases.list(GitHubReleases.endpoint(context))
            ?: return Result.failure(IllegalStateException("GitHub non risponde"))
        return Result.success(releases.releaseFor(installed))
    }

    private fun List<ReleaseInfo>.releaseFor(version: String): ReleaseInfo? {
        val list = published()
        return list.firstOrNull { compareVersions(it.version, version) == 0 }
            ?: list.firstOrNull()?.takeIf { compareVersions(it.version, version) >= 0 }
    }
}
