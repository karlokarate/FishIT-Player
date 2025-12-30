package com.fishit.player.feature.detail.enrichment

import com.fishit.player.core.metadata.TmdbMetadataResolver
import com.fishit.player.core.model.MediaType
import com.fishit.player.core.model.NormalizedMediaMetadata
import com.fishit.player.core.model.SourceType
import com.fishit.player.core.model.TmdbMediaType
import com.fishit.player.core.model.TmdbRef
import com.fishit.player.core.model.repository.CanonicalMediaRepository
import com.fishit.player.core.model.repository.CanonicalMediaWithSources
import com.fishit.player.infra.logging.UnifiedLog
import com.fishit.player.infra.transport.xtream.XtreamApiClient
import com.fishit.player.pipeline.xtream.mapper.toRawMediaMetadata
import com.fishit.player.pipeline.xtream.model.XtreamVodItem
import javax.inject.Inject
import javax.inject.Singleton

/**
 * On-demand enrichment service for detail screens.
 *
 * Per AGENTS.md Section 4:
 * - Feature modules may access transport layer for on-demand enrichment
 * - Enrichment follows pipeline priority: Xtream > TMDB (for Telegram-only)
 *
 * Enrichment Strategy:
 * 1. If media has Xtream source AND plot is missing → fetch via get_vod_info
 * 2. If media has NO Xtream source AND TMDB ID exists AND plot is missing → TMDB enrichment
 * 3. If media already has plot → skip enrichment (fast path)
 *
 * This ensures:
 * - Xtream detail data always takes precedence (richer, provider-specific)
 * - TMDB enrichment only for Telegram-only media (no redundant API calls)
 */
@Singleton
class DetailEnrichmentService
@Inject
constructor(
        private val xtreamApiClient: XtreamApiClient,
        private val tmdbResolver: TmdbMetadataResolver,
        private val canonicalMediaRepository: CanonicalMediaRepository,
) {
    companion object {
        private const val TAG = "DetailEnrichment"
    }

    /**
     * Enrich canonical media with detailed metadata if needed.
     *
     * Priority:
     * 1. Xtream enrichment (if has Xtream source)
     * 2. TMDB enrichment (only if NO Xtream source and has TMDB ID)
     *
     * @param media The canonical media with sources
     * @return Updated media with enriched metadata (or original if no enrichment needed)
     */
    suspend fun enrichIfNeeded(media: CanonicalMediaWithSources): CanonicalMediaWithSources {
        val startMs = System.currentTimeMillis()
        
        // Fast path: already has plot → no enrichment needed
        if (!media.plot.isNullOrBlank()) {
            UnifiedLog.d(TAG) { "enrichIfNeeded: skipped (already has plot) canonicalId=${media.canonicalId.key.value}" }
            return media
        }

        val hasXtreamSource = media.sources.any { it.sourceType == SourceType.XTREAM }

        val result = when {
            hasXtreamSource -> enrichFromXtream(media)
            media.tmdbId != null -> enrichFromTmdb(media)
            else -> {
                UnifiedLog.d(TAG) {
                    "enrichIfNeeded: skipped (no enrichment path) canonicalId=${media.canonicalId.key.value} hasXtream=false hasTmdbId=false"
                }
                media
            }
        }
        
        val durationMs = System.currentTimeMillis() - startMs
        if (result !== media) {
            UnifiedLog.i(TAG) {
                "enrichIfNeeded: completed in ${durationMs}ms canonicalId=${media.canonicalId.key.value} " +
                "hasPlot=${!result.plot.isNullOrBlank()} source=${if (hasXtreamSource) "xtream" else "tmdb"}"
            }
        }
        
        return result
    }

    /**
     * Enrich from Xtream get_vod_info API.
     *
     * This provides rich metadata: plot, genres, director, cast, duration, trailer.
     */
    private suspend fun enrichFromXtream(media: CanonicalMediaWithSources): CanonicalMediaWithSources {
        val xtreamSource = media.sources.firstOrNull { it.sourceType == SourceType.XTREAM }
        if (xtreamSource == null) return media

        // Parse VOD ID from source ID: xtream:vod:{id} or xtream:vod:{id}:{ext}
        val vodId = parseXtreamVodId(xtreamSource.sourceId.value)
        if (vodId == null) {
            UnifiedLog.w(TAG) { "enrichFromXtream: invalid sourceId=${xtreamSource.sourceId.value}" }
            return media
        }

        return try {
            UnifiedLog.d(TAG) { "enrichFromXtream: fetching vod info vodId=$vodId canonicalId=${media.canonicalId.key.value}" }

            val vodInfo = xtreamApiClient.getVodInfo(vodId)
            if (vodInfo == null) {
                UnifiedLog.w(TAG) { "enrichFromXtream: API returned null vodId=$vodId" }
                return media
            }

            // Convert to RawMediaMetadata and upsert
            val containerExt = parseXtreamContainerExt(xtreamSource.sourceId.value)
            val vodItem = XtreamVodItem(
                    id = vodId,
                    name = media.canonicalTitle,
                    containerExtension = containerExt,
            )
            val rawMetadata = vodInfo.toRawMediaMetadata(vodItem)

            // Build TmdbRef from raw metadata or existing media
            val tmdbRef = rawMetadata.externalIds.tmdb
                    ?: media.tmdbId?.let { TmdbRef(TmdbMediaType.MOVIE, it.value) }

            // Create normalized metadata for upsert
            // CRITICAL: Keep the existing canonicalTitle - it's already been through the
            // SceneNameParser/Normalizer. The rawMetadata.originalTitle from get_vod_info
            // may contain scene tags or different formatting we don't want.
            val normalized = NormalizedMediaMetadata(
                    canonicalTitle = media.canonicalTitle, // Keep normalized title!
                    mediaType = MediaType.MOVIE,
                    year = rawMetadata.year ?: media.year,
                    tmdb = tmdbRef,
                    plot = rawMetadata.plot,
                    genres = rawMetadata.genres,
                    director = rawMetadata.director,
                    cast = rawMetadata.cast,
                    rating = rawMetadata.rating,
                    durationMs = rawMetadata.durationMs,
                    trailer = rawMetadata.trailer, // YouTube trailer URL
                    poster = rawMetadata.poster ?: media.poster,
                    backdrop = rawMetadata.backdrop ?: media.backdrop,
            )

            canonicalMediaRepository.upsertCanonicalMedia(normalized)

            UnifiedLog.i(TAG) {
                "enrichFromXtream: success canonicalId=${media.canonicalId.key.value} hasPlot=${!normalized.plot.isNullOrBlank()} hasGenres=${!normalized.genres.isNullOrBlank()}"
            }

            // Re-fetch updated media
            canonicalMediaRepository.findByCanonicalId(media.canonicalId) ?: media
        } catch (e: Exception) {
            UnifiedLog.e(TAG, e) { "enrichFromXtream: failed vodId=$vodId canonicalId=${media.canonicalId.key.value}" }
            media
        }
    }

    /**
     * Enrich from TMDB API.
     *
     * Only used when media has NO Xtream source but has a TMDB ID.
     * Typically Telegram-only media.
     */
    private suspend fun enrichFromTmdb(media: CanonicalMediaWithSources): CanonicalMediaWithSources {
        val tmdbId = media.tmdbId
        if (tmdbId == null) return media

        return try {
            UnifiedLog.d(TAG) { "enrichFromTmdb: resolving tmdbId=${tmdbId.value} canonicalId=${media.canonicalId.key.value}" }

            // Build TmdbRef from existing TMDB ID
            // Determine type: movies have kind=MOVIE, episodes have kind=EPISODE
            val tmdbType = if (media.canonicalId.kind == com.fishit.player.core.model.MediaKind.EPISODE) {
                TmdbMediaType.TV
            } else {
                TmdbMediaType.MOVIE
            }

            // Create normalized metadata for TMDB enrichment
            val toEnrich = NormalizedMediaMetadata(
                    canonicalTitle = media.canonicalTitle,
                    mediaType = if (tmdbType == TmdbMediaType.TV) MediaType.SERIES else MediaType.MOVIE,
                    year = media.year,
                    tmdb = TmdbRef(tmdbType, tmdbId.value),
                    poster = media.poster,
                    backdrop = media.backdrop,
            )

            // Use TmdbMetadataResolver for enrichment
            val enriched = tmdbResolver.enrich(toEnrich)

            // Only upsert if we got new data
            if (!enriched.plot.isNullOrBlank() || !enriched.genres.isNullOrBlank()) {
                canonicalMediaRepository.upsertCanonicalMedia(enriched)
                
                UnifiedLog.i(TAG) {
                    "enrichFromTmdb: success canonicalId=${media.canonicalId.key.value} hasPlot=${!enriched.plot.isNullOrBlank()} hasGenres=${!enriched.genres.isNullOrBlank()}"
                }

                // Re-fetch updated media
                return canonicalMediaRepository.findByCanonicalId(media.canonicalId) ?: media
            }

            media
        } catch (e: Exception) {
            UnifiedLog.e(TAG, e) { "enrichFromTmdb: failed tmdbId=${tmdbId.value} canonicalId=${media.canonicalId.key.value}" }
            media
        }
    }

    /**
     * Parse Xtream VOD ID from source ID.
     * Format: xtream:vod:{id} or xtream:vod:{id}:{ext}
     */
    private fun parseXtreamVodId(sourceId: String): Int? {
        val parts = sourceId.split(":")
        return if (parts.size >= 3 && parts[0] == "xtream" && parts[1] == "vod") {
            parts[2].toIntOrNull()
        } else {
            null
        }
    }

    /**
     * Parse container extension from source ID.
     * Format: xtream:vod:{id}:{ext} → ext
     */
    private fun parseXtreamContainerExt(sourceId: String): String? {
        val parts = sourceId.split(":")
        return if (parts.size >= 4 && parts[0] == "xtream" && parts[1] == "vod") {
            parts[3]
        } else {
            null
        }
    }
}
