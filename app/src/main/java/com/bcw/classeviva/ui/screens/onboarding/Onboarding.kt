package com.bcw.classeviva.ui.screens.onboarding

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.toPath
import com.bcw.classeviva.data.AppearanceMode
import com.bcw.classeviva.data.DashboardMode
import com.bcw.classeviva.data.NewsKind
import com.bcw.classeviva.data.Phase
import com.bcw.classeviva.data.timetable.GenerationSettings
import com.bcw.classeviva.data.timetable.TimetableDraft
import com.bcw.classeviva.data.timetable.Weekday
import com.bcw.classeviva.data.timetable.timetableLayout
import com.bcw.classeviva.platform.BackgroundRefresh
import com.bcw.classeviva.platform.Platform
import com.bcw.classeviva.ui.components.Avatar
import com.bcw.classeviva.ui.components.BCWLogo
import com.bcw.classeviva.ui.components.ConnectedGroup
import com.bcw.classeviva.ui.components.FormIcon
import com.bcw.classeviva.ui.components.ListGroup
import com.bcw.classeviva.ui.components.ListRow
import com.bcw.classeviva.ui.components.ShapeNames
import com.bcw.classeviva.ui.components.Shaped
import com.bcw.classeviva.ui.components.ToggleRow
import com.bcw.classeviva.ui.components.mix
import com.bcw.classeviva.ui.components.noRippleClickable
import com.bcw.classeviva.ui.icons.Icon
import com.bcw.classeviva.ui.screens.login.ClassevivaForm
import com.bcw.classeviva.ui.screens.login.CloudForm
import com.bcw.classeviva.ui.screens.login.ErrorBox
import com.bcw.classeviva.ui.screens.settings.ColorPickerDialog
import com.bcw.classeviva.ui.screens.settings.ThemePreview
import com.bcw.classeviva.ui.screens.settings.colorStyleRow
import com.bcw.classeviva.ui.screens.settings.contrastRow
import com.bcw.classeviva.ui.screens.settings.rememberNotificationPermission
import com.bcw.classeviva.ui.screens.settings.shapeMotionRows
import com.bcw.classeviva.ui.screens.timetable.GenerationFields
import com.bcw.classeviva.ui.screens.timetable.entryColor
import com.bcw.classeviva.ui.screens.timetable.makeTimetableDraft
import com.bcw.classeviva.ui.shell.ConfirmRequest
import com.bcw.classeviva.ui.shell.DialogAction
import com.bcw.classeviva.ui.shell.LocalModel
import com.bcw.classeviva.ui.shell.LocalUi
import com.bcw.classeviva.ui.shell.Transitions
import com.bcw.classeviva.ui.theme.AccentPresets
import com.bcw.classeviva.ui.theme.ColorEngine
import com.bcw.classeviva.ui.theme.ColorStyle
import com.bcw.classeviva.data.DEFAULT_ACCENT
import com.bcw.classeviva.ui.theme.Theme
import com.bcw.classeviva.ui.theme.normalizeHex
import com.bcw.classeviva.util.plural
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * I passaggi dell'introduzione: forma in cima, icona al centro, dimensione della forma.
 * Accoglienza, colori, tema, forme e movimento, Dashboard, funzioni, accesso a Classeviva,
 * account BCW, orario ricavato dalle lezioni (con la scheda Orario e un account), fine.
 */
private enum class Step(val shape: String, val icon: String?, val size: Int) {
    welcome("cookie12Sided", null, 196),
    colors("flower", "palette", 104),
    appearance("softBurst", "sparkles", 84),
    feel("clover8Leaf", "corners", 84),
    dashboard("gem", "dashboard", 84),
    features("puffy", "tune", 84),
    classeviva("cookie9Sided", "graduationCap", 84),
    bcw("sunny", "cloud", 84),
    timetable("cookie7Sided", "wand", 84),
    done("verySunny", "check", 196),
}

private val SATELLITES_A = listOf("pill", "gem", "cookie6Sided", "pentagon", "sunny", "clover4Leaf", "puffyDiamond", "arch", "pill", "flower")
private val SATELLITES_B = listOf("cookie9Sided", "pill", "sunny", "gem", "puffy", "softBoom", "pill", "cookie7Sided", "clover4Leaf", "clover8Leaf")

/** Le forme dei campioni dei colori: ogni colore la sua. */
private val SWATCH_SHAPES = listOf("cookie9Sided", "sunny", "clover4Leaf", "cookie6Sided", "gem", "softBurst", "flower", "puffy", "cookie12Sided", "pentagon")

/**
 * Introduzione (come nell'app): alla prima apertura e, a richiesta, da Impostazioni › Generale ›
 * Rivedi l'introduzione. Finché non si sceglie un colore BCW è in bianco e nero; i colori arrivano
 * con un cerchio che si allarga dal campione toccato. In cima a ogni passaggio una forma di M3
 * Expressive che si trasforma nella successiva; sotto il contenuto che scorre di lato (asse
 * condiviso) e le azioni ferme in fondo.
 */
@Composable
fun OnboardingScreen() {
    val model = LocalModel.current
    val ui = LocalUi.current
    val prefs = model.preferences
    val haptics = LocalHapticFeedback.current
    var step by rememberSaveable { mutableStateOf(Step.welcome) }
    var dir by rememberSaveable { mutableIntStateOf(1) }
    // Il colore è scelto quando BCW non è più in bianco e nero.
    var chosen by rememberSaveable { mutableStateOf(!ui.monochrome) }
    var draft by remember { mutableStateOf<TimetableDraft?>(null) }
    // Le ore scelte per l'orario restano tornando dall'anteprima.
    var generation by remember { mutableStateOf(model.timetable.timetable.generation) }
    val steps = Step.entries.filter { it != Step.timetable || (prefs.showsTimetableTab && model.phase == Phase.signedIn) }
    // Un passaggio sparito (l'orario, spegnendo la scheda) porta al successivo.
    val current = if (step in steps) step else steps.firstOrNull { it.ordinal > step.ordinal } ?: Step.done
    val index = steps.indexOf(current)
    val typing = WindowInsets.ime.getBottom(LocalDensity.current) > 0

    fun go(delta: Int) {
        val target = if (delta > 0) steps.firstOrNull { it.ordinal > current.ordinal } else steps.lastOrNull { it.ordinal < current.ordinal }
        if (target == null) return
        haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
        dir = if (delta > 0) 1 else -1
        step = target
    }

    fun finish(center: Offset?) {
        haptics.performHapticFeedback(HapticFeedbackType.Confirm)
        model.markOnboardingCompleted()
        ui.reveal(center) {
            ui.monochrome = false
            ui.onboarding = false
        }
    }

    BackHandler(enabled = index > 0) {
        if (current == Step.timetable && draft != null) {
            dir = -1
            draft = null
        } else go(-1)
    }

    val reduced = Theme.reducedMotion
    // L'introduzione copre l'app: i tocchi non arrivano alle pagine sotto.
    BoxWithConstraints(Modifier.fillMaxSize().background(Theme.scheme.surface).noRippleClickable {}) {
        val wide = maxWidth >= 840.dp
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
            if (current != Step.welcome && current != Step.done) {
                Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconButton(onClick = {
                        if (current == Step.timetable && draft != null) { dir = -1; draft = null } else go(-1)
                    }) { Icon("arrowBack", contentDescription = "Indietro") }
                    val progress by animateFloatAsState(index.toFloat() / maxOf(1, steps.size - 1), Theme.motion.defaultSpatialSpec(), label = "obProgress")
                    LinearWavyProgressIndicator(progress = { progress }, modifier = Modifier.weight(1f))
                    TextButton(onClick = { finish(null) }) { Text("Salta") }
                }
            } else Spacer(Modifier.height(56.dp))
            val pageKey = if (current == Step.timetable && draft != null) "timetable-preview" else current.name
            val page = @Composable { key: String ->
                StepContent(
                    key = key, chosen = chosen, onChosen = { chosen = true }, draft = draft, onDraft = { dir = if (it != null) 1 else -1; draft = it },
                    generation = generation, onGeneration = { generation = it }, onNext = { go(1) }, onFinish = ::finish,
                )
            }
            if (wide) {
                Row(Modifier.fillMaxSize().widthIn(max = 1100.dp).align(Alignment.CenterHorizontally).padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) { Hero(current, scale = 1.6f) }
                    Box(Modifier.weight(1f).fillMaxHeight()) {
                        AnimatedContent(pageKey, transitionSpec = { Transitions.sharedAxisX(dir, reduced) }, label = "obPage") { page(it) }
                    }
                }
            } else {
                Column(Modifier.fillMaxSize().widthIn(max = 560.dp).align(Alignment.CenterHorizontally)) {
                    if (!typing) Hero(current, scale = 1f)
                    AnimatedContent(pageKey, transitionSpec = { Transitions.sharedAxisX(dir, reduced) }, label = "obPage", modifier = Modifier.weight(1f)) { page(it) }
                }
            }
        }
    }
}

@Composable
private fun StepContent(
    key: String, chosen: Boolean, onChosen: () -> Unit, draft: TimetableDraft?, onDraft: (TimetableDraft?) -> Unit,
    generation: GenerationSettings, onGeneration: (GenerationSettings) -> Unit, onNext: () -> Unit, onFinish: (Offset?) -> Unit,
) {
    when (key) {
        Step.welcome.name -> CenterPage(
            "Ciao! Questa è BCW",
            "Better ClasseViVa: voti, compiti, orario e comunicazioni di Classeviva, in un'app fatta su misura per te.",
        ) { center ->
            NextButton("Inizia", onClick = onNext)
            TextButton(onClick = { onFinish(center()) }, modifier = Modifier.fillMaxWidth().height(48.dp)) { Text("Salta introduzione") }
        }
        Step.colors.name -> ColorsPage(chosen, onChosen, onNext)
        Step.appearance.name -> AppearancePage(onNext)
        Step.feel.name -> FeelPage(onNext)
        Step.dashboard.name -> DashboardPage(onNext)
        Step.features.name -> FeaturesPage(onNext)
        Step.classeviva.name -> ClassevivaPage(onNext)
        Step.bcw.name -> BCWPage(onNext)
        Step.timetable.name -> TimetableSetupPage(generation, onGeneration, onDraft, onNext)
        "timetable-preview" -> if (draft != null) TimetablePreviewPage(draft, onDraft, onNext)
        else -> DonePage(onFinish)
    }
}

// MARK: - Impaginazione

/** Titolo, spiegazione e contenuto che scorrono, con le azioni ferme in fondo. */
@Composable
private fun StepPage(title: String, subtitle: String, actions: @Composable ColumnScope.() -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(top = 8.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(title, style = Theme.type.style(30, 500, rounded = true, lineHeight = 36.sp), color = Theme.scheme.onSurface)
                Text(subtitle, style = Theme.type.style(15, lineHeight = 21.sp), color = Theme.scheme.onSurfaceVariant)
            }
            content()
        }
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = actions)
    }
}

/** Accoglienza e fine: titolo grande, testo e pulsanti; `center()` è il centro del pulsante toccato. */
@Composable
private fun CenterPage(title: String, text: String, actions: @Composable ColumnScope.(center: () -> Offset?) -> Unit) {
    var bounds by remember { mutableStateOf<Offset?>(null) }
    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically)) {
            Text(title, style = Theme.type.style(40, 500, rounded = true, tracking = -0.5, lineHeight = 46.sp), color = Theme.scheme.onSurface, textAlign = TextAlign.Center)
            Text(text, style = Theme.type.style(16, lineHeight = 23.sp), color = Theme.scheme.onSurfaceVariant, textAlign = TextAlign.Center)
        }
        Column(
            Modifier.fillMaxWidth().padding(vertical = 16.dp).onGloballyPositioned { bounds = it.boundsInRoot().center },
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) { actions { bounds } }
    }
}

/** Il pulsante principale: "Continua" con la freccia. */
@Composable
private fun NextButton(title: String = "Continua", enabled: Boolean = true, loading: Boolean = false, onClick: () -> Unit) {
    Button(onClick = onClick, enabled = enabled && !loading, modifier = Modifier.fillMaxWidth().height(56.dp)) {
        if (loading) LoadingIndicator(Modifier.size(28.dp), color = LocalContentColor.current)
        else {
            Text(title, style = Theme.type.style(16, 600))
            Icon("arrowBack", size = 20.dp, modifier = Modifier.padding(start = 8.dp).rotate(180f))
        }
    }
}

// MARK: - Forma in cima

/**
 * La forma di ogni passaggio, che si trasforma nella successiva con una molla e ruota lentamente,
 * con l'icona al centro (nell'accoglienza il logo) e altre due forme più piccole attorno.
 */
@Composable
private fun Hero(step: Step, scale: Float) {
    val index = step.ordinal
    val side = if (index % 2 == 0) 1 else -1
    val big = step.size >= 190
    val a = if (big) Triple(0.56f * side, -0.42f, 0.24f) else Triple(0.62f * side, -0.42f, 0.3f)
    val b = if (big) Triple(-0.6f * side, 0.36f, 0.3f) else Triple(-0.66f * side, 0.34f, 0.38f)
    val size by animateDpAsState((step.size * scale).dp, Theme.motion.slowSpatialSpec(), label = "heroSize")
    val height by animateDpAsState(if (big) (step.size * scale * 1.3f).dp else (step.size * scale * 1.75f).dp, Theme.motion.slowSpatialSpec(), label = "heroHeight")
    val reduced = Theme.reducedMotion
    val turn = rememberInfiniteTransition(label = "heroTurn")
    val angle by turn.animateFloat(0f, if (reduced) 0f else 360f, infiniteRepeatable(tween(24_000, easing = LinearEasing), RepeatMode.Restart), label = "angle")
    val s = Theme.scheme
    Box(Modifier.fillMaxWidth().height(height), contentAlignment = Alignment.Center) {
        Box(Modifier.size(size), contentAlignment = Alignment.Center) {
            MorphShape(SATELLITES_A[index], s.tertiaryContainer, Modifier.offset { IntOffset((size * a.first).roundToPx(), (size * a.second).roundToPx()) }.size(size * a.third), -angle * 1.5f)
            MorphShape(SATELLITES_B[index], s.secondaryContainer, Modifier.offset { IntOffset((size * b.first).roundToPx(), (size * b.second).roundToPx()) }.size(size * b.third), angle * 1.2f)
            MorphShape(step.shape, s.primaryContainer, Modifier.fillMaxSize(), angle)
            AnimatedContent(step.icon, transitionSpec = { Transitions.fadeThrough(reduced) }, label = "heroCenter") { icon ->
                if (icon == null) BCWLogo(height = size * 0.3f, stacked = true)
                else Icon(icon, size = size * 0.42f, tint = s.onPrimaryContainer)
            }
            if (step == Step.done && !reduced) ShapeBurst(size)
        }
    }
}

/** Una forma che si trasforma nella successiva con una molla (`Morph` delle forme di Material). */
@Composable
private fun MorphShape(name: String, color: Color, modifier: Modifier, rotation: Float) {
    val target = ShapeNames.polygon(name)
    var from by remember { mutableStateOf(target) }
    var to by remember { mutableStateOf(target) }
    val progress = remember { Animatable(1f) }
    val reduced = Theme.reducedMotion
    LaunchedEffect(name) {
        if (to === target) return@LaunchedEffect
        from = to
        to = target
        if (reduced) progress.snapTo(1f)
        else {
            progress.snapTo(0f)
            progress.animateTo(1f, spring(dampingRatio = 0.8f, stiffness = 120f))
        }
    }
    val morph = remember(from, to) { Morph(from, to) }
    Canvas(modifier) {
        val path = morph.toPath(progress.value.coerceIn(0f, 1f)).asComposePath()
        rotate(rotation) {
            scale(size.width, size.height, pivot = Offset.Zero) { drawPath(path, color) }
        }
    }
}

/** Coriandoli di forme che escono dal centro, ruotano e svaniscono (pagina finale). */
@Composable
private fun ShapeBurst(size: Dp) {
    val t = remember { Animatable(0f) }
    LaunchedEffect(Unit) { t.animateTo(1f, tween(1400)) }
    val s = Theme.scheme
    val colors = listOf(s.primary, s.tertiary, s.secondary, s.primaryContainer, s.tertiaryContainer)
    val names = listOf("cookie9Sided", "sunny", "pill", "clover4Leaf", "gem", "flower", "puffy", "pentagon")
    val scale = size.value / 196f
    for (i in 0 until 18) {
        val angle = (i * 20 + if (i % 2 == 0) 6 else -6) * PI / 180
        val distance = (if (i % 3 == 0) 190 else if (i % 2 == 0) 150 else 120) * scale
        val piece = if (i % 3 == 0) 26 else 16
        val p = t.value
        val eased = 1 - (1 - p) * (1 - p) * (1 - p)
        Shaped(
            names[i % names.size], colors[i % colors.size], piece.dp,
            Modifier.offset(x = (cos(angle) * distance * eased).dp, y = (sin(angle) * distance * eased).dp).alpha((1 - p * p).coerceIn(0f, 1f)).scale(0.4f + 0.6f * eased),
            rotation = (if (i % 2 == 0) 180f else -220f) * eased,
        ) {}
    }
}

// MARK: - Colori

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColorsPage(chosen: Boolean, onChosen: () -> Unit, onNext: () -> Unit) {
    val model = LocalModel.current
    val ui = LocalUi.current
    val prefs = model.preferences
    val haptics = LocalHapticFeedback.current
    val accent = normalizeHex(prefs.string("accentColor")) ?: DEFAULT_ACCENT
    val style = ColorStyle.entries.firstOrNull { it.name == prefs.string("colorStyle") } ?: ColorStyle.tonalSpot
    val dark = Theme.isDark
    val isCustom = AccentPresets.none { it.first == accent }
    var picking by remember { mutableStateOf(false) }
    var customCenter by remember { mutableStateOf<Offset?>(null) }

    // BCW esce dal bianco e nero con un cerchio che parte dal campione.
    fun choose(hex: String, center: Offset?) {
        haptics.performHapticFeedback(HapticFeedbackType.Confirm)
        ui.reveal(center) {
            ui.monochrome = false
            prefs.set("dynamicColor", false)
            prefs.set("accentColor", hex)
            onChosen()
        }
    }

    StepPage(
        "Scegli i tuoi colori",
        "Da qui comincia tutto: l'app prende il colore che tocchi. Potrai cambiarlo quando vuoi in Impostazioni › Aspetto.",
        actions = { NextButton(enabled = chosen, onClick = onNext) },
    ) {
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalArrangement = Arrangement.spacedBy(16.dp), maxItemsInEachRow = 4) {
            AccentPresets.forEachIndexed { i, (hex, name) ->
                val colors = remember(hex, style, dark) { ColorEngine.swatch(hex, style, dark).map { Color(it) } }
                var center by remember { mutableStateOf<Offset?>(null) }
                ColorSwatch(name.replace(" (BCW)", ""), SWATCH_SHAPES[i % SWATCH_SHAPES.size], chosen && hex == accent,
                    Modifier.onGloballyPositioned { center = it.boundsInRoot().center },
                    fill = { m -> SplitFill(colors, m) }) { choose(hex, center) }
            }
            ColorSwatch("Altro", "arch", chosen && isCustom, Modifier.onGloballyPositioned { customCenter = it.boundsInRoot().center },
                fill = { m -> Box(m.background(Brush.sweepGradient(listOf(Color(0xFFE53935), Color(0xFFFDD835), Color(0xFF43A047), Color(0xFF1E88E5), Color(0xFF8E24AA), Color(0xFFE53935))))) }) {
                picking = true
            }
        }
    }
    if (picking) ColorPickerDialog(accent, onDismiss = { picking = false }, title = "Scegli un colore", confirmLabel = "Usa questo colore") { hex ->
        normalizeHex(hex)?.let { choose(it, customCenter) }
    }
}

@Composable
private fun SplitFill(colors: List<Color>, modifier: Modifier) {
    Column(modifier) {
        Box(Modifier.weight(1f).fillMaxWidth().background(colors[0]))
        Row(Modifier.weight(1f).fillMaxWidth()) {
            Box(Modifier.weight(1f).fillMaxHeight().background(colors[1]))
            Box(Modifier.weight(1f).fillMaxHeight().background(colors[2]))
        }
    }
}

@Composable
private fun ColorSwatch(name: String, shape: String, selected: Boolean, modifier: Modifier, fill: @Composable (Modifier) -> Unit, onClick: () -> Unit) {
    val scale by animateFloatAsState(if (selected) 1.08f else 1f, Theme.motion.fastSpatialSpec(), label = "swatch")
    Column(
        Modifier.width(76.dp).clip(RoundedCornerShape(Theme.radii.l)).clickable(onClickLabel = name, onClick = onClick).padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(modifier.size(60.dp).scale(scale), contentAlignment = Alignment.Center) {
            fill(Modifier.fillMaxSize().clip(ShapeNames.shape(shape)))
            if (selected) {
                Box(Modifier.size(28.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.9f)), contentAlignment = Alignment.Center) {
                    Icon("check", size = 18.dp, tint = Color.Black)
                }
            }
        }
        Text(name, style = Theme.type.style(12, if (selected) 650 else 500), color = Theme.scheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

// MARK: - Tema

@Composable
private fun AppearancePage(onNext: () -> Unit) {
    val model = LocalModel.current
    val ui = LocalUi.current
    val p = model.preferences
    var center by remember { mutableStateOf<Offset?>(null) }
    val icons = mapOf(AppearanceMode.system to "contrast", AppearanceMode.light to "sun", AppearanceMode.dark to "circleFill")
    StepPage(
        "Com'è la tua app?",
        "Chiara o scura, e con quanto contrasto tra i colori. Si cambia anche dopo, in Impostazioni › Aspetto.",
        actions = { NextButton(onClick = onNext) },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Tema", style = Theme.type.style(14, 650), color = Theme.scheme.primary, modifier = Modifier.padding(start = 16.dp))
            // Anche chiaro e scuro arrivano con il cerchio, dal gruppo di pulsanti.
            ConnectedGroup(AppearanceMode.entries, p.appearance, { mode ->
                if (mode != p.appearance) ui.reveal(center) { p.set("appearance", mode.name) }
            }, modifier = Modifier.onGloballyPositioned { center = it.boundsInRoot().center }, height = 72.dp) { mode, _ ->
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(icons.getValue(mode), size = 22.dp)
                    Text(mode.title, maxLines = 1)
                }
            }
        }
        ListGroup(footer = "Più contrasto rende testi e pulsanti più netti sullo sfondo.") {
            contrastRow(p, enabled = !p.dynamicColor)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) item {
                ToggleRow("Colori del sistema", p.dynamicColor, { on -> ui.reveal(center) { p.set("dynamicColor", on) } }, icon = "colorize",
                    subtitle = "Dallo sfondo del telefono (Material You), al posto del colore scelto")
            }
        }
    }
}

// MARK: - Forme e movimento

@Composable
private fun FeelPage(onNext: () -> Unit) {
    val p = LocalModel.current.preferences
    StepPage(
        "Forme e movimento",
        "Quanto arrotondare, come muovere e con che accostamenti di colore. L'anteprima cambia mentre scegli.",
        actions = { NextButton(onClick = onNext) },
    ) {
        ListGroup { item { ThemePreview() } }
        ListGroup {
            shapeMotionRows(p)
            colorStyleRow(p, enabled = !p.dynamicColor)
        }
    }
}

// MARK: - Dashboard

@Composable
private fun DashboardPage(onNext: () -> Unit) {
    val p = LocalModel.current.preferences
    val haptics = LocalHapticFeedback.current
    val mode = p.dashboardMode
    StepPage(
        "Come vuoi la Dashboard?",
        "È la prima cosa che vedi: i prossimi compiti, le verifiche e gli eventi. Si cambia anche dopo, in Impostazioni › Dashboard.",
        actions = { NextButton(onClick = onNext) },
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            for (m in DashboardMode.entries) {
                PreviewChoice(
                    if (m == DashboardMode.list) "Lista" else "Calendario", if (m == DashboardMode.list) "Vista compatta" else "Vista estesa",
                    selected = mode == m, modifier = Modifier.weight(1f),
                    onClick = { if (mode != m) { haptics.performHapticFeedback(HapticFeedbackType.SegmentTick); p.set("dashboardMode", m.name) } },
                ) { DashboardPreview(m) }
            }
        }
    }
}

/** Una scelta tra due, in una scheda grande con un'anteprima stilizzata. */
@Composable
private fun PreviewChoice(title: String, subtitle: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit, preview: @Composable () -> Unit) {
    val shape = RoundedCornerShape(Theme.radii.xl)
    Column(
        modifier.clip(shape).background(if (selected) Theme.scheme.secondaryContainer else Theme.scheme.surfaceContainer)
            .then(if (selected) Modifier.border(2.dp, Theme.scheme.primary, shape) else Modifier)
            .clickable(onClick = onClick).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.fillMaxWidth().height(150.dp).clip(RoundedCornerShape(Theme.radii.l)).background(Theme.scheme.surface).padding(10.dp)) { preview() }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = Theme.type.style(16, 600), color = if (selected) Theme.scheme.onSecondaryContainer else Theme.scheme.onSurface)
                Text(subtitle, style = Theme.type.style(13), color = Theme.scheme.onSurfaceVariant)
            }
            if (selected) Icon("checkCircleFill", tint = Theme.scheme.primary)
        }
    }
}

@Composable
private fun DashboardPreview(mode: DashboardMode) {
    val s = Theme.scheme
    val row = @Composable { accent: Boolean -> Box(Modifier.fillMaxWidth().height(18.dp).clip(RoundedCornerShape(6.dp)).background(if (accent) s.primaryContainer else s.surfaceContainerHigh)) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (mode == DashboardMode.list) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                for (i in 0 until 5) Box(Modifier.weight(if (i == 2) 1.4f else 1f).height(26.dp).clip(RoundedCornerShape(8.dp)).background(if (i == 2) s.primary else s.surfaceContainerHigh))
            }
            Box(Modifier.width(70.dp).height(14.dp).clip(RoundedCornerShape(4.dp)).background(s.onSurface.copy(alpha = 0.7f)))
            row(true); row(false); row(false)
        } else {
            for (w in 0 until 4) {
                Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    for (d in 0 until 7) Box(Modifier.weight(1f).height(14.dp).clip(CircleShape).background(if (w == 1 && d == 3) s.primary else s.surfaceContainerHigh))
                }
            }
            row(true); row(false)
        }
    }
}

// MARK: - Funzioni

@Composable
private fun FeaturesPage(onNext: () -> Unit) {
    val model = LocalModel.current
    val ui = LocalUi.current
    val context = LocalContext.current
    val p = model.preferences
    val withPermission = rememberNotificationPermission()
    val lockAvailable = Platform.lockAvailable(context)
    StepPage("Cosa ti serve?", "Puoi tenere tutto così com'è: ogni scelta si cambia in Impostazioni.", actions = { NextButton(onClick = onNext) }) {
        ListGroup(header = "Orario") {
            item { ToggleRow("Scheda Orario", p.showsTimetableTab, { p.set("showsTimetableTab", it) }, icon = "timetable", subtitle = "Il tuo orario settimanale, tra Dashboard e Voti") }
            item { ToggleRow("Verifiche nell'orario", p.timetableShowsTests, { p.set("timetableShowsTests", it) }, icon = "test", subtitle = "Segna nella lista le verifiche di Classeviva") }
        }
        ListGroup(header = "Notifiche") {
            item {
                ToggleRow("Promemoria", p.homeworkReminders, { on ->
                    if (on) withPermission { p.set("homeworkReminders", true); model.rescheduleReminders() }
                    else { p.set("homeworkReminders", false); model.rescheduleReminders() }
                }, icon = "bell", subtitle = "La sera prima di compiti e verifiche")
            }
            item {
                ToggleRow("Novità", NewsKind.entries.any { p.newsEnabled(it) }, { on ->
                    val apply = {
                        for (kind in NewsKind.entries) p.setNews(kind, on)
                        BackgroundRefresh.schedule(context, model)
                    }
                    if (on) withPermission(apply) else apply()
                }, icon = "bellActive", subtitle = "Voti, comunicazioni, note e assenze")
            }
        }
        ListGroup(header = "Sicurezza") {
            item {
                ToggleRow("Blocca con lo sblocco del telefono", p.useLock && lockAvailable, { on ->
                    if (!on) p.set("useLock", false)
                    else Platform.authenticate(context, "Attiva il blocco di BCW") { ok ->
                        if (ok) {
                            p.set("useLock", true)
                            ui.toast("Blocco attivato.")
                        }
                    }
                }, icon = "lock", enabled = lockAvailable,
                    subtitle = if (lockAvailable) "Impronta, volto o blocco schermo, ogni volta che torni su BCW" else "Imposta un blocco schermo nelle impostazioni del telefono")
            }
        }
    }
}

// MARK: - Classeviva

/** L'accesso a Classeviva, spiegato. Chi ha già un account vede chi è e può aggiungerne un altro. */
@Composable
private fun ClassevivaPage(onNext: () -> Unit) {
    val model = LocalModel.current
    val ui = LocalUi.current
    if (model.phase == Phase.signedIn && !model.isDemo) {
        val count = model.accounts.size
        StepPage("Hai già effettuato l'accesso", "Puoi aggiungere altri account, per esempio quello di un fratello o di una sorella.", actions = { NextButton(onClick = onNext) }) {
            Summary(title = model.displayName, subtitle = if (count == 1) "1 account" else "$count account") {
                Avatar(model.initials, 56.dp, shape = "cookie9Sided")
            }
            FilledTonalButton(onClick = { ui.addAccount = true }, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                Icon("userPlus", size = 18.dp)
                Text(if (model.isCloud) "Abbina un altro account" else "Aggiungi un altro account", Modifier.padding(start = 8.dp))
            }
        }
        return
    }
    StepPage(
        "Accedi a Classeviva",
        "Usa le stesse credenziali del registro: codice utente (o email) e password. Vanno soltanto ai server di Classeviva.",
        actions = { TextButton(onClick = onNext, modifier = Modifier.fillMaxWidth().height(48.dp)) { Text("Più tardi") } },
    ) {
        ClassevivaForm(adding = false, onDone = onNext, onDemo = onNext)
        Text("BCW non è affiliato a Gruppo Spaggiari Parma S.p.A.", style = Theme.type.style(12), color = Theme.scheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun Summary(title: String, subtitle: String, art: @Composable () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(Theme.radii.xl)).background(Theme.scheme.surfaceContainer).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        art()
        Column(Modifier.weight(1f)) {
            Text(title, style = Theme.type.style(18, 600), color = Theme.scheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, style = Theme.type.style(14), color = Theme.scheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

// MARK: - Account BCW

/** L'account BCW, facoltativo: a cosa serve, e crearlo o accedere. */
@Composable
private fun BCWPage(onNext: () -> Unit) {
    val model = LocalModel.current
    var choice by rememberSaveable { mutableStateOf("none") }
    val subtitle = "È facoltativo. Con un nome utente e una password ritrovi tutto su ogni dispositivo, anche nell'app per iPhone e iPad e sul web."
    if (model.isCloud) {
        StepPage("Sei nel tuo account BCW", "Impostazioni, orario e account abbinati ti seguono su ogni dispositivo.", actions = { NextButton(onClick = onNext) }) {
            val host = runCatching { java.net.URI(com.bcw.classeviva.data.BCWServer.base).host }.getOrNull() ?: ""
            Summary(model.cloudUser?.username ?: "", host) {
                Shaped("cookie9Sided", Theme.scheme.primaryContainer, 56.dp) { Icon("cloud", tint = Theme.scheme.onPrimaryContainer) }
            }
        }
        return
    }
    if (choice == "none") {
        StepPage("Vuoi un account BCW?", subtitle, actions = {
            Button(onClick = { choice = "create" }, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                Icon("userPlus", size = 20.dp)
                Text("Crea un account BCW", Modifier.padding(start = 8.dp), style = Theme.type.style(16, 600))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = { choice = "signIn" }, modifier = Modifier.weight(1f).height(48.dp)) { Text("Ho già un account") }
                TextButton(onClick = onNext, modifier = Modifier.weight(1f).height(48.dp)) { Text("No, grazie") }
            }
        }) {
            ListGroup {
                item { ListRow("Tutti i tuoi account insieme", subtitle = "Più account Classeviva, un solo accesso", leading = { FormIcon("users") }) }
                item { ListRow("Orario e impostazioni ovunque", subtitle = "Nell'app per iPhone e iPad e nel browser", leading = { FormIcon("timetable") }) }
                item { ListRow("Credenziali cifrate", subtitle = "Salvate cifrate sul server di BCW", leading = { FormIcon("lock") }) }
            }
        }
        return
    }
    val create = choice == "create"
    StepPage(if (create) "Crea un account BCW" else "Accedi con un account BCW", subtitle, actions = {
        TextButton(onClick = { choice = "none" }, modifier = Modifier.fillMaxWidth().height(48.dp)) { Text("Indietro") }
    }) {
        CloudForm(create, onToggleRegister = { choice = if (create) "signIn" else "create" }, compact = false, onDone = onNext)
    }
}

// MARK: - Orario

/**
 * L'orario ricavato dalle lezioni già registrate: quando cominciano le ore (gli stessi campi del
 * foglio del generatore), poi un'anteprima della settimana e la scelta se applicarla. La bozza
 * resta qui, così sotto l'Orario non va in anteprima.
 */
@Composable
private fun TimetableSetupPage(settings: GenerationSettings, onSettings: (GenerationSettings) -> Unit, onDraft: (TimetableDraft?) -> Unit, onNext: () -> Unit) {
    val model = LocalModel.current
    val store = model.timetable
    val scope = rememberCoroutineScope()
    var generating by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val events = store.timetable.entries.size
    val intro = if (events > 0) "Hai già un orario con ${plural(events, "1 evento", "# eventi")}. Puoi ricavarne un altro" else "BCW può ricavarlo"
    StepPage(
        "Crea il tuo orario",
        "$intro dalle lezioni già registrate su Classeviva, che però dice solo in quale ora si svolge una lezione: indica quando comincia la prima e quanto dura. Prima di applicarlo vedrai un'anteprima.",
        actions = {
            Button(onClick = {
                scope.launch {
                    generating = true
                    error = null
                    try {
                        onDraft(makeTimetableDraft(model, settings))
                    } catch (e: Exception) {
                        error = e.message
                    } finally {
                        generating = false
                    }
                }
            }, enabled = !generating, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                if (generating) LoadingIndicator(Modifier.size(28.dp), color = LocalContentColor.current) else Icon("wand", size = 20.dp)
                Text("Crea l'anteprima", Modifier.padding(start = 8.dp), style = Theme.type.style(16, 600))
            }
            TextButton(onClick = onNext, modifier = Modifier.fillMaxWidth().height(48.dp)) { Text("Lo farò dopo") }
        },
    ) {
        GenerationFields(settings, onSettings)
        error?.let { ErrorBox(it) }
    }
}

@Composable
private fun TimetablePreviewPage(draft: TimetableDraft, onDraft: (TimetableDraft?) -> Unit, onNext: () -> Unit) {
    val model = LocalModel.current
    val ui = LocalUi.current
    val store = model.timetable
    val haptics = LocalHapticFeedback.current
    val lessons = draft.entries.count { !it.isBreak }
    fun apply(replacing: Boolean) {
        haptics.performHapticFeedback(HapticFeedbackType.Confirm)
        // Con ore fuori dalle mezz'ore l'orario passa da sé all'allineamento libero.
        store.applyEntries(draft.entries, draft.settings, replacing)
        onDraft(null)
        onNext()
    }
    StepPage(
        "Ecco la tua settimana",
        "${plural(lessons, "1 lezione ricavata", "# lezioni ricavate")} da ${plural(draft.analyzedDays, "1 giorno", "# giorni")} di registro. Controlla che torni: potrai cambiarla quando vuoi nella scheda Orario.",
        actions = {
            NextButton("Applica l'orario") {
                if (store.isEmpty) apply(true)
                else ui.ask(ConfirmRequest(
                    title = "Hai già un orario",
                    message = "Sostituendolo, eventi, note, file e verifiche segnate a mano dell'orario attuale verranno eliminati.",
                    confirm = DialogAction("Sostituisci l'orario attuale", destructive = true) { apply(true) },
                    extra = listOf(DialogAction("Aggiungi solo dove è libero") { apply(false) }),
                ))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = { onDraft(null) }, modifier = Modifier.weight(1f).height(48.dp)) { Text("Cambia le ore") }
                TextButton(onClick = onNext, modifier = Modifier.weight(1f).height(48.dp)) { Text("Non ora") }
            }
        },
    ) {
        WeekGlance(draft)
    }
}

/** La settimana dell'anteprima: una colonna per giorno, con le lezioni come pillole nella loro tinta, che compaiono una dopo l'altra. */
@Composable
private fun WeekGlance(draft: TimetableDraft) {
    val hourHeight = 40.dp
    val days = (1..7).map { w -> w to draft.entries.filter { it.weekday == w } }.filter { it.second.isNotEmpty() }
    val first = ((draft.entries.minOfOrNull { it.start } ?: 480) / 60) * 60
    val last = maxOf(first + 60, ((draft.entries.maxOfOrNull { it.end } ?: 840) + 59) / 60 * 60)
    val height = hourHeight * ((last - first) / 60f)
    val appear = remember { Animatable(0f) }
    LaunchedEffect(draft) {
        delay(150)
        appear.animateTo(1f, tween(900))
    }
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(Theme.radii.xl)).background(Theme.scheme.surfaceContainerLow).padding(10.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        days.forEachIndexed { column, (w, entries) ->
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(Weekday.shortName(w), style = Theme.type.style(12, 600), color = Theme.scheme.onSurfaceVariant)
                BoxWithConstraints(Modifier.fillMaxWidth().height(height)) {
                    val width = maxWidth
                    var order = 0
                    for (cluster in timetableLayout(entries).clusters) {
                        for (placed in cluster.blocks) {
                            val block = placed.block
                            val entry = block.entries.first()
                            val i = column + order++ % 6
                            val local = ((appear.value * 12f) - i).coerceIn(0f, 1f)
                            val tint = entryColor(entry)
                            Box(
                                Modifier.offset(x = width * (placed.column.toFloat() / cluster.columns), y = hourHeight * ((block.start - first) / 60f))
                                    .width(width / cluster.columns - 2.dp).height(maxOf(14.dp, hourHeight * ((block.end - block.start) / 60f) - 3.dp))
                                    .scale(0.86f + 0.14f * local).alpha(local)
                                    .clip(RoundedCornerShape(Theme.radii.m)).background(tint.mix(Theme.scheme.surface, 0.55f)).padding(horizontal = 4.dp, vertical = 3.dp),
                            ) {
                                Text(entry.title.ifEmpty { "Senza titolo" }, style = Theme.type.style(10, 600, lineHeight = 12.sp), color = Theme.scheme.onSurface, maxLines = 3, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        }
    }
}

// MARK: - Fine

@Composable
private fun DonePage(onFinish: (Offset?) -> Unit) {
    val model = LocalModel.current
    val signedIn = model.phase == Phase.signedIn
    val firstName = model.displayName.split(" ").firstOrNull().orEmpty()
    CenterPage(
        if (signedIn && !model.isDemo && firstName.isNotEmpty()) "Tutto pronto, $firstName!" else "Tutto pronto!",
        if (signedIn) "BCW è fatta su misura per te. Puoi rivedere questa introduzione quando vuoi, in Impostazioni › Generale."
        else "Manca solo l'accesso a Classeviva: lo trovi nella prossima schermata.",
    ) { center ->
        NextButton(if (signedIn) "Apri BCW" else "Vai all'accesso") { onFinish(center()) }
    }
}
