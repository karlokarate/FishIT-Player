package com.fishit.player.playback.xtream

import androidx.media3.datasource.DataSource
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Default implementation of [XtreamDataSourceFactoryProvider].
 *
 * Creates [XtreamHttpDataSourceFactory] instances with per-request configuration.
 * This indirection maintains player layer source-agnosticism by hiding the concrete
 * Xtream implementation behind an interface.
 */
@Singleton
class DefaultXtreamDataSourceFactoryProvider @Inject constructor() : XtreamDataSourceFactoryProvider {
    override fun create(
        headers: Map<String, String>,
        debugMode: Boolean,
    ): DataSource.Factory = XtreamHttpDataSourceFactory(headers, debugMode)
}
