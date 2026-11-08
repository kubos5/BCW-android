package com.bcw.classeviva.data

import com.bcw.classeviva.util.Dates
import com.bcw.classeviva.util.SeededRandom
import com.bcw.classeviva.util.StableID
import com.bcw.classeviva.util.parseJson
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.time.LocalDateTime

/**
 * I dati della demo e i generatori (SeededRandom, StableID) devono essere identici a quelli della
 * versione web: il riferimento `demo-ref.json` è prodotto da `js/demo.js` con TZ=Europe/Rome l'8/11/2026.
 */
class DemoParityTest {
    private val ref = parseJson(javaClass.classLoader!!.getResource("demo-ref.json")!!.readText())!!.jsonObject

    @Before fun setUp() { Dates.clock = { LocalDateTime.of(2026, 11, 8, 10, 0) } }
    @After fun tearDown() { Dates.clock = { LocalDateTime.now() } }

    private fun prim(v: Any?) = when (v) {
        null -> "null"
        is JsonPrimitive -> v.longOrNull?.toString() ?: v.doubleOrNull?.let { if (it == Math.floor(it)) it.toLong().toString() else it.toString() } ?: v.content
        else -> v.toString()
    }

    private fun send(path: String): JsonObject = runBlocking {
        DemoTransport(latency = false).send("GET", "/students/1234567/$path", null, "t").text.let { parseJson(it)!!.jsonObject }
    }

    @Test fun seededRandomMatchesWeb() {
        val r = SeededRandom(2026)
        val values = (0 until 10).map { r.next(1000).toString() }
        assertEquals(ref["rng"]!!.jsonArray.map { prim(it) }, values)
    }

    @Test fun stableIdMatchesWeb() {
        val ids = listOf(StableID.make("a", "b", "c"), StableID.make("Matematica|8|2026-10-01"), StableID.make("AGNT", "Compito in classe", "2026-10-05"))
        assertEquals(ref["stable"]!!.jsonArray.map { prim(it) }, ids.map { it.toString() })
    }

    @Test fun gradesMatchWeb() {
        val grades = send("grades")["grades"]!!.jsonArray.take(40).map { g ->
            val o = g.jsonObject
            listOf("evtId", "subjectId", "evtDate", "decimalValue", "displayValue", "notesForFamily", "color", "evtCode").map { prim(o[it]) }
        }
        val expected = ref["grades"]!!.jsonArray.map { row -> row.jsonArray.map { prim(it) } }
        assertEquals(expected, grades)
    }

    @Test fun agendaMatchesWeb() {
        val agenda = send("agenda/all/20261001/20261130")["agenda"]!!.jsonArray.map { e ->
            val o = e.jsonObject
            listOf(prim(o["evtId"]), prim(o["evtCode"]), prim(o["notes"]), prim(o["subjectId"]), o["evtDatetimeBegin"]!!.jsonPrimitive.content.take(16))
        }
        val expected = ref["agenda"]!!.jsonArray.map { row -> row.jsonArray.map { prim(it) } }
        assertEquals(expected, agenda)
    }

    @Test fun lessonsMatchWeb() {
        val lessons = send("lessons/20261005/20261009")["lessons"]!!.jsonArray.map { l ->
            val o = l.jsonObject
            listOf("evtId", "evtHPos", "evtDuration", "subjectId", "lessonType", "lessonArg").map { prim(o[it]) }
        }
        assertEquals(ref["lessons"]!!.jsonArray.map { row -> row.jsonArray.map { prim(it) } }, lessons)
    }

    @Test fun absencesMatchWeb() {
        val list = send("absences/details")["events"]!!.jsonArray.map { a ->
            val o = a.jsonObject
            listOf("evtId", "evtCode", "evtDate", "isJustified").map { prim(o[it]) }
        }
        assertEquals((ref["absences"] as JsonArray).map { row -> row.jsonArray.map { prim(it) } }, list)
    }
}
