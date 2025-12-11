package com.fishit.player.feature.devtools

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fishit.player.infra.logging.UnifiedLog
import com.fishit.player.infra.transport.telegram.TdlibClientProvider
import com.fishit.player.infra.transport.telegram.TelegramAuthState
import com.fishit.player.infra.transport.telegram.TelegramTransportClient
import com.fishit.player.infra.transport.xtream.XtreamApiClient
import com.fishit.player.infra.transport.xtream.XtreamApiConfig
import com.fishit.player.infra.transport.xtream.XtreamDiscovery
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.g000sha256.tdl.TdlResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * DevTools ViewModel
 *
 * Manages state for Telegram and Xtream login flows in the Mini-UI.
 * 
 * **Contract Compliance:**
 * - Uses UnifiedLog for all logging (LOGGING_CONTRACT_V2)
 * - No metadata normalization in UI (MEDIA_NORMALIZATION_CONTRACT)
 * - Uses existing transport/infra layers correctly (v2 architecture)
 */
@HiltViewModel
class DevToolsViewModel @Inject constructor(
    private val telegramTransport: TelegramTransportClient,
    private val tdlibProvider: TdlibClientProvider,
    private val xtreamClient: XtreamApiClient,
    private val xtreamDiscovery: XtreamDiscovery,
) : ViewModel() {

    companion object {
        private const val TAG = "DevToolsVM"
    }

    // ========== Telegram State ==========

    private val _telegramAuthState = MutableStateFlow<TelegramAuthState>(TelegramAuthState.Idle)
    val telegramAuthState: StateFlow<TelegramAuthState> = _telegramAuthState.asStateFlow()

    private val _telegramError = MutableStateFlow<String?>(null)
    val telegramError: StateFlow<String?> = _telegramError.asStateFlow()

    // ========== Xtream State ==========

    private val _xtreamConfig = MutableStateFlow<XtreamApiConfig?>(null)
    val xtreamConfig: StateFlow<XtreamApiConfig?> = _xtreamConfig.asStateFlow()

    private val _xtreamStatus = MutableStateFlow<XtreamStatus>(XtreamStatus.NotConfigured)
    val xtreamStatus: StateFlow<XtreamStatus> = _xtreamStatus.asStateFlow()

    private val _xtreamError = MutableStateFlow<String?>(null)
    val xtreamError: StateFlow<String?> = _xtreamError.asStateFlow()

    // ========== Combined UI State ==========

    val uiState: StateFlow<DevToolsUiState> = combine(
        _telegramAuthState,
        _telegramError,
        _xtreamConfig,
        _xtreamStatus,
        _xtreamError
    ) { telegramAuth, telegramErr, xtreamCfg, xtreamStat, xtreamErr ->
        DevToolsUiState(
            telegramAuthState = telegramAuth,
            telegramError = telegramErr,
            xtreamConfig = xtreamCfg,
            xtreamStatus = xtreamStat,
            xtreamError = xtreamErr
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = DevToolsUiState()
    )

    init {
        UnifiedLog.d(TAG, "DevToolsViewModel initialized")
        
        // Observe Telegram auth state from transport
        viewModelScope.launch {
            telegramTransport.authState.collect { state ->
                _telegramAuthState.value = state
                UnifiedLog.d(TAG, "Telegram auth state: $state")
            }
        }
    }

    // ========== Telegram Actions ==========

    /**
     * Start Telegram authorization flow.
     */
    fun startTelegramAuth() {
        UnifiedLog.i(TAG, "startTelegramAuth()")
        viewModelScope.launch {
            try {
                _telegramError.value = null
                telegramTransport.ensureAuthorized()
            } catch (e: Exception) {
                val message = "Failed to start auth: ${e.message}"
                UnifiedLog.e(TAG, message, e)
                _telegramError.value = message
            }
        }
    }

    /**
     * Submit phone number for Telegram auth.
     */
    fun submitPhoneNumber(phoneNumber: String) {
        UnifiedLog.i(TAG, "submitPhoneNumber()")
        viewModelScope.launch {
            try {
                _telegramError.value = null
                val tdlClient = tdlibProvider.getClient()
                val result = tdlClient.setAuthenticationPhoneNumber(phoneNumber, null)
                when (result) {
                    is TdlResult.Success -> {
                        UnifiedLog.d(TAG, "Phone number submitted successfully")
                    }
                    is TdlResult.Failure -> {
                        val message = "Phone submission failed: ${result.code} - ${result.message}"
                        UnifiedLog.e(TAG, message)
                        _telegramError.value = message
                    }
                }
            } catch (e: Exception) {
                val message = "Phone submission error: ${e.message}"
                UnifiedLog.e(TAG, message, e)
                _telegramError.value = message
            }
        }
    }

    /**
     * Submit verification code for Telegram auth.
     */
    fun submitCode(code: String) {
        UnifiedLog.i(TAG, "submitCode()")
        viewModelScope.launch {
            try {
                _telegramError.value = null
                val tdlClient = tdlibProvider.getClient()
                val result = tdlClient.checkAuthenticationCode(code)
                when (result) {
                    is TdlResult.Success -> {
                        UnifiedLog.d(TAG, "Code submitted successfully")
                    }
                    is TdlResult.Failure -> {
                        val message = "Code verification failed: ${result.code} - ${result.message}"
                        UnifiedLog.e(TAG, message)
                        _telegramError.value = message
                    }
                }
            } catch (e: Exception) {
                val message = "Code submission error: ${e.message}"
                UnifiedLog.e(TAG, message, e)
                _telegramError.value = message
            }
        }
    }

    /**
     * Submit password for Telegram auth.
     */
    fun submitPassword(password: String) {
        UnifiedLog.i(TAG, "submitPassword()")
        viewModelScope.launch {
            try {
                _telegramError.value = null
                val tdlClient = tdlibProvider.getClient()
                val result = tdlClient.checkAuthenticationPassword(password)
                when (result) {
                    is TdlResult.Success -> {
                        UnifiedLog.d(TAG, "Password submitted successfully")
                    }
                    is TdlResult.Failure -> {
                        val message = "Password verification failed: ${result.code} - ${result.message}"
                        UnifiedLog.e(TAG, message)
                        _telegramError.value = message
                    }
                }
            } catch (e: Exception) {
                val message = "Password submission error: ${e.message}"
                UnifiedLog.e(TAG, message, e)
                _telegramError.value = message
            }
        }
    }

    /**
     * Clear Telegram error.
     */
    fun clearTelegramError() {
        _telegramError.value = null
    }

    // ========== Xtream Actions ==========

    /**
     * Parse and store Xtream configuration from a full URL.
     *
     * Supports URLs like:
     * - http://host:8080/get.php?username=USER&password=PASS&type=m3u
     * - http://host:8080/live/USER/PASS/12345.ts
     */
    fun parseXtreamUrl(url: String) {
        UnifiedLog.i(TAG, "parseXtreamUrl(url=${url.take(50)}...)")
        viewModelScope.launch {
            try {
                _xtreamError.value = null
                _xtreamStatus.value = XtreamStatus.Parsing
                
                // Try to parse using XtreamApiConfig helper
                val config = XtreamApiConfig.fromM3uUrl(url)
                
                if (config != null) {
                    _xtreamConfig.value = config
                    _xtreamStatus.value = XtreamStatus.Parsed
                    UnifiedLog.i(TAG, "Xtream URL parsed: host=${config.host}, user=${config.username}")
                    
                    // Optionally test connectivity
                    testXtreamConnectivity(config)
                } else {
                    // Try alternative parsing for live/VOD URLs
                    val altConfig = parseXtreamLiveUrl(url)
                    if (altConfig != null) {
                        _xtreamConfig.value = altConfig
                        _xtreamStatus.value = XtreamStatus.Parsed
                        UnifiedLog.i(TAG, "Xtream live URL parsed: host=${altConfig.host}, user=${altConfig.username}")
                        testXtreamConnectivity(altConfig)
                    } else {
                        val message = "Failed to parse Xtream URL. Expected format: http://host:port/get.php?username=USER&password=PASS or http://host:port/live/USER/PASS/..."
                        UnifiedLog.w(TAG, message)
                        _xtreamError.value = message
                        _xtreamStatus.value = XtreamStatus.Error
                    }
                }
            } catch (e: Exception) {
                val message = "Xtream URL parsing error: ${e.message}"
                UnifiedLog.e(TAG, message, e)
                _xtreamError.value = message
                _xtreamStatus.value = XtreamStatus.Error
            }
        }
    }

    /**
     * Parse Xtream live/VOD URLs like: http://host:port/live/USER/PASS/12345.ts
     */
    private fun parseXtreamLiveUrl(url: String): XtreamApiConfig? {
        return try {
            val uri = java.net.URI(url)
            val path = uri.path ?: return null
            val parts = path.split("/").filter { it.isNotBlank() }
            
            // Look for pattern: /live/USER/PASS/... or /movie/USER/PASS/...
            val liveIndex = parts.indexOfFirst { it.equals("live", ignoreCase = true) || 
                                                  it.equals("movie", ignoreCase = true) || 
                                                  it.equals("series", ignoreCase = true) }
            
            if (liveIndex >= 0 && parts.size >= liveIndex + 3) {
                val username = parts[liveIndex + 1]
                val password = parts[liveIndex + 2]
                val host = uri.host ?: return null
                val port = if (uri.port > 0) uri.port else null
                
                XtreamApiConfig(
                    scheme = uri.scheme ?: "http",
                    host = host,
                    port = port,
                    username = username,
                    password = password
                )
            } else {
                null
            }
        } catch (e: Exception) {
            UnifiedLog.w(TAG, "Failed to parse as live URL: ${e.message}")
            null
        }
    }

    /**
     * Test Xtream connectivity using discovery ping.
     */
    private suspend fun testXtreamConnectivity(config: XtreamApiConfig) {
        try {
            _xtreamStatus.value = XtreamStatus.Testing
            
            // Resolve port if needed
            val port = config.port ?: xtreamDiscovery.resolvePort(config)
            
            // Test connection
            val isReachable = xtreamDiscovery.ping(config, port)
            
            if (isReachable) {
                _xtreamStatus.value = XtreamStatus.Connected(port)
                UnifiedLog.i(TAG, "Xtream connectivity test passed: port=$port")
            } else {
                _xtreamStatus.value = XtreamStatus.Parsed
                _xtreamError.value = "Server not reachable"
                UnifiedLog.w(TAG, "Xtream connectivity test failed")
            }
        } catch (e: Exception) {
            _xtreamStatus.value = XtreamStatus.Parsed
            _xtreamError.value = "Connectivity test failed: ${e.message}"
            UnifiedLog.w(TAG, "Xtream connectivity test error: ${e.message}")
        }
    }

    /**
     * Clear Xtream configuration and reset state.
     */
    fun clearXtreamConfig() {
        UnifiedLog.i(TAG, "clearXtreamConfig()")
        _xtreamConfig.value = null
        _xtreamStatus.value = XtreamStatus.NotConfigured
        _xtreamError.value = null
    }

    /**
     * Clear Xtream error.
     */
    fun clearXtreamError() {
        _xtreamError.value = null
    }
}

// ========== UI State Models ==========

/**
 * Combined UI state for DevTools screen.
 */
data class DevToolsUiState(
    val telegramAuthState: TelegramAuthState = TelegramAuthState.Idle,
    val telegramError: String? = null,
    val xtreamConfig: XtreamApiConfig? = null,
    val xtreamStatus: XtreamStatus = XtreamStatus.NotConfigured,
    val xtreamError: String? = null,
)

/**
 * Xtream configuration status.
 */
sealed class XtreamStatus {
    data object NotConfigured : XtreamStatus()
    data object Parsing : XtreamStatus()
    data object Parsed : XtreamStatus()
    data object Testing : XtreamStatus()
    data class Connected(val port: Int) : XtreamStatus()
    data object Error : XtreamStatus()
}
