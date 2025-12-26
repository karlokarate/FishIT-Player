package com.fishit.player.infra.data.xtream

import com.fishit.player.core.sourceactivation.SourceActivationStore
import com.fishit.player.core.sourceactivation.SourceErrorReason
import com.fishit.player.feature.onboarding.domain.XtreamAuthRepository
import com.fishit.player.feature.onboarding.domain.XtreamAuthState as DomainAuthState
import com.fishit.player.feature.onboarding.domain.XtreamConfig as DomainConfig
import com.fishit.player.feature.onboarding.domain.XtreamConnectionState as DomainConnectionState
import com.fishit.player.infra.logging.UnifiedLog
import com.fishit.player.infra.transport.xtream.XtreamApiClient
import com.fishit.player.infra.transport.xtream.XtreamApiConfig
import com.fishit.player.infra.transport.xtream.XtreamAuthState as TransportAuthState
import com.fishit.player.infra.transport.xtream.XtreamConnectionState as TransportConnectionState
import com.fishit.player.infra.transport.xtream.XtreamCredentialsStore
import com.fishit.player.infra.transport.xtream.XtreamError
import com.fishit.player.infra.transport.xtream.XtreamStoredConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import javax.inject.Named
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Adapter implementation of [XtreamAuthRepository] for the onboarding feature.
 *
 * This adapter bridges the feature-owned domain interface to the transport layer:
 * - Converts between domain and transport models
 * - Delegates authentication to [XtreamApiClient]
 * - Persists credentials via [XtreamCredentialsStore]
 *
 * **Architecture Compliance:**
 * - Lives in infra/data-xtream (Data layer)
 * - Implements feature-owned interface (Dependency Inversion)
 * - Does NOT expose transport types to feature layer
 *
 * **Layer Flow:**
 * ```
 * feature/onboarding (Domain Interface)
 *        ↓
 * infra/data-xtream (This Adapter)
 *        ↓
 * infra/transport-xtream (Transport Client)
 * ```
 */
@Singleton
class XtreamAuthRepositoryAdapter @Inject constructor(
    private val apiClient: XtreamApiClient,
    private val credentialsStore: XtreamCredentialsStore,
    private val sourceActivationStore: SourceActivationStore,
    @Named("AppLifecycleScope") private val appScope: CoroutineScope,
) : XtreamAuthRepository {

    private val _connectionState = MutableStateFlow<DomainConnectionState>(DomainConnectionState.Disconnected)
    override val connectionState: StateFlow<DomainConnectionState> = _connectionState.asStateFlow()

    private val _authState = MutableStateFlow<DomainAuthState>(DomainAuthState.Idle)
    override val authState: StateFlow<DomainAuthState> = _authState.asStateFlow()

    private var transportStateJob: Job? = null

    override suspend fun initialize(config: DomainConfig): Result<Unit> {
        UnifiedLog.d(TAG) { "initialize: Starting with config - scheme=${config.scheme}, host=${config.host}, port=${config.port}, username=${config.username}" }
        
        _connectionState.value = DomainConnectionState.Connecting

        val transportConfig = config.toTransportConfig()
        UnifiedLog.d(TAG) { "initialize: Calling apiClient.initialize" }
        
        return apiClient.initialize(transportConfig)
            .map { caps ->
                UnifiedLog.d(TAG) { "initialize: API client initialized successfully with capabilities: ${caps.baseUrl}" }
                // Start observing transport state changes
                observeTransportStates()
                // CRITICAL: Set XTREAM as ACTIVE source after successful connection
                sourceActivationStore.setXtreamActive()
                UnifiedLog.i(TAG) { "initialize: XTREAM source activated" }
                Unit
            }
            .onFailure { error ->
                UnifiedLog.e(TAG, error) { "initialize: Failed with error" }
                _connectionState.value = DomainConnectionState.Error(
                    error.message ?: "Unknown error"
                )
                _authState.value = DomainAuthState.Failed(error.message ?: "Unknown error")
                // Deactivate XTREAM on failure
                sourceActivationStore.setXtreamInactive(SourceErrorReason.TRANSPORT_ERROR)
            }
    }

    companion object {
        private const val TAG = "XtreamAuthRepoAdapter"
    }

    override suspend fun close() {
        transportStateJob?.cancel()
        transportStateJob = null
        apiClient.close()
        _connectionState.value = DomainConnectionState.Disconnected
        _authState.value = DomainAuthState.Idle
        // Deactivate XTREAM when disconnecting
        sourceActivationStore.setXtreamInactive()
        UnifiedLog.i(TAG) { "close: XTREAM source deactivated" }
    }

    override suspend fun saveCredentials(config: DomainConfig) {
        val storedConfig = XtreamStoredConfig(
            scheme = config.scheme,
            host = config.host,
            port = config.port,
            username = config.username,
            password = config.password,
        )
        credentialsStore.write(storedConfig)
    }

    override suspend fun clearCredentials() {
        credentialsStore.clear()
    }

    /**
     * Observe transport layer state changes and map them to domain states.
     *
     * This method sets up state synchronization between transport and domain layers
     * after successful initialization.
     */
    private fun observeTransportStates() {
        transportStateJob?.cancel()
        transportStateJob = combine(
            apiClient.connectionState,
            apiClient.authState,
        ) { connection, auth ->
            connection to auth
        }
            .onEach { (connection, auth) ->
                val domainConnection = connection.toDomainConnectionState()
                if (_connectionState.value != domainConnection) {
                    UnifiedLog.d(TAG) { "connectionState -> $connection | domain=$domainConnection" }
                    _connectionState.value = domainConnection
                }

                val domainAuth = auth.toDomainAuthState()
                if (_authState.value != domainAuth) {
                    UnifiedLog.d(TAG) { "authState -> $auth | domain=$domainAuth" }
                    _authState.value = domainAuth
                }
            }
            .launchIn(appScope)
    }

    // =========================================================================
    // Model Conversions
    // =========================================================================

    private fun DomainConfig.toTransportConfig(): XtreamApiConfig = XtreamApiConfig(
        scheme = scheme,
        host = host,
        port = port,
        username = username,
        password = password,
    )

    private fun TransportConnectionState.toDomainConnectionState(): DomainConnectionState = when (this) {
        TransportConnectionState.Disconnected -> DomainConnectionState.Disconnected
        TransportConnectionState.Connecting -> DomainConnectionState.Connecting
        is TransportConnectionState.Connected -> DomainConnectionState.Connected
        is TransportConnectionState.Error -> DomainConnectionState.Error(
            message = error.toErrorMessage()
        )
    }

    private fun TransportAuthState.toDomainAuthState(): DomainAuthState = when (this) {
        TransportAuthState.Unknown -> DomainAuthState.Idle
        TransportAuthState.Pending -> DomainAuthState.Idle
        is TransportAuthState.Authenticated -> DomainAuthState.Authenticated
        is TransportAuthState.Failed -> DomainAuthState.Failed(
            message = error.toErrorMessage()
        )
        is TransportAuthState.Expired -> DomainAuthState.Expired(
            expDate = expDate?.let { formatExpDate(it) }
        )
    }

    private fun XtreamError.toErrorMessage(): String = when (this) {
        is XtreamError.Network -> message
        is XtreamError.Http -> "HTTP $code: $message"
        XtreamError.InvalidCredentials -> "Invalid credentials"
        is XtreamError.AccountExpired -> "Account expired"
        is XtreamError.ParseError -> "Parse error: $message"
        is XtreamError.NonJsonResponse -> if (isM3UPlaylist) {
            "Server returned M3U playlist. Please use the API URL (player_api.php), not the playlist URL (get.php)."
        } else {
            "Server returned non-JSON response (Content-Type: $contentType)"
        }
        is XtreamError.Unsupported -> "Unsupported action: $action"
        is XtreamError.RateLimited -> "Rate limited"
        is XtreamError.Unknown -> message
    }

    private fun formatExpDate(epochSeconds: Long): String {
        val date = Date(epochSeconds * 1000)
        val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        return formatter.format(date)
    }
}
