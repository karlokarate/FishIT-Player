package com.fishit.player.infra.transport.xtream.di

import android.content.Context
import com.fishit.player.infra.transport.xtream.DefaultXtreamApiClient
import com.fishit.player.infra.transport.xtream.EncryptedXtreamCredentialsStore
import com.fishit.player.infra.transport.xtream.XtreamApiClient
import com.fishit.player.infra.transport.xtream.XtreamCredentialsStore
import com.fishit.player.infra.transport.xtream.XtreamDiscovery
import com.fishit.player.infra.transport.xtream.XtreamParallelism
import com.fishit.player.infra.transport.xtream.XtreamTransportConfig
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient

/**
 * Qualifier for Xtream-specific OkHttpClient with Premium Contract settings.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class XtreamHttpClient

/**
 * Hilt module for Xtream transport layer.
 *
 * Provides XtreamApiClient, XtreamDiscovery, and related transport components.
 *
 * Premium Contract Compliance:
 * - Section 3: HTTP Timeouts (connect/read/write/call all 30s)
 * - Section 4: User-Agent FishIT-Player/2.x (Android)
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
     * Premium Contract Section 5:
     * - Phone/Tablet: 10
     * - FireTV/low-RAM: 3
     *
     * This value is used by:
     * - OkHttp Dispatcher limits
     * - All coroutine Semaphores in DefaultXtreamApiClient and XtreamDiscovery
     */
    @Provides
    @Singleton
    fun provideXtreamParallelism(
        @ApplicationContext context: Context,
    ): XtreamParallelism = XtreamParallelism(XtreamTransportConfig.getParallelism(context))

    /**
     * Provides Xtream-specific OkHttpClient with Premium Contract settings.
     *
     * Timeouts per Section 3:
     * - connectTimeout: 30s
     * - readTimeout: 30s
     * - writeTimeout: 30s
     * - callTimeout: 30s (mandatory)
     *
     * Headers per Section 4:
     * - User-Agent: FishIT-Player/2.x (Android)
     * - Accept: application/json
     * - Accept-Encoding: gzip
     *
     * Parallelism per Section 5:
     * - Phone/Tablet: maxRequests=10, maxRequestsPerHost=10
     * - FireTV/low-RAM: maxRequests=3, maxRequestsPerHost=3
     */
    @Provides
    @Singleton
    @XtreamHttpClient
    fun provideXtreamOkHttpClient(
        @ApplicationContext context: Context,
        parallelism: XtreamParallelism,
    ): OkHttpClient {
        return OkHttpClient.Builder()
            // Premium Contract Section 3: HTTP Timeouts
            .connectTimeout(XtreamTransportConfig.CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(XtreamTransportConfig.READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(XtreamTransportConfig.WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .callTimeout(XtreamTransportConfig.CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            // Redirect handling (many Xtream panels use HTTP on non-standard ports)
            .followRedirects(true)
            .followSslRedirects(false)
            // Premium Contract Section 4: Headers via interceptor
            .addInterceptor { chain ->
                val original = chain.request()
                val builder = original.newBuilder()

                // Always set User-Agent per Premium Contract
                builder.header("User-Agent", XtreamTransportConfig.USER_AGENT)
                // Accept headers
                if (original.header("Accept") == null) {
                    builder.header("Accept", XtreamTransportConfig.ACCEPT_JSON)
                }
                if (original.header("Accept-Encoding") == null) {
                    builder.header("Accept-Encoding", XtreamTransportConfig.ACCEPT_ENCODING)
                }

                chain.proceed(builder.build())
            }
            .apply {
                // Premium Contract Section 5: Device-class parallelism
                dispatcher(
                    okhttp3.Dispatcher().apply {
                        maxRequests = parallelism.value
                        maxRequestsPerHost = parallelism.value
                    },
                )
            }
            .build()
    }

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    @Provides
    @Singleton
    fun provideXtreamDiscovery(
        @XtreamHttpClient okHttpClient: OkHttpClient,
        json: Json,
        parallelism: XtreamParallelism,
    ): XtreamDiscovery = XtreamDiscovery(okHttpClient, json, parallelism = parallelism)

    @Provides
    @Singleton
    fun provideXtreamApiClient(
        @XtreamHttpClient okHttpClient: OkHttpClient,
        json: Json,
        parallelism: XtreamParallelism,
    ): XtreamApiClient = DefaultXtreamApiClient(okHttpClient, json, parallelism = parallelism)
}

/** Hilt module for Xtream credentials storage. */
@Module
@InstallIn(SingletonComponent::class)
abstract class XtreamCredentialsModule {
    @Binds
    @Singleton
    abstract fun bindCredentialsStore(impl: EncryptedXtreamCredentialsStore): XtreamCredentialsStore
}
