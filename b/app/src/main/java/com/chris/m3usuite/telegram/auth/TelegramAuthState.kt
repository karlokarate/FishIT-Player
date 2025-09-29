package com.chris.m3usuite.telegram.auth

sealed interface TelegramAuthState {
    object Idle : TelegramAuthState
    data class EnterPhone(val default: String? = null) : TelegramAuthState
    object Loading : TelegramAuthState
    data class CodeRequired(val phone: String, val via: CodeVia) : TelegramAuthState
    data class PasswordRequired(val hint: String?) : TelegramAuthState
    data class Error(val message: String, val canRetry: Boolean) : TelegramAuthState
    data class Authorized(val userId: Long, val name: String?, val avatarSmall: String?) : TelegramAuthState
    object Cancelled : TelegramAuthState

    enum class CodeVia {
        Sms,
        App,
        Call,
        Other
    }
}
