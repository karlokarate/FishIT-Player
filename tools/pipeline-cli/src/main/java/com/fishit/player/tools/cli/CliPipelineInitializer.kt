package com.fishit.player.tools.cli

import com.fishit.player.infra.transport.telegram.DefaultTelegramTransportClient
import com.fishit.player.infra.transport.telegram.TelegramSessionConfig
import com.fishit.player.infra.transport.xtream.DefaultXtreamApiClient
import com.fishit.player.pipeline.telegram.adapter.TelegramPipelineAdapter
import com.fishit.player.pipeline.xtream.adapter.XtreamPipelineAdapter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

/**
 * CLI Pipeline Initializer - lightweight headless initialization.
 *
 * Replaces dependency on core:app-startup.AppStartupImpl.
 * Directly creates transport clients and pipeline adapters for CLI use.
 */
object CliPipelineInitializer {

    /**
     * Initialize pipelines from CLI configuration.
     *
     * @param config CLI configuration
     * @return CliPipelines with initialized adapters
     */
    suspend fun initializePipelines(config: CliConfig): CliPipelines {
        val telegramAdapter = config.telegram?.let { initTelegram(it) }
        val xtreamAdapter = config.xtream?.let { initXtream(it) }

        return CliPipelines(
            telegram = telegramAdapter,
            xtream = xtreamAdapter,
        )
    }

    private suspend fun initTelegram(config: TelegramCliConfig): TelegramPipelineAdapter {
        // Create transport client
        val sessionConfig = TelegramSessionConfig(
            apiId = config.apiId,
            apiHash = config.apiHash,
            databaseDir = config.databaseDir,
            filesDir = config.filesDir,
        )

        val transportClient = DefaultTelegramTransportClient(
            sessionConfig = sessionConfig,
            scope = CoroutineScope(Dispatchers.IO),
        )

        // Create and return pipeline adapter
        return TelegramPipelineAdapter(
            transport = transportClient,
        )
    }

    private suspend fun initXtream(config: XtreamCliConfig): XtreamPipelineAdapter {
        // Create transport client
        val apiClient = DefaultXtreamApiClient(
            baseUrl = config.baseUrl,
            username = config.username,
            password = config.password,
            scope = CoroutineScope(Dispatchers.IO),
        )

        // Authenticate
        apiClient.authenticate()

        // Create and return pipeline adapter
        return XtreamPipelineAdapter(
            apiClient = apiClient,
        )
    }
}
