package com.fishit.player.infra.transport.telegram

import dev.g000sha256.tdl.TdlClient

/**
 * Provider interface for TDLib client initialization.
 *
 * This abstraction allows modules to remain Context-free while still supporting TDLib
 * initialization which requires Android Context.
 *
 * **ARCHITECTURE:**
 * - Transport module defines this interface (no Context dependency)
 * - App module implements this via Hilt, providing Context internally
 * - This preserves v2 architecture: lower modules have no Android dependencies
 *
 * **g00sha tdlib-coroutines Integration:**
 * The underlying TDLib client is `dev.g000sha256.tdl.TdlClient` from the Maven AAR
 * `dev.g000sha256:tdl-coroutines-android:5.0.0`. This wrapper provides:
 * - Kotlin coroutines support (suspend functions)
 * - Flow-based update streams
 * - TdlResult for error handling
 * - DTOs in `dev.g000sha256.tdl.dto.*`
 *
 * @see dev.g000sha256.tdl.TdlClient
 */
interface TdlibClientProvider {
    
    /** Whether the TDLib client is initialized and ready. */
    val isInitialized: Boolean

    /**
     * Get the underlying TdlClient instance.
     *
     * **IMPORTANT:** Only ONE TdlClient should exist per process.
     * This method returns the shared instance.
     * Call [initialize] first if not yet initialized.
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
}
