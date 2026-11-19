package com.bcw.classeviva.platform

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.widget.Toast

/**
 * Risposta del `PackageInstaller` all'aggiornamento dentro all'app (`UpdateInstaller.install`).
 * Con `STATUS_PENDING_USER_ACTION` il sistema non mostra da solo la conferma: mette nell'intent la
 * sua schermata ("Vuoi aggiornare questa app?") e tocca all'app aprirla. Prima la risposta andava
 * a un broadcast che nessuno ascoltava, quindi la conferma non compariva mai.
 */
class InstallStatusReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION") intent.getParcelableExtra(Intent.EXTRA_INTENT)
                } ?: return
                runCatching { context.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                    .onFailure { toast(context, "Non riesco ad aprire la conferma dell'installazione.") }
            }
            // Riuscito: il sistema chiude e aggiorna BCW, non c'è niente da fare.
            PackageInstaller.STATUS_SUCCESS -> Unit
            PackageInstaller.STATUS_FAILURE_ABORTED -> toast(context, "Aggiornamento annullato.")
            else -> {
                val detail = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                toast(context, "Aggiornamento non installato" + (detail?.let { ": $it" } ?: " (errore $status)."))
            }
        }
    }

    private fun toast(context: Context, text: String) = Toast.makeText(context, text, Toast.LENGTH_LONG).show()
}
