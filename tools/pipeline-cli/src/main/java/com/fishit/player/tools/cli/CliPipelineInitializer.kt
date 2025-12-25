package com.fishit.player.tools.cli

import com.fishit.player.infra.transport.telegram.TelegramClientFactory
import com.fishit.player.infra.transport.telegram.TelegramSessionConfig
import com.fishit.player.infra.transport.xtream.DefaultXtreamApiClient
import com.fishit.player.infra.transport.xtream.XtreamApiConfig
import com.fishit.player.pipeline.telegram.adapter.TelegramPipelineAdapter
import com.fishit.player.pipeline.telegram.grouper.TelegramMessageBundler
import com.fishit.player.pipeline.telegram.grouper.TelegramStructuredMetadataExtractor
import com.fishit.player.pipeline.telegram.mapper.TelegramBundleToMediaItemMapper
import com.fishit.player.pipeline.xtream.adapter.XtreamPipelineAdapter
import dev.g000sha256.tdl.TdlClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * CLI Pipeline Initializer - lightweight headless initialization.
 *
 * Replaces dependency on core:app-startup.AppStartupImpl.
 * Directly creates transport clients and pipeline adapters for CLI use.
 *
 * **Architecture Note (v2):**
 * - CLI creates TdlClient directly (v2 pattern, no TdlibClientProvider)
 * - Manual instantiation of bundler/mapper (no Hilt)
 * - Uses OkHttpClient for Xtream transport
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
        // Create CLI-specific TdlClient
        val tdlClient = TdlClient.create()

        // Create transport client
        val sessionConfig = TelegramSessionConfig(
            apiId = config.apiId,
            apiHash = config.apiHash,
            databaseDir = config.databaseDir,
            filesDir = config.filesDir,
            deviceModel = "CLI",
            systemVersion = "CLI",
            appVersion = "cli",
        )

        val scope = CoroutineScope(Dispatchers.IO)
        val transportClient = TelegramClientFactory.fromExistingSession(
            config = sessionConfig,
            scope = scope,
        )

        // Create pipeline dependencies (manual instantiation for CLI)
        val bundler = TelegramMessageBundler()
        val metadataExtractor = TelegramStructuredMetadataExtractor()
        val bundleMapper = TelegramBundleToMediaItemMapper(metadataExtractor)

        // Create and return pipeline adapter
        return TelegramPipelineAdapter(
            transport = transportClient,
            bundler = bundler,
            bundleMapper = bundleMapper,
        )
    }

    private suspend fun initXtream(config: XtreamCliConfig): XtreamPipelineAdapter {
        // Create OkHttpClient for CLI
        val httpClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()

        // Create transport client
        val apiClient = DefaultXtreamApiClient(
            http = httpClient,
        )

        // Parse host from baseUrl
        val url = java.net.URL(config.baseUrl)
        val apiConfig = XtreamApiConfig(
            scheme = url.protocol,
            host = url.host,
            port = if (url.port != -1) url.port else null,
            username = config.username,
            password = config.password,
        )
        
        // Initialize (performs capability discovery and auth validation)
        apiClient.initialize(apiConfig).getOrThrow()

        // Create and return pipeline adapter
        return XtreamPipelineAdapter(
            apiClient = apiClient,
        )
    }
}
