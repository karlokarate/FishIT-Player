package com.fishit.player.infra.data.nx.detail.repository

import com.fishit.player.core.detail.domain.DomainDetailMedia
import com.fishit.player.core.detail.domain.DomainResumeState
import com.fishit.player.core.detail.domain.DomainSourceInfo
import com.fishit.player.core.detail.domain.NxDetailMediaRepository
import com.fishit.player.core.persistence.obx.NX_Work
import com.fishit.player.core.persistence.obx.NX_WorkSourceRef
import com.fishit.player.core.persistence.obx.NX_WorkUserState
import com.fishit.player.core.persistence.obx.NX_WorkUserState_
import com.fishit.player.core.persistence.obx.NX_WorkVariant
import com.fishit.player.core.persistence.obx.NX_Work_
import io.objectbox.Box
import io.objectbox.BoxStore
import io.objectbox.kotlin.toFlow
import io.objectbox.query.QueryBuilder.StringOrder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of [NxDetailMediaRepository] backed by NX_* ObjectBox entities.
 *
 * **Architecture (v2 - INV-6 compliant):**
 * - This implementation lives in Data layer (infra/data-nx)
 * - Returns Domain-layer models (DomainDetailMedia, DomainResumeState)
 * - Internal DTOs (WorkWithSources, etc.) are NOT exported
 * - NX_* entities are the SSOT for UI data
 *
 * **Feature layer (feature/detail):**
 * - Consumes this via NxDetailMediaRepository interface
 * - Maps DomainDetailMedia → DetailMediaInfo (UI model) locally
 */
@Singleton
class NxDetailMediaRepositoryImpl @Inject constructor(
    boxStore: BoxStore,
) : NxDetailMediaRepository {

    private val workBox: Box<NX_Work> = boxStore.boxFor(NX_Work::class.java)
    private val sourceRefBox: Box<NX_WorkSourceRef> = boxStore.boxFor(NX_WorkSourceRef::class.java)
    private val variantBox: Box<NX_WorkVariant> = boxStore.boxFor(NX_WorkVariant::class.java)
    private val userStateBox: Box<NX_WorkUserState> = boxStore.boxFor(NX_WorkUserState::class.java)

    // =========================================================================
    // Load Operations
    // =========================================================================

    override suspend fun loadByWorkKey(workKey: String): DomainDetailMedia? =
        withContext(Dispatchers.IO) {
            val work = workBox.query(NX_Work_.workKey.equal(workKey, StringOrder.CASE_SENSITIVE))
                .build().findFirst() ?: return@withContext null

            val workId = work.id
            // Filter sourceRefs by work.targetId (ToOne relation)
            val sourceRefs = sourceRefBox.all.filter { it.work.targetId == workId }
            // Filter variants by sourceKey of the found sourceRefs
            val sourceKeys = sourceRefs.map { it.sourceKey }.toSet()
            val variants = variantBox.all.filter { it.sourceKey in sourceKeys }

            mapToDomainDetailMedia(work, sourceRefs, variants)
        }

    override fun observeByWorkKey(workKey: String): Flow<DomainDetailMedia?> {
        val query = workBox.query(NX_Work_.workKey.equal(workKey, StringOrder.CASE_SENSITIVE)).build()

        return query.subscribe().toFlow().map { works ->
            val work = works.firstOrNull() ?: return@map null

            val workId = work.id
            val sourceRefs = sourceRefBox.all.filter { it.work.targetId == workId }
            val sourceKeys = sourceRefs.map { it.sourceKey }.toSet()
            val variants = variantBox.all.filter { it.sourceKey in sourceKeys }

            mapToDomainDetailMedia(work, sourceRefs, variants)
        }
    }

    override suspend fun loadResumeState(
        workKey: String,
        profileId: Long,
    ): DomainResumeState? = withContext(Dispatchers.IO) {
        userStateBox.query(
            NX_WorkUserState_.workKey.equal(workKey, StringOrder.CASE_SENSITIVE)
                .and(NX_WorkUserState_.profileId.equal(profileId))
        ).build().findFirst()?.let { mapToDomainResumeState(it) }
    }

    override fun observeResumeState(
        workKey: String,
        profileId: Long,
    ): Flow<DomainResumeState?> {
        val query = userStateBox.query(
            NX_WorkUserState_.workKey.equal(workKey, StringOrder.CASE_SENSITIVE)
                .and(NX_WorkUserState_.profileId.equal(profileId))
        ).build()

        return query.subscribe().toFlow().map { states ->
            states.firstOrNull()?.let { mapToDomainResumeState(it) }
        }
    }

    // =========================================================================
    // Update Operations
    // =========================================================================

    override suspend fun updateResumeState(
        workKey: String,
        profileId: Long,
        positionMs: Long,
        durationMs: Long,
        sourceKey: String,
        sourceType: String,
    ): Unit = withContext(Dispatchers.IO) {
        val existing = userStateBox.query(
            NX_WorkUserState_.workKey.equal(workKey, StringOrder.CASE_SENSITIVE)
                .and(NX_WorkUserState_.profileId.equal(profileId))
        ).build().findFirst()

        val progressPercent = if (durationMs > 0) {
            (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
        } else 0f

        val isCompleted = progressPercent >= 0.9f
        val now = System.currentTimeMillis()

        val entity = existing?.apply {
            this.resumePositionMs = positionMs
            this.totalDurationMs = durationMs
            this.lastWatchedAt = now
            this.updatedAt = now
            if (isCompleted && !this.isWatched) {
                this.isWatched = true
                this.watchCount += 1
            }
        } ?: NX_WorkUserState(
            id = 0,
            workKey = workKey,
            profileId = profileId,
            resumePositionMs = positionMs,
            totalDurationMs = durationMs,
            isWatched = isCompleted,
            watchCount = if (isCompleted) 1 else 0,
            isFavorite = false,
            createdAt = now,
            updatedAt = now,
            lastWatchedAt = now,
        )

        userStateBox.put(entity)
    }

    override suspend fun markCompleted(
        workKey: String,
        profileId: Long,
    ): Unit = withContext(Dispatchers.IO) {
        val existing = userStateBox.query(
            NX_WorkUserState_.workKey.equal(workKey, StringOrder.CASE_SENSITIVE)
                .and(NX_WorkUserState_.profileId.equal(profileId))
        ).build().findFirst()

        val now = System.currentTimeMillis()

        val entity = existing?.apply {
            this.isWatched = true
            this.watchCount += 1
            this.resumePositionMs = this.totalDurationMs
            this.updatedAt = now
            this.lastWatchedAt = now
        } ?: NX_WorkUserState(
            id = 0,
            workKey = workKey,
            profileId = profileId,
            resumePositionMs = 0,
            totalDurationMs = 0,
            isWatched = true,
            watchCount = 1,
            isFavorite = false,
            createdAt = now,
            updatedAt = now,
            lastWatchedAt = now,
        )

        userStateBox.put(entity)
    }

    override suspend fun toggleFavorite(
        workKey: String,
        profileId: Long,
    ): Boolean = withContext(Dispatchers.IO) {
        val existing = userStateBox.query(
            NX_WorkUserState_.workKey.equal(workKey, StringOrder.CASE_SENSITIVE)
                .and(NX_WorkUserState_.profileId.equal(profileId))
        ).build().findFirst()

        val now = System.currentTimeMillis()

        if (existing != null) {
            existing.isFavorite = !existing.isFavorite
            existing.updatedAt = now
            userStateBox.put(existing)
            existing.isFavorite
        } else {
            val newEntity = NX_WorkUserState(
                id = 0,
                workKey = workKey,
                profileId = profileId,
                resumePositionMs = 0,
                totalDurationMs = 0,
                isWatched = false,
                watchCount = 0,
                isFavorite = true,
                createdAt = now,
                updatedAt = now,
                lastWatchedAt = null,
            )
            userStateBox.put(newEntity)
            true
        }
    }

    override suspend fun toggleWatchlist(
        workKey: String,
        profileId: Long,
    ): Boolean = withContext(Dispatchers.IO) {
        val existing = userStateBox.query(
            NX_WorkUserState_.workKey.equal(workKey, StringOrder.CASE_SENSITIVE)
                .and(NX_WorkUserState_.profileId.equal(profileId))
        ).build().findFirst()

        val now = System.currentTimeMillis()

        if (existing != null) {
            existing.inWatchlist = !existing.inWatchlist
            existing.updatedAt = now
            userStateBox.put(existing)
            existing.inWatchlist
        } else {
            val newEntity = NX_WorkUserState(
                id = 0,
                workKey = workKey,
                profileId = profileId,
                resumePositionMs = 0,
                totalDurationMs = 0,
                isWatched = false,
                watchCount = 0,
                isFavorite = false,
                inWatchlist = true,
                createdAt = now,
                updatedAt = now,
                lastWatchedAt = null,
            )
            userStateBox.put(newEntity)
            true
        }
    }

    // =========================================================================
    // Internal Mapping (NX_* → Domain Models)
    // =========================================================================

    /**
     * Maps NX_* entities to DomainDetailMedia.
     *
     * This is internal to the Data layer - Domain/Feature never see NX_* entities.
     */
    private fun mapToDomainDetailMedia(
        work: NX_Work,
        sourceRefs: List<NX_WorkSourceRef>,
        variants: List<NX_WorkVariant>,
    ): DomainDetailMedia {
        // Group variants by sourceKey for efficient lookup
        val variantsBySource = variants.groupBy { it.sourceKey }

        // Build domain source list
        val sources = sourceRefs.flatMap { sourceRef ->
            val sourceVariants = variantsBySource[sourceRef.sourceKey] ?: emptyList()

            if (sourceVariants.isEmpty()) {
                // Source without variants - create default
                listOf(mapSourceWithDefault(sourceRef))
            } else {
                // Map each variant
                sourceVariants.map { variant ->
                    mapSourceWithVariant(sourceRef, variant)
                }
            }
        }.sortedByDescending { it.priority }

        return DomainDetailMedia(
            workKey = work.workKey,
            title = work.canonicalTitle,
            mediaType = work.workType,
            year = work.year,
            season = work.season,
            episode = work.episode,
            tmdbId = work.tmdbId,
            imdbId = work.imdbId,
            poster = work.poster,
            backdrop = work.backdrop,
            plot = work.plot,
            rating = work.rating,
            durationMs = work.durationMs,
            genres = work.genres,
            director = work.director,
            cast = work.cast,
            trailer = work.trailer,
            isAdult = work.isAdult,
            sources = sources,
        )
    }

    private fun mapSourceWithVariant(
        sourceRef: NX_WorkSourceRef,
        variant: NX_WorkVariant,
    ): DomainSourceInfo = DomainSourceInfo(
        sourceKey = sourceRef.sourceKey,
        sourceType = sourceRef.sourceType,
        sourceLabel = buildSourceLabel(sourceRef),
        accountKey = sourceRef.accountKey,
        qualityTag = variant.qualityTag,
        width = variant.width,
        height = variant.height,
        videoCodec = variant.videoCodec,
        containerFormat = variant.containerFormat,
        fileSizeBytes = sourceRef.fileSizeBytes,
        language = variant.languageTag,
        priority = calculatePriority(sourceRef, variant),
        isAvailable = true,
        playbackHints = buildPlaybackHints(sourceRef, variant),
    )

    private fun mapSourceWithDefault(
        sourceRef: NX_WorkSourceRef,
    ): DomainSourceInfo = DomainSourceInfo(
        sourceKey = sourceRef.sourceKey,
        sourceType = sourceRef.sourceType,
        sourceLabel = buildSourceLabel(sourceRef),
        accountKey = sourceRef.accountKey,
        qualityTag = "source",
        width = null,
        height = null,
        videoCodec = null,
        containerFormat = sourceRef.mimeType?.let { guesContainerFromMime(it) },
        fileSizeBytes = sourceRef.fileSizeBytes,
        language = null,
        priority = calculatePriority(sourceRef, null),
        isAvailable = true,
        playbackHints = buildPlaybackHints(sourceRef, null),
    )

    private fun buildSourceLabel(sourceRef: NX_WorkSourceRef): String = when (sourceRef.sourceType) {
        "telegram" -> "Telegram: ${sourceRef.accountKey}"
        "xtream" -> "IPTV: ${sourceRef.accountKey}"
        "local" -> "Local File"
        else -> "${sourceRef.sourceType}: ${sourceRef.accountKey}"
    }

    private fun buildPlaybackHints(
        sourceRef: NX_WorkSourceRef,
        variant: NX_WorkVariant?,
    ): Map<String, String> {
        // Primary: JSON storage from variant (contains all source-specific hints)
        if (variant != null && !variant.playbackHintsJson.isNullOrBlank()) {
            return try {
                Json.decodeFromString<Map<String, String>>(variant.playbackHintsJson!!)
            } catch (e: Exception) {
                // Fallback to legacy on decode error
                buildLegacyPlaybackHints(sourceRef, variant)
            }
        }

        // Fallback: Legacy entity fields (for old data without JSON)
        return buildLegacyPlaybackHints(sourceRef, variant)
    }

    private fun buildLegacyPlaybackHints(
        sourceRef: NX_WorkSourceRef,
        variant: NX_WorkVariant?,
    ): Map<String, String> = buildMap {
        put("sourceType", sourceRef.sourceType)
        put("accountKey", sourceRef.accountKey)
        put("sourceId", sourceRef.sourceId)

        // Telegram hints
        sourceRef.telegramChatId?.let { put("telegramChatId", it.toString()) }
        sourceRef.telegramMessageId?.let { put("telegramMessageId", it.toString()) }

        // Xtream hints
        sourceRef.xtreamStreamId?.let { put("xtreamStreamId", it.toString()) }
        sourceRef.xtreamCategoryId?.let { put("xtreamCategoryId", it.toString()) }

        // File info
        sourceRef.mimeType?.let { put("mimeType", it) }
        sourceRef.fileSizeBytes?.let { put("fileSizeBytes", it.toString()) }

        // Variant hints
        variant?.let {
            it.playbackUrl?.let { url -> put("playbackUrl", url) }
            put("playbackMethod", it.playbackMethod)
            it.containerFormat?.let { f -> put("containerFormat", f) }
        }
    }

    private fun calculatePriority(
        sourceRef: NX_WorkSourceRef,
        variant: NX_WorkVariant?,
    ): Int {
        var priority = when (sourceRef.sourceType) {
            "local" -> 100
            "xtream" -> 60
            "telegram" -> 40
            else -> 20
        }

        if (variant != null) {
            priority += when (variant.qualityTag.lowercase()) {
                "4k", "2160p" -> 50
                "1080p" -> 40
                "720p" -> 30
                "480p" -> 20
                else -> 10
            }
            if (!variant.playbackUrl.isNullOrBlank()) priority += 10
            priority += 5
        }

        return priority
    }

    private fun guesContainerFromMime(mimeType: String): String? = when {
        "mp4" in mimeType -> "mp4"
        "mkv" in mimeType || "matroska" in mimeType -> "mkv"
        "webm" in mimeType -> "webm"
        "ts" in mimeType || "mpegts" in mimeType -> "ts"
        else -> null
    }

    private fun mapToDomainResumeState(userState: NX_WorkUserState): DomainResumeState {
        val progressPercent = if (userState.totalDurationMs > 0) {
            (userState.resumePositionMs.toFloat() / userState.totalDurationMs.toFloat())
                .coerceIn(0f, 1f)
        } else 0f

        return DomainResumeState(
            workKey = userState.workKey,
            profileId = userState.profileId,
            positionMs = userState.resumePositionMs,
            durationMs = userState.totalDurationMs,
            progressPercent = progressPercent,
            isCompleted = userState.isWatched,
            watchCount = userState.watchCount,
            lastSourceKey = null, // TODO: Add to NX_WorkUserState
            lastSourceType = null,
            lastWatchedAt = userState.lastWatchedAt,
            isFavorite = userState.isFavorite,
            inWatchlist = userState.inWatchlist,
        )
    }
}
