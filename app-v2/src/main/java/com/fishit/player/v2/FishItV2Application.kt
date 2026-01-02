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
import com.fishit.player.v2.debug.LeakCanaryConfig
import com.fishit.player.v2.di.AppScopeModule
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Named
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

    // Debug-only bootstraps (null in release)
    @Inject
    lateinit var debugBootstrapsProvider: dagger.Lazy<DebugBootstraps>

    override fun onCreate() {
        super.onCreate()

        // Contract S-1: UnifiedLog MUST be initialized BEFORE any other subsystem
        UnifiedLogInitializer.init(isDebug = BuildConfig.DEBUG)

        // Contract S-1.1: LeakCanary configuration (debug builds only, after logging)
        // NOTE: LeakCanary is now configured by DebugToolsInitializer (OFF by default)
        if (BuildConfig.DEBUG) {
            LeakCanaryConfig.install(this)
        }

        // Contract S-1.2: Start DebugToolsInitializer (syncs DataStore to runtime flags)
        if (BuildConfig.DEBUG) {
            try {
                debugBootstrapsProvider.get().start(appScope)
            } catch (e: Exception) {
                UnifiedLog.w(TAG) { "DebugBootstraps not available (expected in release): ${e.message}" }
            }
        }

        // WorkManager initialization (after logging is ready)
        val workManagerInitialized = WORK_MANAGER_INITIALIZED.compareAndSet(false, true)
        if (workManagerInitialized) {
            WorkManager.initialize(this, workConfiguration)
            UnifiedLog.i(TAG) { "WorkManager initialized" }
        }

        // Contract S-2: Start lightweight observers (no heavy work)
        sourceActivationObserver.start(appScope)
        telegramActivationObserver.start()

        // Contract S-3: Bootstraps started here ONLY (not in MainActivity or AppNavHost)
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
