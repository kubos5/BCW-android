package com.bcw.classeviva.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.bcw.classeviva.data.AppModel
import com.bcw.classeviva.data.BCWLink
import com.bcw.classeviva.data.BCWServer
import com.bcw.classeviva.data.CloudSync
import com.bcw.classeviva.data.SyncCategory
import com.bcw.classeviva.data.SyncStatus
import com.bcw.classeviva.data.accountColorID
import com.bcw.classeviva.data.accountInitials
import com.bcw.classeviva.util.Dates
import com.bcw.classeviva.util.Fmt
import com.bcw.classeviva.ui.components.Avatar
import com.bcw.classeviva.ui.components.ButtonRow
import com.bcw.classeviva.ui.components.ChevronRight
import com.bcw.classeviva.ui.components.FormIcon
import com.bcw.classeviva.ui.components.IconBadge
import com.bcw.classeviva.ui.components.ListGroup
import com.bcw.classeviva.ui.components.ListRow
import com.bcw.classeviva.ui.components.ToggleRow
import com.bcw.classeviva.ui.components.subjectColor
import com.bcw.classeviva.ui.icons.Icon
import com.bcw.classeviva.ui.shell.LocalModel
import com.bcw.classeviva.ui.shell.LocalUi
import com.bcw.classeviva.ui.shell.PageScaffold
import com.bcw.classeviva.ui.shell.block
import com.bcw.classeviva.ui.theme.Theme
import kotlinx.coroutines.launch

fun syncStatusText(sync: CloudSync): String = when (sync.status) {
    SyncStatus.syncing -> "In corso…"
    SyncStatus.ok -> sync.lastSync?.let { "Aggiornata alle ${Fmt.time(it)}" } ?: "Aggiornata"
    SyncStatus.offline -> "Offline"
    SyncStatus.error -> "Non riuscita"
    null -> "Attiva"
}

suspend fun syncNow(model: AppModel) {
    model.refreshCloud(force = true)
    model.sync.flush()
}

/** Interruttore di una voce di "Da sincronizzare". */
@Composable
fun SyncCategoryRow(category: SyncCategory, title: String = category.title, icon: String = category.icon) {
    val model = LocalModel.current
    val p = model.preferences
    ToggleRow(title, p.syncs(category), { on ->
        if (p.syncs(category) != on) {
            p.setSyncs(category, on)
            model.sync.categoryChanged(category, on)
        }
    }, icon = icon, subtitle = if (title == category.title) category.subtitle else null)
}

@Composable
fun AccountScreen() {
    PageScaffold(title = "Account", gap = 24.dp) { block("content") { AccountsContent() } }
}

/** Account Classeviva salvati, abbinamenti agli account BCW, account BCW e sincronizzazione. */
@Composable
fun AccountsContent() {
    val model = LocalModel.current
    Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
        AccountsSection()
        LinksSection()
        CloudSection()
        if (model.isDemo) ListGroup { item { ListRow(model.demoExitMessage, multiline = true) } }
    }
}

/** Righe degli account salvati: tocco = passa a quell'account; il cestino lo rimuove. */
@Composable
fun AccountsSection(showsHeader: Boolean = true, onSwitched: () -> Unit = {}) {
    val model = LocalModel.current
    val ui = LocalUi.current
    val scope = rememberCoroutineScope()
    val cloud = model.isCloud
    val footer = if (cloud) "Gli account Classeviva abbinati all'account BCW ${model.cloudUser?.username ?: ""}: li ritrovi accedendo con l'account BCW su qualsiasi dispositivo.${if (model.accounts.size > 1) " Tocca un account per passare ad esso." else ""}"
    else if (model.accounts.size > 1) "Tocca un account per passare ad esso. Usa il cestino per rimuoverlo."
    else "Puoi aggiungere altri account Classeviva (ad esempio quelli di fratelli o sorelle) e passare dall'uno all'altro."
    ListGroup(header = if (showsHeader) (if (cloud) "Account Classeviva abbinati" else "Account") else null, footer = footer) {
        for (a in model.accounts) item(a.id) {
            val active = !model.isDemo && a.id == model.activeAccountID
            ListRow(
                a.name, subtitle = a.school ?: a.credentials.username, selected = active,
                onClick = { if (!active) { model.switchAccount(a.id); onSwitched() } },
                leading = { Avatar(accountInitials(a), 40.dp, color = subjectColor(accountColorID(a)), shape = "cookie9Sided") },
                trailing = {
                    if (active) Icon("check", tint = Theme.scheme.primary)
                    IconButton(onClick = {
                        if (cloud) ui.ask("Scollegare ${a.name}?", "${a.name} non sarà più disponibile con l'account BCW ${model.cloudUser?.username ?: ""}, su nessun dispositivo. L'orario sincronizzato di questo account viene eliminato dal server.", "Scollega", destructive = true) {
                            scope.launch { runCatching { model.unlinkAccount(a.id) }.onFailure { ui.toast(it.message ?: "Errore") } }
                        } else ui.ask("Rimuovere ${a.name}?", "Le credenziali e l'orario di questo account verranno rimossi da questo dispositivo.", "Rimuovi", destructive = true) {
                            model.removeAccount(a.id)
                        }
                    }) { Icon(if (cloud) "minusCircle" else "trash", tint = Theme.scheme.onSurfaceVariant, contentDescription = if (cloud) "Scollega" else "Rimuovi") }
                },
            )
        }
        item { ButtonRow(if (cloud) "Abbina un account Classeviva" else "Aggiungi account", "userPlus", { ui.addAccount = true }) }
        if (!model.isDemo) item { ButtonRow("Prova la demo", "sparkles", { model.startDemo(); onSwitched() }) }
    }
}

/**
 * Account BCW a cui è abbinato il profilo Classeviva attivo, con lo scollegamento (basta l'accesso a
 * Classeviva). Senza account BCW l'elenco si carica solo a richiesta, perché il server riceve il token.
 */
@Composable
private fun LinksSection() {
    val model = LocalModel.current
    val ui = LocalUi.current
    val scope = rememberCoroutineScope()
    val activeID = model.activeAccountID
    if (model.isDemo || model.client == null || activeID == null) return
    var requested by remember(activeID) { mutableStateOf(model.isCloud) }
    var links by remember(activeID) { mutableStateOf<List<BCWLink>?>(null) }
    var error by remember(activeID) { mutableStateOf<String?>(null) }
    var round by remember(activeID) { mutableIntStateOf(0) }
    val name = model.activeAccount?.name ?: "questo account Classeviva"
    if (!requested) {
        ListGroup(header = "Account BCW abbinati", footer = "Puoi vedere a quali account BCW è abbinato $name e scollegarlo da ognuno. Per verificare l'accesso, il server BCW riceve il token della sessione di Classeviva (non la password).") {
            item { ButtonRow("Mostra gli account BCW abbinati", "cloud", { requested = true }) }
        }
        return
    }
    LaunchedEffect(activeID, round) {
        try {
            links = model.classevivaLinks()
            error = null
        } catch (e: Exception) {
            error = e.message
        }
    }
    ListGroup(header = "Account BCW abbinati", footer = "Gli account BCW con cui si entra in $name. Scollegandolo da uno, quell'account BCW non potrà più entrarci e l'orario sincronizzato per questo account viene eliminato dal server. Per toglierli basta l'accesso a Classeviva.") {
        val l = links
        when {
            error != null -> {
                item { ListRow("Elenco non disponibile", subtitle = error, leading = { FormIcon("alertTriangle") }) }
                item { ButtonRow("Riprova", "refresh", { round += 1 }) }
            }
            l == null -> item { ListRow("Caricamento…", leading = { FormIcon("cloud") }, trailing = { LoadingIndicator(Modifier.size(28.dp)) }) }
            l.isEmpty() -> item { ListRow("Nessun account BCW", subtitle = "Questo account Classeviva non è abbinato a nessun account BCW.", leading = { FormIcon("cloud") }) }
            else -> {
                for (link in l) item(link.id) {
                    val mine = model.isCloud && link.id == model.activeAccountID
                    ListRow(link.username, subtitle = "Abbinato il ${Fmt.shortDayWithYear(Dates.fromEpochMillis(link.linkedAt).toLocalDate())}${if (mine) " · questo account BCW" else ""}",
                        leading = { IconBadge("cloud", if (mine) Theme.scheme.primary else Theme.scheme.secondary) },
                        trailing = {
                            IconButton(onClick = {
                                ui.ask("Scollegare da ${link.username}?", "L'account BCW ${link.username} non potrà più entrare in $name.", "Scollega", destructive = true) {
                                    scope.launch {
                                        try { model.removeClassevivaLink(link.id); round += 1 } catch (e: Exception) { ui.toast(e.message ?: "Errore") }
                                    }
                                }
                            }) { Icon("minusCircle", tint = Theme.scheme.onSurfaceVariant, contentDescription = "Scollega") }
                        })
                }
                if (l.size > 1) item {
                    ButtonRow("Scollega da tutti", "trash", {
                        ui.ask("Scollegare da tutti gli account BCW?", "Nessun account BCW potrà più entrare in $name.", "Scollega da tutti", destructive = true) {
                            scope.launch { try { model.removeClassevivaLink(null); round += 1 } catch (e: Exception) { ui.toast(e.message ?: "Errore") } }
                        }
                    }, destructive = true)
                }
            }
        }
    }
}

private enum class CloudDialog { login, register, password, delete, server }

/** Account BCW: chi ha effettuato l'accesso, la sincronizzazione e le azioni sull'account. */
@Composable
private fun CloudSection() {
    val model = LocalModel.current
    val ui = LocalUi.current
    val scope = rememberCoroutineScope()
    var dialog by remember { mutableStateOf<CloudDialog?>(null) }
    val serverRow: com.bcw.classeviva.ui.components.ListGroupScope.() -> Unit = {
        item { ListRow("Server BCW", subtitle = BCWServer.host, leading = { FormIcon("server") }, onClick = { dialog = CloudDialog.server }, trailing = { ChevronRight() }) }
    }
    if (!model.isCloud) {
        ListGroup(header = "Account BCW", footer = "Con un account BCW abbini i tuoi account Classeviva e ritrovi impostazioni, aspetto e orario su ogni dispositivo, anche nell'app per iPhone e sul web. Dopo l'accesso potrai abbinare gli account salvati su questo dispositivo.") {
            item { ButtonRow("Accedi con un account BCW", "cloud", { dialog = CloudDialog.login }) }
            item { ButtonRow("Crea un account BCW", "userPlus", { dialog = CloudDialog.register }) }
            serverRow()
        }
    } else {
        val sync = model.sync
        Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
            ListGroup(header = "Account BCW", footer = "Si sincronizzano le impostazioni (tranne blocco, promemoria, notifiche e colori del sistema, che valgono solo su questo dispositivo), l'aspetto e l'orario di ogni account abbinato, con i file allegati. Sotto scegli cosa sincronizzare.") {
                item { ListRow(model.cloudUser?.username ?: "", subtitle = "Account BCW", leading = { Avatar((model.cloudUser?.username ?: "?").take(1).uppercase(), 40.dp, color = Theme.scheme.primary, shape = "cookie9Sided") }) }
                item {
                    ListRow("Sincronizzazione", subtitle = if (sync.status == SyncStatus.error || sync.status == SyncStatus.offline) sync.message else sync.fileWarning,
                        leading = { FormIcon("cloud") }, trailing = {
                            if (sync.status == SyncStatus.syncing) LoadingIndicator(Modifier.size(24.dp))
                            Text(syncStatusText(sync), style = Theme.type.style(14, 500), color = Theme.scheme.onSurfaceVariant)
                        })
                }
                item { ButtonRow("Sincronizza ora", "refresh", { scope.launch { syncNow(model) } }, enabled = sync.status != SyncStatus.syncing) }
                item { ButtonRow("Cambia password…", "key", { dialog = CloudDialog.password }) }
                serverRow()
                item {
                    ButtonRow("Esci da BCW", "logout", {
                        ui.ask("Uscire dall'account BCW?", "Gli account abbinati, le loro cache e i loro orari spariranno da questo dispositivo (restano sul server). Torneranno gli account salvati prima su questo dispositivo.", "Esci", destructive = true) {
                            scope.launch { model.cloudSignOut() }
                        }
                    }, destructive = true)
                }
                item { ButtonRow("Elimina l'account BCW…", "trash", { dialog = CloudDialog.delete }, destructive = true) }
            }
            ListGroup(header = "Da sincronizzare", footer = "Vale solo per questo dispositivo. Una voce spenta resta com'è qui e non cambia sugli altri dispositivi. Riaccendendo impostazioni, aspetto o compiti, questo dispositivo prende quelli dell'account BCW; riaccendendo l'orario vince il lato cambiato nel frattempo (se sono cambiati entrambi, questo dispositivo).") {
                for (c in SyncCategory.entries) item(c) { SyncCategoryRow(c) }
            }
        }
    }
    when (dialog) {
        CloudDialog.login, CloudDialog.register -> com.bcw.classeviva.ui.screens.login.CloudLoginDialog(register = dialog == CloudDialog.register) { dialog = null }
        CloudDialog.password -> PasswordDialog { dialog = null }
        CloudDialog.delete -> DeleteDialog { dialog = null }
        CloudDialog.server -> ServerDialog { dialog = null }
        null -> {}
    }
}

@Composable
private fun PasswordDialog(onDismiss: () -> Unit) {
    val model = LocalModel.current
    val ui = LocalUi.current
    val scope = rememberCoroutineScope()
    var current by remember { mutableStateOf("") }
    var new by remember { mutableStateOf("") }
    var repeat by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Cambia password") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PasswordField(current, { current = it }, "Password attuale")
                PasswordField(new, { new = it }, "Nuova password (almeno 8 caratteri)")
                PasswordField(repeat, { repeat = it }, "Ripeti la nuova password")
                error?.let { Text(it, color = Theme.scheme.error, style = Theme.type.style(13)) }
                Text("Gli altri dispositivi dovranno accedere di nuovo.", style = Theme.type.style(12), color = Theme.scheme.onSurfaceVariant)
            }
        },
        confirmButton = {
            Button(enabled = !busy && current.isNotEmpty() && new.length >= 8, onClick = {
                if (new != repeat) { error = "Le due password non coincidono."; return@Button }
                scope.launch {
                    busy = true
                    try { model.changeCloudPassword(current, new); ui.toast("Password cambiata."); onDismiss() } catch (e: Exception) { error = e.message } finally { busy = false }
                }
            }) { Text("Cambia") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annulla") } },
    )
}

@Composable
private fun DeleteDialog(onDismiss: () -> Unit) {
    val model = LocalModel.current
    val scope = rememberCoroutineScope()
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Eliminare l'account BCW?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Si cancellano dal server l'account ${model.cloudUser?.username ?: ""}, gli abbinamenti, le impostazioni e gli orari sincronizzati con i file. Gli account Classeviva non vengono toccati.")
                PasswordField(password, { password = it }, "Password dell'account BCW")
                error?.let { Text(it, color = Theme.scheme.error, style = Theme.type.style(13)) }
            }
        },
        confirmButton = {
            Button(enabled = !busy && password.isNotEmpty(), colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = Theme.scheme.error, contentColor = Theme.scheme.onError), onClick = {
                scope.launch {
                    busy = true
                    try { model.deleteCloudAccount(password); onDismiss() } catch (e: Exception) { error = e.message } finally { busy = false }
                }
            }) { Text("Elimina") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annulla") } },
    )
}

@Composable
private fun ServerDialog(onDismiss: () -> Unit) {
    val ui = LocalUi.current
    var text by remember { mutableStateOf(if (BCWServer.isCustom) BCWServer.base else "") }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Server BCW") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("L'indirizzo del server degli account BCW (BCW-api). Lascia vuoto per quello predefinito (${BCWServer.DEFAULT_URL.removePrefix("https://")}).", style = Theme.type.style(14))
                OutlinedTextField(text, { text = it; error = null }, Modifier.fillMaxWidth(), singleLine = true, placeholder = { Text(BCWServer.DEFAULT_URL) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri))
                error?.let { Text(it, color = Theme.scheme.error, style = Theme.type.style(13)) }
            }
        },
        confirmButton = {
            Button(onClick = {
                if (BCWServer.set(text)) { ui.toast("Server BCW: ${BCWServer.host}"); onDismiss() } else error = "Non è un indirizzo valido."
            }) { Text("Salva") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annulla") } },
    )
}

@Composable
fun PasswordField(value: String, onChange: (String) -> Unit, label: String) {
    OutlinedTextField(value, onChange, Modifier.fillMaxWidth(), singleLine = true, label = { Text(label) },
        visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
}
