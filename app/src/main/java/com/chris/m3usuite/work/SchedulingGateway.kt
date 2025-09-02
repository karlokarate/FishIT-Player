package com.chris.m3usuite.work

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager

/**
 * Central gateway for app background scheduling.
 * Ensures unique names, consistent policies, and simple entry points.
 */
object SchedulingGateway {
    const val NAME_XTREAM_REFRESH = "xtream_refresh"
    const val NAME_XTREAM_ENRICH  = "xtream_enrich"
    const val NAME_EPG_REFRESH    = "epg_refresh"
    const val NAME_SCREEN_RESET   = "screen_time_daily_reset_once"

    fun scheduleAll(ctx: Context) {
        scheduleXtreamPeriodic(ctx)
        scheduleXtreamEnrichment(ctx)
        scheduleEpgPeriodic(ctx)
        scheduleScreenTimeReset(ctx)
    }

    fun scheduleXtreamPeriodic(ctx: Context) {
        // Delegate to worker which already enqueues with unique periodic name and UPDATE policy
        XtreamRefreshWorker.schedule(ctx)
    }

    fun scheduleXtreamEnrichment(ctx: Context) {
        XtreamEnrichmentWorker.schedule(ctx)
    }

    fun scheduleEpgPeriodic(ctx: Context) {
        EpgRefreshWorker.schedule(ctx)
    }

    fun scheduleScreenTimeReset(ctx: Context) {
        ScreenTimeResetWorker.schedule(ctx)
    }

    fun enqueueOneTimeUnique(ctx: Context, uniqueName: String, req: OneTimeWorkRequest, policy: ExistingWorkPolicy = ExistingWorkPolicy.REPLACE) {
        WorkManager.getInstance(ctx).enqueueUniqueWork(uniqueName, policy, req)
    }

    suspend fun refreshFavoritesEpgNow(ctx: Context, aggressive: Boolean = false): Boolean =
        EpgRefreshWorker.refreshFavoritesNow(ctx, aggressive = aggressive)
}

