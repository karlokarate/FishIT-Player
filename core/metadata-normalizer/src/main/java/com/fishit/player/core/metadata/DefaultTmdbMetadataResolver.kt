package com.fishit.player.core.metadata

import app.moviebase.tmdb.Tmdb3
import app.moviebase.tmdb.model.TmdbMovie
import app.moviebase.tmdb.model.TmdbMovieDetail
import app.moviebase.tmdb.model.TmdbShowDetail
import app.moviebase.tmdb.model.TmdbShowPageResult
import com.fishit.player.core.metadata.tmdb.ScoredTmdbResult
import com.fishit.player.core.metadata.tmdb.TmdbConfigProvider
import com.fishit.player.core.metadata.tmdb.TmdbLruCache
import com.fishit.player.core.metadata.tmdb.TmdbMatchDecision
import com.fishit.player.core.metadata.tmdb.TmdbScoring
import com.fishit.player.core.model.ImageRef
import com.fishit.player.core.model.MediaType
import com.fishit.player.core.model.NormalizedMediaMetadata
import com.fishit.player.core.model.TmdbMediaType
import com.fishit.player.core.model.TmdbRef
import com.fishit.player.infra.logging.UnifiedLog
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Real implementation of TmdbMetadataResolver using the TMDB API.
 *
 * Per TMDB_ENRICHMENT_CONTRACT.md:
 * - T-1: Enrichment only, never mutates SourceType/canonicalTitle
 * - T-3: Uses typed TmdbRef (MOVIE or TV, never raw int)
 * - T-5/T-6/T-7: Image SSOT via ImageRef.TmdbPoster/TmdbBackdrop
 * - T-8/T-9: Deterministic scoring via TmdbScoring
 * - T-10/T-11: Details-by-ID when tmdbRef exists, search+score otherwise
 * - T-12: ACCEPT threshold ≥85 with gap ≥10, else AMBIGUOUS
 * - T-13/T-14: Bounded LRU caches (max 256, TTL 7d/24h)
 *
 * ## Resolution Paths
 *
 * **Path A: Details-by-ID** (when tmdbRef exists)
 * 1. Use tmdbRef.id + tmdbRef.type to call GET /movie/{id} or GET /tv/{id}
 * 2. Extract images, year, etc.
 * 3. Return enriched metadata
 *
 * **Path B: Search + Score** (when tmdbRef is null)
 * 1. Search TMDB using canonicalTitle + year + mediaType
 * 2. Score all results using TmdbScoring
 * 3. ACCEPT if best score ≥85 with gap ≥10 to second-best
 * 4. AMBIGUOUS: log warning, do NOT set tmdbRef
 * 5. REJECT: return input unmodified
 */
@Singleton
class DefaultTmdbMetadataResolver @Inject constructor(
    private val configProvider: TmdbConfigProvider,
) : TmdbMetadataResolver {

    private val tag = "TmdbResolver"

    companion object {
        /**
         * TMDB image CDN base URL.
         *
         * Per TMDB API documentation:
         * - Base URL: https://image.tmdb.org/t/p/
         * - Followed by size (e.g., w500, w1280, original)
         * - Then the poster_path/backdrop_path from API response
         */
        private const val TMDB_IMAGE_BASE_URL = "https://image.tmdb.org/t/p/"
    }

    // Lazy initialization of TMDB client
    private val tmdb: Tmdb3 by lazy {
        val config = configProvider.getConfig()
        Tmdb3 {
            tmdbApiKey = config.apiKey
        }
    }

    // Caches per T-13/T-14: max 256 entries, FireTV-safe
    private val movieCache = TmdbLruCache<Int, TmdbMovieDetail>(
        maxSize = TmdbLruCache.DEFAULT_MAX_SIZE,
        ttlMs = TmdbLruCache.TTL_7_DAYS_MS,
    )

    private val tvCache = TmdbLruCache<Int, TmdbShowDetail>(
        maxSize = TmdbLruCache.DEFAULT_MAX_SIZE,
        ttlMs = TmdbLruCache.TTL_7_DAYS_MS,
    )

    private val searchCache = TmdbLruCache<String, SearchCacheEntry>(
        maxSize = TmdbLruCache.DEFAULT_MAX_SIZE,
        ttlMs = TmdbLruCache.TTL_24_HOURS_MS,
    )

    override suspend fun enrich(normalized: NormalizedMediaMetadata): NormalizedMediaMetadata {
        val config = configProvider.getConfig()

        // Check if API key is available
        if (config.apiKey.isBlank()) {
            UnifiedLog.d(tag) { "TMDB API key not configured, skipping enrichment" }
            return normalized
        }

        return try {
            // Path A: Details-by-ID when tmdbRef exists
            if (normalized.tmdb != null) {
                UnifiedLog.d(tag) { "Path A: Details-by-ID for ${normalized.tmdb}" }
                enrichFromDetailsById(normalized, config)
            } else {
                // Path B: Search + Score when tmdbRef is null
                UnifiedLog.d(tag) { "Path B: Search for '${normalized.canonicalTitle}' (${normalized.mediaType})" }
                enrichFromSearch(normalized, config)
            }
        } catch (e: Exception) {
            UnifiedLog.e(tag, e) { "TMDB enrichment failed for '${normalized.canonicalTitle}'" }
            normalized
        }
    }

    /**
     * Path A: Enrich using existing tmdbRef (details-by-ID).
     */
    private suspend fun enrichFromDetailsById(
        normalized: NormalizedMediaMetadata,
        config: com.fishit.player.core.metadata.tmdb.TmdbConfig,
    ): NormalizedMediaMetadata {
        val tmdbRef = normalized.tmdb ?: return normalized

        return when (tmdbRef.type) {
            TmdbMediaType.MOVIE -> enrichMovieById(normalized, tmdbRef.id, config)
            TmdbMediaType.TV -> enrichTvById(normalized, tmdbRef.id, config)
        }
    }

    private suspend fun enrichMovieById(
        normalized: NormalizedMediaMetadata,
        movieId: Int,
        config: com.fishit.player.core.metadata.tmdb.TmdbConfig,
    ): NormalizedMediaMetadata {
        // Check cache first
        val cached = movieCache.get(movieId)
        if (cached != null) {
            UnifiedLog.d(tag) { "Movie $movieId found in cache" }
            return applyMovieDetails(normalized, cached)
        }

        // Fetch from API
        val movie = tmdb.movies.getDetails(
            movieId = movieId,
            language = config.language,
        )

        // Cache result
        movieCache.put(movieId, movie)

        return applyMovieDetails(normalized, movie)
    }

    private suspend fun enrichTvById(
        normalized: NormalizedMediaMetadata,
        tvId: Int,
        config: com.fishit.player.core.metadata.tmdb.TmdbConfig,
    ): NormalizedMediaMetadata {
        // Check cache first
        val cached = tvCache.get(tvId)
        if (cached != null) {
            UnifiedLog.d(tag) { "TV show $tvId found in cache" }
            return applyTvDetails(normalized, cached)
        }

        // Fetch from API
        val show = tmdb.show.getDetails(
            showId = tvId,
            language = config.language,
        )

        // Cache result
        tvCache.put(tvId, show)

        return applyTvDetails(normalized, show)
    }

    /**
     * Path B: Search TMDB and score results.
     */
    private suspend fun enrichFromSearch(
        normalized: NormalizedMediaMetadata,
        config: com.fishit.player.core.metadata.tmdb.TmdbConfig,
    ): NormalizedMediaMetadata {
        val cacheKey = buildSearchCacheKey(normalized, config)

        // Check search cache
        val cached = searchCache.get(cacheKey)
        if (cached != null) {
            UnifiedLog.d(tag) { "Search result found in cache for '${normalized.canonicalTitle}'" }
            return applySearchResult(normalized, cached.tmdbRef, cached.decision, config)
        }

        // Determine search type based on mediaType
        val searchResult = when (normalized.mediaType) {
            MediaType.MOVIE -> searchAndScoreMovies(normalized, config)
            MediaType.SERIES_EPISODE, MediaType.SERIES -> searchAndScoreTvShows(normalized, config)
            else -> searchBothTypes(normalized, config)
        }

        // Cache the search result
        val cacheEntry = SearchCacheEntry(searchResult.first, searchResult.second)
        searchCache.put(cacheKey, cacheEntry)

        return applySearchResult(normalized, searchResult.first, searchResult.second, config)
    }

    private suspend fun searchAndScoreMovies(
        normalized: NormalizedMediaMetadata,
        config: com.fishit.player.core.metadata.tmdb.TmdbConfig,
    ): Pair<TmdbRef?, TmdbMatchDecision> {
        val results = tmdb.search.findMovies(
            query = normalized.canonicalTitle,
            page = 1,
            language = config.language,
            region = config.region,
            year = normalized.year,
        )

        if (results.results.isEmpty()) {
            UnifiedLog.d(tag) { "No movie results for '${normalized.canonicalTitle}'" }
            return null to TmdbMatchDecision.REJECT
        }

        // Score all results
        val scoredResults = results.results.map { movie ->
            val score = TmdbScoring.scoreMovie(
                movieTitle = movie.title,
                movieYear = movie.releaseDate?.year,
                queryTitle = normalized.canonicalTitle,
                queryYear = normalized.year,
            )
            ScoredTmdbResult(
                result = movie,
                score = score,
            )
        }.sortedByDescending { it.totalScore }

        val decision = TmdbScoring.decide(scoredResults)

        return when (decision) {
            TmdbMatchDecision.ACCEPT -> {
                val best = scoredResults.first()
                UnifiedLog.d(tag) { "ACCEPT movie: ${best.result.title} (${best.result.id}) score=${best.totalScore}" }
                TmdbRef(TmdbMediaType.MOVIE, best.result.id) to decision
            }
            TmdbMatchDecision.AMBIGUOUS -> {
                UnifiedLog.w(tag) { "AMBIGUOUS movie results for '${normalized.canonicalTitle}'" }
                null to decision
            }
            TmdbMatchDecision.REJECT -> {
                UnifiedLog.d(tag) { "REJECT: No good movie match for '${normalized.canonicalTitle}'" }
                null to decision
            }
        }
    }

    private suspend fun searchAndScoreTvShows(
        normalized: NormalizedMediaMetadata,
        config: com.fishit.player.core.metadata.tmdb.TmdbConfig,
    ): Pair<TmdbRef?, TmdbMatchDecision> {
        val results = tmdb.search.findShows(
            query = normalized.canonicalTitle,
            page = 1,
            language = config.language,
            firstAirDateYear = normalized.year,
        )

        if (results.results.isEmpty()) {
            UnifiedLog.d(tag) { "No TV results for '${normalized.canonicalTitle}'" }
            return null to TmdbMatchDecision.REJECT
        }

        // Score all results
        val scoredResults = results.results.map { show ->
            val score = TmdbScoring.scoreTvShow(
                showTitle = show.name,
                showYear = show.firstAirDate?.year,
                queryTitle = normalized.canonicalTitle,
                queryYear = normalized.year,
            )
            ScoredTmdbResult(
                result = show,
                score = score,
            )
        }.sortedByDescending { it.totalScore }

        val decision = TmdbScoring.decide(scoredResults)

        return when (decision) {
            TmdbMatchDecision.ACCEPT -> {
                val best = scoredResults.first()
                UnifiedLog.d(tag) { "ACCEPT TV: ${best.result.name} (${best.result.id}) score=${best.totalScore}" }
                TmdbRef(TmdbMediaType.TV, best.result.id) to decision
            }
            TmdbMatchDecision.AMBIGUOUS -> {
                UnifiedLog.w(tag) { "AMBIGUOUS TV results for '${normalized.canonicalTitle}'" }
                null to decision
            }
            TmdbMatchDecision.REJECT -> {
                UnifiedLog.d(tag) { "REJECT: No good TV match for '${normalized.canonicalTitle}'" }
                null to decision
            }
        }
    }

    /**
     * Search both movies and TV shows when mediaType is UNKNOWN.
     */
    private suspend fun searchBothTypes(
        normalized: NormalizedMediaMetadata,
        config: com.fishit.player.core.metadata.tmdb.TmdbConfig,
    ): Pair<TmdbRef?, TmdbMatchDecision> {
        // Try movies first
        val movieResult = searchAndScoreMovies(normalized, config)
        if (movieResult.second == TmdbMatchDecision.ACCEPT) {
            return movieResult
        }

        // Try TV shows
        val tvResult = searchAndScoreTvShows(normalized, config)
        if (tvResult.second == TmdbMatchDecision.ACCEPT) {
            return tvResult
        }

        // If both are ambiguous, return ambiguous
        if (movieResult.second == TmdbMatchDecision.AMBIGUOUS ||
            tvResult.second == TmdbMatchDecision.AMBIGUOUS
        ) {
            return null to TmdbMatchDecision.AMBIGUOUS
        }

        // Both rejected
        return null to TmdbMatchDecision.REJECT
    }

    /**
     * Apply search result to normalized metadata.
     */
    private suspend fun applySearchResult(
        normalized: NormalizedMediaMetadata,
        tmdbRef: TmdbRef?,
        decision: TmdbMatchDecision,
        config: com.fishit.player.core.metadata.tmdb.TmdbConfig,
    ): NormalizedMediaMetadata {
        if (tmdbRef == null || decision != TmdbMatchDecision.ACCEPT) {
            return normalized
        }

        // Fetch full details for the matched result
        return when (tmdbRef.type) {
            TmdbMediaType.MOVIE -> enrichMovieById(
                normalized.copy(tmdb = tmdbRef),
                tmdbRef.id,
                config,
            )
            TmdbMediaType.TV -> enrichTvById(
                normalized.copy(tmdb = tmdbRef),
                tmdbRef.id,
                config,
            )
        }
    }

    /**
     * Apply movie details to normalized metadata.
     *
     * Per T-5/T-6/T-7: Image SSOT using Http ImageRef with TMDB CDN URLs.
     */
    private fun applyMovieDetails(
        normalized: NormalizedMediaMetadata,
        movie: TmdbMovieDetail,
    ): NormalizedMediaMetadata {
        // Build ImageRef for poster (T-5) - TMDB CDN URL
        val poster = movie.posterPath?.let { path ->
            ImageRef.Http(url = buildTmdbPosterUrl(path))
        } ?: normalized.poster

        // Build ImageRef for backdrop (T-6) - TMDB CDN URL
        val backdrop = movie.backdropPath?.let { path ->
            ImageRef.Http(url = buildTmdbBackdropUrl(path))
        } ?: normalized.backdrop

        // Year from release date
        val year = movie.releaseDate?.year ?: normalized.year

        return normalized.copy(
            tmdb = normalized.tmdb ?: TmdbRef(TmdbMediaType.MOVIE, movie.id),
            year = year,
            poster = poster,
            backdrop = backdrop,
        )
    }

    /**
     * Apply TV show details to normalized metadata.
     *
     * Per T-5/T-6/T-7: Image SSOT using Http ImageRef with TMDB CDN URLs.
     */
    private fun applyTvDetails(
        normalized: NormalizedMediaMetadata,
        show: TmdbShowDetail,
    ): NormalizedMediaMetadata {
        // Build ImageRef for poster (T-5) - TMDB CDN URL
        val poster = show.posterPath?.let { path ->
            ImageRef.Http(url = buildTmdbPosterUrl(path))
        } ?: normalized.poster

        // Build ImageRef for backdrop (T-6) - TMDB CDN URL
        val backdrop = show.backdropPath?.let { path ->
            ImageRef.Http(url = buildTmdbBackdropUrl(path))
        } ?: normalized.backdrop

        // Year from first air date
        val year = show.firstAirDate?.year ?: normalized.year

        return normalized.copy(
            tmdb = normalized.tmdb ?: TmdbRef(TmdbMediaType.TV, show.id),
            year = year,
            poster = poster,
            backdrop = backdrop,
        )
    }

    /**
     * Build TMDB poster URL with w500 size (good for posters).
     *
     * TMDB CDN base: https://image.tmdb.org/t/p/
     * Poster size: w500 (500px wide, maintains aspect ratio)
     */
    private fun buildTmdbPosterUrl(posterPath: String): String {
        return "${TMDB_IMAGE_BASE_URL}w500$posterPath"
    }

    /**
     * Build TMDB backdrop URL with w1280 size (good for backdrops).
     *
     * TMDB CDN base: https://image.tmdb.org/t/p/
     * Backdrop size: w1280 (1280px wide, maintains aspect ratio)
     */
    private fun buildTmdbBackdropUrl(backdropPath: String): String {
        return "${TMDB_IMAGE_BASE_URL}w1280$backdropPath"
    }

    private fun buildSearchCacheKey(
        normalized: NormalizedMediaMetadata,
        config: com.fishit.player.core.metadata.tmdb.TmdbConfig,
    ): String {
        return "${normalized.canonicalTitle}|${normalized.year}|${normalized.mediaType}|${config.language}"
    }

    /**
     * Cache entry for search results.
     */
    private data class SearchCacheEntry(
        val tmdbRef: TmdbRef?,
        val decision: TmdbMatchDecision,
    )
}

/**
 * No-op fallback resolver for testing and when TMDB is disabled.
 *
 * Returns input unmodified without any API calls.
 */
class NoOpTmdbMetadataResolver : TmdbMetadataResolver {
    override suspend fun enrich(normalized: NormalizedMediaMetadata): NormalizedMediaMetadata {
        return normalized
    }
}
