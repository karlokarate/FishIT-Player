package com.fishit.player.infra.transport.telegram.api

/**
 * Transport-layer Telegram authentication state.
 *
 * Sealed interface representing Telegram authorization states.
 * Backend-agnostic: works with Telethon HTTP proxy.
 *
 * **v2 Architecture:**
 * - Transport emits these states via Flow
 * - Data layer maps to domain TelegramAuthState (core/feature-api)
 * - No transport implementation types leak outside this layer
 *
 * **IMPORTANT:** This is NOT the same as core/feature-api TelegramAuthState.
 * This is transport-level state; domain state is in core/feature-api.
 */
sealed interface TransportAuthState {
    /**
     * Client is ready and authorized.
     * Normal operations can proceed.
     */
    data object Ready : TransportAuthState

    /**
     * Client is connecting/initializing.
     */
    data object Connecting : TransportAuthState

    /**
     * Idle state before any action taken.
     */
    data object Idle : TransportAuthState

    /**
     * Proxy is shutting down.
     */
    data object LoggingOut : TransportAuthState

    /**
     * Client has been closed.
     */
    data object Closed : TransportAuthState

    /**
     * Logged out successfully.
     */
    data object LoggedOut : TransportAuthState

    /**
     * Waiting for phone number input.
     *
     * @property hint Optional hint about expected format
     */
    data class WaitPhoneNumber(
        val hint: String? = null,
    ) : TransportAuthState

    /**
     * Waiting for verification code.
     *
     * @property phoneNumber The phone number code was sent to
     * @property codeLength Expected code length
     */
    data class WaitCode(
        val phoneNumber: String? = null,
        val codeLength: Int? = null,
    ) : TransportAuthState

    /**
     * Waiting for two-factor authentication password.
     *
     * @property passwordHint Hint for the password
     * @property hasRecoveryEmail Whether recovery email is set
     */
    data class WaitPassword(
        val passwordHint: String? = null,
        val hasRecoveryEmail: Boolean = false,
    ) : TransportAuthState

    /**
     * Error state with message.
     *
     * @property message Error description
     */
    data class Error(
        val message: String,
    ) : TransportAuthState

    /**
     * Unknown or unmapped state.
     *
     * @property raw String representation for debugging
     */
    data class Unknown(
        val raw: String,
    ) : TransportAuthState
}

/**
 * Exception thrown during Telegram authentication operations.
 */
class TelegramAuthException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)
