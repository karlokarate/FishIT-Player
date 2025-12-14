package com.fishit.player.v2.di

import android.content.Context
import com.fishit.player.infra.transport.telegram.TdlibClientProvider
import com.fishit.player.infra.transport.telegram.TelegramSessionConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.g000sha256.tdl.TdlClient
import javax.inject.Inject
import javax.inject.Singleton

/**
 * App-level implementation of TdlibClientProvider.
 *
 * This provides the TdlClient instance for the transport layer.
 * The client is created lazily when first accessed.
 *
 * **Note:** This is a temporary adapter while TelegramTransportModule
 * is migrated to use typed interfaces (TelegramAuthClient, etc.)
 * instead of TdlibClientProvider.
 */
@Singleton
class TdlibClientProviderImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val sessionConfig: TelegramSessionConfig,
) : TdlibClientProvider {

    private var client: TdlClient? = null

    override val isInitialized: Boolean
        get() = client != null

    override fun getClient(): TdlClient {
        return client ?: throw IllegalStateException("TdlClient not initialized")
    }

    override suspend fun initialize() {
        if (client == null) {
            // Install logging before creating client
            installLogging()
            client = TdlClient.create()
        }
    }

    override fun getDatabasePath(): String {
        return sessionConfig.databaseDir
    }

    override fun getFilesDirectory(): String {
        return sessionConfig.filesDir
    }
}
