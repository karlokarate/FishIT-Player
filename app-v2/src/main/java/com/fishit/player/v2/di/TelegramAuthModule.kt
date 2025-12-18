package com.fishit.player.v2.di

import android.content.Context
import android.os.Build
import com.fishit.player.infra.logging.UnifiedLog
import com.fishit.player.infra.transport.telegram.TelegramAuthClient
import com.fishit.player.infra.transport.telegram.TelegramSessionConfig
import com.fishit.player.infra.transport.telegram.auth.TdlibAuthSession
import com.fishit.player.v2.BuildConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.g000sha256.tdl.TdlClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.io.File
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object TelegramAuthModule {
    private const val TAG = "TelegramAuthModule"
    private const val TELEGRAM_AUTH_SCOPE = "TelegramAuthScope"

    @Provides
    @Singleton
    fun provideTelegramSessionConfig(
        @ApplicationContext context: Context,
    ): TelegramSessionConfig {
        val sessionRoot = File(context.filesDir, "tdlib")
        val databaseDir = File(sessionRoot, "db").apply { mkdirs() }
        val filesDir = File(sessionRoot, "files").apply { mkdirs() }

        if (BuildConfig.TG_API_ID == 0 || BuildConfig.TG_API_HASH.isBlank()) {
            UnifiedLog.w(TAG) { "Telegram API credentials are missing; auth will fail" }
        }

        return TelegramSessionConfig(
            apiId = BuildConfig.TG_API_ID,
            apiHash = BuildConfig.TG_API_HASH,
            databaseDir = databaseDir.absolutePath,
            filesDir = filesDir.absolutePath,
            deviceModel = Build.MODEL ?: "Android",
            systemVersion = Build.VERSION.RELEASE ?: "1.0",
            appVersion = BuildConfig.VERSION_NAME,
        )
    }

    @Provides
    @Singleton
    @Named(TELEGRAM_AUTH_SCOPE)
    fun provideTelegramAuthScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Provides the shared TdlClient instance for the entire app.
     *
     * **v2 Architecture:**
     * - TdlClient is created once and shared across all Telegram consumers
     * - This replaces the v1 TdlibClientProvider pattern
     *
     * **Consumers:**
     * - TelegramTransportModule (via TelegramTransportClient)
     * - TelegramAuthClient (via TdlibAuthSession)
     */
    @Provides
    @Singleton
    fun provideTdlClient(): TdlClient {
        return TdlClient.create()
    }

    @Provides
    @Singleton
    fun provideTelegramAuthClient(
        tdlClient: TdlClient,
        sessionConfig: TelegramSessionConfig,
        @Named(TELEGRAM_AUTH_SCOPE) scope: CoroutineScope,
    ): TelegramAuthClient {
        return TdlibAuthSession(tdlClient, sessionConfig, scope)
    }
}
