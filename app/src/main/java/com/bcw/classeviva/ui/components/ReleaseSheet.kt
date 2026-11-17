package com.bcw.classeviva.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.bcw.classeviva.data.ReleaseInfo
import com.bcw.classeviva.ui.shell.LocalUi
import com.bcw.classeviva.ui.theme.Theme
import com.bcw.classeviva.util.Fmt

/** Le novità di una release (dopo un aggiornamento o da Impostazioni › Generale). */
@Composable
fun ReleaseSheet(release: ReleaseInfo, onDismiss: () -> Unit) {
    val ui = LocalUi.current
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state, containerColor = Theme.scheme.surfaceContainerLow) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Novità di BCW ${release.version}", style = Theme.type.style(24, 500, rounded = true), color = Theme.scheme.onSurface)
            release.publishedAt?.let {
                Text("Pubblicata il ${Fmt.dayMonthYear(it.toLocalDate())}", style = Theme.type.style(13), color = Theme.scheme.onSurfaceVariant)
            }
            ReleaseNotesText(release.notes)
            Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("Continua") }
            TextButton(onClick = { ui.openUrl(release.url) }, modifier = Modifier.fillMaxWidth()) { Text("Apri su GitHub") }
        }
    }
}
