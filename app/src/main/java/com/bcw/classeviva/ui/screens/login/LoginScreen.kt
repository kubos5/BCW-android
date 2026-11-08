package com.bcw.classeviva.ui.screens.login

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.bcw.classeviva.data.APIError
import com.bcw.classeviva.data.BCWServer
import com.bcw.classeviva.data.CloudError
import com.bcw.classeviva.data.LoginChoice
import com.bcw.classeviva.data.Phase
import com.bcw.classeviva.ui.components.BCWLogo
import com.bcw.classeviva.ui.components.ConnectedGroup
import com.bcw.classeviva.ui.components.Shaped
import com.bcw.classeviva.ui.icons.Icon
import com.bcw.classeviva.ui.shell.LocalModel
import com.bcw.classeviva.ui.shell.LocalUi
import com.bcw.classeviva.ui.theme.Theme
import kotlinx.coroutines.launch

/** Schermata di accesso: Classeviva o account BCW (gruppo di pulsanti), "Prova la demo". */
@Composable
fun LoginScreen() {
    Box(Modifier.fillMaxSize().background(Theme.scheme.surface).statusBarsPadding().navigationBarsPadding().imePadding(), contentAlignment = Alignment.Center) {
        Column(
            Modifier.widthIn(max = 480.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            LoginContent(adding = false, onDone = {})
        }
    }
}

/** Il modulo di accesso, a tutta pagina o nel foglio "Aggiungi account". */
@Composable
fun LoginContent(adding: Boolean, onDone: () -> Unit) {
    val model = LocalModel.current
    val ui = LocalUi.current
    val linkingFirst = !adding && model.isCloud && model.phase == Phase.signedOut
    var mode by rememberSaveable { mutableStateOf(if (adding || linkingFirst) "classeviva" else "classeviva") }
    var register by rememberSaveable { mutableStateOf(false) }
    val keyboardOpen = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    val compact = keyboardOpen && !adding
    val subtitle = when {
        adding -> if (model.isCloud) "Abbina un account Classeviva" else "Aggiungi un account"
        linkingFirst -> "Ciao, ${model.cloudUser?.username ?: ""}"
        else -> "Better ClasseViVa"
    }
    Column(Modifier.fillMaxWidth().animateContentSize(Theme.motion.defaultSpatialSpec()), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (compact) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                BCWLogo(height = 30.dp)
                Text(subtitle, style = Theme.type.style(18, 550, rounded = true), color = Theme.scheme.onSurfaceVariant, maxLines = 2)
            }
        } else {
            if (!adding) LoginArt()
            BCWLogo(height = if (adding) 40.dp else 56.dp, stacked = true)
            Text(subtitle, style = Theme.type.style(17, 500), color = Theme.scheme.onSurfaceVariant, textAlign = TextAlign.Center)
        }
        model.cloudNotice?.let { notice ->
            ErrorBox(notice)
        }
        if (!adding && !linkingFirst) {
            ConnectedGroup(listOf("classeviva", "bcw"), mode, { mode = it }) { v, _ -> Text(if (v == "classeviva") "Classeviva" else "Account BCW") }
        }
        if (mode == "classeviva" || adding || linkingFirst) {
            ClassevivaForm(adding || linkingFirst, onDone)
            if (linkingFirst) {
                Text("Abbina il tuo primo account Classeviva: con il tuo account BCW entrerai in tutti gli account abbinati, su ogni dispositivo.",
                    style = Theme.type.style(13), color = Theme.scheme.onSurfaceVariant, textAlign = TextAlign.Center)
                TextButton(onClick = { ui.scope.launch { model.cloudSignOut() } }) { Text("Esci da BCW") }
            }
        } else {
            CloudForm(register, onToggleRegister = { register = !register }, compact = compact, onDone = onDone)
        }
        if (!compact && !adding && !linkingFirst) {
            Text("BCW non è affiliato a Gruppo Spaggiari Parma S.p.A.", style = Theme.type.style(12), color = Theme.scheme.onSurfaceVariant, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun LoginArt() {
    Shaped("cookie12Sided", Theme.scheme.primaryContainer, 96.dp) {
        Icon("graduationCap", size = 44.dp, tint = Theme.scheme.onPrimaryContainer)
    }
}

@Composable
private fun ErrorBox(text: String) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(Theme.radii.l)).background(Theme.colors.poor.container).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon("alertCircleFill", size = 20.dp, tint = Theme.colors.poor.onContainer)
        Text(text, style = Theme.type.style(14), color = Theme.colors.poor.onContainer)
    }
}

@Composable
private fun Field(value: String, onChange: (String) -> Unit, placeholder: String, icon: String, password: Boolean = false, last: Boolean = false, onDone: () -> Unit = {}) {
    var visible by remember { mutableStateOf(false) }
    val focus = LocalFocusManager.current
    OutlinedTextField(
        value = value, onValueChange = onChange, singleLine = true, modifier = Modifier.fillMaxWidth(),
        placeholder = { Text(placeholder) }, leadingIcon = { Icon(icon) },
        trailingIcon = if (password) ({ IconButton(onClick = { visible = !visible }) { Icon(if (visible) "eyeOff" else "eye", contentDescription = if (visible) "Nascondi" else "Mostra") } }) else null,
        visualTransformation = if (password && !visible) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = if (password) KeyboardType.Password else KeyboardType.Email, imeAction = if (last) ImeAction.Done else ImeAction.Next, autoCorrectEnabled = false),
        keyboardActions = KeyboardActions(onDone = { focus.clearFocus(); onDone() }),
        shape = RoundedCornerShape(Theme.radii.l),
    )
}

@Composable
private fun ClassevivaForm(adding: Boolean, onDone: () -> Unit) {
    val model = LocalModel.current
    val scope = rememberCoroutineScope()
    var username by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var choices by remember { mutableStateOf<List<LoginChoice>>(emptyList()) }
    fun submit(ident: String? = null) {
        if (username.isBlank() || password.isEmpty() || busy) return
        scope.launch {
            busy = true
            error = null
            try {
                model.signIn(username, password, ident)
                password = ""
                onDone()
            } catch (e: APIError.NeedsProfileChoice) {
                choices = e.choices
            } catch (e: CloudError) {
                if (e.code == "needs_profile_choice" && e.choices.isNotEmpty()) choices = e.choices else error = e.message
            } catch (e: Exception) {
                error = e.message ?: "Accesso non riuscito."
            } finally {
                busy = false
            }
        }
    }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Field(username, { username = it }, "Codice utente o email", "person")
        Field(password, { password = it }, "Password", "key", password = true, last = true) { submit() }
        error?.let { ErrorBox(it) }
        Button(onClick = { submit() }, enabled = username.isNotBlank() && password.isNotEmpty() && !busy, modifier = Modifier.fillMaxWidth().height(56.dp)) {
            if (busy) LoadingIndicator(Modifier.size(28.dp), color = Theme.scheme.onPrimary) else Text("Accedi", style = Theme.type.style(16, 600))
        }
        if (!adding && !model.isCloud) {
            FilledTonalButton(onClick = { model.startDemo() }, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Icon("sparkles", size = 18.dp)
                Text("Prova la demo", modifier = Modifier.padding(start = 8.dp))
            }
        }
        Text(
            if (model.isCloud) "Usa le credenziali di Classeviva. Il server di BCW le verifica e le salva cifrate nel tuo account BCW; l'app le invia anche ai server di Classeviva per accedere."
            else "Usa le stesse credenziali di Classeviva. Restano cifrate su questo dispositivo e vengono inviate esclusivamente ai server di Classeviva.",
            style = Theme.type.style(12), color = Theme.scheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
        )
    }
    if (choices.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { choices = emptyList() },
            title = { Text("Scegli il profilo") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    for (c in choices) {
                        TextButton(onClick = { val ident = c.ident; choices = emptyList(); submit(ident) }, modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.fillMaxWidth()) {
                                Text(c.name.ifEmpty { c.ident }, style = Theme.type.style(16, 550))
                                if (c.school.isNotEmpty()) Text(c.school, style = Theme.type.style(13), color = Theme.scheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { choices = emptyList() }) { Text("Annulla") } },
        )
    }
}

@Composable
private fun CloudForm(register: Boolean, onToggleRegister: () -> Unit, compact: Boolean, onDone: () -> Unit) {
    val model = LocalModel.current
    val ui = LocalUi.current
    val scope = rememberCoroutineScope()
    var username by rememberSaveable { mutableStateOf(model.lastCloudUsername ?: "") }
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var server by remember { mutableStateOf(false) }
    fun submit() {
        if (busy) return
        if (register && password.length < 8) { error = "La password deve avere almeno 8 caratteri."; return }
        if (register && password != confirm) { error = "Le due password non coincidono."; return }
        scope.launch {
            busy = true
            error = null
            try {
                val candidates = model.cloudSignIn(username, password, register)
                password = ""
                confirm = ""
                ui.linkCandidates = candidates
                onDone()
            } catch (e: Exception) {
                error = e.message
            } finally {
                busy = false
            }
        }
    }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Field(username, { username = it.lowercase().trim() }, "Nome utente BCW", "person")
        Field(password, { password = it }, "Password", "key", password = true, last = !register) { submit() }
        AnimatedVisibility(register) { Field(confirm, { confirm = it }, "Ripeti la password", "key", password = true, last = true) { submit() } }
        error?.let { ErrorBox(it) }
        Button(onClick = { submit() }, enabled = username.isNotBlank() && password.isNotEmpty() && !busy, modifier = Modifier.fillMaxWidth().height(56.dp)) {
            if (busy) LoadingIndicator(Modifier.size(28.dp), color = Theme.scheme.onPrimary) else Text(if (register) "Crea l'account" else "Accedi", style = Theme.type.style(16, 600))
        }
        if (!compact) {
            FilledTonalButton(onClick = onToggleRegister, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                if (!register) Icon("userPlus", size = 18.dp)
                Text(if (register) "Hai già un account? Accedi" else "Crea un account BCW", modifier = Modifier.padding(start = if (register) 0.dp else 8.dp))
            }
            Text("Con un account BCW entri in tutti i tuoi account Classeviva abbinati e ritrovi impostazioni e orario su ogni dispositivo. Le credenziali di Classeviva sono salvate cifrate sul server di BCW.",
                style = Theme.type.style(12), color = Theme.scheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            TextButton(onClick = { server = true }, modifier = Modifier.fillMaxWidth()) { Text("Usa un altro server BCW") }
        }
    }
    if (server) {
        var text by remember { mutableStateOf(if (BCWServer.isCustom) BCWServer.base else "") }
        AlertDialog(
            onDismissRequest = { server = false }, title = { Text("Server BCW") },
            text = {
                OutlinedTextField(text, { text = it }, singleLine = true, placeholder = { Text(BCWServer.DEFAULT_URL) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri))
            },
            confirmButton = { Button(onClick = { if (BCWServer.set(text)) { ui.toast("Server BCW aggiornato."); server = false } else ui.toast("Non è un indirizzo valido.") }) { Text("Salva") } },
            dismissButton = { TextButton(onClick = { server = false }) { Text("Annulla") } },
        )
    }
}

/** Accesso all'account BCW dalle Impostazioni, in un foglio. */
@Composable
fun CloudLoginDialog(register: Boolean, onDismiss: () -> Unit) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var reg by remember { mutableStateOf(register) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state, containerColor = Theme.scheme.surfaceContainerLow) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 24.dp).imePadding(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            BCWLogo(height = 36.dp, stacked = true)
            Text(if (reg) "Crea un account BCW" else "Accedi con un account BCW", style = Theme.type.style(20, 500, rounded = true), color = Theme.scheme.onSurface)
            CloudForm(reg, { reg = !reg }, compact = false, onDone = onDismiss)
        }
    }
}

/** Aggiungi (o abbina) un account Classeviva, in un foglio. */
@Composable
fun AddAccountSheet(onDismiss: () -> Unit) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state, containerColor = Theme.scheme.surfaceContainerLow) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 24.dp).imePadding()) {
            LoginContent(adding = true, onDone = onDismiss)
        }
    }
}
