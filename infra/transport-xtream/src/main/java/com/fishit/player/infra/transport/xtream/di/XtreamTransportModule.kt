package com.fishit.player.infra.transport.xtream.di

import android.content.Context
import com.fishit.player.core.device.DeviceClassProvider
import com.fishit.player.infra.networking.di.PlatformHttpClient
import com.fishit.player.infra.transport.xtream.DefaultXtreamApiClient
import com.fishit.player.infra.transport.xtream.EncryptedXtreamCredentialsStore
import com.fishit.player.infra.transport.xtream.XtreamApiClient
import com.fishit.player.infra.transport.xtream.XtreamCredentialsStore
import com.fishit.player.infra.transport.xtream.XtreamDiscovery
import com.fishit.player.infra.transport.xtream.XtreamParallelism
import com.fishit.player.infra.transport.xtream.XtreamTransportConfig
import com.fishit.player.infra.transport.xtream.XtreamUrlBuilder
import com.fishit.player.infra.transport.xtream.client.XtreamCategoryFetcher
import com.fishit.player.infra.transport.xtream.client.XtreamConnectionManager
import com.fishit.player.infra.transport.xtream.client.XtreamStreamFetcher
import com.fishit.player.infra.transport.xtream.strategy.CategoryFallbackStrategy
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton

/**
 * Qualifier for Xtream-specific OkHttpClient — **child** of [@PlatformHttpClient].
 *
 * Derived via [OkHttpClient.newBuilder()] from the platform client, adding:
 * - Accept: application/json
 * - callTimeout: 30s (Premium Contract Section 3)
 * - followSslRedirects: false (Xtream security)
 * - Device-class Dispatcher parallelism (Premium Contract Section 5)
 *
 * Inherits from platform: connection pool, Chucker, User-Agent, base timeouts.
 *
 * @see PlatformHttpClient the parent client
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class XtreamHttpClient

/**
 * Hilt module for Xtream transport layer.
 *
 * Provides XtreamApiClient, XtreamDiscovery, and related transport components.
 *
 * **Parent–Child HTTP Client Architecture:**
 * - [@PlatformHttpClient] (infra/networking) provides shared infrastructure
 * - [@XtreamHttpClient] derives via `.newBuilder()`, adding Xtream-specific settings
 *
 * Premium Contract Compliance:
 * - Section 3: callTimeout 30s (added here; connect/read/write inherited from platform)
 * - Section 4: Accept: application/json (added here; User-Agent inherited from platform)
 * - Section 5: Device-class parallelism (OkHttp Dispatcher limits)
 *
 * @see <a href="contracts/XTREAM_SCAN_PREMIUM_CONTRACT_V1.md">Premium Contract</a>
 */
@Module
@InstallIn(SingletonComponent::class)
object XtreamTransportModule {
    /**
     * Provides the device-aware parallelism as SSOT.
     *
     * Uses DeviceClassProvider from core:device-api for proper PLATIN architecture.
     *
     * Premium Contract Section 5:
     * - Phone/Tablet/TV: 12
     * - TV_LOW_RAM: 3
     *
     * This value is used by:
     * - OkHttp Dispatcher limits
     * - All coroutine Semaphores in DefaultXtreamApiClient and XtreamDiscovery
     *
     * @param deviceClassProvider Injected provider for device classification
     * @param context Application context for device detection
     * @return XtreamParallelism wrapper with appropriate parallelism level
     */
    @Provides
    @Singleton
    fun provideXtreamParallelism(
        deviceClassProvider: DeviceClassProvider,
        @ApplicationContext context: Context,
    ): XtreamParallelism =
        XtreamParallelism(
            XtreamTransportConfig.getParallelism(deviceClassProvider, context),
        )

    /**
     * Provides Xtream-specific OkHttpClient — derived from [@PlatformHttpClient].
     *
     * **Inherited from platform (via .newBuilder(), shared connection pool):**
     * - connectTimeout: 30s, readTimeout: 30s, writeTimeout: 30s
     * - User-Agent: FishIT-Player/2.x (Android)
     * - Chucker HTTP Inspector (gated)
     * - followRedirects: true, followSslRedirects: true
     *
     * **Added here (Xtream-specific):**
     * - callTimeout: 30s (Premium Contract Section 3 — mandatory hard stop)
     * - followSslRedirects: false (Xtream panels often use non-standard SSL)
     * - Accept: application/json (Xtream API returns JSON)
     * - Dispatcher parallelism per device class (Premium Contract Section 5)
     */
    @Provides
    @Singleton
    @XtreamHttpClient
    fun provideXtreamOkHttpClient(
        @PlatformHttpClient platformClient: OkHttpClient,
        parallelism: XtreamParallelism,
    ): OkHttpClient =
        platformClient
            .newBuilder()
            // Xtream-specific: hard call timeout (Premium Contract Section 3)
            .callTimeout(XtreamTransportConfig.CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            // Xtream-specific: many panels use HTTP on non-standard ports
            .followSslRedirects(false)
            // Xtream-specific: Accept header for JSON API responses
            .addInterceptor { chain ->
                val original = chain.request()
                if (original.header("Accept") == null) {
                    chain.proceed(
                        original
                            .newBuilder()
                            .header("Accept", XtreamTransportConfig.ACCEPT_JSON)
                            .build(),
                    )
                } else {
                    chain.proceed(original)
                }
            }.apply {
                // Premium Contract Section 5: Device-class parallelism
                dispatcher(
                    okhttp3.Dispatcher().apply {
                        maxRequests = parallelism.value
                        maxRequestsPerHost = parallelism.value
                    },
                )
            }.build()

    @Provides
    @Singleton
    fun provideJson(): Json =
        Json {
            ignoreUnknownKeys = true
            isLenient = true
            coerceInputValues = true
        }

    @Provides
    @Singleton
    fun provideXtreamUrlBuilder(): XtreamUrlBuilder = XtreamUrlBuilder()

    @Provides
    @Singleton
    fun provideXtreamDiscovery(
        @XtreamHttpClient okHttpClient: OkHttpClient,
        json: Json,
        parallelism: XtreamParallelism,
    ): XtreamDiscovery = XtreamDiscovery(okHttpClient, json, parallelism = parallelism)

    /**
     * Provides XtreamConnectionManager - handles init/ping/close lifecycle.
     */
    @Provides
    @Singleton
    fun provideConnectionManager(
        @XtreamHttpClient okHttpClient: OkHttpClient,
        json: Json,
        urlBuilder: XtreamUrlBuilder,
        discovery: XtreamDiscovery,
    ): XtreamConnectionManager = XtreamConnectionManager(okHttpClient, json, urlBuilder, discovery)

    /**
     * Provides XtreamCategoryFetcher - handles category operations.
     */
    @Provides
    @Singleton
    fun provideCategoryFetcher(
        @XtreamHttpClient okHttpClient: OkHttpClient,
        json: Json,
        urlBuilder: XtreamUrlBuilder,
    ): XtreamCategoryFetcher = XtreamCategoryFetcher(okHttpClient, json, urlBuilder)

    /**
     * Provides XtreamStreamFetcher - handles stream fetching and batch operations.
     */
    @Provides
    @Singleton
    fun provideStreamFetcher(
        @XtreamHttpClient okHttpClient: OkHttpClient,
        json: Json,
        urlBuilder: XtreamUrlBuilder,
        fallbackStrategy: CategoryFallbackStrategy,
    ): XtreamStreamFetcher = XtreamStreamFetcher(okHttpClient, json, urlBuilder, fallbackStrategy)

    /**
     * Provides XtreamApiClient with handler injection.
     *
     * REFACTORED: Now delegates to specialized handlers:
     * - connectionManager: init/ping/close lifecycle
     * - categoryFetcher: category operations
     * - streamFetcher: stream fetching and batch operations
     *
     * Target metrics: ~800 lines, CC ≤ 10 (down from 2312 lines, CC ~52)
     */
    @Provides
    @Singleton
    fun provideXtreamApiClient(
        connectionManager: XtreamConnectionManager,
        categoryFetcher: XtreamCategoryFetcher,
        streamFetcher: XtreamStreamFetcher,
    ): XtreamApiClient =
        DefaultXtreamApiClient(
            connectionManager,
            categoryFetcher,
            streamFetcher,
        )
}

/** Hilt module for Xtream credentials storage. */
@Module
@InstallIn(SingletonComponent::class)
abstract class XtreamCredentialsModule {
    @Binds
    @Singleton
    abstract fun bindCredentialsStore(impl: EncryptedXtreamCredentialsStore): XtreamCredentialsStore
}
