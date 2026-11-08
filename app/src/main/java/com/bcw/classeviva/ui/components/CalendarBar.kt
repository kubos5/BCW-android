package com.bcw.classeviva.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.bcw.classeviva.data.AgendaKind
import com.bcw.classeviva.data.AppModel
import com.bcw.classeviva.util.Dates
import com.bcw.classeviva.util.Fmt
import com.bcw.classeviva.ui.icons.Icon
import com.bcw.classeviva.ui.shell.Transitions
import com.bcw.classeviva.ui.theme.Theme
import java.time.LocalDate

/** Pallini sotto il numero: verifiche, compiti, eventi e assenze. */
@Composable
fun agendaDots(model: AppModel, day: LocalDate, selected: Boolean): List<Color> {
    val events = model.events(day)
    val onSel = Theme.scheme.onPrimary
    val dots = mutableListOf<Color>()
    if (events.any { it.kind == AgendaKind.test }) dots += if (selected) onSel else Theme.scheme.tertiary
    if (events.any { it.kind == AgendaKind.homework }) dots += if (selected) onSel.copy(alpha = 0.7f) else Theme.colors.neutral.color
    if (events.any { it.kind == AgendaKind.event }) dots += if (selected) onSel.copy(alpha = 0.7f) else Theme.colors.fair.color
    if (model.absencesOn(day).isNotEmpty()) dots += if (selected) onSel.copy(alpha = 0.7f) else Theme.colors.poor.color
    return dots
}

/** "12 – 18 ottobre", oppure "28 set – 4 ott" a cavallo di due mesi. */
fun weekRangeTitle(week: LocalDate): String {
    val end = week.plusDays(6)
    return if (week.month == end.month) "${week.dayOfMonth} – ${Fmt.dayMonth(end)}" else "${Fmt.shortDay(week)} – ${Fmt.shortDay(end)}"
}

fun monthWeeks(month: LocalDate): List<LocalDate> {
    val first = month.withDayOfMonth(1)
    val last = first.plusMonths(1).minusDays(1)
    val weeks = mutableListOf<LocalDate>()
    var week = Dates.startOfWeek(first)
    while (!week.isAfter(last)) {
        weeks += week
        week = week.plusDays(7)
    }
    return weeks
}

private val weekdaySymbols = listOf("L", "M", "M", "G", "V", "S", "D")

@Composable
private fun DayButton(
    day: LocalDate, selected: Boolean, compact: Boolean, outside: Boolean, quiet: Boolean, dots: List<Color>,
    modifier: Modifier, onClick: () -> Unit,
) {
    val r = Theme.radii
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed && !selected) 0.94f else 1f, Theme.motion.fastSpatialSpec(), label = "dayScale")
    val radius by animateDpAsState(
        when {
            selected -> if (compact) 22.dp else 34.dp
            pressed -> r.s
            compact -> r.m
            else -> r.l
        }, Theme.motion.fastSpatialSpec(), label = "dayRadius",
    )
    val bg by animateColorAsState(
        when {
            selected -> Theme.scheme.primary
            outside -> Theme.scheme.surfaceContainer
            else -> Theme.scheme.surfaceContainerHigh
        }, Theme.motion.defaultEffectsSpec(), label = "dayBg",
    )
    val today = Dates.isToday(day)
    val fg = when {
        selected -> Theme.scheme.onPrimary
        today -> Theme.scheme.primary
        quiet -> Theme.scheme.onSurfaceVariant
        else -> Theme.scheme.onSurface
    }
    val shape = RoundedCornerShape(radius)
    Column(
        modifier.scale(scale).clip(shape).background(bg)
            .then(if (today && !selected) Modifier.border(1.5.dp, Theme.scheme.primary, shape) else Modifier)
            .clickable(interactionSource = interaction, indication = androidx.compose.material3.ripple(), onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        val alpha = if (outside && !selected) 0.45f else 1f
        if (!compact) Text(Fmt.weekdayShort(day), style = Theme.type.style(12, if (selected) 700 else 550), color = fg, maxLines = 1, modifier = Modifier.alpha(alpha))
        Text(day.dayOfMonth.toString(), style = Theme.type.numeral(if (compact) 16 else 20, if (selected || today) 700 else 550), color = fg, modifier = Modifier.alpha(alpha))
        Row(Modifier.height(if (compact) 4.dp else 5.dp).alpha(alpha), horizontalArrangement = Arrangement.spacedBy(if (compact) 2.dp else 3.dp)) {
            for (c in dots) Box(Modifier.size(if (compact) 4.dp else 5.dp).clip(CircleShape).background(c))
        }
    }
}

/**
 * Settimana o mese come gruppo di pulsanti di M3 Expressive: il giorno scelto è pieno nel
 * primario, tutto arrotondato e più largo. Sopra il periodo (apre il selettore della data) e le
 * frecce; scorrendo di lato si cambia settimana (o mese).
 */
@Composable
fun CalendarBar(
    model: AppModel,
    selected: LocalDate,
    month: Boolean,
    onSelect: (LocalDate) -> Unit,
    onShift: (Int) -> Unit,
    onPick: () -> Unit,
    modifier: Modifier = Modifier,
    dir: Int = 0,
    dots: @Composable (LocalDate, Boolean) -> List<Color> = { day, sel -> agendaDots(model, day, sel) },
) {
    val haptics = LocalHapticFeedback.current
    val unit = if (month) "Mese" else "Settimana"
    val title = if (month) Fmt.monthYear(selected) else weekRangeTitle(Dates.startOfWeek(selected))
    Column(modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth().height(44.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier.padding(start = 0.dp).height(40.dp).clip(RoundedCornerShape(20.dp)).clickable(onClick = onPick).padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(title, style = Theme.type.style(15, 600), color = Theme.scheme.onSurfaceVariant)
                Icon("chevronDown", size = 18.dp, tint = Theme.scheme.onSurfaceVariant)
            }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { onShift(-1) }) { Icon("chevronLeft", contentDescription = "$unit precedente") }
            IconButton(onClick = { onShift(1) }) { Icon("chevronRight", contentDescription = if (month) "Mese successivo" else "Settimana successiva") }
        }
        if (month) {
            Row(Modifier.fillMaxWidth().height(20.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                for (w in weekdaySymbols) Text(w, style = Theme.type.style(12, 600), color = Theme.scheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
            }
        }
        val pageKey = if (month) selected.withDayOfMonth(1) else Dates.startOfWeek(selected)
        var dragged by remember { mutableFloatStateOf(0f) }
        val reduced = Theme.reducedMotion
        AnimatedContent(
            targetState = pageKey,
            transitionSpec = { Transitions.sharedAxisX(if (targetState > initialState) 1 else -1, reduced) },
            modifier = Modifier.fillMaxWidth().pointerInput(month) {
                detectHorizontalDragGestures(
                    onDragStart = { dragged = 0f },
                    onDragEnd = {
                        if (kotlin.math.abs(dragged) > 60) {
                            haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                            onShift(if (dragged < 0) 1 else -1)
                        }
                        dragged = 0f
                    },
                ) { _, amount -> dragged += amount }
            },
            label = "calendarPage",
        ) { key ->
            if (month) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    for (week in monthWeeks(key)) {
                        Row(Modifier.fillMaxWidth().height(44.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            for (i in 0..6) {
                                val day = week.plusDays(i.toLong())
                                val sel = day == selected
                                DayButton(
                                    day, sel, compact = true, outside = day.month != key.month,
                                    quiet = (Dates.isWeekend(day) || model.calendarStatus(day)?.isHoliday == true) && !sel,
                                    dots = dots(day, sel), modifier = Modifier.weight(1f).height(44.dp),
                                ) { haptics.performHapticFeedback(HapticFeedbackType.SegmentTick); onSelect(day) }
                            }
                        }
                    }
                }
            } else {
                Row(Modifier.fillMaxWidth().height(68.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    for (i in 0..6) {
                        val day = key.plusDays(i.toLong())
                        val sel = day == selected
                        val weight by animateFloatAsState(if (sel) 1.45f else 1f, Theme.motion.defaultSpatialSpec(), label = "weight")
                        DayButton(
                            day, sel, compact = false, outside = false,
                            quiet = (Dates.isWeekend(day) || model.calendarStatus(day)?.isHoliday == true) && !sel,
                            dots = dots(day, sel), modifier = Modifier.weight(weight).height(68.dp),
                        ) { haptics.performHapticFeedback(HapticFeedbackType.SegmentTick); onSelect(day) }
                    }
                }
            }
        }
    }
}

@Suppress("unused")
private val unusedDp: Dp = 0.dp
