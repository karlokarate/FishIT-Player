package com.fishit.player.playback.xtream

import android.content.Context
import androidx.media3.datasource.DataSource
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Default implementation of [XtreamDataSourceFactoryProvider].
 *
 * Creates [XtreamHttpDataSourceFactory] instances with per-request configuration.
 * This indirection maintains player layer source-agnosticism by hiding the concrete
 * Xtream implementation behind an interface.
 *
 * **Compile-time Gating (Issue #564):**
 * Debug/release behavior is handled by [XtreamOkHttpClientProviderImpl] in debug/ and release/
 * source sets. No runtime flag needed.
 */
@Singleton
class DefaultXtreamDataSourceFactoryProvider
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : XtreamDataSourceFactoryProvider {
        override fun create(
            headers: Map<String, String>,
        ): DataSource.Factory = XtreamHttpDataSourceFactory(context, headers)
    }
