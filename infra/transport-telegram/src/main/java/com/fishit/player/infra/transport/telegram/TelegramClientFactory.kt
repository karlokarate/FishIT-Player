package com.fishit.player.infra.transport.telegram

import com.fishit.player.infra.logging.UnifiedLog
import com.fishit.player.infra.transport.telegram.internal.DefaultTelegramClient
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
 * Factory for creating TelegramClient from existing sessions.
 *
 * **Purpose:**
 * Creates TelegramClient instances for CLI and test usage without
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
     * Create a TelegramClient from an existing TDLib session.
     *
     * The session directories must already contain valid TDLib database files
     * from a previously authenticated session.
     *
     * @param config Session configuration with paths and API credentials
     * @param scope CoroutineScope for the client (default: IO dispatcher)
     * @return Fully initialized TelegramClient
     * @throws TelegramSessionException if session is invalid or auth fails
     */
    suspend fun fromExistingSession(
        config: TelegramSessionConfig,
        scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    ): TelegramClient {
        UnifiedLog.i(TAG, "Creating client from existing session: db=${config.databaseDir}")

        // Validate directories exist
        validateSessionDirectories(config)

        // Create TdlClient
        val tdlClient = createTdlClient()

        // Verify session is valid before creating transport client
        verifySession(tdlClient)

        // Create transport client
        val transportClient = DefaultTelegramClient(
            tdlClient = tdlClient,
            sessionConfig = config,
            authScope = scope,
            fileScope = scope,
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

    /**
     * Verify that the TDLib session is valid.
     * For existing sessions, we check auth state but don't trigger interactive auth.
     */
    private suspend fun verifySession(client: TdlClient) {
        UnifiedLog.d(TAG, "Verifying TDLib session...")

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
                        // For other states, ensureAuthorized will handle them
                    }
                }
            }
            is TdlResult.Failure -> {
                throw TelegramSessionException(
                    "Failed to get auth state: ${authResult.code} - ${authResult.message}"
                )
            }
        }
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
}

/**
 * Exception thrown when TDLib session operations fail.
 */
class TelegramSessionException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)
