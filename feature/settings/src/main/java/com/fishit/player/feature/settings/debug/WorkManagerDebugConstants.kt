package com.fishit.player.feature.settings.debug

/**
 * WorkManager naming/tags are part of the catalog sync SSOT contract.
 *
 * We keep these constants in the debug UI layer to avoid depending on app-v2.
 * If the contract changes, update both the scheduler and these debug constants.
 */
object WorkManagerDebugConstants {
    // Unique work names
    const val WORK_NAME_CATALOG_SYNC = "catalog_sync_global"
    const val WORK_NAME_TMDB_ENRICHMENT = "tmdb_enrichment_global"

    // Tags
    const val TAG_CATALOG_SYNC = "catalog_sync"
    const val TAG_SOURCE_TMDB = "source_tmdb"
}
