package com.fishit.player.v2.bootstrap

import com.fishit.player.core.catalogsync.XtreamCategoryPreloader
import com.fishit.player.core.sourceactivation.SourceActivationStore
import com.fishit.player.core.sourceactivation.SourceErrorReason
import com.fishit.player.infra.logging.UnifiedLog
import com.fishit.player.infra.transport.xtream.XtreamApiClient
import com.fishit.player.infra.transport.xtream.XtreamCredentialsStore
import com.fishit.player.infra.transport.xtream.XtreamStoredConfig
import com.fishit.player.v2.di.AppScopeModule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/**
 * Bootstraps Xtream session initialization from stored credentials on app start.
 *
 * Responsibilities:
 * - Read stored Xtream credentials on app start
 * - Auto-initialize XtreamApiClient if credentials exist
 * - Retry with exponential backoff on transient network failures
 * - Distinguish network errors (retryable) from credential errors (not retryable)
 * - Detect and report Keystore unavailability
 * - Update SourceActivationStore on success/failure
 * - Does NOT trigger catalog sync (that's CatalogSyncBootstrap's job)
 * - Runs once per app process
 */
@Singleton
class XtreamSessionBootstrap
    @Inject
    constructor(
        private val xtreamApiClient: XtreamApiClient,
        private val xtreamCredentialsStore: XtreamCredentialsStore,
        private val sourceActivationStore: SourceActivationStore,
        private val xtreamCategoryPreloader: XtreamCategoryPreloader,
        @Named(AppScopeModule.APP_LIFECYCLE_SCOPE)
        private val appScope: CoroutineScope,
    ) {
        private val hasAutoInitialized = AtomicBoolean(false)

        fun start() {
            if (!hasAutoInitialized.compareAndSet(false, true)) return

            appScope.launch(Dispatchers.IO) {
                try {
                    // Step 1: Check if secure storage is functional
                    if (!xtreamCredentialsStore.isAvailable()) {
                        UnifiedLog.e(TAG) {
                            "Android Keystore / EncryptedSharedPreferences unavailable. " +
                                "Cannot read stored Xtream credentials. User must re-login."
                        }
                        sourceActivationStore.setXtreamInactive(SourceErrorReason.KEYSTORE_UNAVAILABLE)
                        return@launch
                    }

                    // Step 2: Read stored credentials
                    val storedConfig = xtreamCredentialsStore.read()
                    if (storedConfig == null) {
                        UnifiedLog.d(TAG) { "No stored Xtream credentials found" }
                        sourceActivationStore.setXtreamInactive()
                        return@launch
                    }

                    // Step 3: Optimistic activation so manual sync buttons work immediately
                    sourceActivationStore.setXtreamActive()
                    UnifiedLog.i(TAG) {
                        "XTREAM activated (optimistic) from stored config: " +
                            "scheme=${storedConfig.scheme}, host=${storedConfig.host}, port=${storedConfig.port}"
                    }

                    // Step 4: Delay to let UI stabilize before heavy API calls
                    delay(SESSION_INIT_DELAY_MS)

                    // Step 5: Validate credentials with retry for network errors
                    validateWithRetry(storedConfig)
                } catch (t: Throwable) {
                    UnifiedLog.e(TAG, t) { "Xtream session auto-initialization unexpected error" }
                    sourceActivationStore.setXtreamInactive(SourceErrorReason.TRANSPORT_ERROR)
                }
            }
        }

        /**
         * Validate stored credentials against the Xtream server.
         *
         * Retries with exponential backoff on transient network errors.
         * Fails fast on credential errors (invalid/expired) — no retry.
         */
        private suspend fun validateWithRetry(storedConfig: XtreamStoredConfig) {
            var lastError: Throwable? = null

            for (attempt in 0..MAX_RETRIES) {
                if (attempt > 0) {
                    val backoffMs = INITIAL_BACKOFF_MS * (1L shl (attempt - 1)) // 5s, 10s, 20s
                    UnifiedLog.i(TAG) {
                        "Retry $attempt/$MAX_RETRIES after ${backoffMs}ms backoff"
                    }
                    delay(backoffMs)
                }

                val result = xtreamApiClient.initialize(storedConfig.toApiConfig())

                if (result.isSuccess) {
                    UnifiedLog.i(TAG) {
                        if (attempt > 0) {
                            "Xtream session validation succeeded after $attempt retries"
                        } else {
                            "Xtream session validation succeeded"
                        }
                    }
                    // Already active from optimistic activation — preload categories
                    xtreamCategoryPreloader.preloadCategories()
                    return
                }

                // Classify the error
                val error = result.exceptionOrNull()
                lastError = error

                if (isCredentialError(error)) {
                    // Credential errors are not retryable — fail immediately
                    UnifiedLog.w(TAG, error) {
                        "Xtream credentials invalid or expired — no retry"
                    }
                    sourceActivationStore.setXtreamInactive(SourceErrorReason.INVALID_CREDENTIALS)
                    xtreamCategoryPreloader.clearCache()
                    return
                }

                // Network/transient error — will retry if attempts remain
                UnifiedLog.w(TAG, error) {
                    "Xtream validation attempt ${attempt + 1}/${MAX_RETRIES + 1} failed (network/transient)"
                }
            }

            // All retries exhausted — network error
            UnifiedLog.e(TAG, lastError) {
                "Xtream session validation failed after ${MAX_RETRIES + 1} attempts — " +
                    "network likely unavailable. Keeping optimistic activation for manual retry."
            }
            // Use TRANSPORT_ERROR (not INVALID_CREDENTIALS) — credentials may be fine,
            // just can't reach the server. Keep credentials stored for future app starts.
            sourceActivationStore.setXtreamInactive(SourceErrorReason.TRANSPORT_ERROR)
        }

        /**
         * Classify whether an error is a credential problem (not retryable)
         * vs a transient/network problem (retryable).
         *
         * Credential errors: account expired, account not active, explicit auth failures
         * Network errors: IOException and subclasses (timeout, DNS, connection refused)
         */
        private fun isCredentialError(error: Throwable?): Boolean {
            if (error == null) return false

            // Check the exception message for credential-specific patterns
            val message = error.message?.lowercase() ?: ""
            if (message.contains("expired") ||
                message.contains("not active") ||
                message.contains("invalid credentials") ||
                message.contains("unauthorized") ||
                message.contains("banned")
            ) {
                return true
            }

            // IOException hierarchy is network-related (retryable)
            if (error is IOException) return false

            // Check the cause chain for IOException (OkHttp wraps exceptions)
            var cause = error.cause
            while (cause != null) {
                if (cause is IOException) return false
                cause = cause.cause
            }

            // Unknown error type — treat as credential issue (safer to not retry)
            // This handles cases like invalid JSON from wrong panel type
            return true
        }

        private companion object {
            private const val TAG = "XtreamSessionBootstrap"

            /**
             * Delay before session initialization to prevent UI frame drops.
             *
             * Rationale (see STARTUP_LOG_ANALYSIS.md):
             * - UI needs 1-2 seconds to fully initialize after app start
             * - Heavy API calls (9.4 MB JSON) during UI init cause 85 frame drops
             * - 2-second delay allows UI to complete first, then loads data
             *
             * Impact: Reduces frame drops from 85 → <10 (expected)
             */
            private const val SESSION_INIT_DELAY_MS = 2_000L

            /**
             * Maximum number of retry attempts for transient network errors.
             * Total attempts = MAX_RETRIES + 1 (initial + retries)
             */
            private const val MAX_RETRIES = 3

            /**
             * Initial backoff delay for retries (doubles each attempt).
             * Sequence: 5s → 10s → 20s
             */
            private const val INITIAL_BACKOFF_MS = 5_000L
        }
    }
