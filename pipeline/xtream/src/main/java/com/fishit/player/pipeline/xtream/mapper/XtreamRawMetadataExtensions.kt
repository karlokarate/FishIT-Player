package com.fishit.player.pipeline.xtream.mapper

import com.fishit.player.core.model.ExternalIds
import com.fishit.player.core.model.MediaType
import com.fishit.player.core.model.PipelineIdTag
import com.fishit.player.core.model.PlaybackHintKeys
import com.fishit.player.core.model.RawMediaMetadata
import com.fishit.player.core.model.SourceType
import com.fishit.player.core.model.TmdbMediaType
import com.fishit.player.core.model.TmdbRef
import com.fishit.player.core.model.util.DurationParser
import com.fishit.player.core.model.util.EpochConverter
import com.fishit.player.core.model.util.RatingNormalizer
import com.fishit.player.core.model.util.YearParser
import com.fishit.player.infra.transport.xtream.XtreamVodInfo
import com.fishit.player.pipeline.xtream.debug.XtcLogger
import com.fishit.player.pipeline.xtream.ids.XtreamIdCodec
import com.fishit.player.pipeline.xtream.model.XtreamChannel
import com.fishit.player.pipeline.xtream.model.XtreamEpisode
import com.fishit.player.pipeline.xtream.model.XtreamSeriesItem
import com.fishit.player.pipeline.xtream.model.XtreamVodItem

/**
 * Extensions for converting Xtream pipeline models to RawMediaMetadata.
 *
 * Per MEDIA_NORMALIZATION_CONTRACT.md:
 * - Provides RAW metadata only (no cleaning, no normalization, no heuristics)
 * - Title passed through exactly as received from Xtream API
 * - TMDB fields stored as typed TmdbRef (Gold Decision Dec 2025)
 * - All normalization delegated to :core:metadata-normalizer
 *
 * Per Gold Decision (Dec 2025) - Typed TMDB References:
 * - VOD items → TmdbRef(MOVIE, tmdbId)
 * - Series → TmdbRef(TV, tmdbId)
 * - Episodes → TmdbRef(TV, seriesTmdbId) + season/episode fields
 *
 * Per IMAGING_SYSTEM.md:
 * - ImageRef fields populated from source images
 * - Uses XtreamImageRefExtensions for conversion
 * - NO raw URLs passed through - only ImageRef
 *
 * Live Channel Names:
 * - Unicode block decorators (▃ ▅ ▆ █) are cleaned for display
 * - Country prefix (DE:, US:, etc.) is preserved
 *
 * These extensions enable seamless integration with the centralized metadata normalization
 * pipeline.
 */

// Unicode block characters used as decorators in live channel names
private val UNICODE_DECORATORS = Regex("[▃▅▆█▇▄▂░▒▓■□●○◆◇★☆⬛⬜]+")
private val WHITESPACE_COLLAPSE = Regex("\\s+")

/**
 * Clean live channel name by removing Unicode decorators.
 *
 * Examples:
 * - "▃ ▅ ▆ █ DE HEVC █ ▆ ▅ ▃" → "DE HEVC"
 * - "DE: RTL HD" → "DE: RTL HD" (no change needed)
 */
private fun cleanLiveChannelName(name: String): String = name.replace(UNICODE_DECORATORS, " ").replace(WHITESPACE_COLLAPSE, " ").trim()

/**
 * Converts an XtreamVodItem to RawMediaMetadata.
 *
 * Per Gold Decision (Dec 2025):
 * - VOD items map to TmdbRef(MOVIE, tmdbId) when tmdbId is available
 *
 * @param authHeaders Optional headers for image URL authentication
 * @param accountLabel Human-readable account label for UI display
 * @return RawMediaMetadata with VOD-specific fields
 */
fun XtreamVodItem.toRawMetadata(
    authHeaders: Map<String, String> = emptyMap(),
    accountLabel: String = "xtream",
): RawMediaMetadata {
    val rawTitle = name

    // Year from API field only — title-based extraction is normalizer territory
    // (per pipeline.instructions.md: extractYearFromTitle is FORBIDDEN in pipeline)
    val rawYear = YearParser.validate(year)

    // Parse duration via SSOT — format varies: "01:30:00", "90", "90 min", etc.
    val durationMs = DurationParser.parseToMs(duration)
    // Stable source ID format (contract): xtream:vod:{id}
    // Container extension is *format*, not identity.
    // Playback can infer a default extension or use detail fetch.
    // Uses XtreamIdCodec as SSOT per xtream_290126.md Blocker #1
    val sourceIdStable = XtreamIdCodec.vod(id)
    // Build typed TMDB reference for movies
    val externalIds =
        tmdbId?.let { ExternalIds(tmdb = TmdbRef(TmdbMediaType.MOVIE, it)) }
            ?: ExternalIds()
    // Build playback hints for VOD URL construction
    // BUG FIX (Jan 2026): Include categoryId for proper category-based filtering
    // BUG FIX (Feb 2026): Include streamType as vodKind for correct URL building
    val hints =
        buildMap {
            put(PlaybackHintKeys.Xtream.CONTENT_TYPE, PlaybackHintKeys.Xtream.CONTENT_VOD)
            put(PlaybackHintKeys.Xtream.VOD_ID, id.toString())
            containerExtension?.takeIf { it.isNotBlank() }?.let {
                put(PlaybackHintKeys.Xtream.CONTAINER_EXT, it)
            }
            categoryId?.takeIf { it.isNotBlank() }?.let {
                put(PlaybackHintKeys.Xtream.CATEGORY_ID, it)
            }
            // Persist streamType as vodKind for URL building (movie, vod, movies)
            streamType?.takeIf { it.isNotBlank() }?.let {
                put(PlaybackHintKeys.Xtream.VOD_KIND, it)
            }
        }

    val raw = RawMediaMetadata(
        originalTitle = rawTitle,
        mediaType = MediaType.MOVIE,
        year = rawYear,
        season = null,
        episode = null,
        durationMs = durationMs,
        externalIds = externalIds,
        sourceType = SourceType.XTREAM,
        sourceLabel = accountLabel,
        sourceId = sourceIdStable,
        // === Pipeline Identity (v2) ===
        pipelineIdTag = PipelineIdTag.XTREAM,
        // === Timing (v2) - for "Recently Added" sorting ===
        addedTimestamp = added,
        // BUG FIX (Jan 2026): VOD list API provides "added" timestamp which is when
        // the provider added the item. Use this as lastModifiedTimestamp so it gets
        // persisted to NX_WorkSourceRef.sourceLastModifiedMs for incremental sync.
        lastModifiedTimestamp = added,
        // === Rating (v2) - prefer 0-10 raw rating, fallback to normalized 5-based ===
        rating = rating ?: RatingNormalizer.normalize5to10(rating5Based),
        // === ImageRef from XtreamImageRefExtensions ===
        poster = toPosterImageRef(authHeaders),
        backdrop = null, // VOD list doesn't provide backdrop
        thumbnail = null, // Use poster as fallback in UI
        // === Playback Hints (v2) ===
        playbackHints = hints,
        // === Rich metadata (v2) - from provider TMDB scraping ===
        plot = plot,
        genres = genre, // Xtream uses "genre" singular
        // === Content Classification (v2) ===
        isAdult = isAdult,
        categoryId = categoryId,
    )

    // XTC: Track DTO → RawMetadata mapping
    XtcLogger.logDtoToRaw("VOD", sourceIdStable, rawTitle, raw)

    return raw
}

/**
 * Converts an XtreamSeriesItem to RawMediaMetadata.
 *
 * Note: This represents the series as a whole, not individual episodes. Use
 * XtreamEpisode.toRawMediaMetadata() for episode-level metadata.
 *
 * Per Gold Decision (Dec 2025):
 * - Series map to TmdbRef(TV, tmdbId) when tmdbId is available
 *
 * @param authHeaders Optional headers for image URL authentication
 * @param accountLabel Human-readable account label for UI display
 * @return RawMediaMetadata with series-specific fields
 */
fun XtreamSeriesItem.toRawMetadata(
    authHeaders: Map<String, String> = emptyMap(),
    accountLabel: String = "xtream",
): RawMediaMetadata {
    val rawTitle = name

    // Year from structured API fields only — title-based extraction is normalizer territory
    // (per pipeline.instructions.md: extractYearFromTitle is FORBIDDEN in pipeline)
    val rawYear = YearParser.validate(year) ?: YearParser.fromDate(releaseDate)

    // Build typed TMDB reference for TV shows
    val externalIds =
        tmdbId?.let { ExternalIds(tmdb = TmdbRef(TmdbMediaType.TV, it)) } ?: ExternalIds()

    // Series "episode_run_time" is average episode runtime in MINUTES → convert to ms via SSOT
    val durationMs = episodeRunTime
        ?.toIntOrNull()
        ?.takeIf { it > 0 }
        ?.let { DurationParser.minutesToMs(it) }

    val raw = RawMediaMetadata(
        originalTitle = rawTitle,
        mediaType = MediaType.SERIES, // Series container, not episode
        year = rawYear,
        season = null,
        episode = null,
        durationMs = durationMs, // Average episode runtime (from episode_run_time API field)
        externalIds = externalIds,
        sourceType = SourceType.XTREAM,
        sourceLabel = accountLabel,
        // Uses XtreamIdCodec as SSOT per xtream_290126.md Blocker #1
        sourceId = XtreamIdCodec.series(id),
        // === Pipeline Identity (v2) ===
        pipelineIdTag = PipelineIdTag.XTREAM,
        // === Timing (v2) ===
        // Note: Series API doesn't provide "added" field, only "last_modified"
        // Using lastModified for both since it's the best available timestamp
        addedTimestamp = lastModified,
        // lastModifiedTimestamp enables incremental sync and "new episodes" detection
        lastModifiedTimestamp = lastModified,
        // === Rating (v2) - TMDB rating from provider ===
        rating = rating,
        // === ImageRef from XtreamImageRefExtensions ===
        poster = toPosterImageRef(authHeaders),
        backdrop = toBackdropImageRef(authHeaders), // Series provides backdrop from API
        thumbnail = null,
        // === Rich metadata (v2) - from provider TMDB scraping ===
        plot = plot,
        genres = genre, // Xtream uses "genre" singular
        director = director,
        cast = cast,
        releaseDate = releaseDate, // ISO format: "2014-09-21"
        trailer = youtubeTrailer, // YouTube trailer URL/ID
        // === Content Classification (v2) ===
        isAdult = isAdult,
        categoryId = categoryId,
    )

    // XTC: Track DTO → RawMetadata mapping (uses codec-generated sourceId)
    XtcLogger.logDtoToRaw("SERIES", raw.sourceId, rawTitle, raw)

    return raw
}

/**
 * Converts XtreamEpisode to RawMediaMetadata.
 *
 * Uses the embedded seriesName property (set during loadEpisodes) for context. Falls back to
 * external parameter if provided.
 *
 * Per Gold Decision (Dec 2025):
 * - Episodes map to TmdbRef(TV, seriesTmdbId) - using series TMDB ID, not episode ID
 * - season/episode fields enable episode-level API calls: GET /tv/{id}/season/{s}/episode/{e}
 *
 * @param seriesNameOverride Optional override for parent series name
 * @param seriesKind The parent series kind/alias (series, episodes, movie) for URL building
 * @param authHeaders Optional headers for image URL authentication
 * @param accountLabel Human-readable account label for UI display
 * @return RawMediaMetadata with episode-specific fields
 */
fun XtreamEpisode.toRawMediaMetadata(
    seriesNameOverride: String? = null,
    seriesKind: String? = null,
    authHeaders: Map<String, String> = emptyMap(),
    accountLabel: String = "xtream",
): RawMediaMetadata {
    // Prefer embedded seriesName from data class, fall back to override parameter
    val effectiveSeriesName = seriesName ?: seriesNameOverride
    val rawTitle = title.ifBlank { effectiveSeriesName ?: "Episode $episodeNumber" }
    val rawYear: Int? = null // Episodes typically don't have year; inherit from series
    // Stable source ID format used across v2:
    // Preferred format: xtream:episode:{episodeId} when available
    // Fallback format: xtream:episode:series:{seriesId}:s{season}:e{episode}
    // Uses XtreamIdCodec as SSOT per xtream_290126.md Blocker #1
    //
    // Note: This function receives episode stream ID via `id` parameter (= episodeId)
    // For identity, we use the composite format to enable parent lookups.
    // The stream ID is stored in playback hints for URL construction.
    val sourceIdStable = XtreamIdCodec.episodeComposite(seriesId, seasonNumber, episodeNumber)
    // Build typed TMDB reference for episodes per Gold Decision (Dec 2025):
    // Episodes use the SERIES TMDB ID (TV type) combined with season/episode numbers.
    // This enables lookup via: GET /tv/{seriesTmdbId}/season/{s}/episode/{e}
    //
    // Note: episodeTmdbId (when available) is stored in playbackHints for optional
    // direct episode metadata lookup, but externalIds.tmdb always uses seriesTmdbId
    // to maintain consistency with the normalizer/resolver which expects series context.
    val externalIds =
        seriesTmdbId?.let { ExternalIds(tmdb = TmdbRef(TmdbMediaType.TV, it)) }
            ?: ExternalIds()
    // Build playback hints for episode URL construction
    // **Critical:** episodeId (this.id) is the stream ID needed for playback URL
    // BUG FIX (Feb 2026): Include seriesKind for correct URL building
    val hints =
        buildMap {
            put(PlaybackHintKeys.Xtream.CONTENT_TYPE, PlaybackHintKeys.Xtream.CONTENT_SERIES)
            put(PlaybackHintKeys.Xtream.SERIES_ID, seriesId.toString())
            put(PlaybackHintKeys.Xtream.SEASON_NUMBER, seasonNumber.toString())
            put(PlaybackHintKeys.Xtream.EPISODE_NUMBER, episodeNumber.toString())
            // Episode ID (stream ID) - CRITICAL for URL construction
            put(PlaybackHintKeys.Xtream.EPISODE_ID, id.toString())
            containerExtension?.takeIf { it.isNotBlank() }?.let {
                put(PlaybackHintKeys.Xtream.CONTAINER_EXT, it)
            }
            // Persist seriesKind for URL building (series, episodes, movie)
            seriesKind?.takeIf { it.isNotBlank() }?.let {
                put(PlaybackHintKeys.Xtream.SERIES_KIND, it)
            }
            // Episode-specific TMDB ID (when available) for optional direct metadata lookup
            // Note: externalIds.tmdb uses seriesTmdbId per Gold Decision; this is supplementary
            episodeTmdbId?.let { put(PlaybackHintKeys.Xtream.EPISODE_TMDB_ID, it.toString()) }
            // Video/Audio codec info from ffprobe (optional, for UI display)
            videoCodec?.takeIf { it.isNotBlank() }?.let {
                put(PlaybackHintKeys.VIDEO_CODEC, it)
            }
            videoWidth?.let { put(PlaybackHintKeys.VIDEO_WIDTH, it.toString()) }
            videoHeight?.let { put(PlaybackHintKeys.VIDEO_HEIGHT, it.toString()) }
            audioCodec?.takeIf { it.isNotBlank() }?.let {
                put(PlaybackHintKeys.AUDIO_CODEC, it)
            }
            audioChannels?.let { put(PlaybackHintKeys.AUDIO_CHANNELS, it.toString()) }
            // BUG FIX (Jan 2026): Add bitrate for quality info in player
            bitrate?.takeIf { it > 0 }?.let {
                put(PlaybackHintKeys.Xtream.BITRATE, it.toString())
            }
        }

    // Duration: prefer durationSecs (numeric, more accurate) via SSOT
    val resolvedDurationMs = DurationParser.resolve(durationSecs?.toLong(), duration)

    val raw = RawMediaMetadata(
        originalTitle = rawTitle,
        mediaType = MediaType.SERIES_EPISODE,
        year = rawYear,
        season = seasonNumber,
        episode = episodeNumber,
        durationMs = resolvedDurationMs, // Prefer durationSecs, fallback to parsed duration
        externalIds = externalIds,
        sourceType = SourceType.XTREAM,
        sourceLabel = accountLabel,
        sourceId = sourceIdStable,
        // === Pipeline Identity (v2) ===
        pipelineIdTag = PipelineIdTag.XTREAM,
        // === Timing (v2) - for "Recently Added" sorting ===
        addedTimestamp = added,
        // BUG FIX (Jan 2026): Episode API provides "added" timestamp. Use this as
        // lastModifiedTimestamp for NX_WorkSourceRef.sourceLastModifiedMs.
        lastModifiedTimestamp = added,
        // === Rating (v2) ===
        rating = rating,
        // === ImageRef from XtreamImageRefExtensions ===
        poster = null, // Episodes don't have poster; inherit from series
        backdrop = null,
        thumbnail = toThumbnailImageRef(authHeaders),
        // === Playback Hints (v2) ===
        playbackHints = hints,
        // === Rich metadata (v2) ===
        plot = plot,
        releaseDate = releaseDate,
    )

    // XTC: Track DTO → RawMetadata mapping
    XtcLogger.logDtoToRaw("EPISODE", sourceIdStable, rawTitle, raw)

    return raw
}

/**
 * Converts an XtreamChannel to RawMediaMetadata.
 *
 * Live channel names are cleaned:
 * - Unicode block decorators (▃ ▅ ▆ █) removed
 * - Multiple spaces collapsed
 * - Country prefix (DE:, US:) preserved
 *
 * @param authHeaders Optional headers for image URL authentication
 * @param accountLabel Human-readable account label for UI display
 * @return RawMediaMetadata with live channel fields
 */
fun XtreamChannel.toRawMediaMetadata(
    authHeaders: Map<String, String> = emptyMap(),
    accountLabel: String = "xtream",
): RawMediaMetadata {
    // Clean Unicode decorators from live channel names
    val rawTitle = cleanLiveChannelName(name)
    // Build playback hints for live stream URL construction
    // Note: Live channels don't have containerExtension - streams are typically .ts or .m3u8
    // BUG FIX (Feb 2026): Include streamType as liveKind for correct URL building
    val hints =
        buildMap {
            put(PlaybackHintKeys.Xtream.CONTENT_TYPE, PlaybackHintKeys.Xtream.CONTENT_LIVE)
            put(PlaybackHintKeys.Xtream.STREAM_ID, id.toString())
            // BUG FIX (Jan 2026): Add direct HLS source URL for potential playback optimization
            directSource?.takeIf { it.isNotBlank() }?.let {
                put(PlaybackHintKeys.Xtream.DIRECT_SOURCE, it)
            }
            // Persist streamType as liveKind for URL building (live)
            streamType?.takeIf { it.isNotBlank() }?.let {
                put(PlaybackHintKeys.Xtream.LIVE_KIND, it)
            }
        }

    val raw = RawMediaMetadata(
        originalTitle = rawTitle,
        mediaType = MediaType.LIVE, // Live channels - NO year/scene parsing needed
        year = null,
        season = null,
        episode = null,
        durationMs = null, // Live channels don't have duration
        externalIds = ExternalIds(),
        sourceType = SourceType.XTREAM,
        sourceLabel = accountLabel,
        // Uses XtreamIdCodec as SSOT per xtream_290126.md Blocker #1
        sourceId = XtreamIdCodec.live(id),
        // === Pipeline Identity (v2) ===
        pipelineIdTag = PipelineIdTag.XTREAM,
        // === Timing (v2) - for "Recently Added" sorting ===
        addedTimestamp = added,
        // BUG FIX (Jan 2026): Live channel API provides "added" timestamp. Use this as
        // lastModifiedTimestamp for NX_WorkSourceRef.sourceLastModifiedMs.
        lastModifiedTimestamp = added,
        // === ImageRef from XtreamImageRefExtensions ===
        poster = toLogoImageRef(authHeaders), // Use logo as poster for channels
        backdrop = null,
        thumbnail = toLogoImageRef(authHeaders), // Thumbnail same as logo
        // === Playback Hints (v2) ===
        playbackHints = hints,
        // === Content Classification (v2) ===
        isAdult = isAdult,
        categoryId = categoryId,
        // === Live Channel Fields (v2) ===
        epgChannelId = epgChannelId,
        tvArchive = tvArchive,
        tvArchiveDuration = tvArchiveDuration,
    )

    // XTC: Track DTO → RawMetadata mapping (uses codec-generated sourceId)
    XtcLogger.logDtoToRaw("LIVE", raw.sourceId, rawTitle, raw)

    return raw
}

// =============================================================================
// VOD Detail Extensions (from get_vod_info API)
// =============================================================================

/**
 * Converts XtreamVodInfo (from get_vod_info) to RawMediaMetadata with full rich metadata.
 *
 * This provides all metadata fields available from the detail API:
 * - plot, genres, director, cast
 * - rating, duration
 * - backdrop images
 * - IMDB/TMDB IDs
 * - YouTube trailer URLs
 *
 * Use this for detail screens where full metadata is needed. For list views, use
 * XtreamVodItem.toRawMediaMetadata() instead.
 *
 * @param vodItem The original VOD item (for stream_id and container_extension)
 * @param authHeaders Optional headers for image URL authentication
 * @return RawMediaMetadata with all available rich metadata
 */
fun XtreamVodInfo.toRawMediaMetadata(
    vodItem: XtreamVodItem,
    authHeaders: Map<String, String> = emptyMap(),
    accountLabel: String = "xtream",
): RawMediaMetadata {
    val infoBlock = info
    val movieData = movieData

    // Use info block title, fall back to movie_data name, then original vodItem name
    val rawTitle = infoBlock?.name ?: infoBlock?.originalName ?: movieData?.name ?: vodItem.name

    // Extract year from info block
    val rawYear = infoBlock?.year?.toIntOrNull()

    // Duration: prefer durationSecs (in seconds), convert via SSOT
    val durationMs = infoBlock?.durationSecs?.let { DurationParser.secondsToMs(it.toLong()) }

    // Rating: resolve via SSOT (prefer 0-10 string, fallback to normalized 5-based)
    val rating = RatingNormalizer.resolve(infoBlock?.rating, infoBlock?.rating5Based)
        ?: vodItem.rating

    // Stable source ID format (contract): xtream:vod:{streamId}
    // Container extension is *format*, not identity.
    // Uses XtreamIdCodec as SSOT per xtream_290126.md Blocker #1
    val streamId = movieData?.streamId ?: vodItem.id
    val sourceIdStable = XtreamIdCodec.vod(streamId)

    // Build TMDB reference - prefer info block, fall back to vodItem
    // Note: TMDB-ID is written as Int here, stored as String in NX_Work for persistence.
    // The NxCatalogWriter handles the Int→String conversion via ExternalIds.
    val tmdbId = infoBlock?.tmdbId?.toIntOrNull() ?: vodItem.tmdbId
    val externalIds =
        tmdbId?.let { ExternalIds(tmdb = TmdbRef(TmdbMediaType.MOVIE, it)) }
            ?: ExternalIds()

    // Rich metadata from info block
    val plot = infoBlock?.resolvedPlot

    val genres = infoBlock?.resolvedGenre

    val director = infoBlock?.director

    val cast = infoBlock?.resolvedCast

    // Trailer URL - use resolved property which handles multiple field names
    val trailer = infoBlock?.resolvedTrailer

    // Images: use info block images if available, fall back to vodItem
    val posterUrl = infoBlock?.resolvedPoster

    return RawMediaMetadata(
        originalTitle = rawTitle,
        mediaType = MediaType.MOVIE,
        year = rawYear,
        season = null,
        episode = null,
        durationMs = durationMs,
        externalIds = externalIds,
        sourceType = SourceType.XTREAM,
        sourceLabel = accountLabel,
        sourceId = sourceIdStable,
        // === Pipeline Identity (v2) ===
        pipelineIdTag = PipelineIdTag.XTREAM,
        // === Timing (v2) ===
        // movieData.added is Unix SECONDS string; vodItem.added is already converted to ms
        addedTimestamp = EpochConverter.secondsToMs(movieData?.added) ?: vodItem.added,
        // Detail API must also set lastModifiedTimestamp for
        // NX_WorkSourceRef.sourceLastModifiedMs persistence.
        lastModifiedTimestamp = EpochConverter.secondsToMs(movieData?.added) ?: vodItem.added,
        // === Rating (v2) ===
        rating = rating,
        // === ImageRef ===
        poster =
            posterUrl?.let { createImageRef(it, authHeaders) }
                ?: vodItem.toPosterImageRef(authHeaders),
        backdrop =
            infoBlock?.backdropPath?.let {
                createImageRef(it, authHeaders)
            },
        thumbnail = null,
        // === Rich metadata (v2) ===
        plot = plot,
        genres = genres,
        director = director,
        cast = cast,
        trailer = trailer,
        releaseDate = infoBlock?.releaseDate ?: infoBlock?.releaseDateAlt,
        // === Playback Hints (v2) ===
        playbackHints =
            buildMap {
                put(PlaybackHintKeys.Xtream.CONTENT_TYPE, PlaybackHintKeys.Xtream.CONTENT_VOD)
                put(PlaybackHintKeys.Xtream.VOD_ID, streamId.toString())
                movieData?.containerExtension?.takeIf { it.isNotBlank() }?.let {
                    put(PlaybackHintKeys.Xtream.CONTAINER_EXT, it)
                } ?: vodItem.containerExtension?.takeIf { it.isNotBlank() }?.let {
                    put(PlaybackHintKeys.Xtream.CONTAINER_EXT, it)
                }
                // Include categoryId for consistency with list API
                vodItem.categoryId?.takeIf { it.isNotBlank() }?.let {
                    put(PlaybackHintKeys.Xtream.CATEGORY_ID, it)
                }
                // === Video/Audio Quality from ffprobe (G8 fix) ===
                infoBlock?.videoInfo?.codec?.takeIf { it.isNotBlank() }?.let {
                    put(PlaybackHintKeys.VIDEO_CODEC, it)
                }
                infoBlock?.videoInfo?.width?.let {
                    put(PlaybackHintKeys.VIDEO_WIDTH, it.toString())
                }
                infoBlock?.videoInfo?.height?.let {
                    put(PlaybackHintKeys.VIDEO_HEIGHT, it.toString())
                }
                infoBlock?.audioInfo?.codec?.takeIf { it.isNotBlank() }?.let {
                    put(PlaybackHintKeys.AUDIO_CODEC, it)
                }
                infoBlock?.audioInfo?.channels?.let {
                    put(PlaybackHintKeys.AUDIO_CHANNELS, it.toString())
                }
                infoBlock?.bitrate?.takeIf { it.isNotBlank() }?.let {
                    put(PlaybackHintKeys.Xtream.BITRATE, it)
                }
            },
        // === Content Classification (v2) ===
        isAdult = vodItem.isAdult,
        categoryId = vodItem.categoryId,
    )
}

/**
 * Creates an ImageRef from a URL string. Helper for XtreamVodInfo mapping where we have raw URLs.
 */
private fun createImageRef(
    url: String,
    authHeaders: Map<String, String>,
): com.fishit.player.core.model.ImageRef? {
    if (url.isBlank()) return null
    return com.fishit.player.core.model.ImageRef.Http(
        url = url,
        headers = authHeaders.takeIf { it.isNotEmpty() } ?: emptyMap(),
    )
}


