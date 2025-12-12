package com.fishit.player.infra.transport.telegram

import com.fishit.player.infra.transport.telegram.api.TelegramAuthException
import com.fishit.player.infra.transport.telegram.api.TelegramAuthState
import kotlinx.coroutines.flow.Flow

/**
 * Typed interface for Telegram authentication operations.
 *
 * This is part of the v2 Transport API Surface. Upper layers (pipeline, playback)
 * consume this interface instead of accessing TDLib directly.
 *
 * **v2 Architecture:**
 * - Transport layer owns TDLib lifecycle and auth state machine
 * - Pipeline/Playback consume typed interfaces
 * - No TDLib types (`TdApi.*`) exposed beyond transport
 *
 * **Implementation:** [DefaultTelegramClient] implements this interface internally.
 *
 * @see TelegramHistoryClient for message fetching
 * @see TelegramFileClient for file downloads
 * @see contracts/TELEGRAM_LEGACY_MODULE_MIGRATION_CONTRACT.md
 */
interface TelegramAuthClient {

    /**
     * Current authentication state.
     *
     * Emits updates whenever auth state changes. UI/Domain should observe this
     * to handle interactive auth steps (code entry, password entry).
     */
    val authState: Flow<TelegramAuthState>

    /**
     * Ensure the TDLib client is authorized and ready.
     *
     * Implements "resume-first" behavior:
     * - If already authorized on boot → Ready without UI involvement
     * - If not authorized → initiates auth flow, caller observes [authState]
     *
     * @throws TelegramAuthException if authorization fails
     */
    suspend fun ensureAuthorized()

    /**
     * Check if currently authorized without initiating auth flow.
     *
     * @return true if authorized and ready to use
     */
    suspend fun isAuthorized(): Boolean

    /**
     * Submit phone number for authentication.
     *
     * Called when [authState] emits [TelegramAuthState.WaitPhoneNumber].
     *
     * @param phoneNumber Phone number in international format (e.g., "+49123456789")
     * @throws TelegramAuthException if submission fails
     */
    suspend fun sendPhoneNumber(phoneNumber: String)

    /**
     * Submit verification code for authentication.
     *
     * Called when [authState] emits [TelegramAuthState.WaitCode].
     *
     * @param code The verification code received via SMS/call
     * @throws TelegramAuthException if code is invalid
     */
    suspend fun sendCode(code: String)

    /**
     * Submit 2FA password for authentication.
     *
     * Called when [authState] emits [TelegramAuthState.WaitPassword].
     *
     * @param password The two-factor authentication password
     * @throws TelegramAuthException if password is incorrect
     */
    suspend fun sendPassword(password: String)

    /**
     * Log out from current Telegram session.
     *
     * This will invalidate the current session and require re-authentication.
     */
    suspend fun logout()
}
