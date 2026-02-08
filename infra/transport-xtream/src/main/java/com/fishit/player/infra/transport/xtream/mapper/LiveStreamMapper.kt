package com.fishit.player.infra.transport.xtream.mapper

import com.fishit.player.infra.transport.xtream.XtreamLiveStream
import com.fishit.player.infra.transport.xtream.streaming.JsonObjectReader
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * LiveStreamMapper - Maps JSON to XtreamLiveStream.
 *
 * Extracted from DefaultXtreamApiClient to reduce duplication and CC.
 * Provides both streaming (Jackson) and fallback (kotlinx) mappers.
 *
 * CC: 2 per function (pure data mapping)
 */
object LiveStreamMapper {
    /**
     * Streaming mapper for Jackson parser (O(1) memory).
     */
    fun fromStreaming(reader: JsonObjectReader): XtreamLiveStream {
        return XtreamLiveStream(
            num = reader.getIntOrNull("num"),
            name = reader.getStringOrNull("name"),
            streamId = reader.getIntOrNull("stream_id"),
            id = reader.getIntOrNull("id"),
            streamIcon = reader.getStringOrNull("stream_icon"),
            logo = reader.getStringOrNull("logo"),
            epgChannelId = reader.getStringOrNull("epg_channel_id"),
            tvArchive = reader.getIntOrNull("tv_archive"),
            tvArchiveDuration = reader.getIntOrNull("tv_archive_duration"),
            categoryId = reader.getStringOrNull("category_id"),
            streamType = reader.getStringOrNull("stream_type"),
            added = reader.getStringOrNull("added"),
            isAdult = reader.getStringOrNull("is_adult"),
        )
    }

    /**
     * Fallback mapper for kotlinx.serialization (DOM parsing).
     */
    fun fromJsonObject(obj: JsonObject): XtreamLiveStream {
        return XtreamLiveStream(
            num = obj["num"]?.jsonPrimitive?.intOrNull,
            name = obj["name"]?.jsonPrimitive?.content,
            streamId = obj["stream_id"]?.jsonPrimitive?.intOrNull,
            id = obj["id"]?.jsonPrimitive?.intOrNull,
            streamIcon = obj["stream_icon"]?.jsonPrimitive?.content,
            logo = obj["logo"]?.jsonPrimitive?.content,
            epgChannelId = obj["epg_channel_id"]?.jsonPrimitive?.content,
            tvArchive = obj["tv_archive"]?.jsonPrimitive?.intOrNull,
            tvArchiveDuration = obj["tv_archive_duration"]?.jsonPrimitive?.intOrNull,
            categoryId = obj["category_id"]?.jsonPrimitive?.content,
            streamType = obj["stream_type"]?.jsonPrimitive?.content,
            added = obj["added"]?.jsonPrimitive?.content,
            isAdult = obj["is_adult"]?.jsonPrimitive?.content,
        )
    }
}
