package com.chris.m3usuite.core.xtream

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// =====================================================
// XtreamModels – FINAL (drop-in kompatibel)
//  - Rohmodelle für direkte Deserialisierung der Player-API
//  - Normalisierte Modelle für UI/DB (images/trailer etc.)
// =====================================================

// -----------------------------
// RAW (wie von Xtream-Varianten geliefert)
// -----------------------------

@Serializable
data class RawCategory(
    val category_id: String? = null,
    val category_name: String? = null,
)

@Serializable
data class RawLiveStream(
    val num: Int? = null,
    val name: String? = null,
    val stream_id: Int? = null,
    val stream_icon: String? = null,
    val epg_channel_id: String? = null,
    val tv_archive: Int? = null,
    val category_id: String? = null,
)

@Serializable
data class RawVod(
    val num: Int? = null,
    val name: String? = null,
    val vod_id: Int? = null,
    val stream_icon: String? = null,
    val category_id: String? = null,
)

@Serializable
data class RawSeries(
    val num: Int? = null,
    val name: String? = null,
    val series_id: Int? = null,
    val cover: String? = null,
    val category_id: String? = null,
)

@Serializable
data class RawVodInfo(
    @SerialName("movie_data") val movieData: RawInfoBlock? = null,
)

@Serializable
data class RawSeriesInfo(
    val info: RawInfoBlock? = null,
    val seasons: List<RawSeason> = emptyList(),
    val episodes: Map<String, List<RawEpisode>> = emptyMap(), // key = season number as string
)

@Serializable
data class RawInfoBlock(
    val name: String? = null,
    val year: String? = null,
    val duration: String? = null,
    val rating: String? = null,
    val plot: String? = null,
    val genre: String? = null,
    val director: String? = null,
    val cast: String? = null,
    val country: String? = null,
    @SerialName("releasedate") val releaseDate: String? = null,
    @SerialName("imdb_id") val imdbId: String? = null,
    @SerialName("tmdb_id") val tmdbId: String? = null,
    val backdrop_path: List<String>? = null,
    val youtube_trailer: String? = null,
    val cover: String? = null,
    val logo: String? = null,
    val poster_path: String? = null,
)

@Serializable
data class RawSeason(
    val air_date: String? = null,
    val name: String? = null,
    val season_number: Int? = null,
    val overview: String? = null,
)

@Serializable
data class RawEpisode(
    val id: Int? = null,
    val title: String? = null,
    val episode_num: Int? = null,
    val container_extension: String? = null,
    val info: RawEpisodeInfo? = null,
)

@Serializable
data class RawEpisodeInfo(
    val duration: String? = null,
    val rating: String? = null,
    val plot: String? = null,
    @SerialName("releasedate") val releaseDate: String? = null,
)

// -----------------------------
// NORMALIZED (UI/DB-freundlich)
// -----------------------------

@Serializable
data class NormalizedListItem(
    val kind: String,            // live|vod|series
    val id: Int,
    val name: String,
    val logo: String? = null,
    val epgId: String? = null,
)

@Serializable
data class NormalizedVodDetail(
    val vodId: Int,
    val name: String,
    val year: Int? = null,
    val rating: Double? = null,
    val plot: String? = null,
    val genre: String? = null,
    val director: String? = null,
    val cast: String? = null,
    val country: String? = null,
    val releaseDate: String? = null,
    val imdbId: String? = null,
    val tmdbId: String? = null,
    val images: List<String> = emptyList(),
    val trailer: String? = null,
)

@Serializable
data class NormalizedSeriesDetail(
    val seriesId: Int,
    val name: String,
    val year: Int? = null,
    val rating: Double? = null,
    val plot: String? = null,
    val genre: String? = null,
    val director: String? = null,
    val cast: String? = null,
    val imdbId: String? = null,
    val tmdbId: String? = null,
    val images: List<String> = emptyList(),
    val trailer: String? = null,
    val seasons: List<NormalizedSeason> = emptyList(),
)

@Serializable
data class NormalizedSeason(
    val seasonNumber: Int,
    val episodes: List<NormalizedEpisode> = emptyList(),
)

@Serializable
data class NormalizedEpisode(
    val episodeNum: Int,
    val title: String? = null,
    val durationSecs: Int? = null,
    val rating: Double? = null,
    val plot: String? = null,
    val airDate: String? = null,
    val playExt: String? = null,
)

// -----------------------------
// Short EPG Programme (for get_short_epg)
// -----------------------------
@Serializable
data class XtShortEPGProgramme(
    val title: String? = null,
    val start: String? = null, // epoch seconds as string
    val end: String? = null,   // epoch seconds as string
)
