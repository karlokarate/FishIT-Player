package com.fishit.player.feature.detail.model

import com.fishit.player.core.detail.domain.DomainDetailMedia
import com.fishit.player.core.detail.domain.DomainResumeState
import com.fishit.player.core.detail.domain.DomainSourceInfo

/**
 * Maps domain models to UI models for feature:detail.
 *
 * **Architecture (v2 - INV-6 compliant):**
 * - Domain layer returns [DomainDetailMedia], [DomainResumeState]
 * - This mapper converts them to UI-specific models
 * - Mapping happens in Feature layer (here)
 * - No data-layer or persistence types leak into Feature
 *
 * **Why this exists:**
 * - Domain models are stable contracts
 * - UI models can change with UI requirements
 * - Decouples UI evolution from domain evolution
 */
object DetailModelMapper {
    /**
     * Maps domain detail media to UI model.
     */
    fun mapToDetailMediaInfo(domain: DomainDetailMedia): DetailMediaInfo =
        DetailMediaInfo(
            workKey = domain.workKey,
            title = domain.title,
            mediaType = domain.mediaType,
            year = domain.year,
            season = domain.season,
            episode = domain.episode,
            tmdbId = domain.tmdbId,
            imdbId = domain.imdbId,
            poster = domain.poster,
            backdrop = domain.backdrop,
            plot = domain.plot,
            rating = domain.rating,
            durationMs = domain.durationMs,
            genres = domain.genres,
            director = domain.director,
            cast = domain.cast,
            trailer = domain.trailer,
            isAdult = domain.isAdult,
            sources = domain.sources.map { mapToDetailSourceInfo(it) },
        )

    /**
     * Maps domain source info to UI model.
     */
    fun mapToDetailSourceInfo(domain: DomainSourceInfo): DetailSourceInfo =
        DetailSourceInfo(
            sourceKey = domain.sourceKey,
            sourceType = domain.sourceType,
            sourceLabel = domain.sourceLabel,
            accountKey = domain.accountKey,
            qualityTag = domain.qualityTag,
            width = domain.width,
            height = domain.height,
            videoCodec = domain.videoCodec,
            containerFormat = domain.containerFormat,
            fileSizeBytes = domain.fileSizeBytes,
            language = domain.language,
            priority = domain.priority,
            isAvailable = domain.isAvailable,
            isPlaybackReady = domain.playbackHints.isNotEmpty(),
            playbackHints = domain.playbackHints,
        )

    /**
     * Maps domain resume state to UI model.
     */
    fun mapToDetailResumeInfo(domain: DomainResumeState): DetailResumeInfo =
        DetailResumeInfo(
            workKey = domain.workKey,
            profileId = domain.profileId,
            positionMs = domain.positionMs,
            durationMs = domain.durationMs,
            progressPercent = domain.progressPercent,
            isCompleted = domain.isCompleted,
            watchCount = domain.watchCount,
            lastSourceKey = domain.lastSourceKey,
            lastSourceType = domain.lastSourceType,
            lastWatchedAt = domain.lastWatchedAt,
            isFavorite = domain.isFavorite,
            inWatchlist = domain.inWatchlist,
        )
}
