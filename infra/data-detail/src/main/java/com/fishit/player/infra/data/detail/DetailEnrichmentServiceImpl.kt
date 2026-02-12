package com.fishit.player.infra.data.detail

import com.fishit.player.core.detail.domain.DetailEnrichmentService
import com.fishit.player.core.detail.domain.UnifiedDetailLoader
import com.fishit.player.core.model.CanonicalMediaId
import com.fishit.player.core.model.ExternalIds
import com.fishit.player.core.model.ImageRef
import com.fishit.player.core.model.MediaType
import com.fishit.player.core.model.NormalizedMediaMetadata
import com.fishit.player.core.model.SourceType
import com.fishit.player.core.model.TmdbMediaType
import com.fishit.player.core.model.TmdbRef
import com.fishit.player.core.model.ids.PipelineItemId
import com.fishit.player.core.model.ids.XtreamIdCodec
import com.fishit.player.core.model.repository.CanonicalMediaRepository
import com.fishit.player.core.model.repository.CanonicalMediaWithSources
import com.fishit.player.infra.logging.UnifiedLog
import com.fishit.player.infra.priority.ApiPriorityDispatcher
import com.fishit.player.infra.transport.xtream.XtreamApiClient
import com.fishit.player.pipeline.xtream.mapper.toRawMediaMetadata
import com.fishit.player.pipeline.xtream.model.XtreamVodItem
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of [DetailEnrichmentService].
 *
 * **PLATIN Phase 2:** Now delegates to [UnifiedDetailLoader] for Xtream enrichment.
 *
 * Enriches canonical media with additional metadata from:
 * - ✅ Xtream API via [UnifiedDetailLoader] (PLATIN - ONE API call)
 * - TMDB resolver (future) for media with TMDB IDs
 *
 * **Priority System:**
 * - [enrichImmediate] uses HIGH_USER_ACTION priority (pauses background sync)
 * - [ensureEnriched] uses CRITICAL_PLAYBACK priority (highest, blocks other ops)
 * - [enrichIfNeeded] runs at normal priority (background)
 *
 * **Thread Safety:** Uses per-canonicalId mutexes to prevent concurrent enrichment.
 */
@Singleton
class DetailEnrichmentServiceImpl
    @Inject
    constructor(
        private val xtreamApiClient: XtreamApiClient,
        private val canonicalMediaRepository: CanonicalMediaRepository,
        private val priorityDispatcher: ApiPriorityDispatcher,
        private val unifiedDetailLoader: UnifiedDetailLoader,
    ) : DetailEnrichmentService {
        companion object {
            private const val TAG = "DetailEnrichment"
        }

        private val enrichmentLocks = mutableMapOf<String, Mutex>()
        private val locksLock = Mutex()

        private suspend fun getMutexForCanonicalId(canonicalId: String): Mutex =
            locksLock.withLock {
                enrichmentLocks.getOrPut(canonicalId) { Mutex() }
            }

        override suspend fun ensureEnriched(
            canonicalId: CanonicalMediaId,
            sourceKey: PipelineItemId?,
            requiredHints: List<String>,
            timeoutMs: Long,
        ): CanonicalMediaWithSources? {
            val startMs = System.currentTimeMillis()

            val media = canonicalMediaRepository.findByCanonicalId(canonicalId)
            if (media == null) {
                UnifiedLog.w(TAG) {
                    "ensureEnriched: media not found canonicalId=${canonicalId.key.value}"
                }
                return null
            }

            // Fast path: check if hints are already present
            val source = sourceKey?.let { key -> media.sources.find { it.sourceId == key } }
            if (source != null && requiredHints.isNotEmpty()) {
                val missingHints = requiredHints.filter { source.playbackHints[it].isNullOrBlank() }
                if (missingHints.isEmpty()) {
                    UnifiedLog.d(TAG) {
                        "ensureEnriched: fast path (hints present) canonicalId=${canonicalId.key.value}"
                    }
                    return media
                }
                UnifiedLog.d(TAG) {
                    "ensureEnriched: missing hints=$missingHints canonicalId=${canonicalId.key.value} sourceKey=${sourceKey.value}"
                }
            }

            // CRITICAL priority - pauses all other operations
            val enriched = priorityDispatcher.withCriticalPriority(
                tag = "EnsureEnriched:${canonicalId.key.value}",
                timeoutMs = timeoutMs,
            ) {
                val mutex = getMutexForCanonicalId(canonicalId.key.value)
                mutex.withLock {
                    val currentMedia = canonicalMediaRepository.findByCanonicalId(canonicalId)
                        ?: return@withCriticalPriority null

                    val currentSource =
                        sourceKey?.let { key -> currentMedia.sources.find { it.sourceId == key } }
                    if (currentSource != null && requiredHints.isNotEmpty()) {
                        val stillMissing = requiredHints.filter {
                            currentSource.playbackHints[it].isNullOrBlank()
                        }
                        if (stillMissing.isEmpty()) {
                            UnifiedLog.d(TAG) {
                                "ensureEnriched: post-lock fast path canonicalId=${canonicalId.key.value}"
                            }
                            return@withLock currentMedia
                        }
                    }

                    enrichIfNeededInternal(currentMedia)
                }
            }

            val durationMs = System.currentTimeMillis() - startMs
            UnifiedLog.d(TAG) {
                "ensureEnriched: completed in ${durationMs}ms canonicalId=${canonicalId.key.value} success=${enriched != null}"
            }

            return enriched ?: canonicalMediaRepository.findByCanonicalId(canonicalId)
        }

        override suspend fun enrichImmediate(media: CanonicalMediaWithSources): CanonicalMediaWithSources {
            val startMs = System.currentTimeMillis()

            // Fast path: already enriched
            if (!media.plot.isNullOrBlank()) {
                UnifiedLog.d(TAG) {
                    "enrichImmediate: skipped (already has plot) canonicalId=${media.canonicalId.key.value}"
                }
                return media
            }

            // HIGH priority - pauses background sync
            val result = priorityDispatcher.withHighPriority(
                tag = "DetailEnrich:${media.canonicalId.key.value}",
            ) {
                val mutex = getMutexForCanonicalId(media.canonicalId.key.value)
                mutex.withLock {
                    // Re-check after acquiring lock
                    val currentMedia = canonicalMediaRepository.findByCanonicalId(media.canonicalId)
                        ?: return@withHighPriority media

                    if (!currentMedia.plot.isNullOrBlank()) {
                        return@withHighPriority currentMedia
                    }

                    enrichIfNeededInternal(currentMedia)
                }
            }

            val durationMs = System.currentTimeMillis() - startMs
            UnifiedLog.i(TAG) {
                "enrichImmediate: completed in ${durationMs}ms canonicalId=${media.canonicalId.key.value} " +
                    "hasPlot=${!result.plot.isNullOrBlank()}"
            }

            return result
        }

        override suspend fun enrichIfNeeded(media: CanonicalMediaWithSources): CanonicalMediaWithSources {
            // Normal background priority (no special handling)
            return enrichIfNeededInternal(media)
        }

        /**
         * Internal enrichment logic (no priority handling).
         * Called by all public methods after priority is established.
         */
        private suspend fun enrichIfNeededInternal(media: CanonicalMediaWithSources): CanonicalMediaWithSources {
            val startMs = System.currentTimeMillis()

            if (!media.plot.isNullOrBlank()) {
                UnifiedLog.d(TAG) {
                    "enrichIfNeededInternal: skipped (already has plot) canonicalId=${media.canonicalId.key.value}"
                }
                return media
            }

            val hasXtreamSource = media.sources.any { it.sourceType == SourceType.XTREAM }

            val result = when {
                hasXtreamSource -> enrichFromXtream(media)
                // TODO: TMDB enrichment when TmdbMetadataResolver supports resolveByTmdbId
                else -> {
                    UnifiedLog.d(TAG) {
                        "enrichIfNeededInternal: skipped (no enrichment path) canonicalId=${media.canonicalId.key.value} hasXtream=$hasXtreamSource"
                    }
                    media
                }
            }

            val durationMs = System.currentTimeMillis() - startMs
            if (result !== media) {
                UnifiedLog.i(TAG) {
                    "enrichIfNeededInternal: completed in ${durationMs}ms canonicalId=${media.canonicalId.key.value} " +
                        "hasPlot=${!result.plot.isNullOrBlank()} source=xtream"
                }
            }

            return result
        }

        // =========================================================================
        // PLATIN Phase 2: Xtream Enrichment via UnifiedDetailLoader
        // =========================================================================

        /**
         * 🏆 PLATIN: Delegate Xtream enrichment to [UnifiedDetailLoader].
         *
         * Instead of calling getSeriesInfo/getVodInfo directly (which caused partial saves),
         * we now delegate to UnifiedDetailLoader which:
         * - Makes ONE API call
         * - Saves EVERYTHING atomically (metadata + seasons + episodes for Series)
         * - Has deduplication built-in
         */
        private suspend fun enrichFromXtream(media: CanonicalMediaWithSources): CanonicalMediaWithSources {
            val xtreamSource =
                media.sources.firstOrNull { it.sourceType == SourceType.XTREAM } ?: return media

            val sourceKey = xtreamSource.sourceId.value

            UnifiedLog.d(TAG) { "enrichFromXtream: delegating to UnifiedDetailLoader for sourceKey=$sourceKey (PLATIN)" }

            // Delegate to UnifiedDetailLoader - ONE API call, saves everything
            val bundle = unifiedDetailLoader.loadDetailImmediate(media)

            // If bundle is null, return original media
            if (bundle == null) {
                UnifiedLog.w(TAG) { "enrichFromXtream: UnifiedDetailLoader returned null for canonicalId=${media.canonicalId.key.value}" }
                return media
            }

            // Fetch the updated media from repository (UnifiedDetailLoader already persisted it)
            val updatedMedia = canonicalMediaRepository.findByCanonicalId(media.canonicalId)
            if (updatedMedia != null) {
                UnifiedLog.d(TAG) {
                    "enrichFromXtream: success via UnifiedDetailLoader, hasPlot=${!updatedMedia.plot.isNullOrBlank()}"
                }
                return updatedMedia
            }

            UnifiedLog.w(TAG) { "enrichFromXtream: could not reload media after enrichment" }
            return media
        }

        /**
         * Check if sourceKey represents a VOD item.
         *
         * Delegates to XtreamIdCodec SSOT for legacy format and
         * SourceKeyParser for NX format.
         */
        private fun isVodSourceKey(sourceId: String): Boolean {
            // Legacy format: xtream:vod:{id}
            if (XtreamIdCodec.isVod(sourceId)) return true
            // NX format: src:xtream:{accountKey}:vod:{id}
            val parsed = com.fishit.player.infra.data.nx.mapper.SourceKeyParser.parse(sourceId)
            return parsed?.itemKind == "vod"
        }

        /**
         * Check if sourceKey represents a Series item.
         *
         * Delegates to XtreamIdCodec SSOT for legacy format and
         * SourceKeyParser for NX format.
         */
        private fun isSeriesSourceKey(sourceId: String): Boolean {
            // Legacy format: xtream:series:{id}
            if (XtreamIdCodec.isSeries(sourceId)) return true
            // NX format: src:xtream:{accountKey}:series:{id}
            val parsed = com.fishit.player.infra.data.nx.mapper.SourceKeyParser.parse(sourceId)
            return parsed?.itemKind == "series"
        }
    }
