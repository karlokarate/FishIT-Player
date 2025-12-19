package com.fishit.player.core.metadata.tmdb

import com.fishit.player.core.model.MediaType
import kotlin.math.abs
import kotlin.math.min

/**
 * TMDB match scoring per TMDB_ENRICHMENT_CONTRACT.md T-8.
 *
 * Total score: 0..100 with fixed components:
 * - titleSimilarity: 0..60 (60%)
 * - yearScore: 0..20 (20%)
 * - kindAgreement: 0..10 (10%)
 * - episodeExact: 0..10 (10%, TV only)
 */
data class TmdbMatchScore(
    /** Title similarity score (0..60) using Jaro-Winkler */
    val titleSimilarity: Int,
    /** Year match score (0..20): exact=20, ±1=15, ±2=10, else=0 */
    val yearScore: Int,
    /** Media type agreement score (0..10) */
    val kindAgreement: Int,
    /** Episode exact match score (0..10, TV only) */
    val episodeExact: Int,
) {
    init {
        require(titleSimilarity in 0..60) { "titleSimilarity must be 0..60, got $titleSimilarity" }
        require(yearScore in 0..20) { "yearScore must be 0..20, got $yearScore" }
        require(kindAgreement in 0..10) { "kindAgreement must be 0..10, got $kindAgreement" }
        require(episodeExact in 0..10) { "episodeExact must be 0..10, got $episodeExact" }
    }

    /** Total score (0..100) */
    val total: Int
        get() = titleSimilarity + yearScore + kindAgreement + episodeExact

    companion object {
        /** Maximum possible score */
        const val MAX_SCORE = 100

        /** Zero score (no match) */
        val ZERO = TmdbMatchScore(0, 0, 0, 0)
    }
}

/**
 * Decision result from scoring.
 *
 * Per TMDB_ENRICHMENT_CONTRACT.md T-9:
 * - ACCEPT: bestScore ≥ 85 AND (bestScore - secondBestScore) ≥ 10
 * - AMBIGUOUS: Multiple close matches
 * - REJECT: No acceptable match
 */
enum class TmdbMatchDecision {
    /** Use this match - high confidence and clear winner */
    ACCEPT,

    /** Multiple close matches - cannot determine winner */
    AMBIGUOUS,

    /** No acceptable match found */
    REJECT,
}

/**
 * Scored search result for decision making.
 */
data class ScoredTmdbResult<T>(
    val result: T,
    val score: TmdbMatchScore,
) {
    val totalScore: Int get() = score.total
}

/**
 * Deterministic scoring calculator for TMDB matches.
 *
 * Per TMDB_ENRICHMENT_CONTRACT.md T-8, T-9.
 */
object TmdbScoring {

    // ========== Decision Thresholds (T-9) ==========

    /** Minimum score for ACCEPT decision */
    private const val ACCEPT_THRESHOLD = 85

    /** Minimum gap between best and second-best for ACCEPT */
    private const val ACCEPT_GAP = 10

    /** Minimum score to consider (below this = REJECT) */
    private const val CONSIDER_THRESHOLD = 70

    // ========== Component Weights ==========

    /** Maximum title similarity score */
    private const val MAX_TITLE_SCORE = 60

    /** Maximum year score */
    private const val MAX_YEAR_SCORE = 20

    /** Maximum kind agreement score */
    private const val MAX_KIND_SCORE = 10

    /** Maximum episode exact score */
    private const val MAX_EPISODE_SCORE = 10

    /**
     * Make a decision based on scored results.
     *
     * Per TMDB_ENRICHMENT_CONTRACT.md T-9:
     * - ACCEPT if bestScore ≥ 85 AND (bestScore - secondBestScore) ≥ 10
     * - AMBIGUOUS if scores are close or moderate confidence
     * - REJECT if low confidence
     */
    fun <T> decide(results: List<ScoredTmdbResult<T>>): TmdbMatchDecision {
        if (results.isEmpty()) return TmdbMatchDecision.REJECT

        val sorted = results.sortedByDescending { it.totalScore }
        val best = sorted.first()
        val secondBest = sorted.getOrNull(1)

        // Low confidence = REJECT
        if (best.totalScore < CONSIDER_THRESHOLD) {
            return TmdbMatchDecision.REJECT
        }

        // High confidence + clear winner = ACCEPT
        if (best.totalScore >= ACCEPT_THRESHOLD) {
            if (secondBest == null || best.totalScore - secondBest.totalScore >= ACCEPT_GAP) {
                return TmdbMatchDecision.ACCEPT
            }
        }

        // Close scores or moderate confidence = AMBIGUOUS
        if (secondBest != null && best.totalScore - secondBest.totalScore < ACCEPT_GAP) {
            return TmdbMatchDecision.AMBIGUOUS
        }

        // Moderate confidence but no close competitor = could accept
        if (best.totalScore >= ACCEPT_THRESHOLD) {
            return TmdbMatchDecision.ACCEPT
        }

        return TmdbMatchDecision.AMBIGUOUS
    }

    /**
     * Calculate title similarity score (0..60).
     *
     * Uses Jaro-Winkler similarity, normalized to 0..60.
     */
    fun calculateTitleScore(queryTitle: String, resultTitle: String): Int {
        val normalizedQuery = normalizeTitle(queryTitle)
        val normalizedResult = normalizeTitle(resultTitle)

        if (normalizedQuery.isEmpty() || normalizedResult.isEmpty()) {
            return 0
        }

        // Exact match = full score
        if (normalizedQuery == normalizedResult) {
            return MAX_TITLE_SCORE
        }

        // Calculate Jaro-Winkler similarity
        val similarity = jaroWinklerSimilarity(normalizedQuery, normalizedResult)

        // Scale to 0..60
        return (similarity * MAX_TITLE_SCORE).toInt().coerceIn(0, MAX_TITLE_SCORE)
    }

    /**
     * Calculate year score (0..20).
     *
     * - Exact match: 20
     * - ±1 year: 15
     * - ±2 years: 10
     * - Otherwise: 0
     */
    fun calculateYearScore(queryYear: Int?, resultYear: Int?): Int {
        if (queryYear == null || resultYear == null) {
            // No year to compare - give partial credit
            return 5
        }

        val diff = abs(queryYear - resultYear)
        return when (diff) {
            0 -> MAX_YEAR_SCORE      // Exact: 20
            1 -> 15                   // ±1: 15
            2 -> 10                   // ±2: 10
            else -> 0                 // >±2: 0
        }
    }

    /**
     * Calculate kind agreement score (0..10).
     *
     * @param queryMediaType The media type we're searching for
     * @param isResultMovie Whether the TMDB result is a movie
     * @param isResultTv Whether the TMDB result is a TV show
     */
    fun calculateKindScore(
        queryMediaType: MediaType?,
        isResultMovie: Boolean,
        isResultTv: Boolean,
    ): Int {
        if (queryMediaType == null) {
            // Unknown type - give partial credit
            return 5
        }

        val expectsMovie = queryMediaType == MediaType.MOVIE
        val expectsTv = queryMediaType in setOf(
            MediaType.SERIES,
            MediaType.SERIES_EPISODE,
        )

        return when {
            expectsMovie && isResultMovie -> MAX_KIND_SCORE
            expectsTv && isResultTv -> MAX_KIND_SCORE
            // Type mismatch
            expectsMovie && isResultTv -> 0
            expectsTv && isResultMovie -> 0
            // Unknown/other
            else -> 5
        }
    }

    /**
     * Calculate episode exact score (0..10).
     *
     * Only applicable for TV episodes.
     *
     * @param querySeason Query season number
     * @param queryEpisode Query episode number
     * @param resultSeason Result season number (if checking against episode details)
     * @param resultEpisode Result episode number (if checking against episode details)
     */
    fun calculateEpisodeScore(
        querySeason: Int?,
        queryEpisode: Int?,
        resultSeason: Int?,
        resultEpisode: Int?,
    ): Int {
        // If no episode info in query, not applicable
        if (querySeason == null || queryEpisode == null) {
            return 0
        }

        // If no episode info in result, can't verify
        if (resultSeason == null || resultEpisode == null) {
            return 0
        }

        // Exact match
        return if (querySeason == resultSeason && queryEpisode == resultEpisode) {
            MAX_EPISODE_SCORE
        } else {
            0
        }
    }

    /**
     * Calculate full score for a search result.
     */
    fun calculateScore(
        queryTitle: String,
        queryYear: Int?,
        queryMediaType: MediaType?,
        querySeason: Int?,
        queryEpisode: Int?,
        resultTitle: String,
        resultYear: Int?,
        isResultMovie: Boolean,
        isResultTv: Boolean,
        resultSeason: Int? = null,
        resultEpisode: Int? = null,
    ): TmdbMatchScore {
        return TmdbMatchScore(
            titleSimilarity = calculateTitleScore(queryTitle, resultTitle),
            yearScore = calculateYearScore(queryYear, resultYear),
            kindAgreement = calculateKindScore(queryMediaType, isResultMovie, isResultTv),
            episodeExact = calculateEpisodeScore(querySeason, queryEpisode, resultSeason, resultEpisode),
        )
    }

    /**
     * Convenience method to score a movie search result.
     *
     * @param movieTitle Title from TMDB movie result
     * @param movieYear Year from TMDB movie result (from releaseDate)
     * @param queryTitle Title being searched for
     * @param queryYear Year being searched for
     * @return TmdbMatchScore with movie-specific scoring
     */
    fun scoreMovie(
        movieTitle: String,
        movieYear: Int?,
        queryTitle: String,
        queryYear: Int?,
    ): TmdbMatchScore {
        return TmdbMatchScore(
            titleSimilarity = calculateTitleScore(queryTitle, movieTitle),
            yearScore = calculateYearScore(queryYear, movieYear),
            kindAgreement = MAX_KIND_SCORE, // It's a movie, we're searching for movies
            episodeExact = 0, // Not applicable for movies
        )
    }

    /**
     * Convenience method to score a TV show search result.
     *
     * @param showTitle Title from TMDB TV result (name field)
     * @param showYear Year from TMDB TV result (from firstAirDate)
     * @param queryTitle Title being searched for
     * @param queryYear Year being searched for
     * @param querySeason Season being searched for (optional)
     * @param queryEpisode Episode being searched for (optional)
     * @return TmdbMatchScore with TV-specific scoring
     */
    fun scoreTvShow(
        showTitle: String,
        showYear: Int?,
        queryTitle: String,
        queryYear: Int?,
        querySeason: Int? = null,
        queryEpisode: Int? = null,
    ): TmdbMatchScore {
        return TmdbMatchScore(
            titleSimilarity = calculateTitleScore(queryTitle, showTitle),
            yearScore = calculateYearScore(queryYear, showYear),
            kindAgreement = MAX_KIND_SCORE, // It's a TV show, we're searching for TV shows
            episodeExact = 0, // Episode matching is done later when fetching episode details
        )
    }

    // ========== Helper Functions ==========

    /**
     * Normalize title for comparison.
     *
     * - Lowercase
     * - Remove articles (the, a, an, der, die, das, ein, eine)
     * - Remove punctuation
     * - Collapse whitespace
     */
    private fun normalizeTitle(title: String): String {
        return title
            .lowercase()
            .replace(Regex("^(the|a|an|der|die|das|ein|eine)\\s+"), "")
            .replace(Regex("[^a-z0-9\\s]"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    /**
     * Calculate Jaro-Winkler similarity between two strings.
     *
     * Returns value between 0.0 (no similarity) and 1.0 (identical).
     */
    private fun jaroWinklerSimilarity(s1: String, s2: String): Double {
        if (s1 == s2) return 1.0
        if (s1.isEmpty() || s2.isEmpty()) return 0.0

        val jaroSim = jaroSimilarity(s1, s2)

        // Winkler modification: boost for common prefix
        val prefixLength = s1.zip(s2).takeWhile { it.first == it.second }.size
        val scaledPrefix = min(prefixLength, 4) // Max 4 characters

        // Standard Winkler scaling factor
        val p = 0.1
        return jaroSim + scaledPrefix * p * (1 - jaroSim)
    }

    /**
     * Calculate Jaro similarity between two strings.
     */
    private fun jaroSimilarity(s1: String, s2: String): Double {
        if (s1 == s2) return 1.0
        if (s1.isEmpty() || s2.isEmpty()) return 0.0

        val matchDistance = maxOf(s1.length, s2.length) / 2 - 1
        if (matchDistance < 0) return 0.0

        val s1Matches = BooleanArray(s1.length)
        val s2Matches = BooleanArray(s2.length)

        var matches = 0
        var transpositions = 0

        // Find matches
        for (i in s1.indices) {
            val start = maxOf(0, i - matchDistance)
            val end = minOf(i + matchDistance + 1, s2.length)

            for (j in start until end) {
                if (s2Matches[j] || s1[i] != s2[j]) continue
                s1Matches[i] = true
                s2Matches[j] = true
                matches++
                break
            }
        }

        if (matches == 0) return 0.0

        // Count transpositions
        var k = 0
        for (i in s1.indices) {
            if (!s1Matches[i]) continue
            while (!s2Matches[k]) k++
            if (s1[i] != s2[k]) transpositions++
            k++
        }

        return (
            matches.toDouble() / s1.length +
                matches.toDouble() / s2.length +
                (matches - transpositions / 2.0) / matches
            ) / 3.0
    }
}
