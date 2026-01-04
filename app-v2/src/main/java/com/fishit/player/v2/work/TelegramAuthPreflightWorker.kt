package com.fishit.player.v2.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.fishit.player.core.feature.auth.TelegramAuthRepository
import com.fishit.player.core.feature.auth.TelegramAuthState
import com.fishit.player.core.sourceactivation.SourceActivationStore
import com.fishit.player.core.sourceactivation.SourceId
import com.fishit.player.infra.logging.UnifiedLog
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Telegram Auth Preflight Worker.
 *
 * Verifies TDLib authorization state before catalog scan.
 *
 * Contract: CATALOG_SYNC_WORKERS_CONTRACT_V2
 * - W-19: Bounded retries (AUTO=3, EXPERT=5) via WorkerRetryPolicy
 * - W-20: Non-Retryable Failures (login required, not authorized)
 * - Does NOT perform scanning
 * - Only validates TDLib is authorized and ready
 *
 * **Semantics:**
 * - Connected → Result.success
 * - WaitingForPhone/Code/Password → Result.failure (non-retryable, user action required)
 * - Disconnected/Error → Result.failure (non-retryable)
 * - Idle (still initializing) → Result.retry (bounded by WorkerRetryPolicy)
 *
 * **Architecture:**
 * - Uses domain interface [TelegramAuthRepository] (not transport layer)
 * - Implementation in infra/data-telegram bridges to transport
 *
 * @see TelegramFullHistoryScanWorker for full history scanning
 * @see TelegramIncrementalScanWorker for incremental scanning
 */
@HiltWorker
class TelegramAuthPreflightWorker
    @AssistedInject
    constructor(
        @Assisted context: Context,
        @Assisted workerParams: WorkerParameters,
        private val sourceActivationStore: SourceActivationStore,
        private val telegramAuthRepository: TelegramAuthRepository,
    ) : CoroutineWorker(context, workerParams) {
        companion object {
            private const val TAG = "TelegramAuthPreflightWorker"

            // Failure reasons (non-retryable)
            private const val FAILURE_NOT_AUTHORIZED = WorkerConstants.FAILURE_TELEGRAM_NOT_AUTHORIZED

            // Failure reasons (retryable but limit exceeded)
            private const val FAILURE_IDLE_LIMIT_EXCEEDED = "TELEGRAM_IDLE_TIMEOUT"
            private const val FAILURE_TRANSIENT_LIMIT_EXCEEDED = "TELEGRAM_TRANSIENT_ERROR_LIMIT"
        }

        override suspend fun doWork(): Result {
            val input = WorkerInputData.from(inputData)
            val startTimeMs = System.currentTimeMillis()

            UnifiedLog.i(TAG) {
                "START sync_run_id=${input.syncRunId} mode=${input.syncMode} source=TELEGRAM ${WorkerRetryPolicy.getAttemptInfo(
                    this,
                    input,
                )}"
            }

            // Check runtime guards (respects sync mode - manual syncs skip battery guards)
            val guardReason = RuntimeGuards.checkGuards(applicationContext, input.syncMode)
            if (guardReason != null) {
                UnifiedLog.w(TAG) { "GUARD_DEFER reason=$guardReason mode=${input.syncMode}" }
                return WorkerRetryPolicy.retryOrFail(
                    worker = this,
                    input = input,
                    reasonOnFail = "GUARD_DEFER_$guardReason",
                    durationMs = System.currentTimeMillis() - startTimeMs,
                    logMessage = "Guard defer: $guardReason",
                )
            }

            // Verify Telegram is active
            val activeSources = sourceActivationStore.getActiveSources()
            if (SourceId.TELEGRAM !in activeSources) {
                val durationMs = System.currentTimeMillis() - startTimeMs
                UnifiedLog.w(TAG) { "FAILURE reason=source_not_active duration_ms=$durationMs" }
                return Result.failure(
                    WorkerOutputData.failure(FAILURE_NOT_AUTHORIZED, durationMs),
                )
            }

            // Check authorization state via domain repository
            return try {
                val authState = telegramAuthRepository.authState.value

                UnifiedLog.i(TAG) {
                    "Checking auth state: $authState (isConnected=${authState is TelegramAuthState.Connected})"
                }

                when (authState) {
                    is TelegramAuthState.Connected -> {
                        val durationMs = System.currentTimeMillis() - startTimeMs
                        UnifiedLog.i(TAG) { "✅ SUCCESS duration_ms=$durationMs (TDLib authorized)" }
                        Result.success(
                            WorkerOutputData.success(
                                itemsPersisted = 0,
                                durationMs = durationMs,
                            ),
                        )
                    }
                    is TelegramAuthState.WaitingForPhone,
                    is TelegramAuthState.WaitingForCode,
                    is TelegramAuthState.WaitingForPassword,
                    -> {
                        val durationMs = System.currentTimeMillis() - startTimeMs
                        UnifiedLog.e(TAG) {
                            "❌ FAILURE reason=login_required state=$authState duration_ms=$durationMs retry=false"
                        }
                        // W-20: Non-retryable failure - user action required
                        Result.failure(
                            WorkerOutputData.failure(FAILURE_NOT_AUTHORIZED, durationMs),
                        )
                    }
                    is TelegramAuthState.Disconnected, is TelegramAuthState.Error -> {
                        val durationMs = System.currentTimeMillis() - startTimeMs
                        UnifiedLog.e(TAG) {
                            "❌ FAILURE reason=not_authorized state=$authState duration_ms=$durationMs retry=false"
                        }
                        // W-20: Non-retryable failure - user action required
                        Result.failure(
                            WorkerOutputData.failure(FAILURE_NOT_AUTHORIZED, durationMs),
                        )
                    }
                    is TelegramAuthState.Idle -> {
                        // Still initializing, retry with bounded policy
                        UnifiedLog.w(TAG) { "⚠️ Auth state idle (still initializing)" }
                        WorkerRetryPolicy.retryOrFail(
                            worker = this,
                            input = input,
                            reasonOnFail = FAILURE_IDLE_LIMIT_EXCEEDED,
                            durationMs = System.currentTimeMillis() - startTimeMs,
                            logMessage = "Auth state idle (initializing)",
                        )
                    }
                }
            } catch (e: Exception) {
                val durationMs = System.currentTimeMillis() - startTimeMs
                UnifiedLog.e(TAG, e) { "❌ Exception during auth check: ${e.message}" }
                // Transient init error - retry with bounded policy
                WorkerRetryPolicy.retryOrFail(
                    worker = this,
                    input = input,
                    reasonOnFail = FAILURE_TRANSIENT_LIMIT_EXCEEDED,
                    durationMs = durationMs,
                    logMessage = "Auth check exception: ${e.javaClass.simpleName}",
                )
            }
        }
    }
