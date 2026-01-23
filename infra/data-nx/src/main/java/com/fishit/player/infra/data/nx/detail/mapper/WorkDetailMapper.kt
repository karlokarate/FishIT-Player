package com.fishit.player.infra.data.nx.detail.mapper

import com.fishit.player.core.persistence.obx.NX_Work
import com.fishit.player.core.persistence.obx.NX_WorkSourceRef
import com.fishit.player.core.persistence.obx.NX_WorkUserState
import com.fishit.player.core.persistence.obx.NX_WorkVariant
import com.fishit.player.infra.data.nx.detail.dto.WorkResumeInfo
import com.fishit.player.infra.data.nx.detail.dto.WorkSourceInfo
import com.fishit.player.infra.data.nx.detail.dto.WorkWithSources
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Maps NX_* entities to feature:detail DTOs.
 *
 * This mapper bridges the persistence layer (NX_Work, NX_WorkSourceRef, NX_WorkVariant,
 * NX_WorkUserState) with the feature:detail layer (WorkWithSources, WorkResumeInfo).
 *
 * **NX SSOT Contract (INV-6):** UI reads exclusively from NX_* entities.
 * This mapper provides the projection needed by feature:detail.
 */
@Singleton
class WorkDetailMapper @Inject constructor() {
    /**
     * Maps NX_Work with its sources and variants to WorkWithSources DTO.
     *
     * @param work The NX_Work entity
     * @param sourceRefs All NX_WorkSourceRef for this work
     * @param variants All NX_WorkVariant for this work
     * @param accountLabels Map of accountKey → human-readable label
     * @return WorkWithSources DTO ready for UI consumption
     */
    fun mapToWorkWithSources(
        work: NX_Work,
        sourceRefs: List<NX_WorkSourceRef>,
        variants: List<NX_WorkVariant>,
        accountLabels: Map<String, String> = emptyMap(),
    ): WorkWithSources {
        // Group variants by sourceKey for efficient lookup
        val variantsBySource = variants.groupBy { it.sourceKey }

        // Build source info list
        val sources =
            sourceRefs.flatMap { sourceRef ->
                val sourceVariants = variantsBySource[sourceRef.sourceKey] ?: emptyList()

                if (sourceVariants.isEmpty()) {
                    // Source without explicit variants - create default variant
                    listOf(
                        mapSourceWithDefaultVariant(
                            sourceRef = sourceRef,
                            accountLabel = accountLabels[sourceRef.accountKey] ?: sourceRef.accountKey,
                        ),
                    )
                } else {
                    // Map each variant
                    sourceVariants.map { variant ->
                        mapSourceWithVariant(
                            sourceRef = sourceRef,
                            variant = variant,
                            accountLabel = accountLabels[sourceRef.accountKey] ?: sourceRef.accountKey,
                        )
                    }
                }
            }

        // Sort sources by priority (highest first)
        val sortedSources = sources.sortedByDescending { it.priority }

        return WorkWithSources(
            workKey = work.workKey,
            workType = work.workType,
            canonicalTitle = work.canonicalTitle,
            year = work.year,
            season = work.season,
            episode = work.episode,
            tmdbId = work.tmdbId,
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
            trailer = null, // TODO: Add trailer field to NX_Work if needed
            sources = sortedSources,
        )
    }

    /**
     * Maps source reference with a specific variant.
     */
    private fun mapSourceWithVariant(
        sourceRef: NX_WorkSourceRef,
        variant: NX_WorkVariant,
        accountLabel: String,
    ): WorkSourceInfo =
        WorkSourceInfo(
            // Source identity
            sourceKey = sourceRef.sourceKey,
            sourceType = sourceRef.sourceType,
            accountKey = sourceRef.accountKey,
            sourceLabel = buildSourceLabel(sourceRef, accountLabel),
            // Variant identity
            variantKey = variant.variantKey,
            qualityTag = variant.qualityTag,
            languageTag = variant.languageTag,
            // Quality info
            width = variant.width,
            height = variant.height,
            // Playback
            playbackUrl = variant.playbackUrl,
            playbackMethod = variant.playbackMethod,
            containerFormat = variant.containerFormat,
            videoCodec = variant.videoCodec,
            audioCodec = variant.audioCodec,
            // File info (from sourceRef)
            fileSizeBytes = sourceRef.fileSizeBytes,
            // Hints for PlaybackSourceFactory
            playbackHints = buildPlaybackHints(sourceRef, variant),
            // Selection priority
            priority = calculatePriority(sourceRef, variant),
            isAvailable = true, // TODO: Implement availability check
        )

    /**
     * Maps source reference with a default "source" variant.
     * Used when no explicit variants exist.
     */
    private fun mapSourceWithDefaultVariant(
        sourceRef: NX_WorkSourceRef,
        accountLabel: String,
    ): WorkSourceInfo =
        WorkSourceInfo(
            // Source identity
            sourceKey = sourceRef.sourceKey,
            sourceType = sourceRef.sourceType,
            accountKey = sourceRef.accountKey,
            sourceLabel = buildSourceLabel(sourceRef, accountLabel),
            // Default variant
            variantKey = "${sourceRef.sourceKey}#source:original",
            qualityTag = "source",
            languageTag = "original",
            // No explicit quality info
            width = null,
            height = null,
            // No direct URL (factory will resolve)
            playbackUrl = null,
            playbackMethod = "DIRECT",
            containerFormat = sourceRef.mimeType?.let { guesContainerFromMime(it) },
            videoCodec = null,
            audioCodec = null,
            // File info
            fileSizeBytes = sourceRef.fileSizeBytes,
            // Hints
            playbackHints = buildPlaybackHints(sourceRef, null),
            // Lower priority than explicit variants
            priority = calculatePriority(sourceRef, null),
            isAvailable = true,
        )

    /**
     * Builds human-readable source label for UI display.
     */
    private fun buildSourceLabel(
        sourceRef: NX_WorkSourceRef,
        accountLabel: String,
    ): String =
        when (sourceRef.sourceType) {
            "telegram" -> "Telegram: $accountLabel"
            "xtream" -> "IPTV: $accountLabel"
            "local" -> "Local File"
            "plex" -> "Plex: $accountLabel"
            else -> "${sourceRef.sourceType}: $accountLabel"
        }

    /**
     * Builds playback hints map for PlaybackSourceFactory.
     *
     * These hints contain source-specific data needed for playback resolution.
     */
    private fun buildPlaybackHints(
        sourceRef: NX_WorkSourceRef,
        variant: NX_WorkVariant?,
    ): Map<String, String> =
        buildMap {
            // Common hints
            put("sourceType", sourceRef.sourceType)
            put("accountKey", sourceRef.accountKey)
            put("sourceId", sourceRef.sourceId)

            // Telegram-specific
            sourceRef.telegramChatId?.let { put("telegramChatId", it.toString()) }
            sourceRef.telegramMessageId?.let { put("telegramMessageId", it.toString()) }

            // Xtream-specific
            sourceRef.xtreamStreamId?.let { put("xtreamStreamId", it.toString()) }
            sourceRef.xtreamCategoryId?.let { put("xtreamCategoryId", it.toString()) }

            // File info
            sourceRef.mimeType?.let { put("mimeType", it) }
            sourceRef.fileSizeBytes?.let { put("fileSizeBytes", it.toString()) }

            // Variant-specific
            variant?.let {
                it.playbackUrl?.let { url -> put("playbackUrl", url) }
                put("playbackMethod", it.playbackMethod)
                it.containerFormat?.let { f -> put("containerFormat", f) }
            }
        }

    /**
     * Calculates selection priority for source/variant combination.
     *
     * Higher priority = preferred for auto-selection.
     *
     * Priority factors:
     * - Source type preference (local > xtream > telegram)
     * - Quality (4k > 1080p > 720p > source)
     * - Has direct URL (+10 points)
     * - Explicit variant vs default (+5 points)
     */
    private fun calculatePriority(
        sourceRef: NX_WorkSourceRef,
        variant: NX_WorkVariant?,
    ): Int {
        var priority = 0

        // Source type base priority
        priority +=
            when (sourceRef.sourceType) {
                "local" -> 100
                "plex" -> 80
                "xtream" -> 60
                "telegram" -> 40
                else -> 20
            }

        // Quality priority
        if (variant != null) {
            priority +=
                when (variant.qualityTag.lowercase()) {
                    "4k", "2160p", "uhd" -> 50
                    "1080p", "fhd" -> 40
                    "720p", "hd" -> 30
                    "480p", "sd" -> 20
                    "source" -> 25 // Slightly lower than explicit qualities
                    else -> 10
                }

            // Direct URL bonus
            if (!variant.playbackUrl.isNullOrBlank()) {
                priority += 10
            }

            // Explicit variant bonus
            priority += 5
        }

        return priority
    }

    /**
     * Guesses container format from MIME type.
     */
    private fun guesContainerFromMime(mimeType: String): String? =
        when {
            mimeType.contains("mp4") -> "mp4"
            mimeType.contains("mkv") || mimeType.contains("matroska") -> "mkv"
            mimeType.contains("avi") -> "avi"
            mimeType.contains("webm") -> "webm"
            mimeType.contains("mpegts") || mimeType.contains("ts") -> "ts"
            else -> null
        }

    /**
     * Maps NX_WorkUserState to WorkResumeInfo DTO.
     *
     * @param userState The NX_WorkUserState entity
     * @return WorkResumeInfo DTO
     */
    fun mapToResumeInfo(userState: NX_WorkUserState): WorkResumeInfo {
        val progressPercent =
            if (userState.totalDurationMs > 0) {
                (userState.resumePositionMs.toFloat() / userState.totalDurationMs.toFloat())
                    .coerceIn(0f, 1f)
            } else {
                0f
            }

        return WorkResumeInfo(
            workKey = userState.workKey,
            profileId = userState.profileId,
            resumePositionMs = userState.resumePositionMs,
            totalDurationMs = userState.totalDurationMs,
            progressPercent = progressPercent,
            isWatched = userState.isWatched,
            watchCount = userState.watchCount,
            isFavorite = userState.isFavorite,
            lastWatchedAt = userState.lastWatchedAt,
            lastSourceKey = null, // TODO: Add to NX_WorkUserState if needed
            lastVariantKey = null, // TODO: Add to NX_WorkUserState if needed
            updatedAt = userState.updatedAt,
        )
    }

    /**
     * Creates an empty resume info for works without user state.
     */
    fun emptyResumeInfo(
        workKey: String,
        profileId: Long,
    ): WorkResumeInfo =
        WorkResumeInfo(
            workKey = workKey,
            profileId = profileId,
            resumePositionMs = 0,
            totalDurationMs = 0,
            progressPercent = 0f,
            isWatched = false,
            watchCount = 0,
            isFavorite = false,
            lastWatchedAt = null,
            lastSourceKey = null,
            lastVariantKey = null,
            updatedAt = 0,
        )
}
