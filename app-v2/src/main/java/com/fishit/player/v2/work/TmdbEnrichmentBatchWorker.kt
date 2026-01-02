package com.fishit.player.v2.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.fishit.player.core.metadata.TmdbMetadataResolver
import com.fishit.player.core.metadata.tmdb.TmdbConfigProvider
import com.fishit.player.core.model.CanonicalMediaId
import com.fishit.player.core.model.ExternalIds
import com.fishit.player.core.model.MediaKind
import com.fishit.player.core.model.MediaType
import com.fishit.player.core.model.NormalizedMediaMetadata
import com.fishit.player.core.model.TmdbMediaType
import com.fishit.player.core.model.TmdbRef
import com.fishit.player.core.model.TmdbResolvedBy
import com.fishit.player.core.model.repository.CanonicalMediaRepository
import com.fishit.player.core.model.repository.CanonicalMediaWithSources
import com.fishit.player.infra.logging.UnifiedLog
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

/**
 * TMDB Enrichment Batch Worker.
 *
 * Processes a bounded batch of items for TMDB enrichment.
 * Uses TmdbMetadataResolver exclusively (W-4).
 *
 * Contract: CATALOG_SYNC_WORKERS_CONTRACT_V2
 * - W-4: TMDB API access MUST exist only via TmdbMetadataResolver (MANDATORY)
 * - W-16: Runtime Guards (MANDATORY)
 * - W-17: FireTV Safety - batch size clamping (MANDATORY)
 * - W-22: TMDB Scope Priority (MANDATORY)
 *
 * TMDB_ENRICHMENT_CONTRACT.md:
 * - T-15: Resolve-state schema tracking
 * - T-17: Repository query APIs for batching
 *
 * Scopes:
 * - DETAILS_BY_ID: Items with TmdbRef, fetch details + SSOT images
 * - RESOLVE_MISSING_IDS: Items without TmdbRef, search + resolve
 *
 * @see TmdbEnrichmentOrchestratorWorker for scope selection
 * @see TmdbEnrichmentContinuationWorker for continuation scheduling
 */
@HiltWorker
class TmdbEnrichmentBatchWorker
    @AssistedInject
    constructor(
        @Assisted context: Context,
        @Assisted workerParams: WorkerParameters,
        private val tmdbConfigProvider: TmdbConfigProvider,
        private val tmdbMetadataResolver: TmdbMetadataResolver,
        private val canonicalMediaRepository: CanonicalMediaRepository,
    ) : CoroutineWorker(context, workerParams) {
        companion object {
            private const val TAG = "TmdbEnrichmentBatchWorker"
        }

        override suspend fun doWork(): Result {
            val input = TmdbWorkerInputData.from(inputData)
            val startTimeMs = System.currentTimeMillis()
            val batchSize = input.effectiveBatchSize

            UnifiedLog.i(TAG) {
                "START run_id=${input.runId} scope=${input.tmdbScope} batch_size=$batchSize"
            }

            // W-16: Check runtime guards
            val guardReason = RuntimeGuards.checkGuards(applicationContext)
            if (guardReason != null) {
                UnifiedLog.w(TAG) { "GUARD_DEFER reason=$guardReason" }
                return Result.retry()
            }

            // W-20: Check if TMDB is enabled
            val tmdbConfig = tmdbConfigProvider.getConfig()
            if (!tmdbConfig.isEnabled) {
                UnifiedLog.w(TAG) { "FAILURE reason=TMDB_API_KEY_MISSING" }
                return Result.failure(
                    WorkerOutputData.failure(WorkerConstants.FAILURE_TMDB_API_KEY_MISSING),
                )
            }

            // Process batch based on scope
            val result =
                when (input.tmdbScope) {
                    WorkerConstants.TMDB_SCOPE_DETAILS_BY_ID -> {
                        processDetailsByIdBatch(batchSize, input.maxRuntimeMs, startTimeMs)
                    }
                    WorkerConstants.TMDB_SCOPE_RESOLVE_MISSING_IDS -> {
                        processMissingIdsBatch(batchSize, input.maxRuntimeMs, startTimeMs)
                    }
                    else -> {
                        UnifiedLog.e(TAG) { "Unknown scope: ${input.tmdbScope}" }
                        TmdbEnrichmentResult.PermanentFailure("Unknown scope: ${input.tmdbScope}")
                    }
                }

            val durationMs = System.currentTimeMillis() - startTimeMs

            return when (result) {
                is TmdbEnrichmentResult.Success -> {
                    UnifiedLog.i(TAG) {
                        "SUCCESS duration_ms=$durationMs processed=${result.itemsProcessed} " +
                            "resolved=${result.itemsResolved} failed=${result.itemsFailed} " +
                            "hasMore=${result.hasMore}"
                    }

                    // Schedule continuation if more items need processing
                    if (result.hasMore) {
                        scheduleContinuation(input, result.nextCursor)
                    }

                    Result.success(
                        buildOutputData(
                            itemsPersisted = result.itemsResolved.toLong(),
                            durationMs = durationMs,
                            checkpointCursor = result.nextCursor,
                        ),
                    )
                }

                is TmdbEnrichmentResult.RetryableFailure -> {
                    UnifiedLog.w(TAG) {
                        "RETRY reason=${result.reason} processed=${result.itemsProcessedBeforeFailure}"
                    }
                    Result.retry()
                }

                is TmdbEnrichmentResult.PermanentFailure -> {
                    UnifiedLog.e(TAG) { "FAILURE reason=${result.reason}" }
                    Result.failure(WorkerOutputData.failure(result.reason))
                }

                TmdbEnrichmentResult.NoCandidates -> {
                    UnifiedLog.i(TAG) { "SUCCESS duration_ms=$durationMs (no candidates)" }
                    Result.success(
                        buildOutputData(itemsPersisted = 0, durationMs = durationMs, checkpointCursor = null),
                    )
                }

                TmdbEnrichmentResult.Disabled -> {
                    UnifiedLog.i(TAG) { "SUCCESS duration_ms=$durationMs (TMDB disabled)" }
                    Result.success(
                        buildOutputData(itemsPersisted = 0, durationMs = durationMs, checkpointCursor = null),
                    )
                }
            }
        }

        /**
         * Process DETAILS_BY_ID batch: items with TmdbRef but missing SSOT data.
         */
        private suspend fun processDetailsByIdBatch(
            batchSize: Int,
            maxRuntimeMs: Long,
            startTimeMs: Long,
        ): TmdbEnrichmentResult {
            // Get candidates
            val candidates = canonicalMediaRepository.findCandidatesDetailsByIdMissingSsot(limit = batchSize)
            if (candidates.isEmpty()) {
                return TmdbEnrichmentResult.NoCandidates
            }

            var processed = 0
            var resolved = 0
            var failed = 0

            for (canonicalId in candidates) {
                // Check runtime budget
                val elapsedMs = System.currentTimeMillis() - startTimeMs
                if (elapsedMs > maxRuntimeMs) {
                    UnifiedLog.d(TAG) {
                        "CHECKPOINT_SAVED runtime_budget_exceeded elapsed_ms=$elapsedMs processed=$processed"
                    }
                    break
                }

                try {
                    val success = enrichWithDetails(canonicalId)
                    if (success) resolved++ else failed++
                } catch (e: Exception) {
                    UnifiedLog.w(TAG) { "Item failed: ${canonicalId.key} - ${e.message}" }
                    failed++

                    // If it's a network error, return retryable
                    if (isRetryableError(e)) {
                        return TmdbEnrichmentResult.RetryableFailure(
                            reason = "Network error: ${e.message}",
                            itemsProcessedBeforeFailure = processed,
                        )
                    }
                }
                processed++
            }

            // Check if more candidates exist
            val hasMore =
                canonicalMediaRepository
                    .findCandidatesDetailsByIdMissingSsot(limit = 1)
                    .isNotEmpty()

            return TmdbEnrichmentResult.Success(
                itemsProcessed = processed,
                itemsResolved = resolved,
                itemsFailed = failed,
                hasMore = hasMore,
                nextCursor = null, // DETAILS_BY_ID doesn't need cursor
            )
        }

        /**
         * Process RESOLVE_MISSING_IDS batch: items without TmdbRef eligible for search.
         */
        private suspend fun processMissingIdsBatch(
            batchSize: Int,
            maxRuntimeMs: Long,
            startTimeMs: Long,
        ): TmdbEnrichmentResult {
            val now = System.currentTimeMillis()

            // Get candidates respecting cooldown
            val candidates =
                canonicalMediaRepository
                    .findCandidatesMissingTmdbRefEligible(limit = batchSize, now = now)
            if (candidates.isEmpty()) {
                return TmdbEnrichmentResult.NoCandidates
            }

            var processed = 0
            var resolved = 0
            var failed = 0

            for (canonicalId in candidates) {
                // Check runtime budget
                val elapsedMs = System.currentTimeMillis() - startTimeMs
                if (elapsedMs > maxRuntimeMs) {
                    UnifiedLog.d(TAG) {
                        "CHECKPOINT_SAVED runtime_budget_exceeded elapsed_ms=$elapsedMs processed=$processed"
                    }
                    break
                }

                try {
                    val success = resolveViaSearch(canonicalId)
                    if (success) resolved++ else failed++
                } catch (e: Exception) {
                    UnifiedLog.w(TAG) { "Item failed: ${canonicalId.key} - ${e.message}" }
                    failed++

                    if (isRetryableError(e)) {
                        return TmdbEnrichmentResult.RetryableFailure(
                            reason = "Network error: ${e.message}",
                            itemsProcessedBeforeFailure = processed,
                        )
                    }
                }
                processed++
            }

            // Check if more candidates exist
            val hasMore =
                canonicalMediaRepository
                    .findCandidatesMissingTmdbRefEligible(limit = 1, now = System.currentTimeMillis())
                    .isNotEmpty()

            return TmdbEnrichmentResult.Success(
                itemsProcessed = processed,
                itemsResolved = resolved,
                itemsFailed = failed,
                hasMore = hasMore,
                nextCursor = null, // Cursor-based pagination not needed for eligibility-based queries
            )
        }

        /**
         * Enrich a single item that already has TmdbRef with full details.
         *
         * Path A: Details-by-ID - fetch full metadata from TMDB using known ID.
         *
         * @return true if successfully enriched
         */
        private suspend fun enrichWithDetails(canonicalId: CanonicalMediaId): Boolean {
            val media =
                canonicalMediaRepository.findByCanonicalId(canonicalId)
                    ?: return false

            val tmdbId = media.tmdbId ?: return false

            // Convert to NormalizedMediaMetadata for enrichment
            val normalized = media.toNormalizedMetadata()

            // Call real TMDB API via resolver
            val enriched = tmdbMetadataResolver.enrich(normalized)

            // Check if enrichment added any new data (compare images)
            val wasEnriched = enriched.poster != null || enriched.backdrop != null

            if (wasEnriched) {
                // Save enriched metadata back to repository
                canonicalMediaRepository.updateTmdbEnriched(
                    canonicalId = canonicalId,
                    enriched = enriched,
                    resolvedBy = TmdbResolvedBy.DETAILS_BY_ID,
                    resolvedAt = System.currentTimeMillis(),
                )
                UnifiedLog.d(TAG) { "PROGRESS item=${canonicalId.key} action=DETAILS_BY_ID status=ENRICHED" }
            } else {
                // Mark as applied even if no new data (already complete)
                canonicalMediaRepository.markTmdbDetailsApplied(
                    canonicalId = canonicalId,
                    tmdbId = tmdbId,
                    resolvedBy = TmdbResolvedBy.DETAILS_BY_ID,
                    resolvedAt = System.currentTimeMillis(),
                )
                UnifiedLog.d(TAG) { "PROGRESS item=${canonicalId.key} action=DETAILS_BY_ID status=NO_NEW_DATA" }
            }

            return true
        }

        /**
         * Resolve a single item without TmdbRef via TMDB search.
         *
         * Path B: Search + Score - search TMDB and score results.
         *
         * @return true if successfully resolved
         */
        private suspend fun resolveViaSearch(canonicalId: CanonicalMediaId): Boolean {
            val media =
                canonicalMediaRepository.findByCanonicalId(canonicalId)
                    ?: return false

            val now = System.currentTimeMillis()
            val cooldown = WorkerConstants.TMDB_COOLDOWN_MS
            val nextEligible = now + cooldown

            // Convert to NormalizedMediaMetadata for search
            val normalized = media.toNormalizedMetadata()

            // Call real TMDB API via resolver - this does search + scoring
            val enriched = tmdbMetadataResolver.enrich(normalized)

            // Check if resolver found and assigned a TmdbRef
            val resolvedTmdbRef = enriched.externalIds.tmdb

            return if (resolvedTmdbRef != null) {
                // Successfully resolved - save enriched metadata
                canonicalMediaRepository.updateTmdbEnriched(
                    canonicalId = canonicalId,
                    enriched = enriched,
                    resolvedBy = TmdbResolvedBy.SEARCH_MATCH,
                    resolvedAt = now,
                )
                UnifiedLog.d(TAG) { "PROGRESS item=${canonicalId.key} action=SEARCH_MATCH tmdbId=${resolvedTmdbRef.id}" }
                true
            } else {
                // No match found - mark as failed with cooldown
                canonicalMediaRepository.markTmdbResolveAttemptFailed(
                    canonicalId = canonicalId,
                    reason = "No confident TMDB match found",
                    attemptAt = now,
                    nextEligibleAt = nextEligible,
                )
                UnifiedLog.d(TAG) { "PROGRESS item=${canonicalId.key} action=SEARCH_MATCH status=NO_MATCH" }
                false
            }
        }

        /**
         * Convert CanonicalMediaWithSources to NormalizedMediaMetadata for enrichment.
         *
         * NormalizedMediaMetadata only contains fields needed for TMDB resolution:
         * - canonicalTitle, mediaType, year, season, episode
         * - tmdb (TmdbRef), externalIds
         * - poster, backdrop, thumbnail images
         */
        private fun CanonicalMediaWithSources.toNormalizedMetadata(): NormalizedMediaMetadata {
            // Determine media type from kind and season/episode
            val mediaType =
                when {
                    season != null && episode != null -> MediaType.SERIES_EPISODE
                    canonicalId.kind == MediaKind.EPISODE -> MediaType.SERIES_EPISODE
                    else -> MediaType.MOVIE
                }

            // Determine TMDB media type
            val tmdbMediaType =
                when (mediaType) {
                    MediaType.MOVIE -> TmdbMediaType.MOVIE
                    MediaType.SERIES, MediaType.SERIES_EPISODE -> TmdbMediaType.TV
                    else -> TmdbMediaType.MOVIE
                }

            return NormalizedMediaMetadata(
                canonicalTitle = canonicalTitle,
                mediaType = mediaType,
                year = year,
                season = season,
                episode = episode,
                tmdb = tmdbId?.let { TmdbRef(tmdbMediaType, it.value) },
                externalIds =
                    ExternalIds(
                        tmdb = tmdbId?.let { TmdbRef(tmdbMediaType, it.value) },
                        imdbId = imdbId,
                        tvdbId = null,
                    ),
                poster = poster,
                backdrop = backdrop,
                thumbnail = thumbnail,
            )
        }

        /**
         * Schedule continuation worker if more items need processing.
         */
        private fun scheduleContinuation(
            input: TmdbWorkerInputData,
            cursor: String?,
        ) {
            val continuationInputData =
                TmdbWorkerInputData.buildInputData(
                    runId = input.runId,
                    tmdbScope = input.tmdbScope,
                    forceRefresh = input.forceRefresh,
                    batchSizeHint = input.batchSizeHint,
                    batchCursor = cursor,
                    deviceClass = input.deviceClass,
                    maxRuntimeMs = input.maxRuntimeMs,
                )

            val request =
                OneTimeWorkRequestBuilder<TmdbEnrichmentContinuationWorker>()
                    .setInputData(continuationInputData)
                    .addTag(WorkerConstants.TAG_CATALOG_SYNC)
                    .addTag(WorkerConstants.TAG_SOURCE_TMDB)
                    .addTag(WorkerConstants.TAG_WORKER_TMDB_CONTINUATION)
                    .setBackoffCriteria(
                        BackoffPolicy.EXPONENTIAL,
                        WorkerConstants.BACKOFF_INITIAL_SECONDS,
                        TimeUnit.SECONDS,
                    )
                    // Small initial delay to avoid hammering API
                    .setInitialDelay(5, TimeUnit.SECONDS)
                    .build()

            WorkManager
                .getInstance(applicationContext)
                .beginUniqueWork(
                    "${WorkerConstants.WORK_NAME_TMDB_ENRICHMENT}_continuation",
                    ExistingWorkPolicy.REPLACE,
                    request,
                ).enqueue()

            UnifiedLog.d(TAG) { "Scheduled continuation worker for scope=${input.tmdbScope}" }
        }

        /**
         * Check if an exception represents a retryable error (network, rate limit).
         */
        private fun isRetryableError(e: Exception): Boolean =
            e is java.net.SocketTimeoutException ||
                e is java.net.UnknownHostException ||
                e is java.io.IOException ||
                e.message?.contains("429", ignoreCase = true) == true ||
                e.message?.contains("rate limit", ignoreCase = true) == true

        private fun buildOutputData(
            itemsPersisted: Long,
            durationMs: Long,
            checkpointCursor: String?,
        ): Data =
            Data
                .Builder()
                .putLong(WorkerConstants.KEY_ITEMS_PERSISTED, itemsPersisted)
                .putLong(WorkerConstants.KEY_DURATION_MS, durationMs)
                .apply {
                    if (checkpointCursor != null) {
                        putString(WorkerConstants.KEY_CHECKPOINT_CURSOR, checkpointCursor)
                    }
                }.build()
    }
