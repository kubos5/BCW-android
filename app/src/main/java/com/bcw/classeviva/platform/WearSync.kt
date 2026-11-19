package com.bcw.classeviva.platform

import android.content.Context
import android.content.res.Configuration
import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.toArgb
import com.bcw.classeviva.data.Preferences
import com.bcw.classeviva.data.timetable.EntryTint
import com.bcw.classeviva.data.timetable.TimetableStore
import com.bcw.classeviva.data.timetable.entryOrder
import com.bcw.classeviva.data.timetable.lightened
import com.bcw.classeviva.data.AppearanceMode
import com.bcw.classeviva.ui.theme.SubjectColors
import com.bcw.classeviva.ui.theme.ThemeSettings
import com.bcw.classeviva.ui.theme.bcwColors
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
 * Il telefono invia le lezioni (senza note e allegati), le impostazioni di aspetto, la palette
 * delle materie e (dalla versione 3) i ruoli di colore già risolti del suo tema, chiaro e scuro,
 * compresi colori del sistema e contrasto, più il tema in uso (`dark`): l'orologio ha gli stessi
 * colori del telefono e passa al chiaro o allo scuro con lui. Le tinte scelte a mano arrivano già in entrambi i temi (chiaro e scuro, con lo
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
                dataMap.putString("json", payload(context, store, preferences))
                dataMap.putLong("updatedAt", System.currentTimeMillis())
            }.asPutDataRequest().setUrgent()
            // Senza un orologio collegato la richiesta resta in coda e parte alla connessione.
            Wearable.getDataClient(context).putDataItem(request)
        }
    }

    fun payload(context: Context, store: TimetableStore, preferences: Preferences): String {
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
            put("version", 3)
            store.owner?.let { put("owner", it) }
            put("accent", preferences.string("accentColor"))
            put("colorStyle", preferences.string("colorStyle"))
            put("contrast", preferences.string("contrast"))
            put("appearance", preferences.string("appearance"))
            put("updatedAt", System.currentTimeMillis())
            put("palette", paletteJson())
            put("dark", isDark(context, preferences))
            put("scheme", schemeJson(context, preferences))
            put("entries", JsonArray(entries))
        }.toString()
    }

    /** Il tema che il telefono usa adesso: quello scelto o, con "Automatico", quello del sistema. */
    fun isDark(context: Context, preferences: Preferences): Boolean = when (preferences.appearance) {
        AppearanceMode.light -> false
        AppearanceMode.dark -> true
        AppearanceMode.system -> (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    }

    /** I ruoli di Material 3 del telefono, chiaro e scuro (accento, stile, contrasto o colori del sistema). */
    private fun schemeJson(context: Context, preferences: Preferences): JsonObject {
        val settings = ThemeSettings.from(preferences)
        return buildJsonObject {
            put("light", rolesJson(bcwColors(context, settings, dark = false).scheme))
            put("dark", rolesJson(bcwColors(context, settings, dark = true).scheme))
        }
    }

    private fun rolesJson(s: ColorScheme): JsonObject = buildJsonObject {
        val roles = listOf(
            "primary" to s.primary, "on-primary" to s.onPrimary, "primary-container" to s.primaryContainer, "on-primary-container" to s.onPrimaryContainer,
            "secondary" to s.secondary, "on-secondary" to s.onSecondary, "secondary-container" to s.secondaryContainer, "on-secondary-container" to s.onSecondaryContainer,
            "tertiary" to s.tertiary, "on-tertiary" to s.onTertiary, "tertiary-container" to s.tertiaryContainer, "on-tertiary-container" to s.onTertiaryContainer,
            "error" to s.error, "on-error" to s.onError, "error-container" to s.errorContainer, "on-error-container" to s.onErrorContainer,
            "surface" to s.surface, "surface-container-low" to s.surfaceContainerLow, "surface-container" to s.surfaceContainer,
            "surface-container-high" to s.surfaceContainerHigh, "surface-container-highest" to s.surfaceContainerHighest,
            "on-surface" to s.onSurface, "on-surface-variant" to s.onSurfaceVariant, "outline" to s.outline, "outline-variant" to s.outlineVariant,
        )
        for ((name, color) in roles) put(name, color.toArgb() and 0xFFFFFF)
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
