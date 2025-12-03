package com.chris.m3usuite

import android.app.Application
import com.chris.m3usuite.core.logging.CrashHandler
import com.chris.m3usuite.core.telemetry.FrameTimeWatchdog
import com.chris.m3usuite.core.telemetry.Telemetry
import com.chris.m3usuite.data.repo.TelegramContentRepository
import com.chris.m3usuite.prefs.SettingsStore
import com.chris.m3usuite.telegram.core.T_TelegramServiceClient
import com.chris.m3usuite.telegram.logging.TelegramLogRepository
import com.chris.m3usuite.telegram.prefetch.TelegramThumbPrefetcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.Closeable

class App : Application() {
    private var telemetryCloser: Closeable? = null

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private lateinit var telegramPrefetcher: TelegramThumbPrefetcher

    override fun onCreate() {
        super.onCreate()

        // Install crash handler FIRST - before any other initialization
        CrashHandler.install(this)

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
        val store = SettingsStore(this)
        applicationScope.launch {
            store.logTelemetryEnabled.collect { enabled -> Telemetry.setExternalEnabled(enabled) }
        }

        // Start Telegram thumbnail prefetcher
        val serviceClient = T_TelegramServiceClient.getInstance(this)
        val tgRepo = TelegramContentRepository(this, store)

        // Phase 4: Create settings provider for prefetcher
        val settingsProvider =
            com.chris.m3usuite.telegram.domain.TelegramStreamingSettingsProviderHolder
                .get(this)

        telegramPrefetcher = TelegramThumbPrefetcher(this, serviceClient, tgRepo, settingsProvider)

        // Register in holder for global access
        com.chris.m3usuite.telegram.prefetch.TelegramPrefetcherHolder
            .set(telegramPrefetcher)

        applicationScope.launch { telegramPrefetcher.start(this) }

        // Auto-start Telegram engine if enabled is persisted as true (fixes toggle OFF/ON
        // requirement)
        applicationScope.launch(Dispatchers.IO) {
            try {
                val enabled = store.tgEnabled.first()
                val hasApiCreds = store.tgApiId.first() != 0 && store.tgApiHash.first().isNotBlank()

                if (enabled && hasApiCreds) {
                    TelegramLogRepository.info(
                        source = "App",
                        message = "Auto-starting Telegram engine (tgEnabled=true persisted)",
                    )
                    try {
                        serviceClient.ensureStarted(this@App, store)
                        serviceClient.login() // Let TDLib determine if session is valid
                        TelegramLogRepository.info(
                            source = "App",
                            message = "Telegram auto-start completed successfully",
                        )
                    } catch (e: Exception) {
                        TelegramLogRepository.warn(
                            source = "App",
                            message = "Telegram auto-start failed: ${e.message}",
                        )
                    }
                }
            } catch (e: Exception) {
                TelegramLogRepository.warn(
                    source = "App",
                    message = "Failed to check Telegram auto-start condition: ${e.message}",
                )
            }
        }
    }

    // Global image loader: our AsyncImage wrappers use AppImageLoader directly.
}
