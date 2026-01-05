package com.fishit.player.playback.xtream

import android.content.Context
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * Provider interface for OkHttpClient configuration in Xtream playback.
 *
 * **Compile-time Gating for Chucker (Issue #564):**
 * This interface abstracts OkHttpClient creation to allow different implementations
 * for debug and release builds:
 *
 * - **Debug**: Adds ChuckerInterceptor and RedirectLoggingInterceptor
 * - **Release**: No Chucker, no debug interceptors, zero overhead
 *
 * **Implementation Pattern:**
 * - Interface in main/ source set (this file)
 * - Debug implementation in debug/ source set (XtreamOkHttpClientProviderImpl.kt)
 * - Release implementation in release/ source set (XtreamOkHttpClientProviderImpl.kt)
 *
 * This ensures:
 * - ZERO Chucker classes in release builds (not even no-op stubs)
 * - No `import com.chuckerteam.chucker.*` in main/ source set
 * - Clean compile-time separation
 */
interface XtreamOkHttpClientProvider {
    /**
     * Create an OkHttpClient configured for Xtream streaming.
     *
     * The client will have:
     * - [CONNECT_TIMEOUT_SECONDS] second connection timeout
     * - [READ_TIMEOUT_SECONDS] second read timeout
     * - [WRITE_TIMEOUT_SECONDS] second write timeout
     * - followRedirects = true (for 301, 302, 307, 308)
     * - followSslRedirects = true (for HTTP -> HTTPS)
     * - Debug builds: ChuckerInterceptor + RedirectLoggingInterceptor
     * - Release builds: No interceptors (minimal overhead)
     *
     * @param context Application context (used for Chucker in debug)
     * @return Configured OkHttpClient instance
     */
    fun createClient(context: Context): OkHttpClient

    companion object {
        // Connection timeouts (same as Media3 defaults)
        const val CONNECT_TIMEOUT_SECONDS = 30L
        const val READ_TIMEOUT_SECONDS = 30L
        const val WRITE_TIMEOUT_SECONDS = 30L
    }
}

/**
 * Creates the base OkHttpClient.Builder with standard Xtream streaming configuration.
 *
 * Shared between debug and release implementations for consistency.
 * Debug builds can add interceptors to this builder.
 */
internal fun createBaseOkHttpClientBuilder(): OkHttpClient.Builder =
    OkHttpClient
        .Builder()
        .followRedirects(true) // Follow HTTP redirects (301, 302, 307, 308)
        .followSslRedirects(true) // Allow HTTP -> HTTPS redirects (Cloudflare pattern)
        .connectTimeout(XtreamOkHttpClientProvider.CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(XtreamOkHttpClientProvider.READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .writeTimeout(XtreamOkHttpClientProvider.WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
