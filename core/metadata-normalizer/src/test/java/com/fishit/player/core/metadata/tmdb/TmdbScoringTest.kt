package com.fishit.player.core.metadata.tmdb

import org.junit.Test
import kotlin.test.assertEquals

/**
 * Tests for TmdbScoring.
 *
 * Per TMDB_ENRICHMENT_CONTRACT.md T-8/T-9:
 * - Score components: titleSimilarity (0..60), yearScore (0..20),
 *   kindAgreement (0..10), episodeExact (0..10)
 * - Decision thresholds: ACCEPT ≥85 with gap ≥10, CONSIDER ≥70
 */
class TmdbScoringTest {
    // ========== Title Score Tests ==========

    @Test
    fun `exact title match returns max score`() {
        val score = TmdbScoring.calculateTitleScore("The Matrix", "The Matrix")
        assertEquals(60, score)
    }

    @Test
    fun `normalized exact match returns max score`() {
        // "The Matrix" normalized -> "matrix"
        // "Matrix" normalized -> "matrix"
        val score = TmdbScoring.calculateTitleScore("The Matrix", "Matrix")
        assertEquals(60, score) // Normalized versions match exactly
    }

    @Test
    fun `similar titles return high score`() {
        val score = TmdbScoring.calculateTitleScore("The Matrix", "Matrix Reloaded")
        // Jaro-Winkler should give high similarity for prefix match
        assert(score >= 40) { "Expected score >= 40, got $score" }
    }

    @Test
    fun `completely different titles return low score`() {
        val score = TmdbScoring.calculateTitleScore("The Matrix", "Titanic")
        // Jaro-Winkler gives some similarity for same-length strings
        // "matrix" vs "titanic" - both 7 chars, some common letters
        // Score should be lower than similar titles but may not be extremely low
        assert(score < 50) { "Expected score < 50 for unrelated titles, got $score" }
    }

    @Test
    fun `empty title returns zero score`() {
        assertEquals(0, TmdbScoring.calculateTitleScore("", "The Matrix"))
        assertEquals(0, TmdbScoring.calculateTitleScore("The Matrix", ""))
    }

    // ========== Year Score Tests ==========

    @Test
    fun `exact year match returns max score`() {
        assertEquals(20, TmdbScoring.calculateYearScore(1999, 1999))
    }

    @Test
    fun `plus-minus one year returns 15`() {
        assertEquals(15, TmdbScoring.calculateYearScore(1999, 2000))
        assertEquals(15, TmdbScoring.calculateYearScore(1999, 1998))
    }

    @Test
    fun `plus-minus two years returns 10`() {
        assertEquals(10, TmdbScoring.calculateYearScore(1999, 2001))
        assertEquals(10, TmdbScoring.calculateYearScore(1999, 1997))
    }

    @Test
    fun `more than two years difference returns zero`() {
        assertEquals(0, TmdbScoring.calculateYearScore(1999, 2010))
        assertEquals(0, TmdbScoring.calculateYearScore(1999, 1990))
    }

    @Test
    fun `null year returns partial credit`() {
        assertEquals(5, TmdbScoring.calculateYearScore(null, 1999))
        assertEquals(5, TmdbScoring.calculateYearScore(1999, null))
        assertEquals(5, TmdbScoring.calculateYearScore(null, null))
    }

    // ========== Movie Score Helper ==========

    @Test
    fun `scoreMovie returns correct components`() {
        val score =
            TmdbScoring.scoreMovie(
                movieTitle = "The Matrix",
                movieYear = 1999,
                queryTitle = "The Matrix",
                queryYear = 1999,
            )

        assertEquals(60, score.titleSimilarity) // Exact match
        assertEquals(20, score.yearScore) // Exact year
        assertEquals(10, score.kindAgreement) // Movie search = movie result
        assertEquals(0, score.episodeExact) // N/A for movies
        assertEquals(90, score.total)
    }

    // ========== TV Show Score Helper ==========

    @Test
    fun `scoreTvShow returns correct components`() {
        val score =
            TmdbScoring.scoreTvShow(
                showTitle = "Breaking Bad",
                showYear = 2008,
                queryTitle = "Breaking Bad",
                queryYear = 2008,
            )

        assertEquals(60, score.titleSimilarity) // Exact match
        assertEquals(20, score.yearScore) // Exact year
        assertEquals(10, score.kindAgreement) // TV search = TV result
        assertEquals(0, score.episodeExact) // Episode matching done later
        assertEquals(90, score.total)
    }

    // ========== Decision Tests ==========

    @Test
    fun `decide returns REJECT for empty results`() {
        val decision = TmdbScoring.decide<String>(emptyList())
        assertEquals(TmdbMatchDecision.REJECT, decision)
    }

    @Test
    fun `decide returns ACCEPT for high score with clear gap`() {
        val results =
            listOf(
                ScoredTmdbResult("Best", TmdbMatchScore(55, 20, 10, 5)), // 90
                ScoredTmdbResult("Second", TmdbMatchScore(40, 15, 10, 0)), // 65
            )

        val decision = TmdbScoring.decide(results)
        assertEquals(TmdbMatchDecision.ACCEPT, decision) // 90 >= 85, gap = 25 >= 10
    }

    @Test
    fun `decide returns AMBIGUOUS for close scores`() {
        val results =
            listOf(
                ScoredTmdbResult("Best", TmdbMatchScore(50, 18, 10, 0)), // 78
                ScoredTmdbResult("Second", TmdbMatchScore(48, 18, 10, 0)), // 76
            )

        val decision = TmdbScoring.decide(results)
        assertEquals(TmdbMatchDecision.AMBIGUOUS, decision) // Both moderate, gap = 2 < 10
    }

    @Test
    fun `decide returns REJECT for low scores`() {
        val results =
            listOf(
                ScoredTmdbResult("Best", TmdbMatchScore(30, 15, 10, 0)), // 55
            )

        val decision = TmdbScoring.decide(results)
        assertEquals(TmdbMatchDecision.REJECT, decision) // 55 < 70 threshold
    }

    @Test
    fun `decide returns ACCEPT for single high-scoring result`() {
        val results =
            listOf(
                ScoredTmdbResult("Only", TmdbMatchScore(55, 20, 10, 5)), // 90
            )

        val decision = TmdbScoring.decide(results)
        assertEquals(TmdbMatchDecision.ACCEPT, decision) // 90 >= 85, no competitor
    }

    // ========== Score Validation ==========

    @Test
    fun `TmdbMatchScore validates ranges`() {
        // Valid score should not throw
        val valid = TmdbMatchScore(60, 20, 10, 10)
        assertEquals(100, valid.total)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `TmdbMatchScore rejects titleSimilarity over 60`() {
        TmdbMatchScore(61, 20, 10, 10)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `TmdbMatchScore rejects negative scores`() {
        TmdbMatchScore(-1, 20, 10, 10)
    }
}
