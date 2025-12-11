package com.fishit.player.v2.di

import android.content.Context
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import com.fishit.player.core.imaging.GlobalImageLoader
import com.fishit.player.core.imaging.fetcher.TelegramThumbFetcher
import com.fishit.player.infra.transport.telegram.TelegramTransportClient
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
 */
@Module
@InstallIn(SingletonComponent::class)
object ImagingModule {

    /**
     * Provides the shared OkHttpClient for image loading.
     * Uses GlobalImageLoader defaults optimized for TV/mobile.
     */
    @Provides
    @Singleton
    fun provideImageOkHttpClient(): OkHttpClient {
        return GlobalImageLoader.createDefaultOkHttpClient()
    }

    /**
     * Provides the TelegramThumbFetcher.Factory implementation.
     * Delegates to TelegramTransportClient for TDLib file resolution.
     */
    @Provides
    @Singleton
    fun provideTelegramThumbFetcherFactory(
        telegramClient: TelegramTransportClient
    ): TelegramThumbFetcher.Factory {
        return TelegramThumbFetcherImpl.Factory(telegramClient)
    }

    /**
     * Provides the configured ImageLoader singleton.
     * Also registers as Coil's SingletonImageLoader for global access.
     */
    @Provides
    @Singleton
    fun provideImageLoader(
        @ApplicationContext context: Context,
        okHttpClient: OkHttpClient,
        telegramThumbFetcherFactory: TelegramThumbFetcher.Factory
    ): ImageLoader {
        val imageLoader = GlobalImageLoader.create(
            context = context,
            okHttpClient = okHttpClient,
            telegramThumbFetcher = telegramThumbFetcherFactory,
            enableCrossfade = true,
            crossfadeDurationMs = 200
        )

        // Register as Coil singleton for CompositionLocal fallback
        SingletonImageLoader.setSafe { imageLoader }

        return imageLoader
    }
}
