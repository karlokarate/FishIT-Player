package com.fishit.player.feature.onboarding.domain

import kotlinx.coroutines.flow.StateFlow

/**
 * Telegram authentication client interface for onboarding feature.
 * 
 * This is a feature-owned domain interface following the Dependency Inversion Principle.
 * The actual implementation lives in infra/data-telegram as an adapter.
 */
interface TelegramAuthRepository {
    /**
     * Current authentication state
     */
    val authState: StateFlow<TelegramAuthState>

    /**
     * Start the authentication flow
     */
    suspend fun ensureAuthorized()

    /**
     * Submit phone number for verification
     */
    suspend fun sendPhoneNumber(phoneNumber: String)

    /**
     * Submit verification code
     */
    suspend fun sendCode(code: String)

    /**
     * Submit 2FA password
     */
    suspend fun sendPassword(password: String)

    /**
     * Log out from Telegram
     */
    suspend fun logout()
}

/**
 * Telegram authentication states (feature domain model)
 */
sealed class TelegramAuthState {
    data object Idle : TelegramAuthState()
    data object Disconnected : TelegramAuthState()
    data object WaitingForPhone : TelegramAuthState()
    data object WaitingForCode : TelegramAuthState()
    data object WaitingForPassword : TelegramAuthState()
    data object Connected : TelegramAuthState()
    data class Error(val message: String) : TelegramAuthState()
}
