package com.chris.m3usuite.work

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.Data
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.chris.m3usuite.data.obx.ObxStore
import com.chris.m3usuite.data.repo.XtreamObxRepository
import com.chris.m3usuite.prefs.SettingsStore
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

/**
 * Runs the heavier detail import after heads-only indexing was completed.
 */
class XtreamDetailsWorker(appContext: Context, params: WorkerParameters): CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val ctx = applicationContext
        val store = SettingsStore(ctx)
        if (!store.hasXtream()) return Result.success()
        // Global gate: if disabled, do not perform any API calls
        if (!store.m3uWorkersEnabled.first()) return Result.success()
        return try {
            val repo = XtreamObxRepository(ctx, store)
            val vodLimit = inputData.getInt("vodLimit", 40)
            val seriesLimit = inputData.getInt("seriesLimit", 20)
            val detailResult = repo.refreshDetailsChunk(vodLimit = vodLimit, seriesLimit = seriesLimit)
            val (vodUpdated, seriesUpdated) = detailResult.getOrElse { return Result.retry() }
            runCatching { ObxStore.get(ctx).closeThreadResources() }
            if (vodUpdated > 0 || seriesUpdated > 0) {
                kotlin.runCatching {
                    val aggressive = store.epgFavSkipXmltvIfXtreamOk.first()
                    SchedulingGateway.refreshFavoritesEpgNow(ctx, aggressive = aggressive)
                }
            }
            Result.success()
        } catch (_: Throwable) {
            Result.retry()
        }
    }

    companion object {
        private const val UNIQUE = "xtream_details_once"
        fun triggerOnce(
            context: Context,
            vodLimit: Int = 40,
            seriesLimit: Int = 20,
            initialDelayMinutes: Long = 10
        ) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true)
                .build()
            val data = Data.Builder()
                .putInt("vodLimit", vodLimit)
                .putInt("seriesLimit", seriesLimit)
                .build()
            val delay = initialDelayMinutes.coerceAtLeast(0)
            val req = OneTimeWorkRequestBuilder<XtreamDetailsWorker>()
                .setConstraints(constraints)
                .setInputData(data)
                .setInitialDelay(delay, TimeUnit.MINUTES)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork(UNIQUE, ExistingWorkPolicy.REPLACE, req)
        }
    }
}
