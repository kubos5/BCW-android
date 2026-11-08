package com.bcw.classeviva.util

import java.text.Normalizer
import java.text.NumberFormat
import java.util.Locale
import java.util.UUID

private val it: Locale = Locale.ITALIAN

fun String.capFirst(): String = if (isEmpty()) this else substring(0, 1).uppercase(it) + substring(1)

/** "MATEMATICA E FISICA" → "Matematica e fisica". */
fun String?.sentenceCased(): String = (this ?: "").lowercase(it).capFirst()

/** "ROSSI MARIO" → "Rossi Mario" (anche dopo trattini, apostrofi e parentesi). */
fun String?.nameCased(): String {
    val lower = (this ?: "").lowercase(it)
    val out = StringBuilder(lower.length)
    var capitalizeNext = true
    for (ch in lower) {
        if (capitalizeNext && ch.isLetter()) {
            out.append(ch.toString().uppercase(it))
            capitalizeNext = false
        } else {
            out.append(ch)
            if (ch.isWhitespace() || ch == '-' || ch == '\'' || ch == '’' || ch == '(') capitalizeNext = true
            else if (ch.isLetter()) capitalizeNext = false
        }
    }
    return out.toString()
}

fun String?.nilIfEmpty(): String? {
    val t = this?.trim() ?: return null
    return t.ifEmpty { null }
}

/** Ricerca senza distinzione tra maiuscole e minuscole. */
fun String?.containsText(needle: String?): Boolean {
    if (needle.isNullOrEmpty()) return true
    return (this ?: "").lowercase(it).contains(needle.lowercase(it))
}

/** Testo senza spazi ai lati, senza maiuscole e senza accenti: per confrontare i nomi. */
fun normalized(s: String?): String =
    Normalizer.normalize((s ?: "").trim(), Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "").lowercase(it)

fun plural(n: Int, one: String, many: String): String = if (n == 1) one else many.replace("#", n.toString())

object GradeFormat {
    private fun formatter(min: Int, max: Int) = NumberFormat.getNumberInstance(it).apply {
        minimumFractionDigits = min
        maximumFractionDigits = max
        isGroupingUsed = true
    }
    private val avg = ThreadLocal.withInitial { formatter(2, 2) }
    private val short = ThreadLocal.withInitial { formatter(0, 2) }
    private val euro = ThreadLocal.withInitial { NumberFormat.getCurrencyInstance(Locale.ITALY) }

    fun average(v: Double?): String = if (v == null || v.isNaN()) "–" else avg.get()!!.format(v)
    fun short(v: Double?): String = if (v == null || v.isNaN()) "–" else short.get()!!.format(v)
    fun euro(v: Double): String = euro.get()!!.format(v)
}

/** Identificatore deterministico (djb2 su 64 bit) per gli elementi senza un id dal server. */
object StableID {
    fun make(vararg parts: String): Long {
        var hash = 5381L
        for (b in parts.joinToString("|").toByteArray(Charsets.UTF_8)) {
            hash = (hash shl 5) + hash + (b.toInt() and 0xFF)
        }
        return hash and 0x7FFF_FFFF_FFFFL
    }
}

/** Generatore pseudo-casuale con seme (SplitMix64), per dati demo stabili (uguale al web). */
class SeededRandom(seed: Long) {
    private var state: Long = seed + -0x61c8864680b583ebL // 0x9E3779B97F4A7C15

    private fun nextUInt64(): Long {
        state += -0x61c8864680b583ebL
        var z = state
        z = (z xor (z ushr 30)) * -0x40a7b892e31b1a47L // 0xBF58476D1CE4E5B9
        z = (z xor (z ushr 27)) * -0x6b2fb644ecceee15L // 0x94D049BB133111EB
        return z xor (z ushr 31)
    }

    fun next(upTo: Int): Int {
        if (upTo <= 0) return 0
        return java.lang.Long.remainderUnsigned(nextUInt64(), upTo.toLong()).toInt()
    }
}

fun newUUID(): String = UUID.randomUUID().toString().uppercase(Locale.ROOT)

inline fun <T, K> Iterable<T>.groupByOrdered(key: (T) -> K): LinkedHashMap<K, MutableList<T>> {
    val map = LinkedHashMap<K, MutableList<T>>()
    for (item in this) map.getOrPut(key(item)) { mutableListOf() }.add(item)
    return map
}

val italianCollator: java.text.Collator = java.text.Collator.getInstance(it).apply { strength = java.text.Collator.SECONDARY }
fun byName(a: String, b: String): Int = italianCollator.compare(a, b)
