package com.bcw.classeviva.ui.theme

import android.os.Build
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.bcw.classeviva.R
import com.bcw.classeviva.data.AppearanceMode
import com.bcw.classeviva.data.DEFAULT_ACCENT
import com.bcw.classeviva.data.Preferences

enum class CornerStyle(val title: String) { square("Meno arrotondati"), standard("Standard"), round("Più arrotondati") }
enum class MotionStyle(val title: String) { expressive("Espressive"), standard("Standard"), reduced("Ridotte") }
enum class RoundedText(val title: String) { titles("Solo i titoli"), all("Tutto il testo"), none("Nessuno") }

/** Le scelte di Impostazioni › Aspetto che decidono il tema. */
@Immutable
data class ThemeSettings(
    val appearance: AppearanceMode = AppearanceMode.system,
    val accent: String = DEFAULT_ACCENT,
    val style: ColorStyle = ColorStyle.tonalSpot,
    val contrast: ContrastLevel = ContrastLevel.standard,
    val corners: CornerStyle = CornerStyle.standard,
    val motion: MotionStyle = MotionStyle.expressive,
    val roundedText: RoundedText = RoundedText.titles,
    val dynamicColor: Boolean = false,
    /** In bianco e nero (prima apertura, finché non si sceglie un colore nell'introduzione). */
    val monochrome: Boolean = false,
) {
    companion object {
        fun from(p: Preferences) = ThemeSettings(
            appearance = p.appearance,
            accent = normalizeHex(p.string("accentColor")) ?: DEFAULT_ACCENT,
            style = runCatching { ColorStyle.valueOf(p.string("colorStyle")) }.getOrDefault(ColorStyle.tonalSpot),
            contrast = runCatching { ContrastLevel.valueOf(p.string("contrast")) }.getOrDefault(ContrastLevel.standard),
            corners = runCatching { CornerStyle.valueOf(p.string("corners")) }.getOrDefault(CornerStyle.standard),
            motion = runCatching { MotionStyle.valueOf(p.string("motion")) }.getOrDefault(MotionStyle.expressive),
            roundedText = runCatching { RoundedText.valueOf(p.string("roundedText")) }.getOrDefault(RoundedText.titles),
            dynamicColor = p.dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
        )
    }
}

/** Toni fissi di voti, assenze e materie (non cambiano con l'accento, come sul web). */
@Immutable
data class ToneColors(val color: Color, val onColor: Color, val container: Color, val onContainer: Color)

@Immutable
data class SubjectColors(val color: Color, val container: Color, val onContainer: Color)

@Immutable
class BCWColors(
    val scheme: ColorScheme,
    val isDark: Boolean,
    val good: ToneColors,
    val fair: ToneColors,
    val poor: ToneColors,
    val neutral: ToneColors,
    val subjects: List<SubjectColors>,
    val breakTint: Color,
    val brand: Color,
    val shadow: Color,
    val scrim: Color,
) {
    fun subject(index: Int) = subjects[((index % 12) + 12) % 12]
}

private fun c(hex: Long) = Color(0xFF000000 or hex)

private val lightTones = listOf(
    ToneColors(c(0x2b6a45), c(0xffffff), c(0xaff2c2), c(0x00210f)),
    ToneColors(c(0x855300), c(0xffffff), c(0xffddb8), c(0x2a1700)),
    ToneColors(c(0xab3429), c(0xffffff), c(0xffdad5), c(0x410001)),
    ToneColors(c(0x426182), c(0xffffff), c(0xd1e4ff), c(0x001d35)),
)
private val darkTones = listOf(
    ToneColors(c(0x94d5a7), c(0x00391d), c(0x0d512f), c(0xaff2c2)),
    ToneColors(c(0xffb95f), c(0x472a00), c(0x653e00), c(0xffddb8)),
    ToneColors(c(0xffb4a9), c(0x690002), c(0x8a1c14), c(0xffdad5)),
    ToneColors(c(0xaac9ef), c(0x0f3251), c(0x2a4969), c(0xd1e4ff)),
)
internal val lightSubjects = listOf(
    0x9e412b to (0xffdad2 to 0x3d0600), 0x29685f to (0xafefe2 to 0x00201c), 0x5357a0 to (0xe1e0ff to 0x0c0e5a),
    0x7c5808 to (0xffdea9 to 0x271900), 0x854a78 to (0xffd7f1 to 0x370531), 0x306388 to (0xcce5ff to 0x001e31),
    0x4c671d to (0xcdee94 to 0x131f00), 0x99452f to (0xffdad2 to 0x3c0800), 0x6b5c4a to (0xf4dfc9 to 0x241a0c),
    0x136779 to (0xadecff to 0x001f26), 0x944555 to (0xffd9de to 0x3e0215), 0x47663c to (0xc8edb7 to 0x042101),
).map { (a, b) -> SubjectColors(c(a.toLong()), c(b.first.toLong()), c(b.second.toLong())) }
internal val darkSubjects = listOf(
    0xffb4a3 to (0x7f2a17 to 0xffdad2), 0x94d2c7 to (0x055047 to 0xafefe2), 0xbfc1ff to (0x3b3f86 to 0xe1e0ff),
    0xefbf6a to (0x5e4100 to 0xffdea9), 0xf8b0e4 to (0x6a335f to 0xffd7f1), 0x9cccf6 to (0x114b6f to 0xcce5ff),
    0xb1d27b to (0x354e04 to 0xcdee94), 0xffb4a2 to (0x7b2e1a to 0xffdad2), 0xd7c3ae to (0x524434 to 0xf4dfc9),
    0x8bd1e5 to (0x004e5d to 0xadecff), 0xffb2be to (0x772e3e to 0xffd9de), 0xacd19d to (0x304e26 to 0xc8edb7),
).map { (a, b) -> SubjectColors(c(a.toLong()), c(b.first.toLong()), c(b.second.toLong())) }

private fun Int.color() = Color(this)

private fun schemeFrom(r: RoleColors, dark: Boolean): ColorScheme {
    fun g(n: String) = r[n].color()
    val base = if (dark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = g("primary"), onPrimary = g("on-primary"), primaryContainer = g("primary-container"), onPrimaryContainer = g("on-primary-container"),
        inversePrimary = g("inverse-primary"),
        secondary = g("secondary"), onSecondary = g("on-secondary"), secondaryContainer = g("secondary-container"), onSecondaryContainer = g("on-secondary-container"),
        tertiary = g("tertiary"), onTertiary = g("on-tertiary"), tertiaryContainer = g("tertiary-container"), onTertiaryContainer = g("on-tertiary-container"),
        background = g("surface"), onBackground = g("on-surface"), surface = g("surface"), onSurface = g("on-surface"),
        surfaceVariant = g("surface-container-highest"), onSurfaceVariant = g("on-surface-variant"), surfaceTint = g("primary"),
        inverseSurface = g("inverse-surface"), inverseOnSurface = g("inverse-on-surface"),
        error = g("error"), onError = g("on-error"), errorContainer = g("error-container"), onErrorContainer = g("on-error-container"),
        outline = g("outline"), outlineVariant = g("outline-variant"), scrim = Color(r["shadow"]),
        surfaceBright = g("surface-bright"), surfaceDim = g("surface-dim"),
        surfaceContainer = g("surface-container"), surfaceContainerHigh = g("surface-container-high"),
        surfaceContainerHighest = g("surface-container-highest"), surfaceContainerLow = g("surface-container-low"),
        surfaceContainerLowest = g("surface-container-lowest"),
    )
}

/** Bianco e nero della prima apertura (come `:root[data-mono]` del web). */
private fun monochromeScheme(dark: Boolean): Pair<ColorScheme, Color> {
    val base = if (dark) darkColorScheme() else lightColorScheme()
    return if (!dark) base.copy(
        primary = c(0x141414), onPrimary = c(0xfafafa), primaryContainer = c(0xe4e4e4), onPrimaryContainer = c(0x111111),
        secondary = c(0x333333), onSecondary = c(0xfafafa), secondaryContainer = c(0xe8e8e8), onSecondaryContainer = c(0x151515),
        tertiary = c(0x4a4a4a), onTertiary = c(0xfafafa), tertiaryContainer = c(0xdadada), onTertiaryContainer = c(0x151515),
        error = c(0x2a2a2a), onError = c(0xfafafa), errorContainer = c(0xe4e4e4), onErrorContainer = c(0x111111),
        surface = c(0xfbfbfb), background = c(0xfbfbfb), surfaceDim = c(0xe6e6e6), surfaceBright = c(0xfbfbfb),
        surfaceContainerLowest = c(0xfefefe), surfaceContainerLow = c(0xf5f5f5), surfaceContainer = c(0xefefef),
        surfaceContainerHigh = c(0xe9e9e9), surfaceContainerHighest = c(0xe2e2e2), surfaceVariant = c(0xe2e2e2),
        onSurface = c(0x111111), onBackground = c(0x111111), onSurfaceVariant = c(0x4a4a4a), outline = c(0x767676), outlineVariant = c(0xc8c8c8),
        inverseSurface = c(0x1c1c1c), inverseOnSurface = c(0xf3f3f3), inversePrimary = c(0xcccccc), surfaceTint = c(0x141414),
    ) to c(0x141414) else base.copy(
        primary = c(0xf2f2f2), onPrimary = c(0x111111), primaryContainer = c(0x2e2e2e), onPrimaryContainer = c(0xf5f5f5),
        secondary = c(0xd4d4d4), onSecondary = c(0x111111), secondaryContainer = c(0x2a2a2a), onSecondaryContainer = c(0xefefef),
        tertiary = c(0xbdbdbd), onTertiary = c(0x111111), tertiaryContainer = c(0x363636), onTertiaryContainer = c(0xefefef),
        error = c(0xf2f2f2), onError = c(0x111111), errorContainer = c(0x2e2e2e), onErrorContainer = c(0xf5f5f5),
        surface = c(0x0b0b0b), background = c(0x0b0b0b), surfaceDim = c(0x0b0b0b), surfaceBright = c(0x2c2c2c),
        surfaceContainerLowest = c(0x060606), surfaceContainerLow = c(0x141414), surfaceContainer = c(0x1b1b1b),
        surfaceContainerHigh = c(0x232323), surfaceContainerHighest = c(0x2d2d2d), surfaceVariant = c(0x2d2d2d),
        onSurface = c(0xf5f5f5), onBackground = c(0xf5f5f5), onSurfaceVariant = c(0xbdbdbd), outline = c(0x8a8a8a), outlineVariant = c(0x444444),
        inverseSurface = c(0xededed), inverseOnSurface = c(0x1b1b1b), inversePrimary = c(0x333333), surfaceTint = c(0xf2f2f2),
    ) to c(0xf2f2f2)
}

/** Angoli della scala di BCW (`--r-*` del web), secondo Impostazioni › Aspetto › Angoli. */
@Immutable
data class Radii(val xs: Dp, val s: Dp, val m: Dp, val l: Dp, val li: Dp, val xl: Dp, val xli: Dp, val xxl: Dp) {
    companion object {
        fun of(style: CornerStyle) = when (style) {
            CornerStyle.square -> Radii(2.dp, 4.dp, 8.dp, 10.dp, 12.dp, 16.dp, 20.dp, 28.dp)
            CornerStyle.standard -> Radii(4.dp, 8.dp, 12.dp, 16.dp, 20.dp, 28.dp, 32.dp, 48.dp)
            CornerStyle.round -> Radii(6.dp, 12.dp, 16.dp, 22.dp, 26.dp, 34.dp, 38.dp, 56.dp)
        }
    }
}

// MARK: - Caratteri

/** Google Sans Flex (assi wght, opsz e ROND), un `FontFamily` per combinazione usata. */
@OptIn(ExperimentalTextApi::class)
object Fonts {
    private val cache = HashMap<Triple<Int, Int, Int>, FontFamily>()

    fun family(weight: Int, rond: Int, size: Int): FontFamily = synchronized(cache) {
        val opsz = size.coerceIn(6, 144)
        cache.getOrPut(Triple(weight, rond, opsz)) {
            FontFamily(
                Font(
                    R.font.google_sans_flex, weight = FontWeight(weight),
                    variationSettings = FontVariation.Settings(
                        FontVariation.weight(weight),
                        FontVariation.Setting("opsz", opsz.toFloat()),
                        FontVariation.Setting("ROND", rond.toFloat()),
                    ),
                ),
            )
        }
    }
}

/** Stili della scala tipografica di M3 (pesi e dimensioni dell'app iOS). */
enum class TextRole(val size: Int, val weight: Int, val rounded: Boolean, val tracking: Double, val lineHeight: Int) {
    displayL(57, 450, true, -0.25, 64), displayM(45, 450, true, 0.0, 52), displayS(36, 450, true, 0.0, 44),
    headlineL(32, 450, true, 0.0, 40), headlineM(28, 450, true, 0.0, 36), headlineS(24, 450, true, 0.0, 32),
    titleL(22, 450, true, 0.0, 28), titleM(16, 550, false, 0.15, 24), titleS(14, 550, false, 0.1, 20),
    bodyL(16, 400, false, 0.3, 24), bodyM(14, 400, false, 0.2, 20), bodyS(12, 400, false, 0.3, 16),
    labelL(14, 550, false, 0.1, 20), labelM(12, 550, false, 0.4, 16), labelS(11, 550, false, 0.4, 16),
}

@Immutable
class TypeScale(private val rond: Int, private val rondBody: Int) {
    fun style(size: Number, weight: Int = 400, rounded: Boolean = false, tracking: Double = 0.0, lineHeight: TextUnit = TextUnit.Unspecified): TextStyle {
        val s = size.toFloat()
        return TextStyle(
            fontFamily = Fonts.family(weight, if (rounded) rond else rondBody, s.toInt()),
            fontWeight = FontWeight(weight),
            fontSize = s.sp,
            letterSpacing = if (tracking == 0.0) TextUnit.Unspecified else (tracking / s).em,
            lineHeight = lineHeight,
        )
    }

    fun role(role: TextRole, emphasized: Boolean = false) =
        style(role.size, if (emphasized) 700 else role.weight, role.rounded, role.tracking, role.lineHeight.sp)

    /** Numeri grandi per medie e voti (peso 650, arrotondati, cifre allineate). */
    fun numeral(size: Number, weight: Int = 650) = style(size, weight, rounded = true).copy(fontFeatureSettings = "tnum")

    val typography: Typography by lazy {
        Typography(
            displayLarge = role(TextRole.displayL), displayMedium = role(TextRole.displayM), displaySmall = role(TextRole.displayS),
            headlineLarge = role(TextRole.headlineL), headlineMedium = role(TextRole.headlineM), headlineSmall = role(TextRole.headlineS),
            titleLarge = role(TextRole.titleL), titleMedium = role(TextRole.titleM), titleSmall = role(TextRole.titleS),
            bodyLarge = role(TextRole.bodyL), bodyMedium = role(TextRole.bodyM), bodySmall = role(TextRole.bodyS),
            labelLarge = role(TextRole.labelL), labelMedium = role(TextRole.labelM), labelSmall = role(TextRole.labelS),
            displayLargeEmphasized = role(TextRole.displayL, true), displayMediumEmphasized = role(TextRole.displayM, true),
            displaySmallEmphasized = role(TextRole.displayS, true), headlineLargeEmphasized = role(TextRole.headlineL, true),
            headlineMediumEmphasized = role(TextRole.headlineM, true), headlineSmallEmphasized = role(TextRole.headlineS, true),
            titleLargeEmphasized = role(TextRole.titleL, true), titleMediumEmphasized = role(TextRole.titleM, true),
            titleSmallEmphasized = role(TextRole.titleS, true), bodyLargeEmphasized = role(TextRole.bodyL, true),
            bodyMediumEmphasized = role(TextRole.bodyM, true), bodySmallEmphasized = role(TextRole.bodyS, true),
            labelLargeEmphasized = role(TextRole.labelL, true), labelMediumEmphasized = role(TextRole.labelM, true),
            labelSmallEmphasized = role(TextRole.labelS, true),
        )
    }
}

// MARK: - Movimento

/** Animazioni ridotte: brevi, senza molle. */
private object ReducedMotion : MotionScheme {
    override fun <T> defaultSpatialSpec(): FiniteAnimationSpec<T> = tween(150)
    override fun <T> fastSpatialSpec(): FiniteAnimationSpec<T> = tween(120)
    override fun <T> slowSpatialSpec(): FiniteAnimationSpec<T> = tween(180)
    override fun <T> defaultEffectsSpec(): FiniteAnimationSpec<T> = tween(150)
    override fun <T> fastEffectsSpec(): FiniteAnimationSpec<T> = tween(120)
    override fun <T> slowEffectsSpec(): FiniteAnimationSpec<T> = tween(180)
}

fun motionScheme(style: MotionStyle): MotionScheme = when (style) {
    MotionStyle.expressive -> MotionScheme.expressive()
    MotionStyle.standard -> MotionScheme.standard()
    MotionStyle.reduced -> ReducedMotion
}

// MARK: - Tema

@Immutable
class BCWTheme(val colors: BCWColors, val radii: Radii, val type: TypeScale, val motion: MotionScheme, val reducedMotion: Boolean, val settings: ThemeSettings)

val LocalBCWTheme = staticCompositionLocalOf<BCWTheme> { error("BCWTheme non impostato") }

/** Accesso al tema nelle viste: `Theme.colors`, `Theme.radii`, `Theme.type`. */
object Theme {
    val colors: BCWColors @Composable @ReadOnlyComposable get() = LocalBCWTheme.current.colors
    val scheme: ColorScheme @Composable @ReadOnlyComposable get() = LocalBCWTheme.current.colors.scheme
    val radii: Radii @Composable @ReadOnlyComposable get() = LocalBCWTheme.current.radii
    val type: TypeScale @Composable @ReadOnlyComposable get() = LocalBCWTheme.current.type
    val motion: MotionScheme @Composable @ReadOnlyComposable get() = LocalBCWTheme.current.motion
    val reducedMotion: Boolean @Composable @ReadOnlyComposable get() = LocalBCWTheme.current.reducedMotion
    val isDark: Boolean @Composable @ReadOnlyComposable get() = LocalBCWTheme.current.colors.isDark
}

private val themeCache = HashMap<Triple<String, ColorStyle, ContrastLevel>, ColorEngine.Theme>()

fun generatedTheme(accent: String, style: ColorStyle, contrast: ContrastLevel): ColorEngine.Theme = synchronized(themeCache) {
    themeCache.getOrPut(Triple(accent, style, contrast)) { ColorEngine.build(accent, style, contrast) }
}

/** Colore fisso degli intervalli. */
internal fun breakTintFor(dark: Boolean): Color = if (dark) c(0xb8ac98) else c(0x9a8c78)

/** Colori di BCW per un tema (anche fuori dalle viste, es. per i widget). */
fun bcwColors(context: android.content.Context, settings: ThemeSettings, dark: Boolean): BCWColors {
    val tones = if (dark) darkTones else lightTones
    val subjects = if (dark) darkSubjects else lightSubjects
    val scheme: ColorScheme
    val brand: Color
    val shadow: Color
    when {
        settings.monochrome -> {
            val (s, b) = monochromeScheme(dark)
            scheme = s
            brand = b
            shadow = Color.Black
        }
        settings.dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            scheme = if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            brand = scheme.primary
            shadow = Color.Black
        }
        else -> {
            val generated = generatedTheme(settings.accent, settings.style, settings.contrast)
            val roles = if (dark) generated.dark else generated.light
            scheme = schemeFrom(roles, dark)
            brand = Color(roles["brand"])
            shadow = Color(roles["shadow"])
        }
    }
    return BCWColors(
        scheme = scheme, isDark = dark, good = tones[0], fair = tones[1], poor = tones[2], neutral = tones[3],
        subjects = subjects, breakTint = breakTintFor(dark), brand = brand, shadow = shadow,
        scrim = shadow.copy(alpha = if (dark) 0.55f else 0.38f),
    )
}

@Composable
fun isDarkTheme(settings: ThemeSettings): Boolean = when (settings.appearance) {
    AppearanceMode.light -> false
    AppearanceMode.dark -> true
    AppearanceMode.system -> isSystemInDarkTheme()
}

@Composable
fun BCWTheme(settings: ThemeSettings, content: @Composable () -> Unit) {
    val dark = isDarkTheme(settings)
    val context = LocalContext.current
    val colors = remember(settings, dark) { bcwColors(context, settings, dark) }
    val radii = remember(settings.corners) { Radii.of(settings.corners) }
    val type = remember(settings.roundedText) {
        when (settings.roundedText) {
            RoundedText.titles -> TypeScale(100, 30)
            RoundedText.all -> TypeScale(100, 100)
            RoundedText.none -> TypeScale(0, 0)
        }
    }
    val motion = remember(settings.motion) { motionScheme(settings.motion) }
    val theme = remember(colors, radii, type, motion) { BCWTheme(colors, radii, type, motion, settings.motion == MotionStyle.reduced, settings) }
    val shapes = remember(radii) {
        Shapes(
            extraSmall = RoundedCornerShape(radii.xs), small = RoundedCornerShape(radii.s), medium = RoundedCornerShape(radii.m),
            large = RoundedCornerShape(radii.l), extraLarge = RoundedCornerShape(radii.xl), largeIncreased = RoundedCornerShape(radii.li),
            extraLargeIncreased = RoundedCornerShape(radii.xli), extraExtraLarge = RoundedCornerShape(radii.xxl),
        )
    }
    CompositionLocalProvider(LocalBCWTheme provides theme) {
        MaterialExpressiveTheme(colorScheme = colors.scheme, motionScheme = motion, shapes = shapes, typography = type.typography, content = content)
    }
}
