package com.fishit.player.infra.transport.telegram

import com.fishit.player.infra.transport.telegram.internal.TdLibLogInstaller
import dev.g000sha256.tdl.TdlClient

/**
 * Provider interface for TDLib client initialization.
 *
 * **⚠️ DEPRECATED - V1 LEGACY PATTERN**
 *
 * This interface is a v1 legacy pattern and must NOT be used in v2 code. Use typed interfaces
 * instead:
 * - [TelegramAuthClient] for authentication
 * - [TelegramHistoryClient] for chat/message operations
 * - [TelegramFileClient] for file downloads
 *
 * v2 Architecture: TdlClient is provided directly via Hilt in the app module, not through this
 * provider pattern.
 *
 * @see TelegramAuthClient
 * @see TelegramHistoryClient
 * @see TelegramFileClient
 * @see contracts/GLOSSARY_v2_naming_and_modules.md (TdlibClientProvider entry)
 */
@Deprecated(
        message =
                "v1 legacy pattern. Use typed interfaces (TelegramAuthClient, TelegramHistoryClient, TelegramFileClient) with direct TdlClient injection instead.",
        level = DeprecationLevel.ERROR
)
interface TdlibClientProvider {

    /** Whether the TDLib client is initialized and ready. */
    val isInitialized: Boolean

    /**
     * Get the underlying TdlClient instance.
     *
     * **IMPORTANT:** Only ONE TdlClient should exist per process. This method returns the shared
     * instance. Call [initialize] first if not yet initialized.
     *
     * @return The g00sha TdlClient wrapper around TDLib
     * @throws IllegalStateException if not initialized
     */
    fun getClient(): TdlClient

    /**
     * Initialize the TDLib client.
     *
     * Implementation must:
     * 1. Create TdlClient via `TdlClient.create()`
     * 2. Set TDLib parameters (apiId, apiHash, databasePath, etc.)
     * 3. Handle Context internally (not exposed to transport layer)
     *
     * This is idempotent - safe to call multiple times.
     *
     * @throws TelegramAuthException if initialization fails
     */
    suspend fun initialize()

    /** Get the TDLib database path. Used for session management. */
    fun getDatabasePath(): String

    /** Get the TDLib files directory. Used for downloaded media. */
    fun getFilesDirectory(): String

    /**
     * Install TDLib logging bridge before client creation.
     *
     * Default implementation installs a UnifiedLog-backed handler that maps TDLib log messages to a
     * single tag (`tdlib`). Implementations should invoke this before calling `TdlClient.create()`
     * to ensure all TDLib diagnostics are captured.
     */
    fun installLogging(config: TelegramLoggingConfig = TelegramLoggingConfig.default()) {
        TdLibLogInstaller.install(config)
    }
}
