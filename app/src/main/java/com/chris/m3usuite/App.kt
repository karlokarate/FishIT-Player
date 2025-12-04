package com.chris.m3usuite

import android.app.Application
import android.util.Log
import com.chris.m3usuite.core.logging.CrashHandler
import com.chris.m3usuite.core.logging.UnifiedLog
import com.chris.m3usuite.core.telemetry.FrameTimeWatchdog
import com.chris.m3usuite.core.telemetry.Telemetry
import com.chris.m3usuite.data.repo.TelegramContentRepository
import com.chris.m3usuite.prefs.SettingsStore
import com.chris.m3usuite.telegram.core.T_TelegramServiceClient
import com.chris.m3usuite.telegram.prefetch.TelegramThumbPrefetcher
import com.google.firebase.FirebaseApp
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.perf.FirebasePerformance
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.remoteConfigSettings
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

        // Initialize Firebase FIRST - before crash handlers can use Crashlytics
        initializeFirebase()

        // Install crash handler after Firebase - it uses Crashlytics
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

        // Migrate advanced settings to safe ranges on startup
        applicationScope.launch(Dispatchers.IO) {
            try {
                store.migrateAdvancedSettings()
                UnifiedLog.info(
                    source = "App",
                    message = "Advanced settings migration completed",
                )
            } catch (e: Exception) {
                UnifiedLog.warn(
                    source = "App",
                    message = "Advanced settings migration failed: ${e.message}",
                )
            }
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
                    UnifiedLog.info(
                        source = "App",
                        message = "Auto-starting Telegram engine (tgEnabled=true persisted)",
                    )
                    try {
                        serviceClient.ensureStarted(this@App, store)
                        serviceClient.login() // Let TDLib determine if session is valid
                        UnifiedLog.info(
                            source = "App",
                            message = "Telegram auto-start completed successfully",
                        )
                    } catch (e: Exception) {
                        UnifiedLog.warn(
                            source = "App",
                            message = "Telegram auto-start failed: ${e.message}",
                        )
                    }
                }
            } catch (e: Exception) {
                UnifiedLog.warn(
                    source = "App",
                    message = "Failed to check Telegram auto-start condition: ${e.message}",
                )
            }
        }
    }

    /**
     * Initialize all Firebase services.
     * Called before any other initialization to ensure Crashlytics captures all crashes.
     */
    private fun initializeFirebase() {
        try {
            // Initialize Firebase (usually auto-initialized via google-services.json, but explicit is safer)
            FirebaseApp.initializeApp(this)

            // Configure Crashlytics
            val crashlytics = FirebaseCrashlytics.getInstance()
            crashlytics.setCrashlyticsCollectionEnabled(true)
            crashlytics.setCustomKey("app_version", BuildConfig.VERSION_NAME)
            crashlytics.setCustomKey("version_code", BuildConfig.VERSION_CODE)
            crashlytics.setCustomKey("build_type", BuildConfig.BUILD_TYPE)

            // Configure Performance Monitoring
            val perf = FirebasePerformance.getInstance()
            perf.isPerformanceCollectionEnabled = true

            // Configure Remote Config with sensible defaults
            val remoteConfig = FirebaseRemoteConfig.getInstance()
            val configSettings =
                remoteConfigSettings {
                    minimumFetchIntervalInSeconds = if (BuildConfig.DEBUG) 0 else 3600 // 1 hour in release
                }
            remoteConfig.setConfigSettingsAsync(configSettings)

            // Fetch remote config in background
            remoteConfig.fetchAndActivate()

            Log.i(TAG, "Firebase initialized successfully (Crashlytics, Performance, RemoteConfig)")
        } catch (e: Exception) {
            Log.e(TAG, "Firebase initialization failed", e)
        }
    }

    companion object {
        private const val TAG = "App"
    }
}
