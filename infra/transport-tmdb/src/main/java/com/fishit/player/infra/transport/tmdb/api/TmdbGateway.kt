package com.fishit.player.infra.transport.tmdb.api

/**
 * Gateway to The Movie Database (TMDB) API.
 *
 * This is the single point of access to TMDB in the entire application.
 * It enforces strict architectural boundaries:
 * - ID-first API: All methods require TMDB IDs (no search)
 * - No type leakage: tmdb-java DTOs never appear in signatures
 * - Error handling: All exceptions converted to TmdbResult.Err
 *
 * Contract Compliance:
 * - Implements TMDB Canonical Identity & Imaging SSOT Contract (v2)
 * - Supports MEDIA_NORMALIZATION_CONTRACT.md requirements
 *
 * Search Policy:
 * - Title search is NOT provided here (higher-level policy)
 * - Normalizer must use ID-first calls when tmdbId exists
 * - If no tmdbId, normalizer may use other heuristics or skip TMDB
 */
interface TmdbGateway {
    /**
     * Get movie details by TMDB movie ID.
     *
     * @param movieId TMDB movie ID
     * @param params Language and region preferences
     * @return Result with movie details or typed error
     */
    suspend fun getMovieDetails(
        movieId: Int,
        params: TmdbRequestParams,
    ): TmdbResult<TmdbMovieDetails>

    /**
     * Get TV show details by TMDB TV ID.
     *
     * @param tvId TMDB TV show ID
     * @param params Language and region preferences
     * @return Result with TV details or typed error
     */
    suspend fun getTvDetails(
        tvId: Int,
        params: TmdbRequestParams,
    ): TmdbResult<TmdbTvDetails>

    /**
     * Get movie images by TMDB movie ID.
     *
     * @param movieId TMDB movie ID
     * @param params Language and region preferences
     * @return Result with image collection or typed error
     */
    suspend fun getMovieImages(
        movieId: Int,
        params: TmdbRequestParams,
    ): TmdbResult<TmdbImages>

    /**
     * Get TV show images by TMDB TV ID.
     *
     * @param tvId TMDB TV show ID
     * @param params Language and region preferences
     * @return Result with image collection or typed error
     */
    suspend fun getTvImages(
        tvId: Int,
        params: TmdbRequestParams,
    ): TmdbResult<TmdbImages>
}
