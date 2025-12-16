package com.fishit.player.core.scenenameparser

import com.fishit.player.core.scenenameparser.api.SceneNameInput
import com.fishit.player.core.scenenameparser.api.SceneNameParseResult
import com.fishit.player.core.scenenameparser.api.SourceHint
import com.fishit.player.core.scenenameparser.api.TmdbType
import com.fishit.player.core.scenenameparser.impl.DefaultSceneNameParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Extended test suite with additional real-world test cases.
 * Combined with SceneNameParserTest, provides comprehensive coverage.
 */
class SceneNameParserFullTest {

    private lateinit var parser: DefaultSceneNameParser

    @Before
    fun setup() {
        parser = DefaultSceneNameParser()
    }

    private fun assertParsed(result: SceneNameParseResult) =
        (result as SceneNameParseResult.Parsed).value

    // Additional Telegram test cases
    @Test fun testTelegram_gameOfThrones() {
        val parsed = assertParsed(parser.parse(SceneNameInput("Game.of.Thrones.S03E09.1080p.BluRay.x264.DTS-HD.MA-CTRLHD", SourceHint.TELEGRAM)))
        assertTrue(parsed.title.contains("Game")); assertEquals(3, parsed.season); assertEquals(9, parsed.episode)
    }

    @Test fun testTelegram_dune2021() {
        val parsed = assertParsed(parser.parse(SceneNameInput("Dune.2021.2160p.UHD.BluRay.REMUX.HEVC.TrueHD.Atmos-FGT", SourceHint.TELEGRAM)))
        assertEquals("Dune", parsed.title); assertEquals(2021, parsed.year); assertEquals("2160p", parsed.resolution)
    }

    @Test fun testTelegram_theBoysS01E08() {
        val parsed = assertParsed(parser.parse(SceneNameInput("The.Boys.S01E08.1080p.AMZN.WEB-DL.DDP5.1.H.264-NTb", SourceHint.TELEGRAM)))
        assertTrue(parsed.title.contains("Boys")); assertEquals(1, parsed.season); assertEquals(8, parsed.episode)
    }

    @Test fun testTelegram_interstellar2014() {
        val parsed = assertParsed(parser.parse(SceneNameInput("Interstellar.2014.1080p.BluRay.x265.10bit.DTS-Group", SourceHint.TELEGRAM)))
        assertEquals("Interstellar", parsed.title); assertEquals(2014, parsed.year)
    }

    @Test fun testTelegram_darkS01E01German() {
        val parsed = assertParsed(parser.parse(SceneNameInput("Dark.S01E01.GERMAN.1080p.NF.WEB-DL.x264-GER", SourceHint.TELEGRAM)))
        assertEquals("Dark", parsed.title); assertEquals(1, parsed.season); assertEquals("GERMAN", parsed.language)
    }

    @Test fun testTelegram_mandalorianS02E01() {
        val parsed = assertParsed(parser.parse(SceneNameInput("The.Mandalorian.S02E01.2160p.DSNP.WEB-DL.DDP5.1.HEVC-Group", SourceHint.TELEGRAM)))
        assertTrue(parsed.title.contains("Mandalorian")); assertEquals(2, parsed.season); assertEquals(1, parsed.episode)
    }

    @Test fun testTelegram_oppenheimer2023() {
        val parsed = assertParsed(parser.parse(SceneNameInput("Oppenheimer.2023.1080p.BluRay.x264.DTS-HD.MA-Group", SourceHint.TELEGRAM)))
        assertEquals("Oppenheimer", parsed.title); assertEquals(2023, parsed.year)
    }

    @Test fun testTelegram_topGunMaverick() {
        val parsed = assertParsed(parser.parse(SceneNameInput("Top.Gun.Maverick.2022.2160p.UHD.BluRay.HEVC.TrueHD.Atmos-Group", SourceHint.TELEGRAM)))
        assertTrue(parsed.title.contains("Top Gun")); assertEquals(2022, parsed.year)
    }

    @Test fun testTelegram_parasite2019Korean() {
        val parsed = assertParsed(parser.parse(SceneNameInput("Parasite.2019.KOREAN.1080p.BluRay.x264.DTS-Group", SourceHint.TELEGRAM)))
        assertEquals("Parasite", parsed.title); assertEquals(2019, parsed.year); assertEquals("KOREAN", parsed.language)
    }

    @Test fun testTelegram_godfather1972() {
        val parsed = assertParsed(parser.parse(SceneNameInput("The.Godfather.1972.1080p.BluRay.x264.DTS", SourceHint.TELEGRAM)))
        assertTrue(parsed.title.contains("Godfather")); assertEquals(1972, parsed.year)
    }

    @Test fun testTelegram_bladeRunner2049() {
        val parsed = assertParsed(parser.parse(SceneNameInput("Blade.Runner.2049.2017.2160p.BluRay.HEVC.TrueHD.Atmos-Group", SourceHint.TELEGRAM)))
        assertTrue(parsed.title.contains("Blade Runner")); assertEquals(2017, parsed.year)
    }

    @Test fun testTelegram_shawshank1994() {
        val parsed = assertParsed(parser.parse(SceneNameInput("The.Shawshank.Redemption.1994.1080p.BluRay.x264", SourceHint.TELEGRAM)))
        assertTrue(parsed.title.contains("Shawshank")); assertEquals(1994, parsed.year)
    }

    @Test fun testTelegram_darkKnight2008() {
        val parsed = assertParsed(parser.parse(SceneNameInput("The.Dark.Knight.2008.1080p.BluRay.x264.DTS", SourceHint.TELEGRAM)))
        assertTrue(parsed.title.contains("Dark Knight")); assertEquals(2008, parsed.year)
    }

    // Xtream test cases
    @Test fun testXtream_matrixParens() {
        val parsed = assertParsed(parser.parse(SceneNameInput("The.Matrix (1999) [1080p] [BluRay] [x264]", SourceHint.XTREAM)))
        assertTrue(parsed.title.contains("Matrix")); assertEquals(1999, parsed.year)
    }

    @Test fun testXtream_breakingBadDash() {
        val parsed = assertParsed(parser.parse(SceneNameInput("Breaking Bad - S02E03 - 720p WEB-DL x265 AAC", SourceHint.XTREAM)))
        assertTrue(parsed.title.contains("Breaking")); assertEquals(2, parsed.season); assertEquals(3, parsed.episode)
    }

    @Test fun testXtream_interstellarParens() {
        val parsed = assertParsed(parser.parse(SceneNameInput("Interstellar (2014) 1080p BluRay x265 DTS", SourceHint.XTREAM)))
        assertEquals("Interstellar", parsed.title); assertEquals(2014, parsed.year)
    }

    @Test fun testXtream_theBoysSpace() {
        val parsed = assertParsed(parser.parse(SceneNameInput("The Boys S01E08 1080p AMZN WEB-DL DDP5.1 H.264", SourceHint.XTREAM)))
        assertTrue(parsed.title.contains("Boys")); assertEquals(1, parsed.season); assertEquals(8, parsed.episode)
    }

    @Test fun testXtream_avatarParens() {
        val parsed = assertParsed(parser.parse(SceneNameInput("Avatar The Way of Water (2022) 2160p WEB-DL HEVC DDP5.1", SourceHint.XTREAM)))
        assertTrue(parsed.title.contains("Avatar")); assertEquals(2022, parsed.year)
    }

    @Test fun testXtream_johnWickParens() {
        val parsed = assertParsed(parser.parse(SceneNameInput("John Wick Chapter 4 (2023) 1080p BluRay x265 DTS", SourceHint.XTREAM)))
        assertTrue(parsed.title.contains("John Wick")); assertEquals(2023, parsed.year)
    }

    @Test fun testXtream_parasiteParens() {
        val parsed = assertParsed(parser.parse(SceneNameInput("Parasite (2019) KOREAN 1080p BluRay x264 DTS", SourceHint.XTREAM)))
        assertEquals("Parasite", parsed.title); assertEquals(2019, parsed.year)
    }

    @Test fun testXtream_godfatherParens() {
        val parsed = assertParsed(parser.parse(SceneNameInput("The Godfather (1972) 1080p BluRay x264 DTS", SourceHint.XTREAM)))
        assertTrue(parsed.title.contains("Godfather")); assertEquals(1972, parsed.year)
    }

    @Test fun testXtream_bladeRunnerParens() {
        val parsed = assertParsed(parser.parse(SceneNameInput("Blade Runner 2049 (2017) 2160p BluRay HEVC TrueHD Atmos", SourceHint.XTREAM)))
        assertTrue(parsed.title.contains("Blade Runner")); assertEquals(2017, parsed.year)
    }

    @Test fun testXtream_batmanParens() {
        val parsed = assertParsed(parser.parse(SceneNameInput("The Batman (2022) 2160p WEB-DL HEVC DDP5.1", SourceHint.XTREAM)))
        assertTrue(parsed.title.contains("Batman")); assertEquals(2022, parsed.year)
    }

    @Test fun testXtream_oppenheimerParens() {
        val parsed = assertParsed(parser.parse(SceneNameInput("Oppenheimer (2023) 1080p BluRay x264 DTS-HD MA", SourceHint.XTREAM)))
        assertEquals("Oppenheimer", parsed.title); assertEquals(2023, parsed.year)
    }

    @Test fun testXtream_topGunParens() {
        val parsed = assertParsed(parser.parse(SceneNameInput("Top Gun Maverick (2022) 2160p UHD BluRay HEVC TrueHD Atmos", SourceHint.XTREAM)))
        assertTrue(parsed.title.contains("Top Gun")); assertEquals(2022, parsed.year)
    }

    @Test fun testXtream_duneParens() {
        val parsed = assertParsed(parser.parse(SceneNameInput("Dune (2021) 2160p BluRay REMUX HEVC TrueHD Atmos", SourceHint.XTREAM)))
        assertEquals("Dune", parsed.title); assertEquals(2021, parsed.year)
    }

    @Test fun testXtream_shawshankParens() {
        val parsed = assertParsed(parser.parse(SceneNameInput("The Shawshank Redemption (1994) 1080p BluRay x264", SourceHint.XTREAM)))
        assertTrue(parsed.title.contains("Shawshank")); assertEquals(1994, parsed.year)
    }

    @Test fun testXtream_darkKnightParens() {
        val parsed = assertParsed(parser.parse(SceneNameInput("The Dark Knight (2008) 1080p BluRay x264 DTS", SourceHint.XTREAM)))
        assertTrue(parsed.title.contains("Dark Knight")); assertEquals(2008, parsed.year)
    }

    // TMDB extraction tests
    @Test fun testXtream_expanseWithTmdbUrl() {
        val parsed = assertParsed(parser.parse(SceneNameInput("The Expanse S03E06 themoviedb.org/tv/63639", SourceHint.XTREAM)))
        assertTrue(parsed.title.contains("Expanse")); assertEquals(3, parsed.season); assertEquals(6, parsed.episode)
        assertEquals(63639, parsed.tmdbId); assertEquals(TmdbType.TV, parsed.tmdbType)
    }

    @Test fun testXtream_matrixWithTmdbUrlFirst() {
        val parsed = assertParsed(parser.parse(SceneNameInput("themoviedb.org/movie/603 - The Matrix (1999)", SourceHint.XTREAM)))
        assertTrue(parsed.title.contains("Matrix")); assertEquals(1999, parsed.year)
        assertEquals(603, parsed.tmdbId); assertEquals(TmdbType.MOVIE, parsed.tmdbType)
    }

    @Test fun testXtream_breakingBadWithTmdbTag() {
        val parsed = assertParsed(parser.parse(SceneNameInput("tmdb:1396 Breaking Bad S01E01 1080p", SourceHint.XTREAM)))
        assertTrue(parsed.title.contains("Breaking")); assertEquals(1, parsed.season); assertEquals(1, parsed.episode)
        assertEquals(1396, parsed.tmdbId)
    }
}
