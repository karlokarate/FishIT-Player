package com.chris.m3usuite.telegram.parser

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for AdultHeuristics.
 *
 * Tests follow the design decision from TELEGRAM_PARSER_CONTRACT.md Section 2.1:
 * - Primary signal: Chat title only
 * - Secondary signal: Caption ONLY for extreme explicit sexual terms
 * - FSK and genres do NOT trigger adult classification
 */
class AdultHeuristicsTest {
    // ==========================================
    // isAdultChatTitle tests (Primary signal)
    // ==========================================

    @Test
    fun `isAdultChatTitle returns true for porn keyword`() {
        assertTrue(AdultHeuristics.isAdultChatTitle("HD Porn Collection"))
    }

    @Test
    fun `isAdultChatTitle returns true for xxx keyword`() {
        assertTrue(AdultHeuristics.isAdultChatTitle("XXX Videos"))
    }

    @Test
    fun `isAdultChatTitle returns true for adult keyword`() {
        assertTrue(AdultHeuristics.isAdultChatTitle("Adult Content"))
    }

    @Test
    fun `isAdultChatTitle returns true for 18+ marker`() {
        assertTrue(AdultHeuristics.isAdultChatTitle("Movies 18+"))
    }

    @Test
    fun `isAdultChatTitle returns true for nsfw keyword`() {
        assertTrue(AdultHeuristics.isAdultChatTitle("NSFW Channel"))
    }

    @Test
    fun `isAdultChatTitle returns true for erotics keyword`() {
        assertTrue(AdultHeuristics.isAdultChatTitle("Erotics Collection"))
    }

    @Test
    fun `isAdultChatTitle returns true for restricted emoji 🔞`() {
        assertTrue(AdultHeuristics.isAdultChatTitle("Movies 🔞"))
    }

    @Test
    fun `isAdultChatTitle returns true for adult emojis`() {
        assertTrue(AdultHeuristics.isAdultChatTitle("Hottest 🍑🍆"))
    }

    @Test
    fun `isAdultChatTitle is case insensitive`() {
        assertTrue(AdultHeuristics.isAdultChatTitle("PORN videos"))
        assertTrue(AdultHeuristics.isAdultChatTitle("Adult content"))
        assertTrue(AdultHeuristics.isAdultChatTitle("Xxx channel"))
    }

    @Test
    fun `isAdultChatTitle returns false for neutral movie chat`() {
        assertFalse(AdultHeuristics.isAdultChatTitle("HD Movies Collection"))
    }

    @Test
    fun `isAdultChatTitle returns false for series chat`() {
        assertFalse(AdultHeuristics.isAdultChatTitle("TV Series 4K"))
    }

    @Test
    fun `isAdultChatTitle returns false for documentary chat`() {
        assertFalse(AdultHeuristics.isAdultChatTitle("Documentary Films"))
    }

    @Test
    fun `isAdultChatTitle returns false for null title`() {
        assertFalse(AdultHeuristics.isAdultChatTitle(null))
    }

    @Test
    fun `isAdultChatTitle returns false for blank title`() {
        assertFalse(AdultHeuristics.isAdultChatTitle("   "))
    }

    // ==========================================
    // hasExtremeExplicitTerms tests (Secondary signal)
    // ==========================================

    @Test
    fun `hasExtremeExplicitTerms returns true for bareback`() {
        assertTrue(AdultHeuristics.hasExtremeExplicitTerms("Scene with bareback action"))
    }

    @Test
    fun `hasExtremeExplicitTerms returns true for gangbang`() {
        assertTrue(AdultHeuristics.hasExtremeExplicitTerms("Gangbang scene"))
    }

    @Test
    fun `hasExtremeExplicitTerms returns true for bukkake`() {
        assertTrue(AdultHeuristics.hasExtremeExplicitTerms("bukkake compilation"))
    }

    @Test
    fun `hasExtremeExplicitTerms returns true for fisting`() {
        assertTrue(AdultHeuristics.hasExtremeExplicitTerms("extreme fisting"))
    }

    @Test
    fun `hasExtremeExplicitTerms returns true for bdsm`() {
        assertTrue(AdultHeuristics.hasExtremeExplicitTerms("BDSM session"))
    }

    @Test
    fun `hasExtremeExplicitTerms returns true for deepthroat`() {
        assertTrue(AdultHeuristics.hasExtremeExplicitTerms("deepthroat training"))
    }

    @Test
    fun `hasExtremeExplicitTerms returns true for cumshot`() {
        assertTrue(AdultHeuristics.hasExtremeExplicitTerms("cumshot compilation"))
    }

    @Test
    fun `hasExtremeExplicitTerms is case insensitive`() {
        assertTrue(AdultHeuristics.hasExtremeExplicitTerms("GANGBANG scene"))
        assertTrue(AdultHeuristics.hasExtremeExplicitTerms("Bdsm content"))
    }

    @Test
    fun `hasExtremeExplicitTerms returns false for neutral caption`() {
        assertFalse(AdultHeuristics.hasExtremeExplicitTerms("Great movie, highly recommended!"))
    }

    @Test
    fun `hasExtremeExplicitTerms returns false for null caption`() {
        assertFalse(AdultHeuristics.hasExtremeExplicitTerms(null))
    }

    @Test
    fun `hasExtremeExplicitTerms returns false for blank caption`() {
        assertFalse(AdultHeuristics.hasExtremeExplicitTerms(""))
    }

    // ==========================================
    // Design Decision: FSK and Genres do NOT set isAdult
    // ==========================================

    @Test
    fun `hasExtremeExplicitTerms returns false for FSK 18 mention`() {
        // Per design decision: FSK 18 does NOT imply adult content
        assertFalse(AdultHeuristics.hasExtremeExplicitTerms("FSK: 18"))
        assertFalse(AdultHeuristics.hasExtremeExplicitTerms("Rated FSK 18 for violence"))
    }

    @Test
    fun `hasExtremeExplicitTerms returns false for horror genre`() {
        // Per design decision: Genres do NOT trigger adult classification
        assertFalse(AdultHeuristics.hasExtremeExplicitTerms("Genre: Horror, Thriller"))
    }

    @Test
    fun `hasExtremeExplicitTerms returns false for romance genre`() {
        // Per design decision: Even romantic/erotic-adjacent genres don't trigger
        assertFalse(AdultHeuristics.hasExtremeExplicitTerms("Genre: Romance, Drama"))
    }

    @Test
    fun `hasExtremeExplicitTerms returns false for action violence`() {
        // Words like "action", "violence", "blood" should not trigger
        assertFalse(AdultHeuristics.hasExtremeExplicitTerms("Brutal action with blood and violence"))
    }

    @Test
    fun `hasExtremeExplicitTerms returns false for sex in normal context`() {
        // The word "sex" alone is NOT in the extreme terms list
        assertFalse(AdultHeuristics.hasExtremeExplicitTerms("The movie has some sex scenes"))
    }

    @Test
    fun `hasExtremeExplicitTerms returns false for nude keyword`() {
        // "nude" is NOT in the extreme terms list
        assertFalse(AdultHeuristics.hasExtremeExplicitTerms("Brief nude scene"))
    }

    // ==========================================
    // isAdultContent combined tests
    // ==========================================

    @Test
    fun `isAdultContent returns true when chat title is adult`() {
        assertTrue(AdultHeuristics.isAdultContent("Porn Channel", "Great movie!"))
    }

    @Test
    fun `isAdultContent returns true when caption has extreme terms`() {
        assertTrue(AdultHeuristics.isAdultContent("Movies HD", "Scene with gangbang"))
    }

    @Test
    fun `isAdultContent returns false for neutral title and caption`() {
        assertFalse(AdultHeuristics.isAdultContent("HD Movies", "Great thriller movie"))
    }

    @Test
    fun `isAdultContent returns false for FSK 18 title and rating caption`() {
        // Both title and caption mentioning FSK 18 should NOT trigger adult
        assertFalse(AdultHeuristics.isAdultContent("FSK 18 Movies", "FSK: 18, Genre: Horror"))
    }

    @Test
    fun `isAdultContent handles null values`() {
        assertFalse(AdultHeuristics.isAdultContent(null, null))
        assertFalse(AdultHeuristics.isAdultContent("Movies", null))
        assertFalse(AdultHeuristics.isAdultContent(null, "Good movie"))
    }
}
