package com.chris.m3usuite

import android.app.Application
import android.content.Intent
import android.util.Log
import com.chris.m3usuite.core.telemetry.FrameTimeWatchdog
import com.chris.m3usuite.core.telemetry.Telemetry
import com.chris.m3usuite.prefs.SettingsStore
import com.chris.m3usuite.telegram.service.TelegramServiceClient
import com.chris.m3usuite.telegram.service.TelegramTdlibService
import com.chris.m3usuite.tg.TgGate
import java.io.Closeable
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class App : Application() {
    private var telemetryCloser: Closeable? = null
    private var appScope: CoroutineScope? = null
    private var telegramClient: TelegramServiceClient? = null

    override fun onCreate() {
        super.onCreate()
        Telemetry.registerDefault(this)
        telemetryCloser = FrameTimeWatchdog.install()
        appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val serviceIntent = Intent(this, TelegramTdlibService::class.java)
        val serviceStarted = AtomicBoolean(false)
        fun startTdlibServiceOnce() {
            if (serviceStarted.compareAndSet(false, true)) {
                startService(serviceIntent)
                Log.i("App", "TDLib service started at app init")
            } else {
                startService(serviceIntent)
            }
        }
        if (TgGate.mirrorOnly()) {
            startTdlibServiceOnce()
        }
        // Auto-start TDLib if Telegram is enabled and keys are present, so auth state persists across restarts.
        appScope?.launch(Dispatchers.IO) {
            val store = SettingsStore(applicationContext)
            // Migrate legacy Telegram selections (if any) → unified CSV
            runCatching { store.migrateTelegramSelectedChatsIfNeeded() }
            val enabled = runCatching { store.tgEnabled.first() }.getOrDefault(false)
            if (enabled) {
                startTdlibServiceOnce()
            } else {
                Log.i("App", "TG rebuild skipped: Telegram disabled")
                return@launch
            }
            val apiId = runCatching { store.tgApiId.first() }.getOrDefault(0).takeIf { it > 0 } ?: BuildConfig.TG_API_ID
            val apiHash = runCatching { store.tgApiHash.first() }.getOrDefault("").ifBlank { BuildConfig.TG_API_HASH }
            if (apiId > 0 && apiHash.isNotBlank()) {
                val svc = TelegramServiceClient(applicationContext)
                svc.bind()
                svc.start(apiId, apiHash)
                svc.getAuth()
                telegramClient = svc
                if (TgGate.mirrorOnly()) {
                    Log.i("App", "TG rebuild skipped: MirrorOnly active")
                } else {
                    // Best-effort: rebuild aggregated Telegram series on app start so Library row is present
                    kotlin.runCatching {
                        com.chris.m3usuite.data.repo.TelegramSeriesIndexer.rebuild(applicationContext)
                    }.onFailure {
                        Log.w("App", "TG rebuild failed", it)
                    }
                }
            }
        }
    }

    // Global image loader: our AsyncImage wrappers use AppImageLoader directly.
}
