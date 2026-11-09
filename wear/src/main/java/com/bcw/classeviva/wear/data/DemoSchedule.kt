package com.bcw.classeviva.wear.data

import java.time.LocalDate

/**
 * Orario di esempio, usato finché il telefono non manda il suo. Stessi nomi delle materie della
 * demo di BCW, così l'orologio non sembra un'app diversa.
 */
fun demoPayload(): WatchPayload {
    val subjects = listOf(
        "Matematica" to 3, "Italiano" to 0, "Storia" to 1, "Fisica" to 4,
        "Inglese" to 5, "Scienze naturali" to 6, "Filosofia" to 2, "Disegno e arte" to 7,
    )
    val rooms = listOf("A1", "A1", "A1", "Lab", "A1", "Lab", "A1", "Aula")
    val slots = listOf(480 to 540, 540 to 600, 600 to 615, 615 to 675, 675 to 735, 735 to 795)
    val entries = mutableListOf<WatchLesson>()
    for (weekday in 1..5) {
        var index = (weekday - 1) * 2
        for ((slot, times) in slots.withIndex()) {
            val (start, end) = times
            if (slot == 2) {
                entries += WatchLesson(
                    id = "demo-$weekday-break", weekday = weekday, start = start, end = end,
                    isBreak = true, title = "Intervallo", tint = Tint.Break,
                )
                continue
            }
            val (title, tint) = subjects[index % subjects.size]
            entries += WatchLesson(
                id = "demo-$weekday-$slot", weekday = weekday, start = start, end = end,
                title = title, room = rooms[index % rooms.size], tint = Tint.Subject(tint),
            )
            index += 1
        }
    }
    return WatchPayload(entries = entries, demo = true)
}

fun weekdayName(date: LocalDate): String = listOf(
    "Lunedì", "Martedì", "Mercoledì", "Giovedì", "Venerdì", "Sabato", "Domenica",
)[date.dayOfWeek.value - 1]

private val months = listOf(
    "gen", "feb", "mar", "apr", "mag", "giu", "lug", "ago", "set", "ott", "nov", "dic",
)

fun shortDate(date: LocalDate): String = "${date.dayOfMonth} ${months[date.monthValue - 1]}"

fun dayLabel(date: LocalDate, today: LocalDate): String = when (date) {
    today -> "Oggi"
    today.plusDays(1) -> "Domani"
    else -> weekdayName(date)
}
