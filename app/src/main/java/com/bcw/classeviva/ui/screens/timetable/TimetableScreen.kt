package com.bcw.classeviva.ui.screens.timetable

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.FloatingActionButtonMenu
import androidx.compose.material3.FloatingActionButtonMenuItem
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.IconButton
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleFloatingActionButton
import androidx.compose.material3.ToggleFloatingActionButtonDefaults
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import com.bcw.classeviva.platform.LiveUpdates
import com.bcw.classeviva.ui.screens.settings.rememberNotificationPermission
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bcw.classeviva.data.AgendaEvent
import com.bcw.classeviva.data.timetable.Alignment as TTAlignment
import com.bcw.classeviva.data.timetable.DayContent
import com.bcw.classeviva.data.timetable.EntryBlock
import com.bcw.classeviva.data.timetable.EntryKind
import com.bcw.classeviva.data.timetable.ListItem
import com.bcw.classeviva.data.timetable.TimeOfDay
import com.bcw.classeviva.data.timetable.TimetableEntry
import com.bcw.classeviva.data.timetable.TimetableMode
import com.bcw.classeviva.data.timetable.TimetableStore
import com.bcw.classeviva.data.timetable.Weekday
import com.bcw.classeviva.data.timetable.defaultTimetableDay
import com.bcw.classeviva.data.timetable.timetableLayout
import com.bcw.classeviva.ui.components.BCWMenu
import com.bcw.classeviva.ui.components.CalendarBar
import com.bcw.classeviva.ui.components.DatePickerSheet
import com.bcw.classeviva.ui.components.DayHero
import com.bcw.classeviva.ui.components.DayJump
import com.bcw.classeviva.ui.components.EmptyState
import com.bcw.classeviva.ui.components.ListGroup
import com.bcw.classeviva.ui.components.MenuItem
import com.bcw.classeviva.ui.components.Pill
import com.bcw.classeviva.ui.components.RowCaption
import com.bcw.classeviva.ui.components.SegCell
import com.bcw.classeviva.ui.components.Shaped
import com.bcw.classeviva.ui.components.StatusChip
import com.bcw.classeviva.ui.components.Tone
import com.bcw.classeviva.ui.components.WavyProgress
import com.bcw.classeviva.ui.components.WithContentColor
import com.bcw.classeviva.ui.components.mix
import com.bcw.classeviva.ui.icons.Icon
import com.bcw.classeviva.ui.nav.Page
import com.bcw.classeviva.ui.screens.daySwipe
import com.bcw.classeviva.ui.screens.rememberDaySelection
import com.bcw.classeviva.ui.shell.HidesNavigationBar
import com.bcw.classeviva.ui.shell.LocalModel
import com.bcw.classeviva.ui.shell.LocalNavigator
import com.bcw.classeviva.ui.shell.LocalShell
import com.bcw.classeviva.ui.shell.LocalUi
import com.bcw.classeviva.ui.shell.PageScaffold
import com.bcw.classeviva.ui.shell.Transitions
import com.bcw.classeviva.ui.shell.block
import com.bcw.classeviva.ui.theme.Theme
import com.bcw.classeviva.util.Dates
import com.bcw.classeviva.util.Fmt
import com.bcw.classeviva.util.plural
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.LocalDateTime

/** I fogli dell'Orario aperti dalla pagina. */
sealed interface TimetableSheet {
    data class Detail(val entryId: String, val day: LocalDate) : TimetableSheet
    /** `entry` per modificare, altrimenti un evento nuovo in quel giorno, a quell'ora. */
    data class Editor(val entry: TimetableEntry? = null, val weekday: Int = 1, val start: Int = 480, val kind: EntryKind = EntryKind.lesson) : TimetableSheet
    data class Series(val weekday: Int) : TimetableSheet
    data class Shift(val weekday: Int) : TimetableSheet
    data object Generator : TimetableSheet
}

/** Nome del giorno predefinito sul pulsante che ci riporta: "Oggi", "Domani" o "Lunedì". */
fun jumpTitle(day: LocalDate): String = if (Dates.isToday(day) || Dates.isTomorrow(day)) Fmt.relativeDayName(day) else Fmt.weekdayLong(day)

/** L'ora attuale, aggiornata ogni 30 secondi (il pulsante del giorno predefinito, la lezione in corso, la linea di adesso). */
@Composable
fun rememberNow(): LocalDateTime {
    val now by produceState(Dates.now()) {
        while (true) {
            delay(30_000 - System.currentTimeMillis() % 30_000)
            value = Dates.now()
        }
    }
    return now
}

/**
 * Orario: la settimana dell'utente in due viste (Lista e Giorno; sugli schermi larghi la vista
 * Giorno mostra la settimana intera), con le verifiche di Classeviva sulle lezioni, la modalità
 * Modifica orario (trascinare, allungare, doppio tocco per creare) con la barra degli strumenti
 * flottante, il menu del pulsante +, la bozza generata dalle lezioni e i colori delle palette.
 */
@Composable
fun TimetableScreen() {
    val model = LocalModel.current
    val ui = LocalUi.current
    val nav = LocalNavigator.current
    val shell = LocalShell.current
    val store = model.timetable
    val prefs = model.preferences
    val haptics = LocalHapticFeedback.current
    val now = rememberNow()
    val selection = rememberDaySelection { defaultTimetableDay({ store.entries(it) }) }
    var editing by rememberSaveable { mutableStateOf(false) }
    var sheet by remember { mutableStateOf<TimetableSheet?>(null) }
    var picking by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var fabExpanded by rememberSaveable { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val isDraft = store.draft != null
    LaunchedEffect(isDraft) { if (isDraft) editing = false }
    val mode = if (editing) TimetableMode.day else prefs.timetableMode
    val day = selection.day
    val defaultDay = remember(now, store.timetable, store.draft) { defaultTimetableDay({ store.entries(it) }, now) }

    fun select(target: LocalDate) {
        if (selection.select(target)) haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
    }

    // La Lista riparte dall'alto (la vista Giorno scorre da sola alla prima ora).
    LaunchedEffect(mode) { if (mode == TimetableMode.list) listState.scrollToItem(0) }
    LaunchedEffect(model.openedLink) {
        val link = model.openedLink ?: return@LaunchedEffect
        if (link.tab == "timetable") link.day?.let { select(it) }
    }
    // La modifica e l'anteprima sono modalità temporanee: Indietro le chiude.
    BackHandler(enabled = editing) { editing = false }
    BackHandler(enabled = isDraft) {
        ui.ask("Scartare l'anteprima?", "L'orario generato non verrà applicato.", "Scarta", destructive = true) { store.discardDraft() }
    }
    HidesNavigationBar(editing || isDraft)

    val jump = if (day != defaultDay) DayJump(jumpTitle(defaultDay)) { select(defaultDay) } else null
    val toolbar = editing || isDraft
    val content = store.dayContent(day, model.events(day), splitAtBreaks = mode != TimetableMode.day)
    val reduced = Theme.reducedMotion
    val bottomExtra = if (toolbar) 88.dp else 96.dp

    BoxWithConstraints {
        val weekColumns = shell.wide && maxWidth >= 780.dp && mode == TimetableMode.day
        val days = if (weekColumns) weekDays(store, day) else listOf(day)
        PageScaffold(
            title = if (editing) "Modifica orario" else "Orario",
            actions = {
                // Nella modifica e nella settimana, senza il giorno in grande, il pulsante sta nella barra.
                if (jump != null && (editing || weekColumns)) TextButton(onClick = jump.action) { Text(jump.title) }
                if (!editing) {
                    if (mode == TimetableMode.day) {
                        TooltipIconButton("Vista Lista", "viewAgenda") { prefs.set("timetableMode", "list") }
                    } else {
                        TooltipIconButton(if (shell.wide) "Vista Settimana" else "Vista Giorno", "dayView") { prefs.set("timetableMode", "day") }
                    }
                    Box {
                        TooltipIconButton("Opzioni dell'orario", "moreH") { menu = true }
                        BCWMenu(menu, { menu = false }, listOf(
                            MenuItem("Mostra le verifiche", "test", checked = prefs.timetableShowsTests) { prefs.set("timetableShowsTests", !prefs.timetableShowsTests) },
                            MenuItem("Verifiche nascoste…", "eyeOff") { nav.push(Page.HiddenTests) },
                            null,
                            MenuItem("Colora una giornata…", "palette", enabled = !store.isEmpty && !isDraft) { sheet = TimetableSheet.Series(Weekday.of(day)) },
                        ))
                    }
                }
            },
            fixed = {
                Column {
                    CalendarBar(model, day, false, onSelect = ::select, onShift = { select(day.plusDays(7L * it)) }, onPick = { picking = true }, dir = selection.dir)
                    TimetableExtras(store, editing, if (mode == TimetableMode.day && prefs.timetableShowsTests) content.unmatchedTests else emptyList())
                }
            },
            onRefresh = { model.refreshAll() },
            onReselect = { select(defaultDay) },
            // La vista Giorno parte già scorsa alla prima ora: un nuovo tocco va subito al giorno predefinito.
            reselectScrollsToTop = mode == TimetableMode.list,
            listState = listState,
            maxContentWidth = if (weekColumns) 1600.dp else 840.dp,
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp + shell.bottomSpace + bottomExtra),
            listModifier = if (editing) Modifier else Modifier.daySwipe { select(day.plusDays(it.toLong())) },
            overlay = {
                val bottom = shell.bottomSpace + 16.dp
                AnimatedVisibility(
                    toolbar,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = bottom),
                    enter = slideInVertically(Theme.motion.defaultSpatialSpec()) { it * 2 } + fadeIn(),
                    exit = slideOutVertically(Theme.motion.fastSpatialSpec()) { it * 2 } + fadeOut(),
                ) {
                    if (isDraft) DraftToolbar(
                        onGenerate = { sheet = TimetableSheet.Generator },
                        onDiscard = { store.discardDraft() },
                        onApply = { applyDraft(ui, store) },
                    ) else EditToolbar(
                        store,
                        onAlign = { requestAlignment(ui, store, it) },
                        onShift = { if (!store.isEmpty) sheet = TimetableSheet.Shift(Weekday.of(day)) },
                        onNewBreak = { sheet = TimetableSheet.Editor(weekday = Weekday.of(day), kind = EntryKind.breakTime) },
                        onDone = { editing = false },
                    )
                }
                if (!toolbar) {
                    TimetableFabMenu(
                        expanded = fabExpanded, onExpandedChange = { fabExpanded = it },
                        modifier = Modifier.align(Alignment.BottomEnd).padding(bottom = shell.bottomSpace),
                        onGenerate = { sheet = TimetableSheet.Generator },
                        onEdit = { editing = true },
                        onNewBreak = { sheet = TimetableSheet.Editor(weekday = Weekday.of(day), kind = EntryKind.breakTime) },
                        onNew = { sheet = TimetableSheet.Editor(weekday = Weekday.of(day)) },
                    )
                }
            },
        ) {
            block("content") {
                // La dimensione passa subito a quella della nuova vista: la giornata a ore può scorrere alla prima lezione.
                AnimatedContent(mode, transitionSpec = { Transitions.fadeThrough(reduced) using SizeTransform(clip = false) { _, _ -> snap() } }, label = "ttMode") { shown ->
                    if (shown == TimetableMode.list) {
                        AnimatedContent(day, transitionSpec = { Transitions.sharedAxisX(selection.dir, reduced) }, label = "ttDay") { d ->
                            DayList(
                                store.dayContent(d, model.events(d), splitAtBreaks = true), now,
                                jump = if (d != defaultDay) DayJump(jumpTitle(defaultDay)) { select(defaultDay) } else null,
                                onOpen = { entry -> sheet = TimetableSheet.Detail(entry.id, d) },
                                onEdit = { sheet = TimetableSheet.Editor(it) },
                                onSeries = { sheet = TimetableSheet.Series(it) },
                                onNew = { sheet = TimetableSheet.Editor(weekday = Weekday.of(d)) },
                                onGenerate = { sheet = TimetableSheet.Generator },
                            )
                        }
                    } else {
                        val timeline = @Composable { shownDays: List<LocalDate> ->
                            Timeline(
                                days = shownDays, selected = day, now = now, editing = editing && !isDraft, listState = listState,
                                onSelect = ::select,
                                onOpen = { entry, d -> sheet = if (editing && !isDraft) TimetableSheet.Editor(entry) else TimetableSheet.Detail(entry.id, d) },
                                onEdit = { sheet = TimetableSheet.Editor(it) },
                                onSeries = { sheet = TimetableSheet.Series(it) },
                                onCreate = { weekday, start -> sheet = TimetableSheet.Editor(weekday = weekday, start = start) },
                            )
                        }
                        if (editing || weekColumns) timeline(days)
                        else AnimatedContent(day, transitionSpec = { Transitions.sharedAxisX(selection.dir, reduced) }, label = "ttGrid") { d -> timeline(listOf(d)) }
                    }
                }
            }
        }
    }

    if (picking) DatePickerSheet(day, onDismiss = { picking = false }, onPick = { select(it) })
    TimetableSheetHost(sheet, onChange = { sheet = it })
}

/** Lunedì–venerdì della settimana scelta, più sabato e domenica se scelti o con eventi. */
private fun weekDays(store: TimetableStore, selected: LocalDate): List<LocalDate> {
    val monday = Dates.startOfWeek(selected)
    return (0..6).map { monday.plusDays(it.toLong()) }.filterIndexed { i, d -> i < 5 || d == selected || store.entriesOn(d).isNotEmpty() }
}

/** Applica la bozza: se c'è già un orario chiede se sostituirlo o aggiungere solo dove è libero. */
private fun applyDraft(ui: com.bcw.classeviva.ui.shell.UiController, store: TimetableStore) {
    if (store.isEmpty) {
        store.applyDraft(true)
        return
    }
    ui.ask(com.bcw.classeviva.ui.shell.ConfirmRequest(
        title = "Hai già un orario",
        message = "Sostituendolo, eventi, note, file e verifiche segnate a mano dell'orario attuale verranno eliminati.",
        confirm = com.bcw.classeviva.ui.shell.DialogAction("Sostituisci l'orario attuale", destructive = true) { store.applyDraft(true) },
        extra = listOf(com.bcw.classeviva.ui.shell.DialogAction("Aggiungi solo dove è libero") { store.applyDraft(false) }),
    ))
}

// MARK: - Pulsanti

/** Pulsante con un'icona e il suo nome in un suggerimento (tenendo premuto). */
@Composable
fun TooltipIconButton(label: String, icon: String, enabled: Boolean = true, onClick: () -> Unit) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Below),
        tooltip = { PlainTooltip { Text(label) } },
        state = rememberTooltipState(),
    ) {
        IconButton(onClick = onClick, enabled = enabled) { Icon(icon, contentDescription = label) }
    }
}

/** Menu del pulsante + (M3 Expressive): genera, modifica, nuovo intervallo, nuovo evento. */
@Composable
private fun TimetableFabMenu(
    expanded: Boolean, onExpandedChange: (Boolean) -> Unit, modifier: Modifier,
    onGenerate: () -> Unit, onEdit: () -> Unit, onNewBreak: () -> Unit, onNew: () -> Unit,
) {
    BackHandler(enabled = expanded) { onExpandedChange(false) }
    val items = listOf(
        Triple("Genera dalle lezioni", "wand", onGenerate),
        Triple("Modifica orario", "edit", onEdit),
        Triple("Nuovo intervallo", "coffee", onNewBreak),
        Triple("Nuovo evento", "plus", onNew),
    )
    FloatingActionButtonMenu(
        expanded = expanded,
        modifier = modifier,
        button = {
            ToggleFloatingActionButton(checked = expanded, onCheckedChange = onExpandedChange) {
                val progress = checkedProgress
                Icon(if (progress > 0.5f) "x" else "plus", contentDescription = if (expanded) "Chiudi il menu" else "Aggiungi o modifica",
                    modifier = with(ToggleFloatingActionButtonDefaults) { Modifier.animateIcon({ checkedProgress }) })
            }
        },
    ) {
        for ((label, icon, run) in items) {
            FloatingActionButtonMenuItem(
                onClick = { onExpandedChange(false); run() },
                icon = { Icon(icon) },
                text = { Text(label) },
            )
        }
    }
}

/**
 * Barra degli strumenti della modifica (vivace, al posto della barra di navigazione):
 * allineamento, spostamento degli orari, nuovo intervallo e "Fine".
 */
@Composable
private fun EditToolbar(store: TimetableStore, onAlign: (TTAlignment) -> Unit, onShift: () -> Unit, onNewBreak: () -> Unit, onDone: () -> Unit) {
    HorizontalFloatingToolbar(expanded = true, colors = FloatingToolbarDefaults.vibrantFloatingToolbarColors()) {
        for (a in TTAlignment.entries) {
            TooltipBox(
                positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
                tooltip = { PlainTooltip { Text(a.title) } },
                state = rememberTooltipState(),
            ) {
                // L'allineamento scelto è pieno nel primario, come nella versione web.
                FilledIconToggleButton(
                    checked = store.alignment == a, onCheckedChange = { onAlign(a) },
                    colors = IconButtonDefaults.filledIconToggleButtonColors(
                        containerColor = Color.Transparent, contentColor = Theme.scheme.onPrimaryContainer,
                        checkedContainerColor = Theme.scheme.primary, checkedContentColor = Theme.scheme.onPrimary,
                    ),
                ) { Icon(a.icon, contentDescription = a.title) }
            }
        }
        VerticalDivider(Modifier.height(24.dp).padding(horizontal = 4.dp).align(Alignment.CenterVertically), color = Theme.scheme.onPrimaryContainer.copy(alpha = 0.25f))
        ToolbarButton("Sposta gli orari", "schedule", enabled = !store.isEmpty, onClick = onShift)
        ToolbarButton("Nuovo intervallo", "coffee", onClick = onNewBreak)
        Button(onClick = onDone, contentPadding = ButtonDefaults.SmallContentPadding, modifier = Modifier.padding(start = 4.dp).align(Alignment.CenterVertically)) {
            Icon("check", size = 18.dp)
            Text("Fine", modifier = Modifier.padding(start = 6.dp))
        }
    }
}

/** Barra dell'anteprima della bozza (vivace, al posto della barra di navigazione): rigenera, scarta, applica. */
@Composable
private fun DraftToolbar(onGenerate: () -> Unit, onDiscard: () -> Unit, onApply: () -> Unit) {
    HorizontalFloatingToolbar(expanded = true, colors = FloatingToolbarDefaults.vibrantFloatingToolbarColors()) {
        ToolbarButton("Cambia le impostazioni e rigenera", "tune", onClick = onGenerate)
        ToolbarButton("Scarta l'anteprima", "trash", onClick = onDiscard)
        VerticalDivider(Modifier.height(24.dp).padding(horizontal = 4.dp).align(Alignment.CenterVertically), color = Theme.scheme.onPrimaryContainer.copy(alpha = 0.25f))
        Button(onClick = onApply, contentPadding = ButtonDefaults.SmallContentPadding, modifier = Modifier.padding(start = 4.dp).align(Alignment.CenterVertically)) {
            Icon("check", size = 18.dp)
            Text("Applica", modifier = Modifier.padding(start = 6.dp))
        }
    }
}

@Composable
private fun ToolbarButton(label: String, icon: String, enabled: Boolean = true, onClick: () -> Unit) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
        tooltip = { PlainTooltip { Text(label) } },
        state = rememberTooltipState(),
    ) {
        IconButton(onClick = onClick, enabled = enabled, colors = IconButtonDefaults.iconButtonVibrantColors()) { Icon(icon, contentDescription = label) }
    }
}

// MARK: - Barre: bozza, modifica, verifiche senza lezione

@Composable
private fun TimetableExtras(store: TimetableStore, editing: Boolean, unmatched: List<AgendaEvent>) {
    val draft = store.draft
    if (draft == null && !editing && unmatched.isEmpty()) return
    Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (draft != null) DraftHeader(store)
        if (editing) EditHint(store.alignment)
        if (unmatched.isNotEmpty()) UnmatchedCapsule(unmatched)
    }
}

/**
 * Sopra l'anteprima: da dove viene l'orario, con una forma che ruota lentamente, nel contenitore
 * terziario. Con ore fuori dalle mezz'ore dice che applicando l'orario passa all'allineamento libero.
 */
@Composable
private fun DraftHeader(store: TimetableStore) {
    val draft = store.draft ?: return
    val lessons = draft.entries.count { !it.isBreak }
    val reduced = Theme.reducedMotion
    val turn = rememberInfiniteTransition(label = "draftArt")
    val angle by turn.animateFloat(0f, if (reduced) 0f else 360f, infiniteRepeatable(tween(14_000, easing = LinearEasing), RepeatMode.Restart), label = "angle")
    val c = Theme.scheme.onTertiaryContainer
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(Theme.radii.xl)).background(Theme.scheme.tertiaryContainer).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Shaped("sunny", c.copy(alpha = 0.14f), 52.dp, rotation = angle) { Icon("wand", size = 24.dp, tint = c) }
        Column(Modifier.weight(1f)) {
            Text("Anteprima dell'orario", style = Theme.type.style(17, 600, rounded = true), color = c)
            val range = if (draft.from != null && draft.to != null) " (${Fmt.shortDay(draft.from)} – ${Fmt.shortDay(draft.to)})" else ""
            Text(
                "${plural(lessons, "1 lezione ricavata", "# lezioni ricavate")} da ${plural(draft.analyzedDays, "1 giorno", "# giorni")} di registro$range",
                style = Theme.type.style(12, lineHeight = 16.sp), color = c.copy(alpha = 0.85f), modifier = Modifier.padding(top = 2.dp),
            )
            if (store.draftNeedsFreeAlignment) {
                Text("Orari esatti: applicando, l'orario passa all'allineamento libero.", style = Theme.type.style(12, 600, lineHeight = 16.sp), color = c, modifier = Modifier.padding(top = 2.dp))
            }
        }
    }
}

/** Istruzioni della modifica (nel contenitore terziario): l'allineamento si sceglie nella barra degli strumenti. */
@Composable
private fun EditHint(alignment: TTAlignment) {
    val c = Theme.scheme.onTertiaryContainer
    val hint = if (alignment == TTAlignment.automatic) "Scatti di mezz'ora, eventi da 30 minuti o un'ora." else "Scatti di 5 minuti; orari esatti dall'evento."
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(Theme.radii.li)).background(Theme.scheme.tertiaryContainer).padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(alignment.icon, size = 20.dp, tint = c)
        Column {
            Text(alignment.title, style = Theme.type.style(14, 650), color = c)
            Text(
                "Tieni premuto e trascina per spostare, trascina il bordo in basso per la durata, tocca due volte un'ora vuota per aggiungere. $hint",
                style = Theme.type.style(12, lineHeight = 16.sp), color = c.copy(alpha = 0.85f), modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

private fun unmatchedText(tests: List<AgendaEvent>) =
    if (tests.size == 1) "1 verifica senza lezione in orario: ${tests[0].title}" else "${tests.size} verifiche senza lezione in orario"

@Composable
private fun UnmatchedCapsule(tests: List<AgendaEvent>) {
    val fair = Theme.colors.fair
    Row(
        Modifier.clip(RoundedCornerShape(20.dp)).background(fair.container).padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon("alertTriangle", size = 16.dp, tint = fair.onContainer)
        Text(unmatchedText(tests), style = Theme.type.style(13, 550), color = fair.onContainer, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

// MARK: - Lista della giornata

/** Chip del giorno: lezioni, orario, verifiche, vacanza. */
@Composable
private fun dayChips(content: DayContent): List<@Composable () -> Unit> {
    val model = LocalModel.current
    val prefs = model.preferences
    val lessons = content.entries.count { !it.isBreak }
    val span = content.layout.span
    return buildList {
        if (lessons > 0) add { StatusChip(if (lessons == 1) "1 lezione" else "$lessons lezioni", "bookText") }
        if (span != null) add { StatusChip("${TimeOfDay.string(span.first)}–${TimeOfDay.string(span.last)}", "schedule", Tone.surface) }
        if (prefs.timetableShowsTests && content.testCount > 0) add { StatusChip(if (content.testCount == 1) "1 verifica" else "${content.testCount} verifiche", "test", Tone.tertiary) }
        if (model.calendarStatus(content.day)?.isHoliday == true) add { StatusChip("Vacanza", "beach", Tone.good) }
    }
}

@Composable
private fun DayList(
    content: DayContent, now: LocalDateTime, jump: DayJump?,
    onOpen: (TimetableEntry) -> Unit, onEdit: (TimetableEntry) -> Unit, onSeries: (Int) -> Unit, onNew: () -> Unit, onGenerate: () -> Unit,
) {
    val model = LocalModel.current
    val store = model.timetable
    val prefs = model.preferences
    val day = content.day
    val showsTests = prefs.timetableShowsTests
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        DayHero(day, jump, dayChips(content))
        if (Dates.isToday(day) && !content.isDraft && prefs.timetableShowsNowCard) NowCard(content, now, onOpen)
        if (showsTests && content.unmatchedTests.isNotEmpty() && !content.isDraft) {
            ListGroup(header = "Verifiche senza lezione in orario", background = Theme.colors.fair.container.copy(alpha = 0.45f).mix(Theme.scheme.surfaceContainer, 0.45f)) {
                for (t in content.unmatchedTests) item(t.id) { SegCell { RegisterTestRow(store, t, unmatched = true) } }
            }
        }
        if (content.entries.isEmpty()) {
            val text = if (store.isEmpty) "Aggiungi le materie a mano o crea una bozza dell'orario dalle lezioni registrate." else "Aggiungi un evento per questo giorno della settimana."
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(Theme.radii.xl)).background(Theme.scheme.surfaceContainerLow).padding(vertical = 12.dp)) {
                EmptyState("Nessuna lezione ${Weekday.withArticle(Weekday.of(day))}", if (Dates.isWeekend(day)) "sun" else "calendarPlus", text, actions = if (content.isDraft) null else ({
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton(onClick = onNew) { Icon("plus", size = 18.dp); Text("Aggiungi", Modifier.padding(start = 6.dp)) }
                        if (store.isEmpty) Button(onClick = onGenerate) { Icon("wand", size = 18.dp); Text("Genera", Modifier.padding(start = 6.dp)) }
                    }
                }))
            }
            return@Column
        }
        // Le lezioni di seguito (senza pause tra loro) formano una lista segmentata.
        val rows = mutableListOf<Any>()
        for (item in content.layout.listItems) {
            val last = rows.lastOrNull()
            if (item is ListItem.Group && last is MutableList<*> && item.cluster.start <= (last.last() as com.bcw.classeviva.data.timetable.Cluster).end) {
                @Suppress("UNCHECKED_CAST")
                (last as MutableList<com.bcw.classeviva.data.timetable.Cluster>).add(item.cluster)
            } else if (item is ListItem.Group) rows.add(mutableListOf(item.cluster))
            else rows.add(item)
        }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            for (row in rows) {
                when (row) {
                    is ListItem.Pause -> BreakRow(row.entry, content, onOpen, onEdit, onSeries)
                    is ListItem.Gap -> GapRow(row.end - row.start)
                    is MutableList<*> -> {
                        @Suppress("UNCHECKED_CAST")
                        val clusters = row as List<com.bcw.classeviva.data.timetable.Cluster>
                        val gap by animateDpAsState(if (content.isDraft) 6.dp else 2.dp, Theme.motion.slowSpatialSpec(), label = "runGap")
                        Column(verticalArrangement = Arrangement.spacedBy(gap)) {
                            clusters.forEachIndexed { index, cluster ->
                                val shape = runShape(index == 0, index == clusters.lastIndex, content.isDraft)
                                if (cluster.columns == 1) {
                                    BlockCard(cluster.blocks[0].block, content, now, compact = false, shape = shape, onOpen, onEdit, onSeries)
                                } else {
                                    // Eventi contemporanei: uno accanto all'altro.
                                    Row(Modifier.fillMaxWidth().clip(shape), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                        for (c in 0 until cluster.columns) {
                                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                                for (p in cluster.blocks.filter { it.column == c }) {
                                                    BlockCard(p.block, content, now, compact = true, shape = RoundedCornerShape(if (content.isDraft) Theme.radii.l else Theme.radii.xs), onOpen, onEdit, onSeries)
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

/** Angoli di un elemento di una serie di lezioni: grandi in cima e in fondo, piccoli in mezzo; in anteprima pillole staccate. */
@Composable
private fun runShape(first: Boolean, last: Boolean, draft: Boolean): Shape {
    val r = Theme.radii
    val big = if (draft) r.xl else r.li
    val small = if (draft) r.xl else r.xs
    val top by animateDpAsState(if (first) big else small, Theme.motion.slowSpatialSpec(), label = "top")
    val bottom by animateDpAsState(if (last) big else small, Theme.motion.slowSpatialSpec(), label = "bottom")
    return RoundedCornerShape(topStart = top, topEnd = top, bottomStart = bottom, bottomEnd = bottom)
}

/**
 * In cima all'orario di oggi: la lezione in corso (quanto manca, con l'avanzamento ondulato) e
 * quella dopo, oppure la prossima lezione con il tempo che manca. Le ore uguali di seguito contano
 * come una lezione sola. Toccandola si apre l'ora in corso del gruppo.
 */
@Composable
private fun NowCard(content: DayContent, now: LocalDateTime, onOpen: (TimetableEntry) -> Unit) {
    val minute = Dates.minuteOfDay(now)
    val lessons = timetableLayout(content.entries, splitAtBreaks = true).clusters.flatMap { c -> c.blocks.map { it.block } }.sortedBy { it.start }
    val current = lessons.lastOrNull { it.start <= minute && minute < it.end }
    val next = lessons.firstOrNull { it.start >= (current?.end ?: minute) && it.id != current?.id && it.start > minute }
    val shown = current ?: next ?: return
    val title = shown.entries.first().displayTitle
    val detail = buildList {
        add(if (current != null) "Fino alle ${TimeOfDay.string(shown.end)} · ancora ${TimeOfDay.duration(shown.end - minute)}" else "Alle ${TimeOfDay.string(shown.start)}")
        shown.entries.firstOrNull { it.room.isNotEmpty() }?.room?.let { add(it) }
    }.joinToString(" · ")
    val opened = shown.entries.lastOrNull { it.start <= minute } ?: shown.entries.first()
    val c = Theme.scheme.onPrimaryContainer
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val radius by animateDpAsState(if (pressed) Theme.radii.l else Theme.radii.xl, Theme.motion.fastSpatialSpec(), label = "nowRadius")
    WithContentColor(c) {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(radius)).background(Theme.scheme.primaryContainer)
                .combinedClickable(interactionSource = interaction, indication = androidx.compose.material3.ripple(), onClick = { onOpen(opened) })
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        if (current != null) "ADESSO" else "TRA ${TimeOfDay.duration(shown.start - minute).uppercase(Dates.italian)}",
                        style = Theme.type.style(12, 750, tracking = 0.8), color = c.copy(alpha = 0.8f),
                    )
                    Text(title, style = Theme.type.style(26, 500, rounded = true, lineHeight = 32.sp), color = c, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(detail, style = Theme.type.style(14), color = c.copy(alpha = 0.85f))
                }
                Shaped(if (current != null) "cookie12Sided" else "sunny", c.copy(alpha = 0.12f), 56.dp) {
                    Icon(if (current != null) "timetableFill" else "schedule", size = 26.dp, tint = c)
                }
            }
            if (current != null) WavyProgress((minute - shown.start).toFloat() / (shown.end - shown.start), Modifier.fillMaxWidth(), color = c, trackColor = c.copy(alpha = 0.22f))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (current != null && next != null) {
                    Row(
                        Modifier.weight(1f, fill = false).height(36.dp).clip(RoundedCornerShape(18.dp)).background(c.copy(alpha = 0.08f)).padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon("chevronRight", size = 18.dp, tint = c)
                        Text("Poi ${next.entries.first().displayTitle} alle ${TimeOfDay.string(next.start)}", style = Theme.type.style(14, 600), color = c, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                Spacer(Modifier.weight(1f))
                LiveUpdateToggle(c)
            }
        }
    }
}

/**
 * Porta la lezione in corso nelle notifiche e nella schermata di blocco, o la toglie, solo per
 * oggi (come il lucchetto dell'attività in diretta dell'app iOS).
 */
@Composable
private fun LiveUpdateToggle(color: Color) {
    val context = LocalContext.current
    val withPermission = rememberNotificationPermission()
    val on = LiveUpdates.shownDay == Dates.today()
    val label = if (on) "Togli dalla schermata di blocco" else "Mostra nella schermata di blocco"
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
        tooltip = { PlainTooltip { Text(label) } },
        state = rememberTooltipState(),
    ) {
        FilledIconToggleButton(
            checked = on,
            onCheckedChange = { checked -> if (checked) withPermission { LiveUpdates.setToday(context, true) } else LiveUpdates.setToday(context, false) },
            colors = IconButtonDefaults.filledIconToggleButtonColors(
                containerColor = color.copy(alpha = 0.12f), contentColor = color,
                checkedContainerColor = color, checkedContentColor = Theme.scheme.primaryContainer,
            ),
        ) { Icon("lockClock", contentDescription = label) }
    }
}

/** Menu tenendo premuto un evento: modifica, duplica, colora la giornata, elimina. */
@Composable
private fun EntryMenu(expanded: Boolean, onDismiss: () -> Unit, entry: TimetableEntry, onEdit: (TimetableEntry) -> Unit, onSeries: (Int) -> Unit) {
    val ui = LocalUi.current
    val store = LocalModel.current.timetable
    BCWMenu(expanded, onDismiss, listOf(
        MenuItem("Modifica", "edit") { onEdit(entry) },
        MenuItem("Duplica", "duplicate") { store.duplicate(entry) },
        MenuItem("Colora la giornata…", "palette") { onSeries(entry.weekday) },
        null,
        MenuItem("Elimina", "trash", destructive = true) { deleteEntryDialog(ui, store, entry) },
    ))
}

/**
 * Uno o più eventi uguali accostati: un blocco unico, diviso in righe. La barretta della tinta
 * arriva al bordo tra un'ora e l'altra, così le ore formano una barra a segmenti; ogni ora ha il
 * suo orario a sinistra. In anteprima il blocco è una pillola più piena nella tinta.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BlockCard(
    block: EntryBlock, content: DayContent, now: LocalDateTime, compact: Boolean, shape: Shape,
    onOpen: (TimetableEntry) -> Unit, onEdit: (TimetableEntry) -> Unit, onSeries: (Int) -> Unit,
) {
    val model = LocalModel.current
    val store = model.timetable
    val haptics = LocalHapticFeedback.current
    val showsTests = model.preferences.timetableShowsTests
    val minute = if (Dates.isToday(content.day) && !content.isDraft) Dates.minuteOfDay(now) else null
    val tint = entryColor(block.entries.first())
    val bg = tint.mix(Theme.scheme.surfaceContainerLow, if (content.isDraft) 0.28f else 0.18f)
    val count = block.entries.size
    Column(Modifier.fillMaxWidth().clip(shape).background(bg)) {
        block.entries.forEachIndexed { index, entry ->
            var menu by remember { mutableStateOf(false) }
            val badge = content.badges[entry.id]
            val isTest = showsTests && badge?.isTest == true
            val rowTint = entryColor(entry)
            val ongoing = minute != null && minute >= entry.start && minute < entry.end
            val details = buildList {
                if (entry.teacher.isNotEmpty()) add(entry.teacher)
                if (entry.room.isNotEmpty()) add(entry.room)
                if (!compact && entry.duration != 60) add(TimeOfDay.duration(entry.duration))
            }
            val barWidth by animateDpAsState(if (content.isDraft) 10.dp else 4.dp, Theme.motion.slowSpatialSpec(), label = "bar")
            Box {
                Row(
                    Modifier.fillMaxWidth().height(IntrinsicSize.Min)
                        .combinedClickable(
                            onClick = { onOpen(entry) },
                            onLongClick = if (content.isDraft) null else ({ haptics.performHapticFeedback(HapticFeedbackType.LongPress); menu = true }),
                        )
                        .padding(start = if (compact) 10.dp else 12.dp, end = if (compact) 10.dp else 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 12.dp),
                ) {
                    val vertical = if (compact) 10.dp else 12.dp
                    if (!compact) {
                        Column(Modifier.width(46.dp).padding(top = vertical + 1.dp, bottom = vertical), horizontalAlignment = Alignment.End) {
                            Text(TimeOfDay.string(entry.start), style = Theme.type.numeral(15, 650), color = Theme.scheme.onSurface)
                            Text(TimeOfDay.string(entry.end), style = Theme.type.numeral(12, 400), color = Theme.scheme.onSurfaceVariant)
                        }
                    }
                    // La barretta arriva a 2 dp dal bordo tra un'ora e l'altra del gruppo.
                    val join = 2.dp
                    if (content.isDraft) {
                        WavyBar(rowTint, Modifier.width(barWidth).fillMaxHeight().padding(top = if (index > 0) join else vertical, bottom = if (index < count - 1) join else vertical))
                    } else {
                        Box(Modifier.width(barWidth).fillMaxHeight().padding(top = if (index > 0) join else vertical, bottom = if (index < count - 1) join else vertical).clip(RoundedCornerShape(2.dp)).background(rowTint))
                    }
                    Column(Modifier.weight(1f).padding(vertical = vertical), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        if (compact) {
                            Text(entry.timeRange, style = Theme.type.numeral(12, 400), color = Theme.scheme.onSurfaceVariant)
                            Text(entry.displayTitle, style = Theme.type.style(14, 600, lineHeight = 19.sp), color = Theme.scheme.onSurface.copy(alpha = if (index > 0) 0.55f else 1f), maxLines = 3, overflow = TextOverflow.Ellipsis)
                            if (isTest) Pill("Verifica", small = true, bold = true)
                            if (entry.teacher.isNotEmpty() || entry.room.isNotEmpty()) {
                                Text(listOf(entry.teacher, entry.room).filter { it.isNotEmpty() }.joinToString(" · "), style = Theme.type.style(12), color = Theme.scheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                        } else {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(entry.displayTitle, style = Theme.type.style(16, 600, lineHeight = 22.sp), color = Theme.scheme.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                if (ongoing) Pill("In corso", color = rowTint, filled = true, bold = true)
                                if (isTest) Pill("Verifica", bold = true)
                            }
                            if (details.isNotEmpty()) Text(details.joinToString(" · "), style = Theme.type.style(12), color = Theme.scheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        if (badge != null) BadgesLine(badge.hasNote, badge.attachmentCount)
                    }
                }
                EntryMenu(menu, { menu = false }, entry, onEdit, onSeries)
            }
        }
    }
}

/** Barretta ondulata della bozza (al posto di quella piena). */
@Composable
private fun WavyBar(color: Color, modifier: Modifier) {
    androidx.compose.foundation.Canvas(modifier) {
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
private fun BadgesLine(hasNote: Boolean, attachments: Int) {
    if (!hasNote && attachments == 0) return
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        val c = Theme.scheme.onSurfaceVariant
        if (hasNote) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            Icon("note", size = 14.dp, tint = c); Text("Nota", style = Theme.type.style(12), color = c)
        }
        if (attachments > 0) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            Icon("paperclip", size = 14.dp, tint = c); Text(attachments.toString(), style = Theme.type.numeral(12, 400), color = c)
        }
    }
}

/** Intervallo nella lista: una pillola con l'orario tra due linee ondulate, nello sfondo. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BreakRow(entry: TimetableEntry, content: DayContent, onOpen: (TimetableEntry) -> Unit, onEdit: (TimetableEntry) -> Unit, onSeries: (Int) -> Unit) {
    val haptics = LocalHapticFeedback.current
    var menu by remember { mutableStateOf(false) }
    val tint = entryColor(entry)
    Box {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(Theme.radii.m))
                .combinedClickable(onClick = { onOpen(entry) }, onLongClick = if (content.isDraft) null else ({ haptics.performHapticFeedback(HapticFeedbackType.LongPress); menu = true }))
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            val line = Theme.scheme.outlineVariant.copy(alpha = if (content.isDraft) 0.75f else 1f)
            WavyLine(line, Modifier.weight(1f))
            Row(
                Modifier.height(30.dp).clip(RoundedCornerShape(15.dp)).background(tint.mix(Theme.scheme.surfaceContainerHigh, 0.2f)).padding(start = 10.dp, end = 12.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon("coffeeFill", size = 16.dp, tint = tint)
                Text("${entry.displayTitle} · ${entry.timeRange}", style = Theme.type.numeral(12, 600), color = Theme.scheme.onSurfaceVariant, maxLines = 1)
            }
            WavyLine(line, Modifier.weight(1f))
        }
        EntryMenu(menu, { menu = false }, entry, onEdit, onSeries)
    }
}

@Composable
private fun GapRow(minutes: Int) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        HorizontalDivider(Modifier.weight(1f), color = Theme.scheme.outlineVariant)
        Text("Libero · ${TimeOfDay.duration(minutes)}", style = Theme.type.style(12), color = Theme.scheme.onSurfaceVariant)
        HorizontalDivider(Modifier.weight(1f), color = Theme.scheme.outlineVariant)
    }
}

/** Verifica di Classeviva su una lezione (o senza lezione in orario), con Mostra/Nascondi. */
@Composable
fun RegisterTestRow(store: TimetableStore, test: AgendaEvent, unmatched: Boolean = false, hidden: Boolean = false) {
    val reason = if (hidden) store.hiddenReason(test, unmatched) else null
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon("test", tint = if (hidden) Theme.scheme.onSurfaceVariant else Theme.scheme.primary, modifier = Modifier.padding(top = 2.dp))
        Column(Modifier.weight(1f)) {
            Text(test.title, style = Theme.type.style(15, 600), color = Theme.scheme.onSurface)
            Text(
                test.notes.ifEmpty { "Verifica" }, style = Theme.type.style(14, lineHeight = 20.sp),
                color = if (hidden) Theme.scheme.onSurfaceVariant else Theme.scheme.onSurface,
                textDecoration = if (hidden) androidx.compose.ui.text.style.TextDecoration.LineThrough else null,
            )
            if (test.authorName.isNotEmpty()) RowCaption("Segnata da ${test.authorName} su Classeviva")
            reason?.let { RowCaption(it.description) }
        }
        TextButton(onClick = { store.setTestHidden(!hidden, test, unmatched) }, contentPadding = ButtonDefaults.TextButtonWithIconContentPadding) {
            Text(if (hidden) "Mostra" else "Nascondi")
        }
    }
}
