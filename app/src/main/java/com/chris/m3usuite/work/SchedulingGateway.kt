package com.chris.m3usuite.work

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import androidx.work.ExistingPeriodicWorkPolicy

/**
 * Central gateway for app background scheduling.
 * Ensures unique names, consistent policies, and simple entry points.
 */
object SchedulingGateway {
    data class TelegramSyncResult(
        val moviesAdded: Int,
        val seriesAdded: Int,
        val episodesAdded: Int
    )

    sealed class TelegramSyncState {
        object Idle : TelegramSyncState()
        data class Running(val mode: String, val processedChats: Int, val totalChats: Int) : TelegramSyncState()
        data class Success(val mode: String, val result: TelegramSyncResult) : TelegramSyncState()
        data class Failure(val mode: String, val error: String) : TelegramSyncState()
    }

    const val NAME_XTREAM_REFRESH = "xtream_refresh"
    const val NAME_XTREAM_ENRICH  = "xtream_enrich"
    const val NAME_EPG_REFRESH    = "epg_refresh"
    const val NAME_SCREEN_RESET   = "screen_time_daily_reset_once"
    const val NAME_TG_SYNC_VOD    = "tg_sync_vod"
    const val NAME_TG_SYNC_SERIES = "tg_sync_series"
    const val NAME_XTREAM_DELTA   = "xtream_delta_import"

    private val telegramSyncStateInternal = MutableStateFlow<TelegramSyncState>(TelegramSyncState.Idle)
    val telegramSyncState: StateFlow<TelegramSyncState> = telegramSyncStateInternal.asStateFlow()

    fun scheduleAll(ctx: Context) {
        // Intentionally do NOT schedule Xtream delta periodic.
        // Delta/import is only run on explicit user action.
        cancelXtreamWork(ctx)
        // EPG periodic refresh removed; lazy on-demand prefetch handles freshness
        scheduleScreenTimeReset(ctx)
        TelegramCacheCleanupWorker.schedule(ctx)
        ObxKeyBackfillWorker.scheduleOnce(ctx)
    }

    @Deprecated("Obsoleted by XtreamDeltaImportWorker periodic scheduling")
    fun scheduleXtreamPeriodic(ctx: Context) {
        // No-op: Xtream periodic scheduling disabled
    }

    @Deprecated("Handled on-demand in detail screens")
    fun scheduleXtreamEnrichment(ctx: Context) {
        // No-op: Xtream enrichment scheduling disabled
    }

    fun scheduleXtreamDeltaPeriodic(ctx: Context) { /* disabled by design */ }

    @Deprecated("EPGRefreshWorker removed; kept for binary compatibility")
    fun scheduleEpgPeriodic(ctx: Context) {
        // No-op
    }

    fun scheduleScreenTimeReset(ctx: Context) {
        ScreenTimeResetWorker.schedule(ctx)
    }

    fun triggerXtreamRefreshNow(ctx: Context) { /* disabled: run imports explicitly in UI */ }

    fun triggerXtreamRefreshNowKeep(ctx: Context) { /* disabled */ }

    fun cancelXtreamWork(ctx: Context) {
        val wm = WorkManager.getInstance(ctx)
        // Cancel known unique works for Xtream import
        runCatching { wm.cancelUniqueWork("xtream_delta_import") }
        runCatching { wm.cancelUniqueWork("xtream_delta_import_once") }
        runCatching { wm.cancelUniqueWork("xtream_details_once") }
        // Legacy names (no-op, but safe to cancel)
        runCatching { wm.cancelUniqueWork(NAME_XTREAM_REFRESH) }
        runCatching { wm.cancelUniqueWork(NAME_XTREAM_ENRICH) }
    }

    fun cancelSafeBackgroundWork(ctx: Context) {
        val wm = WorkManager.getInstance(ctx)
        // Xtream related
        cancelXtreamWork(ctx)
        // Telegram cleanup/sync (safe to cancel and re-schedule later)
        runCatching { wm.cancelUniqueWork("tg_cache_cleanup") }
        runCatching { wm.cancelUniqueWork("tg_cache_wipe_once") }
        runCatching { wm.cancelUniqueWork(NAME_TG_SYNC_VOD) }
        runCatching { wm.cancelUniqueWork(NAME_TG_SYNC_SERIES) }
        // Screen time daily reset (safe to re-schedule)
        runCatching { wm.cancelUniqueWork(NAME_SCREEN_RESET) }
        // OBX key backfill (safe to re-run later)
        runCatching { wm.cancelUniqueWork("obx_key_backfill_once") }
    }

    fun resumeSafeBackgroundWork(ctx: Context) {
        // Resume daily reset, Telegram cleanup, and OBX backfill as one-shots/periodic
        runCatching { ScreenTimeResetWorker.schedule(ctx) }
        runCatching { TelegramCacheCleanupWorker.schedule(ctx) }
        runCatching { ObxKeyBackfillWorker.scheduleOnce(ctx) }
        // Xtream seeding is coordinated via XtreamSeeder; no periodic work scheduled here
    }

    fun enqueueOneTimeUnique(
        ctx: Context,
        uniqueName: String,
        req: OneTimeWorkRequest,
        policy: ExistingWorkPolicy = ExistingWorkPolicy.REPLACE
    ) {
        WorkManager.getInstance(ctx).enqueueUniqueWork(uniqueName, policy, req)
    }

    fun scheduleTelegramSync(ctx: Context, mode: String, refreshHome: Boolean = false) {
        TelegramSyncWorker.enqueue(ctx, mode, refreshHome)
    }

    fun onTelegramSyncCompleted(ctx: Context, refreshHomeChrome: Boolean) {
        runCatching { TelegramCacheCleanupWorker.schedule(ctx) }
        runCatching { ObxKeyBackfillWorker.scheduleOnce(ctx) }
        if (refreshHomeChrome) {
            scheduleAll(ctx)
        }
    }

    fun notifyTelegramSyncStarted(mode: String, totalChats: Int) {
        telegramSyncStateInternal.value = TelegramSyncState.Running(mode, 0, totalChats)
    }

    fun notifyTelegramSyncProgress(mode: String, processedChats: Int, totalChats: Int) {
        val clamped = processedChats.coerceAtLeast(0)
        telegramSyncStateInternal.value = TelegramSyncState.Running(mode, clamped, totalChats)
    }

    fun notifyTelegramSyncCompleted(mode: String, result: TelegramSyncResult) {
        telegramSyncStateInternal.value = TelegramSyncState.Success(mode, result)
    }

    fun notifyTelegramSyncFailed(mode: String, error: String) {
        telegramSyncStateInternal.value = TelegramSyncState.Failure(mode, error)
    }

    fun notifyTelegramSyncIdle() {
        telegramSyncStateInternal.value = TelegramSyncState.Idle
    }

    fun acknowledgeTelegramSync() {
        val state = telegramSyncStateInternal.value
        if (state !is TelegramSyncState.Running) {
            telegramSyncStateInternal.value = TelegramSyncState.Idle
        }
    }

    suspend fun refreshFavoritesEpgNow(ctx: Context, aggressive: Boolean = false): Boolean {
        val settings = com.chris.m3usuite.prefs.SettingsStore(ctx)
        if (!settings.m3uWorkersEnabled.first()) return false
        // Direct OBX prefetch for favorite Live streamIds (no Worker)
        // 1) read favorites (Room ids) → 2) map to streamIds → 3) OBX prefetch
        return try {
            val favCsv = settings.favoriteLiveIdsCsv.first()
            val ids = favCsv.split(',').mapNotNull { it.toLongOrNull() }
            if (ids.isEmpty()) return false
            val streamIds = ids.mapNotNull { id ->
                if (id >= 1_000_000_000_000L && id < 2_000_000_000_000L)
                    (id - 1_000_000_000_000L).toInt()
                else null
            }.distinct()
            if (streamIds.isEmpty()) return false
            val xtObx = com.chris.m3usuite.data.repo.XtreamObxRepository(ctx, settings)
            xtObx.prefetchEpgForVisible(streamIds)
            true
        } catch (_: Throwable) { false }
    }
}
