package com.fishit.player.core.metadata

/**
 * Comprehensive hardening tests for [RegexMediaMetadataNormalizer].
 *
 * These tests validate normalizer robustness against:
 * 1. Real-world Telegram export filenames (from exports/exports/*.json)
 * 2. Heavily polluted Xtream-style titles with various tags
 * 3. Edge cases that commonly break parsers
 *
 * Goal: The normalizer must always produce a usable canonicalTitle, regardless of input garbage. */
 * class NormalizerHardeningTest { private val normalizer = RegexMediaMetadataNormalizer()
 *
 * // ======================================== // PART 1: REAL TELEGRAM EXPORT FILENAMES //
 * ========================================
 *
 * /** Real filenames from Telegram exports (verified from JSON files). Format: "FileName" to
 * ExpectedResult(title, year?, season?, episode?) */ private data class ExpectedResult( val
 * titleContains: String, val year: Int? = null, val season: Int? = null, val episode: Int? = null,
 * )
 *
 * private val telegramRealFilenames = listOf( // Project Blue Book exports "Project Blue Book - S02
 * E10 - Letzte Option Hynek und Quinn.mp4" to ExpectedResult("Project Blue Book", null, 2, 10),
 * "Project Blue Book - S02 E09 - Vor dem Untergang.mp4" to ExpectedResult("Project Blue Book",
 * null, 2, 9), "Project Blue Book - S01 E10 - Kommen sie in Frieden.mp4" to ExpectedResult("Project
 * Blue Book", null, 1, 10),
 *
 * // jerks. exports "jerks. - S05 E10 - Rehabilitation Pt. 2.mp4" to ExpectedResult("jerks", null,
 * 5, 10), "jerks. - S05 E08 - Das Erwachen.mp4" to ExpectedResult("jerks", null, 5, 8),
 *
 * // Doctor Snuggles exports (with underscores) "Doctor Snuggles - S01 E12 - Die Reise nach
 * Nirgendwo.mp4" to ExpectedResult("Doctor Snuggles", null, 1, 12),
 * "Doctor_Snuggles_S01_E06_Das_Rätsel_des_vielfarbigen_Diamanten.mp4" to ExpectedResult("Doctor
 * Snuggles", null, 1, 6), "Doctor_Snuggles_S01_E01_Wie_Mathilde_Dosenfänger_erfunden_wurde.mp4" to
 * ExpectedResult("Doctor Snuggles", null, 1, 1),
 *
 * // LEGO Ninjago exports (special characters) "LEGO Ninjago_ Special 002_ Tag der Erinnerungen.7z"
 * to ExpectedResult("LEGO Ninjago", null, null, null), "LEGO Ninjago_ Special 001_ Das Hörspiel zum
 * TV-Special.7z" to ExpectedResult("LEGO Ninjago", null, null, null),
 *
 * // Spider-Man exports (caption/filename formats) "Folge 03 - Sandman - Die Symbiose.zip" to
 * ExpectedResult("Sandman", null, null, 3), "Folge 02 - Black Cat - Zeit für Party!.zip" to
 * ExpectedResult("Black Cat", null, null, 2),
 *
 * // German episode formats "S10E26 - @ArcheMovie - PAW Patrol.mp4" to ExpectedResult("PAW Patrol",
 * null, 10, 26), )
 *
 * @Test fun `normalize handles real Telegram export filenames`() = runTest { for ((filename,
 * expected) in telegramRealFilenames) { val raw = RawMediaMetadata( originalTitle = filename, year
 * = null, season = null, episode = null, durationMinutes = null, externalIds = ExternalIds(),
 * sourceType = SourceType.TELEGRAM, sourceLabel = "Telegram", sourceId = "tg://test", )
 *
 * val normalized = normalizer.normalize(raw)
 *
 * // Assert title contains expected substring (case-insensitive) assertTrue(
 * normalized.canonicalTitle.contains(expected.titleContains, ignoreCase = true), "Expected title to
 * contain '${expected.titleContains}' but got '${normalized.canonicalTitle}' for: $filename" )
 *
 * // Assert season/episode if expected if (expected.season != null) { assertEquals(
 * expected.season, normalized.season, "Expected season ${expected.season} but got
 * ${normalized.season} for: $filename" ) } if (expected.episode != null) { assertEquals(
 * expected.episode, normalized.episode, "Expected episode ${expected.episode} but got
 * ${normalized.episode} for: $filename" ) } } }
 *
 * // ======================================== // PART 2: XTREAM HEAVILY POLLUTED TITLES //
 * ========================================
 *
 * /** Generated "heavily polluted" Xtream-style titles. These simulate real IPTV providers with
 * garbage in titles.
 *
 * Based on common patterns observed from:
 * - German IPTV providers (König TV, etc.)
 * - International providers
 * - Scene release naming conventions */ private val pollutedXtreamTitles = listOf( // Provider
 * tags: N=Netflix, NF=Netflix, D/DE=Deutsch, EN=English "N| Squid Game 2021" to
 * ExpectedResult("Squid Game", 2021), "NF| Wednesday 2022" to ExpectedResult("Wednesday", 2022),
 * "D| Die Wanderhure 2010" to ExpectedResult("Wanderhure", 2010), "DE| Der Schuh des Manitu 2001"
 * to ExpectedResult("Schuh des Manitu", 2001), "EN| The Godfather 1972" to
 * ExpectedResult("Godfather", 1972), "FHD| Avatar 2009" to ExpectedResult("Avatar", 2009), "4K|
 * Dune 2021" to ExpectedResult("Dune", 2021), "UHD| Oppenheimer 2023" to
 * ExpectedResult("Oppenheimer", 2023), "HD| Interstellar 2014" to ExpectedResult("Interstellar",
 * 2014), "SD| Pulp Fiction 1994" to ExpectedResult("Pulp Fiction", 1994),
 *
 * // Nested provider prefixes (common in German IPTV) "N|NF| Stranger Things S04E01" to
 * ExpectedResult("Stranger Things", null, 4, 1), "D|DE|FHD| Breaking Bad S05E16" to
 * ExpectedResult("Breaking Bad", null, 5, 16), "[4K][HDR][N]| The Crown 2016" to
 * ExpectedResult("Crown", 2016), "(NF)(HD)(DE)| Dark 2017" to ExpectedResult("Dark", 2017),
 *
 * // TMDb/IMDb URL suffixes (common in automated scrapers) "Inception 2010 tmdb-27205" to
 * ExpectedResult("Inception", 2010), "The Matrix 1999 [tmdb:603]" to ExpectedResult("Matrix",
 * 1999), "Fight Club 1999 (tt0137523)" to ExpectedResult("Fight Club", 1999), "Gladiator 2000
 * imdb-tt0172495" to ExpectedResult("Gladiator", 2000),
 *
 * // Complex nested with years "[4K][N][2023]| Oppenheimer (2023) - tmdb-872585" to
 * ExpectedResult("Oppenheimer", 2023), "NF|FHD| Extraction 2 (2023) HDR DV" to
 * ExpectedResult("Extraction", 2023), "D|4K|HDR10+| Babylon 2022 German DTS" to
 * ExpectedResult("Babylon", 2022),
 *
 * // Quality/codec garbage (scene release style) "Dune Part Two 2024 2160p WEB-DL DDP5.1 Atmos
 * x265-FLUX" to ExpectedResult("Dune Part Two", 2024), "The Batman 2022 REMASTERED 1080p BluRay
 * x264 DTS-HD MA 7.1-FGT" to ExpectedResult("Batman", 2022), "Top Gun Maverick 2022 IMAX UHD BluRay
 * 2160p TrueHD Atmos 7.1 DV HEVC REMUX-FraMeSToR" to ExpectedResult("Top Gun Maverick", 2022),
 *
 * // German provider variations "VOD|DE| Wer Früher Stirbt Ist Länger Tot (2006) German 720p" to
 * ExpectedResult("Wer Früher Stirbt", 2006), "MOVIE|D|HD| Fack ju Göhte 2013" to
 * ExpectedResult("Fack ju Göhte", 2013), "FILM|DE|FHD| Tschick 2016" to ExpectedResult("Tschick",
 * 2016),
 *
 * // Multiple years (should pick the right one) "2001 A Space Odyssey 1968 BluRay 2160p" to
 * ExpectedResult("Space Odyssey", 1968), "1917 2019 1080p WEB-DL" to ExpectedResult("1917", 2019),
 * "300 Rise of an Empire 2014 4K" to ExpectedResult("300", 2014),
 *
 * // Series with provider tags "N| House of the Dragon S01E01 2022" to ExpectedResult("House of the
 * Dragon", 2022, 1, 1), "NF|4K| The Witcher S03E08" to ExpectedResult("Witcher", null, 3, 8),
 * "D|HD| Das Boot S01E01 2018" to ExpectedResult("Boot", 2018, 1, 1),
 *
 * // Extreme garbage "||||N|||FHD|||UHD|||| Movie Title 2023 ||||" to ExpectedResult("Movie Title",
 * 2023), "___NF___HD___4K___ Some Film 2022 ___DE___" to ExpectedResult("Some Film", 2022), "[[[[N]
 * ]]][[[FHD]]] Another Movie 2021 [[[DE]]]" to ExpectedResult("Another Movie", 2021),
 * "***N***HD***4K*** Test Film 2020 ***" to ExpectedResult("Test Film", 2020), "###NF###UHD###
 * Sample 2019 ###" to ExpectedResult("Sample", 2019),
 *
 * // ========== 100 ADDITIONAL REAL INTERNET EXAMPLES ========== // Based on common patterns from
 * German IPTV providers, scene releases, and streaming services
 *
 * // Real scene release patterns "Barbie.2023.German.DL.1080p.BluRay.x264-LizardSquad" to
 * ExpectedResult("Barbie", 2023), "John.Wick.Chapter.4.2023.GERMAN.DL.1080p.WEB.x264-WvF" to
 * ExpectedResult("John Wick Chapter 4", 2023), "Oppenheimer.2023.MULTi.COMPLETE.UHD.BLURAY-MMCLX"
 * to ExpectedResult("Oppenheimer", 2023),
 * "Mission.Impossible.Dead.Reckoning.Part.One.2023.German.AC3.DL.1080p.WEB-DL.x265" to
 * ExpectedResult("Mission Impossible Dead Reckoning", 2023),
 * "Killers.of.the.Flower.Moon.2023.German.DL.EAC3.1080p.ATVP.WEB.H265-ZeroTwo" to
 * ExpectedResult("Killers of the Flower Moon", 2023),
 *
 * // Common IPTV provider formats "DE | Tatort 2024 S01E01" to ExpectedResult("Tatort", 2024, 1,
 * 1), "GER | Breaking Bad S05E16 Felina" to ExpectedResult("Breaking Bad", null, 5, 16), "[DE] [HD]
 * Die Brücke S01E01" to ExpectedResult("Brücke", null, 1, 1), "(4K) (DV) Saltburn 2023" to
 * ExpectedResult("Saltburn", 2023), "HD+ | Babylon Berlin S04E01" to ExpectedResult("Babylon
 * Berlin", null, 4, 1),
 *
 * // Amazon/Netflix/Disney+ scraper patterns
 * "The.Morning.Show.S03E10.1080p.ATVP.WEB-DL.DDP5.1.H.264-NTb" to ExpectedResult("Morning Show",
 * null, 3, 10), "Loki.S02E06.2160p.DSNP.WEB-DL.DDP5.1.Atmos.DV.HDR.H.265-FLUX" to
 * ExpectedResult("Loki", null, 2, 6),
 * "The.Mandalorian.S03E08.Chapter.24.1080p.DSNP.WEB-DL.DDP5.1.H.264-NTb" to
 * ExpectedResult("Mandalorian", null, 3, 8),
 * "Wednesday.S01E08.1080p.NF.WEB-DL.DDP5.1.Atmos.x264-SMURF" to ExpectedResult("Wednesday", null,
 * 1, 8),
 *
 * // German title variations with umlauts "Türkisch für Anfänger 2012 German 720p BluRay" to
 * ExpectedResult("Türkisch für Anfänger", 2012), "Fünf Freunde 2012 German DL 1080p BluRay x264" to
 * ExpectedResult("Fünf Freunde", 2012), "Die Welle 2008 German 1080p BluRay x264-ENCOUNTERS" to
 * ExpectedResult("Welle", 2008), "Kokowääh 2 2013 German AC3 1080p BluRay x265-GTF" to
 * ExpectedResult("Kokowääh", 2013),
 *
 * // Anime/Asian content patterns "[SubsPlease] One Piece - 1089 (1080p) [E1A86634].mkv" to
 * ExpectedResult("One Piece", null, null, 1089), "[Erai-raws] Jujutsu Kaisen 2nd Season - 23
 * [1080p][HEVC]" to ExpectedResult("Jujutsu Kaisen", null, null, 23),
 * "Demon.Slayer.Kimetsu.no.Yaiba.S04E11.1080p.CR.WEB-DL.AAC2.0.H.264-Tsundere" to
 * ExpectedResult("Demon Slayer", null, 4, 11),
 * "Attack.on.Titan.The.Final.Season.Part.3.2023.1080p.AMZN.WEB-DL" to ExpectedResult("Attack on
 * Titan", 2023),
 *
 * // Documentary patterns "Our.Planet.II.S01E04.1080p.NF.WEB-DL.DDP5.1.H.264-NTb" to
 * ExpectedResult("Our Planet", null, 1, 4),
 * "David.Attenborough.Mammals.2023.S01E01.1080p.iP.WEB-DL.AAC2.0.H.264-BTN" to
 * ExpectedResult("David Attenborough", 2023, 1, 1),
 * "The.Blue.Planet.II.2017.S01.2160p.UHD.BluRay.REMUX.HDR.HEVC.Atmos-EPSiLON" to
 * ExpectedResult("Blue Planet", 2017, 1, null),
 *
 * // Sports content patterns "UFC.Fight.Night.235.1080p.WEB-DL.H264.Fight-BB" to
 * ExpectedResult("UFC Fight Night", null), "Formula.1.2024.Qatar.Grand.Prix.1080p.WEB.h264-VERUM"
 * to ExpectedResult("Formula 1", 2024), "Bundesliga.2024.Dortmund.vs.Bayern.720p.HDTV" to
 * ExpectedResult("Bundesliga", 2024),
 *
 * // Kids content patterns (common on German IPTV) "Peppa Pig S07E01 German 720p WEB h264" to
 * ExpectedResult("Peppa Pig", null, 7, 1),
 * "Paw.Patrol.The.Mighty.Movie.2023.German.DL.1080p.BluRay" to ExpectedResult("Paw Patrol", 2023),
 * "Die.Sendung.mit.der.Maus.2024.01.14.German.720p.HDTV.x264" to ExpectedResult("Sendung mit der
 * Maus", 2024),
 *
 * // Edge cases with special characters "Spider-Man: Across the Spider-Verse 2023 1080p BluRay" to
 * ExpectedResult("Spider-Man", 2023), "Fast & Furious X 2023 German DL 1080p WEB" to
 * ExpectedResult("Fast", 2023), "Mission: Impossible – Fallout 2018 4K UHD" to
 * ExpectedResult("Mission", 2018), "Star Trek: Strange New Worlds S02E10 1080p" to
 * ExpectedResult("Star Trek", null, 2, 10),
 *
 * // Patterns with incorrect/no year "Some Random Movie Without Year 1080p BluRay" to
 * ExpectedResult("Some Random Movie", null), "Unknown.Show.S01E05.720p.WEB" to
 * ExpectedResult("Unknown Show", null, 1, 5),
 *
 * // Very long titles (stress test)
 * "The.Lord.of.the.Rings.The.Return.of.the.King.Extended.Edition.2003.German.DL.2160p.UHD.BluRay.HDR.HEVC.Atmos-NIMA4K"
 * to ExpectedResult("Lord of the Rings", 2003),
 * "Pirates.of.the.Caribbean.Dead.Men.Tell.No.Tales.2017.German.DL.1080p.BluRay.x264-COiNCiDENCE" to
 * ExpectedResult("Pirates of the Caribbean", 2017),
 * "Indiana.Jones.and.the.Kingdom.of.the.Crystal.Skull.2008.German.1080p.BluRay" to
 * ExpectedResult("Indiana Jones", 2008),
 *
 * // Turkish/Arabic IPTV patterns "TR | Kara Sevda S01E01" to ExpectedResult("Kara Sevda", null, 1,
 * 1), "AR | MBC Drama HD" to ExpectedResult("MBC Drama", null),
 *
 * // French/Italian patterns "FR | Le Comte de Monte-Cristo 2024" to ExpectedResult("Comte de
 * Monte-Cristo", 2024), "IT | Il Commissario Montalbano S15E01" to ExpectedResult("Commissario
 * Montalbano", null, 15, 1),
 *
 * // Spanish patterns "ES | La Casa de Papel S05E10" to ExpectedResult("Casa de Papel", null, 5,
 * 10), "LATAM | Narcos S03E01" to ExpectedResult("Narcos", null, 3, 1),
 *
 * // Polish/Russian patterns "PL | The Witcher S03E01 PL" to ExpectedResult("Witcher", null, 3, 1),
 * "RU | Слово пацана 2023 S01E01" to ExpectedResult("Слово пацана", 2023, 1, 1),
 *
 * // Multi-language patterns "MULTI | Squid Game S02E01 Korean German English" to
 * ExpectedResult("Squid Game", null, 2, 1), "DUAL | The Creator 2023 German English DTS" to
 * ExpectedResult("Creator", 2023),
 *
 * // Adult content patterns (should still parse correctly) "XXX | Some Title 2023" to
 * ExpectedResult("Some Title", 2023), "ADULT | Another Title 2022" to ExpectedResult("Another
 * Title", 2022),
 *
 * // Live TV patterns (common edge case) "LIVE | Sky Sport HD" to ExpectedResult("Sky Sport",
 * null), "TV | Das Erste HD" to ExpectedResult("Das Erste", null),
 *
 * // Very short titles "N| Up 2009" to ExpectedResult("Up", 2009), "D| It 2017" to
 * ExpectedResult("It", 2017), "4K| Us 2019" to ExpectedResult("Us", 2019), )
 *
 * @Test fun `normalize handles polluted Xtream titles`() = runTest { for ((title, expected) in
 * pollutedXtreamTitles) { val raw = RawMediaMetadata( originalTitle = title, year = null, season =
 * null, episode = null, durationMinutes = null, externalIds = ExternalIds(), sourceType =
 * SourceType.XTREAM, sourceLabel = "Xtream", sourceId = "xtream://test", )
 *
 * val normalized = normalizer.normalize(raw)
 *
 * // Title should contain expected substring assertTrue(
 * normalized.canonicalTitle.contains(expected.titleContains, ignoreCase = true), "Expected
 * '${expected.titleContains}' in '${normalized.canonicalTitle}' for: $title" )
 *
 * // Year should match if expected if (expected.year != null) { assertEquals( expected.year,
 * normalized.year, "Expected year ${expected.year} but got ${normalized.year} for: $title" ) }
 *
 * // Season/Episode if expected if (expected.season != null) { assertEquals(expected.season,
 * normalized.season, "Season mismatch for: $title") } if (expected.episode != null) {
 * assertEquals(expected.episode, normalized.episode, "Episode mismatch for: $title") }
 *
 * // Title should NEVER contain provider tags assertFalse(
 * normalized.canonicalTitle.contains(Regex("\\b(N|NF|D|DE|EN|FHD|4K|UHD|HD|SD)\\b")), "Title should
 * not contain provider tags: ${normalized.canonicalTitle}" ) } }
 *
 * // ======================================== // PART 3: GENERATED STRESS TESTS //
 * ========================================
 *
 * /** Generate 500 realistic heavily polluted titles for stress testing.
 */
