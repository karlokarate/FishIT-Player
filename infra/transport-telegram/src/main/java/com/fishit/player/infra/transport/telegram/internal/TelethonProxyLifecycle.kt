package com.fishit.player.infra.transport.telegram.internal

import android.content.Context
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import com.fishit.player.infra.logging.UnifiedLog
import com.fishit.player.infra.transport.telegram.TelegramSessionConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Manages the Telethon Python proxy lifecycle via Chaquopy.
 *
 * Responsibilities:
 * - Initializes Chaquopy Python runtime
 * - Sets environment variables for the Python proxy
 * - Starts the proxy server in a background thread
 * - Provides health-check polling until proxy is ready
 *
 * Thread-safety: [start] is idempotent — safe to call multiple times.
 *
 * @param healthClient DI-provided OkHttpClient with short timeouts for health polling.
 *                     Constructed in [TelegramTransportModule].
 */
class TelethonProxyLifecycle(
    private val context: Context,
    private val config: TelegramSessionConfig,
    private val healthClient: OkHttpClient,
) {
    private val started = AtomicBoolean(false)

    companion object {
        private const val TAG = "TelethonProxyLifecycle"
    }

    /**
     * Start the Telethon proxy.
     *
     * Initializes Chaquopy (if needed), sets env vars, and calls
     * `tg_proxy.start_server()` on the Python side. The proxy runs on a
     * daemon thread so this method returns immediately.
     *
     * Idempotent — calling multiple times is safe.
     */
    fun start() {
        if (!started.compareAndSet(false, true)) return

        UnifiedLog.i(TAG) { "Starting Telethon proxy (port=${config.proxyPort})..." }

        try {
            // Initialize Chaquopy
            if (!Python.isStarted()) {
                Python.start(AndroidPlatform(context))
            }

            val py = Python.getInstance()

            // Set environment variables BEFORE importing the Python module
            val os = py.getModule("os")
            val environ = os["environ"]!!
            environ.callAttr("__setitem__", "TG_API_ID", config.apiId.toString())
            environ.callAttr("__setitem__", "TG_API_HASH", config.apiHash)
            environ.callAttr("__setitem__", "TG_SESSION_PATH", config.sessionDir)
            environ.callAttr("__setitem__", "TG_PROXY_PORT", config.proxyPort.toString())

            // Import and start server
            val tgProxy = py.getModule("tg_proxy")
            tgProxy.callAttr("start_server")

            UnifiedLog.i(TAG) { "Telethon proxy start_server() called" }
        } catch (e: Exception) {
            started.set(false) // Allow retry on failure
            UnifiedLog.e(TAG, e) { "Failed to start Telethon proxy" }
            throw e
        }
    }

    /**
     * Wait until the proxy health endpoint responds.
     *
     * @param maxAttempts Maximum number of health check attempts
     * @param delayMs Delay between attempts in milliseconds
     * @return true if proxy is reachable, false if timed out
     */
    suspend fun awaitReady(
        maxAttempts: Int = 30,
        delayMs: Long = 500L,
    ): Boolean = withContext(Dispatchers.IO) {
        val healthUrl = "${config.proxyBaseUrl}/health"
        UnifiedLog.d(TAG) { "awaitReady: polling $healthUrl (max $maxAttempts attempts)" }
        repeat(maxAttempts) { attempt ->
            try {
                val request = Request.Builder().url(healthUrl).get().build()
                healthClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        UnifiedLog.i(TAG) { "Proxy ready after ${attempt + 1} attempt(s)" }
                        return@withContext true
                    }
                }
            } catch (_: Exception) {
                // Proxy not ready yet
            }
            delay(delayMs)
        }
        UnifiedLog.e(TAG) { "Proxy NOT ready after $maxAttempts attempts" }
        false
    }

    /** Whether [start] has been called. */
    val isStarted: Boolean get() = started.get()
}
