package com.fishit.player.core.imaging.di

import android.content.Context
import coil3.ImageLoader
import com.fishit.player.core.imaging.GlobalImageLoader
import com.fishit.player.core.imaging.fetcher.TelegramThumbFetcher
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import javax.inject.Named
import javax.inject.Singleton

/**
 * Hilt module for global image loading configuration.
 *
 * **Purpose:**
 * - Provides ImageLoader singleton via GlobalImageLoader
 * - Wires optional TelegramThumbFetcher.Factory (if available)
 * - Configures shared OkHttpClient for images
 *
 * **Architecture (Phase B2):**
 * - Migrated from app-v2 to core:ui-imaging
 * - Transport modules (telegram) provide TelegramThumbFetcher.Factory
 * - app-v2 no longer contains imaging DI code
 *
 * **Coil Integration Best Practice:**
 * - ImageLoader created via DI with dynamic cache sizing
 * - Application implements coil3.SingletonImageLoader.Factory
 * - Application delegates to this DI-provided ImageLoader
 * - Ensures proper lifecycle and testability
 *
 * @see com.fishit.player.core.imaging.GlobalImageLoader for cache config
 */
@Module
@InstallIn(SingletonComponent::class)
object ImagingModule {
    /**
     * Provides the shared OkHttpClient for image loading.
     *
     * Qualified with @Named("ImageOkHttpClient") to avoid conflicts with other
     * OkHttpClient bindings (e.g., XtreamTransportModule).
     *
     * Uses GlobalImageLoader defaults optimized for TV/mobile.
     */
    @Provides
    @Singleton
    @Named("ImageOkHttpClient")
    fun provideImageOkHttpClient(): OkHttpClient = GlobalImageLoader.createDefaultOkHttpClient()

    /**
     * Provides the configured ImageLoader singleton.
     *
     * Uses dynamic cache sizing based on device capabilities:
     * - 64-bit devices: 512-768 MB disk cache
     * - 32-bit devices: 256-384 MB disk cache
     * - Scales with available storage (2% of available space)
     *
     * **Integration:**
     * - Direct injection: @Inject lateinit var imageLoader: ImageLoader
     * - Coil's SingletonImageLoader via Application.newImageLoader()
     *
     * The Application implements SingletonImageLoader.Factory and delegates
     * to this DI-provided instance.
     *
     * @param telegramThumbFetcherFactory Optional Telegram thumbnail fetcher
     *        (provided by infra/transport-telegram if available)
     */
    @Provides
    @Singleton
    fun provideImageLoader(
        @ApplicationContext context: Context,
        @Named("ImageOkHttpClient") okHttpClient: OkHttpClient,
        telegramThumbFetcherFactory: TelegramThumbFetcher.Factory?,
    ): ImageLoader =
        GlobalImageLoader.createWithDynamicCache(
            context = context,
            okHttpClient = okHttpClient,
            telegramThumbFetcher = telegramThumbFetcherFactory,
            enableCrossfade = true,
            crossfadeDurationMs = 200,
        )
}
