package com.bcw.classeviva.data

import android.content.Context
import com.bcw.classeviva.util.parseJson
import com.bcw.classeviva.util.str
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.IOException
import java.net.URLDecoder
import java.net.UnknownHostException
import java.security.MessageDigest
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

/** Errori di Classeviva, con il messaggio da mostrare. */
sealed class APIError(message: String) : Exception(message) {
    class WrongCredentials(message: String) : APIError(message)
    class NeedsProfileChoice(val choices: List<LoginChoice>) : APIError("Scegli il profilo a cui accedere.")
    object NotAuthenticated : APIError("Sessione scaduta. Accedi di nuovo.") { private fun readResolve(): Any = NotAuthenticated }
    class Server(val code: Int, message: String) : APIError(message.ifEmpty { "Errore del server ($code)." })
    class Unavailable(message: String) : APIError(message)
    object Decoding : APIError("Risposta inattesa da Classeviva.") { private fun readResolve(): Any = Decoding }
    /** La rete non risponde. */
    class Offline(message: String = "Impossibile contattare Classeviva.") : APIError(message)
}

val Throwable.isOffline: Boolean get() = this is APIError.Offline

data class HttpResult(val status: Int, val contentType: String?, val fileName: String?, val data: ByteArray) {
    val isJson: Boolean get() = contentType?.contains("json") == true
    val text: String get() = data.toString(Charsets.UTF_8)
}

/** Livello di trasporto: permette di sostituire il server reale con quello della demo. */
interface Transport {
    suspend fun send(method: String, path: String, body: String?, token: String?): HttpResult
}

object Http {
    val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(25, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .callTimeout(120, TimeUnit.SECONDS)
            .build()
    }
}

class HttpTransport(private val baseURL: String) : Transport {
    companion object {
        const val OFFICIAL = "https://web.spaggiari.eu/rest/v1"
        fun archive(startYear: Int) = String.format(java.util.Locale.ROOT, "https://web%02d.spaggiari.eu/rest/v1", startYear % 100)

        /** Intestazioni dell'app ufficiale di Classeviva: senza, l'API rifiuta le richieste. */
        val defaultHeaders = mapOf(
            "User-Agent" to "CVVS/std/4.2.3 Android/12",
            "Z-Dev-Apikey" to "Tg1NWEwNGIgIC0K",
            "Accept" to "application/json",
        )
        private val jsonType = "application/json".toMediaType()
    }

    override suspend fun send(method: String, path: String, body: String?, token: String?): HttpResult = withContext(Dispatchers.IO) {
        val builder = Request.Builder().url(baseURL + path)
        for ((k, v) in defaultHeaders) builder.header(k, v)
        if (token != null) builder.header("Z-Auth-Token", token)
        if (method == "POST") {
            // Classeviva rifiuta il JSON vuoto `{}` ("101:CvvRestApi/invalid payload") ma accetta un
            // corpo vuoto; il CDN blocca le POST senza Content-Length, quindi il corpo c'è sempre.
            val compact = body?.filterNot { it.isWhitespace() }
            val payload = if (body == null || compact == "{}") ByteArray(0) else body.toByteArray()
            builder.post(payload.toRequestBody(jsonType))
        } else {
            builder.get()
        }
        try {
            Http.client.newCall(builder.build()).execute().use { response ->
                val bytes = response.body?.bytes() ?: ByteArray(0)
                HttpResult(
                    status = response.code,
                    contentType = response.header("Content-Type"),
                    fileName = fileName(response.header("Content-Disposition")),
                    data = bytes,
                )
            }
        } catch (e: UnknownHostException) {
            throw APIError.Offline("Nessuna connessione a Internet.")
        } catch (e: IOException) {
            throw APIError.Offline()
        }
    }
}

/** Nome del file da `Content-Disposition` (anche `filename*=UTF-8''…`). */
fun fileName(disposition: String?): String? {
    if (disposition == null) return null
    // Le intestazioni arrivano come Latin-1: i nomi in UTF-8 (es. "n°") vanno ridecodificati.
    val text = if (disposition.any { it.code in 0x80..0xFF } && disposition.all { it.code <= 0xFF }) {
        runCatching {
            val bytes = ByteArray(disposition.length) { disposition[it].code.toByte() }
            Charsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(bytes)).toString()
        }.getOrDefault(disposition)
    } else disposition
    for (part in text.split(';')) {
        val trimmed = part.trim()
        val lower = trimmed.lowercase()
        if (lower.startsWith("filename*=")) {
            val value = trimmed.substring("filename*=".length)
            val index = value.indexOf("''")
            if (index >= 0) runCatching { return URLDecoder.decode(value.substring(index + 2).replace("+", "%2B"), "UTF-8") }
        }
        if (lower.startsWith("filename=")) return trimmed.substring("filename=".length).trim('"')
    }
    return null
}

/** Un file scaricato, pronto per l'anteprima. */
data class DownloadedFile(val name: String, val type: String, val data: ByteArray)

fun makeFile(result: HttpResult, suggestedName: String?): DownloadedFile {
    var name = (result.fileName ?: suggestedName ?: "documento").replace('/', '-')
    val type = result.contentType?.substringBefore(';')?.trim()?.ifEmpty { null } ?: "application/octet-stream"
    if (!Regex("\\.[a-zA-Z0-9]{2,5}$").containsMatchIn(name)) {
        when {
            type.contains("pdf") -> name += ".pdf"
            type.contains("png") -> name += ".png"
            type.contains("jpeg") -> name += ".jpg"
        }
    }
    return DownloadedFile(name, type, result.data)
}

private fun errorMessage(result: HttpResult): String? {
    val json = parseJson(result.text) as? JsonObject ?: return null
    json.str("message")?.takeIf { it.isNotEmpty() }?.let { return it }
    json.str("error")?.takeIf { it.isNotEmpty() }?.let { return readable(it) }
    return null
}

/** Traduce i codici d'errore più comuni di Classeviva (es. "101:CvvRestApi/invalid payload"). */
private fun readable(error: String): String {
    val lower = error.lowercase()
    return when {
        lower.contains("must first be read") -> "Apri prima la comunicazione, poi riprova a scaricare l'allegato."
        lower.contains("invalid payload") -> "Classeviva ha rifiutato la richiesta ($error)."
        lower.contains("authentication failed") -> "Nome utente o password non validi."
        lower.contains("not found") -> "Elemento non trovato su Classeviva."
        else -> error
    }
}

// MARK: - Cache su disco

/** Cache delle risposte JSON per la modalità offline, una cartella per spazio (account, demo, archivio). */
class DiskCache(val namespace: String) {
    companion object {
        lateinit var root: File
        fun init(context: Context) { root = File(context.noBackupFilesDir, "cache") }

        private fun safe(name: String) = name.replace(Regex("[^A-Za-z0-9_.-]"), "_")

        /** Elimina del tutto una cache (es. quella di un account rimosso). */
        fun remove(namespace: String) { runCatching { File(root, safe(namespace)).deleteRecursively() } }
    }

    private val dir: File get() = File(root, safe(namespace))

    private fun file(path: String): File {
        val digest = MessageDigest.getInstance("SHA-1").digest(path.toByteArray()).joinToString("") { "%02x".format(it) }
        return File(dir, "$digest.json")
    }

    suspend fun store(text: String, path: String) = withContext(Dispatchers.IO) {
        runCatching {
            dir.mkdirs()
            val target = file(path)
            val temp = File(dir, target.name + ".tmp")
            temp.writeText(text)
            temp.renameTo(target)
        }
    }

    suspend fun load(path: String): String? = withContext(Dispatchers.IO) { runCatching { file(path).takeIf { it.exists() }?.readText() }.getOrNull() }
}

// MARK: - Client

data class Credentials(val username: String, val password: String, val ident: String?)

data class FetchResult<T>(val value: T, val fromCache: Boolean)

/** Client per le API REST non ufficiali di Classeviva, le stesse dell'app ufficiale. */
class ClassevivaClient(
    private val transport: Transport,
    cacheNamespace: String,
    credentials: Credentials?,
) {
    val cache = DiskCache(cacheNamespace)
    @Volatile var credentials: Credentials? = credentials
    @Volatile var token: String? = null
        private set
    @Volatile var tokenExpiry: LocalDateTime? = null
        private set
    @Volatile var ident: String? = credentials?.ident
        private set
    private val loginMutex = Mutex()
    private var pendingLogin: CompletableDeferred<LoginResponse>? = null

    /** ID numerico dello studente: "S1234567X" → "1234567". */
    val studentId: String? get() = ident?.filter { it.isDigit() }?.ifEmpty { null }

    // MARK: Autenticazione

    /** Un solo accesso alla volta: le richieste in attesa condividono lo stesso risultato. */
    suspend fun login(): LoginResponse {
        val waiting: CompletableDeferred<LoginResponse>
        val owner: Boolean
        loginMutex.withLock {
            val existing = pendingLogin
            if (existing != null) {
                waiting = existing
                owner = false
            } else {
                waiting = CompletableDeferred()
                pendingLogin = waiting
                owner = true
            }
        }
        if (!owner) return waiting.await()
        try {
            val response = performLogin()
            waiting.complete(response)
            return response
        } catch (e: Throwable) {
            waiting.completeExceptionally(e)
            throw e
        } finally {
            loginMutex.withLock { pendingLogin = null }
        }
    }

    private suspend fun performLogin(): LoginResponse {
        val c = credentials ?: throw APIError.NotAuthenticated
        val body = buildJsonObject {
            put("uid", c.username)
            put("pass", c.password)
            if (c.ident != null) put("ident", c.ident) else put("ident", JsonNull)
        }.toString()
        val result = transport.send("POST", "/auth/login", body, null)
        if (result.status !in 200..299) {
            val message = errorMessage(result)
            if (result.status in setOf(400, 401, 422)) throw APIError.WrongCredentials(message ?: "Nome utente o password non validi.")
            throw APIError.Server(result.status, message ?: "")
        }
        val json = parseJson(result.text) as? JsonObject ?: throw APIError.Decoding
        val response = parseLoginResponse(json)
        if (response.token == null && response.choices.isNotEmpty()) throw APIError.NeedsProfileChoice(response.choices)
        val token = response.token ?: throw APIError.Decoding
        this.token = token
        this.tokenExpiry = response.expire ?: LocalDateTime.now().plusHours(1)
        this.ident = response.ident ?: c.ident
        return response
    }

    /** Riusa la sessione di un altro client (es. quello usato per verificare le credenziali). */
    fun adoptSession(other: ClassevivaClient) {
        token = other.token
        tokenExpiry = other.tokenExpiry
        ident = other.ident
    }

    suspend fun ensureToken() {
        val expiry = tokenExpiry
        if (token != null && expiry != null && java.time.Duration.between(LocalDateTime.now(), expiry).seconds > 120) return
        login()
    }

    // MARK: Richieste

    /** Richiesta autenticata su un percorso relativo allo studente; rinnova il token se scaduto. */
    suspend fun raw(method: String, studentPath: String, body: String? = null): HttpResult {
        ensureToken()
        val id = studentId ?: throw APIError.NotAuthenticated
        val path = "/students/$id/$studentPath"
        var result = transport.send(method, path, body, token)
        if (result.status == 401 || result.status == 403) {
            token = null
            login()
            result = transport.send(method, path, body, token)
        }
        if (result.status !in 200..299) {
            if (result.status == 401) throw APIError.NotAuthenticated
            throw APIError.Server(result.status, errorMessage(result) ?: "")
        }
        return result
    }

    /** Scarica e decodifica una risposta JSON; senza rete restituisce l'ultima copia salvata. */
    suspend fun <T> fetch(
        parse: (JsonElement) -> T, studentPath: String, method: String = "GET", body: String? = null, useCache: Boolean = true,
    ): FetchResult<T> {
        try {
            val result = raw(method, studentPath, body)
            val text = result.text
            val json = parseJson(text) ?: throw APIError.Decoding
            val value = try { parse(json) } catch (e: Exception) { throw APIError.Decoding }
            if (useCache) cache.store(text, studentPath)
            return FetchResult(value, false)
        } catch (e: APIError) {
            if (useCache && e.isOffline) {
                cached(parse, studentPath)?.let { return FetchResult(it, true) }
            }
            throw e
        }
    }

    /** Prova più endpoint equivalenti (es. `grades` / `grades2324`) e usa il primo che risponde. */
    suspend fun <T> fetchFirst(parse: (JsonElement) -> T, paths: List<String>, accept: (T) -> Boolean = { true }): FetchResult<T> {
        var lastError: APIError = APIError.Decoding
        var fallback: FetchResult<T>? = null
        for (path in paths) {
            try {
                val result = fetch(parse, path)
                if (accept(result.value)) return result
                if (fallback == null) fallback = result
            } catch (e: APIError) {
                val skippable = (e is APIError.Server && e.code in setOf(400, 404, 405)) || e is APIError.Decoding
                if (!skippable) throw e
                lastError = e
            }
        }
        return fallback ?: throw lastError
    }

    /** Ultima risposta salvata, per mostrare subito i dati all'avvio. */
    suspend fun <T> cached(parse: (JsonElement) -> T, studentPath: String): T? {
        val text = cache.load(studentPath) ?: return null
        return runCatching { parseJson(text)?.let(parse) }.getOrNull()
    }

    /** Scarica un file. */
    suspend fun download(studentPath: String, method: String = "GET", suggestedName: String?): DownloadedFile {
        val result = raw(method, studentPath)
        // Al posto del file Classeviva può rispondere con un errore in JSON.
        if (result.isJson) errorMessage(result)?.let { throw APIError.Server(result.status, it) }
        return makeFile(result, suggestedName)
    }

    fun signOut() {
        token = null
        tokenExpiry = null
        credentials = null
    }
}

fun jsonBody(vararg pairs: Pair<String, Any?>): String = buildJsonObject {
    for ((k, v) in pairs) when (v) {
        null -> put(k, JsonNull)
        is String -> put(k, v)
        is Boolean -> put(k, v)
        is Number -> put(k, v)
        is JsonElement -> put(k, v)
        else -> put(k, JsonPrimitive(v.toString()))
    }
}.toString()
