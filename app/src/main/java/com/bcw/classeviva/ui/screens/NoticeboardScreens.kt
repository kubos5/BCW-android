package com.bcw.classeviva.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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
import com.bcw.classeviva.data.Notice
import com.bcw.classeviva.data.NoticeDetail
import com.bcw.classeviva.util.Fmt
import com.bcw.classeviva.util.containsText
import com.bcw.classeviva.ui.components.BCWFilterChip
import com.bcw.classeviva.ui.components.Card
import com.bcw.classeviva.ui.components.Cell
import com.bcw.classeviva.ui.components.ChipRow
import com.bcw.classeviva.ui.components.EmptyState
import com.bcw.classeviva.ui.components.Eyebrow
import com.bcw.classeviva.ui.components.IconBadge
import com.bcw.classeviva.ui.components.ListGroup
import com.bcw.classeviva.ui.components.ListRow
import com.bcw.classeviva.ui.components.LoadingCard
import com.bcw.classeviva.ui.components.Pill
import com.bcw.classeviva.ui.components.StatusBanner
import com.bcw.classeviva.ui.components.StatusLine
import com.bcw.classeviva.ui.icons.Icon
import com.bcw.classeviva.ui.nav.Page
import com.bcw.classeviva.ui.shell.LocalModel
import com.bcw.classeviva.ui.shell.LocalNavigator
import com.bcw.classeviva.ui.shell.LocalUi
import com.bcw.classeviva.ui.shell.PageScaffold
import com.bcw.classeviva.ui.shell.block
import com.bcw.classeviva.ui.theme.Theme
import kotlinx.coroutines.launch

/** Campo di ricerca di una pagina (Bacheca, Agenda, Materiale). */
@Composable
fun LocalSearchField(value: String, placeholder: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value, onValueChange = onChange, singleLine = true, modifier = Modifier.fillMaxWidth(),
        placeholder = { Text(placeholder) }, leadingIcon = { Icon("search") },
        trailingIcon = if (value.isNotEmpty()) ({ androidx.compose.material3.IconButton(onClick = { onChange("") }) { Icon("x", contentDescription = "Cancella") } }) else null,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(28.dp),
    )
}

/** Bacheca: comunicazioni con filtri e ricerca. */
@Composable
fun NoticeboardScreen() {
    val model = LocalModel.current
    val nav = LocalNavigator.current
    var search by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf<String?>(null) }
    var onlyUnread by rememberSaveable { mutableStateOf(false) }
    val categories = model.notices.map { it.category }.distinct().sortedWith { a, b -> com.bcw.classeviva.util.byName(a, b) }
    val items = model.notices.filter { (category == null || it.category == category) && (!onlyUnread || !it.isRead) && it.title.containsText(search) }
    PageScaffold(title = "Bacheca", onRefresh = { model.loadNotices() }) {
        block("search") { LocalSearchField(search, "Cerca nelle comunicazioni") { search = it } }
        block("chips") {
            ChipRow {
                BCWFilterChip("Da leggere", onlyUnread, { onlyUnread = !onlyUnread }, icon = "circleFill")
                BCWFilterChip("Tutte", category == null, { category = null })
                for (c in categories) BCWFilterChip(c, category == c, { category = if (category == c) null else c })
            }
        }
        if (items.isEmpty()) {
            block("empty") {
                if (model.notices.isNotEmpty()) EmptyState("Nessun risultato", "megaphone", "Prova a cambiare i filtri.")
                else EmptyState("Bacheca vuota", "megaphone", "Le comunicazioni della scuola appariranno qui.")
            }
            return@PageScaffold
        }
        block("list") {
            ListGroup {
                for (n in items) item(n.id) { NoticeRow(n) { nav.push(Page.Notice(n.id)) } }
            }
        }
    }
}

@Composable
private fun NoticeRow(notice: Notice, onClick: () -> Unit) {
    Cell(onClick = onClick) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.padding(top = 7.dp).size(8.dp).clip(CircleShape).background(if (notice.isRead) androidx.compose.ui.graphics.Color.Transparent else Theme.scheme.primary))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(notice.title, style = Theme.type.style(16, if (notice.isRead) 450 else 650), color = Theme.scheme.onSurface, maxLines = 3, overflow = TextOverflow.Ellipsis)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(notice.category, style = Theme.type.style(13, 600), color = Theme.scheme.primary)
                    notice.publishedAt?.let { Text(Fmt.shortDayWithYear(it.toLocalDate()), style = Theme.type.style(13), color = Theme.scheme.onSurfaceVariant) }
                    Box(Modifier.weight(1f))
                    if (notice.hasAttachments || notice.attachments.isNotEmpty()) Icon("paperclip", size = 16.dp, tint = Theme.scheme.onSurfaceVariant)
                    if (notice.requiresAction) Pill(if (notice.needsSign) "Da firmare" else if (notice.needsJoin) "Adesione" else "Risposta", color = Theme.colors.fair.color, small = true)
                }
            }
        }
    }
}

/** Comunicazione aperta: testo, allegati e la richiesta della scuola (adesione, firma, risposta). */
@Composable
fun NoticeDetailScreen(id: Long) {
    val model = LocalModel.current
    val ui = LocalUi.current
    val scope = rememberCoroutineScope()
    val notice = model.notices.firstOrNull { it.id == id }
    var detail by remember { mutableStateOf<NoticeDetail?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    var downloading by remember { mutableStateOf<Int?>(null) }
    var reply by rememberSaveable { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    LaunchedEffect(id) {
        if (notice == null) return@LaunchedEffect
        try { detail = model.openNotice(notice) } catch (e: Exception) { error = e.message } finally { loading = false }
    }
    fun perform(join: Boolean? = null, sign: Boolean? = null, text: String? = null) {
        val n = notice ?: return
        scope.launch {
            sending = true
            try {
                detail = model.openNotice(n, join, sign, text)
                error = null
            } catch (e: Exception) {
                error = e.message
            } finally {
                sending = false
            }
        }
    }
    PageScaffold(title = "Comunicazione", gap = 18.dp) {
        if (notice == null) {
            block("missing") { EmptyState("Comunicazione non trovata", "megaphone") }
            return@PageScaffold
        }
        block("header") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Eyebrow(notice.category, Theme.scheme.primary)
                SelectionContainer { Text(detail?.title?.trim()?.ifEmpty { null } ?: notice.title, style = Theme.type.style(24, 500, rounded = true), color = Theme.scheme.onSurface) }
                notice.publishedAt?.let { Text("Pubblicata ${Fmt.longDay(it.toLocalDate()).lowercase()} ${it.year}", style = Theme.type.style(13), color = Theme.scheme.onSurfaceVariant) }
                if (notice.validFrom != null && notice.validTo != null) Text("Valida dal ${Fmt.shortDay(notice.validFrom)} al ${Fmt.shortDayWithYear(notice.validTo)}", style = Theme.type.style(13), color = Theme.scheme.onSurfaceVariant)
            }
        }
        if (loading) block("loading") { LoadingCard() }
        error?.let { block("error") { StatusBanner(it, "alertTriangle") } }
        val text = detail?.text?.trim()
        if (!loading && !text.isNullOrEmpty()) block("text") { Card { SelectionContainer { Text(text, style = Theme.type.style(15, 400, lineHeight = androidx.compose.ui.unit.TextUnit.Unspecified), color = Theme.scheme.onSurface) } } }
        if (notice.attachments.isNotEmpty()) block("attachments") {
            ListGroup(header = "Allegati") {
                for (a in notice.attachments) item(a.number) {
                    ListRow(a.fileName, leading = { IconBadge("fileText", Theme.colors.neutral.color) }, onClick = {
                        if (downloading != null) return@ListRow
                        scope.launch {
                            downloading = a.number
                            try { ui.open(model, model.downloadAttachment(a, notice)) } catch (e: Exception) { error = e.message } finally { downloading = null }
                        }
                    }, trailing = {
                        if (downloading == a.number) LoadingIndicator(Modifier.size(28.dp)) else Icon("download", tint = Theme.scheme.primary)
                    })
                }
            }
        }
        val d = detail
        if ((notice.needsJoin || notice.needsSign || notice.needsReply) && d != null) block("actions") {
            Card {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Eyebrow("Richiesta della scuola")
                    if (notice.needsJoin) {
                        if (d.joined) StatusLine("Hai aderito", "checkSealFill", Theme.colors.good.color)
                        else Button(onClick = { ui.ask("Confermi?", "La tua adesione verrà inviata alla scuola.", "Aderisci") { perform(join = true) } }, enabled = !sending) {
                            Icon("thumbsUp", size = 18.dp); Text("Aderisci", modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                    if (notice.needsSign) {
                        if (d.signed) StatusLine("Presa visione confermata", "checkSealFill", Theme.colors.good.color)
                        else Button(onClick = { ui.ask("Confermi?", "Confermerai la presa visione della comunicazione.", "Conferma e firma") { perform(sign = true) } }, enabled = !sending) {
                            Icon("signature", size = 18.dp); Text("Conferma presa visione", modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                    if (notice.needsReply) {
                        if (d.replyText != null) Text("La tua risposta: ${d.replyText}", style = Theme.type.style(14), color = Theme.scheme.onSurfaceVariant)
                        else {
                            OutlinedTextField(reply, { reply = it }, Modifier.fillMaxWidth(), placeholder = { Text("Scrivi una risposta") }, minLines = 3)
                            FilledTonalButton(onClick = { perform(text = reply) }, enabled = reply.isNotBlank() && !sending) {
                                Icon("send", size = 18.dp); Text("Invia risposta", modifier = Modifier.padding(start = 8.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}
