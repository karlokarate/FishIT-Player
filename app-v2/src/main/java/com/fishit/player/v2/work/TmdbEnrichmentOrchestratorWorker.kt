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
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * TMDB Enrichment Orchestrator Worker.
 *
 * Entry point for TMDB enrichment. Determines which scope to process
 * and enqueues the appropriate batch worker.
 *
 * Contract: CATALOG_SYNC_WORKERS_CONTRACT_V2
 * - W-4: TMDB API access MUST exist only via TmdbMetadataResolver (MANDATORY)
 * - W-20: Non-retryable: TMDB API key missing (MANDATORY)
 * - W-21: Typed canonical identity (MANDATORY)
 * - W-22: TMDB Scope Priority (MANDATORY): DETAILS_BY_ID → RESOLVE_MISSING_IDS
 *
 * Work name: "tmdb_enrichment_global"
 * Default policy: ExistingWorkPolicy.KEEP
 * Expert force refresh: ExistingWorkPolicy.REPLACE
 *
 * @see TmdbEnrichmentBatchWorker for actual enrichment logic
 * @see TmdbEnrichmentContinuationWorker for continuation scheduling
 */
@HiltWorker
class TmdbEnrichmentOrchestratorWorker
    @AssistedInject
    constructor(
        @Assisted context: Context,
        @Assisted workerParams: WorkerParameters,
        private val tmdbConfigProvider: TmdbConfigProvider,
        private val canonicalMediaRepository: CanonicalMediaRepository,
    ) : CoroutineWorker(context, workerParams) {
        companion object {
            private const val TAG = "TmdbEnrichmentOrchestratorWorker"

            /**
             * Enqueue TMDB enrichment with default settings.
             * Uses ExistingWorkPolicy.KEEP to avoid interrupting running enrichment.
             */
            fun enqueue(
                context: Context,
                forceRefresh: Boolean = false,
            ) {
                val deviceClass = RuntimeGuards.detectDeviceClass(context)
                val inputData =
                    TmdbWorkerInputData.buildInputData(
                        runId = UUID.randomUUID().toString(),
                        tmdbScope = WorkerConstants.TMDB_SCOPE_BOTH,
                        forceRefresh = forceRefresh,
                        deviceClass = deviceClass,
                    )

                val policy =
                    if (forceRefresh) {
                        ExistingWorkPolicy.REPLACE
                    } else {
                        ExistingWorkPolicy.KEEP
                    }

                val request =
                    OneTimeWorkRequestBuilder<TmdbEnrichmentOrchestratorWorker>()
                        .setInputData(inputData)
                        .addTag(WorkerConstants.TAG_CATALOG_SYNC)
                        .addTag(WorkerConstants.TAG_SOURCE_TMDB)
                        .addTag(WorkerConstants.TAG_WORKER_TMDB_ORCHESTRATOR)
                        .setBackoffCriteria(
                            BackoffPolicy.EXPONENTIAL,
                            WorkerConstants.BACKOFF_INITIAL_SECONDS,
                            TimeUnit.SECONDS,
                        ).build()

                WorkManager
                    .getInstance(context)
                    .enqueueUniqueWork(
                        WorkerConstants.WORK_NAME_TMDB_ENRICHMENT,
                        policy,
                        request,
                    )

                UnifiedLog.i(TAG) {
                    "Enqueued TMDB enrichment: forceRefresh=$forceRefresh, policy=$policy"
                }
            }
        }

        override suspend fun doWork(): Result {
            val input = TmdbWorkerInputData.from(inputData)
            val startTimeMs = System.currentTimeMillis()

            UnifiedLog.i(TAG) {
                "START run_id=${input.runId} scope=${input.tmdbScope} forceRefresh=${input.forceRefresh}"
            }

            // W-16: Check runtime guards
            val guardReason = RuntimeGuards.checkGuards(applicationContext)
            if (guardReason != null) {
                UnifiedLog.w(TAG) { "GUARD_DEFER reason=$guardReason" }
                return Result.retry()
            }

            // W-20: Check if TMDB is enabled (API key present)
            val tmdbConfig = tmdbConfigProvider.getConfig()
            if (!tmdbConfig.isEnabled) {
                val durationMs = System.currentTimeMillis() - startTimeMs
                UnifiedLog.i(TAG) {
                    "SUCCESS duration_ms=$durationMs (TMDB disabled, no API key)"
                }
                return Result.success(
                    WorkerOutputData.success(
                        itemsPersisted = 0,
                        durationMs = durationMs,
                    ),
                )
            }

            // Determine which scope to process (W-22: Priority order)
            val scopeToProcess = determineScope(input)

            if (scopeToProcess == null) {
                val durationMs = System.currentTimeMillis() - startTimeMs
                UnifiedLog.i(TAG) {
                    "SUCCESS duration_ms=$durationMs (no candidates need enrichment)"
                }
                return Result.success(
                    WorkerOutputData.success(
                        itemsPersisted = 0,
                        durationMs = durationMs,
                    ),
                )
            }

            // Enqueue batch worker for the determined scope
            enqueueBatchWorker(input, scopeToProcess)

            val durationMs = System.currentTimeMillis() - startTimeMs
            UnifiedLog.i(TAG) {
                "SUCCESS duration_ms=$durationMs (enqueued batch for scope=$scopeToProcess)"
            }

            return Result.success(
                WorkerOutputData.success(
                    itemsPersisted = 0,
                    durationMs = durationMs,
                ),
            )
        }

        /**
         * Determine which TMDB scope to process based on priority order (W-22).
         *
         * Priority:
         * 1. DETAILS_BY_ID - items with TmdbRef but missing SSOT data
         * 2. RESOLVE_MISSING_IDS - items without TmdbRef eligible for search
         *
         * @return Scope to process, or null if no candidates
         */
        private suspend fun determineScope(input: TmdbWorkerInputData): String? {
            val requestedScope = input.tmdbScope
            val now = System.currentTimeMillis()

            return when (requestedScope) {
                WorkerConstants.TMDB_SCOPE_DETAILS_BY_ID -> {
                    // Check if there are DETAILS_BY_ID candidates
                    val detailsCandidates =
                        canonicalMediaRepository
                            .findCandidatesDetailsByIdMissingSsot(limit = 1)
                    if (detailsCandidates.isNotEmpty()) {
                        WorkerConstants.TMDB_SCOPE_DETAILS_BY_ID
                    } else {
                        null
                    }
                }

                WorkerConstants.TMDB_SCOPE_RESOLVE_MISSING_IDS -> {
                    // Check if there are RESOLVE_MISSING_IDS candidates
                    val searchCandidates =
                        canonicalMediaRepository
                            .findCandidatesMissingTmdbRefEligible(limit = 1, now = now)
                    if (searchCandidates.isNotEmpty()) {
                        WorkerConstants.TMDB_SCOPE_RESOLVE_MISSING_IDS
                    } else {
                        null
                    }
                }

                WorkerConstants.TMDB_SCOPE_BOTH -> {
                    // Priority 1: DETAILS_BY_ID first
                    val detailsCandidates =
                        canonicalMediaRepository
                            .findCandidatesDetailsByIdMissingSsot(limit = 1)
                    if (detailsCandidates.isNotEmpty()) {
                        return WorkerConstants.TMDB_SCOPE_DETAILS_BY_ID
                    }

                    // Priority 2: RESOLVE_MISSING_IDS
                    val searchCandidates =
                        canonicalMediaRepository
                            .findCandidatesMissingTmdbRefEligible(limit = 1, now = now)
                    if (searchCandidates.isNotEmpty()) {
                        return WorkerConstants.TMDB_SCOPE_RESOLVE_MISSING_IDS
                    }

                    null
                }

                else -> {
                    UnifiedLog.w(TAG) { "Unknown TMDB scope: $requestedScope, defaulting to BOTH" }
                    determineScope(input.copy(tmdbScope = WorkerConstants.TMDB_SCOPE_BOTH))
                }
            }
        }

        /**
         * Enqueue the batch worker with appropriate input data.
         */
        private fun enqueueBatchWorker(
            input: TmdbWorkerInputData,
            scope: String,
        ) {
            val batchInputData =
                TmdbWorkerInputData.buildInputData(
                    runId = input.runId,
                    tmdbScope = scope,
                    forceRefresh = input.forceRefresh,
                    batchSizeHint = input.batchSizeHint,
                    batchCursor = null, // First batch starts from beginning
                    deviceClass = input.deviceClass,
                    maxRuntimeMs = input.maxRuntimeMs,
                )

            val request =
                OneTimeWorkRequestBuilder<TmdbEnrichmentBatchWorker>()
                    .setInputData(batchInputData)
                    .addTag(WorkerConstants.TAG_CATALOG_SYNC)
                    .addTag(WorkerConstants.TAG_SOURCE_TMDB)
                    .addTag(WorkerConstants.TAG_WORKER_TMDB_BATCH)
                    .setBackoffCriteria(
                        BackoffPolicy.EXPONENTIAL,
                        WorkerConstants.BACKOFF_INITIAL_SECONDS,
                        TimeUnit.SECONDS,
                    ).build()

            // Use beginUniqueWork to chain under the same unique work name
            WorkManager
                .getInstance(applicationContext)
                .beginUniqueWork(
                    "${WorkerConstants.WORK_NAME_TMDB_ENRICHMENT}_batch",
                    ExistingWorkPolicy.REPLACE,
                    request,
                ).enqueue()

            UnifiedLog.d(TAG) {
                "Enqueued TmdbEnrichmentBatchWorker: scope=$scope, batchSize=${input.effectiveBatchSize}"
            }
        }
    }
