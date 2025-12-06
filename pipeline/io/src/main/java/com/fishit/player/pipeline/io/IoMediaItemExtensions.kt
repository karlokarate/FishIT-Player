package com.fishit.player.pipeline.io

import com.fishit.player.core.model.PlaybackContext
import com.fishit.player.core.model.PlaybackType

/**
 * Extension functions for converting IO pipeline models to core models.
 */

/**
 * Converts an IoMediaItem to RawMediaMetadata for centralized normalization.
 *
 * This function provides the raw, unprocessed metadata from the IO pipeline to the
 * `:core:metadata-normalizer` module. Following the Media Normalization Contract:
 *
 * **Contract Compliance:**
 * - `originalTitle` is set to the raw filename WITHOUT any cleaning, stripping, or normalization
 * - NO title normalization, scene-style parsing, or tag stripping occurs here
 * - Duration is forwarded if available (converted from milliseconds to minutes)
 * - All other metadata is forwarded as-is from the source
 *
 * **Important:**
 * - IO pipeline does NOT perform:
 *   - Title cleaning
 *   - Edition tag removal
 *   - Resolution tag stripping
 *   - TMDB lookups
 *   - Heuristic parsing
 * - All normalization is centralized in `:core:metadata-normalizer`
 *
 * **Future Implementation:**
 * This function currently returns a structural map representing RawMediaMetadata.
 * Once the actual `RawMediaMetadata` type is added to `:core:model`, this will be
 * updated to return that type instead.
 *
 * @return Map representing RawMediaMetadata structure per MEDIA_NORMALIZATION_CONTRACT.md
 *
 * @see <a href="file:///v2-docs/MEDIA_NORMALIZATION_CONTRACT.md">MEDIA_NORMALIZATION_CONTRACT.md</a>
 * @see <a href="file:///v2-docs/MEDIA_NORMALIZATION_AND_UNIFICATION.md">MEDIA_NORMALIZATION_AND_UNIFICATION.md</a>
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
        // No external IDs available from raw filesystem
        "externalIds" to emptyMap<String, String>(),
        // Source identification
        "sourceType" to "IO",
        "sourceLabel" to "Local File: ${fileName}",
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
 * @param profileId Current profile ID for tracking (optional).
 * @param startPositionMs Starting position for resume (optional).
 * @param isKidsContent Whether to treat as kids content (optional).
 * @return PlaybackContext suitable for InternalPlayerEntry.
 */
fun IoMediaItem.toPlaybackContext(
    profileId: Long? = null,
    startPositionMs: Long = 0L,
    isKidsContent: Boolean = false,
): PlaybackContext =
    PlaybackContext(
        type = PlaybackType.IO,
        uri = source.toUriString(),
        title = title,
        subtitle = fileName,
        posterUrl = thumbnailPath,
        contentId = toContentId(),
        startPositionMs = startPositionMs,
        isKidsContent = isKidsContent,
        profileId = profileId,
        extras =
            buildMap {
                mimeType?.let { put("mimeType", it) }
                sizeBytes?.let { put("sizeBytes", it.toString()) }
                durationMs?.let { put("durationMs", it.toString()) }
                putAll(metadata)
            },
    )
