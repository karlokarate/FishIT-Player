package com.fishit.player.core.scenenameparser

import com.fishit.player.core.scenenameparser.api.SceneNameInput
import com.fishit.player.core.scenenameparser.api.SceneNameParseResult
import com.fishit.player.core.scenenameparser.api.SourceHint
import com.fishit.player.core.scenenameparser.api.TmdbType
import com.fishit.player.core.scenenameparser.impl.DefaultSceneNameParser
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Comprehensive test suite for SceneNameParser.
 *
 * Covers 100 test cases:
 * - 50 Telegram-style inputs
 * - 50 Xtream-style inputs
 */
class SceneNameParserTest {
    private lateinit var parser: DefaultSceneNameParser

    @Before
    fun setup() {
        parser = DefaultSceneNameParser()
    }

    // Helper to assert parsed result
    private fun assertParsed(
        raw: String,
        sourceHint: SourceHint,
        expectedTitle: String? = null,
        expectedYear: Int? = null,
        expectedSeason: Int? = null,
        expectedEpisode: Int? = null,
        expectedResolution: String? = null,
        expectedSource: String? = null,
        expectedVideoCodec: String? = null,
        expectedAudioCodec: String? = null,
        expectedLanguage: String? = null,
        expectedReleaseGroup: String? = null,
        expectedTmdbId: Int? = null,
        expectedTmdbType: TmdbType? = null,
    ) {
        val result = parser.parse(SceneNameInput(raw, sourceHint))
        assertTrue(result is SceneNameParseResult.Parsed, "Failed to parse: $raw")
        val parsed = (result as SceneNameParseResult.Parsed).value

        expectedTitle?.let { assertEquals(it, parsed.title, "Title mismatch for: $raw") }
        expectedYear?.let { assertEquals(it, parsed.year, "Year mismatch for: $raw") }
        expectedSeason?.let { assertEquals(it, parsed.season, "Season mismatch for: $raw") }
        expectedEpisode?.let { assertEquals(it, parsed.episode, "Episode mismatch for: $raw") }
        expectedResolution?.let { assertEquals(it, parsed.resolution, "Resolution mismatch for: $raw") }
        expectedSource?.let { assertTrue(parsed.source?.contains(it, ignoreCase = true) == true, "Source mismatch for: $raw") }
        expectedVideoCodec?.let { assertEquals(it, parsed.videoCodec, "Video codec mismatch for: $raw") }
        expectedAudioCodec?.let { assertTrue(parsed.audioCodec?.contains(it, ignoreCase = true) == true, "Audio codec mismatch for: $raw") }
        expectedLanguage?.let { assertEquals(it, parsed.language, "Language mismatch for: $raw") }
        expectedReleaseGroup?.let { assertEquals(it, parsed.releaseGroup, "Release group mismatch for: $raw") }
        expectedTmdbId?.let { assertEquals(it, parsed.tmdbId, "TMDB ID mismatch for: $raw") }
        expectedTmdbType?.let { assertEquals(it, parsed.tmdbType, "TMDB type mismatch for: $raw") }
    }

    // ========== TELEGRAM INPUTS (50 cases) ==========

    @Test
    fun test_telegram_01_matrix_1999() {
        assertParsed(
            raw = "The.Matrix.1999.1080p.BluRay.x264.DTS-GER",
            sourceHint = SourceHint.TELEGRAM,
            expectedTitle = "The Matrix",
            expectedYear = 1999,
            expectedResolution = "1080p",
            expectedVideoCodec = "x264",
        )
    }

    @Test
    fun test_telegram_02_breaking_bad_s02e03() {
        assertParsed(
            raw = "Breaking.Bad.S02E03.720p.WEB-DL.x265.AAC-GROUP",
            sourceHint = SourceHint.TELEGRAM,
            expectedTitle = "Breaking Bad",
            expectedSeason = 2,
            expectedEpisode = 3,
            expectedResolution = "720p",
            expectedVideoCodec = "x265",
        )
    }

    @Test
    fun test_telegram_03_chernobyl_s01e01() {
        assertParsed(
            raw = "Chernobyl.S01E01.1080p.WEBRip.x264-GER",
            sourceHint = SourceHint.TELEGRAM,
            expectedTitle = "Chernobyl",
            expectedSeason = 1,
            expectedEpisode = 1,
            expectedResolution = "1080p",
            expectedVideoCodec = "x264",
        )
    }

    @Test
    fun test_telegram_04_game_of_thrones_s03e09() {
        assertParsed(
            raw = "Game.of.Thrones.S03E09.1080p.BluRay.x264.DTS-HD.MA-CTRLHD",
            sourceHint = SourceHint.TELEGRAM,
            expectedTitle = "Game of Thrones",
            expectedSeason = 3,
            expectedEpisode = 9,
            expectedResolution = "1080p",
            expectedReleaseGroup = "CTRLHD",
        )
    }

    @Test
    fun test_telegram_05_dune_2021() {
        assertParsed(
            raw = "Dune.2021.2160p.UHD.BluRay.REMUX.HEVC.TrueHD.Atmos-FGT",
            sourceHint = SourceHint.TELEGRAM,
            expectedTitle = "Dune",
            expectedYear = 2021,
            expectedResolution = "2160p",
            expectedVideoCodec = "HEVC",
        )
    }

    @Test
    fun test_telegram_06_the_boys_s01e08() {
        assertParsed(
            raw = "The.Boys.S01E08.1080p.AMZN.WEB-DL.DDP5.1.H.264-NTb",
            sourceHint = SourceHint.TELEGRAM,
            expectedTitle = "The Boys",
            expectedSeason = 1,
            expectedEpisode = 8,
            expectedResolution = "1080p",
            expectedReleaseGroup = "NTb",
        )
    }

    @Test
    fun test_telegram_07_interstellar_2014() {
        assertParsed(
            raw = "Interstellar.2014.1080p.BluRay.x265.10bit.DTS-Group",
            sourceHint = SourceHint.TELEGRAM,
            expectedTitle = "Interstellar",
            expectedYear = 2014,
            expectedResolution = "1080p",
            expectedVideoCodec = "x265",
        )
    }

    @Test
    fun test_telegram_08_dark_s01e01_german() {
        assertParsed(
            raw = "Dark.S01E01.GERMAN.1080p.NF.WEB-DL.x264-GER",
            sourceHint = SourceHint.TELEGRAM,
            expectedTitle = "Dark",
            expectedSeason = 1,
            expectedEpisode = 1,
            expectedLanguage = "GERMAN",
            expectedResolution = "1080p",
        )
    }

    @Test
    fun test_telegram_09_office_us_s05e14() {
        assertParsed(
            raw = "The.Office.US.S05E14.720p.WEBRip.x264",
            sourceHint = SourceHint.TELEGRAM,
            expectedTitle = "The Office US",
            expectedSeason = 5,
            expectedEpisode = 14,
            expectedResolution = "720p",
        )
    }

    @Test
    fun test_telegram_10_avatar_way_of_water() {
        assertParsed(
            raw = "Avatar.The.Way.of.Water.2022.2160p.WEB-DL.HEVC.DDP5.1-Group",
            sourceHint = SourceHint.TELEGRAM,
            expectedTitle = "Avatar The Way of Water",
            expectedYear = 2022,
            expectedResolution = "2160p",
            expectedVideoCodec = "HEVC",
        )
    }

    @Test
    fun test_telegram_11_better_call_saul_s04e10() {
        assertParsed(
            raw = "Better.Call.Saul.S04E10.1080p.WEBRip.x265",
            sourceHint = SourceHint.TELEGRAM,
            expectedTitle = "Better Call Saul",
            expectedSeason = 4,
            expectedEpisode = 10,
            expectedResolution = "1080p",
        )
    }

    @Test
    fun test_telegram_12_mandalorian_s02e01() {
        assertParsed(
            raw = "The.Mandalorian.S02E01.2160p.DSNP.WEB-DL.DDP5.1.HEVC-Group",
            sourceHint = SourceHint.TELEGRAM,
            expectedTitle = "The Mandalorian",
            expectedSeason = 2,
            expectedEpisode = 1,
            expectedResolution = "2160p",
        )
    }

    @Test
    fun test_telegram_13_oppenheimer_2023() {
        assertParsed(
            raw = "Oppenheimer.2023.1080p.BluRay.x264.DTS-HD.MA-Group",
            sourceHint = SourceHint.TELEGRAM,
            expectedTitle = "Oppenheimer",
            expectedYear = 2023,
            expectedResolution = "1080p",
        )
    }

    @Test
    fun test_telegram_14_top_gun_maverick() {
        assertParsed(
            raw = "Top.Gun.Maverick.2022.2160p.UHD.BluRay.HEVC.TrueHD.Atmos-Group",
            sourceHint = SourceHint.TELEGRAM,
            expectedTitle = "Top Gun Maverick",
            expectedYear = 2022,
            expectedResolution = "2160p",
        )
    }

    @Test
    fun test_telegram_15_last_of_us_s01e05() {
        assertParsed(
            raw = "The.Last.of.Us.S01E05.1080p.WEB-DL.DDP5.1.H.264-Group",
            sourceHint = SourceHint.TELEGRAM,
            expectedTitle = "The Last of Us",
            expectedSeason = 1,
            expectedEpisode = 5,
            expectedResolution = "1080p",
        )
    }

    @Test
    fun test_telegram_16_peaky_blinders_s06e01() {
        assertParsed(
            raw = "Peaky.Blinders.S06E01.1080p.WEBRip.x265.HEVC",
            sourceHint = SourceHint.TELEGRAM,
            expectedTitle = "Peaky Blinders",
            expectedSeason = 6,
            expectedEpisode = 1,
            expectedResolution = "1080p",
        )
    }

    @Test
    fun test_telegram_17_friends_s01e01() {
        assertParsed(
            raw = "Friends.S01E01.480p.DVDRip.x264",
            sourceHint = SourceHint.TELEGRAM,
            expectedTitle = "Friends",
            expectedSeason = 1,
            expectedEpisode = 1,
            expectedResolution = "480p",
        )
    }

    @Test
    fun test_telegram_18_the_wire_s04e12() {
        assertParsed(
            raw = "The.Wire.S04E12.720p.HDTV.x264",
            sourceHint = SourceHint.TELEGRAM,
            expectedTitle = "The Wire",
            expectedSeason = 4,
            expectedEpisode = 12,
            expectedResolution = "720p",
        )
    }

    @Test
    fun test_telegram_19_john_wick_chapter_4() {
        assertParsed(
            raw = "John.Wick.Chapter.4.2023.1080p.BluRay.x265.DTS-Group",
            sourceHint = SourceHint.TELEGRAM,
            expectedTitle = "John Wick Chapter 4",
            expectedYear = 2023,
            expectedResolution = "1080p",
        )
    }

    @Test
    fun test_telegram_20_parasite_korean() {
        assertParsed(
            raw = "Parasite.2019.KOREAN.1080p.BluRay.x264.DTS-Group",
            sourceHint = SourceHint.TELEGRAM,
            expectedTitle = "Parasite",
            expectedYear = 2019,
            expectedLanguage = "KOREAN",
            expectedResolution = "1080p",
        )
    }

    @Test
    fun test_telegram_21_severance_s01e02() {
        assertParsed(
            raw = "Severance.S01E02.1080p.ATVP.WEB-DL.DDP5.1.H.264-Group",
            sourceHint = SourceHint.TELEGRAM,
            expectedTitle = "Severance",
            expectedSeason = 1,
            expectedEpisode = 2,
            expectedResolution = "1080p",
        )
    }

    @Test
    fun test_telegram_22_the_crown_s03e06() {
        assertParsed(
            raw = "The.Crown.S03E06.1080p.NF.WEB-DL.x264",
            sourceHint = SourceHint.TELEGRAM,
            expectedTitle = "The Crown",
            expectedSeason = 3,
            expectedEpisode = 6,
            expectedResolution = "1080p",
        )
    }

    @Test
    fun test_telegram_23_sopranos_s02e13() {
        assertParsed(
            raw = "The.Sopranos.S02E13.720p.HDTV.x264",
            sourceHint = SourceHint.TELEGRAM,
            expectedTitle = "The Sopranos",
            expectedSeason = 2,
            expectedEpisode = 13,
            expectedResolution = "720p",
        )
    }

    @Test
    fun test_telegram_24_sherlock_s02e01() {
        assertParsed(
            raw = "Sherlock.S02E01.1080p.BluRay.x264",
            sourceHint = SourceHint.TELEGRAM,
            expectedTitle = "Sherlock",
            expectedSeason = 2,
            expectedEpisode = 1,
            expectedResolution = "1080p",
        )
    }

    @Test
    fun test_telegram_25_house_dragon_s01e10() {
        assertParsed(
            raw = "House.of.the.Dragon.S01E10.2160p.WEB-DL.HEVC.DDP5.1-Group",
            sourceHint = SourceHint.TELEGRAM,
            expectedTitle = "House of the Dragon",
            expectedSeason = 1,
            expectedEpisode = 10,
            expectedResolution = "2160p",
        )
    }

    @Test
    fun test_telegram_26_narcos_s01e01() {
        assertParsed(
            raw = "Narcos.S01E01.1080p.NF.WEB-DL.x264",
            sourceHint = SourceHint.TELEGRAM,
            expectedTitle = "Narcos",
            expectedSeason = 1,
            expectedEpisode = 1,
            expectedResolution = "1080p",
        )
    }

    @Test
    fun test_telegram_27_godfather_1972() {
        assertParsed(
            raw = "The.Godfather.1972.1080p.BluRay.x264.DTS",
            sourceHint = SourceHint.TELEGRAM,
            expectedTitle = "The Godfather",
            expectedYear = 1972,
            expectedResolution = "1080p",
        )
    }

    @Test
    fun test_telegram_28_blade_runner_2049() {
        assertParsed(
            raw = "Blade.Runner.2049.2017.2160p.BluRay.HEVC.TrueHD.Atmos-Group",
            sourceHint = SourceHint.TELEGRAM,
            expectedTitle = "Blade Runner 2049",
            expectedYear = 2017,
            expectedResolution = "2160p",
        )
    }

    @Test
    fun test_telegram_29_shawshank_1994() {
        assertParsed(
            raw = "The.Shawshank.Redemption.1994.1080p.BluRay.x264",
            sourceHint = SourceHint.TELEGRAM,
            expectedTitle = "The Shawshank Redemption",
            expectedYear = 1994,
            expectedResolution = "1080p",
        )
    }

    @Test
    fun test_telegram_30_fargo_s02e09() {
        assertParsed(
            raw = "Fargo.S02E09.1080p.WEBRip.x264",
            sourceHint = SourceHint.TELEGRAM,
            expectedTitle = "Fargo",
            expectedSeason = 2,
            expectedEpisode = 9,
            expectedResolution = "1080p",
        )
    }

    @Test
    fun test_telegram_31_mr_robot_s03e07() {
        assertParsed(
            raw = "Mr.Robot.S03E07.720p.WEBRip.x265",
            sourceHint = SourceHint.TELEGRAM,
            expectedTitle = "Mr Robot",
            expectedSeason = 3,
            expectedEpisode = 7,
            expectedResolution = "720p",
        )
    }

    @Test
    fun test_telegram_32_expanse_s03e06() {
        assertParsed(
            raw = "The.Expanse.S03E06.1080p.AMZN.WEB-DL.DDP5.1.H.264-Group",
            sourceHint = SourceHint.TELEGRAM,
            expectedTitle = "The Expanse",
            expectedSeason = 3,
            expectedEpisode = 6,
            expectedResolution = "1080p",
        )
    }

    @Test
    fun test_telegram_33_the_ring_2002() {
        assertParsed(
            raw = "The.Ring.2002.1080p.BluRay.x264",
            sourceHint = SourceHint.TELEGRAM,
            expectedTitle = "The Ring",
            expectedYear = 2002,
            expectedResolution = "1080p",
        )
    }

    @Test
    fun test_telegram_34_batman_2022() {
        assertParsed(
            raw = "The.Batman.2022.2160p.WEB-DL.HEVC.DDP5.1-Group",
            sourceHint = SourceHint.TELEGRAM,
            expectedTitle = "The Batman",
            expectedYear = 2022,
            expectedResolution = "2160p",
        )
    }

    @Test
    fun test_telegram_35_loki_s02e04() {
        assertParsed(
            raw = "Loki.S02E04.2160p.DSNP.WEB-DL.HEVC.DDP5.1-Group",
            sourceHint = SourceHint.TELEGRAM,
            expectedTitle = "Loki",
            expectedSeason = 2,
            expectedEpisode = 4,
            expectedResolution = "2160p",
        )
    }

    @Test
    fun test_telegram_36_prestige_2006() {
        assertParsed(
            raw = "The.Prestige.2006.1080p.BluRay.x265",
            sourceHint = SourceHint.TELEGRAM,
            expectedTitle = "The Prestige",
            expectedYear = 2006,
            expectedResolution = "1080p",
        )
    }

    @Test
    fun test_telegram_37_silo_s01e03() {
        assertParsed(
            raw = "Silo.S01E03.1080p.ATVP.WEB-DL.DDP5.1.H.264-Group",
            sourceHint = SourceHint.TELEGRAM,
            expectedTitle = "Silo",
            expectedSeason = 1,
            expectedEpisode = 3,
            expectedResolution = "1080p",
        )
    }

    @Test
    fun test_telegram_38_matrix_resurrections() {
        assertParsed(
            raw = "The.Matrix.Resurrections.2021.2160p.WEB-DL.HEVC.DDP5.1-Group",
            sourceHint = SourceHint.TELEGRAM,
            expectedTitle = "The Matrix Resurrections",
            expectedYear = 2021,
            expectedResolution = "2160p",
        )
    }

    @Test
    fun test_telegram_39_prison_break_s01e05() {
        assertParsed(
            raw = "Prison.Break.S01E05.720p.HDTV.x264",
            sourceHint = SourceHint.TELEGRAM,
            expectedTitle = "Prison Break",
            expectedSeason = 1,
            expectedEpisode = 5,
            expectedResolution = "720p",
        )
    }

    @Test
    fun test_telegram_40_band_of_brothers_s01e02() {
        assertParsed(
            raw = "Band.of.Brothers.S01E02.1080p.BluRay.x264",
            sourceHint = SourceHint.TELEGRAM,
            expectedTitle = "Band of Brothers",
            expectedSeason = 1,
            expectedEpisode = 2,
            expectedResolution = "1080p",
        )
    }

    @Test
    fun test_telegram_41_sandman_s01e01() {
        assertParsed(
            raw = "The.Sandman.S01E01.1080p.NF.WEB-DL.x264",
            sourceHint = SourceHint.TELEGRAM,
            expectedTitle = "The Sandman",
            expectedSeason = 1,
            expectedEpisode = 1,
            expectedResolution = "1080p",
        )
    }

    @Test
    fun test_telegram_42_no_country_for_old_men() {
        assertParsed(
            raw = "No.Country.for.Old.Men.2007.1080p.BluRay.x264",
            sourceHint = SourceHint.TELEGRAM,
            expectedTitle = "No Country for Old Men",
            expectedYear = 2007,
            expectedResolution = "1080p",
        )
    }

    @Test
    fun test_telegram_43_revenant_2015() {
        assertParsed(
            raw = "The.Revenant.2015.1080p.BluRay.x264.DTS",
            sourceHint = SourceHint.TELEGRAM,
            expectedTitle = "The Revenant",
            expectedYear = 2015,
            expectedResolution = "1080p",
        )
    }

    @Test
    fun test_telegram_44_handmaids_tale_s04e01() {
        assertParsed(
            raw = "The.Handmaids.Tale.S04E01.1080p.WEB-DL.x264",
            sourceHint = SourceHint.TELEGRAM,
            expectedTitle = "The Handmaids Tale",
            expectedSeason = 4,
            expectedEpisode = 1,
            expectedResolution = "1080p",
        )
    }

    @Test
    fun test_telegram_45_witcher_s03e01() {
        assertParsed(
            raw = "The.Witcher.S03E01.1080p.NF.WEB-DL.x264",
            sourceHint = SourceHint.TELEGRAM,
            expectedTitle = "The Witcher",
            expectedSeason = 3,
            expectedEpisode = 1,
            expectedResolution = "1080p",
        )
    }

    @Test
    fun test_telegram_46_foundation_s02e08() {
        assertParsed(
            raw = "Foundation.S02E08.2160p.ATVP.WEB-DL.HEVC.DDP5.1-Group",
            sourceHint = SourceHint.TELEGRAM,
            expectedTitle = "Foundation",
            expectedSeason = 2,
            expectedEpisode = 8,
            expectedResolution = "2160p",
        )
    }

    @Test
    fun test_telegram_47_dark_knight_2008() {
        assertParsed(
            raw = "The.Dark.Knight.2008.1080p.BluRay.x264.DTS",
            sourceHint = SourceHint.TELEGRAM,
            expectedTitle = "The Dark Knight",
            expectedYear = 2008,
            expectedResolution = "1080p",
        )
    }

    @Test
    fun test_telegram_48_penguin_s01e01() {
        assertParsed(
            raw = "The.Penguin.S01E01.1080p.WEB-DL.DDP5.1.H.264-Group",
            sourceHint = SourceHint.TELEGRAM,
            expectedTitle = "The Penguin",
            expectedSeason = 1,
            expectedEpisode = 1,
            expectedResolution = "1080p",
        )
    }

    @Test
    fun test_telegram_49_matrix_with_tmdb_url() {
        assertParsed(
            raw = "The.Matrix.1999 https://www.themoviedb.org/movie/603-the-matrix",
            sourceHint = SourceHint.TELEGRAM,
            expectedTitle = "The Matrix",
            expectedYear = 1999,
            expectedTmdbId = 603,
            expectedTmdbType = TmdbType.MOVIE,
        )
    }

    @Test
    fun test_telegram_50_breaking_bad_with_tmdb_tag() {
        assertParsed(
            raw = "Breaking.Bad tmdb:1396 S01E01 1080p WEB-DL",
            sourceHint = SourceHint.TELEGRAM,
            expectedTitle = "Breaking Bad",
            expectedSeason = 1,
            expectedEpisode = 1,
            expectedResolution = "1080p",
            expectedTmdbId = 1396,
        )
    }

    // ========== XTREAM INPUTS (50 cases) ==========

    @Test
    fun test_xtream_01_matrix_1999() {
        assertParsed(
            raw = "The.Matrix (1999) [1080p] [BluRay] [x264]",
            sourceHint = SourceHint.XTREAM,
            expectedTitle = "The Matrix",
            expectedYear = 1999,
            expectedResolution = "1080p",
            expectedVideoCodec = "x264",
        )
    }

    @Test
    fun test_xtream_02_breaking_bad_s02e03() {
        assertParsed(
            raw = "Breaking Bad - S02E03 - 720p WEB-DL x265 AAC",
            sourceHint = SourceHint.XTREAM,
            expectedTitle = "Breaking Bad",
            expectedSeason = 2,
            expectedEpisode = 3,
            expectedResolution = "720p",
        )
    }

    @Test
    fun test_xtream_03_dark_s01e01() {
        assertParsed(
            raw = "Dark - S01E01 - GERMAN - 1080p NF WEB-DL",
            sourceHint = SourceHint.XTREAM,
            expectedTitle = "Dark",
            expectedSeason = 1,
            expectedEpisode = 1,
            expectedLanguage = "GERMAN",
            expectedResolution = "1080p",
        )
    }

    @Test
    fun test_xtream_04_interstellar_2014() {
        assertParsed(
            raw = "Interstellar (2014) 1080p BluRay x265 DTS",
            sourceHint = SourceHint.XTREAM,
            expectedTitle = "Interstellar",
            expectedYear = 2014,
            expectedResolution = "1080p",
        )
    }

    @Test
    fun test_xtream_05_the_boys_s01e08() {
        assertParsed(
            raw = "The Boys S01E08 1080p AMZN WEB-DL DDP5.1 H.264",
            sourceHint = SourceHint.XTREAM,
            expectedTitle = "The Boys",
            expectedSeason = 1,
            expectedEpisode = 8,
            expectedResolution = "1080p",
        )
    }

    @Test
    fun test_xtream_06_office_us_s05e14() {
        assertParsed(
            raw = "The Office US S05E14 720p WEBRip x264",
            sourceHint = SourceHint.XTREAM,
            expectedTitle = "The Office US",
            expectedSeason = 5,
            expectedEpisode = 14,
            expectedResolution = "720p",
        )
    }

    @Test
    fun test_xtream_07_avatar_way_of_water() {
        assertParsed(
            raw = "Avatar The Way of Water (2022) 2160p WEB-DL HEVC DDP5.1",
            sourceHint = SourceHint.XTREAM,
            expectedTitle = "Avatar The Way of Water",
            expectedYear = 2022,
            expectedResolution = "2160p",
        )
    }

    @Test
    fun test_xtream_08_sopranos_s02e13() {
        assertParsed(
            raw = "The Sopranos S02E13 720p HDTV x264",
            sourceHint = SourceHint.XTREAM,
            expectedTitle = "The Sopranos",
            expectedSeason = 2,
            expectedEpisode = 13,
            expectedResolution = "720p",
        )
    }

    @Test
    fun test_xtream_09_sherlock_s02e01() {
        assertParsed(
            raw = "Sherlock S02E01 1080p BluRay x264",
            sourceHint = SourceHint.XTREAM,
            expectedTitle = "Sherlock",
            expectedSeason = 2,
            expectedEpisode = 1,
            expectedResolution = "1080p",
        )
    }

    @Test
    fun test_xtream_10_house_dragon_s01e10() {
        assertParsed(
            raw = "House of the Dragon S01E10 2160p WEB-DL HEVC DDP5.1",
            sourceHint = SourceHint.XTREAM,
            expectedTitle = "House of the Dragon",
            expectedSeason = 1,
            expectedEpisode = 10,
            expectedResolution = "2160p",
        )
    }

    @Test
    fun test_xtream_11_john_wick_chapter_4() {
        assertParsed(
            raw = "John Wick Chapter 4 (2023) 1080p BluRay x265 DTS",
            sourceHint = SourceHint.XTREAM,
            expectedTitle = "John Wick Chapter 4",
            expectedYear = 2023,
            expectedResolution = "1080p",
        )
    }

    @Test
    fun test_xtream_12_parasite_korean() {
        assertParsed(
            raw = "Parasite (2019) KOREAN 1080p BluRay x264 DTS",
            sourceHint = SourceHint.XTREAM,
            expectedTitle = "Parasite",
            expectedYear = 2019,
            expectedLanguage = "KOREAN",
            expectedResolution = "1080p",
        )
    }

    @Test
    fun test_xtream_13_godfather_1972() {
        assertParsed(
            raw = "The Godfather (1972) 1080p BluRay x264 DTS",
            sourceHint = SourceHint.XTREAM,
            expectedTitle = "The Godfather",
            expectedYear = 1972,
            expectedResolution = "1080p",
        )
    }

    @Test
    fun test_xtream_14_blade_runner_2049() {
        assertParsed(
            raw = "Blade Runner 2049 (2017) 2160p BluRay HEVC TrueHD Atmos",
            sourceHint = SourceHint.XTREAM,
            expectedTitle = "Blade Runner 2049",
            expectedYear = 2017,
            expectedResolution = "2160p",
        )
    }

    @Test
    fun test_xtream_15_fargo_s02e09() {
        assertParsed(
            raw = "Fargo S02E09 1080p WEBRip x264",
            sourceHint = SourceHint.XTREAM,
            expectedTitle = "Fargo",
            expectedSeason = 2,
            expectedEpisode = 9,
            expectedResolution = "1080p",
        )
    }

    @Test
    fun test_xtream_16_mr_robot_s03e07() {
        assertParsed(
            raw = "Mr Robot S03E07 720p WEBRip x265",
            sourceHint = SourceHint.XTREAM,
            expectedTitle = "Mr Robot",
            expectedSeason = 3,
            expectedEpisode = 7,
            expectedResolution = "720p",
        )
    }

    @Test
    fun test_xtream_17_expanse_s03e06() {
        assertParsed(
            raw = "The Expanse S03E06 1080p AMZN WEB-DL DDP5.1 H.264",
            sourceHint = SourceHint.XTREAM,
            expectedTitle = "The Expanse",
            expectedSeason = 3,
            expectedEpisode = 6,
            expectedResolution = "1080p",
        )
    }

    @Test
    fun test_xtream_18_batman_2022() {
        assertParsed(
            raw = "The Batman (2022) 2160p WEB-DL HEVC DDP5.1",
            sourceHint = SourceHint.XTREAM,
            expectedTitle = "The Batman",
            expectedYear = 2022,
            expectedResolution = "2160p",
        )
    }

    @Test
    fun test_xtream_19_loki_s02e04() {
        assertParsed(
            raw = "Loki S02E04 2160p DSNP WEB-DL HEVC DDP5.1",
            sourceHint = SourceHint.XTREAM,
            expectedTitle = "Loki",
            expectedSeason = 2,
            expectedEpisode = 4,
            expectedResolution = "2160p",
        )
    }

    @Test
    fun test_xtream_20_silo_s01e03() {
        assertParsed(
            raw = "Silo S01E03 1080p ATVP WEB-DL DDP5.1 H.264",
            sourceHint = SourceHint.XTREAM,
            expectedTitle = "Silo",
            expectedSeason = 1,
            expectedEpisode = 3,
            expectedResolution = "1080p",
        )
    }

    @Test
    fun test_xtream_21_mandalorian_s02e01() {
        assertParsed(
            raw = "The Mandalorian S02E01 2160p DSNP WEB-DL HEVC DDP5.1",
            sourceHint = SourceHint.XTREAM,
            expectedTitle = "The Mandalorian",
            expectedSeason = 2,
            expectedEpisode = 1,
            expectedResolution = "2160p",
        )
    }

    @Test
    fun test_xtream_22_oppenheimer_2023() {
        assertParsed(
            raw = "Oppenheimer (2023) 1080p BluRay x264 DTS-HD MA",
            sourceHint = SourceHint.XTREAM,
            expectedTitle = "Oppenheimer",
            expectedYear = 2023,
            expectedResolution = "1080p",
        )
    }

    @Test
    fun test_xtream_23_top_gun_maverick() {
        assertParsed(
            raw = "Top Gun Maverick (2022) 2160p UHD BluRay HEVC TrueHD Atmos",
            sourceHint = SourceHint.XTREAM,
            expectedTitle = "Top Gun Maverick",
            expectedYear = 2022,
            expectedResolution = "2160p",
        )
    }

    @Test
    fun test_xtream_24_last_of_us_s01e05() {
        assertParsed(
            raw = "The Last of Us S01E05 1080p WEB-DL DDP5.1 H.264",
            sourceHint = SourceHint.XTREAM,
            expectedTitle = "The Last of Us",
            expectedSeason = 1,
            expectedEpisode = 5,
            expectedResolution = "1080p",
        )
    }

    @Test
    fun test_xtream_25_peaky_blinders_s06e01() {
        assertParsed(
            raw = "Peaky Blinders S06E01 1080p WEBRip x265",
            sourceHint = SourceHint.XTREAM,
            expectedTitle = "Peaky Blinders",
            expectedSeason = 6,
            expectedEpisode = 1,
            expectedResolution = "1080p",
        )
    }

    @Test
    fun test_xtream_26_friends_s01e01() {
        assertParsed(
            raw = "Friends S01E01 480p DVDRip x264",
            sourceHint = SourceHint.XTREAM,
            expectedTitle = "Friends",
            expectedSeason = 1,
            expectedEpisode = 1,
            expectedResolution = "480p",
        )
    }

    @Test
    fun test_xtream_27_wire_s04e12() {
        assertParsed(
            raw = "The Wire S04E12 720p HDTV x264",
            sourceHint = SourceHint.XTREAM,
            expectedTitle = "The Wire",
            expectedSeason = 4,
            expectedEpisode = 12,
            expectedResolution = "720p",
        )
    }

    @Test
    fun test_xtream_28_severance_s01e02() {
        assertParsed(
            raw = "Severance S01E02 1080p ATVP WEB-DL DDP5.1 H.264",
            sourceHint = SourceHint.XTREAM,
            expectedTitle = "Severance",
            expectedSeason = 1,
            expectedEpisode = 2,
            expectedResolution = "1080p",
        )
    }

    @Test
    fun test_xtream_29_crown_s03e06() {
        assertParsed(
            raw = "The Crown S03E06 1080p NF WEB-DL x264",
            sourceHint = SourceHint.XTREAM,
            expectedTitle = "The Crown",
            expectedSeason = 3,
            expectedEpisode = 6,
            expectedResolution = "1080p",
        )
    }

    @Test
    fun test_xtream_30_band_of_brothers_s01e02() {
        assertParsed(
            raw = "Band of Brothers S01E02 1080p BluRay x264",
            sourceHint = SourceHint.XTREAM,
            expectedTitle = "Band of Brothers",
            expectedSeason = 1,
            expectedEpisode = 2,
            expectedResolution = "1080p",
        )
    }

    @Test
    fun test_xtream_31_no_country_old_men() {
        assertParsed(
            raw = "No Country for Old Men (2007) 1080p BluRay x264",
            sourceHint = SourceHint.XTREAM,
            expectedTitle = "No Country for Old Men",
            expectedYear = 2007,
            expectedResolution = "1080p",
        )
    }

    @Test
    fun test_xtream_32_revenant_2015() {
        assertParsed(
            raw = "The Revenant (2015) 1080p BluRay x264 DTS",
            sourceHint = SourceHint.XTREAM,
            expectedTitle = "The Revenant",
            expectedYear = 2015,
            expectedResolution = "1080p",
        )
    }

    @Test
    fun test_xtream_33_handmaids_tale_s04e01() {
        assertParsed(
            raw = "The Handmaid's Tale S04E01 1080p WEB-DL x264",
            sourceHint = SourceHint.XTREAM,
            expectedTitle = "The Handmaid's Tale",
            expectedSeason = 4,
            expectedEpisode = 1,
            expectedResolution = "1080p",
        )
    }

    @Test
    fun test_xtream_34_witcher_s03e01() {
        assertParsed(
            raw = "The Witcher S03E01 1080p NF WEB-DL x264",
            sourceHint = SourceHint.XTREAM,
            expectedTitle = "The Witcher",
            expectedSeason = 3,
            expectedEpisode = 1,
            expectedResolution = "1080p",
        )
    }

    @Test
    fun test_xtream_35_foundation_s02e08() {
        assertParsed(
            raw = "Foundation S02E08 2160p ATVP WEB-DL HEVC DDP5.1",
            sourceHint = SourceHint.XTREAM,
            expectedTitle = "Foundation",
            expectedSeason = 2,
            expectedEpisode = 8,
            expectedResolution = "2160p",
        )
    }

    @Test
    fun test_xtream_36_dark_knight_2008() {
        assertParsed(
            raw = "The Dark Knight (2008) 1080p BluRay x264 DTS",
            sourceHint = SourceHint.XTREAM,
            expectedTitle = "The Dark Knight",
            expectedYear = 2008,
            expectedResolution = "1080p",
        )
    }

    @Test
    fun test_xtream_37_penguin_s01e01() {
        assertParsed(
            raw = "The Penguin S01E01 1080p WEB-DL DDP5.1 H.264",
            sourceHint = SourceHint.XTREAM,
            expectedTitle = "The Penguin",
            expectedSeason = 1,
            expectedEpisode = 1,
            expectedResolution = "1080p",
        )
    }

    @Test
    fun test_xtream_38_matrix_resurrections() {
        assertParsed(
            raw = "The Matrix Resurrections (2021) 2160p WEB-DL HEVC DDP5.1",
            sourceHint = SourceHint.XTREAM,
            expectedTitle = "The Matrix Resurrections",
            expectedYear = 2021,
            expectedResolution = "2160p",
        )
    }

    @Test
    fun test_xtream_39_prison_break_s01e05() {
        assertParsed(
            raw = "Prison Break S01E05 720p HDTV x264",
            sourceHint = SourceHint.XTREAM,
            expectedTitle = "Prison Break",
            expectedSeason = 1,
            expectedEpisode = 5,
            expectedResolution = "720p",
        )
    }

    @Test
    fun test_xtream_40_chernobyl_s01e01() {
        assertParsed(
            raw = "Chernobyl S01E01 1080p WEBRip x264",
            sourceHint = SourceHint.XTREAM,
            expectedTitle = "Chernobyl",
            expectedSeason = 1,
            expectedEpisode = 1,
            expectedResolution = "1080p",
        )
    }

    @Test
    fun test_xtream_41_dune_2021() {
        assertParsed(
            raw = "Dune (2021) 2160p BluRay REMUX HEVC TrueHD Atmos",
            sourceHint = SourceHint.XTREAM,
            expectedTitle = "Dune",
            expectedYear = 2021,
            expectedResolution = "2160p",
        )
    }

    @Test
    fun test_xtream_42_shawshank_1994() {
        assertParsed(
            raw = "The Shawshank Redemption (1994) 1080p BluRay x264",
            sourceHint = SourceHint.XTREAM,
            expectedTitle = "The Shawshank Redemption",
            expectedYear = 1994,
            expectedResolution = "1080p",
        )
    }

    @Test
    fun test_xtream_43_prestige_2006() {
        assertParsed(
            raw = "The Prestige (2006) 1080p BluRay x265",
            sourceHint = SourceHint.XTREAM,
            expectedTitle = "The Prestige",
            expectedYear = 2006,
            expectedResolution = "1080p",
        )
    }

    @Test
    fun test_xtream_44_ring_2002() {
        assertParsed(
            raw = "The Ring (2002) 1080p BluRay x264",
            sourceHint = SourceHint.XTREAM,
            expectedTitle = "The Ring",
            expectedYear = 2002,
            expectedResolution = "1080p",
        )
    }

    @Test
    fun test_xtream_45_narcos_s01e01() {
        assertParsed(
            raw = "Narcos S01E01 1080p NF WEB-DL x264",
            sourceHint = SourceHint.XTREAM,
            expectedTitle = "Narcos",
            expectedSeason = 1,
            expectedEpisode = 1,
            expectedResolution = "1080p",
        )
    }

    @Test
    fun test_xtream_46_game_of_thrones_s03e09() {
        assertParsed(
            raw = "Game of Thrones S03E09 1080p BluRay x264 DTS-HD MA",
            sourceHint = SourceHint.XTREAM,
            expectedTitle = "Game of Thrones",
            expectedSeason = 3,
            expectedEpisode = 9,
            expectedResolution = "1080p",
        )
    }

    @Test
    fun test_xtream_47_sandman_s01e01() {
        assertParsed(
            raw = "The Sandman S01E01 1080p NF WEB-DL x264",
            sourceHint = SourceHint.XTREAM,
            expectedTitle = "The Sandman",
            expectedSeason = 1,
            expectedEpisode = 1,
            expectedResolution = "1080p",
        )
    }

    @Test
    fun test_xtream_48_expanse_with_tmdb_url() {
        assertParsed(
            raw = "The Expansе S03E06 themoviedb.org/tv/63639",
            sourceHint = SourceHint.XTREAM,
            expectedTitle = "The Expansе",
            expectedSeason = 3,
            expectedEpisode = 6,
            expectedTmdbId = 63639,
            expectedTmdbType = TmdbType.TV,
        )
    }

    @Test
    fun test_xtream_49_matrix_with_tmdb_url_prefix() {
        assertParsed(
            raw = "themoviedb.org/movie/603 - The Matrix (1999)",
            sourceHint = SourceHint.XTREAM,
            expectedTitle = "The Matrix",
            expectedYear = 1999,
            expectedTmdbId = 603,
            expectedTmdbType = TmdbType.MOVIE,
        )
    }

    @Test
    fun test_xtream_50_breaking_bad_with_tmdb_tag() {
        assertParsed(
            raw = "tmdb:1396 Breaking Bad S01E01 1080p",
            sourceHint = SourceHint.XTREAM,
            expectedTitle = "Breaking Bad",
            expectedSeason = 1,
            expectedEpisode = 1,
            expectedResolution = "1080p",
            expectedTmdbId = 1396,
        )
    }

    // ========== PERFORMANCE TEST ==========

    @Test
    fun test_performance_throughput() {
        // Simple throughput test - parse 100 strings 1000 times
        val testInputs =
            listOf(
                SceneNameInput("The.Matrix.1999.1080p.BluRay.x264", SourceHint.TELEGRAM),
                SceneNameInput("Breaking.Bad.S02E03.720p.WEB-DL.x265", SourceHint.TELEGRAM),
                SceneNameInput("Dune (2021) 2160p BluRay REMUX HEVC", SourceHint.XTREAM),
            )

        val iterations = 1000
        val startTime = System.nanoTime()

        repeat(iterations) {
            testInputs.forEach { input ->
                parser.parse(input)
            }
        }

        val elapsedMs = (System.nanoTime() - startTime) / 1_000_000
        val throughput = (iterations * testInputs.size) / (elapsedMs / 1000.0)

        println("Performance: ${iterations * testInputs.size} parses in ${elapsedMs}ms")
        println("Throughput: %.2f parses/second".format(throughput))

        // Assert reasonable performance (>1000 parses/sec)
        assertTrue(throughput > 1000, "Throughput too low: $throughput parses/sec")
    }
}
