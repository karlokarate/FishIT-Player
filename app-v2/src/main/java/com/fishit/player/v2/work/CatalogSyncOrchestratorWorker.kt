package com.fishit.player.v2.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.fishit.player.core.sourceactivation.SourceActivationStore
import com.fishit.player.core.sourceactivation.SourceId
import com.fishit.player.infra.logging.UnifiedLog
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

/**
 * Catalog Sync Orchestrator Worker.
 *
 * Builds and enqueues PARALLEL worker chains per active source.
 *
 * Contract: CATALOG_SYNC_WORKERS_CONTRACT_V2
 * - W-8: No-Source Behavior (MANDATORY): No workers if no sources active
 * - Does NOT call pipelines or CatalogSyncService directly
 * - Only reads SourceActivationStore and enqueues child workers
 * - Each source runs in its own independent chain (not sequential)
 * - Per-source unique work names prevent cross-source blocking
 *
 * **Architecture:**
 * - Xtream chain: catalog_sync_global_xtream
 * - Telegram chain: catalog_sync_global_telegram
 * - IO chain: catalog_sync_global_io
 * - Each chain uses REPLACE policy for late activation safety
 *
 * @see XtreamPreflightWorker
 * @see XtreamCatalogScanWorker
 * @see TelegramAuthPreflightWorker
 * @see TelegramFullHistoryScanWorker
 * @see TelegramIncrementalScanWorker
 * @see IoQuickScanWorker
 */
@HiltWorker
class CatalogSyncOrchestratorWorker
    @AssistedInject
    constructor(
        @Assisted context: Context,
        @Assisted workerParams: WorkerParameters,
        private val sourceActivationStore: SourceActivationStore,
    ) : CoroutineWorker(context, workerParams) {
        companion object {
            private const val TAG = "CatalogSyncOrchestratorWorker"
        }

        override suspend fun doWork(): Result {
            val input = WorkerInputData.from(inputData)
            val startTimeMs = System.currentTimeMillis()

            UnifiedLog.i(TAG) { "START sync_run_id=${input.syncRunId} mode=${input.syncMode}" }

            // W-16: Check runtime guards (respects sync mode - manual syncs skip battery guards)
            val guardReason = RuntimeGuards.checkGuards(applicationContext, input.syncMode)
            if (guardReason != null) {
                UnifiedLog.w(TAG) { "GUARD_DEFER reason=$guardReason mode=${input.syncMode}" }
                // Return retry to defer execution
                return Result.retry()
            }

            // W-8: Check for active sources
            val activeSources = sourceActivationStore.getActiveSources()

            UnifiedLog.i(TAG) {
                "Active sources check: $activeSources (isEmpty=${activeSources.isEmpty()})"
            }

            if (activeSources.isEmpty()) {
                val durationMs = System.currentTimeMillis() - startTimeMs
                UnifiedLog.i(TAG) {
                    "SUCCESS duration_ms=$durationMs (no active sources, nothing to sync)"
                }
                return Result.success(
                    WorkerOutputData.success(
                        itemsPersisted = 0,
                        durationMs = durationMs,
                    ),
                )
            }

            UnifiedLog.d(TAG) {
                "Active sources: $activeSources (TELEGRAM=${SourceId.TELEGRAM in activeSources}, XTREAM=${SourceId.XTREAM in activeSources}, IO=${SourceId.IO in activeSources})"
            }

            // Build PARALLEL chains per source (not sequential)
            // Each source gets its own unique work name and can run independently
            // This prevents one source's preflight retries from blocking another source
            val workManager = WorkManager.getInstance(applicationContext)
            val childInputData = buildChildInputData(input, activeSources)

            // Track enqueued chains for logging
            val enqueuedChains = mutableListOf<String>()

            // 1. Xtream (if active) - independent chain
            if (SourceId.XTREAM in activeSources) {
                val xtreamChain = buildXtreamChain(childInputData)
                val xtreamWorkName = "${WorkerConstants.WORK_NAME_CATALOG_SYNC}_xtream"
                
                workManager
                    .beginUniqueWork(
                        xtreamWorkName,
                        ExistingWorkPolicy.REPLACE,
                        xtreamChain.first(),
                    )
                    .then(xtreamChain.drop(1))
                    .enqueue()
                
                enqueuedChains.add("XTREAM")
                UnifiedLog.d(TAG) {
                    "Enqueued Xtream chain: work_name=$xtreamWorkName workers=${xtreamChain.size}"
                }
            }

            // 2. Telegram (if active) - independent chain
            if (SourceId.TELEGRAM in activeSources) {
                UnifiedLog.i(TAG) { "✅ Telegram is ACTIVE - building Telegram worker chain" }
                val telegramChain = buildTelegramChain(childInputData, input)
                val telegramWorkName = "${WorkerConstants.WORK_NAME_CATALOG_SYNC}_telegram"
                
                workManager
                    .beginUniqueWork(
                        telegramWorkName,
                        ExistingWorkPolicy.REPLACE,
                        telegramChain.first(),
                    )
                    .then(telegramChain.drop(1))
                    .enqueue()
                
                enqueuedChains.add("TELEGRAM")
                UnifiedLog.i(TAG) {
                    "✅ Enqueued Telegram chain: work_name=$telegramWorkName workers=${telegramChain.size} (preflight + scan)"
                }
            } else {
                // Use DEBUG level - Telegram might just be initializing, not an error
                UnifiedLog.d(TAG) { "Telegram not in active sources - skipping Telegram workers" }
            }

            // 3. IO (if active) - independent chain
            if (SourceId.IO in activeSources) {
                val ioWorker = buildIoWorker(childInputData)
                val ioWorkName = "${WorkerConstants.WORK_NAME_CATALOG_SYNC}_io"
                
                workManager
                    .beginUniqueWork(
                        ioWorkName,
                        ExistingWorkPolicy.REPLACE,
                        ioWorker,
                    )
                    .enqueue()
                
                enqueuedChains.add("IO")
                UnifiedLog.d(TAG) {
                    "Enqueued IO chain: work_name=$ioWorkName workers=1"
                }
            }

            val durationMs = System.currentTimeMillis() - startTimeMs
            UnifiedLog.i(TAG) {
                "SUCCESS duration_ms=$durationMs (enqueued ${enqueuedChains.size} parallel chains: ${enqueuedChains.joinToString(", ")})"
            }

            return Result.success(
                WorkerOutputData.success(
                    itemsPersisted = 0,
                    durationMs = durationMs,
                ),
            )
        }

        private fun buildChildInputData(
            parentInput: WorkerInputData,
            activeSources: Set<SourceId>,
        ): Data =
            Data
                .Builder()
                .putString(WorkerConstants.KEY_SYNC_RUN_ID, parentInput.syncRunId)
                .putString(WorkerConstants.KEY_SYNC_MODE, parentInput.syncMode)
                .putStringArray(
                    WorkerConstants.KEY_ACTIVE_SOURCES,
                    activeSources.map { it.name }.toTypedArray(),
                ).putBoolean(WorkerConstants.KEY_WIFI_ONLY, parentInput.wifiOnly)
                .putLong(WorkerConstants.KEY_MAX_RUNTIME_MS, parentInput.maxRuntimeMs)
                .putString(WorkerConstants.KEY_DEVICE_CLASS, parentInput.deviceClass)
                .putString(
                    WorkerConstants.KEY_XTREAM_SYNC_SCOPE,
                    parentInput.xtreamSyncScope ?: WorkerConstants.XTREAM_SCOPE_INCREMENTAL,
                ).putString(
                    WorkerConstants.KEY_TELEGRAM_SYNC_KIND,
                    parentInput.telegramSyncKind
                        ?: WorkerConstants.TELEGRAM_KIND_INCREMENTAL,
                ).putString(
                    WorkerConstants.KEY_IO_SYNC_SCOPE,
                    parentInput.ioSyncScope ?: WorkerConstants.IO_SCOPE_QUICK,
                ).build()

        private fun buildXtreamChain(inputData: Data): List<OneTimeWorkRequest> =
            listOf(
                OneTimeWorkRequestBuilder<XtreamPreflightWorker>()
                    .setInputData(inputData)
                    .addTag(WorkerConstants.TAG_CATALOG_SYNC)
                    .addTag(WorkerConstants.TAG_SOURCE_XTREAM)
                    .addTag(WorkerConstants.TAG_WORKER_XTREAM_PREFLIGHT)
                    .setBackoffCriteria(
                        androidx.work.BackoffPolicy.EXPONENTIAL,
                        WorkerConstants.BACKOFF_INITIAL_SECONDS,
                        TimeUnit.SECONDS,
                    ).build(),
                OneTimeWorkRequestBuilder<XtreamCatalogScanWorker>()
                    .setInputData(inputData)
                    .addTag(WorkerConstants.TAG_CATALOG_SYNC)
                    .addTag(WorkerConstants.TAG_SOURCE_XTREAM)
                    .addTag(WorkerConstants.TAG_WORKER_XTREAM_SCAN)
                    .setBackoffCriteria(
                        androidx.work.BackoffPolicy.EXPONENTIAL,
                        WorkerConstants.BACKOFF_INITIAL_SECONDS,
                        TimeUnit.SECONDS,
                    ).build(),
            )

        private fun buildTelegramChain(
            inputData: Data,
            parentInput: WorkerInputData,
        ): List<OneTimeWorkRequest> {
            val scanWorker =
                if (parentInput.telegramSyncKind == WorkerConstants.TELEGRAM_KIND_FULL_HISTORY ||
                    parentInput.syncMode == WorkerConstants.SYNC_MODE_FORCE_RESCAN
                ) {
                    OneTimeWorkRequestBuilder<TelegramFullHistoryScanWorker>()
                        .setInputData(inputData)
                        .addTag(WorkerConstants.TAG_CATALOG_SYNC)
                        .addTag(WorkerConstants.TAG_SOURCE_TELEGRAM)
                        .addTag(WorkerConstants.TAG_WORKER_TELEGRAM_FULL)
                        .setBackoffCriteria(
                            androidx.work.BackoffPolicy.EXPONENTIAL,
                            WorkerConstants.BACKOFF_INITIAL_SECONDS,
                            TimeUnit.SECONDS,
                        ).build()
                } else {
                    OneTimeWorkRequestBuilder<TelegramIncrementalScanWorker>()
                        .setInputData(inputData)
                        .addTag(WorkerConstants.TAG_CATALOG_SYNC)
                        .addTag(WorkerConstants.TAG_SOURCE_TELEGRAM)
                        .addTag(WorkerConstants.TAG_WORKER_TELEGRAM_INCREMENTAL)
                        .setBackoffCriteria(
                            androidx.work.BackoffPolicy.EXPONENTIAL,
                            WorkerConstants.BACKOFF_INITIAL_SECONDS,
                            TimeUnit.SECONDS,
                        ).build()
                }

            return listOf(
                OneTimeWorkRequestBuilder<TelegramAuthPreflightWorker>()
                    .setInputData(inputData)
                    .addTag(WorkerConstants.TAG_CATALOG_SYNC)
                    .addTag(WorkerConstants.TAG_SOURCE_TELEGRAM)
                    .addTag(WorkerConstants.TAG_WORKER_TELEGRAM_AUTH)
                    .setBackoffCriteria(
                        androidx.work.BackoffPolicy.EXPONENTIAL,
                        WorkerConstants.BACKOFF_INITIAL_SECONDS,
                        TimeUnit.SECONDS,
                    ).build(),
                scanWorker,
            )
        }

        private fun buildIoWorker(inputData: Data): OneTimeWorkRequest =
            OneTimeWorkRequestBuilder<IoQuickScanWorker>()
                .setInputData(inputData)
                .addTag(WorkerConstants.TAG_CATALOG_SYNC)
                .addTag(WorkerConstants.TAG_SOURCE_IO)
                .addTag(WorkerConstants.TAG_WORKER_IO_QUICK)
                .setBackoffCriteria(
                    androidx.work.BackoffPolicy.EXPONENTIAL,
                    WorkerConstants.BACKOFF_INITIAL_SECONDS,
                    TimeUnit.SECONDS,
                ).build()
    }
