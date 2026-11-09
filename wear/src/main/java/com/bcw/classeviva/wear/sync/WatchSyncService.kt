package com.bcw.classeviva.wear.sync

import com.bcw.classeviva.wear.data.WatchData
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.WearableListenerService

/** Riceve l'orario che l'app del telefono mette nella Data Layer. */
class WatchSyncService : WearableListenerService() {
    override fun onDataChanged(dataEvents: DataEventBuffer) {
        for (event in dataEvents) {
            if (event.type != DataEvent.TYPE_CHANGED) continue
            if (!event.dataItem.uri.path.orEmpty().startsWith("/bcw/")) continue
            val json = DataMapItem.fromDataItem(event.dataItem).dataMap.getString("json") ?: continue
            WatchData.save(this, json)
        }
    }
}
