package com.fishit.player.core.feature.auth

import kotlinx.coroutines.flow.StateFlow

/**
 * Feature-domain contract for Telegram authentication.
 *
 * Implementations live outside the feature layer (e.g., infra/data-telegram)
 * and delegate to transport APIs.
 */
interface TelegramAuthRepository {
    val authState: StateFlow<TelegramAuthState>

    suspend fun ensureAuthorized()

    suspend fun sendPhoneNumber(phoneNumber: String)

    suspend fun sendCode(code: String)

    suspend fun sendPassword(password: String)

    suspend fun logout()
}

/**
 * Feature-domain auth states exposed to UI.
 */
sealed class TelegramAuthState {
    data object Idle : TelegramAuthState()

    data object Disconnected : TelegramAuthState()

    data object WaitingForPhone : TelegramAuthState()

    data object WaitingForCode : TelegramAuthState()

    data object WaitingForPassword : TelegramAuthState()

    data object Connected : TelegramAuthState()

    data class Error(
        val message: String,
    ) : TelegramAuthState()
}
