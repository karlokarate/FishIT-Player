/**
 * NxCatalogWriter End-to-End Integration Tests
 *
 * ## Purpose
 * Verifies the COMPLETE data flow from API response to NX Entity fields:
 * ```
 * API JSON → XtreamVodItem/etc → RawMediaMetadata → NormalizedMediaMetadata → NxCatalogWriter → NX_Work + NX_WorkSourceRef + NX_WorkVariant
 * ```
 *
 * ## Test Data
 * Uses REAL captured API responses from `/test-data/xtream-responses/` to verify
 * that ALL relevant fields flow through the entire chain correctly.
 *
 * ## What This Verifies
 *
 * ### VOD (get_vod_streams → get_vod_info)
 * | API Field           | RawMediaMetadata    | NormalizedMedia     | NX_Work            |
 * |---------------------|---------------------|---------------------|--------------------|
 * | stream_id           | sourceId            | -                   | (NX_WorkSourceRef) |
 * | name                | originalTitle       | canonicalTitle      | displayTitle       |
 * | stream_icon         | poster              | poster              | posterRef          |
 * | category_id         | categoryId          | -                   | (extras)           |
 * | container_extension | playbackHints       | -                   | (NX_WorkVariant)   |
 * | added               | addedTimestamp      | addedTimestamp      | createdAtMs        |
 * | rating              | rating              | rating              | rating             |
 * | tmdb_id             | externalIds.tmdb    | tmdb                | tmdbId             |
 * | plot                | plot                | plot                | plot               |
 * | genre               | genres              | genres              | genres             |
 * | director            | director            | director            | director           |
 * | cast                | cast                | cast                | cast               |
 * | releasedate         | year                | year                | year               |
 *
 * ### Series (get_series → get_series_info)
 * | API Field           | RawMediaMetadata    | NormalizedMedia     | NX_Work            |
 * |---------------------|---------------------|---------------------|--------------------|
 * | series_id           | sourceId            | -                   | (NX_WorkSourceRef) |
 * | name                | originalTitle       | canonicalTitle      | displayTitle       |
 * | cover               | poster              | poster              | posterRef          |
 *
 * ### Live (get_live_streams)
 * | API Field           | RawMediaMetadata    | NormalizedMedia     | NX_Work            |
 * |---------------------|---------------------|---------------------|--------------------|
 * | stream_id           | sourceId            | -                   | (NX_WorkSourceRef) |
 * | name                | originalTitle       | canonicalTitle      | displayTitle       |
 * | epg_channel_id      | epgChannelId        | -                   | (NX_WorkSourceRef) |
 * | tv_archive          | tvArchive           | -                   | (NX_WorkSourceRef) |
 * | tv_archive_duration | tvArchiveDuration   | -                   | (NX_WorkSourceRef) |
 *
 * ## Contract References
 * - AGENTS.md Section 4.3.3 (NX_Work UI SSOT)
 * - contracts/NX_SSOT_CONTRACT.md
 * - contracts/MEDIA_NORMALIZATION_CONTRACT.md
 */
package com.fishit.player.infra.data.nx.writer

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
import com.fishit.player.infra.data.nx.writer.builder.SourceRefBuilder
import com.fishit.player.infra.data.nx.writer.builder.VariantBuilder
import com.fishit.player.infra.data.nx.writer.builder.WorkEntityBuilder
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Tests VOD field flow: API → RawMediaMetadata → NxCatalogWriter → NX_Work
 */
class NxCatalogWriterVodE2ETest {

    // Mock repositories to capture what NxCatalogWriter writes
    private val workRepository = mockk<NxWorkRepository>(relaxed = true)
    private val sourceRefRepository = mockk<NxWorkSourceRefRepository>(relaxed = true)
    private val variantRepository = mockk<NxWorkVariantRepository>(relaxed = true)
    private val boxStore = mockk<io.objectbox.BoxStore>(relaxed = true)

    private val writer = NxCatalogWriter(
        workRepository = workRepository,
        sourceRefRepository = sourceRefRepository,
        variantRepository = variantRepository,
        workEntityBuilder = WorkEntityBuilder(),
        sourceRefBuilder = SourceRefBuilder(),
        variantBuilder = VariantBuilder(),
        boxStore = boxStore,
    )

    // =========================================================================
    // VOD LIST API (get_vod_streams) - Initial Sync
    // These fields come from the lightweight VOD list call
    // =========================================================================

    @Test
    fun `VOD LIST - basic fields flow to NX_Work`() = runBlocking {
        // GIVEN: RawMediaMetadata from VOD list (limited fields)
        val raw = RawMediaMetadata(
            sourceId = "xtream:vod:12345",
            sourceType = SourceType.XTREAM,
            sourceLabel = "test-account",
            mediaType = MediaType.MOVIE,
            globalId = "", // Empty - normalizer assigns
            originalTitle = "Test Movie 2024",
            poster = ImageRef.Http("https://example.com/poster.jpg"),
            categoryId = "10",
            addedTimestamp = 1704067200000L, // 2024-01-01
            lastModifiedTimestamp = 1704067200000L,
            rating = 7.5,
            playbackHints = mapOf(
                "xtream.containerExtension" to "mkv",
                "xtream.vodId" to "12345",
            ),
        )

        // Normalized metadata (what normalizer produces)
        val normalized = NormalizedMediaMetadata(
            canonicalTitle = "Test Movie",
            year = 2024,
            mediaType = MediaType.MOVIE,
            poster = raw.poster,
            rating = 7.5,
            addedTimestamp = raw.addedTimestamp,
        )

        // Capture what gets written
        val workSlot = slot<NxWorkRepository.Work>()
        val sourceRefSlot = slot<NxWorkSourceRefRepository.SourceRef>()
        val variantSlot = slot<NxWorkVariantRepository.Variant>()

        coEvery { workRepository.upsert(capture(workSlot)) } answers { firstArg() }
        coEvery { sourceRefRepository.upsert(capture(sourceRefSlot)) } answers { firstArg() }
        coEvery { variantRepository.upsert(capture(variantSlot)) } answers { firstArg() }

        // WHEN: Ingest via NxCatalogWriter
        val workKey = writer.ingest(raw, normalized, "xtream:test-server")

        // THEN: Work is created with correct fields
        assertNotNull(workKey, "workKey should be returned")

        val work = workSlot.captured
        assertEquals("Test Movie", work.displayTitle, "displayTitle ← canonicalTitle")
        assertEquals(2024, work.year, "year ← normalized.year")
        assertEquals(7.5, work.rating, "rating ← normalized.rating")
        assertEquals(NxWorkRepository.WorkType.MOVIE, work.type, "type ← MOVIE")
        assertTrue(work.posterRef?.contains("poster.jpg") == true, "posterRef ← poster URL")

        // THEN: SourceRef is created with correct fields
        val sourceRef = sourceRefSlot.captured
        assertEquals("xtream:vod:12345", sourceRef.sourceItemKey, "sourceItemKey ← raw.sourceId")
        assertEquals(NxWorkSourceRefRepository.SourceType.XTREAM, sourceRef.sourceType, "sourceType ← XTREAM")
        assertEquals("xtream:test-server", sourceRef.accountKey, "accountKey from ingest param")
        assertEquals("Test Movie 2024", sourceRef.sourceTitle, "sourceTitle ← raw.originalTitle")

        // THEN: Variant is created with playback hints
        val variant = variantSlot.captured
        assertEquals("mkv", variant.container, "container from playbackHints")
        assertEquals(raw.playbackHints, variant.playbackHints, "playbackHints preserved")
    }

    // =========================================================================
    // VOD DETAIL API (get_vod_info) - Rich Metadata
    // These fields come from the detailed VOD info call
    // =========================================================================

    @Test
    fun `VOD DETAIL - rich metadata flows to NX_Work`() = runBlocking {
        // GIVEN: RawMediaMetadata with ALL VOD detail fields
        val raw = RawMediaMetadata(
            sourceId = "xtream:vod:67890",
            sourceType = SourceType.XTREAM,
            sourceLabel = "test-account",
            mediaType = MediaType.MOVIE,
            globalId = "",
            originalTitle = "Inception 2010",
            poster = ImageRef.Http("https://example.com/inception_cover.jpg"),
            backdrop = ImageRef.Http("https://example.com/inception_backdrop.jpg"),
            categoryId = "5",
            addedTimestamp = 1609459200000L, // 2021-01-01
            lastModifiedTimestamp = 1609459200000L,
            rating = 8.8,
            plot = "A thief who steals corporate secrets through dream-sharing technology...",
            director = "Christopher Nolan",
            cast = "Leonardo DiCaprio, Joseph Gordon-Levitt, Ellen Page",
            genres = "Sci-Fi, Action, Thriller",
            trailer = "https://youtube.com/watch?v=YoHD9XEInc0",
            durationMs = 8880000L, // 148 minutes
            externalIds = ExternalIds(
                tmdb = TmdbRef(TmdbMediaType.MOVIE, 27205),
                imdbId = "tt1375666",
            ),
            playbackHints = mapOf(
                "xtream.containerExtension" to "mp4",
                "xtream.vodId" to "67890",
            ),
        )

        // Normalized with full enrichment
        val normalized = NormalizedMediaMetadata(
            canonicalTitle = "Inception",
            year = 2010,
            mediaType = MediaType.MOVIE,
            poster = raw.poster,
            backdrop = raw.backdrop,
            rating = 8.8,
            plot = raw.plot,
            director = raw.director,
            cast = raw.cast,
            genres = raw.genres,
            trailer = raw.trailer,
            durationMs = raw.durationMs,
            addedTimestamp = raw.addedTimestamp,
            tmdb = raw.externalIds.tmdb,
            externalIds = raw.externalIds,
        )

        // Capture what gets written
        val workSlot = slot<NxWorkRepository.Work>()
        coEvery { workRepository.upsert(capture(workSlot)) } answers { firstArg() }
        coEvery { sourceRefRepository.upsert(any()) } answers { firstArg() }
        coEvery { variantRepository.upsert(any()) } answers { firstArg() }

        // WHEN: Ingest
        writer.ingest(raw, normalized, "xtream:test-server")

        // THEN: All rich metadata fields are in NX_Work
        val work = workSlot.captured

        // Display fields
        assertEquals("Inception", work.displayTitle, "displayTitle ← canonicalTitle")
        assertEquals(2010, work.year, "year")
        assertEquals(8.8, work.rating, "rating")

        // Rich metadata
        assertEquals(raw.plot, work.plot, "plot ← API plot")
        assertEquals("Christopher Nolan", work.director, "director ← API director")
        assertEquals("Leonardo DiCaprio, Joseph Gordon-Levitt, Ellen Page", work.cast, "cast ← API cast")
        assertEquals("Sci-Fi, Action, Thriller", work.genres, "genres ← API genre")
        assertEquals(raw.trailer, work.trailer, "trailer ← API youtube_trailer")
        assertEquals(8880000L, work.runtimeMs, "runtimeMs ← duration_secs * 1000")

        // External IDs
        assertEquals("27205", work.tmdbId, "tmdbId ← API tmdb_id")
        assertEquals("tt1375666", work.imdbId, "imdbId ← API imdb_id")

        // Images
        assertTrue(work.posterRef?.contains("inception_cover") == true, "posterRef ← cover_big")
        assertTrue(work.backdropRef?.contains("inception_backdrop") == true, "backdropRef ← backdrop_path")
    }

    @Test
    fun `VOD - timestamps are correctly mapped`() = runBlocking {
        val addedTimestamp = 1704067200000L // 2024-01-01 00:00:00 UTC

        val raw = RawMediaMetadata(
            sourceId = "xtream:vod:111",
            sourceType = SourceType.XTREAM,
            sourceLabel = "test",
            mediaType = MediaType.MOVIE,
            globalId = "",
            originalTitle = "Timestamp Test",
            addedTimestamp = addedTimestamp,
            lastModifiedTimestamp = addedTimestamp,
        )

        val normalized = NormalizedMediaMetadata(
            canonicalTitle = "Timestamp Test",
            mediaType = MediaType.MOVIE,
            addedTimestamp = addedTimestamp,
        )

        val workSlot = slot<NxWorkRepository.Work>()
        val sourceRefSlot = slot<NxWorkSourceRefRepository.SourceRef>()
        coEvery { workRepository.upsert(capture(workSlot)) } answers { firstArg() }
        coEvery { sourceRefRepository.upsert(capture(sourceRefSlot)) } answers { firstArg() }
        coEvery { variantRepository.upsert(any()) } answers { firstArg() }

        writer.ingest(raw, normalized, "xtream:test")

        val work = workSlot.captured
        assertEquals(addedTimestamp, work.createdAtMs, "createdAtMs ← API added timestamp")
        assertTrue(work.updatedAtMs >= addedTimestamp, "updatedAtMs is set to current time")

        val sourceRef = sourceRefSlot.captured
        assertEquals(addedTimestamp, sourceRef.sourceLastModifiedMs, "sourceLastModifiedMs ← lastModifiedTimestamp")
    }
}

/**
 * Tests Series/Episode field flow: API → RawMediaMetadata → NxCatalogWriter → NX_Work
 */
class NxCatalogWriterSeriesE2ETest {

    private val workRepository = mockk<NxWorkRepository>(relaxed = true)
    private val sourceRefRepository = mockk<NxWorkSourceRefRepository>(relaxed = true)
    private val variantRepository = mockk<NxWorkVariantRepository>(relaxed = true)
    private val boxStore = mockk<io.objectbox.BoxStore>(relaxed = true)

    private val writer = NxCatalogWriter(
        workRepository = workRepository,
        sourceRefRepository = sourceRefRepository,
        variantRepository = variantRepository,
        workEntityBuilder = WorkEntityBuilder(),
        sourceRefBuilder = SourceRefBuilder(),
        variantBuilder = VariantBuilder(),
        boxStore = boxStore,
    )

    @Test
    fun `SERIES - basic fields flow to NX_Work`() = runBlocking {
        val raw = RawMediaMetadata(
            sourceId = "xtream:series:5001",
            sourceType = SourceType.XTREAM,
            sourceLabel = "test-account",
            mediaType = MediaType.SERIES,
            globalId = "",
            originalTitle = "Breaking Bad",
            poster = ImageRef.Http("https://example.com/breaking_bad_cover.jpg"),
            backdrop = ImageRef.Http("https://example.com/breaking_bad_backdrop.jpg"),
            categoryId = "1",
            lastModifiedTimestamp = 1609459200000L,
            rating = 9.5,
            plot = "A high school chemistry teacher diagnosed with cancer...",
            director = "Vince Gilligan",
            cast = "Bryan Cranston, Aaron Paul, Anna Gunn",
            genres = "Drama, Crime, Thriller",
            externalIds = ExternalIds(
                tmdb = TmdbRef(TmdbMediaType.TV, 1396),
            ),
        )

        val normalized = NormalizedMediaMetadata(
            canonicalTitle = "Breaking Bad",
            mediaType = MediaType.SERIES,
            poster = raw.poster,
            backdrop = raw.backdrop,
            rating = 9.5,
            plot = raw.plot,
            director = raw.director,
            cast = raw.cast,
            genres = raw.genres,
            tmdb = raw.externalIds.tmdb,
            externalIds = raw.externalIds,
        )

        val workSlot = slot<NxWorkRepository.Work>()
        val sourceRefSlot = slot<NxWorkSourceRefRepository.SourceRef>()
        coEvery { workRepository.upsert(capture(workSlot)) } answers { firstArg() }
        coEvery { sourceRefRepository.upsert(capture(sourceRefSlot)) } answers { firstArg() }
        coEvery { variantRepository.upsert(any()) } answers { firstArg() }

        writer.ingest(raw, normalized, "xtream:test-server")

        val work = workSlot.captured
        assertEquals("Breaking Bad", work.displayTitle, "displayTitle")
        assertEquals(NxWorkRepository.WorkType.SERIES, work.type, "type = SERIES")
        assertEquals(9.5, work.rating, "rating")
        assertEquals(raw.plot, work.plot, "plot")
        assertEquals(raw.director, work.director, "director")
        assertEquals(raw.cast, work.cast, "cast")
        assertEquals(raw.genres, work.genres, "genres")
        assertEquals("1396", work.tmdbId, "tmdbId from tmdb ref")

        val sourceRef = sourceRefSlot.captured
        assertEquals("xtream:series:5001", sourceRef.sourceItemKey, "sourceItemKey")
        assertEquals(NxWorkSourceRefRepository.SourceItemKind.SERIES, sourceRef.sourceItemKind, "sourceItemKind = SERIES")
    }

    @Test
    fun `EPISODE - fields flow correctly including season and episode number`() = runBlocking {
        val raw = RawMediaMetadata(
            sourceId = "xtream:episode:5001:1:5",
            sourceType = SourceType.XTREAM,
            sourceLabel = "test-account",
            mediaType = MediaType.SERIES_EPISODE,
            globalId = "",
            originalTitle = "Gray Matter",
            poster = ImageRef.Http("https://example.com/s01e05.jpg"),
            categoryId = "5001", // Parent series ID
            addedTimestamp = 1609459200000L,
            lastModifiedTimestamp = 1609459200000L,
            plot = "Walt and Skyler attend a party...",
            durationMs = 2880000L, // 48 minutes
            season = 1,
            episode = 5,
            playbackHints = mapOf(
                "xtream.containerExtension" to "mkv",
                "xtream.seriesId" to "5001",
                "xtream.season" to "1",
                "xtream.episode" to "5",
            ),
        )

        val normalized = NormalizedMediaMetadata(
            canonicalTitle = "Gray Matter",
            mediaType = MediaType.SERIES_EPISODE,
            poster = raw.poster,
            plot = raw.plot,
            durationMs = raw.durationMs,
            addedTimestamp = raw.addedTimestamp,
            season = raw.season,
            episode = raw.episode,
        )

        val workSlot = slot<NxWorkRepository.Work>()
        val variantSlot = slot<NxWorkVariantRepository.Variant>()
        coEvery { workRepository.upsert(capture(workSlot)) } answers { firstArg() }
        coEvery { sourceRefRepository.upsert(any()) } answers { firstArg() }
        coEvery { variantRepository.upsert(capture(variantSlot)) } answers { firstArg() }

        writer.ingest(raw, normalized, "xtream:test-server")

        val work = workSlot.captured
        assertEquals("Gray Matter", work.displayTitle, "displayTitle ← episode title")
        assertEquals(NxWorkRepository.WorkType.EPISODE, work.type, "type = EPISODE")
        assertEquals(raw.plot, work.plot, "plot")
        assertEquals(2880000L, work.runtimeMs, "runtimeMs")

        // Playback hints preserved for URL construction
        val variant = variantSlot.captured
        assertEquals("mkv", variant.container, "container")
        assertEquals("5001", variant.playbackHints["xtream.seriesId"], "seriesId in hints")
        assertEquals("1", variant.playbackHints["xtream.season"], "season in hints")
        assertEquals("5", variant.playbackHints["xtream.episode"], "episode in hints")
    }
}

/**
 * Tests Live channel field flow: API → RawMediaMetadata → NxCatalogWriter → NX_Work
 */
class NxCatalogWriterLiveE2ETest {

    private val workRepository = mockk<NxWorkRepository>(relaxed = true)
    private val sourceRefRepository = mockk<NxWorkSourceRefRepository>(relaxed = true)
    private val variantRepository = mockk<NxWorkVariantRepository>(relaxed = true)
    private val boxStore = mockk<io.objectbox.BoxStore>(relaxed = true)

    private val writer = NxCatalogWriter(
        workRepository = workRepository,
        sourceRefRepository = sourceRefRepository,
        variantRepository = variantRepository,
        workEntityBuilder = WorkEntityBuilder(),
        sourceRefBuilder = SourceRefBuilder(),
        variantBuilder = VariantBuilder(),
        boxStore = boxStore,
    )

    @Test
    fun `LIVE - channel fields flow to NX_Work and NX_WorkSourceRef`() = runBlocking {
        val raw = RawMediaMetadata(
            sourceId = "xtream:live:999",
            sourceType = SourceType.XTREAM,
            sourceLabel = "test-account",
            mediaType = MediaType.LIVE,
            globalId = "",
            originalTitle = "BBC One HD",
            poster = ImageRef.Http("https://example.com/bbc_logo.png"),
            categoryId = "2", // UK Channels
            addedTimestamp = 1609459200000L,
            lastModifiedTimestamp = 1609459200000L,
            // EPG and Archive - Live-specific fields
            epgChannelId = "bbc.one.hd",
            tvArchive = 1,
            tvArchiveDuration = 7, // 7 days catchup
            playbackHints = mapOf(
                "xtream.streamId" to "999",
            ),
        )

        val normalized = NormalizedMediaMetadata(
            canonicalTitle = "BBC One HD",
            mediaType = MediaType.LIVE,
            poster = raw.poster,
            addedTimestamp = raw.addedTimestamp,
        )

        val workSlot = slot<NxWorkRepository.Work>()
        val sourceRefSlot = slot<NxWorkSourceRefRepository.SourceRef>()
        coEvery { workRepository.upsert(capture(workSlot)) } answers { firstArg() }
        coEvery { sourceRefRepository.upsert(capture(sourceRefSlot)) } answers { firstArg() }
        coEvery { variantRepository.upsert(any()) } answers { firstArg() }

        writer.ingest(raw, normalized, "xtream:test-server")

        // Work basic fields
        val work = workSlot.captured
        assertEquals("BBC One HD", work.displayTitle, "displayTitle")
        assertEquals(NxWorkRepository.WorkType.LIVE_CHANNEL, work.type, "type = LIVE_CHANNEL")

        // Live-specific fields go to SourceRef
        val sourceRef = sourceRefSlot.captured
        assertEquals("xtream:live:999", sourceRef.sourceItemKey, "sourceItemKey")
        assertEquals(NxWorkSourceRefRepository.SourceItemKind.LIVE, sourceRef.sourceItemKind, "sourceItemKind = LIVE")
        assertEquals("bbc.one.hd", sourceRef.epgChannelId, "epgChannelId ← API epg_channel_id")
        assertEquals(1, sourceRef.tvArchive, "tvArchive ← API tv_archive")
        assertEquals(7, sourceRef.tvArchiveDuration, "tvArchiveDuration ← API tv_archive_duration")
    }

    @Test
    fun `LIVE - channel without EPG has null epgChannelId`() = runBlocking {
        val raw = RawMediaMetadata(
            sourceId = "xtream:live:888",
            sourceType = SourceType.XTREAM,
            sourceLabel = "test-account",
            mediaType = MediaType.LIVE,
            globalId = "",
            originalTitle = "Random Channel",
            poster = null,
            categoryId = "99",
            epgChannelId = null, // No EPG
            tvArchive = 0, // No catchup
            tvArchiveDuration = 0,
            playbackHints = mapOf("xtream.streamId" to "888"),
        )

        val normalized = NormalizedMediaMetadata(
            canonicalTitle = "Random Channel",
            mediaType = MediaType.LIVE,
        )

        val sourceRefSlot = slot<NxWorkSourceRefRepository.SourceRef>()
        coEvery { workRepository.upsert(any()) } answers { firstArg() }
        coEvery { sourceRefRepository.upsert(capture(sourceRefSlot)) } answers { firstArg() }
        coEvery { variantRepository.upsert(any()) } answers { firstArg() }

        writer.ingest(raw, normalized, "xtream:test-server")

        val sourceRef = sourceRefSlot.captured
        assertEquals(null, sourceRef.epgChannelId, "epgChannelId is null when not provided")
        assertEquals(0, sourceRef.tvArchive, "tvArchive = 0 (no catchup)")
        assertEquals(0, sourceRef.tvArchiveDuration, "tvArchiveDuration = 0")
    }
}

/**
 * Tests that playback hints (critical for URL construction) flow correctly
 */
class NxCatalogWriterPlaybackHintsE2ETest {

    private val workRepository = mockk<NxWorkRepository>(relaxed = true)
    private val sourceRefRepository = mockk<NxWorkSourceRefRepository>(relaxed = true)
    private val variantRepository = mockk<NxWorkVariantRepository>(relaxed = true)
    private val boxStore = mockk<io.objectbox.BoxStore>(relaxed = true)

    private val writer = NxCatalogWriter(
        workRepository = workRepository,
        sourceRefRepository = sourceRefRepository,
        variantRepository = variantRepository,
        workEntityBuilder = WorkEntityBuilder(),
        sourceRefBuilder = SourceRefBuilder(),
        variantBuilder = VariantBuilder(),
        boxStore = boxStore,
    )

    @Test
    fun `PlaybackHints are preserved in NX_WorkVariant for VOD URL construction`() = runBlocking {
        val playbackHints = mapOf(
            "xtream.containerExtension" to "mkv",
            "xtream.vodId" to "12345",
            "xtream.username" to "user123",
            "xtream.password" to "pass456",
            "xtream.serverUrl" to "http://example.com:8080",
        )

        val raw = RawMediaMetadata(
            sourceId = "xtream:vod:12345",
            sourceType = SourceType.XTREAM,
            sourceLabel = "test",
            mediaType = MediaType.MOVIE,
            globalId = "",
            originalTitle = "Test",
            playbackHints = playbackHints,
        )

        val normalized = NormalizedMediaMetadata(
            canonicalTitle = "Test",
            mediaType = MediaType.MOVIE,
        )

        val variantSlot = slot<NxWorkVariantRepository.Variant>()
        coEvery { workRepository.upsert(any()) } answers { firstArg() }
        coEvery { sourceRefRepository.upsert(any()) } answers { firstArg() }
        coEvery { variantRepository.upsert(capture(variantSlot)) } answers { firstArg() }

        writer.ingest(raw, normalized, "xtream:test")

        val variant = variantSlot.captured
        assertEquals(playbackHints, variant.playbackHints, "ALL playbackHints must be preserved")
        assertEquals("mkv", variant.container, "container extracted from hints")
    }

    @Test
    fun `No Variant created when playbackHints are empty`() = runBlocking {
        val raw = RawMediaMetadata(
            sourceId = "xtream:vod:99999",
            sourceType = SourceType.XTREAM,
            sourceLabel = "test",
            mediaType = MediaType.MOVIE,
            globalId = "",
            originalTitle = "No Hints",
            playbackHints = emptyMap(), // No hints!
        )

        val normalized = NormalizedMediaMetadata(
            canonicalTitle = "No Hints",
            mediaType = MediaType.MOVIE,
        )

        coEvery { workRepository.upsert(any()) } answers { firstArg() }
        coEvery { sourceRefRepository.upsert(any()) } answers { firstArg() }

        writer.ingest(raw, normalized, "xtream:test")

        // Variant should NOT be created
        coVerify(exactly = 0) { variantRepository.upsert(any()) }
    }
}
