package com.fishit.player.v2.di

import android.content.Context
import coil3.ImageLoader
import com.fishit.player.core.imaging.GlobalImageLoader
import com.fishit.player.core.imaging.fetcher.TelegramThumbFetcher
import com.fishit.player.infra.transport.telegram.TelegramTransportClient
import com.fishit.player.v2.di.TelegramThumbFetcherImpl
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Provider
import javax.inject.Singleton
import okhttp3.OkHttpClient

/**
 * Hilt module for image loading configuration.
 *
 * **Purpose:**
 * - Provides [ImageLoader] singleton via [GlobalImageLoader]
 * - Wires [TelegramThumbFetcher.Factory] implementation
 * - Configures shared [OkHttpClient] for images
 *
 * **Architecture (IMAGING_SYSTEM.md):**
 * - core:ui-imaging defines GlobalImageLoader and TelegramThumbFetcher interface
 * - app-v2 wires the implementation via DI
 * - Transport layer resolves TDLib files, UI layer displays images
 *
 * **Coil Integration Best Practice:**
 * - ImageLoader is created via DI with dynamic cache sizing
 * - Application implements [coil3.SingletonImageLoader.Factory]
 * - Application delegates to this DI-provided ImageLoader
 * - This ensures proper lifecycle and testability
 *
 * @see FishItV2Application for the SingletonImageLoader.Factory implementation
 */
@Module
@InstallIn(SingletonComponent::class)
object ImagingModule {

    /**
     * Provides the shared OkHttpClient for image loading.
     * Uses GlobalImageLoader defaults optimized for TV/mobile.
     *
     * Qualified with [@ImageOkHttpClient] to avoid conflicts with other
     * OkHttpClient bindings (e.g., XtreamTransportModule).
     */
    @Provides
    @Singleton
    @ImageOkHttpClient
    fun provideImageOkHttpClient(): OkHttpClient {
        return GlobalImageLoader.createDefaultOkHttpClient()
    }

    /**
     * Provides the TelegramThumbFetcher.Factory implementation.
     */
    @Provides
    @Singleton
    fun provideTelegramThumbFetcherFactory(
        telegramClientProvider: Provider<TelegramTransportClient>
    ): TelegramThumbFetcher.Factory? {
        return runCatching {
            TelegramThumbFetcherImpl.Factory(telegramClientProvider.get())
        }.getOrNull()
    }

    /**
     * Provides the configured ImageLoader singleton.
     *
     * Uses dynamic cache sizing based on device capabilities (ported from v1):
     * - 64-bit devices: 512-768 MB disk cache
     * - 32-bit devices: 256-384 MB disk cache
     * - Scales with available storage (2% of available space)
     *
     * **Note:** This ImageLoader is accessed via:
     * 1. Direct injection (@Inject lateinit var imageLoader: ImageLoader)
     * 2. Coil's SingletonImageLoader (via Application.newImageLoader())
     *
     * The Application implements SingletonImageLoader.Factory and delegates
     * to this DI-provided instance for proper initialization order.
     */
    @Provides
    @Singleton
    fun provideImageLoader(
        @ApplicationContext context: Context,
        @ImageOkHttpClient okHttpClient: OkHttpClient,
        telegramThumbFetcherFactory: TelegramThumbFetcher.Factory?
    ): ImageLoader {
        return GlobalImageLoader.createWithDynamicCache(
            context = context,
            okHttpClient = okHttpClient,
            telegramThumbFetcher = telegramThumbFetcherFactory,
            enableCrossfade = true,
            crossfadeDurationMs = 200
        )
    }
}
