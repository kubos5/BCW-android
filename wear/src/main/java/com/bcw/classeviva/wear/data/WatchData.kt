package com.bcw.classeviva.wear.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** L'ultimo orario arrivato dal telefono, salvato così l'orologio funziona anche senza telefono. */
object WatchData {
    private const val PREFS = "bcw.wear"
    private const val KEY_PAYLOAD = "payload"

    var payload: WatchPayload? by mutableStateOf(null)
        private set

    fun load(context: Context) {
        val text = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_PAYLOAD, null)
        payload = text?.let(::parsePayload)
    }

    fun save(context: Context, text: String) {
        val parsed = parsePayload(text) ?: return
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_PAYLOAD, text).apply()
        payload = parsed
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY_PAYLOAD).apply()
        payload = null
    }
}
