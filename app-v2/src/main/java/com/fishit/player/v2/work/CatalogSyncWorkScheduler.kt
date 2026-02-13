package com.fishit.player.v2.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.fishit.player.core.catalogsync.CatalogSyncWorkScheduler
import com.fishit.player.infra.logging.UnifiedLog
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

internal const val WORK_NAME = "catalog_sync_global"
private const val CATALOG_SYNC_TAG = "catalog_sync"
private const val WORKER_TAG = "worker/CatalogSyncOrchestratorWorker"
private const val TAG = "CatalogSyncScheduler"

private const val KEY_SYNC_RUN_ID = "sync_run_id"
private const val KEY_SYNC_MODE = "sync_mode"
private const val KEY_ACTIVE_SOURCES = "active_sources"
private const val KEY_WIFI_ONLY = "wifi_only"
private const val KEY_MAX_RUNTIME_MS = "max_runtime_ms"
private const val KEY_DEVICE_CLASS = "device_class"

/**
 * SSOT implementation of [CatalogSyncWorkScheduler] using WorkManager.
 *
 * Contract: CATALOG_SYNC_WORKERS_CONTRACT_V2
 * - uniqueWorkName = "catalog_sync_global"
 * - All sync triggers MUST go through this scheduler
 * - No UI/ViewModel may call sync services directly
 * 
 * ## Sync Strategy (Industry Best Practice)
 * 
 * This implementation follows patterns from TiviMate, Kodi, and XCIPTV:
 * 
 * **Initial Sync (AUTO/EXPERT_NOW/FORCE_RESCAN):**
 * - Full catalog scan, runs once at first launch or on manual trigger
 * - High traffic, but only happens rarely
 * 
 * **Periodic Incremental Sync:**
 * - Runs every 2 hours in background
 * - Quick count comparison first (like XCIPTV)
 * - Only fetches items where `added > lastSyncTimestamp` (like TiviMate)
 * - ~95% traffic reduction vs full scan
 * - Battery-efficient: only when connected and not low battery
 */
@Singleton
class CatalogSyncWorkSchedulerImpl
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : CatalogSyncWorkScheduler {
        // ========== Interface Implementation ==========

        override fun enqueueAutoSync() {
            UnifiedLog.d(TAG) { "Enqueueing AUTO sync" }
            schedule(
                CatalogSyncWorkRequest(
                    syncRunId = UUID.randomUUID().toString(),
                    mode = CatalogSyncWorkMode.AUTO,
                ),
            )
        }

        override fun enqueueExpertSyncNow() {
            UnifiedLog.d(TAG) { "Enqueueing EXPERT_NOW sync" }
            schedule(
                CatalogSyncWorkRequest(
                    syncRunId = UUID.randomUUID().toString(),
                    mode = CatalogSyncWorkMode.EXPERT_NOW,
                ),
            )
        }

        override fun enqueueForceRescan() {
            UnifiedLog.d(TAG) { "Enqueueing FORCE_RESCAN sync" }
            schedule(
                CatalogSyncWorkRequest(
                    syncRunId = UUID.randomUUID().toString(),
                    mode = CatalogSyncWorkMode.FORCE_RESCAN,
                ),
            )
        }
        
        override fun enqueueIncrementalSync() {
            UnifiedLog.d(TAG) { "Enqueueing INCREMENTAL sync" }
            schedule(
                CatalogSyncWorkRequest(
                    syncRunId = UUID.randomUUID().toString(),
                    mode = CatalogSyncWorkMode.INCREMENTAL,
                ),
            )
        }
        
        override fun schedulePeriodicSync(intervalHours: Long) {
            val effectiveInterval = intervalHours.coerceAtLeast(
                WorkerConstants.PERIODIC_SYNC_MIN_INTERVAL_HOURS
            )
            
            UnifiedLog.d(TAG) { "Scheduling periodic incremental sync every $effectiveInterval hours" }
            
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true)
                .build()
            
            val inputData = Data.Builder()
                .putString(KEY_SYNC_RUN_ID, "periodic_${System.currentTimeMillis()}")
                .putString(KEY_SYNC_MODE, CatalogSyncWorkMode.INCREMENTAL.storageValue)
                .build()
            
            val periodicRequest = PeriodicWorkRequestBuilder<CatalogSyncOrchestratorWorker>(
                effectiveInterval,
                TimeUnit.HOURS
            )
                .setConstraints(constraints)
                .setInputData(inputData)
                .addTag(CATALOG_SYNC_TAG)
                .addTag(CatalogSyncWorkMode.INCREMENTAL.tagValue)
                .addTag(WORKER_TAG)
                .build()
            
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WorkerConstants.WORK_NAME_PERIODIC_SYNC,
                ExistingPeriodicWorkPolicy.UPDATE,
                periodicRequest
            )
            
            UnifiedLog.i(TAG) { "Periodic incremental sync scheduled: every $effectiveInterval hours" }
        }
        
        override fun cancelPeriodicSync() {
            UnifiedLog.d(TAG) { "Cancelling periodic sync" }
            WorkManager.getInstance(context).cancelUniqueWork(
                WorkerConstants.WORK_NAME_PERIODIC_SYNC
            )
        }

        override fun cancelSync() {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }

        // ========== Internal Implementation ==========

        private fun schedule(request: CatalogSyncWorkRequest) {
            WorkManager
                .getInstance(context)
                .enqueueUniqueWork(
                    WORK_NAME,
                    request.mode.workPolicy,
                    buildRequest(request),
                )
        }

        private fun buildRequest(request: CatalogSyncWorkRequest): OneTimeWorkRequest {
            val builder =
                Data
                    .Builder()
                    .putString(KEY_SYNC_RUN_ID, request.syncRunId)
                    .putString(KEY_SYNC_MODE, request.mode.storageValue)
                    .putStringArray(KEY_ACTIVE_SOURCES, request.activeSources.toTypedArray())
                    .putBoolean(KEY_WIFI_ONLY, request.wifiOnly)
                    .putString(KEY_DEVICE_CLASS, request.deviceClass.orEmpty())

            // Only set max_runtime_ms if explicitly provided and > 0
            // Otherwise, let the worker apply its DEFAULT_MAX_RUNTIME_MS
            // This fixes the bug where null maxRuntimeMs was written as 0ms
            request.maxRuntimeMs?.takeIf { it > 0 }?.let {
                builder.putLong(KEY_MAX_RUNTIME_MS, it)
            }

            val inputData = builder.build()

            return OneTimeWorkRequestBuilder<CatalogSyncOrchestratorWorker>()
                .setInputData(inputData)
                .addTag(CATALOG_SYNC_TAG)
                .addTag(request.mode.tagValue)
                .addTag(WORKER_TAG)
                .build()
        }
    }

data class CatalogSyncWorkRequest(
    val syncRunId: String,
    val mode: CatalogSyncWorkMode,
    val activeSources: List<String> = emptyList(),
    val wifiOnly: Boolean = false,
    val maxRuntimeMs: Long? = null,
    val deviceClass: String? = null,
)

/**
 * Sync modes for catalog synchronization.
 * 
 * ## Traffic Comparison (Xtream Provider with ~10k items):
 * 
 * | Mode | Traffic | Use Case |
 * |------|---------|----------|
 * | AUTO | ~2-5 MB | First launch, app update |
 * | EXPERT_NOW | ~2-5 MB | User-triggered "Refresh" |
 * | FORCE_RESCAN | ~2-5 MB | User-triggered "Full Rescan" |
 * | INCREMENTAL | ~10-50 KB | Background periodic (every 2h) |
 * 
 * The INCREMENTAL mode achieves ~95% traffic reduction by:
 * 1. Quick count comparison first (1 API call)
 * 2. Only fetching items where `added > lastSyncTimestamp`
 */
enum class CatalogSyncWorkMode(
    val storageValue: String,
    val tagValue: String,
    val workPolicy: ExistingWorkPolicy,
) {
    AUTO(
        storageValue = "auto",
        tagValue = "mode_auto",
        workPolicy = ExistingWorkPolicy.KEEP,
    ),
    /**
     * User-triggered "Sync Now" from Settings.
     * 
     * BUG FIX (Feb 2026): Changed from KEEP to REPLACE.
     * 
     * KEEP was wrong because:
     * - If an AUTO sync started at app launch, user's explicit "Sync Now" was silently ignored
     * - User expected their action to trigger a new sync, not be discarded
     * - This caused "sync doesn't work" complaints when AUTO sync was still running
     * 
     * REPLACE ensures user-triggered syncs always execute immediately.
     */
    EXPERT_NOW(
        storageValue = "expert_now",
        tagValue = "mode_expert_now",
        workPolicy = ExistingWorkPolicy.REPLACE,
    ),
    FORCE_RESCAN(
        storageValue = "force_rescan",
        tagValue = "mode_force_rescan",
        workPolicy = ExistingWorkPolicy.REPLACE,
    ),
    /**
     * Incremental sync for periodic background updates.
     * 
     * This mode is optimized for minimal traffic:
     * - Compares item counts to detect changes
     * - Only fetches items where `added > lastSyncTimestamp`
     * - Skips unchanged sources entirely
     * 
     * Used by [CatalogSyncWorkSchedulerImpl.schedulePeriodicSync].
     */
    INCREMENTAL(
        storageValue = "incremental",
        tagValue = "mode_incremental",
        workPolicy = ExistingWorkPolicy.KEEP,
    ),
}
