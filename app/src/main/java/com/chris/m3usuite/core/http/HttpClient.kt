package com.chris.m3usuite.core.http

import android.content.Context
import com.chris.m3usuite.prefs.SettingsStore
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * In-memory cookie jar with merge + expiry + basic domain/path matching.
 * Kept as a singleton instance inside HttpClientFactory so cookies persist across requests.
 */
class MergingCookieJar : CookieJar {
    private val lock = Any()
    private val store = mutableListOf<Cookie>()

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        synchronized(lock) {
            val now = System.currentTimeMillis()
            // Remove expired first
            store.removeAll { it.expiresAt < now }
            for (c in cookies) {
                // Replace by (name, domain, path)
                store.removeAll { it.name == c.name && it.domain.equals(c.domain, true) && it.path == c.path }
                store.add(c)
            }
        }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val now = System.currentTimeMillis()
        val host = url.host
        val path = url.encodedPath
        val https = url.isHttps
        synchronized(lock) {
            // Remove expired
            store.removeAll { it.expiresAt < now }
            return store.filter { c ->
                (!c.secure || https) &&
                (host.equals(c.domain, true) || host.endsWith("." + c.domain, true)) &&
                path.startsWith(c.path)
            }
        }
    }
}

object HttpClientFactory {
    @Volatile private var client: OkHttpClient? = null
    @Volatile private var cookieJar: PersistentCookieJar? = null

    fun create(context: Context, settings: SettingsStore): OkHttpClient {
        val existing = client
        if (existing != null) return existing

        val seeded = RequestHeadersProvider.ensureSeededBlocking(settings)
        // TrafficLogger enabled state is driven from Settings UI toggle at runtime
        val jar = PersistentCookieJar.get(context).also { cookieJar = it }

        val built = OkHttpClient.Builder()
            .cookieJar(jar)
            .connectTimeout(120, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(120, TimeUnit.SECONDS)
            .followRedirects(true)
            .retryOnConnectionFailure(true)
            // Retry interceptor for HTTP 5xx with 1.5x backoff, up to 2 retries
            .addInterceptor { chain ->
                var attempt = 0
                val maxRetries = 2
                var backoffMs = 500L
                var req = chain.request()
                var res = chain.proceed(req)
                while (attempt < maxRetries && res.code in 500..599) {
                    try {
                        res.close()
                    } catch (_: Throwable) {}
                    try { Thread.sleep(backoffMs) } catch (_: InterruptedException) { }
                    backoffMs = (backoffMs * 1.5).toLong().coerceAtLeast(1L)
                    attempt++
                    res = chain.proceed(req)
                }
                res
            }
            .addInterceptor { chain ->
                val headers = RequestHeadersProvider.snapshot().ifEmpty { seeded }
                val req = chain.request()
                val nb = req.newBuilder().header("Accept", "*/*")
                for ((k, v) in headers) nb.header(k, v)
                // Keep player_api.php headers minimal (some WAFs are sensitive to Origin/Referer/Accept-Language)
                val outReq = nb.build()
                val start = System.nanoTime()
                val res = chain.proceed(outReq)
                TrafficLogger.tryLog(appContext = context, request = outReq, response = res, startedNs = start)
                res
            }
            .build()

        client = built
        return built
    }
}
