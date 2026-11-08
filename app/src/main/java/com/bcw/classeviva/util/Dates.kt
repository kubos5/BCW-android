package com.bcw.classeviva.util

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

/** Date e formattazione in italiano (come `js/util.js` della versione web). */
object Dates {
    val italian: Locale = Locale.ITALIAN

    /** Orologio sostituibile nelle prove. */
    @Volatile var clock: () -> LocalDateTime = { LocalDateTime.now() }

    fun now(): LocalDateTime = clock()
    fun today(): LocalDate = now().toLocalDate()

    fun isToday(d: LocalDate) = d == today()
    fun isTomorrow(d: LocalDate) = d == today().plusDays(1)
    fun isYesterday(d: LocalDate) = d == today().minusDays(1)
    fun isWeekend(d: LocalDate) = d.dayOfWeek == DayOfWeek.SATURDAY || d.dayOfWeek == DayOfWeek.SUNDAY

    /** La settimana inizia di lunedì, come in Italia. */
    fun startOfWeek(d: LocalDate): LocalDate = d.minusDays((d.dayOfWeek.value - 1).toLong())
    fun startOfMonth(d: LocalDate): LocalDate = d.withDayOfMonth(1)
    fun daysBetween(from: LocalDate, to: LocalDate): Long = ChronoUnit.DAYS.between(from, to)

    /** `yyyy-MM-dd`. */
    fun dayKey(d: LocalDate): String = d.toString()

    /** Formato dei percorsi delle API: `yyyyMMdd`. */
    fun apiString(d: LocalDate): String = String.format(Locale.ROOT, "%04d%02d%02d", d.year, d.monthValue, d.dayOfMonth)

    fun parseApiDate(s: String?): LocalDate? {
        val m = Regex("^(\\d{4})(\\d{2})(\\d{2})$").find(s ?: return null) ?: return null
        return runCatching { LocalDate.of(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt()) }.getOrNull()
    }

    private val zonedPattern = Regex("^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}(:\\d{2}(\\.\\d+)?)?(Z|[+-]\\d{2}:?\\d{2})$")
    private val localPattern = Regex("^(\\d{4})-(\\d{2})-(\\d{2})[T ](\\d{2}):(\\d{2})(?::(\\d{2}))?")
    private val dayPattern = Regex("^(\\d{4})-(\\d{2})-(\\d{2})")

    /** Interpreta le date di Classeviva (ISO 8601 con o senza fuso, solo giorno…) nell'ora locale. */
    fun parseDateTime(value: String?): LocalDateTime? {
        val s = value?.trim().orEmpty()
        if (s.isEmpty()) return null
        if (zonedPattern.matches(s)) {
            val normalized = if (Regex("[+-]\\d{4}$").containsMatchIn(s)) s.substring(0, s.length - 2) + ":" + s.takeLast(2) else s
            runCatching {
                return OffsetDateTime.parse(normalized).atZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime()
            }
        }
        localPattern.find(s)?.let { m ->
            val g = m.groupValues
            return runCatching {
                LocalDateTime.of(g[1].toInt(), g[2].toInt(), g[3].toInt(), g[4].toInt(), g[5].toInt(), g[6].ifEmpty { "0" }.toInt())
            }.getOrNull()
        }
        dayPattern.find(s)?.let { m ->
            val g = m.groupValues
            return runCatching { LocalDate.of(g[1].toInt(), g[2].toInt(), g[3].toInt()).atStartOfDay() }.getOrNull()
        }
        return null
    }

    fun parseDate(value: String?): LocalDate? = parseDateTime(value)?.toLocalDate()

    data class SchoolYear(val start: LocalDate, val end: LocalDate, val startYear: Int)

    /** Anno scolastico che contiene `date` (1 settembre – 31 agosto). */
    fun schoolYear(date: LocalDate = today()): SchoolYear {
        val startYear = if (date.monthValue >= 9) date.year else date.year - 1
        return SchoolYear(LocalDate.of(startYear, 9, 1), LocalDate.of(startYear + 1, 8, 31), startYear)
    }

    /** Data e ora ISO 8601 con il fuso locale. */
    fun isoLocal(dt: LocalDateTime): String =
        dt.atZone(ZoneId.systemDefault()).toOffsetDateTime().withNano(0).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)

    /** Data ISO 8601 in UTC senza millisecondi (come l'app iOS). */
    fun isoUtc(instant: Instant): String = DateTimeFormatter.ISO_INSTANT.format(instant.truncatedTo(ChronoUnit.SECONDS))

    fun parseIsoInstant(value: String?): Instant? {
        if (value.isNullOrBlank()) return null
        return runCatching { Instant.parse(value) }.getOrNull()
            ?: runCatching { OffsetDateTime.parse(value).toInstant() }.getOrNull()
            ?: runCatching { LocalDate.parse(value.take(10)).atStartOfDay(ZoneId.systemDefault()).toInstant() }.getOrNull()
    }

    fun epochMillis(dt: LocalDateTime): Long = dt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    fun fromEpochMillis(ms: Long): LocalDateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(ms), ZoneId.systemDefault())

    fun minuteOfDay(dt: LocalDateTime): Int = dt.hour * 60 + dt.minute
    fun nowMinute(): Int = minuteOfDay(now())

    fun yearMonth(d: LocalDate): YearMonth = YearMonth.from(d)
}

/** Formattazione delle date in italiano, come `fmt` della versione web. */
object Fmt {
    private val cache = HashMap<String, DateTimeFormatter>()
    private fun f(pattern: String): DateTimeFormatter = synchronized(cache) {
        cache.getOrPut(pattern) { DateTimeFormatter.ofPattern(pattern, Dates.italian) }
    }

    /** "Domani", "Oggi", "Ieri" oppure "Lunedì 5 ottobre". */
    fun relativeDayName(d: LocalDate): String = when {
        Dates.isToday(d) -> "Oggi"
        Dates.isTomorrow(d) -> "Domani"
        Dates.isYesterday(d) -> "Ieri"
        else -> longDay(d)
    }

    /** "Gio 1 ott", oppure Oggi/Domani/Ieri. */
    fun relativeShortDayName(d: LocalDate): String =
        if (Dates.isToday(d) || Dates.isTomorrow(d) || Dates.isYesterday(d)) relativeDayName(d)
        else "${weekdayShort(d)} ${d.dayOfMonth} ${monthShort(d)}"

    fun longDay(d: LocalDate): String = d.format(f("EEEE d MMMM")).capFirst()
    fun shortDay(d: LocalDate): String = "${d.dayOfMonth} ${monthShort(d)}"
    fun shortDayWithYear(d: LocalDate): String = "${d.dayOfMonth} ${monthShort(d)} ${d.year}"
    fun weekdayShort(d: LocalDate): String = d.dayOfWeek.getDisplayName(TextStyle.SHORT, Dates.italian).replace(".", "").capFirst()
    fun weekdayLong(d: LocalDate): String = d.dayOfWeek.getDisplayName(TextStyle.FULL, Dates.italian).capFirst()
    fun weekdayLongDay(d: LocalDate): String = "${weekdayLong(d)} ${d.dayOfMonth}"
    fun dayMonth(d: LocalDate): String = d.format(f("d MMMM"))
    fun dayMonthYear(d: LocalDate): String = d.format(f("d MMMM yyyy"))
    fun weekdayNarrow(d: LocalDate): String = d.dayOfWeek.getDisplayName(TextStyle.NARROW, Dates.italian).uppercase(Dates.italian)
    fun monthYear(d: LocalDate): String = d.format(f("MMMM yyyy")).capFirst()
    fun monthLong(d: LocalDate): String = d.format(f("MMMM")).capFirst()
    fun monthShort(d: LocalDate): String = d.month.getDisplayName(TextStyle.SHORT, Dates.italian).replace(".", "")
    fun time(dt: LocalDateTime): String = String.format(Locale.ROOT, "%02d:%02d", dt.hour, dt.minute)
    fun year(d: LocalDate): String = d.year.toString()
}
