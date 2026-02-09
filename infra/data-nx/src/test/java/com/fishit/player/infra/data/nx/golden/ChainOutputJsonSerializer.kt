package com.fishit.player.infra.data.nx.golden

import com.fishit.player.core.model.repository.NxWorkRepository
import com.fishit.player.core.model.repository.NxWorkSourceRefRepository
import com.fishit.player.core.model.repository.NxWorkVariantRepository
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Serializes the full-chain output (Work + SourceRef + Variant) to deterministic JSON
 * for golden file comparison.
 *
 * Covers the complete NxCatalogWriter.ingest() output in a single snapshot:
 * - `work`: The NX_Work entity (from WorkEntityBuilder)
 * - `sourceRef`: The NX_WorkSourceRef entity (from SourceRefBuilder)
 * - `variant`: The NX_WorkVariant entity (from VariantBuilder), or null if no hints
 *
 * **Timestamps excluded** from comparison (non-deterministic):
 * createdAtMs, updatedAtMs, firstSeenAtMs, lastSeenAtMs in all entities.
 */
@OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
object ChainOutputJsonSerializer {

    private val json = Json {
        prettyPrint = true
        prettyPrintIndent = "  "
    }

    /**
     * Serialize the full chain output to a composite JSON object.
     */
    fun toJsonElement(
        work: NxWorkRepository.Work,
        sourceRef: NxWorkSourceRefRepository.SourceRef,
        variant: NxWorkVariantRepository.Variant?,
    ): JsonElement = buildJsonObject {
        put("work", workToJson(work))
        put("sourceRef", sourceRefToJson(sourceRef))
        if (variant != null) {
            put("variant", variantToJson(variant))
        }
    }

    fun toJsonString(
        work: NxWorkRepository.Work,
        sourceRef: NxWorkSourceRefRepository.SourceRef,
        variant: NxWorkVariantRepository.Variant?,
    ): String = json.encodeToString(JsonElement.serializer(), toJsonElement(work, sourceRef, variant))

    fun parseGoldenFile(jsonString: String): JsonElement = json.parseToJsonElement(jsonString)

    // =========================================================================
    // Work serialization (same fields as WorkJsonSerializer, timestamps excluded)
    // =========================================================================

    private fun workToJson(work: NxWorkRepository.Work): JsonElement = buildJsonObject {
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
        putNullableString("posterRef", work.posterRef)
        putNullableString("backdropRef", work.backdropRef)
        putNullableString("thumbnailRef", work.thumbnailRef)

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

    // =========================================================================
    // SourceRef serialization (timestamps excluded)
    // =========================================================================

    private fun sourceRefToJson(ref: NxWorkSourceRefRepository.SourceRef): JsonElement = buildJsonObject {
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

    // =========================================================================
    // Variant serialization (timestamps excluded)
    // =========================================================================

    private fun variantToJson(v: NxWorkVariantRepository.Variant): JsonElement = buildJsonObject {
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

        // Playback hints (sorted alphabetically for determinism)
        put("playbackHints", buildJsonObject {
            v.playbackHints.toSortedMap().forEach { (k, value) ->
                put(k, value)
            }
        })
    }

    // =========================================================================
    // Nullable helpers
    // =========================================================================

    private fun kotlinx.serialization.json.JsonObjectBuilder.putNullableString(key: String, value: String?) {
        if (value != null) put(key, value) else put(key, JsonNull)
    }

    private fun kotlinx.serialization.json.JsonObjectBuilder.putNullableInt(key: String, value: Int?) {
        if (value != null) put(key, value) else put(key, JsonNull)
    }

    private fun kotlinx.serialization.json.JsonObjectBuilder.putNullableLong(key: String, value: Long?) {
        if (value != null) put(key, value) else put(key, JsonNull)
    }

    private fun kotlinx.serialization.json.JsonObjectBuilder.putNullableDouble(key: String, value: Double?) {
        if (value != null) put(key, JsonPrimitive(value)) else put(key, JsonNull)
    }
}
