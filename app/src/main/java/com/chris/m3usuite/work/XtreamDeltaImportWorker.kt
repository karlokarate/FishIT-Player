package com.chris.m3usuite.work

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.chris.m3usuite.core.xtream.XtreamImportCoordinator
import com.chris.m3usuite.core.xtream.XtreamSeeder
import com.chris.m3usuite.data.obx.ObxStore
import com.chris.m3usuite.data.repo.XtreamObxRepository
import com.chris.m3usuite.prefs.SettingsStore
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

class XtreamDeltaImportWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val ctx = applicationContext
        val store = SettingsStore(ctx)
        val hasXt = store.hasXtream()
        if (!hasXt) return Result.success()
        // Global gate: if disabled, do not perform any API calls
        if (!store.m3uWorkersEnabled.first()) return Result.success()
        XtreamImportCoordinator.waitUntilIdle()
        return try {
            val repo = XtreamObxRepository(ctx, store)
            // Default to false for on-demand runs to avoid flooding Live calls
            val includeLive: Boolean = inputData.getBoolean("include_live", false)
            val vodLimit: Int = inputData.getInt("vod_limit", 0)
            val seriesLimit: Int = inputData.getInt("series_limit", 0)
            // Ensure heads are present once (no-op if already there)
            val seedResult =
                XtreamSeeder.ensureSeeded(
                    context = ctx,
                    store = store,
                    reason = "worker",
                    force = false,
                    forceDiscovery = false,
                )
            // Delta across full lists to update provider/genre keys and indexes even without details
            val delta = repo.importDelta(deleteOrphans = false, includeLive = includeLive)
            // Then refresh a chunk of details to enrich posters/plots etc.
            val detail = repo.refreshDetailsChunk(vodLimit = vodLimit, seriesLimit = seriesLimit)
            detail.getOrNull()?.let { (vodUpd, seriesUpd) ->
                if (vodUpd > 0 || seriesUpd > 0) {
                    kotlin.runCatching {
                        store.setLastDeltaCounts(0, vodUpd, seriesUpd)
                        store.setLastImportAtMs(System.currentTimeMillis())
                    }
                }
            }
            // clean thread-locals after heavy OBX activity inside the repo
            runCatching { ObxStore.get(ctx).closeThreadResources() }
            val seedOk = seedResult?.isSuccess != false
            val deltaOk = delta.isSuccess
            val detailOk = detail.isSuccess
            if (seedOk && deltaOk && detailOk) {
                // After successful updates, proactively prefetch EPG for favorites (keeps home row snappy)
                kotlin.runCatching {
                    val aggressive = store.epgFavSkipXmltvIfXtreamOk.first()
                    SchedulingGateway.refreshFavoritesEpgNow(ctx, aggressive = aggressive)
                }
                Result.success()
            } else {
                Result.retry()
            }
        } catch (_: Throwable) {
            Result.retry()
        }
    }

    companion object {
        private const val UNIQUE = "xtream_delta_import"

        fun schedulePeriodic(context: Context) {
            val constraints =
                Constraints
                    .Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .setRequiresBatteryNotLow(true)
                    .build()
            val req =
                PeriodicWorkRequestBuilder<XtreamDeltaImportWorker>(6, TimeUnit.HOURS)
                    .setConstraints(constraints)
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                    .build()
            WorkManager
                .getInstance(context)
                .enqueueUniquePeriodicWork(UNIQUE, ExistingPeriodicWorkPolicy.UPDATE, req)
        }

        fun triggerOnce(
            context: Context,
            includeLive: Boolean = false,
            vodLimit: Int = 0,
            seriesLimit: Int = 0,
        ) {
            XtreamImportCoordinator.enqueueWork {
                val constraints =
                    Constraints
                        .Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                val req =
                    OneTimeWorkRequestBuilder<XtreamDeltaImportWorker>()
                        .setConstraints(constraints)
                        .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
                        .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                        .setInputData(
                            workDataOf(
                                "include_live" to includeLive,
                                "vod_limit" to vodLimit,
                                "series_limit" to seriesLimit,
                            ),
                        ).build()
                WorkManager
                    .getInstance(context)
                    .enqueueUniqueWork("${UNIQUE}_once", ExistingWorkPolicy.REPLACE, req)
            }
        }

        fun triggerOnceDelayedLive(
            context: Context,
            delayMinutes: Long = 5,
        ) {
            XtreamImportCoordinator.enqueueWork {
                val constraints =
                    Constraints
                        .Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                val req =
                    OneTimeWorkRequestBuilder<XtreamDeltaImportWorker>()
                        .setConstraints(constraints)
                        .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
                        // Delayed jobs cannot be expedited; schedule as regular one-shot with delay
                        .setInitialDelay(delayMinutes, TimeUnit.MINUTES)
                        .setInputData(
                            workDataOf(
                                "include_live" to true,
                                "vod_limit" to 0,
                                "series_limit" to 0,
                            ),
                        ).build()
                WorkManager
                    .getInstance(context)
                    .enqueueUniqueWork("${UNIQUE}_once_live_delay", ExistingWorkPolicy.REPLACE, req)
            }
        }
    }
}
