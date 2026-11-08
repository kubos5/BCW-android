package com.bcw.classeviva.util

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull

/**
 * Decodifica tollerante (come `str/int/dbl/bool/arr` di `js/models.js`): le API di Classeviva non
 * sono documentate e i tipi dei campi cambiano (numeri come stringhe, booleani come 0/1…).
 */
val JsonLenient = Json { ignoreUnknownKeys = true; isLenient = true; explicitNulls = false }

fun parseJson(text: String): JsonElement? = runCatching { JsonLenient.parseToJsonElement(text) }.getOrNull()

val JsonElement?.obj: JsonObject? get() = this as? JsonObject
val JsonElement?.arr: JsonArray? get() = this as? JsonArray

fun JsonObject?.str(key: String): String? {
    val v = this?.get(key) ?: return null
    if (v is JsonPrimitive && v !== JsonNull) return v.content
    return null
}

fun JsonObject?.int(key: String): Long? {
    val v = this?.get(key) as? JsonPrimitive ?: return null
    if (v === JsonNull) return null
    if (v.isString) {
        val t = v.content.trim()
        return if (Regex("^-?\\d+$").matches(t)) t.toLongOrNull() else null
    }
    v.longOrNull?.let { return it }
    val d = v.doubleOrNull ?: return null
    return if (d.isFinite()) d.toLong() else null
}

fun JsonObject?.dbl(key: String): Double? {
    val v = this?.get(key) as? JsonPrimitive ?: return null
    if (v === JsonNull) return null
    if (v.isString) {
        val t = v.content.trim()
        if (t.isEmpty()) return null
        return t.replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() }
    }
    return v.doubleOrNull?.takeIf { it.isFinite() }
}

fun JsonObject?.bool(key: String): Boolean {
    val v = this?.get(key) as? JsonPrimitive ?: return false
    if (v === JsonNull) return false
    if (v.isString) return v.content.lowercase() in setOf("true", "1", "s", "si", "sì", "y", "yes")
    v.booleanOrNull?.let { return it }
    v.doubleOrNull?.let { return it != 0.0 }
    return false
}

fun JsonObject?.child(key: String): JsonObject? = this?.get(key) as? JsonObject

/** Elementi di un array che si riescono a decodificare (gli altri vengono scartati). */
fun <T> JsonObject?.list(key: String, parse: (JsonObject) -> T?): List<T> {
    val array = this?.get(key) as? JsonArray ?: return emptyList()
    return array.mapNotNull { item -> (item as? JsonObject)?.let { runCatching { parse(it) }.getOrNull() } }
}

fun jsonString(value: String?): JsonElement = if (value == null) JsonNull else JsonPrimitive(value)

/** Serializza con le chiavi in ordine alfabetico e senza i valori nulli (come `JSONEncoder` dell'app). */
fun JsonElement.sortedCompact(): JsonElement = when (this) {
    is JsonObject -> JsonObject(entries.filter { it.value !is JsonNull }.sortedBy { it.key }.associate { it.key to it.value.sortedCompact() }.toSortedMap())
    is JsonArray -> JsonArray(map { it.sortedCompact() })
    else -> this
}
