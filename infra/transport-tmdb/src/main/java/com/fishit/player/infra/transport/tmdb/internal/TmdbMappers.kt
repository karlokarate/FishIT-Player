package com.fishit.player.infra.transport.tmdb.internal

import com.fishit.player.infra.transport.tmdb.api.TmdbGenre
import com.fishit.player.infra.transport.tmdb.api.TmdbImage
import com.fishit.player.infra.transport.tmdb.api.TmdbImages
import com.fishit.player.infra.transport.tmdb.api.TmdbMovieDetails
import com.fishit.player.infra.transport.tmdb.api.TmdbSeason
import com.fishit.player.infra.transport.tmdb.api.TmdbTvDetails
import com.uwetrottmann.tmdb2.entities.Genre
import com.uwetrottmann.tmdb2.entities.Image
import com.uwetrottmann.tmdb2.entities.Images
import com.uwetrottmann.tmdb2.entities.Movie
import com.uwetrottmann.tmdb2.entities.TvSeason
import com.uwetrottmann.tmdb2.entities.TvShow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Mappers from tmdb-java entities to internal DTOs.
 *
 * These extension functions ensure tmdb-java types never leak outside this module.
 */

/**
 * Convert Date to ISO 8601 string (YYYY-MM-DD).
 */
private fun Date?.toIsoString(): String? {
    if (this == null) return null
    return try {
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(this)
    } catch (e: Exception) {
        null
    }
}

/**
 * Map tmdb-java Movie to internal TmdbMovieDetails.
 */
fun Movie.toTmdbMovieDetails(): TmdbMovieDetails =
    TmdbMovieDetails(
        id = id ?: 0,
        title = title.orEmpty(),
        originalTitle = original_title,
        overview = overview,
        releaseDate = release_date.toIsoString(),
        runtime = runtime,
        voteAverage = vote_average,
        voteCount = vote_count,
        popularity = popularity,
        adult = adult ?: false,
        genres = genres?.map { it.toTmdbGenre() } ?: emptyList(),
        posterPath = poster_path,
        backdropPath = backdrop_path,
        imdbId = imdb_id,
    )

/**
 * Map tmdb-java TvShow to internal TmdbTvDetails.
 */
fun TvShow.toTmdbTvDetails(): TmdbTvDetails =
    TmdbTvDetails(
        id = id ?: 0,
        name = name.orEmpty(),
        originalName = original_name,
        overview = overview,
        firstAirDate = first_air_date.toIsoString(),
        lastAirDate = last_air_date.toIsoString(),
        numberOfSeasons = number_of_seasons,
        numberOfEpisodes = number_of_episodes,
        voteAverage = vote_average,
        voteCount = vote_count,
        popularity = popularity,
        adult = false, // TvShow doesn't have adult field
        genres = genres?.map { it.toTmdbGenre() } ?: emptyList(),
        posterPath = poster_path,
        backdropPath = backdrop_path,
        seasons = seasons?.map { it.toTmdbSeason() } ?: emptyList(),
    )

/**
 * Map tmdb-java TvSeason to internal TmdbSeason.
 */
fun TvSeason.toTmdbSeason(): TmdbSeason =
    TmdbSeason(
        id = id ?: 0,
        seasonNumber = season_number ?: 0,
        name = name,
        overview = overview,
        airDate = air_date.toIsoString(),
        episodeCount = episode_count,
        posterPath = poster_path,
    )

/**
 * Map tmdb-java Genre to internal TmdbGenre.
 */
fun Genre.toTmdbGenre(): TmdbGenre =
    TmdbGenre(
        id = id ?: 0,
        name = name.orEmpty(),
    )

/**
 * Map tmdb-java Images to internal TmdbImages.
 */
fun Images.toTmdbImages(mediaId: Int): TmdbImages =
    TmdbImages(
        id = mediaId,
        posters = posters?.map { it.toTmdbImage() } ?: emptyList(),
        backdrops = backdrops?.map { it.toTmdbImage() } ?: emptyList(),
        logos = emptyList(), // Note: tmdb-java Images doesn't expose logos field
    )

/**
 * Map tmdb-java Image to internal TmdbImage.
 */
fun Image.toTmdbImage(): TmdbImage =
    TmdbImage(
        filePath = file_path.orEmpty(),
        width = width ?: 0,
        height = height ?: 0,
        aspectRatio = aspect_ratio ?: 0.0,
        voteAverage = vote_average,
        voteCount = vote_count,
        iso6391 = iso_639_1,
    )
