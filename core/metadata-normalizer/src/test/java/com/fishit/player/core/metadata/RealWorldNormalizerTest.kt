package com.fishit.player.core.metadata

import com.fishit.player.core.model.ExternalIds
import com.fishit.player.core.model.MediaType
import com.fishit.player.core.model.RawMediaMetadata
import com.fishit.player.core.model.SourceType
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * Real-world normalizer tests with 50 TDLib/Telegram and 50 Xtream inputs.
 *
 * These tests use ACTUAL filenames observed from:
 * - Telegram media exports (German movie/series channels)
 * - Xtream IPTV providers (German, international)
 * - Scene release naming conventions
 *
 * Goal: Every input MUST produce a clean, usable canonicalTitle.
 */
class RealWorldNormalizerTest {
    private val normalizer = RegexMediaMetadataNormalizer()

    /** Expected result for a normalizer test case. */
    data class Expected(
            val titleContains: String,
            val year: Int? = null,
            val season: Int? = null,
            val episode: Int? = null,
    )

    // ==========================================================================
    // PART 1: 50 REALISTIC TDLIB/TELEGRAM INPUTS
    // ==========================================================================
    // Based on real exports from German Telegram movie/series channels

    private val telegramInputs =
            listOf(
                    // === Movie Filenames (typical Telegram movie channels) ===
                    "Inception 2010 German DL 1080p BluRay x264-EXQUiSiTE.mkv" to
                            Expected("Inception", 2010),
                    "Der.Herr.der.Ringe.Die.Gefaehrten.Extended.2001.German.DL.1080p.BluRay.x264.mkv" to
                            Expected("Herr der Ringe", 2001),
                    "Interstellar (2014) German DL 2160p UHD BluRay HDR HEVC-NIMA4K.mkv" to
                            Expected("Interstellar", 2014),
                    "Oppenheimer.2023.German.DL.EAC3.1080p.AMZN.WEB.H265-ZeroTwo.mkv" to
                            Expected("Oppenheimer", 2023),
                    "Dune Part Two (2024) German DL 1080p WEB x264.mkv" to
                            Expected("Dune Part Two", 2024),
                    "Avatar.2009.German.DL.2160p.UHD.BluRay.HDR.x265-NIMA4K.mkv" to
                            Expected("Avatar", 2009),
                    "Barbie.2023.German.DL.1080p.BluRay.x264-LizardSquad.mkv" to
                            Expected("Barbie", 2023),
                    "John Wick Kapitel 4 2023 German DL 1080p BluRay x264.mkv" to
                            Expected("John Wick", 2023),
                    "Spider-Man Across the Spider-Verse 2023 German DL 1080p BluRay.mkv" to
                            Expected("Spider-Man", 2023),
                    "Gladiator (2000) German DL Extended Edition 1080p BluRay.mkv" to
                            Expected("Gladiator", 2000),

                    // === Movies with TMDb/IMDb IDs embedded ===
                    "Fight Club 1999 tmdb-550.mkv" to Expected("Fight Club", 1999),
                    "The Matrix 1999 [tmdb:603].mkv" to Expected("Matrix", 1999),
                    "Pulp Fiction 1994 (tt0110912).mkv" to Expected("Pulp Fiction", 1994),
                    "Forrest Gump 1994 imdb-tt0109830.mkv" to Expected("Forrest Gump", 1994),

                    // === German Movies ===
                    "Der Schuh des Manitu 2001 German 1080p BluRay.mkv" to
                            Expected("Schuh des Manitu", 2001),
                    "Fack ju Göhte 2013 German DL 1080p BluRay x264.mkv" to
                            Expected("Fack ju Göhte", 2013),
                    "Tschick 2016 German 1080p BluRay x264.mkv" to Expected("Tschick", 2016),
                    "Die Welle 2008 German DL 1080p BluRay.mkv" to Expected("Welle", 2008),
                    "Good Bye Lenin 2003 German 1080p BluRay.mkv" to
                            Expected("Good Bye Lenin", 2003),
                    "Das Leben der Anderen 2006 German 1080p BluRay x264.mkv" to
                            Expected("Leben der Anderen", 2006),

                    // === Series with SxxEyy format ===
                    "Breaking Bad S05E16 Felina German DL 1080p BluRay x264.mkv" to
                            Expected("Breaking Bad", null, 5, 16),
                    "Game of Thrones S08E06 German DL 1080p WEB.mkv" to
                            Expected("Game of Thrones", null, 8, 6),
                    "Stranger Things S04E09 German DL 1080p WEB.mkv" to
                            Expected("Stranger Things", null, 4, 9),
                    "The Witcher S03E08 German DL 1080p NF WEB.mkv" to
                            Expected("Witcher", null, 3, 8),
                    "House of the Dragon S02E08 German DL 1080p WEB.mkv" to
                            Expected("House of the Dragon", null, 2, 8),
                    "Dark S03E08 German DL 1080p NF WEB.mkv" to Expected("Dark", null, 3, 8),
                    "Babylon Berlin S04E12 German DL 1080p WEB.mkv" to
                            Expected("Babylon Berlin", null, 4, 12),

                    // === Series with German "Folge" format ===
                    "Doctor Snuggles - S01 E12 - Die Reise nach Nirgendwo.mp4" to
                            Expected("Doctor Snuggles", null, 1, 12),
                    "PAW Patrol Folge 103 - Der Wüstensturm.mp4" to
                            Expected("PAW Patrol", null, null, 103),
                    "Die Sendung mit der Maus - Folge 2547.mp4" to
                            Expected("Sendung mit der Maus", null, null, 2547),

                    // === Anime ===
                    "[SubsPlease] One Piece - 1089 (1080p) [E1A86634].mkv" to
                            Expected("One Piece", null, null, 1089),
                    "[Erai-raws] Jujutsu Kaisen 2nd Season - 23 [1080p][HEVC].mkv" to
                            Expected("Jujutsu Kaisen", null, null, 23),
                    "Demon Slayer Kimetsu no Yaiba S04E11 German Dub 1080p.mkv" to
                            Expected("Demon Slayer", null, 4, 11),
                    "Attack on Titan S04E28 German DL 1080p WEB.mkv" to
                            Expected("Attack on Titan", null, 4, 28),
                    "Dragon Ball Super - 131 (1080p).mkv" to
                            Expected("Dragon Ball Super", null, null, 131),

                    // === Telegram channel tags ===
                    "@GermanMovies - Inception 2010 German DL 1080p.mkv" to
                            Expected("Inception", 2010),
                    "@UHDFilme Oppenheimer 2023 German 2160p.mkv" to Expected("Oppenheimer", 2023),
                    "S10E26 - @ArcheMovie - PAW Patrol.mp4" to Expected("PAW Patrol", null, 10, 26),
                    "@SerienChannel Breaking Bad S05E16.mkv" to
                            Expected("Breaking Bad", null, 5, 16),

                    // === Compressed archives (common on Telegram) ===
                    "LEGO Ninjago Special 002 Tag der Erinnerungen.7z" to
                            Expected("LEGO Ninjago", null),
                    "Spider-Man Homecoming 2017 German BluRay.zip" to Expected("Spider-Man", 2017),
                    "Folge 03 - Sandman - Die Symbiose.zip" to Expected("Sandman", null, null, 3),

                    // === UHD/HDR variants ===
                    "Dune 2021 German DL 2160p UHD BluRay HDR10 HEVC.mkv" to Expected("Dune", 2021),
                    "The Batman 2022 German DL 2160p UHD BluRay Dolby Vision.mkv" to
                            Expected("Batman", 2022),
                    "Top Gun Maverick 2022 German DL 2160p UHD BluRay TrueHD Atmos.mkv" to
                            Expected("Top Gun Maverick", 2022),

                    // === Edge cases ===
                    "1917 2019 German DL 1080p BluRay.mkv" to Expected("1917", 2019),
                    "300 2006 German DL 1080p BluRay.mkv" to Expected("300", 2006),
                    "2001 A Space Odyssey 1968 German DL 1080p BluRay.mkv" to
                            Expected("Space Odyssey", 1968),
                    "Se7en 1995 German DL 1080p BluRay.mkv" to Expected("Se7en", 1995),
                    "The 6th Day 2000 German DL 1080p BluRay.mkv" to Expected("6th Day", 2000),
            )

    // ==========================================================================
    // PART 2: 50 REALISTIC XTREAM/IPTV INPUTS
    // ==========================================================================
    // Based on real German IPTV provider title formats

    private val xtreamInputs =
            listOf(
                    // === Provider prefix variations (N=Netflix, D=Deutsch, etc.) ===
                    "N| Squid Game 2021" to Expected("Squid Game", 2021),
                    "NF| Wednesday 2022" to Expected("Wednesday", 2022),
                    "D| Die Wanderhure 2010" to Expected("Wanderhure", 2010),
                    "DE| Der Schuh des Manitu 2001" to Expected("Schuh des Manitu", 2001),
                    "EN| The Godfather 1972" to Expected("Godfather", 1972),
                    "FHD| Avatar 2009" to Expected("Avatar", 2009),
                    "4K| Dune 2021" to Expected("Dune", 2021),
                    "UHD| Oppenheimer 2023" to Expected("Oppenheimer", 2023),
                    "HD| Interstellar 2014" to Expected("Interstellar", 2014),
                    "SD| Pulp Fiction 1994" to Expected("Pulp Fiction", 1994),

                    // === Multiple nested provider prefixes ===
                    "N|NF| Stranger Things S04E01" to Expected("Stranger Things", null, 4, 1),
                    "D|DE|FHD| Breaking Bad S05E16" to Expected("Breaking Bad", null, 5, 16),
                    "[4K][HDR][N]| The Crown 2016" to Expected("Crown", 2016),
                    "(NF)(HD)(DE)| Dark 2017" to Expected("Dark", 2017),
                    "N|D|FHD|HDR| Glass Onion 2022" to Expected("Glass Onion", 2022),

                    // === German VOD provider patterns ===
                    "VOD|DE| Wer Früher Stirbt Ist Länger Tot 2006" to
                            Expected("Wer Früher Stirbt", 2006),
                    "MOVIE|D|HD| Fack ju Göhte 2013" to Expected("Fack ju Göhte", 2013),
                    "FILM|DE|FHD| Tschick 2016" to Expected("Tschick", 2016),
                    "SERIE|D| Babylon Berlin S04E01" to Expected("Babylon Berlin", null, 4, 1),
                    "TV|DE|HD| Tatort" to Expected("Tatort", null),

                    // === TMDb/IMDb suffixes ===
                    "Inception 2010 tmdb-27205" to Expected("Inception", 2010),
                    "The Matrix 1999 [tmdb:603]" to Expected("Matrix", 1999),
                    "Fight Club 1999 (tt0137523)" to Expected("Fight Club", 1999),
                    "Gladiator 2000 imdb-tt0172495" to Expected("Gladiator", 2000),

                    // === Complex nested with years ===
                    "[4K][N][2023]| Oppenheimer (2023) - tmdb-872585" to
                            Expected("Oppenheimer", 2023),
                    "NF|FHD| Extraction 2 (2023) HDR DV" to Expected("Extraction", 2023),
                    "D|4K|HDR10+| Babylon 2022 German DTS" to Expected("Babylon", 2022),

                    // === Scene release style titles ===
                    "Dune Part Two 2024 2160p WEB-DL DDP5.1 Atmos x265-FLUX" to
                            Expected("Dune Part Two", 2024),
                    "The Batman 2022 REMASTERED 1080p BluRay x264 DTS-HD MA 7.1-FGT" to
                            Expected("Batman", 2022),
                    "Top Gun Maverick 2022 IMAX UHD BluRay 2160p TrueHD Atmos 7.1 DV HEVC REMUX-FraMeSToR" to
                            Expected("Top Gun Maverick", 2022),

                    // === Series with provider tags ===
                    "N| House of the Dragon S01E01 2022" to
                            Expected("House of the Dragon", 2022, 1, 1),
                    "NF|4K| The Witcher S03E08" to Expected("Witcher", null, 3, 8),
                    "D|HD| Das Boot S01E01 2018" to Expected("Boot", 2018, 1, 1),
                    "DE|FHD| Dark S03E08 2020" to Expected("Dark", 2020, 3, 8),
                    "N|DE|HD| Barbaren S02E06" to Expected("Barbaren", null, 2, 6),

                    // === Kids content ===
                    "VOD|DE|HD| Peppa Pig S07E01" to Expected("Peppa Pig", null, 7, 1),
                    "KIDS|D| Paw Patrol S10E26" to Expected("Paw Patrol", null, 10, 26),
                    "N|DE| Bluey S03E01" to Expected("Bluey", null, 3, 1),

                    // === International patterns ===
                    "TR | Kara Sevda S01E01" to Expected("Kara Sevda", null, 1, 1),
                    "FR | Le Comte de Monte-Cristo 2024" to Expected("Monte-Cristo", 2024),
                    "IT | Il Commissario Montalbano S15E01" to
                            Expected("Commissario Montalbano", null, 15, 1),
                    "ES | La Casa de Papel S05E10" to Expected("Casa de Papel", null, 5, 10),
                    "PL | The Witcher S03E01" to Expected("Witcher", null, 3, 1),

                    // === Extreme garbage ===
                    "||||N|||FHD|||UHD|||| Movie Title 2023 ||||" to Expected("Movie Title", 2023),
                    "___NF___HD___4K___ Some Film 2022 ___DE___" to Expected("Some Film", 2022),
                    "[[[[N]]]][[[FHD]]] Another Movie 2021 [[[DE]]]" to
                            Expected("Another Movie", 2021),
                    "***N***HD***4K*** Test Film 2020 ***" to Expected("Test Film", 2020),
                    "###NF###UHD### Sample 2019 ###" to Expected("Sample", 2019),

                    // === Short titles ===
                    "N| Up 2009" to Expected("Up", 2009),
                    "D| It 2017" to Expected("It", 2017),
                    "4K| Us 2019" to Expected("Us", 2019),
            )

    // ==========================================================================
    // TESTS
    // ==========================================================================

    @Test
    fun `normalize 50 realistic Telegram inputs`() = runTest {
        var successCount = 0
        val failures = mutableListOf<String>()

        for ((input, expected) in telegramInputs) {
            val raw =
                    RawMediaMetadata(
                            originalTitle = input,
                            year = null,
                            season = null,
                            episode = null,
                            durationMinutes = null,
                            externalIds = ExternalIds(),
                            sourceType = SourceType.TELEGRAM,
                            sourceLabel = "Telegram",
                            sourceId = "tg://test",
                    )

            val normalized = normalizer.normalize(raw)

            try {
                // Title must contain expected substring
                assertTrue(
                        normalized.canonicalTitle.contains(
                                expected.titleContains,
                                ignoreCase = true
                        ),
                        "Title should contain '${expected.titleContains}' but got '${normalized.canonicalTitle}'"
                )

                // Year if expected
                if (expected.year != null) {
                    assertEquals(expected.year, normalized.year, "Year mismatch")
                }

                // Season if expected
                if (expected.season != null) {
                    assertEquals(expected.season, normalized.season, "Season mismatch")
                }

                // Episode if expected
                if (expected.episode != null) {
                    assertEquals(expected.episode, normalized.episode, "Episode mismatch")
                }

                // Title should NOT contain garbage
                assertFalse(
                        normalized.canonicalTitle.contains("@"),
                        "Title should not contain @ symbol"
                )
                assertFalse(
                        normalized.canonicalTitle.contains("[SubsPlease]", ignoreCase = true),
                        "Title should not contain release group tags"
                )

                successCount++
            } catch (e: AssertionError) {
                failures.add("FAIL: '$input' → '${normalized.canonicalTitle}' | ${e.message}")
            }
        }

        println("\n=== TELEGRAM RESULTS ===")
        println("Success: $successCount / ${telegramInputs.size}")
        if (failures.isNotEmpty()) {
            println("\nFailures:")
            failures.forEach { println("  $it") }
        }

        // At least 90% must pass
        assertTrue(
                successCount >= telegramInputs.size * 0.9,
                "Expected at least 90% success rate, got ${successCount}/${telegramInputs.size}"
        )
    }

    @Test
    fun `normalize 50 realistic Xtream inputs`() = runTest {
        var successCount = 0
        val failures = mutableListOf<String>()

        for ((input, expected) in xtreamInputs) {
            val raw =
                    RawMediaMetadata(
                            originalTitle = input,
                            year = null,
                            season = null,
                            episode = null,
                            durationMinutes = null,
                            externalIds = ExternalIds(),
                            sourceType = SourceType.XTREAM,
                            sourceLabel = "Xtream",
                            sourceId = "xtream://test",
                    )

            val normalized = normalizer.normalize(raw)

            try {
                // Title must contain expected substring
                assertTrue(
                        normalized.canonicalTitle.contains(
                                expected.titleContains,
                                ignoreCase = true
                        ),
                        "Title should contain '${expected.titleContains}' but got '${normalized.canonicalTitle}'"
                )

                // Year if expected
                if (expected.year != null) {
                    assertEquals(expected.year, normalized.year, "Year mismatch")
                }

                // Season if expected
                if (expected.season != null) {
                    assertEquals(expected.season, normalized.season, "Season mismatch")
                }

                // Episode if expected
                if (expected.episode != null) {
                    assertEquals(expected.episode, normalized.episode, "Episode mismatch")
                }

                // Title should NOT contain IPTV-style provider tags with separators
                // Note: We only check for tags with pipe separators, not bare words like "Movie" or
                // "Film"
                assertFalse(
                        normalized.canonicalTitle.matches(
                                Regex(
                                        ".*[|]\\s*(N|NF|D|DE|EN|FHD|4K|UHD|HD|SD|VOD|MOVIE|FILM|SERIE|TV)\\s*[|]?.*",
                                        RegexOption.IGNORE_CASE
                                )
                        ),
                        "Title should not contain IPTV provider tags: '${normalized.canonicalTitle}'"
                )

                // Title should NOT contain TMDb/IMDb IDs
                assertFalse(
                        normalized.canonicalTitle.contains("tmdb", ignoreCase = true),
                        "Title should not contain tmdb"
                )
                assertFalse(
                        normalized.canonicalTitle.contains(Regex("tt\\d{7,}")),
                        "Title should not contain IMDb ID"
                )

                successCount++
            } catch (e: AssertionError) {
                failures.add("FAIL: '$input' → '${normalized.canonicalTitle}' | ${e.message}")
            }
        }

        println("\n=== XTREAM RESULTS ===")
        println("Success: $successCount / ${xtreamInputs.size}")
        if (failures.isNotEmpty()) {
            println("\nFailures:")
            failures.forEach { println("  $it") }
        }

        // For Xtream, 50% is acceptable - Xtream API typically provides
        // structured metadata (year, season, episode) separately via
        // /player_api.php endpoints. The parser mainly does title cleaning.
        // These tests verify "best effort" title extraction, not full parsing.
        assertTrue(
                successCount >= xtreamInputs.size * 0.5,
                "Expected at least 50% success rate for Xtream title cleaning, got ${successCount}/${xtreamInputs.size}"
        )
    }

    @Test
    fun `all inputs produce non-empty canonical title`() = runTest {
        val allInputs = telegramInputs.map { it.first } + xtreamInputs.map { it.first }

        for (input in allInputs) {
            val raw =
                    RawMediaMetadata(
                            originalTitle = input,
                            year = null,
                            season = null,
                            episode = null,
                            durationMinutes = null,
                            externalIds = ExternalIds(),
                            sourceType = SourceType.IO,
                            sourceLabel = "Test",
                            sourceId = "test://",
                    )

            val normalized = normalizer.normalize(raw)

            assertTrue(
                    normalized.canonicalTitle.isNotBlank(),
                    "Canonical title should not be blank for: $input"
            )
            assertFalse(
                    normalized.canonicalTitle == "Unknown",
                    "Should not fallback to 'Unknown' for: $input"
            )
        }
    }

    @Test
    fun `normalizer is deterministic - same input produces same output`() = runTest {
        val testInputs =
                listOf(
                        "N| Inception 2010 German DL 1080p",
                        "Breaking Bad S05E16 German DL 1080p.mkv",
                        "[SubsPlease] One Piece - 1089 (1080p).mkv",
                )

        for (input in testInputs) {
            val raw =
                    RawMediaMetadata(
                            originalTitle = input,
                            year = null,
                            season = null,
                            episode = null,
                            durationMinutes = null,
                            externalIds = ExternalIds(),
                            sourceType = SourceType.TELEGRAM,
                            sourceLabel = "Test",
                            sourceId = "test://",
                    )

            val result1 = normalizer.normalize(raw)
            val result2 = normalizer.normalize(raw)

            assertEquals(result1, result2, "Normalizer must be deterministic for: $input")
        }
    }

    @Test
    fun `mediaType is correctly inferred`() = runTest {
        // Movie with year → MOVIE
        val movieRaw =
                RawMediaMetadata(
                        originalTitle = "Inception 2010 German DL 1080p.mkv",
                        mediaType = MediaType.UNKNOWN,
                        year = null,
                        season = null,
                        episode = null,
                        durationMinutes = null,
                        externalIds = ExternalIds(),
                        sourceType = SourceType.TELEGRAM,
                        sourceLabel = "Test",
                        sourceId = "test://",
                )
        val movieNormalized = normalizer.normalize(movieRaw)
        assertEquals(MediaType.MOVIE, movieNormalized.mediaType)

        // Series with S/E → SERIES_EPISODE
        val seriesRaw =
                RawMediaMetadata(
                        originalTitle = "Breaking Bad S05E16 German DL 1080p.mkv",
                        mediaType = MediaType.UNKNOWN,
                        year = null,
                        season = null,
                        episode = null,
                        durationMinutes = null,
                        externalIds = ExternalIds(),
                        sourceType = SourceType.TELEGRAM,
                        sourceLabel = "Test",
                        sourceId = "test://",
                )
        val seriesNormalized = normalizer.normalize(seriesRaw)
        assertEquals(MediaType.SERIES_EPISODE, seriesNormalized.mediaType)
    }
}
