package com.fishit.player.infra.http

/**
 * Configuration for HTTP client cache behavior.
 *
 * @param ttlSeconds Time-to-live in seconds for cached responses
 */
data class CacheConfig(
    val ttlSeconds: Int = 60,
) {
    companion object {
        val DEFAULT = CacheConfig(ttlSeconds = 60)
        val EPG = CacheConfig(ttlSeconds = 15)
        val DISABLED = CacheConfig(ttlSeconds = 0)
    }
}

/**
 * Configuration for a single HTTP request.
 *
 * @param headers Additional headers to include in the request
 * @param cache Cache configuration for this request
 * @param useExtendedTimeout Use extended timeout for streaming large responses
 */
data class RequestConfig(
    val headers: Map<String, String> = emptyMap(),
    val cache: CacheConfig = CacheConfig.DEFAULT,
    val useExtendedTimeout: Boolean = false,
)

/**
 * Configuration for HTTP client behavior.
 *
 * @param baseUrl Optional base URL to prepend to all requests
 * @param defaultHeaders Headers to include in all requests
 * @param rateLimitIntervalMs Minimum interval between requests in milliseconds
 * @param enableRateLimiting Whether to enforce rate limiting
 * @param enableCaching Whether to enable response caching
 */
data class HttpClientConfig(
    val baseUrl: String? = null,
    val defaultHeaders: Map<String, String> = emptyMap(),
    val rateLimitIntervalMs: Long = 120L,
    val enableRateLimiting: Boolean = true,
    val enableCaching: Boolean = true,
) {
    companion object {
        val DEFAULT = HttpClientConfig()
    }
}
