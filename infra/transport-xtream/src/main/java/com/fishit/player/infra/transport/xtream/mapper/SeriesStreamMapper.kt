package com.fishit.player.infra.transport.xtream.mapper

import com.fishit.player.infra.transport.xtream.XtreamSeriesStream
import com.fishit.player.infra.transport.xtream.streaming.JsonObjectReader
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * SeriesStreamMapper - Maps JSON to XtreamSeriesStream.
 *
 * Extracted from DefaultXtreamApiClient to reduce duplication and CC.
 * Provides both streaming (Jackson) and fallback (kotlinx) mappers.
 *
 * CC: 2 per function (pure data mapping)
 */
object SeriesStreamMapper {
    /**
     * Streaming mapper for Jackson parser (O(1) memory).
     */
    fun fromStreaming(reader: JsonObjectReader): XtreamSeriesStream {
        return XtreamSeriesStream(
            num = reader.getIntOrNull("num"),
            name = reader.getStringOrNull("name"),
            seriesId = reader.getIntOrNull("series_id"),
            id = reader.getIntOrNull("id"),
            cover = reader.getStringOrNull("cover"),
            plot = reader.getStringOrNull("plot"),
            cast = reader.getStringOrNull("cast"),
            director = reader.getStringOrNull("director"),
            genre = reader.getStringOrNull("genre"),
            releaseDate = reader.getStringOrNull("releaseDate"),
            lastModified = reader.getStringOrNull("last_modified"),
            rating = reader.getStringOrNull("rating"),
            rating5Based = reader.getDoubleOrNull("rating_5based"),
            backdropPath = reader.getStringOrNull("backdrop_path"),
            youtubeTrailer = reader.getStringOrNull("youtube_trailer"),
            episodeRunTime = reader.getStringOrNull("episode_run_time"),
            categoryId = reader.getStringOrNull("category_id"),
            streamType = reader.getStringOrNull("stream_type"),
        )
    }

    /**
     * Fallback mapper for kotlinx.serialization (DOM parsing).
     */
    fun fromJsonObject(obj: JsonObject): XtreamSeriesStream {
        return XtreamSeriesStream(
            num = obj["num"]?.jsonPrimitive?.intOrNull,
            name = obj["name"]?.jsonPrimitive?.content,
            seriesId = obj["series_id"]?.jsonPrimitive?.intOrNull,
            id = obj["id"]?.jsonPrimitive?.intOrNull,
            cover = obj["cover"]?.jsonPrimitive?.content,
            plot = obj["plot"]?.jsonPrimitive?.content,
            cast = obj["cast"]?.jsonPrimitive?.content,
            director = obj["director"]?.jsonPrimitive?.content,
            genre = obj["genre"]?.jsonPrimitive?.content,
            releaseDate = obj["releaseDate"]?.jsonPrimitive?.content,
            lastModified = obj["last_modified"]?.jsonPrimitive?.content,
            rating = obj["rating"]?.jsonPrimitive?.content,
            rating5Based = obj["rating_5based"]?.jsonPrimitive?.doubleOrNull,
            backdropPath = obj["backdrop_path"]?.let { element ->
                when (element) {
                    is JsonPrimitive -> element.contentOrNull?.takeIf { it.isNotBlank() }
                    is JsonArray -> element.firstOrNull()?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
                    else -> null
                }
            },
            youtubeTrailer = obj["youtube_trailer"]?.jsonPrimitive?.content,
            episodeRunTime = obj["episode_run_time"]?.jsonPrimitive?.content,
            categoryId = obj["category_id"]?.jsonPrimitive?.content,
            streamType = obj["stream_type"]?.jsonPrimitive?.content,
        )
    }
}
