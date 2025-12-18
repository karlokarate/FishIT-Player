package com.fishit.player.infra.transport.tmdb.api

/**
 * Movie details from TMDB.
 *
 * Internal DTO - no tmdb-java types leak into this.
 */
data class TmdbMovieDetails(
    val id: Int,
    val title: String,
    val originalTitle: String?,
    val overview: String?,
    val releaseDate: String?, // ISO 8601 format (YYYY-MM-DD)
    val runtime: Int?, // Minutes
    val voteAverage: Double?,
    val voteCount: Int?,
    val popularity: Double?,
    val adult: Boolean,
    val genres: List<TmdbGenre>,
    val posterPath: String?, // TMDB poster path (e.g., "/abc123.jpg")
    val backdropPath: String?, // TMDB backdrop path
    val imdbId: String?,
)

/**
 * TV show details from TMDB.
 */
data class TmdbTvDetails(
    val id: Int,
    val name: String,
    val originalName: String?,
    val overview: String?,
    val firstAirDate: String?, // ISO 8601 format
    val lastAirDate: String?,
    val numberOfSeasons: Int?,
    val numberOfEpisodes: Int?,
    val voteAverage: Double?,
    val voteCount: Int?,
    val popularity: Double?,
    val adult: Boolean,
    val genres: List<TmdbGenre>,
    val posterPath: String?,
    val backdropPath: String?,
    val seasons: List<TmdbSeason>,
)

/**
 * TV season metadata.
 */
data class TmdbSeason(
    val id: Int,
    val seasonNumber: Int,
    val name: String?,
    val overview: String?,
    val airDate: String?,
    val episodeCount: Int?,
    val posterPath: String?,
)

/**
 * Genre information.
 */
data class TmdbGenre(
    val id: Int,
    val name: String,
)

/**
 * Image collection from TMDB.
 */
data class TmdbImages(
    val id: Int,
    val posters: List<TmdbImage>,
    val backdrops: List<TmdbImage>,
    val logos: List<TmdbImage>,
)

/**
 * Image metadata.
 */
data class TmdbImage(
    val filePath: String,
    val width: Int,
    val height: Int,
    val aspectRatio: Double,
    val voteAverage: Double?,
    val voteCount: Int?,
    val iso6391: String?, // Language code
)
