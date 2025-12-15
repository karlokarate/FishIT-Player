package com.fishit.player.tools.cli

/**
 * Local CLI configuration model (tool-owned).
 *
 * This replaces dependency on core:app-startup.AppStartupConfig.
 */
data class CliConfig(
    val telegram: TelegramCliConfig?,
    val xtream: XtreamCliConfig?,
)

/**
 * Telegram configuration for CLI.
 */
data class TelegramCliConfig(
    val apiId: Int,
    val apiHash: String,
    val databaseDir: String,
    val filesDir: String,
    val useHotWarmColdClassification: Boolean = true,
)

/**
 * Xtream configuration for CLI.
 */
data class XtreamCliConfig(
    val baseUrl: String,
    val username: String,
    val password: String,
)
