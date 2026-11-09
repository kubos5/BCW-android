package com.bcw.classeviva.platform

import com.bcw.classeviva.BCWApplication
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService

/** Risponde all'orologio che chiede l'orario (all'apertura dell'app Wear OS). */
class WearSyncService : WearableListenerService() {
    override fun onMessageReceived(event: MessageEvent) {
        if (event.path != WearSync.REQUEST_PATH) return
        val app = application as? BCWApplication ?: return
        WearSync.pushNow(this, app.model.timetable, app.model.preferences)
    }
}
