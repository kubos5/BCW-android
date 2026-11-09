package com.bcw.classeviva.wear.ui.theme

import com.materialkolor.dynamiccolor.ColorSpec
import com.materialkolor.dynamiccolor.DynamicColor
import com.materialkolor.dynamiccolor.MaterialDynamicColors
import com.materialkolor.hct.Hct
import com.materialkolor.palettes.TonalPalette
import com.materialkolor.scheme.DynamicScheme
import com.materialkolor.scheme.SchemeExpressive
import com.materialkolor.scheme.SchemeFidelity
import com.materialkolor.scheme.SchemeFruitSalad
import com.materialkolor.scheme.SchemeNeutral
import com.materialkolor.scheme.SchemeRainbow
import com.materialkolor.scheme.SchemeTonalSpot
import com.materialkolor.scheme.SchemeVibrant
import kotlin.math.min
import kotlin.math.roundToInt

/** Stili degli schemi dinamici, gli stessi dell'app del telefono. */
enum class ColorStyle(val title: String) {
    tonalSpot("Tonale"),
    vibrant("Vivace"),
    expressive("Espressivo"),
    fidelity("Fedele"),
    neutral("Neutro"),
    rainbow("Arcobaleno"),
    fruitSalad("Macedonia"),
}

enum class ContrastLevel(val title: String, val level: Double) {
    standard("Standard", 0.0),
    medium("Medio", 0.5),
    high("Alto", 1.0),
}

fun normalizeHex(value: String?): String? {
    val text = value?.trim()?.lowercase() ?: return null
    if (Regex("^#[0-9a-f]{6}$").matches(text)) return text
    if (Regex("^#[0-9a-f]{3}$").matches(text)) return "#" + text.drop(1).map { "$it$it" }.joinToString("")
    return null
}

fun argbFromHex(hex: String): Int = (0xFF000000.toInt()) or (hex.removePrefix("#").toInt(16) and 0xFFFFFF)

/** I ruoli di Material 3 di un tema (chiaro o scuro), come ARGB. */
data class RoleColors(val roles: Map<String, Int>) {
    operator fun get(name: String): Int = roles[name] ?: error("Ruolo $name mancante")
}

/**
 * Gli stessi colori dell'app del telefono (`ColorEngine`), così l'orologio segue l'accento scelto
 * su BCW per Android.
 */
object ColorEngine {
    private fun scheme(style: ColorStyle, seed: Hct, dark: Boolean, level: Double, spec: ColorSpec.SpecVersion): DynamicScheme {
        val p = DynamicScheme.Platform.PHONE
        return when (style) {
            ColorStyle.tonalSpot -> SchemeTonalSpot(seed, dark, level, spec, p)
            ColorStyle.vibrant -> SchemeVibrant(seed, dark, level, spec, p)
            ColorStyle.expressive -> SchemeExpressive(seed, dark, level, spec, p)
            ColorStyle.fidelity -> SchemeFidelity(seed, dark, level, spec, p)
            ColorStyle.neutral -> SchemeNeutral(seed, dark, level, spec, p)
            ColorStyle.rainbow -> SchemeRainbow(seed, dark, level, spec, p)
            ColorStyle.fruitSalad -> SchemeFruitSalad(seed, dark, level, spec, p)
        }
    }

    private fun dynamic(c: MaterialDynamicColors): Map<String, DynamicColor> = mapOf(
        "primary" to c.primary(), "on-primary" to c.onPrimary(), "primary-container" to c.primaryContainer(), "on-primary-container" to c.onPrimaryContainer(),
        "secondary" to c.secondary(), "on-secondary" to c.onSecondary(), "secondary-container" to c.secondaryContainer(), "on-secondary-container" to c.onSecondaryContainer(),
        "tertiary" to c.tertiary(), "on-tertiary" to c.onTertiary(), "tertiary-container" to c.tertiaryContainer(), "on-tertiary-container" to c.onTertiaryContainer(),
        "error" to c.error(), "on-error" to c.onError(), "error-container" to c.errorContainer(), "on-error-container" to c.onErrorContainer(),
        "surface" to c.surface(), "surface-dim" to c.surfaceDim(), "surface-bright" to c.surfaceBright(),
        "surface-container-lowest" to c.surfaceContainerLowest(), "surface-container-low" to c.surfaceContainerLow(),
        "surface-container" to c.surfaceContainer(), "surface-container-high" to c.surfaceContainerHigh(),
        "surface-container-highest" to c.surfaceContainerHighest(),
        "on-surface" to c.onSurface(), "on-surface-variant" to c.onSurfaceVariant(), "outline" to c.outline(), "outline-variant" to c.outlineVariant(),
        "inverse-surface" to c.inverseSurface(), "inverse-on-surface" to c.inverseOnSurface(), "inverse-primary" to c.inversePrimary(),
    )

    /** Avvicina un colore al bianco (senza arrivarci: niente sfondi bianchi puri). */
    private fun mixWhite(argb: Int, amount: Double): Int {
        var out = 0xFF000000.toInt()
        for (shift in intArrayOf(16, 8, 0)) {
            val c = (argb shr shift) and 0xFF
            val v = min((c + (255 - c) * amount).roundToInt(), 254)
            out = out or (v shl shift)
        }
        return out
    }

    data class Theme(val light: RoleColors, val dark: RoleColors)

    fun build(accent: String, style: ColorStyle, contrast: ContrastLevel): Theme {
        val seed = Hct.fromInt(argbFromHex(normalizeHex(accent) ?: "#a23e2f"))
        val level = contrast.level
        val colors = dynamic(MaterialDynamicColors())

        fun make(dark: Boolean): Pair<MutableMap<String, Int>, DynamicScheme> {
            val s = scheme(style, seed, dark, level, ColorSpec.SpecVersion.SPEC_2025)
            val legacy = scheme(style, seed, dark, level, ColorSpec.SpecVersion.SPEC_2021)
            val legacyColors = dynamic(MaterialDynamicColors())
            val values = LinkedHashMap<String, Int>()
            for ((name, c) in colors) values[name] = c.getArgb(s)
            values["inverse-surface"] = legacyColors.getValue("inverse-surface").getArgb(legacy)
            values["inverse-on-surface"] = legacyColors.getValue("inverse-on-surface").getArgb(legacy)
            values["surface-container-lowest"] = if (dark) s.neutralPalette.tone(3) else mixWhite(values.getValue("surface"), 0.6)
            if (level == 0.0) {
                values["outline-variant"] = s.neutralVariantPalette.tone(if (dark) 26 else 84)
                if (dark) {
                    for ((role, palette) in listOf("primary" to s.primaryPalette, "secondary" to s.secondaryPalette, "tertiary" to s.tertiaryPalette)) {
                        if (Hct.fromInt(values.getValue("$role-container")).tone > 50) {
                            values["$role-container"] = palette.tone(35)
                            values["on-$role-container"] = palette.tone(90)
                        }
                    }
                    values["on-secondary-container"] = s.secondaryPalette.tone(85)
                    values["on-error-container"] = s.errorPalette.tone(80)
                }
            }
            return values to s
        }

        val (lightValues, lightScheme) = make(false)
        val (darkValues, darkScheme) = make(true)
        darkValues["inverse-primary"] = lightValues.getValue("primary")
        val seedPalette = TonalPalette.fromHct(seed)
        lightValues["brand"] = seedPalette.tone(40)
        darkValues["brand"] = Hct.from(seed.hue, seed.chroma * 0.8, 64.0).toInt()
        lightValues["shadow"] = lightScheme.neutralPalette.tone(6)
        darkValues["shadow"] = darkScheme.neutralPalette.tone(2)
        return Theme(RoleColors(lightValues), RoleColors(darkValues))
    }
}
