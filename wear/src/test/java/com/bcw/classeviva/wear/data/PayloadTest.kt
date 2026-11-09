package com.bcw.classeviva.wear.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PayloadTest {
    @Test
    fun payloadCompleto() {
        val subjects = (0 until 12).joinToString(",") { "[${100 + it},${200 + it},${300 + it}]" }
        val json = """
            {
              "version": 2,
              "owner": "abc",
              "accent": "#a23e2f",
              "colorStyle": "vibrant",
              "contrast": "high",
              "appearance": "dark",
              "updatedAt": 1234,
              "palette": {
                "light": {
                  "subjects": [$subjects],
                  "break": 42
                },
                "dark": {
                  "subjects": [$subjects],
                  "break": 43
                }
              },
              "entries": [
                {"id": "1", "weekday": 1, "start": 480, "end": 540, "kind": "lesson", "title": "Mate", "room": "A1", "teacher": "Rossi", "tint": {"kind": "subject", "index": 3}},
                {"id": "2", "weekday": 1, "start": 600, "end": 615, "kind": "breakTime", "title": "Intervallo", "tint": {"kind": "break"}},
                {"id": "3", "weekday": 2, "start": 480, "end": 540, "kind": "lesson", "title": "Arte", "tint": {"kind": "hex", "light": 65280, "dark": 255}}
              ]
            }
        """.trimIndent()
        val payload = parsePayload(json)!!
        assertEquals(3, payload.entries.size)
        assertEquals("Mate", payload.entries[0].title)
        assertEquals("A1", payload.entries[0].room)
        assertIs<Tint.Subject>(payload.entries[0].tint).let { assertEquals(3, it.index) }
        assertIs<Tint.Break>(payload.entries[1].tint)
        val hex = assertIs<Tint.Hex>(payload.entries[2].tint)
        assertEquals(65280, hex.light)
        assertEquals(255, hex.dark)
        assertEquals("dark", payload.appearance)
        assertEquals(1234, payload.updatedAt)
        val palette = payload.palette!!
        assertEquals(12, palette.light.subjects.size)
        assertEquals(42, palette.light.breakTint)
        assertEquals(43, palette.dark.breakTint)
        assertTrue(payload.entries[1].isBreak)
        assertEquals(60, payload.entries[0].duration)
    }

    @Test
    fun righeNonValideScartate() {
        val json = """
            {"entries": [
              {"weekday": 9, "start": 0, "end": 60},
              {"weekday": 1, "start": 2000, "end": 2100},
              {"weekday": 1, "start": 480, "end": 540, "title": "Ok"}
            ]}
        """.trimIndent()
        val payload = parsePayload(json)!!
        assertEquals(1, payload.entries.size)
        assertEquals("Ok", payload.entries[0].title)
    }

    @Test
    fun jsonRottoNessunCrash() {
        assertNull(parsePayload("{ non json"))
        assertNull(parsePayload("[]"))
    }

    @Test
    fun valoriMancantiConPredefiniti() {
        val payload = parsePayload("""{"entries": []}""")!!
        assertEquals(DEFAULT_ACCENT, payload.accent)
        assertEquals("tonalSpot", payload.colorStyle)
        assertEquals("standard", payload.contrast)
        assertEquals("system", payload.appearance)
        assertEquals(0, payload.updatedAt)
        assertNull(payload.palette)
    }
}
