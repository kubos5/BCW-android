package com.bcw.classeviva.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import com.bcw.classeviva.data.AppModel
import com.bcw.classeviva.data.Phase
import com.bcw.classeviva.ui.components.BCWLogo
import com.bcw.classeviva.ui.components.ButtonRow
import com.bcw.classeviva.ui.components.ListGroup
import com.bcw.classeviva.ui.nav.Navigator
import com.bcw.classeviva.ui.screens.login.AddAccountSheet
import com.bcw.classeviva.ui.screens.login.LoginScreen
import com.bcw.classeviva.ui.screens.onboarding.OnboardingScreen
import com.bcw.classeviva.ui.screens.settings.AccountsSection
import com.bcw.classeviva.ui.shell.LocalModel
import com.bcw.classeviva.ui.shell.LocalNavigator
import com.bcw.classeviva.ui.shell.LocalScreenMemory
import com.bcw.classeviva.ui.shell.LocalUi
import com.bcw.classeviva.ui.shell.MainShell
import com.bcw.classeviva.ui.shell.ScreenMemory
import com.bcw.classeviva.ui.shell.Transitions
import com.bcw.classeviva.ui.shell.UiController
import com.bcw.classeviva.ui.theme.BCWTheme
import com.bcw.classeviva.ui.theme.Theme
import com.bcw.classeviva.ui.theme.ThemeSettings
import com.bcw.classeviva.ui.viewer.FileViewer
import kotlinx.coroutines.launch

/** Stato del blocco dell'app (impronta, volto o blocco schermo). */
class LockState {
    var locked by mutableStateOf(false)
}

@Composable
fun BCWRoot(model: AppModel, lock: LockState, onUnlock: () -> Unit, onSystemBars: (dark: Boolean) -> Unit = {}) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val ui = remember { UiController(context, scope) }
    val navigator = remember { Navigator() }
    // Alla prima apertura l'introduzione parte in bianco e nero, già dal primo disegno.
    remember {
        model.isFirstRun().also { first ->
            if (first) {
                ui.monochrome = true
                ui.onboarding = true
            }
        }
    }
    val settings = ThemeSettings.from(model.preferences).copy(monochrome = ui.onboarding && ui.monochrome)
    // Memoria delle pagine per sessione (account o demo): cambiando sessione si azzera.
    val sessionKey = "${model.isDemo}-${model.activeAccountID}-${model.cloud?.user?.id}"
    val memory = remember(sessionKey) { ScreenMemory() }
    LaunchedEffect(sessionKey) { navigator.reset() }

    BCWTheme(settings) {
        val dark = Theme.isDark
        LaunchedEffect(dark) { onSystemBars(dark) }
        // Il colore del contenuto predefinito (icone delle barre, testi senza colore) segue il tema: senza, era nero anche nel tema scuro.
        CompositionLocalProvider(
            LocalModel provides model, LocalUi provides ui, LocalNavigator provides navigator, LocalScreenMemory provides memory,
            LocalContentColor provides Theme.scheme.onSurface,
        ) {
            ThemeReveal(ui) {
                val alpha by animateFloatAsState(if (model.isChangingSession) 0f else 1f, tween(if (model.isChangingSession) 180 else 260), label = "session")
                val reduced = Theme.reducedMotion
                // Sotto l'introduzione l'app non esiste per TalkBack (ci torna alla fine dell'introduzione).
                val hidden = if (ui.onboarding) Modifier.clearAndSetSemantics {} else Modifier
                AnimatedContent(model.phase, transitionSpec = { Transitions.fadeThrough(reduced) }, label = "phase", modifier = Modifier.alpha(alpha).then(hidden)) { phase ->
                    when (phase) {
                        Phase.launching -> Launching()
                        Phase.signedOut -> LoginScreen()
                        Phase.signedIn -> MainShell()
                    }
                }
                if (ui.onboarding) OnboardingScreen()
                Overlays()
                SnackbarHost(ui.snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 80.dp))
                if (lock.locked) LockScreen(onUnlock)
            }
        }
    }
}

/** Durata e curva del cerchio, come `revealTheme` del web. */
private const val REVEAL_DURATION = 1150
private val revealEasing = CubicBezierEasing(0.42f, 0f, 0.18f, 1f)

/** Opacità dell'anello lungo il cerchio: compare, resta un po' e svanisce (come sul web). */
private fun ringAlpha(t: Float): Float = when {
    t < 0.12f -> 0.5f * t / 0.12f
    t < 0.6f -> 0.5f - 0.2f * (t - 0.12f) / 0.48f
    else -> 0.3f * (1f - (t - 0.6f) / 0.4f)
}.coerceIn(0f, 1f)

/**
 * Il cambio di tema con un cerchio (come `revealTheme` del web): si fotografa la schermata con i
 * colori di prima, si applica il cambio e la foto si apre con un cerchio che si allarga dal punto
 * toccato. Sul bordo un anello del nuovo colore primario separa il dentro dal fuori: nasce spesso e
 * si assottiglia allargandosi. Con le animazioni ridotte il cambio è immediato.
 */
@Composable
private fun ThemeReveal(ui: UiController, content: @Composable BoxScope.() -> Unit) {
    val layer = rememberGraphicsLayer()
    val scope = rememberCoroutineScope()
    val reduced = Theme.reducedMotion
    var snapshot by remember { mutableStateOf<ImageBitmap?>(null) }
    var center by remember { mutableStateOf(Offset.Zero) }
    var far by remember { mutableFloatStateOf(0f) }
    val progress = remember { Animatable(0f) }
    SideEffect {
        ui.revealer = { point, change ->
            if (reduced || snapshot != null) change()
            else scope.launch {
                val image = runCatching { layer.toImageBitmap() }.getOrNull()
                if (image == null) {
                    change()
                    return@launch
                }
                val c = point ?: Offset(image.width / 2f, image.height / 2f)
                center = c
                far = listOf(Offset.Zero, Offset(image.width.toFloat(), 0f), Offset(0f, image.height.toFloat()), Offset(image.width.toFloat(), image.height.toFloat()))
                    .maxOf { (it - c).getDistance() }
                progress.snapTo(0f)
                snapshot = image
                change()
                progress.animateTo(1f, tween(REVEAL_DURATION, easing = LinearEasing))
                snapshot = null
            }
        }
    }
    Box(
        Modifier.fillMaxSize().background(Theme.scheme.surface).drawWithContent {
            layer.record { this@drawWithContent.drawContent() }
            drawLayer(layer)
        },
        content = content,
    )
    snapshot?.let { image ->
        val ring = Theme.scheme.primary
        Canvas(Modifier.fillMaxSize()) {
            val t = progress.value
            val eased = revealEasing.transform(t)
            val radius = 0.5f + eased * (far + 8.dp.toPx())
            val hole = Path().apply { addOval(Rect(center, radius)) }
            clipPath(hole, ClipOp.Difference) { drawImage(image) }
            // Il tratto è centrato sul bordo del cerchio: metà sopra la schermata di prima, metà sopra la nuova.
            val width = (26f + (2f - 26f) * eased).dp.toPx()
            drawCircle(ring.copy(alpha = ringAlpha(t)), radius, center, style = Stroke(width))
        }
    }
}

@Composable
private fun Launching() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(24.dp)) {
            BCWLogo(height = 56.dp, stacked = true)
            LoadingIndicator(Modifier.size(48.dp))
        }
    }
}

/** Dialoghi e fogli comuni a tutte le pagine. */
@Composable
private fun Overlays() {
    val model = LocalModel.current
    val ui = LocalUi.current
    val scope = rememberCoroutineScope()
    ui.confirm?.let { request ->
        AlertDialog(
            onDismissRequest = { ui.confirm = null },
            title = { Text(request.title) },
            text = request.message?.let { { Text(it) } },
            confirmButton = {
                Column(horizontalAlignment = Alignment.End) {
                    for (extra in request.extra) {
                        TextButton(onClick = { ui.confirm = null; extra.run() }) { Text(extra.label, color = if (extra.destructive) Theme.scheme.error else Theme.scheme.primary) }
                    }
                    Button(
                        onClick = { ui.confirm = null; request.confirm.run() },
                        colors = if (request.confirm.destructive) ButtonDefaults.buttonColors(containerColor = Theme.scheme.error, contentColor = Theme.scheme.onError) else ButtonDefaults.buttonColors(),
                    ) { Text(request.confirm.label) }
                }
            },
            dismissButton = { TextButton(onClick = { ui.confirm = null }) { Text(request.cancelLabel) } },
        )
    }
    if (ui.accountSwitcher) AccountSwitcherSheet { ui.accountSwitcher = false }
    if (ui.addAccount) AddAccountSheet { ui.addAccount = false }
    ui.viewer?.let { FileViewer(it) { ui.viewer = null } }
    val candidates = ui.linkCandidates
    if (candidates.isNotEmpty() && model.isCloud) {
        val one = candidates.size == 1
        val names = candidates.joinToString(", ") { it.name }
        AlertDialog(
            onDismissRequest = { ui.linkCandidates = emptyList() },
            title = { Text(if (one) "Abbinare ${candidates[0].name} al tuo account BCW?" else "Abbinare ${candidates.size} account al tuo account BCW?") },
            text = { Text("${if (one) "Su questo dispositivo c'è un account Classeviva" else "Su questo dispositivo ci sono ${candidates.size} account Classeviva"} ($names). Abbinandoli li ritroverai con il tuo account BCW su ogni dispositivo, insieme al loro orario.") },
            confirmButton = {
                Button(onClick = {
                    val list = candidates
                    ui.linkCandidates = emptyList()
                    scope.launch {
                        val failures = model.linkLocalAccounts(list)
                        if (failures.isEmpty()) ui.toast(if (list.size == 1) "Account abbinato." else "Account abbinati.")
                        else ui.ask("Alcuni account non sono stati abbinati", failures.joinToString("\n") { "${it.first.name}: ${it.second}" }, "OK") {}
                    }
                }) { Text(if (one) "Abbina" else "Abbina tutti") }
            },
            dismissButton = { TextButton(onClick = { ui.linkCandidates = emptyList() }) { Text("Non ora") } },
        )
    }
}

/** Popup degli account (dall'avatar della Dashboard, dal profilo e tenendo premuta la scheda Tu). */
@Composable
fun AccountSwitcherSheet(onDismiss: () -> Unit) {
    val model = LocalModel.current
    val ui = LocalUi.current
    val scope = rememberCoroutineScope()
    val state = rememberModalBottomSheetState()
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state, containerColor = Theme.scheme.surfaceContainerLow) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Account", style = Theme.type.style(22, 500, rounded = true), color = Theme.scheme.onSurface, modifier = Modifier.padding(horizontal = 8.dp))
            AccountsSection(showsHeader = false, onSwitched = onDismiss)
            if (model.isCloud) {
                ListGroup {
                    item {
                        ButtonRow("Esci da BCW", "logout", {
                            ui.ask("Uscire dall'account BCW?", "Gli account abbinati, le loro cache e i loro orari spariranno da questo dispositivo (restano sul server).", "Esci", destructive = true) {
                                onDismiss()
                                scope.launch { model.cloudSignOut() }
                            }
                        }, destructive = true)
                    }
                }
            } else if (!model.isDemo && model.activeAccount != null) {
                ListGroup {
                    item {
                        ButtonRow("Esci da ${model.displayName}", "logout", {
                            ui.ask("Vuoi uscire da ${model.displayName}?", "Le credenziali di questo account verranno rimosse da questo dispositivo.", "Esci", destructive = true) {
                                onDismiss()
                                model.signOut()
                            }
                        }, destructive = true)
                    }
                }
            } else if (model.isDemo) {
                ListGroup {
                    item {
                        ButtonRow("Esci dalla demo", "logout", {
                            ui.ask("Vuoi uscire dalla demo?", model.demoExitMessage, "Esci", destructive = true) {
                                onDismiss()
                                model.signOut()
                            }
                        }, destructive = true)
                    }
                }
            }
        }
    }
}

/** Schermata del blocco: si sblocca con l'impronta, il volto o il blocco schermo. */
@Composable
private fun LockScreen(onUnlock: () -> Unit) {
    LaunchedEffect(Unit) { onUnlock() }
    Box(Modifier.fillMaxSize().background(Theme.scheme.surface), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(24.dp)) {
            com.bcw.classeviva.ui.components.Shaped("cookie12Sided", Theme.scheme.primaryContainer, 112.dp) {
                com.bcw.classeviva.ui.icons.Icon("lock", size = 48.dp, tint = Theme.scheme.onPrimaryContainer)
            }
            Text("BCW è bloccata", style = Theme.type.style(24, 500, rounded = true), color = Theme.scheme.onSurface)
            Button(onClick = onUnlock) { Text("Sblocca") }
        }
    }
}
