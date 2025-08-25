package com.chris.m3usuite.core.xtream

import android.content.Context
import com.chris.m3usuite.core.http.HttpClientFactory
import com.chris.m3usuite.prefs.SettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import okhttp3.Request
import java.net.URLEncoder

class XtreamClient(
    private val context: Context,
    private val settings: SettingsStore,
    private val config: XtreamConfig
) {
    private val json = Json { ignoreUnknownKeys = true }

    private suspend fun get(q: String): String = withContext(Dispatchers.IO) {
        val client = HttpClientFactory.create(context, settings)
        val url = "${config.baseQuery}&$q"
        val req = Request.Builder().url(url).build()
        client.newCall(req).execute().use { res ->
            if (!res.isSuccessful) error("HTTP ${res.code}")
            res.body?.string() ?: ""
        }
    }

    suspend fun handshake(): XtHandshake {
        val s = get("action=user&password=${enc(config.password)}&username=${enc(config.username)}")
        return json.decodeFromString(XtHandshake.serializer(), s)
    }

    suspend fun liveCategories() = json.decodeFromString(
        ListSerializer(XtCategory.serializer()),
        get("action=get_live_categories")
    )

    suspend fun liveStreams() = json.decodeFromString(
        ListSerializer(XtLiveStream.serializer()),
        get("action=get_live_streams")
    )

    suspend fun vodCategories() = json.decodeFromString(
        ListSerializer(XtCategory.serializer()),
        get("action=get_vod_categories")
    )

    suspend fun vodStreams() = json.decodeFromString(
        ListSerializer(XtVodStream.serializer()),
        get("action=get_vod_streams")
    )

    suspend fun vodInfo(vodId: Int) =
        json.decodeFromString(XtVodInfo.serializer(), get("action=get_vod_info&vod_id=$vodId"))

    suspend fun seriesCategories() = json.decodeFromString(
        ListSerializer(XtCategory.serializer()),
        get("action=get_series_categories")
    )

    suspend fun seriesList() = json.decodeFromString(
        ListSerializer(XtSeries.serializer()),
        get("action=get_series")
    )

    suspend fun seriesInfo(seriesId: Int) =
        json.decodeFromString(XtSeriesInfo.serializer(), get("action=get_series_info&series_id=$seriesId"))

    suspend fun shortEPG(streamId: Int, limit: Int = 2) = runCatching {
        json.decodeFromString(
            ListSerializer(XtShortEPGProgramme.serializer()),
            get("action=get_short_epg&stream_id=$streamId&limit=$limit")
        )
    }.getOrDefault(emptyList())

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")
}
