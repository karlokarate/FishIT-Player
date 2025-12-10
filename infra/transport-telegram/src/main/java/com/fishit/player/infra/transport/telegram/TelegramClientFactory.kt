package com.fishit.player.infra.transport.telegram

import com.fishit.player.infra.logging.UnifiedLog
import dev.g000sha256.tdl.TdlClient
import dev.g000sha256.tdl.TdlResult
import dev.g000sha256.tdl.dto.AuthorizationStateClosed
import dev.g000sha256.tdl.dto.AuthorizationStateReady
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.withTimeout
import java.io.File

/**
 * Factory for creating TelegramTransportClient from existing sessions.
 *
 * **Purpose:**
 * Creates TelegramTransportClient instances for CLI and test usage without
 * requiring Android Context or interactive authentication.
 *
 * **Codespace/CI Usage:**
 * ```kotlin
 * val config = TelegramSessionConfig.fromEnvironment()
 *     ?: error("Missing TG_API_ID or TG_API_HASH")
 * val client = TelegramClientFactory.fromExistingSession(config)
 * ```
 *
 * **IMPORTANT:**
 * - Session directories must contain a valid, pre-authenticated TDLib session
 * - No interactive auth prompts are triggered
 * - If session is invalid, an exception is thrown
 */
object TelegramClientFactory {

    private const val TAG = "TelegramClientFactory"
    private const val AUTH_TIMEOUT_MS = 30_000L

    /**
     * Create a TelegramTransportClient from an existing TDLib session.
     *
     * The session directories must already contain valid TDLib database files
     * from a previously authenticated session.
     *
     * @param config Session configuration with paths and API credentials
     * @param scope CoroutineScope for the client (default: IO dispatcher)
     * @return Fully initialized TelegramTransportClient
     * @throws TelegramSessionException if session is invalid or auth fails
     */
    suspend fun fromExistingSession(
        config: TelegramSessionConfig,
        scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    ): TelegramTransportClient {
        UnifiedLog.i(TAG, "Creating client from existing session: db=${config.databaseDir}")

        // Validate directories exist
        validateSessionDirectories(config)

        // Create TdlClient
        val tdlClient = createTdlClient()

        // Create provider wrapper
        val provider = ExistingSessionProvider(
            client = tdlClient,
            config = config,
        )

        // Initialize and wait for auth
        provider.initialize()

        // Create transport client
        val transportClient = DefaultTelegramTransportClient(
            clientProvider = provider,
            scope = scope,
        )

        // Ensure authorized
        try {
            withTimeout(AUTH_TIMEOUT_MS) {
                transportClient.ensureAuthorized()
            }
            UnifiedLog.i(TAG, "Session authenticated successfully")
        } catch (e: Exception) {
            UnifiedLog.e(TAG, "Session authentication failed", e)
            throw TelegramSessionException("Session authentication failed: ${e.message}", e)
        }

        return transportClient
    }

    private fun validateSessionDirectories(config: TelegramSessionConfig) {
        val dbDir = File(config.databaseDir)
        val filesDir = File(config.filesDir)

        if (!dbDir.exists()) {
            throw TelegramSessionException(
                "TDLib database directory does not exist: ${config.databaseDir}"
            )
        }

        // Files directory can be created if missing
        if (!filesDir.exists()) {
            filesDir.mkdirs()
            UnifiedLog.w(TAG, "Created files directory: ${config.filesDir}")
        }
    }

    private fun createTdlClient(): TdlClient {
        return TdlClient.create()
    }

    /**
     * TdlibClientProvider implementation for existing sessions.
     *
     * Handles TDLib initialization with pre-configured session paths.
     * Note: For existing sessions, TDLib should already be ready.
     */
    private class ExistingSessionProvider(
        private val client: TdlClient,
        private val config: TelegramSessionConfig,
    ) : TdlibClientProvider {

        private var initialized = false

        override val isInitialized: Boolean
            get() = initialized

        override fun getClient(): TdlClient {
            check(initialized) { "TDLib client not initialized. Call initialize() first." }
            return client
        }

        override suspend fun initialize() {
            if (initialized) return

            UnifiedLog.d(TAG, "Initializing TDLib with existing session...")

            // For existing sessions, we just verify the auth state
            val authResult = client.getAuthorizationState()
            when (authResult) {
                is TdlResult.Success -> {
                    when (authResult.result) {
                        is AuthorizationStateReady -> {
                            UnifiedLog.i(TAG, "Session already authenticated")
                        }
                        is AuthorizationStateClosed -> {
                            throw TelegramSessionException("TDLib session is closed/invalid")
                        }
                        else -> {
                            UnifiedLog.d(TAG, "Initial auth state: ${authResult.result}")
                            // For other states, we'll let ensureAuthorized handle it
                        }
                    }
                }
                is TdlResult.Failure -> {
                    throw TelegramSessionException(
                        "Failed to get auth state: ${authResult.code} - ${authResult.message}"
                    )
                }
            }

            initialized = true
        }

        override fun getDatabasePath(): String = config.databaseDir

        override fun getFilesDirectory(): String = config.filesDir
    }
}

/**
 * Exception thrown when TDLib session operations fail.
 */
class TelegramSessionException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)
