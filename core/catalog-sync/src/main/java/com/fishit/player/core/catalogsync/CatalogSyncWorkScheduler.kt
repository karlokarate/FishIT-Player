package com.fishit.player.core.catalogsync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.fishit.player.infra.logging.UnifiedLog
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single Source of Truth (SSOT) scheduler for catalog synchronization.
 *
 * **Contract:** CATALOG_SYNC_WORKERS_CONTRACT_V2.md Section 2 (W-6)
 *
 * This is the ONLY entry point for scheduling catalog sync work.
 * All sync triggers (auto, expert, UI actions) MUST go through this scheduler.
 *
 * **Hard Rules:**
 * - Exactly one unique work name: `catalog_sync_global`
 * - No UI/ViewModel may call `CatalogSyncService.sync()` directly
 * - No other unique work names for catalog sync may exist
 *
 * **Scheduling Policies:**
 * - `enqueueAutoSync()`: Uses `ExistingWorkPolicy.KEEP` (don't interrupt existing sync)
 * - `enqueueExpertSyncNow()`: Uses `ExistingWorkPolicy.KEEP` (don't interrupt existing sync)
 * - `enqueueForceRescan()`: Uses `ExistingWorkPolicy.REPLACE` (restart sync immediately)
 * - `cancelSync()`: Cancels work by name `catalog_sync_global`
 */
@Singleton
class CatalogSyncWorkScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val workManager: WorkManager by lazy { WorkManager.getInstance(context) }

    companion object {
        /**
         * SSOT unique work name for catalog synchronization.
         * This is the ONLY work name allowed for catalog sync.
         */
        private const val CATALOG_SYNC_GLOBAL = "catalog_sync_global"
        private const val TAG = "CatalogSyncWorkScheduler"
    }

    /**
     * Enqueue automatic catalog synchronization.
     *
     * Used for background auto-sync (e.g., on app startup after auth).
     * Uses `ExistingWorkPolicy.KEEP` to avoid interrupting existing sync.
     *
     * **Contract:** W-6, W-10 (AUTO mode)
     */
    fun enqueueAutoSync() {
        UnifiedLog.i(TAG) { "Enqueueing AUTO sync with policy=KEEP" }

        val request = OneTimeWorkRequestBuilder<CatalogSyncOrchestratorWorker>()
            .addTag("catalog_sync")
            .addTag("mode_auto")
            .setInputData(
                androidx.work.workDataOf(
                    "sync_mode" to "AUTO",
                )
            )
            .build()

        workManager.enqueueUniqueWork(
            CATALOG_SYNC_GLOBAL,
            ExistingWorkPolicy.KEEP,
            request
        )
    }

    /**
     * Enqueue expert-triggered sync now.
     *
     * Used for user-initiated "Sync Now" action from expert/debug UI.
     * Uses `ExistingWorkPolicy.KEEP` to avoid interrupting existing sync.
     *
     * **Contract:** W-6, W-10 (EXPERT_SYNC_NOW mode)
     */
    fun enqueueExpertSyncNow() {
        UnifiedLog.i(TAG) { "Enqueueing EXPERT_SYNC_NOW with policy=KEEP" }

        val request = OneTimeWorkRequestBuilder<CatalogSyncOrchestratorWorker>()
            .addTag("catalog_sync")
            .addTag("mode_expert_sync_now")
            .setInputData(
                androidx.work.workDataOf(
                    "sync_mode" to "EXPERT_SYNC_NOW",
                )
            )
            .build()

        workManager.enqueueUniqueWork(
            CATALOG_SYNC_GLOBAL,
            ExistingWorkPolicy.KEEP,
            request
        )
    }

    /**
     * Enqueue force rescan (restart sync immediately).
     *
     * Used for user-initiated "Force Rescan" action from expert UI.
     * Uses `ExistingWorkPolicy.REPLACE` to cancel existing sync and start fresh.
     *
     * **Contract:** W-6, W-10 (EXPERT_FORCE_RESCAN mode)
     */
    fun enqueueForceRescan() {
        UnifiedLog.i(TAG) { "Enqueueing EXPERT_FORCE_RESCAN with policy=REPLACE (restart)" }

        val request = OneTimeWorkRequestBuilder<CatalogSyncOrchestratorWorker>()
            .addTag("catalog_sync")
            .addTag("mode_expert_force_rescan")
            .setInputData(
                androidx.work.workDataOf(
                    "sync_mode" to "EXPERT_FORCE_RESCAN",
                )
            )
            .build()

        workManager.enqueueUniqueWork(
            CATALOG_SYNC_GLOBAL,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    /**
     * Cancel any ongoing catalog synchronization.
     *
     * Cancels work by unique name `catalog_sync_global`.
     * This will stop any running sync workers.
     *
     * **Contract:** W-6
     */
    fun cancelSync() {
        UnifiedLog.i(TAG) { "Cancelling catalog sync work: $CATALOG_SYNC_GLOBAL" }
        workManager.cancelUniqueWork(CATALOG_SYNC_GLOBAL)
    }
}

/**
 * Placeholder for CatalogSyncOrchestratorWorker.
 *
 * This will be implemented in a future task per CATALOG_SYNC_WORKERS_CONTRACT_V2.md Section 7.
 * For now, this is a stub to allow compilation.
 *
 * **Contract:** W-9 (must be CoroutineWorker)
 * **Contract:** Section 7 - Builds deterministic chain based on active sources
 */
internal class CatalogSyncOrchestratorWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        // TODO: Implement orchestrator worker (future task)
        UnifiedLog.w("CatalogSyncOrchestratorWorker") {
            "Stub worker - not yet implemented"
        }
        return Result.success()
    }
}
