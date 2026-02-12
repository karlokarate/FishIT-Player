package com.fishit.player.v2.di

import android.content.Context
import com.fishit.player.infra.logging.UnifiedLog
import com.fishit.player.infra.transport.telegram.TelegramSessionConfig
import com.fishit.player.v2.BuildConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import javax.inject.Singleton

/**
 * Hilt module for Telegram configuration.
 *
 * **SSOT Architecture:**
 * - This module provides ONLY TelegramSessionConfig (needs Android Context)
 * - TelegramTransportModule provides ALL typed interfaces (Auth, History, File, Thumb)
 * - All interfaces resolve to the SAME TelegramClient instance
 *
 * **Why separate from TelegramTransportModule?**
 * - TelegramSessionConfig requires Android Context (app-v2 module)
 * - TelegramTransportModule is in infra:transport-telegram (no Context access)
 *
 * **Consumers:**
 * - TelegramTransportModule consumes TelegramSessionConfig
 * - All Telegram interfaces come from TelegramTransportModule
 */
@Module
@InstallIn(SingletonComponent::class)
object TelegramAuthModule {
    private const val TAG = "TelegramAuthModule"

    @Provides
    @Singleton
    fun provideTelegramSessionConfig(
        @ApplicationContext context: Context,
    ): TelegramSessionConfig {
        val sessionDir = File(context.filesDir, "telethon").apply { mkdirs() }

        val hasTelegramCredentials = BuildConfig.TG_API_ID != 0 && BuildConfig.TG_API_HASH.isNotBlank()

        if (!hasTelegramCredentials) {
            val message = "Telegram API credentials are missing. Set TG_API_ID and TG_API_HASH " +
                "environment variables before building. Telegram features will be disabled."
            UnifiedLog.w(TAG) { message }
        }

        return TelegramSessionConfig(
            apiId = if (hasTelegramCredentials) BuildConfig.TG_API_ID else 1,
            apiHash = if (hasTelegramCredentials) BuildConfig.TG_API_HASH else "dummy",
            sessionDir = sessionDir.absolutePath,
        )
    }
}
