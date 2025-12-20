package com.fishit.player.v2

import android.app.Application
import androidx.work.Configuration
import androidx.work.WorkManager
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import com.fishit.player.infra.logging.UnifiedLog
import com.fishit.player.infra.logging.UnifiedLogInitializer
import com.fishit.player.infra.work.SourceActivationObserver
import com.fishit.player.v2.di.AppScopeModule
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Provider
import java.util.concurrent.atomic.AtomicBoolean

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
    SingletonImageLoader.Factory,
    Configuration.Provider {
    /**
     * Lazy provider for ImageLoader to avoid DI initialization order issues.
     * Hilt ensures this is initialized before newImageLoader() is called.
     */
    @Inject
    lateinit var imageLoaderProvider: Provider<ImageLoader>

    @Inject
    lateinit var workConfiguration: Configuration
    
    // Bootstrap components
    @Inject
    lateinit var xtreamSessionBootstrap: XtreamSessionBootstrap
    
    @Inject
    lateinit var catalogSyncBootstrap: CatalogSyncBootstrap
    
    @Inject
    lateinit var telegramActivationObserver: TelegramActivationObserver
    
    @Inject
    lateinit var sourceActivationObserver: SourceActivationObserver
    
    @Inject
    @Named(AppScopeModule.APP_LIFECYCLE_SCOPE)
    lateinit var appScope: CoroutineScope

    override fun onCreate() {
        super.onCreate()

        val workManagerInitialized = WORK_MANAGER_INITIALIZED.compareAndSet(false, true)
        if (workManagerInitialized) {
            WorkManager.initialize(this, workConfiguration)
        }

        // Early initialization of unified logging system to ensure all subsequent logging works correctly
        UnifiedLogInitializer.init(isDebug = BuildConfig.DEBUG)

        if (workManagerInitialized) {
            UnifiedLog.i(TAG) { "WorkManager initialized" }
        }

        // Start source activation observers (must be before bootstraps)
        sourceActivationObserver.start(appScope)
        telegramActivationObserver.start()
        
        // Start session bootstraps
        xtreamSessionBootstrap.start()
        catalogSyncBootstrap.start()
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

    override val workManagerConfiguration: Configuration
        get() = workConfiguration

    companion object {
        private const val TAG = "FishItV2Application"
        private val WORK_MANAGER_INITIALIZED = AtomicBoolean(false)
    }
}
