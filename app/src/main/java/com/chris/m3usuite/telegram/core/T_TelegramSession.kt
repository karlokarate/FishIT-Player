package com.chris.m3usuite.telegram.core

import com.chris.m3usuite.telegram.config.AppConfig
import com.chris.m3usuite.telegram.logging.TelegramLogRepository
import dev.g000sha256.tdl.TdlClient
import dev.g000sha256.tdl.TdlResult
import dev.g000sha256.tdl.dto.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Extension function to convert TdlResult to value or throw exception.
 * @JvmSynthetic prevents KAPT from generating Java stubs for this function
 * (avoids stub generation issues with R8-minified TdlResult generics)
 */
@JvmSynthetic
fun <T> TdlResult<T>.getOrThrow(): T =
    when (this) {
        is TdlResult.Success -> result
        is TdlResult.Failure -> throw RuntimeException("TDLib error $code: $message")
    }

/**
 * Authentication state events emitted during login flow.
 */
sealed class AuthEvent {
    data class StateChanged(
        val state: AuthorizationState,
    ) : AuthEvent()

    data class Error(
        val message: String,
        val code: Int? = null,
    ) : AuthEvent()

    object Ready : AuthEvent()

    data class ReauthRequired(
        val reason: String,
    ) : AuthEvent()
}

/**
 * Manages TDLib authentication session with Flow-based events.
 *
 * This class DOES NOT create its own TdlClient - it receives an injected client
 * from T_TelegramServiceClient. This ensures single TdlClient instance per process.
 *
 * Key responsibilities:
 * - Complete auth loop (phone → code → password → ready)
 * - AuthorizationState → AuthEvent mapping
 * - Automatic TdlibParameters setup
 * - Phone number auto-submission from config
 * - Manual code/password submission from UI
 * - Robust error handling with retries
 * - Auth event streaming via SharedFlow
 *
 * All operations run in the ServiceClient's scope.
 */
class T_TelegramSession(
    val client: TdlClient,
    val config: AppConfig,
    private val scope: CoroutineScope,
) {
    companion object {
        private const val TAG = "TelegramSession"
    }

    @Volatile
    private var currentState: AuthorizationState? = null

    @Volatile
    private var previousState: AuthorizationState? = null

    private val collectorStarted = AtomicBoolean(false)
    private val tdParamsSet = AtomicBoolean(false)

    private val _authEvents = MutableSharedFlow<AuthEvent>(replay = 1)
    val authEvents: SharedFlow<AuthEvent> = _authEvents.asSharedFlow()

    /**
     * Start the login flow.
     *
     * This will:
     * 1. Monitor authorization state updates
     * 2. Automatically handle TdlibParameters setup
     * 3. Automatically submit phone number from config
     * 4. Wait for user to submit code/password via sendCode() or sendPassword()
     * 5. Emit Ready event when authentication complete
     *
     * For interactive steps (code, password), the caller must:
     * - Listen to authEvents for state changes
     * - Call sendCode() or sendPassword() when appropriate
     *
     * @throws TimeoutCancellationException if authentication doesn't complete within 5 minutes
     */
    suspend fun login() {
        TelegramLogRepository.debug("T_TelegramSession", " Login flow starting...")

        startAuthCollectorIfNeeded()

        // Get initial state for debugging
        val initial = client.getAuthorizationState().getOrThrow()
        TelegramLogRepository.debug("T_TelegramSession", " Initial state: ${initial::class.simpleName}")
        currentState = initial

        // Wait for ready state or handle initial state
        handleAuthState(initial)

        // Wait loop for ready state with timeout (5 minutes)
        try {
            withTimeout(300_000L) {
                while (true) {
                    when (val s = currentState) {
                        is AuthorizationStateReady -> {
                            TelegramLogRepository.debug("T_TelegramSession", " Ready ✅")
                            _authEvents.emit(AuthEvent.Ready)
                            return@withTimeout
                        }

                        is AuthorizationStateClosing,
                        is AuthorizationStateClosed,
                        is AuthorizationStateLoggingOut,
                        -> {
                            val error = "Fatal state: ${s::class.simpleName}"
                            TelegramLogRepository.debug("T_TelegramSession", " $error")
                            _authEvents.emit(AuthEvent.Error(error))
                            throw RuntimeException(error)
                        }

                        else -> {
                            delay(200)
                        }
                    }
                }
            }
        } catch (e: TimeoutCancellationException) {
            val error = "Login timeout - no response from TDLib after 5 minutes"
            TelegramLogRepository.debug("T_TelegramSession", " $error")
            _authEvents.emit(AuthEvent.Error(error))
            throw RuntimeException(error, e)
        }
    }

    /**
     * Send phone number for authentication.
     * Call this when in AuthorizationStateWaitPhoneNumber.
     *
     * @param phoneNumber Phone number in international format (e.g., +491234567890)
     * @param retries Number of retry attempts on failure (default 3)
     */
    suspend fun sendPhoneNumber(
        phoneNumber: String,
        retries: Int = 3,
    ) {
        TelegramLogRepository.debug("T_TelegramSession", " Sending phone number...")
        var lastError: Exception? = null

        repeat(retries) { attempt ->
            try {
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
                TelegramLogRepository.debug("T_TelegramSession", " Phone number submitted successfully")
                return // Success, exit
            } catch (e: Exception) {
                lastError = e
                TelegramLogRepository.debug("T_TelegramSession", " Error sending phone (attempt ${attempt + 1}/$retries): ${e.message}")

                if (attempt < retries - 1) {
                    delay(1000L * (attempt + 1)) // Exponential backoff
                }
            }
        }

        // All retries failed
        val errorMsg = "Failed to send phone number after $retries attempts: ${lastError?.message}"
        TelegramLogRepository.debug("T_TelegramSession", " $errorMsg")
        _authEvents.emit(AuthEvent.Error(errorMsg))
        throw lastError ?: Exception(errorMsg)
    }

    /**
     * Send authentication code received via SMS or Telegram.
     * Call this when in AuthorizationStateWaitCode.
     *
     * @param code Verification code
     * @param retries Number of retry attempts on failure (default 2)
     */
    suspend fun sendCode(
        code: String,
        retries: Int = 2,
    ) {
        TelegramLogRepository.debug("T_TelegramSession", " Sending code...")
        var lastError: Exception? = null

        repeat(retries) { attempt ->
            try {
                client.checkAuthenticationCode(code).getOrThrow()
                TelegramLogRepository.debug("T_TelegramSession", " Code submitted successfully")
                return // Success, exit
            } catch (e: Exception) {
                lastError = e
                TelegramLogRepository.debug("T_TelegramSession", " Error sending code (attempt ${attempt + 1}/$retries): ${e.message}")

                if (attempt < retries - 1) {
                    delay(500L)
                }
            }
        }

        // All retries failed
        val errorMsg = "Failed to send code after $retries attempts: ${lastError?.message}"
        TelegramLogRepository.debug("T_TelegramSession", " $errorMsg")
        _authEvents.emit(AuthEvent.Error(errorMsg))
        throw lastError ?: Exception(errorMsg)
    }

    /**
     * Send 2FA password.
     * Call this when in AuthorizationStateWaitPassword.
     *
     * @param password 2FA password
     * @param retries Number of retry attempts on failure (default 2)
     */
    suspend fun sendPassword(
        password: String,
        retries: Int = 2,
    ) {
        TelegramLogRepository.debug("T_TelegramSession", " Sending password...")
        var lastError: Exception? = null

        repeat(retries) { attempt ->
            try {
                client.checkAuthenticationPassword(password).getOrThrow()
                TelegramLogRepository.debug("T_TelegramSession", " Password submitted successfully")
                return // Success, exit
            } catch (e: Exception) {
                lastError = e
                TelegramLogRepository.debug("T_TelegramSession", " Error sending password (attempt ${attempt + 1}/$retries): ${e.message}")

                if (attempt < retries - 1) {
                    delay(500L)
                }
            }
        }

        // All retries failed
        val errorMsg = "Failed to send password after $retries attempts: ${lastError?.message}"
        TelegramLogRepository.debug("T_TelegramSession", " $errorMsg")
        _authEvents.emit(AuthEvent.Error(errorMsg))
        throw lastError ?: Exception(errorMsg)
    }

    /**
     * Log out from Telegram.
     */
    suspend fun logout() {
        TelegramLogRepository.debug("T_TelegramSession", " Logging out...")
        try {
            client.logOut().getOrThrow()
        } catch (e: Exception) {
            TelegramLogRepository.debug("T_TelegramSession", " Error during logout: ${e.message}")
            throw e
        }
    }

    /**
     * Start collecting authorization state updates from TDLib.
     * This runs in the ServiceClient's scope.
     */
    private fun startAuthCollectorIfNeeded() {
        if (!collectorStarted.compareAndSet(false, true)) return

        TelegramLogRepository.debug("T_TelegramSession", " Starting auth state flow collector...")

        scope.launch {
            try {
                client.authorizationStateUpdates.collect { update ->
                    val state = update.authorizationState
                    TelegramLogRepository.debug("T_TelegramSession", " State update: ${state::class.simpleName}")

                    // Detect reauth requirement: if we were Ready and now need phone/code/password
                    if (previousState is AuthorizationStateReady) {
                        when (state) {
                            is AuthorizationStateWaitPhoneNumber,
                            is AuthorizationStateWaitCode,
                            is AuthorizationStateWaitPassword,
                            -> {
                                TelegramLogRepository.debug(
                                    "T_TelegramSession",
                                    " Reauth required: was Ready, now ${state::class.simpleName}",
                                )
                                _authEvents.emit(
                                    AuthEvent.ReauthRequired("Telegram session expired, please login again"),
                                )
                            }
                            else -> {}
                        }
                    }

                    previousState = currentState
                    currentState = state
                    _authEvents.emit(AuthEvent.StateChanged(state))
                    handleAuthState(state)
                }
            } catch (t: Throwable) {
                TelegramLogRepository.debug("T_TelegramSession", " Error in auth flow: ${t.message}")
                t.printStackTrace()
                _authEvents.emit(AuthEvent.Error("Auth flow error: ${t.message}"))
            }
        }
    }

    /**
     * Handle specific authorization states with automatic actions where appropriate.
     */
    private suspend fun handleAuthState(state: AuthorizationState) {
        when (state) {
            is AuthorizationStateWaitTdlibParameters -> onWaitTdlibParameters()
            is AuthorizationStateWaitPhoneNumber -> onWaitPhoneNumber()
            is AuthorizationStateWaitCode -> onWaitCode()
            is AuthorizationStateWaitPassword -> onWaitPassword()
            is AuthorizationStateReady -> {
                TelegramLogRepository.debug("T_TelegramSession", " Ready state received")
            }
            is AuthorizationStateLoggingOut,
            is AuthorizationStateClosing,
            is AuthorizationStateClosed,
            -> {
                TelegramLogRepository.debug("T_TelegramSession", " Terminal state: ${state::class.simpleName}")
            }
            else -> {
                TelegramLogRepository.debug("T_TelegramSession", " Unhandled state: ${state::class.simpleName}")
            }
        }
    }

    /**
     * Handle TdlibParameters state - automatically set parameters.
     */
    private suspend fun onWaitTdlibParameters() {
        TelegramLogRepository.debug("T_TelegramSession", " Setting TdlibParameters...")

        if (!tdParamsSet.compareAndSet(false, true)) {
            TelegramLogRepository.debug("T_TelegramSession", " TdlibParameters already set, skipping")
            return
        }

        try {
            client
                .setTdlibParameters(
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
                    applicationVersion = "FishIT-Player-1.0",
                ).getOrThrow()

            TelegramLogRepository.debug("T_TelegramSession", " TdlibParameters set successfully")
        } catch (e: Exception) {
            TelegramLogRepository.debug("T_TelegramSession", " Error setting parameters: ${e.message}")
            _authEvents.emit(AuthEvent.Error("Failed to set parameters: ${e.message}"))
            throw e
        }
    }

    /**
     * Handle phone number state - automatically send from config if available.
     */
    private suspend fun onWaitPhoneNumber() {
        TelegramLogRepository.debug("T_TelegramSession", " → AuthorizationStateWaitPhoneNumber")

        // Only auto-send if phone number is provided in config
        if (config.phoneNumber.isNotBlank()) {
            try {
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

                client.setAuthenticationPhoneNumber(config.phoneNumber, settings).getOrThrow()
                TelegramLogRepository.debug("T_TelegramSession", " Phone number auto-submitted: ${config.phoneNumber}")
            } catch (e: Exception) {
                TelegramLogRepository.debug("T_TelegramSession", " Error auto-submitting phone: ${e.message}")
                _authEvents.emit(AuthEvent.Error("Failed to submit phone number: ${e.message}"))
            }
        } else {
            TelegramLogRepository.debug("T_TelegramSession", " Waiting for phone number from UI...")
        }
    }

    /**
     * Handle code state - wait for UI to call sendCode().
     */
    private suspend fun onWaitCode() {
        TelegramLogRepository.debug("T_TelegramSession", " Waiting for authentication code from UI...")
        // UI must call sendCode() when user enters the code
    }

    /**
     * Handle password state - wait for UI to call sendPassword().
     */
    private suspend fun onWaitPassword() {
        TelegramLogRepository.debug("T_TelegramSession", " Waiting for 2FA password from UI...")
        // UI must call sendPassword() when user enters the password
    }
}
