package com.bcw.classeviva.ui.screens.settings

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.bcw.classeviva.data.AppearanceMode
import com.bcw.classeviva.data.DEFAULT_ACCENT
import com.bcw.classeviva.platform.WatchStatus
import com.bcw.classeviva.platform.WatchStatusReader
import com.bcw.classeviva.platform.WearSync
import com.bcw.classeviva.ui.components.ButtonRow
import com.bcw.classeviva.ui.components.Collapsible
import com.bcw.classeviva.ui.components.FormIcon
import com.bcw.classeviva.ui.components.ListGroup
import com.bcw.classeviva.ui.components.ListRow
import com.bcw.classeviva.ui.components.SelectButton
import com.bcw.classeviva.ui.components.Shaped
import com.bcw.classeviva.ui.components.ToggleRow
import com.bcw.classeviva.ui.components.ValueRow
import com.bcw.classeviva.ui.icons.Icon
import com.bcw.classeviva.ui.shell.LocalModel
import com.bcw.classeviva.ui.shell.LocalUi
import com.bcw.classeviva.ui.theme.ColorStyle
import com.bcw.classeviva.ui.theme.ContrastLevel
import com.bcw.classeviva.ui.theme.Theme
import com.bcw.classeviva.ui.shell.Transitions
import com.bcw.classeviva.ui.theme.normalizeHex
import kotlinx.coroutines.delay
import java.text.DateFormat
import java.util.Date

/**
 * Impostazioni › Smartwatch (solo Android, richiesta dell'utente): l'orologio Wear OS abbinato,
 * con il modello e un pallino verde o rosso (collegato o no), la sincronizzazione dell'orario e
 * dell'aspetto, l'aspetto proprio dell'orologio quando non si sincronizza, e come collegarne uno.
 */
@Composable
fun SmartwatchPane() {
    val model = LocalModel.current
    val ui = LocalUi.current
    val context = LocalContext.current
    val p = model.preferences
    // Le scelte fatte qui sono un invio esplicito: vanno all'orologio (unite se ravvicinate).
    val sendSoon = { WearSync.timetableChanged(context, model.timetable, p) }
    var status by remember { mutableStateOf<WatchStatus?>(null) }
    var round by remember { mutableIntStateOf(0) }

    // Lo stato si aggiorna mentre la pagina è aperta (ogni 5 secondi) e tornando nell'app.
    LifecycleResumeEffect(Unit) {
        round++
        onPauseOrDispose { }
    }
    LaunchedEffect(round) {
        while (true) {
            status = WatchStatusReader.read(context)
            delay(5_000)
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
        WatchHero(status)

        ListGroup(
            header = "Sincronizzazione",
            footer = if (p.watchSyncAppearance) "L'orario va sull'orologio quando lo modifichi o con Invia l'orario ora. Tema e colori di BCW cambiati arrivano con il prossimo invio."
            else "L'orario va sull'orologio quando lo modifichi o con Invia l'orario ora. L'aspetto dell'orologio si sceglie qui sotto.",
        ) {
            item {
                ToggleRow(
                    "Sincronizza le impostazioni", p.watchSyncAppearance, { p.set("watchSyncAppearance", it); sendSoon() }, icon = "sync",
                    subtitle = "Tema e colori dell'orologio uguali a BCW",
                )
            }
            item {
                ButtonRow("Invia l'orario ora", "refresh", {
                    WearSync.pushNow(context, model.timetable, p)
                    ui.toast(if (status?.connected == true) "Orario inviato all'orologio" else "L'orario partirà quando l'orologio si collega")
                })
            }
            WearSync.lastSent?.let { sent ->
                item { ValueRow("Ultimo invio", DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(sent)), icon = "clock") }
            }
        }

        Collapsible(expanded = !p.watchSyncAppearance) {
            WatchAppearanceGroup(onChange = sendSoon)
        }

        HowToConnectGroup()
    }
}

// MARK: - Orologio e stato

/** In cima, centrati: il disegno dell'orologio con il pallino dello stato, il modello e lo stato. */
@Composable
private fun WatchHero(status: WatchStatus?) {
    val connected = status?.connected == true
    val name = status?.name
    val dot = if (connected) Theme.colors.good.color else Theme.colors.poor.color
    Column(
        Modifier.fillMaxWidth().padding(top = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(150.dp).semantics { contentDescription = if (connected) "Orologio collegato" else "Orologio non collegato" },
            contentAlignment = Alignment.Center,
        ) {
            WatchDrawing(connected)
            // Il pallino dello stato, verde o rosso, sul bordo della cassa in alto a destra.
            Box(
                Modifier.align(Alignment.Center).offset(x = 40.dp, y = (-40).dp).size(22.dp)
                    .clip(CircleShape).background(Theme.scheme.surface).padding(3.dp)
                    .clip(CircleShape).background(dot),
            )
        }
        val reduced = Theme.reducedMotion
        AnimatedContent(
            targetState = name to connected,
            transitionSpec = { Transitions.fadeThrough(reduced) },
            label = "watchName",
        ) { (shownName, isConnected) ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    shownName ?: "Nessuno smartwatch",
                    style = Theme.type.style(24, 550, rounded = true),
                    color = Theme.scheme.onSurface,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 12.dp),
                )
                Row(
                    Modifier.padding(top = 6.dp).clip(RoundedCornerShape(50)).background(Theme.scheme.surfaceContainerHigh)
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(if (isConnected) Theme.colors.good.color else Theme.colors.poor.color))
                    Text(
                        statusText(status),
                        style = Theme.type.style(13, 500),
                        color = Theme.scheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

private fun statusText(status: WatchStatus?): String = when {
    status == null -> "Cerco l'orologio…"
    status.unavailable -> "Servizi Google Play non disponibili"
    !status.connected && status.name == null -> "Abbina un orologio Wear OS al telefono"
    !status.connected -> "Non collegato"
    status.appInstalled -> "Collegato · BCW installata"
    else -> "Collegato · BCW non installata sull'orologio"
}

/** Un orologio disegnato con i colori del tema: cinturino, cassa, corona e quadrante con BCW. */
@Composable
private fun WatchDrawing(connected: Boolean) {
    val s = Theme.scheme
    val band = s.surfaceContainerHighest
    Box(Modifier.size(150.dp), contentAlignment = Alignment.Center) {
        // Cinturino sopra e sotto la cassa.
        Box(Modifier.align(Alignment.TopCenter).width(62.dp).height(40.dp).clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 6.dp, bottomEnd = 6.dp)).background(band))
        Box(Modifier.align(Alignment.BottomCenter).width(62.dp).height(40.dp).clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp, bottomStart = 16.dp, bottomEnd = 16.dp)).background(band))
        // Corona sul lato destro.
        Box(Modifier.align(Alignment.Center).offset(x = 52.dp).width(10.dp).height(20.dp).clip(RoundedCornerShape(4.dp)).background(s.outline))
        // Cassa e quadrante: acceso nel colore primario se collegato, spento se no.
        Box(
            Modifier.size(104.dp).clip(CircleShape).background(s.inverseSurface).padding(6.dp)
                .clip(CircleShape).background(if (connected) s.primaryContainer else s.surfaceContainerHigh)
                .border(1.dp, s.outlineVariant.copy(alpha = 0.4f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (connected) {
                Shaped("cookie9Sided", s.primary, 52.dp) { Icon("watch", size = 28.dp, tint = s.onPrimary) }
            } else {
                Icon("watchOff", size = 34.dp, tint = s.onSurfaceVariant)
            }
        }
    }
}

// MARK: - Aspetto proprio dell'orologio

/** Spenta la sincronizzazione: tema, colore, stile e contrasto solo per l'orologio. */
@Composable
private fun WatchAppearanceGroup(onChange: () -> Unit) {
    val p = LocalModel.current.preferences
    fun set(key: String, value: String) { p.set(key, value); onChange() }
    var picking by remember { mutableStateOf(false) }
    val accent = normalizeHex(p.string("watchAccentColor")) ?: DEFAULT_ACCENT
    val style = ColorStyle.entries.firstOrNull { it.name == p.string("watchColorStyle") } ?: ColorStyle.tonalSpot
    val contrast = ContrastLevel.entries.firstOrNull { it.name == p.string("watchContrast") } ?: ContrastLevel.standard
    val appearance = AppearanceMode.entries.firstOrNull { it.name == p.string("watchAppearance") } ?: AppearanceMode.system
    ListGroup(
        header = "Aspetto sull'orologio",
        footer = "Vale solo per l'orologio: BCW sul telefono non cambia. Automatico segue il tema del telefono.",
    ) {
        item {
            ListRow("Tema", leading = { FormIcon("sun") }, trailing = {
                SelectButton(AppearanceMode.entries.map { it to it.title }, appearance, { set("watchAppearance", it.name) })
            })
        }
        item {
            AccentPicker(accent, style, Theme.isDark, onPick = { set("watchAccentColor", it) }, onCustom = { picking = true })
        }
        item {
            ListRow("Stile dei colori", subtitle = style.subtitle, leading = { FormIcon("colorStyle") }, trailing = {
                SelectButton(ColorStyle.entries.map { it to it.title }, style, { set("watchColorStyle", it.name) })
            })
        }
        item {
            ListRow("Contrasto", leading = { FormIcon("contrast") }, trailing = {
                SelectButton(ContrastLevel.entries.map { it to it.title }, contrast, { set("watchContrast", it.name) })
            })
        }
        item {
            ButtonRow("Usa l'aspetto di BCW", "refresh", {
                p.set("watchAppearance", p.string("appearance"))
                p.set("watchAccentColor", normalizeHex(p.string("accentColor")) ?: DEFAULT_ACCENT)
                p.set("watchColorStyle", p.string("colorStyle"))
                p.set("watchContrast", p.string("contrast"))
                onChange()
            })
        }
    }
    if (picking) {
        ColorPickerDialog(accent, onDismiss = { picking = false }, title = "Colore dell'orologio") { set("watchAccentColor", it) }
    }
}

// MARK: - Come collegare

@Composable
private fun HowToConnectGroup() {
    val ui = LocalUi.current
    ListGroup(
        header = "Collegare uno smartwatch",
        footer = "Serve un orologio con Wear OS 3 o successivo. L'orario resta sull'orologio anche lontano dal telefono.",
    ) {
        item { StepRow(1, "Abbina l'orologio al telefono", "Con l'app del produttore: Galaxy Wearable, Pixel Watch o Wear OS") }
        item { StepRow(2, "Installa BCW sull'orologio", "L'app per Wear OS è nel modulo wear di BCW-android, da installare con Android Studio") }
        item { StepRow(3, "Apri BCW sull'orologio", "L'orario e i colori arrivano dal telefono in pochi secondi") }
        item {
            ListRow(
                "BCW-android su GitHub", subtitle = "Codice e istruzioni dell'app per Wear OS", leading = { FormIcon("android") },
                onClick = { ui.openUrl("https://github.com/kubos5/BCW-android") },
                trailing = { Icon("externalLink", size = 18.dp, tint = Theme.scheme.onSurfaceVariant) },
            )
        }
    }
}

@Composable
private fun StepRow(number: Int, title: String, subtitle: String) {
    ListRow(title, subtitle = subtitle, multiline = true, leading = {
        Box(
            Modifier.size(32.dp).clip(CircleShape).background(Theme.scheme.secondaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Text("$number", style = Theme.type.numeral(15, 650).copy(fontSize = 15.sp), color = Theme.scheme.onSecondaryContainer)
        }
    })
}
