package com.fishit.player.core.catalogsync

import kotlinx.coroutines.flow.Flow

/**
 * Interface for observing sync state.
 *
 * Abstraction over WorkManager state observation, allowing feature modules
 * to observe sync state without depending on WorkManager directly.
 *
 * Implementation: [CatalogSyncUiBridge] in app-v2 module.
 * Contract: CATALOG_SYNC_WORKERS_CONTRACT_V2
 */
interface SyncStateObserver {
    /**
     * Observe the current sync UI state as a Flow.
     *
     * Emits [SyncUiState] whenever sync state changes:
     * - [SyncUiState.Idle] - No sync running
     * - [SyncUiState.Running] - Sync in progress
     * - [SyncUiState.Success] - Last sync completed successfully
     * - [SyncUiState.Failed] - Last sync failed
     */
    fun observeSyncState(): Flow<SyncUiState>

    /**
     * Get current sync state synchronously.
     *
     * Useful for initial UI state before Flow collection starts.
     */
    fun getCurrentState(): SyncUiState
}
