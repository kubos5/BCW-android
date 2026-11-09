package com.bcw.classeviva.wear.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull

/** Tinta di una lezione, già risolta dall'app del telefono (per il tema chiaro e per lo scuro). */
sealed class Tint {
    data class Subject(val index: Int) : Tint()
    object Break : Tint()
    data class Hex(val light: Int, val dark: Int) : Tint()
}

/** Una modalità della palette: materie come (colore, contenitore, su contenitore) e intervallo. */
data class PaletteMode(val subjects: List<Triple<Int, Int, Int>>, val breakTint: Int)

/** La palette delle materie inviata dal telefono, per il tema chiaro e per lo scuro. */
data class WatchPalette(val light: PaletteMode, val dark: PaletteMode)

data class WatchLesson(
    val id: String,
    val weekday: Int,
    val start: Int,
    val end: Int,
    val isBreak: Boolean = false,
    val title: String = "",
    val room: String = "",
    val teacher: String = "",
    val tint: Tint = Tint.Subject(0),
) {
    val duration: Int get() = end - start
}

/**
 * Ciò che l'app del telefono manda all'orologio tramite la Data Layer: le lezioni della settimana
 * e le impostazioni di aspetto. È un formato interno ad Android: non tocca quello condiviso con
 * iOS e web.
 */
data class WatchPayload(
    val entries: List<WatchLesson>,
    val accent: String = DEFAULT_ACCENT,
    val colorStyle: String = "tonalSpot",
    val contrast: String = "standard",
    val appearance: String = "system",
    val owner: String? = null,
    val updatedAt: Long = 0L,
    /** Palette del telefono; assente nei dati di esempio, dove si usano i colori integrati. */
    val palette: WatchPalette? = null,
    /** Dati di esempio: nessuna sincronizzazione ancora ricevuta. */
    val demo: Boolean = false,
) {
    val isEmpty: Boolean get() = entries.isEmpty()
}

const val DEFAULT_ACCENT = "#a23e2f"

fun parsePayload(text: String): WatchPayload? = runCatching {
    val root = Json.parseToJsonElement(text) as? JsonObject ?: return null
    val entries = (root["entries"] as? JsonArray)?.mapNotNull { item ->
        parseLesson(item as? JsonObject)
    }.orEmpty()
    WatchPayload(
        entries = entries,
        accent = root.string("accent") ?: DEFAULT_ACCENT,
        colorStyle = root.string("colorStyle") ?: "tonalSpot",
        contrast = root.string("contrast") ?: "standard",
        appearance = root.string("appearance") ?: "system",
        owner = root.string("owner"),
        updatedAt = (root["updatedAt"] as? JsonPrimitive)?.longOrNull ?: 0L,
        palette = parsePalette(root["palette"] as? JsonObject),
    )
}.getOrNull()

private fun parsePalette(o: JsonObject?): WatchPalette? {
    o ?: return null
    val light = parseMode(o["light"] as? JsonObject) ?: return null
    val dark = parseMode(o["dark"] as? JsonObject) ?: return null
    return WatchPalette(light, dark)
}

private fun parseMode(o: JsonObject?): PaletteMode? {
    o ?: return null
    val subjects = (o["subjects"] as? JsonArray)?.mapNotNull { item ->
        val triple = (item as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.intOrNull } ?: return@mapNotNull null
        if (triple.size != 3) return@mapNotNull null
        Triple(triple[0] and 0xFFFFFF, triple[1] and 0xFFFFFF, triple[2] and 0xFFFFFF)
    } ?: return null
    if (subjects.size != 12) return null
    val breakTint = (o["break"] as? JsonPrimitive)?.intOrNull ?: return null
    return PaletteMode(subjects, breakTint and 0xFFFFFF)
}

private fun JsonObject.string(key: String): String? =
    (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

private fun parseLesson(o: JsonObject?): WatchLesson? {
    o ?: return null
    val weekday = (o["weekday"] as? JsonPrimitive)?.intOrNull ?: return null
    if (weekday !in 1..7) return null
    val start = (o["start"] as? JsonPrimitive)?.intOrNull ?: return null
    val end = (o["end"] as? JsonPrimitive)?.intOrNull ?: (start + 60)
    if (start !in 0..1439 || end <= start) return null
    val kind = o.string("kind") ?: "lesson"
    return WatchLesson(
        id = o.string("id") ?: "lesson-$weekday-$start",
        weekday = weekday,
        start = start,
        end = end,
        isBreak = kind == "breakTime",
        title = o.string("title") ?: "",
        room = o.string("room").orEmpty(),
        teacher = o.string("teacher").orEmpty(),
        tint = parseTint(o["tint"] as? JsonObject),
    )
}

private fun parseTint(o: JsonObject?): Tint = when (o?.string("kind")) {
    "subject" -> Tint.Subject(((o["index"] as? JsonPrimitive)?.intOrNull ?: 0).coerceIn(0, 11))
    "hex" -> {
        val light = (o["light"] as? JsonPrimitive)?.intOrNull ?: 0
        val dark = (o["dark"] as? JsonPrimitive)?.intOrNull ?: light
        Tint.Hex(light and 0xFFFFFF, dark and 0xFFFFFF)
    }
    else -> Tint.Break
}

/** Orologio locale in minuti dalla mezzanotte, come il formato dell'orario. */
fun minuteOfDay(hour: Int, minute: Int): Int = hour * 60 + minute

fun timeString(minutes: Int): String {
    val v = minutes.coerceIn(0, 1440)
    return "%02d:%02d".format(v / 60, v % 60)
}

fun durationString(minutes: Int): String = when {
    minutes <= 0 -> "meno di un minuto"
    minutes == 1 -> "1 minuto"
    minutes < 60 -> "$minutes minuti"
    minutes == 60 -> "1 ora"
    minutes % 60 == 0 -> "${minutes / 60} ore"
    else -> "${minutes / 60} h ${minutes % 60} min"
}
