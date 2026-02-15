package com.fishit.player.pipeline.xtream.golden

import com.fishit.player.core.model.RawMediaMetadata
import com.fishit.player.core.model.toUriString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Converts [RawMediaMetadata] to a deterministic JSON representation for golden file testing.
 *
 * **Design decisions:**
 * - All fields included (null shown as explicit `null`) — catches both directions of regression
 * - ImageRef fields stored via [toUriString] for human-readable output
 * - PlaybackHints keys sorted alphabetically for stable output
 * - ExternalIds as nested object with effectiveTmdbId for convenient assertion
 *
 * This class is test-only and does NOT modify production code.
 */
@OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
object RawMetadataJsonSerializer {
    private val json =
        Json {
            prettyPrint = true
            prettyPrintIndent = "  "
        }

    /**
     * Convert a [RawMediaMetadata] to a [JsonElement] for structural comparison.
     */
    fun toJsonElement(raw: RawMediaMetadata): JsonElement =
        buildJsonObject {
            // === Identity ===
            put("sourceId", raw.sourceId)
            put("globalId", raw.globalId)
            put("sourceType", raw.sourceType.name)
            put("mediaType", raw.mediaType.name)
            put("pipelineIdTag", raw.pipelineIdTag.name)

            // === Display ===
            put("originalTitle", raw.originalTitle)

            // === Temporal ===
            putNullableInt("year", raw.year)
            putNullableInt("season", raw.season)
            putNullableInt("episode", raw.episode)
            putNullableLong("durationMs", raw.durationMs)
            putNullableLong("addedTimestamp", raw.addedTimestamp)
            putNullableLong("lastModifiedTimestamp", raw.lastModifiedTimestamp)

            // === Imaging ===
            putNullableString("poster", raw.poster?.toUriString())
            putNullableString("backdrop", raw.backdrop?.toUriString())
            putNullableString("thumbnail", raw.thumbnail?.toUriString())
            putNullableString("placeholderThumbnail", raw.placeholderThumbnail?.toUriString())

            // === Rating ===
            putNullableDouble("rating", raw.rating)
            putNullableInt("ageRating", raw.ageRating)

            // === Rich Metadata ===
            putNullableString("plot", raw.plot)
            putNullableString("genres", raw.genres)
            putNullableString("director", raw.director)
            putNullableString("cast", raw.cast)
            putNullableString("trailer", raw.trailer)
            putNullableString("releaseDate", raw.releaseDate)

            // === Content Classification ===
            put("isAdult", raw.isAdult)
            putNullableString("categoryId", raw.categoryId)
            put("sourceLabel", raw.sourceLabel)

            // === Live Channel ===
            putNullableString("epgChannelId", raw.epgChannelId)
            put("tvArchive", raw.tvArchive)
            put("tvArchiveDuration", raw.tvArchiveDuration)

            // === External IDs ===
            @Suppress("DEPRECATION")
            put(
                "externalIds",
                buildJsonObject {
                    putNullableInt("effectiveTmdbId", raw.externalIds.effectiveTmdbId)
                    putNullableString(
                        "tmdbType",
                        raw.externalIds.tmdb
                            ?.type
                            ?.name,
                    )
                    putNullableString("imdbId", raw.externalIds.imdbId)
                    putNullableString("tvdbId", raw.externalIds.tvdbId)
                },
            )

            // === Playback Hints (sorted for deterministic output) ===
            put(
                "playbackHints",
                buildJsonObject {
                    raw.playbackHints.toSortedMap().forEach { (key, value) ->
                        put(key, value)
                    }
                },
            )
        }

    /**
     * Convert a [RawMediaMetadata] to a pretty-printed JSON string.
     */
    fun toJsonString(raw: RawMediaMetadata): String = json.encodeToString(JsonElement.serializer(), toJsonElement(raw))

    /**
     * Parse a golden file JSON string back to a [JsonElement] for comparison.
     */
    fun parseGoldenFile(jsonString: String): JsonElement = json.parseToJsonElement(jsonString)

    // === Builder helpers for nullable primitive types ===

    private fun JsonObjectBuilderScope.putNullableString(
        key: String,
        value: String?,
    ) {
        if (value != null) put(key, value) else put(key, JsonNull)
    }

    private fun JsonObjectBuilderScope.putNullableInt(
        key: String,
        value: Int?,
    ) {
        if (value != null) put(key, value) else put(key, JsonNull)
    }

    private fun JsonObjectBuilderScope.putNullableLong(
        key: String,
        value: Long?,
    ) {
        if (value != null) put(key, value) else put(key, JsonNull)
    }

    private fun JsonObjectBuilderScope.putNullableDouble(
        key: String,
        value: Double?,
    ) {
        if (value != null) put(key, JsonPrimitive(value)) else put(key, JsonNull)
    }
}

/**
 * Type alias to keep the builder scope readable. Not strictly needed but documents intent.
 */
private typealias JsonObjectBuilderScope = kotlinx.serialization.json.JsonObjectBuilder
