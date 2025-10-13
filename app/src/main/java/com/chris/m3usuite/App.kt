package com.chris.m3usuite

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import com.chris.m3usuite.prefs.SettingsStore
import com.chris.m3usuite.telegram.service.TelegramServiceClient
import com.chris.m3usuite.core.telemetry.Telemetry
import com.chris.m3usuite.core.telemetry.FrameTimeWatchdog
import java.io.Closeable

class App : Application() {
    private var telemetryCloser: Closeable? = null
    private var appScope: CoroutineScope? = null
    private var telegramClient: TelegramServiceClient? = null

    override fun onCreate() {
        super.onCreate()
        Telemetry.registerDefault(this)
        telemetryCloser = FrameTimeWatchdog.install()
        appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        // Auto-start TDLib if Telegram is enabled and keys are present, so auth state persists across restarts.
        appScope?.launch {
            val store = SettingsStore(applicationContext)
            val enabled = runCatching { store.tgEnabled.first() }.getOrDefault(false)
            if (!enabled) return@launch
            val apiId = runCatching { store.tgApiId.first() }.getOrDefault(0).takeIf { it > 0 } ?: BuildConfig.TG_API_ID
            val apiHash = runCatching { store.tgApiHash.first() }.getOrDefault("").ifBlank { BuildConfig.TG_API_HASH }
            if (apiId > 0 && apiHash.isNotBlank()) {
                val svc = TelegramServiceClient(applicationContext)
                svc.bind()
                svc.start(apiId, apiHash)
                svc.getAuth()
                telegramClient = svc
                // Best-effort: rebuild aggregated Telegram series on app start so Library row is present
                kotlin.runCatching { com.chris.m3usuite.data.repo.TelegramSeriesIndexer.rebuild(applicationContext) }
            }
        }
    }

    // Global image loader: our AsyncImage wrappers use AppImageLoader directly.
}
