package com.bcw.classeviva.data

import com.bcw.classeviva.util.Dates
import com.bcw.classeviva.util.parseJson
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Compiti del modulo Compiti di Classeviva (issue 16 di kubos5/BCW): decodifica, evento dell'agenda,
 * unione con `agenda/all` e dati della demo. I riferimenti vengono dal modello `Homework.swift` di iOS.
 */
class HomeworkModuleTest {
    @Before fun setUp() { Dates.clock = { LocalDateTime.of(2026, 11, 8, 10, 0) } }
    @After fun tearDown() { Dates.clock = { LocalDateTime.now() } }

    private val sample = """
        {
          "evtId": 1234, "evtCode": "NEWDC", "teacherName": "ROSSI GIORGIO",
          "homeworkDesc": "  Esercizi pag. 20  ", "assignmentDate": "2026-11-05",
          "expiryDate": "2026-11-10", "expiryTime": "14:30",
          "homeworkMarkedDone": true, "homeworkDone": false,
          "subjectId": 103, "subjectDesc": "MATEMATICA",
          "teacherFiles": [{"fileId": 1, "fileName": "scheda.pdf"}],
          "teacherLinks": [{"linkId": 2}]
        }
    """.trimIndent()

    private fun json(text: String) = parseJson(text)!!.jsonObject

    @Test fun parsesHomework() {
        val h = parseHomework(json(sample))
        assertNotNull(h)
        h!!
        assertEquals(1234L, h.id)
        assertEquals("Esercizi pag. 20", h.text)
        assertEquals("Rossi Giorgio", h.teacherName)
        assertEquals(103L, h.subjectId)
        assertEquals("Matematica", h.subjectName)
        assertEquals(LocalDate.of(2026, 11, 5), h.assignedOn)
        assertEquals(LocalDateTime.of(2026, 11, 10, 14, 30), h.due)
        assertTrue(h.hasDueTime)
        assertTrue(h.isDone)
        assertEquals(2, h.attachments)
    }

    @Test fun withoutDueTimeIsAllDay() {
        val h = parseHomework(json(sample.replace("\"expiryTime\": \"14:30\",", "\"expiryTime\": null,")))!!
        assertEquals(LocalDateTime.of(2026, 11, 10, 0, 0), h.due)
        assertFalse(h.hasDueTime)
    }

    @Test fun homeworkWithoutDueDateIsSkipped() {
        assertEquals(null, parseHomework(json(sample.replace("\"expiryDate\": \"2026-11-10\",", ""))))
        assertEquals(emptyList<Homework>(), parseHomeworksResponse(json("""{"items":[{"homeworkDesc":"senza data"}]}""")))
    }

    @Test fun responseAcceptsItemsAndHomeworks() {
        val items = parseJson("""{"items":[$sample]}""")!!
        val list = parseJson("""{"homeworks":[$sample]}""")!!
        assertEquals(1, parseHomeworksResponse(items).size)
        assertEquals(1, parseHomeworksResponse(list).size)
        assertTrue(isValidHomeworks(items))
        assertTrue(isValidHomeworks(list))
        assertFalse(isValidHomeworks(parseJson("""{"agenda":[]}""")))
        assertFalse(isValidHomeworks(parseJson("<html>accesso</html>")))
    }

    @Test fun eventKeepsTheHomeworkInTheAgenda() {
        val h = parseHomework(json(sample))!!
        val e = homeworkEvent(h)
        assertEquals(-1234L, e.id)
        assertEquals(HOMEWORK_MODULE_CODE, e.code)
        assertTrue(e.isFromHomeworkModule)
        assertEquals(AgendaKind.homework, e.kind)
        assertEquals(h.due, e.begin)
        assertFalse(e.isFullDay)
        assertEquals(2, e.attachments)
        assertEquals(1234L, e.homeworkId)
    }

    @Test fun mergeDoesNotRepeatHomeworkAlreadyInTheAgenda() {
        val day = LocalDate.of(2026, 11, 10)
        val h = parseHomework(json(sample))!!
        val fromModule = homeworkEvent(h)
        val agendaEvent = AgendaEvent(
            id = 99, code = "AGHW", begin = day.atTime(8, 0), end = day.atTime(8, 0), isFullDay = false,
            notes = "Esercizi pag. 20", authorName = "Rossi Giorgio", classDescription = null, subjectId = 103,
            subjectName = "Matematica", homeworkId = 1234L, kind = AgendaKind.homework,
        )
        val other = agendaEvent.copy(id = 100, homeworkId = null, notes = "Altra cosa")
        val merged = mergeAgenda(listOf(agendaEvent, other), listOf(fromModule))
        assertEquals(listOf(99L, 100L), merged.map { it.id })
    }

    @Test fun demoHasHomeworkModuleEvents() = runBlocking {
        val response = DemoTransport(latency = false).send("GET", "/students/1234567/homeworks/index", null, "t")
        val items = parseHomeworksResponse(parseJson(response.text)!!)
        assertTrue("La demo non ha compiti del modulo Compiti", items.size >= 5)
        assertTrue(items.any { it.attachments > 0 })
        assertTrue(items.any { it.isDone })
        val events = items.map(::homeworkEvent)
        assertTrue(events.all { it.isFromHomeworkModule && it.kind == AgendaKind.homework })
        assertTrue(events.all { it.id < 0 })
    }
}
