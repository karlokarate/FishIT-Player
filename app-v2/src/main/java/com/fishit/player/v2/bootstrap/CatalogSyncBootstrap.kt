package com.fishit.player.v2.bootstrap

import com.fishit.player.core.catalogsync.CatalogSyncWorkScheduler
import com.fishit.player.core.sourceactivation.SourceActivationStore
import com.fishit.player.infra.logging.UnifiedLog
import com.fishit.player.v2.di.AppScopeModule
import com.fishit.player.v2.work.WorkerConstants
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/**
 * Bootstraps catalog synchronization once per app session after at least one source is active.
 *
 * Contract: STARTUP_TRIGGER_CONTRACT (T-1, T-2, T-3)
 * - Observes SourceActivationStore (SSOT for source state)
 * - Triggers catalog sync when activeSources.isNotEmpty()
 * - Supports all sources: Xtream, Telegram, IO (IO-only usage triggers sync correctly)
 * - Does NOT handle session initialization (that's XtreamSessionBootstrap's job)
 *
 * ## Sync Strategy (Industry Best Practice)
 *
 * Follows patterns from TiviMate, Kodi, and XCIPTV:
 *
 * 1. **Initial Full Sync**: AUTO mode on first launch (full catalog scan)
 * 2. **Periodic Incremental Sync**: Every 2 hours, only check for new items
 *    - Quick count comparison first
 *    - Only fetch items where `added > lastSyncTimestamp`
 *    - ~95% traffic reduction vs full scan
 */
@Singleton
class CatalogSyncBootstrap
    @Inject
    constructor(
        private val catalogSyncWorkScheduler: CatalogSyncWorkScheduler,
        private val sourceActivationStore: SourceActivationStore,
        @Named(AppScopeModule.APP_LIFECYCLE_SCOPE)
        private val appScope: CoroutineScope,
    ) {
        private val hasStarted = AtomicBoolean(false)
        private val hasTriggered = AtomicBoolean(false)

        fun start() {
            if (!hasStarted.compareAndSet(false, true)) {
                UnifiedLog.d(TAG) { "Catalog sync bootstrap already started, ignoring" }
                return
            }

            UnifiedLog.i(TAG) { "Catalog sync bootstrap collection started" }

            appScope.launch {
                try {
                    // Contract T-1: Wait for at least one active source
                    // Uses SourceActivationStore (SSOT) which includes Xtream, Telegram, and IO
                    sourceActivationStore
                        .observeStates()
                        .map { snapshot -> snapshot.activeSources }
                        .distinctUntilChanged()
                        .first { activeSources ->
                            val hasActive = activeSources.isNotEmpty()
                            UnifiedLog.d(TAG) { "Source activation check: activeSources=$activeSources, hasActive=$hasActive" }
                            hasActive
                        }

                    triggerSync()
                } catch (cancellation: CancellationException) {
                    UnifiedLog.d(TAG) { "Catalog sync bootstrap cancelled" }
                    throw cancellation
                } catch (t: Throwable) {
                    UnifiedLog.e(TAG, t) { "Catalog sync bootstrap failed to start" }
                }
            }
        }

        /**
         * Contract T-2: Mandatory delay gate before sync.
         * This prevents sync from racing with other startup tasks.
         */
        private suspend fun triggerSync() {
            if (!hasTriggered.compareAndSet(false, true)) return

            // Contract T-2: Delay gate - wait before triggering sync
            delay(SYNC_DELAY_MS)

            // Contract T-3: Final check - only sync if at least one source is still active
            val activeSources = sourceActivationStore.getActiveSources()
            if (activeSources.isEmpty()) {
                UnifiedLog.i(TAG) { "No sources active after delay, skipping catalog sync" }
                return
            }

            UnifiedLog.i(TAG) { "Catalog sync bootstrap triggered; activeSources=$activeSources" }

            // 1. Trigger initial full sync (AUTO mode)
            catalogSyncWorkScheduler.enqueueAutoSync()

            // 2. Schedule periodic incremental sync (every 2 hours)
            // This runs in background to check for newly added content
            // Uses count comparison + timestamp filtering for minimal traffic
            catalogSyncWorkScheduler.schedulePeriodicSync(
                WorkerConstants.PERIODIC_SYNC_INTERVAL_HOURS,
            )

            UnifiedLog.i(TAG) {
                "Periodic incremental sync scheduled: every ${WorkerConstants.PERIODIC_SYNC_INTERVAL_HOURS}h"
            }
        }

        private companion object {
            private const val TAG = "CatalogSyncBootstrap"

            /**
             * Contract T-2: Delay gate in milliseconds.
             * Prevents sync from racing with other startup operations.
             */
            private const val SYNC_DELAY_MS = 5_000L
        }
    }
