package com.fishit.player.core.catalogsync

import com.fishit.player.core.model.MediaSourceRef
import com.fishit.player.core.model.RawMediaMetadata
import com.fishit.player.core.model.SourceType
import com.fishit.player.core.model.ids.asPipelineItemId

/**
 * Utility for building MediaSourceRef instances from RawMediaMetadata.
 *
 * **Purpose:** SSOT for source reference creation logic used in catalog sync operations.
 * Ensures consistent source priority calculation and MediaSourceRef construction across:
 * - DefaultCatalogSyncService (hot path canonical linking)
 * - CanonicalLinkingBacklogWorker (deferred canonical linking)
 *
 * **Architecture Position:** core/catalog-sync (shared utility)
 * - Used by: DefaultCatalogSyncService, CanonicalLinkingBacklogWorker
 * - Depends on: core/model (RawMediaMetadata, MediaSourceRef)
 *
 * **Per core-catalog-sync.instructions.md:**
 * - Pure utility (no state, no side effects)
 * - Works only with RawMediaMetadata (source-agnostic)
 * - No business logic (normalization, classification)
 * - No repository dependencies
 *
 * **Usage:**
 * ```kotlin
 * val sourceRef = MediaSourceRefBuilder.fromRawMetadata(rawMetadata)
 * canonicalMediaRepository.addOrUpdateSourceRef(canonicalId, sourceRef)
 * ```
 */
object MediaSourceRefBuilder {
    /**
     * Convert RawMediaMetadata to MediaSourceRef for canonical linking.
     *
     * **Source Priority (Higher = Preferred):**
     * - Xtream: 100 (structured metadata, reliable quality)
     * - IO: 75 (local files, good quality)
     * - Telegram: 50 (community sources, variable quality)
     * - Audiobook: 25 (specialized content)
     * - Others: 0 (fallback)
     *
     * **Note:** Priority affects source selection in multi-source scenarios.
     * The Detail screen shows sources ordered by priority (highest first).
     */
    fun fromRawMetadata(raw: RawMediaMetadata): MediaSourceRef =
        MediaSourceRef(
            sourceType = raw.sourceType,
            sourceId = raw.sourceId.asPipelineItemId(),
            sourceLabel = raw.sourceLabel,
            quality = null, // Not yet extracted from metadata
            languages = null, // Not yet extracted from metadata
            format = null, // Not yet extracted from metadata
            sizeBytes = null, // Not available in RawMediaMetadata
            durationMs = raw.durationMs,
            playbackHints = raw.playbackHints,
            priority = calculateSourcePriority(raw.sourceType),
        )

    /**
     * Calculate source priority for ordering in source selection.
     *
     * **Design Note:** Xtream gets highest priority because it provides:
     * - Structured metadata (genres, plot, cast)
     * - Reliable streaming URLs
     * - EPG data for live content
     *
     * Telegram gets lower priority due to:
     * - Variable quality (community uploads)
     * - Potential for duplicate/incomplete uploads
     * - Less structured metadata
     */
    private fun calculateSourcePriority(sourceType: SourceType): Int =
        when (sourceType) {
            SourceType.XTREAM -> 100
            SourceType.TELEGRAM -> 50
            SourceType.IO -> 75
            SourceType.AUDIOBOOK -> 25
            else -> 0
        }
}
