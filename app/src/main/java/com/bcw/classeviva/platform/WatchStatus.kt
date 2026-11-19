package com.bcw.classeviva.platform

import android.content.Context
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.tasks.Task
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * Lo smartwatch Wear OS abbinato, per Impostazioni › Smartwatch: il nome del modello, se è
 * collegato adesso e se ha l'app BCW (la capability `bcw_wear` dichiarata dall'app Wear OS).
 * L'ultimo orologio visto resta salvato: scollegato, la pagina ne mostra comunque il nome.
 */
data class WatchStatus(
    /** Nome del modello (es. "Galaxy Watch7"), `null` se non si è mai visto un orologio. */
    val name: String?,
    /** Il codice tra parentesi del nome di sistema (es. "4ZHF"), se c'è. */
    val code: String?,
    val connected: Boolean,
    val appInstalled: Boolean,
    /** Servizi Google Play assenti: la sincronizzazione con l'orologio non è possibile. */
    val unavailable: Boolean = false,
)

object WatchStatusReader {
    const val CAPABILITY = "bcw_wear"
    private const val PREFS = "bcw.watch"

    suspend fun read(context: Context): WatchStatus {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val saved = prefs.getString("name", null)
        fun fromSaved(unavailable: Boolean = false) =
            WatchStatus(saved?.let(::modelName), saved?.let(::modelCode), connected = false, appInstalled = false, unavailable = unavailable)

        if (GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context) != ConnectionResult.SUCCESS) return fromSaved(unavailable = true)
        val nodes = withTimeoutOrNull(4_000) { Wearable.getNodeClient(context).connectedNodes.awaitOrNull() }.orEmpty()
        val node = nodes.firstOrNull { it.isNearby } ?: nodes.firstOrNull() ?: return fromSaved()
        prefs.edit().putString("name", node.displayName).apply()
        val withApp = withTimeoutOrNull(4_000) {
            Wearable.getCapabilityClient(context).getCapability(CAPABILITY, CapabilityClient.FILTER_REACHABLE).awaitOrNull()
        }?.nodes.orEmpty()
        return WatchStatus(modelName(node.displayName), modelCode(node.displayName), connected = true, appInstalled = withApp.any { it.id == node.id })
    }

    /** "Galaxy Watch7 (4ZHF)" → "Galaxy Watch7". */
    fun modelName(displayName: String): String = displayName.substringBefore(" (").trim().ifEmpty { displayName }

    fun modelCode(displayName: String): String? =
        displayName.substringAfter(" (", "").substringBefore(")").trim().takeIf { it.isNotEmpty() }

    private suspend fun <T> Task<T>.awaitOrNull(): T? = suspendCancellableCoroutine { cont ->
        addOnCompleteListener { task -> if (cont.isActive) cont.resume(if (task.isSuccessful) task.result else null) }
    }
}
