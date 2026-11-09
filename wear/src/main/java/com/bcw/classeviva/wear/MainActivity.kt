package com.bcw.classeviva.wear

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.bcw.classeviva.wear.data.WatchData
import com.bcw.classeviva.wear.data.demoPayload
import com.bcw.classeviva.wear.ui.WearApp
import com.bcw.classeviva.wear.ui.theme.WearAppTheme
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.wearable.Wearable

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WatchData.load(this)
        setContent {
            val payload = WatchData.payload ?: demoPayload()
            WearAppTheme(
                accent = payload.accent,
                colorStyle = payload.colorStyle,
                contrast = payload.contrast,
                appearance = payload.appearance,
                palette = payload.palette,
            ) {
                WearApp(payload)
            }
        }
        requestSync()
    }

    override fun onResume() {
        super.onResume()
        WatchData.load(this)
    }

    /** Chiede al telefono l'orario, così la prima apertura non aspetta il prossimo salvataggio. */
    private fun requestSync() {
        runCatching {
            if (GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(this) != ConnectionResult.SUCCESS) return
            Wearable.getNodeClient(this).connectedNodes.addOnSuccessListener { nodes ->
                nodes.forEach { node ->
                    runCatching {
                        Wearable.getMessageClient(this).sendMessage(node.id, "/bcw/request", ByteArray(0))
                    }
                }
            }
        }
    }
}
