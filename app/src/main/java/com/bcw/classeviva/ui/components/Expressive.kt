package com.bcw.classeviva.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.WavyProgressIndicatorDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.bcw.classeviva.data.Grade
import com.bcw.classeviva.util.Fmt
import com.bcw.classeviva.util.GradeFormat
import com.bcw.classeviva.ui.icons.Icon
import com.bcw.classeviva.ui.theme.Theme
import com.bcw.classeviva.ui.theme.ToneColors
import java.time.LocalDate

// MARK: - Toni

enum class Tone { primary, secondary, tertiary, good, fair, poor, neutral, surface, error }

data class ToneSet(val color: Color, val container: Color, val onContainer: Color)

@Composable
fun tone(t: Tone): ToneSet {
    val s = Theme.scheme
    val c = Theme.colors
    fun of(x: ToneColors) = ToneSet(x.color, x.container, x.onContainer)
    return when (t) {
        Tone.primary -> ToneSet(s.primary, s.primaryContainer, s.onPrimaryContainer)
        Tone.secondary -> ToneSet(s.secondary, s.secondaryContainer, s.onSecondaryContainer)
        Tone.tertiary -> ToneSet(s.tertiary, s.tertiaryContainer, s.onTertiaryContainer)
        Tone.good -> of(c.good)
        Tone.fair -> of(c.fair)
        Tone.poor -> of(c.poor)
        Tone.neutral -> of(c.neutral)
        Tone.surface -> ToneSet(s.onSurfaceVariant, s.surfaceContainerHigh, s.onSurfaceVariant)
        Tone.error -> ToneSet(s.error, s.errorContainer, s.onErrorContainer)
    }
}

/** Tono di una media o di un voto: bene, quasi, insufficiente, neutro. */
fun gradeTone(value: Double?): Tone = when {
    value == null -> Tone.neutral
    value >= 6 -> Tone.good
    value >= 5.5 -> Tone.fair
    else -> Tone.poor
}

fun gradeToneOf(g: Grade): Tone = if (g.canceled || !g.countsTowardAverage) Tone.neutral else gradeTone(g.value)

@Composable
fun gradeColor(g: Grade): Color = if (g.canceled) Theme.scheme.onSurfaceVariant else tone(gradeToneOf(g)).color

@Composable
fun subjectColor(id: Long?): Color = if (id == null) Theme.scheme.onSurfaceVariant else Theme.colors.subject((kotlin.math.abs(id) % 12).toInt()).color

/** Colore con trasparenza mescolato sopra un fondo (come `color-mix` del CSS). */
fun Color.mix(over: Color, amount: Float): Color = Color(
    red = red * amount + over.red * (1 - amount), green = green * amount + over.green * (1 - amount),
    blue = blue * amount + over.blue * (1 - amount), alpha = 1f,
)

// MARK: - Etichette

/** Etichetta di stato su una riga (22 dp), tinta o piena. */
@Composable
fun Pill(text: String, modifier: Modifier = Modifier, color: Color = Theme.scheme.primary, filled: Boolean = false, small: Boolean = false, bold: Boolean = false) {
    val bg = if (filled) color else color.copy(alpha = 0.15f)
    val fg = if (filled) Theme.scheme.surface else color
    Box(
        modifier.height(if (small) 20.dp else 22.dp).clip(RoundedCornerShape(Theme.radii.s)).background(bg).padding(horizontal = if (small) 6.dp else 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = Theme.type.style(if (small) 11 else 12, if (bold) 750 else 650, tracking = 0.3), color = fg, maxLines = 1)
    }
}

/** Numero in un cerchio rosso (comunicazioni da leggere…). */
@Composable
fun CountBadge(count: Int, modifier: Modifier = Modifier) {
    Box(
        modifier.defaultMinSize(minWidth = 24.dp).height(22.dp).clip(CircleShape).background(Theme.scheme.error).padding(horizontal = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(count.toString(), style = Theme.type.numeral(12, 650), color = Theme.scheme.onError)
    }
}

/** Chip di stato (32 dp, contenitore tonale): un conteggio o uno stato del giorno. */
@Composable
fun StatusChip(text: String, icon: String, t: Tone = Tone.secondary, modifier: Modifier = Modifier) {
    val ts = tone(t)
    Row(
        modifier.height(32.dp).clip(RoundedCornerShape(Theme.radii.s)).background(ts.container).padding(start = 8.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(icon, size = 18.dp, tint = ts.onContainer)
        Text(text, style = Theme.type.style(13, 600), color = ts.onContainer, maxLines = 1)
    }
}

/** Icona in un riquadro tinto (righe delle sezioni di Tu, impostazioni). */
@Composable
fun IconBadge(icon: String, color: Color, size: Dp = 40.dp) {
    Box(Modifier.size(size).clip(RoundedCornerShape(size * 0.34f)).background(color.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
        Icon(icon, size = size * 0.58f, tint = color)
    }
}

/** Iniziali in una forma, nel colore indicato. */
@Composable
fun Avatar(initials: String, size: Dp = 64.dp, color: Color? = null, shape: String = "circle", rotation: Float = 0f) {
    val fill = color?.copy(alpha = 0.22f)?.mix(Theme.scheme.surfaceContainerHigh, 1f) ?: Theme.scheme.primaryContainer
    val ink = color ?: Theme.scheme.onPrimaryContainer
    Shaped(shape, if (color != null) color.copy(alpha = 0.2f) else fill, size, rotation = rotation) {
        Text(initials, style = Theme.type.numeral(size.value * 0.36f, 650), color = ink)
    }
}

@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, subtitle: String? = null, trailing: (@Composable () -> Unit)? = null) {
    Row(modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = Theme.type.style(22, 500, rounded = true), color = Theme.scheme.onSurface)
            if (subtitle != null) Text(subtitle, style = Theme.type.style(14), color = Theme.scheme.onSurfaceVariant)
        }
        trailing?.invoke()
    }
}

@Composable
fun Eyebrow(text: String, color: Color = Theme.scheme.onSurfaceVariant) {
    Text(text.uppercase(), style = Theme.type.style(12, 650, tracking = 0.6), color = color)
}

@Composable
fun DetailRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label, style = Theme.type.style(14), color = Theme.scheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(value, style = Theme.type.style(14, 500), color = Theme.scheme.onSurface, textAlign = TextAlign.End)
    }
}

@Composable
fun StatusLine(text: String, icon: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(icon, size = 20.dp, tint = color)
        Text(text, style = Theme.type.style(14, 600), color = color)
    }
}

// MARK: - Card

@Composable
fun Card(modifier: Modifier = Modifier, padding: Dp = 16.dp, color: Color = Theme.scheme.surfaceContainer, onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(Theme.radii.xl)
    var m = modifier.fillMaxWidth().clip(shape).background(color)
    if (onClick != null) m = m.clickable(onClick = onClick)
    Column(m.padding(padding), content = content)
}

/**
 * Riquadro con un numero (assenze, ritardi, medie…), con l'icona in una forma. In una griglia
 * collegata (`corners`) solo gli angoli verso l'esterno sono ampi.
 */
@Composable
fun StatTile(
    title: String, value: String, icon: String, color: Color, shape: String, modifier: Modifier = Modifier,
    corners: String = "", vertical: Boolean = false, onClick: (() -> Unit)? = null,
) {
    val r = Theme.radii
    val w = r.li
    val n = r.xs
    val tileShape = if (corners.isEmpty()) RoundedCornerShape(w) else RoundedCornerShape(
        topStart = if (corners in setOf("tl", "l")) w else n, topEnd = if (corners in setOf("tr", "r")) w else n,
        bottomStart = if (corners in setOf("bl", "l")) w else n, bottomEnd = if (corners in setOf("br", "r")) w else n,
    )
    var m = modifier.clip(tileShape).background(Theme.scheme.surfaceContainer)
    if (onClick != null) m = m.clickable(onClick = onClick)
    val art = @Composable {
        Shaped(shape, color.copy(alpha = 0.18f).mix(Theme.scheme.surfaceContainerHigh, 1f).let { color.copy(alpha = 0.2f) }, 40.dp) {
            Icon(icon, size = 20.dp, tint = color)
        }
    }
    if (vertical) {
        Column(m.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            art()
            Column {
                Text(value, style = Theme.type.numeral(28), color = Theme.scheme.onSurface)
                Text(title, style = Theme.type.style(13, 550), color = Theme.scheme.onSurfaceVariant, maxLines = 2)
            }
        }
    } else {
        Row(m.heightIn(min = 84.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f)) {
                Text(value, style = Theme.type.numeral(30), color = Theme.scheme.onSurface, maxLines = 1)
                AutoShrinkText(title, Theme.type.style(13, 550), Theme.scheme.onSurfaceVariant, minScale = 0.8f)
            }
            art()
        }
    }
}

/** Testo su una riga che si rimpicciolisce (fino a `minScale`) se non ci sta. */
@Composable
fun AutoShrinkText(text: String, style: androidx.compose.ui.text.TextStyle, color: Color, minScale: Float = 0.7f, modifier: Modifier = Modifier, maxLines: Int = 1) {
    var scale by remember(text) { mutableStateOf(1f) }
    Text(
        text, modifier = modifier, color = color, maxLines = maxLines, softWrap = maxLines > 1, overflow = TextOverflow.Ellipsis,
        style = style.copy(fontSize = style.fontSize * scale, letterSpacing = style.letterSpacing),
        onTextLayout = { r -> if ((r.hasVisualOverflow) && scale > minScale) scale = maxOf(minScale, scale - 0.05f) },
    )
}

/** Data in un riquadro (iniziali del giorno e numero), come la vista Programma di Google Calendar. */
@Composable
fun DateBadge(day: LocalDate, highlighted: Boolean = false, today: Boolean = false, modifier: Modifier = Modifier) {
    val bg = when {
        today -> Theme.scheme.primary
        highlighted -> Theme.scheme.tertiaryContainer
        else -> Theme.scheme.surfaceContainerHigh
    }
    val fg = when {
        today -> Theme.scheme.onPrimary
        highlighted -> Theme.scheme.onTertiaryContainer
        else -> Theme.scheme.onSurface
    }
    Column(modifier.width(52.dp).height(58.dp).clip(RoundedCornerShape(Theme.radii.l)).background(bg), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text(Fmt.weekdayShort(day).uppercase(), style = Theme.type.style(11, 700, tracking = 0.6), color = fg)
        Text(day.dayOfMonth.toString(), style = Theme.type.numeral(22), color = fg)
    }
}

// MARK: - Stati

/** Equivalente di `ContentUnavailableView`: l'icona sta in una forma espressiva. */
@Composable
fun EmptyState(title: String, icon: String, description: String? = null, modifier: Modifier = Modifier, actions: (@Composable () -> Unit)? = null) {
    val shapeName = ShapeNames.all[(title.fold(0) { h, c -> (h * 31 + c.code) and 0x7FFFFFFF }) % ShapeNames.all.size]
    Column(modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 40.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Shaped(shapeName, Theme.scheme.secondaryContainer, 88.dp, Modifier.padding(bottom = 10.dp)) {
            Icon(icon, size = 40.dp, tint = Theme.scheme.onSecondaryContainer)
        }
        Text(title, style = Theme.type.style(22, 500, rounded = true), color = Theme.scheme.onSurface, textAlign = TextAlign.Center)
        if (description != null) Text(description, style = Theme.type.style(14), color = Theme.scheme.onSurfaceVariant, textAlign = TextAlign.Center)
        if (actions != null) Box(Modifier.padding(top = 8.dp)) { actions() }
    }
}

@Composable
fun LoadingCard(text: String = "Caricamento…", modifier: Modifier = Modifier) {
    Card(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            LoadingIndicator(Modifier.size(40.dp))
            Text(text, style = Theme.type.style(15), color = Theme.scheme.onSurfaceVariant)
        }
    }
}

@Composable
fun StatusBanner(message: String, icon: String = "wifiOff", modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(Theme.radii.l)).background(Theme.scheme.surfaceContainerHigh).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(icon, size = 20.dp, tint = Theme.scheme.onSurfaceVariant)
        Text(message, style = Theme.type.style(14), color = Theme.scheme.onSurfaceVariant)
    }
}

@Composable
fun WarningBox(text: String) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(Theme.radii.l)).background(Theme.colors.poor.container).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon("alertCircleFill", size = 20.dp, tint = Theme.colors.poor.onContainer)
        Text(text, style = Theme.type.style(14, 600), color = Theme.colors.poor.onContainer)
    }
}

// MARK: - Sezioni comprimibili

@Composable
fun Chevron(collapsed: Boolean, rotateUp: Boolean = false) {
    val angle by animateFloatAsState(
        if (rotateUp) (if (collapsed) 0f else 180f) else (if (collapsed) -90f else 0f),
        Theme.motion.fastSpatialSpec(), label = "chevron",
    )
    Icon("chevronDown", size = 22.dp, tint = Theme.scheme.onSurfaceVariant, modifier = Modifier.rotate(angle))
}

/** Espansione di M3: il contenitore si allunga con la molla e il contenuto compare in dissolvenza. */
@Composable
fun Collapsible(expanded: Boolean, content: @Composable ColumnScope.() -> Unit) {
    AnimatedVisibility(
        visible = expanded,
        enter = expandVertically(Theme.motion.defaultSpatialSpec(), expandFrom = Alignment.Top) + fadeIn(Theme.motion.defaultEffectsSpec()),
        exit = shrinkVertically(Theme.motion.fastSpatialSpec(), shrinkTowards = Alignment.Top) + fadeOut(Theme.motion.fastEffectsSpec()),
    ) {
        Column(content = content)
    }
}

/**
 * Sezione segmentata: intestazione (icona nel colore, titolo, valore, avanzamento e freccia) e
 * sotto le voci in una lista segmentata. Toccando l'intestazione la lista si chiude.
 */
@Composable
fun SegmentedSection(
    title: String, icon: String, collapsed: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier,
    color: Color = Theme.scheme.primary, trailing: String? = null, progress: Float? = null, content: ListGroupScope.() -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 44.dp).clip(RoundedCornerShape(Theme.radii.m))
                .clickable { haptics.performHapticFeedback(HapticFeedbackType.SegmentTick); onToggle() }.padding(start = 8.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(icon, size = 20.dp, tint = color)
            Text(title, style = Theme.type.style(16, 600), color = Theme.scheme.onSurface)
            Spacer(Modifier.weight(1f))
            if (progress != null) WavyProgress(progress, Modifier.width(44.dp))
            if (trailing != null) Text(trailing, style = Theme.type.numeral(13, 600), color = Theme.scheme.onSurfaceVariant)
            Chevron(collapsed)
        }
        Collapsible(!collapsed) {
            Spacer(Modifier.height(6.dp))
            ListGroup(content = content)
        }
    }
}

// MARK: - Avanzamento

/** Barra di avanzamento lineare e ondulata di M3 Expressive. */
@Composable
fun WavyProgress(value: Float, modifier: Modifier = Modifier, color: Color = Theme.scheme.primary, trackColor: Color = Theme.scheme.secondaryContainer) {
    val p = value.coerceIn(0f, 1f)
    val animated by animateFloatAsState(p, Theme.motion.defaultSpatialSpec(), label = "progress")
    LinearWavyProgressIndicator(progress = { animated }, modifier = modifier, color = color, trackColor = trackColor)
}

/** Anello che si riempie in proporzione alla media (su 10), ondulato, con la media al centro. */
@Composable
fun AverageRing(value: Double?, size: Dp = 120.dp, lineWidth: Dp = 10.dp, showsLabel: Boolean = true) {
    val ts = tone(gradeTone(value))
    val p = ((value ?: 0.0) / 10.0).toFloat().coerceIn(0f, 1f)
    val animated by animateFloatAsState(p, Theme.motion.slowSpatialSpec(), label = "ring")
    val density = LocalDensity.current
    val stroke = with(density) { Stroke(width = lineWidth.toPx(), cap = StrokeCap.Round) }
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        CircularWavyProgressIndicator(
            progress = { animated }, modifier = Modifier.size(size), color = ts.color, trackColor = ts.container,
            stroke = stroke, trackStroke = stroke, gapSize = lineWidth * 0.6f,
            amplitude = if (size >= 60.dp) WavyProgressIndicatorDefaults.indicatorAmplitude else { _ -> 0f },
            wavelength = if (size >= 60.dp) maxOf(16.dp, size / 4) else WavyProgressIndicatorDefaults.CircularWavelength,
        )
        if (showsLabel) Text(GradeFormat.average(value), style = Theme.type.numeral(size.value * 0.25f), color = Theme.scheme.onSurface)
    }
}

/**
 * Voto in una forma di M3 Expressive: i voti che fanno media in un "biscotto" a nove lobi nel
 * colore del loro esito, gli altri in un cerchio.
 */
@Composable
fun GradeBadge(grade: Grade, size: Dp = 48.dp) {
    val counts = grade.countsTowardAverage
    val ts = tone(gradeToneOf(grade))
    val text = grade.displayValue
    val fontSize = size.value * when { text.length > 3 -> 0.27f; text.length > 2 -> 0.32f; else -> 0.4f }
    Shaped(if (counts) "cookie9Sided" else "circle", if (counts) ts.container else Theme.scheme.surfaceContainerHighest, size) {
        Text(
            text, style = Theme.type.numeral(fontSize, 700).copy(textDecoration = if (grade.canceled) TextDecoration.LineThrough else null),
            color = if (counts) ts.onContainer else Theme.scheme.onSurfaceVariant, maxLines = 1,
        )
    }
}

// MARK: - Filtri e scelte

/** Chip di filtro: quando è scelto mostra il segno di spunta. */
@Composable
fun BCWFilterChip(title: String, selected: Boolean, onClick: () -> Unit, icon: String? = null, trailingIcon: String? = null) {
    FilterChip(
        selected = selected, onClick = onClick, label = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        leadingIcon = when {
            selected && trailingIcon == null -> ({ Icon("check", size = 18.dp) })
            icon != null -> ({ Icon(icon, size = 18.dp) })
            else -> null
        },
        trailingIcon = trailingIcon?.let { { Icon(it, size = 18.dp) } },
        shape = RoundedCornerShape(Theme.radii.s),
        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Theme.scheme.secondaryContainer),
    )
}

/** Fila di chip che scorre in orizzontale. */
@Composable
fun ChipRow(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    Row(modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically, content = content)
}

@Composable
fun ChipFlow(modifier: Modifier = Modifier, center: Boolean = false, content: @Composable () -> Unit) {
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp, if (center) Alignment.CenterHorizontally else Alignment.Start), verticalArrangement = Arrangement.spacedBy(8.dp)) { content() }
}

/**
 * Gruppo di pulsanti collegati di M3 Expressive (scelta singola): 2 dp tra i pulsanti, angoli
 * interni piccoli ed esterni pieni; quello scelto diventa tutto arrotondato e pieno.
 */
@Composable
fun <T> ConnectedGroup(
    options: List<T>, selected: T, onSelect: (T) -> Unit, modifier: Modifier = Modifier, height: Dp = 48.dp,
    label: @Composable (T, Boolean) -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)) {
        options.forEachIndexed { index, option ->
            val checked = option == selected
            ToggleButton(
                checked = checked,
                onCheckedChange = { if (!checked) { haptics.performHapticFeedback(HapticFeedbackType.SegmentTick); onSelect(option) } },
                modifier = Modifier.weight(1f).heightIn(min = height).semantics { role = Role.RadioButton },
                shapes = when {
                    options.size == 1 -> ToggleButtonDefaults.shapes()
                    index == 0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                    index == options.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                },
                colors = ToggleButtonDefaults.toggleButtonColors(containerColor = Theme.scheme.surfaceContainerHigh, contentColor = Theme.scheme.onSurfaceVariant),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
            ) { label(option, checked) }
        }
    }
}

/** Valore con i pulsanti meno e più. */
@Composable
fun Stepper(value: String, onMinus: () -> Unit, onPlus: () -> Unit, canMinus: Boolean = true, canPlus: Boolean = true) {
    val haptics = LocalHapticFeedback.current
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        FilledTonalIconButton(onClick = { haptics.performHapticFeedback(HapticFeedbackType.SegmentTick); onMinus() }, enabled = canMinus, modifier = Modifier.size(36.dp)) { Icon("minus", size = 20.dp) }
        Text(value, style = Theme.type.numeral(17), color = Theme.scheme.onSurface, modifier = Modifier.widthIn(min = 36.dp), textAlign = TextAlign.Center)
        FilledTonalIconButton(onClick = { haptics.performHapticFeedback(HapticFeedbackType.SegmentTick); onPlus() }, enabled = canPlus, modifier = Modifier.size(36.dp)) { Icon("plus", size = 20.dp) }
    }
}

/** Scelta da un elenco: un pulsante che apre un menu. */
@Composable
fun <T> SelectButton(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit, modifier: Modifier = Modifier, maxLines: Int = 1, alignEnd: Boolean = false) {
    var open by remember { mutableStateOf(false) }
    val current = options.firstOrNull { it.first == selected }?.second ?: ""
    // Con `alignEnd` il valore sta a destra dello spazio che ha (come i valori delle righe dei moduli).
    Box(modifier, contentAlignment = if (alignEnd) Alignment.CenterEnd else Alignment.TopStart) {
        Row(
            Modifier.clip(RoundedCornerShape(Theme.radii.s)).clickable { open = true }.padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(current, style = Theme.type.style(14, 550), color = Theme.scheme.primary, maxLines = maxLines, overflow = TextOverflow.Ellipsis,
                textAlign = if (alignEnd) TextAlign.End else TextAlign.Start, modifier = Modifier.weight(1f, fill = false))
            Icon("chevronUpDown", size = 18.dp, tint = Theme.scheme.primary)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, shape = RoundedCornerShape(Theme.radii.l)) {
            for ((value, label) in options) {
                DropdownMenuItem(
                    text = { Text(label) }, onClick = { open = false; onSelect(value) },
                    trailingIcon = if (value == selected) ({ Icon("check", size = 20.dp) }) else null,
                )
            }
        }
    }
}

/** Contenuto con il colore del testo dato. */
@Composable
fun WithContentColor(color: Color, content: @Composable () -> Unit) = CompositionLocalProvider(LocalContentColor provides color, content = content)

@Composable
fun Modifier.noRippleClickable(onClick: () -> Unit): Modifier = this.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
