package com.fishit.player.v2.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.fishit.player.core.catalogsync.SourceActivationStore
import com.fishit.player.core.catalogsync.SourceId
import com.fishit.player.core.feature.auth.TelegramAuthRepository
import com.fishit.player.core.feature.auth.TelegramAuthState
import com.fishit.player.infra.logging.UnifiedLog
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Telegram Auth Preflight Worker.
 * 
 * Verifies TDLib authorization state before catalog scan.
 * 
 * Contract: CATALOG_SYNC_WORKERS_CONTRACT_V2
 * - W-20: Non-Retryable Failures (login required)
 * - Does NOT perform scanning
 * - Only validates TDLib is authorized and ready
 * 
 * **Architecture:**
 * - Uses domain interface [TelegramAuthRepository] (not transport layer)
 * - Implementation in infra/data-telegram bridges to transport
 * 
 * @see TelegramFullHistoryScanWorker for full history scanning
 * @see TelegramIncrementalScanWorker for incremental scanning
 */
@HiltWorker
class TelegramAuthPreflightWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val sourceActivationStore: SourceActivationStore,
    private val telegramAuthRepository: TelegramAuthRepository,
) : CoroutineWorker(context, workerParams) {

    companion object {
        private const val TAG = "TelegramAuthPreflightWorker"
    }

    override suspend fun doWork(): Result {
        val input = WorkerInputData.from(inputData)
        val startTimeMs = System.currentTimeMillis()
        
        UnifiedLog.i(TAG) { 
            "START sync_run_id=${input.syncRunId} mode=${input.syncMode} source=TELEGRAM"
        }
        
        // Check runtime guards
        val guardReason = RuntimeGuards.checkGuards(applicationContext)
        if (guardReason != null) {
            UnifiedLog.w(TAG) { "GUARD_DEFER reason=$guardReason" }
            return Result.retry()
        }
        
        // Verify Telegram is active
        val activeSources = sourceActivationStore.getActiveSources()
        if (SourceId.TELEGRAM !in activeSources) {
            val durationMs = System.currentTimeMillis() - startTimeMs
            UnifiedLog.w(TAG) { "FAILURE reason=source_not_active duration_ms=$durationMs" }
            return Result.failure(
                WorkerOutputData.failure(WorkerConstants.FAILURE_TELEGRAM_NOT_AUTHORIZED)
            )
        }
        
        // Check authorization state via domain repository
        return try {
            val authState = telegramAuthRepository.authState.value
            
            UnifiedLog.i(TAG) { "Checking auth state: $authState (isConnected=${authState is TelegramAuthState.Connected})" }
            
            when (authState) {
                is TelegramAuthState.Connected -> {
                    val durationMs = System.currentTimeMillis() - startTimeMs
                    UnifiedLog.i(TAG) { "✅ SUCCESS duration_ms=$durationMs (TDLib authorized)" }
                    Result.success(
                        WorkerOutputData.success(
                            itemsPersisted = 0,
                            durationMs = durationMs,
                        )
                    )
                }
                
                is TelegramAuthState.WaitingForPhone,
                is TelegramAuthState.WaitingForCode,
                is TelegramAuthState.WaitingForPassword -> {
                    val durationMs = System.currentTimeMillis() - startTimeMs
                    UnifiedLog.e(TAG) { 
                        "❌ FAILURE reason=login_required state=$authState duration_ms=$durationMs retry=false"
                    }
                    // W-20: Non-retryable failure - user action required
                    Result.failure(
                        WorkerOutputData.failure(WorkerConstants.FAILURE_TELEGRAM_NOT_AUTHORIZED)
                    )
                }
                
                is TelegramAuthState.Disconnected,
                is TelegramAuthState.Error -> {
                    val durationMs = System.currentTimeMillis() - startTimeMs
                    UnifiedLog.e(TAG) { 
                        "❌ FAILURE reason=not_authorized state=$authState duration_ms=$durationMs retry=false"
                    }
                    // W-20: Non-retryable failure - user action required
                    Result.failure(
                        WorkerOutputData.failure(WorkerConstants.FAILURE_TELEGRAM_NOT_AUTHORIZED)
                    )
                }
                
                is TelegramAuthState.Idle -> {
                    // Still initializing, retry
                    UnifiedLog.w(TAG) { "⚠️ Auth state idle (still initializing), retrying" }
                    Result.retry()
                }
            }
        } catch (e: Exception) {
            val durationMs = System.currentTimeMillis() - startTimeMs
            UnifiedLog.e(TAG, e) { 
                "❌ FAILURE reason=auth_check_failed duration_ms=$durationMs retry=true"
            }
            // Transient init error - retry
            Result.retry()
        }
    }
}
