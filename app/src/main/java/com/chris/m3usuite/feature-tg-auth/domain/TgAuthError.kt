package com.chris.m3usuite.feature_tg_auth.domain

/**
 * High-level Telegram auth errors with UI-ready messaging and optional retry hints.
 */
sealed class TgAuthError(open val rawMessage: String? = null) {
    abstract val userMessage: String
    open val retryDelaySeconds: Int? = null

    object InvalidPhoneNumber : TgAuthError() {
        override val userMessage: String = "Telefonnummer ungültig – bitte internationale Vorwahl prüfen."
    }

    object InvalidCode : TgAuthError() {
        override val userMessage: String = "Code ungültig – bitte erneut prüfen oder neu anfordern."
    }

    object CodeExpired : TgAuthError() {
        override val userMessage: String = "Der Code ist abgelaufen. Bitte fordere einen neuen Code an."
    }

    data class FloodWait(override val retryDelaySeconds: Int, override val rawMessage: String? = null) : TgAuthError(rawMessage) {
        override val userMessage: String =
            if (retryDelaySeconds > 0) "Bitte ${retryDelaySeconds}s warten, bevor du einen neuen Code anforderst." else "Bitte kurz warten, bevor du es erneut versuchst."
    }

    object AppUpdateRequired : TgAuthError() {
        override val userMessage: String = "Telegram verlangt ein App-Update für die Anmeldung. Bitte QR-Code nutzen oder aktualisieren."
    }

    object Banned : TgAuthError() {
        override val userMessage: String = "Die Telefonnummer wurde von Telegram gesperrt. Anmeldung ist nicht möglich."
    }

    object TooManyAttempts : TgAuthError() {
        override val userMessage: String = "Zu viele Versuche. Bitte kurz warten, bevor du es erneut versuchst."
    }

    data class Generic(override val userMessage: String, override val rawMessage: String? = null) : TgAuthError(rawMessage)
}
