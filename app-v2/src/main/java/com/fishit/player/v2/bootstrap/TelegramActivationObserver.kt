package com.fishit.player.v2.bootstrap

import com.fishit.player.core.feature.auth.TelegramAuthRepository
import com.fishit.player.core.feature.auth.TelegramAuthState
import com.fishit.player.core.sourceactivation.SourceActivationStore
import com.fishit.player.core.sourceactivation.SourceErrorReason
import com.fishit.player.infra.logging.UnifiedLog
import com.fishit.player.v2.di.AppScopeModule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/**
 * Observes Telegram auth state and updates [SourceActivationStore] accordingly.
 *
 * Responsibilities:
 * - Watch TelegramAuthRepository.authState for changes
 * - Set Telegram source ACTIVE when Connected
 * - Set Telegram source INACTIVE when logged out/disconnected/error
 *
 * **Architecture:**
 * - Uses domain interface [TelegramAuthRepository] (not transport layer)
 * - Implementation in infra/data-telegram bridges to transport
 *
 * Contract: CATALOG_SYNC_WORKERS_CONTRACT_V2 (W-1)
 * - Sources are independent
 * - Activation triggers sync scheduling via SourceActivationObserver
 */
@Singleton
class TelegramActivationObserver
    @Inject
    constructor(
        private val telegramAuthRepository: TelegramAuthRepository,
        private val sourceActivationStore: SourceActivationStore,
        @Named(AppScopeModule.APP_LIFECYCLE_SCOPE)
        private val appScope: CoroutineScope,
    ) {
        private val hasStarted = AtomicBoolean(false)

        fun start() {
            if (!hasStarted.compareAndSet(false, true)) return

            UnifiedLog.i(TAG) { "TelegramActivationObserver starting" }

            appScope.launch {
                telegramAuthRepository.authState
                    .map { state -> mapToActivation(state) }
                    .distinctUntilChanged()
                    .collect { activation ->
                        handleActivation(activation)
                    }
            }
        }

        private sealed interface TelegramActivation {
            /** Telegram is authorized and ready */
            data object Active : TelegramActivation

            /** Telegram is explicitly disconnected/logged out */
            data object Inactive : TelegramActivation

            /** Telegram is initializing - do NOT change activation state */
            data object Unchanged : TelegramActivation

            /** Telegram has an error that prevents operation */
            data class Error(
                val reason: SourceErrorReason,
            ) : TelegramActivation
        }

        private fun mapToActivation(state: TelegramAuthState): TelegramActivation {
            val activation =
                when (state) {
                    // Authorized = ACTIVE
                    is TelegramAuthState.Connected -> TelegramActivation.Active

                    // Explicitly logged out/disconnected = INACTIVE
                    is TelegramAuthState.Disconnected -> TelegramActivation.Inactive

                    // Idle = TDLib initializing, do NOT change activation state
                    // This prevents false INACTIVE at app startup
                    is TelegramAuthState.Idle -> TelegramActivation.Unchanged

                    // Waiting for user input = user is in auth flow, don't change state
                    // (they might be re-authenticating while previously ACTIVE)
                    is TelegramAuthState.WaitingForPhone,
                    is TelegramAuthState.WaitingForCode,
                    is TelegramAuthState.WaitingForPassword,
                    -> TelegramActivation.Unchanged

                    // Error state = inactive with LOGIN_REQUIRED
                    is TelegramAuthState.Error -> TelegramActivation.Error(SourceErrorReason.LOGIN_REQUIRED)
                }

            // Only log state transitions at appropriate levels
            when (activation) {
                is TelegramActivation.Unchanged -> {
                    UnifiedLog.d(TAG) { "Auth state=$state (initializing/in-progress) → leaving activation unchanged" }
                }
                else -> {
                    UnifiedLog.d(TAG) { "Auth state mapped: $state → $activation" }
                }
            }
            return activation
        }

        private suspend fun handleActivation(activation: TelegramActivation) {
            when (activation) {
                is TelegramActivation.Active -> {
                    UnifiedLog.i(TAG) { "✅ Telegram auth ready → calling sourceActivationStore.setTelegramActive()" }
                    sourceActivationStore.setTelegramActive()
                    UnifiedLog.i(TAG) { "✅ Telegram source marked ACTIVE - workers should be scheduled" }
                }
                is TelegramActivation.Inactive -> {
                    UnifiedLog.i(TAG) { "Telegram disconnected → setting INACTIVE" }
                    sourceActivationStore.setTelegramInactive()
                }
                is TelegramActivation.Error -> {
                    UnifiedLog.w(TAG) { "❌ Telegram error: ${activation.reason} → setting INACTIVE with reason" }
                    sourceActivationStore.setTelegramInactive(activation.reason)
                }
                is TelegramActivation.Unchanged -> {
                    // Do nothing - keep previous activation state
                    // This prevents false INACTIVE at startup when TDLib is just initializing
                }
            }
        }

        private companion object {
            private const val TAG = "TelegramActivationObserver"
        }
    }
