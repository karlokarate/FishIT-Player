package com.fishit.player.infra.http

import com.fishit.player.infra.http.interceptors.CacheInterceptor
import com.fishit.player.infra.http.interceptors.HeaderInterceptor
import com.fishit.player.infra.http.interceptors.RateLimitInterceptor
import com.fishit.player.infra.logging.UnifiedLog
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.InputStream
import java.io.PushbackInputStream
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPInputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Default implementation of HttpClient.
 *
 * Ported from DefaultXtreamApiClient with improvements:
 * - Generic for all HTTP-based pipelines (Fix for Finding #5, #9)
 * - Configurable rate limiting and caching (via interceptors)
 * - Automatic GZIP handling
 * - JSON validation
 * - Streaming support for large responses
 */
@Singleton
class DefaultHttpClient @Inject constructor(
    private val baseHttpClient: OkHttpClient,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : HttpClient {
    // Manual rate limiting and caching removed (Fix for Finding #10, #11)
    // All rate limiting and caching now handled by OkHttp interceptors

    companion object {
        private const val TAG = "HttpClient"
        private const val STREAMING_READ_TIMEOUT_SECONDS = 120L
        private const val STREAMING_CALL_TIMEOUT_SECONDS = 180L

        /**
         * Redact URL for safe logging: returns "host/path" only.
         * No query parameters (which may contain credentials) are logged.
         */
        private fun redactUrl(url: String): String {
            return try {
                val httpUrl = url.toHttpUrlOrNull()
                if (httpUrl != null) {
                    "${httpUrl.host}${httpUrl.encodedPath}"
                } else {
                    "<invalid-url>"
                }
            } catch (_: Exception) {
                "<invalid-url>"
            }
        }
    }

    override suspend fun fetch(
        url: String,
        config: RequestConfig,
    ): Result<String?> = withContext(io) {
        try {
            // Fix for Finding #7: Rate limiting is now always enforced at interceptor level
            // Fix for Finding #10: Caching is now handled at interceptor level
            // Both are independent concerns and handled by OkHttp interceptors

            // Build request
            val requestBuilder = Request.Builder().url(url)
            
            // Add custom headers from config (Fix for Finding #9)
            // Default headers should be set via HttpClientConfig, not hardcoded here
            config.headers.forEach { (name, value) ->
                requestBuilder.header(name, value)
            }

            val request = requestBuilder.get().build()
            val safeUrl = redactUrl(url)

            // Select client based on timeout requirement
            val client = if (config.useExtendedTimeout) {
                baseHttpClient.newBuilder()
                    .readTimeout(STREAMING_READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                    .callTimeout(STREAMING_CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                    .build()
            } else {
                baseHttpClient
            }

            // Execute request
            val response = client.newCall(request).execute()
            response.use { resp ->
                val contentType = resp.header("Content-Type") ?: "unknown"

                // Fix for Finding #6: HTTP errors should be Result.failure(), not success(null)
                if (!resp.isSuccessful) {
                    UnifiedLog.i(TAG) {
                        "HTTP ${resp.code} for $safeUrl | contentType=$contentType"
                    }
                    return@withContext Result.failure(
                        HttpException(resp.code, "HTTP ${resp.code} for $safeUrl")
                    )
                }

                // Get body as bytes first to allow gzip detection
                val bodyBytes = resp.body?.bytes() ?: return@withContext Result.success(null)

                UnifiedLog.d(TAG) {
                    "HTTP ${resp.code} for $safeUrl | bytes=${bodyBytes.size} contentType=$contentType"
                }

                if (bodyBytes.isEmpty()) {
                    return@withContext Result.success(null)
                }

                // Defensive gzip handling
                val body = decompressIfNeeded(bodyBytes, safeUrl)

                // Fix for Finding #5: Remove JSON-specific validation from generic HTTP client
                // Let callers decide what response format is valid for their use case
                // (M3U is text/plain, Jellyfin may use XML, etc.)
                UnifiedLog.d(TAG) { "Response received for $safeUrl (${body.length} chars)" }

                Result.success(body)
            }
        } catch (e: java.net.UnknownHostException) {
            UnifiedLog.i(TAG) { "DNS resolution failed for ${redactUrl(url)}" }
            Result.success(null)
        } catch (e: java.net.ConnectException) {
            UnifiedLog.i(TAG) { "Connection refused for ${redactUrl(url)} - ${e.message}" }
            Result.success(null)
        } catch (e: javax.net.ssl.SSLException) {
            UnifiedLog.i(TAG) { "SSL/TLS error for ${redactUrl(url)} - ${e.message}" }
            Result.success(null)
        } catch (e: java.net.SocketTimeoutException) {
            UnifiedLog.i(TAG) { "Timeout for ${redactUrl(url)}" }
            Result.success(null)
        } catch (e: java.io.IOException) {
            val message = e.message ?: ""
            if (message.contains("CLEARTEXT") || message.contains("cleartext")) {
                UnifiedLog.e(TAG) {
                    "Cleartext HTTP blocked! Enable usesCleartextTraffic in AndroidManifest for ${redactUrl(url)}"
                }
            } else {
                UnifiedLog.i(TAG) {
                    "IO error for ${redactUrl(url)} - ${e.javaClass.simpleName}: $message"
                }
            }
            Result.success(null)
        } catch (e: Exception) {
            UnifiedLog.e(TAG, e) { "Failed ${redactUrl(url)}" }
            Result.failure(e)
        }
    }

    override suspend fun fetchStream(
        url: String,
        config: RequestConfig,
    ): Result<InputStream?> = withContext(io) {
        try {
            // Rate limit
            takeRateSlot(extractHost(url))

            // Build request
            val requestBuilder = Request.Builder().url(url)
            
            // Add default headers
            requestBuilder.header("Accept", "application/json")
            requestBuilder.header("Accept-Encoding", "gzip")
            requestBuilder.header("User-Agent", "FishIT-Player/2.x (Android)")
            
            // Add custom headers
            config.headers.forEach { (name, value) ->
                requestBuilder.header(name, value)
            }

            val request = requestBuilder.get().build()

            // Select client based on timeout requirement
            val client = if (config.useExtendedTimeout) {
                baseHttpClient.newBuilder()
                    .readTimeout(STREAMING_READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                    .callTimeout(STREAMING_CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                    .build()
            } else {
                baseHttpClient
            }

            // Execute request (don't close response - caller will do it)
            val response = client.newCall(request).execute()

            if (!response.isSuccessful) {
                response.close()
                return@withContext Result.success(null)
            }

            // Get input stream
            val inputStream = response.body?.byteStream()
            if (inputStream == null) {
                response.close()
                return@withContext Result.success(null)
            }

            // Wrap in GZIP stream if needed
            val finalStream = wrapStreamIfGzipped(inputStream, response)
            Result.success(finalStream)
        } catch (e: Exception) {
            UnifiedLog.e(TAG, e) { "Failed to fetch stream for ${redactUrl(url)}" }
            Result.failure(e)
        }
    }

    override fun createOkHttpClient(config: HttpClientConfig): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .callTimeout(30, TimeUnit.SECONDS)

        // Add default headers if provided
        if (config.defaultHeaders.isNotEmpty()) {
            builder.addInterceptor(HeaderInterceptor(config.defaultHeaders))
        }

        // Add rate limiting if enabled
        if (config.enableRateLimiting) {
            builder.addInterceptor(RateLimitInterceptor(config.rateLimitIntervalMs))
        }

        // Add caching if enabled
        if (config.enableCaching) {
            builder.addInterceptor(CacheInterceptor())
        }

        return builder.build()
    }

    // =========================================================================
    // Internal Helpers
    // =========================================================================

    /**
     * Rate limiting - now no-op since handled exclusively by RateLimitInterceptor.
     * Fix for Finding #11: Removed duplicate rate limiting implementation.
     * Rate limiting is handled by OkHttp interceptors for cleaner architecture.
     */
    @Suppress("UNUSED_PARAMETER")
    private suspend fun takeRateSlot(host: String) {
        // No-op: Rate limiting handled by RateLimitInterceptor in OkHttp chain
        // This preserves the method signature for backward compatibility
    }

    /**
     * Manual caching removed - now no-op since handled by CacheInterceptor.
     * Fix for Finding #10: Removed duplicate caching implementation.
     * Caching is handled by OkHttp interceptors for proper HTTP semantics.
     */
    @Suppress("UNUSED_PARAMETER")
    private suspend fun readCache(url: String, ttlSeconds: Int): String? {
        // No-op: Always return null to indicate no manual cached value
        // Caching handled by CacheInterceptor in OkHttp chain
        return null
    }

    @Suppress("UNUSED_PARAMETER")
    private suspend fun writeCache(url: String, body: String) {
        // No-op: Caching handled by CacheInterceptor
    }

    private fun decompressIfNeeded(bodyBytes: ByteArray, safeUrl: String): String {
        // Check for gzip magic bytes: 0x1F 0x8B
        return if (bodyBytes.size >= 2 &&
            (bodyBytes[0].toInt() and 0xFF) == 0x1F &&
            (bodyBytes[1].toInt() and 0xFF) == 0x8B
        ) {
            try {
                val decompressed = GZIPInputStream(bodyBytes.inputStream()).use { gzipStream ->
                    gzipStream.bufferedReader().use { reader ->
                        reader.readText()
                    }
                }
                UnifiedLog.d(TAG) {
                    "Manually decompressed gzip for $safeUrl | original=${bodyBytes.size} decompressed=${decompressed.length}"
                }
                decompressed
            } catch (e: Exception) {
                UnifiedLog.w(TAG) {
                    "Failed to decompress suspected gzip body for $safeUrl - ${e.message}"
                }
                String(bodyBytes, Charsets.UTF_8)
            }
        } else {
            String(bodyBytes, Charsets.UTF_8)
        }
    }

    private fun wrapStreamIfGzipped(inputStream: InputStream, response: Response): InputStream {
        val contentEncoding = response.header("Content-Encoding")
        return if (contentEncoding?.contains("gzip", ignoreCase = true) == true) {
            GZIPInputStream(inputStream)
        } else {
            inputStream
        }
    }

    private fun extractHost(url: String): String {
        return try {
            url.toHttpUrlOrNull()?.host ?: ""
        } catch (_: Exception) {
            ""
        }
    }
}
