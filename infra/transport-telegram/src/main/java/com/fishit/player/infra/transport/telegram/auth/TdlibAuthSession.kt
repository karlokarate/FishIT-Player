package com.fishit.player.infra.transport.telegram.auth

import com.fishit.player.infra.logging.UnifiedLog
import com.fishit.player.infra.transport.telegram.TelegramAuthClient
import com.fishit.player.infra.transport.telegram.TelegramSessionConfig
import com.fishit.player.infra.transport.telegram.api.TelegramAuthException
import com.fishit.player.infra.transport.telegram.api.TdlibAuthState
import com.fishit.player.infra.transport.telegram.util.RetryConfig
import com.fishit.player.infra.transport.telegram.util.TelegramRetry
import dev.g000sha256.tdl.TdlClient
import dev.g000sha256.tdl.TdlResult
import dev.g000sha256.tdl.dto.AuthorizationState
import dev.g000sha256.tdl.dto.AuthorizationStateClosed
import dev.g000sha256.tdl.dto.AuthorizationStateClosing
import dev.g000sha256.tdl.dto.AuthorizationStateLoggingOut
import dev.g000sha256.tdl.dto.AuthorizationStateReady
import dev.g000sha256.tdl.dto.AuthorizationStateWaitCode
import dev.g000sha256.tdl.dto.AuthorizationStateWaitPassword
import dev.g000sha256.tdl.dto.AuthorizationStateWaitPhoneNumber
import dev.g000sha256.tdl.dto.AuthorizationStateWaitTdlibParameters
import dev.g000sha256.tdl.dto.PhoneNumberAuthenticationSettings
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

/**
 * TDLib Authorization Session Manager (v2 Architecture).
 *
 * Manages TDLib authentication state machine with Flow-based events. Ported from legacy
 * `T_TelegramSession` with v2 architecture compliance.
 *
 * **Key Behaviors (from legacy):**
 * - Resume-first: If already authorized on boot → Ready without UI involvement
 * - Automatic TDLib parameters setup
 * - Interactive auth: phone → code → password → ready
 * - Auth event streaming via SharedFlow
 * - Exponential backoff on retries
 *
 * **v2 Compliance:**
 * - No UI references (emits events instead of snackbars)
 * - Uses UnifiedLog for all logging
 * - DI-scoped (receives TdlClient, doesn't create it)
 *
 * @param client The TDLib client (injected via DI)
 * @param config Session configuration (API credentials, paths)
 * @param scope Coroutine scope for background operations
 *
 * @see TelegramAuthClient interface this implements
 * @see contracts/TELEGRAM_LEGACY_MODULE_MIGRATION_CONTRACT.md
 */
class TdlibAuthSession(
        private val client: TdlClient,
        private val config: TelegramSessionConfig,
        private val scope: CoroutineScope
) : TelegramAuthClient {

    companion object {
        private const val TAG = "TdlibAuthSession"
        private const val LOGIN_TIMEOUT_MS = 300_000L // 5 minutes
    }

    @Volatile private var currentState: AuthorizationState? = null

    @Volatile private var previousState: AuthorizationState? = null

    private val collectorStarted = AtomicBoolean(false)
    private val tdParamsSet = AtomicBoolean(false)

    private val _authState = MutableStateFlow< TdlibAuthState>( TdlibAuthState.Idle)
    override val authState: Flow< TdlibAuthState> = _authState.asStateFlow()

    private val _authEvents = MutableSharedFlow<AuthEvent>(replay = 1)

    /**
     * Internal auth events for detailed state tracking. Domain/UI can observe these for interactive
     * auth handling.
     */
    val authEvents: Flow<AuthEvent> = _authEvents.asSharedFlow()

    // ========== TelegramAuthClient Implementation ==========

    override suspend fun ensureAuthorized() {
        UnifiedLog.d(TAG, "ensureAuthorized() - starting auth flow")

        startAuthCollectorIfNeeded()

        // Get initial state
        val initialResult = client.getAuthorizationState()
        when (initialResult) {
            is TdlResult.Success -> {
                val state = initialResult.result
                currentState = state
                updateAuthState(state)
                handleAuthState(state)

                // If already ready, we're done
                if (state is AuthorizationStateReady) {
                    UnifiedLog.i(TAG, "Already authorized - Ready ✅")
                    return
                }

                // Wait for ready state with timeout
                waitForReady()
            }
            is TdlResult.Failure -> {
                val error = "Auth check failed: ${initialResult.code} - ${initialResult.message}"
                _authState.value = TdlibAuthState.Error(error)
                UnifiedLog.e(TAG, error)
                throw TelegramAuthException(error)
            }
        }
    }

    override suspend fun isAuthorized(): Boolean {
        return try {
            val result = client.getAuthorizationState()
            result is TdlResult.Success && result.result is AuthorizationStateReady
        } catch (e: Exception) {
            UnifiedLog.w(TAG, "isAuthorized check failed: ${e.message}")
            false
        }
    }

    override suspend fun sendPhoneNumber(phoneNumber: String) {
        UnifiedLog.d(TAG, "Sending phone number...")
        TelegramRetry.executeWithRetry(
                config = RetryConfig.AUTH,
                operationName = "sendPhoneNumber",
        ) {
            val settings =
                    PhoneNumberAuthenticationSettings(
                            allowFlashCall = false,
                            allowMissedCall = false,
                            allowSmsRetrieverApi = false,
                            hasUnknownPhoneNumber = false,
                            isCurrentPhoneNumber = false,
                            firebaseAuthenticationSettings = null,
                            authenticationTokens = emptyArray(),
                    )
            client.setAuthenticationPhoneNumber(phoneNumber, settings).getOrThrow()
            UnifiedLog.d(TAG, "Phone number submitted successfully")
        }
    }

    override suspend fun sendCode(code: String) {
        UnifiedLog.d(TAG, "Sending verification code...")
        TelegramRetry.executeWithRetry(
                config = RetryConfig.QUICK,
                operationName = "sendCode",
        ) {
            client.checkAuthenticationCode(code).getOrThrow()
            UnifiedLog.d(TAG, "Code submitted successfully")
        }
    }

    override suspend fun sendPassword(password: String) {
        UnifiedLog.d(TAG, "Sending 2FA password...")
        TelegramRetry.executeWithRetry(
                config = RetryConfig.QUICK,
                operationName = "sendPassword",
        ) {
            client.checkAuthenticationPassword(password).getOrThrow()
            UnifiedLog.d(TAG, "Password submitted successfully")
        }
    }

    override suspend fun logout() {
        UnifiedLog.d(TAG, "Logging out...")
        try {
            client.logOut().getOrThrow()
            _authState.value = TdlibAuthState.LoggedOut
        } catch (e: Exception) {
            UnifiedLog.e(TAG, "Error during logout: ${e.message}")
            throw TelegramAuthException("Logout failed: ${e.message}", e)
        }
    }

    override suspend fun getCurrentUserId(): Long? {
        return try {
            val me = client.getMe()
            when (me) {
                is TdlResult.Success -> {
                    val userId = me.result.id
                    UnifiedLog.d(TAG, "getCurrentUserId: $userId")
                    userId
                }
                is TdlResult.Failure -> {
                    UnifiedLog.w(TAG, "getMe failed: ${me.code} - ${me.message}")
                    null
                }
            }
        } catch (e: Exception) {
            UnifiedLog.w(TAG, "getCurrentUserId error: ${e.message}")
            null
        }
    }

    // ========== Internal Methods ==========

    private suspend fun waitForReady() {
        try {
            withTimeout(LOGIN_TIMEOUT_MS) {
                while (true) {
                    when (val s = currentState) {
                        is AuthorizationStateReady -> {
                            UnifiedLog.i(TAG, "Authorization complete - Ready ✅")
                            _authEvents.emit(AuthEvent.Ready)
                            return@withTimeout
                        }
                        is AuthorizationStateClosing,
                        is AuthorizationStateClosed,
                        is AuthorizationStateLoggingOut -> {
                            val error = "Fatal auth state: ${s::class.simpleName}"
                            UnifiedLog.e(TAG, error)
                            _authEvents.emit(AuthEvent.Error(error))
                            throw TelegramAuthException(error)
                        }
                        else -> delay(200)
                    }
                }
            }
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            val error = "Login timeout - no response from TDLib after 5 minutes"
            UnifiedLog.e(TAG, error)
            _authEvents.emit(AuthEvent.Error(error))
            throw TelegramAuthException(error, e)
        }
    }

    private fun startAuthCollectorIfNeeded() {
        if (!collectorStarted.compareAndSet(false, true)) return

        UnifiedLog.d(TAG, "Starting auth state flow collector...")

        scope.launch {
            try {
                client.authorizationStateUpdates.collect { update ->
                    val state = update.authorizationState
                    val previousStateName = previousState?.let { it::class.simpleName } ?: "None"
                    val currentStateName = state::class.simpleName

                    UnifiedLog.d(TAG, "Auth state: $previousStateName → $currentStateName")

                    previousState = currentState
                    currentState = state

                    updateAuthState(state)
                    _authEvents.emit(AuthEvent.StateChanged(state))

                    // Handle automatic state transitions
                    handleAuthState(state)

                    // Detect reauth requirement (Ready → WaitPhone/WaitCode/WaitPassword)
                    if (previousState is AuthorizationStateReady && needsReauth(state)) {
                        val reason = "Auth state changed from Ready to $currentStateName"
                        UnifiedLog.w(TAG, "Reauth required: $reason")
                        _authEvents.emit(AuthEvent.ReauthRequired(reason))
                    }
                }
            } catch (e: Exception) {
                UnifiedLog.e(TAG, "Auth collector error: ${e.message}")
                _authEvents.emit(AuthEvent.Error(e.message ?: "Unknown error"))
            }
        }
    }

    private fun needsReauth(state: AuthorizationState): Boolean {
        return state is AuthorizationStateWaitPhoneNumber ||
                state is AuthorizationStateWaitCode ||
                state is AuthorizationStateWaitPassword
    }

    private suspend fun handleAuthState(state: AuthorizationState) {
        when (state) {
            is AuthorizationStateWaitTdlibParameters -> {
                if (tdParamsSet.compareAndSet(false, true)) {
                    UnifiedLog.d(TAG, "Setting TDLib parameters...")
                    setTdlibParameters()
                }
            }
            is AuthorizationStateWaitPhoneNumber -> {
                // Config may have phone number for auto-submission
                config.phoneNumber?.let { phone ->
                    if (phone.isNotBlank()) {
                        UnifiedLog.d(TAG, "Auto-submitting phone from config...")
                        try {
                            sendPhoneNumber(phone)
                        } catch (e: Exception) {
                            UnifiedLog.w(TAG, "Auto phone submission failed: ${e.message}")
                        }
                    }
                }
            }
            else -> {
                /* No automatic handling */
            }
        }
    }

    private suspend fun setTdlibParameters() {
        val result = client.setTdlibParameters(
            useTestDc = false,
            databaseDirectory = config.databasePath,
            filesDirectory = config.filesPath,
            databaseEncryptionKey = ByteArray(0),
            useFileDatabase = true,
            useChatInfoDatabase = true,
            useMessageDatabase = true,
            useSecretChats = false,
            apiId = config.apiId,
            apiHash = config.apiHash,
            systemLanguageCode = "en",
            deviceModel = config.deviceModel,
            systemVersion = config.systemVersion,
            applicationVersion = config.appVersion,
        )

        when (result) {
            is TdlResult.Success -> UnifiedLog.d(TAG, "TDLib parameters set successfully")
            is TdlResult.Failure -> {
                val error = "Failed to set TDLib parameters: ${result.code} - ${result.message}"
                UnifiedLog.e(TAG, error)
                throw TelegramAuthException(error)
            }
        }
    }

    private fun updateAuthState(state: AuthorizationState) {
        _authState.value =
                when (state) {
                    is AuthorizationStateWaitTdlibParameters -> TdlibAuthState.Connecting
                    is AuthorizationStateWaitPhoneNumber -> TdlibAuthState.WaitPhoneNumber()
                    is AuthorizationStateWaitCode -> {
                        // Note: codeInfo.type doesn't directly expose length in g00sha256 wrapper
                        // We use null and let UI request code without length hint
                        TdlibAuthState.WaitCode(codeLength = null)
                    }
                    is AuthorizationStateWaitPassword -> {
                        TdlibAuthState.WaitPassword(
                            passwordHint = state.passwordHint,
                            hasRecoveryEmail = state.hasRecoveryEmailAddress
                        )
                    }
                    is AuthorizationStateReady -> TdlibAuthState.Ready
                    is AuthorizationStateLoggingOut -> TdlibAuthState.LoggingOut
                    is AuthorizationStateClosed -> TdlibAuthState.Closed
                    else -> TdlibAuthState.Idle
                }
    }
}

/** Extension function to convert TdlResult to value or throw exception. */
private fun <T> TdlResult<T>.getOrThrow(): T =
        when (this) {
            is TdlResult.Success -> result
            is TdlResult.Failure -> throw RuntimeException("TDLib error $code: $message")
        }

/** Authentication state events emitted during login flow. */
sealed class AuthEvent {
    /** Auth state changed to a new TDLib state */
    data class StateChanged(val state: AuthorizationState) : AuthEvent()

    /** Error occurred during auth */
    data class Error(val message: String, val code: Int? = null) : AuthEvent()

    /** Successfully authorized and ready */
    data object Ready : AuthEvent()

    /** Reauth required (was Ready, now needs login again) */
    data class ReauthRequired(val reason: String) : AuthEvent()
}
