package com.bcw.classeviva.ui.screens.timetable

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bcw.classeviva.data.AgendaEvent
import com.bcw.classeviva.data.AgendaKind
import com.bcw.classeviva.data.timetable.HiddenReason
import com.bcw.classeviva.data.timetable.RulePeriod
import com.bcw.classeviva.data.timetable.TestRules
import com.bcw.classeviva.data.timetable.TimetableStore
import com.bcw.classeviva.data.timetable.Weekday
import com.bcw.classeviva.data.timetable.matchTests
import com.bcw.classeviva.ui.components.ChevronRight
import com.bcw.classeviva.ui.components.DatePickerSheet
import com.bcw.classeviva.ui.components.FormIcon
import com.bcw.classeviva.ui.components.ListGroup
import com.bcw.classeviva.ui.components.ListRow
import com.bcw.classeviva.ui.components.RowCaption
import com.bcw.classeviva.ui.components.SegCell
import com.bcw.classeviva.ui.components.ToggleRow
import com.bcw.classeviva.ui.icons.Icon
import com.bcw.classeviva.ui.shell.LocalModel
import com.bcw.classeviva.ui.shell.PageScaffold
import com.bcw.classeviva.ui.shell.block
import com.bcw.classeviva.ui.theme.Theme
import com.bcw.classeviva.util.Dates
import com.bcw.classeviva.util.Fmt
import com.bcw.classeviva.util.byName
import com.bcw.classeviva.util.capFirst
import com.bcw.classeviva.util.newUUID
import com.bcw.classeviva.util.normalized
import java.time.LocalDate

private data class HiddenItem(val test: AgendaEvent, val unmatched: Boolean, val reason: HiddenReason?)

/** Le verifiche di Classeviva, con il motivo per cui sono nascoste (o `null` se si vedono). */
private fun hiddenItems(model: com.bcw.classeviva.data.AppModel): List<HiddenItem> {
    val store = model.timetable
    return model.agenda.filter { it.kind == AgendaKind.test }.sortedBy { it.begin }.map { test ->
        val unmatched = matchTests(listOf(test), store.entriesOn(test.day)).unmatched.isNotEmpty()
        HiddenItem(test, unmatched, store.hiddenReason(test, unmatched))
    }
}

/**
 * Verifiche nascoste dall'orario: quelle nascoste a mano o dalle regole (prossime e passate),
 * quelle mostrate nonostante le regole, e le regole per nasconderle in automatico.
 */
@Composable
fun HiddenTestsScreen() {
    val model = LocalModel.current
    val store = model.timetable
    var showsPast by rememberSaveable { mutableStateOf(false) }
    var rules by remember { mutableStateOf(false) }
    val items = hiddenItems(model)
    val today = Dates.today()
    val hidden = items.filter { it.reason != null }
    val upcoming = hidden.filter { !it.test.day.isBefore(today) }
    val past = hidden.filter { it.test.day.isBefore(today) }.reversed()
    val overridden = items.filter { it.reason == null && it.test.id in store.timetable.shownTests && store.ruleReason(it.test, it.unmatched) != null }
    val count = store.timetable.testRules.count

    PageScaffold(title = "Verifiche nascoste", gap = 20.dp) {
        block("rules") {
            ListGroup(footer = "Le regole nascondono da sole le verifiche segnate dai docenti su Classeviva, ad esempio quelle di un docente, di un giorno della settimana o di un periodo. Puoi sempre mostrarne una a mano.") {
                item {
                    ListRow("Nascondi in automatico", leading = { FormIcon("filter") }, onClick = { rules = true }, trailing = {
                        Text(when (count) { 0 -> "Nessuna"; 1 -> "1 regola"; else -> "$count regole" }, style = Theme.type.style(14), color = Theme.scheme.onSurfaceVariant)
                        ChevronRight()
                    })
                }
            }
        }
        block("upcoming") {
            ListGroup(header = "Prossime") {
                if (upcoming.isEmpty()) item { SegCell { Text("Nessuna verifica nascosta nei prossimi giorni.", style = Theme.type.style(15), color = Theme.scheme.onSurfaceVariant) } }
                for (i in upcoming) item(i.test.id) { SegCell { HiddenRow(store, i) } }
            }
        }
        if (past.isNotEmpty()) {
            block("past") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (showsPast) ListGroup(header = "Passate") { for (i in past) item(i.test.id) { SegCell { HiddenRow(store, i) } } }
                    TextButton(onClick = { showsPast = !showsPast }) {
                        Text(if (showsPast) "Nascondi le verifiche passate" else "Mostra le verifiche passate (${past.size})")
                    }
                }
            }
        }
        if (overridden.isNotEmpty()) {
            block("overridden") {
                ListGroup(header = "Mostrate nonostante le regole", footer = "Le hai mostrate a mano. \"Nascondi\" le restituisce alle regole.") {
                    for (i in overridden) item(i.test.id) { SegCell { HiddenRow(store, i, overridden = true) } }
                }
            }
        }
    }
    if (rules) RulesSheet(onDismiss = { rules = false })
}

@Composable
private fun HiddenRow(store: TimetableStore, item: HiddenItem, overridden: Boolean = false) {
    val test = item.test
    val (icon, line) = when {
        overridden -> "eye" to "Mostrata a mano · regola «${store.ruleReason(test, item.unmatched) ?: ""}»"
        item.reason is HiddenReason.Manual -> "hand" to "Nascosta a mano"
        else -> "filter" to "Nascosta dalla regola «${(item.reason as? HiddenReason.Rule)?.rule ?: ""}»"
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon("test", tint = Theme.scheme.primary, modifier = Modifier.padding(top = 2.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(Fmt.longDay(test.day), style = Theme.type.style(12, 650), color = Theme.scheme.primary)
            Text(test.title, style = Theme.type.style(15, 600), color = Theme.scheme.onSurface)
            if (test.notes.isNotEmpty()) Text(test.notes, style = Theme.type.style(14), color = Theme.scheme.onSurface, maxLines = 3, overflow = TextOverflow.Ellipsis)
            if (test.authorName.isNotEmpty()) RowCaption(test.authorName)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(icon, size = 14.dp, tint = Theme.scheme.onSurfaceVariant)
                RowCaption(line)
            }
        }
        if (overridden) TextButton(onClick = { store.resetTestOverride(test.id) }) { Text("Nascondi") }
        else TextButton(onClick = { store.setTestHidden(false, test, item.unmatched) }) { Text("Mostra") }
    }
}

/** Le regole per nascondere in automatico le verifiche: giorni, docenti, materie, periodi, parole, senza lezione. */
@Composable
private fun RulesSheet(onDismiss: () -> Unit) {
    val model = LocalModel.current
    val store = model.timetable
    val handle = rememberSheetHandle()
    val rules = store.timetable.testRules
    var from by remember { mutableStateOf(Dates.today()) }
    var to by remember { mutableStateOf(Dates.today().plusDays(6)) }
    var picking by remember { mutableStateOf<String?>(null) }
    var word by remember { mutableStateOf("") }
    fun update(change: (TestRules) -> TestRules) = store.setTestRules(change(store.timetable.testRules))

    val byRule = hiddenItems(model).count { it.reason is HiddenReason.Rule }
    val tests = model.agenda.filter { it.kind == AgendaKind.test }
    val teachers = (tests.map { it.authorName }.filter { it.isNotEmpty() } + rules.teachers).distinct().sortedWith { a, b -> byName(a, b) }
    val subjects = LinkedHashMap<String, String>()
    for (t in tests) t.subjectName?.let { subjects[normalized(it)] = it }
    for (key in rules.subjects) if (key !in subjects) subjects[key] = key.capFirst()
    val subjectList = subjects.entries.sortedWith { a, b -> byName(a.value, b.value) }

    fun addWord() {
        val w = word.trim()
        if (w.isEmpty()) return
        update { r -> if (w in r.keywords) r else r.copy(keywords = r.keywords + w) }
        word = ""
    }

    BCWSheet("Nascondi in automatico", onDismiss, handle, leading = { SheetCloseButton { handle.close() } }, trailing = { SheetDoneButton { handle.close() } }) {
        ListGroup(footer = "Una verifica è nascosta se soddisfa almeno una regola. Le trovi tutte in Verifiche nascoste, da dove puoi mostrarle di nuovo.") {
            item {
                ListRow(
                    when (byRule) { 0 -> "Le regole non nascondono nessuna verifica."; 1 -> "Le regole nascondono 1 verifica."; else -> "Le regole nascondono $byRule verifiche." },
                    leading = { FormIcon("eyeOff") },
                )
            }
        }
        ListGroup(header = "Giorni della settimana") {
            for (w in 1..7) item(w) {
                ToggleRow(Weekday.name(w), w in rules.weekdays, { on -> update { r -> r.copy(weekdays = if (on) (r.weekdays + w).distinct() else r.weekdays - w) } })
            }
        }
        ListGroup(header = "Docenti", footer = "Nasconde le verifiche segnate da questi docenti.") {
            if (teachers.isEmpty()) item { SegCell { Text("Nessun docente ha ancora segnato verifiche.", style = Theme.type.style(15), color = Theme.scheme.onSurfaceVariant) } }
            for (t in teachers) item(t) {
                ToggleRow(t, t in rules.teachers, { on -> update { r -> r.copy(teachers = if (on) (r.teachers + t).distinct() else r.teachers - t) } })
            }
        }
        if (subjectList.isNotEmpty()) {
            ListGroup(header = "Materie") {
                for ((key, name) in subjectList) item(key) {
                    ToggleRow(name, key in rules.subjects, { on -> update { r -> r.copy(subjects = if (on) (r.subjects + key).distinct() else r.subjects - key) } })
                }
            }
        }
        ListGroup(header = "Periodi", footer = "Nasconde le verifiche di questi periodi (estremi compresi), ad esempio durante una gita o uno scambio.") {
            for (p in rules.periods) item(p.id) {
                ListRow("${Fmt.shortDayWithYear(p.start)} – ${Fmt.shortDayWithYear(p.end)}", leading = { FormIcon("calendar") }, trailing = {
                    IconButton(onClick = { update { r -> r.copy(periods = r.periods.filter { it.id != p.id }) } }) {
                        Icon("minusCircle", tint = Theme.scheme.error, contentDescription = "Rimuovi il periodo")
                    }
                })
            }
            item("from") { DateRow("Dal", from) { picking = "from" } }
            item("to") { DateRow("Al", to) { picking = "to" } }
            item("add") {
                com.bcw.classeviva.ui.components.ButtonRow("Aggiungi il periodo", "plus", {
                    update { r -> r.copy(periods = r.periods + RulePeriod(newUUID(), from, if (to < from) from else to)) }
                })
            }
        }
        ListGroup(header = "Parole", footer = "Nasconde le verifiche il cui testo contiene una di queste parole (ad esempio \"recupero\").") {
            rules.keywords.forEachIndexed { i, w ->
                item("w$i") {
                    ListRow(w, leading = { FormIcon("textSearch") }, trailing = {
                        IconButton(onClick = { update { r -> r.copy(keywords = r.keywords.filterIndexed { j, _ -> j != i }) } }) {
                            Icon("minusCircle", tint = Theme.scheme.error, contentDescription = "Rimuovi la parola")
                        }
                    })
                }
            }
            item("new") {
                SegCell {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            word, { word = it }, placeholder = { Text("Parola o frase") }, singleLine = true, modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { addWord() }),
                        )
                        FilledTonalButton(onClick = { addWord() }, enabled = word.isNotBlank()) { Text("Aggiungi") }
                    }
                }
            }
        }
        ListGroup(footer = "Nasconde le verifiche di materie che quel giorno non sono nel tuo orario.") {
            item { ToggleRow("Verifiche senza lezione in orario", rules.unmatched, { on -> update { it.copy(unmatched = on) } }) }
        }
        if (rules.count > 0) {
            FilledTonalButton(
                onClick = { store.setTestRules(TestRules()) }, modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.filledTonalButtonColors(contentColor = Theme.scheme.error),
            ) {
                Icon("trash", size = 18.dp)
                Text("Elimina tutte le regole", Modifier.padding(start = 8.dp))
            }
        }
    }
    when (picking) {
        "from" -> DatePickerSheet(from, onDismiss = { picking = null }, onPick = { day -> from = day; if (to < day) to = day })
        "to" -> DatePickerSheet(to, onDismiss = { picking = null }, onPick = { to = it }, min = from)
    }
}

@Composable
private fun DateRow(label: String, day: LocalDate, onClick: () -> Unit) {
    ListRow(label, leading = { FormIcon("calendarDay") }, trailing = {
        FilledTonalButton(onClick = onClick, contentPadding = ButtonDefaults.SmallContentPadding) {
            Icon("calendarMonth", size = 18.dp)
            Text(Fmt.longDay(day), Modifier.padding(start = 6.dp))
        }
    })
}
