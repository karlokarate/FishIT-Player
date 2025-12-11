package com.fishit.player.v2.di

import android.content.Context
import coil3.ImageLoader
import com.fishit.player.core.imaging.GlobalImageLoader
import com.fishit.player.core.imaging.fetcher.TelegramThumbFetcher
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
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
     *
     * Returns null since TelegramTransportClient requires TdlibClientProvider
     * which is not yet wired in v2. When Telegram integration is needed,
     * this should be updated to inject TelegramTransportClient.
     *
     * GlobalImageLoader handles null TelegramThumbFetcher.Factory gracefully
     * by not registering the Telegram fetcher (HTTP and LocalFile still work).
     */
    @Provides
    @Singleton
    fun provideTelegramThumbFetcherFactory(): TelegramThumbFetcher.Factory? {
        // TODO: Wire TelegramTransportClient when TdlibClientProvider is available
        // return TelegramThumbFetcherImpl.Factory(telegramClient)
        return null
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
