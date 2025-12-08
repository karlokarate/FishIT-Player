package com.chris.m3usuite.work

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import kotlinx.coroutines.flow.first

/**
 * Central gateway for app background scheduling.
 * Ensures unique names, consistent policies, and simple entry points.
 */
object SchedulingGateway {
    const val NAME_XTREAM_REFRESH = "xtream_refresh"
    const val NAME_XTREAM_ENRICH = "xtream_enrich"
    const val NAME_EPG_REFRESH = "epg_refresh"
    const val NAME_SCREEN_RESET = "screen_time_daily_reset_once"
    const val NAME_XTREAM_DELTA = "xtream_delta_import"
    const val NAME_TG_SYNC = "telegram_sync"

    fun scheduleAll(ctx: Context) {
        // Intentionally do NOT schedule Xtream delta periodic.
        // Delta/import is only run on explicit user action.
        cancelXtreamWork(ctx)
        // EPG periodic refresh removed; lazy on-demand prefetch handles freshness
        scheduleScreenTimeReset(ctx)
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
        // Screen time daily reset (safe to re-schedule)
        runCatching { wm.cancelUniqueWork(NAME_SCREEN_RESET) }
        // OBX key backfill (safe to re-run later)
        runCatching { wm.cancelUniqueWork("obx_key_backfill_once") }
    }

    fun resumeSafeBackgroundWork(ctx: Context) {
        // Resume daily reset and OBX backfill as one-shots/periodic
        runCatching { ScreenTimeResetWorker.schedule(ctx) }
        runCatching { ObxKeyBackfillWorker.scheduleOnce(ctx) }
        // Xtream seeding is coordinated via XtreamSeeder; no periodic work scheduled here
    }

    fun enqueueOneTimeUnique(
        ctx: Context,
        uniqueName: String,
        req: OneTimeWorkRequest,
        policy: ExistingWorkPolicy = ExistingWorkPolicy.REPLACE,
    ) {
        WorkManager.getInstance(ctx).enqueueUniqueWork(uniqueName, policy, req)
    }

    /**
     * Schedule a Telegram sync with the specified mode.
     *
     * @param ctx Android context
     * @param mode Sync mode: "all", "selection_changed", or "backfill_series"
     * @param refreshHome Whether to refresh home screen after sync
     */
    fun scheduleTelegramSync(
        ctx: Context,
        mode: String = "all",
        refreshHome: Boolean = false,
    ) {
        com.chris.m3usuite.telegram.work.TelegramSyncWorker.scheduleNow(
            context = ctx,
            mode = mode,
            refreshHome = refreshHome,
        )
    }

    suspend fun refreshFavoritesEpgNow(
        ctx: Context,
        aggressive: Boolean = false,
    ): Boolean {
        val settings =
            com.chris.m3usuite.prefs
                .SettingsStore(ctx)
        if (!settings.m3uWorkersEnabled.first()) return false
        // Direct OBX prefetch for favorite Live streamIds (no Worker)
        // 1) read favorites (Room ids) → 2) map to streamIds → 3) OBX prefetch
        return try {
            val favCsv = settings.favoriteLiveIdsCsv.first()
            val ids = favCsv.split(',').mapNotNull { it.toLongOrNull() }
            if (ids.isEmpty()) return false
            val streamIds =
                ids
                    .mapNotNull { id ->
                        if (id >= 1_000_000_000_000L && id < 2_000_000_000_000L) {
                            (id - 1_000_000_000_000L).toInt()
                        } else {
                            null
                        }
                    }.distinct()
            if (streamIds.isEmpty()) return false
            val xtObx =
                com.chris.m3usuite.data.repo
                    .XtreamObxRepository(ctx, settings)
            xtObx.prefetchEpgForVisible(streamIds)
            true
        } catch (_: Throwable) {
            false
        }
    }
}
