package com.fishit.player.infra.transport.xtream.mapper

import com.fishit.player.infra.transport.xtream.XtreamVodStream
import com.fishit.player.infra.transport.xtream.streaming.JsonObjectReader
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * VodStreamMapper - Maps JSON to XtreamVodStream.
 *
 * Extracted from DefaultXtreamApiClient to reduce duplication and CC.
 * Provides both streaming (Jackson) and fallback (kotlinx) mappers.
 *
 * CC: 2 per function (pure data mapping)
 */
object VodStreamMapper {
    /**
     * Streaming mapper for Jackson parser (O(1) memory).
     */
    fun fromStreaming(reader: JsonObjectReader): XtreamVodStream {
        return XtreamVodStream(
            num = reader.getIntOrNull("num"),
            name = reader.getStringOrNull("name"),
            vodId = reader.getIntOrNull("vod_id"),
            movieId = reader.getIntOrNull("movie_id"),
            streamId = reader.getIntOrNull("stream_id"),
            id = reader.getIntOrNull("id"),
            streamIcon = reader.getStringOrNull("stream_icon"),
            posterPath = reader.getStringOrNull("poster_path"),
            cover = reader.getStringOrNull("cover"),
            logo = reader.getStringOrNull("logo"),
            categoryId = reader.getStringOrNull("category_id"),
            containerExtension = reader.getStringOrNull("container_extension"),
            streamType = reader.getStringOrNull("stream_type"),
            added = reader.getStringOrNull("added"),
            rating = reader.getStringOrNull("rating"),
            rating5Based = reader.getDoubleOrNull("rating_5based"),
            isAdult = reader.getStringOrNull("is_adult"),
            year = reader.getStringOrNull("year"),
            genre = reader.getStringOrNull("genre"),
            plot = reader.getStringOrNull("plot"),
            duration = reader.getStringOrNull("duration"),
        )
    }

    /**
     * Fallback mapper for kotlinx.serialization (DOM parsing).
     */
    fun fromJsonObject(obj: JsonObject): XtreamVodStream {
        return XtreamVodStream(
            num = obj["num"]?.jsonPrimitive?.intOrNull,
            name = obj["name"]?.jsonPrimitive?.content,
            vodId = obj["vod_id"]?.jsonPrimitive?.intOrNull,
            movieId = obj["movie_id"]?.jsonPrimitive?.intOrNull,
            streamId = obj["stream_id"]?.jsonPrimitive?.intOrNull,
            id = obj["id"]?.jsonPrimitive?.intOrNull,
            streamIcon = obj["stream_icon"]?.jsonPrimitive?.content,
            posterPath = obj["poster_path"]?.jsonPrimitive?.content,
            cover = obj["cover"]?.jsonPrimitive?.content,
            logo = obj["logo"]?.jsonPrimitive?.content,
            categoryId = obj["category_id"]?.jsonPrimitive?.content,
            containerExtension = obj["container_extension"]?.jsonPrimitive?.content,
            streamType = obj["stream_type"]?.jsonPrimitive?.content,
            added = obj["added"]?.jsonPrimitive?.content,
            rating = obj["rating"]?.jsonPrimitive?.content,
            rating5Based = obj["rating_5based"]?.jsonPrimitive?.doubleOrNull,
            isAdult = obj["is_adult"]?.jsonPrimitive?.content,
            year = obj["year"]?.jsonPrimitive?.content,
            genre = obj["genre"]?.jsonPrimitive?.content,
            plot = obj["plot"]?.jsonPrimitive?.content,
            duration = obj["duration"]?.jsonPrimitive?.content,
        )
    }
}
