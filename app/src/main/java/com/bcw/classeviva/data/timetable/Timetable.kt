package com.bcw.classeviva.data.timetable

import com.bcw.classeviva.util.Dates
import com.bcw.classeviva.util.StableID
import com.bcw.classeviva.util.newUUID
import com.bcw.classeviva.util.normalized
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong
import kotlin.math.sign

/*
 * Orario settimanale: lo stesso JSON dell'app iOS e della versione web (backup compatibili).
 * Orari in minuti dalla mezzanotte, giorni 1 = lunedì … 7 = domenica, UUID maiuscoli, date ISO
 * 8601 senza millisecondi.
 */

// MARK: - Basi

object Weekday {
    val names = listOf("Lunedì", "Martedì", "Mercoledì", "Giovedì", "Venerdì", "Sabato", "Domenica")
    val short = listOf("Lun", "Mar", "Mer", "Gio", "Ven", "Sab", "Dom")
    fun name(w: Int) = names.getOrElse(w - 1) { "" }
    fun shortName(w: Int) = short.getOrElse(w - 1) { "" }
    fun lower(w: Int) = name(w).lowercase(Locale.ITALIAN)
    /** Con l'articolo: "il lunedì", ma "la domenica". */
    fun withArticle(w: Int) = "${if (w == 7) "la" else "il"} ${lower(w)}"
    /** 1 = lunedì … 7 = domenica. */
    fun of(date: LocalDate) = date.dayOfWeek.value
    /** Giorno della settimana `w` nella settimana di `date`. */
    fun dateIn(date: LocalDate, w: Int): LocalDate = Dates.startOfWeek(date).plusDays((w - 1).toLong())
}

/** Arrotondamento come in Swift (`.rounded()`): metà lontano da zero. */
fun roundHalfAway(x: Double): Long = (sign(x) * Math.round(abs(x)).toDouble()).toLong()

object TimeOfDay {
    fun string(m: Int): String {
        val v = m.coerceIn(0, 1440)
        return String.format(Locale.ROOT, "%02d:%02d", v / 60, v % 60)
    }

    fun duration(m: Int): String = when {
        m == 60 -> "1 ora"
        m % 60 == 0 -> "${m / 60} ore"
        m < 60 -> "$m min"
        else -> "${m / 60} h ${m % 60} min"
    }

    fun snapped(m: Int, step: Int): Int = (roundHalfAway(m.toDouble() / step) * step).toInt()

    fun parse(text: String?): Int? {
        val m = Regex("^(\\d{1,2}):(\\d{2})$").find(text ?: return null) ?: return null
        return m.groupValues[1].toInt() * 60 + m.groupValues[2].toInt()
    }
}

enum class Alignment(val title: String, val shortTitle: String, val icon: String, val dragStep: Int) {
    automatic("Allineamento automatico", "Automatico", "alignAuto", 30),
    free("Allineamento libero", "Libero", "alignFree", 5),
}

enum class EntryKind(val title: String, val icon: String) {
    lesson("Lezione", "book"),
    breakTime("Intervallo", "coffee"),
}

enum class TimetableMode(val title: String, val icon: String) {
    list("Lista", "list"),
    day("Giorno", "dayView"),
}

/**
 * Attività in diretta (nell'app iOS) / aggiornamento in corso (su Android) per un evento:
 * `null` segue l'impostazione, `show` lo mostra sempre, `hide` lo salta.
 */
enum class LiveActivityChoice(val title: String) {
    show("Mostra"),
    hide("Non mostrare"),
}

// MARK: - Modello

sealed class EntryColor {
    data class Pastel(val index: Int) : EntryColor()
    data class Custom(val hex: Int) : EntryColor()
    data class Series(val paletteID: String, val index: Int, val count: Int) : EntryColor()
}

data class Attachment(val id: String, val name: String, val storedName: String, val size: Long, val addedAt: Instant)

data class TimetableEntry(
    val id: String = newUUID(),
    val weekday: Int = 1,
    val start: Int = 480,
    val end: Int = minOf(1440, start + 60),
    val kind: EntryKind = EntryKind.lesson,
    val title: String = "",
    val subjectId: Long? = null,
    val teacher: String = "",
    val room: String = "",
    val color: EntryColor? = null,
    val note: String = "",
    val attachments: List<Attachment> = emptyList(),
    val isWeeklyTest: Boolean = false,
    val liveActivity: LiveActivityChoice? = null,
) {
    val isBreak: Boolean get() = kind == EntryKind.breakTime
    val duration: Int get() = end - start
    val matchKey: String get() = normalized(title)
    val displayTitle: String get() = title.trim().ifEmpty { if (isBreak) "Intervallo" else "Senza titolo" }
    val timeRange: String get() = "${TimeOfDay.string(start)}–${TimeOfDay.string(end)}"
    val isAutoAligned: Boolean get() = isBreak || (start % 30 == 0 && (duration == 30 || duration == 60))

    /** L'evento spostato di `minutes` minuti, con la stessa durata e senza uscire dalla giornata. */
    fun shifted(minutes: Int): TimetableEntry {
        val length = duration
        val s = minOf(maxOf(0, start + minutes), 1440 - length)
        return copy(start = s, end = s + length)
    }

    /**
     * L'evento allineato alle ore e alle mezz'ore; quelli più lunghi di un'ora diventano una serie
     * di eventi da un'ora (o mezz'ora). Solo il primo pezzo tiene id, nota, allegati e verifica.
     */
    fun alignedPieces(): List<TimetableEntry> {
        if (isAutoAligned) return listOf(this)
        val s = minOf(maxOf(0, TimeOfDay.snapped(start, 30)), 1440 - 30)
        val e = minOf(1440, maxOf(s + 30, TimeOfDay.snapped(end, 30)))
        val pieces = mutableListOf<TimetableEntry>()
        var cursor = s
        while (cursor < e) {
            val length = if (e - cursor >= 60) 60 else 30
            val piece = if (pieces.isEmpty()) copy(start = cursor, end = cursor + length)
            else copy(id = newUUID(), note = "", attachments = emptyList(), isWeeklyTest = false, start = cursor, end = cursor + length)
            pieces += piece
            cursor += length
        }
        return pieces
    }
}

/** Ordine degli eventi di una giornata: inizio, fine, nome. */
val entryOrder: Comparator<TimetableEntry> = compareBy<TimetableEntry>({ it.start }, { it.end }).thenComparator { a, b -> a.displayTitle.compareTo(b.displayTitle) }

data class Occurrence(val note: String = "", val attachments: List<Attachment> = emptyList(), val isTest: Boolean? = null) {
    val isEmpty: Boolean get() = note.isEmpty() && attachments.isEmpty() && isTest == null
}

data class RulePeriod(val id: String, val start: LocalDate, val end: LocalDate)

data class TestRules(
    val teachers: List<String> = emptyList(),
    val subjects: List<String> = emptyList(),
    val weekdays: List<Int> = emptyList(),
    val periods: List<RulePeriod> = emptyList(),
    val keywords: List<String> = emptyList(),
    val unmatched: Boolean = false,
) {
    val count: Int get() = teachers.size + subjects.size + weekdays.size + periods.size + keywords.size + if (unmatched) 1 else 0
}

data class BreakRule(val id: String = newUUID(), val start: Int, val duration: Int)

data class GenerationSettings(
    val firstHourStart: Int = 480,
    val hourLength: Int = 60,
    val weeks: Int = 6,
    val breaks: List<BreakRule> = emptyList(),
)

data class Palette(val id: String, val name: String, val colors: List<Int>, val isGradient: Boolean) {
    val isBuiltIn: Boolean get() = id.startsWith("builtin.")
}

data class Timetable(
    val entries: List<TimetableEntry> = emptyList(),
    val occurrences: Map<String, Occurrence> = emptyMap(),
    val hiddenTests: List<Long> = emptyList(),
    val shownTests: List<Long> = emptyList(),
    val testRules: TestRules = TestRules(),
    val alignment: Alignment = Alignment.automatic,
    val customColors: List<Int> = emptyList(),
    val customPalettes: List<Palette> = emptyList(),
    val generation: GenerationSettings = GenerationSettings(),
) {
    val referencedFiles: Set<String>
        get() = buildSet {
            for (e in entries) for (a in e.attachments) add(a.storedName)
            for (o in occurrences.values) for (a in o.attachments) add(a.storedName)
        }
}

fun occurrenceKey(id: String, day: LocalDate) = "$id@${Dates.dayKey(day)}"

// MARK: - Colori e palette

const val PASTEL_ID = "builtin.pastel"
private fun hexes(s: String) = s.split(' ').map { it.toInt(16) }

val builtInPalettes = listOf(
    Palette(PASTEL_ID, "Pastello (Lezioni)", hexes("B5523B 3F7D73 5B5FA8 A0782A 8A4F7D 4F7FA6 6E8B3D B0563F 7A6A58 2F7A8C 9A4A5A 5E7F52"), false),
    Palette("builtin.ocean", "Oceano", hexes("A6D8F2 5FA8DC 2F6FB5 1B3F7A"), true),
    Palette("builtin.sunset", "Tramonto", hexes("F4D35E F59E3B E8613C C0392B"), true),
    Palette("builtin.lagoon", "Laguna", hexes("8BCF7E 3FA796 2E86AB 2B5C9E"), true),
    Palette("builtin.lavender", "Lavanda", hexes("F4A6C6 C98BD9 9A7BD6 6A5ACD"), true),
    Palette("builtin.forest", "Bosco", hexes("C5E1A5 8BC34A 4E9A45 2E6B34"), true),
    Palette("builtin.earth", "Terra", hexes("E9D8A6 D4A373 B07D4F 7F5539"), true),
    Palette("builtin.rainbow", "Arcobaleno", hexes("E57373 F0A35E E8C95A 81C784 64B5F6 9575CD"), true),
    Palette("builtin.slate", "Ardesia", hexes("C2CAD3 8794A3 5C6B7A 3B4754"), true),
)

fun interpolate(stops: List<Int>, t: Double): Int {
    val position = t.coerceIn(0.0, 1.0) * (stops.size - 1)
    val lower = minOf(position.toInt(), stops.size - 2)
    val fraction = position - lower
    var result = 0
    for (shift in intArrayOf(16, 8, 0)) {
        val a = (stops[lower] shr shift) and 0xFF
        val b = (stops[lower + 1] shr shift) and 0xFF
        result = result or (roundHalfAway(a * (1 - fraction) + b * fraction).toInt() shl shift)
    }
    return result
}

/** Colore dell'elemento `index` di una serie di `count` eventi. */
fun paletteColor(palette: Palette, index: Int, count: Int): Int {
    val colors = palette.colors
    if (colors.isEmpty()) return 0x8FA3B8
    if (!palette.isGradient || colors.size < 2 || count < 2) return colors[((index % colors.size) + colors.size) % colors.size]
    return interpolate(colors, index.coerceIn(0, count - 1).toDouble() / (count - 1))
}

fun paletteSamples(palette: Palette, count: Int = 6): List<Int> {
    if (!palette.isGradient || palette.colors.size < 2) return palette.colors
    return (0 until count).map { paletteColor(palette, it, count) }
}

fun hexString(hex: Int) = String.format(Locale.ROOT, "#%06X", hex and 0xFFFFFF)
fun parseHex(s: String): Int? = s.removePrefix("#").takeIf { Regex("^[0-9a-fA-F]{6}$").matches(it) }?.toInt(16)

/** Un colore scelto dall'utente: com'è nel tema chiaro, schiarito del 30% nel tema scuro. */
fun lightened(hex: Int, amount: Double): Int {
    var result = 0
    for (shift in intArrayOf(16, 8, 0)) {
        val v = (hex shr shift) and 0xFF
        result = result or (roundHalfAway(v + (255 - v) * amount).toInt() shl shift)
    }
    return result
}

/** Tinta di un evento: un colore delle materie (indice 0–11), un intervallo o un colore esatto. */
sealed class EntryTint {
    data class Subject(val index: Int) : EntryTint()
    object Break : EntryTint()
    data class Hex(val hex: Int) : EntryTint()
}

fun subjectTintIndex(id: Long): Int = (abs(id) % 12).toInt()

fun defaultTint(entry: TimetableEntry): EntryTint =
    if (entry.isBreak) EntryTint.Break else EntryTint.Subject(subjectTintIndex(entry.subjectId ?: StableID.make(entry.matchKey)))

fun entryTint(entry: TimetableEntry, palette: (String) -> Palette?): EntryTint {
    return when (val c = entry.color) {
        null -> defaultTint(entry)
        is EntryColor.Pastel -> EntryTint.Subject(c.index)
        is EntryColor.Custom -> EntryTint.Hex(c.hex)
        is EntryColor.Series -> {
            val p = palette(c.paletteID) ?: return defaultTint(entry)
            if (p.id == PASTEL_ID) EntryTint.Subject(c.index) else EntryTint.Hex(paletteColor(p, c.index, c.count))
        }
    }
}

/**
 * Posizioni nella serie di una palette: gli eventi uguali accostati (stesso nome, uno subito dopo
 * l'altro) prendono lo stesso colore.
 */
fun seriesPositions(entries: List<TimetableEntry>): Pair<List<Int>, Int> {
    val index = mutableListOf<Int>()
    var count = 0
    val open = HashMap<String, Pair<Int, Int>>() // chiave → (fine, indice)
    for (entry in entries) {
        val key = entry.matchKey
        val previous = open[key]
        if (previous != null && previous.first == entry.start && key.isNotEmpty()) {
            index += previous.second
            open[key] = entry.end to previous.second
        } else {
            index += count
            open[key] = entry.end to count
            count += 1
        }
    }
    return index to count
}

// MARK: - Decodifica e codifica

class TimetableFormatError : Exception()

private fun JsonElement?.isObject() = this is JsonObject
private fun JsonElement?.intOrNull(): Long? {
    val p = this as? JsonPrimitive ?: return null
    if (p.isString || p === JsonNull) return null
    p.longOrNull?.let { return it }
    val d = p.doubleOrNull ?: return null
    return if (d.isFinite()) d.toLong() else null
}
/** Solo numeri interi (come `Number.isInteger` del web). */
private fun JsonElement?.integerOrNull(): Long? {
    val p = this as? JsonPrimitive ?: return null
    if (p.isString || p === JsonNull) return null
    p.longOrNull?.let { return it }
    val d = p.doubleOrNull ?: return null
    return if (d.isFinite() && d == Math.floor(d)) d.toLong() else null
}
private fun JsonElement?.stringOrNull(): String? = (this as? JsonPrimitive)?.takeIf { it.isString }?.content
private fun JsonElement?.boolOrNull(): Boolean? = (this as? JsonPrimitive)?.takeIf { !it.isString }?.booleanOrNull
private fun JsonElement?.numberOrNull(): Double? = (this as? JsonPrimitive)?.takeIf { !it.isString && it !== JsonNull }?.doubleOrNull

private fun parseInstant(v: JsonElement?): Instant? = Dates.parseIsoInstant(v.stringOrNull())

private fun decodeColor(v: JsonElement?): EntryColor? {
    val o = v as? JsonObject ?: return null
    (o["pastel"] as? JsonObject)?.get("_0")?.integerOrNull()?.let { return EntryColor.Pastel(it.toInt().coerceIn(0, 11)) }
    (o["custom"] as? JsonObject)?.get("_0")?.integerOrNull()?.let { return EntryColor.Custom(it.toInt() and 0xFFFFFF) }
    val s = o["series"] as? JsonObject ?: return null
    val id = s["paletteID"].stringOrNull() ?: return null
    val index = s["index"].integerOrNull() ?: return null
    val count = s["count"].integerOrNull() ?: return null
    return EntryColor.Series(id, index.toInt(), count.toInt())
}

private fun encodeColor(c: EntryColor?): JsonElement? = when (c) {
    null -> null
    is EntryColor.Pastel -> JsonObject(mapOf("pastel" to JsonObject(mapOf("_0" to JsonPrimitive(c.index)))))
    is EntryColor.Custom -> JsonObject(mapOf("custom" to JsonObject(mapOf("_0" to JsonPrimitive(c.hex)))))
    is EntryColor.Series -> JsonObject(mapOf("series" to JsonObject(mapOf(
        "count" to JsonPrimitive(c.count), "index" to JsonPrimitive(c.index), "paletteID" to JsonPrimitive(c.paletteID)))))
}

private fun decodeAttachment(v: JsonElement?, strict: Boolean): Attachment? {
    val o = v as? JsonObject
    val id = o?.get("id").stringOrNull()
    val name = o?.get("name").stringOrNull()
    val stored = o?.get("storedName").stringOrNull()
    val size = o?.get("size").numberOrNull()
    val added = parseInstant(o?.get("addedAt"))
    if (id == null || name == null || stored == null || size == null || added == null) {
        if (strict) throw TimetableFormatError()
        return null
    }
    return Attachment(id, name, stored, size.toLong(), added)
}

private fun decodeAttachments(v: JsonElement?, strict: Boolean): List<Attachment> {
    if (v == null || v === JsonNull) return emptyList()
    if (v !is JsonArray) {
        if (strict) throw TimetableFormatError()
        return emptyList()
    }
    return v.mapNotNull { decodeAttachment(it, strict) }
}

private fun decodeEntry(v: JsonElement?, strict: Boolean): TimetableEntry? {
    val o = v as? JsonObject
    if (o == null) {
        if (strict) throw TimetableFormatError()
        return null
    }
    val start = (o["start"].intOrNull() ?: 480L).toInt().coerceIn(0, 1439)
    val id = o["id"].stringOrNull()?.takeIf { it.isNotEmpty() }?.uppercase(Locale.ROOT) ?: newUUID()
    return TimetableEntry(
        id = id,
        weekday = (o["weekday"].intOrNull() ?: 1L).toInt().coerceIn(1, 7),
        start = start,
        end = (o["end"].intOrNull() ?: (start + 60L)).toInt().coerceIn(start + 1, 1440),
        kind = if (o["kind"].stringOrNull() == "breakTime") EntryKind.breakTime else EntryKind.lesson,
        title = o["title"].stringOrNull() ?: "",
        subjectId = o["subjectId"].integerOrNull(),
        teacher = o["teacher"].stringOrNull() ?: "",
        room = o["room"].stringOrNull() ?: "",
        color = decodeColor(o["color"]),
        note = o["note"].stringOrNull() ?: "",
        attachments = decodeAttachments(o["attachments"], strict),
        isWeeklyTest = o["isWeeklyTest"].boolOrNull() == true,
        // Un valore sconosciuto (di una versione futura) vale come "segue l'impostazione".
        liveActivity = LiveActivityChoice.entries.firstOrNull { it.name == o["liveActivity"].stringOrNull() },
    )
}

private fun decodeOccurrence(v: JsonElement?, strict: Boolean): Occurrence? {
    val o = v as? JsonObject
    if (o == null) {
        if (strict) throw TimetableFormatError()
        return null
    }
    return Occurrence(o["note"].stringOrNull() ?: "", decodeAttachments(o["attachments"], strict), o["isTest"].boolOrNull())
}

private fun JsonElement?.strings(): List<String> = (this as? JsonArray)?.mapNotNull { it.stringOrNull() } ?: emptyList()

private fun decodeRules(v: JsonElement?): TestRules {
    val o = v as? JsonObject ?: return TestRules()
    val weekdays = (o["weekdays"] as? JsonArray)?.mapNotNull { it.integerOrNull()?.toInt()?.takeIf { w -> w in 1..7 } } ?: emptyList()
    val periods = (o["periods"] as? JsonArray)?.mapNotNull { p ->
        val po = p as? JsonObject
        val s = parseInstant(po?.get("start"))
        val e = parseInstant(po?.get("end"))
        if (s == null || e == null) null
        else RulePeriod(po?.get("id").stringOrNull() ?: newUUID(), s.atZone(ZoneId.systemDefault()).toLocalDate(), e.atZone(ZoneId.systemDefault()).toLocalDate())
    } ?: emptyList()
    return TestRules(o["teachers"].strings(), o["subjects"].strings(), weekdays, periods, o["keywords"].strings(), o["unmatched"].boolOrNull() == true)
}

private fun decodeGeneration(v: JsonElement?): GenerationSettings {
    val o = v as? JsonObject ?: return GenerationSettings()
    return try {
        val breaks = ((o["breaks"] as? JsonArray) ?: JsonArray(emptyList())).map { b ->
            val bo = b as? JsonObject ?: throw TimetableFormatError()
            val id = bo["id"].stringOrNull() ?: throw TimetableFormatError()
            val start = bo["start"].integerOrNull() ?: throw TimetableFormatError()
            val duration = bo["duration"].integerOrNull() ?: throw TimetableFormatError()
            BreakRule(id, start.toInt(), duration.toInt())
        }
        GenerationSettings(
            firstHourStart = (o["firstHourStart"].intOrNull() ?: 480L).toInt(),
            hourLength = maxOf(10, (o["hourLength"].intOrNull() ?: 60L).toInt()),
            weeks = (o["weeks"].intOrNull() ?: 6L).toInt(),
            breaks = breaks,
        )
    } catch (e: TimetableFormatError) {
        GenerationSettings()
    }
}

/**
 * Legge un orario. Con `strict` (importazione di un backup) un elemento non valido fa rifiutare
 * tutto il documento; altrimenti viene solo saltato.
 */
fun decodeTimetable(v: JsonElement, strict: Boolean = false): Timetable {
    val o = v as? JsonObject ?: throw TimetableFormatError()
    fun list(a: JsonElement?): List<JsonElement> {
        if (a == null || a === JsonNull) return emptyList()
        if (a !is JsonArray) {
            if (strict) throw TimetableFormatError()
            return emptyList()
        }
        return a
    }
    val entries = list(o["entries"]).mapNotNull { decodeEntry(it, strict) }
    val occurrences = LinkedHashMap<String, Occurrence>()
    val rawOccurrences = o["occurrences"]
    if (rawOccurrences != null && rawOccurrences !== JsonNull) {
        if (rawOccurrences !is JsonObject) {
            if (strict) throw TimetableFormatError()
        } else {
            for ((key, info) in rawOccurrences) {
                val occ = decodeOccurrence(info, strict)
                if (occ != null && !occ.isEmpty) occurrences[key] = occ
            }
        }
    }
    fun ints(a: JsonElement?): List<Long> = list(a).mapNotNull { x ->
        val n = x.integerOrNull()
        if (n == null && strict) throw TimetableFormatError()
        n
    }
    return Timetable(
        entries = entries,
        occurrences = occurrences,
        hiddenTests = ints(o["hiddenTests"]),
        shownTests = (o["shownTests"] as? JsonArray)?.mapNotNull { it.integerOrNull() } ?: emptyList(),
        testRules = decodeRules(o["testRules"]),
        alignment = if (o["alignment"].stringOrNull() == "free") Alignment.free else Alignment.automatic,
        customColors = ints(o["customColors"]).map { (it and 0xFFFFFF).toInt() },
        customPalettes = list(o["customPalettes"]).mapNotNull { p ->
            val po = p as? JsonObject
            if (po == null) {
                if (strict) throw TimetableFormatError()
                null
            } else Palette(
                id = po["id"].stringOrNull() ?: newUUID(),
                name = po["name"].stringOrNull() ?: "Palette",
                colors = (po["colors"] as? JsonArray)?.mapNotNull { it.integerOrNull()?.let { c -> (c and 0xFFFFFF).toInt() } } ?: emptyList(),
                isGradient = po["isGradient"].boolOrNull() != false,
            )
        },
        generation = decodeGeneration(o["generation"]),
    )
}

/** Data come la codifica dell'app: ISO 8601 in UTC senza millisecondi. */
private fun isoDate(instant: Instant): JsonPrimitive = JsonPrimitive(Dates.isoUtc(instant))
private fun isoDay(day: LocalDate): JsonPrimitive = isoDate(day.atStartOfDay(ZoneId.systemDefault()).toInstant())

private fun encodeAttachment(a: Attachment) = JsonObject(sortedMapOf(
    "addedAt" to isoDate(a.addedAt), "id" to JsonPrimitive(a.id), "name" to JsonPrimitive(a.name),
    "size" to JsonPrimitive(a.size), "storedName" to JsonPrimitive(a.storedName),
))

private fun encodeEntry(e: TimetableEntry): JsonObject {
    val map = sortedMapOf<String, JsonElement>(
        "attachments" to JsonArray(e.attachments.map(::encodeAttachment)), "end" to JsonPrimitive(e.end),
        "id" to JsonPrimitive(e.id), "isWeeklyTest" to JsonPrimitive(e.isWeeklyTest), "kind" to JsonPrimitive(e.kind.name),
        "note" to JsonPrimitive(e.note), "room" to JsonPrimitive(e.room), "start" to JsonPrimitive(e.start),
        "teacher" to JsonPrimitive(e.teacher), "title" to JsonPrimitive(e.title), "weekday" to JsonPrimitive(e.weekday),
    )
    encodeColor(e.color)?.let { map["color"] = it }
    e.liveActivity?.let { map["liveActivity"] = JsonPrimitive(it.name) }
    e.subjectId?.let { map["subjectId"] = JsonPrimitive(it) }
    return JsonObject(map)
}

/** Il documento dell'orario con le chiavi in ordine e senza valori nulli (come l'app e il web). */
fun encodeTimetable(t: Timetable): JsonObject {
    val occurrences = sortedMapOf<String, JsonElement>()
    for ((k, o) in t.occurrences) {
        val m = sortedMapOf<String, JsonElement>("attachments" to JsonArray(o.attachments.map(::encodeAttachment)), "note" to JsonPrimitive(o.note))
        o.isTest?.let { m["isTest"] = JsonPrimitive(it) }
        occurrences[k] = JsonObject(m)
    }
    val rules = t.testRules
    return JsonObject(sortedMapOf(
        "alignment" to JsonPrimitive(t.alignment.name),
        "customColors" to JsonArray(t.customColors.map { JsonPrimitive(it) }),
        "customPalettes" to JsonArray(t.customPalettes.map { p ->
            JsonObject(sortedMapOf("colors" to JsonArray(p.colors.map { JsonPrimitive(it) }), "id" to JsonPrimitive(p.id),
                "isGradient" to JsonPrimitive(p.isGradient), "name" to JsonPrimitive(p.name)))
        }),
        "entries" to JsonArray(t.entries.map(::encodeEntry)),
        "generation" to JsonObject(sortedMapOf(
            "breaks" to JsonArray(t.generation.breaks.map { b ->
                JsonObject(sortedMapOf("duration" to JsonPrimitive(b.duration), "id" to JsonPrimitive(b.id), "start" to JsonPrimitive(b.start)))
            }),
            "firstHourStart" to JsonPrimitive(t.generation.firstHourStart),
            "hourLength" to JsonPrimitive(t.generation.hourLength),
            "weeks" to JsonPrimitive(t.generation.weeks),
        )),
        "hiddenTests" to JsonArray(t.hiddenTests.sorted().map { JsonPrimitive(it) }),
        "occurrences" to JsonObject(occurrences),
        "shownTests" to JsonArray(t.shownTests.sorted().map { JsonPrimitive(it) }),
        "testRules" to JsonObject(sortedMapOf(
            "keywords" to JsonArray(rules.keywords.map { JsonPrimitive(it) }),
            "periods" to JsonArray(rules.periods.map { p ->
                JsonObject(sortedMapOf("end" to isoDay(p.end), "id" to JsonPrimitive(p.id), "start" to isoDay(p.start)))
            }),
            "subjects" to JsonArray(rules.subjects.map { JsonPrimitive(it) }),
            "teachers" to JsonArray(rules.teachers.map { JsonPrimitive(it) }),
            "unmatched" to JsonPrimitive(rules.unmatched),
            "weekdays" to JsonArray(rules.weekdays.map { JsonPrimitive(it) }),
        )),
    ))
}

fun fileExtension(name: String?): String {
    val base = (name ?: "").substringAfterLast('/')
    val dot = base.lastIndexOf('.')
    return if (dot > 0 && dot < base.length - 1) base.substring(dot + 1) else ""
}

val timetableFileTypes = mapOf(
    "jpg" to "image/jpeg", "jpeg" to "image/jpeg", "png" to "image/png", "gif" to "image/gif", "heic" to "image/heic",
    "webp" to "image/webp", "pdf" to "application/pdf", "txt" to "text/plain", "mp3" to "audio/mpeg", "m4a" to "audio/mp4",
    "mp4" to "video/mp4", "mov" to "video/quicktime",
)

fun mimeType(name: String): String = timetableFileTypes[fileExtension(name).lowercase()]
    ?: runCatching { android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(fileExtension(name).lowercase()) }.getOrNull()
    ?: "application/octet-stream"
