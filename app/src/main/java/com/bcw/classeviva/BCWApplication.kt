package com.bcw.classeviva

import android.app.Application
import android.content.res.Configuration
import com.bcw.classeviva.data.AppModel
import com.bcw.classeviva.data.PlatformHooks
import com.bcw.classeviva.data.ReminderPlanItem
import com.bcw.classeviva.platform.BackgroundRefresh
import com.bcw.classeviva.platform.LiveUpdates
import com.bcw.classeviva.platform.Notifier
import com.bcw.classeviva.platform.ReminderScheduler
import com.bcw.classeviva.platform.WearSync
import com.bcw.classeviva.ui.theme.ThemeSettings
import com.bcw.classeviva.ui.theme.generatedTheme
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
        // I colori del tema (ColorEngine, oltre 100 ms in debug) si generano in background mentre
        // si crea l'activity: il primo disegno li trova pronti invece di aspettarli.
        val theme = ThemeSettings.from(model.preferences)
        if (!theme.dynamicColor) Thread({ generatedTheme(theme.accent, theme.style, theme.contrast) }, "bcw-tema").start()
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
        // Qualche secondo dopo l'avvio, in background: l'apertura dell'app viene prima.
        WearSync.timetableChanged(this, model.timetable, model.preferences, delayMillis = 3_000)
        lastNight = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
    }

    private var lastNight = Configuration.UI_MODE_NIGHT_UNDEFINED

    /** Il telefono passa al tema chiaro o scuro: con "Automatico" l'orologio lo segue. */
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val night = newConfig.uiMode and Configuration.UI_MODE_NIGHT_MASK
        if (night == lastNight) return
        lastNight = night
        if (::model.isInitialized) WearSync.timetableChanged(this, model.timetable, model.preferences)
    }
}
