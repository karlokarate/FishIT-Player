package com.fishit.player.infra.transport.telegram.api

/**
 * Transport-layer authentication state for Telegram.
 *
 * Sealed interface representing TDLib authorization states without
 * exposing TDLib types. Used by UI/Domain to handle auth flows.
 *
 * **v2 Architecture:**
 * - Transport emits these states via Flow
 * - UI observes and presents appropriate screens
 * - No TDLib types leak outside transport
 */
sealed interface TelegramAuthState {

    /**
     * Client is ready and authorized.
     * Normal operations can proceed.
     */
    data object Ready : TelegramAuthState

    /**
     * Client is connecting/initializing.
     */
    data object Connecting : TelegramAuthState

    /**
     * Idle state before any action taken.
     */
    data object Idle : TelegramAuthState

    /**
     * TDLib is closing down.
     */
    data object LoggingOut : TelegramAuthState

    /**
     * Client has been closed.
     */
    data object Closed : TelegramAuthState

    /**
     * Logged out successfully.
     */
    data object LoggedOut : TelegramAuthState

    /**
     * Waiting for phone number input.
     *
     * @property hint Optional hint about expected format
     */
    data class WaitPhoneNumber(val hint: String? = null) : TelegramAuthState

    /**
     * Waiting for verification code.
     *
     * @property phoneNumber The phone number code was sent to
     * @property codeLength Expected code length
     */
    data class WaitCode(
        val phoneNumber: String? = null,
        val codeLength: Int? = null
    ) : TelegramAuthState

    /**
     * Waiting for two-factor authentication password.
     *
     * @property passwordHint Hint for the password
     * @property hasRecoveryEmail Whether recovery email is set
     */
    data class WaitPassword(
        val passwordHint: String? = null,
        val hasRecoveryEmail: Boolean = false
    ) : TelegramAuthState

    /**
     * Waiting for TDLib parameters to be set.
     * Internal state, usually handled automatically.
     */
    data object WaitTdlibParameters : TelegramAuthState

    /**
     * Waiting for database encryption key.
     * Internal state for encrypted databases.
     */
    data object WaitEncryptionKey : TelegramAuthState

    /**
     * Error state with message.
     *
     * @property message Error description
     */
    data class Error(val message: String) : TelegramAuthState

    /**
     * Unknown or unmapped TDLib state.
     *
     * @property raw String representation for debugging
     */
    data class Unknown(val raw: String) : TelegramAuthState
}

/**
 * Exception thrown during Telegram authentication operations.
 */
class TelegramAuthException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)
