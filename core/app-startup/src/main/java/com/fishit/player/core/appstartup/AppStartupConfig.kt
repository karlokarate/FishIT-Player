package com.fishit.player.core.appstartup

import com.fishit.player.infra.transport.telegram.TelegramSessionConfig
import com.fishit.player.infra.transport.xtream.XtreamApiConfig

/**
 * Configuration for app/CLI pipeline startup.
 *
 * This config is used by both the Android app and the CLI to initialize
 * pipelines consistently. Null values indicate the pipeline is disabled.
 *
 * @property telegram Telegram pipeline configuration (null = disabled)
 * @property xtream Xtream pipeline configuration (null = disabled)
 */
data class AppStartupConfig(
    val telegram: TelegramPipelineConfig?,
    val xtream: XtreamPipelineConfig?,
)

/**
 * Telegram-specific pipeline configuration.
 *
 * @property sessionConfig Telegram API session configuration
 * @property useHotWarmColdClassification Enable chat media classification
 */
data class TelegramPipelineConfig(
    val sessionConfig: TelegramSessionConfig,
    val useHotWarmColdClassification: Boolean = true,
)

/**
 * Xtream-specific pipeline configuration.
 *
 * @property baseUrl Xtream server base URL (e.g., "http://example.com:8080")
 * @property username Xtream account username
 * @property password Xtream account password
 */
data class XtreamPipelineConfig(
    val baseUrl: String,
    val username: String,
    val password: String,
) {
    /**
     * Convert to transport-layer config.
     */
    fun toApiConfig(): XtreamApiConfig {
        // Parse baseUrl to extract scheme, host, port
        val uri = java.net.URI(baseUrl)
        return XtreamApiConfig(
            scheme = uri.scheme ?: "http",
            host = uri.host ?: baseUrl,
            port = if (uri.port > 0) uri.port else null,
            username = username,
            password = password,
        )
    }
}
