/**
 * NxDetailWriter — Creates NEW NX entities when a detail page is opened.
 *
 * ## 3-Writer Architecture
 * | Writer | When | What |
 * |--------|------|------|
 * | [NxCatalogWriter] | Catalog Sync | Creates NX_Work + SourceRef + Variant from listing API |
 * | **NxDetailWriter** | Detail Open | Creates NEW works that don't exist yet (e.g., Episodes) |
 * | [NxEnrichmentWriter] | Detail Open | Enriches EXISTING works with info_call metadata |
 *
 * ## Responsibilities
 * - **Episode creation:** When user opens a series detail, the Xtream `get_series_info`
 *   API returns episodes. This writer creates NX_Work (EPISODE), NX_WorkSourceRef,
 *   NX_WorkVariant, and NX_WorkRelation entities for each episode.
 *
 * ## SSOT Contract
 * Uses the SAME builders as [NxCatalogWriter] to guarantee field-level consistency:
 * - [WorkEntityBuilder] for NX_Work construction (recognition state, normalized fields)
 * - [VariantBuilder] for NX_WorkVariant construction (container extraction, hints)
 * - sourceKey: `src:{sourceType}:{identifier}:{sourceId}` (via [SourceKeyParser])
 * - variantKey: `{sourceKey}#original`
 * - workKey: via [NxKeyGenerator]
 *
 * ## Playback Hints — Minimal Episode Pattern
 * Follows the same reduced-hint approach as VOD in [NxCatalogWriter]:
 * only Xtream identification keys required for URL construction at playback time.
 * Technical metadata (codecs, bitrate) lives on NX_WorkVariant fields directly,
 * NOT duplicated into playbackHints.
 *
 * ## tmdbId Handling
 * Episodes are created with heuristic workKeys (no tmdbId in key).
 * After batch creation, the caller ([NxXtreamSeriesIndexRepository.upsertEpisodes])
 * runs [NxEnrichmentWriter] over parent + all children to set tmdbId via
 * ALWAYS_UPDATE semantics.
 *
 * @see NxCatalogWriter for sync-time writing
 * @see NxEnrichmentWriter for detail-time enrichment of existing works
 * @see com.fishit.player.infra.data.nx.xtream.NxXtreamSeriesIndexRepository
 */
package com.fishit.player.infra.data.nx.writer

import com.fishit.player.core.detail.domain.EpisodeIndexItem
import com.fishit.player.core.model.ExternalIds
import com.fishit.player.core.model.ImageRef
import com.fishit.player.core.model.MediaType
import com.fishit.player.core.model.NormalizedMediaMetadata
import com.fishit.player.core.model.PlaybackHintKeys
import com.fishit.player.core.model.TmdbMediaType
import com.fishit.player.core.model.TmdbRef
import com.fishit.player.core.model.repository.NxWorkRelationRepository
import com.fishit.player.core.model.repository.NxWorkRelationRepository.RelationType
import com.fishit.player.core.model.repository.NxWorkRepository
import com.fishit.player.core.model.repository.NxWorkSourceRefRepository
import com.fishit.player.core.model.repository.NxWorkSourceRefRepository.SourceItemKind
import com.fishit.player.core.model.repository.NxWorkSourceRefRepository.SourceType
import com.fishit.player.core.model.repository.NxWorkVariantRepository
import com.fishit.player.core.persistence.obx.NxKeyGenerator
import com.fishit.player.infra.data.nx.mapper.SourceKeyParser
import com.fishit.player.infra.data.nx.mapper.base.PlaybackHintsDecoder
import com.fishit.player.infra.data.nx.writer.builder.VariantBuilder
import com.fishit.player.infra.data.nx.writer.builder.WorkEntityBuilder
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Result of [NxDetailWriter.buildEpisodeEntities] — all entities ready for batch upsert.
 */
data class EpisodeDetailResult(
    val works: List<NxWorkRepository.Work>,
    val sourceRefs: List<NxWorkSourceRefRepository.SourceRef>,
    val relations: List<NxWorkRelationRepository.Relation>,
    val variants: List<NxWorkVariantRepository.Variant>,
) {
    val isEmpty: Boolean get() = works.isEmpty()
}

@Singleton
class NxDetailWriter @Inject constructor(
    private val workEntityBuilder: WorkEntityBuilder,
    private val variantBuilder: VariantBuilder,
) {
    /**
     * Build NX entities for a batch of episodes from the Xtream Series Detail API.
     *
     * Uses the SAME [WorkEntityBuilder] and [VariantBuilder] as [NxCatalogWriter]
     * to guarantee identical field population (sortTitle, titleNormalized,
     * recognitionState, etc.).
     *
     * PlaybackHints are reduced to the minimal Xtream identification keys:
     * `CONTENT_TYPE`, `SERIES_ID`, `SEASON_NUMBER`, `EPISODE_NUMBER`,
     * `EPISODE_ID`, `CONTAINER_EXT` — matching the VOD pattern.
     *
     * @param episodes The episode data from the detail API
     * @param seriesWorkKey The workKey of the parent series NX_Work (for relations)
     * @param seriesWork The parent series NX_Work (for workKey construction: title + year)
     * @param accountKey The full account key from the series' source ref
     *                   (e.g., "xtream:user@server.com")
     * @return All entities ready for batch upsert
     */
    fun buildEpisodeEntities(
        episodes: List<EpisodeIndexItem>,
        seriesWorkKey: String?,
        seriesWork: NxWorkRepository.Work?,
        accountKey: String,
    ): EpisodeDetailResult {
        val works = mutableListOf<NxWorkRepository.Work>()
        val sourceRefs = mutableListOf<NxWorkSourceRefRepository.SourceRef>()
        val relations = mutableListOf<NxWorkRelationRepository.Relation>()
        val variants = mutableListOf<NxWorkVariantRepository.Variant>()

        // Derive the identifier portion for sourceKey construction.
        // accountKey is stored as "xtream:user@server" → identifier = "user@server"
        val identifier = accountKey.removePrefix("xtream:")

        for (episode in episodes) {
            val now = episode.lastUpdatedMs
            val episodeWorkKey = buildEpisodeWorkKey(episode, seriesWork)

            // ── sourceKey: SSOT format via SourceKeyParser ──────────────
            val cleanSourceId = episode.sourceKey.removePrefix("xtream:")
            val sourceKey = SourceKeyParser.buildSourceKey(
                sourceType = com.fishit.player.core.model.SourceType.XTREAM,
                accountKey = identifier,
                sourceId = cleanSourceId,
            )

            // ── NX_Work (via WorkEntityBuilder — same as NxCatalogWriter) ──
            val normalized = toNormalizedMetadata(episode, seriesWork)
            works += workEntityBuilder.build(normalized, episodeWorkKey, now)

            // ── NX_WorkSourceRef ───────────────────────────────────────
            sourceRefs += NxWorkSourceRefRepository.SourceRef(
                sourceKey = sourceKey,
                workKey = episodeWorkKey,
                sourceType = SourceType.XTREAM,
                accountKey = accountKey,
                sourceItemKind = SourceItemKind.EPISODE,
                sourceItemKey = "series:${episode.seriesId}:s${episode.seasonNumber}:e${episode.episodeNumber}",
                sourceTitle = episode.title,
                firstSeenAtMs = now,
                lastSeenAtMs = now,
                sourceLastModifiedMs = episode.addedTimestamp?.toLongOrNull(),
            )

            // ── NX_WorkRelation (series → episode) ─────────────────────
            if (seriesWorkKey != null) {
                relations += NxWorkRelationRepository.Relation(
                    parentWorkKey = seriesWorkKey,
                    childWorkKey = episodeWorkKey,
                    relationType = RelationType.SERIES_EPISODE,
                    seasonNumber = episode.seasonNumber,
                    episodeNumber = episode.episodeNumber,
                    orderIndex = episode.seasonNumber * 1000 + episode.episodeNumber,
                )
            }

            // ── NX_WorkVariant (via VariantBuilder — same as NxCatalogWriter) ──
            // Minimal playbackHints: only Xtream identification keys for URL building.
            // Technical metadata (codecs, bitrate, resolution) goes on Variant fields
            // via VariantBuilder, NOT duplicated in hints map.
            val playbackHints = buildEpisodePlaybackHints(episode)
            if (playbackHints.isNotEmpty()) {
                val variantKey = "$sourceKey#original"
                val durationMs = episode.durationSecs?.let { it.toLong() * 1000 }
                variants += variantBuilder.build(
                    variantKey = variantKey,
                    workKey = episodeWorkKey,
                    sourceKey = sourceKey,
                    playbackHints = playbackHints,
                    durationMs = durationMs,
                    now = now,
                )
            }
        }

        return EpisodeDetailResult(works, sourceRefs, relations, variants)
    }

    // =========================================================================
    // Private Helpers
    // =========================================================================

    /**
     * Convert [EpisodeIndexItem] to [NormalizedMediaMetadata] so we can
     * delegate to [WorkEntityBuilder] — the SSOT for NX_Work field population.
     *
     * This ensures episodes get the same fields as catalog-created works:
     * sortTitle, titleNormalized, recognitionState, etc.
     *
     * Note: tmdbId is intentionally included here. WorkEntityBuilder will set it
     * on the NX_Work entity. The workKey remains heuristic (tmdbId not in key).
     */
    private fun toNormalizedMetadata(
        episode: EpisodeIndexItem,
        seriesWork: NxWorkRepository.Work?,
    ): NormalizedMediaMetadata {
        val tmdbRef = episode.tmdbId?.let { TmdbRef(TmdbMediaType.TV, it) }
        return NormalizedMediaMetadata(
            canonicalTitle = episode.title ?: "Episode ${episode.episodeNumber}",
            mediaType = MediaType.SERIES_EPISODE,
            year = seriesWork?.year,
            season = episode.seasonNumber,
            episode = episode.episodeNumber,
            tmdb = tmdbRef,
            externalIds = ExternalIds(tmdb = tmdbRef),
            poster = ImageRef.fromString(episode.thumbUrl),
            backdrop = ImageRef.fromString(episode.coverUrl),
            thumbnail = ImageRef.fromString(episode.thumbnailUrl),
            plot = episode.plot,
            rating = episode.rating,
            durationMs = episode.durationSecs?.let { it.toLong() * 1000 },
            releaseDate = episode.airDate,
            addedTimestamp = episode.addedTimestamp?.toLongOrNull(),
        )
    }

    /**
     * Build minimal playbackHints for episode — matching the VOD pattern.
     *
     * Only includes Xtream identification keys required for playback URL construction:
     * - CONTENT_TYPE, SERIES_ID, SEASON_NUMBER, EPISODE_NUMBER
     * - EPISODE_ID (= stream ID for URL building)
     * - CONTAINER_EXT (when available)
     *
     * Technical metadata (videoCodec, audioCodec, bitrate, resolution) is extracted
     * by [VariantBuilder] from these hints into dedicated Variant fields — NOT
     * duplicated as extra hint keys. This matches [NxCatalogWriter]'s VOD pattern.
     */
    private fun buildEpisodePlaybackHints(episode: EpisodeIndexItem): Map<String, String> {
        return buildMap {
            put(PlaybackHintKeys.Xtream.CONTENT_TYPE, PlaybackHintKeys.Xtream.CONTENT_SERIES)
            put(PlaybackHintKeys.Xtream.SERIES_ID, episode.seriesId.toString())
            put(PlaybackHintKeys.Xtream.SEASON_NUMBER, episode.seasonNumber.toString())
            put(PlaybackHintKeys.Xtream.EPISODE_NUMBER, episode.episodeNumber.toString())
            episode.episodeId?.let {
                put(PlaybackHintKeys.Xtream.EPISODE_ID, it.toString())
            }
            // Container from pre-parsed hints JSON (when available)
            episode.playbackHintsJson?.let { json ->
                extractContainerFromHints(json)?.takeIf { it.isNotBlank() }?.let {
                    put(PlaybackHintKeys.Xtream.CONTAINER_EXT, it)
                }
            }
        }
    }

    /**
     * Build episode workKey using [NxKeyGenerator.episodeKey] — SSOT.
     *
     * Uses the series title from DB to match NxCatalogWriter's key format.
     * Falls back to episode title when series Work is unavailable.
     */
    private fun buildEpisodeWorkKey(
        episode: EpisodeIndexItem,
        seriesWork: NxWorkRepository.Work?,
    ): String {
        val title = seriesWork?.displayTitle
            ?: episode.title
            ?: "episode-${episode.episodeNumber}"
        val year = seriesWork?.year
        return NxKeyGenerator.episodeKey(
            seriesTitle = title,
            year = year,
            season = episode.seasonNumber,
            episode = episode.episodeNumber,
        )
    }

    private fun extractContainerFromHints(hintsJson: String): String? {
        val hints = PlaybackHintsDecoder.decodeJson(hintsJson) ?: return null
        return hints[PlaybackHintKeys.Xtream.CONTAINER_EXT]
    }
}
