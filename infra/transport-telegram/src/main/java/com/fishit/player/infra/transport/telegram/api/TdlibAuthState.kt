package com.fishit.player.infra.transport.telegram.api

/**
 * Transport-layer TDLib authentication state.
 *
 * Sealed interface representing TDLib authorization states without
 * exposing TDLib types. Maps directly to TDLib AuthorizationState events.
 *
 * **v2 Architecture:**
 * - Transport emits these states via Flow
 * - Data layer maps to domain TelegramAuthState (core/feature-api)
 * - No TDLib types leak outside transport
 *
 * **IMPORTANT:** This is NOT the same as core/feature-api TelegramAuthState.
 * This is transport-level TDLib state; domain state is in core/feature-api.
 */
sealed interface TdlibAuthState {
    /**
     * Client is ready and authorized.
     * Normal operations can proceed.
     */
    data object Ready : TdlibAuthState

    /**
     * Client is connecting/initializing.
     */
    data object Connecting : TdlibAuthState

    /**
     * Idle state before any action taken.
     */
    data object Idle : TdlibAuthState

    /**
     * TDLib is closing down.
     */
    data object LoggingOut : TdlibAuthState

    /**
     * Client has been closed.
     */
    data object Closed : TdlibAuthState

    /**
     * Logged out successfully.
     */
    data object LoggedOut : TdlibAuthState

    /**
     * Waiting for phone number input.
     *
     * @property hint Optional hint about expected format
     */
    data class WaitPhoneNumber(
        val hint: String? = null,
    ) : TdlibAuthState

    /**
     * Waiting for verification code.
     *
     * @property phoneNumber The phone number code was sent to
     * @property codeLength Expected code length
     */
    data class WaitCode(
        val phoneNumber: String? = null,
        val codeLength: Int? = null,
    ) : TdlibAuthState

    /**
     * Waiting for two-factor authentication password.
     *
     * @property passwordHint Hint for the password
     * @property hasRecoveryEmail Whether recovery email is set
     */
    data class WaitPassword(
        val passwordHint: String? = null,
        val hasRecoveryEmail: Boolean = false,
    ) : TdlibAuthState

    /**
     * Waiting for TDLib parameters to be set.
     * Internal state, usually handled automatically.
     */
    data object WaitTdlibParameters : TdlibAuthState

    /**
     * Waiting for database encryption key.
     * Internal state for encrypted databases.
     */
    data object WaitEncryptionKey : TdlibAuthState

    /**
     * Error state with message.
     *
     * @property message Error description
     */
    data class Error(
        val message: String,
    ) : TdlibAuthState

    /**
     * Unknown or unmapped TDLib state.
     *
     * @property raw String representation for debugging
     */
    data class Unknown(
        val raw: String,
    ) : TdlibAuthState
}

/**
 * Exception thrown during Telegram authentication operations.
 */
class TelegramAuthException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)
