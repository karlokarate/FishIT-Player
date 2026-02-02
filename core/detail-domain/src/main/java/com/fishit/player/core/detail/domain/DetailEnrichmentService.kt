package com.fishit.player.core.detail.domain

import com.fishit.player.core.model.CanonicalMediaId
import com.fishit.player.core.model.ids.PipelineItemId
import com.fishit.player.core.model.repository.CanonicalMediaWithSources

/**
 * Service for enriching canonical media with detail information from source APIs.
 *
 * **Priority Levels:**
 * - [enrichImmediate] - HIGH priority, pauses background sync
 * - [ensureEnriched] - CRITICAL priority, blocking with timeout (for playback)
 * - [enrichIfNeeded] - Normal priority, runs in background
 *
 * **Usage:**
 * - Call [enrichImmediate] when user clicks a tile (Detail Screen open)
 * - Call [ensureEnriched] before playback starts (Play button)
 * - Call [enrichIfNeeded] for background pre-fetch
 */
interface DetailEnrichmentService {
    /**
     * Ensure media is enriched with a blocking timeout (CRITICAL priority).
     *
     * Used before playback to guarantee containerExtension and other hints are available.
     * This is the highest priority level and will block other operations.
     *
     * @param canonicalId The canonical media ID
     * @param sourceKey Optional specific source to enrich
     * @param requiredHints List of hint keys that must be present
     * @param timeoutMs Maximum time to wait for enrichment
     * @return Enriched media or null if timeout/failure
     */
    suspend fun ensureEnriched(
        canonicalId: CanonicalMediaId,
        sourceKey: PipelineItemId? = null,
        requiredHints: List<String> = emptyList(),
        timeoutMs: Long = DEFAULT_TIMEOUT_MS,
    ): CanonicalMediaWithSources?

    /**
     * Enrich media immediately with HIGH priority.
     *
     * Call this when user opens a Detail Screen. This will:
     * - Pause background catalog sync
     * - Execute the detail API call (getVodInfo/getSeriesInfo) immediately
     * - Resume background sync after completion
     *
     * If media is already enriched, returns immediately without API call.
     *
     * @param media The canonical media to enrich
     * @return Enriched media (same instance if already enriched)
     */
    suspend fun enrichImmediate(media: CanonicalMediaWithSources): CanonicalMediaWithSources

    /**
     * Check and enrich media if needed (normal background priority).
     *
     * Runs at normal priority without affecting background sync.
     * Use for pre-fetching or lazy loading scenarios.
     *
     * @param media The canonical media to check/enrich
     * @return Enriched media (same instance if already enriched)
     */
    suspend fun enrichIfNeeded(media: CanonicalMediaWithSources): CanonicalMediaWithSources

    companion object {
        const val DEFAULT_TIMEOUT_MS = 8000L
    }
}
