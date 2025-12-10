package com.fishit.player.tools.cli

import com.fishit.player.core.appstartup.AppStartupConfig
import com.fishit.player.core.appstartup.TelegramPipelineConfig
import com.fishit.player.core.appstartup.XtreamPipelineConfig
import com.fishit.player.infra.transport.telegram.TelegramSessionConfig

/**
 * CLI Configuration Loader.
 *
 * Reads environment variables to build [AppStartupConfig].
 *
 * **Required Environment Variables for Telegram:**
 * - TG_API_ID: Telegram API ID (numeric)
 * - TG_API_HASH: Telegram API Hash
 *
 * **Optional for Telegram:**
 * - TDLIB_DATABASE_DIR: Path to TDLib database
 * - TDLIB_FILES_DIR: Path to TDLib files directory
 *
 * **Required Environment Variables for Xtream:**
 * - XTREAM_BASE_URL: Xtream server URL
 * - XTREAM_USERNAME: Account username
 * - XTREAM_PASSWORD: Account password
 */
object CliConfigLoader {

    private const val DEFAULT_SESSION_ROOT = "/workspace/.cache/tdlib-user/tdlib"

    /**
     * Load configuration from environment variables.
     *
     * @return AppStartupConfig with detected pipelines
     */
    fun loadAppStartupConfig(): AppStartupConfig {
        val telegramCfg = loadTelegramConfig()
        val xtreamCfg = loadXtreamConfig()

        return AppStartupConfig(
            telegram = telegramCfg,
            xtream = xtreamCfg,
        )
    }

    /**
     * Load Telegram configuration from environment.
     *
     * @return TelegramPipelineConfig or null if not configured
     */
    private fun loadTelegramConfig(): TelegramPipelineConfig? {
        val apiId = getenvInt("TG_API_ID") ?: return null
        val apiHash = getenv("TG_API_HASH") ?: return null

        val databaseDir = getenv("TDLIB_DATABASE_DIR")
            ?: "$DEFAULT_SESSION_ROOT/db"
        val filesDir = getenv("TDLIB_FILES_DIR")
            ?: "$DEFAULT_SESSION_ROOT/files"

        return TelegramPipelineConfig(
            sessionConfig = TelegramSessionConfig(
                apiId = apiId,
                apiHash = apiHash,
                databaseDir = databaseDir,
                filesDir = filesDir,
            ),
            useHotWarmColdClassification = true,
        )
    }

    /**
     * Load Xtream configuration from environment.
     *
     * @return XtreamPipelineConfig or null if not configured
     */
    private fun loadXtreamConfig(): XtreamPipelineConfig? {
        val baseUrl = getenv("XTREAM_BASE_URL") ?: return null
        val username = getenv("XTREAM_USERNAME") ?: return null
        val password = getenv("XTREAM_PASSWORD") ?: return null

        return XtreamPipelineConfig(
            baseUrl = baseUrl,
            username = username,
            password = password,
        )
    }

    /**
     * Check which pipelines are configured.
     *
     * @return Pair<Boolean, Boolean> (hasTelegram, hasXtream)
     */
    fun checkAvailability(): Pair<Boolean, Boolean> {
        val hasTelegram = getenv("TG_API_ID") != null && getenv("TG_API_HASH") != null
        val hasXtream = getenv("XTREAM_BASE_URL") != null &&
            getenv("XTREAM_USERNAME") != null &&
            getenv("XTREAM_PASSWORD") != null
        return hasTelegram to hasXtream
    }

    private fun getenv(name: String): String? = System.getenv(name)?.takeIf { it.isNotBlank() }

    private fun getenvInt(name: String): Int? = getenv(name)?.toIntOrNull()
}
