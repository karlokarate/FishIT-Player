package com.fishit.player.playback.xtream

import android.content.Context
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import okhttp3.OkHttpClient

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
 * **Compile-time Gating (Issue #564):**
 * Chucker integration is handled via [XtreamOkHttpClientProvider] with separate
 * implementations in debug/ and release/ source sets:
 * - Debug: ChuckerInterceptor + RedirectLoggingInterceptor
 * - Release: No Chucker, ZERO debug overhead
 *
 * This ensures:
 * - ZERO Chucker classes in release builds (not even no-op stubs)
 * - No `import com.chuckerteam.chucker.*` in main/ source set
 * - Clean compile-time separation per Issue #564 requirements
 *
 * **Debug Logging:**
 * In debug builds, logs:
 * - HTTP status codes (301, 302, 307, 308)
 * - Target host/scheme changes
 * - Whether final response starts with "#EXTM3U" (HLS manifest detection)
 * - No secrets (credentials, keys, tokens) are logged
 *
 * @param context Application context for OkHttpClient initialization
 * @param headers HTTP headers to apply (User-Agent, Referer, Accept, Accept-Encoding)
 */
class XtreamHttpDataSourceFactory(
    private val context: Context,
    private val headers: Map<String, String>,
) : DataSource.Factory {
    /**
     * OkHttpClient provider for build-variant-specific configuration.
     *
     * Uses [XtreamOkHttpClientProviderImpl] from debug/ or release/ source set.
     */
    private val clientProvider: XtreamOkHttpClientProvider = XtreamOkHttpClientProviderImpl()

    /**
     * OkHttpClient instance configured for Xtream streaming.
     *
     * Thread-safe lazy initialization - Kotlin's `by lazy` ensures thread-safe singleton creation.
     * OkHttpClient itself is thread-safe and designed to be shared across the application.
     */
    private val okHttpClient: OkHttpClient by lazy {
        clientProvider.createClient(context)
    }

    override fun createDataSource(): DataSource =
        OkHttpDataSource
            .Factory(okHttpClient)
            .setDefaultRequestProperties(headers)
            .createDataSource()
}
