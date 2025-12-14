package com.fishit.player.infra.transport.telegram.di

import android.content.Context
import android.os.Build
import com.fishit.player.infra.logging.UnifiedLog
import com.fishit.player.infra.transport.telegram.DefaultTelegramTransportClient
import com.fishit.player.infra.transport.telegram.TdlibClientProvider
import com.fishit.player.infra.transport.telegram.TelegramAuthClient
import com.fishit.player.infra.transport.telegram.TelegramSessionConfig
import com.fishit.player.infra.transport.telegram.TelegramTransportClient
import com.fishit.player.infra.transport.telegram.auth.TdlibAuthSession
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

/**
 * Hilt module for Telegram transport layer.
 *
 * Provides TelegramTransportClient and TelegramAuthClient for use by higher layers.
 *
 * **Architecture (Phase B2):**
 * - Auth bindings migrated from app-v2 to transport-telegram
 * - Session config and TdlClient creation owned by transport layer
 * - BuildConfig values injected via Named parameters (provided by app-v2)
 */
@Module
@InstallIn(SingletonComponent::class)
object TelegramTransportModule {
    private const val TAG = "TelegramTransportModule"
    private const val TELEGRAM_AUTH_SCOPE = "TelegramAuthScope"

    /**
     * Provides the Telegram session configuration.
     *
     * Requires API credentials to be provided by app module via Named parameters.
     */
    @Provides
    @Singleton
    fun provideTelegramSessionConfig(
        @ApplicationContext context: Context,
        @Named("TG_API_ID") apiId: Int,
        @Named("TG_API_HASH") apiHash: String,
        @Named("APP_VERSION") appVersion: String,
    ): TelegramSessionConfig {
        val sessionRoot = File(context.filesDir, "tdlib")
        val databaseDir = File(sessionRoot, "db").apply { mkdirs() }
        val filesDir = File(sessionRoot, "files").apply { mkdirs() }

        if (apiId == 0 || apiHash.isBlank()) {
            UnifiedLog.w(TAG) { "Telegram API credentials are missing; auth will fail" }
        }

        return TelegramSessionConfig(
            apiId = apiId,
            apiHash = apiHash,
            databaseDir = databaseDir.absolutePath,
            filesDir = filesDir.absolutePath,
            deviceModel = Build.MODEL ?: "Android",
            systemVersion = Build.VERSION.RELEASE ?: "1.0",
            appVersion = appVersion,
        )
    }

    /**
     * Provides a dedicated coroutine scope for Telegram auth operations.
     */
    @Provides
    @Singleton
    @Named(TELEGRAM_AUTH_SCOPE)
    fun provideTelegramAuthScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Provides the TelegramAuthClient singleton.
     *
     * Uses TdlibAuthSession which manages the TDLib auth state machine.
     */
    @Provides
    @Singleton
    fun provideTelegramAuthClient(
        sessionConfig: TelegramSessionConfig,
        @Named(TELEGRAM_AUTH_SCOPE) scope: CoroutineScope,
    ): TelegramAuthClient {
        val client = TdlClient.create()
        return TdlibAuthSession(client, sessionConfig, scope)
    }

    /**
     * Provides the TelegramTransportClient singleton.
     *
     * **Note:** TdlibClientProvider must be provided by the app module
     * since it requires Android Context for TDLib initialization.
     */
    @Provides
    @Singleton
    fun provideTelegramTransportClient(
        clientProvider: TdlibClientProvider
    ): TelegramTransportClient {
        return DefaultTelegramTransportClient(clientProvider)
    }
}
