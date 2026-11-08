package com.bcw.classeviva.data.timetable

import com.bcw.classeviva.data.DemoTransport
import com.bcw.classeviva.data.parseLessonsResponse
import com.bcw.classeviva.util.Dates
import com.bcw.classeviva.util.parseJson
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.time.LocalDate
import java.time.LocalDateTime

/** L'orario Android deve leggere e scrivere lo stesso JSON del web e dell'app iOS (riferimento da `js/timetable.js`). */
class TimetableParityTest {
    private val ref = parseJson(javaClass.classLoader!!.getResource("tt-ref.json")!!.readText())!!.jsonObject
    private val encoded = ref["encoded"]!!.jsonPrimitive.content

    @Before fun setUp() { Dates.clock = { LocalDateTime.of(2026, 11, 8, 10, 0) } }
    @After fun tearDown() { Dates.clock = { LocalDateTime.now() } }

    @Test fun decodeEncodeIsIdentical() {
        assertTrue(ref["roundtrip"]!!.jsonPrimitive.content == "true")
        val decoded = decodeTimetable(parseJson(encoded)!!)
        assertEquals(encoded, encodeTimetable(decoded).toString())
        assertEquals(7, decoded.entries.size)
        assertEquals(EntryColor.Series("builtin.ocean", 1, 3), decoded.entries[4].color)
        assertEquals(LiveActivityChoice.hide, decoded.entries[6].liveActivity)
        assertEquals(LocalDate.of(2026, 12, 1), decoded.testRules.periods[0].start)
    }

    private fun describe(l: TimetableLayout) = l.listItems.map { i ->
        when (i) {
            is ListItem.Group -> "cluster:${i.cluster.start}-${i.cluster.end}:${i.cluster.columns}:" +
                i.cluster.blocks.joinToString(",") { b -> "${b.column}/${b.block.entries.joinToString("+") { it.id.takeLast(2) }}" }
            is ListItem.Gap -> "gap:${i.start}-${i.end}"
            is ListItem.Pause -> "pause:${i.entry.id.takeLast(2)}"
        }
    }

    @Test fun layoutMatchesWeb() {
        val t = decodeTimetable(parseJson(encoded)!!)
        val day = t.entries.filter { it.weekday == 1 }
        for ((key, split) in listOf("layout" to true, "layoutGrid" to false)) {
            val expected = ref[key]!!.jsonObject
            val layout = timetableLayout(day, split)
            assertEquals(expected["items"]!!.jsonArray.map { it.jsonPrimitive.content }, describe(layout))
            val span = expected["span"]!!.jsonArray.map { it.jsonPrimitive.content.toInt() }
            assertEquals(span, listOf(layout.span!!.first, layout.span!!.last))
        }
    }

    @Test fun generatorMatchesWeb() = runBlocking {
        val response = DemoTransport(latency = false).send("GET", "/students/1/lessons/20260914/20261107", null, null)
        val lessons = parseLessonsResponse(parseJson(response.text)!!)
        val draft = generateEntries(lessons, GenerationSettings(480, 60, 6, listOf(BreakRule("B", 650, 10))), LocalDate.of(2026, 11, 8))
        val expected = ref["gen"]!!.jsonObject
        assertEquals(expected["days"]!!.jsonPrimitive.content.toInt(), draft.analyzedDays)
        val rows = draft.entries.map { listOf(it.weekday.toString(), it.start.toString(), it.end.toString(), it.kind.name, it.title, it.subjectId?.toString() ?: "null", it.teacher) }
        val exp = expected["entries"]!!.jsonArray.map { r -> r.jsonArray.map { (it as? JsonPrimitive)?.content ?: "null" } }
        assertEquals(exp, rows)
    }

    @Test fun piecesPaletteAndSeries() {
        val pieces = TimetableEntry(start = 490, end = 640, title = "X").alignedPieces().map { listOf(it.start, it.end) }
        assertEquals(ref["pieces"]!!.jsonArray.map { r -> r.jsonArray.map { it.jsonPrimitive.content.toInt() } }, pieces)
        val pal = (0 until 5).map { paletteColor(builtInPalettes[1], it, 5) }
        assertEquals(ref["pal"]!!.jsonArray.map { it.jsonPrimitive.content.toInt() }, pal)
        val t = decodeTimetable(parseJson(encoded)!!)
        val (index, count) = seriesPositions(t.entries.filter { it.weekday == 1 })
        val series = ref["series"]!!.jsonObject
        assertEquals(series["index"]!!.jsonArray.map { it.jsonPrimitive.content.toInt() }, index)
        assertEquals(series["count"]!!.jsonPrimitive.content.toInt(), count)
    }

    @Test fun storeSavesBackupAndRestores() = runBlocking {
        val dir = Files.createTempDirectory("tt").toFile()
        val store = TimetableStore(dir)
        store.load("acc-1")
        store.add(listOf(TimetableEntry(weekday = 1, start = 480, end = 540, title = "Matematica")))
        val entry = store.timetable.entries[0]
        store.attach("ciao".toByteArray(), "nota.txt", entry, EditScope.EveryWeek)
        store.setNote("Solo oggi", entry, EditScope.Day(LocalDate.of(2026, 11, 9)))
        val backup = store.makeBackup("3.3")
        val other = TimetableStore(dir)
        other.load("acc-2")
        val parsed = other.readBackup(backup)
        other.restore(parsed)
        assertEquals(1, other.timetable.entries.size)
        assertEquals("Solo oggi", other.occurrence(entry.id, LocalDate.of(2026, 11, 9)).note)
        val stored = other.timetable.entries[0].attachments[0].storedName
        assertEquals("ciao", File(dir, "timetable-files/acc-2/$stored").readText())
        // Un file che non è un backup viene rifiutato.
        val error = runCatching { other.readBackup("{\"format\":\"altro\"}") }.exceptionOrNull()
        assertEquals(BackupError.notABackup, error?.message)
        // Il documento salvato si rilegge da disco.
        val reopened = TimetableStore(dir)
        reopened.load("acc-1")
        assertEquals("Matematica", reopened.timetable.entries[0].title)
        // Allineamento automatico: l'evento non allineato viene diviso.
        reopened.setAlignment(Alignment.free)
        reopened.add(listOf(TimetableEntry(weekday = 2, start = 490, end = 640, title = "Lungo")))
        reopened.setAlignment(Alignment.automatic)
        assertEquals(listOf(480 to 540, 540 to 600, 600 to 630), reopened.entries(2).map { it.start to it.end })
        dir.deleteRecursively()
        Unit
    }

    @Test fun defaultDay() {
        val entries = mapOf(1 to listOf(TimetableEntry(weekday = 1, start = 480, end = 540, title = "A")), 3 to listOf(TimetableEntry(weekday = 3, start = 600, end = 660, title = "B")))
        val of = { w: Int -> entries[w] ?: emptyList() }
        // Domenica 8/11/2026 alle 10: il primo giorno con eventi è lunedì 9.
        assertEquals(LocalDate.of(2026, 11, 9), defaultTimetableDay(of, LocalDateTime.of(2026, 11, 8, 10, 0)))
        // Lunedì alle 8:30 c'è ancora una lezione in corso: oggi.
        assertEquals(LocalDate.of(2026, 11, 9), defaultTimetableDay(of, LocalDateTime.of(2026, 11, 9, 8, 30)))
        // Lunedì alle 9: finita, si passa a mercoledì.
        assertEquals(LocalDate.of(2026, 11, 11), defaultTimetableDay(of, LocalDateTime.of(2026, 11, 9, 9, 0)))
    }
}
