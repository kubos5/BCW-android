package com.bcw.classeviva.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.bcw.classeviva.util.Fmt
import com.bcw.classeviva.util.containsText
import com.bcw.classeviva.ui.components.AgendaCell
import com.bcw.classeviva.ui.components.BCWFilterChip
import com.bcw.classeviva.ui.components.ChevronRight
import com.bcw.classeviva.ui.components.ChipFlow
import com.bcw.classeviva.ui.components.EmptyState
import com.bcw.classeviva.ui.components.GradeBadge
import com.bcw.classeviva.ui.components.IconBadge
import com.bcw.classeviva.ui.components.ListGroup
import com.bcw.classeviva.ui.components.ListHeader
import com.bcw.classeviva.ui.components.ListRow
import com.bcw.classeviva.ui.components.RowCaption
import com.bcw.classeviva.ui.components.SegCell
import com.bcw.classeviva.ui.components.SubjectTag
import com.bcw.classeviva.ui.icons.Icon
import com.bcw.classeviva.ui.nav.Page
import com.bcw.classeviva.ui.shell.LocalModel
import com.bcw.classeviva.ui.shell.LocalNavigator
import com.bcw.classeviva.ui.shell.PageScaffold
import com.bcw.classeviva.ui.shell.block
import com.bcw.classeviva.ui.theme.Theme
import java.time.Duration
import java.time.LocalDateTime

private val kinds = listOf("Verifica" to "test", "Interrogazione" to "person", "Compiti" to "book", "Comunicazione" to "megaphone")

/**
 * Ricerca globale su compiti, voti, comunicazioni, materiale e note. Si apre dalla barra in cima
 * alla Dashboard: i risultati prendono il posto della pagina.
 */
@Composable
fun SearchScreen(onClose: () -> Unit) {
    val model = LocalModel.current
    val nav = LocalNavigator.current
    var query by rememberSaveable { mutableStateOf("") }
    var expanded by remember { mutableStateOf(setOf<String>()) }
    val focus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    LaunchedEffect(Unit) {
        runCatching { focus.requestFocus() }
        if (model.didactics.isEmpty()) model.loadDidactics()
    }
    val q = query.trim()

    PageScaffold(
        title = "Cerca",
        topBar = {
            Row(
                Modifier.fillMaxWidth().background(Theme.scheme.surface).windowInsetsPadding(WindowInsets.statusBars).padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    Modifier.weight(1f).height(56.dp).clip(RoundedCornerShape(28.dp)).background(Theme.scheme.surfaceContainerHigh).padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onClose) { Icon("arrowBack", contentDescription = "Chiudi la ricerca") }
                    BasicTextField(
                        value = query, onValueChange = { query = it }, singleLine = true,
                        textStyle = Theme.type.style(16).copy(color = Theme.scheme.onSurface),
                        cursorBrush = SolidColor(Theme.scheme.primary),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                        modifier = Modifier.weight(1f).focusRequester(focus),
                        decorationBox = { inner ->
                            if (query.isEmpty()) Text("Cerca nel registro", style = Theme.type.style(16), color = Theme.scheme.onSurfaceVariant)
                            inner()
                        },
                    )
                    if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon("x", contentDescription = "Cancella") }
                }
            }
        },
        gap = 20.dp,
    ) {
        if (q.isEmpty()) {
            block("kinds") {
                Column {
                    ListHeader("Cerca per tipo")
                    ChipFlow {
                        for ((label, icon) in kinds) FilledTonalButton(onClick = { query = label }) {
                            Icon(icon, size = 18.dp)
                            Text(label, modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                }
            }
            val subjects = model.grades.map { it.subjectName }.distinct().sortedWith { a, b -> com.bcw.classeviva.util.byName(a, b) }.take(10)
            if (subjects.isNotEmpty()) block("subjects") {
                Column {
                    ListHeader("Materie")
                    ChipFlow { for (s in subjects) BCWFilterChip(s, false, { query = s }) }
                }
            }
            return@PageScaffold
        }
        val now = LocalDateTime.now()
        val agenda = model.agenda.filter { it.notes.containsText(q) || it.subjectName.containsText(q) }
            .sortedBy { kotlin.math.abs(Duration.between(now, it.begin).toMinutes()) }
        val grades = model.grades.filter { it.subjectName.containsText(q) || it.notes.containsText(q) }
        val notices = model.notices.filter { it.title.containsText(q) || it.category.containsText(q) }
        val notes = model.notes.filter { it.text.containsText(q) || it.authorName.containsText(q) }
        val files = model.didactics.flatMap { t -> t.folders.flatMap { it.contents }.filter { it.name.containsText(q) || t.name.containsText(q) }.map { t to it } }
        if (agenda.isEmpty() && grades.isEmpty() && notices.isEmpty() && notes.isEmpty() && files.isEmpty()) {
            block("empty") { EmptyState("Nessun risultato per \"$q\"", "search", "Controlla l'ortografia o prova con un'altra ricerca.") }
            return@PageScaffold
        }
        fun header(title: String, count: Int) = if (count > 15) "$title · $count" else title
        if (agenda.isNotEmpty()) block("agenda") {
            ListGroup(header = header("Agenda", agenda.size)) {
                for (e in agenda.take(15)) item(e.id) { AgendaCell(e, expanded, { expanded = it }, scope = "se", showsDate = true) }
            }
        }
        if (grades.isNotEmpty()) block("grades") {
            ListGroup(header = header("Voti", grades.size)) {
                for (g in grades.take(15)) item(g.id) {
                    SegCell {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            GradeBadge(g, 44.dp)
                            Column(Modifier.weight(1f)) {
                                SubjectTag(g.subjectName, g.subjectId)
                                RowCaption(listOfNotNull(g.kind, Fmt.shortDay(g.date), g.notes).joinToString(" · "))
                            }
                        }
                    }
                }
            }
        }
        if (notices.isNotEmpty()) block("notices") {
            ListGroup(header = header("Bacheca", notices.size)) {
                for (n in notices.take(10)) item(n.id) {
                    ListRow(n.title, subtitle = n.category, leading = { IconBadge("megaphone", Theme.scheme.primary) }, trailing = { ChevronRight() },
                        onClick = { onCloseAndOpen(nav, onClose, Page.Notice(n.id)) })
                }
            }
        }
        if (files.isNotEmpty()) block("files") {
            ListGroup(header = header("Materiale didattico", files.size)) {
                for ((teacher, content) in files.take(10)) item(content.id) {
                    ListRow(content.name, subtitle = teacher.name, leading = { IconBadge(content.icon, Theme.scheme.tertiary) }, trailing = { ChevronRight() },
                        onClick = { onCloseAndOpen(nav, onClose, Page.Didactics) })
                }
            }
        }
        if (notes.isNotEmpty()) block("notes") {
            ListGroup(header = header("Note", notes.size)) {
                for (n in notes.take(10)) item("${n.category}-${n.id}") {
                    ListRow(n.text, subtitle = "${n.category.singular} · ${Fmt.shortDay(n.date)}", multiline = true,
                        leading = { IconBadge("messageAlert", Theme.colors.poor.color) })
                }
            }
        }
    }
}

private fun onCloseAndOpen(nav: com.bcw.classeviva.ui.nav.Navigator, onClose: () -> Unit, page: Page) {
    onClose()
    nav.openInYou(page)
}

