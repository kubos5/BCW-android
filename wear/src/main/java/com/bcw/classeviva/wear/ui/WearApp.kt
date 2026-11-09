package com.bcw.classeviva.wear.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.EdgeButton
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ProgressIndicatorDefaults
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.TimeText
import androidx.wear.compose.material3.curvedText
import androidx.wear.compose.material3.timeTextCurvedText
import androidx.wear.compose.material3.timeTextSeparator
import com.bcw.classeviva.wear.data.ScheduleNow
import com.bcw.classeviva.wear.data.WatchLesson
import com.bcw.classeviva.wear.data.WatchPayload
import com.bcw.classeviva.wear.data.dayLabel
import com.bcw.classeviva.wear.data.durationString
import com.bcw.classeviva.wear.data.nowLocalDateTime
import com.bcw.classeviva.wear.data.scheduleNow
import com.bcw.classeviva.wear.data.shortDate
import com.bcw.classeviva.wear.data.timeString
import com.bcw.classeviva.wear.data.weekFrom
import com.bcw.classeviva.wear.ui.theme.LocalWearColors
import com.bcw.classeviva.wear.ui.theme.WearColors
import com.bcw.classeviva.wear.ui.theme.accent
import com.bcw.classeviva.wear.ui.theme.container
import com.bcw.classeviva.wear.ui.theme.onContainer
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.Locale

/**
 * Le due schermate dell'orologio: "Adesso", la lezione in corso (o la prossima) a tutto schermo
 * sul colore della materia, con l'anello di avanzamento attorno al bordo; e "Tutto l'orario", la
 * settimana in una lista trasformante. Material 3 Expressive: l'ora e l'etichetta della lezione
 * curvano lungo il bordo in alto, il pulsante di bordo chiude lo schermo in basso, al centro
 * tipografia display grande.
 */
@Composable
fun WearApp(payload: WatchPayload) {
    val colors = LocalWearColors.current
    var now by remember { mutableStateOf(nowLocalDateTime()) }
    // Il minuto che cambia basta per il conto alla rovescia: niente aggiornamenti inutili.
    LaunchedEffect(Unit) {
        while (true) {
            delay(10_000)
            now = nowLocalDateTime()
        }
    }
    val schedule = remember(payload, now) { scheduleNow(payload, now) }
    var showTimetable by rememberSaveable { mutableStateOf(false) }
    BackHandler(enabled = showTimetable) { showTimetable = false }

    val tint = (schedule.current ?: schedule.next)?.tint
    val heroBackground = tint?.container(colors) ?: colors.surfaceContainerHigh
    val onHero = tint?.onContainer(colors) ?: colors.onSurface

    Box(Modifier.fillMaxSize().background(if (showTimetable) colors.surface else heroBackground)) {
        // AppScaffold rende attivo lo schermo (senza, il TimeText non compare) e mostra l'ora curva
        // in alto; lo sfondo trasparente lascia vedere il colore della materia.
        AppScaffold(
            containerColor = Color.Transparent,
            timeText = {
                if (showTimetable) {
                    TimeText()
                } else {
                    TimeText { time ->
                        timeTextCurvedText(time)
                        timeTextSeparator()
                        curvedText(shortLabel(schedule), color = onHero.copy(alpha = 0.8f))
                    }
                }
            },
        ) {
            if (showTimetable) {
                TimetableScreen(payload, colors, now, onBack = { showTimetable = false })
            } else {
                NowScreen(payload, schedule, colors, onOpenTimetable = { showTimetable = true })
            }
        }
    }
}

// MARK: - Adesso

@Composable
private fun NowScreen(payload: WatchPayload, schedule: ScheduleNow, colors: WearColors, onOpenTimetable: () -> Unit) {
    val shown = schedule.current ?: schedule.next
    val on = shown?.tint?.onContainer(colors) ?: colors.onSurface
    val current = schedule.current

    // Niente ScreenScaffold qui: con uno stato di scroll scollegato da una lista reale
    // lo scaffold restringe l'area del contenuto e taglia i testi. L'ora curva in alto
    // è resa dall'AppScaffold in WearApp.
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (current != null) {
            // Anello di avanzamento lungo il bordo: la firma di Material 3 Expressive.
            val fraction = ((schedule.minute - current.start).toFloat() / current.duration.coerceAtLeast(1)).coerceIn(0f, 1f)
            CircularProgressIndicator(
                progress = { fraction },
                modifier = Modifier.fillMaxSize(),
                colors = ProgressIndicatorDefaults.colors().copy(
                    indicatorColor = on,
                    trackColor = on.copy(alpha = 0.25f),
                ),
            )
        }
        // Inset reali per lo schermo XL round (240dp): il tempo curvo occupa in alto ~30dp,
        // il pulsante di bordo riserva ~56dp al fondo. Il resto è tutto spazio per la hero.
        Column(
            Modifier
                .fillMaxSize()
                .padding(top = 30.dp, bottom = 60.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            HeroContent(payload, schedule, colors, on)
        }
        // Il pulsante di bordo segue la curvatura del bordo inferiore.
        EdgeButton(
            onClick = onOpenTimetable,
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Text("Tutto l'orario")
        }
    }
}

/** Il contenuto centrale della schermata "Adesso": la materia, i dettagli e i tempi. */
@Composable
private fun HeroContent(payload: WatchPayload, schedule: ScheduleNow, colors: WearColors, on: Color) {
    val shown = schedule.current ?: schedule.next
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        if (payload.demo) {
            Text(
                "DEMO",
                style = MaterialTheme.typography.labelSmall,
                color = on,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(on.copy(alpha = 0.16f))
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            )
        }
        if (shown == null) {
            Spacer(Modifier.height(8.dp))
            Text(
                "Nessuna lezione",
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = on,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Crea l'orario in BCW sul telefono",
                style = MaterialTheme.typography.bodySmall,
                color = on.copy(alpha = 0.8f),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            return@Column
        }
        Spacer(Modifier.height(10.dp))
        Text(
            shown.title.ifEmpty { "Senza titolo" },
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
            color = on,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(),
        )
        val detail = listOf(shown.room, shown.teacher).filter { it.isNotBlank() }.joinToString(" · ")
        if (detail.isNotEmpty()) {
            Spacer(Modifier.height(2.dp))
            Text(
                detail,
                style = MaterialTheme.typography.bodyMedium,
                color = on.copy(alpha = 0.8f),
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(Modifier.height(12.dp))
        Text(
            "${timeString(shown.start)} – ${timeString(shown.end)}",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = on,
        )
        if (schedule.current != null) {
            Spacer(Modifier.height(4.dp))
            Text(
                "Mancano ${durationString(shown.end - schedule.minute)}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = on,
            )
            val next = schedule.next
            if (next != null) {
                Spacer(Modifier.height(14.dp))
                NextRow(next, on)
            }
        } else {
            Spacer(Modifier.height(4.dp))
            Text(
                if (schedule.nextDay == schedule.today) {
                    "Inizia tra ${durationString(shown.start - schedule.minute)}"
                } else {
                    "Inizia ${dayLabel(schedule.nextDay ?: schedule.today, schedule.today).lowercase(Locale.ITALIAN)} alle ${timeString(shown.start)}"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = on.copy(alpha = 0.85f),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun NextRow(next: WatchLesson, on: Color) {
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(on.copy(alpha = 0.14f))
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("POI", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, color = on.copy(alpha = 0.7f))
        Spacer(Modifier.width(7.dp))
        Text(
            next.title.ifEmpty { "Senza titolo" },
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = on,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.width(7.dp))
        Text(timeString(next.start), style = MaterialTheme.typography.labelMedium, color = on.copy(alpha = 0.8f))
    }
}

// MARK: - Tutto l'orario

@Composable
private fun TimetableScreen(payload: WatchPayload, colors: WearColors, now: LocalDateTime, onBack: () -> Unit) {
    val state = rememberTransformingLazyColumnState()
    val today = now.toLocalDate()
    ScreenScaffold(scrollState = state) { padding ->
        TransformingLazyColumn(
            state = state,
            contentPadding = padding,
            modifier = Modifier.fillMaxSize(),
        ) {
            item(key = "heading") {
                Text(
                    "Tutto l'orario",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = colors.onSurface,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp, bottom = 2.dp),
                )
            }
            if (payload.isEmpty) {
                item(key = "empty") {
                    Text(
                        "Crea l'orario in BCW sul telefono: arriverà qui da solo.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurface,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                    )
                }
            } else {
                for ((date, lessons) in weekFrom(payload, today)) {
                    item(key = "day-$date") {
                        DayHeader(date, today, colors)
                    }
                    for (lesson in lessons) {
                        item(key = "${lesson.id}-$date") {
                            val minute = now.hour * 60 + now.minute
                            val isNow = date == today && minute in lesson.start until lesson.end
                            LessonRow(lesson, isNow, colors)
                        }
                    }
                }
                item(key = "footer") {
                    Text(
                        if (payload.demo) "Demo · apri BCW sul telefono per sincronizzare" else "Sincronizzato dal telefono",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun DayHeader(date: LocalDate, today: LocalDate, colors: WearColors) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(top = 10.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            dayLabel(date, today),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = colors.onSurface,
        )
        Spacer(Modifier.width(6.dp))
        Text(shortDate(date), style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
    }
}

@Composable
private fun LessonRow(lesson: WatchLesson, isNow: Boolean, colors: WearColors) {
    val on = if (isNow) lesson.tint.onContainer(colors) else colors.onSurface
    val secondary = if (isNow) on.copy(alpha = 0.75f) else colors.onSurfaceVariant
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(if (isNow) lesson.tint.container(colors) else colors.surfaceContainerHigh)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.width(46.dp)) {
            Text(timeString(lesson.start), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, color = on)
            Text(timeString(lesson.end), style = MaterialTheme.typography.labelSmall, color = secondary)
        }
        Box(
            Modifier
                .width(5.dp)
                .height(28.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(lesson.tint.accent(colors)),
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                lesson.title.ifEmpty { if (lesson.isBreak) "Intervallo" else "Senza titolo" },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isNow) FontWeight.Bold else FontWeight.Medium,
                color = on,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val detail = listOf(lesson.room, lesson.teacher).filter { it.isNotBlank() }.joinToString(" · ")
            if (detail.isNotEmpty()) {
                Text(detail, style = MaterialTheme.typography.labelSmall, color = secondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (isNow) {
            Spacer(Modifier.width(6.dp))
            Text(
                "ORA",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = on,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(on.copy(alpha = 0.16f))
                    .padding(horizontal = 7.dp, vertical = 3.dp),
            )
        }
    }
}

// MARK: - Etichette

/** L'etichetta breve che curva lungo il bordo in alto, accanto all'ora. */
private fun shortLabel(schedule: ScheduleNow): String {
    schedule.current?.let { return "ADESSO" }
    val next = schedule.next ?: return ""
    return if (schedule.nextDay == schedule.today) {
        val minutes = next.start - schedule.minute
        when {
            minutes >= 60 -> "TRA ${minutes / 60} H"
            else -> "TRA $minutes MIN"
        }
    } else {
        dayLabel(schedule.nextDay ?: schedule.today, schedule.today).uppercase(Locale.ITALIAN)
    }
}
