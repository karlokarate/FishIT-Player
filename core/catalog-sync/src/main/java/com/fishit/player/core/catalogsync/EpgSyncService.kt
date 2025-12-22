package com.fishit.player.core.catalogsync

/**
 * EPG Sync Service interface.
 *
 * This is a placeholder interface for future EPG synchronization functionality.
 * No implementation exists yet - this is intentionally a no-op interface.
 *
 * Contract: STARTUP_TRIGGER_CONTRACT (X-1)
 * - EPG sync will use WorkManager unique work: `epg_sync_global`
 * - No EPG network calls are made until the EPG contract is finalized
 *
 * TODO(EPG): Implement epg_sync_global and EPG normalization per upcoming EPG contract.
 */
interface EpgSyncService {

    /**
     * Request an EPG refresh.
     *
     * This is a no-op placeholder. When implemented, this will enqueue
     * EPG sync work via WorkManager with the unique work name `epg_sync_global`.
     *
     * @param reason Human-readable reason for the refresh request (for logging)
     */
    fun requestEpgRefresh(reason: String)
}
