package com.fishit.player.v2.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.fishit.player.core.catalogsync.SourceActivationStore
import com.fishit.player.core.catalogsync.SourceId
import com.fishit.player.infra.logging.UnifiedLog
import com.fishit.player.infra.transport.xtream.XtreamApiClient
import com.fishit.player.infra.transport.xtream.XtreamAuthState
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
 * @see XtreamCatalogScanWorker for actual scanning
 */
@HiltWorker
class XtreamPreflightWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val sourceActivationStore: SourceActivationStore,
    private val xtreamApiClient: XtreamApiClient,
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
        
        // Check runtime guards
        val guardReason = RuntimeGuards.checkGuards(applicationContext)
        if (guardReason != null) {
            UnifiedLog.w(TAG) { "GUARD_DEFER reason=$guardReason" }
            return Result.retry()
        }
        
        // Verify Xtream is active
        val activeSources = sourceActivationStore.getActiveSources()
        if (SourceId.XTREAM !in activeSources) {
            val durationMs = System.currentTimeMillis() - startTimeMs
            UnifiedLog.w(TAG) { "FAILURE reason=source_not_active duration_ms=$durationMs" }
            return Result.failure(
                WorkerOutputData.failure(WorkerConstants.FAILURE_XTREAM_INVALID_CREDENTIALS)
            )
        }
        
        // Check auth state
        val authState = xtreamApiClient.authState.value
        
        return when (authState) {
            is XtreamAuthState.Authenticated -> {
                val durationMs = System.currentTimeMillis() - startTimeMs
                UnifiedLog.i(TAG) { "SUCCESS duration_ms=$durationMs (credentials valid)" }
                Result.success(
                    WorkerOutputData.success(
                        itemsPersisted = 0,
                        durationMs = durationMs,
                    )
                )
            }
            
            is XtreamAuthState.Failed -> {
                val durationMs = System.currentTimeMillis() - startTimeMs
                UnifiedLog.e(TAG) { 
                    "FAILURE reason=auth_failed error=${authState.error} duration_ms=$durationMs retry=false"
                }
                // W-20: Non-retryable failure
                Result.failure(
                    WorkerOutputData.failure(WorkerConstants.FAILURE_XTREAM_INVALID_CREDENTIALS)
                )
            }
            
            is XtreamAuthState.Expired -> {
                val durationMs = System.currentTimeMillis() - startTimeMs
                UnifiedLog.e(TAG) { 
                    "FAILURE reason=account_expired duration_ms=$durationMs retry=false"
                }
                // W-20: Non-retryable failure
                Result.failure(
                    WorkerOutputData.failure(WorkerConstants.FAILURE_XTREAM_INVALID_CREDENTIALS)
                )
            }
            
            is XtreamAuthState.Pending,
            is XtreamAuthState.Unknown -> {
                // Try ping to validate
                try {
                    val isReachable = xtreamApiClient.ping()
                    if (isReachable) {
                        val durationMs = System.currentTimeMillis() - startTimeMs
                        UnifiedLog.i(TAG) { "SUCCESS duration_ms=$durationMs (ping successful)" }
                        Result.success(
                            WorkerOutputData.success(
                                itemsPersisted = 0,
                                durationMs = durationMs,
                            )
                        )
                    } else {
                        UnifiedLog.w(TAG) { "FAILURE reason=ping_failed retry=true" }
                        Result.retry()
                    }
                } catch (e: Exception) {
                    UnifiedLog.e(TAG, e) { "FAILURE reason=ping_exception retry=true" }
                    Result.retry()
                }
            }
        }
    }
}
