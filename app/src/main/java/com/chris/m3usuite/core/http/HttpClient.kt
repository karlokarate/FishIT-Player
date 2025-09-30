package com.chris.m3usuite.core.http

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.StatFs
import com.chris.m3usuite.prefs.SettingsStore
import okhttp3.Cache
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.TimeUnit
import okhttp3.Dispatcher
import com.chris.m3usuite.core.device.DeviceProfile

/**
 * HttpClientFactory – ohne zusätzliche Abhängigkeiten.
 *
 * Änderungen:
 * - **Kein** HttpLoggingInterceptor (kein neues Gradle-Dependency nötig).
 * - Einheitlicher Header-Injector + TrafficLogger.
 * - 5xx-Retry mit einfacher Backoff-Schleife (max 2 Versuche).
 */
object HttpClientFactory {
    @Volatile private var client: OkHttpClient? = null
    @Volatile private var cookieJar: PersistentCookieJar? = null

    fun create(context: Context, settings: SettingsStore): OkHttpClient {
        client?.let { return it }

        // Seed headers synchron (für Aufrufer ohne Lifecycle-Scope)
        val seeded = RequestHeadersProvider.ensureSeededBlocking(settings)
        val jar = PersistentCookieJar.get(context).also { cookieJar = it }

        // Dynamic HTTP response cache (MiB):
        // 32-bit base 32 (low-ram 24), cap 64; 64-bit base 96 (low-ram 64), cap 128
        val cacheDir = File(context.cacheDir, "http_cache").apply { mkdirs() }
        val httpCache = Cache(cacheDir, computeHttpCacheSizeBytes(context, cacheDir))

        val builder = OkHttpClient.Builder()
            .cookieJar(jar)
            .cache(httpCache)
            .connectTimeout(120, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(120, TimeUnit.SECONDS)
            .followRedirects(true)
            .retryOnConnectionFailure(true)
            // --- 5xx-Retry (2 Wiederholungen) ---
            .addInterceptor { chain ->
                var attempt = 0
                val maxRetries = 2
                var backoffMs = 500L
                val req0 = chain.request()
                var res = chain.proceed(req0)
                while (attempt < maxRetries && res.code in 500..599) {
                    try { res.close() } catch (_: Throwable) {}
                    try { Thread.sleep(backoffMs) } catch (_: InterruptedException) {}
                    backoffMs = (backoffMs * 1.5).toLong().coerceAtLeast(1L)
                    attempt++
                    res = chain.proceed(req0)
                }
                res
            }
            // --- Header & Traffic-Log ---
            .addInterceptor { chain ->
                val dynamic = RequestHeadersProvider.snapshot().ifEmpty { seeded }
                val inReq = chain.request()
                val nb = inReq.newBuilder()
                    .header("Accept", "*/*")
                for ((k, v) in dynamic) nb.header(k, v)
                val outReq = nb.build()
                val start = System.nanoTime()
                val res = chain.proceed(outReq)
                TrafficLogger.tryLog(appContext = context, request = outReq, response = res, startedNs = start)
                res
            }

        // TV low-spec: throttle OkHttp concurrency for smoother UI
        if (DeviceProfile.isTvLowSpec(context)) {
            val disp = Dispatcher().apply {
                maxRequests = 16
                maxRequestsPerHost = 4
            }
            builder.dispatcher(disp)
        }

        val built = builder.build()

        client = built
        return built
    }
}

private fun computeHttpCacheSizeBytes(context: Context, cacheDir: File): Long {
    val MB = 1024L * 1024L
    val is64 = try { Build.SUPPORTED_64_BIT_ABIS.isNotEmpty() } catch (_: Throwable) { false }
    val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
    val isLowRam = am?.isLowRamDevice == true

    val baseMiB = if (is64) 96 else 32
    val lowMiB = if (is64) 64 else 24
    val capMiB = if (is64) 128 else 64
    val chosenBase = if (isLowRam) lowMiB else baseMiB

    // 1% of available space on the cache filesystem
    val stat = runCatching { StatFs(cacheDir.absolutePath) }.getOrNull()
    val availBytes = stat?.availableBytes ?: 0L
    val onePercent = (availBytes / 100L).coerceAtLeast(0L)

    val minBytes = chosenBase * MB
    val maxBytes = capMiB * MB
    return minOf(maxBytes, maxOf(minBytes, onePercent))
}
