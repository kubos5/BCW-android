package com.bcw.classeviva.data

import com.bcw.classeviva.ui.components.ReleaseBlock
import com.bcw.classeviva.ui.components.inlineSpans
import com.bcw.classeviva.ui.components.releaseBlocks
import com.bcw.classeviva.util.parseJson
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdatesTest {
    @Test fun versionsCompareByParts() {
        assertTrue(compareVersions("3.3.1", "3.3") > 0)
        assertTrue(compareVersions("3.3", "4.0") < 0)
        assertEquals(0, compareVersions("v3.3.1", "3.3.1"))
        assertEquals(0, compareVersions("3.3.1-debug", "3.3.1"))
        assertTrue(compareVersions("3.10", "3.9") > 0)
    }

    @Test fun releaseFromGitHub() {
        val json = parseJson(
            """{"tag_name":"v4.0","body":"Novità","html_url":"https://github.com/kubos5/BCW-android/releases/tag/v4.0",
            "published_at":"2026-11-16T00:55:36Z","draft":false,"prerelease":false,
            "assets":[{"name":"BCW-4.0.apk","browser_download_url":"https://example.com/BCW-4.0.apk","size":6000000,
            "digest":"sha256:abc123"}]}""",
        )!!.jsonObject
        val release = parseRelease(json)!!
        assertEquals("4.0", release.version)
        assertEquals("Novità", release.notes)
        assertEquals("BCW-4.0.apk", release.apk?.name)
        assertEquals("abc123", release.apk?.sha256)
    }

    @Test fun publishedSkipsDraftsAndPrereleases() {
        val releases = listOf("3.3", "3.3.1", "4.0").mapIndexed { i, tag ->
            ReleaseInfo(tag = "v$tag", notes = "", url = "", publishedAt = null,
                draft = i == 2, prerelease = false, assets = emptyList())
        }
        assertEquals(listOf("v3.3.1", "v3.3"), releases.published().map { it.tag })
    }

    @Test fun checkIntervalAndSnooze() {
        val now = 1_000_000_000L
        assertTrue(UpdateCenter.checkDue(0L, UpdateCenter.CHECK_INTERVAL_MS, now))
        assertFalse(UpdateCenter.checkDue(now - UpdateCenter.CHECK_INTERVAL_MS + 1, UpdateCenter.CHECK_INTERVAL_MS, now))
        assertTrue(UpdateCenter.checkDue(now - UpdateCenter.CHECK_INTERVAL_MS, UpdateCenter.CHECK_INTERVAL_MS, now))
        assertTrue(UpdateCenter.isSnoozed("v4.0", "v4.0", now + UpdateCenter.SNOOZE_MS, now))
        assertFalse(UpdateCenter.isSnoozed("v4.0", null, now + UpdateCenter.SNOOZE_MS, now))
        assertFalse(UpdateCenter.isSnoozed("v4.0", "v4.0", now, now))
    }

    @Test fun markdownBlocks() {
        val blocks = releaseBlocks(
            """
            # BCW v3.3.1
            Aggiornamento alla **3.3.1**.

            - Una voce
            1. Primo
            > Una citazione
            """.trimIndent(),
        )
        assertEquals(ReleaseBlock.Heading(1, "BCW v3.3.1"), blocks[0])
        assertEquals(ReleaseBlock.Paragraph("Aggiornamento alla **3.3.1**."), blocks[1])
        assertEquals(ReleaseBlock.Bullet("Una voce"), blocks[2])
        assertEquals(ReleaseBlock.Numbered("1", "Primo"), blocks[3])
        assertEquals(ReleaseBlock.Quote("Una citazione"), blocks[4])
    }

    @Test fun inlineStyles() {
        val spans = inlineSpans("Testo **forte** e `codice` con [link](https://x.y).")
        assertEquals("forte", spans[1].text)
        assertTrue(spans[1].bold)
        assertEquals("codice", spans[3].text)
        assertTrue(spans[3].code)
        assertEquals("link", spans[5].text)
        assertFalse(spans[5].bold)
        assertNull(spans.firstOrNull { it.italic })
    }
}
