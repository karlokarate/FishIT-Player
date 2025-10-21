package com.chris.m3usuite.telegram

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TelegramHeuristicsTest {

    @Test
    fun `parse extracts season episode and metadata from german caption`() {
        val caption = "MeineSerie S02E05 – Der Plan (2021) [GER] 1080p"

        val result = TelegramHeuristics.parse(caption)

        assertTrue(result.isSeries)
        assertEquals("MeineSerie", result.seriesTitle)
        assertEquals(2, result.season)
        assertEquals(5, result.episode)
        assertNull(result.episodeEnd)
        assertEquals("Der Plan", result.title)
        assertEquals("de", result.language)
        assertEquals(2021, result.year)
    }

    @Test
    fun `parse handles range based notation`() {
        val caption = "Serie 1x03-05 ENG"

        val result = TelegramHeuristics.parse(caption)

        assertTrue(result.isSeries)
        assertEquals("Serie", result.seriesTitle)
        assertEquals(1, result.season)
        assertEquals(3, result.episode)
        assertEquals(5, result.episodeEnd)
        assertEquals("en", result.language)
    }

    @Test
    fun `parse recognises colon separated season episode`() {
        val caption = "Show S1:E2 - Die Probe [DE/EN]"

        val result = TelegramHeuristics.parse(caption)

        assertTrue(result.isSeries)
        assertEquals(1, result.season)
        assertEquals(2, result.episode)
        assertEquals("de+en", result.language)
    }

    @Test
    fun `cleanMovieTitle removes release tokens`() {
        val raw = "Film.Name.2022.GER.1080p.x265"

        val cleaned = TelegramHeuristics.cleanMovieTitle(raw)

        assertEquals("Film Name", cleaned)
    }

    @Test
    fun `fallbackParse returns movie result when caption empty`() {
        val result = TelegramHeuristics.fallbackParse(null)

        assertFalse(result.isSeries)
        assertNull(result.title)
    }
}
