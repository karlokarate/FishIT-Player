package com.fishit.player.feature.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

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
    // TODO: Inject TelegramTransportClient when ready
    // TODO: Inject XtreamApiClient when ready
) : ViewModel() {

    private val _state = MutableStateFlow(OnboardingState())
    val state: StateFlow<OnboardingState> = _state.asStateFlow()

    // ═══════════════════════════════════════════════════════════════════
    // Telegram Login Flow
    // ═══════════════════════════════════════════════════════════════════

    fun startTelegramLogin() {
        _state.update {
            it.copy(
                telegramState = TelegramAuthState.WaitingForPhoneNumber,
                telegramError = null
            )
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
            _state.update { it.copy(telegramState = TelegramAuthState.SendingPhone) }

            // TODO: Call TelegramTransportClient.login(phone)
            // For now, simulate transition to code entry
            kotlinx.coroutines.delay(1000)

            _state.update {
                it.copy(
                    telegramState = TelegramAuthState.WaitingForCode,
                    telegramError = null
                )
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
            _state.update { it.copy(telegramState = TelegramAuthState.SendingCode) }

            // TODO: Call TelegramTransportClient.login(code)
            // For now, simulate success
            kotlinx.coroutines.delay(1000)

            _state.update {
                it.copy(
                    telegramState = TelegramAuthState.Connected,
                    telegramError = null,
                    canContinue = true
                )
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
            _state.update { it.copy(telegramState = TelegramAuthState.SendingPassword) }

            // TODO: Call TelegramTransportClient.login(password)
            kotlinx.coroutines.delay(1000)

            _state.update {
                it.copy(
                    telegramState = TelegramAuthState.Connected,
                    telegramError = null,
                    canContinue = true
                )
            }
        }
    }

    fun disconnectTelegram() {
        viewModelScope.launch {
            // TODO: Call TelegramTransportClient.logout()
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
            _state.update { it.copy(xtreamState = XtreamConnectionState.Connecting) }

            // TODO: Call XtreamApiClient.connect(credentials)
            // For now, simulate connection
            kotlinx.coroutines.delay(1500)

            _state.update {
                it.copy(
                    xtreamState = XtreamConnectionState.Connected,
                    xtreamError = null,
                    canContinue = true
                )
            }
        }
    }

    fun disconnectXtream() {
        _state.update {
            it.copy(
                xtreamState = XtreamConnectionState.Disconnected,
                xtreamUrl = ""
            )
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
            val telegramConnected = state.telegramState == TelegramAuthState.Connected
            val xtreamConnected = state.xtreamState == XtreamConnectionState.Connected
            state.copy(canContinue = telegramConnected || xtreamConnected)
        }
    }
}
