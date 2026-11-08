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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.bcw.classeviva.widget.Widgets
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
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

/**
 * La lezione in corso in una notifica che si aggiorna da sola (Live Updates su Android 16), come
 * l'attività in diretta dell'app iOS: parte mezz'ora prima della prima lezione e sparisce dopo
 * l'ultima. Dalla scheda in cima all'orario di oggi si avvia o si ferma a mano; fermata (anche
 * togliendola dalle notifiche), quel giorno non riparte da sola. Lo stesso allarme ridisegna i
 * widget all'inizio e alla fine di ogni evento.
 */
object LiveUpdates {
    private const val TAG = "bcw.live"
    private const val ACTION_DISMISSED = "com.bcw.classeviva.LIVE_DISMISSED"

    /** Il giorno in cui la notifica è in vista (per il pulsante della scheda in cima all'orario). */
    var shownDay by mutableStateOf<LocalDate?>(null)
        private set

    private fun state(context: Context) = context.getSharedPreferences("bcw.live", Context.MODE_PRIVATE)

    private fun pending(context: Context) = PendingIntent.getBroadcast(
        context, 7, Intent(context, LiveUpdateReceiver::class.java).setAction("com.bcw.classeviva.LIVE"),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun dismissed(context: Context) = PendingIntent.getBroadcast(
        context, 8, Intent(context, LiveUpdateReceiver::class.java).setAction(ACTION_DISMISSED),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    /** Avvia (anche con l'impostazione spenta) o ferma la notifica per oggi. */
    fun setToday(context: Context, on: Boolean) {
        val today = Dates.dayKey(LocalDate.now())
        state(context).edit().putString(if (on) "forced" else "stopped", today).remove(if (on) "stopped" else "forced").apply()
        refresh(context)
    }

    internal fun onDismissed(context: Context) {
        state(context).edit().putString("stopped", Dates.dayKey(LocalDate.now())).remove("forced").apply()
        shownDay = null
    }

    fun refresh(context: Context) {
        val app = context.applicationContext as? BCWApplication ?: return
        val model = app.model
        val manager = NotificationManagerCompat.from(context)
        val alarms = context.getSystemService(AlarmManager::class.java)
        alarms.cancel(pending(context))
        if (model.phase != Phase.signedIn) {
            hide(manager)
            return
        }
        val now = LocalDateTime.now()
        val today = now.toLocalDate()
        val minute = Dates.minuteOfDay(now)
        // I widget si ridisegnano all'inizio e alla fine di ogni evento di oggi.
        val boundaries = model.timetable.entriesOn(today, includingDraft = false).flatMap { listOf(it.start, it.end) }.toMutableList()
        if (!(Notifier.canNotify(context) && post(context, model, now, boundaries))) hide(manager)
        val next = boundaries.filter { it > minute }.minOrNull()
        if (next != null && next < 1440) schedule(alarms, context, today.atStartOfDay().plusMinutes(next.toLong()))
        // Si ricontrolla domattina presto (l'orario di domani può cambiare nel frattempo).
        else schedule(alarms, context, today.plusDays(1).atTime(6, 0))
    }

    private fun hide(manager: NotificationManagerCompat) {
        manager.cancel(TAG, 0)
        shownDay = null
    }

    /** Mostra la notifica se oggi è il momento; aggiunge a `boundaries` i suoi passaggi di fase. */
    private fun post(context: Context, model: AppModel, now: LocalDateTime, boundaries: MutableList<Int>): Boolean {
        val today = now.toLocalDate()
        val key = Dates.dayKey(today)
        val saved = state(context)
        if (saved.getString("stopped", null) == key) return false
        val forced = saved.getString("forced", null) == key
        val minute = Dates.minuteOfDay(now)
        val phases = LivePlan.phases(model, today, model.preferences.liveUpdates || forced)
        if (phases.none { !it.isBreak }) return false
        val dayStart = phases.first().start
        val dayEnd = phases.filter { !it.isBreak }.maxOf { it.end }
        val windowStart = if (forced) 0 else dayStart - 30
        val windowEnd = dayEnd + 10
        if (minute < windowStart) {
            boundaries += windowStart
            return false
        }
        if (minute >= windowEnd) return false
        val current = phases.lastOrNull { it.start <= minute && minute < it.end && it.isBreak } ?: phases.firstOrNull { it.start <= minute && minute < it.end }
        val next = phases.firstOrNull { it.start > minute && !it.isBreak }
        val dark = (context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
        val colors = bcwColors(context, ThemeSettings.from(model.preferences), dark)
        // Una sola idea per notifica: cosa c'è adesso, quando finisce, e il progresso della giornata.
        val title: String
        val text: String
        val endMinute: Int
        when {
            current != null && !current.isBreak -> {
                title = current.title
                text = listOfNotNull(current.room.ifEmpty { null }, "fino alle ${TimeOfDay.string(current.end)}").joinToString(" · ")
                endMinute = current.end
            }
            current != null -> {
                title = "Pausa"
                text = "fino alle ${TimeOfDay.string(current.end)}"
                endMinute = current.end
            }
            next != null -> {
                title = "Prossima: ${next.title}"
                text = "alle ${TimeOfDay.string(next.start)}${if (next.room.isNotEmpty()) " · ${next.room}" else ""}"
                endMinute = next.start
            }
            else -> {
                title = "Finito per oggi"
                text = ""
                endMinute = windowEnd
            }
        }
        // Barra a due tinte: la parte già fatta nel colore d'accento, il resto neutro.
        val total = dayEnd - dayStart
        val done = (minute - dayStart).coerceIn(0, total)
        val style = NotificationCompat.ProgressStyle().setProgress(done)
        if (done > 0) style.addProgressSegment(NotificationCompat.ProgressStyle.Segment(done).setColor(colors.scheme.primary.toArgbCompat()))
        if (total - done > 0) style.addProgressSegment(NotificationCompat.ProgressStyle.Segment(total - done).setColor(colors.scheme.outlineVariant.toArgbCompat()))
        val remaining = maxOf(0, endMinute - minute)
        val builder = NotificationCompat.Builder(context, Notifier.LIVE)
            .setSmallIcon(R.drawable.ic_notification)
            .setSubText("BCW · Orario")
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(style)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setShortCriticalText(if (remaining >= 60) "${remaining / 60}h${String.format(java.util.Locale.ROOT, "%02d", remaining % 60)}" else "${remaining}min")
            .setRequestPromotedOngoing(true)
            .setContentIntent(Notifier.openApp(context, "bcw://orario?giorno=${Dates.dayKey(today)}", 77))
            .setDeleteIntent(dismissed(context))
            .setColor(colors.scheme.primary.toArgbCompat())
        try {
            NotificationManagerCompat.from(context).notify(TAG, 0, builder.build())
            shownDay = today
        } catch (_: SecurityException) {
            return false
        }
        boundaries += phases.flatMap { listOf(it.start, it.end) } + windowEnd
        return true
    }

    private fun schedule(alarms: AlarmManager, context: Context, at: LocalDateTime) {
        alarms.setWindow(AlarmManager.RTC_WAKEUP, Dates.epochMillis(at), 60_000L, pending(context))
    }

    fun cancel(context: Context) {
        NotificationManagerCompat.from(context).cancel(TAG, 0)
        shownDay = null
        context.getSystemService(AlarmManager::class.java).cancel(pending(context))
    }
}

private fun androidx.compose.ui.graphics.Color.toArgbCompat(): Int = android.graphics.Color.argb(
    (alpha * 255).toInt(), (red * 255).toInt(), (green * 255).toInt(), (blue * 255).toInt(),
)

class LiveUpdateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == "com.bcw.classeviva.LIVE_DISMISSED") {
            LiveUpdates.onDismissed(context)
            return
        }
        LiveUpdates.refresh(context)
        val result = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                Widgets.updateAll(context)
            } finally {
                result.finish()
            }
        }
    }
}

/** Dopo il riavvio del telefono o un cambio d'ora gli allarmi vanno rifatti. */
class BootReceiver : BroadcastReceiver() {
    private val actions = setOf(Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED, Intent.ACTION_MY_PACKAGE_REPLACED)

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in actions) return
        val app = context.applicationContext as? BCWApplication ?: return
        app.model.rescheduleReminders()
        LiveUpdates.refresh(context)
    }
}
