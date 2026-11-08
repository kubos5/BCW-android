package com.bcw.classeviva.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bcw.classeviva.data.AbsenceKind
import com.bcw.classeviva.data.AgendaKind
import com.bcw.classeviva.data.AppModel
import com.bcw.classeviva.data.ArchiveModel
import com.bcw.classeviva.data.Lesson
import com.bcw.classeviva.data.NoteCategory
import com.bcw.classeviva.util.Dates
import com.bcw.classeviva.util.Fmt
import com.bcw.classeviva.util.GradeFormat
import com.bcw.classeviva.util.StableID
import com.bcw.classeviva.util.byName
import com.bcw.classeviva.util.containsText
import com.bcw.classeviva.util.plural
import com.bcw.classeviva.ui.components.AbsenceRow
import com.bcw.classeviva.ui.components.AgendaCell
import com.bcw.classeviva.ui.components.BCWFilterChip
import com.bcw.classeviva.ui.components.BCWMenu
import com.bcw.classeviva.ui.components.Card
import com.bcw.classeviva.ui.components.Cell
import com.bcw.classeviva.ui.components.Chevron
import com.bcw.classeviva.ui.components.ChevronRight
import com.bcw.classeviva.ui.components.ChipRow
import com.bcw.classeviva.ui.components.Collapsible
import com.bcw.classeviva.ui.components.ConnectedGroup
import com.bcw.classeviva.ui.components.DatePickerSheet
import com.bcw.classeviva.ui.components.EmptyState
import com.bcw.classeviva.ui.components.Eyebrow
import com.bcw.classeviva.ui.components.IconBadge
import com.bcw.classeviva.ui.components.LessonRow
import com.bcw.classeviva.ui.components.ListGroup
import com.bcw.classeviva.ui.components.ListRow
import com.bcw.classeviva.ui.components.LoadingCard
import com.bcw.classeviva.ui.components.MenuItem
import com.bcw.classeviva.ui.components.Pill
import com.bcw.classeviva.ui.components.SectionHeader
import com.bcw.classeviva.ui.components.SegCell
import com.bcw.classeviva.ui.components.StatTile
import com.bcw.classeviva.ui.components.StatusBanner
import com.bcw.classeviva.ui.components.SubjectTag
import com.bcw.classeviva.ui.components.WarningBox
import com.bcw.classeviva.ui.components.WavyProgress
import com.bcw.classeviva.ui.components.gradeTone
import com.bcw.classeviva.ui.components.subjectColor
import com.bcw.classeviva.ui.components.tone
import com.bcw.classeviva.ui.icons.Icon
import com.bcw.classeviva.ui.nav.Page
import com.bcw.classeviva.ui.shell.LocalModel
import com.bcw.classeviva.ui.shell.LocalNavigator
import com.bcw.classeviva.ui.shell.LocalUi
import com.bcw.classeviva.ui.shell.PageScaffold
import com.bcw.classeviva.ui.shell.block
import com.bcw.classeviva.ui.theme.Theme
import kotlinx.coroutines.launch
import java.time.LocalDate

// MARK: - Note

@Composable
fun noteTint(c: NoteCategory) = when (c) {
    NoteCategory.NTTE -> Theme.colors.neutral.color
    NoteCategory.NTCL, NoteCategory.NTST -> Theme.colors.poor.color
    NoteCategory.NTWN -> Theme.colors.fair.color
}

@Composable
fun NotesScreen() {
    val model = LocalModel.current
    var category by rememberSaveable { mutableStateOf<NoteCategory?>(null) }
    val list = model.notes.filter { category == null || it.category == category }
    // Le note si segnano come lette quando compaiono, come in Classeviva.
    LaunchedEffect(list.map { it.id to it.isRead }) { for (n in list) if (!n.isRead) launch { model.readNote(n) } }
    PageScaffold(title = "Note", onRefresh = { model.loadNotes() }) {
        block("summary") {
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                for ((i, c) in listOf(NoteCategory.NTTE, NoteCategory.NTCL).withIndex()) {
                    StatTile(c.title, model.notes.count { it.category == c }.toString(), c.icon, noteTint(c), if (i == 0) "cookie4Sided" else "clover4Leaf",
                        Modifier.weight(1f), corners = if (i == 0) "l" else "r", vertical = true)
                }
            }
        }
        block("chips") {
            ChipRow {
                BCWFilterChip("Tutte", category == null, { category = null })
                for (c in NoteCategory.entries) BCWFilterChip(c.title, category == c, { category = if (category == c) null else c }, icon = c.icon)
            }
        }
        if (list.isEmpty()) {
            block("empty") { EmptyState("Nessuna nota", "thumbsUp", "Continua così!") }
            return@PageScaffold
        }
        block("list") {
            ListGroup {
                for (n in list) item("${n.category}-${n.id}") {
                    SegCell {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(n.category.icon, size = 20.dp, tint = noteTint(n.category))
                                Text(n.category.singular, style = Theme.type.style(14, 650), color = noteTint(n.category), modifier = Modifier.padding(start = 8.dp).weight(1f))
                                if (!n.isRead) Pill("Nuova", filled = true, small = true)
                            }
                            SelectionContainer { Text(n.text.ifEmpty { "Tocca per leggere il contenuto." }, style = Theme.type.style(15, 400, lineHeight = androidx.compose.ui.unit.TextUnit.Unspecified), color = Theme.scheme.onSurface) }
                            Text("${n.authorName} · ${Fmt.longDay(n.date)}", style = Theme.type.style(12), color = Theme.scheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

// MARK: - Assenze e ritardi

@Composable
fun AbsencesScreen() {
    val model = LocalModel.current
    var kind by rememberSaveable { mutableStateOf<AbsenceKind?>(null) }
    var onlyPending by rememberSaveable { mutableStateOf(false) }
    val list = model.absences.filter { (kind == null || it.kind == kind || (kind == AbsenceKind.late && it.kind == AbsenceKind.shortLate)) && (!onlyPending || !it.isJustified) }
    fun count(vararg k: AbsenceKind) = model.absences.count { it.kind in k }
    val pending = model.unjustifiedAbsences.size
    PageScaffold(title = "Assenze e ritardi", onRefresh = { model.loadAbsences() }, gap = 18.dp) {
        block("counters") {
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                StatTile("Assenze", count(AbsenceKind.absence).toString(), AbsenceKind.absence.icon, Theme.colors.poor.color, "clover4Leaf", Modifier.weight(1f), corners = "l", vertical = true)
                StatTile("Ritardi", count(AbsenceKind.late, AbsenceKind.shortLate).toString(), AbsenceKind.late.icon, Theme.colors.fair.color, "sunny", Modifier.weight(1f), corners = "c", vertical = true)
                StatTile("Uscite", count(AbsenceKind.earlyExit).toString(), AbsenceKind.earlyExit.icon, Theme.colors.neutral.color, "cookie6Sided", Modifier.weight(1f), corners = "r", vertical = true)
            }
        }
        if (pending > 0) block("warning") { WarningBox(if (pending == 1) "Hai un evento da giustificare" else "Hai $pending eventi da giustificare") }
        block("chips") {
            ChipRow {
                BCWFilterChip("Da giustificare", onlyPending, { onlyPending = !onlyPending }, icon = "alertCircle")
                BCWFilterChip("Tutti", kind == null, { kind = null })
                for (k in listOf(AbsenceKind.absence, AbsenceKind.late, AbsenceKind.earlyExit)) BCWFilterChip(k.plural, kind == k, { kind = if (kind == k) null else k }, icon = k.icon)
            }
        }
        if (list.isEmpty()) {
            block("empty") { EmptyState("Nessun evento", "checkCircle", "Nessuna assenza, ritardo o uscita registrati.") }
            return@PageScaffold
        }
        for ((month, events) in list.groupBy { it.date.withDayOfMonth(1) }.toSortedMap(compareByDescending { it })) {
            block("m-$month") {
                ListGroup(header = Fmt.monthYear(month)) {
                    for (e in events.sortedByDescending { it.date }) item(e.id) {
                        SegCell {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                Column(Modifier.width(40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(e.date.dayOfMonth.toString(), style = Theme.type.numeral(20), color = Theme.scheme.onSurface)
                                    Text(Fmt.weekdayShort(e.date), style = Theme.type.style(11, 600), color = Theme.scheme.onSurfaceVariant)
                                }
                                AbsenceRow(e, showsReason = true, modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
    }
}

// MARK: - Agenda completa

/** Esporta l'agenda in un file .ics con gli avvisi la sera prima (Impostazioni › Notifiche). */
fun exportAgenda(model: AppModel, ui: com.bcw.classeviva.ui.shell.UiController) {
    val ics = com.bcw.classeviva.data.CalendarExport.makeICS(model.agenda, if (model.preferences.homeworkReminders) model.preferences.reminderHour else null, "BCW – ${model.displayName}")
    val f = model.temporaryFile("Agenda BCW.ics").apply { writeText(ics) }
    ui.share(f, "text/calendar")
}

@Composable
fun AgendaScreen() {
    val model = LocalModel.current
    val ui = LocalUi.current
    val prefs = model.preferences
    var kind by rememberSaveable { mutableStateOf<AgendaKind?>(null) }
    var showPast by rememberSaveable { mutableStateOf(false) }
    var onlyPending by rememberSaveable { mutableStateOf(false) }
    var search by rememberSaveable { mutableStateOf("") }
    var expanded by remember { mutableStateOf(setOf<String>()) }
    val today = Dates.today().atStartOfDay()
    val items = model.agenda.filter {
        (if (showPast) it.begin.isBefore(today) else !it.begin.isBefore(today)) && (kind == null || it.kind == kind) &&
            (!onlyPending || !prefs.isCompleted(it)) && (it.notes.containsText(search) || it.subjectName.containsText(search))
    }
    val days = items.groupBy { it.day }.let { if (showPast) it.toSortedMap(compareByDescending { d -> d }) else it.toSortedMap() }
    PageScaffold(
        title = "Agenda",
        actions = { IconButton(onClick = { exportAgenda(model, ui) }) { Icon("calendarPlus", contentDescription = "Esporta nel calendario") } },
        onRefresh = { model.loadAgenda() },
    ) {
        block("search") { LocalSearchField(search, "Cerca compiti ed eventi") { search = it } }
        block("past") {
            ConnectedGroup(listOf(false, true), showPast, { showPast = it }) { v, _ -> Text(if (v) "Passati" else "In arrivo") }
        }
        block("chips") {
            ChipRow {
                BCWFilterChip("Da fare", onlyPending, { onlyPending = !onlyPending }, icon = "circle")
                BCWFilterChip("Tutto", kind == null, { kind = null })
                for (k in AgendaKind.entries) BCWFilterChip(k.title, kind == k, { kind = if (kind == k) null else k }, icon = k.icon)
            }
        }
        if (days.isEmpty()) {
            block("empty") { EmptyState("Niente da mostrare", "checklist", "Nessun elemento corrisponde ai filtri.") }
            return@PageScaffold
        }
        for ((day, events) in days.entries.take(120)) {
            block("d-$day") {
                ListGroup(header = Fmt.relativeDayName(day)) {
                    for (e in events) item(e.id) { AgendaCell(e, expanded, { expanded = it }, scope = "ag") }
                }
            }
        }
    }
}

// MARK: - Registro delle lezioni

private fun previousSchoolDay(d: LocalDate): LocalDate {
    var x = d.minusDays(1)
    while (Dates.isWeekend(x)) x = x.minusDays(1)
    return x
}

private fun nextSchoolDay(d: LocalDate): LocalDate {
    var x = d.plusDays(1)
    while (Dates.isWeekend(x)) x = x.plusDays(1)
    return x
}

@Composable
fun LessonsScreen() {
    val model = LocalModel.current
    var mode by rememberSaveable { mutableStateOf("day") }
    var dayEpoch by rememberSaveable {
        var d = Dates.today()
        while (Dates.isWeekend(d)) d = d.minusDays(1)
        mutableStateOf(d.toEpochDay())
    }
    val day = LocalDate.ofEpochDay(dayEpoch)
    var all by remember { mutableStateOf<List<Lesson>?>(null) }
    var loadingAll by remember { mutableStateOf(false) }
    var subject by rememberSaveable { mutableStateOf<Long?>(null) }
    var picking by remember { mutableStateOf(false) }
    val week = Dates.startOfWeek(day)
    LaunchedEffect(week, model.lastUpdated) { model.ensureLessons(week, week.plusDays(6)) }
    LaunchedEffect(mode) {
        if (mode == "subject" && all == null) {
            loadingAll = true
            all = runCatching { model.fetchLessons(Dates.schoolYear().start, Dates.today()) }.getOrDefault(emptyList())
            loadingAll = false
        }
    }
    PageScaffold(title = "Lezioni") {
        block("mode") { ConnectedGroup(listOf("day", "subject"), mode, { mode = it }) { v, _ -> Text(if (v == "day") "Per giorno" else "Per materia") } }
        if (mode == "day") {
            block("nav") {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalIconButton(onClick = { dayEpoch = previousSchoolDay(day).toEpochDay() }) { Icon("chevronLeft", contentDescription = "Giorno precedente") }
                    FilledTonalButton(onClick = { picking = true }, modifier = Modifier.weight(1f)) {
                        Icon("calendarMonth", size = 18.dp)
                        Text(Fmt.longDay(day), modifier = Modifier.padding(start = 8.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    FilledTonalIconButton(onClick = {
                        val next = nextSchoolDay(day)
                        dayEpoch = (if (next < Dates.today()) next else Dates.today()).toEpochDay()
                    }, enabled = !Dates.isToday(day)) { Icon("chevronRight", contentDescription = "Giorno successivo") }
                }
            }
            val list = model.lessons(day)
            block("list") {
                when {
                    list.isNotEmpty() -> ListGroup { for (l in list) item(l.id) { SegCell { LessonRow(l) } } }
                    day.isAfter(Dates.today()) -> EmptyState("Nessuna lezione", "bookText", "Le lezioni compaiono nel registro dopo essere state svolte.")
                    model.hasLoadedLessons(day) -> EmptyState("Nessuna lezione", "bookText", if (Dates.isWeekend(day)) "È il fine settimana." else "Non ci sono lezioni registrate per questo giorno.")
                    else -> LoadingCard()
                }
            }
        } else {
            val lessons = all
            if (loadingAll || lessons == null) {
                block("loading") { LoadingCard("Carico il registro dell'anno…") }
            } else {
                val groups = lessons.groupBy { it.subjectId ?: 0 }.map { (id, l) -> Triple(id, l.first().subjectName, l.sortedByDescending { it.date }) }
                    .sortedWith { a, b -> byName(a.second, b.second) }
                block("chips") {
                    ChipRow {
                        BCWFilterChip("Tutte", subject == null, { subject = null })
                        for ((id, name) in groups.map { it.first to it.second }) BCWFilterChip(name, subject == id, { subject = if (subject == id) null else id })
                    }
                }
                val shown = groups.filter { subject == null || it.first == subject }
                if (shown.isEmpty()) block("empty") { EmptyState("Nessuna lezione", "bookText", "Non ci sono lezioni registrate.") }
                for ((id, name, l) in shown) {
                    block("s-$id") {
                        Card {
                            val topics = l.filter { it.topic.isNotEmpty() }
                            val hours = l.sumOf { it.duration.toInt() }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                SubjectTag(name, id, Modifier.weight(1f))
                                Text(plural(hours, "1 ora", "# ore"), style = Theme.type.style(12), color = Theme.scheme.onSurfaceVariant)
                            }
                            Spacer(Modifier.height(8.dp))
                            for (t in topics.take(if (subject == null) 4 else 200)) {
                                Row(Modifier.padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Text(Fmt.shortDay(t.date), style = Theme.type.numeral(13, 550), color = Theme.scheme.onSurfaceVariant, modifier = Modifier.width(52.dp))
                                    Text(t.topic, style = Theme.type.style(14), color = Theme.scheme.onSurface, modifier = Modifier.weight(1f))
                                }
                            }
                            if (subject == null && topics.size > 4) {
                                val article = if (topics.size == 11 || topics.size.toString().startsWith("8")) "gli" else "i"
                                TextButton(onClick = { subject = id }) { Text("Mostra tutti $article ${topics.size} argomenti") }
                            }
                        }
                    }
                }
            }
        }
    }
    if (picking) DatePickerSheet(day, { picking = false }, { dayEpoch = it.toEpochDay() }, min = Dates.schoolYear().start, max = Dates.today())
}

// MARK: - Materiale didattico

@Composable
fun DidacticsScreen() {
    val model = LocalModel.current
    val ui = LocalUi.current
    val scope = rememberCoroutineScope()
    var teacher by rememberSaveable { mutableStateOf<String?>(null) }
    var search by rememberSaveable { mutableStateOf("") }
    var expanded by remember { mutableStateOf(setOf<String>()) }
    var opening by remember { mutableStateOf<Long?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    var text by remember { mutableStateOf<Pair<String, String>?>(null) }
    LaunchedEffect(Unit) {
        if (model.didactics.isEmpty()) {
            loading = true
            error = model.loadDidactics()
            loading = false
        }
    }
    PageScaffold(title = "Materiale didattico", onRefresh = { error = model.loadDidactics() }, gap = 18.dp) {
        error?.let { block("error") { StatusBanner(it, "alertTriangle") } }
        if (model.didactics.isNotEmpty()) {
            block("search") { LocalSearchField(search, "Cerca file o cartelle") { search = it } }
            block("chips") {
                ChipRow {
                    BCWFilterChip("Tutti i docenti", teacher == null, { teacher = null })
                    for (t in model.didactics) BCWFilterChip(t.name, teacher == t.id, { teacher = if (teacher == t.id) null else t.id })
                }
            }
        }
        if (loading && model.didactics.isEmpty()) block("loading") { LoadingCard() }
        else if (model.didactics.isEmpty()) block("empty") { EmptyState("Nessun materiale", "folder", "I file condivisi dai docenti appariranno qui.") }
        val teachers = model.didactics.filter { teacher == null || it.id == teacher }.sortedByDescending { it.lastShare }
        for (t in teachers) {
            val folders = t.folders.filter { f -> search.isEmpty() || f.name.containsText(search) || f.contents.any { it.name.containsText(search) } }.sortedByDescending { it.lastShare }
            if (folders.isEmpty()) continue
            block("t-${t.id}") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(Modifier.size(32.dp).clip(CircleShape).background(subjectColor(StableID.make(t.id))), contentAlignment = Alignment.Center) {
                            Text(t.name.take(1), style = Theme.type.numeral(15), color = Theme.scheme.surface)
                        }
                        Text(t.name, style = Theme.type.style(18, 600, rounded = true), color = Theme.scheme.onSurface)
                    }
                    ListGroup {
                        for (f in folders) item(f.id) {
                            val key = "${t.id}-${f.id}"
                            val open = key in expanded || search.isNotEmpty()
                            Cell {
                                Column {
                                    Row(
                                        Modifier.fillMaxWidth().clip(RoundedCornerShape(Theme.radii.l)).clickableRow { expanded = if (key in expanded) expanded - key else expanded + key }
                                            .padding(horizontal = 16.dp, vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
                                    ) {
                                        Icon(if (open) "folderFill" else "folder", tint = Theme.colors.subject(3).color)
                                        Column(Modifier.weight(1f)) {
                                            Text(f.name, style = Theme.type.style(16, 550), color = Theme.scheme.onSurface)
                                            Text(plural(f.contents.size, "1 elemento", "# elementi") + (f.lastShare?.let { " · ${Fmt.shortDayWithYear(it.toLocalDate())}" } ?: ""),
                                                style = Theme.type.style(13), color = Theme.scheme.onSurfaceVariant)
                                        }
                                        Chevron(!open, rotateUp = true)
                                    }
                                    Collapsible(open) {
                                        val contents = f.contents.filter { search.isEmpty() || it.name.containsText(search) || f.name.containsText(search) }
                                        for (c in contents) {
                                            HorizontalDivider(Modifier.padding(start = 54.dp), color = Theme.scheme.outlineVariant)
                                            Row(
                                                Modifier.fillMaxWidth().clickableRow {
                                                    if (opening != null) return@clickableRow
                                                    scope.launch {
                                                        opening = c.id
                                                        try {
                                                            when (val item = model.openDidacticContent(c)) {
                                                                is AppModel.DidacticItem.Link -> ui.openUrl(item.url)
                                                                is AppModel.DidacticItem.Text -> text = c.name to item.text
                                                                is AppModel.DidacticItem.File -> ui.open(model, item.file)
                                                            }
                                                            error = null
                                                        } catch (e: Exception) {
                                                            error = e.message
                                                        } finally {
                                                            opening = null
                                                        }
                                                    }
                                                }.padding(horizontal = 16.dp, vertical = 12.dp),
                                                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
                                            ) {
                                                Icon(c.icon, tint = Theme.scheme.onSurfaceVariant)
                                                Column(Modifier.weight(1f)) {
                                                    Text(c.name, style = Theme.type.style(15), color = Theme.scheme.onSurface)
                                                    c.sharedAt?.let { Text(Fmt.shortDayWithYear(it.toLocalDate()), style = Theme.type.style(12), color = Theme.scheme.onSurfaceVariant) }
                                                }
                                                if (opening == c.id) LoadingIndicator(Modifier.size(28.dp))
                                                else Icon(if (c.kind == com.bcw.classeviva.data.DidacticKind.link) "externalLink" else "download", tint = Theme.scheme.primary)
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
    text?.let { (title, body) ->
        AlertDialog(onDismissRequest = { text = null }, confirmButton = { Button(onClick = { text = null }) { Text("Fine") } },
            title = { Text(title) }, text = { SelectionContainer { Text(body) } })
    }
}

private fun Modifier.clickableRow(onClick: () -> Unit): Modifier = this.clickable(onClick = onClick)

// MARK: - Scrutini e pagelle

@Composable
fun ReportsScreen() {
    val model = LocalModel.current
    val ui = LocalUi.current
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var downloading by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        loading = true
        error = model.loadDocuments()
        loading = false
    }
    val book = model.gradeBook
    PageScaffold(title = "Scrutini", onRefresh = { error = model.loadDocuments() }, gap = 20.dp) {
        error?.let { block("error") { StatusBanner(it, "alertTriangle") } }
        block("summary") {
            Card {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Eyebrow("Situazione per periodo")
                    if (book.activePeriods.isEmpty()) Text("Nessun voto registrato.", style = Theme.type.style(14), color = Theme.scheme.onSurfaceVariant)
                    for (p in book.activePeriods) {
                        val below = book.subjects(p.position).filter { (it.average ?: 10.0) < 6 }
                        val avg = book.average(p.position)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(p.name, style = Theme.type.style(17, 600), color = Theme.scheme.onSurface)
                                Text(if (below.isNotEmpty()) "Insufficienze: ${below.joinToString(", ") { it.name }}" else "Nessuna insufficienza",
                                    style = Theme.type.style(13), color = if (below.isNotEmpty()) Theme.colors.poor.color else Theme.colors.good.color)
                            }
                            Text(GradeFormat.average(avg), style = Theme.type.numeral(26), color = tone(gradeTone(avg)).color)
                        }
                    }
                }
            }
        }
        val documents = model.documents
        if (loading && documents == null) block("loading") { LoadingCard() }
        else if (documents != null) {
            if (documents.documents.isEmpty() && documents.schoolReports.isEmpty()) {
                block("empty") { EmptyState("Nessun documento", "fileText", "Pagelle e documenti di valutazione appariranno qui dopo gli scrutini.") }
            }
            if (documents.documents.isNotEmpty()) block("docs") {
                ListGroup(header = "Documenti") {
                    for (d in documents.documents) item(d.documentHash) {
                        ListRow(d.title, leading = { IconBadge("fileText", Theme.colors.neutral.color) }, onClick = {
                            if (downloading != null) return@ListRow
                            scope.launch {
                                downloading = d.documentHash
                                try { ui.open(model, model.downloadDocument(d)); error = null } catch (e: Exception) { error = e.message } finally { downloading = null }
                            }
                        }, trailing = { if (downloading == d.documentHash) LoadingIndicator(Modifier.size(28.dp)) else ChevronRight() })
                    }
                }
            }
            if (documents.schoolReports.isNotEmpty()) block("reports") {
                ListGroup(header = "Pagelle online", footer = "Si aprono sul sito di Classeviva.") {
                    for (r in documents.schoolReports) item(r.title + r.viewLink) {
                        ListRow(r.title, leading = { IconBadge("compass", Theme.colors.neutral.color) }, onClick = { r.viewLink?.let(ui::openUrl) },
                            trailing = { Icon("externalLink", size = 20.dp, tint = Theme.scheme.onSurfaceVariant) })
                    }
                }
            }
        }
    }
}

// MARK: - Anni precedenti

@Composable
fun PreviousScreen() {
    val model = LocalModel.current
    val ui = LocalUi.current
    val scope = rememberCoroutineScope()
    var startYear by rememberSaveable { mutableStateOf(ArchiveModel.previousStartYear) }
    var archive by remember(startYear) { mutableStateOf(model.archive(startYear)) }
    var downloading by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var menu by remember { mutableStateOf(false) }
    LaunchedEffect(archive) { archive.load(model.credentialsForArchive, model.isDemo) }
    PageScaffold(
        title = "Anno ${archive.title}",
        actions = {
            Box {
                IconButton(onClick = { menu = true }) { Icon("calendarClock", contentDescription = "Scegli l'anno") }
                BCWMenu(menu, { menu = false }, ArchiveModel.availableStartYears.map { y ->
                    MenuItem(ArchiveModel.title(y), checked = y == startYear) { startYear = y; error = null }
                }, sections = mapOf(0 to "Anno scolastico"))
            }
        },
        gap = 20.dp,
    ) {
        block("web") {
            Card {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        IconBadge("compass", Theme.colors.neutral.color)
                        Column {
                            Text("Voti, assenze e note", style = Theme.type.style(17, 600), color = Theme.scheme.onSurface)
                            Text("Disponibili sul sito di Classeviva", style = Theme.type.style(13), color = Theme.scheme.onSurfaceVariant)
                        }
                    }
                    Text("Classeviva non rende disponibili all'app i dati degli anni passati. Accedi al sito e, dal menu principale, scegli \"Vai all'a.s. ${archive.title}\".",
                        style = Theme.type.style(14), color = Theme.scheme.onSurface)
                    Button(onClick = { ui.openUrl(ArchiveModel.WEB_URL) }) {
                        Icon("externalLink", size = 18.dp); Text("Apri l'archivio di Classeviva", modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        }
        block("header") { SectionHeader("Pagelle e documenti", subtitle = "Anno scolastico ${archive.title}") }
        error?.let { block("error") { StatusBanner(it, "alertTriangle") } }
        block("docs") {
            val retry = @Composable {
                FilledTonalButton(onClick = { archive = model.reloadArchive(startYear) }) { Text("Riprova") }
            }
            when (archive.state) {
                ArchiveModel.State.idle, ArchiveModel.State.loading -> LoadingCard("Accesso all'archivio ${archive.title}…")
                ArchiveModel.State.failed -> Card { EmptyState("Nessun documento", "fileText", archive.failure, actions = retry) }
                ArchiveModel.State.loaded -> if (archive.documents.isEmpty()) {
                    Card { EmptyState("Nessun documento", "fileText", archive.documentsError?.let { "Classeviva ha risposto: $it" } ?: "La scuola non ha pubblicato documenti per questo anno.", actions = retry) }
                } else ListGroup {
                    for (d in archive.documents) item(d.documentHash) {
                        ListRow(d.title, leading = { IconBadge("fileText", Theme.colors.neutral.color) }, onClick = {
                            if (downloading != null) return@ListRow
                            scope.launch {
                                downloading = d.documentHash
                                try { ui.open(model, archive.downloadDocument(d)); error = null } catch (e: Exception) { error = e.message } finally { downloading = null }
                            }
                        }, trailing = { if (downloading == d.documentHash) LoadingIndicator(Modifier.size(28.dp)) else ChevronRight() })
                    }
                }
            }
        }
    }
}

// MARK: - Materie e docenti

@Composable
fun SubjectsScreen() {
    val model = LocalModel.current
    val nav = LocalNavigator.current
    val sorted = model.subjects.sortedBy { it.order }
    val summaries = model.gradeBook.subjects()
    PageScaffold(title = "Materie", onRefresh = { model.loadGrades() }) {
        if (sorted.isEmpty()) {
            block("empty") { EmptyState("Nessuna materia", "library") }
            return@PageScaffold
        }
        block("list") {
            ListGroup {
                for (s in sorted) item(s.id) {
                    val average = summaries.firstOrNull { it.subjectId == s.id }?.average
                    ListRow(s.name, subtitle = s.teachers.joinToString(", ").ifEmpty { null }, onClick = { nav.push(Page.Subject(s.id)) },
                        leading = { Box(Modifier.size(12.dp).clip(CircleShape).background(subjectColor(s.id))) },
                        trailing = {
                            Text(GradeFormat.average(average), style = Theme.type.numeral(17), color = tone(gradeTone(average)).color)
                            ChevronRight()
                        })
                }
            }
        }
    }
}

// MARK: - Libri di testo

@Composable
fun SchoolbooksScreen() {
    val model = LocalModel.current
    var loading by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        loading = true
        model.loadSchoolbooks()
        loading = false
    }
    PageScaffold(title = "Libri di testo", gap = 22.dp) {
        if (model.schoolbooks.isEmpty()) {
            block("empty") { if (loading) LoadingCard() else EmptyState("Nessun libro", "library", "La scuola non ha pubblicato le adozioni.") }
            return@PageScaffold
        }
        for (course in model.schoolbooks) {
            block("c-${course.id}") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SectionHeader(course.name)
                    ListGroup {
                        for (b in course.books) item(b.id) {
                            SegCell {
                                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                    Row(verticalAlignment = Alignment.Top) {
                                        Text(b.title, style = Theme.type.style(16, 600), color = Theme.scheme.onSurface, modifier = Modifier.weight(1f))
                                        b.price?.takeIf { it > 0 }?.let { Text(GradeFormat.euro(it), style = Theme.type.numeral(14, 550), color = Theme.scheme.onSurfaceVariant) }
                                    }
                                    Text(listOfNotNull(b.subject, b.volume).joinToString(" · "), style = Theme.type.style(13), color = Theme.scheme.primary)
                                    val by = listOfNotNull(b.author, b.publisher).joinToString(" · ")
                                    if (by.isNotEmpty()) Text(by, style = Theme.type.style(13), color = Theme.scheme.onSurfaceVariant)
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        if (b.toBuy) Pill("Da acquistare", color = Theme.colors.fair.color, small = true)
                                        if (b.inUse) Pill("Già in uso", color = Theme.colors.good.color, small = true)
                                        if (b.recommended) Pill("Consigliato", color = Theme.colors.neutral.color, small = true)
                                        Spacer(Modifier.weight(1f))
                                        if (b.isbn.isNotEmpty()) Text("ISBN ${b.isbn}", style = Theme.type.numeral(11, 500), color = Theme.scheme.onSurfaceVariant)
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

// MARK: - Calendario scolastico

@Composable
fun CalendarScreen() {
    val model = LocalModel.current
    LaunchedEffect(Unit) { if (model.calendarDays.isEmpty()) model.loadCalendar() }
    val today = Dates.today()
    val days = model.calendarDays.sortedBy { it.date }
    // Periodi consecutivi di vacanza nei giorni feriali.
    val holidays = mutableListOf<Pair<LocalDate, LocalDate>>()
    var current: Pair<LocalDate, LocalDate>? = null
    for (d in days) {
        if (!d.isHoliday || Dates.isWeekend(d.date)) continue
        val c = current
        current = if (c != null && Dates.daysBetween(c.second, d.date) <= 3) c.first to d.date
        else {
            if (c != null) holidays += c
            d.date to d.date
        }
    }
    current?.let { holidays += it }
    val school = days.filter { it.isSchoolDay }
    val done = school.count { it.date < today }
    val total = school.size
    fun range(h: Pair<LocalDate, LocalDate>) = if (h.first == h.second) Fmt.longDay(h.first) else "${Fmt.shortDay(h.first)} – ${Fmt.shortDay(h.second)}"
    PageScaffold(title = "Calendario", gap = 18.dp) {
        if (total > 0) block("progress") {
            Card {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Eyebrow("Anno scolastico")
                    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(done.toString(), style = Theme.type.numeral(44), color = Theme.scheme.onSurface)
                        Text("di $total giorni di lezione", style = Theme.type.style(15), color = Theme.scheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 8.dp))
                    }
                    WavyProgress(done.toFloat() / maxOf(total, 1), Modifier.fillMaxWidth())
                    Text(plural(total - done, "Manca 1 giorno di scuola", "Mancano # giorni di scuola"), style = Theme.type.style(13), color = Theme.scheme.onSurfaceVariant)
                }
            }
        }
        holidays.firstOrNull { !it.second.isBefore(today) }?.let { next ->
            block("next") {
                Card {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Eyebrow("Prossima vacanza", Theme.scheme.primary)
                        Text(range(next), style = Theme.type.style(22, 500, rounded = true), color = Theme.scheme.onSurface)
                        val d = Dates.daysBetween(today, next.first).toInt()
                        Text(if (d <= 0) "Sei in vacanza!" else plural(d, "Domani", "Tra # giorni"), style = Theme.type.style(15), color = Theme.scheme.onSurfaceVariant)
                    }
                }
            }
        }
        block("header") { SectionHeader("Vacanze e chiusure") }
        block("list") {
            if (holidays.isEmpty()) Card { Text("Il calendario non è ancora disponibile.", style = Theme.type.style(14), color = Theme.scheme.onSurfaceVariant) }
            else ListGroup {
                for (h in holidays) item(h.first) {
                    val past = h.second < today
                    val count = Dates.daysBetween(h.first, h.second).toInt() + 1
                    ListRow(range(h), leading = { Icon(if (past) "checkCircle" else "sun", tint = if (past) Theme.scheme.onSurfaceVariant else Theme.colors.fair.color) },
                        titleColor = if (past) Theme.scheme.onSurfaceVariant else null,
                        trailing = { Text(if (count == 1) "1 giorno" else "$count giorni", style = Theme.type.style(13), color = Theme.scheme.onSurfaceVariant) })
                }
            }
        }
    }
}
