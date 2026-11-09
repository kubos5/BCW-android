package com.bcw.classeviva.wear.data

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** Cosa mostrare in cima: la lezione in corso o, se non c'è, quella che viene dopo. */
data class ScheduleNow(
    val current: WatchLesson?,
    val next: WatchLesson?,
    val nextDay: LocalDate?,
    val today: LocalDate,
    val minute: Int,
) {
    val empty: Boolean get() = current == null && next == null
}

fun lessonsOf(payload: WatchPayload, date: LocalDate): List<WatchLesson> =
    payload.entries.filter { it.weekday == date.dayOfWeek.value }

fun lessonsOnly(payload: WatchPayload, date: LocalDate): List<WatchLesson> =
    lessonsOf(payload, date).filter { !it.isBreak }

/** La lezione in corso, la prossima di oggi e, se oggi è finita, la prima dei giorni successivi. */
fun scheduleNow(payload: WatchPayload, now: LocalDateTime): ScheduleNow {
    val today = now.toLocalDate()
    val minute = minuteOfDay(now.hour, now.minute)
    val lessons = lessonsOnly(payload, today).sortedWith(compareBy({ it.start }, { it.end }))
    val current = lessons.firstOrNull { minute >= it.start && minute < it.end }
    if (current != null) {
        val next = lessons.firstOrNull { it.start >= current.end }
        return ScheduleNow(current, next, if (next != null) today else null, today, minute)
    }
    val next = lessons.firstOrNull { it.start > minute }
    if (next != null) return ScheduleNow(null, next, today, today, minute)
    for (offset in 1..7) {
        val day = today.plusDays(offset.toLong())
        val first = lessonsOnly(payload, day).minByOrNull { it.start }
        if (first != null) return ScheduleNow(null, first, day, today, minute)
    }
    return ScheduleNow(null, null, null, today, minute)
}

/**
 * La settimana a partire da oggi (oggi, domani, …), con le lezioni di ciascun giorno in ordine.
 * Serve alla schermata "Tutto l'orario".
 */
fun weekFrom(payload: WatchPayload, today: LocalDate): List<Pair<LocalDate, List<WatchLesson>>> =
    (0..6).map { offset ->
        val day = today.plusDays(offset.toLong())
        day to lessonsOf(payload, day).sortedWith(compareBy({ it.start }, { it.end }))
    }.filter { it.second.isNotEmpty() }

fun nowLocalDateTime(): LocalDateTime = LocalDateTime.now()

fun minuteOf(now: LocalDateTime): Int = now.hour * 60 + now.minute

fun LocalDateTime.toMinute(): Int = minuteOf(this)

fun LocalDate.atMinutes(minutes: Int): LocalDateTime = atTime(LocalTime.of(minutes / 60, minutes % 60))
