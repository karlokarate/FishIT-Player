package com.chris.m3usuite.feature_tg_auth.data

import com.chris.m3usuite.data.repo.TelegramAuthRepository
import com.chris.m3usuite.feature_tg_auth.domain.TgAuthError
import java.util.Locale

class TgErrorMapper {
    private val floodRegex = Regex("FLOOD_WAIT_(\\d+)", RegexOption.IGNORE_CASE)

    fun map(error: TelegramAuthRepository.AuthError): TgAuthError {
        val raw = (error.rawMessage ?: error.message).uppercase(Locale.ROOT)
        return when {
            "PHONE_NUMBER_INVALID" in raw -> TgAuthError.InvalidPhoneNumber
            "PHONE_CODE_INVALID" in raw || "PHONE_CODE_EMPTY" in raw -> TgAuthError.InvalidCode
            "PHONE_CODE_EXPIRED" in raw -> TgAuthError.CodeExpired
            "UPDATE_APP_TO_LOGIN" in raw || error.code == 406 -> TgAuthError.AppUpdateRequired
            "PHONE_NUMBER_BANNED" in raw || "USER_BANNED" in raw || "ACCOUNT_DELETED" in raw -> TgAuthError.Banned
            floodRegex.containsMatchIn(raw) -> {
                val wait = floodRegex.find(raw)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0
                TgAuthError.FloodWait(wait, error.rawMessage)
            }
            error.code == 420 || raw.contains("TOO MANY REQUESTS") || raw.contains("CODE_TOO_MUCH") -> TgAuthError.TooManyAttempts
            "SESSION_PASSWORD_NEEDED" in raw -> TgAuthError.Generic("Passwort erforderlich", error.rawMessage)
            else -> TgAuthError.Generic(error.message.ifBlank { error.rawMessage ?: "Unbekannter Fehler" }, error.rawMessage)
        }
    }
}
