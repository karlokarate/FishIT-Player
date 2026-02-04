package com.fishit.player.infra.http.interceptors

import android.os.SystemClock
import okhttp3.Interceptor
import okhttp3.Response

/**
 * OkHttp interceptor for per-host rate limiting.
 *
 * Enforces a minimum interval between requests to the same host
 * to prevent overwhelming servers.
 *
 * Fix for Finding #2: Use synchronized blocks instead of runBlocking.
 * This avoids blocking OkHttp's I/O threads with coroutine overhead.
 *
 * @param intervalMs Minimum interval between requests in milliseconds
 */
class RateLimitInterceptor(
    private val intervalMs: Long = 120L,
) : Interceptor {
    private val lastCallByHost = mutableMapOf<String, Long>()

    override fun intercept(chain: Interceptor.Chain): Response {
        val host = chain.request().url.host

        // Rate limit per host using synchronized block
        synchronized(lastCallByHost) {
            val now = SystemClock.elapsedRealtime()
            val lastCall = lastCallByHost[host] ?: 0L
            val delta = now - lastCall
            if (delta in 0 until intervalMs) {
                Thread.sleep(intervalMs - delta)
            }
            lastCallByHost[host] = SystemClock.elapsedRealtime()
        }

        return chain.proceed(chain.request())
    }
}
