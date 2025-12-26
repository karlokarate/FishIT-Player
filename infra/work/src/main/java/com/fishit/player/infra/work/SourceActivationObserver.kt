package com.fishit.player.infra.work

import com.fishit.player.core.catalogsync.CatalogSyncWorkScheduler
import com.fishit.player.core.sourceactivation.SourceActivationSnapshot
import com.fishit.player.core.sourceactivation.SourceActivationStore
import com.fishit.player.infra.logging.UnifiedLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Observes [SourceActivationStore] changes and triggers sync scheduling accordingly.
 * 
 * Contract: CATALOG_SYNC_WORKERS_CONTRACT_V2
 * - When activation snapshot changes and sources are non-empty → enqueueAutoSync()
 * - When activeSources becomes empty → cancelSync()
 * 
 * This class bridges the activation store with the work scheduler.
 * It should be started once during app initialization.
 */
@Singleton
class SourceActivationObserver @Inject constructor(
    private val sourceActivationStore: SourceActivationStore,
    private val catalogSyncWorkScheduler: CatalogSyncWorkScheduler,
) {
    private var isStarted = false
    
    /**
     * Start observing activation state changes.
     * 
     * @param scope CoroutineScope tied to app lifecycle
     */
    fun start(scope: CoroutineScope) {
        if (isStarted) {
            UnifiedLog.w(TAG) { "SourceActivationObserver already started, ignoring" }
            return
        }
        isStarted = true
        
        UnifiedLog.i(TAG) { "Starting activation observer" }
        
        scope.launch {
            sourceActivationStore.observeStates()
                // Skip initial emission (handled by CatalogSyncBootstrap)
                .drop(1)
                // Only react when activeSources set changes
                .map { snapshot -> snapshot.activeSources }
                .distinctUntilChanged()
                .collect { activeSources ->
                    handleActivationChange(activeSources)
                }
        }
    }
    
    private fun handleActivationChange(activeSources: Set<com.fishit.player.core.catalogsync.SourceId>) {
        if (activeSources.isNotEmpty()) {
            UnifiedLog.i(TAG) { "Active sources changed: $activeSources → enqueuing auto sync" }
            catalogSyncWorkScheduler.enqueueAutoSync()
        } else {
            UnifiedLog.i(TAG) { "No active sources → cancelling sync" }
            catalogSyncWorkScheduler.cancelSync()
        }
    }
    
    private companion object {
        private const val TAG = "SourceActivationObserver"
    }
}
