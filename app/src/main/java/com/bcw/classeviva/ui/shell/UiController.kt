package com.bcw.classeviva.ui.shell

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.core.content.FileProvider
import com.bcw.classeviva.data.AppModel
import com.bcw.classeviva.data.DownloadedFile
import com.bcw.classeviva.ui.nav.Navigator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Un'azione di un dialogo di conferma. */
data class DialogAction(val label: String, val destructive: Boolean = false, val run: () -> Unit)

data class ConfirmRequest(
    val title: String,
    val message: String? = null,
    val confirm: DialogAction,
    val extra: List<DialogAction> = emptyList(),
    val cancelLabel: String = "Annulla",
)

/** Un file da mostrare nel visore (immagine, PDF, testo) con il nome e il tipo. */
data class ViewerFile(val file: File, val name: String, val type: String)

/** Avvisi, dialoghi, visore dei file, popup degli account: le parti dell'interfaccia comuni a tutte le pagine. */
@Stable
class UiController(val context: Context, val scope: CoroutineScope) {
    val snackbar = SnackbarHostState()
    var confirm by mutableStateOf<ConfirmRequest?>(null)
    var viewer by mutableStateOf<ViewerFile?>(null)
    var accountSwitcher by mutableStateOf(false)
    var addAccount by mutableStateOf(false)
    /** Introduzione aperta (prima apertura o "Rivedi l'introduzione"). */
    var onboarding by mutableStateOf(false)
    /** Account del dispositivo da abbinare dopo l'accesso all'account BCW. */
    var linkCandidates by mutableStateOf<List<com.bcw.classeviva.data.SavedAccount>>(emptyList())

    fun toast(message: String) {
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            snackbar.showSnackbar(message)
        }
    }

    fun ask(request: ConfirmRequest) { confirm = request }

    fun ask(title: String, message: String?, confirmLabel: String, destructive: Boolean = false, onConfirm: () -> Unit) {
        confirm = ConfirmRequest(title, message, DialogAction(confirmLabel, destructive, onConfirm))
    }

    /** Apre un file scaricato nel visore di BCW. */
    fun open(model: AppModel, downloaded: DownloadedFile) {
        scope.launch {
            val f = withContext(Dispatchers.IO) { model.temporaryFile(downloaded.name).apply { writeBytes(downloaded.data) } }
            viewer = ViewerFile(f, downloaded.name, downloaded.type)
        }
    }

    fun open(file: File, name: String, type: String) { viewer = ViewerFile(file, name, type) }

    fun uri(file: File): Uri = FileProvider.getUriForFile(context, context.packageName + ".files", file)

    /** Apre il file con un'altra app. */
    fun openExternally(file: File, type: String) {
        val intent = Intent(Intent.ACTION_VIEW).setDataAndType(uri(file), type)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(Intent.createChooser(intent, "Apri con").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: ActivityNotFoundException) {
            toast("Nessuna app può aprire questo file.")
        }
    }

    fun share(file: File, type: String) {
        val intent = Intent(Intent.ACTION_SEND).setType(type).putExtra(Intent.EXTRA_STREAM, uri(file))
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(Intent.createChooser(intent, "Condividi").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun shareText(text: String) {
        val intent = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
        context.startActivity(Intent.createChooser(intent, "Condividi").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun copy(text: String, message: String = "Copiato negli appunti.") {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("BCW", text))
        if (android.os.Build.VERSION.SDK_INT < 33) toast(message)
    }

    /** Apre un sito in una scheda personalizzata (come `SFSafariViewController` nell'app iOS). */
    fun openUrl(url: String) {
        try {
            CustomTabsIntent.Builder().setShowTitle(true).build().apply { intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }.launchUrl(context, Uri.parse(url))
        } catch (e: Exception) {
            try {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (_: ActivityNotFoundException) {
                toast("Impossibile aprire il collegamento.")
            }
        }
    }
}

val LocalUi = staticCompositionLocalOf<UiController> { error("UiController non impostato") }
val LocalModel = staticCompositionLocalOf<AppModel> { error("AppModel non impostato") }
val LocalNavigator = staticCompositionLocalOf<Navigator> { error("Navigator non impostato") }
