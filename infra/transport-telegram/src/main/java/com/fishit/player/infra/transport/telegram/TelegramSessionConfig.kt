package com.fishit.player.infra.transport.telegram

/**
 * Configuration for a TDLib session.
 *
 * Used by [TelegramClientFactory] to create a [TelegramTransportClient].
 * Can be used for:
 * - Pre-authenticated sessions (Codespaces/CI)
 * - Interactive auth flows (production app)
 *
 * @property apiId Telegram API ID from my.telegram.org
 * @property apiHash Telegram API Hash from my.telegram.org
 * @property databaseDir Path to TDLib database directory (td.db)
 * @property filesDir Path to TDLib files directory (downloads, photos)
 * @property useMessageDatabase Whether to enable message database (default: true)
 * @property useFileDatabase Whether to enable file database (default: true)
 * @property phoneNumber Optional phone number for auto-auth (international format)
 * @property deviceModel Device model for TDLib (default: "FishIT-Player")
 * @property systemVersion OS version for TDLib (default: "1.0")
 * @property appVersion App version for TDLib (default: "1.0")
 */
data class TelegramSessionConfig(
    val apiId: Int,
    val apiHash: String,
    val databaseDir: String,
    val filesDir: String,
    val useMessageDatabase: Boolean = true,
    val useFileDatabase: Boolean = true,
    val phoneNumber: String? = null,
    val deviceModel: String = "FishIT-Player",
    val systemVersion: String = "1.0",
    val appVersion: String = "1.0"
) {
    /** Alias for [databaseDir] for compatibility with TdlibAuthSession. */
    val databasePath: String get() = databaseDir

    /** Alias for [filesDir] for compatibility with TdlibAuthSession. */
    val filesPath: String get() = filesDir

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
         * - TG_PHONE_NUMBER (for auto-auth)
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
            val phoneNumber = System.getenv("TG_PHONE_NUMBER")

            return TelegramSessionConfig(
                apiId = apiId,
                apiHash = apiHash,
                databaseDir = databaseDir,
                filesDir = filesDir,
                phoneNumber = phoneNumber,
            )
        }
    }
}
