package com.bcw.classeviva.platform

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.bcw.classeviva.MainActivity
import com.bcw.classeviva.R
import com.bcw.classeviva.data.ReminderPlanItem
import com.bcw.classeviva.util.Dates

/** Notifiche: novità, promemoria e lezione in corso, ognuna nel suo canale. */
object Notifier {
    const val NEWS = "news"
    const val REMINDERS = "reminders"
    const val LIVE = "live"

    fun createChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(NEWS, "Novità", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "Nuovi voti, comunicazioni, note, assenze, compiti e verifiche"
        })
        manager.createNotificationChannel(NotificationChannel(REMINDERS, "Promemoria", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "La sera prima di ogni compito o verifica"
        })
        manager.createNotificationChannel(NotificationChannel(LIVE, "Lezione in corso", NotificationManager.IMPORTANCE_LOW).apply {
            description = "La lezione in corso e la prossima, durante la giornata"
            setShowBadge(false)
        })
    }

    fun canNotify(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun openApp(context: Context, link: String? = null, request: Int = 0): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        if (link != null) intent.data = Uri.parse(link)
        return PendingIntent.getActivity(context, request, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    fun show(context: Context, title: String, body: String, tag: String, channel: String, link: String? = null) {
        if (!canNotify(context)) return
        val notification = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body.lines().firstOrNull() ?: body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setContentIntent(openApp(context, link, tag.hashCode()))
            .build()
        try {
            NotificationManagerCompat.from(context).notify(tag, 0, notification)
        } catch (_: SecurityException) {
        }
    }
}

/** Promemoria programmati con AlarmManager (non servono permessi speciali: finestra di 10 minuti). */
object ReminderScheduler {
    private const val PREFS = "bcw.reminders"
    private const val KEY = "scheduled"

    private fun intent(context: Context, item: ReminderPlanItem? = null, id: String) =
        Intent(context, ReminderReceiver::class.java).setAction("com.bcw.classeviva.REMINDER").setData(Uri.parse("bcw-reminder://$id")).apply {
            if (item != null) {
                putExtra("title", item.title)
                putExtra("body", item.body)
                putExtra("id", item.id)
            }
        }

    fun reschedule(context: Context, plan: List<ReminderPlanItem>) {
        val alarms = context.getSystemService(AlarmManager::class.java)
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        for (id in prefs.getStringSet(KEY, emptySet()) ?: emptySet()) {
            val pending = PendingIntent.getBroadcast(context, id.hashCode(), intent(context, null, id), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE)
            if (pending != null) alarms.cancel(pending)
        }
        val scheduled = mutableSetOf<String>()
        for (item in plan) {
            val pending = PendingIntent.getBroadcast(context, item.id.hashCode(), intent(context, item, item.id), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            alarms.setWindow(AlarmManager.RTC_WAKEUP, Dates.epochMillis(item.fireAt), 10 * 60_000L, pending)
            scheduled += item.id
        }
        prefs.edit().putStringSet(KEY, scheduled).apply()
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val title = intent.getStringExtra("title") ?: return
        val body = intent.getStringExtra("body") ?: ""
        val id = intent.getStringExtra("id") ?: "bcw.agenda"
        val day = id.removePrefix("bcw.agenda.")
        Notifier.show(context, title, body, id, Notifier.REMINDERS, "bcw://dashboard?giorno=$day")
    }
}
