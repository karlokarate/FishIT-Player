package com.chris.m3usuite.telegram.session

import com.chris.m3usuite.telegram.config.AppConfig
import dev.g000sha256.tdl.TdlClient
import dev.g000sha256.tdl.TdlResult
import dev.g000sha256.tdl.dto.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

/**
 * Extension function to convert TdlResult to value or throw exception.
 */
fun <T> TdlResult<T>.getOrThrow(): T = when (this) {
    is TdlResult.Success -> result
    is TdlResult.Failure -> throw RuntimeException("TDLib error $code: $message")
}

/**
 * Authentication state events emitted during login flow.
 */
sealed class AuthEvent {
    data class StateChanged(val state: AuthorizationState) : AuthEvent()
    data class Error(val message: String, val code: Int? = null) : AuthEvent()
    object Ready : AuthEvent()
}

/**
 * Manages a TDLib client session with Flow-based authentication.
 * Handles the complete login flow including phone number, code, and password steps.
 */
class TelegramSession(
    val client: TdlClient,
    val config: AppConfig,
    private val scope: CoroutineScope
) {

    @Volatile
    private var currentState: AuthorizationState? = null

    private var collectorStarted = false
    private var tdParamsSet = false

    private val _authEvents = MutableSharedFlow<AuthEvent>(replay = 1)
    val authEvents: SharedFlow<AuthEvent> = _authEvents.asSharedFlow()

    /**
     * Start the login flow. This will monitor authorization state updates
     * and automatically handle initial setup (TdlibParameters).
     * 
     * For interactive steps (phone, code, password), the caller must:
     * - Listen to authEvents for state changes
     * - Call sendPhoneNumber(), sendCode(), or sendPassword() accordingly
     */
    suspend fun login() {
        println("[TelegramSession] Login flow starting...")

        startAuthCollectorIfNeeded()

        // Get initial state for debugging
        val initial = client.getAuthorizationState().getOrThrow()
        println("[TelegramSession] Initial state: ${initial::class.simpleName}")
        currentState = initial

        // Wait for ready state
        while (true) {
            when (val s = currentState) {
                is AuthorizationStateReady -> {
                    println("[TelegramSession] Ready ✅")
                    _authEvents.emit(AuthEvent.Ready)
                    return
                }

                is AuthorizationStateClosing,
                is AuthorizationStateClosed,
                is AuthorizationStateLoggingOut -> {
                    val error = "Fatal state: ${s::class.simpleName}"
                    println("[TelegramSession] $error")
                    _authEvents.emit(AuthEvent.Error(error))
                    throw RuntimeException(error)
                }

                else -> {
                    delay(200)
                }
            }
        }
    }

    /**
     * Send phone number for authentication.
     * Call this when in AuthorizationStateWaitPhoneNumber.
     */
    suspend fun sendPhoneNumber(phoneNumber: String) {
        println("[TelegramSession] Sending phone number...")
        try {
            val settings = PhoneNumberAuthenticationSettings(
                allowFlashCall = false,
                allowMissedCall = false,
                allowSmsRetrieverApi = false,
                hasUnknownPhoneNumber = false,
                isCurrentPhoneNumber = false,
                firebaseAuthenticationSettings = null,
                authenticationTokens = emptyArray()
            )

            client.setAuthenticationPhoneNumber(phoneNumber, settings).getOrThrow()
            println("[TelegramSession] Phone number submitted")
        } catch (e: Exception) {
            println("[TelegramSession] Error sending phone: ${e.message}")
            _authEvents.emit(AuthEvent.Error("Failed to send phone number: ${e.message}"))
            throw e
        }
    }

    /**
     * Send authentication code received via SMS or Telegram.
     * Call this when in AuthorizationStateWaitCode.
     */
    suspend fun sendCode(code: String) {
        println("[TelegramSession] Sending code...")
        try {
            client.checkAuthenticationCode(code).getOrThrow()
            println("[TelegramSession] Code submitted")
        } catch (e: Exception) {
            println("[TelegramSession] Error sending code: ${e.message}")
            _authEvents.emit(AuthEvent.Error("Failed to send code: ${e.message}"))
            throw e
        }
    }

    /**
     * Send 2FA password.
     * Call this when in AuthorizationStateWaitPassword.
     */
    suspend fun sendPassword(password: String) {
        println("[TelegramSession] Sending password...")
        try {
            client.checkAuthenticationPassword(password).getOrThrow()
            println("[TelegramSession] Password submitted")
        } catch (e: Exception) {
            println("[TelegramSession] Error sending password: ${e.message}")
            _authEvents.emit(AuthEvent.Error("Failed to send password: ${e.message}"))
            throw e
        }
    }

    /**
     * Log out from Telegram.
     */
    suspend fun logout() {
        println("[TelegramSession] Logging out...")
        try {
            client.logOut().getOrThrow()
        } catch (e: Exception) {
            println("[TelegramSession] Error during logout: ${e.message}")
        }
    }

    private fun startAuthCollectorIfNeeded() {
        if (collectorStarted) return
        collectorStarted = true

        println("[TelegramSession] Starting auth state flow collector...")

        scope.launch {
            try {
                client.authorizationStateUpdates.collect { update ->
                    val state = update.authorizationState
                    println("[TelegramSession] State update: ${state::class.simpleName}")
                    currentState = state
                    _authEvents.emit(AuthEvent.StateChanged(state))
                    handleAuthState(state)
                }
            } catch (t: Throwable) {
                println("[TelegramSession] Error in auth flow: ${t.message}")
                t.printStackTrace()
                _authEvents.emit(AuthEvent.Error("Auth flow error: ${t.message}"))
            }
        }
    }

    private suspend fun handleAuthState(state: AuthorizationState) {
        when (state) {
            is AuthorizationStateWaitTdlibParameters -> onWaitTdlibParameters()
            is AuthorizationStateWaitPhoneNumber     -> onWaitPhoneNumber()
            is AuthorizationStateWaitCode            -> onWaitCode()
            is AuthorizationStateWaitPassword        -> onWaitPassword()
            is AuthorizationStateReady               -> {
                println("[TelegramSession] Ready state received")
            }
            is AuthorizationStateLoggingOut,
            is AuthorizationStateClosing,
            is AuthorizationStateClosed -> {
                println("[TelegramSession] Terminal state: ${state::class.simpleName}")
            }
            else -> {
                println("[TelegramSession] Unhandled state: ${state::class.simpleName}")
            }
        }
    }

    private suspend fun onWaitTdlibParameters() {
        println("[TelegramSession] Setting TdlibParameters...")

        if (tdParamsSet) {
            println("[TelegramSession] TdlibParameters already set, skipping")
            return
        }
        tdParamsSet = true

        try {
            client.setTdlibParameters(
                useTestDc = false,
                databaseDirectory = config.dbDir,
                filesDirectory = config.filesDir,
                databaseEncryptionKey = ByteArray(0),
                useFileDatabase = true,
                useChatInfoDatabase = true,
                useMessageDatabase = true,
                useSecretChats = false,
                apiId = config.apiId,
                apiHash = config.apiHash,
                systemLanguageCode = "de",
                deviceModel = android.os.Build.MODEL,
                systemVersion = "Android ${android.os.Build.VERSION.RELEASE}",
                applicationVersion = "FishIT-Player-1.0"
            ).getOrThrow()

            println("[TelegramSession] TdlibParameters set successfully")
        } catch (e: Exception) {
            println("[TelegramSession] Error setting parameters: ${e.message}")
            _authEvents.emit(AuthEvent.Error("Failed to set parameters: ${e.message}"))
            throw e
        }
    }

    private suspend fun onWaitPhoneNumber() {
        println("[TelegramSession] Waiting for phone number...")
        // UI should call sendPhoneNumber() when ready
    }

    private suspend fun onWaitCode() {
        println("[TelegramSession] Waiting for authentication code...")
        // UI should call sendCode() when ready
    }

    private suspend fun onWaitPassword() {
        println("[TelegramSession] Waiting for 2FA password...")
        // UI should call sendPassword() when ready
    }
}
