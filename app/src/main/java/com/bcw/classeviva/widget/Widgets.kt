package com.bcw.classeviva.widget

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.RowScope
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
import com.bcw.classeviva.data.AgendaEvent
import com.bcw.classeviva.data.AgendaKind
import com.bcw.classeviva.data.AppModel
import com.bcw.classeviva.data.Grade
import com.bcw.classeviva.data.Phase
import com.bcw.classeviva.data.WidgetBrand
import com.bcw.classeviva.data.timetable.EntryTint
import com.bcw.classeviva.data.timetable.TimeOfDay
import com.bcw.classeviva.data.timetable.Weekday
import com.bcw.classeviva.data.timetable.defaultTimetableDay
import com.bcw.classeviva.data.timetable.timetableLayout
import com.bcw.classeviva.platform.LivePhase
import com.bcw.classeviva.platform.LivePlan
import com.bcw.classeviva.ui.theme.BCWColors
import com.bcw.classeviva.ui.theme.ThemeSettings
import com.bcw.classeviva.ui.theme.ToneColors
import com.bcw.classeviva.ui.theme.bcwColors
import com.bcw.classeviva.util.Dates
import com.bcw.classeviva.util.Fmt
import com.bcw.classeviva.util.GradeFormat
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

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

    fun providers(light: BCWColors, dark: BCWColors): androidx.glance.color.ColorProviders =
        ColorProviders(light = light.scheme, dark = dark.scheme)

    /** Colore che segue il tema chiaro o scuro del telefono. */
    fun dual(light: Color, dark: Color): GlanceColor = ColorProvider(day = light, night = dark)
}

/**
 * Misure dei widget, con la scala della app: contenitore con gli angoli del sistema (16-28 dp a
 * seconda del launcher) e 14 dp di margine, voci delle liste a 16 dp all'esterno del gruppo e 4 dp
 * tra una voce e l'altra (come `ListGroup`), 2 dp di spazio tra le voci.
 */
private object WidgetShape {
    val padding = 14.dp
    val header = 22.dp
    val gap = 2.dp
}

/** I due temi della app, per i colori che non sono ruoli di Material (materie, voti). */
private class Palette(val light: BCWColors, val dark: BCWColors) {
    fun of(pick: (BCWColors) -> Color): GlanceColor = Widgets.dual(pick(light), pick(dark))
}

// MARK: - Struttura comune

/**
 * Il contenitore di ogni widget: sfondo `surfaceContainer` come le card della app (o il colore
 * scelto, es. quello della materia), angoli del sistema, intestazione con icona e nome a sinistra e
 * il marchio a destra, poi il contenuto.
 */
@Composable
private fun WidgetFrame(
    context: Context, link: String, palette: Palette, icon: String, title: String,
    background: GlanceColor? = null, accent: GlanceColor? = null,
    content: @Composable () -> Unit,
) {
    val brand = Widgets.model(context).preferences.widgetBrand
    GlanceTheme(colors = Widgets.providers(palette.light, palette.dark)) {
        val corners = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) GlanceModifier.cornerRadius(android.R.dimen.system_app_widget_background_radius) else GlanceModifier.cornerRadius(24.dp)
        Box(
            GlanceModifier.fillMaxSize().appWidgetBackground().background(background ?: palette.of { it.scheme.surfaceContainer })
                .then(corners).padding(WidgetShape.padding).clickable(Widgets.open(context, link)),
        ) {
            Column(GlanceModifier.fillMaxSize()) {
                Header(icon, title, accent ?: GlanceTheme.colors.primary, brand)
                Box(GlanceModifier.fillMaxWidth().defaultWeight()) { content() }
            }
        }
    }
}

/** Intestazione: icona e nome del widget a sinistra, il marchio BCW a destra (se non è nascosto). */
@Composable
private fun Header(icon: String, title: String, color: GlanceColor, chosen: WidgetBrand) {
    val width = innerWidth()
    // Nei widget stretti (2 colonne) il marchio è solo l'icona: il nome del widget resta intero.
    val brand = if (chosen == WidgetBrand.full && width < 190.dp) WidgetBrand.icon else chosen
    Row(GlanceModifier.fillMaxWidth().height(WidgetShape.header).padding(bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        IconImage(icon, 16.dp, color)
        Spacer(GlanceModifier.width(6.dp))
        val brandWidth = when (brand) { WidgetBrand.full -> 52.dp; WidgetBrand.icon -> 22.dp; else -> 0.dp }
        Box(GlanceModifier.defaultWeight()) {
            SansText(title, 13f, color, (width - 22.dp - brandWidth).coerceAtLeast(40.dp), weight = 650, rond = 100)
        }
        if (brand != WidgetBrand.hidden) {
            Image(
                provider = ImageProvider(R.drawable.ic_notification), contentDescription = "BCW",
                colorFilter = ColorFilter.tint(color), modifier = GlanceModifier.size(14.dp),
            )
            if (brand == WidgetBrand.full) {
                Spacer(GlanceModifier.width(3.dp))
                SansText("BCW", 12f, color, 40.dp, weight = 750, rond = 100)
            }
        }
    }
}

/** Larghezza utile dentro il contenitore. */
@Composable
private fun innerWidth(): Dp = (LocalSize.current.width - WidgetShape.padding * 2).coerceAtLeast(40.dp)

/** Altezza utile sotto l'intestazione. */
@Composable
private fun innerHeight(): Dp = (LocalSize.current.height - WidgetShape.padding * 2 - WidgetShape.header).coerceAtLeast(20.dp)

/** Titoli e numeri in Google Sans Flex (disegnati da `WidgetArt`), colorati con il tema. */
@Composable
private fun SansText(
    text: String, sizeSp: Float, color: GlanceColor, maxWidth: Dp, weight: Int = 600, rond: Int = 100,
    maxLines: Int = 1, center: Boolean = false, tabular: Boolean = false, modifier: GlanceModifier = GlanceModifier,
) {
    if (text.isEmpty()) return
    val drawn = WidgetArt.text(LocalContext.current, text, sizeSp, weight, rond, maxWidth.value, maxLines, center, tabular)
    Image(
        provider = ImageProvider(drawn.bitmap), contentDescription = text, colorFilter = ColorFilter.tint(color),
        contentScale = ContentScale.Fit, modifier = modifier.width(drawn.widthDp.dp).height(drawn.heightDp.dp),
    )
}

/** Un'icona Material Symbols (gli stessi tracciati della app), colorata con il tema. */
@Composable
private fun IconImage(name: String, size: Dp, color: GlanceColor, modifier: GlanceModifier = GlanceModifier) {
    Image(
        provider = ImageProvider(WidgetArt.icon(LocalContext.current, name, size.value)), contentDescription = null,
        colorFilter = ColorFilter.tint(color), modifier = modifier.size(size),
    )
}

/** Testo nel carattere di sistema (descrizioni e righe delle liste). */
@Composable
private fun BodyText(text: String, sizeSp: Int, color: GlanceColor, maxLines: Int = 1, bold: Boolean = false, modifier: GlanceModifier = GlanceModifier) {
    Text(text, maxLines = maxLines, modifier = modifier, style = TextStyle(color = color, fontSize = sizeSp.sp, fontWeight = if (bold) FontWeight.Medium else FontWeight.Normal))
}

/**
 * Voce di una lista segmentata: angoli ampi solo all'esterno del gruppo, come `ListGroup`. Con
 * `height` nullo la voce prende la sua parte dello spazio rimasto (peso), così la lista arriva in
 * fondo anche quando il launcher dichiara un'altezza più piccola di quella vera.
 */
@Composable
private fun androidx.glance.layout.ColumnScope.SegmentRow(index: Int, count: Int, color: GlanceColor, height: Dp?, content: @Composable RowScope.() -> Unit) {
    val shape = when {
        count == 1 -> R.drawable.widget_seg_single
        index == 0 -> R.drawable.widget_seg_top
        index == count - 1 -> R.drawable.widget_seg_bottom
        else -> R.drawable.widget_seg_middle
    }
    // Lo spazio dalla voce sopra (2 dp) è dentro il disegno di sfondo: la voce resta un figlio solo.
    val gap = if (index > 0) WidgetShape.gap else 0.dp
    Row(
        GlanceModifier.fillMaxWidth().then(if (height != null) GlanceModifier.height(height + gap) else GlanceModifier.defaultWeight())
            .background(ImageProvider(shape), colorFilter = ColorFilter.tint(color)).padding(start = 12.dp, end = 12.dp, top = gap),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

/**
 * Quante voci di una lista mostrare in `available` (almeno `minHeight` ciascuna, lasciando posto a
 * "e altre N" se non entrano tutte) e la loro altezza: `null` = si allungano fino in fondo (peso),
 * altrimenti `maxHeight` perché sono poche e starebbero troppo alte.
 */
private class ListFit(val count: Int, val height: Dp?)

private fun fit(total: Int, available: Dp, minHeight: Dp, maxHeight: Dp): ListFit {
    val gap = WidgetShape.gap.value
    fun fitting(space: Float) = min(total, max(0, floor((space + gap) / (minHeight.value + gap)).toInt()))
    // Al massimo 9 voci: Glance tiene al massimo 10 figli per colonna.
    var count = min(9, fitting(available.value))
    if (count < total) count = min(9, fitting(available.value - 22f))
    if (count == 0) return ListFit(0, null)
    val few = count == total && count * maxHeight.value + gap * (count - 1) <= available.value
    return ListFit(count, if (few) maxHeight else null)
}

/** "e altre N", sotto una lista che non entra tutta. */
@Composable
private fun MoreLine(hidden: Int, noun: Pair<String, String>) {
    if (hidden <= 0) return
    Spacer(GlanceModifier.height(4.dp))
    BodyText("e ${if (hidden == 1) "un'altra ${noun.first}" else "altre $hidden ${noun.second}"}", 12, GlanceTheme.colors.onSurfaceVariant, modifier = GlanceModifier.padding(start = 12.dp))
}

@Composable
private fun Empty(icon: String, text: String) {
    Column(GlanceModifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalAlignment = Alignment.CenterVertically) {
        IconImage(icon, 28.dp, GlanceTheme.colors.onSurfaceVariant)
        Spacer(GlanceModifier.height(6.dp))
        BodyText(text, 13, GlanceTheme.colors.onSurfaceVariant)
    }
}

private fun EntryTint.color(c: BCWColors): Color = when (this) {
    is EntryTint.Subject -> c.subject(index).color
    EntryTint.Break -> c.breakTint
    is EntryTint.Hex -> Color(0xFF000000.toInt() or hex)
}

private fun EntryTint.container(c: BCWColors): Color = when (this) {
    is EntryTint.Subject -> c.subject(index).container
    EntryTint.Break -> c.scheme.surfaceContainerHigh
    is EntryTint.Hex -> c.scheme.secondaryContainer
}

private fun EntryTint.onContainer(c: BCWColors): Color = when (this) {
    is EntryTint.Subject -> c.subject(index).onContainer
    EntryTint.Break -> c.scheme.onSurface
    is EntryTint.Hex -> c.scheme.onSecondaryContainer
}

// MARK: - Orario

class TimetableWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val model = Widgets.model(context)
        val (light, dark) = Widgets.colors(context)
        val palette = Palette(light, dark)
        provideContent { WidgetFrame(context, "bcw://orario", palette, "timetable", "Orario") { TimetableContent(model, palette) } }
    }
}

/** Il giorno dell'orario che si vedrebbe nella app: titolo grande, riepilogo e le lezioni. */
@Composable
private fun TimetableContent(model: AppModel, palette: Palette) {
    if (model.phase != Phase.signedIn) return Empty("timetable", "Accedi a BCW")
    val store = model.timetable
    if (store.isEmpty) return Empty("timetable", "Crea l'orario nella app")
    val day = defaultTimetableDay({ store.entries(it, false) })
    val layout = timetableLayout(store.entries(Weekday.of(day), false), splitAtBreaks = true)
    val blocks = layout.blocks.sortedBy { it.start }
    val minute = if (Dates.isToday(day)) Dates.nowMinute() else -1
    val title = when {
        Dates.isToday(day) -> "Oggi"
        Dates.isTomorrow(day) -> "Domani"
        else -> Weekday.name(Weekday.of(day))
    }
    val width = innerWidth()
    Column(GlanceModifier.fillMaxSize()) {
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            SansText(title, 24f, GlanceTheme.colors.onSurface, width * 0.6f, weight = 550, rond = 100)
            Spacer(GlanceModifier.width(8.dp))
            BodyText(Fmt.shortDay(day), 13, GlanceTheme.colors.onSurfaceVariant, modifier = GlanceModifier.padding(bottom = 3.dp))
        }
        layout.span?.let {
            BodyText("${blocks.size} ${if (blocks.size == 1) "lezione" else "lezioni"} · ${TimeOfDay.string(it.first)}–${TimeOfDay.string(it.last)}", 12, GlanceTheme.colors.onSurfaceVariant)
        }
        Spacer(GlanceModifier.height(8.dp))
        if (blocks.isEmpty()) return@Column Empty("timetable", "Nessuna lezione")
        val list = fit(blocks.size, innerHeight() - 30.dp - 16.dp - 8.dp, 38.dp, 52.dp)
        val count = list.count
        val rowHeight = list.height
        Column(GlanceModifier.fillMaxWidth().defaultWeight()) {
        blocks.take(count).forEachIndexed { index, block ->
            val first = block.entries.first()
            val tint = store.tint(first)
            val current = minute in block.start until block.end
            val on = if (current) GlanceTheme.colors.onPrimaryContainer else GlanceTheme.colors.onSurface
            val onVariant = if (current) GlanceTheme.colors.onPrimaryContainer else GlanceTheme.colors.onSurfaceVariant
            SegmentRow(index, count, if (current) GlanceTheme.colors.primaryContainer else palette.of { it.scheme.surfaceContainerHigh }, rowHeight) {
                SansText(TimeOfDay.string(block.start), 14f, onVariant, 48.dp, weight = 600, rond = 30, tabular = true)
                Spacer(GlanceModifier.width(8.dp))
                Box(GlanceModifier.width(4.dp).height(20.dp).background(palette.of { tint.color(it) }).cornerRadius(2.dp)) {}
                Spacer(GlanceModifier.width(10.dp))
                BodyText(first.displayTitle, 14, on, bold = true, modifier = GlanceModifier.defaultWeight())
                if (current) {
                    Spacer(GlanceModifier.width(6.dp))
                    BodyText("In corso", 12, on, bold = true)
                } else if (first.room.isNotEmpty()) {
                    Spacer(GlanceModifier.width(6.dp))
                    BodyText(first.room, 12, onVariant)
                }
            }
        }
        }
        MoreLine(blocks.size - count, "lezione" to "lezioni")
    }
}

class TimetableWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TimetableWidget()
}

// MARK: - Compiti e verifiche

class AgendaWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val model = Widgets.model(context)
        val (light, dark) = Widgets.colors(context)
        val palette = Palette(light, dark)
        provideContent { WidgetFrame(context, "bcw://dashboard", palette, "book", "Compiti e verifiche") { AgendaContent(model, palette) } }
    }
}

/**
 * Compiti e verifiche dei prossimi 14 giorni, tutti nella stessa lista segmentata: le verifiche si
 * riconoscono dall'icona nel terziario, i compiti dall'icona nel secondario (come nella app).
 */
@Composable
private fun AgendaContent(model: AppModel, palette: Palette) {
    if (model.phase != Phase.signedIn) return Empty("book", "Accedi a BCW")
    val today = Dates.today()
    val events = model.agenda.filter { !it.day.isBefore(today) && it.day.isBefore(today.plusDays(14)) && !(it.kind == AgendaKind.homework && model.preferences.isCompleted(it)) }
        .sortedWith(compareBy({ it.day }, { if (it.kind == AgendaKind.test) 0 else 1 }))
    if (events.isEmpty()) return Empty("book", "Niente in programma")
    val list = fit(events.size, innerHeight(), 54.dp, 62.dp)
    val count = list.count
    val rowHeight = list.height
    Column(GlanceModifier.fillMaxSize()) {
        Column(GlanceModifier.fillMaxWidth().defaultWeight()) {
            events.take(count).forEachIndexed { index, e -> AgendaRow(e, index, count, rowHeight, palette) }
        }
        MoreLine(events.size - count, "voce" to "voci")
    }
}

@Composable
private fun androidx.glance.layout.ColumnScope.AgendaRow(e: AgendaEvent, index: Int, count: Int, height: Dp?, palette: Palette) {
    val test = e.kind == AgendaKind.test
    SegmentRow(index, count, palette.of { it.scheme.surfaceContainerHigh }, height) {
        Box(
            GlanceModifier.size(32.dp).background(if (test) GlanceTheme.colors.tertiaryContainer else GlanceTheme.colors.secondaryContainer).cornerRadius(16.dp),
            contentAlignment = Alignment.Center,
        ) {
            IconImage(if (test) "test" else "book", 18.dp, if (test) GlanceTheme.colors.onTertiaryContainer else GlanceTheme.colors.onSecondaryContainer)
        }
        Spacer(GlanceModifier.width(10.dp))
        Column(GlanceModifier.defaultWeight()) {
            BodyText(e.title, 14, GlanceTheme.colors.onSurface, bold = true)
            BodyText((if (test) "Verifica · " else "") + e.notes.ifEmpty { "Nessuna descrizione" }, 12, GlanceTheme.colors.onSurfaceVariant)
        }
        Spacer(GlanceModifier.width(8.dp))
        BodyText(Fmt.relativeShortDayName(e.day), 12, if (Dates.isToday(e.day)) GlanceTheme.colors.primary else GlanceTheme.colors.onSurfaceVariant, bold = Dates.isToday(e.day))
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
        val palette = Palette(light, dark)
        provideContent { WidgetFrame(context, "bcw://voti", palette, "graduationCap", "Voti") { AverageContent(model, palette) } }
    }
}

private fun toneOf(average: Double?, c: BCWColors) = when {
    average == null -> c.neutral
    average >= 6 -> c.good
    average >= 5.5 -> c.fair
    else -> c.poor
}

/** Stesso criterio della app: bene, quasi, insufficiente; grigio per annullati e voti che non fanno media. */
private fun gradeToneOf(g: Grade, c: BCWColors): ToneColors =
    if (g.canceled || !g.countsTowardAverage) c.neutral else toneOf(g.value, c)

/**
 * La media generale nell'anello ondulato della schermata Voti; se il widget è più alto, sotto gli
 * ultimi voti in una lista segmentata (due colonne se è largo), solo quelli che entrano interi.
 */
@Composable
private fun AverageContent(model: AppModel, palette: Palette) {
    if (model.phase != Phase.signedIn) return Empty("graduationCap", "Accedi a BCW")
    val average = model.gradeBook.average()
    val height = innerHeight()
    val ring = if (height >= 150.dp) 96.dp else min(height.value - 22f, 84f).coerceAtLeast(48f).dp
    val progress = ((average ?: 0.0) / 10.0).toFloat().coerceIn(0f, 1f)
    val context = LocalContext.current
    val px = (ring.value * context.resources.displayMetrics.density).toInt().coerceAtLeast(1)
    Column(GlanceModifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(GlanceModifier.size(ring), contentAlignment = Alignment.Center) {
            Image(
                provider = ImageProvider(WidgetArt.ringMask(1f, px)), contentDescription = null,
                colorFilter = ColorFilter.tint(palette.of { toneOf(average, it).container }), modifier = GlanceModifier.size(ring),
            )
            if (progress > 0f) Image(
                provider = ImageProvider(WidgetArt.ringMask(progress, px)), contentDescription = null,
                colorFilter = ColorFilter.tint(palette.of { toneOf(average, it).color }), modifier = GlanceModifier.size(ring),
            )
            SansText(GradeFormat.average(average), ring.value * 0.23f, GlanceTheme.colors.onSurface, ring * 0.66f, weight = 650, rond = 100, tabular = true)
        }
        Spacer(GlanceModifier.height(4.dp))
        BodyText("Media generale", 12, GlanceTheme.colors.onSurfaceVariant, bold = true)
        val listHeight = height - ring - 4.dp - 16.dp - 10.dp
        if (listHeight < 48.dp) return@Column
        Spacer(GlanceModifier.height(10.dp))
        val cols = if (innerWidth() >= 250.dp) 2 else 1
        val recent = model.gradeBook.grades.sortedByDescending { it.date }
        if (recent.isEmpty()) return@Column BodyText("Nessun voto", 13, GlanceTheme.colors.onSurfaceVariant)
        val list = fit((recent.size + cols - 1) / cols, listHeight + 22.dp, 48.dp, 56.dp)
        val rows = list.count
        val rowHeight = list.height
        if (rows == 0) return@Column
        Row(GlanceModifier.fillMaxWidth().defaultWeight()) {
            for (col in 0 until cols) {
                if (col > 0) Spacer(GlanceModifier.width(6.dp))
                Column(GlanceModifier.defaultWeight().fillMaxSize()) {
                    val mine = recent.drop(col * rows).take(rows)
                    mine.forEachIndexed { index, grade -> GradeRow(grade, index, rows, rowHeight, palette) }
                }
            }
        }
    }
}

/** Un voto come nella schermata Voti: biscotto nel colore del voto se fa media, cerchio grigio se no. */
@Composable
private fun androidx.glance.layout.ColumnScope.GradeRow(grade: Grade, index: Int, count: Int, height: Dp?, palette: Palette) {
    val counts = grade.countsTowardAverage && !grade.canceled
    val context = LocalContext.current
    SegmentRow(index, count, palette.of { it.scheme.surfaceContainerHigh }, height) {
        Box(GlanceModifier.size(34.dp), contentAlignment = Alignment.Center) {
            Image(
                provider = ImageProvider(WidgetArt.badge(context, 34f, counts)), contentDescription = null,
                colorFilter = ColorFilter.tint(palette.of { if (counts) gradeToneOf(grade, it).container else it.scheme.surfaceContainerHighest }),
                modifier = GlanceModifier.size(34.dp),
            )
            SansText(grade.displayValue, 14f, palette.of { if (counts) gradeToneOf(grade, it).onContainer else it.scheme.onSurfaceVariant }, 34.dp, weight = 700, rond = 100, tabular = true)
        }
        Spacer(GlanceModifier.width(10.dp))
        Column(GlanceModifier.defaultWeight()) {
            BodyText(grade.subjectName, 13, GlanceTheme.colors.onSurface, bold = true)
            BodyText(Fmt.shortDay(grade.date), 11, GlanceTheme.colors.onSurfaceVariant)
        }
    }
}

class AverageWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = AverageWidget()
}

// MARK: - Lezione in corso

class NowWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val model = Widgets.model(context)
        val (light, dark) = Widgets.colors(context)
        val palette = Palette(light, dark)
        val shown = nowState(model)
        val tint = shown?.lesson?.tint
        provideContent {
            WidgetFrame(
                context, "bcw://orario", palette, "clock", shown?.label ?: "Lezione",
                background = tint?.let { t -> palette.of { t.container(it) } },
                accent = tint?.let { t -> palette.of { t.onContainer(it) } },
            ) { NowContent(model, shown, palette) }
        }
    }
}

/** Cosa mostra "Lezione in corso": la lezione, l'etichetta in alto, quella dopo e se è in corso. */
private class NowState(val lesson: LivePhase, val label: String, val current: Boolean, val following: LivePhase?, val minute: Int)

private fun nowState(model: AppModel): NowState? {
    if (model.phase != Phase.signedIn || model.timetable.isEmpty) return null
    val today = Dates.today()
    val minute = Dates.nowMinute()
    val todayLessons = LivePlan.phases(model, today, true).filter { !it.isBreak }
    todayLessons.firstOrNull { it.start <= minute && minute < it.end }?.let { current ->
        return NowState(current, "Adesso", true, todayLessons.firstOrNull { it.start >= current.end }, minute)
    }
    todayLessons.firstOrNull { it.start > minute }?.let { next ->
        return NowState(next, "Tra ${TimeOfDay.duration(next.start - minute)}", false, todayLessons.firstOrNull { it.start >= next.end }, minute)
    }
    val day = defaultTimetableDay({ model.timetable.entries(it, false) })
    val lessons = LivePlan.phases(model, day, true).filter { !it.isBreak }
    val first = lessons.firstOrNull() ?: return null
    val label = if (Dates.isTomorrow(day)) "Domani" else Weekday.name(Weekday.of(day))
    return NowState(first, label, false, lessons.firstOrNull { it.start >= first.end }, minute)
}

/**
 * La lezione sul colore della materia (come la schermata dell'orologio): il nome grande, aula e
 * docente; in basso l'orario in un chip o, se è in corso, quanto manca con la barra; poi la lezione
 * dopo.
 */
@Composable
private fun NowContent(model: AppModel, state: NowState?, palette: Palette) {
    if (model.phase != Phase.signedIn) return Empty("clock", "Accedi a BCW")
    if (model.timetable.isEmpty) return Empty("timetable", "Crea l'orario nella app")
    if (state == null) return Empty("clock", "Nessuna lezione")
    val lesson = state.lesson
    val on = palette.of { lesson.tint.onContainer(it) }
    val onSoft = palette.of { lesson.tint.onContainer(it).copy(alpha = 0.78f) }
    val width = innerWidth()
    val tall = innerHeight() >= 130.dp
    Column(GlanceModifier.fillMaxSize()) {
        SansText(lesson.title, if (tall) 22f else 18f, on, width, weight = 650, rond = 100, maxLines = if (tall) 3 else 2)
        val detail = listOf(lesson.room, lesson.teacher).filter { it.isNotBlank() }.joinToString(" · ")
        if (detail.isNotEmpty()) BodyText(detail, 12, onSoft, modifier = GlanceModifier.padding(top = 2.dp))
        Spacer(GlanceModifier.defaultWeight())
        if (state.current) {
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                BodyText("fino alle ${TimeOfDay.string(lesson.end)}", 12, onSoft, modifier = GlanceModifier.defaultWeight())
                SansText(TimeOfDay.duration(lesson.end - state.minute), 15f, on, width / 2, weight = 650, rond = 100)
            }
            Spacer(GlanceModifier.height(4.dp))
            LinearProgressIndicator(
                progress = ((state.minute - lesson.start).toFloat() / (lesson.end - lesson.start)).coerceIn(0f, 1f),
                modifier = GlanceModifier.fillMaxWidth().height(6.dp).cornerRadius(3.dp),
                color = on, backgroundColor = palette.of { lesson.tint.onContainer(it).copy(alpha = 0.2f) },
            )
        } else {
            Box(
                GlanceModifier.background(palette.of { lesson.tint.onContainer(it).copy(alpha = 0.12f) }).cornerRadius(12.dp).padding(horizontal = 10.dp, vertical = 5.dp),
            ) {
                SansText("${TimeOfDay.string(lesson.start)}–${TimeOfDay.string(lesson.end)}", 14f, on, width, weight = 600, rond = 100, tabular = true)
            }
        }
        state.following?.let {
            Spacer(GlanceModifier.height(6.dp))
            BodyText("Poi ${it.title} alle ${TimeOfDay.string(it.start)}", 12, onSoft)
        }
    }
}

class NowWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = NowWidget()
}

// MARK: - Prossima verifica

class NextTestWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val model = Widgets.model(context)
        val (light, dark) = Widgets.colors(context)
        val palette = Palette(light, dark)
        val next = if (model.phase == Phase.signedIn) model.agenda.filter { it.kind == AgendaKind.test && !it.day.isBefore(Dates.today()) }.minByOrNull { it.begin } else null
        val link = next?.let { "bcw://dashboard?giorno=${Dates.dayKey(it.day)}" } ?: "bcw://dashboard"
        provideContent {
            WidgetFrame(context, link, palette, "test", "Prossima verifica", accent = GlanceTheme.colors.tertiary) { NextTestContent(model, next) }
        }
    }
}

/**
 * La prossima verifica: quanto manca in un biscotto nel terziario (come i badge della app), la
 * materia o il docente, il giorno, e sotto la descrizione; tutto centrato in altezza.
 */
@Composable
private fun NextTestContent(model: AppModel, test: AgendaEvent?) {
    if (model.phase != Phase.signedIn) return Empty("test", "Accedi a BCW")
    if (test == null) return Empty("test", "Nessuna verifica in vista")
    val days = Dates.daysBetween(Dates.today(), test.day)
    val width = innerWidth()
    val context = LocalContext.current
    Column(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(GlanceModifier.size(60.dp), contentAlignment = Alignment.Center) {
                Image(
                    provider = ImageProvider(WidgetArt.badge(context, 60f, true)), contentDescription = null,
                    colorFilter = ColorFilter.tint(GlanceTheme.colors.tertiaryContainer), modifier = GlanceModifier.size(60.dp),
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    when (days) {
                        0L -> SansText("Oggi", 15f, GlanceTheme.colors.onTertiaryContainer, 56.dp, weight = 700, rond = 100)
                        1L -> SansText("Domani", 13f, GlanceTheme.colors.onTertiaryContainer, 56.dp, weight = 700, rond = 100)
                        else -> {
                            SansText("$days", 22f, GlanceTheme.colors.onTertiaryContainer, 56.dp, weight = 700, rond = 100, tabular = true)
                            BodyText("giorni", 10, GlanceTheme.colors.onTertiaryContainer)
                        }
                    }
                }
            }
            Spacer(GlanceModifier.width(12.dp))
            Column(GlanceModifier.defaultWeight()) {
                SansText(test.title, 17f, GlanceTheme.colors.onSurface, width - 72.dp, weight = 650, rond = 100, maxLines = 2)
                BodyText(Fmt.relativeShortDayName(test.day) + if (test.isFullDay) "" else " · ${test.timeDescription}", 12, GlanceTheme.colors.onSurfaceVariant)
            }
        }
        if (test.notes.isNotEmpty()) {
            Spacer(GlanceModifier.height(10.dp))
            BodyText(test.notes, 13, GlanceTheme.colors.onSurfaceVariant, maxLines = if (innerHeight() >= 160.dp) 4 else 2)
        }
    }
}

class NextTestWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = NextTestWidget()
}
