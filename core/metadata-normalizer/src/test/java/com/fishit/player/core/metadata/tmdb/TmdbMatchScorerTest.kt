package com.fishit.player.core.metadata.tmdb

import com.fishit.player.core.model.MediaType
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Tests for TmdbMatchScorer.
 *
 * Per TMDB_ENRICHMENT_CONTRACT.md Section 8.1 requirement #6.
 */
class TmdbMatchScorerTest {
    @Test
    fun `title similarity - exact match gives 60`() {
        val score = TmdbMatchScorer.calculateTitleSimilarity("The Matrix", "The Matrix")
        assertEquals(60, score)
    }

    @Test
    fun `title similarity - case insensitive`() {
        val score = TmdbMatchScorer.calculateTitleSimilarity("the matrix", "THE MATRIX")
        assertEquals(60, score)
    }

    @Test
    fun `title similarity - normalized punctuation`() {
        val score = TmdbMatchScorer.calculateTitleSimilarity("The Matrix!", "The Matrix")
        assertEquals(60, score)
    }

    @Test
    fun `title similarity - partial match`() {
        val score = TmdbMatchScorer.calculateTitleSimilarity("The Matrix", "The Matrix Reloaded")
        // Not exact, but should have good similarity
        assert(score in 30..59)
    }

    @Test
    fun `title similarity - completely different gives 0`() {
        val score = TmdbMatchScorer.calculateTitleSimilarity("The Matrix", "Avatar")
        // Very low similarity
        assert(score < 30)
    }

    @Test
    fun `year score - exact match gives 20`() {
        val score = TmdbMatchScorer.calculateYearScore(1999, 1999)
        assertEquals(20, score)
    }

    @Test
    fun `year score - 1 year difference gives 15`() {
        val score = TmdbMatchScorer.calculateYearScore(1999, 2000)
        assertEquals(15, score)
    }

    @Test
    fun `year score - 2 year difference gives 10`() {
        val score = TmdbMatchScorer.calculateYearScore(1999, 2001)
        assertEquals(10, score)
    }

    @Test
    fun `year score - 3 year difference gives 5`() {
        val score = TmdbMatchScorer.calculateYearScore(1999, 2002)
        assertEquals(5, score)
    }

    @Test
    fun `year score - more than 3 years gives 0`() {
        val score = TmdbMatchScorer.calculateYearScore(1999, 2010)
        assertEquals(0, score)
    }

    @Test
    fun `year score - null year gives 0`() {
        val score = TmdbMatchScorer.calculateYearScore(null, 1999)
        assertEquals(0, score)
    }

    @Test
    fun `kind agreement - movie matches movie`() {
        val score = TmdbMatchScorer.calculateKindAgreement(MediaType.MOVIE, "movie")
        assertEquals(10, score)
    }

    @Test
    fun `kind agreement - episode matches tv`() {
        val score = TmdbMatchScorer.calculateKindAgreement(MediaType.SERIES_EPISODE, "tv")
        assertEquals(10, score)
    }

    @Test
    fun `kind agreement - mismatch gives 0`() {
        val score = TmdbMatchScorer.calculateKindAgreement(MediaType.MOVIE, "tv")
        assertEquals(0, score)
    }

    @Test
    fun `episode exact - season and episode match gives 10`() {
        val score = TmdbMatchScorer.calculateEpisodeExact(1, 5, 1, 5)
        assertEquals(10, score)
    }

    @Test
    fun `episode exact - season matches but episode differs gives 5`() {
        val score = TmdbMatchScorer.calculateEpisodeExact(1, 5, 1, 6)
        assertEquals(5, score)
    }

    @Test
    fun `episode exact - season differs gives 0`() {
        val score = TmdbMatchScorer.calculateEpisodeExact(1, 5, 2, 5)
        assertEquals(0, score)
    }

    @Test
    fun `episode exact - null season gives 0`() {
        val score = TmdbMatchScorer.calculateEpisodeExact(null, 5, 1, 5)
        assertEquals(0, score)
    }

    @Test
    fun `decision - score 85+ with gap 10+ is ACCEPT`() {
        val scores =
            listOf(
                TmdbMatchScore(titleSimilarity = 60, yearScore = 20, kindAgreement = 10, episodeExact = 0),
                TmdbMatchScore(titleSimilarity = 50, yearScore = 15, kindAgreement = 10, episodeExact = 0),
            )
        val decision = TmdbMatchScorer.decideMatch(scores)
        assertEquals(MatchDecision.ACCEPT, decision)
    }

    @Test
    fun `decision - score 70+ with gap less than 10 is AMBIGUOUS`() {
        val scores =
            listOf(
                TmdbMatchScore(titleSimilarity = 50, yearScore = 20, kindAgreement = 10, episodeExact = 0),
                TmdbMatchScore(titleSimilarity = 50, yearScore = 15, kindAgreement = 10, episodeExact = 0),
            )
        val decision = TmdbMatchScorer.decideMatch(scores)
        assertEquals(MatchDecision.AMBIGUOUS, decision)
    }

    @Test
    fun `decision - score below 70 is REJECT`() {
        val scores =
            listOf(
                TmdbMatchScore(titleSimilarity = 40, yearScore = 10, kindAgreement = 0, episodeExact = 0),
                TmdbMatchScore(titleSimilarity = 30, yearScore = 5, kindAgreement = 0, episodeExact = 0),
            )
        val decision = TmdbMatchScorer.decideMatch(scores)
        assertEquals(MatchDecision.REJECT, decision)
    }

    @Test
    fun `decision - single strong candidate is ACCEPT`() {
        val scores =
            listOf(
                TmdbMatchScore(titleSimilarity = 60, yearScore = 20, kindAgreement = 10, episodeExact = 0),
            )
        val decision = TmdbMatchScorer.decideMatch(scores)
        assertEquals(MatchDecision.ACCEPT, decision)
    }

    @Test
    fun `decision - single weak candidate is REJECT`() {
        val scores =
            listOf(
                TmdbMatchScore(titleSimilarity = 40, yearScore = 10, kindAgreement = 0, episodeExact = 0),
            )
        val decision = TmdbMatchScorer.decideMatch(scores)
        assertEquals(MatchDecision.REJECT, decision)
    }

    @Test
    fun `decision - empty list is REJECT`() {
        val decision = TmdbMatchScorer.decideMatch(emptyList())
        assertEquals(MatchDecision.REJECT, decision)
    }
}
