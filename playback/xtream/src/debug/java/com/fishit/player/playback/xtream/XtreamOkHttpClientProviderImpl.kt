package com.fishit.player.playback.xtream

import android.content.Context
import com.fishit.player.infra.logging.UnifiedLog
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response

/**
 * Debug implementation of [XtreamOkHttpClientProvider].
 *
 * **Adds debug-only interceptors:**
 * - ChuckerInterceptor: HTTP traffic inspection (when Chucker is available)
 * - RedirectLoggingInterceptor: Logs redirect chains for debugging
 *
 * **Compile-time Gating (Issue #564):**
 * - Uses reflection to check if Chucker is available
 * - When -PincludeChucker=false, Chucker is not in the APK and will be skipped
 * - RedirectLoggingInterceptor is always added for debugging
 */
class XtreamOkHttpClientProviderImpl : XtreamOkHttpClientProvider {
    override fun createClient(context: Context): OkHttpClient {
        val builder = createBaseOkHttpClientBuilder()
            .addNetworkInterceptor(RedirectLoggingInterceptor())
        
        // Conditionally add Chucker interceptor if available (compile-time gating)
        createChuckerInterceptor(context)?.let { chucker ->
            builder.addInterceptor(chucker)
            UnifiedLog.d(TAG) { "Chucker interceptor added" }
        } ?: run {
            UnifiedLog.d(TAG) { "Chucker not available (disabled via compile-time gating)" }
        }
        
        return builder.build()
    }

    /**
     * Creates ChuckerInterceptor via reflection to avoid compile-time dependency.
     * Returns null if Chucker classes are not available (disabled via Gradle properties).
     */
    private fun createChuckerInterceptor(context: Context): Interceptor? =
        try {
            val builderClass = Class.forName("com.chuckerteam.chucker.api.ChuckerInterceptor\$Builder")
            val constructor = builderClass.getConstructor(Context::class.java)
            val builder = constructor.newInstance(context)
            val buildMethod = builderClass.getMethod("build")
            buildMethod.invoke(builder) as Interceptor
        } catch (e: ClassNotFoundException) {
            // Chucker not in classpath - this is expected when disabled
            null
        } catch (e: Exception) {
            UnifiedLog.w(TAG) { "Failed to create Chucker interceptor: ${e.message}" }
            null
        }

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

    private companion object {
        private const val TAG = "XtreamHttpDataSource"
    }
}
