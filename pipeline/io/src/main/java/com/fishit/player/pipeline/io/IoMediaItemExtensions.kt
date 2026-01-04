package com.fishit.player.pipeline.io

import com.fishit.player.core.model.MediaType
import com.fishit.player.core.model.PipelineIdTag
import com.fishit.player.core.model.RawMediaMetadata
import com.fishit.player.core.playermodel.PlaybackContext
import com.fishit.player.core.playermodel.SourceType
import com.fishit.player.core.model.SourceType as CoreSourceType

/**
 * Extension functions for converting IO pipeline models to core models.
 */

/**
 * Converts an IoMediaItem to RawMediaMetadata for centralized normalization.
 *
 * **Contract Compliance:**
 *
 * This function strictly follows the Media Normalization Contract:
 * - `originalTitle`: Raw filename WITHOUT any cleaning, stripping, or normalization
 * - `year`, `season`, `episode`: Always null (extraction is the normalizer's responsibility)
 * - `durationMs`: Forwarded directly from source (already in milliseconds)
 * - `externalIds`: Empty (filesystem does not provide TMDB/IMDB IDs)
 * - `sourceType`, `sourceLabel`, `sourceId`: IO-specific identification
 *
 * **IO Pipeline Does NOT:**
 * - Clean titles or strip tags (scene-style names, resolution, release groups preserved)
 * - Extract year/season/episode from filenames
 * - Perform TMDB/IMDB lookups
 * - Apply any heuristics or normalization logic
 *
 * All normalization, matching, and identity resolution is handled centrally by
 * `:core:metadata-normalizer` and TMDB resolver services.
 *
 * @return RawMediaMetadata for downstream normalization
 */
fun IoMediaItem.toRawMediaMetadata(): RawMediaMetadata =
    RawMediaMetadata(
        originalTitle = fileName,
        mediaType = inferMediaType(),
        year = null,
        season = null,
        episode = null,
        durationMs = durationMs, // Already in milliseconds from source
        sourceType = CoreSourceType.IO,
        sourceLabel = "Local File: $fileName",
        sourceId = toContentId(),
        pipelineIdTag = PipelineIdTag.IO,
    )

/**
 * Infers MediaType from MIME type if available.
 */
private fun IoMediaItem.inferMediaType(): MediaType =
    when {
        mimeType?.startsWith("video/") == true -> MediaType.CLIP
        mimeType?.startsWith("audio/") == true -> MediaType.CLIP
        else -> MediaType.UNKNOWN
    }

/**
 * Converts an IoMediaItem to a PlaybackContext.
 *
 * @param startPositionMs Starting position for resume (optional).
 * @return PlaybackContext suitable for InternalPlayerEntry.
 */
fun IoMediaItem.toPlaybackContext(startPositionMs: Long = 0L): PlaybackContext =
    PlaybackContext(
        canonicalId = "io:${toContentId()}",
        sourceType = SourceType.FILE,
        uri = source.toUriString(),
        title = title,
        subtitle = fileName,
        posterUrl = thumbnailPath,
        startPositionMs = startPositionMs,
        extras =
            buildMap {
                mimeType?.let { put("mimeType", it) }
                sizeBytes?.let { put("sizeBytes", it.toString()) }
                durationMs?.let { put("durationMs", it.toString()) }
                putAll(metadata)
            },
    )
