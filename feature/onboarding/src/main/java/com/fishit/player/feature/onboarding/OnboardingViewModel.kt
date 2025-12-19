package com.fishit.player.feature.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fishit.player.core.feature.auth.TelegramAuthRepository
import com.fishit.player.core.feature.auth.TelegramAuthState
import com.fishit.player.feature.onboarding.domain.XtreamAuthRepository
import com.fishit.player.feature.onboarding.domain.XtreamAuthState as DomainXtreamAuthState
import com.fishit.player.feature.onboarding.domain.XtreamConfig
import com.fishit.player.feature.onboarding.domain.XtreamConnectionState as DomainXtreamConnectionState
import com.fishit.player.infra.logging.UnifiedLog
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
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
    val canContinue: Boolean = false,
)

/**
 * Xtream connection states (UI model)
 */
sealed class XtreamConnectionState {
    data object Disconnected : XtreamConnectionState()

    data object Connecting : XtreamConnectionState()

    data object Connected : XtreamConnectionState()

    data class Error(
        val message: String,
    ) : XtreamConnectionState()
}

/**
 * Parsed Xtream credentials from URL
 */
data class XtreamCredentials(
    val host: String,
    val port: Int,
    val username: String,
    val password: String,
    val useHttps: Boolean,
)

@HiltViewModel
class OnboardingViewModel
    @Inject
    constructor(
        private val telegramAuthRepository: TelegramAuthRepository,
        private val xtreamAuthRepository: XtreamAuthRepository,
    ) : ViewModel() {
        private val _state = MutableStateFlow(OnboardingState())
        val state: StateFlow<OnboardingState> = _state.asStateFlow()

        private var lastTelegramAuthState: TelegramAuthState = TelegramAuthState.Idle
        private var lastXtreamConnectionState: DomainXtreamConnectionState =
            DomainXtreamConnectionState.Disconnected

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
                runCatching { telegramAuthRepository.ensureAuthorized() }
                    .onFailure { error ->
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
                runCatching { telegramAuthRepository.sendPhoneNumber(phone) }
                    .onFailure { error ->
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
                runCatching { telegramAuthRepository.sendCode(code) }
                    .onFailure { error ->
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
                runCatching { telegramAuthRepository.sendPassword(password) }
                    .onFailure { error ->
                        _state.update { it.copy(telegramError = error.message) }
                    }
            }
        }

        fun disconnectTelegram() {
            viewModelScope.launch {
                runCatching { telegramAuthRepository.logout() }
                    .onFailure { error ->
                        _state.update { it.copy(telegramError = error.message) }
                    }
                _state.update {
                    it.copy(
                        telegramState = TelegramAuthState.Disconnected,
                        telegramPhoneNumber = "",
                        telegramCode = "",
                        telegramPassword = "",
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
            UnifiedLog.d(TAG) { "connectXtream: Starting with URL: $url" }
            
            if (url.isBlank()) {
                UnifiedLog.w(TAG) { "connectXtream: URL is blank" }
                _state.update {
                    it.copy(xtreamError = "Please enter an Xtream URL")
                }
                return
            }

            // Parse credentials from URL
            val credentials = parseXtreamUrl(url)
            if (credentials == null) {
                UnifiedLog.e(TAG) { "connectXtream: Failed to parse URL: $url" }
                _state.update {
                    it.copy(xtreamError = "Invalid Xtream URL. Expected format: http://host:port/get.php?username=X&password=Y")
                }
                return
            }

            UnifiedLog.d(TAG) { "connectXtream: Parsed credentials - host=${credentials.host}, port=${credentials.port}, username=${credentials.username}, useHttps=${credentials.useHttps}" }

            viewModelScope.launch {
                val config =
                    XtreamConfig(
                        scheme = if (credentials.useHttps) "https" else "http",
                        host = credentials.host,
                        port = credentials.port,
                        username = credentials.username,
                        password = credentials.password,
                    )

                UnifiedLog.d(TAG) { "connectXtream: Created config - scheme=${config.scheme}, host=${config.host}, port=${config.port}, username=${config.username}" }

                val result = xtreamAuthRepository.initialize(config)
                if (result.isFailure) {
                    val error = result.exceptionOrNull()
                    val message = error?.message ?: "Failed to connect"
                    UnifiedLog.e(TAG, error) { "connectXtream: Initialize failed with message: $message" }
                    _state.update {
                        it.copy(
                            xtreamState = XtreamConnectionState.Error(message),
                            xtreamError = message,
                        )
                    }
                } else {
                    UnifiedLog.d(TAG) { "connectXtream: Initialize succeeded, saving credentials" }
                    // Success - persist credentials for auto-rehydration
                    runCatching { xtreamAuthRepository.saveCredentials(config) }
                        .onFailure { error ->
                            UnifiedLog.e(TAG, error) { "connectXtream: Failed to save credentials" }
                            _state.update { it.copy(xtreamError = error.message) }
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
                runCatching { xtreamAuthRepository.close() }
                    .onFailure { error -> _state.update { it.copy(xtreamError = error.message) } }

                // Clear stored credentials
                runCatching { xtreamAuthRepository.clearCredentials() }
                    .onFailure { error -> _state.update { it.copy(xtreamError = error.message) } }
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
                        useHttps = scheme == "https",
                    )
                }

                // Standard URL format with query params
                val uri = java.net.URI(trimmed)
                val host = uri.host ?: return null
                val port =
                    if (uri.port > 0) {
                        uri.port
                    } else if (uri.scheme == "https") {
                        443
                    } else {
                        80
                    }
                val useHttps = uri.scheme == "https"

                // Parse query parameters
                val queryParams =
                    uri.query?.split("&")?.associate { param ->
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
                    useHttps = useHttps,
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
                val telegramConnected = lastTelegramAuthState is TelegramAuthState.Connected
                val xtreamConnected = lastXtreamConnectionState is DomainXtreamConnectionState.Connected
                state.copy(canContinue = telegramConnected || xtreamConnected)
            }
        }

        private fun observeTelegramAuth() {
            viewModelScope.launch {
                telegramAuthRepository.authState.collectLatest { authState ->
                    lastTelegramAuthState = authState
                    _state.update {
                        val errorMessage = (authState as? TelegramAuthState.Error)?.message
                        it.copy(
                            telegramState = authState,
                            telegramError = errorMessage,
                        )
                    }

                    updateCanContinue()
                }
            }
        }

        private fun observeXtreamConnection() {
            viewModelScope.launch {
                xtreamAuthRepository.connectionState.collectLatest { connectionState ->
                    lastXtreamConnectionState = connectionState
                    val mappedState =
                        when (connectionState) {
                            DomainXtreamConnectionState.Disconnected -> XtreamConnectionState.Disconnected
                            DomainXtreamConnectionState.Connecting -> XtreamConnectionState.Connecting
                            DomainXtreamConnectionState.Connected -> XtreamConnectionState.Connected
                            is DomainXtreamConnectionState.Error ->
                                XtreamConnectionState.Error(connectionState.message)
                        }

                    _state.update {
                        it.copy(
                            xtreamState = mappedState,
                            xtreamError = (mappedState as? XtreamConnectionState.Error)?.message,
                        )
                    }

                    updateCanContinue()
                }
            }
        }

        private fun observeXtreamAuth() {
            viewModelScope.launch {
                xtreamAuthRepository.authState.collectLatest { authState ->
                    when (authState) {
                        is DomainXtreamAuthState.Failed -> {
                            _state.update { it.copy(xtreamError = authState.message) }
                        }

                        is DomainXtreamAuthState.Expired -> {
                            val message =
                                authState.expDate?.let { expDate ->
                                    "Account expired (expires $expDate)"
                                } ?: "Account expired"
                            _state.update { it.copy(xtreamError = message) }
                        }

                        else -> Unit
                    }
                }
            }
        }

        companion object {
            private const val TAG = "OnboardingViewModel"
        }
    }
