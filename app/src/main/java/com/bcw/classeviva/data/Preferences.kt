package com.bcw.classeviva.data

import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.bcw.classeviva.util.parseJson
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull

enum class DashboardMode(val title: String, val icon: String) {
    list("Lista", "viewAgenda"),
    calendar("Calendario", "calendarMonth"),
}

enum class AppearanceMode(val title: String) { system("Automatico"), light("Chiaro"), dark("Scuro") }

enum class TitleAbbreviation(val title: String) { automatic("Automatica"), always("Sempre"), never("Mai") }

/** Tipi di novità che si possono notificare (Impostazioni › Notifiche › Novità). */
enum class NewsKind(val title: String, val icon: String) {
    grades("Nuovi voti", "graduationCap"),
    notices("Nuove comunicazioni", "megaphone"),
    notes("Note e annotazioni", "messageAlert"),
    absences("Assenze e ritardi", "userClock"),
    agenda("Nuovi compiti e verifiche", "checklist"),
}

const val DEFAULT_ACCENT = "#a23e2f"

/**
 * Impostazioni dell'app: un documento JSON con le stesse chiavi del web e dell'app iOS, così si
 * sincronizzano con l'account BCW. I valori sono stato di Compose: le viste si aggiornano da sole.
 */
class Preferences(private val store: SharedPreferences) {
    companion object {
        private const val KEY = "bcw.preferences"
        val defaults: Map<String, JsonElement> = mapOf(
            "averageMode" to JsonPrimitive("allGrades"),
            "weightedAverage" to JsonPrimitive(false),
            "targetAverage" to JsonPrimitive(7),
            "useLock" to JsonPrimitive(false),
            "homeworkReminders" to JsonPrimitive(false),
            "reminderHour" to JsonPrimitive(18),
            "dashboardMode" to JsonPrimitive("list"),
            "appearance" to JsonPrimitive("system"),
            "accentColor" to JsonPrimitive(DEFAULT_ACCENT),
            "colorStyle" to JsonPrimitive("tonalSpot"),
            "contrast" to JsonPrimitive("standard"),
            "corners" to JsonPrimitive("standard"),
            "motion" to JsonPrimitive("expressive"),
            "roundedText" to JsonPrimitive("titles"),
            "hideCompletedHomework" to JsonPrimitive(false),
            "showUpcomingDays" to JsonPrimitive(true),
            "collapsedSections" to JsonArray(emptyList()),
            "completedHomework" to JsonArray(emptyList()),
            "timetableMode" to JsonPrimitive("list"),
            "timetableShowsTests" to JsonPrimitive(true),
            "timetableShowsNowCard" to JsonPrimitive(true),
            "showsTimetableTab" to JsonPrimitive(true),
            "newsNotifications" to JsonArray(emptyList()),
            "unsyncedCategories" to JsonArray(emptyList()),
            "titleAbbreviation" to JsonPrimitive("automatic"),
            // Solo su Android, solo su questo dispositivo.
            "dynamicColor" to JsonPrimitive(false),
            "liveUpdates" to JsonPrimitive(true),
        )
        val appearanceKeys = listOf("accentColor", "colorStyle", "contrast", "corners", "motion", "roundedText")
    }

    var values: Map<String, JsonElement> by mutableStateOf(defaults)
        private set

    /** Avvisa la sincronizzazione (non durante `applyRemote`). */
    var onPersist: (() -> Unit)? = null
    var onChange: (() -> Unit)? = null

    init {
        val stored = (parseJson(store.getString(KEY, null) ?: "{}") as? JsonObject) ?: JsonObject(emptyMap())
        values = defaults + stored
    }

    operator fun get(key: String): JsonElement? = values[key]

    fun string(key: String): String = (values[key] as? JsonPrimitive)?.content ?: (defaults[key] as? JsonPrimitive)?.content ?: ""
    fun bool(key: String): Boolean = (values[key] as? JsonPrimitive)?.booleanOrNull ?: (defaults[key] as? JsonPrimitive)?.booleanOrNull ?: false
    fun double(key: String): Double = (values[key] as? JsonPrimitive)?.doubleOrNull ?: (defaults[key] as? JsonPrimitive)?.doubleOrNull ?: 0.0
    fun int(key: String): Int = double(key).toInt()
    fun strings(key: String): List<String> = (values[key] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.content } ?: emptyList()
    fun longs(key: String): List<Long> = (values[key] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.longOrNull } ?: emptyList()

    fun set(key: String, value: JsonElement) {
        if (values[key] == value) return
        values = values + (key to value)
        save()
    }

    fun set(key: String, value: String) = set(key, JsonPrimitive(value))
    fun set(key: String, value: Boolean) = set(key, JsonPrimitive(value))
    fun set(key: String, value: Number) = set(key, JsonPrimitive(value))
    fun setStrings(key: String, list: List<String>) = set(key, JsonArray(list.map { JsonPrimitive(it) }))
    fun setLongs(key: String, list: List<Long>) = set(key, JsonArray(list.map { JsonPrimitive(it) }))

    private fun persist() {
        store.edit().putString(KEY, JsonObject(values).toString()).apply()
    }

    private fun save() {
        persist()
        onPersist?.invoke()
        onChange?.invoke()
    }

    /** Valori arrivati dall'account BCW (senza avvisare `onPersist`). */
    fun applyRemote(remote: Map<String, JsonElement>) {
        if (remote.isEmpty()) return
        values = values + remote
        persist()
        onChange?.invoke()
    }

    // MARK: Valori

    val averageMode: AverageMode get() = runCatching { AverageMode.valueOf(string("averageMode")) }.getOrDefault(AverageMode.allGrades)
    val weightedAverage: Boolean get() = bool("weightedAverage")
    val targetAverage: Double get() = double("targetAverage")
    val dashboardMode: DashboardMode get() = runCatching { DashboardMode.valueOf(string("dashboardMode")) }.getOrDefault(DashboardMode.list)
    val appearance: AppearanceMode get() = runCatching { AppearanceMode.valueOf(string("appearance")) }.getOrDefault(AppearanceMode.system)
    val titleAbbreviation: TitleAbbreviation get() = runCatching { TitleAbbreviation.valueOf(string("titleAbbreviation")) }.getOrDefault(TitleAbbreviation.automatic)
    val hideCompletedHomework: Boolean get() = bool("hideCompletedHomework")
    val showUpcomingDays: Boolean get() = bool("showUpcomingDays")
    val timetableMode: com.bcw.classeviva.data.timetable.TimetableMode
        get() = if (string("timetableMode") == "day") com.bcw.classeviva.data.timetable.TimetableMode.day else com.bcw.classeviva.data.timetable.TimetableMode.list
    val timetableShowsTests: Boolean get() = bool("timetableShowsTests")
    val timetableShowsNowCard: Boolean get() = bool("timetableShowsNowCard")
    val showsTimetableTab: Boolean get() = bool("showsTimetableTab")
    val homeworkReminders: Boolean get() = bool("homeworkReminders")
    val reminderHour: Int get() = int("reminderHour")
    val useLock: Boolean get() = bool("useLock")
    val dynamicColor: Boolean get() = bool("dynamicColor")
    val liveUpdates: Boolean get() = bool("liveUpdates")

    /** La voce si sincronizza con l'account BCW (scelta solo per questo dispositivo). */
    fun syncs(category: SyncCategory): Boolean = category.name !in strings("unsyncedCategories")

    fun setSyncs(category: SyncCategory, enabled: Boolean) {
        val list = strings("unsyncedCategories").filter { it != category.name }.toMutableList()
        if (!enabled) list += category.name
        setStrings("unsyncedCategories", list.sorted())
    }

    /** Colori, angoli, animazioni e caratteri sono quelli di BCW. */
    val isDefaultAppearance: Boolean
        get() = appearanceKeys.all { key ->
            val v = string(key)
            (if (key == "accentColor") v.lowercase() else v) == (defaults[key] as JsonPrimitive).content
        }

    fun resetAppearance() {
        values = values + appearanceKeys.associateWith { defaults.getValue(it) }
        save()
    }

    fun newsEnabled(kind: NewsKind) = kind.name in strings("newsNotifications")

    fun setNews(kind: NewsKind, enabled: Boolean) {
        val list = strings("newsNotifications").filter { it != kind.name }.toMutableList()
        if (enabled) list += kind.name
        setStrings("newsNotifications", list)
    }

    fun isCollapsed(section: String) = section in strings("collapsedSections")

    fun toggleCollapsed(section: String) {
        val list = strings("collapsedSections").toMutableList()
        if (!list.remove(section)) list += section
        setStrings("collapsedSections", list)
    }

    fun isCompleted(event: AgendaEvent) = event.id in completedSet
    private val completedSet: Set<Long> get() = longs("completedHomework").toSet()
    val completed: Set<Long> get() = completedSet

    fun toggleCompleted(event: AgendaEvent) {
        val list = longs("completedHomework").toMutableList()
        if (!list.remove(event.id)) list += event.id
        setLongs("completedHomework", list)
    }

    fun clearCompleted() = setLongs("completedHomework", emptyList())
}
