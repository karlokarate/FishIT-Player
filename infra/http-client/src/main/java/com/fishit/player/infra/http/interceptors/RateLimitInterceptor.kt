package com.fishit.player.infra.http.interceptors

import android.os.SystemClock
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.Interceptor
import okhttp3.Response

/**
 * OkHttp interceptor for per-host rate limiting.
 *
 * Enforces a minimum interval between requests to the same host
 * to prevent overwhelming servers.
 *
 * @param intervalMs Minimum interval between requests in milliseconds
 */
class RateLimitInterceptor(
    private val intervalMs: Long = 120L,
) : Interceptor {
    private val mutex = Mutex()
    private val lastCallByHost = mutableMapOf<String, Long>()

    override fun intercept(chain: Interceptor.Chain): Response {
        val host = chain.request().url.host

        // Rate limit per host
        runBlocking {
            mutex.withLock {
                val now = SystemClock.elapsedRealtime()
                val lastCall = lastCallByHost[host] ?: 0L
                val delta = now - lastCall
                if (delta in 0 until intervalMs) {
                    delay(intervalMs - delta)
                }
                lastCallByHost[host] = SystemClock.elapsedRealtime()
            }
        }

        return chain.proceed(chain.request())
    }
}
