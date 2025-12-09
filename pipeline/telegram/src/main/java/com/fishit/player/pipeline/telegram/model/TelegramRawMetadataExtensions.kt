package com.fishit.player.pipeline.telegram.model

import com.fishit.player.core.model.ExternalIds
import com.fishit.player.core.model.MediaType
import com.fishit.player.core.model.RawMediaMetadata
import com.fishit.player.core.model.SourceType

/**
 * Extensions for converting Telegram pipeline models to RawMediaMetadata.
 *
 * Per MEDIA_NORMALIZATION_CONTRACT.md:
 * - Provides RAW metadata only (no cleaning, no normalization, no heuristics)
 * - Title extracted via simple field priority: title > episodeTitle > caption > fileName
 * - Scene-style filenames preserved exactly (e.g., "Movie.2000.1080p.BluRay.x264-GROUP.mkv")
 * - NO tag stripping, NO case normalization, NO TMDB lookups
 * - All normalization delegated to :core:metadata-normalizer
 *
 * Per IMAGING_SYSTEM.md:
 * - ImageRef fields populated from source thumbnails/photos
 * - Uses TelegramImageRefExtensions for conversion
 * - NO raw TDLib DTOs passed through - only ImageRef
 *
 * CONTRACT COMPLIANCE:
 * - ✅ Provide raw title via simple field priority (NO cleaning)
 * - ✅ Pass through year, season, episode, duration as-is from source
 * - ✅ Provide stable sourceId for tracking (remoteId or "msg:chatId:messageId")
 * - ✅ Leave externalIds empty (Telegram doesn't provide TMDB/IMDB/TVDB)
 * - ✅ ImageRef populated from thumbnail/photo data
 * - ✅ NO TMDB lookups, NO cross-pipeline matching, NO canonical identity computation
 */

/**
 * Converts a TelegramMediaItem to RawMediaMetadata.
 *
 * @return RawMediaMetadata with Telegram-specific fields and ImageRefs
 */
fun TelegramMediaItem.toRawMediaMetadata(): RawMediaMetadata =
    RawMediaMetadata(
        originalTitle = extractRawTitle(),
        mediaType = mapTelegramMediaType(),
        year = year,
        season = seasonNumber,
        episode = episodeNumber,
        durationMinutes = durationSecs?.let { it / 60 },
        externalIds = ExternalIds(), // Telegram doesn't provide external IDs
        sourceType = SourceType.TELEGRAM,
        sourceLabel = buildTelegramSourceLabel(),
        sourceId = remoteId ?: "msg:$chatId:$messageId",
        // === ImageRef from TelegramImageRefExtensions ===
        poster = toPosterImageRef(), // Photo or null for video
        backdrop = null, // Telegram doesn't provide backdrops
        thumbnail = toThumbnailImageRef(), // Video thumbnail or best photo size
        // === Minithumbnail for instant blur placeholder (Netflix-style tiered loading) ===
        placeholderThumbnail = toMinithumbnailImageRef(),
    )

/**
 * Extracts the raw title using simple field priority.
 *
 * Priority order: title > episodeTitle > caption > fileName > fallback
 *
 * CRITICAL: NO cleaning of technical tags - pass raw source data AS-IS. Examples of what stays
 * unchanged:
 * - "Movie.2020.1080p.BluRay.x264-GROUP" → returned AS-IS
 * - "Series.S01E05.HDTV.x264" → returned AS-IS
 * - "[GER] Show Name Episode 5" → returned AS-IS
 */
private fun TelegramMediaItem.extractRawTitle(): String =
    when {
        title.isNotBlank() -> title
        episodeTitle?.isNotBlank() == true -> episodeTitle
        caption?.isNotBlank() == true -> caption
        fileName?.isNotBlank() == true -> fileName
        else -> "Untitled Media $messageId"
    }

/** Maps TelegramMediaType to core MediaType. */
private fun TelegramMediaItem.mapTelegramMediaType(): MediaType =
    when {
        isSeries || seasonNumber != null || episodeNumber != null -> MediaType.SERIES_EPISODE
        mediaType == TelegramMediaType.VIDEO -> MediaType.MOVIE
        mediaType == TelegramMediaType.AUDIO -> MediaType.MUSIC
        mediaType == TelegramMediaType.DOCUMENT -> MediaType.UNKNOWN // Could be anything
        else -> MediaType.UNKNOWN
    }

/**
 * Builds a human-readable source label.
 *
 * Examples:
 * - "Telegram: Movies HD" (when series name available)
 * - "Telegram Chat: 123456789" (fallback)
 */
private fun TelegramMediaItem.buildTelegramSourceLabel(): String =
    when {
        seriesName?.isNotBlank() == true -> "Telegram: $seriesName"
        else -> "Telegram Chat: $chatId"
    }
