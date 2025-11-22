package com.chris.m3usuite

import android.app.Application
import com.chris.m3usuite.core.telemetry.FrameTimeWatchdog
import com.chris.m3usuite.core.telemetry.Telemetry
import com.chris.m3usuite.data.repo.TelegramContentRepository
import com.chris.m3usuite.prefs.SettingsStore
import com.chris.m3usuite.telegram.core.T_TelegramServiceClient
import com.chris.m3usuite.telegram.prefetch.TelegramThumbPrefetcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.Closeable

class App : Application() {
    private var telemetryCloser: Closeable? = null

    private val applicationScope =
        CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private lateinit var telegramPrefetcher: TelegramThumbPrefetcher

    override fun onCreate() {
        super.onCreate()

        // Initialize debug tools in debug builds
        if (BuildConfig.DEBUG) {
            try {
                val debugClass = Class.forName("com.chris.m3usuite.DebugToolsInitializer")
                val initMethod = debugClass.getMethod("initialize", Application::class.java)
                initMethod.invoke(null, this)
            } catch (e: Exception) {
                // Debug initializer not available (e.g., in release builds)
            }
        }

        Telemetry.registerDefault(this)
        telemetryCloser = FrameTimeWatchdog.install()

        // Start Telegram thumbnail prefetcher
        val serviceClient = T_TelegramServiceClient.getInstance(this)
        val store = SettingsStore(this)
        val tgRepo = TelegramContentRepository(this, store)
        telegramPrefetcher = TelegramThumbPrefetcher(this, serviceClient, tgRepo)

        // Register in holder for global access
        com.chris.m3usuite.telegram.prefetch.TelegramPrefetcherHolder.set(telegramPrefetcher)

        applicationScope.launch {
            telegramPrefetcher.start(this)
        }
    }

    // Global image loader: our AsyncImage wrappers use AppImageLoader directly.
}
