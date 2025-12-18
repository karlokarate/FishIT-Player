package com.fishit.player.pipeline.telegram.model

import com.fishit.player.core.model.ExternalIds
import com.fishit.player.core.model.MediaType
import com.fishit.player.core.model.PipelineIdTag
import com.fishit.player.core.model.RawMediaMetadata
import com.fishit.player.core.model.SourceType
import com.fishit.player.core.model.ids.TmdbId

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
 * Per TELEGRAM_STRUCTURED_BUNDLES_CONTRACT.md (v2.2):
 * - Structured Bundle fields passed through to RawMediaMetadata
 * - structuredTmdbId → externalIds.tmdbId (enables downstream unification)
 * - structuredYear overrides year when present
 * - structuredFsk → ageRating for Kids filter
 * - structuredRating → rating
 * - Lossless emission: One RawMediaMetadata per VIDEO (no merging)
 *
 * CONTRACT COMPLIANCE:
 * - ✅ Provide raw title via simple field priority (NO cleaning)
 * - ✅ Pass through year, season, episode, duration as-is from source
 * - ✅ Provide stable sourceId for tracking (remoteId or "msg:chatId:messageId")
 * - ✅ Pass through structuredTmdbId to externalIds.tmdbId (if available)
 * - ✅ ImageRef populated from thumbnail/photo data
 * - ✅ NO TMDB lookups, NO cross-pipeline matching, NO canonical identity computation
 */

/**
 * Converts a TelegramMediaItem to RawMediaMetadata.
 *
 * Structured Bundle items (bundleType != SINGLE) will have:
 * - externalIds.tmdbId from structuredTmdbId
 * - rating from structuredRating
 * - ageRating from structuredFsk
 * - year from structuredYear (overrides filename-parsed year)
 * - durationMinutes from structuredLengthMinutes (overrides durationSecs)
 *
 * Per TELEGRAM_STRUCTURED_BUNDLES_CONTRACT.md R7 (Lossless Emission):
 * - Each VIDEO in a bundle becomes one RawMediaMetadata
 * - All items from same bundle share the same externalIds.tmdbId
 * - Downstream normalizer unifies items with matching tmdbId
 *
 * @return RawMediaMetadata with Telegram-specific fields and ImageRefs
 */
fun TelegramMediaItem.toRawMediaMetadata(): RawMediaMetadata {
        val rawTitle = extractRawTitle()
        // Structured year takes precedence over filename-parsed year
        val effectiveYear = structuredYear ?: year
        // Structured duration takes precedence over video duration
        val effectiveDurationMinutes = structuredLengthMinutes ?: durationSecs?.let { it / 60 }
        // Build ExternalIds with structured TMDB ID if available
        val externalIds = buildExternalIds()
        
        return RawMediaMetadata(
                originalTitle = rawTitle,
                mediaType = mapTelegramMediaType(),
                year = effectiveYear,
                season = seasonNumber,
                episode = episodeNumber,
                durationMinutes = effectiveDurationMinutes,
                externalIds = externalIds,
                sourceType = SourceType.TELEGRAM,
                sourceLabel = buildTelegramSourceLabel(),
                sourceId = remoteId ?: "msg:$chatId:$messageId",
                // === Pipeline Identity (v2) ===
                pipelineIdTag = PipelineIdTag.TELEGRAM,
                // === ImageRef from TelegramImageRefExtensions ===
                poster = toPosterImageRef(), // Photo or null for video
                backdrop = null, // Telegram doesn't provide backdrops
                thumbnail = toThumbnailImageRef(), // Video thumbnail or best photo size
                // === Minithumbnail for instant blur placeholder (Netflix-style tiered loading) ===
                placeholderThumbnail = toMinithumbnailImageRef(),
                // === Structured Bundle Rating Fields (v2.2) ===
                rating = structuredRating,
                ageRating = structuredFsk,
        )
}

/**
 * Builds ExternalIds with structured TMDB ID if available.
 *
 * Per TELEGRAM_STRUCTURED_BUNDLES_CONTRACT.md:
 * - Structured Bundles pass through TMDB IDs provided by the source
 * - The pipeline doesn't "guess" - it reads structured fields
 * - externalIds.tmdbId enables downstream unification by normalizer
 */
private fun TelegramMediaItem.buildExternalIds(): ExternalIds =
        if (structuredTmdbId != null) {
                ExternalIds(tmdbId = TmdbId(structuredTmdbId))
        } else {
                ExternalIds() // Telegram doesn't provide external IDs for non-structured content
        }

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
                isSeries || seasonNumber != null || episodeNumber != null ->
                        MediaType.SERIES_EPISODE
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
