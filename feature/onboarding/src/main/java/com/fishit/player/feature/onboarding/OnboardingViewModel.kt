package com.fishit.player.feature.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fishit.player.infra.logging.UnifiedLog
import com.fishit.player.infra.transport.telegram.TelegramAuthClient
import com.fishit.player.infra.transport.telegram.api.TelegramAuthState as TransportTelegramAuthState
import com.fishit.player.infra.transport.xtream.XtreamApiClient
import com.fishit.player.infra.transport.xtream.XtreamApiConfig
import com.fishit.player.infra.transport.xtream.XtreamAuthState
import com.fishit.player.infra.transport.xtream.XtreamConnectionState as TransportXtreamConnectionState
import com.fishit.player.infra.transport.xtream.XtreamError
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * State for the onboarding/start screen
 */
data class OnboardingState(
    // Telegram auth state
    val telegramState: TelegramAuthState = TelegramAuthState.Disconnected,
    val telegramPhoneNumber: String = "",
    val telegramCode: String = "",
    val telegramPassword: String = "",
    val telegramError: String? = null,

    // Xtream state
    val xtreamState: XtreamConnectionState = XtreamConnectionState.Disconnected,
    val xtreamUrl: String = "",
    val xtreamError: String? = null,

    // General
    val canContinue: Boolean = false
)

/**
 * Telegram authentication states
 */
sealed class TelegramAuthState {
    data object Disconnected : TelegramAuthState()
    data object WaitingForPhoneNumber : TelegramAuthState()
    data object SendingPhone : TelegramAuthState()
    data object WaitingForCode : TelegramAuthState()
    data object SendingCode : TelegramAuthState()
    data object WaitingForPassword : TelegramAuthState()
    data object SendingPassword : TelegramAuthState()
    data object Connected : TelegramAuthState()
    data class Error(val message: String) : TelegramAuthState()
}

/**
 * Xtream connection states
 */
sealed class XtreamConnectionState {
    data object Disconnected : XtreamConnectionState()
    data object Connecting : XtreamConnectionState()
    data object Connected : XtreamConnectionState()
    data class Error(val message: String) : XtreamConnectionState()
}

/**
 * Parsed Xtream credentials from URL
 */
data class XtreamCredentials(
    val host: String,
    val port: Int,
    val username: String,
    val password: String,
    val useHttps: Boolean
)

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val telegramAuthClient: TelegramAuthClient,
    private val xtreamApiClient: XtreamApiClient,
) : ViewModel() {

    private companion object {
        private const val TAG = "OnboardingVM"
    }

    private val _state = MutableStateFlow(OnboardingState())
    val state: StateFlow<OnboardingState> = _state.asStateFlow()

    private var lastTelegramAuthState: TransportTelegramAuthState = TransportTelegramAuthState.Idle
    private var lastXtreamConnectionState: TransportXtreamConnectionState =
        TransportXtreamConnectionState.Disconnected

    init {
        observeTelegramAuth()
        observeXtreamConnection()
        observeXtreamAuth()
    }

    // ═══════════════════════════════════════════════════════════════════
    // Telegram Login Flow
    // ═══════════════════════════════════════════════════════════════════

    fun startTelegramLogin() {
        _state.update { it.copy(telegramError = null) }
        viewModelScope.launch {
            UnifiedLog.i(TAG) { "Starting Telegram auth" }
            runCatching { telegramAuthClient.ensureAuthorized() }
                .onFailure { error ->
                    UnifiedLog.e(TAG, error) { "Telegram ensureAuthorized failed" }
                    _state.update { it.copy(telegramError = error.message) }
                }
        }
    }

    fun updatePhoneNumber(phone: String) {
        _state.update { it.copy(telegramPhoneNumber = phone) }
    }

    fun submitPhoneNumber() {
        val phone = _state.value.telegramPhoneNumber
        if (phone.isBlank()) {
            _state.update {
                it.copy(telegramError = "Please enter a phone number")
            }
            return
        }

        viewModelScope.launch {
            UnifiedLog.i(TAG) { "Submitting phone number" }
            runCatching { telegramAuthClient.sendPhoneNumber(phone) }
                .onFailure { error ->
                    UnifiedLog.e(TAG, error) { "Failed to submit phone" }
                    _state.update { it.copy(telegramError = error.message) }
                }
        }
    }

    fun updateCode(code: String) {
        _state.update { it.copy(telegramCode = code) }
    }

    fun submitCode() {
        val code = _state.value.telegramCode
        if (code.isBlank()) {
            _state.update {
                it.copy(telegramError = "Please enter the verification code")
            }
            return
        }

        viewModelScope.launch {
            UnifiedLog.i(TAG) { "Submitting verification code" }
            runCatching { telegramAuthClient.sendCode(code) }
                .onFailure { error ->
                    UnifiedLog.e(TAG, error) { "Failed to submit code" }
                    _state.update { it.copy(telegramError = error.message) }
                }
        }
    }

    fun updatePassword(password: String) {
        _state.update { it.copy(telegramPassword = password) }
    }

    fun submitPassword() {
        val password = _state.value.telegramPassword
        if (password.isBlank()) {
            _state.update {
                it.copy(telegramError = "Please enter your 2FA password")
            }
            return
        }

        viewModelScope.launch {
            UnifiedLog.i(TAG) { "Submitting 2FA password" }
            runCatching { telegramAuthClient.sendPassword(password) }
                .onFailure { error ->
                    UnifiedLog.e(TAG, error) { "Failed to submit password" }
                    _state.update { it.copy(telegramError = error.message) }
                }
        }
    }

    fun disconnectTelegram() {
        viewModelScope.launch {
            UnifiedLog.i(TAG) { "Logging out of Telegram" }
            runCatching { telegramAuthClient.logout() }
                .onFailure { error ->
                    UnifiedLog.e(TAG, error) { "Logout failed" }
                    _state.update { it.copy(telegramError = error.message) }
                }
            _state.update {
                it.copy(
                    telegramState = TelegramAuthState.Disconnected,
                    telegramPhoneNumber = "",
                    telegramCode = "",
                    telegramPassword = ""
                )
            }
            updateCanContinue()
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // Xtream Login Flow
    // ═══════════════════════════════════════════════════════════════════

    fun updateXtreamUrl(url: String) {
        _state.update { it.copy(xtreamUrl = url, xtreamError = null) }
    }

    fun connectXtream() {
        val url = _state.value.xtreamUrl
        if (url.isBlank()) {
            _state.update {
                it.copy(xtreamError = "Please enter an Xtream URL")
            }
            return
        }

        // Parse credentials from URL
        val credentials = parseXtreamUrl(url)
        if (credentials == null) {
            _state.update {
                it.copy(xtreamError = "Invalid Xtream URL. Expected format: http://host:port/get.php?username=X&password=Y")
            }
            return
        }

        viewModelScope.launch {
            UnifiedLog.i(TAG) { "Connecting to Xtream host=${credentials.host}" }
            val config = XtreamApiConfig(
                scheme = if (credentials.useHttps) "https" else "http",
                host = credentials.host,
                port = credentials.port,
                username = credentials.username,
                password = credentials.password,
            )

            val result = xtreamApiClient.initialize(config)
            if (result.isFailure) {
                val message = result.exceptionOrNull()?.message ?: "Failed to connect"
                UnifiedLog.e(TAG) { "Xtream initialization failed: $message" }
                _state.update {
                    it.copy(
                        xtreamState = XtreamConnectionState.Error(message),
                        xtreamError = message,
                    )
                }
            }
        }
    }

    fun disconnectXtream() {
        _state.update {
            it.copy(
                xtreamState = XtreamConnectionState.Disconnected,
                xtreamUrl = "",
            )
        }
        viewModelScope.launch {
            UnifiedLog.i(TAG) { "Closing Xtream client" }
            runCatching { xtreamApiClient.close() }
                .onFailure { error -> UnifiedLog.e(TAG, error) { "Error closing Xtream" } }
        }
        updateCanContinue()
    }
    // ═══════════════════════════════════════════════════════════════════
    // URL Parsing (ported from v1)
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Parse Xtream credentials from a full URL.
     *
     * Supports formats:
     * - http://host:port/get.php?username=X&password=Y&type=m3u_plus
     * - http://host:port/player_api.php?username=X&password=Y
     * - http://username:password@host:port
     */
    fun parseXtreamUrl(url: String): XtreamCredentials? {
        return try {
            val trimmed = url.trim()

            // Check for userinfo format: http://user:pass@host:port
            val userinfoPattern = Regex("""^(https?)://([^:]+):([^@]+)@([^:/]+)(?::(\d+))?""")
            userinfoPattern.find(trimmed)?.let { match ->
                val (scheme, user, pass, host, portStr) = match.destructured
                return XtreamCredentials(
                    host = host,
                    port = portStr.toIntOrNull() ?: if (scheme == "https") 443 else 80,
                    username = user,
                    password = pass,
                    useHttps = scheme == "https"
                )
            }

            // Standard URL format with query params
            val uri = java.net.URI(trimmed)
            val host = uri.host ?: return null
            val port = if (uri.port > 0) uri.port else if (uri.scheme == "https") 443 else 80
            val useHttps = uri.scheme == "https"

            // Parse query parameters
            val queryParams = uri.query?.split("&")?.associate { param ->
                val parts = param.split("=", limit = 2)
                if (parts.size == 2) parts[0] to parts[1] else parts[0] to ""
            } ?: emptyMap()

            val username = queryParams["username"] ?: return null
            val password = queryParams["password"] ?: return null

            XtreamCredentials(
                host = host,
                port = port,
                username = username,
                password = password,
                useHttps = useHttps
            )
        } catch (e: Exception) {
            null
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // Helpers
    // ═══════════════════════════════════════════════════════════════════

    private fun updateCanContinue() {
        _state.update { state ->
            val telegramConnected = lastTelegramAuthState is TransportTelegramAuthState.Ready
            val xtreamConnected = lastXtreamConnectionState is TransportXtreamConnectionState.Connected
            state.copy(canContinue = telegramConnected || xtreamConnected)
        }
    }

    private fun observeTelegramAuth() {
        viewModelScope.launch {
            telegramAuthClient.authState.collectLatest { authState ->
                lastTelegramAuthState = authState
                val mappedState = when (authState) {
                    TransportTelegramAuthState.Idle,
                    TransportTelegramAuthState.LoggedOut,
                    TransportTelegramAuthState.Closed,
                    TransportTelegramAuthState.LoggingOut -> TelegramAuthState.Disconnected

                    TransportTelegramAuthState.Connecting,
                    TransportTelegramAuthState.WaitTdlibParameters,
                    TransportTelegramAuthState.WaitEncryptionKey -> TelegramAuthState.Disconnected

                    is TransportTelegramAuthState.WaitPhoneNumber ->
                        TelegramAuthState.WaitingForPhoneNumber

                    is TransportTelegramAuthState.WaitCode -> TelegramAuthState.WaitingForCode

                    is TransportTelegramAuthState.WaitPassword ->
                        TelegramAuthState.WaitingForPassword

                    TransportTelegramAuthState.Ready -> TelegramAuthState.Connected

                    is TransportTelegramAuthState.Error,
                    is TransportTelegramAuthState.Unknown -> TelegramAuthState.Error(
                        (authState as? TransportTelegramAuthState.Error)?.message
                            ?: (authState as? TransportTelegramAuthState.Unknown)?.raw
                            ?: "Unknown error"
                    )
                }

                _state.update {
                    val errorMessage = (mappedState as? TelegramAuthState.Error)?.message
                    it.copy(
                        telegramState = mappedState,
                        telegramError = errorMessage,
                    )
                }

                updateCanContinue()
            }
        }
    }

    private fun observeXtreamConnection() {
        viewModelScope.launch {
            xtreamApiClient.connectionState.collectLatest { connectionState ->
                lastXtreamConnectionState = connectionState
                val mappedState = when (connectionState) {
                    TransportXtreamConnectionState.Disconnected -> XtreamConnectionState.Disconnected
                    TransportXtreamConnectionState.Connecting -> XtreamConnectionState.Connecting
                    is TransportXtreamConnectionState.Connected -> XtreamConnectionState.Connected
                    is TransportXtreamConnectionState.Error -> XtreamConnectionState.Error(
                        formatXtreamError(connectionState.error)
                    )
                }

                _state.update {
                    it.copy(
                        xtreamState = mappedState,
                        xtreamError = (mappedState as? XtreamConnectionState.Error)?.message
                    )
                }

                updateCanContinue()
            }
        }
    }

    private fun observeXtreamAuth() {
        viewModelScope.launch {
            xtreamApiClient.authState.collectLatest { authState ->
                if (authState is XtreamAuthState.Failed) {
                    _state.update { it.copy(xtreamError = formatXtreamError(authState.error)) }
                }
            }
        }
    }

    private fun formatXtreamError(error: XtreamError): String {
        return when (error) {
            is XtreamError.Network -> error.message
            is XtreamError.Http -> "HTTP ${error.code}: ${error.message}"
            XtreamError.InvalidCredentials -> "Invalid credentials"
            is XtreamError.AccountExpired -> "Account expired"
            is XtreamError.ParseError -> error.message
            is XtreamError.Unsupported -> "Unsupported action ${error.action}"
            is XtreamError.RateLimited -> "Rate limited"
            is XtreamError.Unknown -> error.message
        }
    }
}
