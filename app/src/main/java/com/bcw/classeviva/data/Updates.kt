package com.bcw.classeviva.data

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
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
import java.security.MessageDigest
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

/**
 * Ricordi dell'aggiornamento: versione dell'ultima apertura, note in attesa, controlli e rinvii.
 */
class UpdateStore(private val prefs: SharedPreferences) {
    var lastRunVersion: String?
        get() = prefs.getString("lastRunVersion", null)
        set(value) = prefs.edit().putString("lastRunVersion", value).apply()

    /** Versione appena installata di cui mostrare le note al prossimo avvio. */
    var pendingNotesVersion: String?
        get() = prefs.getString("pendingNotesVersion", null)
        set(value) = prefs.edit().putString("pendingNotesVersion", value).apply()

    /** Ultimo controllo della versione più recente (millisecondi da Epoch). */
    var lastCheckAt: Long
        get() = prefs.getLong("updateCheckedAt", 0L)
        set(value) = prefs.edit().putLong("updateCheckedAt", value).apply()

    /** Versione rimandata con "Più tardi" e fino a quando non si ripropone (millisecondi da Epoch). */
    var snoozedVersion: String?
        get() = prefs.getString("updateSnoozedVersion", null)
        set(value) = prefs.edit().putString("updateSnoozedVersion", value).apply()

    var snoozedUntil: Long
        get() = prefs.getLong("updateSnoozedUntil", 0L)
        set(value) = prefs.edit().putLong("updateSnoozedUntil", value).apply()
}

/**
 * Aggiornamento dell'app dentro l'app, dalle release di GitHub di kubos5/BCW-android:
 * - ogni 12 ore, all'apertura, un foglio avvisa di una versione più recente, con le note;
 * - "Aggiorna" scarica l'APK e passa all'installer di sistema, che chiede conferma all'utente;
 *   il file temporaneo va cancellato subito dopo;
 * - "Più tardi" non ripropone la stessa versione per 3 giorni;
 * - "Non avvisarmi più" spegne gli avvisi (Impostazioni › Generale).
 * Il controllo usa solo `api.github.com` e non porta dati dell'utente.
 */
class UpdateCenter(private val context: Context) {
    companion object {
        /** Ogni quanto si controlla la versione più recente. */
        const val CHECK_INTERVAL_MS: Long = 12 * 60 * 60 * 1000L

        /** "Più tardi": la stessa versione non si ripropone per 3 giorni. */
        const val SNOOZE_MS: Long = 3 * 24 * 60 * 60 * 1000L

        /** Un controllo è dovuto se non ne è passato l'intervallo. */
        fun checkDue(lastCheckAt: Long, intervalMs: Long, now: Long): Boolean = lastCheckAt <= 0 || now - lastCheckAt >= intervalMs

        /** La versione rimandata non si ripropone finché non passa il tempo rimesso. */
        fun isSnoozed(tag: String?, snoozedVersion: String?, snoozedUntil: Long, now: Long): Boolean =
            tag != null && tag == snoozedVersion && snoozedUntil > now
    }

    private val store = UpdateStore(context.getSharedPreferences("bcw.updates", Context.MODE_PRIVATE))
    val installed: String get() = BuildConfig.VERSION_NAME

    /** All'apertura: se la versione è cambiata, le note della nuova vanno mostrate (mai alla prima apertura). */
    fun onLaunch(firstRun: Boolean) {
        val last = store.lastRunVersion
        store.lastRunVersion = installed
        if (firstRun) return
        if (last == null || compareVersions(last, installed) < 0) store.pendingNotesVersion = installed
        cleanUp()
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

    /** "Cerca aggiornamenti": l'ultima versione più recente dell'installata, se c'è. */
    suspend fun latestUpdate(): ReleaseInfo? {
        val releases = GitHubReleases.list(GitHubReleases.endpoint(context)) ?: return null
        store.lastCheckAt = System.currentTimeMillis()
        val newest = releases.published().firstOrNull() ?: return null
        return newest.takeIf { compareVersions(it.version, installed) > 0 }
    }

    /** Il controllo automatico all'avvio: rispetta l'intervallo e il "Più tardi". */
    suspend fun automaticCheck(alertsOn: Boolean, now: Long = System.currentTimeMillis()): ReleaseInfo? {
        if (!alertsOn) return null
        if (!checkDue(store.lastCheckAt, CHECK_INTERVAL_MS, now)) return null
        val release = latestUpdate() ?: return null
        if (isSnoozed(release.tag, store.snoozedVersion, store.snoozedUntil, now)) return null
        return release
    }

    /** "Più tardi" nel foglio della versione nuova. */
    fun snooze(release: ReleaseInfo, now: Long = System.currentTimeMillis()) {
        store.snoozedVersion = release.tag
        store.snoozedUntil = now + SNOOZE_MS
    }

    private fun List<ReleaseInfo>.releaseFor(version: String): ReleaseInfo? {
        val list = published()
        return list.firstOrNull { compareVersions(it.version, version) == 0 }
            ?: list.firstOrNull()?.takeIf { compareVersions(it.version, version) >= 0 }
    }

    /** Gli APK della cache di un aggiornamento mai terminato (all'avvio il telefono è calmo). */
    private fun cleanUp() {
        runCatching {
            File(context.cacheDir, "updates").takeIf { it.isDirectory }?.listFiles()?.forEach { it.delete() }
        }
    }
}

/**
 * Scarica l'APK di una release nella cache, verifica l'impronta se GitHub la dà e passa all'installer
 * di sistema, che chiede la conferma all'utente. Il file temporaneo va cancellato subito dopo la
 * consegna al PackageInstaller (che ne ha già letto il contenuto). Serve il permesso "installa app
 * sconosciute": se manca, `requestInstallPermission` apre la schermata di sistema per attivarlo.
 */
class UpdateInstaller(private val context: Context) {
    private val dir: File get() = File(context.cacheDir, "updates").apply { mkdirs() }

    data class Prepared(val file: File)

    /** Il telefono può installare aggiornamenti provenienti da BCW? */
    fun canInstall(): Boolean = context.packageManager.canRequestPackageInstalls()

    /** Apre la schermata di sistema che permette a BCW di installare app. */
    fun requestInstallPermission() {
        val intent = Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }

    /** Scarica l'APK con il suo progresso (0-1) e verifica l'impronta SHA-256, se l'asset la dà. */
    suspend fun download(asset: ReleaseAsset, onProgress: (Float) -> Unit): Result<Prepared> = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder().url(asset.url).header("User-Agent", "BCW-Android").build()
            Http.client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) error("Il server ha risposto ${response.code}")
                val total = response.body!!.contentLength()
                val digest = MessageDigest.getInstance("SHA-256")
                val target = File(dir, asset.name.ifEmpty { "BCW-update.apk" })
                response.body!!.byteStream().use { input ->
                    target.outputStream().use { output ->
                        val buffer = ByteArray(16 * 1024)
                        var read = 0L
                        while (true) {
                            val n = input.read(buffer)
                            if (n < 0) break
                            output.write(buffer, 0, n)
                            digest.update(buffer, 0, n)
                            read += n
                            if (total > 0) onProgress((read.toFloat() / total).coerceIn(0f, 1f))
                        }
                    }
                }
                if (asset.sha256 != null) {
                    val actual = digest.digest().joinToString("") { "%02x".format(it) }
                    if (!actual.equals(asset.sha256, ignoreCase = true)) {
                        target.delete()
                        error("L'APK scaricato non è quello pubblicato (l'impronta non coincide).")
                    }
                }
                Prepared(target)
            }
        }
    }

    /** Passa l'APK all'installer di sistema e cancella il file temporaneo. `message` è l'eventuale motivo di un fallimento. */
    fun install(file: File, onComplete: (String?) -> Unit) {
        try {
            val installer = context.packageManager.packageInstaller
            val params = android.content.pm.PackageInstaller.SessionParams(android.content.pm.PackageInstaller.SessionParams.MODE_FULL_INSTALL)
            val sessionId = installer.createSession(params)
            val session = installer.openSession(sessionId)
            session.use {
                file.inputStream().use { input -> session.openWrite("BCW.apk", 0, file.length()).use { output -> input.copyTo(output) } }
                val intent = Intent(context.packageName).setPackage(context.packageName)
                val pending = android.app.PendingIntent.getBroadcast(
                    context, sessionId, intent,
                    android.app.PendingIntent.FLAG_MUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT,
                )
                session.commit(pending.intentSender)
            }
            onComplete(null)
        } catch (e: Exception) {
            onComplete(e.message ?: "L'installazione non è partita.")
        } finally {
            // Il contenuto è nel gestore delle sessioni del sistema: il file temporaneo non serve più.
            file.delete()
        }
    }
}
