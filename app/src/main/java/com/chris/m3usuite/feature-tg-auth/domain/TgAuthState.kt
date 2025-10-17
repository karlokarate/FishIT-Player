package com.chris.m3usuite.feature_tg_auth.domain

sealed class TgAuthState {
    object Unauthenticated : TgAuthState()
    object WaitPhone : TgAuthState()
    data class WaitCode(
        val resendAvailableAtMillis: Long?,
        val suggestedCode: String? = null,
        val lastError: TgAuthError? = null
    ) : TgAuthState() {
        val canResend: Boolean
            get() = resendAvailableAtMillis?.let { System.currentTimeMillis() >= it } ?: true

        val remainingSeconds: Long
            get() = resendAvailableAtMillis?.let { millis ->
                val delta = millis - System.currentTimeMillis()
                if (delta <= 0L) 0L else (delta + 999L) / 1000L
            } ?: 0L
    }
    data class WaitPassword(
        val hint: String? = null,
        val lastError: TgAuthError? = null
    ) : TgAuthState()
    data class Qr(val link: String?) : TgAuthState()
    object Ready : TgAuthState()
    object LoggingOut : TgAuthState()
}
