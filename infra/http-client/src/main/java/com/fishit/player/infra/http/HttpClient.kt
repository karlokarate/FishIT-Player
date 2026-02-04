package com.fishit.player.infra.http

import okhttp3.OkHttpClient
import java.io.InputStream

/**
 * Generic HTTP client interface for all HTTP-based pipelines.
 *
 * Provides:
 * - Response caching
 * - Rate limiting
 * - GZIP handling
 * - Streaming support for large responses
 *
 * This is the SSOT for HTTP operations across all pipelines (Xtream, M3U, Jellyfin, etc.)
 */
interface HttpClient {
    /**
     * Fetch HTTP response as a string.
     *
     * Features:
     * - Automatic response caching with configurable TTL
     * - Rate limiting per host
     * - GZIP decompression (automatic and manual fallback)
     * - JSON validation
     *
     * @param url The URL to fetch
     * @param config Request configuration (headers, cache, timeout)
     * @return Result with response body, or null if request failed
     */
    suspend fun fetch(
        url: String,
        config: RequestConfig = RequestConfig(),
    ): Result<String?>

    /**
     * Fetch HTTP response as an InputStream for streaming parsing.
     *
     * Benefits:
     * - O(1) memory regardless of response size
     * - No full body loading into memory
     * - Automatic GZIP decompression
     *
     * Caller must close the InputStream when done.
     *
     * @param url The URL to fetch
     * @param config Request configuration
     * @return Result with InputStream, or null if request failed
     */
    suspend fun fetchStream(
        url: String,
        config: RequestConfig = RequestConfig(),
    ): Result<InputStream?>

    /**
     * Create an OkHttpClient with the specified configuration.
     *
     * This can be used by transport layers that need direct OkHttpClient access
     * for specialized operations (e.g., Telegram file downloads).
     *
     * @param config HTTP client configuration
     * @return Configured OkHttpClient instance
     */
    fun createOkHttpClient(config: HttpClientConfig = HttpClientConfig.DEFAULT): OkHttpClient
}
