package com.bcw.classeviva.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.bcw.classeviva.data.timetable.TimetableStore
import com.bcw.classeviva.util.Dates
import com.bcw.classeviva.util.StableID
import com.bcw.classeviva.util.nameCased
import com.bcw.classeviva.util.newUUID
import com.bcw.classeviva.util.parseJson
import com.bcw.classeviva.util.str
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime

enum class Phase { launching, signedOut, signedIn }

/** Collegamenti dai widget e dalle notifiche (`bcw://orario?giorno=…`, `bcw://dashboard?giorno=…`, `bcw://voti`). */
data class OpenedLink(val tab: String, val day: LocalDate?, val id: Long = System.nanoTime())

/** Ganci verso Android (notifiche, promemoria, widget), impostati dall'applicazione. */
interface PlatformHooks {
    fun notify(title: String, body: String, tag: String, channel: String)
    fun rescheduleReminders(plan: List<ReminderPlanItem>)
    fun dataChanged()
}

data class ReminderPlanItem(val id: String, val fireAt: LocalDateTime, val title: String, val body: String)

private const val OFFLINE_MESSAGE = "Sei offline: stai vedendo gli ultimi dati salvati."
private val gradePaths = listOf("grades", "grades2324", "grades2")

fun accountInitials(account: SavedAccount): String {
    val letters = account.name.split(' ').filter { it.isNotEmpty() }.take(2).joinToString("") { it.take(1) }
    return letters.uppercase().ifEmpty { "?" }
}

fun accountColorID(account: SavedAccount): Long = StableID.make(account.id)

/** Stato globale dell'app: sessione, account salvati, dati del registro e operazioni (`js/store.js`). */
class AppModel(context: Context, var hooks: PlatformHooks? = null) {
    private val appContext = context.applicationContext
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val defaults: SharedPreferences = appContext.getSharedPreferences("bcw.state", Context.MODE_PRIVATE)
    private val secure = SecureStore(appContext)

    val preferences = Preferences(appContext.getSharedPreferences("bcw.prefs", Context.MODE_PRIVATE))
    val timetable = TimetableStore(appContext.filesDir)

    var phase by mutableStateOf(Phase.launching)
        private set
    var isDemo by mutableStateOf(false)
        private set
    var client: ClassevivaClient? = null
        private set
    var accounts by mutableStateOf<List<SavedAccount>>(emptyList())
        private set
    var activeAccountID by mutableStateOf<String?>(null)
        private set
    var isChangingSession by mutableStateOf(false)
        private set
    private var pendingSessionChange: (() -> Unit)? = null
    private var archives = HashMap<Int, ArchiveModel>()

    /** Sessione dell'account BCW, `null` con gli account del dispositivo. */
    var cloud by mutableStateOf<BCWSession?>(null)
        private set
    /** Messaggio per la schermata di accesso (es. sessione BCW scaduta). */
    var cloudNotice by mutableStateOf<String?>(null)
    var lastCloudUsername by mutableStateOf<String?>(null)
        private set
    private var cloudRefreshedAt = 0L
    private var cloudRefreshing = false
    val sync = CloudSync(this, defaults, scope)

    // Dati del registro
    var loginName by mutableStateOf<String?>(null)
        private set
    var card by mutableStateOf<Card?>(null)
        private set
    var grades by mutableStateOf<List<Grade>>(emptyList())
        private set
    var periods by mutableStateOf<List<Period>>(emptyList())
        private set
    var subjects by mutableStateOf<List<Subject>>(emptyList())
        private set
    var agenda by mutableStateOf<List<AgendaEvent>>(emptyList())
        private set
    var absences by mutableStateOf<List<Absence>>(emptyList())
        private set
    var notices by mutableStateOf<List<Notice>>(emptyList())
        private set
    var notes by mutableStateOf<List<Note>>(emptyList())
        private set
    var didactics by mutableStateOf<List<DidacticTeacher>>(emptyList())
        private set
    var documents by mutableStateOf<Documents?>(null)
        private set
    var calendarDays by mutableStateOf<List<CalendarDay>>(emptyList())
        private set
    var schoolbooks by mutableStateOf<List<SchoolbookCourse>>(emptyList())
        private set
    var lessonsByDay by mutableStateOf<Map<LocalDate, List<Lesson>>>(emptyMap())
        private set
    var loadedLessonDays by mutableStateOf<Set<LocalDate>>(emptySet())
        private set
    var isRefreshing by mutableStateOf(false)
        private set
    var isOffline by mutableStateOf(false)
        private set
    var lastUpdated by mutableStateOf<LocalDateTime?>(null)
        private set
    var lastError by mutableStateOf<String?>(null)
    private var refreshingClient: ClassevivaClient? = null

    /** Collegamento da aprire (widget, notifiche). */
    var openedLink by mutableStateOf<OpenedLink?>(null)

    init {
        DiskCache.init(appContext)
        BCWServer.prefs = defaults
        preferences.onPersist = { sync.settingsChanged() }
        // Un cambio d'impostazioni (es. marchio dei widget) ridisegna anche i widget.
        preferences.onChange = {
            gradeBookCache = null
            hooks?.dataChanged()
        }
        timetable.onSaved = { owner ->
            sync.timetableChanged(owner)
            hooks?.dataChanged()
        }
        timetable.remoteFile = { owner, name -> sync.fetchFile(owner, name) }
    }

    private fun resetFields() {
        loginName = null
        card = null
        grades = emptyList()
        periods = emptyList()
        subjects = emptyList()
        agenda = emptyList()
        absences = emptyList()
        notices = emptyList()
        notes = emptyList()
        didactics = emptyList()
        documents = null
        calendarDays = emptyList()
        schoolbooks = emptyList()
        lessonsByDay = emptyMap()
        loadedLessonDays = emptySet()
        isRefreshing = false
        isOffline = false
        lastUpdated = null
        lastError = null
        refreshingClient = null
        gradeBookCache = null
    }

    // MARK: Archivio degli account

    private fun readAccounts(key: String): List<SavedAccount> {
        val text = secure.get(key) ?: return emptyList()
        return (parseJson(text) as? JsonArray)?.mapNotNull { (it as? JsonObject)?.let(::savedAccountFrom) } ?: emptyList()
    }

    private fun writeAccounts(key: String, list: List<SavedAccount>) =
        secure.put(key, if (list.isEmpty()) null else JsonArray(list.map { it.toJson() }).toString())

    private val localAccountsKey = "classeviva.accounts"
    private val cloudAccountsKey = "bcw.accounts"
    private val sessionKey = "bcw.session"
    private val activeKey = "activeAccount"
    private val cloudActiveKey = "activeCloudAccount"
    private val demoKey = "demoMode"

    private val onboardingKey = "onboardingCompleted"

    /**
     * Prima apertura: nessuna introduzione finita e nessun segno che BCW fosse già in uso
     * (account salvati, account BCW, demo). Chi aggiorna l'app non la vede.
     */
    fun isFirstRun(): Boolean {
        if (defaults.getBoolean(onboardingKey, false)) return false
        val first = secure.get(localAccountsKey) == null && secure.get(sessionKey) == null && !defaults.getBoolean(demoKey, false)
        if (!first) markOnboardingCompleted()
        return first
    }

    fun markOnboardingCompleted() = defaults.edit().putBoolean(onboardingKey, true).apply()

    private fun readSession(): BCWSession? {
        val text = secure.get(sessionKey) ?: return null
        return parseSession(parseJson(text) as? JsonObject)
    }

    val activeAccount: SavedAccount? get() = accounts.firstOrNull { it.id == activeAccountID }

    /** Con `refresh = false` (avvio in background per un widget o una notifica) mostra solo la cache. */
    fun bootstrap(refresh: Boolean = true) {
        if (phase != Phase.launching) return
        cloud = readSession()
        accounts = if (cloud != null) readAccounts(cloudAccountsKey) else readAccounts(localAccountsKey)
        val storedID = storedActiveID()
        if (cloud != null) scope.launch { refreshCloud(force = true) }
        if (defaults.getBoolean(demoKey, false)) {
            // Uscendo dalla demo si torna all'account che era attivo, anche dopo un riavvio.
            if (accounts.any { it.id == storedID }) activeAccountID = storedID
            beginDemo(refresh)
            return
        }
        val account = accounts.firstOrNull { it.id == storedID } ?: accounts.firstOrNull()
        if (account == null) {
            phase = Phase.signedOut
            return
        }
        activate(account, refresh)
    }

    /** Aggiorna se i dati non sono recenti (tornando nell'app). */
    fun refreshIfStale(minutes: Long = 5) {
        if (phase != Phase.signedIn) return
        val last = lastUpdated
        if (last == null || last.isBefore(LocalDateTime.now().minusMinutes(minutes))) scope.launch { refreshAll() }
        if (cloud != null) scope.launch { refreshCloud() }
    }

    private fun saveAccounts() {
        if (cloud != null) writeAccounts(cloudAccountsKey, accounts) else writeAccounts(localAccountsKey, accounts)
    }

    private fun storedActiveID(): String? = defaults.getString(if (cloud != null) cloudActiveKey else activeKey, null)

    private fun saveActiveID(id: String?) {
        defaults.edit().apply { if (id == null) remove(if (cloud != null) cloudActiveKey else activeKey) else putString(if (cloud != null) cloudActiveKey else activeKey, id) }.apply()
    }

    private fun matches(account: SavedAccount, c: Credentials) =
        account.credentials.username.equals(c.username, ignoreCase = true) && (account.credentials.ident ?: "") == (c.ident ?: "")

    private fun sameProfile(a: SavedAccount, b: SavedAccount): Boolean {
        val ia = (a.credentials.ident ?: "").uppercase()
        return ia.isNotEmpty() && ia == (b.credentials.ident ?: "").uppercase()
    }

    /**
     * Accede e aggiunge (o aggiorna) l'account tra quelli salvati, rendendolo attivo. Lancia
     * `NeedsProfileChoice` per i genitori con più figli: in quel caso si richiama con `ident`.
     */
    suspend fun signIn(username: String, password: String, ident: String? = null) {
        val credentials = Credentials(username.trim(), password, ident)
        val probe = ClassevivaClient(HttpTransport(HttpTransport.OFFICIAL), "login", credentials)
        val response = probe.login()
        val stored = credentials.copy(ident = ident ?: response.ident)
        val name = listOfNotNull(response.firstName, response.lastName).joinToString(" ").nameCased()

        val account: SavedAccount
        if (cloud != null) {
            // Con un account BCW l'account viene abbinato sul server, che verifica le credenziali.
            account = upsertCloudAccount(linkOnServer(stored))
        } else {
            val existing = accounts.firstOrNull { matches(it, stored) }
            account = if (existing != null) {
                val updated = existing.copy(credentials = stored, name = name.ifEmpty { existing.name })
                accounts = accounts.map { if (it.id == existing.id) updated else it }
                updated
            } else {
                val created = SavedAccount(newUUID(), stored, name.ifEmpty { stored.username }, null)
                accounts = accounts + created
                created
            }
        }
        saveAccounts()
        if (phase == Phase.signedIn) {
            // Account aggiunto ad app aperta: i dati cambiano mentre l'interfaccia è sfumata.
            changeSession {
                activate(account, refresh = false)
                client?.adoptSession(probe)
                scope.launch { refreshAll() }
            }
            return
        }
        activate(account, refresh = false)
        client?.adoptSession(probe)
        refreshAll()
    }

    // MARK: Cambio di sessione con dissolvenza

    /**
     * Cambia sessione sfumando l'interfaccia: i dati cambiano mentre non si vedono e l'interfaccia
     * riappare quando ci sono (subito con la cache, al massimo dopo 3 s).
     */
    fun changeSession(change: () -> Unit) {
        if (phase != Phase.signedIn) {
            change()
            return
        }
        pendingSessionChange = change
        if (isChangingSession) return
        isChangingSession = true
        scope.launch {
            delay(200)
            val next = pendingSessionChange
            pendingSessionChange = null
            next?.invoke()
            waitForSessionContent()
            delay(60)
            val again = pendingSessionChange
            if (again != null) {
                isChangingSession = false
                pendingSessionChange = null
                changeSession(again)
                return@launch
            }
            isChangingSession = false
        }
    }

    private suspend fun waitForSessionContent() {
        withTimeoutOrNull(3000) {
            while (true) {
                if (card != null || agenda.isNotEmpty() || grades.isNotEmpty() || lastUpdated != null || lastError != null || phase != Phase.signedIn) return@withTimeoutOrNull
                delay(40)
            }
        }
    }

    /** Passa a un altro account salvato. */
    fun switchAccount(id: String) {
        val account = accounts.firstOrNull { it.id == id } ?: return
        if (!isDemo && account.id == activeAccountID) return
        changeSession {
            if (isDemo) leaveDemo()
            activate(account)
        }
    }

    /** Rende attivo un account: azzera i dati, mostra subito la cache e aggiorna. */
    private fun activate(account: SavedAccount, refresh: Boolean = true) {
        resetData()
        isDemo = false
        defaults.edit().remove(demoKey).apply()
        activeAccountID = account.id
        saveActiveID(account.id)
        loginName = account.name
        val c = ClassevivaClient(HttpTransport(HttpTransport.OFFICIAL), "live-${account.id}", account.credentials)
        client = c
        timetable.load(account.id)
        if (cloud != null) sync.pullTimetable(account.id)
        phase = Phase.signedIn
        scope.launch {
            loadFromCache(c)
            if (refresh) refreshAll()
        }
        hooks?.dataChanged()
    }

    /** Apre la demo, anche con un account collegato (uscendo si torna a quello). */
    fun startDemo() {
        if (isDemo) return
        changeSession { beginDemo() }
    }

    private fun beginDemo(refresh: Boolean = true) {
        resetData()
        isDemo = true
        defaults.edit().putBoolean(demoKey, true).apply()
        client = ClassevivaClient(DemoTransport(), "demo", Credentials("demo", "demo", null))
        timetable.load("demo")
        phase = Phase.signedIn
        if (refresh) scope.launch { refreshAll() }
        hooks?.dataChanged()
    }

    /** Chiude la demo e cancella i suoi dati (cache e orario). */
    private fun leaveDemo() {
        resetData()
        DiskCache.remove("demo")
        timetable.removeData("demo")
        timetable.load(null)
        defaults.edit().remove(demoKey).apply()
        isDemo = false
    }

    /** L'account a cui si torna uscendo dalla demo. */
    val accountAfterDemo: SavedAccount? get() = activeAccount ?: accounts.firstOrNull()

    val demoExitMessage: String
        get() = accountAfterDemo?.let { "Stai usando la modalità demo. Uscendo tornerai a ${it.name}." } ?: "Stai usando la modalità demo."

    /** Esce dall'account attivo (o dalla demo): passa al successivo o torna all'accesso. */
    fun signOut() {
        if (cloud != null && !isDemo) {
            scope.launch { cloudSignOut() }
            return
        }
        if (isDemo) {
            val next = accountAfterDemo
            if (next != null) {
                changeSession {
                    leaveDemo()
                    activate(next)
                }
            } else {
                leaveDemo()
                endSession()
            }
            return
        }
        val id = activeAccountID
        if (id != null) removeAccount(id) else endSession()
    }

    /** Rimuove un account salvato e la sua cache. */
    fun removeAccount(id: String) {
        val account = accounts.firstOrNull { it.id == id } ?: return
        val wasActive = id == activeAccountID && !isDemo
        removeCaches(account.id)
        if (cloud != null) {
            // L'orario di un account abbinato è sul server: su questo dispositivo se ne va con l'account.
            timetable.removeData(account.id)
            sync.forgetTimetable(account.id)
        }
        accounts = accounts.filter { it.id != id }
        saveAccounts()
        if (!wasActive) return
        val next = accounts.firstOrNull()
        if (next != null) changeSession { activate(next) } else endSession()
    }

    private fun endSession() {
        resetData()
        timetable.load(null)
        activeAccountID = null
        saveActiveID(null)
        hooks?.rescheduleReminders(emptyList())
        phase = Phase.signedOut
        hooks?.dataChanged()
    }

    private fun resetData() {
        client?.signOut()
        client = null
        archives = HashMap()
        resetFields()
    }

    /** Aggiorna nome e scuola dell'account salvato con i dati della scheda. */
    private fun updateActiveAccount(card: Card) {
        if (isDemo) return
        val account = activeAccount ?: return
        val name = card.fullName.trim()
        val school = card.schoolDescription
        if (!((name.isNotEmpty() && account.name != name) || account.school != school)) return
        val updated = account.copy(name = name.ifEmpty { account.name }, school = school)
        accounts = accounts.map { if (it.id == account.id) updated else it }
        saveAccounts()
        val session = cloud ?: return
        scope.launch {
            runCatching { CloudAPI.json("PATCH", "/accounts/${account.id}", session.token, obj("name" to updated.name, "school" to school)) }
        }
    }

    val credentialsForArchive: Credentials? get() = client?.credentials

    /** Proprietario della cache della sessione attuale. */
    val cacheOwner: String get() = if (isDemo) "demo" else (activeAccountID ?: "live")

    /** Archivio di un anno passato per la sessione attuale (azzerato cambiando sessione). */
    fun archive(startYear: Int): ArchiveModel = archives.getOrPut(startYear) { ArchiveModel(startYear, cacheOwner) }

    fun reloadArchive(startYear: Int): ArchiveModel {
        archives.remove(startYear)
        DiskCache.remove(ArchiveModel.cacheNamespace(cacheOwner, startYear))
        return archive(startYear)
    }

    private fun removeCaches(owner: String) {
        DiskCache.remove(if (owner == "demo") "demo" else "live-$owner")
        for (year in ArchiveModel.availableStartYears) DiskCache.remove(ArchiveModel.cacheNamespace(owner, year))
    }

    // MARK: Account BCW

    val isCloud: Boolean get() = cloud != null
    val cloudUser: BCWUser? get() = cloud?.user

    /** Accede (o crea l'account) e passa agli account Classeviva abbinati. Restituisce quelli locali da abbinare. */
    suspend fun cloudSignIn(username: String, password: String, register: Boolean = false): List<SavedAccount> {
        val response = CloudAPI.json("POST", if (register) "/auth/register" else "/auth/login",
            body = obj("username" to username.trim(), "password" to password, "client" to CloudAPI.CLIENT_NAME))
        val session = parseSession(response) ?: throw CloudError("decoding", "Risposta inattesa dal server BCW.")
        return enterCloud(session)
    }

    private suspend fun enterCloud(session: BCWSession): List<SavedAccount> {
        val remote = CloudAPI.json("GET", "/accounts", session.token)
        val list = (remote["accounts"] as? JsonArray)?.mapNotNull { accountFromServer(it as? JsonObject) } ?: emptyList()
        val local = if (cloud != null) emptyList() else readAccounts(localAccountsKey)
        val candidates = local.filter { l -> list.none { sameProfile(it, l) } }
        cloudNotice = null
        cloudRefreshedAt = System.currentTimeMillis()
        changeSession {
            if (isDemo) leaveDemo()
            sync.reset()
            cloud = session
            secure.put(sessionKey, session.toJson().toString())
            accounts = list
            saveAccounts()
            val first = list.firstOrNull()
            if (first != null) activate(first) else endSession()
            sync.pullSettings()
        }
        return candidates
    }

    private suspend fun linkOnServer(credentials: Credentials): SavedAccount {
        val session = cloud ?: throw CloudError("unauthorized", "Accedi all'account BCW.")
        try {
            val response = CloudAPI.json("POST", "/accounts", session.token,
                obj("username" to credentials.username, "password" to credentials.password, "ident" to credentials.ident))
            return accountFromServer(response["account"] as? JsonObject) ?: throw CloudError("decoding", "Risposta inattesa dal server BCW.")
        } catch (e: CloudError) {
            if (e.isUnauthorized) cloudSessionExpired()
            throw e
        }
    }

    /** Aggiunge o aggiorna un account abbinato (stesso id del server). */
    private fun upsertCloudAccount(remote: SavedAccount): SavedAccount {
        val existing = accounts.firstOrNull { it.id == remote.id }
        if (existing == null) {
            accounts = accounts + remote
            return remote
        }
        val updated = existing.copy(credentials = remote.credentials, name = remote.name.ifEmpty { existing.name }, school = remote.school ?: existing.school)
        accounts = accounts.map { if (it.id == remote.id) updated else it }
        return updated
    }

    /** Abbina all'account BCW gli account del dispositivo, copiandone l'orario. Restituisce gli errori. */
    suspend fun linkLocalAccounts(locals: List<SavedAccount>): List<Pair<SavedAccount, String>> {
        val failures = mutableListOf<Pair<SavedAccount, String>>()
        for (local in locals) {
            if (cloud == null) break
            try {
                val account = upsertCloudAccount(linkOnServer(local.credentials))
                saveAccounts()
                timetable.copyData(local.id, account.id)
                sync.pullTimetable(account.id)
            } catch (e: Exception) {
                if (cloud == null) break
                failures += local to (e.message ?: "Errore sconosciuto.")
            }
        }
        val first = accounts.firstOrNull()
        if (cloud != null && phase == Phase.signedOut && first != null) activate(first)
        return failures
    }

    /** Aggiorna gli account abbinati e sincronizza impostazioni e orario dell'account attivo (al massimo ogni 30 s). */
    suspend fun refreshCloud(force: Boolean = false) {
        val session = cloud ?: return
        if (cloudRefreshing) return
        if (!force && System.currentTimeMillis() - cloudRefreshedAt < 30_000) return
        cloudRefreshing = true
        try {
            val remote = CloudAPI.json("GET", "/accounts", session.token)
            if (cloud != session) return
            cloudRefreshedAt = System.currentTimeMillis()
            applyRemoteAccounts((remote["accounts"] as? JsonArray)?.mapNotNull { accountFromServer(it as? JsonObject) } ?: emptyList())
        } catch (e: CloudError) {
            if (cloud != session) return
            if (e.isUnauthorized) cloudSessionExpired()
            return
        } catch (e: Exception) {
            return
        } finally {
            cloudRefreshing = false
        }
        sync.pullSettings()
        val active = activeAccount
        if (active != null && !isDemo) sync.pullTimetable(active.id)
    }

    /** Allinea gli account abbinati a quelli del server (aggiunti o tolti da un altro dispositivo). */
    private fun applyRemoteAccounts(list: List<SavedAccount>) {
        val active = if (isDemo) null else activeAccount
        val removed = accounts.filter { a -> list.none { it.id == a.id } }
        val next = list.map { r ->
            val existing = accounts.firstOrNull { it.id == r.id } ?: return@map r
            existing.copy(credentials = r.credentials, name = r.name.ifEmpty { existing.name }, school = r.school ?: existing.school)
        }
        accounts = next
        saveAccounts()
        for (account in removed) {
            removeCaches(account.id)
            timetable.removeData(account.id)
            sync.forgetTimetable(account.id)
        }
        // Password cambiata da un altro dispositivo: il prossimo accesso usa quella nuova.
        val fresh = active?.let { a -> next.firstOrNull { it.id == a.id } }
        if (fresh != null && client?.credentials != null) client?.credentials = fresh.credentials
        if (isDemo) return
        if (active != null && fresh == null) {
            val first = next.firstOrNull()
            if (first != null) changeSession { activate(first) } else endSession()
        } else if (phase == Phase.signedOut && next.isNotEmpty()) {
            activate(next[0])
        }
    }

    /** Scollega un account Classeviva dall'account BCW (con il suo orario sul server). */
    suspend fun unlinkAccount(id: String) {
        val session = cloud
        if (session == null) {
            removeAccount(id)
            return
        }
        try {
            CloudAPI.request("DELETE", "/accounts/$id", session.token)
        } catch (e: CloudError) {
            if (e.isUnauthorized) {
                cloudSessionExpired()
                return
            }
            if (e.code != "not_found") throw e
        }
        removeAccount(id)
    }

    /** Esce dall'account BCW: account abbinati, cache e orari spariscono da questo dispositivo. */
    suspend fun cloudSignOut() {
        val session = cloud ?: return
        // Le ultime modifiche in attesa partono prima di uscire (al massimo 3 secondi).
        withTimeoutOrNull(3000) { sync.flush() }
        scope.launch { runCatching { CloudAPI.request("POST", "/auth/logout", session.token) } }
        leaveCloud()
    }

    fun cloudSessionExpired() {
        if (cloud == null) return
        leaveCloud("La sessione dell'account BCW è scaduta o è stata chiusa: accedi di nuovo.")
    }

    private fun leaveCloud(notice: String? = null) {
        if (cloud == null) return
        val cloudAccounts = accounts
        cloudNotice = notice
        lastCloudUsername = cloud?.user?.username
        changeSession {
            if (cloud == null) return@changeSession
            if (isDemo) leaveDemo() else resetData()
            for (account in cloudAccounts) {
                removeCaches(account.id)
                timetable.removeData(account.id)
            }
            secure.put(sessionKey, null)
            secure.put(cloudAccountsKey, null)
            defaults.edit().remove(cloudActiveKey).apply()
            sync.reset()
            cloud = null
            accounts = readAccounts(localAccountsKey)
            val storedID = defaults.getString(activeKey, null)
            val next = accounts.firstOrNull { it.id == storedID } ?: accounts.firstOrNull()
            if (next != null) activate(next) else endSession()
        }
    }

    suspend fun changeCloudPassword(current: String, new: String) {
        val session = cloud ?: return
        val response = CloudAPI.json("PUT", "/me/password", session.token,
            obj("currentPassword" to current, "newPassword" to new, "client" to CloudAPI.CLIENT_NAME))
        val token = response.str("token") ?: return
        val updated = session.copy(token = token)
        cloud = updated
        secure.put(sessionKey, updated.toJson().toString())
    }

    suspend fun deleteCloudAccount(password: String) {
        val session = cloud ?: return
        CloudAPI.request("DELETE", "/me", session.token, obj("password" to password))
        leaveCloud()
    }

    /** Le impostazioni sono cambiate da un altro dispositivo. */
    fun settingsApplied() {
        gradeBookCache = null
        if (phase == Phase.signedIn) rescheduleReminders()
        hooks?.dataChanged()
    }

    // MARK: Lato Classeviva

    private suspend fun classevivaHeaders(): Map<String, String> {
        val c = client
        if (c == null || isDemo) throw CloudError("demo", "Non disponibile nella demo.")
        c.ensureToken()
        return mapOf("X-Classeviva-Ident" to (c.ident ?: ""), "X-Classeviva-Token" to (c.token ?: ""))
    }

    /** Gli account BCW a cui è abbinato il profilo Classeviva attivo. */
    suspend fun classevivaLinks(): List<BCWLink> {
        val response = CloudAPI.json("GET", "/classeviva/links", headers = classevivaHeaders())
        return (response["links"] as? JsonArray)?.mapNotNull { l ->
            val o = l as? JsonObject ?: return@mapNotNull null
            BCWLink(o.str("id") ?: return@mapNotNull null, o.str("username") ?: "", (o["linkedAt"] as? JsonPrimitive)?.content?.toDoubleOrNull()?.toLong() ?: 0)
        } ?: emptyList()
    }

    /** Scollega il profilo attivo da un account BCW (`null` = da tutti). */
    suspend fun removeClassevivaLink(id: String?) {
        val headers = classevivaHeaders()
        CloudAPI.request("DELETE", if (id != null) "/classeviva/links/$id" else "/classeviva/links", headers = headers)
        if (cloud != null) refreshCloud(force = true)
    }

    // MARK: Caricamento

    private val agendaPath: String
        get() {
            val year = Dates.schoolYear()
            return "agenda/all/${Dates.apiString(year.start)}/${Dates.apiString(year.end)}"
        }

    private fun sortedNotices(items: List<Notice>) = items.filter { !it.isDeleted }.sortedByDescending { it.publishedAt ?: LocalDateTime.MIN }

    /** Mostra subito gli ultimi dati salvati, prima che arrivi la risposta di rete. */
    private suspend fun loadFromCache(c: ClassevivaClient) {
        val cardC = c.cached(::parseCardResponse, "card")
        val periodsC = c.cached(::parsePeriodsResponse, "periods")
        val subjectsC = c.cached(::parseSubjectsResponse, "subjects")
        val agendaC = c.cached(::parseAgendaResponse, agendaPath)
        val absencesC = c.cached(::parseAbsencesResponse, "absences/details")
        val noticesC = c.cached(::parseNoticeboardResponse, "noticeboard")
        val notesC = c.cached(::parseNotesResponse, "notes/all")
        val calendarC = c.cached(::parseCalendarResponse, "calendar/all")
        var gradesC: List<Grade>? = null
        for (path in gradePaths) {
            gradesC = c.cached(::parseGradesResponse, path)
            if (gradesC != null) break
        }
        if (c !== client) return
        // I dati di rete arrivati nel frattempo hanno la precedenza.
        if (lastUpdated == null) {
            if (card == null) card = cardC
            if (grades.isEmpty() && gradesC != null) grades = gradesC.sortedByDescending { it.date }
            if (periods.isEmpty() && periodsC != null) periods = periodsC
            if (subjects.isEmpty() && subjectsC != null) subjects = subjectsC
            if (agenda.isEmpty() && agendaC != null) agenda = agendaC.sortedBy { it.begin }
            if (absences.isEmpty() && absencesC != null) absences = absencesC.sortedByDescending { it.date }
            if (notices.isEmpty() && noticesC != null) notices = sortedNotices(noticesC)
            if (notes.isEmpty() && notesC != null) notes = notesC
            if (calendarDays.isEmpty() && calendarC != null) calendarDays = calendarC
            gradeBookCache = null
        }
        hooks?.dataChanged()
    }

    private var refreshJob: Job? = null

    /** Aggiorna tutti i dati principali (un aggiornamento alla volta per client). */
    suspend fun refreshAll() {
        val c = client ?: return
        if (refreshingClient === c) {
            refreshJob?.join()
            return
        }
        if (cloud != null) scope.launch { refreshCloud() }
        refreshingClient = c
        isRefreshing = true
        lastError = null
        val job = scope.launch {
            try {
                try {
                    if (c.token == null) c.login()
                } catch (e: APIError) {
                    if (c !== client) return@launch
                    lastError = when {
                        e.isOffline -> { isOffline = true; OFFLINE_MESSAGE }
                        e is APIError.WrongCredentials -> "Le credenziali di $displayName non sono più valide. Accedi di nuovo da Tu › Gestisci account › Aggiungi account."
                        else -> e.message
                    }
                    return@launch
                } catch (e: Exception) {
                    if (c !== client) return@launch
                    lastError = e.message
                    return@launch
                }
                listOf(
                    async { loadCard() }, async { loadGrades() }, async { loadAgenda() }, async { loadAbsences() },
                    async { loadNotices() }, async { loadNotes() }, async { loadCalendar() },
                ).awaitAll()
                if (c !== client) return@launch
                lessonsByDay = emptyMap()
                loadedLessonDays = emptySet()
                lastUpdated = LocalDateTime.now()
                if (!isDemo) News.process(this@AppModel, defaults)
            } finally {
                if (refreshingClient === c) {
                    refreshingClient = null
                    isRefreshing = false
                }
                gradeBookCache = null
                hooks?.dataChanged()
            }
        }
        refreshJob = job
        job.join()
    }

    /**
     * Scarica un endpoint e applica il risultato. Gli errori delle sezioni principali finiscono nel
     * banner globale; quelli delle sezioni secondarie (`global = false`) vengono solo restituiti.
     */
    private suspend fun <T> run(parse: (JsonElement) -> T, path: String, method: String = "GET", global: Boolean = true, apply: (T) -> Unit): String? {
        val c = client ?: return null
        return try {
            val result = c.fetch(parse, path, method = method)
            if (c !== client) return null
            apply(result.value)
            if (global) {
                if (result.fromCache) {
                    isOffline = true
                    lastError = OFFLINE_MESSAGE
                } else isOffline = false
            }
            gradeBookCache = null
            null
        } catch (e: Exception) {
            if (c !== client) return null
            if (global && lastError == null) lastError = e.message
            e.message
        }
    }

    suspend fun loadCard() = run(::parseCardResponse, "card") { r ->
        card = r
        if (r != null) updateActiveAccount(r)
    }

    suspend fun loadGrades() {
        scope.async {
            listOf(
                async { run(::parsePeriodsResponse, "periods") { periods = it } },
                async { run(::parseSubjectsResponse, "subjects") { subjects = it } },
                async { loadGradeList() },
            ).awaitAll()
        }.await()
    }

    private suspend fun loadGradeList() {
        val c = client ?: return
        try {
            val result = c.fetchFirst(::parseGradesResponse, gradePaths) { it.isNotEmpty() }
            if (c !== client) return
            grades = result.value.sortedByDescending { it.date }
            if (result.fromCache) isOffline = true
        } catch (e: Exception) {
            if (c !== client) return
            if (lastError == null) lastError = e.message
        }
        gradeBookCache = null
    }

    suspend fun loadAgenda() {
        run(::parseAgendaResponse, agendaPath) { list -> agenda = list.sortedBy { it.begin } }
        rescheduleReminders()
    }

    suspend fun loadAbsences() = run(::parseAbsencesResponse, "absences/details") { list -> absences = list.sortedByDescending { it.date } }
    suspend fun loadNotices() = run(::parseNoticeboardResponse, "noticeboard") { notices = sortedNotices(it) }
    suspend fun loadNotes() = run(::parseNotesResponse, "notes/all") { notes = it }
    suspend fun loadDidactics() = run(::parseDidacticsResponse, "didactics", global = false) { didactics = it }
    suspend fun loadDocuments() = run(::parseDocumentsResponse, "documents", method = "POST", global = false) { documents = it }
    suspend fun loadCalendar() = run(::parseCalendarResponse, "calendar/all") { calendarDays = it }
    suspend fun loadSchoolbooks() = run(::parseSchoolbooksResponse, "schoolbooks", global = false) { schoolbooks = it }

    fun rescheduleReminders() {
        val p = preferences
        hooks?.rescheduleReminders(if (p.homeworkReminders) Reminders.plan(agenda, p.reminderHour, p.completed) else emptyList())
    }

    // MARK: Lezioni

    fun lessons(day: LocalDate): List<Lesson> = lessonsByDay[day] ?: emptyList()
    fun hasLoadedLessons(day: LocalDate) = day in loadedLessonDays

    /** Scarica le lezioni dei giorni non ancora caricati nell'intervallo (solo giorni passati e oggi). */
    suspend fun ensureLessons(from: LocalDate, to: LocalDate) {
        val c = client ?: return
        val today = Dates.today()
        val end = if (to < today) to else today
        var start = from
        while (!start.isAfter(end) && start in loadedLessonDays) start = start.plusDays(1)
        if (start.isAfter(end)) return
        val path = "lessons/${Dates.apiString(start)}/${Dates.apiString(end)}"
        try {
            val result = c.fetch(::parseLessonsResponse, path)
            if (c !== client) return
            val map = HashMap(lessonsByDay)
            val loaded = HashSet(loadedLessonDays)
            var day = start
            while (!day.isAfter(end)) {
                loaded += day
                map[day] = emptyList()
                day = day.plusDays(1)
            }
            for ((d, list) in result.value.groupBy { it.date }) map[d] = (map[d].orEmpty() + list).sortedBy { it.hour }
            lessonsByDay = map
            loadedLessonDays = loaded
        } catch (e: Exception) {
            // Le lezioni non sono essenziali: in caso di errore restano vuote.
        }
    }

    /** Lezioni di un intervallo, senza salvarle (registro per materia, generatore dell'orario). */
    suspend fun fetchLessons(from: LocalDate, to: LocalDate): List<Lesson> {
        val c = client ?: return emptyList()
        return c.fetch(::parseLessonsResponse, "lessons/${Dates.apiString(from)}/${Dates.apiString(to)}").value
    }

    // MARK: Bacheca

    /** Apre una comunicazione (segnandola come letta) ed eventualmente aderisce, firma o risponde. */
    suspend fun openNotice(notice: Notice, join: Boolean? = null, sign: Boolean? = null, text: String? = null): NoticeDetail {
        val c = client ?: throw APIError.NotAuthenticated
        // Per la sola lettura si invia un corpo vuoto: `{}` viene rifiutato dal server.
        val pairs = buildList {
            if (join != null) add("join" to join)
            if (sign != null) add("sign" to sign)
            if (text != null) add("text" to text)
        }
        val body = if (pairs.isEmpty()) null else jsonBody(*pairs.toTypedArray())
        val detail = c.fetch(::parseNoticeDetail, "noticeboard/read/${notice.eventCode}/${notice.pubId}/101", method = "POST", body = body, useCache = false).value
        if (notices.any { it.id == notice.id && !it.isRead }) notices = notices.map { if (it.id == notice.id) it.copy(isRead = true) else it }
        return detail
    }

    suspend fun downloadAttachment(attachment: NoticeAttachment, notice: Notice): DownloadedFile {
        val c = client ?: throw APIError.NotAuthenticated
        // Classeviva risponde "item must first be read" se la comunicazione non è stata aperta.
        if (notices.firstOrNull { it.id == notice.id }?.isRead != true) openNotice(notice)
        return c.download("noticeboard/attach/${notice.eventCode}/${notice.pubId}/${attachment.number}", suggestedName = attachment.fileName)
    }

    val unreadNoticesCount: Int get() = notices.count { !it.isRead }

    // MARK: Note

    private val readingNotes = HashSet<String>()

    suspend fun readNote(note: Note) {
        val c = client ?: return
        val key = "${note.category.code}-${note.id}"
        if (note.isRead || !readingNotes.add(key)) return
        try {
            val text = c.fetch(::parseNoteReadText, "notes/${note.category.code}/read/${note.id}", method = "POST", useCache = false).value
            notes = notes.map { if (it.id == note.id && it.category == note.category) it.copy(isRead = true, text = text ?: it.text) else it }
        } catch (_: Exception) {
            // Riprova alla prossima apertura.
        } finally {
            readingNotes.remove(key)
        }
    }

    val unreadNotesCount: Int get() = notes.count { !it.isRead }

    // MARK: Didattica

    sealed class DidacticItem {
        data class Link(val url: String) : DidacticItem()
        data class Text(val text: String) : DidacticItem()
        data class File(val file: DownloadedFile) : DidacticItem()
    }

    suspend fun openDidacticContent(content: DidacticContent): DidacticItem {
        val c = client ?: throw APIError.NotAuthenticated
        val result = c.raw("GET", "didactics/item/${content.id}")
        if (result.isJson) {
            val json = parseJson(result.text) as? JsonObject
            if (json != null) {
                val item = json["item"] as? JsonObject ?: json
                item.str("link")?.let { return DidacticItem.Link(if (it.startsWith("http")) it else "https://$it") }
                item.str("text")?.let { return DidacticItem.Text(it) }
            }
        }
        return DidacticItem.File(makeFile(result, content.name))
    }

    // MARK: Documenti

    suspend fun downloadDocument(document: SchoolDocument): DownloadedFile {
        val c = client ?: throw APIError.NotAuthenticated
        val available = c.fetch(::parseDocumentAvailable, "documents/check/${document.documentHash}", method = "POST", useCache = false).value
        if (!available) throw APIError.Unavailable("Il documento non è ancora disponibile.")
        return c.download("documents/read/${document.documentHash}", method = "POST", suggestedName = "${document.title}.pdf")
    }

    // MARK: Dati derivati

    private var gradeBookCache: GradeBook? = null

    val gradeBook: GradeBook
        get() {
            // Legge lo stato osservato, così chi lo usa si aggiorna.
            val g = grades
            val p = periods
            val mode = preferences.averageMode
            val weighted = preferences.weightedAverage
            val cached = gradeBookCache
            if (cached != null && cached.grades === g && cached.periods === p && cached.mode == mode && cached.weighted == weighted) return cached
            return GradeBook(g, p, mode, weighted).also { gradeBookCache = it }
        }

    val displayName: String get() = card?.fullName ?: loginName ?: if (isDemo) "Giulia Esposito" else "Studente"

    val initials: String get() = card?.initials?.ifEmpty { null } ?: displayName.take(1).uppercase()

    /** Classe dello studente, ricavata da agenda e lezioni (la scheda non la include). */
    val classDescription: String?
        get() = agenda.firstNotNullOfOrNull { it.classDescription } ?: lessonsByDay.values.flatten().firstNotNullOfOrNull { it.classDescription }

    private var eventsIndex: Pair<List<AgendaEvent>, Map<LocalDate, List<AgendaEvent>>>? = null
    private var absencesIndex: Pair<List<Absence>, Map<LocalDate, List<Absence>>>? = null
    private var calendarIndex: Pair<List<CalendarDay>, Map<LocalDate, CalendarDay>>? = null

    fun events(day: LocalDate): List<AgendaEvent> {
        val list = agenda
        val index = eventsIndex?.takeIf { it.first === list } ?: (list to list.groupBy { it.day }).also { eventsIndex = it }
        return index.second[day] ?: emptyList()
    }

    fun absencesOn(day: LocalDate): List<Absence> {
        val list = absences
        val index = absencesIndex?.takeIf { it.first === list } ?: (list to list.groupBy { it.date }).also { absencesIndex = it }
        return index.second[day] ?: emptyList()
    }

    fun calendarStatus(day: LocalDate): CalendarDay? {
        val list = calendarDays
        val index = calendarIndex?.takeIf { it.first === list } ?: (list to list.associateBy { it.date }).also { calendarIndex = it }
        return index.second[day]
    }

    val unjustifiedAbsences: List<Absence> get() = absences.filter { !it.isJustified }

    /** Tutte le materie dell'anno, più quelle che compaiono solo nei voti. */
    fun allSubjectNames(): List<Pair<Long, String>> {
        val names = LinkedHashMap<Long, String>()
        for (s in subjects) if (s.id != 0L && s.name.isNotEmpty()) names[s.id] = s.name
        for (g in grades) if (!names.containsKey(g.subjectId)) names[g.subjectId] = g.subjectName
        return names.entries.map { it.key to it.value }.sortedWith { a, b -> com.bcw.classeviva.util.byName(a.second, b.second) }
    }

    // MARK: Collegamenti

    /** `bcw://orario?giorno=AAAA-MM-GG`, `bcw://dashboard?giorno=…`, `bcw://voti`. */
    fun open(uri: android.net.Uri) {
        if (uri.scheme != "bcw") return
        val day = uri.getQueryParameter("giorno")?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        val tab = when (uri.host) {
            "orario" -> "timetable"
            "dashboard" -> "dashboard"
            "voti" -> "grades"
            "tu" -> "you"
            else -> return
        }
        openedLink = OpenedLink(tab, day)
    }

    /** File temporaneo per l'anteprima o la condivisione. */
    fun temporaryFile(name: String): File {
        val dir = File(appContext.cacheDir, "shared").apply { mkdirs() }
        return File(dir, name.replace('/', '-'))
    }
}

/**
 * Pagelle e documenti di un anno scolastico passato. Classeviva archivia gli anni passati su
 * `webYY.spaggiari.eu`: lì funzionano solo i documenti; il resto si consulta sul sito.
 */
class ArchiveModel(val startYear: Int, val owner: String) {
    enum class State { idle, loading, loaded, failed }

    var state by mutableStateOf(State.idle)
        private set
    var failure by mutableStateOf<String?>(null)
        private set
    var documents by mutableStateOf<List<SchoolDocument>>(emptyList())
        private set
    var documentsError by mutableStateOf<String?>(null)
        private set
    private var client: ClassevivaClient? = null

    companion object {
        val previousStartYear: Int get() = Dates.schoolYear().startYear - 1
        val availableStartYears: List<Int> get() = (0..4).map { previousStartYear - it }
        fun title(startYear: Int) = "$startYear/${(startYear + 1).toString().takeLast(2)}"
        fun cacheNamespace(owner: String, startYear: Int) = "archive-$owner-$startYear"
        const val WEB_URL = "https://web.spaggiari.eu/home/app/default/login.php"
    }

    val title: String get() = title(startYear)

    suspend fun load(credentials: Credentials?, demo: Boolean) {
        if (state == State.loaded || state == State.loading) return
        state = State.loading
        documentsError = null
        val transport: Transport = if (demo) DemoTransport(startYear - Dates.schoolYear().startYear) else HttpTransport(HttpTransport.archive(startYear))
        // Per l'archivio non si riusa subito l'ident: lo studente potrebbe avere un codice diverso.
        val c = ClassevivaClient(transport, cacheNamespace(owner, startYear), credentials?.copy(ident = null))
        client = c
        try {
            login(c, credentials?.ident)
        } catch (e: APIError) {
            fail(when {
                e is APIError.WrongCredentials -> "Classeviva non ha accettato le credenziali per l'archivio $title. Se hai cambiato password dopo la fine dell'anno, nell'archivio potrebbe valere ancora quella vecchia."
                e is APIError.Decoding -> "Per l'anno $title Classeviva non permette di scaricare le pagelle dall'app."
                e.isOffline -> "L'archivio $title non è raggiungibile."
                else -> e.message ?: "Errore sconosciuto."
            })
            return
        } catch (e: Exception) {
            fail(e.message ?: "Errore sconosciuto.")
            return
        }
        try {
            documents = c.fetch(::parseDocumentsResponse, "documents", method = "POST").value.documents
        } catch (e: Exception) {
            documentsError = e.message
        }
        state = State.loaded
    }

    private fun fail(message: String) {
        state = State.failed
        failure = message
    }

    /** Per gli account con più profili sceglie quello dell'account attivo; se l'accesso senza profilo fallisce, riprova con quello. */
    private suspend fun login(c: ClassevivaClient, mainIdent: String?) {
        try {
            c.login()
        } catch (e: APIError.NeedsProfileChoice) {
            val digits = mainIdent?.filter { it.isDigit() }
            val choice = e.choices.firstOrNull { it.ident == mainIdent }
                ?: e.choices.firstOrNull { !digits.isNullOrEmpty() && it.ident.filter { ch -> ch.isDigit() } == digits }
                ?: e.choices.firstOrNull() ?: throw APIError.NotAuthenticated
            c.credentials = c.credentials?.copy(ident = choice.ident)
            c.login()
        } catch (e: APIError.WrongCredentials) {
            if (mainIdent == null) throw e
            c.credentials = c.credentials?.copy(ident = mainIdent)
            c.login()
        }
    }

    suspend fun downloadDocument(document: SchoolDocument): DownloadedFile {
        val c = client ?: throw APIError.NotAuthenticated
        return c.download("documents/read/${document.documentHash}", method = "POST", suggestedName = "${document.title}.pdf")
    }
}
