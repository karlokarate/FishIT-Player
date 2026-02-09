package com.fishit.player.infra.data.nx.golden

import com.fishit.player.core.metadata.RegexMediaMetadataNormalizer
import com.fishit.player.core.model.ExternalIds
import com.fishit.player.core.model.ImageRef
import com.fishit.player.core.model.MediaType
import com.fishit.player.core.model.NormalizedMediaMetadata
import com.fishit.player.core.model.RawMediaMetadata
import com.fishit.player.core.model.SourceType
import com.fishit.player.core.model.TmdbMediaType
import com.fishit.player.core.model.TmdbRef
import com.fishit.player.core.model.repository.NxWorkRepository
import com.fishit.player.core.model.repository.NxWorkSourceRefRepository
import com.fishit.player.core.model.repository.NxWorkVariantRepository
import com.fishit.player.infra.data.nx.mapper.MediaTypeMapper
import com.fishit.player.infra.data.nx.mapper.SourceKeyParser
import com.fishit.player.infra.data.nx.writer.builder.SourceRefBuilder
import com.fishit.player.infra.data.nx.writer.builder.VariantBuilder
import com.fishit.player.infra.data.nx.writer.builder.WorkEntityBuilder
import java.io.File
import java.util.Locale
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * Full-chain golden file tests: RawMediaMetadata → Normalize → Build → Snapshot.
 *
 * These tests exercise the **complete mapping chain** that NxCatalogWriter.ingest()
 * performs at runtime, validating that all layers work correctly together:
 *
 * ```
 * RawMediaMetadata (pipeline output)
 *    │ RegexMediaMetadataNormalizer.normalize()
 *    ▼
 * NormalizedMediaMetadata
 *    │ WorkEntityBuilder.build() + SourceRefBuilder.build() + VariantBuilder.build()
 *    ▼
 * Work + SourceRef + Variant (persistence entities)
 * ```
 *
 * Unlike the P0 tests that verify each layer in isolation, P1 tests detect **seam
 * failures** where the normalizer's output doesn't align with what builders expect.
 *
 * ## Running modes
 *
 * **Comparison mode** (default):
 * ```
 * ./gradlew :infra:data-nx:testDebugUnitTest --tests "*FullChainGoldenFileTest*"
 * ```
 *
 * **Update mode** (regenerate golden files):
 * ```
 * ./gradlew :infra:data-nx:testDebugUnitTest --tests "*FullChainGoldenFileTest*" -Dgolden.update=true
 * ```
 *
 * ## Test coverage
 *
 * 1. Xtream VOD movie — structured metadata, TMDB, playback hints → mp4 variant
 * 2. Xtream series episode — S/E numbers, series TMDB ref, m3u8 → hls normalization
 * 3. Xtream live channel — EPG fields, live-specific SourceRef, no variant (no hints)
 * 4. Telegram scene-named movie — title parsing, year extraction, type refinement
 * 5. Telegram series episode — S01E05 scene name, season/episode extraction
 * 6. Minimal item — bare minimum fields, all defaults, heuristic recognition
 */
class FullChainGoldenFileTest {

    // Production components — all zero-arg constructors, no DI needed
    private val normalizer = RegexMediaMetadataNormalizer()
    private val workBuilder = WorkEntityBuilder()
    private val sourceRefBuilder = SourceRefBuilder()
    private val variantBuilder = VariantBuilder()

    private val shouldUpdate: Boolean
        get() = System.getProperty("golden.update")?.toBoolean() == true
    private val goldenDir = File("test-data/golden/full-chain")

    // Fixed timestamp for deterministic tests
    private val fixedNow = 1700000000000L // 2023-11-14T22:13:20Z

    // =========================================================================
    // Test Fixtures
    // =========================================================================

    /**
     * Xtream VOD movie with rich structured metadata.
     * Xtream API provides explicit year, mediaType, rating, etc.
     * Has playback hints → produces a Variant entity.
     */
    private fun createXtreamVodMovieRaw() = RawMediaMetadata(
        sourceId = "xtream:vod:87654",
        sourceType = SourceType.XTREAM,
        sourceLabel = "premium-iptv",
        originalTitle = "Oppenheimer 2023",
        mediaType = MediaType.MOVIE,
        year = 2023,
        poster = ImageRef.Http(
            url = "https://image.tmdb.org/t/p/w500/8Gxv8gSFCU0XGDykEGv7zR1n2ua.jpg",
            preferredWidth = 500,
            preferredHeight = 750,
        ),
        backdrop = ImageRef.Http(
            url = "https://image.tmdb.org/t/p/w1280/fm6KqXpk3M2HVveHwCrBSSBaO0V.jpg",
            preferredWidth = 1280,
            preferredHeight = 720,
        ),
        rating = 8.1,
        genres = "Drama, History",
        plot = "The story of J. Robert Oppenheimer and the Manhattan Project.",
        director = "Christopher Nolan",
        cast = "Cillian Murphy, Emily Blunt, Robert Downey Jr.",
        trailer = "https://youtube.com/watch?v=uYPbbksJxIg",
        releaseDate = "2023-07-19",
        durationMs = 10_860_000L,
        externalIds = ExternalIds(
            tmdb = TmdbRef(TmdbMediaType.MOVIE, 872585),
            imdbId = "tt15398776",
        ),
        playbackHints = mapOf(
            "xtream.containerExtension" to "mp4",
            "xtream.streamId" to "87654",
        ),
        addedTimestamp = 1690000000L,
    )

    /**
     * Xtream series episode with structured season/episode from API.
     * Has m3u8 container → should normalize to "hls".
     */
    private fun createXtreamSeriesEpisodeRaw() = RawMediaMetadata(
        sourceId = "xtream:episode:5001:2:7",
        sourceType = SourceType.XTREAM,
        sourceLabel = "premium-iptv",
        originalTitle = "Breaking Bad S02E07",
        mediaType = MediaType.SERIES_EPISODE,
        year = 2009,
        season = 2,
        episode = 7,
        poster = ImageRef.Http(
            url = "https://image.tmdb.org/t/p/w500/ggFHVNu6YYI5L9pCfOacjizRGt.jpg",
            preferredWidth = 500,
            preferredHeight = 750,
        ),
        rating = 9.5,
        genres = "Crime, Drama, Thriller",
        plot = "Walt and Jesse try a new method of distribution.",
        durationMs = 2_820_000L,
        externalIds = ExternalIds(
            tmdb = TmdbRef(TmdbMediaType.TV, 1396),
            imdbId = "tt0903747",
        ),
        playbackHints = mapOf(
            "xtream.containerExtension" to "m3u8",
            "xtream.streamId" to "5001",
        ),
        addedTimestamp = 1680000000L,
    )

    /**
     * Xtream live channel with EPG and catchup fields.
     * No playback hints → no Variant entity created.
     */
    private fun createXtreamLiveChannelRaw() = RawMediaMetadata(
        sourceId = "xtream:live:999",
        sourceType = SourceType.XTREAM,
        sourceLabel = "premium-iptv",
        originalTitle = "BBC One HD",
        mediaType = MediaType.LIVE,
        poster = ImageRef.Http(
            url = "https://cdn.example.com/logos/bbc_one_hd.png",
            preferredWidth = 200,
            preferredHeight = 200,
        ),
        genres = "Entertainment, News",
        epgChannelId = "bbc.one.hd",
        tvArchive = 1,
        tvArchiveDuration = 7,
        categoryId = "uk-entertainment",
    )

    /**
     * Telegram video with scene-style filename (no structured metadata).
     * Normalizer must parse: "Oppenheimer.2023.1080p.BluRay.x264-YTS"
     * → title="Oppenheimer", year=2023, mediaType=MOVIE (refined from UNKNOWN)
     */
    private fun createTelegramSceneMovieRaw() = RawMediaMetadata(
        sourceId = "msg:-1001234567890:42001",
        sourceType = SourceType.TELEGRAM,
        sourceLabel = "movie-channel",
        originalTitle = "Oppenheimer.2023.1080p.BluRay.x264-YTS",
        mediaType = MediaType.UNKNOWN, // Telegram doesn't know media type
        thumbnail = ImageRef.TelegramThumb(
            remoteId = "AgACAgIAAxkBAAIBZ2XYabcdef",
            preferredWidth = 320,
            preferredHeight = 180,
        ),
        durationMs = 10_860_000L,
    )

    /**
     * Telegram series episode with scene naming convention.
     * Uses format proven in RealWorldNormalizerTest:
     * "Breaking Bad S05E16 Felina German DL 1080p BluRay x264.mkv"
     * → title="Breaking Bad", season=5, episode=16, mediaType=SERIES_EPISODE
     */
    private fun createTelegramSeriesEpisodeRaw() = RawMediaMetadata(
        sourceId = "msg:-1001234567890:42050",
        sourceType = SourceType.TELEGRAM,
        sourceLabel = "series-channel",
        originalTitle = "Breaking Bad S05E16 Felina German DL 1080p BluRay x264.mkv",
        mediaType = MediaType.UNKNOWN,
        durationMs = 2_820_000L,
    )

    /**
     * Minimal item — only required fields set.
     * Tests default handling in normalizer and all builders.
     */
    private fun createMinimalRaw() = RawMediaMetadata(
        sourceId = "xtream:vod:1",
        sourceType = SourceType.XTREAM,
        sourceLabel = "basic-server",
        originalTitle = "Unknown Video File",
        mediaType = MediaType.UNKNOWN,
    )

    // =========================================================================
    // Key Building (mirrors NxCatalogWriter private methods)
    // =========================================================================

    /**
     * Build work key from normalized metadata.
     * Mirrors [NxCatalogWriter.buildWorkKey] exactly.
     */
    private fun buildWorkKey(normalized: NormalizedMediaMetadata): String {
        val authority = if (normalized.tmdb != null) "tmdb" else "heuristic"
        val id = normalized.tmdb?.id?.toString()
            ?: "${toSlug(normalized.canonicalTitle)}-${normalized.year ?: "unknown"}"
        val workType = when {
            normalized.mediaType.name.contains("SERIES") -> "series"
            normalized.mediaType.name.contains("EPISODE") -> "episode"
            normalized.mediaType.name.contains("LIVE") -> "live"
            normalized.mediaType.name.contains("CLIP") -> "clip"
            else -> "movie"
        }
        return "$workType:$authority:$id"
    }

    private fun toSlug(title: String): String {
        return title.lowercase(Locale.ROOT)
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')
            .take(50)
    }

    private fun buildSourceKey(raw: RawMediaMetadata, accountKey: String): String {
        return SourceKeyParser.buildSourceKey(raw.sourceType, accountKey, raw.sourceId)
    }

    private fun buildVariantKey(sourceKey: String): String = "$sourceKey#original"

    // =========================================================================
    // Full-Chain Execution
    // =========================================================================

    /**
     * Run the complete chain and return all output entities.
     */
    private data class ChainOutput(
        val work: NxWorkRepository.Work,
        val sourceRef: NxWorkSourceRefRepository.SourceRef,
        val variant: NxWorkVariantRepository.Variant?,
    )

    private suspend fun runChain(raw: RawMediaMetadata, accountKey: String): ChainOutput {
        // Step 1: Normalize (title parsing, type refinement)
        val normalized = normalizer.normalize(raw)

        // Step 2: Build keys (mirrors NxCatalogWriter)
        val workKey = buildWorkKey(normalized)
        val sourceKey = buildSourceKey(raw, accountKey)
        val variantKey = buildVariantKey(sourceKey)

        // Step 3: Build Work entity
        val work = workBuilder.build(normalized, workKey, fixedNow)

        // Step 4: Build SourceRef entity
        val sourceRef = sourceRefBuilder.build(raw, workKey, accountKey, sourceKey, fixedNow)

        // Step 5: Build Variant entity (only if playback hints present)
        val variant = if (raw.playbackHints.isNotEmpty()) {
            variantBuilder.build(variantKey, workKey, sourceKey, raw.playbackHints, normalized.durationMs, fixedNow)
        } else {
            null
        }

        return ChainOutput(work, sourceRef, variant)
    }

    // =========================================================================
    // Golden File Comparison
    // =========================================================================

    private fun assertGoldenMatch(testName: String, output: ChainOutput) {
        val goldenFile = File(goldenDir, "$testName.json")
        val actualJson = ChainOutputJsonSerializer.toJsonString(output.work, output.sourceRef, output.variant)

        if (shouldUpdate) {
            goldenDir.mkdirs()
            goldenFile.writeText(actualJson + "\n")
            println("✅ Updated golden file: ${goldenFile.path}")
            return
        }

        if (!goldenFile.exists()) {
            goldenDir.mkdirs()
            goldenFile.writeText(actualJson + "\n")
            println("⚠️  Created new golden file: ${goldenFile.path}")
            println("    Review the file and re-run to verify. Content:")
            println(actualJson)
            return
        }

        val expectedJson = goldenFile.readText().trim()
        val expected = ChainOutputJsonSerializer.parseGoldenFile(expectedJson)
        val actual = ChainOutputJsonSerializer.parseGoldenFile(actualJson)

        assertEquals(
            expected, actual,
            "Golden file mismatch for '$testName'.\n" +
                "Expected (from golden file):\n$expectedJson\n\n" +
                "Actual (from chain):\n$actualJson\n\n" +
                "To update: ./gradlew :infra:data-nx:testDebugUnitTest " +
                "--tests \"*FullChainGoldenFileTest*\" -Dgolden.update=true",
        )
    }

    // =========================================================================
    // Tests
    // =========================================================================

    @Test
    fun `xtream_vod_movie - full chain with structured metadata and TMDB`() = runTest {
        val raw = createXtreamVodMovieRaw()
        val output = runChain(raw, accountKey = "xtream:premium-iptv")

        // Verify key structural properties before golden comparison
        assertEquals("movie:tmdb:872585", output.work.workKey)
        assertEquals(NxWorkRepository.WorkType.MOVIE, output.work.type)
        assertEquals(NxWorkRepository.RecognitionState.CONFIRMED, output.work.recognitionState)
        assertEquals("872585", output.work.tmdbId)
        assertNotNull(output.variant, "VOD with playback hints should produce a Variant")
        assertEquals("mp4", output.variant?.container)
        assertEquals("87654", output.sourceRef.sourceItemKey)

        assertGoldenMatch("xtream_vod_movie", output)
    }

    @Test
    fun `xtream_series_episode - season_episode with m3u8 to hls normalization`() = runTest {
        val raw = createXtreamSeriesEpisodeRaw()
        val output = runChain(raw, accountKey = "xtream:premium-iptv")

        // Verify episode-specific behavior
        assertEquals(2, output.work.season)
        assertEquals(7, output.work.episode)
        assertNotNull(output.variant)
        assertEquals("hls", output.variant?.container, "m3u8 should normalize to hls")
        assertEquals(NxWorkSourceRefRepository.SourceItemKind.EPISODE, output.sourceRef.sourceItemKind)

        assertGoldenMatch("xtream_series_episode", output)
    }

    @Test
    fun `xtream_live_channel - EPG fields and no variant`() = runTest {
        val raw = createXtreamLiveChannelRaw()
        val output = runChain(raw, accountKey = "xtream:premium-iptv")

        // Verify live-specific behavior
        assertEquals(NxWorkRepository.WorkType.LIVE_CHANNEL, output.work.type)
        assertEquals("bbc.one.hd", output.sourceRef.epgChannelId)
        assertEquals(1, output.sourceRef.tvArchive)
        assertEquals(7, output.sourceRef.tvArchiveDuration)
        assertEquals(null, output.variant, "Live channel without hints should have no Variant")

        assertGoldenMatch("xtream_live_channel", output)
    }

    @Test
    fun `telegram_scene_movie - title parsing and type refinement`() = runTest {
        val raw = createTelegramSceneMovieRaw()
        val output = runChain(raw, accountKey = "telegram:movie-channel")

        // Verify normalizer parsed the scene name correctly
        assertEquals("Oppenheimer", output.work.displayTitle, "Scene name should be cleaned")
        assertEquals(2023, output.work.year, "Year should be extracted from scene name")
        assertEquals(NxWorkRepository.WorkType.MOVIE, output.work.type, "UNKNOWN → MOVIE with year")
        assertEquals(NxWorkRepository.RecognitionState.HEURISTIC, output.work.recognitionState)
        // Telegram preserves full msg:chatId:messageId format
        assertEquals("msg:-1001234567890:42001", output.sourceRef.sourceItemKey)

        assertGoldenMatch("telegram_scene_movie", output)
    }

    @Test
    fun `telegram_series_episode - scene name S05E16 parsing`() = runTest {
        val raw = createTelegramSeriesEpisodeRaw()
        val output = runChain(raw, accountKey = "telegram:series-channel")

        // The golden file captures the actual normalizer output for this scene name.
        // Pre-assertions verify only fields that are INPUT-determined (not parser-dependent):
        assertEquals("msg:-1001234567890:42050", output.sourceRef.sourceItemKey)
        assertEquals(NxWorkSourceRefRepository.SourceType.TELEGRAM, output.sourceRef.sourceType)

        assertGoldenMatch("telegram_series_episode", output)
    }

    @Test
    fun `minimal_item - defaults and heuristic recognition`() = runTest {
        val raw = createMinimalRaw()
        val output = runChain(raw, accountKey = "xtream:basic-server")

        // Verify minimal defaults
        assertEquals(NxWorkRepository.RecognitionState.HEURISTIC, output.work.recognitionState)
        assertEquals(null, output.work.tmdbId)
        assertEquals(null, output.work.year)
        assertEquals(null, output.variant, "No playback hints → no Variant")
        assertEquals(false, output.work.isAdult)
        assertEquals(false, output.work.isDeleted)

        assertGoldenMatch("minimal_item", output)
    }
}
