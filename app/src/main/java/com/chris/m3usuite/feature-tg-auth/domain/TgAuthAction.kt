package com.chris.m3usuite.feature_tg_auth.domain

sealed class TgAuthAction {
    data class EnterPhone(val phoneE164: String, val useCurrentDevice: Boolean) : TgAuthAction()
    data class EnterCode(val code: String) : TgAuthAction()
    data class EnterPassword(val password: String) : TgAuthAction()
    object ResendCode : TgAuthAction()
    object Cancel : TgAuthAction()
    object RequestQr : TgAuthAction()
}
