package com.fishit.player.core.onboarding.domain

import kotlinx.coroutines.flow.StateFlow

/**
 * Domain contract for Xtream authentication.
 */
interface XtreamAuthRepository {
    val connectionState: StateFlow<XtreamConnectionState>

    val authState: StateFlow<XtreamAuthState>

    suspend fun initialize(config: XtreamConfig): Result<Unit>

    suspend fun close()

    suspend fun saveCredentials(config: XtreamConfig)

    suspend fun clearCredentials()
}

data class XtreamConfig(
    val scheme: String,
    val host: String,
    val port: Int,
    val username: String,
    val password: String,
)

sealed class XtreamConnectionState {
    data object Disconnected : XtreamConnectionState()

    data object Connecting : XtreamConnectionState()

    data object Connected : XtreamConnectionState()

    data class Error(
        val message: String,
    ) : XtreamConnectionState()
}

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
