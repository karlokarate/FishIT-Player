package com.fishit.player.v2.work

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.fishit.player.core.sourceactivation.SourceActivationStore
import com.fishit.player.core.sourceactivation.SourceId
import com.fishit.player.infra.logging.UnifiedLog
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * IO Quick Scan Worker.
 *
 * Executes quick local IO synchronization via CatalogSyncService ONLY.
 *
 * Contract: CATALOG_SYNC_WORKERS_CONTRACT_V2
 * - W-2: All scanning MUST go through CatalogSyncService
 * - W-20: Non-Retryable Failures (IO permission missing)
 * - W-17: FireTV Safety (bounded batches, frequent checkpoints)
 * - If IO inactive or permission missing → Result.failure() (non-retryable)
 * - Persists streaming
 *
 * Note: IO pipeline is currently a stub. This worker validates permissions and provides the
 * integration point for future local file scanning.
 */
@HiltWorker
class IoQuickScanWorker
    @AssistedInject
    constructor(
        @Assisted context: Context,
        @Assisted workerParams: WorkerParameters,
        private val sourceActivationStore: SourceActivationStore,
// Note: CatalogSyncService.syncIo() not yet implemented
// private val catalogSyncService: CatalogSyncService,
    ) : CoroutineWorker(context, workerParams) {
        companion object {
            private const val TAG = "IoQuickScanWorker"
        }

        override suspend fun doWork(): Result {
            val input = WorkerInputData.from(inputData)
            val startTimeMs = System.currentTimeMillis()

            UnifiedLog.i(TAG) {
                "START sync_run_id=${input.syncRunId} mode=${input.syncMode} source=IO scope=${input.ioSyncScope}"
            }

            // Check runtime guards (respects sync mode - manual syncs skip battery guards)
            val guardReason = RuntimeGuards.checkGuards(applicationContext, input.syncMode)
            if (guardReason != null) {
                UnifiedLog.w(TAG) { "GUARD_DEFER reason=$guardReason mode=${input.syncMode}" }
                return Result.retry()
            }

            // Verify IO is active
            val activeSources = sourceActivationStore.getActiveSources()
            if (SourceId.IO !in activeSources) {
                val durationMs = System.currentTimeMillis() - startTimeMs
                UnifiedLog.w(TAG) { "FAILURE reason=source_not_active duration_ms=$durationMs" }
                return Result.failure(
                    WorkerOutputData.failure(WorkerConstants.FAILURE_IO_PERMISSION_MISSING),
                )
            }

            // Check storage permission (W-20: Non-retryable if missing)
            if (!hasStoragePermission()) {
                val durationMs = System.currentTimeMillis() - startTimeMs
                UnifiedLog.e(TAG) {
                    "FAILURE reason=permission_missing duration_ms=$durationMs retry=false"
                }
                // W-20: Non-retryable failure - user action required
                return Result.failure(
                    WorkerOutputData.failure(WorkerConstants.FAILURE_IO_PERMISSION_MISSING),
                )
            }

            // IO pipeline is not yet implemented.
            // Per TODO_AUDIT_BLOCKING_ISSUES.md: Workers must not lie about success.
            // Return non-retryable failure to indicate feature is unavailable.
            val durationMs = System.currentTimeMillis() - startTimeMs
            UnifiedLog.w(TAG) { "FAILURE reason=not_implemented duration_ms=$durationMs retry=false" }

            return Result.failure(
                WorkerOutputData.failure(WorkerConstants.FAILURE_NOT_IMPLEMENTED),
            )
        }

        private fun hasStoragePermission(): Boolean =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                // Android 13+ uses granular media permissions
                ContextCompat.checkSelfPermission(
                    applicationContext,
                    Manifest.permission.READ_MEDIA_VIDEO,
                ) == PackageManager.PERMISSION_GRANTED
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                // Android 11-12 uses scoped storage
                // For now, assume permission granted if IO is active
                true
            } else {
                // Android 10 and below
                ContextCompat.checkSelfPermission(
                    applicationContext,
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                ) == PackageManager.PERMISSION_GRANTED
            }
    }
