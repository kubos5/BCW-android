package com.bcw.classeviva.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bcw.classeviva.data.Absence
import com.bcw.classeviva.data.AbsenceKind
import com.bcw.classeviva.data.AgendaEvent
import com.bcw.classeviva.data.AgendaKind
import com.bcw.classeviva.data.Lesson
import com.bcw.classeviva.util.Dates
import com.bcw.classeviva.util.Fmt
import com.bcw.classeviva.ui.icons.Icon
import com.bcw.classeviva.ui.theme.Theme
import java.time.LocalDate

/** Nome di una materia con il rombo nel suo colore. */
@Composable
fun SubjectTag(name: String, subjectId: Long?, modifier: Modifier = Modifier, color: Color? = null) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.size(10.dp).rotate(45f).clip(RoundedCornerShape(3.dp)).background(color ?: subjectColor(subjectId)))
        Text(name, style = Theme.type.style(15, 600), color = Theme.scheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun RowCaption(text: String, modifier: Modifier = Modifier) {
    Text(text, style = Theme.type.style(12), color = Theme.scheme.onSurfaceVariant, modifier = modifier.padding(top = 2.dp))
}

/**
 * Riga di un compito, una verifica o un evento. Il cerchio a sinistra segna i compiti come fatti;
 * toccando il testo lo si apre (se è tagliato); tenendo premuto si apre il menu con le altre azioni.
 */
@Composable
fun AgendaEventRow(
    event: AgendaEvent, done: Boolean, expanded: Boolean, onToggleDone: () -> Unit, onExpand: () -> Unit,
    showsDate: Boolean = false,
) {
    val color = subjectColor(event.subjectId)
    val haptics = LocalHapticFeedback.current
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        if (event.kind == AgendaKind.event) {
            Box(Modifier.size(32.dp).offset(x = (-2).dp, y = (-4).dp), contentAlignment = Alignment.Center) {
                Icon("calendar", size = 22.dp, tint = color)
            }
        } else {
            val interaction = remember { MutableInteractionSource() }
            val pressed by interaction.collectIsPressedAsState()
            val scale by animateFloatAsState(if (pressed) 0.86f else 1f, Theme.motion.fastSpatialSpec(), label = "check")
            Box(
                Modifier.size(32.dp).offset(x = (-2).dp, y = (-4).dp).scale(scale).clip(CircleShape)
                    .clickable(interactionSource = interaction, indication = androidx.compose.material3.ripple(bounded = false)) {
                        haptics.performHapticFeedback(if (done) HapticFeedbackType.ToggleOff else HapticFeedbackType.ToggleOn)
                        onToggleDone()
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(if (done) "checkCircleFill" else "circle", size = 24.dp, tint = if (done) Theme.colors.good.color else color,
                    contentDescription = if (done) "Fatto" else "Da fare")
            }
        }
        Column(Modifier.weight(1f).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onExpand).animateContentSize(Theme.motion.defaultSpatialSpec())) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SubjectTag(event.title, event.subjectId, Modifier.weight(1f, fill = false))
                if (event.kind == AgendaKind.test) Pill("Verifica", bold = true)
            }
            Text(
                event.notes.ifEmpty { "Nessuna descrizione" },
                style = Theme.type.style(15, 400, lineHeight = androidx.compose.ui.unit.TextUnit.Unspecified).copy(textDecoration = if (done) TextDecoration.LineThrough else null),
                color = if (done) Theme.scheme.onSurfaceVariant else Theme.scheme.onSurface,
                maxLines = if (expanded) Int.MAX_VALUE else 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 3.dp),
            )
            val meta = buildList {
                if (showsDate) add(Fmt.relativeDayName(event.day))
                if (event.kind != AgendaKind.homework) add(event.timeDescription)
                if (event.authorName.isNotEmpty() && event.subjectName != null) add(event.authorName)
            }
            if (meta.isNotEmpty()) RowCaption(meta.joinToString(" · "))
        }
    }
}

private fun absenceTone(a: Absence): Tone = when (a.kind) {
    AbsenceKind.absence -> Tone.poor
    AbsenceKind.late, AbsenceKind.shortLate -> Tone.fair
    AbsenceKind.earlyExit -> Tone.neutral
}

@Composable
fun AbsenceRow(absence: Absence, showsReason: Boolean = false, modifier: Modifier = Modifier) {
    val ts = tone(absenceTone(absence))
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Shaped("cookie6Sided", ts.container, 40.dp) {
            Text(absence.kind.letter, style = Theme.type.numeral(15), color = ts.onContainer)
        }
        Column(Modifier.weight(1f)) {
            Text(absence.kind.title, style = Theme.type.style(16, 550), color = Theme.scheme.onSurface)
            Text(absence.detail, style = Theme.type.style(14), color = Theme.scheme.onSurfaceVariant)
            if (showsReason && absence.justificationReason != null) RowCaption(absence.justificationReason)
        }
        if (absence.isJustified) Icon("checkSealFill", size = 22.dp, tint = Theme.colors.good.color, contentDescription = "Giustificata")
        else Pill("Da giustificare", color = Theme.colors.poor.color)
    }
}

@Composable
fun LessonRow(lesson: Lesson) {
    val tint = subjectColor(lesson.subjectId)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Column(
            Modifier.width(44.dp).height(48.dp).clip(RoundedCornerShape(14.dp)).background(tint.copy(alpha = 0.16f)),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
        ) {
            Text(lesson.hour.toString(), style = Theme.type.numeral(20), color = tint)
            Text("ORA", style = Theme.type.style(10, 600, tracking = 0.6), color = tint)
        }
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SubjectTag(lesson.subjectName, lesson.subjectId, Modifier.weight(1f, fill = false))
                if (lesson.duration > 1) RowCaption("${lesson.duration.toInt()} ore")
            }
            if (lesson.topic.isNotEmpty()) Text(lesson.topic, style = Theme.type.style(15, 400, lineHeight = androidx.compose.ui.unit.TextUnit.Unspecified), color = Theme.scheme.onSurface, modifier = Modifier.padding(top = 4.dp))
            val meta = listOf(lesson.type, lesson.authorName).filter { it.isNotEmpty() }.joinToString(" · ")
            if (meta.isNotEmpty()) RowCaption(meta)
        }
    }
}

/** Pulsante per tornare al giorno predefinito (accanto al giorno in grande). */
data class DayJump(val title: String, val action: () -> Unit)

/**
 * Il giorno in grande (Dashboard e Orario): "Domani" o il giorno della settimana, la data sotto, a
 * destra il pulsante per il giorno predefinito e sotto i chip di stato. Il nome che non ci sta si
 * rimpicciolisce.
 */
@Composable
fun DayHero(day: LocalDate, jump: DayJump?, chips: List<@Composable () -> Unit>, abbreviate: Boolean = false, modifier: Modifier = Modifier) {
    val diff = Dates.daysBetween(Dates.today(), day)
    val relative = diff in -1..1
    val title = when {
        relative -> Fmt.relativeDayName(day)
        abbreviate -> Fmt.relativeShortDayName(day)
        else -> Fmt.weekdayLong(day)
    }
    val subtitle = if (relative) Fmt.longDay(day) else Fmt.dayMonthYear(day)
    Column(modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 4.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f)) {
                AutoShrinkText(title, Theme.type.style(40, 500, rounded = true, tracking = -0.5, lineHeight = androidx.compose.ui.unit.TextUnit.Unspecified), Theme.scheme.onSurface)
                Text(subtitle, style = Theme.type.style(16, 500), color = Theme.scheme.onSurfaceVariant)
            }
            if (jump != null) {
                FilledTonalButton(onClick = jump.action, contentPadding = ButtonDefaults.SmallContentPadding, modifier = Modifier.height(40.dp)) {
                    Icon("today", size = 18.dp)
                    Text(jump.title, modifier = Modifier.padding(start = 6.dp))
                }
            }
        }
        if (chips.isNotEmpty()) ChipFlow { chips.forEach { it() } }
    }
}
