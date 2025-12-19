package com.fishit.player.v2

import com.fishit.player.core.catalogsync.SourceActivationStore
import com.fishit.player.core.catalogsync.SourceErrorReason
import com.fishit.player.infra.logging.UnifiedLog
import com.fishit.player.infra.transport.telegram.TelegramAuthClient
import com.fishit.player.infra.transport.telegram.api.TdlibAuthState
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
 * - Watch TelegramAuthClient.authState for changes
 * - Set Telegram source ACTIVE when TdlibAuthState.Ready
 * - Set Telegram source INACTIVE when logged out/closed/error
 *
 * Contract: CATALOG_SYNC_WORKERS_CONTRACT_V2 (W-1)
 * - Sources are independent
 * - Activation triggers sync scheduling via SourceActivationObserver
 */
@Singleton
class TelegramActivationObserver @Inject constructor(
    private val telegramAuthClient: TelegramAuthClient,
    private val sourceActivationStore: SourceActivationStore,
    @Named(AppScopeModule.APP_LIFECYCLE_SCOPE)
    private val appScope: CoroutineScope,
) {
    private val hasStarted = AtomicBoolean(false)
    
    fun start() {
        if (!hasStarted.compareAndSet(false, true)) return
        
        UnifiedLog.i(TAG) { "TelegramActivationObserver starting" }
        
        appScope.launch {
            telegramAuthClient.authState
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
        data class Error(val reason: SourceErrorReason) : TelegramActivation
    }
    
    private fun mapToActivation(state: TdlibAuthState): TelegramActivation = when (state) {
        is TdlibAuthState.Ready -> TelegramActivation.Active
        
        // Logged out states = inactive, no error
        is TdlibAuthState.LoggedOut,
        is TdlibAuthState.Closed -> TelegramActivation.Inactive
        
        // Waiting for user input = login required
        is TdlibAuthState.WaitPhoneNumber,
        is TdlibAuthState.WaitCode,
        is TdlibAuthState.WaitPassword -> TelegramActivation.Error(SourceErrorReason.LOGIN_REQUIRED)
        
        // Connection/init states = still inactive but not an error
        is TdlibAuthState.Idle,
        is TdlibAuthState.Connecting,
        is TdlibAuthState.LoggingOut -> TelegramActivation.Inactive
        
        // Unknown states (e.g. WaitEmailAddress, WaitRegistration)
        else -> TelegramActivation.Inactive
    }
    
    private suspend fun handleActivation(activation: TelegramActivation) {
        when (activation) {
            is TelegramActivation.Active -> {
                UnifiedLog.i(TAG) { "Telegram auth ready → setting ACTIVE" }
                sourceActivationStore.setTelegramActive()
            }
            is TelegramActivation.Inactive -> {
                UnifiedLog.i(TAG) { "Telegram inactive → setting INACTIVE" }
                sourceActivationStore.setTelegramInactive()
            }
            is TelegramActivation.Error -> {
                UnifiedLog.i(TAG) { "Telegram error: ${activation.reason} → setting INACTIVE with reason" }
                sourceActivationStore.setTelegramInactive(activation.reason)
            }
        }
    }
    
    private companion object {
        private const val TAG = "TelegramActivationObserver"
    }
}
