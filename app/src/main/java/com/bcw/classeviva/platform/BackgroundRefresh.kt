package com.bcw.classeviva.platform

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.bcw.classeviva.BCWApplication
import com.bcw.classeviva.data.AppModel
import com.bcw.classeviva.data.NewsKind
import com.bcw.classeviva.data.Phase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * Aggiornamento in background (come `BGAppRefreshTask` dell'app iOS): circa ogni ora, con la rete,
 * scarica i dati per le notifiche delle novità, riallinea promemoria, widget e lezione in corso.
 */
object BackgroundRefresh {
    private const val NAME = "bcw.refresh"

    fun schedule(context: Context, model: AppModel) {
        val wanted = model.phase == Phase.signedIn && !model.isDemo && NewsKind.entries.any { model.preferences.newsEnabled(it) }
        val work = WorkManager.getInstance(context)
        if (!wanted) {
            work.cancelUniqueWork(NAME)
            return
        }
        val request = PeriodicWorkRequestBuilder<RefreshWorker>(1, TimeUnit.HOURS, 15, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        work.enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }
}

class RefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as BCWApplication
        withContext(Dispatchers.Main) {
            val model = app.model
            if (model.phase == Phase.signedIn && !model.isDemo) model.refreshAll()
        }
        LiveUpdates.refresh(applicationContext)
        return Result.success()
    }
}
