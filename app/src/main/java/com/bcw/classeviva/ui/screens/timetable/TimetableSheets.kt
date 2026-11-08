package com.bcw.classeviva.ui.screens.timetable

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.bcw.classeviva.data.AgendaKind
import com.bcw.classeviva.data.timetable.Attachment
import com.bcw.classeviva.data.timetable.EditScope
import com.bcw.classeviva.data.timetable.EntryColor
import com.bcw.classeviva.data.timetable.EntryKind
import com.bcw.classeviva.data.timetable.EntryTint
import com.bcw.classeviva.data.timetable.LiveActivityChoice
import com.bcw.classeviva.data.timetable.PASTEL_ID
import com.bcw.classeviva.data.timetable.Palette
import com.bcw.classeviva.data.timetable.TimeOfDay
import com.bcw.classeviva.data.timetable.TimetableEntry
import com.bcw.classeviva.data.timetable.TimetableStore
import com.bcw.classeviva.data.timetable.Weekday
import com.bcw.classeviva.data.timetable.defaultTint
import com.bcw.classeviva.data.timetable.fileExtension
import com.bcw.classeviva.data.timetable.hexString
import com.bcw.classeviva.data.timetable.parseHex
import com.bcw.classeviva.ui.screens.settings.ColorPickerDialog
import com.bcw.classeviva.data.timetable.matchTests
import com.bcw.classeviva.data.timetable.mimeType
import com.bcw.classeviva.data.timetable.paletteSamples
import com.bcw.classeviva.ui.components.ConnectedGroup
import com.bcw.classeviva.ui.components.EmptyState
import com.bcw.classeviva.ui.components.FormIcon
import com.bcw.classeviva.ui.components.ListFooter
import com.bcw.classeviva.ui.components.ListGroup
import com.bcw.classeviva.ui.components.ListHeader
import com.bcw.classeviva.ui.components.ListRow
import com.bcw.classeviva.ui.components.RowCaption
import com.bcw.classeviva.ui.components.SegCell
import com.bcw.classeviva.ui.components.SelectButton
import com.bcw.classeviva.ui.components.ToggleRow
import com.bcw.classeviva.ui.components.ButtonRow
import com.bcw.classeviva.ui.components.ChevronRight
import com.bcw.classeviva.ui.icons.Icon
import com.bcw.classeviva.ui.shell.ConfirmRequest
import com.bcw.classeviva.ui.shell.DialogAction
import com.bcw.classeviva.ui.shell.LocalModel
import com.bcw.classeviva.ui.shell.LocalUi
import com.bcw.classeviva.ui.theme.Theme
import com.bcw.classeviva.util.Dates
import com.bcw.classeviva.util.Fmt
import com.bcw.classeviva.util.newUUID
import com.bcw.classeviva.util.normalized
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.util.Locale

/** Il foglio aperto dall'Orario; alcuni ne aprono un altro (dettaglio → editor). */
@Composable
fun TimetableSheetHost(sheet: TimetableSheet?, onChange: (TimetableSheet?) -> Unit) {
    // Ogni foglio riparte dal suo stato iniziale.
    key(sheet) {
        when (sheet) {
            null -> Unit
            is TimetableSheet.Detail -> DetailSheet(sheet.entryId, sheet.day, onDismiss = { onChange(null) }, onEdit = { onChange(TimetableSheet.Editor(it)) })
            is TimetableSheet.Editor -> EditorSheet(sheet, onDismiss = { onChange(null) })
            is TimetableSheet.Series -> SeriesSheet(sheet.weekday, onDismiss = { onChange(null) })
            is TimetableSheet.Shift -> ShiftSheet(sheet.weekday, onDismiss = { onChange(null) })
            TimetableSheet.Generator -> GeneratorSheet(onDismiss = { onChange(null) })
        }
    }
}

private fun formatBytes(n: Long): String = when {
    n < 1000 -> "$n byte"
    n < 1_000_000 -> "${Math.round(n / 1000.0)} KB"
    else -> String.format(Locale.ITALIAN, "%.1f MB", n / 1_000_000.0)
}

private fun attachmentIcon(name: String): String = when (fileExtension(name).lowercase(Locale.ROOT)) {
    "jpg", "jpeg", "png", "gif", "heic", "webp" -> "image"
    "pdf" -> "fileText"
    "mp3", "m4a", "wav", "mp4", "mov" -> "film"
    "ppt", "pptx", "key", "odp" -> "presentation"
    "xls", "xlsx", "numbers", "csv", "ods" -> "table"
    "txt", "rtf", "doc", "docx", "pages", "md" -> "fileText"
    else -> "file"
}

/** Campo di testo senza bordi in una voce di una lista (note). */
@Composable
private fun NoteField(value: String, placeholder: String, onChange: (String) -> Unit) {
    TextField(
        value = value, onValueChange = onChange, placeholder = { Text(placeholder) }, minLines = 2,
        modifier = Modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
            focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
        ),
    )
}

// MARK: - Dettaglio dell'evento

/**
 * Dettaglio di un evento in un giorno: le verifiche (di Classeviva o segnate a mano, per il giorno
 * o per ogni settimana), note e file del giorno e di ogni settimana. Chiudendolo, se nota, file o
 * verifica settimanale sono cambiati e ci sono altri eventi con lo stesso nome, chiede se
 * applicarli anche a loro.
 */
@Composable
private fun DetailSheet(entryId: String, day: LocalDate, onDismiss: () -> Unit, onEdit: (TimetableEntry) -> Unit) {
    val model = LocalModel.current
    val ui = LocalUi.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = model.timetable
    val original = remember { store.entry(entryId) }
    val entry = store.entry(entryId)
    val handle = rememberSheetHandle()
    // Passando all'editor non si chiede di applicare le modifiche agli altri eventi.
    var toEditor by remember { mutableStateOf(false) }

    fun finish() {
        val current = store.entry(entryId)
        if (current == null || original == null || store.isDraftEntry(current.id)) return
        val ids = { list: List<Attachment> -> list.joinToString("|") { it.id } }
        val changed = original.note != current.note || original.isWeeklyTest != current.isWeeklyTest || ids(original.attachments) != ids(current.attachments)
        val others = store.sameNameEntries(current)
        if (!changed || others.isEmpty()) return
        ui.ask(ConfirmRequest(
            title = "Applicare le modifiche anche agli altri eventi «${current.displayTitle}»?",
            message = "Nota, file e verifica di ogni settimana verranno aggiornati in tutti gli eventi con lo stesso nome. Le modifiche dei singoli giorni restano solo a questo evento.",
            confirm = DialogAction("Applica a tutti (${others.size + 1})") {
                if (original.note != current.note) store.setNote(current.note, current, EditScope.AllSameName)
                if (original.isWeeklyTest != current.isWeeklyTest) store.setTest(current.isWeeklyTest, current, EditScope.AllSameName)
                val before = original.attachments.map { it.id }.toSet()
                val now = current.attachments.map { it.id }.toSet()
                val added = current.attachments.filter { it.id !in before }
                val removed = original.attachments.filter { it.id !in now }.map { it.id }.toSet()
                for (other in store.sameNameEntries(current)) {
                    var list = other.attachments.filter { it.id !in removed }
                    for (a in added) if (list.none { it.id == a.id }) list = list + a
                    if (ids(list) != ids(other.attachments)) store.saveEntry(other.copy(attachments = list))
                }
            },
            extra = listOf(DialogAction("Solo a questo evento") {}),
        ))
    }

    val dismiss = { onDismiss(); if (!toEditor) finish() }
    if (entry == null) {
        BCWSheet("Evento", onDismiss = dismiss, handle = handle) { EmptyState("Evento eliminato", "calendarMinus") }
        return
    }
    val isDraft = store.isDraftEntry(entry.id)
    val others = if (isDraft) emptyList() else store.sameNameEntries(entry)
    val weekdayLower = Weekday.lower(entry.weekday)

    // Il file scelto va nel giorno o in ogni settimana, secondo il pulsante toccato.
    var attachScope by remember { mutableStateOf<EditScope>(EditScope.EveryWeek) }
    fun attach(uris: List<android.net.Uri>, photo: Boolean) {
        val target = store.entry(entryId) ?: return
        scope.launch {
            for (uri in uris) {
                val picked = withContext(Dispatchers.IO) { readPicked(context, uri) }
                if (picked == null) {
                    ui.ask("Errore", "Impossibile leggere il file scelto.", "OK") {}
                    return@launch
                }
                val name = if (photo) {
                    val now = Dates.now()
                    val ext = fileExtension(picked.name).ifEmpty { "jpg" }
                    "Foto ${Fmt.shortDayWithYear(now.toLocalDate())} ${String.format(Locale.ROOT, "%02d.%02d.%02d", now.hour, now.minute, now.second)}.$ext"
                } else picked.name
                try {
                    store.attach(picked.bytes, name, store.entry(entryId) ?: target, attachScope)
                } catch (e: Exception) {
                    ui.ask("Errore", "Impossibile allegare «$name»: ${e.message}", "OK") {}
                    return@launch
                }
            }
        }
    }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { attach(it, photo = false) }
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) { attach(it, photo = true) }

    fun open(a: Attachment) {
        scope.launch {
            try {
                val f = store.openAttachment(a)
                ui.open(f, a.name, mimeType(a.name))
            } catch (e: Exception) {
                ui.toast(e.message ?: "Il file non è disponibile.")
            }
        }
    }

    BCWSheet(
        "", onDismiss = dismiss, handle = handle,
        leading = { SheetCloseButton { handle.close() } },
        trailing = {
            if (!isDraft) TooltipIconButton("Modifica", "edit") { toEditor = true; handle.close { onEdit(entry) } }
            SheetDoneButton { handle.close() }
        },
    ) {
        DetailHeader(entry, day, others.size)
        if (isDraft) {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(Theme.radii.li)).background(Theme.scheme.secondaryContainer).padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon("wand", tint = Theme.scheme.onSecondaryContainer)
                Text("Questo evento fa parte della bozza generata dalle lezioni registrate. Applica la bozza per modificarlo o aggiungere note e file.",
                    style = Theme.type.style(14), color = Theme.scheme.onSecondaryContainer)
            }
            return@BCWSheet
        }
        val occurrence = store.occurrence(entry.id, day)
        if (!entry.isBreak) {
            val all = model.events(day).filter { it.kind == AgendaKind.test }
            val mine = matchTests(all, store.entriesOn(day)).matched[entry.id] ?: emptyList()
            val visible = mine.filter { store.hiddenReason(it, false) == null }
            val hidden = mine.filter { store.hiddenReason(it, false) != null }
            val dayLabel = when {
                Dates.isToday(day) -> "Verifica oggi"
                Dates.isTomorrow(day) -> "Verifica domani"
                else -> "Verifica il ${Fmt.dayMonth(day)}"
            }
            val footer = buildList {
                add(when {
                    visible.isNotEmpty() -> "Le verifiche segnate dai docenti su Classeviva compaiono da sole. Se sono sbagliate puoi nasconderle dall'orario."
                    hidden.isNotEmpty() -> "Le verifiche di Classeviva di questo giorno sono nascoste (le trovi anche in Verifiche nascoste, nel menu dell'orario)."
                    else -> "Le verifiche segnate dai docenti su Classeviva compaiono da sole; qui puoi segnarne altre a mano."
                })
                if (entry.isWeeklyTest && occurrence.isTest == false) add("Solo per questo giorno hai tolto la verifica settimanale.")
            }.joinToString(" ")
            ListGroup(header = "Verifiche", footer = footer) {
                for (t in visible) item(t.id) { SegCell { RegisterTestRow(store, t) } }
                for (t in hidden) item(t.id) { SegCell { RegisterTestRow(store, t, hidden = true) } }
                item { ToggleRow(dayLabel, store.isManualTest(entry, day), { store.setTest(it, entry, EditScope.Day(day)) }, icon = "test") }
                item { ToggleRow("Verifica ogni $weekdayLower", entry.isWeeklyTest, { store.setTest(it, entry, EditScope.EveryWeek) }, icon = "repeat") }
            }
        }
        NotesSection(
            header = Fmt.longDay(day), note = occurrence.note, placeholder = "Nota solo per questo giorno",
            onNote = { store.setNote(it, store.entry(entryId) ?: entry, EditScope.Day(day)) },
            attachments = occurrence.attachments, onOpen = ::open,
            onRemove = { store.removeAttachment(it, store.entry(entryId) ?: entry, EditScope.Day(day)) },
            onFile = { attachScope = EditScope.Day(day); filePicker.launch(arrayOf("*/*")) },
            onPhoto = { attachScope = EditScope.Day(day); photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
        )
        NotesSection(
            header = "Ogni $weekdayLower", note = entry.note, placeholder = "Nota per ogni settimana",
            footer = if (others.isNotEmpty()) "Premendo Fine potrai applicare nota, file e verifica di ogni settimana anche agli altri eventi «${entry.displayTitle}»." else null,
            onNote = { store.setNote(it, store.entry(entryId) ?: entry, EditScope.EveryWeek) },
            attachments = entry.attachments, onOpen = ::open,
            onRemove = { store.removeAttachment(it, store.entry(entryId) ?: entry, EditScope.EveryWeek) },
            onFile = { attachScope = EditScope.EveryWeek; filePicker.launch(arrayOf("*/*")) },
            onPhoto = { attachScope = EditScope.EveryWeek; photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
        )
    }
}

@Composable
private fun DetailHeader(entry: TimetableEntry, day: LocalDate, others: Int) {
    val tint = entryColor(entry)
    val people = listOf(entry.teacher, if (entry.room.isNotEmpty()) "Aula ${entry.room}" else "").filter { it.isNotEmpty() }
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).padding(horizontal = 4.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(Modifier.width(5.dp).fillMaxHeight().clip(RoundedCornerShape(3.dp)).background(tint))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(entry.displayTitle, style = Theme.type.style(28, 500, rounded = true), color = Theme.scheme.onSurface)
            Text("${Fmt.longDay(day)} · ${entry.timeRange}", style = Theme.type.style(15, 500), color = Theme.scheme.onSurface)
            Text("Ogni ${Weekday.lower(entry.weekday)} · ${TimeOfDay.duration(entry.duration)}${if (entry.isBreak) " · Intervallo" else ""}", style = Theme.type.style(14), color = Theme.scheme.onSurfaceVariant)
            if (people.isNotEmpty()) Text(people.joinToString(" · "), style = Theme.type.style(14), color = Theme.scheme.onSurfaceVariant)
            if (others > 0) RowCaption(if (others == 1) "C'è un altro evento «${entry.displayTitle}» nell'orario." else "Ci sono altri $others eventi «${entry.displayTitle}» nell'orario.")
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NotesSection(
    header: String, note: String, placeholder: String, onNote: (String) -> Unit,
    attachments: List<Attachment>, onOpen: (Attachment) -> Unit, onRemove: (Attachment) -> Unit,
    onFile: () -> Unit, onPhoto: () -> Unit, footer: String? = null,
) {
    var text by remember(header) { mutableStateOf(note) }
    var menu by remember { mutableStateOf(false) }
    ListGroup(header = header, footer = footer) {
        item("note") {
            com.bcw.classeviva.ui.components.Cell(padding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)) {
                NoteField(text, placeholder) { text = it; onNote(it) }
            }
        }
        for (a in attachments) item(a.id) {
            var attachmentMenu by remember { mutableStateOf(false) }
            Box {
                ListRow(
                    a.name, subtitle = formatBytes(a.size), leading = { FormIcon(attachmentIcon(a.name)) },
                    onClick = { onOpen(a) }, onLongClick = { attachmentMenu = true },
                    trailing = { IconButton(onClick = { onRemove(a) }) { Icon("trash", tint = Theme.scheme.error, contentDescription = "Rimuovi") } },
                )
                com.bcw.classeviva.ui.components.BCWMenu(attachmentMenu, { attachmentMenu = false }, listOf(
                    com.bcw.classeviva.ui.components.MenuItem("Apri", "download") { onOpen(a) },
                    com.bcw.classeviva.ui.components.MenuItem("Rimuovi", "trash", destructive = true) { onRemove(a) },
                ))
            }
        }
        item("attach") {
            Box {
                ButtonRow("Allega un file", "paperclip", { menu = true })
                com.bcw.classeviva.ui.components.BCWMenu(menu, { menu = false }, listOf(
                    com.bcw.classeviva.ui.components.MenuItem("Scegli un file…", "file", run = onFile),
                    com.bcw.classeviva.ui.components.MenuItem("Scegli una foto…", "image", run = onPhoto),
                ))
            }
        }
    }
}

// MARK: - Editor

/** Materie di Classeviva e nomi già usati nell'orario, da proporre come titolo. */
private data class Suggestion(val name: String, val subjectId: Long?, val teacher: String?)

private fun suggestions(model: com.bcw.classeviva.data.AppModel): List<Suggestion> {
    val seen = HashSet<String>()
    val list = mutableListOf<Suggestion>()
    for (subject in model.subjects.sortedBy { it.order }) {
        val name = subject.name.trim()
        if (name.isEmpty() || !seen.add(normalized(name))) continue
        list += Suggestion(name, subject.id, subject.teachers.firstOrNull())
    }
    for (title in model.timetable.knownTitles) {
        if (seen.add(normalized(title))) list += Suggestion(title, null, null)
    }
    return list
}

private fun usesAutomatic(store: TimetableStore, d: TimetableEntry) = store.alignment == com.bcw.classeviva.data.timetable.Alignment.automatic && !d.isBreak

private fun aligned(d: TimetableEntry): TimetableEntry {
    val length = if (d.duration < 45) 30 else 60
    val start = TimeOfDay.snapped(d.start, 30).coerceIn(0, 1440 - length)
    return d.copy(start = start, end = start + length)
}

@Composable
private fun EditorSheet(request: TimetableSheet.Editor, onDismiss: () -> Unit) {
    val model = LocalModel.current
    val ui = LocalUi.current
    val store = model.timetable
    val haptics = LocalHapticFeedback.current
    val handle = rememberSheetHandle()
    val original = request.entry
    val isNew = original == null
    var d by remember {
        var draft = original ?: run {
            val start = request.start
            val isBreak = request.kind == EntryKind.breakTime
            TimetableEntry(weekday = request.weekday, start = start, end = minOf(1440, start + if (isBreak) 10 else 60), kind = request.kind, title = if (isBreak) "Intervallo" else "")
        }
        if (usesAutomatic(store, draft) && !draft.isAutoAligned) draft = aligned(draft)
        mutableStateOf(draft)
    }
    var weekdays by remember { mutableStateOf(setOf(d.weekday)) }
    var colorPicker by remember { mutableStateOf(false) }
    var suggesting by remember { mutableStateOf(false) }
    val automatic = usesAutomatic(store, d)
    val valid = d.end > d.start && weekdays.isNotEmpty() && (d.isBreak || d.title.isNotBlank())

    fun save() {
        val e = d.copy(title = d.title.trim())
        fun commit(propagate: Boolean) {
            if (isNew) store.add(weekdays.sorted().mapIndexed { i, w -> e.copy(id = if (i == 0) e.id else newUUID(), weekday = w) })
            else store.saveEntry(e, if (propagate) original else null)
            haptics.performHapticFeedback(HapticFeedbackType.Confirm)
            handle.close()
        }
        if (original != null) {
            val others = store.sameNameEntries(original)
            val o = original
            val differs = o.title != e.title || o.subjectId != e.subjectId || o.teacher != e.teacher || o.room != e.room || o.note != e.note ||
                o.liveActivity != e.liveActivity || (o.color != e.color && e.color !is EntryColor.Series)
            if (others.isNotEmpty() && differs) {
                ui.ask(ConfirmRequest(
                    title = "Applicare le modifiche anche agli altri eventi «${o.displayTitle}»?",
                    message = "Nome, materia, docente, aula, colore, nota e lezione in corso nelle notifiche verranno aggiornati in tutti gli eventi con lo stesso nome. Orari, giorni e colori delle palette applicate a una giornata restano invariati.",
                    confirm = DialogAction("Applica a tutti (${others.size + 1})") { commit(true) },
                    extra = listOf(DialogAction("Solo a questo evento") { commit(false) }),
                ))
                return
            }
        }
        commit(false)
    }

    BCWSheet(
        if (isNew) (if (d.isBreak) "Nuovo intervallo" else "Nuovo evento") else "Modifica evento",
        onDismiss = onDismiss, handle = handle,
        leading = { SheetCloseButton { handle.close() } },
        trailing = { SheetDoneButton("Salva", enabled = valid) { save() } },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ConnectedGroup(listOf(EntryKind.lesson, EntryKind.breakTime), d.kind, { kind ->
                var n = d.copy(kind = kind)
                if (kind == EntryKind.breakTime) {
                    if (n.title.isEmpty()) n = n.copy(title = "Intervallo")
                    if (n.duration > 30) n = n.copy(end = n.start + 10)
                } else {
                    if (n.title == "Intervallo") n = n.copy(title = "")
                    if (usesAutomatic(store, n) && !n.isAutoAligned) n = aligned(n)
                }
                d = n
            }) { kind, _ -> Text(kind.title) }
            val list = if (!d.isBreak) suggestions(model) else emptyList()
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    d.title, { d = d.copy(title = it) }, modifier = Modifier.weight(1f), singleLine = true,
                    placeholder = { Text(if (d.isBreak) "Nome (es. Intervallo)" else "Materia o evento") },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                )
                if (list.isNotEmpty()) {
                    Box {
                        FilledTonalIconButton(onClick = { suggesting = true }) { Icon("list", contentDescription = "Scegli una materia") }
                        DropdownMenu(suggesting, { suggesting = false }, shape = RoundedCornerShape(Theme.radii.l), modifier = Modifier.heightIn(max = 420.dp)) {
                            for (s in list) {
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text(s.name)
                                            s.teacher?.let { Text(it, style = Theme.type.style(12), color = Theme.scheme.onSurfaceVariant) }
                                        }
                                    },
                                    onClick = {
                                        suggesting = false
                                        var n = d.copy(title = s.name)
                                        val key = normalized(s.name)
                                        val existing = store.timetable.entries.firstOrNull { it.id != n.id && it.matchKey == key }
                                        if (existing != null) {
                                            n = n.copy(subjectId = existing.subjectId ?: s.subjectId)
                                            if (n.teacher.isEmpty()) n = n.copy(teacher = existing.teacher)
                                            if (n.room.isEmpty()) n = n.copy(room = existing.room)
                                            if (n.color == null && existing.color != null && existing.color !is EntryColor.Series) n = n.copy(color = existing.color)
                                        } else {
                                            n = n.copy(subjectId = s.subjectId)
                                            if (n.teacher.isEmpty() && s.teacher != null) n = n.copy(teacher = s.teacher)
                                        }
                                        d = n
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ListHeader("Quando")
            if (isNew) {
                Text("Giorni", style = Theme.type.style(13, 600), color = Theme.scheme.onSurfaceVariant)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)) {
                    for (w in 1..7) {
                        val on = w in weekdays
                        ToggleButton(
                            checked = on,
                            onCheckedChange = {
                                haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                weekdays = if (on) weekdays - w else weekdays + w
                                if (weekdays.isNotEmpty()) d = d.copy(weekday = weekdays.min())
                            },
                            modifier = Modifier.weight(1f).heightIn(min = 44.dp),
                            shapes = when (w) {
                                1 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                                7 -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                                else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                            },
                            colors = ToggleButtonDefaults.toggleButtonColors(containerColor = Theme.scheme.surfaceContainerHigh, contentColor = Theme.scheme.onSurfaceVariant),
                            contentPadding = PaddingValues(horizontal = 2.dp, vertical = 6.dp),
                        ) { Text(Weekday.shortName(w), maxLines = 1, style = Theme.type.style(13, 600)) }
                    }
                }
            } else {
                FieldRow("Giorno") {
                    SelectButton((1..7).map { it to Weekday.name(it) }, d.weekday, { w -> d = d.copy(weekday = w); weekdays = setOf(w) })
                }
            }
            if (automatic) {
                FieldRow("Inizio") {
                    SelectButton((0 until 1440 step 30).map { it to TimeOfDay.string(it) }, d.start, { v ->
                        val length = d.duration
                        val s = minOf(v, 1440 - length)
                        d = d.copy(start = s, end = s + length)
                    })
                }
                FieldRow("Fine") { Text(TimeOfDay.string(d.end), style = Theme.type.numeral(16, 600), color = Theme.scheme.onSurface, modifier = Modifier.padding(end = 8.dp)) }
                ConnectedGroup(listOf(30, 60), d.duration, { length ->
                    val s = minOf(d.start, 1440 - length)
                    d = d.copy(start = s, end = s + length)
                }) { v, _ -> Text(if (v == 30) "30 minuti" else "1 ora") }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    LabeledTime("Inizio", d.start, Modifier.weight(1f)) { v ->
                        val length = maxOf(1, d.duration)
                        d = d.copy(start = v, end = minOf(1440, v + length))
                    }
                    LabeledTime("Fine", d.end, Modifier.weight(1f)) { v -> d = d.copy(end = if (v == 0) 1440 else v) }
                    Column(Modifier.weight(1f)) {
                        Text("Durata", style = Theme.type.style(13, 600), color = Theme.scheme.onSurfaceVariant)
                        Text(if (d.end > d.start) TimeOfDay.duration(d.duration) else "—", style = Theme.type.numeral(16, 600), color = Theme.scheme.onSurface, modifier = Modifier.padding(top = 10.dp))
                    }
                }
            }
            ListFooter(
                when {
                    d.end <= d.start -> "L'evento deve finire dopo l'inizio."
                    d.isBreak -> "Gli intervalli si posizionano sempre liberamente e compaiono sopra le lezioni, senza cambiarne la durata."
                    automatic -> "Allineamento automatico: gli eventi iniziano all'ora o alla mezz'ora e durano 30 minuti o un'ora. Per orari liberi passa all'allineamento libero in Modifica orario."
                    else -> "Allineamento libero: inizio e fine possono essere qualsiasi orario."
                },
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ListHeader("Dettagli")
            if (!d.isBreak) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(d.teacher, { d = d.copy(teacher = it) }, label = { Text("Docente") }, singleLine = true, modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words))
                    OutlinedTextField(d.room, { d = d.copy(room = it) }, label = { Text("Aula") }, singleLine = true, modifier = Modifier.weight(1f))
                }
            }
            ListGroup {
                item {
                    ListRow("Colore", leading = { FormIcon("palette") }, onClick = { colorPicker = true }, trailing = {
                        Text(colorDescription(d.color, store), style = Theme.type.style(14), color = Theme.scheme.onSurfaceVariant)
                        SwatchDot(tintColor(store.tint(d)))
                        ChevronRight()
                    })
                }
            }
            OutlinedTextField(d.note, { d = d.copy(note = it) }, label = { Text("Nota per ogni settimana") }, minLines = 2, modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences))
            ListGroup(footer = liveFooter(d)) {
                item {
                    ListRow("Lezione in corso", leading = { FormIcon("timer") }, trailing = {
                        SelectButton(listOf<Pair<LiveActivityChoice?, String>>(null to "Segui l'impostazione") + LiveActivityChoice.entries.map { it to it.title }, d.liveActivity, { d = d.copy(liveActivity = it) })
                    })
                }
            }
        }
        if (original != null) {
            FilledTonalButton(
                onClick = { deleteEntryDialog(ui, store, original.copy(title = d.title.ifEmpty { original.title })) { handle.close() } },
                modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.filledTonalButtonColors(contentColor = Theme.scheme.error),
            ) {
                Icon("trash", size = 18.dp)
                Text("Elimina evento", Modifier.padding(start = 8.dp))
            }
        }
    }
    if (colorPicker) ColorPickerSheet(d, onPick = { d = d.copy(color = it) }, onDismiss = { colorPicker = false })
}

/** Cosa fa la scelta della lezione in corso nelle notifiche (Impostazioni › Orario). */
private fun liveFooter(d: TimetableEntry): String {
    val what = if (d.isBreak) "l'intervallo" else "l'evento"
    return when (d.liveActivity) {
        LiveActivityChoice.show -> if (d.isBreak) "L'intervallo compare nella notifica della lezione in corso anche con l'impostazione spenta."
        else "La notifica della lezione in corso segue questo evento anche con l'impostazione spenta: compare mezz'ora prima."
        LiveActivityChoice.hide -> if (d.isBreak) "La notifica della lezione in corso salta l'intervallo: al suo posto resta la lezione o il tempo che manca alla successiva."
        else "La notifica della lezione in corso salta questo evento: se è tra altri eventi, al suo posto mostra il tempo che manca al successivo."
        null -> "Come in Impostazioni › Orario › Lezione in corso nelle notifiche: con l'impostazione attiva $what compare nelle notifiche e nella schermata di blocco."
    }
}

@Composable
private fun FieldRow(label: String, content: @Composable () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 52.dp).clip(RoundedCornerShape(Theme.radii.li)).background(Theme.scheme.surfaceContainer).padding(start = 16.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = Theme.type.style(16, 500), color = Theme.scheme.onSurface, modifier = Modifier.weight(1f))
        content()
    }
}

@Composable
private fun LabeledTime(label: String, minutes: Int, modifier: Modifier, onChange: (Int) -> Unit) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = Theme.type.style(13, 600), color = Theme.scheme.onSurfaceVariant)
        TimeField(minutes, onChange, Modifier.fillMaxWidth(), title = label)
    }
}

// MARK: - Colore di un evento

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColorPickerSheet(d: TimetableEntry, onPick: (EntryColor?) -> Unit, onDismiss: () -> Unit) {
    val store = LocalModel.current.timetable
    val handle = rememberSheetHandle()
    var custom by remember { mutableStateOf(0x4F7FA6) }
    var choosing by remember { mutableStateOf(false) }
    var palette by remember { mutableStateOf<Palette?>(null) }
    val c = d.color
    BCWSheet("Colore", onDismiss, handle, leading = { SheetCloseButton { handle.close() } }, trailing = { SheetDoneButton { handle.close() } }) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ListGroup {
                item {
                    ListRow(
                        "Automatico", subtitle = if (d.isBreak) "Il colore degli intervalli" else "Il colore della materia nella sezione Lezioni",
                        leading = { SwatchDot(tintColor(defaultTint(d)), 28.dp) }, selected = c == null, onClick = { onPick(null) },
                        trailing = { if (c == null) Icon("check") },
                    )
                }
            }
            if (c is EntryColor.Series) ListFooter("Colorato con la palette «${store.palette(c.paletteID)?.name ?: ""}» insieme alla sua giornata. Scegliendo un altro colore l'evento esce dalla serie.")
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ListHeader("Suggeriti")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                for (i in 0 until 12) Swatch(tintColor(EntryTint.Subject(i)), selected = c is EntryColor.Pastel && c.index == i, label = "Colore suggerito ${i + 1}") { onPick(EntryColor.Pastel(i)) }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ListHeader("Personalizzati")
            val saved = store.timetable.customColors
            if (saved.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    for (hex in saved) {
                        var menu by remember { mutableStateOf(false) }
                        Box {
                            Swatch(hexColor(hex), selected = c is EntryColor.Custom && c.hex == hex, label = "Colore personalizzato ${hexString(hex).drop(1)}", onLongClick = { menu = true }) { onPick(EntryColor.Custom(hex)) }
                            com.bcw.classeviva.ui.components.BCWMenu(menu, { menu = false }, listOf(
                                com.bcw.classeviva.ui.components.MenuItem("Elimina colore", "trash", destructive = true) { store.deleteCustomColor(hex) },
                            ))
                        }
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(40.dp).clip(CircleShape).background(hexColor(custom)).border(1.dp, Theme.scheme.outlineVariant, CircleShape)
                    .clickable(onClickLabel = "Scegli un colore") { choosing = true })
                FilledTonalButton(onClick = { choosing = true }, contentPadding = ButtonDefaults.SmallContentPadding) { Text("Scegli…") }
                FilledTonalButton(onClick = { onPick(EntryColor.Custom(custom)) }, contentPadding = ButtonDefaults.SmallContentPadding) { Text("Usa") }
                TextButton(onClick = { store.saveCustomColor(custom); onPick(EntryColor.Custom(custom)) }) {
                    Icon("add", size = 18.dp); Text("Salva", Modifier.padding(start = 4.dp))
                }
            }
            ListFooter("I colori salvati restano disponibili per tutti gli eventi. Tieni premuto su un colore per eliminarlo.")
        }
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ListHeader("Dalle palette")
            for (p in store.allPalettes) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(p.name, style = Theme.type.style(15, 600), color = Theme.scheme.onSurface, modifier = Modifier.weight(1f).padding(start = 4.dp))
                        if (!p.isBuiltIn) TextButton(onClick = { palette = p }) { Text("Modifica") }
                    }
                    val samples: List<Pair<Color, EntryColor>> = if (p.id == PASTEL_ID) p.colors.indices.map { tintColor(EntryTint.Subject(it)) to EntryColor.Pastel(it) }
                    else paletteSamples(p, 6).map { hexColor(it) to EntryColor.Custom(it) }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        for ((color, value) in samples) Swatch(color, selected = c == value, size = 30, label = p.name) { onPick(value) }
                    }
                }
            }
            FilledTonalButton(onClick = { palette = Palette(newUUID(), "", listOf(0xA6D8F2, 0x2F6FB5), true) }, contentPadding = ButtonDefaults.SmallContentPadding) {
                Icon("plus", size = 18.dp); Text("Nuova palette", Modifier.padding(start = 6.dp))
            }
            ListFooter("Tocca un colore per usarlo. Per colorare un'intera giornata con una sfumatura usa \"Colora una giornata\" nel menu dell'orario.")
        }
    }
    if (choosing) ColorPickerDialog(hexString(custom), onDismiss = { choosing = false }, title = "Scegli un colore", confirmLabel = "OK") { hex -> parseHex(hex)?.let { custom = it } }
    palette?.let { p -> PaletteEditorSheet(p, onDismiss = { palette = null }) }
}

/** Un colore da scegliere: un cerchio, con il segno di spunta se è quello scelto. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Swatch(color: Color, selected: Boolean, label: String, size: Int = 36, onLongClick: (() -> Unit)? = null, onClick: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    Box(
        Modifier.size(size.dp).clip(CircleShape).background(color)
            .then(if (selected) Modifier.border(3.dp, Theme.scheme.onSurface, CircleShape) else Modifier)
            .combinedClickable(
                onClickLabel = label, onClick = { haptics.performHapticFeedback(HapticFeedbackType.SegmentTick); onClick() },
                onLongClick = onLongClick?.let { { haptics.performHapticFeedback(HapticFeedbackType.LongPress); it() } },
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) Icon("check", size = (size * 0.5).dp, tint = if (color.luminance() > 0.55f) Color.Black else Color.White)
    }
}

private fun Color.luminance(): Float = 0.2126f * red + 0.7152f * green + 0.0722f * blue

/** Striscia dei colori di una palette (sfumatura o colori separati). */
@Composable
fun PaletteStrip(p: Palette, modifier: Modifier = Modifier, count: Int = 12) {
    val colors = when {
        p.id == PASTEL_ID -> p.colors.indices.map { tintColor(EntryTint.Subject(it)) }
        p.isGradient && p.colors.size > 1 -> paletteSamples(p, count).map { hexColor(it) }
        else -> p.colors.map { hexColor(it) }
    }
    val gapped = !(p.isGradient && p.colors.size > 1) || p.id == PASTEL_ID
    Row(modifier.height(14.dp).clip(RoundedCornerShape(4.dp)), horizontalArrangement = Arrangement.spacedBy(if (gapped) 2.dp else 0.dp)) {
        for (c in colors) Box(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(if (gapped) 3.dp else 0.dp)).background(c))
    }
}

// MARK: - Palette

@Composable
fun PaletteEditorSheet(initial: Palette, onDismiss: () -> Unit) {
    val ui = LocalUi.current
    val store = LocalModel.current.timetable
    val handle = rememberSheetHandle()
    var p by remember { mutableStateOf(initial) }
    var editing by remember { mutableStateOf<Int?>(null) }
    val isNew = store.timetable.customPalettes.none { it.id == initial.id }
    BCWSheet(
        if (isNew) "Nuova palette" else "Modifica palette", onDismiss, handle,
        leading = { SheetCloseButton { handle.close() } },
        trailing = {
            SheetDoneButton("Salva", enabled = p.colors.size >= 2) {
                store.savePalette(p.copy(name = p.name.trim().ifEmpty { "La mia palette" }))
                handle.close()
            }
        },
    ) {
        OutlinedTextField(p.name, { p = p.copy(name = it) }, label = { Text("Nome della palette") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences))
        ListGroup(footer = if (p.isGradient) "Con la sfumatura, applicando la palette a una serie di eventi i colori intermedi vengono calcolati in base al numero di eventi."
        else "Senza sfumatura gli eventi di una serie prendono i colori uno dopo l'altro.") {
            item { ToggleRow("Sfumatura", p.isGradient, { p = p.copy(isGradient = it) }, icon = "palette") }
            item { SegCell { PaletteStrip(p, Modifier.fillMaxWidth()) } }
        }
        ListGroup(header = "Colori") {
            p.colors.forEachIndexed { i, hex ->
                item("c$i") {
                    ListRow(
                        "Colore ${i + 1}", subtitle = hexString(hex),
                        leading = { Box(Modifier.size(32.dp).clip(CircleShape).background(hexColor(hex)).border(1.dp, Theme.scheme.outlineVariant, CircleShape)) },
                        onClick = { editing = i },
                        trailing = {
                            if (i > 0) IconButton(onClick = { p = p.copy(colors = p.colors.toMutableList().also { l -> val t = l[i - 1]; l[i - 1] = l[i]; l[i] = t }) }) {
                                Icon("chevronUp", contentDescription = "Sposta su")
                            }
                            if (p.colors.size > 2) IconButton(onClick = { p = p.copy(colors = p.colors.filterIndexed { j, _ -> j != i }) }) {
                                Icon("minusCircle", tint = Theme.scheme.error, contentDescription = "Rimuovi colore ${i + 1}")
                            }
                        },
                    )
                }
            }
            if (p.colors.size < 12) item("add") { ButtonRow("Aggiungi colore", "plus", { p = p.copy(colors = p.colors + (p.colors.lastOrNull() ?: 0x8FA3B8)) }) }
        }
        if (!isNew) {
            FilledTonalButton(
                onClick = {
                    ui.ask("Eliminare la palette «${p.name}»?", "Gli eventi colorati con questa palette torneranno al colore automatico.", "Elimina", destructive = true) {
                        store.deletePalette(p.id)
                        handle.close()
                    }
                },
                modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.filledTonalButtonColors(contentColor = Theme.scheme.error),
            ) {
                Icon("trash", size = 18.dp)
                Text("Elimina palette", Modifier.padding(start = 8.dp))
            }
        }
    }
    editing?.let { i ->
        if (i < p.colors.size) ColorPickerDialog(hexString(p.colors[i]), onDismiss = { editing = null }, title = "Colore ${i + 1}", confirmLabel = "OK") { hex ->
            parseHex(hex)?.let { v -> p = p.copy(colors = p.colors.toMutableList().also { it[i] = v }) }
        }
    }
}

/** Le palette in una fila che scorre di lato: la scelta resta in vista. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PaletteCards(selectedId: String, onSelect: (String) -> Unit, onEdit: (Palette) -> Unit, onNew: () -> Unit) {
    val store = LocalModel.current.timetable
    val scroll = rememberScrollState()
    val density = androidx.compose.ui.platform.LocalDensity.current
    // La palette scelta resta in vista (all'apertura e quando cambia).
    var shown by remember { mutableStateOf<String?>(null) }
    androidx.compose.runtime.LaunchedEffect(selectedId, scroll.viewportSize) {
        val index = store.allPalettes.indexOfFirst { it.id == selectedId }
        if (index < 0 || scroll.viewportSize == 0) return@LaunchedEffect
        val card = with(density) { 132.dp.toPx() }
        val left = with(density) { index * (132.dp + 10.dp).toPx() }
        val target = (left - (scroll.viewportSize - card) / 2).toInt().coerceIn(0, scroll.maxValue)
        if (shown == null) scroll.scrollTo(target) else scroll.animateScrollTo(target)
        shown = selectedId
    }
    Row(Modifier.fillMaxWidth().horizontalScroll(scroll), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        for (p in store.allPalettes) {
            val selected = p.id == selectedId
            Column(
                Modifier.width(132.dp).clip(RoundedCornerShape(Theme.radii.l))
                    .background(if (selected) Theme.scheme.secondaryContainer else Theme.scheme.surfaceContainer)
                    .then(if (selected) Modifier.border(2.dp, Theme.scheme.primary, RoundedCornerShape(Theme.radii.l)) else Modifier)
                    .combinedClickable(onClick = { onSelect(p.id) }, onLongClick = if (p.isBuiltIn) null else ({ onEdit(p) }))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PaletteStrip(p, Modifier.fillMaxWidth())
                Text(p.name, style = Theme.type.style(13, 600), color = if (selected) Theme.scheme.onSecondaryContainer else Theme.scheme.onSurface, maxLines = 1)
            }
        }
        Column(
            Modifier.width(96.dp).clip(RoundedCornerShape(Theme.radii.l)).background(Theme.scheme.surfaceContainer).combinedClickable(onClick = onNew).padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon("plus", tint = Theme.scheme.primary)
            Text("Nuova", style = Theme.type.style(13, 600), color = Theme.scheme.primary, textAlign = TextAlign.Center)
        }
    }
}
