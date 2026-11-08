package com.bcw.classeviva.ui.screens.timetable

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import com.bcw.classeviva.data.timetable.EntryColor
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDialog
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Stable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.material3.SheetState
import kotlinx.coroutines.CoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bcw.classeviva.data.timetable.Alignment as TTAlignment
import com.bcw.classeviva.data.timetable.EntryTint
import com.bcw.classeviva.data.timetable.TimeOfDay
import com.bcw.classeviva.data.timetable.TimetableEntry
import com.bcw.classeviva.data.timetable.TimetableStore
import com.bcw.classeviva.data.timetable.lightened
import com.bcw.classeviva.ui.components.ButtonRow
import com.bcw.classeviva.ui.components.ListGroup
import com.bcw.classeviva.ui.icons.Icon
import com.bcw.classeviva.ui.screens.settings.VERSION
import com.bcw.classeviva.ui.shell.ConfirmRequest
import com.bcw.classeviva.ui.shell.DialogAction
import com.bcw.classeviva.ui.shell.LocalModel
import com.bcw.classeviva.ui.shell.LocalUi
import com.bcw.classeviva.ui.shell.UiController
import com.bcw.classeviva.ui.theme.Theme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.sin

/** Colore di una tinta dell'orario (i colori scelti a mano si schiariscono nel tema scuro). */
@Composable
fun tintColor(t: EntryTint): Color = when (t) {
    is EntryTint.Subject -> Theme.colors.subject(t.index).color
    EntryTint.Break -> Theme.colors.breakTint
    is EntryTint.Hex -> Color(0xFF000000.toInt() or (if (Theme.isDark) lightened(t.hex, 0.3) else t.hex))
}

@Composable
fun entryColor(entry: TimetableEntry): Color = tintColor(LocalModel.current.timetable.tint(entry))

/** Linea ondulata orizzontale (separatori degli intervalli). */
@Composable
fun WavyLine(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.height(8.dp)) {
        val wave = 10.dp.toPx()
        val amp = 2.dp.toPx()
        val path = Path()
        var x = 0f
        path.moveTo(0f, size.height / 2)
        while (x <= size.width) {
            path.lineTo(x, size.height / 2 + amp * sin(2 * PI * x / wave).toFloat())
            x += 1.5f
        }
        drawPath(path, color, style = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round))
    }
}

/** Chiude un foglio con l'animazione e poi fa `then` (es. aprire un altro foglio). */
@Stable
class SheetHandle internal constructor(private val scope: CoroutineScope, val state: SheetState) {
    internal var onDismiss: () -> Unit = {}

    fun close(then: () -> Unit = {}) {
        scope.launch { state.hide() }.invokeOnCompletion {
            onDismiss()
            then()
        }
    }
}

@Composable
fun rememberSheetHandle(): SheetHandle {
    val scope = rememberCoroutineScope()
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    return remember { SheetHandle(scope, state) }
}

/** Foglio dal basso con la riga del titolo e i pulsanti (come `sheetChrome` dell'app). */
@Composable
fun BCWSheet(
    title: String,
    onDismiss: () -> Unit,
    handle: SheetHandle = rememberSheetHandle(),
    leading: (@Composable () -> Unit)? = null,
    trailing: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    val dismiss by rememberUpdatedState(onDismiss)
    handle.onDismiss = { dismiss() }
    ModalBottomSheet(onDismissRequest = { dismiss() }, sheetState = handle.state, containerColor = Theme.scheme.surfaceContainerLow, dragHandle = { BottomSheetDefaults.DragHandle() }) {
        Row(Modifier.fillMaxWidth().padding(start = if (leading != null) 4.dp else 20.dp, end = 12.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            leading?.invoke()
            Text(title, style = Theme.type.style(22, 500, rounded = true), color = Theme.scheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(start = if (leading != null) 4.dp else 0.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp), content = trailing)
        }
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp).navigationBarsPadding().imePadding(),
            verticalArrangement = Arrangement.spacedBy(20.dp), content = content,
        )
    }
}

@Composable
fun SheetDoneButton(label: String = "Fine", icon: String = "check", enabled: Boolean = true, onClick: () -> Unit) {
    Button(onClick = onClick, enabled = enabled, contentPadding = androidx.compose.material3.ButtonDefaults.SmallContentPadding, modifier = Modifier.height(40.dp)) {
        Icon(icon, size = 18.dp)
        Text(label, modifier = Modifier.padding(start = 6.dp))
    }
}

@Composable
fun SheetCloseButton(onClick: () -> Unit) = IconButton(onClick = onClick) { Icon("x", contentDescription = "Chiudi") }

/** Orario (ore e minuti) in una pillola che apre il selettore dell'orario di Material 3. */
@Composable
fun TimeField(minutes: Int, onChange: (Int) -> Unit, modifier: Modifier = Modifier, title: String = "Orario") {
    var open by remember { mutableStateOf(false) }
    FilledTonalButton(onClick = { open = true }, modifier = modifier) {
        Text(TimeOfDay.string(minutes.coerceAtMost(1439)), style = Theme.type.numeral(16, 600))
    }
    if (open) {
        val state = rememberTimePickerState(initialHour = minutes.coerceIn(0, 1439) / 60, initialMinute = minutes.coerceIn(0, 1439) % 60, is24Hour = true)
        TimePickerDialog(
            onDismissRequest = { open = false },
            title = { Text(title) },
            confirmButton = { TextButton(onClick = { onChange(state.hour * 60 + state.minute); open = false }) { Text("OK") } },
            dismissButton = { TextButton(onClick = { open = false }) { Text("Annulla") } },
        ) { TimePicker(state) }
    }
}

// MARK: - Allineamento

/** Cambia l'allineamento; passando all'automatico con eventi non allineati chiede conferma. */
fun requestAlignment(ui: UiController, store: TimetableStore, value: TTAlignment) {
    if (value == store.alignment) return
    val unaligned = store.unalignedEntries
    if (value != TTAlignment.automatic || unaligned.isEmpty()) {
        store.setAlignment(value)
        return
    }
    val n = unaligned.size
    val split = unaligned.count { it.alignedPieces().size > 1 }
    var text = if (n == 1) "Un evento non è allineato alle ore e alle mezz'ore." else "$n eventi non sono allineati alle ore e alle mezz'ore."
    text += " Con l'allineamento automatico inizio e fine verranno spostati all'ora o alla mezz'ora più vicina"
    text += if (split > 0) " e gli eventi più lunghi di un'ora verranno divisi in eventi da un'ora (o mezz'ora) di seguito, mostrati uniti." else "."
    text += " Gli intervalli restano come sono."
    ui.ask("Allineare gli eventi?", text, "Allinea") { store.setAlignment(TTAlignment.automatic) }
}

/** Elimina un evento (o tutti quelli con lo stesso nome). */
fun deleteEntryDialog(ui: UiController, store: TimetableStore, entry: TimetableEntry, after: () -> Unit = {}) {
    val others = store.sameNameEntries(entry)
    val title = entry.displayTitle
    ui.ask(ConfirmRequest(
        title = "Eliminare «$title»?",
        confirm = DialogAction("Elimina solo questo", destructive = true) { store.delete(listOf(entry.id)); after() },
        extra = if (others.isNotEmpty()) listOf(DialogAction("Elimina tutti gli eventi «$title» (${others.size + 1})", destructive = true) {
            store.delete(listOf(entry.id) + others.map { it.id }); after()
        }) else emptyList(),
    ))
}

// MARK: - File

data class PickedFile(val name: String, val bytes: ByteArray)

fun readPicked(context: android.content.Context, uri: Uri): PickedFile? = runCatching {
    var name = "file"
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
        if (c.moveToFirst()) c.getString(0)?.let { name = it }
    }
    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
    PickedFile(name, bytes)
}.getOrNull()

// MARK: - Backup

/** Esporta, importa (con due conferme) ed elimina l'orario. */
@Composable
fun TimetableBackupSection() {
    val model = LocalModel.current
    val ui = LocalUi.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = model.timetable
    val hasOwner = store.owner != null
    val synced = model.isCloud && model.preferences.syncs(com.bcw.classeviva.data.SyncCategory.timetable)
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            try {
                val text = store.makeBackup(VERSION)
                withContext(Dispatchers.IO) { context.contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray()) } }
                ui.toast("Backup dell'orario esportato.")
            } catch (e: Exception) {
                ui.toast("Esportazione non riuscita: ${e.message}")
            }
        }
    }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val picked = withContext(Dispatchers.IO) { readPicked(context, uri) }
            val backup = try {
                store.readBackup(picked?.bytes?.toString(Charsets.UTF_8) ?: "")
            } catch (e: Exception) {
                ui.ask("Impossibile importare", e.message, "OK") {}
                return@launch
            }
            val lessons = backup.timetable.entries.count { !it.isBreak }
            ui.ask("Importare il backup?", "Il backup ha $lessons lezioni${if (backup.files.isNotEmpty()) " e ${backup.files.size} file" else ""}. L'orario attuale verrà sostituito.", "Continua") {
                ui.ask("Sostituire l'orario attuale?", "Eventi, colori, note, file e verifiche segnate dell'orario attuale verranno eliminati. L'operazione non si può annullare.", "Sostituisci", destructive = true) {
                    scope.launch {
                        try {
                            store.restore(backup)
                            ui.toast("Orario importato.")
                        } catch (e: Exception) {
                            ui.ask("Impossibile importare", e.message, "OK") {}
                        }
                    }
                }
            }
        }
    }
    ListGroup(
        header = "Backup dell'orario",
        footer = "${if (synced) "L'orario di ogni account abbinato si sincronizza con il tuo account BCW." else "L'orario è salvato solo su questo dispositivo, separato per ogni account."} Il backup contiene eventi, colori, palette, note, verifiche segnate e file allegati; importandolo sostituisce l'orario attuale. È compatibile con l'app BCW per iPhone e con la versione web.",
    ) {
        item { ButtonRow("Esporta un backup dell'orario", "upload", { exporter.launch("Orario BCW.json") }, enabled = hasOwner) }
        item { ButtonRow("Importa un backup…", "download", { importer.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }, enabled = hasOwner) }
        item {
            ButtonRow("Elimina l'orario", "trash", {
                ui.ask("Eliminare l'orario?", "Tutti gli eventi, le note, i file e le verifiche segnate verranno eliminati.", "Elimina", destructive = true) { store.deleteAll() }
            }, destructive = true, enabled = !store.isEmpty)
        }
    }
}

// MARK: - Colori

fun colorDescription(color: EntryColor?, store: TimetableStore): String = when (color) {
    null -> "Automatico"
    is EntryColor.Pastel -> "Suggerito"
    is EntryColor.Custom -> "Personalizzato"
    is EntryColor.Series -> store.palette(color.paletteID)?.name ?: "Palette"
}

/** Colore di un esadecimale dell'orario (schiarito nel tema scuro, come i colori degli eventi). */
@Composable
fun hexColor(hex: Int): Color = tintColor(EntryTint.Hex(hex))

/** Cerchio pieno di un colore (anteprima del colore di un evento). */
@Composable
fun SwatchDot(color: Color, size: Dp = 20.dp) = Box(Modifier.size(size).clip(CircleShape).background(color))
