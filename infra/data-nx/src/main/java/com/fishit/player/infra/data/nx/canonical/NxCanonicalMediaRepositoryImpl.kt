package com.fishit.player.infra.data.nx.canonical

import com.fishit.player.core.model.CanonicalMediaId
import com.fishit.player.core.model.MediaFormat
import com.fishit.player.core.model.MediaKind
import com.fishit.player.core.model.MediaQuality
import com.fishit.player.core.model.MediaSourceRef
import com.fishit.player.core.model.MediaType
import com.fishit.player.core.model.NormalizedMediaMetadata
import com.fishit.player.core.model.PlaybackHintKeys
import com.fishit.player.core.model.SourceType
import com.fishit.player.core.model.TmdbResolvedBy
import com.fishit.player.core.model.ids.CanonicalId
import com.fishit.player.core.model.ids.PipelineItemId
import com.fishit.player.core.model.ids.TmdbId
import com.fishit.player.core.model.repository.CanonicalMediaRepository
import com.fishit.player.core.model.repository.CanonicalMediaStats
import com.fishit.player.core.model.repository.CanonicalMediaWithResume
import com.fishit.player.core.model.repository.CanonicalMediaWithSources
import com.fishit.player.core.model.repository.CanonicalResumeInfo
import com.fishit.player.core.model.repository.NxWorkRepository
import com.fishit.player.core.model.repository.toEnrichment
import com.fishit.player.core.model.util.ResolutionLabel
import com.fishit.player.core.model.util.SourcePriority
import com.fishit.player.core.persistence.obx.NX_Work
import com.fishit.player.core.persistence.obx.NX_WorkSourceRef
import com.fishit.player.core.persistence.obx.NX_WorkSourceRef_
import com.fishit.player.core.persistence.obx.NX_WorkUserState
import com.fishit.player.core.persistence.obx.NX_WorkUserState_
import com.fishit.player.core.persistence.obx.NX_WorkVariant
import com.fishit.player.core.persistence.obx.NX_WorkVariant_
import com.fishit.player.core.persistence.obx.NX_Work_
import com.fishit.player.core.persistence.obx.NxKeyGenerator
import com.fishit.player.infra.data.nx.mapper.MediaTypeMapper
import com.fishit.player.infra.data.nx.mapper.SourceKeyParser
import com.fishit.player.infra.data.nx.mapper.SourceLabelBuilder
import com.fishit.player.infra.data.nx.mapper.base.PlaybackHintsDecoder
import com.fishit.player.infra.data.nx.writer.builder.WorkEntityBuilder
import io.objectbox.Box
import io.objectbox.BoxStore
import io.objectbox.query.QueryCondition
import io.objectbox.query.QueryBuilder.StringOrder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * NX-based implementation of CanonicalMediaRepository.
 *
 * Architecture (v2 - INV-6 compliant):
 * - Uses NX_* entities as SSOT for all canonical media
 * - Replaces legacy ObxCanonicalMediaRepository
 * - All features consume this implementation via CanonicalMediaRepository interface
 *
 * Entity Mapping:
 * - NX_Work -> CanonicalMediaWithSources
 * - NX_WorkSourceRef -> MediaSourceRef
 * - NX_WorkVariant -> playbackHints in MediaSourceRef
 * - NX_WorkUserState -> CanonicalResumeInfo
 */
@Singleton
class NxCanonicalMediaRepositoryImpl @Inject constructor(
    boxStore: BoxStore,
    private val workRepository: NxWorkRepository,
    private val workEntityBuilder: WorkEntityBuilder,
) : CanonicalMediaRepository {

    private val workBox: Box<NX_Work> = boxStore.boxFor(NX_Work::class.java)
    private val sourceRefBox: Box<NX_WorkSourceRef> = boxStore.boxFor(NX_WorkSourceRef::class.java)
    private val variantBox: Box<NX_WorkVariant> = boxStore.boxFor(NX_WorkVariant::class.java)
    private val userStateBox: Box<NX_WorkUserState> = boxStore.boxFor(NX_WorkUserState::class.java)

    // ========== Core CRUD Operations ==========

    /**
     * Upsert canonical media using the SSOT write path.
     *
     * **NX_CONSOLIDATION_PLAN Phase 2 — Single Write Path**
     *
     * Uses [WorkEntityBuilder] for correct field mapping (workType, recognitionState, etc.)
     * and [NxWorkRepository.enrichIfAbsent] for write-protected enrichment.
     *
     * Eliminates the previous dual-write path that bypassed the builder and caused:
     * - workType stored as "SERIES_EPISODE" instead of "EPISODE"
     * - trailer, releaseDate, isAdult fields not being set
     * - recognitionState not being set correctly
     */
    override suspend fun upsertCanonicalMedia(
        normalized: NormalizedMediaMetadata,
    ): CanonicalMediaId = withContext(Dispatchers.IO) {
        val workKey = buildWorkKey(normalized)
        val now = System.currentTimeMillis()
        val enrichment = workEntityBuilder.build(normalized, workKey, now)
        val enrichmentData = enrichment.toEnrichment()

        // --- Dual-Lookup Strategy ---
        // Step 1: Try enrichment by heuristic workKey (primary path)
        val enrichedByKey = workRepository.enrichIfAbsent(workKey, enrichmentData)
        if (enrichedByKey != null) {
            return@withContext CanonicalMediaId(
                kind = mediaTypeToKind(normalized.mediaType),
                key = CanonicalId(enrichedByKey.workKey),
            )
        }

        // Step 2: tmdbId fallback — find existing entity that was created with
        //         a different slug (title variation) or old tmdb-format workKey.
        //         Prevents duplicates across key format migration and title aliases.
        //         Uses same tmdbId source as WorkEntityBuilder to avoid lookup misses.
        val tmdbId = (normalized.tmdb ?: normalized.externalIds.tmdb)?.id?.toString()
        if (tmdbId != null) {
            val existingByTmdb = workBox.query(
                NX_Work_.tmdbId.equal(tmdbId),
            ).build().findFirst()

            if (existingByTmdb != null) {
                // Entity exists with different workKey → enrich via its actual key
                val enrichedByTmdb = workRepository.enrichIfAbsent(
                    existingByTmdb.workKey, enrichmentData,
                )
                val actualKey = enrichedByTmdb?.workKey ?: existingByTmdb.workKey
                return@withContext CanonicalMediaId(
                    kind = mediaTypeToKind(normalized.mediaType),
                    key = CanonicalId(actualKey),
                )
            }
        }

        // Step 3: No existing entity found → create new
        val created = workRepository.upsert(enrichment)

        CanonicalMediaId(
            kind = mediaTypeToKind(normalized.mediaType),
            key = CanonicalId(created.workKey),
        )
    }

    override suspend fun addOrUpdateSourceRef(
        canonicalId: CanonicalMediaId,
        source: MediaSourceRef,
    ) = withContext(Dispatchers.IO) {
        val workKey = canonicalId.key.value
        val work = workBox.query(NX_Work_.workKey.equal(workKey))
            .build()
            .findFirst() ?: return@withContext

        // Check if sourceRef already exists
        val existing = sourceRefBox.query(
            NX_WorkSourceRef_.sourceKey.equal(source.sourceId.value, StringOrder.CASE_SENSITIVE)
        ).build().findFirst()

        val sourceRef = existing ?: NX_WorkSourceRef()
        sourceRef.apply {
            this.sourceKey = source.sourceId.value
            this.sourceType = source.sourceType.name.lowercase()  // ← FIX: lowercase!
            this.accountKey = "" // Not available in new MediaSourceRef
            this.sourceId = extractSourceSpecificId(source.sourceId.value)
            // File metadata from playbackHints if available
            this.fileSizeBytes = source.sizeBytes
            this.lastSeenAt = System.currentTimeMillis()
            if (existing == null) {
                this.discoveredAt = System.currentTimeMillis()
            }
        }
        sourceRef.work.target = work
        sourceRef.workKey = work.workKey

        sourceRefBox.put(sourceRef)

        // Also create/update variant if playbackHints present
        if (source.playbackHints.isNotEmpty()) {
            upsertVariant(work, sourceRef, source)
        }
    }

    override suspend fun removeSourceRef(sourceId: PipelineItemId) = withContext(Dispatchers.IO) {
        val sourceRef = sourceRefBox.query(
            NX_WorkSourceRef_.sourceKey.equal(sourceId.value, StringOrder.CASE_SENSITIVE)
        ).build().findFirst() ?: return@withContext

        // Remove associated variants
        variantBox.query(NX_WorkVariant_.sourceKey.equal(sourceId.value, StringOrder.CASE_SENSITIVE))
            .build()
            .remove()

        sourceRefBox.remove(sourceRef)
    }

    // ========== Query Operations ==========

    override suspend fun findByCanonicalId(
        canonicalId: CanonicalMediaId,
    ): CanonicalMediaWithSources? = withContext(Dispatchers.IO) {
        val work = workBox.query(NX_Work_.workKey.equal(canonicalId.key.value))
            .build()
            .findFirst() ?: return@withContext null

        mapToCanonicalMediaWithSources(work)
    }

    override suspend fun findByExternalId(
        tmdbId: TmdbId?,
        imdbId: String?,
        tvdbId: String?,
    ): CanonicalMediaWithSources? = withContext(Dispatchers.IO) {
        val work = when {
            tmdbId != null -> workBox.query(NX_Work_.tmdbId.equal(tmdbId.value.toString()))
                .build().findFirst()
            imdbId != null -> workBox.query(NX_Work_.imdbId.equal(imdbId))
                .build().findFirst()
            tvdbId != null -> workBox.query(NX_Work_.tvdbId.equal(tvdbId))
                .build().findFirst()
            else -> null
        } ?: return@withContext null

        mapToCanonicalMediaWithSources(work)
    }

    override suspend fun findByTitleAndYear(
        canonicalTitle: String,
        year: Int?,
        kind: MediaKind?,
    ): List<CanonicalMediaWithSources> = withContext(Dispatchers.IO) {
        // Build initial condition
        var condition: QueryCondition<NX_Work> = NX_Work_.canonicalTitleLower.equal(canonicalTitle.lowercase())

        // Chain additional conditions
        if (year != null) {
            condition = condition.and(NX_Work_.year.equal(year.toLong()))
        }
        if (kind != null) {
            condition = condition.and(NX_Work_.workType.equal(kindToWorkType(kind)))
        }

        workBox.query(condition).build().find().map { mapToCanonicalMediaWithSources(it) }
    }

    override suspend fun findBySourceId(
        sourceId: PipelineItemId,
    ): CanonicalMediaWithSources? = withContext(Dispatchers.IO) {
        val sourceRef = sourceRefBox.query(
            NX_WorkSourceRef_.sourceKey.equal(sourceId.value)
        ).build().findFirst() ?: return@withContext null

        val work = sourceRef.work.target ?: return@withContext null
        mapToCanonicalMediaWithSources(work)
    }

    override suspend fun getSourcesForMedia(
        canonicalId: CanonicalMediaId,
    ): List<MediaSourceRef> = withContext(Dispatchers.IO) {
        val work = workBox.query(NX_Work_.workKey.equal(canonicalId.key.value))
            .build()
            .findFirst() ?: return@withContext emptyList()

        getSourceRefsForWork(work.workKey).map { mapToMediaSourceRef(it) }
            .sortedByDescending { it.priority }
    }

    override suspend fun search(
        query: String,
        kind: MediaKind?,
        limit: Int,
    ): List<CanonicalMediaWithSources> = withContext(Dispatchers.IO) {
        // Build initial condition
        var condition: QueryCondition<NX_Work> = NX_Work_.canonicalTitleLower.contains(query.lowercase())

        // Chain additional condition if kind specified
        if (kind != null) {
            condition = condition.and(NX_Work_.workType.equal(kindToWorkType(kind)))
        }

        workBox.query(condition).build()
            .find(0, limit.toLong())
            .map { mapToCanonicalMediaWithSources(it) }
    }

    // ========== Cross-Pipeline Resume ==========

    override suspend fun getCanonicalResume(
        canonicalId: CanonicalMediaId,
        profileId: Long,
    ): CanonicalResumeInfo? = withContext(Dispatchers.IO) {
        val userState = userStateBox.query(
            NX_WorkUserState_.workKey.equal(canonicalId.key.value)
                .and(NX_WorkUserState_.profileId.equal(profileId))
        ).build().findFirst() ?: return@withContext null

        mapToCanonicalResumeInfo(userState, canonicalId.key)
    }

    override suspend fun setCanonicalResume(
        canonicalId: CanonicalMediaId,
        profileId: Long,
        positionMs: Long,
        durationMs: Long,
        sourceRef: MediaSourceRef,
    ) {
        withContext(Dispatchers.IO) {
            val existing = userStateBox.query(
                NX_WorkUserState_.workKey.equal(canonicalId.key.value)
                    .and(NX_WorkUserState_.profileId.equal(profileId))
            ).build().findFirst()

            val userState = existing ?: NX_WorkUserState()
            userState.apply {
                this.workKey = canonicalId.key.value
                this.profileId = profileId
                this.resumePositionMs = positionMs
                this.totalDurationMs = durationMs
                this.lastWatchedAt = System.currentTimeMillis()
                this.updatedAt = System.currentTimeMillis()
                if (existing == null) {
                    this.createdAt = System.currentTimeMillis()
                }
            }

            userStateBox.put(userState)
        }
    }

    override suspend fun markCompleted(
        canonicalId: CanonicalMediaId,
        profileId: Long,
    ) = withContext(Dispatchers.IO) {
        val userState = userStateBox.query(
            NX_WorkUserState_.workKey.equal(canonicalId.key.value)
                .and(NX_WorkUserState_.profileId.equal(profileId))
        ).build().findFirst() ?: return@withContext

        userState.isWatched = true
        userState.watchCount += 1
        userState.updatedAt = System.currentTimeMillis()

        userStateBox.put(userState)
    }

    override suspend fun clearCanonicalResume(
        canonicalId: CanonicalMediaId,
        profileId: Long,
    ) {
        withContext(Dispatchers.IO) {
            userStateBox.query(
                NX_WorkUserState_.workKey.equal(canonicalId.key.value)
                    .and(NX_WorkUserState_.profileId.equal(profileId))
            ).build().remove()
        }
    }

    override suspend fun getResumeList(
        profileId: Long,
        limit: Int,
    ): List<CanonicalMediaWithResume> = withContext(Dispatchers.IO) {
        val userStates = userStateBox.query(
            NX_WorkUserState_.profileId.equal(profileId)
                .and(NX_WorkUserState_.resumePositionMs.greater(0))
                .and(NX_WorkUserState_.isWatched.equal(false))
        ).orderDesc(NX_WorkUserState_.lastWatchedAt)
            .build()
            .find(0, limit.toLong())

        userStates.mapNotNull { userState ->
            val work = workBox.query(NX_Work_.workKey.equal(userState.workKey))
                .build()
                .findFirst() ?: return@mapNotNull null

            val media = mapToCanonicalMediaWithSources(work)
            val resume = mapToCanonicalResumeInfo(userState, CanonicalId(userState.workKey))

            CanonicalMediaWithResume(media, resume)
        }
    }

    // ========== TMDB Resolution Operations ==========

    override suspend fun findCandidatesDetailsByIdMissingSsot(
        limit: Int,
    ): List<CanonicalMediaId> = withContext(Dispatchers.IO) {
        // Find works with TMDB ID but missing SSOT data (poster)
        workBox.query()
            .notNull(NX_Work_.tmdbId)
            .and()
            .isNull(NX_Work_.poster)
            .build()
            .find(0, limit.toLong())
            .map {
                CanonicalMediaId(
                    kind = workTypeToKind(it.workType),
                    key = CanonicalId(it.workKey),
                )
            }
    }

    override suspend fun findCandidatesMissingTmdbRefEligible(
        limit: Int,
        now: Long,
    ): List<CanonicalMediaId> = withContext(Dispatchers.IO) {
        // Find works without TMDB ID
        workBox.query()
            .isNull(NX_Work_.tmdbId)
            .build()
            .find(0, limit.toLong())
            .map {
                CanonicalMediaId(
                    kind = workTypeToKind(it.workType),
                    key = CanonicalId(it.workKey),
                )
            }
    }

    override suspend fun markTmdbDetailsApplied(
        canonicalId: CanonicalMediaId,
        tmdbId: TmdbId,
        resolvedBy: TmdbResolvedBy,
        resolvedAt: Long,
    ) = withContext(Dispatchers.IO) {
        val work = workBox.query(NX_Work_.workKey.equal(canonicalId.key.value))
            .build()
            .findFirst() ?: return@withContext

        work.tmdbId = tmdbId.value.toString()
        work.updatedAt = resolvedAt

        workBox.put(work)
    }

    override suspend fun markTmdbResolveAttemptFailed(
        canonicalId: CanonicalMediaId,
        reason: String,
        attemptAt: Long,
        nextEligibleAt: Long,
    ) = withContext(Dispatchers.IO) {
        val work = workBox.query(NX_Work_.workKey.equal(canonicalId.key.value))
            .build()
            .findFirst() ?: return@withContext

        work.updatedAt = attemptAt
        // Note: NX_Work doesn't have resolve state fields yet
        // This would need schema extension for full TMDB resolution tracking

        workBox.put(work)
    }

    override suspend fun markTmdbResolved(
        canonicalId: CanonicalMediaId,
        tmdbId: TmdbId,
        resolvedBy: TmdbResolvedBy,
        resolvedAt: Long,
    ) = withContext(Dispatchers.IO) {
        val work = workBox.query(NX_Work_.workKey.equal(canonicalId.key.value))
            .build()
            .findFirst() ?: return@withContext

        work.tmdbId = tmdbId.value.toString()
        work.updatedAt = resolvedAt

        workBox.put(work)
    }

    override suspend fun updateTmdbEnriched(
        canonicalId: CanonicalMediaId,
        enriched: NormalizedMediaMetadata,
        resolvedBy: TmdbResolvedBy,
        resolvedAt: Long,
    ) = withContext(Dispatchers.IO) {
        val work = workBox.query(NX_Work_.workKey.equal(canonicalId.key.value))
            .build()
            .findFirst() ?: return@withContext

        // TMDB data is authoritative — always overwrite non-null fields.
        // AUTHORITY_WINS: poster, backdrop, thumbnail (TMDB images replace pipeline images)
        enriched.poster?.let { work.poster = it }
        enriched.backdrop?.let { work.backdrop = it }
        enriched.thumbnail?.let { work.thumbnail = it }
        // AUTHORITY_WINS: plot, rating, durationMs, genres, director, cast
        enriched.plot?.let { work.plot = it }
        enriched.rating?.let { work.rating = it }
        enriched.durationMs?.let { work.durationMs = it }
        enriched.genres?.let { work.genres = it }
        enriched.director?.let { work.director = it }
        enriched.cast?.let { work.cast = it }
        // ALWAYS_UPDATE: external IDs
        enriched.externalIds.effectiveTmdbId?.let { work.tmdbId = it.toString() }
        enriched.externalIds.imdbId?.let { work.imdbId = it }

        // Update authorityKey when tmdbId changes (cross-reference index)
        enriched.externalIds.effectiveTmdbId?.let { newTmdbId ->
            val workType = work.workType.lowercase()
            work.authorityKey = NxKeyGenerator.authorityKey("TMDB", workType, newTmdbId.toString())
        }

        work.updatedAt = resolvedAt
        workBox.put(work)
    }

    // ========== Maintenance Operations ==========

    override suspend fun pruneOrphans(): Int = withContext(Dispatchers.IO) {
        var removed = 0
        workBox.all.forEach { work ->
            val sourceRefs = getSourceRefsForWork(work.workKey)
            if (sourceRefs.isEmpty()) {
                workBox.remove(work)
                removed++
            }
        }
        removed
    }

    override suspend fun verifySourceAvailability(
        canonicalId: CanonicalMediaId,
    ): Int = withContext(Dispatchers.IO) {
        val work = workBox.query(NX_Work_.workKey.equal(canonicalId.key.value))
            .build()
            .findFirst() ?: return@withContext 0

        getSourceRefsForWork(work.workKey).size
    }

    override suspend fun getStats(): CanonicalMediaStats = withContext(Dispatchers.IO) {
        val allWorks = workBox.all
        val allSourceRefs = sourceRefBox.all

        val movieCount = allWorks.count { it.workType == "MOVIE" }.toLong()
        val episodeCount = allWorks.count { it.workType == "EPISODE" }.toLong()
        val withTmdbId = allWorks.count { it.tmdbId != null }.toLong()

        // Use denormalized workKey for grouping (avoids ToOne lazy loads)
        val sourceCountByWorkKey = allSourceRefs.groupBy { it.workKey }
        val workKeysWithSources = sourceCountByWorkKey.keys
        val withMultipleSources = sourceCountByWorkKey.count { it.value.size > 1 }.toLong()
        val orphanedCount = allWorks.count { work ->
            work.workKey !in workKeysWithSources
        }.toLong()

        val sourcesByType = allSourceRefs.groupBy { it.sourceType }
            .mapValues { it.value.size.toLong() }

        CanonicalMediaStats(
            totalCanonicalMedia = allWorks.size.toLong(),
            totalSourceRefs = allSourceRefs.size.toLong(),
            movieCount = movieCount,
            episodeCount = episodeCount,
            withTmdbId = withTmdbId,
            withMultipleSources = withMultipleSources,
            orphanedCount = orphanedCount,
            sourcesByType = sourcesByType,
        )
    }

    // ========== Private Helper Methods ==========

    /**
     * Build work key from normalized metadata.
     *
     * Delegates to NxKeyGenerator.workKey() — the single source of truth for
     * work key format: `{workType}:{authority}:{id}`
     *
     * Uses canonical MediaTypeMapper.toWorkType() for correct type mapping.
     */
    private fun buildWorkKey(normalized: NormalizedMediaMetadata): String {
        val workType = MediaTypeMapper.toWorkType(normalized.mediaType)
        return NxKeyGenerator.workKey(
            workType = workType,
            title = normalized.canonicalTitle,
            year = normalized.year,
            // tmdbId intentionally NOT passed — workKey is always heuristic.
            // tmdbId is stored separately on NX_Work and used via Dual-Lookup.
            season = normalized.season,
            episode = normalized.episode,
        )
    }

    /** INV-PERF: Indexed query via @Index workKey (replaces box.all full scan). */
    private fun getSourceRefsForWork(workKey: String): List<NX_WorkSourceRef> {
        return sourceRefBox.query(
            NX_WorkSourceRef_.workKey.equal(workKey, StringOrder.CASE_SENSITIVE),
        ).build().find()
    }

    private fun getVariantsForSource(sourceKey: String): List<NX_WorkVariant> {
        return variantBox.query(NX_WorkVariant_.sourceKey.equal(sourceKey, StringOrder.CASE_SENSITIVE))
            .build()
            .find()
    }

    private fun mapToCanonicalMediaWithSources(work: NX_Work): CanonicalMediaWithSources {
        val sourceRefs = getSourceRefsForWork(work.workKey)
        val sources = sourceRefs.map { mapToMediaSourceRef(it) }
            .sortedByDescending { it.priority }

        return CanonicalMediaWithSources(
            canonicalId = CanonicalMediaId(
                kind = workTypeToKind(work.workType),
                key = CanonicalId(work.workKey),
            ),
            canonicalTitle = work.canonicalTitle,
            mediaType = try {
                MediaType.valueOf(work.workType)
            } catch (e: IllegalArgumentException) {
                MediaType.UNKNOWN
            },
            year = work.year,
            season = work.season,
            episode = work.episode,
            tmdbId = work.tmdbId?.toIntOrNull()?.let { TmdbId(it) },
            imdbId = work.imdbId,
            poster = work.poster,
            backdrop = work.backdrop,
            thumbnail = work.thumbnail,
            plot = work.plot,
            rating = work.rating,
            durationMs = work.durationMs,
            genres = work.genres,
            director = work.director,
            cast = work.cast,
            trailer = work.trailer,
            sources = sources,
        )
    }

    private fun mapToMediaSourceRef(sourceRef: NX_WorkSourceRef): MediaSourceRef {
        val variants = getVariantsForSource(sourceRef.sourceKey)
        val bestVariant = variants.maxByOrNull { it.height ?: 0 }

        val playbackHints = PlaybackHintsDecoder.decodeFromVariant(bestVariant)
        val sourceLabel = SourceLabelBuilder.buildLabelWithQuality(
            sourceType = sourceRef.sourceType,
            accountLabel = sourceRef.accountKey,
            qualityHeight = bestVariant?.height,
        )

        val quality = bestVariant?.let { variant ->
            MediaQuality(
                resolution = variant.height,
                resolutionLabel = ResolutionLabel.fromHeight(variant.height),
                codec = variant.videoCodec,
            )
        }

        val format = bestVariant?.containerFormat?.let { container ->
            MediaFormat(
                container = container,
                videoCodec = bestVariant.videoCodec,
                audioCodec = bestVariant.audioCodec,
            )
        }

        return MediaSourceRef(
            sourceType = try {
                SourceType.valueOf(sourceRef.sourceType.uppercase())
            } catch (e: IllegalArgumentException) {
                SourceType.UNKNOWN
            },
            sourceId = PipelineItemId(sourceRef.sourceKey),
            sourceLabel = sourceLabel,
            quality = quality,
            languages = null,
            format = format,
            sizeBytes = sourceRef.fileSizeBytes,
            durationMs = null,
            addedAt = sourceRef.discoveredAt,
            priority = SourcePriority.totalPriority(
                sourceType = sourceRef.sourceType,
                qualityTag = bestVariant?.qualityTag,
                hasDirectUrl = !bestVariant?.playbackUrl.isNullOrBlank(),
                isExplicitVariant = bestVariant != null,
            ),
            playbackHints = playbackHints,
        )
    }

    private fun mapToCanonicalResumeInfo(
        userState: NX_WorkUserState,
        canonicalKey: CanonicalId,
    ): CanonicalResumeInfo {
        val progressPercent = if (userState.totalDurationMs > 0) {
            (userState.resumePositionMs.toFloat() / userState.totalDurationMs).coerceIn(0f, 1f)
        } else {
            0f
        }

        return CanonicalResumeInfo(
            canonicalKey = canonicalKey,
            progressPercent = progressPercent,
            positionMs = userState.resumePositionMs,
            durationMs = userState.totalDurationMs,
            lastSourceType = null,
            lastSourceId = null,
            lastSourceDurationMs = null,
            isCompleted = userState.isWatched,
            watchedCount = userState.watchCount,
            updatedAt = userState.lastWatchedAt ?: userState.updatedAt,
        )
    }

    private fun upsertVariant(
        work: NX_Work,
        sourceRef: NX_WorkSourceRef,
        source: MediaSourceRef,
    ) {
        val qualityTag = source.quality?.resolutionLabel ?: "source"
        val variantKey = "${sourceRef.sourceKey}#$qualityTag:original"

        val existing = variantBox.query(NX_WorkVariant_.variantKey.equal(variantKey, StringOrder.CASE_SENSITIVE))
            .build()
            .findFirst()

        val variant = existing ?: NX_WorkVariant()
        variant.apply {
            this.variantKey = variantKey
            this.qualityTag = qualityTag
            this.languageTag = "original"
            this.playbackUrl = source.playbackHints[PlaybackHintKeys.Xtream.DIRECT_SOURCE]
                ?: source.playbackHints["playbackUrl"] // legacy fallback
            this.playbackMethod = source.playbackHints["playbackMethod"] ?: "DIRECT"
            this.containerFormat = source.format?.container
            this.videoCodec = source.format?.videoCodec ?: source.quality?.codec
            this.width = null
            this.height = source.quality?.resolution
            this.sourceKey = sourceRef.sourceKey
            // SSOT: Serialize ALL playbackHints to JSON — no downstream fallbacks needed
            this.playbackHintsJson = PlaybackHintsDecoder.encodeToJson(source.playbackHints)
        }
        variant.work.target = work
        variant.workKey = work.workKey

        variantBox.put(variant)
    }

    private fun extractSourceSpecificId(sourceKey: String): String {
        return SourceKeyParser.extractItemKey(sourceKey) ?: sourceKey
    }

    /** Delegates to [MediaTypeMapper.toMediaKind] — SSOT. NX_CONSOLIDATION_PLAN Phase 2. */
    private fun mediaTypeToKind(mediaType: MediaType): MediaKind =
        MediaTypeMapper.toMediaKind(mediaType)

    /** Delegates to [MediaTypeMapper.workTypeStringToMediaKind] — SSOT. */
    private fun workTypeToKind(workType: String): MediaKind =
        MediaTypeMapper.workTypeStringToMediaKind(workType)

    /** Delegates to [MediaTypeMapper.fromMediaKind] — SSOT. NX_CONSOLIDATION_PLAN Phase 7 #2. */
    private fun kindToWorkType(kind: MediaKind): String =
        MediaTypeMapper.fromMediaKind(kind)
}
