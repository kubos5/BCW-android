package com.bcw.classeviva.wear.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.wear.compose.material3.ColorScheme
import androidx.wear.compose.material3.MaterialTheme
import com.bcw.classeviva.wear.data.Tint
import com.bcw.classeviva.wear.data.WatchPalette
import com.bcw.classeviva.wear.data.WatchPayload
import com.bcw.classeviva.wear.data.WatchScheme
import com.materialkolor.hct.Hct
import kotlin.math.max

/** I toni fissi delle materie, gli stessi dell'app del telefono (non cambiano con l'accento). */
@Immutable
data class SubjectColors(val color: Color, val container: Color, val onContainer: Color)

/**
 * I colori di un tema Wear: i ruoli di Material 3 del telefono (dal payload, compresi colori del
 * sistema e contrasto) o, senza, quelli generati da `ColorEngine` con lo stesso accento.
 */
@Immutable
class WearColors(
    private val roles: Map<String, Int>,
    val isDark: Boolean,
    val subjects: List<SubjectColors>,
    val breakTint: Color,
) {
    fun role(name: String): Color = Color(0xFF000000.toInt() or (roles[name] ?: 0x808080))
    val surface get() = role("surface")
    val surfaceContainer get() = role("surface-container")
    val surfaceContainerHigh get() = role("surface-container-high")
    val onSurface get() = role("on-surface")
    val onSurfaceVariant get() = role("on-surface-variant")
    val primary get() = role("primary")
    val onPrimary get() = role("on-primary")
    fun subject(index: Int) = subjects[((index % 12) + 12) % 12]

    /** Lo schema di Wear Material 3 con i colori del telefono: pulsanti, ora e indicatori li seguono. */
    fun wearScheme(): ColorScheme = ColorScheme(
        primary = role("primary"), primaryDim = role("primary"), primaryContainer = role("primary-container"),
        onPrimary = role("on-primary"), onPrimaryContainer = role("on-primary-container"),
        secondary = role("secondary"), secondaryDim = role("secondary"), secondaryContainer = role("secondary-container"),
        onSecondary = role("on-secondary"), onSecondaryContainer = role("on-secondary-container"),
        tertiary = role("tertiary"), tertiaryDim = role("tertiary"), tertiaryContainer = role("tertiary-container"),
        onTertiary = role("on-tertiary"), onTertiaryContainer = role("on-tertiary-container"),
        surfaceContainerLow = role("surface-container-low"), surfaceContainer = role("surface-container"),
        surfaceContainerHigh = role("surface-container-high"),
        onSurface = role("on-surface"), onSurfaceVariant = role("on-surface-variant"),
        outline = role("outline"), outlineVariant = role("outline-variant"),
        background = role("surface"), onBackground = role("on-surface"),
        error = role("error"), errorDim = role("error"), errorContainer = role("error-container"),
        onError = role("on-error"), onErrorContainer = role("on-error-container"),
    )
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

fun wearColors(
    accent: String,
    style: ColorStyle,
    contrast: ContrastLevel,
    dark: Boolean,
    palette: WatchPalette? = null,
    scheme: WatchScheme? = null,
): WearColors {
    val roles = (if (dark) scheme?.dark else scheme?.light) ?: run {
        val generated = synchronized(themeCache) {
            themeCache.getOrPut(Triple(accent, style, contrast)) { ColorEngine.build(accent, style, contrast) }
        }
        (if (dark) generated.dark else generated.light).roles.mapValues { it.value and 0xFFFFFF }
    }
    val mode = if (dark) palette?.dark else palette?.light
    return WearColors(
        roles = roles,
        isDark = dark,
        subjects = mode?.subjects?.map { (color, container, onContainer) ->
            SubjectColors(c(color.toLong()), c(container.toLong()), c(onContainer.toLong()))
        } ?: if (dark) darkSubjects else lightSubjects,
        breakTint = mode?.let { c(it.breakTint.toLong()) } ?: if (dark) c(0xb8ac98) else c(0x9a8c78),
    )
}

/**
 * Il tema dell'orologio. Chiaro o scuro come il telefono: "Chiaro" e "Scuro" valgono come scelti,
 * "Automatico" segue il tema che il telefono usa adesso (`payload.dark`); senza telefono, quello
 * dell'orologio. Colori e caratteri di Wear Material 3 Expressive, con lo schema del telefono.
 */
@Composable
fun WearAppTheme(payload: WatchPayload, content: @Composable () -> Unit) {
    val systemDark = isSystemInDarkTheme()
    val dark = when (payload.appearance) {
        "dark" -> true
        "light" -> false
        else -> payload.dark ?: systemDark
    }
    val style = runCatching { ColorStyle.valueOf(payload.colorStyle) }.getOrDefault(ColorStyle.tonalSpot)
    val level = runCatching { ContrastLevel.valueOf(payload.contrast) }.getOrDefault(ContrastLevel.standard)
    val colors = remember(payload.accent, style, level, dark, payload.palette, payload.scheme) {
        wearColors(payload.accent, style, level, dark, payload.palette, payload.scheme)
    }
    CompositionLocalProvider(LocalWearColors provides colors) {
        MaterialTheme(colorScheme = colors.wearScheme(), content = content)
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
