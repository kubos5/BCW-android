package com.bcw.classeviva.ui.screens

import androidx.compose.animation.AnimatedContent
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bcw.classeviva.data.Grade
import com.bcw.classeviva.data.GradeBook
import com.bcw.classeviva.data.HypotheticalGrade
import com.bcw.classeviva.util.Fmt
import com.bcw.classeviva.util.GradeFormat
import com.bcw.classeviva.util.byName
import com.bcw.classeviva.ui.components.AverageRing
import com.bcw.classeviva.ui.components.BCWFilterChip
import com.bcw.classeviva.ui.components.BCWMenu
import com.bcw.classeviva.ui.components.Card
import com.bcw.classeviva.ui.components.Cell
import com.bcw.classeviva.ui.components.ChartDot
import com.bcw.classeviva.ui.components.Chevron
import com.bcw.classeviva.ui.components.ChipFlow
import com.bcw.classeviva.ui.components.ChipRow
import com.bcw.classeviva.ui.components.Collapsible
import com.bcw.classeviva.ui.components.ConnectedGroup
import com.bcw.classeviva.ui.components.DetailRow
import com.bcw.classeviva.ui.components.EmptyState
import com.bcw.classeviva.ui.components.GradeBadge
import com.bcw.classeviva.ui.components.ListGroup
import com.bcw.classeviva.ui.components.MenuItem
import com.bcw.classeviva.ui.components.SectionHeader
import com.bcw.classeviva.ui.components.SelectButton
import com.bcw.classeviva.ui.components.Shaped
import com.bcw.classeviva.ui.components.StatusBanner
import com.bcw.classeviva.ui.components.StatusChip
import com.bcw.classeviva.ui.components.StatusLine
import com.bcw.classeviva.ui.components.Stepper
import com.bcw.classeviva.ui.components.SubjectTag
import com.bcw.classeviva.ui.components.Tone
import com.bcw.classeviva.ui.components.TrendChart
import com.bcw.classeviva.ui.components.gradeColor
import com.bcw.classeviva.ui.components.gradeTone
import com.bcw.classeviva.ui.components.subjectColor
import com.bcw.classeviva.ui.components.tone
import com.bcw.classeviva.ui.icons.Icon
import com.bcw.classeviva.ui.nav.Page
import com.bcw.classeviva.ui.shell.LocalModel
import com.bcw.classeviva.ui.shell.LocalNavigator
import com.bcw.classeviva.ui.shell.PageScaffold
import com.bcw.classeviva.ui.shell.Transitions
import com.bcw.classeviva.ui.shell.block
import com.bcw.classeviva.ui.shell.rememberInSession
import com.bcw.classeviva.ui.theme.Theme

private fun allSubjects(book: GradeBook): List<Pair<Long, String>> =
    book.grades.distinctBy { it.subjectId }.map { it.subjectId to it.subjectName }.sortedWith { a, b -> byName(a.second, b.second) }

/** Voti: media generale e dei periodi, andamento, "E se prendessi…", ultimi voti e materie. */
@Composable
fun GradesScreen() {
    val model = LocalModel.current
    val nav = LocalNavigator.current
    val book = model.gradeBook
    var section by rememberSaveable { mutableStateOf("recent") }
    var tabDir by remember { mutableIntStateOf(0) }
    var period by rememberSaveable { mutableStateOf<Int?>(null) }
    var subject by rememberSaveable { mutableStateOf<Long?>(null) }
    var expanded by remember { mutableStateOf(setOf<Long>()) }
    var filterMenu by remember { mutableStateOf(false) }
    val filtered = period != null || subject != null
    val reduced = Theme.reducedMotion

    PageScaffold(
        title = "Voti",
        actions = {
            Box {
                IconButton(onClick = { filterMenu = true }) { Icon(if (filtered) "filterFill" else "filter", contentDescription = "Filtri") }
                val items = buildList<MenuItem?> {
                    add(MenuItem("Tutto l'anno", checked = period == null) { period = null })
                    for (p in book.activePeriods) add(MenuItem(p.name, checked = period == p.position) { period = p.position })
                    add(MenuItem("Tutte le materie", checked = subject == null) { subject = null })
                    for ((id, name) in allSubjects(book)) add(MenuItem(name, checked = subject == id) { subject = id })
                    if (filtered) {
                        add(null)
                        add(MenuItem("Rimuovi filtri", "xCircle", destructive = true) { period = null; subject = null })
                    }
                }
                BCWMenu(filterMenu, { filterMenu = false }, items, sections = mapOf(0 to "Periodo", book.activePeriods.size + 1 to "Materia"))
            }
        },
        onRefresh = { model.loadGrades() },
        onReselect = { period = null },
        gap = 24.dp,
    ) {
        model.lastError?.let { block("banner") { StatusBanner(it, if (model.isOffline) "wifiOff" else "alertTriangle") } }
        if (book.grades.isEmpty()) {
            block("empty") { EmptyState("Nessun voto", "graduationCap", "I voti appariranno qui non appena verranno registrati.") }
            return@PageScaffold
        }
        block("hero") { AverageHero(book, period, onPeriod = { period = it }) }
        block("trend") { TrendCard(book, period, subject) }
        block("simulator") { GradeSimulator(book, period, subjectId = null, suggestedSubject = subject, collapsible = true) }
        block("tabs") {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                PrimaryTabRow(selectedTabIndex = if (section == "recent") 0 else 1, containerColor = Theme.scheme.surface) {
                    Tab(section == "recent", onClick = { if (section != "recent") { tabDir = -1; section = "recent" } }, text = { Text("Ultimi voti") })
                    Tab(section == "subjects", onClick = { if (section != "subjects") { tabDir = 1; section = "subjects" } }, text = { Text("Materie") })
                }
                val subjectName = allSubjects(book).firstOrNull { it.first == subject }?.second
                var subjectMenu by remember { mutableStateOf(false) }
                ChipRow {
                    Box {
                        BCWFilterChip(subjectName ?: "Tutte le materie", subject != null, { subjectMenu = true }, icon = if (subject != null) "check" else "library", trailingIcon = "chevronDown")
                        BCWMenu(subjectMenu, { subjectMenu = false }, listOf(MenuItem("Tutte le materie", checked = subject == null) { subject = null }) +
                            allSubjects(book).map { (id, name) -> MenuItem(name, checked = subject == id) { subject = id } })
                    }
                    if (subject != null) BCWFilterChip("Rimuovi filtro", false, { subject = null }, icon = "x")
                }
                AnimatedContent(section, transitionSpec = { Transitions.sharedAxisX(tabDir, reduced) }, label = "gradesTab") { s ->
                    if (s == "recent") RecentList(book, period, subject, expanded) { expanded = it }
                    else SubjectList(book, period, subject) { nav.push(Page.Subject(it)) }
                }
            }
        }
    }
}

@Composable
private fun RecentList(book: GradeBook, period: Int?, subject: Long?, expanded: Set<Long>, onExpanded: (Set<Long>) -> Unit) {
    val list = book.gradesIn(period).filter { subject == null || it.subjectId == subject }.sortedByDescending { it.date }
    if (list.isEmpty()) {
        EmptyState("Nessun voto", "filter", "Nessun voto corrisponde ai filtri selezionati.")
        return
    }
    val months = list.groupBy { it.date.withDayOfMonth(1) }.toSortedMap(compareByDescending { it })
    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        for ((month, items) in months) {
            ListGroup(header = Fmt.monthYear(month)) {
                for (g in items) item(g.id) { GradeCard(g, book, g.id in expanded) { onExpanded(if (g.id in expanded) expanded - g.id else expanded + g.id) } }
            }
        }
    }
}

@Composable
private fun SubjectList(book: GradeBook, period: Int?, subject: Long?, onOpen: (Long) -> Unit) {
    val subjects = book.subjects(period).filter { subject == null || it.subjectId == subject }
    ListGroup {
        for (s in subjects) item(s.subjectId) {
            Cell(onClick = { onOpen(s.subjectId) }) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    AverageRing(s.average, 52.dp, 5.dp)
                    Column(Modifier.weight(1f)) {
                        Text(s.name, style = Theme.type.style(16, 550), color = Theme.scheme.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            for (g in s.grades.take(6)) {
                                val c = gradeColor(g)
                                Box(Modifier.clip(RoundedCornerShape(6.dp)).background(c.copy(alpha = 0.16f)).padding(horizontal = 6.dp, vertical = 2.dp)) {
                                    Text(g.displayValue, style = Theme.type.numeral(12, 650), color = c)
                                }
                            }
                        }
                    }
                    Icon("chevronRight", size = 20.dp, tint = Theme.scheme.onSurfaceVariant)
                }
            }
        }
    }
}

/**
 * La media in primo piano: l'indicatore circolare ondulato al centro, sotto il periodo e lo stato
 * delle insufficienze; poi i periodi in un gruppo di pulsanti collegati con la loro media.
 */
@Composable
private fun AverageHero(book: GradeBook, period: Int?, onPeriod: (Int?) -> Unit) {
    val average = book.average(period)
    val periodName = period?.let { p -> book.activePeriods.firstOrNull { it.position == p }?.name }
    val count = book.gradesIn(period).count { it.countsTowardAverage }
    val insufficient = book.subjects(period).count { (it.average ?: 10.0) < 6 }
    Column(Modifier.fillMaxWidth().padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            AverageRing(average, 188.dp, 14.dp)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(if (periodName != null) "Media del ${periodName.lowercase()}" else "Media generale", style = Theme.type.style(24, 500, rounded = true), color = Theme.scheme.onSurface, textAlign = TextAlign.Center)
                Text(if (count == 1) "1 voto che fa media" else "$count voti che fanno media", style = Theme.type.style(14), color = Theme.scheme.onSurfaceVariant)
            }
            if (insufficient > 0) StatusChip(if (insufficient == 1) "1 insufficienza" else "$insufficient insufficienze", "alertTriangleFill", Tone.poor)
            else StatusChip("Nessuna insufficienza", "checkSealFill", Tone.good)
        }
        if (book.activePeriods.isNotEmpty()) {
            val options = listOf<Int?>(null) + book.activePeriods.map { it.position }
            ConnectedGroup(options, period, onPeriod, height = 64.dp) { value, checked ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(if (value == null) "Anno" else book.activePeriods.first { it.position == value }.name, style = Theme.type.style(12, 550), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(GradeFormat.average(book.average(value)), style = Theme.type.numeral(17, if (checked) 700 else 650))
                }
            }
        }
    }
}

@Composable
private fun TrendCard(book: GradeBook, period: Int?, subjectId: Long?) {
    val points = book.runningAverage(subjectId, period)
    if (points.size < 2) return
    val last = points.last()
    val delta = last.value - points.first().value
    Card {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text("Andamento della media", style = Theme.type.style(14, 600), color = Theme.scheme.onSurfaceVariant)
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(GradeFormat.average(last.value), style = Theme.type.numeral(34), color = Theme.scheme.onSurface)
                    Text("ultimo voto", style = Theme.type.style(13), color = Theme.scheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 6.dp))
                }
            }
            StatusChip("${if (delta >= 0) "+" else "−"}${GradeFormat.average(kotlin.math.abs(delta))}", if (delta >= 0) "trendingUp" else "trendingDown", if (delta >= 0) Tone.good else Tone.poor)
        }
        Spacer(Modifier.height(12.dp))
        TrendChart(points)
    }
}

/** Voto in una lista segmentata: si apre per i dettagli e l'effetto sulla media. */
@Composable
fun GradeCard(grade: Grade, book: GradeBook, expanded: Boolean, onToggle: () -> Unit) {
    Cell(onClick = onToggle) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                GradeBadge(grade, 50.dp)
                Column(Modifier.weight(1f)) {
                    SubjectTag(grade.subjectName, grade.subjectId)
                    Text("${grade.kind} · ${Fmt.shortDay(grade.date)}", style = Theme.type.style(14), color = Theme.scheme.onSurfaceVariant)
                    if (grade.notes != null && !expanded) Text(grade.notes, style = Theme.type.style(13), color = Theme.scheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Chevron(!expanded, rotateUp = true)
            }
            Collapsible(expanded) {
                Column(Modifier.padding(top = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (grade.notes != null) {
                        Text(grade.notes, style = Theme.type.style(14), color = Theme.scheme.onSurface,
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(Theme.radii.m)).background(Theme.scheme.surfaceContainerHigh).padding(12.dp))
                    }
                    DetailRow("Data", Fmt.longDay(grade.date))
                    DetailRow("Tipo", grade.kind)
                    if (grade.periodName.isNotEmpty()) DetailRow("Periodo", grade.periodName)
                    grade.teacherName?.let { DetailRow("Docente", it) }
                    val w = grade.weight
                    if (w != null && w != 1.0 && w > 0) DetailRow("Peso", "${GradeFormat.short(w * 100)}%")
                    grade.value?.let { DetailRow("Valore", GradeFormat.average(it)) }
                    if (!grade.countsTowardAverage) {
                        StatusLine(if (grade.canceled) "Voto annullato" else "Non fa media", "info", Theme.colors.neutral.color)
                    } else {
                        val delta = remember(grade, book) { GradeBook.impact(grade, book) }
                        if (delta != null) {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text("Effetto sulla media di materia", style = Theme.type.style(14), color = Theme.scheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                                val c = if (delta >= 0) Theme.colors.good.color else Theme.colors.poor.color
                                Icon(if (delta >= 0) "arrowUp" else "arrowDown", size = 18.dp, tint = c)
                                Text(GradeFormat.average(kotlin.math.abs(delta)), style = Theme.type.numeral(15), color = c)
                            }
                        }
                    }
                }
            }
        }
    }
}

// MARK: - Dettaglio materia

@Composable
fun SubjectDetailScreen(subjectId: Long) {
    val model = LocalModel.current
    val book = model.gradeBook
    var period by rememberSaveable { mutableStateOf<Int?>(null) }
    var expanded by remember { mutableStateOf(setOf<Long>()) }
    val list = book.gradesIn(period).filter { it.subjectId == subjectId }.sortedByDescending { it.date }
    val name = book.grades.firstOrNull { it.subjectId == subjectId }?.subjectName ?: model.subjects.firstOrNull { it.id == subjectId }?.name ?: "Materia"
    var teachers = model.subjects.firstOrNull { it.id == subjectId }?.teachers ?: emptyList()
    if (teachers.isEmpty()) teachers = list.mapNotNull { it.teacherName }.distinct().sortedWith { a, b -> byName(a, b) }
    val values = list.filter { it.countsTowardAverage }.mapNotNull { it.value }

    PageScaffold(title = name, onRefresh = { model.loadGrades() }, gap = 20.dp) {
        block("hero") {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                AverageRing(GradeBook.average(list, book.weighted), 156.dp, 12.dp)
                Text(name, style = Theme.type.style(24, 500, rounded = true), color = Theme.scheme.onSurface, textAlign = TextAlign.Center)
                if (teachers.isNotEmpty()) Text(teachers.joinToString(", "), style = Theme.type.style(14), color = Theme.scheme.onSurfaceVariant, textAlign = TextAlign.Center)
                if (values.isNotEmpty()) ChipFlow(center = true) {
                    StatusChip("Migliore ${GradeFormat.short(values.max())}", "arrowUp", Tone.good)
                    StatusChip("Peggiore ${GradeFormat.short(values.min())}", "arrowDown", Tone.poor)
                }
            }
        }
        if (book.activePeriods.size > 1) block("periods") {
            ConnectedGroup(listOf<Int?>(null) + book.activePeriods.map { it.position }, period, { period = it }, height = 48.dp) { value, _ ->
                Text(if (value == null) "Anno" else book.activePeriods.first { it.position == value }.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        val sorted = list.filter { it.countsTowardAverage }.sortedBy { it.date }
        if (sorted.size >= 2) block("chart") {
            Card {
                Text("Voti e media", style = Theme.type.style(16, 600), color = Theme.scheme.onSurface)
                Spacer(Modifier.height(12.dp))
                val color = subjectColor(subjectId)
                val dots = sorted.map { ChartDot(it.date, it.value ?: 0.0, gradeColor(it)) }
                TrendChart(book.runningAverage(subjectId, period), height = 170.dp, domain = 2.0..10.0, ticks = listOf(2, 4, 6, 8, 10), color = color, dots = dots, area = false, lastDot = false)
            }
        }
        block("goal") { GoalCalculator(list, book.weighted) }
        block("sim") { GradeSimulator(book, period, subjectId = subjectId, suggestedSubject = null, collapsible = false) }
        block("header") { SectionHeader("Voti", subtitle = if (list.size == 1) "1 valutazione" else "${list.size} valutazioni") }
        block("list") {
            ListGroup {
                for (g in list) item(g.id) { GradeCard(g, book, g.id in expanded) { expanded = if (g.id in expanded) expanded - g.id else expanded + g.id } }
            }
        }
    }
}

/** "Quanto devo prendere?": voto necessario per raggiungere una media obiettivo. */
@Composable
private fun GoalCalculator(list: List<Grade>, weighted: Boolean) {
    val model = LocalModel.current
    val prefs = model.preferences
    val target = prefs.targetAverage
    var upcoming by rememberSaveable { mutableIntStateOf(1) }
    val needed = GradeBook.neededGrade(target, list, upcoming, weighted)
    Card {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Shaped("cookie9Sided", Theme.scheme.primaryContainer, 44.dp) { Icon("target", size = 22.dp, tint = Theme.scheme.onPrimaryContainer) }
                Column {
                    Text("Obiettivo", style = Theme.type.style(16, 600), color = Theme.scheme.onSurface)
                    Text("Quanto ti serve per arrivarci", style = Theme.type.style(13), color = Theme.scheme.onSurfaceVariant)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Media desiderata", style = Theme.type.style(15), color = Theme.scheme.onSurface, modifier = Modifier.weight(1f))
                Stepper(GradeFormat.short(target), { prefs.set("targetAverage", maxOf(4.0, target - 0.25)) }, { prefs.set("targetAverage", minOf(10.0, target + 0.25)) }, target > 4, target < 10)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Prossime prove", style = Theme.type.style(15), color = Theme.scheme.onSurface, modifier = Modifier.weight(1f))
                Stepper(upcoming.toString(), { upcoming = maxOf(1, upcoming - 1) }, { upcoming = minOf(5, upcoming + 1) }, upcoming > 1, upcoming < 5)
            }
            HorizontalDivider(color = Theme.scheme.outlineVariant)
            when {
                needed <= 1 -> StatusLine("Hai già raggiunto l'obiettivo, qualunque voto prenderai.", "checkSealFill", Theme.colors.good.color)
                needed > 10 -> StatusLine("Obiettivo non raggiungibile con ${if (upcoming == 1) "una prova" else "$upcoming prove"}.", "octagonX", Theme.colors.poor.color)
                else -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(if (upcoming == 1) "Ti serve almeno" else "Ti serve in media almeno", style = Theme.type.style(15), color = Theme.scheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                    Text(GradeFormat.average(needed), style = Theme.type.numeral(28), color = tone(gradeTone(needed)).color)
                }
            }
        }
    }
}

// MARK: - E se prendessi…

@Stable
class SimulatorState {
    var open by mutableStateOf(false)
    var grades by mutableStateOf<List<HypotheticalGrade>>(emptyList())
    var value by mutableStateOf(7.0)
    var weight by mutableStateOf(1.0)
    var chosen by mutableStateOf<Long?>(null)
}

/**
 * "E se prendessi…": come cambierebbe la media con uno o più voti ipotetici. Nella pagina di una
 * materia i voti sono di quella materia, nei Voti si sceglie la materia di ognuno tra tutte quelle
 * dell'anno. Nulla viene salvato.
 */
@Composable
private fun GradeSimulator(book: GradeBook, period: Int?, subjectId: Long?, suggestedSubject: Long?, collapsible: Boolean) {
    val model = LocalModel.current
    val s = if (subjectId == null) rememberInSession("sim:grades:${model.cacheOwner}") { SimulatorState() } else remember(subjectId) { SimulatorState() }
    val subjects = model.allSubjectNames()
    fun known(id: Long?) = id != null && subjects.any { it.first == id }
    val target = subjectId ?: s.chosen?.takeIf(::known) ?: suggestedSubject?.takeIf(::known) ?: subjects.firstOrNull()?.first
    fun nameOf(id: Long) = subjects.firstOrNull { it.first == id }?.second ?: "Materia"

    Card {
        Row(
            Modifier.fillMaxWidth().then(if (collapsible) Modifier.clip(RoundedCornerShape(Theme.radii.m)).clickable { s.open = !s.open } else Modifier),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Shaped("clover4Leaf", Theme.scheme.tertiaryContainer, 44.dp) { Icon("wand", size = 22.dp, tint = Theme.scheme.onTertiaryContainer) }
            Column(Modifier.weight(1f)) {
                Text("E se prendessi…", style = Theme.type.style(16, 600), color = Theme.scheme.onSurface)
                Text("Come cambierebbe la media", style = Theme.type.style(13), color = Theme.scheme.onSurfaceVariant)
            }
            if (collapsible) Chevron(!s.open, rotateUp = true)
        }
        Collapsible(!collapsible || s.open) {
            Column(Modifier.padding(top = 14.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                if (subjectId == null && subjects.isNotEmpty()) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Materia", style = Theme.type.style(15), color = Theme.scheme.onSurface)
                        SelectButton(subjects, target, { s.chosen = it }, Modifier.weight(1f), maxLines = 2, alignEnd = true)
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Voto", style = Theme.type.style(15), color = Theme.scheme.onSurface, modifier = Modifier.weight(1f))
                    Stepper(GradeFormat.short(s.value), { s.value = maxOf(1.0, s.value - 0.25) }, { s.value = minOf(10.0, s.value + 0.25) }, s.value > 1, s.value < 10)
                }
                if (book.weighted) Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Peso", style = Theme.type.style(15), color = Theme.scheme.onSurface, modifier = Modifier.weight(1f))
                    Stepper("${GradeFormat.short(s.weight * 100)}%", { s.weight = maxOf(0.25, s.weight - 0.25) }, { s.weight = minOf(3.0, s.weight + 0.25) }, s.weight > 0.25, s.weight < 3)
                }
                FilledTonalButton(
                    onClick = { if (target != null) s.grades = s.grades + HypotheticalGrade.make(target, nameOf(target), s.value, if (book.weighted) s.weight else 1.0) },
                    enabled = target != null, modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon("plus", size = 18.dp)
                    Text("Aggiungi questo voto", modifier = Modifier.padding(start = 8.dp))
                }
                if (s.grades.isNotEmpty()) {
                    ChipFlow {
                        for (g in s.grades) {
                            val ts = tone(gradeTone(g.value))
                            InputChip(
                                selected = true, onClick = { s.grades = s.grades.filter { it.id != g.id } },
                                label = {
                                    Text(buildString {
                                        append(GradeFormat.short(g.value))
                                        if (subjectId == null) append(" · ${g.subjectName}")
                                        if (book.weighted && g.weight != 1.0) append(" · ${GradeFormat.short(g.weight * 100)}%")
                                    }, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                },
                                trailingIcon = { Icon("x", size = 18.dp) },
                                colors = InputChipDefaults.inputChipColors(selectedContainerColor = ts.container, selectedLabelColor = ts.onContainer, selectedTrailingIconColor = ts.onContainer),
                                shape = RoundedCornerShape(Theme.radii.s),
                            )
                        }
                    }
                    HorizontalDivider(color = Theme.scheme.outlineVariant)
                    val chosenPeriod = period?.let { p -> book.activePeriods.firstOrNull { it.position == p } } ?: book.activePeriods.lastOrNull()
                    val simulated = book.adding(s.grades, chosenPeriod?.position ?: period ?: 1, chosenPeriod?.name ?: "")
                    val touched = s.grades.map { it.subjectId }.distinct().sortedWith { a, b -> byName(nameOf(a), nameOf(b)) }
                    for (id in touched) {
                        SimulatorRow(
                            if (subjectId == null) nameOf(id) else "Media della materia",
                            GradeBook.average(book.gradesIn(period).filter { it.subjectId == id }, book.weighted),
                            GradeBook.average(simulated.gradesIn(period).filter { it.subjectId == id }, book.weighted),
                            prominent = subjectId != null,
                        )
                    }
                    SimulatorRow(if (period == null) "Media generale" else "Media del periodo", book.average(period), simulated.average(period), prominent = subjectId == null)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { s.grades = emptyList() }) { Text("Azzera") }
                    }
                }
            }
        }
    }
}

/** Prima e dopo: la media di prima in piccolo, quella nuova in grande, e la differenza. */
@Composable
private fun SimulatorRow(title: String, before: Double?, after: Double?, prominent: Boolean) {
    val delta = if (before != null && after != null && kotlin.math.abs(after - before) >= 0.005) after - before else null
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = Theme.type.style(if (prominent) 15 else 14, if (prominent) 600 else 500), color = Theme.scheme.onSurface, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(GradeFormat.average(before), style = Theme.type.numeral(14, 550), color = Theme.scheme.onSurfaceVariant)
        Icon("chevronRight", size = 16.dp, tint = Theme.scheme.onSurfaceVariant)
        Text(GradeFormat.average(after), style = Theme.type.numeral(if (prominent) 22 else 18), color = tone(gradeTone(after)).color)
        if (delta != null) {
            val c = if (delta > 0) Theme.colors.good else Theme.colors.poor
            Box(Modifier.clip(RoundedCornerShape(Theme.radii.s)).background(c.container).padding(horizontal = 6.dp, vertical = 2.dp)) {
                Text("${if (delta > 0) "+" else "−"}${GradeFormat.average(kotlin.math.abs(delta))}", style = Theme.type.numeral(12, 650), color = c.onContainer)
            }
        }
    }
}
