package com.fishit.player.infra.networking.di

import com.fishit.player.infra.networking.PlatformHttpConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

/**
 * Hilt module for app-wide platform HTTP client.
 *
 * Provides [@PlatformHttpClient] — the **parent** OkHttpClient that all pipeline-specific
 * clients derive from via [OkHttpClient.newBuilder()].
 *
 * **What's included (shared across ALL pipelines):**
 * - Connection pool (OkHttp shares it via newBuilder())
 * - Chucker HTTP Inspector (gated, debug only — all traffic visible in one place)
 * - User-Agent: FishIT-Player/2.x (Android)
 * - Default timeouts: connect/read/write 30s
 * - followRedirects: true (OkHttp default)
 * - followSslRedirects: true (OkHttp default)
 *
 * **What's NOT included (pipeline-specific):**
 * - callTimeout (Xtream=30s, streaming=180s, platform default=0/unlimited)
 * - Accept header (Xtream=application/json)
 * - Dispatcher limits (Xtream uses device-class parallelism)
 * - SSL redirect override (Xtream disables followSslRedirects)
 *
 * **Debug Runtime Toggles:**
 * - Chucker HTTP Inspector: OFF by default, gated via GatedChuckerInterceptor
 * - User can enable/disable via Settings (debug builds only)
 *
 * @see PlatformHttpClient
 * @see PlatformHttpConfig
 */
@Module
@InstallIn(SingletonComponent::class)
object NetworkingModule {
    /**
     * Provides the platform-level OkHttpClient.
     *
     * Pipeline modules inject this via `@PlatformHttpClient` and derive
     * their own client:
     *
     * ```kotlin
     * @Provides @XtreamHttpClient
     * fun provide(@PlatformHttpClient platform: OkHttpClient) =
     *     platform.newBuilder()
     *         .addInterceptor { /* Accept: application/json */ }
     *         .callTimeout(30, SECONDS)
     *         .build()
     * ```
     *
     * @param chuckerInterceptor GatedChuckerInterceptor (debug) or no-op (release),
     *   provided by source-set-specific [DebugInterceptorModule].
     */
    @Provides
    @Singleton
    @PlatformHttpClient
    fun providePlatformHttpClient(chuckerInterceptor: Interceptor): OkHttpClient =
        OkHttpClient
            .Builder()
            .connectTimeout(PlatformHttpConfig.CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(PlatformHttpConfig.READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(PlatformHttpConfig.WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            // NO callTimeout — pipeline-specific (Xtream=30s, streaming=180s)
            // Redirect handling: OkHttp defaults (both true)
            .followRedirects(true)
            .followSslRedirects(true)
            // Chucker HTTP Inspector (gated in debug, no-op in release)
            .addInterceptor(chuckerInterceptor)
            // User-Agent header (app-wide, all pipelines)
            .addInterceptor { chain ->
                val request =
                    chain
                        .request()
                        .newBuilder()
                        .header("User-Agent", PlatformHttpConfig.USER_AGENT)
                        .build()
                chain.proceed(request)
            }.build()
}
