package com.bcw.classeviva.ui.screens.timetable

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.bcw.classeviva.data.timetable.Alignment as TTAlignment
import com.bcw.classeviva.data.timetable.DayContent
import com.bcw.classeviva.data.timetable.TimeOfDay
import com.bcw.classeviva.data.timetable.TimetableEntry
import com.bcw.classeviva.data.timetable.Weekday
import com.bcw.classeviva.data.timetable.roundHalfAway
import com.bcw.classeviva.ui.components.BCWMenu
import com.bcw.classeviva.ui.components.MenuItem
import com.bcw.classeviva.ui.components.mix
import com.bcw.classeviva.ui.icons.Icon
import com.bcw.classeviva.ui.shell.LocalModel
import com.bcw.classeviva.ui.shell.LocalShell
import com.bcw.classeviva.ui.shell.LocalUi
import com.bcw.classeviva.ui.theme.Theme
import com.bcw.classeviva.util.Dates
import com.bcw.classeviva.util.Fmt
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.math.abs
import kotlin.math.floor

private const val GUTTER = 54

/** Trascinamento in corso nella modifica: anteprima dell'orario e del giorno. */
private data class DragState(val entryId: String, val start: Int, val end: Int, val dayShift: Int)

/**
 * Orario mentre si trascina (come `previewFor` dell'app): gli scatti seguono l'allineamento (mezz'ora
 * o 5 minuti, 5 per gli intervalli); con l'automatico la durata è 30 minuti o un'ora.
 */
private fun previewFor(entry: TimetableEntry, alignment: TTAlignment, moveY: Float, resizeY: Float, moveX: Float, hourPx: Float, days: Int, dayIndex: Int, dayWidthPx: Float): Triple<Int, Int, Int> {
    val step = if (entry.isBreak) 5 else alignment.dragStep
    val isAutomatic = !entry.isBreak && alignment == TTAlignment.automatic
    var start = entry.start
    var end = entry.end
    val moveMinutes = moveY / hourPx * 60
    if (abs(moveMinutes) >= step / 2.0) {
        start = TimeOfDay.snapped(entry.start + roundHalfAway(moveMinutes.toDouble()).toInt(), step).coerceIn(0, 1440 - entry.duration)
        end = start + entry.duration
    }
    val resizeMinutes = resizeY / hourPx * 60
    if (abs(resizeMinutes) >= step / 2.0) {
        var length = entry.duration + roundHalfAway(resizeMinutes.toDouble()).toInt()
        length = if (isAutomatic) (if (length < 45) 30 else 60) else maxOf(5, TimeOfDay.snapped(length, 5))
        end = minOf(1440, start + length)
    }
    var dayShift = 0
    if (days > 1 && dayWidthPx > 0) dayShift = roundHalfAway((moveX / dayWidthPx).toDouble()).toInt().coerceIn(-dayIndex, days - 1 - dayIndex)
    return Triple(start, end, dayShift)
}

/**
 * Trascinamento dopo una breve pressione (con il dito lo scorrimento della pagina resta libero):
 * `onDrag` riceve lo spostamento totale, `onEnd` se lo spostamento va salvato. Lasciando senza
 * essersi spostati il tocco resta un tocco (apre l'editor). L'evento si sposta mentre lo si
 * trascina: `origin` è la sua posizione nella finestra, così lo spostamento si misura rispetto
 * alla finestra e non all'evento (che a ogni scatto cambierebbe le coordinate del dito).
 */
private suspend fun PointerInputScope.detectDelayedDrag(
    delayMillis: Long, origin: () -> Offset, onStart: () -> Unit, onDrag: (Offset) -> Unit, onEnd: (commit: Boolean) -> Unit,
) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        // Prima della pressione prolungata: un tocco o uno scorrimento non avviano il trascinamento.
        val early = withTimeoutOrNull(delayMillis) {
            var ended = false
            while (!ended) {
                val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id }
                ended = change == null || !change.pressed || change.isConsumed ||
                    (change.position - down.position).getDistance() > viewConfiguration.touchSlop
            }
            true
        }
        if (early != null) return@awaitEachGesture
        onStart()
        val start = down.position + origin()
        var total = Offset.Zero
        var moved = false
        while (true) {
            val event = awaitPointerEvent()
            val change = event.changes.firstOrNull { it.id == down.id }
            if (change == null) {
                onEnd(false)
                return@awaitEachGesture
            }
            if (!change.pressed) {
                // Dopo uno spostamento il rilascio non apre l'editor.
                if (moved) change.consume()
                onEnd(moved)
                return@awaitEachGesture
            }
            total = change.position + origin() - start
            if (abs(total.x) >= 4 || abs(total.y) >= 4) moved = true
            change.consume()
            onDrag(total)
        }
    }
}

/**
 * I giorni sopra le colonne della settimana (schermi larghi): fermi sopra la giornata che scorre,
 * con le stesse misure delle colonne. Toccandone uno lo si sceglie.
 */
@Composable
fun TimelineDayHeader(days: List<LocalDate>, selected: LocalDate, onSelect: (LocalDate) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().height(64.dp).padding(end = 6.dp, bottom = 8.dp)) {
        Box(Modifier.width(GUTTER.dp))
        for (day in days) {
            val isSel = day == selected
            val today = Dates.isToday(day)
            Column(
                Modifier.weight(1f).fillMaxSize().clip(RoundedCornerShape(Theme.radii.l)).clickable { onSelect(day) }.padding(vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(Fmt.weekdayShort(day), style = Theme.type.style(13), color = if (isSel) Theme.scheme.primary else Theme.scheme.onSurfaceVariant)
                Box(Modifier.size(30.dp).clip(CircleShape).background(if (isSel) Theme.scheme.primary else Color.Transparent), contentAlignment = Alignment.Center) {
                    Text(day.dayOfMonth.toString(), style = Theme.type.numeral(15, 500),
                        color = when { isSel -> Theme.scheme.onPrimary; today -> Theme.scheme.primary; else -> Theme.scheme.onSurface })
                }
            }
        }
    }
}

/**
 * La giornata a ore (vista Giorno; sugli schermi larghi la settimana): righe delle ore, linea di
 * adesso, eventi posizionati per orario, intervalli sopra le lezioni. In modifica gli eventi si
 * trascinano (anche in un altro giorno della settimana), si allungano dal bordo in basso e un
 * doppio tocco su un'ora vuota crea un evento.
 */
@Composable
fun Timeline(
    days: List<LocalDate>, selected: LocalDate, now: LocalDateTime, editing: Boolean, listState: LazyListState,
    onOpen: (TimetableEntry, LocalDate) -> Unit, onEdit: (TimetableEntry) -> Unit, onSeries: (Int) -> Unit,
    onCreate: (weekday: Int, start: Int) -> Unit,
) {
    val model = LocalModel.current
    val store = model.timetable
    val density = LocalDensity.current
    val wide = LocalShell.current.wide
    val hh = if (wide) 52.dp else 60.dp
    val multiple = days.size > 1
    val contents = days.map { store.dayContent(it, model.events(it), splitAtBreaks = false) }
    val showsNow = days.any { Dates.isToday(it) }
    val nowMinutes = Dates.minuteOfDay(now)
    var drag by remember { mutableStateOf<DragState?>(null) }

    // La giornata scorre a mezz'ora prima della prima lezione (all'apertura, cambiando giorno o vista).
    LaunchedEffect(days) {
        val starts = contents.mapNotNull { it.layout.span?.first }
        val hour = if (starts.isNotEmpty()) maxOf(0, floor((starts.min() - 30) / 60.0).toInt()) else 7
        withFrameNanos {}
        val offset = with(density) { (hh * hour).roundToPx() }
        listState.scrollToItem(0, maxOf(0, offset))
    }

    Column(Modifier.fillMaxWidth()) {
        val line = Theme.scheme.outlineVariant
        Box(
            Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 12.dp).height(hh * 24)
                .drawBehind {
                    val hourPx = hh.toPx()
                    val left = GUTTER.dp.toPx()
                    val right = size.width - 6.dp.toPx()
                    for (h in 0 until 24) {
                        val y = h * hourPx
                        drawLine(line, Offset(0f, y), Offset(right, y), 1.dp.toPx())
                        drawLine(line.copy(alpha = 0.45f), Offset(left, y + hourPx / 2), Offset(right, y + hourPx / 2), 1.dp.toPx())
                    }
                },
        ) {
            // Le etichette delle ore; quella coperta dall'etichetta di adesso si toglie.
            for (h in 0 until 24) {
                val covered = showsNow && abs(nowMinutes / 60f * hh.value - h * hh.value) < 17
                if (!covered) {
                    Text(
                        String.format(java.util.Locale.ROOT, "%02d:00", h),
                        style = Theme.type.numeral(11, 400), color = Theme.scheme.onSurfaceVariant, textAlign = TextAlign.End,
                        modifier = Modifier.offset(y = hh * h - 8.dp).width(44.dp).background(Theme.scheme.surface).padding(end = 4.dp),
                    )
                }
            }
            Row(Modifier.fillMaxSize().padding(start = GUTTER.dp, end = 6.dp)) {
                days.forEachIndexed { index, day ->
                    DayColumn(
                        content = contents[index], dayIndex = index, days = days, selected = day == selected && multiple, editing = editing, hh = hh,
                        drag = drag, onDragChange = { drag = it }, onOpen = onOpen, onEdit = onEdit, onSeries = onSeries, onCreate = onCreate,
                        // La colonna da cui si trascina sta sopra le altre: l'evento resta visibile anche spostandolo in un altro giorno.
                        modifier = Modifier.weight(1f).fillMaxSize().zIndex(if (drag != null && contents[index].entries.any { it.id == drag?.entryId }) 1f else 0f).then(
                            if (index > 0) Modifier.drawBehind { drawLine(line.copy(alpha = 0.6f), Offset(0f, 0f), Offset(0f, size.height), 1.dp.toPx()) } else Modifier,
                        ),
                    )
                }
            }
            if (showsNow) {
                val poor = Theme.colors.poor
                Box(Modifier.zIndex(30f).fillMaxWidth().offset(y = hh * (nowMinutes / 60f) - 9.dp).height(18.dp)) {
                    Box(Modifier.padding(start = 50.dp, end = 6.dp).fillMaxWidth().height(2.dp).align(Alignment.CenterStart).background(poor.color))
                    Box(Modifier.padding(start = 50.dp).size(8.dp).align(Alignment.CenterStart).clip(CircleShape).background(poor.color))
                    Text(
                        TimeOfDay.string(nowMinutes), style = Theme.type.numeral(11, 700), color = poor.onColor,
                        modifier = Modifier.align(Alignment.CenterStart).clip(RoundedCornerShape(8.dp)).background(poor.color).padding(horizontal = 5.dp, vertical = 1.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun DayColumn(
    content: DayContent, dayIndex: Int, days: List<LocalDate>, selected: Boolean, editing: Boolean, hh: Dp,
    drag: DragState?, onDragChange: (DragState?) -> Unit,
    onOpen: (TimetableEntry, LocalDate) -> Unit, onEdit: (TimetableEntry) -> Unit, onSeries: (Int) -> Unit, onCreate: (Int, Int) -> Unit,
    modifier: Modifier,
) {
    val day = content.day
    val density = LocalDensity.current
    BoxWithConstraints(
        modifier
            .then(if (selected) Modifier.background(Theme.scheme.primary.copy(alpha = 0.05f)) else Modifier)
            .then(if (editing) Modifier.pointerInput(day) {
                detectTapGestures(onDoubleTap = { offset ->
                    val hourPx = hh.toPx()
                    val minute = TimeOfDay.snapped((offset.y / hourPx * 60).toInt() - 15, 30).coerceIn(0, 23 * 60 + 30)
                    onCreate(Weekday.of(day), minute)
                })
            } else Modifier),
    ) {
        val width = maxWidth
        val columnWidthPx = with(density) { width.toPx() }
        for (cluster in content.layout.clusters) {
            for (placed in cluster.blocks) {
                placed.block.entries.forEachIndexed { index, entry ->
                    Segment(
                        entry, content, x = width * (placed.column.toFloat() / cluster.columns) + 2.dp, w = width / cluster.columns - 4.dp,
                        roundsTop = index == 0, roundsBottom = index == placed.block.entries.lastIndex, blockStart = placed.block.start,
                        dayIndex = dayIndex, days = days, columnWidthPx = columnWidthPx, editing = editing, hh = hh,
                        drag = drag, onDragChange = onDragChange, onOpen = onOpen, onEdit = onEdit, onSeries = onSeries,
                    )
                }
            }
        }
        for (entry in content.layout.breaks) {
            Segment(
                entry, content, x = 8.dp, w = width - 12.dp, roundsTop = true, roundsBottom = true, blockStart = entry.start,
                dayIndex = dayIndex, days = days, columnWidthPx = columnWidthPx, editing = editing, hh = hh,
                drag = drag, onDragChange = onDragChange, onOpen = onOpen, onEdit = onEdit, onSeries = onSeries,
            )
        }
        if (content.entries.isEmpty() && days.size == 1) {
            Column(
                Modifier.offset(y = hh * 8).padding(horizontal = 12.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp))
                    .background(Theme.scheme.surfaceContainerHigh.copy(alpha = 0.95f)).padding(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text("Nessuna lezione ${Weekday.withArticle(Weekday.of(day))}", style = Theme.type.style(14, 600), color = Theme.scheme.onSurface, textAlign = TextAlign.Center)
                Text(
                    if (editing) "Tocca due volte un'ora per aggiungere un evento." else "Aggiungi eventi con + o da Modifica orario.",
                    style = Theme.type.style(12), color = Theme.scheme.onSurfaceVariant, textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Segment(
    entry: TimetableEntry, content: DayContent, x: Dp, w: Dp, roundsTop: Boolean, roundsBottom: Boolean, blockStart: Int,
    dayIndex: Int, days: List<LocalDate>, columnWidthPx: Float, editing: Boolean, hh: Dp,
    drag: DragState?, onDragChange: (DragState?) -> Unit,
    onOpen: (TimetableEntry, LocalDate) -> Unit, onEdit: (TimetableEntry) -> Unit, onSeries: (Int) -> Unit,
) {
    val model = LocalModel.current
    val store = model.timetable
    val ui = LocalUi.current
    val haptics = LocalHapticFeedback.current
    val day = content.day
    val badge = content.badges[entry.id]
    val isTest = model.preferences.timetableShowsTests && badge?.isTest == true
    val tint = entryColor(entry)
    val dragging = drag?.entryId == entry.id
    val start = if (dragging) drag!!.start else entry.start
    val end = if (dragging) drag!!.end else entry.end
    val top = hh * (start / 60f)
    val height = maxOf(if (entry.isBreak) 10.dp else 16.dp, hh * ((end - start) / 60f) - 2.dp)
    val draft = content.isDraft
    var menu by remember { mutableStateOf(false) }
    val r = Theme.radii
    val big = if (draft) r.xl else r.m
    val small = if (draft) r.l else r.xs
    val shape = if (entry.isBreak) RoundedCornerShape(10.dp) else RoundedCornerShape(
        topStart = if (roundsTop) big else small, topEnd = if (roundsTop) big else small,
        bottomStart = if (roundsBottom) big else small, bottomEnd = if (roundsBottom) big else small,
    )
    val surface = Theme.scheme.surface
    val bg = when {
        entry.isBreak -> tint.mix(Theme.scheme.surfaceContainerLowest, 0.32f)
        draft -> tint.mix(surface, 0.30f)
        else -> tint.mix(surface, 0.36f)
    }
    val currentEntry by rememberUpdatedState(entry)
    val alignment = store.alignment

    // Posizione nella finestra dell'evento e del bordo in basso, per misurare il trascinamento.
    val bodyOrigin = remember { arrayOf(Offset.Zero) }
    val handleOrigin = remember { arrayOf(Offset.Zero) }

    fun dragModifier(resize: Boolean, delay: Long, origin: Array<Offset>) = Modifier.onGloballyPositioned { origin[0] = it.positionInRoot() }.pointerInput(entry.id, resize, dayIndex, days, alignment, columnWidthPx) {
        val hourPx = hh.toPx()
        var preview = DragState(entry.id, entry.start, entry.end, 0)
        detectDelayedDrag(
            delay, origin = { origin[0] },
            onStart = {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                preview = DragState(currentEntry.id, currentEntry.start, currentEntry.end, 0)
                onDragChange(preview)
            },
            onDrag = { total ->
                val (s, e, shift) = if (resize) previewFor(currentEntry, alignment, 0f, total.y, 0f, hourPx, days.size, dayIndex, columnWidthPx)
                else previewFor(currentEntry, alignment, total.y, 0f, total.x, hourPx, days.size, dayIndex, columnWidthPx)
                if (s != preview.start || e != preview.end || shift != preview.dayShift) {
                    if (s != preview.start || e != preview.end) haptics.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
                    preview = DragState(currentEntry.id, s, e, shift)
                    onDragChange(preview)
                }
            },
            onEnd = { commit ->
                onDragChange(null)
                if (commit && store.entry(currentEntry.id) != null) {
                    val weekday = Weekday.of(days[(dayIndex + preview.dayShift).coerceIn(0, days.lastIndex)])
                    store.move(currentEntry.id, weekday, preview.start, preview.end)
                }
            },
        )
    }

    val shift = if (dragging) drag!!.dayShift else 0
    val shiftPx = shift * columnWidthPx
    Box(
        Modifier
            .zIndex(if (dragging) 20f else if (entry.isBreak) 2f else 1f)
            .offset(x = x, y = top)
            .size(w, height)
            .graphicsLayer {
                translationX = shiftPx
                val scale = if (dragging) 1.02f else 1f
                scaleX = scale
                scaleY = scale
            }
            .then(if (dragging) Modifier.shadow(8.dp, shape, clip = false) else Modifier)
            .semantics { contentDescription = "${entry.displayTitle}, ${TimeOfDay.string(start)}–${TimeOfDay.string(end)}" },
    ) {
        Box(
            Modifier.fillMaxSize().clip(shape).background(bg)
                .then(if (entry.isBreak) Modifier.drawBehind {
                    val gap = 10.dp.toPx()
                    val stripe = tint.copy(alpha = 0.25f)
                    var o = -size.height
                    while (o < size.width) {
                        drawLine(stripe, Offset(o, size.height), Offset(o + size.height, 0f), 3.dp.toPx())
                        o += gap
                    }
                }.border(0.75.dp, tint.copy(alpha = 0.6f), shape) else Modifier)
                .combinedClickable(
                    onClick = { onOpen(entry, day) },
                    onLongClick = if (draft || editing) null else ({ haptics.performHapticFeedback(HapticFeedbackType.LongPress); menu = true }),
                )
                .then(if (editing) dragModifier(resize = false, delay = 200, bodyOrigin) else Modifier)
                .padding(start = if (draft && !entry.isBreak) 18.dp else if (entry.isBreak) 8.dp else 10.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        ) {
            if (draft && !entry.isBreak) {
                Box(Modifier.offset(x = (-13).dp).width(10.dp).fillMaxSize().padding(vertical = 4.dp)) {
                    WavyStripe(tint)
                }
            }
            SegmentLabel(entry, start, end, height, isTest)
        }
        if (editing) {
            // Il bordo in basso: trascinandolo cambia la durata.
            Box(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(12.dp).then(dragModifier(resize = true, delay = 100, handleOrigin)),
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.size(22.dp, 3.dp).clip(RoundedCornerShape(2.dp)).background(Theme.scheme.onSurface.copy(alpha = 0.35f)))
            }
        }
        if (dragging) {
            val target = days[(dayIndex + shift).coerceIn(0, days.lastIndex)]
            val dayName = if (shift != 0) " · ${Weekday.shortName(Weekday.of(target)).lowercase(Dates.italian)}" else ""
            Text(
                "${TimeOfDay.string(start)}–${TimeOfDay.string(end)}$dayName",
                style = Theme.type.numeral(11, 700), color = Theme.scheme.onPrimary, maxLines = 1,
                modifier = Modifier.offset(x = 4.dp, y = (-26).dp).shadow(3.dp, RoundedCornerShape(12.dp)).clip(RoundedCornerShape(12.dp))
                    .background(Theme.scheme.primary).padding(horizontal = 10.dp, vertical = 2.dp),
            )
        }
        BCWMenu(menu, { menu = false }, listOf(
            MenuItem("Modifica", "edit") { onEdit(entry) },
            MenuItem("Duplica", "duplicate") { store.duplicate(entry) },
            MenuItem("Colora la giornata…", "palette") { onSeries(entry.weekday) },
            null,
            MenuItem("Elimina", "trash", destructive = true) { deleteEntryDialog(ui, store, entry) },
        ))
    }
}

/** Onda sul bordo sinistro degli eventi dell'anteprima. */
@Composable
private fun WavyStripe(color: Color) {
    androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
        val path = androidx.compose.ui.graphics.Path()
        val amp = size.width / 2 - 1.5.dp.toPx()
        val wave = 12.dp.toPx()
        var y = 0f
        path.moveTo(size.width / 2, 0f)
        while (y <= size.height) {
            path.lineTo(size.width / 2 + amp * kotlin.math.sin(2 * Math.PI * y / wave).toFloat(), y)
            y += 1.5f
        }
        drawPath(path, color, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round))
    }
}

@Composable
private fun SegmentLabel(entry: TimetableEntry, start: Int, end: Int, height: Dp, isTest: Boolean) {
    val title = entry.displayTitle
    val time = "${TimeOfDay.string(start)}–${TimeOfDay.string(end)}"
    val titleStyle = Theme.type.style(12, 650, lineHeight = 15.sp)
    val timeStyle = Theme.type.numeral(11, 400)
    val variant = Theme.scheme.onSurfaceVariant
    if (height >= 34.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = titleStyle, color = Theme.scheme.onSurface, maxLines = if (height >= 70.dp) 2 else 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                if (isTest) Icon("test", size = 14.dp, tint = Theme.scheme.primary)
            }
            Text(time + if (entry.room.isNotEmpty()) " · ${entry.room}" else "", style = timeStyle, color = variant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (isTest && height >= 52.dp) Text("Verifica", style = Theme.type.style(11, 750), color = Theme.scheme.primary)
        }
    } else {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = titleStyle, color = Theme.scheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
            Text(time, style = timeStyle, color = variant, maxLines = 1)
            if (isTest) Icon("test", size = 14.dp, tint = Theme.scheme.primary)
        }
    }
}
