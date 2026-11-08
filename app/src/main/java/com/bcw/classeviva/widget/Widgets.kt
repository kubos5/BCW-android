package com.bcw.classeviva.widget

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.material3.ColorProviders
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider as GlanceColor
import com.bcw.classeviva.BCWApplication
import com.bcw.classeviva.MainActivity
import com.bcw.classeviva.data.AgendaKind
import com.bcw.classeviva.data.AppModel
import com.bcw.classeviva.data.Phase
import com.bcw.classeviva.data.timetable.EntryTint
import com.bcw.classeviva.data.timetable.TimeOfDay
import com.bcw.classeviva.data.timetable.Weekday
import com.bcw.classeviva.data.timetable.defaultTimetableDay
import com.bcw.classeviva.data.timetable.timetableLayout
import com.bcw.classeviva.ui.theme.BCWColors
import com.bcw.classeviva.ui.theme.ThemeSettings
import com.bcw.classeviva.ui.theme.bcwColors
import com.bcw.classeviva.util.Dates
import com.bcw.classeviva.util.Fmt
import com.bcw.classeviva.util.GradeFormat

/** Aggiorna tutti i widget quando cambiano i dati (orario, agenda, voti, tema). */
object Widgets {
    suspend fun updateAll(context: Context) {
        val manager = GlanceAppWidgetManager(context)
        if (manager.getGlanceIds(TimetableWidget::class.java).isNotEmpty()) TimetableWidget().updateAll(context)
        if (manager.getGlanceIds(AgendaWidget::class.java).isNotEmpty()) AgendaWidget().updateAll(context)
        if (manager.getGlanceIds(AverageWidget::class.java).isNotEmpty()) AverageWidget().updateAll(context)
    }

    fun model(context: Context): AppModel = (context.applicationContext as BCWApplication).model

    fun open(context: Context, link: String) = actionStartActivity(
        Intent(context, MainActivity::class.java).setData(Uri.parse(link)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
    )

    fun colors(context: Context): Pair<BCWColors, BCWColors> {
        val settings = ThemeSettings.from(model(context).preferences)
        return bcwColors(context, settings, false) to bcwColors(context, settings, true)
    }

    fun providers(context: Context): androidx.glance.color.ColorProviders {
        val (light, dark) = colors(context)
        return ColorProviders(light = light.scheme, dark = dark.scheme)
    }

    /** Colore che segue il tema chiaro o scuro del telefono. */
    fun dual(light: Color, dark: Color): GlanceColor = ColorProvider(day = light, night = dark)
}

@Composable
private fun WidgetFrame(context: Context, link: String, content: @Composable () -> Unit) {
    GlanceTheme(colors = Widgets.providers(context)) {
        Box(
            GlanceModifier.fillMaxSize().background(GlanceTheme.colors.surface).cornerRadius(24.dp).padding(14.dp).clickable(Widgets.open(context, link)),
        ) { content() }
    }
}

@Composable
private fun Empty(text: String) {
    Box(GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 14.sp))
    }
}

// MARK: - Orario

class TimetableWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val model = Widgets.model(context)
        val (light, dark) = Widgets.colors(context)
        provideContent { WidgetFrame(context, "bcw://orario") { TimetableContent(model, light, dark) } }
    }
}

@Composable
private fun TimetableContent(model: AppModel, light: BCWColors, dark: BCWColors) {
    if (model.phase != Phase.signedIn) return Empty("Accedi a BCW")
    val store = model.timetable
    val day = defaultTimetableDay({ store.entries(it, false) })
    val entries = store.entries(Weekday.of(day), false)
    val layout = timetableLayout(entries, splitAtBreaks = true)
    val minute = if (Dates.isToday(day)) Dates.nowMinute() else -1
    val title = when {
        Dates.isToday(day) -> "Oggi"
        Dates.isTomorrow(day) -> "Domani"
        else -> Weekday.name(Weekday.of(day))
    }
    Column(GlanceModifier.fillMaxSize()) {
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 20.sp, fontWeight = FontWeight.Medium))
            Spacer(GlanceModifier.width(8.dp))
            Text(Fmt.shortDay(day), style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 14.sp))
        }
        layout.span?.let {
            Text("${layout.blocks.size} lezioni · ${TimeOfDay.string(it.first)}–${TimeOfDay.string(it.last)}", style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp))
        }
        Spacer(GlanceModifier.height(8.dp))
        if (layout.blocks.isEmpty()) return@Column Empty(if (store.isEmpty) "Crea l'orario nell'app" else "Nessuna lezione")
        LazyColumn {
            items(layout.blocks.sortedBy { it.start }, itemId = { it.id.hashCode().toLong() }) { block ->
                val first = block.entries.first()
                val current = minute in block.start until block.end
                fun tint(c: BCWColors) = when (val t = store.tint(first)) {
                    is EntryTint.Subject -> c.subject(t.index).color
                    EntryTint.Break -> c.breakTint
                    is EntryTint.Hex -> Color(0xFF000000.toInt() or t.hex)
                }
                Column(GlanceModifier.fillMaxWidth().padding(bottom = 2.dp)) {
                    Row(
                        GlanceModifier.fillMaxWidth().background(if (current) GlanceTheme.colors.primaryContainer else GlanceTheme.colors.surfaceVariant)
                            .cornerRadius(10.dp).padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(TimeOfDay.string(block.start), style = TextStyle(color = if (current) GlanceTheme.colors.onPrimaryContainer else GlanceTheme.colors.onSurfaceVariant, fontSize = 13.sp, fontWeight = FontWeight.Medium))
                        Spacer(GlanceModifier.width(8.dp))
                        Box(GlanceModifier.width(4.dp).height(18.dp).background(Widgets.dual(tint(light), tint(dark))).cornerRadius(2.dp)) {}
                        Spacer(GlanceModifier.width(8.dp))
                        Text(first.displayTitle, maxLines = 1, modifier = GlanceModifier.defaultWeight(),
                            style = TextStyle(color = if (current) GlanceTheme.colors.onPrimaryContainer else GlanceTheme.colors.onSurface, fontSize = 14.sp, fontWeight = if (current) FontWeight.Bold else FontWeight.Medium))
                        if (current) Text("In corso", style = TextStyle(color = GlanceTheme.colors.onPrimaryContainer, fontSize = 12.sp, fontWeight = FontWeight.Bold))
                        else if (first.room.isNotEmpty()) Text(first.room, maxLines = 1, style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp))
                    }
                }
            }
        }
    }
}

class TimetableWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TimetableWidget()
}

// MARK: - Compiti e verifiche

class AgendaWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val model = Widgets.model(context)
        provideContent { WidgetFrame(context, "bcw://dashboard") { AgendaContent(model) } }
    }
}

@Composable
private fun AgendaContent(model: AppModel) {
    if (model.phase != Phase.signedIn) return Empty("Accedi a BCW")
    val today = Dates.today()
    val events = model.agenda.filter { !it.day.isBefore(today) && it.day.isBefore(today.plusDays(14)) && !(it.kind == AgendaKind.homework && model.preferences.isCompleted(it)) }
        .sortedWith(compareBy({ it.day }, { if (it.kind == AgendaKind.test) 0 else 1 }))
    Column(GlanceModifier.fillMaxSize()) {
        Text("Compiti e verifiche", style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 18.sp, fontWeight = FontWeight.Medium))
        Spacer(GlanceModifier.height(8.dp))
        if (events.isEmpty()) return@Column Empty("Niente in programma")
        LazyColumn {
            items(events.take(30), itemId = { it.id }) { e ->
                val test = e.kind == AgendaKind.test
                Column(GlanceModifier.fillMaxWidth().padding(bottom = 2.dp)) {
                    Column(
                        GlanceModifier.fillMaxWidth().background(if (test) GlanceTheme.colors.tertiaryContainer else GlanceTheme.colors.surfaceVariant).cornerRadius(10.dp).padding(horizontal = 10.dp, vertical = 8.dp),
                    ) {
                        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(e.title, maxLines = 1, modifier = GlanceModifier.defaultWeight(),
                                style = TextStyle(color = if (test) GlanceTheme.colors.onTertiaryContainer else GlanceTheme.colors.onSurface, fontSize = 14.sp, fontWeight = FontWeight.Bold))
                            Text(Fmt.relativeShortDayName(e.day), style = TextStyle(color = if (test) GlanceTheme.colors.onTertiaryContainer else GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp))
                        }
                        Text((if (test) "Verifica · " else "") + e.notes.ifEmpty { "Nessuna descrizione" }, maxLines = 2,
                            style = TextStyle(color = if (test) GlanceTheme.colors.onTertiaryContainer else GlanceTheme.colors.onSurfaceVariant, fontSize = 13.sp))
                    }
                }
            }
        }
    }
}

class AgendaWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = AgendaWidget()
}

// MARK: - Media

class AverageWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val model = Widgets.model(context)
        val (light, dark) = Widgets.colors(context)
        provideContent { WidgetFrame(context, "bcw://voti") { AverageContent(model, light, dark) } }
    }
}

@Composable
private fun AverageContent(model: AppModel, light: BCWColors, dark: BCWColors) {
    if (model.phase != Phase.signedIn) return Empty("Accedi a BCW")
    val average = model.gradeBook.average()
    fun tone(c: BCWColors) = when {
        average == null -> c.neutral
        average >= 6 -> c.good
        average >= 5.5 -> c.fair
        else -> c.poor
    }
    Column(GlanceModifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalAlignment = Alignment.CenterVertically) {
        Box(
            GlanceModifier.size(84.dp).background(Widgets.dual(tone(light).container, tone(dark).container)).cornerRadius(42.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(GradeFormat.average(average), style = TextStyle(color = Widgets.dual(tone(light).onContainer, tone(dark).onContainer), fontSize = 26.sp, fontWeight = FontWeight.Bold))
        }
        Spacer(GlanceModifier.height(8.dp))
        Text("Media generale", style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 13.sp, fontWeight = FontWeight.Medium))
    }
}

class AverageWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = AverageWidget()
}
