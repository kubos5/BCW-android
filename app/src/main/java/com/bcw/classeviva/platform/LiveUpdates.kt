package com.bcw.classeviva.platform

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.bcw.classeviva.BCWApplication
import com.bcw.classeviva.R
import com.bcw.classeviva.data.AppModel
import com.bcw.classeviva.data.Phase
import com.bcw.classeviva.data.timetable.EntryTint
import com.bcw.classeviva.data.timetable.LiveActivityChoice
import com.bcw.classeviva.data.timetable.TimeOfDay
import com.bcw.classeviva.data.timetable.timetableLayout
import com.bcw.classeviva.ui.theme.ThemeSettings
import com.bcw.classeviva.ui.theme.bcwColors
import com.bcw.classeviva.util.Dates
import java.time.LocalDate
import java.time.LocalDateTime

/** Una fase della giornata: una lezione (ore uguali di seguito unite), un intervallo o l'attesa. */
data class LivePhase(val start: Int, val end: Int, val title: String, val room: String, val teacher: String, val isBreak: Boolean, val hasTest: Boolean, val tint: EntryTint)

/**
 * Piano della lezione in corso per un giorno (come `TimetableActivityPlan` dell'app iOS): lezioni con
 * le ore uguali unite, gli intervalli che le interrompono, eventi con "Non mostrare" saltati.
 */
object LivePlan {
    fun phases(model: AppModel, day: LocalDate, showsByDefault: Boolean): List<LivePhase> {
        val store = model.timetable
        val entries = store.entriesOn(day, includingDraft = false).filter {
            when (it.liveActivity) {
                LiveActivityChoice.show -> true
                LiveActivityChoice.hide -> false
                null -> showsByDefault
            }
        }
        val content = store.dayContent(day, model.events(day), splitAtBreaks = true)
        val layout = timetableLayout(entries, splitAtBreaks = true)
        val lessons = layout.blocks.flatMap { block ->
            // Le lezioni perdono i tratti coperti da un intervallo: una lezione con un intervallo in mezzo diventa due.
            val first = block.entries.first()
            var pieces = listOf(block.start to block.end)
            for (b in layout.breaks) {
                pieces = pieces.flatMap { (s, e) ->
                    if (b.end <= s || b.start >= e) listOf(s to e)
                    else listOfNotNull((s to b.start).takeIf { b.start > s }, (b.end to e).takeIf { b.end < e })
                }
            }
            val hasTest = block.entries.any { content.badges[it.id]?.isTest == true }
            pieces.map { (s, e) -> LivePhase(s, e, first.displayTitle, first.room, first.teacher, false, hasTest, store.tint(first)) }
        }
        val firstLesson = lessons.minOfOrNull { it.start } ?: return emptyList()
        val lastLesson = lessons.maxOf { it.end }
        val breaks = layout.breaks.filter { it.end > firstLesson - 30 && it.start < lastLesson }
            .map { LivePhase(it.start, it.end, it.displayTitle, it.room, it.teacher, true, false, store.tint(it)) }
        return (lessons + breaks).sortedBy { it.start }
    }
}

/** La lezione in corso in una notifica che si aggiorna da sola (Live Updates su Android 16). */
object LiveUpdates {
    private const val TAG = "bcw.live"

    private fun pending(context: Context) = PendingIntent.getBroadcast(
        context, 7, Intent(context, LiveUpdateReceiver::class.java).setAction("com.bcw.classeviva.LIVE"),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    fun refresh(context: Context) {
        val app = context.applicationContext as? BCWApplication ?: return
        val model = app.model
        val manager = NotificationManagerCompat.from(context)
        val alarms = context.getSystemService(AlarmManager::class.java)
        alarms.cancel(pending(context))
        if (model.phase != Phase.signedIn || !Notifier.canNotify(context)) {
            manager.cancel(TAG, 0)
            return
        }
        val now = LocalDateTime.now()
        val minute = Dates.minuteOfDay(now)
        val phases = LivePlan.phases(model, now.toLocalDate(), model.preferences.liveUpdates)
        if (phases.isEmpty()) {
            manager.cancel(TAG, 0)
            scheduleTomorrow(context, alarms)
            return
        }
        val dayStart = phases.first().start
        val dayEnd = phases.filter { !it.isBreak }.maxOf { it.end }
        val windowStart = dayStart - 30
        val windowEnd = dayEnd + 10
        if (minute < windowStart) {
            manager.cancel(TAG, 0)
            schedule(alarms, context, now.toLocalDate().atStartOfDay().plusMinutes(windowStart.toLong()))
            return
        }
        if (minute >= windowEnd) {
            manager.cancel(TAG, 0)
            scheduleTomorrow(context, alarms)
            return
        }
        val current = phases.lastOrNull { it.start <= minute && minute < it.end && it.isBreak } ?: phases.firstOrNull { it.start <= minute && minute < it.end }
        val next = phases.firstOrNull { it.start > minute && !it.isBreak }
        val lessons = phases.filter { !it.isBreak }
        val dark = (context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
        val colors = bcwColors(context, ThemeSettings.from(model.preferences), dark)
        fun tintArgb(t: EntryTint): Int = when (t) {
            is EntryTint.Subject -> colors.subject(t.index).color.toArgbCompat()
            EntryTint.Break -> colors.breakTint.toArgbCompat()
            is EntryTint.Hex -> (0xFF000000.toInt() or t.hex)
        }
        val title: String
        val text: String
        val endMinute: Int
        when {
            current != null && !current.isBreak -> {
                val index = lessons.indexOf(current) + 1
                title = current.title + if (current.hasTest) " · verifica" else ""
                text = listOfNotNull(
                    current.room.ifEmpty { null }, current.teacher.ifEmpty { null },
                    next?.let { "Poi ${it.title} alle ${TimeOfDay.string(it.start)}" },
                ).joinToString(" · ").ifEmpty { "$index di ${lessons.size}" }
                endMinute = current.end
            }
            current != null -> {
                title = current.title
                text = next?.let { "Poi ${it.title}${if (it.room.isNotEmpty()) " in ${it.room}" else ""} alle ${TimeOfDay.string(it.start)}" } ?: "Ultima pausa"
                endMinute = current.end
            }
            next != null -> {
                title = if (minute < dayStart) "Tra poco: ${next.title}" else "Ora libera"
                text = "${next.title} alle ${TimeOfDay.string(next.start)}${if (next.room.isNotEmpty()) " · ${next.room}" else ""}"
                endMinute = next.start
            }
            else -> {
                title = "Lezioni finite"
                text = "Buon pomeriggio!"
                endMinute = windowEnd
            }
        }
        val endTime = now.toLocalDate().atStartOfDay().plusMinutes(endMinute.toLong())
        val style = NotificationCompat.ProgressStyle()
            .setStyledByProgress(true)
            .setProgress((minute - dayStart).coerceIn(0, dayEnd - dayStart))
        var cursor = dayStart
        for (p in phases.filter { it.end > dayStart }) {
            if (p.start > cursor) style.addProgressSegment(NotificationCompat.ProgressStyle.Segment(p.start - cursor).setColor(colors.scheme.outlineVariant.toArgbCompat()))
            val s = maxOf(p.start, cursor)
            val e = minOf(p.end, dayEnd)
            if (e > s) style.addProgressSegment(NotificationCompat.ProgressStyle.Segment(e - s).setColor(tintArgb(p.tint)))
            cursor = maxOf(cursor, e)
        }
        if (cursor < dayEnd) style.addProgressSegment(NotificationCompat.ProgressStyle.Segment(dayEnd - cursor))
        val remaining = maxOf(0, endMinute - minute)
        val builder = NotificationCompat.Builder(context, Notifier.LIVE)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(style)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setWhen(Dates.epochMillis(endTime))
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setShowWhen(true)
            .setShortCriticalText(if (remaining >= 60) "${remaining / 60}h${String.format(java.util.Locale.ROOT, "%02d", remaining % 60)}" else "${remaining}min")
            .setRequestPromotedOngoing(true)
            .setContentIntent(Notifier.openApp(context, "bcw://orario?giorno=${Dates.dayKey(now.toLocalDate())}", 77))
        current?.let { builder.setColor(tintArgb(it.tint)) }
        try {
            manager.notify(TAG, 0, builder.build())
        } catch (_: SecurityException) {
        }
        // Prossimo passaggio di fase.
        val boundaries = phases.flatMap { listOf(it.start, it.end) } + windowEnd
        val nextBoundary = boundaries.filter { it > minute }.minOrNull() ?: windowEnd
        schedule(alarms, context, now.toLocalDate().atStartOfDay().plusMinutes(nextBoundary.toLong()))
    }

    private fun scheduleTomorrow(context: Context, alarms: AlarmManager) {
        // Si ricontrolla domattina presto (l'orario di domani può cambiare nel frattempo).
        schedule(alarms, context, LocalDate.now().plusDays(1).atTime(6, 0))
    }

    private fun schedule(alarms: AlarmManager, context: Context, at: LocalDateTime) {
        alarms.setWindow(AlarmManager.RTC_WAKEUP, Dates.epochMillis(at), 60_000L, pending(context))
    }

    fun cancel(context: Context) {
        NotificationManagerCompat.from(context).cancel(TAG, 0)
        context.getSystemService(AlarmManager::class.java).cancel(pending(context))
    }
}

private fun androidx.compose.ui.graphics.Color.toArgbCompat(): Int = android.graphics.Color.argb(
    (alpha * 255).toInt(), (red * 255).toInt(), (green * 255).toInt(), (blue * 255).toInt(),
)

class LiveUpdateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        LiveUpdates.refresh(context)
    }
}

/** Dopo il riavvio del telefono o un cambio d'ora gli allarmi vanno rifatti. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as? BCWApplication ?: return
        app.model.rescheduleReminders()
        LiveUpdates.refresh(context)
    }
}
