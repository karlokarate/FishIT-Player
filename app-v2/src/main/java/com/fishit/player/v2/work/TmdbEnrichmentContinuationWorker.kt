package com.fishit.player.v2.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.fishit.player.core.metadata.tmdb.TmdbConfigProvider
import com.fishit.player.core.model.repository.CanonicalMediaRepository
import com.fishit.player.infra.logging.UnifiedLog
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

/**
 * TMDB Enrichment Continuation Worker.
 *
 * Checks if more items need processing and schedules the next batch.
 * Handles scope transitions (DETAILS_BY_ID → RESOLVE_MISSING_IDS).
 *
 * Contract: CATALOG_SYNC_WORKERS_CONTRACT_V2
 * - W-22: TMDB Scope Priority (MANDATORY): DETAILS_BY_ID first, then RESOLVE_MISSING_IDS
 *
 * Flow:
 * 1. Check if current scope has more candidates
 * 2. If yes, schedule next batch for current scope
 * 3. If no and original scope was BOTH, check next priority scope
 * 4. If no more candidates, enrichment is complete
 *
 * @see TmdbEnrichmentOrchestratorWorker for initial scheduling
 * @see TmdbEnrichmentBatchWorker for actual enrichment
 */
@HiltWorker
class TmdbEnrichmentContinuationWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val tmdbConfigProvider: TmdbConfigProvider,
    private val canonicalMediaRepository: CanonicalMediaRepository,
) : CoroutineWorker(context, workerParams) {

    companion object {
        private const val TAG = "TmdbEnrichmentContinuationWorker"
    }

    override suspend fun doWork(): Result {
        val input = TmdbWorkerInputData.from(inputData)
        val startTimeMs = System.currentTimeMillis()

        UnifiedLog.i(TAG) {
            "START run_id=${input.runId} scope=${input.tmdbScope}"
        }

        // W-16: Check runtime guards
        val guardReason = RuntimeGuards.checkGuards(applicationContext)
        if (guardReason != null) {
            UnifiedLog.w(TAG) { "GUARD_DEFER reason=$guardReason" }
            return Result.retry()
        }

        // Check if TMDB is enabled
        val tmdbConfig = tmdbConfigProvider.getConfig()
        if (!tmdbConfig.isEnabled) {
            val durationMs = System.currentTimeMillis() - startTimeMs
            UnifiedLog.i(TAG) {
                "SUCCESS duration_ms=$durationMs (TMDB disabled)"
            }
            return Result.success(
                WorkerOutputData.success(itemsPersisted = 0, durationMs = durationMs)
            )
        }

        // Check for more candidates in current scope
        val now = System.currentTimeMillis()
        val hasMoreInCurrentScope = when (input.tmdbScope) {
            WorkerConstants.TMDB_SCOPE_DETAILS_BY_ID -> {
                canonicalMediaRepository
                    .findCandidatesDetailsByIdMissingSsot(limit = 1)
                    .isNotEmpty()
            }
            WorkerConstants.TMDB_SCOPE_RESOLVE_MISSING_IDS -> {
                canonicalMediaRepository
                    .findCandidatesMissingTmdbRefEligible(limit = 1, now = now)
                    .isNotEmpty()
            }
            else -> false
        }

        if (hasMoreInCurrentScope) {
            // Continue with current scope
            scheduleNextBatch(input, input.tmdbScope)
            val durationMs = System.currentTimeMillis() - startTimeMs
            UnifiedLog.i(TAG) {
                "SUCCESS duration_ms=$durationMs (scheduled next batch for ${input.tmdbScope})"
            }
            return Result.success(
                WorkerOutputData.success(itemsPersisted = 0, durationMs = durationMs)
            )
        }

        // Current scope exhausted - check if we should transition to next scope
        // This only applies if original scope was BOTH or DETAILS_BY_ID
        if (input.tmdbScope == WorkerConstants.TMDB_SCOPE_DETAILS_BY_ID) {
            // Check if RESOLVE_MISSING_IDS has candidates
            val hasSearchCandidates = canonicalMediaRepository
                .findCandidatesMissingTmdbRefEligible(limit = 1, now = now)
                .isNotEmpty()

            if (hasSearchCandidates) {
                // Transition to RESOLVE_MISSING_IDS scope
                scheduleNextBatch(input, WorkerConstants.TMDB_SCOPE_RESOLVE_MISSING_IDS)
                val durationMs = System.currentTimeMillis() - startTimeMs
                UnifiedLog.i(TAG) {
                    "SUCCESS duration_ms=$durationMs (transitioning to RESOLVE_MISSING_IDS)"
                }
                return Result.success(
                    WorkerOutputData.success(itemsPersisted = 0, durationMs = durationMs)
                )
            }
        }

        // All scopes exhausted - enrichment complete
        val durationMs = System.currentTimeMillis() - startTimeMs
        UnifiedLog.i(TAG) {
            "SUCCESS duration_ms=$durationMs (enrichment complete, no more candidates)"
        }
        return Result.success(
            WorkerOutputData.success(itemsPersisted = 0, durationMs = durationMs)
        )
    }

    /**
     * Schedule the next batch worker.
     */
    private fun scheduleNextBatch(input: TmdbWorkerInputData, scope: String) {
        val batchInputData = TmdbWorkerInputData.buildInputData(
            runId = input.runId,
            tmdbScope = scope,
            forceRefresh = input.forceRefresh,
            batchSizeHint = input.batchSizeHint,
            batchCursor = input.batchCursor,
            deviceClass = input.deviceClass,
            maxRuntimeMs = input.maxRuntimeMs,
        )

        val request = OneTimeWorkRequestBuilder<TmdbEnrichmentBatchWorker>()
            .setInputData(batchInputData)
            .addTag(WorkerConstants.TAG_CATALOG_SYNC)
            .addTag(WorkerConstants.TAG_SOURCE_TMDB)
            .addTag(WorkerConstants.TAG_WORKER_TMDB_BATCH)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                WorkerConstants.BACKOFF_INITIAL_SECONDS,
                TimeUnit.SECONDS
            )
            .build()

        WorkManager.getInstance(applicationContext)
            .beginUniqueWork(
                "${WorkerConstants.WORK_NAME_TMDB_ENRICHMENT}_batch",
                ExistingWorkPolicy.REPLACE,
                request
            )
            .enqueue()

        UnifiedLog.d(TAG) { "Scheduled next batch worker for scope=$scope" }
    }
}
