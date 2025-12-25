package com.fishit.player.v2

import com.fishit.player.core.catalogsync.SourceActivationStore
import com.fishit.player.core.catalogsync.SourceErrorReason
import com.fishit.player.core.feature.auth.TelegramAuthRepository
import com.fishit.player.core.feature.auth.TelegramAuthState
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
            data object Active : TelegramActivation

            data object Inactive : TelegramActivation

            data class Error(
                val reason: SourceErrorReason,
            ) : TelegramActivation
        }

        private fun mapToActivation(state: TelegramAuthState): TelegramActivation {
            val activation =
                when (state) {
                    is TelegramAuthState.Connected -> TelegramActivation.Active

                    // Logged out/disconnected = inactive, no error
                    is TelegramAuthState.Disconnected -> TelegramActivation.Inactive

                    // Waiting for user input = login required
                    is TelegramAuthState.WaitingForPhone,
                    is TelegramAuthState.WaitingForCode,
                    is TelegramAuthState.WaitingForPassword,
                    -> TelegramActivation.Error(SourceErrorReason.LOGIN_REQUIRED)

                    // Idle state = still inactive
                    is TelegramAuthState.Idle -> TelegramActivation.Inactive

                    // Error state = inactive with error
                    is TelegramAuthState.Error -> TelegramActivation.Error(SourceErrorReason.LOGIN_REQUIRED)
                }

            UnifiedLog.d(TAG) { "Auth state mapped: $state → $activation" }
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
                    UnifiedLog.i(TAG) { "⚠️ Telegram inactive → setting INACTIVE" }
                    sourceActivationStore.setTelegramInactive()
                }
                is TelegramActivation.Error -> {
                    UnifiedLog.i(TAG) { "❌ Telegram error: ${activation.reason} → setting INACTIVE with reason" }
                    sourceActivationStore.setTelegramInactive(activation.reason)
                }
            }
        }

        private companion object {
            private const val TAG = "TelegramActivationObserver"
        }
    }
