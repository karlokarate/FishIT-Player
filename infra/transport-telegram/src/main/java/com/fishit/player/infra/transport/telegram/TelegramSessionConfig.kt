package com.fishit.player.infra.transport.telegram

/**
 * Configuration for an existing TDLib session.
 *
 * Used by [TelegramClientFactory] to create a [TelegramTransportClient]
 * from a pre-authenticated TDLib session (e.g., in Codespaces/CI).
 *
 * **Codespace Usage:**
 * The session directories contain a previously authenticated TDLib user session.
 * No interactive auth (phone, code, password) is performed.
 *
 * @property apiId Telegram API ID from my.telegram.org
 * @property apiHash Telegram API Hash from my.telegram.org
 * @property databaseDir Path to TDLib database directory (td.db)
 * @property filesDir Path to TDLib files directory (downloads, photos)
 * @property useMessageDatabase Whether to enable message database (default: true)
 * @property useFileDatabase Whether to enable file database (default: true)
 */
data class TelegramSessionConfig(
    val apiId: Int,
    val apiHash: String,
    val databaseDir: String,
    val filesDir: String,
    val useMessageDatabase: Boolean = true,
    val useFileDatabase: Boolean = true,
) {
    companion object {
        /**
         * Create config from environment variables.
         *
         * Required env vars:
         * - TG_API_ID
         * - TG_API_HASH
         *
         * Optional (defaults to Codespace paths):
         * - TDLIB_DATABASE_DIR
         * - TDLIB_FILES_DIR
         *
         * @return TelegramSessionConfig or null if required vars missing
         */
        fun fromEnvironment(): TelegramSessionConfig? {
            val apiId = System.getenv("TG_API_ID")?.toIntOrNull() ?: return null
            val apiHash = System.getenv("TG_API_HASH") ?: return null

            val defaultSessionRoot = "/workspace/.cache/tdlib-user/tdlib"
            val databaseDir = System.getenv("TDLIB_DATABASE_DIR")
                ?: "$defaultSessionRoot/db"
            val filesDir = System.getenv("TDLIB_FILES_DIR")
                ?: "$defaultSessionRoot/files"

            return TelegramSessionConfig(
                apiId = apiId,
                apiHash = apiHash,
                databaseDir = databaseDir,
                filesDir = filesDir,
            )
        }
    }
}
