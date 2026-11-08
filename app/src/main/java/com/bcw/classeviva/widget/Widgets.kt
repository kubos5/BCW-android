package com.bcw.classeviva.widget

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.net.Uri
import androidx.compose.ui.graphics.toArgb
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.LinearProgressIndicator
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
import com.bcw.classeviva.R
import com.bcw.classeviva.data.AgendaKind
import com.bcw.classeviva.data.AppModel
import com.bcw.classeviva.data.Grade
import com.bcw.classeviva.data.Phase
import com.bcw.classeviva.data.WidgetBrand
import com.bcw.classeviva.data.timetable.EntryTint
import com.bcw.classeviva.platform.LivePlan
import com.bcw.classeviva.data.timetable.TimeOfDay
import com.bcw.classeviva.data.timetable.Weekday
import com.bcw.classeviva.data.timetable.defaultTimetableDay
import com.bcw.classeviva.data.timetable.timetableLayout
import com.bcw.classeviva.ui.theme.BCWColors
import com.bcw.classeviva.ui.theme.ThemeSettings
import com.bcw.classeviva.ui.theme.ToneColors
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
        if (manager.getGlanceIds(NowWidget::class.java).isNotEmpty()) NowWidget().updateAll(context)
        if (manager.getGlanceIds(NextTestWidget::class.java).isNotEmpty()) NextTestWidget().updateAll(context)
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
    val brand = Widgets.model(context).preferences.widgetBrand
    val (light, dark) = Widgets.colors(context)
    // Stesso contenitore delle card della app (surfaceContainer), dalla palette di sistema se attiva.
    val frame = Widgets.dual(light.scheme.surfaceContainer, dark.scheme.surfaceContainer)
    GlanceTheme(colors = Widgets.providers(context)) {
        // Contenitore esterno a 32dp, con 16dp di padding: gli elementi interni hanno 16dp (32 − 16).
        Box(
            GlanceModifier.fillMaxSize().background(frame).cornerRadius(WidgetShape.frame).padding(WidgetShape.padding).clickable(Widgets.open(context, link)),
        ) {
            Column(GlanceModifier.fillMaxSize()) {
                if (brand != WidgetBrand.hidden) BrandMark(brand)
                Box(GlanceModifier.fillMaxWidth().defaultWeight()) { content() }
            }
        }
    }
}

/** Forme dei widget, con la scala di raggi della app (scala "standard"): esterno 32, interno 16, piccolo 8. */
object WidgetShape {
    val frame = 32.dp
    val padding = 16.dp
    val item = 16.dp
    val chip = 8.dp
}

/** Marchio BCW in alto a destra: l'icona dell'app e, se scelto, il nome. */
@Composable
private fun BrandMark(brand: WidgetBrand) {
    Row(GlanceModifier.fillMaxWidth().padding(bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Spacer(GlanceModifier.defaultWeight())
        Image(
            provider = ImageProvider(R.drawable.ic_notification), contentDescription = "BCW",
            colorFilter = ColorFilter.tint(GlanceTheme.colors.primary), modifier = GlanceModifier.size(14.dp),
        )
        if (brand == WidgetBrand.full) {
            Spacer(GlanceModifier.width(4.dp))
            Text("BCW", style = TextStyle(color = GlanceTheme.colors.primary, fontSize = 11.sp, fontWeight = FontWeight.Bold))
        }
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
            Text(title, style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 24.sp, fontWeight = FontWeight.Bold))
            Spacer(GlanceModifier.width(8.dp))
            Text(Fmt.shortDay(day), style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 14.sp, fontWeight = FontWeight.Medium))
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
                        GlanceModifier.fillMaxWidth().background(if (current) GlanceTheme.colors.primaryContainer else Widgets.dual(light.scheme.surfaceContainerHigh, dark.scheme.surfaceContainerHigh))
                            .cornerRadius(WidgetShape.item).padding(horizontal = 12.dp, vertical = 10.dp),
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
        val (light, dark) = Widgets.colors(context)
        provideContent { WidgetFrame(context, "bcw://dashboard") { AgendaContent(model, light, dark) } }
    }
}

@Composable
private fun AgendaContent(model: AppModel, light: BCWColors, dark: BCWColors) {
    if (model.phase != Phase.signedIn) return Empty("Accedi a BCW")
    val today = Dates.today()
    val events = model.agenda.filter { !it.day.isBefore(today) && it.day.isBefore(today.plusDays(14)) && !(it.kind == AgendaKind.homework && model.preferences.isCompleted(it)) }
        .sortedWith(compareBy({ it.day }, { if (it.kind == AgendaKind.test) 0 else 1 }))
    Column(GlanceModifier.fillMaxSize()) {
        Text("Compiti e verifiche", style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 22.sp, fontWeight = FontWeight.Bold))
        Spacer(GlanceModifier.height(8.dp))
        if (events.isEmpty()) return@Column Empty("Niente in programma")
        LazyColumn {
            items(events.take(30), itemId = { it.id }) { e ->
                val test = e.kind == AgendaKind.test
                Column(GlanceModifier.fillMaxWidth().padding(bottom = 2.dp)) {
                    Column(
                        GlanceModifier.fillMaxWidth().background(if (test) GlanceTheme.colors.tertiaryContainer else Widgets.dual(light.scheme.surfaceContainerHigh, dark.scheme.surfaceContainerHigh)).cornerRadius(WidgetShape.item).padding(horizontal = 12.dp, vertical = 10.dp),
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
    // Exact: il contenuto si ridisegna a ogni ridimensionamento (con Single non cambiava mai).
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val model = Widgets.model(context)
        val (light, dark) = Widgets.colors(context)
        val night = (context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
        provideContent { WidgetFrame(context, "bcw://voti") { AverageContent(model, light, dark, night) } }
    }
}

@Composable
private fun AverageContent(model: AppModel, light: BCWColors, dark: BCWColors, night: Boolean) {
    if (model.phase != Phase.signedIn) return Empty("Accedi a BCW")
    val average = model.gradeBook.average()
    fun tone(c: BCWColors) = when {
        average == null -> c.neutral
        average >= 6 -> c.good
        average >= 5.5 -> c.fair
        else -> c.poor
    }
    val size = LocalSize.current
    if (size.height >= 180.dp) {
        ExpandedAverage(model, average, if (night) dark else light, size)
        return
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

/**
 * Media ingrandita: la media in alto e gli ultimi voti in griglia. Colonne: 1, o 2 se il widget è
 * largo. Righe: quante ne entrano nell'altezza, fra 2 e 10.
 */
@Composable
private fun ExpandedAverage(model: AppModel, average: Double?, c: BCWColors, size: androidx.compose.ui.unit.DpSize) {
    val cols = if (size.width >= 240.dp) 2 else 1
    // Altezza tolta: margini del contenitore, marchio, l'anello con la scritta e i margini tra le righe.
    val listHeight = size.height - WidgetShape.padding * 2 - 22.dp - 120.dp - 20.dp - 16.dp
    // Ogni riga: cella di 56dp più 12dp di spazio.
    val rows = (listHeight.value / 68f).toInt().coerceIn(2, 10)
    val recent = model.gradeBook.grades.sortedByDescending { it.date }.take(rows * cols)
    val ring = toneOf(average, c)
    Column(GlanceModifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        // Come nella schermata Voti: anello ondulato con l'avanzamento e il numero al centro.
        Box(GlanceModifier.size(120.dp), contentAlignment = Alignment.Center) {
            Image(
                provider = ImageProvider(WidgetArt.ring(((average ?: 0.0) / 10.0).toFloat().coerceIn(0f, 1f), ring.container.toArgb(), ring.color.toArgb(), 180)),
                contentDescription = null, modifier = GlanceModifier.size(120.dp),
            )
            Text(GradeFormat.average(average), style = TextStyle(color = c.scheme.onSurface.toGlance(), fontSize = 30.sp, fontWeight = FontWeight.Bold))
        }
        Text("Media generale", style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 14.sp, fontWeight = FontWeight.Medium))
        Spacer(GlanceModifier.height(8.dp))
        if (recent.isEmpty()) return@Column Empty("Nessun voto")
        for (row in recent.chunked(cols)) {
            Row(GlanceModifier.fillMaxWidth()) {
                for (grade in row) {
                    GradeCell(grade, c, GlanceModifier.defaultWeight())
                    Spacer(GlanceModifier.width(8.dp))
                }
            }
            Spacer(GlanceModifier.height(12.dp))
        }
    }
}

/** Immagini dei voti, disegnate come nella schermata Voti: anello ondulato e biscotti a nove lobi. */
private object WidgetArt {
    private const val RING_WAVES = 11
    private const val LOBES = 9

    /** Anello con avanzamento (`progress` 0..1), come `AverageRing` della app. */
    fun ring(progress: Float, track: Int, color: Int, px: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val stroke = px * 0.09f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = stroke; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
        }
        val center = px / 2f
        val base = center - stroke - px * 0.02f
        val amp = px * 0.03f
        paint.color = track
        canvas.drawPath(wavy(center, base, amp, RING_WAVES, 0.0, 2 * PI, 240), paint)
        if (progress > 0f) {
            paint.color = color
            canvas.drawPath(wavy(center, base, amp, RING_WAVES, 0.0, 2 * PI * progress, (240 * progress).toInt().coerceAtLeast(8)), paint)
        }
        return bitmap
    }

    /** Biscotto a nove lobi per i voti che fanno media, cerchio per gli altri. */
    fun badge(fill: Int, px: Int, lobed: Boolean): Bitmap {
        val bitmap = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = fill }
        val center = px / 2f
        val base = px / 2f * 0.9f
        val amp = if (lobed) base * 0.07f else 0f
        canvas.drawPath(wavy(center, base, amp, if (lobed) LOBES else 1, 0.0, 2 * PI, 240, lobed), paint)
        return bitmap
    }

    private fun wavy(cx: Float, base: Float, amp: Float, waves: Int, from: Double, to: Double, steps: Int, closed: Boolean = false): Path {
        val path = Path()
        for (i in 0..steps) {
            val t = from + (to - from) * i / steps
            val r = base + amp * sin(waves * t).toFloat()
            val x = cx + r * cos(t - PI / 2).toFloat()
            val y = cx + r * sin(t - PI / 2).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        if (closed) path.close()
        return path
    }
}

private fun Color.toGlance(): GlanceColor = Widgets.dual(this, this)

private fun toneOf(average: Double?, c: BCWColors) = when {
    average == null -> c.neutral
    average >= 6 -> c.good
    average >= 5.5 -> c.fair
    else -> c.poor
}

/** Stesso criterio della app: bene, quasi, insufficiente; grigio per annullati e voti che non fanno media. */
private fun gradeToneOf(g: Grade, c: BCWColors): ToneColors =
    if (g.canceled || !g.countsTowardAverage) c.neutral else toneOf(g.value, c)

/** Un voto come nella schermata Voti: biscotto nel colore del voto se fa media, cerchio grigio se no. */
@Composable
private fun GradeCell(grade: Grade, c: BCWColors, modifier: GlanceModifier) {
    val counts = grade.countsTowardAverage && !grade.canceled
    val tone = gradeToneOf(grade, c)
    val fill = if (counts) tone.container else c.scheme.surfaceContainerHigh
    val textColor = if (counts) tone.onContainer else c.scheme.onSurfaceVariant
    Row(modifier.height(56.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(GlanceModifier.size(44.dp), contentAlignment = Alignment.Center) {
            Image(
                provider = ImageProvider(WidgetArt.badge(fill.toArgb(), 66, counts)), contentDescription = null,
                modifier = GlanceModifier.size(44.dp),
            )
            Text(grade.displayValue, maxLines = 1, style = TextStyle(color = textColor.toGlance(), fontSize = 15.sp, fontWeight = FontWeight.Bold))
        }
        Spacer(GlanceModifier.width(10.dp))
        Column(GlanceModifier.defaultWeight()) {
            Text(grade.subjectName, maxLines = 1, style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 14.sp, fontWeight = FontWeight.Bold))
            Text(Fmt.shortDay(grade.date), maxLines = 1, style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 11.sp))
        }
    }
}

class AverageWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = AverageWidget()
}

// MARK: - Lezione in corso

class NowWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val model = Widgets.model(context)
        val (light, dark) = Widgets.colors(context)
        provideContent { WidgetFrame(context, "bcw://orario") { NowContent(model, light, dark) } }
    }
}

/**
 * La lezione in corso (materia, aula, quanto manca con la barra e la lezione dopo); senza lezioni
 * in corso la prossima, anche di un altro giorno.
 */
@Composable
private fun NowContent(model: AppModel, light: BCWColors, dark: BCWColors) {
    if (model.phase != Phase.signedIn) return Empty("Accedi a BCW")
    val store = model.timetable
    if (store.isEmpty) return Empty("Crea l'orario nell'app")
    val today = Dates.today()
    val minute = Dates.nowMinute()
    val todayLessons = LivePlan.phases(model, today, true).filter { !it.isBreak }
    val current = todayLessons.firstOrNull { it.start <= minute && minute < it.end }
    val nextToday = todayLessons.firstOrNull { it.start > minute }
    val laterDay = if (current == null && nextToday == null) defaultTimetableDay({ store.entries(it, false) }) else null
    val shown = current ?: nextToday ?: laterDay?.let { d -> LivePlan.phases(model, d, true).firstOrNull { !it.isBreak } } ?: return Empty("Nessuna lezione")
    fun tint(c: BCWColors) = when (val t = shown.tint) {
        is EntryTint.Subject -> c.subject(t.index).color
        EntryTint.Break -> c.breakTint
        is EntryTint.Hex -> Color(0xFF000000.toInt() or t.hex)
    }
    val label = when {
        current != null -> "ADESSO"
        laterDay == null -> "TRA ${TimeOfDay.duration(shown.start - minute).uppercase(Dates.italian)}"
        Dates.isTomorrow(laterDay) -> "DOMANI ALLE ${TimeOfDay.string(shown.start)}"
        else -> "${Weekday.name(Weekday.of(laterDay)).uppercase(Dates.italian)} ALLE ${TimeOfDay.string(shown.start)}"
    }
    val following = if (current != null) todayLessons.firstOrNull { it.start >= current.end } else null
    Column(GlanceModifier.fillMaxSize()) {
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(GlanceModifier.width(4.dp).height(14.dp).background(Widgets.dual(tint(light), tint(dark))).cornerRadius(2.dp)) {}
            Spacer(GlanceModifier.width(6.dp))
            Text(label, maxLines = 1, style = TextStyle(color = GlanceTheme.colors.primary, fontSize = 11.sp, fontWeight = FontWeight.Bold))
        }
        Spacer(GlanceModifier.height(6.dp))
        Text(shown.title, maxLines = 2, style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 22.sp, fontWeight = FontWeight.Bold))
        if (shown.room.isNotEmpty()) Text(shown.room, maxLines = 1, style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 13.sp))
        Spacer(GlanceModifier.defaultWeight())
        if (current != null) {
            Text("Fino alle ${TimeOfDay.string(current.end)} · ${TimeOfDay.duration(current.end - minute)}", maxLines = 1,
                style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp))
            Spacer(GlanceModifier.height(4.dp))
            LinearProgressIndicator(
                progress = (minute - current.start).toFloat() / (current.end - current.start),
                modifier = GlanceModifier.fillMaxWidth().height(6.dp),
                color = GlanceTheme.colors.primary, backgroundColor = GlanceTheme.colors.secondaryContainer,
            )
            following?.let {
                Spacer(GlanceModifier.height(6.dp))
                Text("Poi ${it.title} alle ${TimeOfDay.string(it.start)}", maxLines = 1, style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp))
            }
        }
    }
}

class NowWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = NowWidget()
}

// MARK: - Prossima verifica

class NextTestWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val model = Widgets.model(context)
        val next = if (model.phase == Phase.signedIn) model.agenda.filter { it.kind == AgendaKind.test && !it.day.isBefore(Dates.today()) }.minByOrNull { it.begin } else null
        val link = next?.let { "bcw://dashboard?giorno=${Dates.dayKey(it.day)}" } ?: "bcw://dashboard"
        provideContent { WidgetFrame(context, link) { NextTestContent(model, next) } }
    }
}

/** La prossima verifica con i giorni che mancano. */
@Composable
private fun NextTestContent(model: AppModel, test: com.bcw.classeviva.data.AgendaEvent?) {
    if (model.phase != Phase.signedIn) return Empty("Accedi a BCW")
    if (test == null) return Empty("Nessuna verifica in vista")
    val days = Dates.daysBetween(Dates.today(), test.day)
    val whenText = when (days) {
        0L -> "Oggi"
        1L -> "Domani"
        else -> "Tra $days giorni"
    }
    Column(GlanceModifier.fillMaxSize()) {
        Text("PROSSIMA VERIFICA", maxLines = 1, style = TextStyle(color = GlanceTheme.colors.tertiary, fontSize = 11.sp, fontWeight = FontWeight.Bold))
        Spacer(GlanceModifier.height(6.dp))
        Text(test.title, maxLines = 2, style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 22.sp, fontWeight = FontWeight.Bold))
        if (test.notes.isNotEmpty()) Text(test.notes, maxLines = 2, style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp))
        Spacer(GlanceModifier.defaultWeight())
        Box(GlanceModifier.background(GlanceTheme.colors.tertiaryContainer).cornerRadius(WidgetShape.item).padding(horizontal = 12.dp, vertical = 6.dp)) {
            Text("$whenText · ${Fmt.shortDay(test.day)}", maxLines = 1, style = TextStyle(color = GlanceTheme.colors.onTertiaryContainer, fontSize = 13.sp, fontWeight = FontWeight.Bold))
        }
    }
}

class NextTestWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = NextTestWidget()
}
