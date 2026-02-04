package com.fishit.player.infra.http.interceptors

import android.os.SystemClock
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody

/**
 * Application-level response cache interceptor.
 *
 * This provides in-memory caching with configurable TTL,
 * independent of HTTP cache headers.
 *
 * Note: This is intended for API responses, not for general HTTP caching.
 * For HTTP caching, use OkHttp's built-in Cache.
 */
class CacheInterceptor : Interceptor {
    private val mutex = Mutex()
    private val cache = object : LinkedHashMap<String, CacheEntry>(512, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CacheEntry>?): Boolean {
            return size > 512
        }
    }

    data class CacheEntry(
        val timestamp: Long,
        val body: String,
        val contentType: String,
    )

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val url = request.url.toString()

        // Check if cache TTL is specified in request tag
        val ttlSeconds = request.tag(CacheTtlTag::class.java)?.ttlSeconds ?: 0

        if (ttlSeconds > 0) {
            // Check cache first
            val cached = runBlocking {
                mutex.withLock {
                    cache[url]?.let { entry ->
                        val age = (SystemClock.elapsedRealtime() - entry.timestamp) / 1000
                        if (age <= ttlSeconds) entry else null
                    }
                }
            }

            if (cached != null) {
                // Return cached response
                return Response.Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK (cached)")
                    .body(cached.body.toResponseBody(cached.contentType.toMediaType()))
                    .build()
            }
        }

        // Execute request
        val response = chain.proceed(request)

        // Cache successful responses if TTL is set
        if (response.isSuccessful && ttlSeconds > 0) {
            val responseBody = response.body
            val bodyString = responseBody?.string() ?: ""
            val contentType = responseBody?.contentType()?.toString() ?: "application/json"

            runBlocking {
                mutex.withLock {
                    cache[url] = CacheEntry(
                        timestamp = SystemClock.elapsedRealtime(),
                        body = bodyString,
                        contentType = contentType,
                    )
                }
            }

            // Return new response with cached body
            return response.newBuilder()
                .body(bodyString.toResponseBody(contentType.toMediaType()))
                .build()
        }

        return response
    }

    /**
     * Tag for specifying cache TTL in OkHttp request.
     */
    data class CacheTtlTag(val ttlSeconds: Int)
}
