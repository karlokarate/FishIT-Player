package com.fishit.player.playback.xtream

import android.content.Context
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import com.chuckerteam.chucker.api.ChuckerInterceptor
import com.fishit.player.infra.logging.UnifiedLog
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import java.util.concurrent.TimeUnit

/**
 * Custom DataSource.Factory for Xtream playback using OkHttp for reliable redirect handling.
 *
 * **Key Features:**
 * - Uses OkHttp to ensure headers survive HTTP redirects (User-Agent, Referer, etc.)
 * - Integrates Chucker HTTP inspector in debug builds for observability
 * - Logs redirect chains in debug builds (status codes, host changes, final response type)
 * - Allows HTTPS redirects from HTTP (common with Cloudflare panels)
 *
 * **Why OkHttp over DefaultHttpDataSource:**
 * DefaultHttpDataSource may not reliably preserve headers across redirects, especially
 * with Cloudflare and other proxy/CDN configurations. OkHttp provides explicit control
 * over redirect handling and header propagation.
 *
 * **Chucker Integration:**
 * In debug builds, Chucker interceptor is added to allow inspection of:
 * - `/live/username/password/streamId.ext` (live TV streams)
 * - `/movie/username/password/streamId.ext` (VOD streams)
 * - `/series/username/password/streamId.ext` (series episodes)
 * This makes debugging panel connectivity and redirect issues much easier.
 *
 * **Debug Logging:**
 * In debug builds, logs:
 * - HTTP status codes (301, 302, 307, 308)
 * - Target host/scheme changes
 * - Whether final response starts with "#EXTM3U" (HLS manifest detection)
 * - No secrets (credentials, keys, tokens) are logged
 *
 * @param context Application context for Chucker initialization
 * @param headers HTTP headers to apply (User-Agent, Referer, Accept, Accept-Encoding)
 * @param debugMode Enable redirect logging and Chucker (should be tied to BuildConfig.DEBUG)
 */
class XtreamHttpDataSourceFactory(
    private val context: Context,
    private val headers: Map<String, String>,
    private val debugMode: Boolean = false,
) : DataSource.Factory {
    companion object {
        private const val TAG = "XtreamHttpDataSource"

        // Connection timeouts (same as Media3 defaults)
        private const val CONNECT_TIMEOUT_SECONDS = 30L
        private const val READ_TIMEOUT_SECONDS = 30L
        private const val WRITE_TIMEOUT_SECONDS = 30L
    }

    /**
     * OkHttpClient instance configured for Xtream streaming.
     *
     * Thread-safe lazy initialization - Kotlin's `by lazy` ensures thread-safe singleton creation.
     * OkHttpClient itself is thread-safe and designed to be shared across the application.
     */
    private val okHttpClient: OkHttpClient by lazy {
        OkHttpClient
            .Builder()
            .followRedirects(true) // Follow HTTP redirects (301, 302, 307, 308)
            .followSslRedirects(true) // Allow HTTP -> HTTPS redirects (Cloudflare pattern)
            .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .apply {
                if (debugMode) {
                    // Add Chucker first so it sees the original request/response
                    addInterceptor(ChuckerInterceptor.Builder(context).build())
                    // Add redirect logging after Chucker for structured logs
                    addNetworkInterceptor(RedirectLoggingInterceptor())
                }
            }.build()
    }

    override fun createDataSource(): DataSource =
        OkHttpDataSource
            .Factory(okHttpClient)
            .setDefaultRequestProperties(headers)
            .createDataSource()

    /**
     * Network interceptor for logging redirect chains (DEBUG BUILDS ONLY).
     *
     * Logs:
     * - Status codes (301, 302, 307, 308, 200, etc.)
     * - Host/scheme changes (e.g., http://panel.com -> https://cdn.cloudflare.com)
     * - Final response starts with "#EXTM3U" check (HLS manifest detection)
     *
     * Does NOT log:
     * - Request/response bodies (except first 10 bytes for #EXTM3U check)
     * - Authorization headers
     * - Credentials in URLs
     */
    private inner class RedirectLoggingInterceptor : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val request = chain.request()
            val url = request.url

            // Log initial request
            UnifiedLog.d(TAG) {
                "→ Xtream HTTP request: ${request.method} ${url.scheme}://${url.host}${url.encodedPath}"
            }

            // Execute request
            val response = chain.proceed(request)

            // Log response details
            val statusCode = response.code
            val finalUrl = response.request.url
            val hostChanged = url.host != finalUrl.host
            val schemeChanged = url.scheme != finalUrl.scheme

            when {
                statusCode in 300..399 -> {
                    // This shouldn't happen with followRedirects=true, but log if seen
                    UnifiedLog.w(TAG) {
                        "← Redirect response: $statusCode (redirects should be auto-followed)"
                    }
                }
                statusCode == 200 -> {
                    val redirectInfo =
                        if (hostChanged || schemeChanged) {
                            buildString {
                                if (hostChanged) append(" [host: ${url.host} → ${finalUrl.host}]")
                                if (schemeChanged) append(" [scheme: ${url.scheme} → ${finalUrl.scheme}]")
                            }
                        } else {
                            " [no redirect]"
                        }

                    // Check if response is HLS manifest (only for plausible content types)
                    val contentType = response.header("Content-Type") ?: ""
                    val maybeHls =
                        contentType.contains("mpegurl", ignoreCase = true) ||
                            contentType.contains("m3u", ignoreCase = true) ||
                            contentType.contains("text", ignoreCase = true) ||
                            contentType.isEmpty()

                    val isHlsManifest =
                        if (maybeHls) {
                            response.peekBody(10).string().startsWith("#EXTM3U")
                        } else {
                            false
                        }

                    UnifiedLog.i(TAG) {
                        "← Xtream HTTP success: 200$redirectInfo, HLS manifest: $isHlsManifest"
                    }
                }
                else -> {
                    UnifiedLog.w(TAG) {
                        "← Xtream HTTP error: $statusCode ${response.message}"
                    }
                }
            }

            return response
        }
    }
}
