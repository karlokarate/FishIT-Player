package com.fishit.player.infra.transport.tmdb.testing

import com.fishit.player.infra.transport.tmdb.api.TmdbError
import com.fishit.player.infra.transport.tmdb.api.TmdbGateway
import com.fishit.player.infra.transport.tmdb.api.TmdbGenre
import com.fishit.player.infra.transport.tmdb.api.TmdbImages
import com.fishit.player.infra.transport.tmdb.api.TmdbMovieDetails
import com.fishit.player.infra.transport.tmdb.api.TmdbRequestParams
import com.fishit.player.infra.transport.tmdb.api.TmdbResult
import com.fishit.player.infra.transport.tmdb.api.TmdbTvDetails

/**
 * Fake implementation of TmdbGateway for testing.
 *
 * Usage in tests:
 * ```kotlin
 * val fakeGateway = FakeTmdbGateway()
 * fakeGateway.movieDetailsToReturn = TmdbResult.Ok(movieDetails)
 * // OR
 * fakeGateway.movieDetailsToReturn = TmdbResult.Err(TmdbError.NotFound)
 * ```
 */
class FakeTmdbGateway : TmdbGateway {
    var movieDetailsToReturn: TmdbResult<TmdbMovieDetails> = TmdbResult.Err(TmdbError.NotFound)
    var tvDetailsToReturn: TmdbResult<TmdbTvDetails> = TmdbResult.Err(TmdbError.NotFound)
    var movieImagesToReturn: TmdbResult<TmdbImages> = TmdbResult.Err(TmdbError.NotFound)
    var tvImagesToReturn: TmdbResult<TmdbImages> = TmdbResult.Err(TmdbError.NotFound)

    var getMovieDetailsCalls = mutableListOf<Pair<Int, TmdbRequestParams>>()
    var getTvDetailsCalls = mutableListOf<Pair<Int, TmdbRequestParams>>()
    var getMovieImagesCalls = mutableListOf<Pair<Int, TmdbRequestParams>>()
    var getTvImagesCalls = mutableListOf<Pair<Int, TmdbRequestParams>>()

    override suspend fun getMovieDetails(
        movieId: Int,
        params: TmdbRequestParams,
    ): TmdbResult<TmdbMovieDetails> {
        getMovieDetailsCalls.add(movieId to params)
        return movieDetailsToReturn
    }

    override suspend fun getTvDetails(
        tvId: Int,
        params: TmdbRequestParams,
    ): TmdbResult<TmdbTvDetails> {
        getTvDetailsCalls.add(tvId to params)
        return tvDetailsToReturn
    }

    override suspend fun getMovieImages(
        movieId: Int,
        params: TmdbRequestParams,
    ): TmdbResult<TmdbImages> {
        getMovieImagesCalls.add(movieId to params)
        return movieImagesToReturn
    }

    override suspend fun getTvImages(
        tvId: Int,
        params: TmdbRequestParams,
    ): TmdbResult<TmdbImages> {
        getTvImagesCalls.add(tvId to params)
        return tvImagesToReturn
    }

    fun reset() {
        movieDetailsToReturn = TmdbResult.Err(TmdbError.NotFound)
        tvDetailsToReturn = TmdbResult.Err(TmdbError.NotFound)
        movieImagesToReturn = TmdbResult.Err(TmdbError.NotFound)
        tvImagesToReturn = TmdbResult.Err(TmdbError.NotFound)
        getMovieDetailsCalls.clear()
        getTvDetailsCalls.clear()
        getMovieImagesCalls.clear()
        getTvImagesCalls.clear()
    }

    companion object {
        /**
         * Create a fake movie details object for testing.
         */
        fun createFakeMovieDetails(
            id: Int = 123,
            title: String = "Test Movie",
        ): TmdbMovieDetails =
            TmdbMovieDetails(
                id = id,
                title = title,
                originalTitle = title,
                overview = "Test overview",
                releaseDate = "2024-01-01",
                runtime = 120,
                voteAverage = 7.5,
                voteCount = 1000,
                popularity = 100.0,
                adult = false,
                genres =
                    listOf(
                        TmdbGenre(id = 28, name = "Action"),
                        TmdbGenre(id = 878, name = "Science Fiction"),
                    ),
                posterPath = "/test-poster.jpg",
                backdropPath = "/test-backdrop.jpg",
                imdbId = "tt1234567",
            )

        /**
         * Create a fake TV details object for testing.
         */
        fun createFakeTvDetails(
            id: Int = 456,
            name: String = "Test Show",
        ): TmdbTvDetails =
            TmdbTvDetails(
                id = id,
                name = name,
                originalName = name,
                overview = "Test TV overview",
                firstAirDate = "2024-01-01",
                lastAirDate = null,
                numberOfSeasons = 2,
                numberOfEpisodes = 20,
                voteAverage = 8.0,
                voteCount = 500,
                popularity = 50.0,
                adult = false,
                genres = listOf(TmdbGenre(id = 18, name = "Drama")),
                posterPath = "/test-tv-poster.jpg",
                backdropPath = "/test-tv-backdrop.jpg",
                seasons = emptyList(),
            )

        /**
         * Create a fake images object for testing.
         */
        fun createFakeImages(id: Int = 123): TmdbImages =
            TmdbImages(
                id = id,
                posters = emptyList(),
                backdrops = emptyList(),
                logos = emptyList(),
            )
    }
}
