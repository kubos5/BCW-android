package com.bcw.classeviva

import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.bcw.classeviva.platform.LiveUpdates
import com.bcw.classeviva.platform.Platform
import com.bcw.classeviva.ui.BCWRoot
import com.bcw.classeviva.ui.LockState
import kotlinx.coroutines.launch

class MainActivity : FragmentActivity() {
    private val lock = LockState()
    private var stoppedAt = 0L
    private var authenticating = false

    private val model get() = (application as BCWApplication).model

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (model.preferences.useLock && Platform.lockAvailable(this)) lock.locked = true
        handle(intent)
        setContent {
            BCWRoot(model, lock, onUnlock = ::unlock) { dark ->
                // Barre di sistema trasparenti con le icone del colore giusto per il tema.
                enableEdgeToEdge(
                    statusBarStyle = if (dark) SystemBarStyle.dark(android.graphics.Color.TRANSPARENT) else SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
                    navigationBarStyle = if (dark) SystemBarStyle.dark(android.graphics.Color.TRANSPARENT) else SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    private fun handle(intent: Intent?) {
        val uri = intent?.data ?: return
        if (uri.scheme == "bcw") model.open(uri)
    }

    override fun onStart() {
        super.onStart()
        // Blocco: all'apertura e tornando dopo averla lasciata in secondo piano.
        if (stoppedAt > 0 && model.preferences.useLock && Platform.lockAvailable(this) && SystemClock.elapsedRealtime() - stoppedAt > 2_000) lock.locked = true
        model.refreshIfStale()
        LiveUpdates.refresh(this)
    }

    override fun onStop() {
        super.onStop()
        stoppedAt = SystemClock.elapsedRealtime()
        // Le modifiche in attesa partono subito verso l'account BCW.
        lifecycleScope.launch { runCatching { model.sync.flush() } }
    }

    private fun unlock() {
        if (!lock.locked || authenticating) return
        authenticating = true
        Platform.authenticate(this, "Sblocca BCW") { ok ->
            authenticating = false
            if (ok) lock.locked = false
        }
    }
}
