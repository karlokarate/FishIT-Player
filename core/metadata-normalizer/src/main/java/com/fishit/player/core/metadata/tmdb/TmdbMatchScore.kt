package com.fishit.player.core.metadata.tmdb

import com.fishit.player.core.model.MediaType
import kotlin.math.max
import kotlin.math.min

/**
 * Deterministic TMDB match score (0..100 points).
 *
 * Per TMDB_ENRICHMENT_CONTRACT.md Section 5.1:
 * - titleSimilarity: 0..60 points
 * - yearScore: 0..20 points
 * - kindAgreement: 0..10 points
 * - episodeExact: 0..10 points (episodes only)
 *
 * @property titleSimilarity Title match score (0..60)
 * @property yearScore Year proximity score (0..20)
 * @property kindAgreement Media type agreement score (0..10)
 * @property episodeExact Episode exactness score (0..10, episodes only)
 */
data class TmdbMatchScore(
    val titleSimilarity: Int,
    val yearScore: Int,
    val kindAgreement: Int,
    val episodeExact: Int,
) {
    /**
     * Total score (0..100).
     */
    val total: Int get() = titleSimilarity + yearScore + kindAgreement + episodeExact

    init {
        require(titleSimilarity in 0..60) { "titleSimilarity must be 0..60, got $titleSimilarity" }
        require(yearScore in 0..20) { "yearScore must be 0..20, got $yearScore" }
        require(kindAgreement in 0..10) { "kindAgreement must be 0..10, got $kindAgreement" }
        require(episodeExact in 0..10) { "episodeExact must be 0..10, got $episodeExact" }
    }
}

/**
 * Match decision from scoring.
 *
 * Per TMDB_ENRICHMENT_CONTRACT.md Section 5.2:
 * - ACCEPT if bestScore >= 85 AND (bestScore - secondBestScore) >= 10
 * - AMBIGUOUS if bestScore >= 70 AND (bestScore - secondBestScore) < 10
 * - REJECT otherwise
 */
enum class MatchDecision {
    /** Strong match: accept this TMDB ID */
    ACCEPT,

    /** Multiple close matches: manual review needed */
    AMBIGUOUS,

    /** No good match: skip TMDB enrichment */
    REJECT,
}

/**
 * Score calculator for TMDB matching.
 *
 * Implements deterministic scoring rules from TMDB_ENRICHMENT_CONTRACT.md Section 5.
 */
object TmdbMatchScorer {
    /**
     * Calculate title similarity score (0..60).
     *
     * - Exact match (normalized, case-insensitive): 60
     * - Levenshtein distance scaling: 60 * (1 - distance / maxLength)
     * - Minimum: 0
     */
    fun calculateTitleSimilarity(
        queryTitle: String,
        tmdbTitle: String,
    ): Int {
        val normalized1 = queryTitle.normalizeForMatch()
        val normalized2 = tmdbTitle.normalizeForMatch()

        if (normalized1 == normalized2) return 60

        val distance = levenshteinDistance(normalized1, normalized2)
        val maxLength = max(normalized1.length, normalized2.length)
        if (maxLength == 0) return 0

        val similarity = 1.0 - (distance.toDouble() / maxLength)
        return (60 * max(0.0, similarity)).toInt().coerceIn(0, 60)
    }

    /**
     * Calculate year proximity score (0..20).
     *
     * - Exact match: 20
     * - ±1 year: 15
     * - ±2 years: 10
     * - ±3 years: 5
     * - >3 years or missing: 0
     */
    fun calculateYearScore(
        queryYear: Int?,
        tmdbYear: Int?,
    ): Int {
        if (queryYear == null || tmdbYear == null) return 0

        val diff = kotlin.math.abs(queryYear - tmdbYear)
        return when (diff) {
            0 -> 20
            1 -> 15
            2 -> 10
            3 -> 5
            else -> 0
        }
    }

    /**
     * Calculate media type agreement score (0..10).
     *
     * - MOVIE ↔ TMDB movie: 10
     * - SERIES_EPISODE ↔ TMDB tv: 10
     * - Mismatch or unknown: 0
     */
    fun calculateKindAgreement(
        queryType: MediaType,
        tmdbMediaType: String,
    ): Int =
        when {
            queryType == MediaType.MOVIE && tmdbMediaType == "movie" -> 10
            queryType == MediaType.SERIES_EPISODE && tmdbMediaType == "tv" -> 10
            else -> 0
        }

    /**
     * Calculate episode exactness score (0..10, episodes only).
     *
     * - Season AND episode match exactly: 10
     * - Season matches, episode differs: 5
     * - No season/episode data: 0
     */
    fun calculateEpisodeExact(
        querySeason: Int?,
        queryEpisode: Int?,
        tmdbSeason: Int?,
        tmdbEpisode: Int?,
    ): Int {
        if (querySeason == null || tmdbSeason == null) return 0

        return when {
            querySeason == tmdbSeason && queryEpisode == tmdbEpisode -> 10
            querySeason == tmdbSeason -> 5
            else -> 0
        }
    }

    /**
     * Decide match acceptance based on scores.
     *
     * Per TMDB_ENRICHMENT_CONTRACT.md Section 5.2.
     *
     * @param scores List of scores (must not be empty)
     * @return Match decision
     */
    fun decideMatch(scores: List<TmdbMatchScore>): MatchDecision {
        if (scores.isEmpty()) return MatchDecision.REJECT

        val sorted = scores.sortedByDescending { it.total }
        val bestScore = sorted[0].total
        val secondBestScore = sorted.getOrNull(1)?.total ?: 0

        return when {
            bestScore >= 85 && (bestScore - secondBestScore) >= 10 -> MatchDecision.ACCEPT
            bestScore >= 70 && (bestScore - secondBestScore) < 10 -> MatchDecision.AMBIGUOUS
            else -> MatchDecision.REJECT
        }
    }

    /**
     * Normalize title for matching.
     *
     * - Lowercase
     * - Remove special characters (keep alphanumeric and spaces)
     * - Collapse multiple spaces to single space
     * - Trim
     */
    private fun String.normalizeForMatch(): String =
        this
            .lowercase()
            .replace(Regex("[^a-z0-9\\s]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

    /**
     * Calculate Levenshtein distance between two strings.
     *
     * Standard dynamic programming implementation.
     */
    private fun levenshteinDistance(
        s1: String,
        s2: String,
    ): Int {
        val len1 = s1.length
        val len2 = s2.length

        if (len1 == 0) return len2
        if (len2 == 0) return len1

        val dp = Array(len1 + 1) { IntArray(len2 + 1) }

        for (i in 0..len1) dp[i][0] = i
        for (j in 0..len2) dp[0][j] = j

        for (i in 1..len1) {
            for (j in 1..len2) {
                val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1
                dp[i][j] =
                    min(
                        min(dp[i - 1][j] + 1, dp[i][j - 1] + 1),
                        dp[i - 1][j - 1] + cost,
                    )
            }
        }

        return dp[len1][len2]
    }
}
