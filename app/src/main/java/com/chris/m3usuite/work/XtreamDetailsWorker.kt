package com.chris.m3usuite.work

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.chris.m3usuite.core.logging.AppLog
import com.chris.m3usuite.data.obx.ObxStore
import com.chris.m3usuite.data.repo.XtreamObxRepository
import com.chris.m3usuite.playback.PlaybackPriority
import com.chris.m3usuite.prefs.SettingsStore
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

/**
 * Runs the heavier detail import after heads-only indexing was completed.
 *
 * ════════════════════════════════════════════════════════════════════════════════
 * PHASE 8 – Task 3: Playback-Aware Worker Scheduling
 * ════════════════════════════════════════════════════════════════════════════════
 *
 * This worker is playback-aware: when [PlaybackPriority.isPlaybackActive] is true,
 * heavy operations are throttled to avoid impacting playback quality.
 */
class XtreamDetailsWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val ctx = applicationContext
        val store = SettingsStore(ctx)
        if (!store.hasXtream()) return Result.success()
        // Global gate: if disabled, do not perform any API calls
        if (!store.m3uWorkersEnabled.first()) return Result.success()
        return try {
            // Phase 8: Playback-aware throttling before heavy operations
            throttleIfPlaybackActive()

            val repo = XtreamObxRepository(ctx, store)
            val vodLimit = inputData.getInt("vodLimit", 40)
            val seriesLimit = inputData.getInt("seriesLimit", 20)
            val detailResult = repo.refreshDetailsChunk(vodLimit = vodLimit, seriesLimit = seriesLimit)
            val (vodUpdated, seriesUpdated) = detailResult.getOrElse { return Result.retry() }
            runCatching { ObxStore.get(ctx).closeThreadResources() }
            if (vodUpdated > 0 || seriesUpdated > 0) {
                // Phase 8: Throttle before EPG prefetch
                throttleIfPlaybackActive()

                kotlin.runCatching {
                    val aggressive = store.epgFavSkipXmltvIfXtreamOk.first()
                    SchedulingGateway.refreshFavoritesEpgNow(ctx, aggressive = aggressive)
                }
            }
            Result.success()
        } catch (e: Throwable) {
            // Phase 8 Task 6b: Log worker error via AppLog
            logWorkerError(e)
            Result.retry()
        }
    }

    /**
     * Phase 8 Task 6b: Log worker error to AppLog with category "WORKER_ERROR".
     */
    private fun logWorkerError(e: Throwable) {
        AppLog.log(
            category = "WORKER_ERROR",
            level = AppLog.Level.ERROR,
            message = "Worker XtreamDetailsWorker failed: ${e.message}",
            extras = mapOf(
                "worker" to "XtreamDetailsWorker",
                "exception" to e.javaClass.simpleName,
                "cause" to (e.cause?.javaClass?.simpleName ?: "none"),
            ),
            bypassMaster = true,
        )
    }

    /**
     * Phase 8: Delays execution when playback is active to avoid stuttering.
     * Uses [PlaybackPriority.PLAYBACK_THROTTLE_MS] delay when playback is active.
     */
    private suspend fun throttleIfPlaybackActive() {
        if (PlaybackPriority.isPlaybackActive.value) {
            delay(PlaybackPriority.PLAYBACK_THROTTLE_MS)
        }
    }

    companion object {
        private const val UNIQUE = "xtream_details_once"

        fun triggerOnce(
            context: Context,
            vodLimit: Int = 40,
            seriesLimit: Int = 20,
            initialDelayMinutes: Long = 10,
        ) {
            val constraints =
                Constraints
                    .Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .setRequiresBatteryNotLow(true)
                    .build()
            val data =
                Data
                    .Builder()
                    .putInt("vodLimit", vodLimit)
                    .putInt("seriesLimit", seriesLimit)
                    .build()
            val delay = initialDelayMinutes.coerceAtLeast(0)
            val req =
                OneTimeWorkRequestBuilder<XtreamDetailsWorker>()
                    .setConstraints(constraints)
                    .setInputData(data)
                    .setInitialDelay(delay, TimeUnit.MINUTES)
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                    .build()
            WorkManager
                .getInstance(context)
                .enqueueUniqueWork(UNIQUE, ExistingWorkPolicy.REPLACE, req)
        }
    }
}
