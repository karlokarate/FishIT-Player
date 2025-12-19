package com.fishit.player.core.catalogsync

/**
 * Schedules TMDB enrichment work (via WorkManager).
 *
 * This enriches catalog items with TMDB metadata after catalog sync completes.
 * Runs as background work separate from catalog sync.
 *
 * SSOT Contract (CATALOG_SYNC_WORKERS_CONTRACT_V2):
 * - uniqueWorkName = "tmdb_enrichment_global"
 * - W-22: TMDB Scope Priority: DETAILS_BY_ID → RESOLVE_MISSING_IDS
 * - W-4: TMDB API access MUST exist only via TmdbMetadataResolver
 */
interface TmdbEnrichmentScheduler {
    
    /**
     * Enqueue TMDB enrichment with default settings.
     * Uses ExistingWorkPolicy.KEEP (won't interrupt running enrichment).
     * Processes DETAILS_BY_ID first, then RESOLVE_MISSING_IDS.
     */
    fun enqueueEnrichment()

    /**
     * Enqueue a force TMDB refresh that replaces any running enrichment.
     * Uses ExistingWorkPolicy.REPLACE (cancels running enrichment and restarts).
     * Forces re-enrichment of all items.
     */
    fun enqueueForceRefresh()

    /**
     * Cancel any currently running or enqueued TMDB enrichment work.
     */
    fun cancelEnrichment()
}
