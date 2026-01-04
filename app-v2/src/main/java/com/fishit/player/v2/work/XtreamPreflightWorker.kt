package com.fishit.player.v2.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.fishit.player.core.onboarding.domain.XtreamAuthRepository
import com.fishit.player.core.onboarding.domain.XtreamAuthState
import com.fishit.player.core.onboarding.domain.XtreamConnectionState
import com.fishit.player.core.sourceactivation.SourceActivationStore
import com.fishit.player.core.sourceactivation.SourceId
import com.fishit.player.infra.logging.UnifiedLog
import com.fishit.player.infra.transport.xtream.XtreamCredentialsStore
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Xtream Preflight Worker.
 *
 * Validates Xtream configuration and connectivity before catalog scan.
 *
 * Contract: CATALOG_SYNC_WORKERS_CONTRACT_V2
 * - W-19: Bounded retries (AUTO=3, EXPERT=5) via WorkerRetryPolicy
 * - W-20: Non-Retryable Failures (invalid credentials, not configured)
 * - Does NOT perform scanning
 * - Only validates config is present and credentials are valid
 *
 * **Semantics:**
 * - Authenticated → Result.success
 * - Failed/Expired → Result.failure (non-retryable)
 * - Idle + no stored credentials → Result.failure (not configured, non-retryable)
 * - Idle + stored credentials exist → Result.retry (bounded by WorkerRetryPolicy)
 * - Credentials read exception → Result.retry (bounded, transient init error)
 * - Connection error → Result.retry (bounded by WorkerRetryPolicy)
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
        private val xtreamCredentialsStore: XtreamCredentialsStore,
    ) : CoroutineWorker(context, workerParams) {
        companion object {
            private const val TAG = "XtreamPreflightWorker"

            // Failure reasons (non-retryable)
            private const val FAILURE_INVALID_CREDS = WorkerConstants.FAILURE_XTREAM_INVALID_CREDENTIALS
            private const val FAILURE_NOT_CONFIGURED = WorkerConstants.FAILURE_XTREAM_NOT_CONFIGURED

            // Failure reasons (retryable but limit exceeded)
            private const val FAILURE_IDLE_LIMIT_EXCEEDED = "XTREAM_IDLE_TIMEOUT"
            private const val FAILURE_CONNECTION_LIMIT_EXCEEDED = "XTREAM_CONNECTION_ERROR_LIMIT"
            private const val FAILURE_CREDENTIALS_READ_LIMIT_EXCEEDED =
                "XTREAM_CREDENTIALS_READ_ERROR_LIMIT"
        }

        override suspend fun doWork(): Result {
            val input = WorkerInputData.from(inputData)
            val startTimeMs = System.currentTimeMillis()

            UnifiedLog.i(TAG) {
                "START sync_run_id=${input.syncRunId} mode=${input.syncMode} source=XTREAM ${WorkerRetryPolicy.getAttemptInfo(this, input)}"
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

            // Verify Xtream is active
            val activeSources = sourceActivationStore.getActiveSources()
            if (SourceId.XTREAM !in activeSources) {
                val durationMs = System.currentTimeMillis() - startTimeMs
                UnifiedLog.w(TAG) { "FAILURE reason=source_not_active duration_ms=$durationMs" }
                return Result.failure(
                    WorkerOutputData.failure(FAILURE_INVALID_CREDS, durationMs),
                )
            }

            // Check auth state via domain repository
            val authState = xtreamAuthRepository.authState.value
            val connectionState = xtreamAuthRepository.connectionState.value

            // First check connection state
            if (connectionState is XtreamConnectionState.Error) {
                UnifiedLog.w(TAG) { "Connection error: ${connectionState.message}" }
                return WorkerRetryPolicy.retryOrFail(
                    worker = this,
                    input = input,
                    reasonOnFail = FAILURE_CONNECTION_LIMIT_EXCEEDED,
                    durationMs = System.currentTimeMillis() - startTimeMs,
                    logMessage = "Connection error: ${connectionState.message}",
                )
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
                        WorkerOutputData.failure(FAILURE_INVALID_CREDS, durationMs),
                    )
                }
                is XtreamAuthState.Expired -> {
                    val durationMs = System.currentTimeMillis() - startTimeMs
                    UnifiedLog.e(TAG) {
                        "FAILURE reason=account_expired exp_date=${authState.expDate} duration_ms=$durationMs retry=false"
                    }
                    // W-20: Non-retryable failure
                    Result.failure(
                        WorkerOutputData.failure(FAILURE_INVALID_CREDS, durationMs),
                    )
                }
                is XtreamAuthState.Idle -> {
                    val durationMs = System.currentTimeMillis() - startTimeMs

                    // PHASE 2: Wrap credentials read in try/catch
                    // - If read throws → transient init error → retry (bounded)
                    // - If read returns null → not configured → fail-fast
                    // - If read returns incomplete → invalid → fail-fast
                    val storedCredentials =
                        try {
                            xtreamCredentialsStore.read()
                        } catch (e: Exception) {
                            UnifiedLog.w(TAG, e) {
                                "Credentials store read failed (transient init error): ${e.message}"
                            }
                            // Transient read error - retry with bounded policy
                            return WorkerRetryPolicy.retryOrFail(
                                worker = this,
                                input = input,
                                reasonOnFail = FAILURE_CREDENTIALS_READ_LIMIT_EXCEEDED,
                                durationMs = durationMs,
                                logMessage =
                                    "Credentials store read error: ${e.javaClass.simpleName}",
                            )
                        }

                    when {
                        storedCredentials == null -> {
                            // No credentials stored = not configured
                            // W-20: Non-retryable failure - source not configured
                            UnifiedLog.e(TAG) {
                                "FAILURE reason=not_configured state=Idle no_stored_credentials=true duration_ms=$durationMs retry=false"
                            }
                            Result.failure(
                                WorkerOutputData.failure(FAILURE_NOT_CONFIGURED, durationMs),
                            )
                        }
                        storedCredentials.host.isBlank() || storedCredentials.username.isBlank() -> {
                            // Incomplete credentials = invalid configuration
                            UnifiedLog.e(TAG) {
                                "FAILURE reason=incomplete_credentials state=Idle duration_ms=$durationMs retry=false"
                            }
                            Result.failure(
                                WorkerOutputData.failure(FAILURE_INVALID_CREDS, durationMs),
                            )
                        }
                        else -> {
                            // Credentials exist but session not yet initialized = transient
                            // Retry with bounded policy
                            UnifiedLog.w(TAG) {
                                "Auth state idle but credentials exist (session initializing)"
                            }
                            WorkerRetryPolicy.retryOrFail(
                                worker = this,
                                input = input,
                                reasonOnFail = FAILURE_IDLE_LIMIT_EXCEEDED,
                                durationMs = durationMs,
                                logMessage = "Auth state idle with valid credentials",
                            )
                        }
                    }
                }
            }
        }
    }
