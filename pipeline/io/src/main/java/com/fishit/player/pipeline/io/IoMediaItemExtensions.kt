package com.fishit.player.pipeline.io

import com.fishit.player.core.playermodel.PlaybackContext
import com.fishit.player.core.model.SourceType

/**
 * Extension functions for converting IO pipeline models to core models.
 */

/**
 * Converts an IoMediaItem to RawMediaMetadata for centralized normalization.
 *
 * **IMPORTANT: Temporary Placeholder Implementation**
 *
 * This function currently returns a `Map<String, Any?>` as a structural placeholder for
 * the future `RawMediaMetadata` data class that will be defined in `:core:model`.
 *
 * - The Map keys exactly mirror the `RawMediaMetadata` fields defined in
 *   `docs/v2/MEDIA_NORMALIZATION_CONTRACT.md` (Section 1.1).
 * - Once `RawMediaMetadata` is added to `:core:model`, this function signature will change
 *   from `Map<String, Any?>` to `RawMediaMetadata`.
 * - **DO NOT** define a local `RawMediaMetadata` type in `:pipeline:io`. The shared type
 *   must come from `:core:model` to ensure consistency across all pipelines.
 *
 * **Contract Compliance:**
 *
 * This function strictly follows the Media Normalization Contract:
 * - `originalTitle`: Raw filename WITHOUT any cleaning, stripping, or normalization
 * - `year`, `season`, `episode`: Always null (extraction is the normalizer's responsibility)
 * - `durationMinutes`: Forwarded if available (converted from ms to minutes)
 * - `externalIds`: Empty map (filesystem does not provide TMDB/IMDB IDs)
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
 * @return Map with keys matching RawMediaMetadata structure from MEDIA_NORMALIZATION_CONTRACT.md
 *
 * @see <a href="file:///docs/v2/MEDIA_NORMALIZATION_CONTRACT.md">MEDIA_NORMALIZATION_CONTRACT.md Section 1.1</a>
 * @see <a href="file:///docs/v2/MEDIA_NORMALIZATION_AND_UNIFICATION.md">MEDIA_NORMALIZATION_AND_UNIFICATION.md</a>
 */
fun IoMediaItem.toRawMediaMetadata(): Map<String, Any?> =
    mapOf(
        // Raw filename as originalTitle - NO CLEANING, NO NORMALIZATION
        "originalTitle" to fileName,
        // Year extraction not performed by IO pipeline - reserved for normalizer
        "year" to null,
        // Season/episode extraction not performed by IO pipeline
        "season" to null,
        "episode" to null,
        // Duration forwarded if available, converted from ms to minutes
        "durationMinutes" to durationMs?.let { (it / 60_000).toInt() },
        // externalIds: Empty map as placeholder for ExternalIds type
        // (filesystem does not provide TMDB/IMDB IDs)
        "externalIds" to emptyMap<String, String>(),
        // Source identification
        "sourceType" to "IO",
        "sourceLabel" to "Local File: $fileName",
        "sourceId" to toContentId(),
    )

/**
 * Converts an IoMediaItem to a PlaybackContext.
 *
 * This helper function bridges the IO pipeline domain model to the
 * playback-agnostic PlaybackContext used by the internal player.
 *
 * **Usage:**
 * ```kotlin
 * val item: IoMediaItem = repository.getItemById("some-id")
 * val context = item.toPlaybackContext()
 * // Navigate to InternalPlayerEntry(context)
 * ```
 *
 * @param startPositionMs Starting position for resume (optional).
 * @return PlaybackContext suitable for InternalPlayerEntry.
 */
fun IoMediaItem.toPlaybackContext(
    startPositionMs: Long = 0L,
): PlaybackContext =
    PlaybackContext(
        canonicalId = "io:${toContentId()}",
        sourceType = SourceType.IO,
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
