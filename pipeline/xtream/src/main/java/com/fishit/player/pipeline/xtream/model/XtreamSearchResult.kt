package com.fishit.player.pipeline.xtream.model

/**
 * Represents a search result from the Xtream catalog.
 *
 * This sealed interface provides type-safe handling of mixed search results
 * containing both VOD items and series items.
 */
sealed interface XtreamSearchResult {
    /**
     * A VOD item search result.
     */
    data class VodResult(
        val item: XtreamVodItem,
    ) : XtreamSearchResult

    /**
     * A series item search result.
     */
    data class SeriesResult(
        val item: XtreamSeriesItem,
    ) : XtreamSearchResult
}
