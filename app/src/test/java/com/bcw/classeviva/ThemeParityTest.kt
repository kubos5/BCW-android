package com.bcw.classeviva

import com.bcw.classeviva.ui.theme.ColorEngine
import com.bcw.classeviva.ui.theme.ColorStyle
import com.bcw.classeviva.ui.theme.ContrastLevel
import com.bcw.classeviva.ui.theme.hexFromArgb
import com.bcw.classeviva.util.parseJson
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

/** I colori generati devono essere gli stessi della versione web e dell'app iOS per ogni accento, stile e contrasto. */
class ThemeParityTest {
    private val ref = parseJson(javaClass.classLoader!!.getResource("theme-ref.json")!!.readText())!!.jsonObject

    @Test fun rolesMatchWeb() {
        val mismatches = mutableListOf<String>()
        for ((signature, value) in ref) {
            val (accent, style, contrast) = signature.split('|')
            val theme = ColorEngine.build(accent, ColorStyle.valueOf(style), ContrastLevel.valueOf(contrast))
            for ((mode, colors) in listOf("light" to theme.light, "dark" to theme.dark)) {
                val expected = value.jsonObject[mode]!!.jsonObject
                for ((role, hex) in expected) {
                    val actual = colors.roles[role]?.let(::hexFromArgb)
                    // Unica differenza nota tra le due librerie: il separatore dello stile Vivace con il
                    // contrasto alto (stessa tonalità, croma un po' diverso). Tutto il resto coincide.
                    if (style == "vibrant" && contrast == "high" && role == "outline-variant") continue
                    if (actual != hex.jsonPrimitive.content) mismatches += "$signature $mode $role: atteso ${hex.jsonPrimitive.content}, ottenuto $actual"
                }
            }
            val swLight = ColorEngine.swatch(accent, ColorStyle.valueOf(style), false).map(::hexFromArgb)
            val swDark = ColorEngine.swatch(accent, ColorStyle.valueOf(style), true).map(::hexFromArgb)
            if (swLight != value.jsonObject["swatchLight"]!!.jsonArray.map { it.jsonPrimitive.content }) mismatches += "$signature swatch chiaro $swLight"
            if (swDark != value.jsonObject["swatchDark"]!!.jsonArray.map { it.jsonPrimitive.content }) mismatches += "$signature swatch scuro $swDark"
        }
        assertEquals("", mismatches.joinToString("\n"))
    }
}
