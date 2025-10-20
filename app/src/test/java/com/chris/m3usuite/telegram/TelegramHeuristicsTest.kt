package com.chris.m3usuite.telegram

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TelegramHeuristicsTest {
    @Test
    fun `parse handles german season format`() {
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
    fun `parse handles range notation`() {
        val caption = "Serie 1x03-05 ENG"
        val result = TelegramHeuristics.parse(caption)

        assertTrue(result.isSeries)
        assertEquals("Serie", result.seriesTitle)
        assertEquals(1, result.season)
        assertEquals(3, result.episode)
        assertEquals(5, result.episodeEnd)
        assertNull(result.title)
        assertEquals("en", result.language)
        assertNull(result.year)
    }

    @Test
    fun `parse cleans movie metadata`() {
        val caption = "Großer Film 2020 GER 1080p x265"
        val result = TelegramHeuristics.parse(caption)

        assertTrue(!result.isSeries)
        assertEquals("Großer Film", result.title)
        assertEquals("de", result.language)
        assertEquals(2020, result.year)
    }
}
