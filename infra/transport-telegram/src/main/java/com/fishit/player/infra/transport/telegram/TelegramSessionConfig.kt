package com.fishit.player.infra.transport.telegram

/**
 * Configuration for the Telethon sidecar proxy session.
 *
 * @property apiId Telegram API ID from my.telegram.org
 * @property apiHash Telegram API Hash from my.telegram.org
 * @property sessionDir Path where Telethon stores its SQLite session file (~6 KB)
 * @property proxyPort Port for the localhost HTTP proxy (default: 8089)
 * @property phoneNumber Optional phone number for auto-auth (international format)
 */
data class TelegramSessionConfig(
    val apiId: Int,
    val apiHash: String,
    val sessionDir: String,
    val proxyPort: Int = 8089,
    val phoneNumber: String? = null,
) {
    /** Base URL for the Telethon HTTP proxy. */
    val proxyBaseUrl: String get() = "http://127.0.0.1:$proxyPort"

    companion object {
        /**
         * Create config from environment variables.
         *
         * Required env vars:
         * - TG_API_ID
         * - TG_API_HASH
         *
         * Optional:
         * - TG_SESSION_DIR (defaults to /data/data/com.fishit.player/files/telethon)
         * - TG_PROXY_PORT (defaults to 8089)
         * - TG_PHONE_NUMBER (for auto-auth)
         *
         * @return TelegramSessionConfig or null if required vars missing
         */
        fun fromEnvironment(): TelegramSessionConfig? {
            val apiId = System.getenv("TG_API_ID")?.toIntOrNull() ?: return null
            val apiHash = System.getenv("TG_API_HASH") ?: return null

            val sessionDir = System.getenv("TG_SESSION_DIR")
                ?: "/data/data/com.fishit.player/files/telethon"
            val proxyPort = System.getenv("TG_PROXY_PORT")?.toIntOrNull() ?: 8089
            val phoneNumber = System.getenv("TG_PHONE_NUMBER")

            return TelegramSessionConfig(
                apiId = apiId,
                apiHash = apiHash,
                sessionDir = sessionDir,
                proxyPort = proxyPort,
                phoneNumber = phoneNumber,
            )
        }
    }
}
