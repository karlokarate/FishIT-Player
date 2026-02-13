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
 * Uses the SAME key formats as [NxCatalogWriter]:
 * - sourceKey: `src:{sourceType}:{identifier}:{sourceId}` (via [SourceKeyParser])
 * - variantKey: `{sourceKey}#original`
 * - workKey: via [NxKeyGenerator]
 *
 * ## Call Order
 * For series detail: [NxEnrichmentWriter] enriches the series NX_Work first,
 * then NxDetailWriter creates episode NX_Works.
 *
 * @see NxCatalogWriter for sync-time writing
 * @see NxEnrichmentWriter for detail-time enrichment of existing works
 * @see com.fishit.player.infra.data.nx.xtream.NxXtreamSeriesIndexRepository
 */
package com.fishit.player.infra.data.nx.writer

import com.fishit.player.core.detail.domain.EpisodeIndexItem
import com.fishit.player.core.model.ImageRef
import com.fishit.player.core.model.PlaybackHintKeys
import com.fishit.player.core.model.repository.NxWorkRelationRepository
import com.fishit.player.core.model.repository.NxWorkRelationRepository.RelationType
import com.fishit.player.core.model.repository.NxWorkRepository
import com.fishit.player.core.model.repository.NxWorkRepository.WorkType
import com.fishit.player.core.model.repository.NxWorkSourceRefRepository
import com.fishit.player.core.model.repository.NxWorkSourceRefRepository.SourceItemKind
import com.fishit.player.core.model.repository.NxWorkSourceRefRepository.SourceType
import com.fishit.player.core.model.repository.NxWorkVariantRepository
import com.fishit.player.core.persistence.obx.NxKeyGenerator
import com.fishit.player.infra.data.nx.mapper.SourceKeyParser
import com.fishit.player.infra.data.nx.mapper.base.PlaybackHintsDecoder
import com.fishit.player.infra.data.nx.writer.builder.VariantBuilder
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
    private val variantBuilder: VariantBuilder,
) {
    /**
     * Build NX entities for a batch of episodes from the Xtream Series Detail API.
     *
     * Uses SSOT key formats consistent with [NxCatalogWriter]:
     * - sourceKey: `src:xtream:{identifier}:episode:series:{id}:s{S}:e{E}`
     * - variantKey: `{sourceKey}#original`
     * - accountKey: Inherited from parent series' source ref
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
            val episodeWorkKey = buildEpisodeWorkKey(episode, seriesWork)

            // ── sourceKey: SSOT format via SourceKeyParser ──────────────
            val cleanSourceId = episode.sourceKey.removePrefix("xtream:")
            val sourceKey = SourceKeyParser.buildSourceKey(
                sourceType = com.fishit.player.core.model.SourceType.XTREAM,
                accountKey = identifier,
                sourceId = cleanSourceId,
            )

            // ── NX_Work ────────────────────────────────────────────────
            works += NxWorkRepository.Work(
                workKey = episodeWorkKey,
                type = WorkType.EPISODE,
                displayTitle = episode.title ?: "Episode ${episode.episodeNumber}",
                season = episode.seasonNumber,
                episode = episode.episodeNumber,
                year = null,
                runtimeMs = episode.durationSecs?.let { it.toLong() * 1000 },
                poster = ImageRef.fromString(episode.thumbUrl),
                backdrop = ImageRef.fromString(episode.coverUrl),
                thumbnail = ImageRef.fromString(episode.thumbnailUrl),
                rating = episode.rating,
                plot = episode.plot,
                releaseDate = episode.airDate,
                tmdbId = episode.tmdbId?.toString(),
                createdAtMs = episode.addedTimestamp?.toLongOrNull() ?: 0L,
                updatedAtMs = episode.lastUpdatedMs,
            )

            // ── NX_WorkSourceRef ───────────────────────────────────────
            sourceRefs += NxWorkSourceRefRepository.SourceRef(
                sourceKey = sourceKey,
                workKey = episodeWorkKey,
                sourceType = SourceType.XTREAM,
                accountKey = accountKey,
                sourceItemKind = SourceItemKind.EPISODE,
                sourceItemKey = "series:${episode.seriesId}:s${episode.seasonNumber}:e${episode.episodeNumber}",
                sourceTitle = episode.title,
                lastSeenAtMs = episode.lastUpdatedMs,
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

            // ── NX_WorkVariant (via VariantBuilder SSOT) ───────────────
            val hintsJson = episode.playbackHintsJson
            val hasVariantData = !hintsJson.isNullOrEmpty() ||
                episode.videoCodec != null || episode.audioCodec != null || episode.bitrateKbps != null

            if (hasVariantData) {
                val variantKey = "$sourceKey#original"

                // Build COMPLETE playbackHints map with ALL metadata.
                // VariantBuilder extracts explicit variant fields FROM this map,
                // ensuring consistency with NxCatalogWriter's pipeline-built hints.
                val playbackUrl = hintsJson?.let { extractPlaybackUrlFromHints(it) }?.ifEmpty { null }
                val containerExt = hintsJson?.let { extractContainerFromHints(it) }?.ifEmpty { null }

                val playbackHints = buildMap<String, String> {
                    // ── Xtream identification (required for playback URL building) ──
                    put(PlaybackHintKeys.Xtream.CONTENT_TYPE, PlaybackHintKeys.Xtream.CONTENT_SERIES)
                    put(PlaybackHintKeys.Xtream.SERIES_ID, episode.seriesId.toString())
                    put(PlaybackHintKeys.Xtream.SEASON_NUMBER, episode.seasonNumber.toString())
                    put(PlaybackHintKeys.Xtream.EPISODE_NUMBER, episode.episodeNumber.toString())
                    episode.episodeId?.let {
                        put(PlaybackHintKeys.Xtream.EPISODE_ID, it.toString())
                        put(PlaybackHintKeys.Xtream.STREAM_ID, it.toString())
                    }
                    // ── Playback URLs & container ──
                    playbackUrl?.let { put(PlaybackHintKeys.Xtream.DIRECT_SOURCE, it) }
                    containerExt?.let { put(PlaybackHintKeys.Xtream.CONTAINER_EXT, it) }
                    // ── Technical metadata (also used by VariantBuilder) ──
                    episode.videoHeight?.let { put(PlaybackHintKeys.VIDEO_HEIGHT, it.toString()) }
                    episode.videoWidth?.let { put(PlaybackHintKeys.VIDEO_WIDTH, it.toString()) }
                    episode.videoCodec?.let { put(PlaybackHintKeys.VIDEO_CODEC, it) }
                    episode.audioCodec?.let { put(PlaybackHintKeys.AUDIO_CODEC, it) }
                    episode.bitrateKbps?.let { put(PlaybackHintKeys.Xtream.BITRATE, it.toString()) }
                    episode.videoAspectRatio?.let { put(PlaybackHintKeys.VIDEO_ASPECT_RATIO, it) }
                    episode.audioChannels?.let { put(PlaybackHintKeys.AUDIO_CHANNELS, it.toString()) }
                    episode.audioLanguage?.let { put(PlaybackHintKeys.AUDIO_LANGUAGE, it) }
                    episode.durationString?.let { put(PlaybackHintKeys.DURATION_DISPLAY, it) }
                    episode.customSid?.let { put(PlaybackHintKeys.Xtream.CUSTOM_SID, it) }
                }

                // Delegate to VariantBuilder — SSOT for variant entity construction
                val durationMs = episode.durationSecs?.let { it.toLong() * 1000 }
                variants += variantBuilder.build(
                    variantKey = variantKey,
                    workKey = episodeWorkKey,
                    sourceKey = sourceKey,
                    playbackHints = playbackHints,
                    durationMs = durationMs,
                    now = episode.lastUpdatedMs,
                )
            }
        }

        return EpisodeDetailResult(works, sourceRefs, relations, variants)
    }

    // =========================================================================
    // Private Helpers
    // =========================================================================

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

    private fun extractPlaybackUrlFromHints(hintsJson: String): String? {
        val hints = PlaybackHintsDecoder.decodeJson(hintsJson) ?: return null
        return hints[PlaybackHintKeys.Xtream.DIRECT_SOURCE]
    }

    private fun extractContainerFromHints(hintsJson: String): String? {
        val hints = PlaybackHintsDecoder.decodeJson(hintsJson) ?: return null
        return hints[PlaybackHintKeys.Xtream.CONTAINER_EXT]
    }
}
