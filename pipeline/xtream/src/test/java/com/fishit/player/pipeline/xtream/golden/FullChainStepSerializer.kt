package com.fishit.player.pipeline.xtream.golden

import com.fishit.player.core.model.NormalizedMediaMetadata
import com.fishit.player.core.model.RawMediaMetadata
import com.fishit.player.core.model.repository.NxWorkRepository
import com.fishit.player.core.model.repository.NxWorkSourceRefRepository
import com.fishit.player.core.model.repository.NxWorkVariantRepository
import com.fishit.player.core.model.toUriString
import com.fishit.player.pipeline.xtream.model.XtreamChannel
import com.fishit.player.pipeline.xtream.model.XtreamEpisode
import com.fishit.player.pipeline.xtream.model.XtreamSeriesItem
import com.fishit.player.pipeline.xtream.model.XtreamVodItem
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Serializes the COMPLETE chain output across ALL 5 steps into a single composite JSON.
 *
 * Each golden file captures the transformation at every step of the production chain:
 *
 * ```
 * step1_transport  → Raw API JSON (input, not serialized here — kept as-is)
 * step2_pipeline   → Pipeline DTO (XtreamVodItem, XtreamChannel, etc.)
 * step3_raw        → RawMediaMetadata (from toRawMetadata())
 * step4_normalized → NormalizedMediaMetadata (from RegexMediaMetadataNormalizer)
 * step5_nx         → NX entities (Work + SourceRef + Variant)
 * ```
 *
 * This serializer is test-only. It uses Kotlin property names (not @SerialName aliases)
 * so golden files show what the Kotlin runtime sees at each step.
 *
 * **Timestamps excluded** from comparison (non-deterministic in production).
 */
@OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
object FullChainStepSerializer {
    private val json =
        Json {
            prettyPrint = true
            prettyPrintIndent = "  "
        }

    // =========================================================================
    // Composite Chain Output
    // =========================================================================

    /**
     * Build the complete chain output JSON with all 5 steps.
     *
     * @param transportInput The raw JSON string from the API (verbatim, for reference)
     * @param pipeline The pipeline DTO as JsonElement
     * @param raw The RawMediaMetadata
     * @param normalized The NormalizedMediaMetadata
     * @param work The NX Work entity
     * @param sourceRef The NX SourceRef entity
     * @param variant The NX Variant entity (null if no playback hints)
     */
    fun toCompositeJson(
        transportInput: String,
        pipeline: JsonElement,
        raw: RawMediaMetadata,
        normalized: NormalizedMediaMetadata,
        work: NxWorkRepository.Work,
        sourceRef: NxWorkSourceRefRepository.SourceRef,
        variant: NxWorkVariantRepository.Variant?,
    ): JsonElement =
        buildJsonObject {
            // Step 1: Transport input is the raw JSON — parse it for pretty-printing
            put("step1_transport", json.parseToJsonElement(transportInput))

            // Step 2: Pipeline DTO
            put("step2_pipeline", pipeline)

            // Step 3: RawMediaMetadata
            put("step3_raw", RawMetadataJsonSerializer.toJsonElement(raw))

            // Step 4: NormalizedMediaMetadata
            put("step4_normalized", normalizedToJson(normalized))

            // Step 5: NX entities
            put(
                "step5_nx",
                buildJsonObject {
                    put("work", workToJson(work))
                    put("sourceRef", sourceRefToJson(sourceRef))
                    if (variant != null) {
                        put("variant", variantToJson(variant))
                    }
                },
            )
        }

    fun toJsonString(element: JsonElement): String = json.encodeToString(JsonElement.serializer(), element)

    fun parseGoldenFile(jsonString: String): JsonElement = json.parseToJsonElement(jsonString)

    // =========================================================================
    // Step 2: Pipeline DTO Serializers
    // =========================================================================

    fun vodItemToJson(dto: XtreamVodItem): JsonElement =
        buildJsonObject {
            put("id", dto.id)
            put("name", dto.name)
            putNullableString("streamIcon", dto.streamIcon)
            putNullableString("categoryId", dto.categoryId)
            putNullableString("containerExtension", dto.containerExtension)
            putNullableString("streamType", dto.streamType)
            putNullableLong("added", dto.added)
            putNullableDouble("rating", dto.rating)
            putNullableDouble("rating5Based", dto.rating5Based)
            putNullableInt("tmdbId", dto.tmdbId)
            putNullableString("year", dto.year)
            putNullableString("genre", dto.genre)
            putNullableString("plot", dto.plot)
            putNullableString("duration", dto.duration)
            put("isAdult", dto.isAdult)
            putNullableString("videoCodec", dto.videoCodec)
            putNullableInt("videoWidth", dto.videoWidth)
            putNullableInt("videoHeight", dto.videoHeight)
            putNullableString("audioCodec", dto.audioCodec)
            putNullableInt("audioChannels", dto.audioChannels)
        }

    fun seriesItemToJson(dto: XtreamSeriesItem): JsonElement =
        buildJsonObject {
            put("id", dto.id)
            put("name", dto.name)
            putNullableString("cover", dto.cover)
            putNullableString("backdrop", dto.backdrop)
            putNullableString("categoryId", dto.categoryId)
            putNullableString("streamType", dto.streamType)
            putNullableString("year", dto.year)
            putNullableDouble("rating", dto.rating)
            putNullableString("plot", dto.plot)
            putNullableString("cast", dto.cast)
            putNullableString("director", dto.director)
            putNullableString("genre", dto.genre)
            putNullableString("releaseDate", dto.releaseDate)
            putNullableString("youtubeTrailer", dto.youtubeTrailer)
            putNullableString("episodeRunTime", dto.episodeRunTime)
            putNullableLong("lastModified", dto.lastModified)
            putNullableInt("tmdbId", dto.tmdbId)
            put("isAdult", dto.isAdult)
        }

    fun channelToJson(dto: XtreamChannel): JsonElement =
        buildJsonObject {
            put("id", dto.id)
            put("name", dto.name)
            putNullableString("streamIcon", dto.streamIcon)
            putNullableString("epgChannelId", dto.epgChannelId)
            put("tvArchive", dto.tvArchive)
            put("tvArchiveDuration", dto.tvArchiveDuration)
            putNullableString("categoryId", dto.categoryId)
            putNullableString("streamType", dto.streamType)
            putNullableLong("added", dto.added)
            put("isAdult", dto.isAdult)
            putNullableString("directSource", dto.directSource)
        }

    fun episodeToJson(dto: XtreamEpisode): JsonElement =
        buildJsonObject {
            put("id", dto.id)
            put("seriesId", dto.seriesId)
            putNullableString("seriesName", dto.seriesName)
            put("seasonNumber", dto.seasonNumber)
            put("episodeNumber", dto.episodeNumber)
            put("title", dto.title)
            putNullableString("containerExtension", dto.containerExtension)
            putNullableString("plot", dto.plot)
            putNullableString("duration", dto.duration)
            putNullableInt("durationSecs", dto.durationSecs)
            putNullableString("releaseDate", dto.releaseDate)
            putNullableDouble("rating", dto.rating)
            putNullableString("thumbnail", dto.thumbnail)
            putNullableLong("added", dto.added)
            putNullableInt("bitrate", dto.bitrate)
            putNullableInt("seriesTmdbId", dto.seriesTmdbId)
            putNullableInt("episodeTmdbId", dto.episodeTmdbId)
            putNullableString("videoCodec", dto.videoCodec)
            putNullableInt("videoWidth", dto.videoWidth)
            putNullableInt("videoHeight", dto.videoHeight)
            putNullableString("audioCodec", dto.audioCodec)
            putNullableInt("audioChannels", dto.audioChannels)
        }

    // =========================================================================
    // Step 4: NormalizedMediaMetadata Serializer
    // =========================================================================

    fun normalizedToJson(n: NormalizedMediaMetadata): JsonElement =
        buildJsonObject {
            // Identity
            put("canonicalTitle", n.canonicalTitle)
            put("mediaType", n.mediaType.name)

            // Temporal
            putNullableInt("year", n.year)
            putNullableInt("season", n.season)
            putNullableInt("episode", n.episode)
            putNullableLong("durationMs", n.durationMs)
            putNullableLong("addedTimestamp", n.addedTimestamp)

            // Imaging
            putNullableString("poster", n.poster?.toUriString())
            putNullableString("backdrop", n.backdrop?.toUriString())
            putNullableString("thumbnail", n.thumbnail?.toUriString())
            putNullableString("placeholderThumbnail", n.placeholderThumbnail?.toUriString())

            // Rich metadata
            putNullableDouble("rating", n.rating)
            putNullableString("plot", n.plot)
            putNullableString("genres", n.genres)
            putNullableString("director", n.director)
            putNullableString("cast", n.cast)
            putNullableString("trailer", n.trailer)
            putNullableString("releaseDate", n.releaseDate)

            // External IDs
            put(
                "externalIds",
                buildJsonObject {
                    putNullableInt("tmdbId", n.externalIds.tmdb?.id)
                    putNullableString(
                        "tmdbType",
                        n.externalIds.tmdb
                            ?.type
                            ?.name,
                    )
                    putNullableString("imdbId", n.externalIds.imdbId)
                    putNullableString("tvdbId", n.externalIds.tvdbId)
                },
            )

            // TMDB ref (typed)
            if (n.tmdb != null) {
                put(
                    "tmdb",
                    buildJsonObject {
                        put("type", n.tmdb!!.type.name)
                        put("id", n.tmdb!!.id)
                    },
                )
            } else {
                put("tmdb", JsonNull)
            }

            // Classification
            put("isAdult", n.isAdult)
            putNullableString("categoryId", n.categoryId)

            // Live-specific
            putNullableString("epgChannelId", n.epgChannelId)
            put("tvArchive", n.tvArchive)
            put("tvArchiveDuration", n.tvArchiveDuration)
        }

    // =========================================================================
    // Step 5: NX Entity Serializers
    // =========================================================================

    private fun workToJson(work: NxWorkRepository.Work): JsonElement =
        buildJsonObject {
            // Identity
            put("workKey", work.workKey)
            put("type", work.type.name)
            put("displayTitle", work.displayTitle)
            put("sortTitle", work.sortTitle)
            put("titleNormalized", work.titleNormalized)

            // Content
            putNullableInt("year", work.year)
            putNullableInt("season", work.season)
            putNullableInt("episode", work.episode)
            putNullableLong("runtimeMs", work.runtimeMs)

            // Imaging
            putNullableString("poster", work.poster?.toUriString())
            putNullableString("backdrop", work.backdrop?.toUriString())
            putNullableString("thumbnail", work.thumbnail?.toUriString())

            // Rich metadata
            putNullableDouble("rating", work.rating)
            putNullableString("genres", work.genres)
            putNullableString("plot", work.plot)
            putNullableString("director", work.director)
            putNullableString("cast", work.cast)
            putNullableString("trailer", work.trailer)
            putNullableString("releaseDate", work.releaseDate)

            // External IDs
            putNullableString("tmdbId", work.tmdbId)
            putNullableString("imdbId", work.imdbId)
            putNullableString("tvdbId", work.tvdbId)

            // Classification
            put("isAdult", work.isAdult)
            put("recognitionState", work.recognitionState.name)
            put("isDeleted", work.isDeleted)
        }

    private fun sourceRefToJson(ref: NxWorkSourceRefRepository.SourceRef): JsonElement =
        buildJsonObject {
            // Keys
            put("sourceKey", ref.sourceKey)
            put("workKey", ref.workKey)

            // Source identity
            put("sourceType", ref.sourceType.name)
            put("accountKey", ref.accountKey)
            put("sourceItemKind", ref.sourceItemKind.name)
            put("sourceItemKey", ref.sourceItemKey)
            putNullableString("sourceTitle", ref.sourceTitle)

            // Availability
            put("availability", ref.availability.name)
            putNullableString("note", ref.note)

            // Source timing
            putNullableLong("sourceLastModifiedMs", ref.sourceLastModifiedMs)

            // Live-specific fields
            putNullableString("epgChannelId", ref.epgChannelId)
            put("tvArchive", ref.tvArchive)
            put("tvArchiveDuration", ref.tvArchiveDuration)
        }

    private fun variantToJson(v: NxWorkVariantRepository.Variant): JsonElement =
        buildJsonObject {
            // Keys
            put("variantKey", v.variantKey)
            put("workKey", v.workKey)
            put("sourceKey", v.sourceKey)

            // Properties
            putNullableString("label", v.label)
            put("isDefault", v.isDefault)
            putNullableString("container", v.container)
            putNullableLong("durationMs", v.durationMs)

            // Quality info
            putNullableInt("qualityHeight", v.qualityHeight)
            putNullableInt("bitrateKbps", v.bitrateKbps)
            putNullableString("videoCodec", v.videoCodec)
            putNullableString("audioCodec", v.audioCodec)
            putNullableString("audioLang", v.audioLang)

            // Playback hints (sorted for determinism)
            put(
                "playbackHints",
                buildJsonObject {
                    v.playbackHints.toSortedMap().forEach { (k, value) ->
                        put(k, value)
                    }
                },
            )
        }

    // =========================================================================
    // Nullable helpers
    // =========================================================================

    private fun kotlinx.serialization.json.JsonObjectBuilder.putNullableString(
        key: String,
        value: String?,
    ) {
        if (value != null) put(key, value) else put(key, JsonNull)
    }

    private fun kotlinx.serialization.json.JsonObjectBuilder.putNullableInt(
        key: String,
        value: Int?,
    ) {
        if (value != null) put(key, value) else put(key, JsonNull)
    }

    private fun kotlinx.serialization.json.JsonObjectBuilder.putNullableLong(
        key: String,
        value: Long?,
    ) {
        if (value != null) put(key, value) else put(key, JsonNull)
    }

    private fun kotlinx.serialization.json.JsonObjectBuilder.putNullableDouble(
        key: String,
        value: Double?,
    ) {
        if (value != null) put(key, JsonPrimitive(value)) else put(key, JsonNull)
    }
}
