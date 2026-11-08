package com.bcw.classeviva.ui.screens.settings

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.bcw.classeviva.BuildConfig
import com.bcw.classeviva.data.AppearanceMode
import com.bcw.classeviva.data.AverageMode
import com.bcw.classeviva.data.DEFAULT_ACCENT
import com.bcw.classeviva.data.DashboardMode
import com.bcw.classeviva.data.NewsKind
import com.bcw.classeviva.data.SyncCategory
import com.bcw.classeviva.data.TitleAbbreviation
import com.bcw.classeviva.data.timetable.Alignment as TTAlignment
import com.bcw.classeviva.platform.Platform
import com.bcw.classeviva.util.GradeFormat
import com.bcw.classeviva.ui.components.ButtonRow
import com.bcw.classeviva.ui.components.Cell
import com.bcw.classeviva.ui.components.FormIcon
import com.bcw.classeviva.ui.components.IconBadge
import com.bcw.classeviva.ui.components.ListGroup
import com.bcw.classeviva.ui.components.ListRow
import com.bcw.classeviva.ui.components.NavigationRow
import com.bcw.classeviva.ui.components.SelectButton
import com.bcw.classeviva.ui.components.Shaped
import com.bcw.classeviva.ui.components.Stepper
import com.bcw.classeviva.ui.components.ToggleRow
import com.bcw.classeviva.ui.components.ValueRow
import com.bcw.classeviva.ui.icons.Icon
import com.bcw.classeviva.ui.nav.Page
import com.bcw.classeviva.ui.screens.timetable.TimetableBackupSection
import com.bcw.classeviva.ui.screens.timetable.requestAlignment
import com.bcw.classeviva.ui.shell.LocalModel
import com.bcw.classeviva.ui.shell.LocalNavigator
import com.bcw.classeviva.ui.shell.LocalUi
import com.bcw.classeviva.ui.shell.PageScaffold
import com.bcw.classeviva.ui.shell.block
import com.bcw.classeviva.ui.theme.AccentPresets
import com.bcw.classeviva.ui.theme.ColorEngine
import com.bcw.classeviva.ui.theme.ColorStyle
import com.bcw.classeviva.ui.theme.ContrastLevel
import com.bcw.classeviva.ui.theme.CornerStyle
import com.bcw.classeviva.ui.theme.MotionStyle
import com.bcw.classeviva.ui.theme.RoundedText
import com.bcw.classeviva.ui.theme.Theme
import com.bcw.classeviva.ui.theme.argbFromHex
import com.bcw.classeviva.ui.theme.hexFromArgb
import com.bcw.classeviva.ui.theme.normalizeHex
import com.materialkolor.hct.Hct
import kotlinx.coroutines.launch

const val VERSION = BuildConfig.VERSION_NAME

enum class SettingsPane(val title: String, val icon: String) {
    general("Generale", "gear"),
    appearance("Aspetto", "palette"),
    grades("Voti", "graduationCap"),
    dashboard("Dashboard", "dashboard"),
    timetable("Orario", "timetable"),
    notifications("Notifiche", "bellActive"),
    accounts("Account", "users"),
}

@Composable
private fun paneTint(p: SettingsPane) = when (p) {
    SettingsPane.general -> Theme.scheme.onSurfaceVariant
    SettingsPane.appearance -> Theme.scheme.tertiary
    SettingsPane.grades -> Theme.colors.subject(1).color
    SettingsPane.dashboard -> Theme.scheme.primary
    SettingsPane.timetable -> Theme.colors.subject(3).color
    SettingsPane.notifications -> Theme.colors.fair.color
    SettingsPane.accounts -> Theme.colors.subject(5).color
}

/** Impostazioni: elenco delle sottopagine. */
@Composable
fun SettingsScreen() {
    val nav = LocalNavigator.current
    PageScaffold(title = "Impostazioni") {
        val groups = listOf(
            listOf(SettingsPane.general, SettingsPane.appearance),
            listOf(SettingsPane.grades, SettingsPane.dashboard, SettingsPane.timetable, SettingsPane.notifications),
            listOf(SettingsPane.accounts),
        )
        for ((i, group) in groups.withIndex()) block("g$i") {
            ListGroup {
                for (p in group) item(p) {
                    NavigationRow(p.title, { nav.push(if (p == SettingsPane.accounts) Page.Account else Page.SettingsPane(p.name)) }, leading = { IconBadge(p.icon, paneTint(p)) })
                }
            }
        }
    }
}

@Composable
fun SettingsPaneScreen(name: String) {
    val pane = runCatching { SettingsPane.valueOf(name) }.getOrDefault(SettingsPane.general)
    PageScaffold(title = pane.title, gap = 24.dp) {
        when (pane) {
            SettingsPane.general -> generalPane()
            SettingsPane.appearance -> block("appearance") { AppearancePane() }
            SettingsPane.grades -> block("grades") { GradesPane() }
            SettingsPane.dashboard -> block("dashboard") { DashboardPane() }
            SettingsPane.timetable -> block("timetable") { TimetablePane() }
            SettingsPane.notifications -> block("notifications") { NotificationsPane() }
            SettingsPane.accounts -> block("accounts") { AccountsContent() }
        }
    }
}

// MARK: - Generale

private fun androidx.compose.foundation.lazy.LazyListScope.generalPane() {
    block("security") { SecuritySection() }
    block("data") {
        val model = LocalModel.current
        val ui = LocalUi.current
        val scope = rememberCoroutineScope()
        ListGroup(header = "Dati") {
            item { ButtonRow("Aggiorna tutto", "refresh", { scope.launch { model.refreshAll() } }) }
            item { ButtonRow("Azzera compiti segnati come fatti", "listUnchecked", {
                ui.ask("Azzerare i compiti fatti?", "Tutti i compiti torneranno da fare.", "Azzera", destructive = true) {
                    model.preferences.clearCompleted()
                    model.rescheduleReminders()
                }
            }) }
        }
    }
    block("about") {
        val model = LocalModel.current
        val ui = LocalUi.current
        ListGroup(
            header = "Informazioni",
            footer = if (model.isCloud) "BCW (Better ClasseViVa) è un client non ufficiale e non è affiliato a Gruppo Spaggiari Parma S.p.A. Le credenziali degli account abbinati sono salvate cifrate nel tuo account BCW e inviate ai server di Classeviva per accedere."
            else "BCW (Better ClasseViVa) è un client non ufficiale e non è affiliato a Gruppo Spaggiari Parma S.p.A. Le credenziali sono salvate cifrate solo su questo dispositivo e inviate esclusivamente ai server di Classeviva.",
        ) {
            item { ValueRow("Versione", VERSION) }
            item { ListRow("Apri Classeviva sul web", leading = { FormIcon("compass") }, onClick = { ui.openUrl("https://web.spaggiari.eu") }, trailing = { Icon("externalLink", size = 18.dp, tint = Theme.scheme.onSurfaceVariant) }) }
        }
    }
    block("onboarding") {
        val ui = LocalUi.current
        ListGroup { item { ButtonRow("Rivedi l'introduzione", "sparkles", { ui.onboarding = true }) } }
    }
    block("github") {
        val ui = LocalUi.current
        val items = listOf(
            Triple("BCW", "L'app per iPhone e iPad (repository principale)", "github" to "https://github.com/kubos5/BCW/"),
            Triple("BCW-android", "L'app per Android", "github" to "https://github.com/kubos5/BCW-android/"),
            Triple("BCW-web", "La versione web", "globe" to "https://github.com/kubos5/BCW-web/"),
            Triple("BCW-api", "Il server degli account BCW", "server" to "https://github.com/kubos5/BCW-api/"),
            Triple("kubos5", "Il profilo dell'autore su GitHub", "person" to "https://github.com/kubos5/"),
        )
        ListGroup(header = "Su GitHub") {
            for ((title, subtitle, pair) in items) item(title) {
                ListRow(title, subtitle = subtitle, leading = { FormIcon(pair.first) }, onClick = { ui.openUrl(pair.second) }, trailing = { Icon("externalLink", size = 18.dp, tint = Theme.scheme.onSurfaceVariant) })
            }
        }
    }
}

@Composable
private fun SecuritySection() {
    val model = LocalModel.current
    val ui = LocalUi.current
    val context = LocalContext.current
    val available = Platform.lockAvailable(context)
    ListGroup(header = "Sicurezza", footer = "BCW si blocca all'apertura e quando torni dopo averla lasciata in secondo piano. Si sblocca con l'impronta, il volto o il blocco schermo del telefono.") {
        item {
            ToggleRow("Blocca con lo sblocco del telefono", model.preferences.useLock && available, { on ->
                if (!on) model.preferences.set("useLock", false)
                else Platform.authenticate(context, "Attiva il blocco di BCW") { ok ->
                    if (ok) {
                        model.preferences.set("useLock", true)
                        ui.toast("Blocco attivato.")
                    }
                }
            }, icon = "lock", enabled = available, subtitle = if (available) null else "Imposta un blocco schermo nelle impostazioni del telefono")
        }
    }
}

// MARK: - Aspetto

@Composable
private fun AppearancePane() {
    val model = LocalModel.current
    val p = model.preferences
    val dark = Theme.isDark
    var picking by remember { mutableStateOf(false) }
    val dynamic = p.dynamicColor
    Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
        ListGroup(header = "Tema") {
            item { ThemePreview() }
            item {
                ListRow("Tema", leading = { FormIcon("sun") }, trailing = {
                    SelectButton(AppearanceMode.entries.map { it to it.title }, p.appearance, { p.set("appearance", it.name) })
                })
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) item {
                ToggleRow("Colori del sistema", dynamic, { p.set("dynamicColor", it) }, icon = "colorize", subtitle = "Dallo sfondo del telefono (Material You), solo su questo dispositivo")
            }
        }
        ListGroup(
            header = "Colore d'accento",
            footer = if (dynamic) "Con i colori del sistema BCW usa quelli dello sfondo del telefono: il colore d'accento torna quando li spegni."
            else "Tutti i colori di BCW (pulsanti, superfici, schede) sono generati dal colore scelto con i colori dinamici di Material 3, nel tema chiaro e in quello scuro. I colori di materie, voti e assenze non cambiano.",
        ) {
            item {
                AccentPicker(p.string("accentColor"), ColorStyle.entries.firstOrNull { it.name == p.string("colorStyle") } ?: ColorStyle.tonalSpot, dark,
                    enabled = !dynamic, onPick = { p.set("accentColor", it) }, onCustom = { picking = true })
            }
            item {
                val style = ColorStyle.entries.firstOrNull { it.name == p.string("colorStyle") } ?: ColorStyle.tonalSpot
                ListRow("Stile dei colori", subtitle = style.subtitle, leading = { FormIcon("colorStyle") }, enabled = !dynamic, trailing = {
                    SelectButton(ColorStyle.entries.map { it to it.title }, style, { p.set("colorStyle", it.name) })
                })
            }
            item {
                ListRow("Contrasto", leading = { FormIcon("contrast") }, enabled = !dynamic, trailing = {
                    SelectButton(ContrastLevel.entries.map { it to it.title }, ContrastLevel.entries.firstOrNull { it.name == p.string("contrast") } ?: ContrastLevel.standard, { p.set("contrast", it.name) })
                })
            }
        }
        ListGroup(
            header = "Forme e movimento",
            footer = "Espressive: animazioni a molla con un piccolo rimbalzo, come in Material 3 Expressive. Standard: senza rimbalzo. Ridotte: brevi e senza movimenti ampi. I caratteri arrotondati usano l'asse ROND di Google Sans Flex.",
        ) {
            item {
                ListRow("Angoli", leading = { FormIcon("corners") }, trailing = {
                    SelectButton(CornerStyle.entries.map { it to it.title }, CornerStyle.entries.firstOrNull { it.name == p.string("corners") } ?: CornerStyle.standard, { p.set("corners", it.name) })
                })
            }
            item {
                ListRow("Animazioni", leading = { FormIcon("motion") }, trailing = {
                    SelectButton(MotionStyle.entries.map { it to it.title }, MotionStyle.entries.firstOrNull { it.name == p.string("motion") } ?: MotionStyle.expressive, { p.set("motion", it.name) })
                })
            }
            item {
                ListRow("Caratteri arrotondati", leading = { FormIcon("textStyle") }, trailing = {
                    SelectButton(RoundedText.entries.map { it to it.title }, RoundedText.entries.firstOrNull { it.name == p.string("roundedText") } ?: RoundedText.titles, { p.set("roundedText", it.name) })
                })
            }
            if (!p.isDefaultAppearance) item { ButtonRow("Ripristina l'aspetto di BCW", "refresh", { p.resetAppearance() }) }
        }
    }
    if (picking) ColorPickerDialog(normalizeHex(p.string("accentColor")) ?: DEFAULT_ACCENT, onDismiss = { picking = false }) { p.set("accentColor", it) }
}

/** Piccola anteprima dei componenti con i colori e le forme scelti. */
@Composable
fun ThemePreview() {
    Cell {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Shaped("cookie9Sided", Theme.scheme.primaryContainer, 56.dp) { Icon("palette", tint = Theme.scheme.onPrimaryContainer) }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Anteprima", style = Theme.type.style(16, 600), color = Theme.scheme.onSurface)
                Text("Pulsanti, schede e forme", style = Theme.type.style(13), color = Theme.scheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(Modifier.clip(RoundedCornerShape(16.dp)).background(Theme.scheme.primary).padding(horizontal = 12.dp, vertical = 6.dp)) { Text("Primario", style = Theme.type.style(12, 600), color = Theme.scheme.onPrimary) }
                    Box(Modifier.clip(RoundedCornerShape(16.dp)).background(Theme.scheme.secondaryContainer).padding(horizontal = 12.dp, vertical = 6.dp)) { Text("Tonale", style = Theme.type.style(12, 600), color = Theme.scheme.onSecondaryContainer) }
                    Box(Modifier.clip(RoundedCornerShape(Theme.radii.s)).background(Theme.scheme.tertiaryContainer).padding(horizontal = 10.dp, vertical = 6.dp)) { Text("Terziario", style = Theme.type.style(12, 600), color = Theme.scheme.onTertiaryContainer) }
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                for (c in listOf(Theme.scheme.primary, Theme.scheme.secondaryContainer, Theme.scheme.tertiaryContainer, Theme.scheme.surfaceContainerHighest)) {
                    Box(Modifier.size(14.dp).clip(CircleShape).background(c))
                }
            }
        }
    }
}

/** Campioni dei colori d'accento (come quelli di Android) e un colore qualsiasi. */
@Composable
fun AccentPicker(current: String, style: ColorStyle, dark: Boolean, enabled: Boolean = true, onPick: (String) -> Unit, onCustom: () -> Unit) {
    val accent = normalizeHex(current) ?: DEFAULT_ACCENT
    val isPreset = AccentPresets.any { it.first == accent }
    Cell(enabled = enabled) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                for ((hex, name) in AccentPresets) Swatch(hex, style, dark, hex == accent, name) { if (enabled) onPick(hex) }
                val selectedCustom = !isPreset
                Box(
                    Modifier.size(52.dp).clip(CircleShape).background(if (selectedCustom) Color(argbFromHex(accent)) else Theme.scheme.surfaceContainerHighest)
                        .clickable(enabled = enabled, onClick = onCustom),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(if (selectedCustom) "check" else "colorize", tint = if (selectedCustom) Color.White else Theme.scheme.onSurfaceVariant, contentDescription = "Scegli un colore qualsiasi")
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.size(14.dp).clip(CircleShape).background(Color(argbFromHex(accent))))
                Text(accent.uppercase(), style = Theme.type.numeral(14, 600), color = Theme.scheme.onSurface, modifier = Modifier.weight(1f))
                if (accent != DEFAULT_ACCENT) TextButton(onClick = { onPick(DEFAULT_ACCENT) }, enabled = enabled) {
                    Icon("refresh", size = 18.dp); Text("Colore di BCW", modifier = Modifier.padding(start = 6.dp))
                }
            }
        }
    }
}

@Composable
private fun Swatch(hex: String, style: ColorStyle, dark: Boolean, selected: Boolean, name: String, onClick: () -> Unit) {
    val colors = remember(hex, style, dark) { ColorEngine.swatch(hex, style, dark).map { Color(it) } }
    Box(
        Modifier.size(52.dp).clip(CircleShape).then(if (selected) Modifier.border(3.dp, Theme.scheme.primary, CircleShape) else Modifier).clickable(onClick = onClick).padding(if (selected) 5.dp else 0.dp).clip(CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Column(Modifier.matchParentSize()) {
            Box(Modifier.weight(1f).fillMaxWidth().background(colors[0]))
            Row(Modifier.weight(1f)) {
                Box(Modifier.weight(1f).fillMaxWidth().background(colors[1]))
                Box(Modifier.weight(1f).fillMaxWidth().background(colors[2]))
            }
        }
        if (selected) Icon("check", size = 20.dp, tint = Color.White, contentDescription = name)
    }
}

/** Un colore qualsiasi: tonalità, croma e tono (HCT, come i colori di Material) o il codice esadecimale. */
@Composable
fun ColorPickerDialog(initial: String, onDismiss: () -> Unit, onPick: (String) -> Unit) {
    val hct = remember { Hct.fromInt(argbFromHex(initial)) }
    var hue by remember { mutableFloatStateOf(hct.hue.toFloat()) }
    var chroma by remember { mutableFloatStateOf(hct.chroma.toFloat().coerceAtMost(120f)) }
    var toneValue by remember { mutableFloatStateOf(hct.tone.toFloat()) }
    var text by remember { mutableStateOf(initial.uppercase()) }
    val argb = Hct.from(hue.toDouble(), chroma.toDouble(), toneValue.toDouble()).toInt()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Colore d'accento") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.fillMaxWidth().padding(bottom = 8.dp).size(64.dp).clip(RoundedCornerShape(Theme.radii.l)).background(Color(argb)))
                Text("Tonalità", style = Theme.type.style(13, 600))
                Slider(hue, { hue = it; text = hexFromArgb(Hct.from(it.toDouble(), chroma.toDouble(), toneValue.toDouble()).toInt()).uppercase() }, valueRange = 0f..360f)
                Text("Saturazione", style = Theme.type.style(13, 600))
                Slider(chroma, { chroma = it; text = hexFromArgb(Hct.from(hue.toDouble(), it.toDouble(), toneValue.toDouble()).toInt()).uppercase() }, valueRange = 0f..120f)
                Text("Luminosità", style = Theme.type.style(13, 600))
                Slider(toneValue, { toneValue = it; text = hexFromArgb(Hct.from(hue.toDouble(), chroma.toDouble(), it.toDouble()).toInt()).uppercase() }, valueRange = 10f..90f)
                OutlinedTextField(text, { value ->
                    text = value
                    normalizeHex(value.let { if (it.startsWith("#")) it else "#$it" })?.let { hex ->
                        val h = Hct.fromInt(argbFromHex(hex))
                        hue = h.hue.toFloat(); chroma = h.chroma.toFloat().coerceAtMost(120f); toneValue = h.tone.toFloat()
                    }
                }, label = { Text("Codice") }, singleLine = true)
            }
        },
        confirmButton = { Button(onClick = { onPick(hexFromArgb(argb)); onDismiss() }) { Text("Usa questo colore") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annulla") } },
    )
}

// MARK: - Voti, Dashboard

@Composable
private fun GradesPane() {
    val p = LocalModel.current.preferences
    ListGroup(header = "Voti", footer = "I voti blu, annullati o \"non fa media\" sono sempre esclusi. I pesi sono quelli impostati dai docenti su Classeviva.") {
        item { ListRow("Calcolo della media", trailing = { SelectButton(AverageMode.entries.map { it to it.title }, p.averageMode, { p.set("averageMode", it.name) }, Modifier.fillMaxWidth(0.55f), maxLines = 2) }) }
        item { ToggleRow("Usa i pesi dei voti", p.weightedAverage, { p.set("weightedAverage", it) }) }
        item {
            ListRow("Media obiettivo", trailing = {
                val t = p.targetAverage
                Stepper(GradeFormat.short(t), { p.set("targetAverage", maxOf(4.0, t - 0.25)) }, { p.set("targetAverage", minOf(10.0, t + 0.25)) }, t > 4, t < 10)
            })
        }
    }
}

@Composable
private fun DashboardPane() {
    val p = LocalModel.current.preferences
    ListGroup(header = "Dashboard", footer = "La vista Lista mostra in alto la settimana, la vista Calendario il mese intero. \"Nei prossimi giorni\" c'è nella vista Lista. Il titolo abbreviato mostra la data in forma breve (es. \"Gio 1 ott\"); in automatico si rimpicciolisce solo se il nome completo non entra.") {
        item { ListRow("Vista", trailing = { SelectButton(DashboardMode.entries.map { it to it.title }, p.dashboardMode, { p.set("dashboardMode", it.name) }) }) }
        item { ToggleRow("Mostra \"Nei prossimi giorni\"", p.showUpcomingDays, { p.set("showUpcomingDays", it) }) }
        item { ListRow("Abbrevia il titolo", trailing = { SelectButton(TitleAbbreviation.entries.map { it to it.title }, p.titleAbbreviation, { p.set("titleAbbreviation", it.name) }) }) }
    }
}

// MARK: - Orario

@Composable
private fun TimetablePane() {
    val model = LocalModel.current
    val ui = LocalUi.current
    val p = model.preferences
    val store = model.timetable
    val synced = model.isCloud && p.syncs(SyncCategory.timetable)
    Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
        ListGroup(header = "Scheda", footer = "Nascosta, la scheda Orario sparisce dalla barra di navigazione. L'orario resta${if (synced) " e si sincronizza ancora con l'account BCW" else ""}: widget e lezione in corso continuano a usarlo. Per riaverla torna qui (Impostazioni, dall'ingranaggio in Tu).") {
            item { ToggleRow("Mostra la scheda Orario", p.showsTimetableTab, { p.set("showsTimetableTab", it) }, icon = "timetable") }
        }
        ListGroup(header = "Vista e allineamento", footer = "Di serie la vista Lista segna le verifiche di Classeviva e quelle segnate a mano; spegnendo \"Mostra le verifiche\" restano nel dettaglio dell'evento e nella vista Giorno. L'allineamento vale per tutto l'orario. Automatico: gli eventi iniziano all'ora o alla mezz'ora e durano 30 minuti o un'ora. Libero: qualsiasi orario e durata. Gli intervalli si posizionano sempre liberamente.") {
            item {
                ListRow("Allineamento", leading = { FormIcon(store.alignment.icon) }, trailing = {
                    SelectButton(TTAlignment.entries.map { it to it.shortTitle }, store.alignment, { requestAlignment(ui, store, it) })
                })
            }
            item { ToggleRow("Mostra le verifiche nella lista", p.timetableShowsTests, { p.set("timetableShowsTests", it) }, icon = "test") }
        }
        ListGroup(header = "Oggi", footer = "La card in cima all'orario di oggi mostra l'evento in corso con il tempo che manca, o il prossimo. La lezione in corso nelle notifiche (come le attività in diretta dell'iPhone) si aggiorna da sola durante la giornata.") {
            item { ToggleRow("Card dell'evento in corso", p.timetableShowsNowCard, { p.set("timetableShowsNowCard", it) }, icon = "schedule", subtitle = "In cima all'orario di oggi") }
            item { ToggleRow("Lezione in corso nelle notifiche", p.liveUpdates, { on -> p.set("liveUpdates", on); com.bcw.classeviva.platform.LiveUpdates.refresh(ui.context) }, icon = "timer", subtitle = "Solo su questo dispositivo") }
        }
        TimetableBackupSection()
        TimetableSyncSection()
    }
}

@Composable
private fun TimetableSyncSection() {
    val model = LocalModel.current
    val nav = LocalNavigator.current
    if (!model.isCloud) {
        ListGroup(header = "Sincronizzazione", footer = "Con un account BCW l'orario di ogni account Classeviva abbinato, con i file allegati, si sincronizza tra i tuoi dispositivi (anche con l'app per iPhone e con il web).") {
            item { ButtonRow("Accedi con un account BCW…", "cloud", { nav.push(Page.Account) }) }
        }
        return
    }
    val on = model.preferences.syncs(SyncCategory.timetable)
    ListGroup(
        header = "Sincronizzazione",
        footer = when {
            model.isDemo -> "L'orario della demo non si sincronizza."
            on -> "L'orario di questo account, con i file allegati, si sincronizza con l'account BCW ${model.cloudUser?.username ?: ""}."
            else -> "L'orario resta solo su questo dispositivo. Riaccendendo la sincronizzazione vince il lato cambiato nel frattempo (se sono cambiati entrambi, questo dispositivo)."
        },
    ) {
        if (!model.isDemo) item { SyncCategoryRow(SyncCategory.timetable, "Sincronizza l'orario", "backup") }
        if (on || model.isDemo) {
            item { ValueRow("Sincronizzazione", syncStatusText(model.sync), icon = "cloud", subtitle = model.sync.fileWarning) }
            item {
                val scope = rememberCoroutineScope()
                ButtonRow("Sincronizza ora", "refresh", { scope.launch { syncNow(model) } }, enabled = model.sync.status != com.bcw.classeviva.data.SyncStatus.syncing && !model.isDemo)
            }
        }
    }
}

// MARK: - Notifiche

@Composable
private fun NotificationsPane() {
    val model = LocalModel.current
    val ui = LocalUi.current
    val context = LocalContext.current
    val p = model.preferences
    var pending by remember { mutableStateOf<(() -> Unit)?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) pending?.invoke() else ui.toast("Senza il permesso BCW non può mostrare notifiche.")
        pending = null
    }
    fun withPermission(action: () -> Unit) {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            pending = action
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else action()
    }
    val hours = (14..22).map { it to "$it:00" }
    Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
        ListGroup(header = "Promemoria", footer = "Ricevi una notifica la sera prima di ogni compito o verifica. I compiti segnati come fatti vengono saltati. Puoi anche esportare l'agenda nel calendario: ogni compito e verifica avrà un avviso all'orario scelto.") {
            item {
                ToggleRow("Promemoria compiti e verifiche", p.homeworkReminders, { on ->
                    if (on) withPermission { p.set("homeworkReminders", true); model.rescheduleReminders() }
                    else { p.set("homeworkReminders", false); model.rescheduleReminders() }
                }, icon = "bell")
            }
            if (p.homeworkReminders) item {
                ListRow("Orario", leading = { FormIcon("clock") }, trailing = { SelectButton(hours, p.reminderHour, { p.set("reminderHour", it); model.rescheduleReminders() }) })
            }
            item { ButtonRow("Esporta l'agenda nel calendario", "calendarPlus", { com.bcw.classeviva.ui.screens.exportAgenda(model, ui) }) }
        }
        ListGroup(header = "Novità", footer = "Una notifica per ogni novità trovata aggiornando i dati, anche con BCW chiusa: Android controlla più o meno ogni ora, quando c'è la connessione. La prima volta BCW ricorda ciò che c'è già senza notificarlo.") {
            for (kind in NewsKind.entries) item(kind) {
                ToggleRow(kind.title, p.newsEnabled(kind), { on ->
                    if (on) withPermission { p.setNews(kind, true); com.bcw.classeviva.platform.BackgroundRefresh.schedule(context, model) }
                    else { p.setNews(kind, false); com.bcw.classeviva.platform.BackgroundRefresh.schedule(context, model) }
                }, icon = kind.icon)
            }
        }
        ListGroup {
            item {
                ListRow("Impostazioni delle notifiche di Android", leading = { FormIcon("gear") }, onClick = {
                    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    runCatching { context.startActivity(intent) }
                }, trailing = { Icon("externalLink", size = 18.dp, tint = Theme.scheme.onSurfaceVariant) })
            }
        }
    }
}
