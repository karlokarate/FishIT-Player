package com.fishit.player.v2

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import com.fishit.player.infra.logging.UnifiedLogInitializer
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import javax.inject.Provider

/**
 * FishIT Player v2 Application.
 *
 * Entry point for the v2 generation of FishIT Player.
 * Uses Hilt for dependency injection.
 *
 * **Image Loading (Coil 3 Best Practice):**
 * Implements [SingletonImageLoader.Factory] to provide the ImageLoader at Application level.
 * This ensures:
 * - ImageLoader is available before any UI component accesses it
 * - Proper lifecycle management (Application scope)
 * - DI-provided ImageLoader for testability
 * - No race conditions with setSafe() workarounds
 *
 * @see com.fishit.player.v2.di.ImagingModule for ImageLoader configuration
 */
@HiltAndroidApp
class FishItV2Application :
    Application(),
    SingletonImageLoader.Factory {
    /**
     * Lazy provider for ImageLoader to avoid DI initialization order issues.
     * Hilt ensures this is initialized before newImageLoader() is called.
     */
    @Inject
    lateinit var imageLoaderProvider: Provider<ImageLoader>

    override fun onCreate() {
        super.onCreate()

        // Initialize unified logging system FIRST
        // This ensures all subsequent logging works correctly
        UnifiedLogInitializer.init(isDebug = BuildConfig.DEBUG)

        // Initialization logic will be added in later phases:
        // - DeviceProfile detection
        // - Local profile loading
        // - Pipeline initialization (background)
    }

    /**
     * Coil 3 SingletonImageLoader.Factory implementation.
     *
     * Called by Coil when any component (AsyncImage, FishImage, etc.) needs
     * an ImageLoader and none was explicitly provided via LocalImageLoader.
     *
     * This delegates to the Hilt-provided ImageLoader singleton which is
     * configured with:
     * - Dynamic disk cache sizing (device-aware)
     * - TelegramThumbFetcher for Telegram thumbnail resolution
     * - Custom OkHttpClient with appropriate timeouts
     *
     * @param context Platform context (unused, we use DI)
     * @return The DI-configured ImageLoader singleton
     */
    override fun newImageLoader(context: PlatformContext): ImageLoader = imageLoaderProvider.get()
}
