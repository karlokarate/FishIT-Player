package com.chris.m3usuite.work

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import androidx.work.ExistingPeriodicWorkPolicy

/**
 * Central gateway for app background scheduling.
 * Ensures unique names, consistent policies, and simple entry points.
 */
object SchedulingGateway {
    const val NAME_XTREAM_REFRESH = "xtream_refresh"
    const val NAME_XTREAM_ENRICH  = "xtream_enrich"
    const val NAME_EPG_REFRESH    = "epg_refresh"
    const val NAME_SCREEN_RESET   = "screen_time_daily_reset_once"
    const val NAME_TG_SYNC_VOD    = "tg_sync_vod"
    const val NAME_TG_SYNC_SERIES = "tg_sync_series"
    const val NAME_XTREAM_DELTA   = "xtream_delta_import"

    fun scheduleAll(ctx: Context) {
        // Xtream delta import: periodic + on-demand
        scheduleXtreamDeltaPeriodic(ctx)
        // EPG periodic refresh removed; lazy on-demand prefetch handles freshness
        scheduleScreenTimeReset(ctx)
        TelegramCacheCleanupWorker.schedule(ctx)
        ObxKeyBackfillWorker.scheduleOnce(ctx)
    }

    fun scheduleXtreamPeriodic(ctx: Context) {
        // No-op: Xtream periodic scheduling disabled
    }

    fun scheduleXtreamEnrichment(ctx: Context) {
        // No-op: Xtream enrichment scheduling disabled
    }

    fun scheduleXtreamDeltaPeriodic(ctx: Context) {
        XtreamDeltaImportWorker.schedulePeriodic(ctx)
    }

    fun scheduleEpgPeriodic(ctx: Context) {
        // No-op (EPGRefreshWorker removed). Kept for binary/source compatibility.
    }

    fun scheduleScreenTimeReset(ctx: Context) {
        ScreenTimeResetWorker.schedule(ctx)
    }

    fun triggerXtreamRefreshNow(ctx: Context) {
        XtreamDeltaImportWorker.triggerOnce(ctx)
    }

    fun triggerXtreamRefreshNowKeep(ctx: Context) {
        XtreamDeltaImportWorker.triggerOnce(ctx)
    }

    fun enqueueOneTimeUnique(ctx: Context, uniqueName: String, req: OneTimeWorkRequest, policy: ExistingWorkPolicy = ExistingWorkPolicy.REPLACE) {
        WorkManager.getInstance(ctx).enqueueUniqueWork(uniqueName, policy, req)
    }

    fun scheduleTelegramSync(ctx: Context, mode: String) {
        TelegramSyncWorker.enqueue(ctx, mode)
    }

    suspend fun refreshFavoritesEpgNow(ctx: Context, aggressive: Boolean = false): Boolean {
        // Direct OBX prefetch for favorite Live streamIds (no Worker)
        // 1) read favorites (Room ids) → 2) map to streamIds → 3) OBX prefetch
        return try {
            val settings = com.chris.m3usuite.prefs.SettingsStore(ctx)
            val favCsv = settings.favoriteLiveIdsCsv.first()
            val ids = favCsv.split(',').mapNotNull { it.toLongOrNull() }
            if (ids.isEmpty()) return false
            val streamIds = ids.mapNotNull { id -> if (id >= 1_000_000_000_000L && id < 2_000_000_000_000L) (id - 1_000_000_000_000L).toInt() else null }.distinct()
            if (streamIds.isEmpty()) return false
            val xtObx = com.chris.m3usuite.data.repo.XtreamObxRepository(ctx, settings)
            xtObx.prefetchEpgForVisible(streamIds)
            true
        } catch (_: Throwable) { false }
    }
}
