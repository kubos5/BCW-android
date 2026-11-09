package com.bcw.classeviva

import android.app.Application
import com.bcw.classeviva.data.AppModel
import com.bcw.classeviva.data.PlatformHooks
import com.bcw.classeviva.data.ReminderPlanItem
import com.bcw.classeviva.platform.BackgroundRefresh
import com.bcw.classeviva.platform.LiveUpdates
import com.bcw.classeviva.platform.Notifier
import com.bcw.classeviva.platform.ReminderScheduler
import com.bcw.classeviva.platform.WearSync
import com.bcw.classeviva.widget.Widgets
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** L'applicazione: un solo `AppModel` per processo, con i ganci verso Android. */
class BCWApplication : Application() {
    lateinit var model: AppModel
        private set

    private var dataJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        Notifier.createChannels(this)
        model = AppModel(this)
        model.hooks = object : PlatformHooks {
            override fun notify(title: String, body: String, tag: String, channel: String) {
                val link = when {
                    tag.startsWith("bcw.news.grades") -> "bcw://voti"
                    tag.startsWith("bcw.news.agenda") -> "bcw://dashboard"
                    else -> "bcw://tu"
                }
                Notifier.show(this@BCWApplication, title, body, tag, channel, link)
            }

            override fun rescheduleReminders(plan: List<ReminderPlanItem>) = ReminderScheduler.reschedule(this@BCWApplication, plan)

            override fun dataChanged() {
                // I cambi ravvicinati si uniscono (come `WidgetExporter` dell'app iOS, 700 ms).
                dataJob?.cancel()
                dataJob = model.scope.launch {
                    delay(700)
                    Widgets.updateAll(this@BCWApplication)
                    LiveUpdates.refresh(this@BCWApplication)
                    BackgroundRefresh.schedule(this@BCWApplication, model)
                    WearSync.timetableChanged(this@BCWApplication, model.timetable, model.preferences)
                }
            }
        }
        // All'avvio in background (widget, notifiche) solo la cache: l'aggiornamento parte aprendo l'app.
        model.bootstrap(refresh = false)
        // Anche se l'orologio non ha chiesto nulla, l'ultimo orario gli resta a disposizione.
        WearSync.pushNow(this, model.timetable, model.preferences)
    }
}
