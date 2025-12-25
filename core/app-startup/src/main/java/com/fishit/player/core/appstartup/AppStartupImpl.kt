package com.fishit.player.core.appstartup

import com.fishit.player.infra.logging.UnifiedLog
import com.fishit.player.infra.transport.telegram.TelegramClientFactory
import com.fishit.player.infra.transport.telegram.TelegramTransportClient
import com.fishit.player.infra.transport.xtream.DefaultXtreamApiClient
import com.fishit.player.infra.transport.xtream.XtreamApiClient
import com.fishit.player.infra.transport.xtream.XtreamParallelism
import com.fishit.player.pipeline.telegram.adapter.TelegramPipelineAdapter
import com.fishit.player.pipeline.telegram.grouper.TelegramMessageBundler
import com.fishit.player.pipeline.telegram.grouper.TelegramStructuredMetadataExtractor
import com.fishit.player.pipeline.telegram.mapper.TelegramBundleToMediaItemMapper
import com.fishit.player.pipeline.xtream.adapter.XtreamPipelineAdapter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * Default AppStartup implementation.
 *
 * Creates and wires pipeline components for both Android app and CLI usage.
 *
 * **Pipeline Creation Flow:**
 * 1. Telegram: TelegramClientFactory → TelegramClient → TelegramPipelineAdapter
 * 2. Xtream: DefaultXtreamApiClient → XtreamPipelineAdapter
 *
 * **Thread Safety:**
 * This class is NOT thread-safe. Call [startPipelines] once per application lifecycle.
 *
 * @param scope CoroutineScope for pipeline operations (default: IO dispatcher)
 */
class AppStartupImpl(
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) : AppStartup {
    companion object {
        private const val TAG = "AppStartup"

        /** Default parallelism for Xtream API calls (conservative for startup) */
        private const val DEFAULT_PARALLELISM = 3
    }

    private var telegramClient: TelegramTransportClient? = null
    private var xtreamClient: XtreamApiClient? = null

    override suspend fun startPipelines(config: AppStartupConfig): Pipelines {
        UnifiedLog.i(TAG, "Starting pipelines...")

        val telegramAdapter =
            config.telegram?.let { telegramConfig ->
                initTelegramPipeline(telegramConfig)
            }

        val xtreamAdapter =
            config.xtream?.let { xtreamConfig ->
                initXtreamPipeline(xtreamConfig)
            }

        UnifiedLog.i(
            TAG,
            "Pipelines started: telegram=${telegramAdapter != null}, xtream=${xtreamAdapter != null}",
        )

        return Pipelines(
            telegram = telegramAdapter,
            xtream = xtreamAdapter,
        )
    }

    private suspend fun initTelegramPipeline(config: TelegramPipelineConfig): TelegramPipelineAdapter? =
        try {
            UnifiedLog.d(TAG, "Initializing Telegram pipeline...")

            // Create transport client from existing session
            val transportClient =
                TelegramClientFactory.fromExistingSession(
                    config = config.sessionConfig,
                    scope = scope,
                )
            telegramClient = transportClient

            // Create pipeline adapter with bundler and mapper
            val metadataExtractor = TelegramStructuredMetadataExtractor()
            val bundler = TelegramMessageBundler()
            val bundleMapper = TelegramBundleToMediaItemMapper(metadataExtractor)
            val adapter = TelegramPipelineAdapter(transportClient, bundler, bundleMapper)

            UnifiedLog.i(TAG, "Telegram pipeline initialized")
            adapter
        } catch (e: Exception) {
            UnifiedLog.e(TAG, "Failed to initialize Telegram pipeline", e)
            null
        }

    private suspend fun initXtreamPipeline(config: XtreamPipelineConfig): XtreamPipelineAdapter? =
        try {
            UnifiedLog.d(TAG) { "Initializing Xtream pipeline: ${config.baseUrl}" }

            // Create HTTP client with reasonable timeouts
            val httpClient =
                OkHttpClient
                    .Builder()
                    .connectTimeout(30, TimeUnit.SECONDS)
                    .readTimeout(60, TimeUnit.SECONDS)
                    .writeTimeout(30, TimeUnit.SECONDS)
                    .build()

            // Create API client with default parallelism (conservative for startup)
            val apiConfig = config.toApiConfig()
            val apiClient =
                DefaultXtreamApiClient(
                    http = httpClient,
                    parallelism = XtreamParallelism(DEFAULT_PARALLELISM),
                )
            xtreamClient = apiClient

            // Initialize and authenticate
            val result = apiClient.initialize(apiConfig)
            if (result.isFailure) {
                UnifiedLog.e(TAG) {
                    "Xtream initialization failed: ${result.exceptionOrNull()?.message}"
                }
                return null
            }

            // Create pipeline adapter
            val adapter = XtreamPipelineAdapter(apiClient)

            UnifiedLog.i(TAG, "Xtream pipeline initialized")
            adapter
        } catch (e: Exception) {
            UnifiedLog.e(TAG, "Failed to initialize Xtream pipeline", e)
            null
        }
}
