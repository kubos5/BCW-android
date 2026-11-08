package com.bcw.classeviva.ui.screens.timetable

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.bcw.classeviva.data.AppModel
import com.bcw.classeviva.data.timetable.Alignment as TTAlignment
import com.bcw.classeviva.data.timetable.BreakRule
import com.bcw.classeviva.data.timetable.EntryColor
import com.bcw.classeviva.data.timetable.EntryTint
import com.bcw.classeviva.data.timetable.GenerationSettings
import com.bcw.classeviva.data.timetable.PASTEL_ID
import com.bcw.classeviva.data.timetable.Palette
import com.bcw.classeviva.data.timetable.TimetableDraft
import com.bcw.classeviva.data.timetable.TimetableStore
import com.bcw.classeviva.data.timetable.Weekday
import com.bcw.classeviva.data.timetable.builtInPalettes
import com.bcw.classeviva.data.timetable.entryOrder
import com.bcw.classeviva.data.timetable.generateEntries
import com.bcw.classeviva.data.timetable.generationRange
import com.bcw.classeviva.data.timetable.paletteColor
import com.bcw.classeviva.data.timetable.seriesPositions
import com.bcw.classeviva.ui.components.ButtonRow
import com.bcw.classeviva.ui.components.Cell
import com.bcw.classeviva.ui.components.Chevron
import com.bcw.classeviva.ui.components.Collapsible
import com.bcw.classeviva.ui.components.FormIcon
import com.bcw.classeviva.ui.components.ListFooter
import com.bcw.classeviva.ui.components.ListGroup
import com.bcw.classeviva.ui.components.ListHeader
import com.bcw.classeviva.ui.components.ListRow
import com.bcw.classeviva.ui.components.SelectButton
import com.bcw.classeviva.ui.components.Stepper
import com.bcw.classeviva.ui.components.ToggleRow
import com.bcw.classeviva.ui.icons.Icon
import com.bcw.classeviva.ui.shell.LocalModel
import com.bcw.classeviva.ui.shell.LocalUi
import com.bcw.classeviva.ui.theme.Theme
import com.bcw.classeviva.util.newUUID
import kotlinx.coroutines.launch
import kotlin.math.abs

// MARK: - Colora una giornata

/** Colora gli eventi scelti di un giorno con una palette: gli eventi uguali accostati prendono lo stesso colore. */
@Composable
fun SeriesSheet(startWeekday: Int, onDismiss: () -> Unit) {
    val store = LocalModel.current.timetable
    val handle = rememberSheetHandle()
    var weekday by remember { mutableStateOf(startWeekday) }
    fun lessonsOf(w: Int) = store.entries(w, false).filter { !it.isBreak }.sortedWith(entryOrder)
    var paletteId by remember {
        val existing = lessonsOf(startWeekday).firstNotNullOfOrNull { (it.color as? EntryColor.Series)?.paletteID }
        mutableStateOf(if (existing != null && store.palette(existing) != null) existing else "builtin.lagoon")
    }
    var chosen by remember { mutableStateOf(lessonsOf(startWeekday).map { it.id }.toSet()) }
    var editing by remember { mutableStateOf<Palette?>(null) }
    val lessons = lessonsOf(weekday)
    val palette = store.palette(paletteId) ?: builtInPalettes[1]
    val picked = lessons.filter { it.id in chosen }
    val (positions, count) = seriesPositions(picked)
    val preview = picked.mapIndexed { i, e ->
        e.id to (if (palette.id == PASTEL_ID) EntryTint.Subject(positions[i]) else EntryTint.Hex(paletteColor(palette, positions[i], count)))
    }.toMap()

    BCWSheet(
        "Colora una giornata", onDismiss, handle,
        leading = { SheetCloseButton { handle.close() } },
        trailing = {
            SheetDoneButton("Applica", enabled = picked.isNotEmpty()) {
                store.applyPalette(if (store.palette(paletteId) != null) paletteId else builtInPalettes[1].id, chosen.filter { id -> lessons.any { it.id == id } })
                handle.close()
            }
        },
    ) {
        ListGroup {
            item {
                ListRow("Giorno", trailing = {
                    SelectButton((1..7).map { it to Weekday.name(it) }, weekday, { w -> weekday = w; chosen = emptySet() })
                })
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ListHeader("Palette")
            PaletteCards(paletteId, onSelect = { paletteId = it }, onEdit = { editing = it }, onNew = {
                editing = Palette(newUUID(), "", listOf(0x8BCF7E, 0x2B5C9E), true)
            })
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ListHeader("Eventi", Modifier.weight(1f).padding(top = 8.dp))
                TextButton(onClick = { chosen = lessons.map { it.id }.toSet() }) { Text("Tutti") }
                TextButton(onClick = { chosen = emptySet() }) { Text("Nessuno") }
            }
            if (lessons.isEmpty()) {
                Text("Nessuna lezione ${Weekday.withArticle(weekday)}.", style = Theme.type.style(14), color = Theme.scheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp))
            } else {
                ListGroup {
                    for (e in lessons) item(e.id) {
                        val on = e.id in chosen
                        ListRow(
                            e.displayTitle, subtitle = e.timeRange, selected = on,
                            onClick = { chosen = if (on) chosen - e.id else chosen + e.id },
                            leading = {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Icon(if (on) "checkCircleFill" else "circle", tint = if (on) Theme.scheme.primary else Theme.scheme.onSurfaceVariant)
                                    Box(Modifier.width(5.dp).height(32.dp).clip(RoundedCornerShape(3.dp)).background(if (on) tintColor(preview.getValue(e.id)) else entryColor(e)))
                                }
                            },
                        )
                    }
                }
            }
            ListFooter("Tocca gli eventi per sceglierli: una serie di seguito o tutta la giornata. Gli eventi uguali accostati prendono lo stesso colore. La palette vale solo per ${Weekday.withArticle(weekday)}: gli eventi con lo stesso nome negli altri giorni restano come sono.")
        }
        TextButton(
            onClick = { store.applyPalette(null, chosen); handle.close() }, enabled = picked.isNotEmpty(),
            colors = ButtonDefaults.textButtonColors(contentColor = Theme.scheme.error), modifier = Modifier.align(Alignment.CenterHorizontally),
        ) { Text("Togli i colori") }
    }
    editing?.let { PaletteEditorSheet(it, onDismiss = { editing = null }) }
}

// MARK: - Sposta gli orari

private const val SHIFT_LIMIT = 720

private fun signedMinutes(v: Int) = when {
    v > 0 -> "+$v"
    v < 0 -> "−${-v}"
    else -> "0"
}

/**
 * Sposta più eventi insieme di un numero di minuti: un valore per tutti i giorni e uno per ogni
 * giorno; aprendo un giorno si scelgono gli eventi da spostare, con l'orario nuovo accanto. La
 * durata non cambia. Con l'allineamento automatico, se una lezione non cadrebbe più su un'ora o
 * una mezz'ora, un avviso dice che l'orario passa all'allineamento libero.
 */
@Composable
fun ShiftSheet(startWeekday: Int, onDismiss: () -> Unit) {
    val store = LocalModel.current.timetable
    val haptics = LocalHapticFeedback.current
    val handle = rememberSheetHandle()
    val weekdays = (1..7).filter { store.entries(it, false).isNotEmpty() }
    var common by remember { mutableStateOf(5) }
    var minutes by remember { mutableStateOf(mapOf<Int, Int>()) }
    var excluded by remember { mutableStateOf(setOf<String>()) }
    var includesBreaks by remember { mutableStateOf(true) }
    var expanded by remember { mutableStateOf(if (startWeekday in weekdays) setOf(startWeekday) else emptySet()) }

    fun selectable(isBreak: Boolean) = includesBreaks || !isBreak
    fun valueOf(weekday: Int?) = if (weekday != null) minutes[weekday] ?: common else common
    fun set(weekday: Int?, value: Int) {
        val v = value.coerceIn(-SHIFT_LIMIT, SHIFT_LIMIT)
        if (weekday != null) minutes = minutes + (weekday to v)
        else {
            common = v
            minutes = emptyMap()
        }
    }
    val moves = buildMap {
        for (w in weekdays) {
            val delta = valueOf(w)
            if (delta == 0) continue
            for (e in store.entries(w, false)) if (selectable(e.isBreak) && e.id !in excluded) put(e.id, delta)
        }
    }
    val leaves = store.alignment == TTAlignment.automatic && store.timetable.entries.any { e -> !e.isBreak && (moves[e.id] ?: 0) != 0 && e.shifted(moves.getValue(e.id)).start % 30 != 0 }
    val days = store.timetable.entries.filter { it.id in moves }.map { it.weekday }.toSet().size
    val summary = if (moves.isEmpty()) "Nessun evento da spostare"
    else "${if (moves.size == 1) "1 evento" else "${moves.size} eventi"} in ${if (days == 1) "1 giorno" else "$days giorni"}"

    BCWSheet(
        "Sposta gli orari", onDismiss, handle,
        leading = { SheetCloseButton { handle.close() } },
        trailing = {
            SheetDoneButton("Sposta", enabled = moves.isNotEmpty()) {
                haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                store.shift(moves, freeAlignment = leaves)
                handle.close()
            }
        },
    ) {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(Theme.radii.xl)).background(Theme.scheme.surfaceContainer).padding(vertical = 20.dp, horizontal = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("TUTTI I GIORNI", style = Theme.type.style(12, 750, tracking = 0.8), color = Theme.scheme.onSurfaceVariant)
            MinutesField(common, large = true) { set(null, it) }
            Row(horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)) {
                val shortcuts = listOf(-15, -10, -5, 5, 10, 15)
                shortcuts.forEachIndexed { i, v ->
                    ToggleButton(
                        checked = common == v,
                        onCheckedChange = { haptics.performHapticFeedback(HapticFeedbackType.SegmentTick); set(null, v) },
                        shapes = when (i) {
                            0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                            shortcuts.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                            else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                        },
                        colors = ToggleButtonDefaults.toggleButtonColors(containerColor = Theme.scheme.surfaceContainerHighest, contentColor = Theme.scheme.onSurfaceVariant),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
                        modifier = Modifier.weight(1f).heightIn(min = 40.dp),
                    ) { Text(signedMinutes(v), style = Theme.type.numeral(13, 600), maxLines = 1) }
                }
            }
            Text(summary, style = Theme.type.style(14), color = Theme.scheme.onSurfaceVariant)
        }
        ListGroup {
            item { ToggleRow("Sposta anche gli intervalli", includesBreaks, { includesBreaks = it }, icon = "coffee") }
        }
        if (leaves) {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(Theme.radii.li)).background(Theme.scheme.tertiaryContainer).padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon("alignFree", tint = Theme.scheme.onTertiaryContainer)
                Text("L'orario usa l'allineamento automatico, con gli eventi alle ore e alle mezz'ore. Con questi minuti passerà all'allineamento libero.",
                    style = Theme.type.style(14), color = Theme.scheme.onTertiaryContainer)
            }
        }
        ListGroup(header = "Giorno per giorno", footer = "Ogni giorno ha i suoi minuti. Tocca un giorno per vedere i nuovi orari e scegliere quali eventi spostare. La durata degli eventi non cambia.") {
            for (w in weekdays) item(w) {
                val entries = store.entries(w, false).filter { selectable(it.isBreak) }
                val picked = entries.count { it.id !in excluded }
                val open = w in expanded
                val delta = valueOf(w)
                val count = if (picked == entries.size) (if (entries.size == 1) "1 evento" else "${entries.size} eventi") else "$picked di ${entries.size}"
                Cell {
                    Column {
                        Row(Modifier.fillMaxWidth().padding(end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Row(
                                Modifier.weight(1f).clip(RoundedCornerShape(Theme.radii.m)).clickable { expanded = if (open) expanded - w else expanded + w }
                                    .padding(horizontal = 12.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Chevron(!open, rotateUp = true)
                                Column {
                                    Text(Weekday.name(w), style = Theme.type.style(16, 500), color = Theme.scheme.onSurface)
                                    Text(count, style = Theme.type.style(13), color = Theme.scheme.onSurfaceVariant)
                                }
                            }
                            MinutesField(delta, large = false) { set(w, it) }
                        }
                        Collapsible(open) {
                            Column(Modifier.padding(start = 8.dp, end = 8.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                for (e in entries) {
                                    val on = e.id !in excluded
                                    val moved = e.shifted(delta)
                                    val moving = on && moved.start != e.start
                                    Row(
                                        Modifier.fillMaxWidth().clip(RoundedCornerShape(Theme.radii.m))
                                            .clickable { haptics.performHapticFeedback(HapticFeedbackType.SegmentTick); excluded = if (on) excluded + e.id else excluded - e.id }
                                            .padding(horizontal = 8.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    ) {
                                        Icon(if (on) "checkCircleFill" else "circle", size = 22.dp, tint = if (on) Theme.scheme.primary else Theme.scheme.onSurfaceVariant)
                                        Box(Modifier.width(4.dp).height(28.dp).clip(RoundedCornerShape(2.dp)).background(entryColor(e)))
                                        Column(Modifier.weight(1f)) {
                                            Text(e.displayTitle, style = Theme.type.style(15, 500), color = Theme.scheme.onSurface, maxLines = 1)
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                Text(e.timeRange, style = Theme.type.numeral(12, 400), color = Theme.scheme.onSurfaceVariant,
                                                    textDecoration = if (moving) androidx.compose.ui.text.style.TextDecoration.LineThrough else null)
                                                if (moving) {
                                                    Icon("chevronRight", size = 14.dp, tint = Theme.scheme.primary)
                                                    Text(moved.timeRange, style = Theme.type.numeral(12, 650), color = Theme.scheme.primary)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Minuti con il segno, tra i pulsanti − e +: si possono anche scrivere (il segno si cambia toccandolo). */
@Composable
private fun MinutesField(value: Int, large: Boolean, onChange: (Int) -> Unit) {
    val haptics = LocalHapticFeedback.current
    var text by remember(value) { mutableStateOf(if (value == 0) "" else abs(value).toString()) }
    val buttonSize = if (large) 48.dp else 36.dp
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(if (large) 12.dp else 6.dp)) {
        FilledTonalIconButton(onClick = { haptics.performHapticFeedback(HapticFeedbackType.SegmentTick); onChange(value - 5) }, enabled = value > -SHIFT_LIMIT, modifier = Modifier.size(buttonSize)) {
            Icon("minus", contentDescription = "5 minuti prima")
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (value < 0) "−" else "+",
                style = Theme.type.numeral(if (large) 34 else 17, 600),
                color = if (value == 0) Theme.scheme.onSurfaceVariant else Theme.scheme.onSurface,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClickLabel = "Cambia il verso") { onChange(-value) }.padding(horizontal = 4.dp),
            )
            BasicTextField(
                value = text,
                onValueChange = { typed ->
                    val digits = typed.filter { it.isDigit() }.take(3)
                    text = digits
                    val n = minOf(digits.toIntOrNull() ?: 0, SHIFT_LIMIT)
                    onChange(if (value < 0) -n else n)
                },
                singleLine = true,
                textStyle = Theme.type.numeral(if (large) 34 else 17, 600).copy(color = Theme.scheme.onSurface, textAlign = TextAlign.Center),
                cursorBrush = SolidColor(Theme.scheme.primary),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                modifier = Modifier.widthIn(min = if (large) 64.dp else 36.dp).width(if (large) 72.dp else 40.dp),
                decorationBox = { inner ->
                    Box(contentAlignment = Alignment.Center) {
                        if (text.isEmpty()) Text("0", style = Theme.type.numeral(if (large) 34 else 17, 600), color = Theme.scheme.onSurfaceVariant)
                        inner()
                    }
                },
            )
            if (large) Text("min", style = Theme.type.style(16, 500), color = Theme.scheme.onSurfaceVariant)
        }
        FilledTonalIconButton(onClick = { haptics.performHapticFeedback(HapticFeedbackType.SegmentTick); onChange(value + 5) }, enabled = value < SHIFT_LIMIT, modifier = Modifier.size(buttonSize)) {
            Icon("plus", contentDescription = "5 minuti dopo")
        }
    }
}

// MARK: - Generatore

/** Nota sotto le ore di lezione: con ore fuori dalle mezz'ore applicando la bozza l'orario passa all'allineamento libero. */
fun generationHoursFooter(store: TimetableStore, g: GenerationSettings): String {
    val alignedSettings = g.firstHourStart % 30 == 0 && (g.hourLength == 30 || g.hourLength == 60)
    return if (store.alignment == TTAlignment.automatic && !alignedSettings)
        "Con questi orari BCW tiene gli orari esatti: applicando la bozza l'orario passerà da solo dall'allineamento automatico a quello libero, che si cambia in Modifica orario."
    else "Classeviva registra solo in quale ora della giornata si svolge una lezione: l'orario si ricava da qui."
}

/** Le impostazioni della generazione (ore di lezione e intervalli), nel foglio e nell'introduzione. */
@Composable
fun GenerationFields(g: GenerationSettings, onChange: (GenerationSettings) -> Unit) {
    val store = LocalModel.current.timetable
    ListGroup(header = "Ore di lezione", footer = generationHoursFooter(store, g)) {
        item { ListRow("Inizio della prima ora", trailing = { TimeField(g.firstHourStart, { onChange(g.copy(firstHourStart = it)) }, title = "Inizio della prima ora") }) }
        item {
            ListRow("Durata di un'ora", trailing = {
                Stepper("${g.hourLength} min", { onChange(g.copy(hourLength = maxOf(30, g.hourLength - 5))) }, { onChange(g.copy(hourLength = minOf(120, g.hourLength + 5))) },
                    canMinus = g.hourLength > 30, canPlus = g.hourLength < 120)
            })
        }
        item {
            ListRow("Lezioni da esaminare", trailing = {
                SelectButton(listOf(2 to "Ultime 2 settimane", 4 to "Ultime 4 settimane", 6 to "Ultime 6 settimane", 10 to "Ultime 10 settimane", 0 to "Tutto l'anno"),
                    g.weeks, { onChange(g.copy(weeks = it)) })
            })
        }
    }
    ListGroup(header = "Intervalli", footer = "Facoltativi: compaiono sopra le lezioni nei giorni in cui c'è scuola a quell'ora, senza cambiarne la durata. Per toglierne uno usa il pulsante rosso accanto.") {
        g.breaks.forEachIndexed { i, b ->
            item(b.id) {
                Cell(padding = PaddingValues(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 12.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            FormIcon("coffee")
                            Text("Intervallo ${i + 1}", style = Theme.type.style(16, 500), color = Theme.scheme.onSurface, modifier = Modifier.weight(1f))
                            IconButton(onClick = { onChange(g.copy(breaks = g.breaks.filterIndexed { j, _ -> j != i })) }) {
                                Icon("minusCircle", tint = Theme.scheme.error, contentDescription = "Rimuovi l'intervallo")
                            }
                        }
                        Row(Modifier.padding(start = 40.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            TimeField(b.start, { v -> onChange(g.copy(breaks = g.breaks.map { if (it.id == b.id) it.copy(start = v) else it })) }, title = "Inizio dell'intervallo")
                            Spacer(Modifier.weight(1f))
                            Stepper("${b.duration} min",
                                { onChange(g.copy(breaks = g.breaks.map { if (it.id == b.id) it.copy(duration = maxOf(5, it.duration - 5)) else it })) },
                                { onChange(g.copy(breaks = g.breaks.map { if (it.id == b.id) it.copy(duration = minOf(60, it.duration + 5)) else it })) },
                                canMinus = b.duration > 5, canPlus = b.duration < 60)
                        }
                    }
                }
            }
        }
        item("add") {
            ButtonRow("Aggiungi intervallo", "coffee", {
                val last = g.breaks.lastOrNull()
                val start = minOf(if (last != null) last.start + 120 else g.firstHourStart + g.hourLength * 3 - 10, 23 * 60)
                onChange(g.copy(breaks = g.breaks + BreakRule(newUUID(), start, 10)))
            })
        }
        if (g.breaks.isNotEmpty()) item("clear") { ButtonRow("Rimuovi gli intervalli", "trash", { onChange(g.copy(breaks = emptyList())) }, destructive = true) }
    }
}

/**
 * Scarica le lezioni registrate e ne ricava una bozza dell'orario, sempre con gli orari esatti
 * delle ore indicate (se non stanno nell'allineamento automatico, applicandola l'orario passa a
 * quello libero). Usata dal foglio e dall'introduzione.
 */
suspend fun makeTimetableDraft(model: AppModel, settings: GenerationSettings): TimetableDraft {
    val (from, to) = generationRange(settings)
    val lessons = try {
        model.fetchLessons(from, to)
    } catch (e: Exception) {
        throw IllegalStateException("Non riesco a scaricare le lezioni: ${e.message}")
    }
    val result = generateEntries(lessons, settings)
    if (result.entries.isEmpty()) {
        throw IllegalStateException(
            if (lessons.isNotEmpty()) "Le lezioni registrate sono troppo poche o troppo irregolari per ricavare un orario. Prova a esaminare un periodo più lungo."
            else "Non ci sono lezioni registrate nel periodo scelto. Riprova più avanti o esamina un periodo più lungo.",
        )
    }
    return TimetableDraft(result.entries, settings, result.analyzedDays, from, to)
}

@Composable
fun GeneratorSheet(onDismiss: () -> Unit) {
    val model = LocalModel.current
    val ui = LocalUi.current
    val store = model.timetable
    val scope = rememberCoroutineScope()
    val handle = rememberSheetHandle()
    var settings by remember { mutableStateOf(store.draft?.settings ?: store.timetable.generation) }
    var generating by remember { mutableStateOf(false) }
    BCWSheet("Genera l'orario", onDismiss, handle, leading = { SheetCloseButton { handle.close() } }) {
        GenerationFields(settings) { settings = it }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    scope.launch {
                        generating = true
                        val draft = try {
                            makeTimetableDraft(model, settings)
                        } catch (e: Exception) {
                            generating = false
                            ui.ask("Impossibile creare l'orario", e.message, "OK") {}
                            return@launch
                        }
                        generating = false
                        store.showDraft(draft)
                        handle.close()
                    }
                },
                enabled = !generating, modifier = Modifier.fillMaxWidth().height(56.dp),
            ) {
                if (generating) LoadingIndicator(Modifier.size(24.dp), color = LocalContentColor.current) else Icon("wand", size = 20.dp)
                Text("Crea l'anteprima", Modifier.padding(start = 8.dp), style = Theme.type.style(16, 600))
            }
            ListFooter("BCW guarda in quale ora della giornata si è svolta ogni materia, settimana per settimana, e tiene quelle che si ripetono (le settimane recenti contano di più, supplenze e recuperi isolati vengono ignorati). Vedrai l'orario in anteprima prima di applicarlo.")
        }
    }
}
