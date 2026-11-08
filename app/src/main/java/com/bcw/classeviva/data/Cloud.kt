package com.bcw.classeviva.data

import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.bcw.classeviva.util.parseJson
import com.bcw.classeviva.util.str
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.URLEncoder
import java.net.UnknownHostException
import java.time.LocalDateTime

/** Server degli account BCW (BCW-api, https://github.com/kubos5/BCW-api). */
object BCWServer {
    const val DEFAULT_URL = "https://bcw-api.romeo-ca1211.workers.dev"
    private const val KEY = "bcwServerURL"
    lateinit var prefs: SharedPreferences

    /** "bcw-api.esempio.workers.dev/" → "https://bcw-api.esempio.workers.dev"; `null` se non è un indirizzo. */
    fun normalized(text: String): String? {
        var value = text.trim().trimEnd('/')
        if (value.isEmpty()) return null
        if (!value.lowercase().startsWith("https://") && !value.lowercase().startsWith("http://")) value = "https://$value"
        val host = runCatching { java.net.URI(value).host }.getOrNull()
        return if (host.isNullOrEmpty()) null else value
    }

    val base: String get() = prefs.getString(KEY, null)?.let { normalized(it) } ?: DEFAULT_URL
    val isCustom: Boolean get() = prefs.getString(KEY, null) != null
    val host: String get() = runCatching { java.net.URI(base).host }.getOrNull() ?: base

    /** Imposta l'indirizzo (vuoto = quello predefinito). `false` se il testo non è un indirizzo. */
    fun set(text: String): Boolean {
        if (text.isBlank()) {
            prefs.edit().remove(KEY).apply()
            return true
        }
        val url = normalized(text) ?: return false
        prefs.edit().putString(KEY, url).apply()
        return true
    }
}

/** Errore dell'API di BCW: `code` è quello del server (es. `conflict`) oppure `offline`. */
class CloudError(
    val code: String, message: String, val status: Int = 0, val document: JsonElement? = null,
    val version: Int? = null, val choices: List<LoginChoice> = emptyList(),
) : Exception(message) {
    val isUnauthorized get() = code == "unauthorized"
    val isOffline get() = code == "offline"
}

data class BCWUser(val id: String, val username: String)
data class BCWSession(val token: String, val user: BCWUser)
data class BCWLink(val id: String, val username: String, val linkedAt: Long)

/** Account Classeviva salvato (sul dispositivo o abbinato all'account BCW, con l'id del server). */
data class SavedAccount(val id: String, val credentials: Credentials, val name: String, val school: String?)

fun SavedAccount.toJson(): JsonObject = obj(
    "id" to id, "name" to name, "school" to school,
    "credentials" to obj("username" to credentials.username, "password" to credentials.password, "ident" to credentials.ident),
)

fun savedAccountFrom(o: JsonObject): SavedAccount? {
    val c = o["credentials"] as? JsonObject ?: return null
    val username = c.str("username") ?: return null
    return SavedAccount(o.str("id") ?: return null, Credentials(username, c.str("password") ?: "", c.str("ident")), o.str("name") ?: username, o.str("school"))
}

/** Account abbinato nel formato degli account salvati. */
fun accountFromServer(remote: JsonObject?): SavedAccount? {
    val c = remote?.get("credentials") as? JsonObject ?: return null
    val username = c.str("username")?.takeIf { it.isNotEmpty() } ?: return null
    return SavedAccount(
        id = remote.str("id") ?: return null,
        credentials = Credentials(username, c.str("password") ?: "", c.str("ident") ?: remote.str("ident")),
        name = remote.str("name")?.takeIf { it.isNotEmpty() } ?: username,
        school = remote.str("school"),
    )
}

object CloudAPI {
    const val CLIENT_NAME = "BCW per Android"
    private val jsonType = "application/json".toMediaType()

    /** Richiesta all'API di BCW: restituisce il JSON (o i byte con `binary`); gli errori diventano `CloudError`. */
    suspend fun request(
        method: String, path: String, token: String? = null, body: JsonElement? = null, raw: ByteArray? = null,
        type: String? = null, headers: Map<String, String> = emptyMap(), timeoutSeconds: Long = 25,
    ): ByteArray = withContext(Dispatchers.IO) {
        val base = BCWServer.base
        val builder = Request.Builder().url(base + path)
            .header("Accept", "application/json")
            .header("X-BCW-Client", "android")
        if (token != null) builder.header("Authorization", "Bearer $token")
        for ((k, v) in headers) builder.header(k, v)
        val requestBody = when {
            body != null -> body.toString().toByteArray().toRequestBody(jsonType)
            raw != null -> raw.toRequestBody((type ?: "application/octet-stream").toMediaType())
            method == "POST" || method == "PUT" -> ByteArray(0).toRequestBody(jsonType)
            else -> null
        }
        builder.method(method, requestBody)
        val client = Http.client.newBuilder().callTimeout(timeoutSeconds, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(timeoutSeconds, java.util.concurrent.TimeUnit.SECONDS).build()
        val response = try {
            client.newCall(builder.build()).execute()
        } catch (e: UnknownHostException) {
            throw CloudError("offline", "Nessuna connessione a Internet.")
        } catch (e: IOException) {
            throw CloudError("offline", "Impossibile contattare il server BCW (${BCWServer.host}).")
        }
        response.use {
            val bytes = try { it.body?.bytes() ?: ByteArray(0) } catch (e: IOException) {
                throw CloudError("offline", "Impossibile contattare il server BCW (${BCWServer.host}).")
            }
            // Il server di BCW segna ogni risposta: senza il segno all'indirizzo risponde altro.
            if (it.header("X-BCW-API") == null) {
                throw CloudError("not_configured", "All'indirizzo ${BCWServer.host} non risponde il server BCW. Controlla l'indirizzo in Impostazioni › Account › Server BCW.")
            }
            if (!it.isSuccessful) {
                val json = parseJson(bytes.toString(Charsets.UTF_8)) as? JsonObject
                throw CloudError(
                    code = json.str("error") ?: "server_error",
                    message = json.str("message") ?: "Errore del server BCW (${it.code}).",
                    status = it.code,
                    document = json?.get("data")?.takeIf { d -> d is JsonObject },
                    version = (json?.get("version") as? JsonPrimitive)?.intOrNull,
                    choices = (json?.get("choices") as? JsonArray)?.mapNotNull { c -> (c as? JsonObject)?.let(::parseLoginChoice) } ?: emptyList(),
                )
            }
            bytes
        }
    }

    suspend fun json(method: String, path: String, token: String? = null, body: JsonElement? = null, headers: Map<String, String> = emptyMap()): JsonObject {
        val bytes = request(method, path, token, body, headers = headers)
        return parseJson(bytes.toString(Charsets.UTF_8)) as? JsonObject ?: JsonObject(emptyMap())
    }

    fun escaped(name: String): String = URLEncoder.encode(name, "UTF-8").replace("+", "%20")
}

fun parseSession(o: JsonObject?): BCWSession? {
    val token = o.str("token") ?: return null
    val user = o?.get("user") as? JsonObject ?: return null
    return BCWSession(token, BCWUser(user.str("id") ?: return null, user.str("username") ?: return null))
}

fun BCWSession.toJson(): JsonObject = obj("token" to token, "user" to obj("id" to user.id, "username" to user.username))

// MARK: - Sincronizzazione

/** Cosa si sincronizza con l'account BCW, scelto per questo dispositivo. */
enum class SyncCategory(val title: String, val subtitle: String, val icon: String) {
    settings("Impostazioni", "Voti, Dashboard e Orario", "tune"),
    appearance("Aspetto", "Colori, tema chiaro o scuro, angoli, animazioni e testo", "palette"),
    homework("Compiti fatti", "Compiti segnati come fatti", "checkCircle"),
    timetable("Orario", "Eventi, colori, note e file di ogni account abbinato", "timetable");

    companion object {
        private val appearanceKeys = setOf("appearance", "accentColor", "colorStyle", "contrast", "corners", "motion", "roundedText")

        /** La voce di un'impostazione sincronizzata; quelle nuove o sconosciute stanno in Impostazioni. */
        fun of(key: String): SyncCategory = when {
            key in appearanceKeys -> appearance
            key == "completedHomework" -> homework
            else -> settings
        }
    }
}

/**
 * Impostazioni che valgono su tutti i dispositivi (gli stessi nomi del web e dell'app iOS). Restano
 * locali blocco, promemoria, notifiche, colori del sistema e aggiornamenti in corso.
 */
val SYNCED_PREFERENCES = listOf(
    "averageMode", "weightedAverage", "targetAverage", "dashboardMode", "hideCompletedHomework", "showUpcomingDays",
    "completedHomework", "collapsedSections", "timetableMode", "timetableShowsTests", "timetableShowsNowCard",
    "titleAbbreviation", "showsTimetableTab",
    "appearance", "accentColor", "colorStyle", "contrast", "corners", "motion", "roundedText",
)

private fun oneOf(vararg allowed: String): (JsonElement) -> Boolean = { v -> (v as? JsonPrimitive)?.takeIf { it.isString }?.content in allowed }
private val isBool: (JsonElement) -> Boolean = { v -> (v as? JsonPrimitive)?.takeIf { !it.isString }?.booleanOrNull != null }

/** Valori accettati da questa versione. */
private val validators: Map<String, (JsonElement) -> Boolean> = mapOf(
    "averageMode" to oneOf("allGrades", "subjectAverages"),
    "weightedAverage" to isBool,
    "targetAverage" to { v -> (v as? JsonPrimitive)?.takeIf { !it.isString }?.doubleOrNull?.let { it in 1.0..10.0 } == true },
    "dashboardMode" to oneOf("list", "calendar"),
    "hideCompletedHomework" to isBool,
    "showUpcomingDays" to isBool,
    "completedHomework" to { v -> v is JsonArray && v.all { (it as? JsonPrimitive)?.takeIf { p -> !p.isString }?.longOrNull != null } },
    "collapsedSections" to { v -> v is JsonArray && v.all { (it as? JsonPrimitive)?.isString == true } },
    "timetableMode" to oneOf("list", "day"),
    "timetableShowsTests" to isBool,
    "timetableShowsNowCard" to isBool,
    "showsTimetableTab" to isBool,
    "titleAbbreviation" to oneOf("automatic", "always", "never"),
    "appearance" to oneOf("system", "light", "dark"),
    "accentColor" to { v -> (v as? JsonPrimitive)?.takeIf { it.isString }?.content?.matches(Regex("^#[0-9a-fA-F]{6}$")) == true },
    "colorStyle" to oneOf("tonalSpot", "vibrant", "expressive", "fidelity", "neutral", "rainbow", "fruitSalad"),
    "contrast" to oneOf("standard", "medium", "high"),
    "corners" to oneOf("square", "standard", "round"),
    "motion" to oneOf("expressive", "standard", "reduced"),
    "roundedText" to oneOf("titles", "all", "none"),
)

private fun validSettings(values: Map<String, JsonElement>): Map<String, JsonElement> = buildMap {
    for ((key, value) in values) {
        if (validators[key]?.invoke(value) == true) {
            put(key, if (key == "accentColor") JsonPrimitive((value as JsonPrimitive).content.lowercase()) else value)
        }
    }
}

private fun pick(values: Map<String, JsonElement>?, keys: Collection<String>): Map<String, JsonElement> =
    buildMap { if (values != null) for (k in keys) values[k]?.let { put(k, it) } }

/** Hash FNV-1a del testo (stesso del web), per capire se un documento è cambiato. */
fun hashText(text: String): String {
    var h = 0x811c9dc5.toInt()
    for (ch in text) {
        h = h xor ch.code
        h *= 0x01000193
    }
    return "${text.length}:${Integer.toHexString(h)}"
}

/** File citati da un orario. */
private fun referencedNames(data: JsonElement?): Set<String> {
    val names = LinkedHashSet<String>()
    fun add(list: JsonElement?) {
        (list as? JsonArray)?.forEach { a -> ((a as? JsonObject)?.get("storedName") as? JsonPrimitive)?.takeIf { it.isString }?.let { names += it.content } }
    }
    val o = data as? JsonObject
    (o?.get("entries") as? JsonArray)?.forEach { e -> add((e as? JsonObject)?.get("attachments")) }
    (o?.get("occurrences") as? JsonObject)?.values?.forEach { occ -> add((occ as? JsonObject)?.get("attachments")) }
    return names
}

enum class SyncStatus { syncing, ok, offline, error }

/**
 * Sincronizzazione con l'account BCW (come `CloudSync` del web e dell'app iOS). Impostazioni e
 * orario sono documenti con una versione: chi salva invia la versione da cui è partito e, se nel
 * frattempo un altro dispositivo ha salvato, riceve il documento attuale e unisce le modifiche.
 */
class CloudSync(private val model: AppModel, private val prefs: SharedPreferences, private val scope: CoroutineScope) {
    private val stateKey = "bcw.cloud.sync"

    private data class SettingsState(val version: Int = 0, val snapshot: String? = null, val remote: JsonObject? = null)
    private data class TimetableState(val version: Int = 0, val hash: String? = null)

    private var settingsState = SettingsState()
    private val timetables = HashMap<String, TimetableState>()

    var status: SyncStatus? by mutableStateOf(null)
        private set
    var message: String? by mutableStateOf(null)
        private set
    var lastSync: LocalDateTime? by mutableStateOf(null)
        private set
    /** Avviso sui file non caricati (spazio esaurito, file troppo grande). */
    var fileWarning: String? by mutableStateOf(null)
        private set

    private val timers = HashMap<String, Pair<Job, suspend () -> Unit>>()
    private val queues = HashMap<String, Mutex>()
    private var pending = 0
    private var applying = false

    init { loadState() }

    private val token: String? get() = model.cloud?.token
    val enabled: Boolean get() = token != null

    private fun loadState() {
        val o = parseJson(prefs.getString(stateKey, null) ?: return) as? JsonObject ?: return
        (o["settings"] as? JsonObject)?.let { s ->
            settingsState = SettingsState(
                (s["version"] as? JsonPrimitive)?.intOrNull ?: 0, s.str("snapshot"), s["remote"] as? JsonObject,
            )
        }
        (o["timetables"] as? JsonObject)?.forEach { (id, v) ->
            val t = v as? JsonObject ?: return@forEach
            timetables[id] = TimetableState((t["version"] as? JsonPrimitive)?.intOrNull ?: 0, t.str("hash"))
        }
    }

    private fun saveState() {
        val o = obj(
            "settings" to obj("version" to settingsState.version, "snapshot" to settingsState.snapshot, "remote" to settingsState.remote),
            "timetables" to JsonObject(timetables.mapValues { (_, t) -> obj("version" to t.version, "hash" to t.hash) }),
        )
        prefs.edit().putString(stateKey, o.toString()).apply()
    }

    fun reset() {
        for ((job, _) in timers.values) job.cancel()
        timers.clear()
        settingsState = SettingsState()
        timetables.clear()
        status = null
        message = null
        lastSync = null
        fileWarning = null
        prefs.edit().remove(stateKey).apply()
    }

    private suspend fun request(method: String, path: String, body: JsonElement? = null): JsonObject =
        CloudAPI.json(method, path, token, body)

    private suspend fun <T> serial(key: String, fn: suspend () -> T): T {
        val mutex = queues.getOrPut(key) { Mutex() }
        return mutex.withLock { fn() }
    }

    /** Esegue un'operazione aggiornando lo stato mostrato nelle impostazioni. */
    private suspend fun run(key: String, fn: suspend () -> Unit) {
        if (!enabled) return
        pending += 1
        status = SyncStatus.syncing
        try {
            serial(key) { fn() }
            pending -= 1
            if (pending == 0) {
                status = SyncStatus.ok
                message = null
                lastSync = LocalDateTime.now()
            }
        } catch (e: CloudError) {
            pending -= 1
            if (e.isUnauthorized) {
                model.cloudSessionExpired()
                return
            }
            if (e.code == "not_found") {
                // Account scollegato da un altro dispositivo: l'elenco aggiornato lo toglie.
                model.refreshCloud(force = true)
                if (pending == 0) status = null
                return
            }
            status = if (e.isOffline) SyncStatus.offline else SyncStatus.error
            message = e.message
        } catch (e: Exception) {
            pending -= 1
            status = SyncStatus.error
            message = e.message
        }
    }

    private fun schedule(key: String, delayMs: Long = 1500, fn: suspend () -> Unit) {
        timers.remove(key)?.first?.cancel()
        val job = scope.launch {
            delay(delayMs)
            timers.remove(key)
            run(key, fn)
        }
        timers[key] = job to fn
    }

    /** Invia subito le modifiche in attesa (es. quando l'app va in secondo piano). */
    suspend fun flush() {
        val waiting = timers.entries.toList()
        for ((_, pair) in waiting) pair.first.cancel()
        timers.clear()
        val jobs: List<Deferred<Unit>> = waiting.map { (key, pair) -> scope.async { run(key, pair.second) } }
        jobs.awaitAll()
    }

    val hasPendingChanges: Boolean get() = timers.isNotEmpty()

    // MARK: Impostazioni

    private fun syncsKey(key: String) = model.preferences.syncs(SyncCategory.of(key))
    private val syncedKeys: List<String> get() = SYNCED_PREFERENCES.filter(::syncsKey)
    val syncsTimetable: Boolean get() = model.preferences.syncs(SyncCategory.timetable)

    private fun localSettings(): Map<String, JsonElement> = pick(model.preferences.values, syncedKeys)

    fun settingsChanged() {
        if (!enabled || applying) return
        schedule("settings") { syncSettings(false) }
    }

    fun pullSettings() {
        scope.launch { run("settings") { syncSettings(true) } }
    }

    private fun applySettings(values: Map<String, JsonElement>) {
        applying = true
        try {
            model.preferences.applyRemote(validSettings(pick(values, syncedKeys)))
        } finally {
            applying = false
        }
        model.settingsApplied()
    }

    /** Una voce di "Da sincronizzare" è stata accesa o spenta. */
    fun categoryChanged(category: SyncCategory, enabled: Boolean) {
        if (!this.enabled) return
        if (category == SyncCategory.timetable) {
            if (!enabled) return
            for (account in model.accounts) pullTimetable(account.id)
            return
        }
        if (enabled) {
            pullSettings()
            return
        }
        val snapshot = settingsState.snapshot?.let { parseJson(it) as? JsonObject } ?: return
        val filtered = snapshot.filterKeys { SyncCategory.of(it) != category }
        settingsState = settingsState.copy(snapshot = JsonObject(filtered).toString())
        saveState()
    }

    /**
     * Unione a tre vie: le chiavi cambiate qui dall'ultima sincronizzazione vincono, le altre
     * arrivano dal server. La prima volta su un dispositivo vince il server.
     */
    private suspend fun syncSettings(fetchFirst: Boolean) {
        val before = settingsState.snapshot?.let { parseJson(it) as? JsonObject }
        var remoteData: JsonObject?
        var remoteVersion: Int
        if (fetchFirst) {
            val r = request("GET", "/sync/settings")
            remoteData = r["data"] as? JsonObject
            remoteVersion = (r["version"] as? JsonPrimitive)?.intOrNull ?: 0
        } else {
            remoteData = settingsState.remote
            remoteVersion = settingsState.version
        }
        repeat(3) {
            val keys = syncedKeys
            val local = localSettings()
            // Una chiave che l'ultima sincronizzazione non conosceva conta come cambiata solo se il
            // server non ce l'ha: il valore predefinito di un dispositivo appena aggiornato non
            // cancella quello scelto su un altro.
            val changed: List<String>? = before?.let { b ->
                keys.filter { k ->
                    if (b.containsKey(k)) local[k] != b[k] else local[k] != null && remoteData?.get(k) == null
                }
            }
            val merged: Map<String, JsonElement>
            if (remoteVersion == 0 || remoteData == null) {
                merged = (remoteData ?: emptyMap()) + local
            } else if (changed == null) {
                applySettings(remoteData!!)
                finishSettings(remoteVersion, remoteData!!)
                return
            } else {
                merged = remoteData!! + pick(local, changed)
                if (pick(merged, keys) != local) applySettings(merged)
                if (changed.isEmpty()) {
                    finishSettings(remoteVersion, remoteData!!)
                    return
                }
            }
            try {
                val saved = request("PUT", "/sync/settings", obj("data" to JsonObject(merged), "baseVersion" to remoteVersion))
                finishSettings((saved["version"] as? JsonPrimitive)?.intOrNull ?: (remoteVersion + 1), JsonObject(merged))
                return
            } catch (e: CloudError) {
                if (e.code != "conflict") throw e
                remoteData = e.document as? JsonObject
                remoteVersion = e.version ?: 0
            }
        }
    }

    private fun finishSettings(version: Int, data: JsonObject) {
        settingsState = SettingsState(version, JsonObject(localSettings()).toString(), data)
        saveState()
    }

    // MARK: Orario

    private fun isCloudAccount(id: String) = model.isCloud && model.accounts.any { it.id == id }

    fun timetableChanged(owner: String) {
        if (!enabled || applying || !syncsTimetable || !isCloudAccount(owner)) return
        schedule("timetable:$owner", 2000) { syncTimetable(owner, false) }
    }

    fun pullTimetable(id: String) {
        if (!syncsTimetable || !isCloudAccount(id)) return
        scope.launch { run("timetable:$id") { syncTimetable(id, true) } }
    }

    suspend fun pullTimetableNow(id: String) {
        if (!syncsTimetable || !isCloudAccount(id)) return
        run("timetable:$id") { syncTimetable(id, true) }
    }

    /**
     * L'orario è un documento unico: se è cambiato solo da una parte vince quella; se è cambiato da
     * entrambe vince questo dispositivo. La prima volta vince il server, se ha già un orario.
     */
    private suspend fun syncTimetable(id: String, fetchFirst: Boolean) {
        if (!syncsTimetable || !isCloudAccount(id)) return
        val st = timetables[id] ?: TimetableState()
        val neverSynced = st.version == 0 && st.hash == null
        var remoteData: JsonElement?
        var remoteVersion: Int
        if (fetchFirst) {
            val r = request("GET", "/sync/timetable/$id")
            remoteData = r["data"]?.takeIf { it is JsonObject }
            remoteVersion = (r["version"] as? JsonPrimitive)?.intOrNull ?: 0
        } else {
            remoteData = null
            remoteVersion = st.version
        }
        repeat(3) {
            val text = model.timetable.storedText(id)
            if (text == null) {
                // Nessun orario salvato qui: si prende quello del server, se c'è.
                if (remoteVersion != 0 && remoteData != null) applyTimetable(id, remoteData!!, remoteVersion)
                return
            }
            val hash = hashText(text)
            val dirty = !neverSynced && hash != st.hash
            val base: Int
            if (remoteVersion == 0) {
                base = 0
            } else if (neverSynced && remoteData != null) {
                applyTimetable(id, remoteData!!, remoteVersion)
                return
            } else if (remoteVersion == st.version) {
                if (!dirty) return
                base = st.version
            } else if (!dirty && remoteData != null) {
                applyTimetable(id, remoteData!!, remoteVersion)
                return
            } else {
                base = remoteVersion
            }
            val data = parseJson(text) ?: return
            uploadFiles(id, data)
            try {
                val saved = request("PUT", "/sync/timetable/$id", obj("data" to data, "baseVersion" to base))
                timetables[id] = TimetableState((saved["version"] as? JsonPrimitive)?.intOrNull ?: (base + 1), hash)
                saveState()
                return
            } catch (e: CloudError) {
                if (e.code != "conflict") throw e
                remoteData = e.document
                remoteVersion = e.version ?: 0
            }
        }
    }

    private suspend fun applyTimetable(id: String, data: JsonElement, version: Int) {
        applying = true
        val text = try {
            model.timetable.applyRemote(id, data)
        } finally {
            applying = false
        }
        timetables[id] = TimetableState(version, text?.let(::hashText))
        saveState()
        downloadMissing(id, data)
    }

    fun forgetTimetable(id: String) {
        timetables.remove(id)
        saveState()
    }

    // MARK: File dell'orario

    private suspend fun uploadFiles(id: String, data: JsonElement) {
        val names = referencedNames(data)
        if (names.isEmpty()) return
        val list = request("GET", "/files/$id")
        val present = (list["files"] as? JsonArray)?.mapNotNull { (it as? JsonObject).str("name") }?.toSet() ?: emptySet()
        for (name in names) {
            if (name in present) continue
            val file = model.timetable.file(id, name)
            val bytes = withContext(Dispatchers.IO) { if (file.exists()) file.readBytes() else null } ?: continue
            try {
                CloudAPI.request("PUT", "/files/$id/${CloudAPI.escaped(name)}", token, raw = bytes,
                    type = com.bcw.classeviva.data.timetable.mimeType(name), timeoutSeconds = 120)
            } catch (e: CloudError) {
                // Un file troppo grande o lo spazio esaurito non fermano l'orario.
                if (e.code == "quota_exceeded" || e.code == "too_large") {
                    fileWarning = e.message
                    continue
                }
                throw e
            }
        }
    }

    private suspend fun downloadMissing(id: String, data: JsonElement) {
        for (name in referencedNames(data)) {
            runCatching {
                if (withContext(Dispatchers.IO) { model.timetable.file(id, name).exists() }) return@runCatching
                fetchFile(id, name)
            }
        }
    }

    /** Scarica un file dell'orario mancante su questo dispositivo. */
    suspend fun fetchFile(id: String, name: String): ByteArray? {
        if (!enabled || !isCloudAccount(id)) return null
        val bytes = CloudAPI.request("GET", "/files/$id/${CloudAPI.escaped(name)}", token, timeoutSeconds = 120)
        withContext(Dispatchers.IO) {
            val f = model.timetable.file(id, name)
            f.parentFile?.mkdirs()
            f.writeBytes(bytes)
        }
        return bytes
    }
}
