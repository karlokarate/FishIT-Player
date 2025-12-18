package com.fishit.player.infra.data.xtream

import com.fishit.player.feature.onboarding.domain.XtreamAuthRepository
import com.fishit.player.feature.onboarding.domain.XtreamAuthState as DomainAuthState
import com.fishit.player.feature.onboarding.domain.XtreamConfig as DomainConfig
import com.fishit.player.feature.onboarding.domain.XtreamConnectionState as DomainConnectionState
import com.fishit.player.infra.transport.xtream.XtreamApiClient
import com.fishit.player.infra.transport.xtream.XtreamApiConfig
import com.fishit.player.infra.transport.xtream.XtreamAuthState as TransportAuthState
import com.fishit.player.infra.transport.xtream.XtreamConnectionState as TransportConnectionState
import com.fishit.player.infra.transport.xtream.XtreamCredentialsStore
import com.fishit.player.infra.transport.xtream.XtreamError
import com.fishit.player.infra.transport.xtream.XtreamStoredConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
) : XtreamAuthRepository {

    private val _connectionState = MutableStateFlow<DomainConnectionState>(DomainConnectionState.Disconnected)
    override val connectionState: StateFlow<DomainConnectionState> = _connectionState.asStateFlow()

    private val _authState = MutableStateFlow<DomainAuthState>(DomainAuthState.Idle)
    override val authState: StateFlow<DomainAuthState> = _authState.asStateFlow()

    override suspend fun initialize(config: DomainConfig): Result<Unit> {
        _connectionState.value = DomainConnectionState.Connecting

        val transportConfig = config.toTransportConfig()
        return apiClient.initialize(transportConfig)
            .map { _ ->
                // Start observing transport state changes
                observeTransportStates()
                Unit
            }
            .onFailure { error ->
                _connectionState.value = DomainConnectionState.Error(
                    error.message ?: "Unknown error"
                )
                _authState.value = DomainAuthState.Failed(error.message ?: "Unknown error")
            }
    }

    override suspend fun close() {
        apiClient.close()
        _connectionState.value = DomainConnectionState.Disconnected
        _authState.value = DomainAuthState.Idle
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
        // Map current transport connection state
        _connectionState.value = apiClient.connectionState.value.toDomainConnectionState()
        
        // Map current transport auth state
        _authState.value = apiClient.authState.value.toDomainAuthState()
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
