package com.bcw.classeviva.wear.data

import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ScheduleTest {
    private fun lesson(id: String, weekday: Int, start: Int, end: Int, break_: Boolean = false) =
        WatchLesson(id = id, weekday = weekday, start = start, end = end, isBreak = break_, title = if (break_) "Intervallo" else id)

    private fun payload(vararg entries: WatchLesson) = WatchPayload(entries = entries.toList())

    private fun at(dayOfWeek: Int, hour: Int, minute: Int): LocalDateTime {
        // Un lunedì fisso: 9 novembre 2026 è lunedì.
        var date = LocalDate.of(2026, 11, 9)
        while (date.dayOfWeek.value != dayOfWeek) date = date.plusDays(1)
        return LocalDateTime.of(date, java.time.LocalTime.of(hour, minute))
    }

    @Test
    fun lezioneInCorso() {
        val p = payload(lesson("a", 1, 600, 660), lesson("b", 1, 660, 720))
        val now = scheduleNow(p, at(1, 10, 30))
        assertEquals("a", now.current?.id)
        assertEquals("b", now.next?.id)
        assertEquals(at(1, 10, 30).toLocalDate(), now.today)
    }

    @Test
    fun intervalloNonContato() {
        val p = payload(lesson("a", 1, 540, 600), lesson("x", 1, 600, 615, break_ = true), lesson("b", 1, 615, 675))
        val now = scheduleNow(p, at(1, 10, 0))
        assertNull(now.current)
        // La pausa non vale come lezione in corso né come prossima.
        assertEquals("b", now.next?.id)
    }

    @Test
    fun prossimaLezioneNeiGiorniSeguenti() {
        val p = payload(lesson("a", 1, 540, 600), lesson("b", 3, 540, 600))
        val now = scheduleNow(p, at(1, 18, 0))
        assertNull(now.current)
        assertEquals("b", now.next?.id)
        assertEquals(at(3, 12, 0).toLocalDate(), now.nextDay)
    }

    @Test
    fun domenicaVuotaPuntaAlLunedì() {
        val p = payload(lesson("a", 1, 540, 600))
        val now = scheduleNow(p, at(7, 15, 0))
        assertEquals("a", now.next?.id)
        assertEquals(at(1, 12, 0).toLocalDate().plusDays(7), now.nextDay)
    }

    @Test
    fun settimanaDaOggi() {
        val p = payload(
            lesson("lun", 1, 540, 600), lesson("mar", 2, 480, 540),
            lesson("mar2", 2, 600, 660), lesson("dom", 7, 540, 600),
        )
        val today = LocalDate.of(2026, 11, 9) // lunedì
        val week = weekFrom(p, today)
        assertEquals(listOf(1, 2, 7), week.map { it.first.dayOfWeek.value })
        assertEquals(listOf("mar", "mar2"), week[1].second.map { it.id })
    }

    @Test
    fun testiDiTempo() {
        assertEquals("08:05", timeString(485))
        assertEquals("00:00", timeString(0))
        assertEquals("45 minuti", durationString(45))
        assertEquals("1 ora", durationString(60))
        assertEquals("1 h 30 min", durationString(90))
        assertEquals("2 ore", durationString(120))
        assertTrue(durationString(0).contains("meno di un minuto"))
    }
}
