package com.bcw.classeviva.data

import com.bcw.classeviva.util.Dates
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/** Esportazione dell'agenda in formato iCalendar (.ics), con l'avviso la sera prima. */
object CalendarExport {
    private val utcFormat = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC)

    private fun utc(dt: LocalDateTime) = utcFormat.format(dt.atZone(ZoneId.systemDefault()).toInstant())
    private fun dateOnly(d: java.time.LocalDate) = Dates.dayKey(d).replace("-", "")
    private fun escape(text: String?) = (text ?: "").replace("\\", "\\\\").replace(";", "\\;").replace(",", "\\,").replace(Regex("\r?\n"), Regex.escapeReplacement("\\n"))

    /** Le righe più lunghe di 75 byte vanno spezzate (RFC 5545). */
    private fun fold(line: String): String {
        val parts = mutableListOf<String>()
        val current = StringBuilder()
        var bytes = 0
        var i = 0
        while (i < line.length) {
            val cp = line.codePointAt(i)
            val ch = String(Character.toChars(cp))
            val size = ch.toByteArray(Charsets.UTF_8).size
            if (bytes + size > 73) {
                parts += current.toString()
                current.setLength(0)
                current.append(' ')
                bytes = 1
            }
            current.append(ch)
            bytes += size
            i += Character.charCount(cp)
        }
        parts += current.toString()
        return parts.joinToString("\r\n")
    }

    private fun title(e: AgendaEvent): String {
        val prefix = when (e.kind) { AgendaKind.test -> "Verifica"; AgendaKind.homework -> "Compiti"; else -> "" }
        return listOf(prefix, e.title).filter { it.isNotEmpty() }.joinToString(" · ")
    }

    fun makeICS(events: List<AgendaEvent>, reminderHour: Int? = null, name: String = "BCW"): String {
        val lines = mutableListOf("BEGIN:VCALENDAR", "VERSION:2.0", "PRODID:-//BCW//Better ClasseViVa//IT", "CALSCALE:GREGORIAN", "X-WR-CALNAME:${escape(name)}")
        val stamp = utcFormat.format(Instant.now())
        for (e in events) {
            val allDay = e.isFullDay || e.kind == AgendaKind.homework
            lines += listOf("BEGIN:VEVENT", "UID:bcw-${e.id}@bcw", "DTSTAMP:$stamp", "SUMMARY:${escape(title(e))}", "DESCRIPTION:${escape(e.notes)}")
            if (allDay) {
                lines += "DTSTART;VALUE=DATE:${dateOnly(e.day)}"
                lines += "DTEND;VALUE=DATE:${dateOnly(e.day.plusDays(1))}"
            } else {
                val end = if (e.end.isAfter(e.begin)) e.end else e.begin.plusHours(1)
                lines += "DTSTART:${utc(e.begin)}"
                lines += "DTEND:${utc(end)}"
            }
            if (reminderHour != null && e.kind != AgendaKind.event) {
                val fire = e.day.minusDays(1).atTime(reminderHour, 0)
                lines += listOf("BEGIN:VALARM", "ACTION:DISPLAY", "DESCRIPTION:${escape(title(e))}", "TRIGGER;VALUE=DATE-TIME:${utc(fire)}", "END:VALARM")
            }
            lines += "END:VEVENT"
        }
        lines += "END:VCALENDAR"
        return lines.joinToString("\r\n") { fold(it) } + "\r\n"
    }
}
