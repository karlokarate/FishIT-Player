package com.fishit.player.infra.data.nx.golden

import com.fishit.player.core.model.ExternalIds
import com.fishit.player.core.model.ImageRef
import com.fishit.player.core.model.MediaType
import com.fishit.player.core.model.NormalizedMediaMetadata
import com.fishit.player.core.model.TmdbMediaType
import com.fishit.player.core.model.TmdbRef
import com.fishit.player.core.model.repository.NxWorkRepository
import com.fishit.player.infra.data.nx.mapper.MediaTypeMapper
import com.fishit.player.infra.data.nx.writer.builder.WorkEntityBuilder
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Golden file tests for WorkEntityBuilder (NormalizedMediaMetadata → Work).
 *
 * Verifies that [WorkEntityBuilder.build] produces deterministic output matching
 * expected golden JSON files under `test-data/golden/builder/`.
 *
 * ## Running modes
 *
 * **Comparison mode** (default): Asserts current output matches golden files.
 * ```
 * ./gradlew :infra:data-nx:testDebugUnitTest --tests "*BuilderGoldenFileTest*"
 * ```
 *
 * **Update mode**: Regenerates golden files from current builder output.
 * ```
 * ./gradlew :infra:data-nx:testDebugUnitTest --tests "*BuilderGoldenFileTest*" -Dgolden.update=true
 * ```
 *
 * ## Test coverage
 *
 * 1. Full movie (all fields, TMDB → CONFIRMED, all rich metadata)
 * 2. Series episode (season/episode, TV TMDB ref)
 * 3. Minimal item (just title, all defaults → HEURISTIC)
 * 4. Telegram item with ImageRef variants (TelegramThumb, InlineBytes)
 * 5. Adult content with externalIds fallback (no typed tmdb, externalIds.tmdb used)
 *
 * **Note:** Timestamps (createdAtMs, updatedAtMs) are excluded from golden files
 * because they depend on `System.currentTimeMillis()`. Timestamp behavior is tested
 * via direct assertions in the existing [WorkEntityBuilderTest].
 */
class BuilderGoldenFileTest {

    private val builder = WorkEntityBuilder()
    private val shouldUpdate: Boolean
        get() = System.getProperty("golden.update")?.toBoolean() == true
    private val goldenDir = File("test-data/golden/builder")

    // Fixed timestamp for deterministic tests
    private val fixedNow = 1700000000000L // 2023-11-14T22:13:20Z

    // =========================================================================
    // Test Fixtures
    // =========================================================================

    /**
     * Full movie with TMDB, all rich metadata, ImageRef.Http for poster/backdrop.
     * Recognition: CONFIRMED (tmdb != null).
     */
    private fun createFullMovieMetadata() = NormalizedMediaMetadata(
        canonicalTitle = "Oppenheimer",
        mediaType = MediaType.MOVIE,
        year = 2023,
        durationMs = 10_860_000L, // 181 minutes
        tmdb = TmdbRef(TmdbMediaType.MOVIE, 872585),
        externalIds = ExternalIds(
            tmdb = TmdbRef(TmdbMediaType.MOVIE, 872585),
            imdbId = "tt15398776",
            tvdbId = null,
        ),
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
        thumbnail = null,
        rating = 8.1,
        genres = "Drama, History",
        plot = "The story of J. Robert Oppenheimer and the Manhattan Project.",
        director = "Christopher Nolan",
        cast = "Cillian Murphy, Emily Blunt, Robert Downey Jr.",
        trailer = "https://youtube.com/watch?v=uYPbbksJxIg",
        releaseDate = "2023-07-19",
        isAdult = false,
        addedTimestamp = 1690000000L, // 2023-07-22
    )

    /**
     * Series episode with season/episode, TV TMDB ref.
     * Recognition: CONFIRMED.
     */
    private fun createSeriesEpisodeMetadata() = NormalizedMediaMetadata(
        canonicalTitle = "Breaking Bad",
        mediaType = MediaType.SERIES_EPISODE,
        year = 2008,
        season = 1,
        episode = 5,
        durationMs = 2_820_000L, // 47 minutes
        tmdb = TmdbRef(TmdbMediaType.TV, 1396),
        externalIds = ExternalIds(
            tmdb = TmdbRef(TmdbMediaType.TV, 1396),
            imdbId = "tt0903747",
            tvdbId = "81189",
        ),
        poster = ImageRef.Http(
            url = "https://image.tmdb.org/t/p/w500/ggFHVNu6YYI5L9pCfOacjizRGt.jpg",
        ),
        rating = 9.5,
        genres = "Drama, Crime, Thriller",
        plot = "A chemistry teacher diagnosed with inoperable lung cancer...",
        director = "Vince Gilligan",
        cast = "Bryan Cranston, Aaron Paul, Anna Gunn",
    )

    /**
     * Minimal item with just title — all other fields default.
     * Recognition: HEURISTIC (no tmdb).
     */
    private fun createMinimalMetadata() = NormalizedMediaMetadata(
        canonicalTitle = "Unknown Movie",
        mediaType = MediaType.UNKNOWN,
    )

    /**
     * Telegram item with TelegramThumb (poster) and InlineBytes (placeholder).
     * Tests ImageRef.toSerializedString() behavior for non-Http types.
     */
    private fun createTelegramItemMetadata() = NormalizedMediaMetadata(
        canonicalTitle = "Avatar",
        mediaType = MediaType.MOVIE,
        year = 2009,
        durationMs = 9_720_000L, // 162 minutes
        thumbnail = ImageRef.TelegramThumb(
            remoteId = "AAMCAgADGQEAAj_avatar_thumb",
            chatId = -1001234567890L,
            messageId = 500L,
            preferredWidth = 320,
            preferredHeight = 180,
        ),
        placeholderThumbnail = ImageRef.InlineBytes(
            bytes = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte()),
            mimeType = "image/jpeg",
            preferredWidth = 40,
            preferredHeight = 22,
        ),
        addedTimestamp = 1690000000L,
    )

    /**
     * Adult item with externalIds fallback (no typed tmdb, only externalIds.tmdb).
     * Tests: isAdult=true, externalIds.tmdb fallback for tmdbId.
     */
    private fun createAdultWithExternalIdsFallback() = NormalizedMediaMetadata(
        canonicalTitle = "Some Adult Movie",
        mediaType = MediaType.MOVIE,
        year = 2020,
        tmdb = null, // No typed tmdb
        externalIds = ExternalIds(
            tmdb = TmdbRef(TmdbMediaType.MOVIE, 99999),
            imdbId = "tt9999999",
        ),
        isAdult = true,
        rating = 3.2,
    )

    // =========================================================================
    // Golden File Tests
    // =========================================================================

    @Test
    fun `full movie with TMDB produces CONFIRMED work`() {
        val metadata = createFullMovieMetadata()
        val work = builder.build(metadata, "movie:tmdb:872585", now = fixedNow)

        assertGolden("builder_full_movie.json", work)

        // Spot checks
        assertEquals("movie:tmdb:872585", work.workKey)
        assertEquals(MediaTypeMapper.toWorkType(MediaType.MOVIE), work.type)
        assertEquals("Oppenheimer", work.displayTitle)
        assertEquals("oppenheimer", work.titleNormalized)
        assertEquals(2023, work.year)
        assertEquals(10_860_000L, work.runtimeMs)
        assertEquals(NxWorkRepository.RecognitionState.CONFIRMED, work.recognitionState)
        assertEquals("872585", work.tmdbId)
        assertEquals("tt15398776", work.imdbId)
        assertEquals(8.1, work.rating)
        assertEquals("Christopher Nolan", work.director)
        assertTrue(work.poster is ImageRef.Http, "poster should be Http")
        assertTrue(work.backdrop is ImageRef.Http, "backdrop should be Http")
    }

    @Test
    fun `series episode maps season and episode correctly`() {
        val metadata = createSeriesEpisodeMetadata()
        val work = builder.build(metadata, "series:tmdb:1396", now = fixedNow)

        assertGolden("builder_series_episode.json", work)

        assertEquals("Breaking Bad", work.displayTitle)
        assertEquals(1, work.season)
        assertEquals(5, work.episode)
        assertEquals(NxWorkRepository.RecognitionState.CONFIRMED, work.recognitionState)
        assertEquals("1396", work.tmdbId)
        assertEquals("tt0903747", work.imdbId)
        assertEquals("81189", work.tvdbId)
    }

    @Test
    fun `minimal item produces HEURISTIC with nulls`() {
        val metadata = createMinimalMetadata()
        val work = builder.build(metadata, "unknown:fallback:unknown-movie", now = fixedNow)

        assertGolden("builder_minimal.json", work)

        assertEquals("Unknown Movie", work.displayTitle)
        assertEquals("unknown movie", work.titleNormalized)
        assertEquals(NxWorkRepository.RecognitionState.HEURISTIC, work.recognitionState)
        assertNull(work.tmdbId)
        assertNull(work.year)
        assertNull(work.runtimeMs)
        assertNull(work.poster)
        assertNull(work.rating)
    }

    @Test
    fun `telegram item serializes ImageRef types correctly`() {
        val metadata = createTelegramItemMetadata()
        val work = builder.build(metadata, "movie:fallback:avatar:2009", now = fixedNow)

        assertGolden("builder_telegram_item.json", work)

        // TelegramThumb → thumbnail field
        val thumb = work.thumbnail as? ImageRef.TelegramThumb
        assertTrue(thumb != null, "thumbnail should be TelegramThumb")
        assertEquals("AAMCAgADGQEAAj_avatar_thumb", thumb!!.remoteId)
        // InlineBytes → NOT mapped to thumbnail (placeholderThumbnail not a Work field)
        // Work only has poster, backdrop, thumbnail
        assertNull(work.poster)
        assertNull(work.backdrop)
    }

    @Test
    fun `adult item with externalIds fallback uses externalIds tmdb`() {
        val metadata = createAdultWithExternalIdsFallback()
        val work = builder.build(metadata, "movie:tmdb:99999", now = fixedNow)

        assertGolden("builder_adult_fallback.json", work)

        // No typed tmdb → HEURISTIC recognition
        assertEquals(NxWorkRepository.RecognitionState.HEURISTIC, work.recognitionState)
        // But externalIds.tmdb → tmdbId still populated
        assertEquals("99999", work.tmdbId)
        assertEquals("tt9999999", work.imdbId)
        assertEquals(true, work.isAdult)
        assertEquals(3.2, work.rating)
    }

    // =========================================================================
    // Golden file assertion helper
    // =========================================================================

    private fun assertGolden(fileName: String, work: NxWorkRepository.Work) {
        val actualJson = WorkJsonSerializer.toJsonString(work)
        val goldenFile = File(goldenDir, fileName)

        if (shouldUpdate) {
            goldenDir.mkdirs()
            goldenFile.writeText(actualJson + "\n")
            println("Updated golden file: ${goldenFile.absolutePath}")
            return
        }

        assertTrue(
            goldenFile.exists(),
            "Golden file not found: ${goldenFile.absolutePath}. Run with -Dgolden.update=true to generate.",
        )
        val expectedJson = goldenFile.readText().trim()
        val expected = WorkJsonSerializer.parseGoldenFile(expectedJson)
        val actual = WorkJsonSerializer.parseGoldenFile(actualJson)

        assertEquals(
            expected,
            actual,
            buildString {
                appendLine("Golden file mismatch: $fileName")
                appendLine("--- Expected ---")
                appendLine(expectedJson)
                appendLine("--- Actual ---")
                appendLine(actualJson)
            },
        )
    }
}
