package com.fishit.player.v2.work

import android.content.Context
import androidx.work.WorkManager
import com.fishit.player.core.catalogsync.TmdbEnrichmentScheduler
import com.fishit.player.core.metadata.tmdb.TmdbConfigProvider
import com.fishit.player.infra.logging.UnifiedLog
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * SSOT implementation of [TmdbEnrichmentScheduler] using WorkManager.
 *
 * Contract: CATALOG_SYNC_WORKERS_CONTRACT_V2
 * - uniqueWorkName = "tmdb_enrichment_global"
 * - W-22: TMDB Scope Priority: DETAILS_BY_ID → RESOLVE_MISSING_IDS
 * - W-4: TMDB API access MUST exist only via TmdbMetadataResolver
 *
 * **Early Exit (Dec 2025):**
 * If no TMDB API key is configured, no worker is enqueued at all.
 * This avoids unnecessary WorkManager overhead when TMDB is disabled.
 *
 * @see TmdbEnrichmentOrchestratorWorker for orchestration logic
 */
@Singleton
class TmdbEnrichmentSchedulerImpl
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val tmdbConfigProvider: TmdbConfigProvider,
    ) : TmdbEnrichmentScheduler {
        companion object {
            private const val TAG = "TmdbEnrichmentSchedulerImpl"
        }

        override fun enqueueEnrichment() {
            // Early exit: Don't enqueue worker if TMDB is disabled (no API key)
            if (!tmdbConfigProvider.getConfig().isEnabled) {
                UnifiedLog.d(TAG) { "TMDB enrichment skipped (no API key configured)" }
                return
            }
            UnifiedLog.i(TAG) { "Enqueuing TMDB enrichment (normal mode)" }
            TmdbEnrichmentOrchestratorWorker.enqueue(context, forceRefresh = false)
        }

        override fun enqueueForceRefresh() {
            // Early exit: Don't enqueue worker if TMDB is disabled (no API key)
            if (!tmdbConfigProvider.getConfig().isEnabled) {
                UnifiedLog.d(TAG) { "TMDB force refresh skipped (no API key configured)" }
                return
            }
            UnifiedLog.i(TAG) { "Enqueuing TMDB enrichment (force refresh)" }
            TmdbEnrichmentOrchestratorWorker.enqueue(context, forceRefresh = true)
        }

        override fun cancelEnrichment() {
            UnifiedLog.i(TAG) { "Cancelling TMDB enrichment work" }
            WorkManager
                .getInstance(context)
                .cancelUniqueWork(WorkerConstants.WORK_NAME_TMDB_ENRICHMENT)

            // Also cancel the batch and continuation sub-works
            WorkManager
                .getInstance(context)
                .cancelUniqueWork("${WorkerConstants.WORK_NAME_TMDB_ENRICHMENT}_batch")
            WorkManager
                .getInstance(context)
                .cancelUniqueWork("${WorkerConstants.WORK_NAME_TMDB_ENRICHMENT}_continuation")
        }
    }
