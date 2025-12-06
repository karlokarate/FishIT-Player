package com.fishit.player.pipeline.xtream.model

import com.fishit.player.core.model.ExternalIds
import com.fishit.player.core.model.RawMediaMetadata
import com.fishit.player.core.model.SourceType

/**
 * Xtream Media Normalization Extensions.
 *
 * These functions provide the mandatory toRawMediaMetadata() mapping
 * required by MEDIA_NORMALIZATION_CONTRACT.md.
 *
 * IMPORTANT CONTRACT RULES:
 * - Extract raw fields ONLY (no cleaning, no heuristics)
 * - Pass through external IDs without modification
 * - Keep originalTitle exactly as provided by source
 * - NO TMDB lookups or title normalization
 *
 * All normalization will be handled centrally by :core:metadata-normalizer
 * in Phase 3.
 */

/**
 * Converts XtreamVodItem to RawMediaMetadata.
 *
 * This mapping extracts only raw metadata fields from the Xtream VOD item.
 * No title cleaning or normalization is performed.
 *
 * Note: Current stub model doesn't include tmdbId/imdbId fields. When these
 * are added to XtreamVodItem in Phase 3, they must be passed through via
 * externalIds parameter.
 *
 * @return RawMediaMetadata with raw fields extracted from this VOD item
 */
fun XtreamVodItem.toRawMediaMetadata(): RawMediaMetadata {
    return RawMediaMetadata(
        originalTitle = name, // NO cleaning - pass through as-is
        year = null, // Xtream stub doesn't provide year yet - will be added in Phase 3
        season = null, // VOD items are not episodes
        episode = null, // VOD items are not episodes
        durationMinutes = null, // Duration not in stub model yet
        externalIds = ExternalIds(
            // TODO Phase 3: Pass through tmdbId/imdbId from XtreamVodItem when available
            // tmdbId = this.tmdbId,
            // imdbId = this.imdbId,
        ),
        sourceType = SourceType.XTREAM,
        sourceLabel = "Xtream VOD",
        sourceId = "xtream:vod:$id",
    )
}

/**
 * Converts XtreamEpisode to RawMediaMetadata.
 *
 * This mapping extracts only raw metadata fields from the Xtream episode.
 * No title cleaning or normalization is performed.
 *
 * Note: Current stub model doesn't include tmdbId/imdbId fields. When these
 * are added to XtreamEpisode in Phase 3, they must be passed through via
 * externalIds parameter.
 *
 * @return RawMediaMetadata with raw fields extracted from this episode
 */
fun XtreamEpisode.toRawMediaMetadata(): RawMediaMetadata {
    return RawMediaMetadata(
        originalTitle = title, // NO cleaning - pass through as-is
        year = null, // Air date not in stub model yet - will be added in Phase 3
        season = seasonNumber,
        episode = episodeNumber,
        durationMinutes = null, // Duration not in stub model yet
        externalIds = ExternalIds(
            // TODO Phase 3: Pass through tmdbId/imdbId from XtreamEpisode when available
            // tmdbId = this.tmdbId,
            // imdbId = this.imdbId,
        ),
        sourceType = SourceType.XTREAM,
        sourceLabel = "Xtream Episode",
        sourceId = "xtream:episode:$id",
    )
}

/**
 * Converts XtreamSeriesItem to RawMediaMetadata.
 *
 * This mapping extracts only raw metadata fields from the Xtream series.
 * Note: Series items represent the show itself, not individual episodes.
 * No title cleaning or normalization is performed.
 *
 * Note: Current stub model doesn't include tmdbId/imdbId fields. When these
 * are added to XtreamSeriesItem in Phase 3, they must be passed through via
 * externalIds parameter.
 *
 * @return RawMediaMetadata with raw fields extracted from this series
 */
fun XtreamSeriesItem.toRawMediaMetadata(): RawMediaMetadata {
    return RawMediaMetadata(
        originalTitle = name, // NO cleaning - pass through as-is
        year = null, // Series start year not in stub model yet - will be added in Phase 3
        season = null, // Series items don't have season/episode
        episode = null, // Series items don't have season/episode
        durationMinutes = null, // Duration not applicable for series container
        externalIds = ExternalIds(
            // TODO Phase 3: Pass through tmdbId/imdbId from XtreamSeriesItem when available
            // tmdbId = this.tmdbId,
            // imdbId = this.imdbId,
        ),
        sourceType = SourceType.XTREAM,
        sourceLabel = "Xtream Series",
        sourceId = "xtream:series:$id",
    )
}

/**
 * Converts XtreamChannel to RawMediaMetadata.
 *
 * Note: Live TV channels are not typically part of the canonical media
 * unification system since they are live streams, not discrete content.
 * This mapping is provided for completeness but may not be used in
 * the canonical media flow.
 *
 * @return RawMediaMetadata with raw fields extracted from this channel
 */
fun XtreamChannel.toRawMediaMetadata(): RawMediaMetadata {
    return RawMediaMetadata(
        originalTitle = name, // NO cleaning - pass through as-is
        year = null, // Not applicable for live channels
        season = null, // Not applicable for live channels
        episode = null, // Not applicable for live channels
        durationMinutes = null, // Live streams have no fixed duration
        externalIds = ExternalIds(),
        sourceType = SourceType.XTREAM,
        sourceLabel = "Xtream Live Channel",
        sourceId = "xtream:live:$id",
    )
}
