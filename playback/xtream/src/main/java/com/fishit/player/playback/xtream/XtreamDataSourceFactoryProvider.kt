package com.fishit.player.playback.xtream

import androidx.media3.datasource.DataSource

/**
 * Provider interface for creating Xtream DataSource factories with per-request configuration.
 *
 * Unlike static DataSource.Factory instances, Xtream playback requires per-request headers
 * (User-Agent, Referer, etc.) that are only known at playback time. This provider allows
 * the player layer to create appropriately configured factories without directly depending
 * on the Xtream implementation.
 *
 * **Architecture:**
 * - Player layer depends on this interface (source-agnostic)
 * - Xtream module provides the implementation
 * - DI wires them together at runtime
 *
 * This maintains the hard rule that player/internal must compile with zero playback sources.
 */
interface XtreamDataSourceFactoryProvider {
    /**
     * Creates a DataSource.Factory configured for Xtream playback.
     *
     * @param headers HTTP headers to apply (User-Agent, Referer, Accept, Accept-Encoding)
     * @param debugMode Enable redirect logging (DEBUG builds only)
     * @return Configured DataSource.Factory for Xtream streaming
     */
    fun create(
        headers: Map<String, String>,
        debugMode: Boolean = false,
    ): DataSource.Factory
}
