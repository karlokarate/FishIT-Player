package com.fishit.player.infra.transport.telegram.api

/**
 * Telegram connection state (transport layer).
 *
 * This represents the TDLib connection status.
 * For domain-level connection state, see core/feature-api.
 */
sealed interface TelegramConnectionState {
    /** Not connected to Telegram servers. */
    data object Disconnected : TelegramConnectionState

    /** Establishing connection to Telegram servers. */
    data object Connecting : TelegramConnectionState

    /** Successfully connected to Telegram servers. */
    data object Connected : TelegramConnectionState

    /** Connection error occurred. */
    data class Error(
        val message: String,
    ) : TelegramConnectionState
}
