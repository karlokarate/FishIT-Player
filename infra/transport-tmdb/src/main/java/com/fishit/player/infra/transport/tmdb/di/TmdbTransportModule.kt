package com.fishit.player.infra.transport.tmdb.di

import com.fishit.player.infra.transport.tmdb.api.TmdbGateway
import com.fishit.player.infra.transport.tmdb.api.TmdbRequestParams
import com.fishit.player.infra.transport.tmdb.internal.TmdbGatewayImpl
import com.uwetrottmann.tmdb2.Tmdb
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.Cache
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton

/**
 * Hilt module providing TMDB gateway dependencies.
 *
 * Configures:
 * - TMDB API client with API key
 * - OkHttpClient with timeouts and caching
 * - TmdbRequestParams provider for default locale/region
 */
@Module
@InstallIn(SingletonComponent::class)
object TmdbTransportModule {
    /**
     * Provide TMDB API client.
     *
     * API key is injected via BuildConfig (from environment or local.properties).
     */
    @Provides
    @Singleton
    fun provideTmdb(apiKey: String): Tmdb = Tmdb(apiKey)

    /**
     * Provide TMDB API key.
     *
     * TODO: Read from BuildConfig or Secrets when available.
     * For now, returns empty string (will cause Unauthorized errors).
     */
    @Provides
    @Singleton
    fun provideTmdbApiKey(): String {
        // TODO: Inject from BuildConfig.TMDB_API_KEY or Secrets
        // For CI/testing, this can remain empty (tests use FakeTmdbGateway)
        return System.getenv("TMDB_API_KEY") ?: ""
    }

    /**
     * Provide OkHttpClient configured for TMDB.
     *
     * Configuration:
     * - Connect timeout: 15s
     * - Read timeout: 30s
     * - Write timeout: 30s
     * - Disk cache: 10MB (optional, can be expanded)
     */
    @Provides
    @Singleton
    @TmdbOkHttpClient
    fun provideTmdbOkHttpClient(
        @TmdbCacheDir cacheDir: File?,
    ): OkHttpClient =
        OkHttpClient
            .Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .apply {
                if (cacheDir != null) {
                    cache(Cache(cacheDir, 10L * 1024 * 1024)) // 10MB cache
                }
            }.build()

    /**
     * Provide cache directory for TMDB HTTP cache.
     *
     * TODO: Use app-provided cache directory when available.
     * For now, returns null (no caching).
     */
    @Provides
    @Singleton
    @TmdbCacheDir
    fun provideTmdbCacheDir(): File? {
        // TODO: Inject from Application.cacheDir/tmdb
        // For now, disable caching
        return null
    }

    /**
     * Provide default TMDB request parameters.
     *
     * TODO: Read locale/region from app settings when available.
     * For now, uses en-US with no region.
     */
    @Provides
    @Singleton
    fun provideTmdbRequestParams(): TmdbRequestParams {
        // TODO: Inject from LocaleProvider or SettingsRepository
        // For now, default to en-US
        return TmdbRequestParams(language = "en-US", region = null)
    }
}

/**
 * Hilt binding module for TmdbGateway interface.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class TmdbGatewayModule {
    @Binds
    @Singleton
    abstract fun bindTmdbGateway(impl: TmdbGatewayImpl): TmdbGateway
}

/**
 * Qualifier for TMDB-specific OkHttpClient.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class TmdbOkHttpClient

/**
 * Qualifier for TMDB cache directory.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class TmdbCacheDir
