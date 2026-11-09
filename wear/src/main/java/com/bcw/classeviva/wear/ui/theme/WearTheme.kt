package com.bcw.classeviva.wear.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.wear.compose.material3.MaterialTheme
import com.bcw.classeviva.wear.data.Tint
import com.bcw.classeviva.wear.data.WatchPalette
import com.materialkolor.hct.Hct
import kotlin.math.max

/** I toni fissi delle materie, gli stessi dell'app del telefono (non cambiano con l'accento). */
@Immutable
data class SubjectColors(val color: Color, val container: Color, val onContainer: Color)

/** I colori di un tema Wear: ruoli di Material 3 presi da `ColorEngine`, come sul telefono. */
@Immutable
class WearColors(
    val surface: Color,
    val surfaceContainerHigh: Color,
    val onSurface: Color,
    val onSurfaceVariant: Color,
    val isDark: Boolean,
    val subjects: List<SubjectColors>,
    val breakTint: Color,
) {
    fun subject(index: Int) = subjects[((index % 12) + 12) % 12]
}

val LocalWearColors = staticCompositionLocalOf<WearColors> { error("WearColors non impostato") }

private fun c(hex: Long) = Color(0xFF000000 or hex)

private val lightSubjects = listOf(
    0x9e412b to (0xffdad2 to 0x3d0600), 0x29685f to (0xafefe2 to 0x00201c), 0x5357a0 to (0xe1e0ff to 0x0c0e5a),
    0x7c5808 to (0xffdea9 to 0x271900), 0x854a78 to (0xffd7f1 to 0x370531), 0x306388 to (0xcce5ff to 0x001e31),
    0x4c671d to (0xcdee94 to 0x131f00), 0x99452f to (0xffdad2 to 0x3c0800), 0x6b5c4a to (0xf4dfc9 to 0x241a0c),
    0x136779 to (0xadecff to 0x001f26), 0x944555 to (0xffd9de to 0x3e0215), 0x47663c to (0xc8edb7 to 0x042101),
).map { (a, b) -> SubjectColors(c(a.toLong()), c(b.first.toLong()), c(b.second.toLong())) }

private val darkSubjects = listOf(
    0xffb4a3 to (0x7f2a17 to 0xffdad2), 0x94d2c7 to (0x055047 to 0xafefe2), 0xbfc1ff to (0x3b3f86 to 0xe1e0ff),
    0xefbf6a to (0x5e4100 to 0xffdea9), 0xf8b0e4 to (0x6a335f to 0xffd7f1), 0x9cccf6 to (0x114b6f to 0xcce5ff),
    0xb1d27b to (0x354e04 to 0xcdee94), 0xffb4a2 to (0x7b2e1a to 0xffdad2), 0xd7c3ae to (0x524434 to 0xf4dfc9),
    0x8bd1e5 to (0x004e5d to 0xadecff), 0xffb2be to (0x772e3e to 0xffd9de), 0xacd19d to (0x304e26 to 0xc8edb7),
).map { (a, b) -> SubjectColors(c(a.toLong()), c(b.first.toLong()), c(b.second.toLong())) }

private val themeCache = HashMap<Triple<String, ColorStyle, ContrastLevel>, ColorEngine.Theme>()

fun wearColors(accent: String, style: ColorStyle, contrast: ContrastLevel, dark: Boolean, palette: WatchPalette? = null): WearColors {
    val generated = synchronized(themeCache) {
        themeCache.getOrPut(Triple(accent, style, contrast)) { ColorEngine.build(accent, style, contrast) }
    }
    val r = if (dark) generated.dark else generated.light
    val mode = if (dark) palette?.dark else palette?.light
    return WearColors(
        surface = Color(r["surface"]),
        surfaceContainerHigh = Color(r["surface-container-high"]),
        onSurface = Color(r["on-surface"]),
        onSurfaceVariant = Color(r["on-surface-variant"]),
        isDark = dark,
        subjects = mode?.subjects?.map { (color, container, onContainer) ->
            SubjectColors(c(color.toLong()), c(container.toLong()), c(onContainer.toLong()))
        } ?: if (dark) darkSubjects else lightSubjects,
        breakTint = mode?.let { c(it.breakTint.toLong()) } ?: if (dark) c(0xb8ac98) else c(0x9a8c78),
    )
}

@Composable
fun WearAppTheme(
    accent: String,
    colorStyle: String,
    contrast: String,
    appearance: String,
    palette: WatchPalette?,
    content: @Composable () -> Unit,
) {
    val dark = when (appearance) {
        "dark" -> true
        "light" -> false
        else -> isSystemInDarkTheme()
    }
    val style = runCatching { ColorStyle.valueOf(colorStyle) }.getOrDefault(ColorStyle.tonalSpot)
    val level = runCatching { ContrastLevel.valueOf(contrast) }.getOrDefault(ContrastLevel.standard)
    val colors = remember(accent, style, level, dark, palette) { wearColors(accent, style, level, dark, palette) }
    CompositionLocalProvider(LocalWearColors provides colors) {
        MaterialTheme(content = content)
    }
}

// MARK: - Tinte delle lezioni

/** Il colore pieno della materia, per pallini e barre. */
fun Tint.accent(colors: WearColors): Color = when (this) {
    is Tint.Subject -> colors.subject(index).color
    Tint.Break -> colors.breakTint
    is Tint.Hex -> Color(0xFF000000.toInt() or (if (colors.isDark) dark else light))
}

/** Lo sfondo della schermata: il contenitore della materia in corso. */
fun Tint.container(colors: WearColors): Color = when (this) {
    is Tint.Subject -> colors.subject(index).container
    Tint.Break -> colors.surfaceContainerHigh
    is Tint.Hex -> customTone(if (colors.isDark) dark else light, if (colors.isDark) 34.0 else 90.0)
}

fun Tint.onContainer(colors: WearColors): Color = when (this) {
    is Tint.Subject -> colors.subject(index).onContainer
    Tint.Break -> colors.onSurface
    is Tint.Hex -> customTone(if (colors.isDark) dark else light, if (colors.isDark) 92.0 else 12.0)
}

private fun customTone(rgb: Int, tone: Double): Color {
    val hct = Hct.fromInt(0xFF000000.toInt() or (rgb and 0xFFFFFF))
    return Color(Hct.from(hct.hue, max(12.0, hct.chroma), tone).toInt())
}
