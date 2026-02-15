package com.fishit.player.pipeline.xtream.golden

import com.fishit.player.core.model.MediaType
import com.fishit.player.core.model.PipelineIdTag
import com.fishit.player.core.model.RawMediaMetadata
import com.fishit.player.core.model.SourceType
import com.fishit.player.infra.transport.xtream.XtreamVodInfo
import com.fishit.player.pipeline.xtream.mapper.toRawMediaMetadata
import com.fishit.player.pipeline.xtream.mapper.toRawMetadata
import com.fishit.player.pipeline.xtream.model.XtreamChannel
import com.fishit.player.pipeline.xtream.model.XtreamEpisode
import com.fishit.player.pipeline.xtream.model.XtreamSeriesItem
import com.fishit.player.pipeline.xtream.model.XtreamVodItem
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Golden file tests for Xtream pipeline mapper functions.
 *
 * These tests verify the COMPLETE output of each mapper by comparing against
 * a golden JSON file. Advantages over field-by-field assertEquals:
 *
 * - **Catches regressions in ANY field** (not just the ones explicitly asserted)
 * - **Self-documenting** — golden file shows exact expected output
 * - **Easy to update** — run with `-Dgolden.update=true` to regenerate
 * - **Covers new fields automatically** — serializer includes all RawMediaMetadata fields
 *
 * ## Usage
 *
 * Normal run (compare against golden files):
 * ```
 * ./gradlew :pipeline:xtream:test --tests "*XtreamGoldenFileTest*"
 * ```
 *
 * Regenerate golden files after intentional changes:
 * ```
 * ./gradlew :pipeline:xtream:test --tests "*XtreamGoldenFileTest*" -Dgolden.update=true
 * ```
 *
 * Test data: `test-data/xtream-responses/`
 * Golden files: `test-data/golden/xtream/`
 */
class XtreamGoldenFileTest {
    private val testDataDir = File("test-data/xtream-responses")
    private val goldenDir = File("test-data/golden/xtream")
    private val accountLabel = "test-account"

    private val json =
        Json {
            ignoreUnknownKeys = true
            isLenient = true
            coerceInputValues = true
        }

    private val shouldUpdate: Boolean
        get() = System.getProperty("golden.update") == "true"

    // =========================================================================
    // Inline API DTOs (same pattern as existing integration tests)
    // =========================================================================

    @Serializable
    data class ApiVodItem(
        val stream_id: Int,
        val name: String,
        val stream_icon: String? = null,
        val category_id: String? = null,
        val container_extension: String? = null,
        val added: String? = null,
        val rating: String? = null,
        val rating_5based: String? = null,
        val tmdb_id: String? = null,
        val year: String? = null,
        val genre: String? = null,
        val plot: String? = null,
        val duration: String? = null,
        val is_adult: String? = null,
    )

    @Serializable
    data class ApiSeriesItem(
        val series_id: Int,
        val name: String,
        val cover: String? = null,
        val backdrop_path: List<String>? = null,
        val category_id: String? = null,
        val year: String? = null,
        val rating: String? = null,
        val plot: String? = null,
        val cast: String? = null,
        val director: String? = null,
        val genre: String? = null,
        val releaseDate: String? = null,
        val youtube_trailer: String? = null,
        val episode_run_time: String? = null,
        val last_modified: String? = null,
        val tmdb_id: String? = null,
        val is_adult: String? = null,
    )

    @Serializable
    data class ApiSeriesDetail(
        val seasons: List<ApiSeason>? = null,
        val info: ApiSeriesInfo? = null,
        val episodes: Map<String, List<ApiEpisode>>? = null,
    )

    @Serializable
    data class ApiSeason(
        val season_number: Int? = null,
        val name: String? = null,
    )

    @Serializable
    data class ApiSeriesInfo(
        val name: String? = null,
        val tmdb_id: String? = null,
    )

    @Serializable
    data class ApiEpisode(
        val id: String? = null,
        val season: Int = 0,
        val episode_num: Int = 0,
        val title: String = "",
        val container_extension: String? = null,
        val info: ApiEpisodeInfo? = null,
        val added: String? = null,
    )

    @Serializable
    data class ApiEpisodeInfo(
        val tmdb_id: String? = null,
        val releasedate: String? = null,
        val plot: String? = null,
        val duration_secs: Int? = null,
        val duration: String? = null,
        val rating: String? = null,
        val movie_image: String? = null,
        val video: kotlinx.serialization.json.JsonElement? = null,
        val audio: kotlinx.serialization.json.JsonElement? = null,
        val bitrate: String? = null,
    )

    @Serializable
    data class ApiLiveStream(
        val stream_id: Int,
        val name: String,
        val stream_icon: String? = null,
        val epg_channel_id: String? = null,
        val tv_archive: Int = 0,
        val tv_archive_duration: Int = 0,
        val category_id: String? = null,
        val added: String? = null,
        val is_adult: String? = null,
        val direct_source: String? = null,
    )

    // =========================================================================
    // Golden File Assertion Helper
    // =========================================================================

    /**
     * Compare [raw] against the golden file at [goldenFileName].
     * If the golden file doesn't exist or `-Dgolden.update=true`, writes it.
     */
    private fun assertGolden(
        raw: RawMediaMetadata,
        goldenFileName: String,
    ) {
        val actualJson = RawMetadataJsonSerializer.toJsonElement(raw)
        val actualString = RawMetadataJsonSerializer.toJsonString(raw)

        val goldenFile = File(goldenDir, goldenFileName)

        if (!goldenFile.exists() || shouldUpdate) {
            goldenFile.parentFile.mkdirs()
            goldenFile.writeText(actualString)
            println("📝 Golden file ${if (goldenFile.exists()) "UPDATED" else "CREATED"}: ${goldenFile.path}")
            // Don't fail — just generate
            return
        }

        val expectedString = goldenFile.readText()
        val expectedJson = RawMetadataJsonSerializer.parseGoldenFile(expectedString)

        assertEquals(
            expectedJson,
            actualJson,
            buildString {
                appendLine("Golden file mismatch for $goldenFileName")
                appendLine()
                appendLine("To update golden files, run:")
                appendLine("  ./gradlew :pipeline:xtream:test --tests \"*XtreamGoldenFileTest*\" -Dgolden.update=true")
            },
        )
    }

    // =========================================================================
    // TEST: VOD List item golden file
    // =========================================================================

    @Test
    fun `VOD list item - first item from vod_streams json matches golden`() {
        val file = File(testDataDir, "vod_streams.json")
        if (!file.exists()) {
            println("SKIP: vod_streams.json not found")
            return
        }

        val items: List<ApiVodItem> = json.decodeFromString(file.readText())
        assertTrue(items.isNotEmpty(), "VOD list should not be empty")

        val api = items.first()
        val dto =
            XtreamVodItem(
                id = api.stream_id,
                name = api.name,
                streamIcon = api.stream_icon,
                categoryId = api.category_id,
                containerExtension = api.container_extension,
                added = api.added?.toLongOrNull(),
                rating = api.rating?.toDoubleOrNull(),
                rating5Based = api.rating_5based?.toDoubleOrNull(),
                tmdbId = api.tmdb_id?.toIntOrNull(),
                year = api.year,
                genre = api.genre,
                plot = api.plot,
                duration = api.duration,
                isAdult = api.is_adult == "1",
            )

        val raw = dto.toRawMetadata(accountLabel = accountLabel)

        // Sanity: verify critical identity fields before golden comparison
        assertEquals(MediaType.MOVIE, raw.mediaType, "wrong mediaType")
        assertEquals(SourceType.XTREAM, raw.sourceType, "wrong sourceType")
        assertEquals(PipelineIdTag.XTREAM, raw.pipelineIdTag, "wrong pipelineIdTag")
        assertNotNull(raw.sourceId, "sourceId must not be null")

        assertGolden(raw, "xtream_vod_list_item.json")
    }

    // =========================================================================
    // TEST: VOD Detail golden file (via XtreamVodInfo transport DTO)
    // =========================================================================

    @Test
    fun `VOD detail - 36 Chinatown from vod_details matches golden`() {
        val file = File(testDataDir, "vod_details_response_xtream.txt")
        if (!file.exists()) {
            println("SKIP: vod_details_response_xtream.txt not found")
            return
        }

        val vodInfo: XtreamVodInfo = json.decodeFromString(file.readText())

        // Create a minimal XtreamVodItem for the detail mapper
        val vodItem =
            XtreamVodItem(
                id = vodInfo.movieData?.streamId ?: 0,
                name = vodInfo.movieData?.name ?: "",
                containerExtension = vodInfo.movieData?.containerExtension,
                added = vodInfo.movieData?.added?.toLongOrNull(),
                categoryId = vodInfo.movieData?.categoryId,
            )

        val raw =
            vodInfo.toRawMediaMetadata(
                vodItem = vodItem,
                accountLabel = accountLabel,
            )

        // Sanity checks
        assertEquals(MediaType.MOVIE, raw.mediaType)
        assertEquals(SourceType.XTREAM, raw.sourceType)

        assertGolden(raw, "xtream_vod_detail.json")
    }

    // =========================================================================
    // TEST: Series list item golden file
    // =========================================================================

    @Test
    fun `Series list item - first valid item from series json matches golden`() {
        val file = File(testDataDir, "series.json")
        if (!file.exists()) {
            println("SKIP: series.json not found")
            return
        }

        val items: List<ApiSeriesItem> = json.decodeFromString(file.readText())
        assertTrue(items.isNotEmpty(), "Series list should not be empty")

        // Find the first item with a valid ID (>0)
        val api = items.first { it.series_id > 0 }

        val dto =
            XtreamSeriesItem(
                id = api.series_id,
                name = api.name,
                cover = api.cover,
                backdrop = api.backdrop_path?.firstOrNull(),
                categoryId = api.category_id,
                year = api.year,
                rating = api.rating?.toDoubleOrNull(),
                plot = api.plot,
                cast = api.cast,
                director = api.director,
                genre = api.genre,
                releaseDate = api.releaseDate,
                youtubeTrailer = api.youtube_trailer,
                episodeRunTime = api.episode_run_time,
                lastModified = api.last_modified?.toLongOrNull(),
                tmdbId = api.tmdb_id?.toIntOrNull(),
                isAdult = api.is_adult == "1",
            )

        val raw = dto.toRawMetadata(accountLabel = accountLabel)

        // Sanity checks
        assertEquals(MediaType.SERIES, raw.mediaType)
        assertEquals(SourceType.XTREAM, raw.sourceType)

        assertGolden(raw, "xtream_series_list_item.json")
    }

    // =========================================================================
    // TEST: Episode golden file
    // =========================================================================

    @Test
    fun `Episode - first episode from series_detail matches golden`() {
        val file = File(testDataDir, "series_detail_response_xtream.txt")
        if (!file.exists()) {
            println("SKIP: series_detail_response_xtream.txt not found")
            return
        }

        val detail: ApiSeriesDetail = json.decodeFromString(file.readText())

        // Get first episode from first available season
        val firstSeason = detail.episodes?.entries?.firstOrNull()
        assertNotNull(firstSeason, "should have at least one season")

        val api = firstSeason!!.value.first()
        val seriesName = detail.info?.name ?: "Unknown Series"
        val seriesTmdbId = detail.info?.tmdb_id?.toIntOrNull()

        // Series ID is typically known from the list API; use placeholder matching series name
        val seriesId = 1899

        val dto =
            XtreamEpisode(
                id = api.id?.toIntOrNull() ?: 0,
                seriesId = seriesId,
                seriesName = seriesName,
                seasonNumber = api.season,
                episodeNumber = api.episode_num,
                title = api.title,
                containerExtension = api.container_extension,
                plot = api.info?.plot,
                duration = api.info?.duration,
                durationSecs = api.info?.duration_secs,
                releaseDate = api.info?.releasedate,
                rating = api.info?.rating?.toDoubleOrNull(),
                thumbnail = api.info?.movie_image,
                added = api.added?.toLongOrNull(),
                seriesTmdbId = seriesTmdbId,
                episodeTmdbId = api.info?.tmdb_id?.toIntOrNull(),
            )

        val raw = dto.toRawMediaMetadata()

        // Sanity checks
        assertEquals(MediaType.SERIES_EPISODE, raw.mediaType)
        assertEquals(SourceType.XTREAM, raw.sourceType)

        assertGolden(raw, "xtream_episode.json")
    }

    // =========================================================================
    // TEST: Live channel golden file
    // =========================================================================

    @Test
    fun `Live channel - first item from live_streams json matches golden`() {
        val file = File(testDataDir, "live_streams.json")
        if (!file.exists()) {
            println("SKIP: live_streams.json not found")
            return
        }

        val items: List<ApiLiveStream> = json.decodeFromString(file.readText())
        assertTrue(items.isNotEmpty(), "Live list should not be empty")

        val api = items.first()

        val dto =
            XtreamChannel(
                id = api.stream_id,
                name = api.name,
                streamIcon = api.stream_icon,
                epgChannelId = api.epg_channel_id,
                tvArchive = api.tv_archive,
                tvArchiveDuration = api.tv_archive_duration,
                categoryId = api.category_id,
                added = api.added?.toLongOrNull(),
                isAdult = api.is_adult == "1",
                directSource = api.direct_source,
            )

        val raw = dto.toRawMediaMetadata(accountLabel = accountLabel)

        // Sanity checks
        assertEquals(MediaType.LIVE, raw.mediaType)
        assertEquals(SourceType.XTREAM, raw.sourceType)

        assertGolden(raw, "xtream_live_channel.json")
    }

    // =========================================================================
    // TEST: Multiple VOD items for consistency
    // =========================================================================

    @Test
    fun `VOD list - first 3 items produce consistent non-null sourceIds`() {
        val file = File(testDataDir, "vod_streams.json")
        if (!file.exists()) return

        val items: List<ApiVodItem> = json.decodeFromString(file.readText())
        val count = minOf(3, items.size)

        for (idx in 0 until count) {
            val api = items[idx]
            val dto =
                XtreamVodItem(
                    id = api.stream_id,
                    name = api.name,
                    streamIcon = api.stream_icon,
                    added = api.added?.toLongOrNull(),
                )
            val raw = dto.toRawMetadata(accountLabel = accountLabel)

            assertTrue(raw.sourceId.startsWith("xtream:vod:"), "sourceId format for item $idx")
            assertEquals(api.name, raw.originalTitle, "originalTitle for item $idx")
            assertEquals(SourceType.XTREAM, raw.sourceType, "sourceType for item $idx")
        }
    }
}
