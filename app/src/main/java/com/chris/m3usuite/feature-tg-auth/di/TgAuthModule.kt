package com.chris.m3usuite.feature_tg_auth.di

import android.content.Context
import com.chris.m3usuite.data.repo.TelegramAuthRepository
import com.chris.m3usuite.feature_tg_auth.data.TgAuthOrchestrator
import com.chris.m3usuite.feature_tg_auth.data.TgErrorMapper
import com.chris.m3usuite.feature_tg_auth.data.TgSmsConsentManager

object TgAuthModule {
    fun provideSmsConsentManager(context: Context): TgSmsConsentManager =
        TgSmsConsentManager(context.applicationContext)

    fun provideErrorMapper(): TgErrorMapper = TgErrorMapper()

    fun provideOrchestrator(
        context: Context,
        repository: TelegramAuthRepository
    ): TgAuthOrchestrator {
        val sms = provideSmsConsentManager(context)
        val mapper = provideErrorMapper()
        return TgAuthOrchestrator(repository, sms, mapper)
    }
}
