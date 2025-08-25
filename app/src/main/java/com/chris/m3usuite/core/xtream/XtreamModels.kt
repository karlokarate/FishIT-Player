package com.chris.m3usuite.core.xtream

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class XtServerInfo(
    @SerialName("url") val url: String? = null,
    @SerialName("port") val port: String? = null,
    @SerialName("server_protocol") val serverProtocol: String? = null,
)

@Serializable
data class XtUserInfo(
    @SerialName("auth") val auth: Int? = null,
    @SerialName("status") val status: String? = null
)

@Serializable
data class XtHandshake(
    @SerialName("user_info") val userInfo: XtUserInfo? = null,
    @SerialName("server_info") val serverInfo: XtServerInfo? = null
)

@Serializable
data class XtCategory(
    @SerialName("category_id") val categoryId: String,
    @SerialName("category_name") val categoryName: String
)

@Serializable
data class XtLiveStream(
    @SerialName("name") val name: String,
    @SerialName("stream_id") val streamId: Int,
    @SerialName("epg_channel_id") val epgChannelId: String? = null,
    @SerialName("stream_icon") val streamIcon: String? = null,
    @SerialName("category_id") val categoryId: String? = null
)

@Serializable
data class XtVodStream(
    @SerialName("name") val name: String,
    @SerialName("stream_id") val streamId: Int,
    @SerialName("category_id") val categoryId: String? = null,
    @SerialName("stream_icon") val streamIcon: String? = null,
    @SerialName("rating") val rating: String? = null,
    @SerialName("year") val year: String? = null,
    @SerialName("container_extension") val containerExtension: String? = null
)

@Serializable
data class XtVodInfo(@SerialName("info") val info: XtVodInfoDetails? = null)
@Serializable
data class XtVodInfoDetails(
    @SerialName("movie_image") val movieImage: String? = null,
    @SerialName("backdrop_path") val backdropPath: List<String>? = null,
    @SerialName("plot") val plot: String? = null,
    @SerialName("duration_secs") val durationSecs: Int? = null,
    @SerialName("rating") val rating: String? = null
)

@Serializable
data class XtSeries(
    @SerialName("name") val name: String,
    @SerialName("series_id") val seriesId: Int,
    @SerialName("cover") val cover: String? = null,
    @SerialName("plot") val plot: String? = null,
    @SerialName("category_id") val categoryId: String? = null
)

@Serializable
data class XtSeriesInfo(
    @SerialName("info") val info: XtSeriesInfoDetails? = null,
    @SerialName("episodes") val episodes: Map<String, List<XtEpisode>>? = null
)
@Serializable
data class XtSeriesInfoDetails(
    @SerialName("cover") val cover: String? = null,
    @SerialName("backdrop_path") val backdropPath: List<String>? = null,
    @SerialName("plot") val plot: String? = null,
    @SerialName("rating") val rating: String? = null
)
@Serializable
data class XtEpisode(
    @SerialName("id") val id: Int,
    @SerialName("title") val title: String? = null,
    @SerialName("episode_num") val episodeNum: Int? = null,
    @SerialName("season") val season: Int? = null,
    @SerialName("container_extension") val containerExtension: String? = null,
    @SerialName("info") val info: XtEpisodeInfo? = null
)
@Serializable
data class XtEpisodeInfo(
    @SerialName("movie_image") val movieImage: String? = null,
    @SerialName("plot") val plot: String? = null,
    @SerialName("duration_secs") val durationSecs: Int? = null
)

@Serializable
data class XtShortEPGProgramme(
    @SerialName("title") val title: String? = null,
    @SerialName("start") val start: String? = null,
    @SerialName("end") val end: String? = null,
    @SerialName("description") val description: String? = null
)
