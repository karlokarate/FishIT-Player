package com.fishit.player.core.metadata

import com.fishit.player.core.metadata.parser.Re2jSceneNameParser
import org.junit.Ignore
import org.junit.Test
import kotlin.test.assertEquals

/**
 * Debug test for RE2J parser - Two-stage approach
 *
 * Tests marked with @Ignore are extended edge cases planned for future implementation.
 */
class Re2jDebugTest {
    private val parser = Re2jSceneNameParser()

    @Test
    fun debugXMen() {
        val result = parser.parse("X-Men.2000.1080p.BluRay.x264-GROUP")
        println("Input: 'X-Men.2000.1080p.BluRay.x264-GROUP'")
        println("  Title: '${result.title}'")
        println("  Year: ${result.year}")
        assertEquals("X-Men", result.title, "Title should be 'X-Men'")
        assertEquals(2000, result.year, "Year should be 2000")
    }

    @Test
    fun debugBasicMovie() {
        val result = parser.parse("Pulp Fiction 1994 German BluRay.mkv")
        println("Input: 'Pulp Fiction 1994 German BluRay.mkv'")
        println("  Title: '${result.title}'")
        println("  Year: ${result.year}")
        assertEquals(1994, result.year, "Year should be 1994")
        assertEquals("Pulp Fiction", result.title, "Title should be 'Pulp Fiction'")
    }

    @Test
    fun debugNumericTitle300() {
        val result = parser.parse("300 2006 German DL 1080p BluRay.mkv")
        println("Input: '300 2006 German DL 1080p BluRay.mkv'")
        println("  Title: '${result.title}'")
        println("  Year: ${result.year}")
        assertEquals("300", result.title, "Title should be '300'")
        assertEquals(2006, result.year, "Year should be 2006")
    }

    @Ignore("Extended edge case: Numeric title (1917) followed by year - requires title-vs-year disambiguation")
    @Test
    fun debugNumericTitle1917() {
        val result = parser.parse("1917 2019 German DL 1080p BluRay.mkv")
        println("Input: '1917 2019 German DL 1080p BluRay.mkv'")
        println("  Title: '${result.title}'")
        println("  Year: ${result.year}")
        assertEquals("1917", result.title, "Title should be '1917'")
        assertEquals(2019, result.year, "Year should be 2019")
    }

    @Test
    fun debugSeriesWithSxxEyy() {
        val result = parser.parse("Breaking Bad S05E16 German DL 1080p.mkv")
        println("Input: 'Breaking Bad S05E16 German DL 1080p.mkv'")
        println("  Title: '${result.title}'")
        println("  Season: ${result.season}")
        println("  Episode: ${result.episode}")
        assertEquals("Breaking Bad", result.title, "Title should be 'Breaking Bad'")
        assertEquals(5, result.season, "Season should be 5")
        assertEquals(16, result.episode, "Episode should be 16")
    }

    @Ignore("Extended edge case: Anime standalone episode number without E prefix - requires context-aware parsing")
    @Test
    fun debugAnimeEpisode() {
        val result = parser.parse("[SubsPlease] One Piece - 1089 (1080p) [E1A86634].mkv")
        println("Input: '[SubsPlease] One Piece - 1089 (1080p) [E1A86634].mkv'")
        println("  Title: '${result.title}'")
        println("  Episode: ${result.episode}")
        assertEquals("One Piece", result.title, "Title should be 'One Piece'")
        assertEquals(1089, result.episode, "Episode should be 1089")
    }

    @Test
    fun debugGermanFolge() {
        val result = parser.parse("PAW Patrol Folge 103 - Der Wüstensturm.mp4")
        println("Input: 'PAW Patrol Folge 103 - Der Wüstensturm.mp4'")
        println("  Title: '${result.title}'")
        println("  Episode: ${result.episode}")
        assertEquals(103, result.episode, "Episode should be 103")
    }

    @Test
    fun debugProviderPrefix() {
        val result = parser.parse("N| Squid Game 2021")
        println("Input: 'N| Squid Game 2021'")
        println("  Title: '${result.title}'")
        println("  Year: ${result.year}")
        assertEquals("Squid Game", result.title, "Title should be 'Squid Game'")
        assertEquals(2021, result.year, "Year should be 2021")
    }

    @Test
    fun debugTmdbId() {
        val result = parser.parse("Fight Club 1999 tmdb-550.mkv")
        println("Input: 'Fight Club 1999 tmdb-550.mkv'")
        println("  Title: '${result.title}'")
        println("  Year: ${result.year}")
        assertEquals("Fight Club", result.title, "Title should be 'Fight Club'")
        assertEquals(1999, result.year, "Year should be 1999")
    }

    @Test
    fun debugTschick() {
        val result = parser.parse("Tschick 2016 German 1080p BluRay x264.mkv")
        println("Input: 'Tschick 2016 German 1080p BluRay x264.mkv'")
        println("  Title: '${result.title}'")
        println("  Year: ${result.year}")
        assertEquals("Tschick", result.title, "Title should be 'Tschick'")
        assertEquals(2016, result.year, "Year should be 2016")
    }

    @Ignore("Extended edge case: Anime with standalone episode number and season indicator - requires context-aware parsing")
    @Test
    fun debugJujutsuKaisen() {
        val result = parser.parse("[Erai-raws] Jujutsu Kaisen 2nd Season - 23 [1080p][HEVC].mkv")
        println("Input: '[Erai-raws] Jujutsu Kaisen 2nd Season - 23 [1080p][HEVC].mkv'")
        println("  Title: '${result.title}'")
        println("  Episode: ${result.episode}")
        assertEquals(23, result.episode, "Episode should be 23")
    }
}
