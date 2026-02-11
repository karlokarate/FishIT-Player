package com.fishit.player.infra.data.nx.golden

import com.fishit.player.core.model.ImageRef
import com.fishit.player.core.model.repository.NxWorkRepository
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Converts [NxWorkRepository.Work] to a deterministic JSON representation for golden file testing.
 *
 * **Design decisions:**
 * - All fields included (null shown as explicit `null`) — catches both directions of regression
 * - Enum values stored as `.name` for human readability
 * - This tests the WorkEntityBuilder output (NormalizedMediaMetadata → Work)
 *
 * This class is test-only and does NOT modify production code.
 */
@OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
object WorkJsonSerializer {

    private val json = Json {
        prettyPrint = true
        prettyPrintIndent = "  "
    }

    /**
     * Convert a [NxWorkRepository.Work] to a [JsonElement] for structural comparison.
     */
    fun toJsonElement(work: NxWorkRepository.Work): JsonElement = buildJsonObject {
        // === Identity ===
        put("workKey", work.workKey)
        put("type", work.type.name)
        put("displayTitle", work.displayTitle)
        put("sortTitle", work.sortTitle)
        put("titleNormalized", work.titleNormalized)

        // === Content ===
        putNullableInt("year", work.year)
        putNullableInt("season", work.season)
        putNullableInt("episode", work.episode)
        putNullableLong("runtimeMs", work.runtimeMs)

        // === Imaging (serialized strings) ===
        putNullableString("poster", work.poster.toSerializedString())
        putNullableString("backdrop", work.backdrop.toSerializedString())
        putNullableString("thumbnail", work.thumbnail.toSerializedString())

        // === Rich Metadata ===
        putNullableDouble("rating", work.rating)
        putNullableString("genres", work.genres)
        putNullableString("plot", work.plot)
        putNullableString("director", work.director)
        putNullableString("cast", work.cast)
        putNullableString("trailer", work.trailer)
        putNullableString("releaseDate", work.releaseDate)

        // === External IDs ===
        putNullableString("tmdbId", work.tmdbId)
        putNullableString("imdbId", work.imdbId)
        putNullableString("tvdbId", work.tvdbId)

        // === Classification ===
        put("isAdult", work.isAdult)
        put("recognitionState", work.recognitionState.name)
        put("isDeleted", work.isDeleted)

        // === Timestamps (excluded from golden comparison — non-deterministic) ===
        // createdAtMs and updatedAtMs are NOT included because they depend on
        // System.currentTimeMillis() when addedTimestamp is null. Tests that care
        // about timestamp behavior should assert directly.
    }

    /**
     * Convert a [NxWorkRepository.Work] to a pretty-printed JSON string.
     */
    fun toJsonString(work: NxWorkRepository.Work): String =
        json.encodeToString(JsonElement.serializer(), toJsonElement(work))

    /**
     * Parse a golden file JSON string back to a [JsonElement] for comparison.
     */
    fun parseGoldenFile(jsonString: String): JsonElement = json.parseToJsonElement(jsonString)

    // === Builder helpers for nullable primitive types ===

    private fun JsonObjectBuilderScope.putNullableString(key: String, value: String?) {
        if (value != null) put(key, value) else put(key, JsonNull)
    }

    private fun JsonObjectBuilderScope.putNullableInt(key: String, value: Int?) {
        if (value != null) put(key, value) else put(key, JsonNull)
    }

    private fun JsonObjectBuilderScope.putNullableLong(key: String, value: Long?) {
        if (value != null) put(key, value) else put(key, JsonNull)
    }

    private fun JsonObjectBuilderScope.putNullableDouble(key: String, value: Double?) {
        if (value != null) put(key, JsonPrimitive(value)) else put(key, JsonNull)
    }
}

private typealias JsonObjectBuilderScope = kotlinx.serialization.json.JsonObjectBuilder

/**
 * Convert ImageRef to a serialized string for golden file comparison.
 * Http → URL string, Telegram → "telegram:{remoteId}", null → null.
 */
private fun ImageRef?.toSerializedString(): String? = when (this) {
    is ImageRef.Http -> url
    is ImageRef.TelegramThumb -> "tg:${remoteId}"
    is ImageRef.LocalFile -> "file:${path}"
    is ImageRef.InlineBytes -> "inline:${bytes.size}bytes"
    null -> null
}
