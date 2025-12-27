package com.fishit.player.v2.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.fishit.player.core.sourceactivation.SourceActivationStore
import com.fishit.player.core.sourceactivation.SourceId
import com.fishit.player.feature.onboarding.domain.XtreamAuthRepository
import com.fishit.player.feature.onboarding.domain.XtreamAuthState
import com.fishit.player.feature.onboarding.domain.XtreamConnectionState
import com.fishit.player.infra.logging.UnifiedLog
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Xtream Preflight Worker.
 *
 * Validates Xtream configuration and connectivity before catalog scan.
 *
 * Contract: CATALOG_SYNC_WORKERS_CONTRACT_V2
 * - W-20: Non-Retryable Failures (invalid credentials)
 * - Does NOT perform scanning
 * - Only validates config is present and credentials are valid
 *
 * **Architecture:**
 * - Uses domain interface [XtreamAuthRepository] (not transport layer)
 * - Implementation in infra/data-xtream bridges to transport
 *
 * @see XtreamCatalogScanWorker for actual scanning
 */
@HiltWorker
class XtreamPreflightWorker
@AssistedInject
constructor(
        @Assisted context: Context,
        @Assisted workerParams: WorkerParameters,
        private val sourceActivationStore: SourceActivationStore,
        private val xtreamAuthRepository: XtreamAuthRepository,
) : CoroutineWorker(context, workerParams) {
    companion object {
        private const val TAG = "XtreamPreflightWorker"
    }

    override suspend fun doWork(): Result {
        val input = WorkerInputData.from(inputData)
        val startTimeMs = System.currentTimeMillis()

        UnifiedLog.i(TAG) {
            "START sync_run_id=${input.syncRunId} mode=${input.syncMode} source=XTREAM"
        }

        // Check runtime guards (respects sync mode - manual syncs skip battery guards)
        val guardReason = RuntimeGuards.checkGuards(applicationContext, input.syncMode)
        if (guardReason != null) {
            UnifiedLog.w(TAG) { "GUARD_DEFER reason=$guardReason mode=${input.syncMode}" }
            return Result.retry()
        }

        // Verify Xtream is active
        val activeSources = sourceActivationStore.getActiveSources()
        if (SourceId.XTREAM !in activeSources) {
            val durationMs = System.currentTimeMillis() - startTimeMs
            UnifiedLog.w(TAG) { "FAILURE reason=source_not_active duration_ms=$durationMs" }
            return Result.failure(
                    WorkerOutputData.failure(WorkerConstants.FAILURE_XTREAM_INVALID_CREDENTIALS),
            )
        }

        // Check auth state via domain repository
        val authState = xtreamAuthRepository.authState.value
        val connectionState = xtreamAuthRepository.connectionState.value

        // First check connection state
        if (connectionState is XtreamConnectionState.Error) {
            UnifiedLog.w(TAG) { "Connection error: ${connectionState.message} retry=true" }
            return Result.retry()
        }

        return when (authState) {
            is XtreamAuthState.Authenticated -> {
                val durationMs = System.currentTimeMillis() - startTimeMs
                UnifiedLog.i(TAG) { "SUCCESS duration_ms=$durationMs (credentials valid)" }
                Result.success(
                        WorkerOutputData.success(
                                itemsPersisted = 0,
                                durationMs = durationMs,
                        ),
                )
            }
            is XtreamAuthState.Failed -> {
                val durationMs = System.currentTimeMillis() - startTimeMs
                UnifiedLog.e(TAG) {
                    "FAILURE reason=auth_failed error=${authState.message} duration_ms=$durationMs retry=false"
                }
                // W-20: Non-retryable failure
                Result.failure(
                        WorkerOutputData.failure(
                                WorkerConstants.FAILURE_XTREAM_INVALID_CREDENTIALS
                        ),
                )
            }
            is XtreamAuthState.Expired -> {
                val durationMs = System.currentTimeMillis() - startTimeMs
                UnifiedLog.e(TAG) {
                    "FAILURE reason=account_expired exp_date=${authState.expDate} duration_ms=$durationMs retry=false"
                }
                // W-20: Non-retryable failure
                Result.failure(
                        WorkerOutputData.failure(
                                WorkerConstants.FAILURE_XTREAM_INVALID_CREDENTIALS
                        ),
                )
            }
            is XtreamAuthState.Idle -> {
                // Not yet authenticated, retry
                UnifiedLog.w(TAG) { "Auth state idle, retrying" }
                Result.retry()
            }
        }
    }
}
