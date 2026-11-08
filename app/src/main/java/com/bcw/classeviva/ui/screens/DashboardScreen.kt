package com.bcw.classeviva.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bcw.classeviva.data.AgendaKind
import com.bcw.classeviva.data.DashboardMode
import com.bcw.classeviva.data.TitleAbbreviation
import com.bcw.classeviva.data.accountColorID
import com.bcw.classeviva.util.Dates
import com.bcw.classeviva.util.Fmt
import com.bcw.classeviva.util.plural
import com.bcw.classeviva.ui.components.AbsenceRow
import com.bcw.classeviva.ui.components.AgendaCell
import com.bcw.classeviva.ui.components.Avatar
import com.bcw.classeviva.ui.components.BCWMenu
import com.bcw.classeviva.ui.components.CalendarBar
import com.bcw.classeviva.ui.components.Chevron
import com.bcw.classeviva.ui.components.Collapsible
import com.bcw.classeviva.ui.components.DateBadge
import com.bcw.classeviva.ui.components.DatePickerSheet
import com.bcw.classeviva.ui.components.DayHero
import com.bcw.classeviva.ui.components.DayJump
import com.bcw.classeviva.ui.components.LessonRow
import com.bcw.classeviva.ui.components.ListGroup
import com.bcw.classeviva.ui.components.LoadingCard
import com.bcw.classeviva.ui.components.MenuItem
import com.bcw.classeviva.ui.components.SegCell
import com.bcw.classeviva.ui.components.SegmentedSection
import com.bcw.classeviva.ui.components.Shaped
import com.bcw.classeviva.ui.components.StatusBanner
import com.bcw.classeviva.ui.components.StatusChip
import com.bcw.classeviva.ui.components.Tone
import com.bcw.classeviva.ui.components.subjectColor
import com.bcw.classeviva.ui.icons.Icon
import com.bcw.classeviva.ui.shell.HidesNavigationBar
import com.bcw.classeviva.ui.shell.LocalModel
import com.bcw.classeviva.ui.shell.LocalNavigator
import com.bcw.classeviva.ui.shell.LocalUi
import com.bcw.classeviva.ui.shell.PageScaffold
import com.bcw.classeviva.ui.shell.Transitions
import com.bcw.classeviva.ui.shell.block
import com.bcw.classeviva.ui.theme.Theme
import java.time.LocalDate

/** Stato del giorno scelto con il verso dell'ultimo cambio (per l'asse condiviso). */
class DaySelection(initial: LocalDate) {
    var day by mutableStateOf(initial)
        private set
    var dir by mutableIntStateOf(0)
        private set

    fun select(target: LocalDate): Boolean {
        if (target == day) return false
        dir = if (target > day) 1 else -1
        day = target
        return true
    }
}

@Composable
fun rememberDaySelection(initial: () -> LocalDate): DaySelection {
    var saved by rememberSaveable { mutableStateOf(initial().toEpochDay()) }
    val selection = remember { DaySelection(LocalDate.ofEpochDay(saved)) }
    LaunchedEffect(selection.day) { saved = selection.day.toEpochDay() }
    return selection
}

/**
 * Scorrere di lato sul contenuto cambia giorno (non sopra elementi che scorrono di lato). Il gesto
 * usa sempre l'ultimo `onSwipe`, che calcola dal giorno scelto in quel momento.
 */
@Composable
fun Modifier.daySwipe(onSwipe: (Int) -> Unit): Modifier {
    val current by rememberUpdatedState(onSwipe)
    return pointerInput(Unit) {
        var total = 0f
        detectHorizontalDragGestures(
            onDragStart = { total = 0f },
            onDragEnd = {
                if (kotlin.math.abs(total) > 90) current(if (total < 0) 1 else -1)
                total = 0f
            },
            onDragCancel = { total = 0f },
        ) { change, amount ->
            total += amount
            change.consume()
        }
    }
}

/**
 * Dashboard: compiti, verifiche ed eventi del giorno scelto (di default domani), con la settimana o
 * il mese, le presenze e, per i giorni passati, le lezioni in una sezione separata. In alto la
 * barra di ricerca. Scorrendo di lato si passa al giorno prima o dopo.
 */
@Composable
fun DashboardScreen() {
    val nav = LocalNavigator.current
    val reduced = Theme.reducedMotion
    AnimatedContent(nav.searching, transitionSpec = { Transitions.fadeThrough(reduced) }, label = "search") { searching ->
        if (searching) {
            HidesNavigationBar()
            SearchScreen(onClose = { nav.searching = false })
        } else DashboardContent()
    }
}

@Composable
private fun DashboardContent() {
    val model = LocalModel.current
    val nav = LocalNavigator.current
    val prefs = model.preferences
    val haptics = LocalHapticFeedback.current
    val selection = rememberDaySelection { Dates.today().plusDays(1) }
    val mode = prefs.dashboardMode
    val month = mode == DashboardMode.calendar
    var picking by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(setOf<String>()) }
    var collapsedDays by remember { mutableStateOf(setOf<LocalDate>()) }
    var menu by remember { mutableStateOf(false) }

    fun select(day: LocalDate) {
        if (selection.select(day)) {
            haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
            collapsedDays = emptySet()
        }
    }

    // Collegamento da un widget: la Dashboard su quel giorno.
    LaunchedEffect(model.openedLink) {
        val link = model.openedLink ?: return@LaunchedEffect
        if (link.tab == "dashboard" || (link.tab == "timetable" && !prefs.showsTimetableTab)) link.day?.let { select(it) }
    }

    val day = selection.day
    val reduced = Theme.reducedMotion
    PageScaffold(
        title = "Dashboard",
        topBar = {
            SearchAppBar(
                modeIcon = mode.icon,
                onSearch = { nav.searching = true },
                onMenu = { menu = true },
                menu = {
                    BCWMenu(menu, { menu = false }, buildList {
                        for (m in DashboardMode.entries) add(MenuItem(m.title, m.icon, checked = mode == m) { prefs.set("dashboardMode", m.name) })
                        add(null)
                        add(MenuItem("Vai a oggi", "sun") { select(Dates.today()) })
                        add(MenuItem("Nascondi compiti fatti", "checkCircle", checked = prefs.hideCompletedHomework) { prefs.set("hideCompletedHomework", !prefs.hideCompletedHomework) })
                        if (mode == DashboardMode.list) add(MenuItem("Mostra i prossimi giorni", "timeline", checked = prefs.showUpcomingDays) { prefs.set("showUpcomingDays", !prefs.showUpcomingDays) })
                    }, sections = mapOf(0 to "Vista"))
                },
            )
        },
        fixed = {
            CalendarBar(
                model, day, month, onSelect = ::select,
                onShift = { delta ->
                    if (month) {
                        val target = day.plusMonths(delta.toLong())
                        select(target.withDayOfMonth(minOf(day.dayOfMonth, target.lengthOfMonth())))
                    } else select(day.plusDays(7L * delta))
                },
                onPick = { picking = true },
            )
        },
        onRefresh = { model.refreshAll() },
        onReselect = { select(Dates.today().plusDays(1)) },
        listModifier = Modifier.daySwipe { select(day.plusDays(it.toLong())) },
        gap = 20.dp,
    ) {
        model.lastError?.let { error -> block("banner") { StatusBanner(error, if (model.isOffline) "wifiOff" else "alertTriangle") } }
        block("day") {
            AnimatedContent(day, transitionSpec = { Transitions.sharedAxisX(selection.dir, reduced) }, label = "day") { shown ->
                DayDetail(shown, jump = if (Dates.isTomorrow(shown)) null else DayJump("Domani") { select(Dates.today().plusDays(1)) },
                    expanded = expanded, onExpandedChange = { expanded = it })
            }
        }
        if (prefs.showUpcomingDays && mode == DashboardMode.list) {
            block("upcoming") {
                UpcomingDays(day, collapsedDays, { collapsedDays = it }, expanded, { expanded = it }, onSelect = ::select)
            }
        }
    }
    if (picking) DatePickerSheet(day, onDismiss = { picking = false }, onPick = { select(it) })
}

/** Barra di ricerca in cima alla Dashboard, con il menu della vista e l'avatar dell'account. */
@Composable
fun SearchAppBar(modeIcon: String, onSearch: () -> Unit, onMenu: () -> Unit, menu: @Composable () -> Unit) {
    val model = LocalModel.current
    val ui = LocalUi.current
    Row(
        Modifier.fillMaxWidth().background(Theme.scheme.surface).windowInsetsPadding(WindowInsets.statusBars).padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier.weight(1f).height(56.dp).clip(RoundedCornerShape(28.dp)).background(Theme.scheme.surfaceContainerHigh).clickable(onClick = onSearch).padding(start = 16.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon("search", tint = Theme.scheme.onSurfaceVariant)
            Text("Cerca nel registro", style = Theme.type.style(16), color = Theme.scheme.onSurfaceVariant, modifier = Modifier.weight(1f).padding(start = 12.dp), maxLines = 1)
            Box {
                IconButton(onClick = onMenu) { Icon(modeIcon, contentDescription = "Vista e filtri") }
                menu()
            }
            Box(Modifier.size(40.dp).clip(RoundedCornerShape(20.dp)).clickable { ui.accountSwitcher = true }, contentAlignment = Alignment.Center) {
                val account = model.activeAccount
                Avatar(model.initials, 34.dp, color = if (model.isDemo || account == null) Theme.scheme.primary else subjectColor(accountColorID(account)), shape = "cookie9Sided")
            }
            Spacer(Modifier.size(4.dp))
        }
    }
}

/**
 * Tutto ciò che riguarda un giorno: in cima il giorno in grande con i chip di stato, sotto
 * verifiche ed eventi, compiti, presenze e (per i giorni passati) le lezioni svolte.
 */
@Composable
fun DayDetail(day: LocalDate, jump: DayJump?, expanded: Set<String>, onExpandedChange: (Set<String>) -> Unit) {
    val model = LocalModel.current
    val prefs = model.preferences
    val events = model.events(day).filter { !(prefs.hideCompletedHomework && prefs.isCompleted(it)) }
    val homework = events.filter { it.kind == AgendaKind.homework }
    val testsAndEvents = events.filter { it.kind != AgendaKind.homework }
        .sortedWith(compareBy({ if (it.kind == AgendaKind.test) 0 else 1 }, { it.begin }))
    val absences = model.absencesOn(day)
    val lessons = model.lessons(day)
    val isPastOrToday = !day.isAfter(Dates.today())
    val completed = homework.count { prefs.isCompleted(it) }

    if (isPastOrToday) {
        // Anche dopo ogni aggiornamento completo, che azzera le lezioni già scaricate.
        val week = Dates.startOfWeek(day)
        LaunchedEffect(week, model.lastUpdated, model.client) { model.ensureLessons(week, week.plusDays(6)) }
    }

    val chips = buildList<@Composable () -> Unit> {
        val tests = events.count { it.kind == AgendaKind.test }
        if (tests > 0) add { StatusChip(if (tests == 1) "1 verifica" else "$tests verifiche", "test", Tone.tertiary) }
        if (homework.isNotEmpty()) {
            val done = completed == homework.size
            add { StatusChip(if (done) "Compiti fatti" else if (homework.size == 1) "1 compito" else "${homework.size} compiti", if (done) "checkCircle" else "book") }
        }
        for (kind in absences.map { it.kind }.distinct().sortedBy { it.name }) add { StatusChip(kind.title, "userClock", Tone.poor) }
        if (isEmpty()) {
            if (model.calendarStatus(day)?.isHoliday == true) add { StatusChip("Vacanza", "beach", Tone.good) }
            else if (Dates.isWeekend(day)) add { StatusChip("Fine settimana", "sun", Tone.good) }
        }
        if (isPastOrToday && lessons.isNotEmpty()) {
            val hours = lessons.sumOf { it.duration.toInt() }
            add { StatusChip(if (hours == 1) "1 ora di lezione" else "$hours ore di lezione", "bookText", Tone.surface) }
        }
    }
    val hasAgenda = homework.isNotEmpty() || testsAndEvents.isNotEmpty() || absences.isNotEmpty()
    val showEmpty = !hasAgenda && (lessons.isEmpty() || !isPastOrToday)
    val abbreviation = prefs.titleAbbreviation

    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        DayHero(day, jump, chips, abbreviate = abbreviation == TitleAbbreviation.always)
        if (showEmpty) EmptyDay(Dates.isWeekend(day))
        if (testsAndEvents.isNotEmpty()) {
            SegmentedSection("Verifiche ed eventi", "test", prefs.isCollapsed("testsAndEvents"), { prefs.toggleCollapsed("testsAndEvents") },
                color = Theme.scheme.tertiary, trailing = testsAndEvents.size.toString()) {
                for (e in testsAndEvents) item(e.id) { AgendaCell(e, expanded, onExpandedChange) }
            }
        }
        if (homework.isNotEmpty()) {
            SegmentedSection("Compiti", "book", prefs.isCollapsed("homework"), { prefs.toggleCollapsed("homework") },
                trailing = "$completed/${homework.size}", progress = completed.toFloat() / homework.size) {
                for (e in homework) item(e.id) { AgendaCell(e, expanded, onExpandedChange) }
            }
        }
        if (absences.isNotEmpty()) {
            SegmentedSection("Presenze", "userClock", prefs.isCollapsed("attendance"), { prefs.toggleCollapsed("attendance") }, color = Theme.colors.poor.color) {
                for (a in absences) item(a.id) { SegCell { AbsenceRow(a) } }
            }
        }
        if (isPastOrToday) {
            if (lessons.isNotEmpty()) {
                val hours = lessons.sumOf { it.duration.toInt() }
                SegmentedSection("Lezioni", "bookText", prefs.isCollapsed("lessons"), { prefs.toggleCollapsed("lessons") },
                    color = Theme.scheme.secondary, trailing = plural(hours, "1 ora", "# ore")) {
                    for (l in lessons) item(l.id) { SegCell { LessonRow(l) } }
                }
            } else if (!model.hasLoadedLessons(day) && !Dates.isWeekend(day) && model.client != null) {
                LoadingCard("Carico le lezioni…")
            }
        }
    }
}

@Composable
private fun EmptyDay(weekend: Boolean) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(Theme.radii.xl)).background(Theme.scheme.surfaceContainerLow).padding(horizontal = 20.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Shaped(if (weekend) "sunny" else "cookie9Sided", Theme.scheme.tertiaryContainer, 88.dp, Modifier.padding(bottom = 10.dp)) {
            Icon(if (weekend) "sun" else "checkSeal", size = 40.dp, tint = Theme.scheme.onTertiaryContainer)
        }
        Text(if (weekend) "Goditi il weekend" else "Niente in programma", style = Theme.type.style(22, 500, rounded = true), color = Theme.scheme.onSurface, textAlign = TextAlign.Center)
        Text("Nessun compito, verifica o evento per questo giorno.", style = Theme.type.style(14), color = Theme.scheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

private fun summary(events: List<com.bcw.classeviva.data.AgendaEvent>): String {
    val tests = events.count { it.kind == AgendaKind.test }
    val homework = events.count { it.kind == AgendaKind.homework }
    val other = events.size - tests - homework
    return buildList {
        if (tests > 0) add(if (tests == 1) "1 verifica" else "$tests verifiche")
        if (homework > 0) add(if (homework == 1) "1 compito" else "$homework compiti")
        if (other > 0) add(if (other == 1) "1 evento" else "$other eventi")
    }.joinToString(" · ")
}

/**
 * "Nei prossimi giorni", come la vista Programma di Google Calendar: a sinistra la data (toccandola
 * si va al giorno), a destra il nome del giorno, che comprime gli elementi, e gli elementi.
 */
@Composable
private fun UpcomingDays(
    after: LocalDate, collapsedDays: Set<LocalDate>, onCollapsedDays: (Set<LocalDate>) -> Unit,
    expanded: Set<String>, onExpandedChange: (Set<String>) -> Unit, onSelect: (LocalDate) -> Unit,
) {
    val model = LocalModel.current
    val prefs = model.preferences
    val haptics = LocalHapticFeedback.current
    val start = after.plusDays(1).atStartOfDay()
    val end = after.plusDays(21).atStartOfDay()
    val days = model.agenda.filter { !it.begin.isBefore(start) && it.begin.isBefore(end) && !(prefs.hideCompletedHomework && prefs.isCompleted(it)) }
        .groupBy { it.day }.toSortedMap()
    if (days.isEmpty()) return
    val collapsed = prefs.isCollapsed("upcoming")
    Column {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(Theme.radii.m)).clickable { prefs.toggleCollapsed("upcoming") }.padding(horizontal = 4.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Nei prossimi giorni", style = Theme.type.style(22, 500, rounded = true), color = Theme.scheme.onSurface, modifier = Modifier.weight(1f))
            Chevron(collapsed)
        }
        Collapsible(!collapsed) {
            Spacer(Modifier.height(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                for ((day, events) in days) {
                    val closed = day in collapsedDays
                    val sorted = events.sortedBy { if (it.kind == AgendaKind.test) 0 else 1 }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        DateBadge(day, highlighted = events.any { it.kind == AgendaKind.test }, today = Dates.isToday(day),
                            modifier = Modifier.clip(RoundedCornerShape(Theme.radii.l)).clickable { onSelect(day) })
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                Modifier.fillMaxWidth().heightIn(min = 58.dp).clip(RoundedCornerShape(Theme.radii.m)).clickable {
                                    haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                    onCollapsedDays(if (closed) collapsedDays - day else collapsedDays + day)
                                },
                                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(Fmt.relativeDayName(day), style = Theme.type.style(16, 600), color = Theme.scheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(summary(events), style = Theme.type.style(13), color = Theme.scheme.onSurfaceVariant)
                                }
                                Chevron(closed)
                            }
                            Collapsible(!closed) {
                                ListGroup { for (e in sorted) item(e.id) { AgendaCell(e, expanded, onExpandedChange, scope = "up") } }
                            }
                        }
                    }
                }
            }
        }
    }
}
