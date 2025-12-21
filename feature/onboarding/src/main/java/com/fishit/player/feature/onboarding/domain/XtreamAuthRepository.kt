package com.fishit.player.feature.onboarding.domain

import kotlinx.coroutines.flow.StateFlow

/**
 * Xtream API client interface for onboarding feature.
 *
 * This is a feature-owned domain interface following the Dependency Inversion Principle.
 * The actual implementation lives in infra/data-xtream as an adapter.
 */
interface XtreamAuthRepository {
    /**
     * Current connection state
     */
    val connectionState: StateFlow<XtreamConnectionState>

    /**
     * Current authentication state
     */
    val authState: StateFlow<XtreamAuthState>

    /**
     * Initialize Xtream connection with configuration
     */
    suspend fun initialize(config: XtreamConfig): Result<Unit>

    /**
     * Close Xtream connection
     */
    suspend fun close()

    /**
     * Persist credentials for auto-login
     */
    suspend fun saveCredentials(config: XtreamConfig)

    /**
     * Clear stored credentials
     */
    suspend fun clearCredentials()
}

/**
 * Xtream configuration (feature domain model)
 */
data class XtreamConfig(
    val scheme: String,
    val host: String,
    val port: Int,
    val username: String,
    val password: String,
)

/**
 * Xtream connection states (feature domain model)
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
 * Xtream authentication states (feature domain model)
 */
sealed class XtreamAuthState {
    data object Idle : XtreamAuthState()

    data object Authenticated : XtreamAuthState()

    data class Failed(
        val message: String,
    ) : XtreamAuthState()

    data class Expired(
        val expDate: String?,
    ) : XtreamAuthState()
}
