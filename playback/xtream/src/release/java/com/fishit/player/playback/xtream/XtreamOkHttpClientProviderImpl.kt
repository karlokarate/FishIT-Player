package com.fishit.player.playback.xtream

import android.content.Context
import okhttp3.OkHttpClient

/**
 * Release implementation of [XtreamOkHttpClientProvider].
 *
 * **Zero debug overhead:**
 * - No ChuckerInterceptor (not even no-op)
 * - No RedirectLoggingInterceptor
 * - Just the base OkHttpClient configuration
 *
 * **Compile-time Gating (Issue #564):**
 * This file is in release/ source set and is ONLY compiled for release builds.
 * There are ZERO references to Chucker or debug tooling.
 */
class XtreamOkHttpClientProviderImpl : XtreamOkHttpClientProvider {
    override fun createClient(context: Context): OkHttpClient =
        createBaseOkHttpClientBuilder()
            .build()
}
