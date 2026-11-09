package com.bcw.classeviva.platform

import android.content.Context
import androidx.compose.ui.graphics.toArgb
import com.bcw.classeviva.data.Preferences
import com.bcw.classeviva.data.timetable.EntryTint
import com.bcw.classeviva.data.timetable.TimetableStore
import com.bcw.classeviva.data.timetable.entryOrder
import com.bcw.classeviva.data.timetable.lightened
import com.bcw.classeviva.ui.theme.SubjectColors
import com.bcw.classeviva.ui.theme.breakTintFor
import com.bcw.classeviva.ui.theme.darkSubjects
import com.bcw.classeviva.ui.theme.lightSubjects
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Sincronizzazione dell'orario con l'app Wear OS, tramite la Data Layer di Google Play services.
 *
 * Il telefono invia le lezioni (senza note e allegati), le impostazioni di aspetto e la palette
 * delle materie. Le tinte scelte a mano arrivano già in entrambi i temi (chiaro e scuro, con lo
 * schiarimento del telefono), così l'orologio mostra gli stessi colori. Il formato è interno ad
 * Android e non tocca i formati condivisi con iOS e web.
 */
object WearSync {
    const val TIMETABLE_PATH = "/bcw/timetable"
    const val REQUEST_PATH = "/bcw/request"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var pending: Job? = null

    /** Un salvataggio ravvicinato vale una sola spedizione. */
    fun timetableChanged(context: Context, store: TimetableStore, preferences: Preferences) {
        pending?.cancel()
        pending = scope.launch {
            delay(700)
            pushNow(context, store, preferences)
        }
    }

    fun pushNow(context: Context, store: TimetableStore, preferences: Preferences) {
        runCatching {
            if (GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context) != ConnectionResult.SUCCESS) return
            val request = PutDataMapRequest.create(TIMETABLE_PATH).apply {
                dataMap.putString("json", payload(store, preferences))
                dataMap.putLong("updatedAt", System.currentTimeMillis())
            }.asPutDataRequest().setUrgent()
            // Senza un orologio collegato la richiesta resta in coda e parte alla connessione.
            Wearable.getDataClient(context).putDataItem(request)
        }
    }

    fun payload(store: TimetableStore, preferences: Preferences): String {
        val entries = store.timetable.entries.sortedWith(entryOrder).map { entry ->
            buildJsonObject {
                put("id", entry.id)
                put("weekday", entry.weekday)
                put("start", entry.start)
                put("end", entry.end)
                put("kind", entry.kind.name)
                put("title", entry.displayTitle)
                put("room", entry.room)
                put("teacher", entry.teacher)
                put("tint", tintJson(store.tint(entry)))
            }
        }
        return buildJsonObject {
            put("version", 2)
            store.owner?.let { put("owner", it) }
            put("accent", preferences.string("accentColor"))
            put("colorStyle", preferences.string("colorStyle"))
            put("contrast", preferences.string("contrast"))
            put("appearance", preferences.string("appearance"))
            put("updatedAt", System.currentTimeMillis())
            put("palette", paletteJson())
            put("entries", JsonArray(entries))
        }.toString()
    }

    /** Tabella delle materie e colore degli intervalli, per entrambi i temi, come li usa il telefono. */
    private fun paletteJson(): JsonObject = buildJsonObject {
        put("light", modeJson(lightSubjects, breakTintFor(dark = false)))
        put("dark", modeJson(darkSubjects, breakTintFor(dark = true)))
    }

    private fun modeJson(subjects: List<SubjectColors>, breakTint: androidx.compose.ui.graphics.Color): JsonObject = buildJsonObject {
        put("subjects", JsonArray(subjects.map { s ->
            JsonArray(listOf(s.color, s.container, s.onContainer).map { JsonPrimitive(it.toArgb() and 0xFFFFFF) })
        }))
        put("break", breakTint.toArgb() and 0xFFFFFF)
    }

    private fun tintJson(tint: EntryTint): JsonObject = buildJsonObject {
        when (tint) {
            is EntryTint.Subject -> {
                put("kind", "subject")
                put("index", tint.index)
            }
            EntryTint.Break -> put("kind", "break")
            is EntryTint.Hex -> {
                put("kind", "hex")
                put("light", tint.hex and 0xFFFFFF)
                put("dark", lightened(tint.hex, 0.3) and 0xFFFFFF)
            }
        }
    }
}
