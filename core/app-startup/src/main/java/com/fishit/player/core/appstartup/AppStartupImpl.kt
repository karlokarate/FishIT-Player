package com.fishit.player.core.appstartup

import com.fishit.player.infra.logging.UnifiedLog
import com.fishit.player.infra.transport.telegram.TelegramClient
import com.fishit.player.infra.transport.telegram.TelegramClientFactory
import com.fishit.player.infra.transport.xtream.DefaultXtreamApiClient
import com.fishit.player.infra.transport.xtream.XtreamApiClient
import com.fishit.player.infra.transport.xtream.XtreamDiscovery
import com.fishit.player.infra.transport.xtream.XtreamParallelism
import com.fishit.player.infra.transport.xtream.XtreamUrlBuilder
import com.fishit.player.infra.transport.xtream.client.XtreamCategoryFetcher
import com.fishit.player.infra.transport.xtream.client.XtreamConnectionManager
import com.fishit.player.infra.transport.xtream.client.XtreamStreamFetcher
import com.fishit.player.infra.transport.xtream.strategy.CategoryFallbackStrategy
import com.fishit.player.pipeline.telegram.adapter.TelegramPipelineAdapter
import com.fishit.player.pipeline.telegram.grouper.TelegramMessageBundler
import com.fishit.player.pipeline.telegram.grouper.TelegramStructuredMetadataExtractor
import com.fishit.player.pipeline.telegram.mapper.TelegramBundleToMediaItemMapper
import com.fishit.player.pipeline.xtream.adapter.XtreamPipelineAdapter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.serialization.json.Json
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
 * **Thread Safety:** This class is NOT thread-safe. Call [startPipelines] once per application
 * lifecycle.
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

    private var telegramClient: TelegramClient? = null
    private var xtreamClient: XtreamApiClient? = null

    override suspend fun startPipelines(config: AppStartupConfig): Pipelines {
        UnifiedLog.i(TAG, "Starting pipelines...")

        val telegramAdapter =
            config.telegram?.let { telegramConfig -> initTelegramPipeline(telegramConfig) }

        val xtreamAdapter = config.xtream?.let { xtreamConfig -> initXtreamPipeline(xtreamConfig) }

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

            // Create unified client from existing session (v2 pattern)
            val client =
                TelegramClientFactory.createUnifiedClient(
                    config = config.sessionConfig,
                    scope = scope,
                )
            telegramClient = client

            // Create pipeline adapter with bundler and mapper
            // TelegramClient implements TelegramAuthClient + TelegramHistoryClient
            val metadataExtractor = TelegramStructuredMetadataExtractor()
            val bundler = TelegramMessageBundler()
            val bundleMapper = TelegramBundleToMediaItemMapper(metadataExtractor)
            val adapter =
                TelegramPipelineAdapter(
                    authClient = client,
                    historyClient = client,
                    bundler = bundler,
                    bundleMapper = bundleMapper,
                )

            UnifiedLog.i(TAG, "Telegram pipeline initialized")
            adapter
        } catch (e: Exception) {
            UnifiedLog.e(TAG, "Failed to initialize Telegram pipeline", e)
            null
        }

    private suspend fun initXtreamPipeline(config: XtreamPipelineConfig): XtreamPipelineAdapter? {
        return try {
            UnifiedLog.d(TAG, "Initializing Xtream pipeline: ${config.baseUrl}")

            // Create base OkHttpClient with reasonable timeouts for CLI usage
            val okHttpClient =
                OkHttpClient
                    .Builder()
                    .connectTimeout(30, TimeUnit.SECONDS)
                    .readTimeout(60, TimeUnit.SECONDS)
                    .writeTimeout(30, TimeUnit.SECONDS)
                    .build()

            // Create shared dependencies for handler-based architecture (Sprint 5 refactor)
            val json = Json { ignoreUnknownKeys = true }
            val urlBuilder = XtreamUrlBuilder()
            val parallelism = XtreamParallelism(DEFAULT_PARALLELISM)
            val discovery = XtreamDiscovery(okHttpClient, json, parallelism, Dispatchers.IO)
            val categoryFallback = CategoryFallbackStrategy()

            // Create handlers — all use the same OkHttpClient (SSOT)
            val connectionManager = XtreamConnectionManager(
                okHttpClient = okHttpClient,
                json = json,
                urlBuilder = urlBuilder,
                discovery = discovery,
                io = Dispatchers.IO,
            )
            val categoryFetcher = XtreamCategoryFetcher(
                okHttpClient = okHttpClient,
                json = json,
                urlBuilder = urlBuilder,
                io = Dispatchers.IO,
            )
            val streamFetcher = XtreamStreamFetcher(
                okHttpClient = okHttpClient,
                json = json,
                urlBuilder = urlBuilder,
                categoryFallbackStrategy = categoryFallback,
                io = Dispatchers.IO,
            )

            // Create API client with handler injection
            val apiConfig = config.toApiConfig()
            val apiClient = DefaultXtreamApiClient(
                connectionManager = connectionManager,
                categoryFetcher = categoryFetcher,
                streamFetcher = streamFetcher,
            )
            xtreamClient = apiClient

            // Initialize and authenticate
            val result = apiClient.initialize(apiConfig)
            if (result.isFailure) {
                UnifiedLog.e(
                    TAG,
                    "Xtream initialization failed: ${result.exceptionOrNull()?.message}",
                )
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
}
