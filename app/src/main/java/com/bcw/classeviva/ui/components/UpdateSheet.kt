package com.bcw.classeviva.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.bcw.classeviva.BuildConfig
import com.bcw.classeviva.data.ReleaseInfo
import com.bcw.classeviva.data.UpdateInstaller
import com.bcw.classeviva.ui.shell.LocalUi
import com.bcw.classeviva.ui.theme.Theme
import com.bcw.classeviva.util.Fmt
import kotlinx.coroutines.launch

/**
 * Una versione più recente su GitHub: le note e l'aggiornamento dentro all'app. "Aggiorna" scarica
 * l'APK (con l'impronta, se GitHub la dà) e passa all'installer di sistema, che chiede la conferma
 * all'utente. "Più tardi" e "Non avvisarmi più" li gestisce chi mostra il foglio.
 */
@Composable
fun UpdateSheet(release: ReleaseInfo, onSnooze: () -> Unit, onDisableAlerts: () -> Unit, onDismiss: () -> Unit) {
    val ui = LocalUi.current
    val installed = BuildConfig.VERSION_NAME
    var progress by remember { mutableFloatStateOf(-1f) }
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state, containerColor = Theme.scheme.surfaceContainerLow) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("È disponibile BCW ${release.version}", style = Theme.type.style(24, 500, rounded = true), color = Theme.scheme.onSurface)
            Text("Hai la $installed", style = Theme.type.style(13), color = Theme.scheme.onSurfaceVariant)
            release.publishedAt?.let {
                Text("Pubblicata il ${Fmt.dayMonthYear(it.toLocalDate())}", style = Theme.type.style(13), color = Theme.scheme.onSurfaceVariant)
            }
            ReleaseNotesText(release.notes)
            if (progress >= 0f) {
                LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                Text("Scarico l'aggiornamento…", style = Theme.type.style(13), color = Theme.scheme.onSurfaceVariant)
            } else {
                Button(
                    onClick = {
                        val asset = release.apk ?: run {
                            ui.openUrl(release.url)
                            return@Button
                        }
                        val installer = UpdateInstaller(ui.context)
                        if (!installer.canInstall()) {
                            installer.requestInstallPermission()
                            ui.toast("Attiva l'installazione per BCW, poi riprova.")
                            return@Button
                        }
                        ui.scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                            val result = installer.download(asset) { p -> progress = p }
                            result.fold(
                                onSuccess = { prepared ->
                                    installer.install(prepared.file) { message ->
                                        if (message == null) ui.toast("Conferma l'installazione a schermo.")
                                        else ui.toast(message)
                                    }
                                    onDismiss()
                                },
                                onFailure = {
                                    progress = -1f
                                    ui.toast(it.message ?: "Il download non è riuscito.")
                                },
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Aggiorna") }
                TextButton(onClick = { onSnooze(); onDismiss() }, modifier = Modifier.fillMaxWidth()) { Text("Più tardi") }
                TextButton(onClick = { onDisableAlerts(); onDismiss() }, modifier = Modifier.fillMaxWidth()) { Text("Non avvisarmi più") }
                Text("Gli avvisi si riattivano in Impostazioni › Generale.", style = Theme.type.style(12), color = Theme.scheme.onSurfaceVariant)
            }
        }
    }
}
