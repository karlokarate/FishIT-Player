package com.fishit.player.pipeline.xtream.integration

import com.fishit.player.core.model.MediaType
import com.fishit.player.core.model.SourceType
import com.fishit.player.pipeline.xtream.ids.XtreamIdCodec
import com.fishit.player.pipeline.xtream.mapper.toRawMediaMetadata
import com.fishit.player.pipeline.xtream.mapper.toRawMetadata
import com.fishit.player.pipeline.xtream.model.XtreamEpisode
import com.fishit.player.pipeline.xtream.model.XtreamSeriesItem
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Series API Integration Tests using REAL captured Xtream responses.
 *
 * API Calls tested:
 * - get_series (Series list)
 * - get_series_info (Series detail + episodes)
 *
 * Test data: `/test-data/xtream-responses/`
 */
class XtreamSeriesIntegrationTest {

    private val testDataDir = File("test-data/xtream-responses")
    private val accountName = "test-account"

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    // =========================================================================
    // API DTOs for parsing real Series JSON
    // =========================================================================

    @Serializable
    data class ApiSeriesItem(
        val series_id: Int,
        val name: String,
        val cover: String? = null,
        val category_id: String? = null,
        val last_modified: String? = null,
        val rating: String? = null,
        val rating_5based: Double? = null,
        val plot: String? = null,
        val cast: String? = null,
        val director: String? = null,
        val genre: String? = null,
        val releaseDate: String? = null,
        val backdrop_path: List<String>? = null,
    )

    @Serializable
    data class ApiSeriesDetail(
        val info: ApiSeriesInfo,
        val episodes: Map<String, List<ApiEpisode>>,
    )

    @Serializable
    data class ApiSeriesInfo(
        val name: String? = null,
        val cover: String? = null,
        val plot: String? = null,
        val cast: String? = null,
        val director: String? = null,
        val genre: String? = null,
        val releaseDate: String? = null,
        val last_modified: String? = null,
        val rating: String? = null,
        val rating_5based: Double? = null,
        val backdrop_path: List<String>? = null,
        val category_id: String? = null,
    )

    @Serializable
    data class ApiEpisode(
        val id: String,
        val episode_num: Int,
        val title: String? = null,
        val container_extension: String? = null,
        val season: Int,
        val added: String? = null,
        val info: ApiEpisodeInfo? = null,
    )

    @Serializable
    data class ApiEpisodeInfo(
        val tmdb_id: Int? = null,
        val releasedate: String? = null,
        val plot: String? = null,
        val duration_secs: Int? = null,
        val movie_image: String? = null,
        val rating: Double? = null,
        val season: String? = null,
    )

    // =========================================================================
    // TEST: Series List API (get_series)
    // Chain: JSON → ApiSeriesItem → XtreamSeriesItem → RawMediaMetadata
    //
    // NOTE: Some Xtream providers return invalid series_id values (0, negative).
    // We filter to only test valid entries (positive IDs).
    // =========================================================================

    @Test
    fun `SERIES_LIST - field mapping and XtreamIdCodec`() {
        val file = File(testDataDir, "series.json")
        if (!file.exists()) {
            println("SKIP: series.json not found")
            return
        }

        val apiItems: List<ApiSeriesItem> = json.decodeFromString(file.readText())
        assertTrue(apiItems.isNotEmpty(), "Series list should not be empty")
        println("📺 Series List: ${apiItems.size} items")

        // Filter to only test items with valid (positive) series_id
        var testedCount = 0
        for (api in apiItems) {
            if (api.series_id <= 0) continue // Skip invalid IDs
            if (testedCount >= 5) break

            val dto = XtreamSeriesItem(
                id = api.series_id,
                name = api.name,
                cover = api.cover,
                categoryId = api.category_id,
                lastModified = api.last_modified?.toLongOrNull(),
                rating = api.rating?.toDoubleOrNull(),
                plot = api.plot,
                cast = api.cast,
                director = api.director,
                genre = api.genre,
                releaseDate = api.releaseDate,
                backdrop = api.backdrop_path?.firstOrNull(),
            )

            val raw = dto.toRawMetadata(accountName = accountName)

            // XtreamIdCodec
            assertEquals("xtream:series:${api.series_id}", raw.sourceId, "sourceId")
            assertEquals(XtreamIdCodec.series(api.series_id), raw.sourceId, "XtreamIdCodec format")

            // Core fields
            assertEquals(api.name, raw.originalTitle, "originalTitle")
            assertEquals(MediaType.SERIES, raw.mediaType, "mediaType")
            assertEquals(SourceType.XTREAM, raw.sourceType, "sourceType")
            assertEquals("", raw.globalId, "globalId must be empty")

            // Timestamps (Series uses last_modified)
            val expectedTs = api.last_modified?.toLongOrNull()
            assertEquals(expectedTs, raw.lastModifiedTimestamp, "lastModifiedTimestamp")
            assertEquals(expectedTs, raw.addedTimestamp, "addedTimestamp")

            // Category
            assertEquals(api.category_id, raw.categoryId, "categoryId")

            println("  ✅ [${api.series_id}] ${api.name.take(40)}")
            println("     timestamps: lastMod=$expectedTs, added=${raw.addedTimestamp}")
            testedCount++
        }
        assertTrue(testedCount > 0, "Should have found series with valid IDs")
    }

    @Test
    fun `SERIES_LIST - rich metadata propagation`() {
        val file = File(testDataDir, "series.json")
        if (!file.exists()) return

        val apiItems: List<ApiSeriesItem> = json.decodeFromString(file.readText())
        var testedCount = 0

        for (api in apiItems) {
            if (api.series_id <= 0) continue // Skip invalid IDs
            if (api.plot.isNullOrBlank()) continue
            if (testedCount >= 3) break

            val dto = XtreamSeriesItem(
                id = api.series_id,
                name = api.name,
                plot = api.plot,
                cast = api.cast,
                director = api.director,
                genre = api.genre,
            )
            val raw = dto.toRawMetadata()

            assertEquals(api.plot, raw.plot, "plot")
            assertEquals(api.cast, raw.cast, "cast")
            assertEquals(api.director, raw.director, "director")
            assertEquals(api.genre, raw.genres, "genres")

            println("  ✅ ${api.name.take(30)}: plot=${raw.plot?.take(30)}...")
            testedCount++
        }
        assertTrue(testedCount > 0, "Should have found series with plots")
    }

    // =========================================================================
    // TEST: Series Detail + Episodes API (get_series_info)
    // Chain: JSON → ApiSeriesDetail → XtreamEpisode → RawMediaMetadata
    //
    // NOTE: The API response doesn't include series_id directly.
    // In practice, the caller knows the series_id from the list API call.
    // For this test, we use a placeholder series_id = 1899 (from filename).
    // =========================================================================

    @Test
    fun `SERIES_DETAIL - episode field mapping and XtreamIdCodec`() {
        val file = File(testDataDir, "series_detail_response_xtream.txt")
        if (!file.exists()) {
            println("SKIP: series_detail_response_xtream.txt not found")
            return
        }

        val detail: ApiSeriesDetail = json.decodeFromString(file.readText())
        val seriesName = detail.info.name ?: "Unknown"
        // Series ID is typically known from the list API call that triggered this detail fetch
        // Using 1899 as a test value (matches the series name "1899")
        val seriesId = 1899
        println("📺 Series Detail: $seriesName (ID: $seriesId)")

        var episodeCount = 0

        // Iterate over seasons using entries
        val seasonEntries = detail.episodes.entries.toList()
        for (seasonEntry in seasonEntries) {
            val seasonKey = seasonEntry.key
            val episodeList = seasonEntry.value
            val seasonNum = seasonKey.toIntOrNull() ?: continue

            for (ep in episodeList) {
                val episodeId = ep.id.toIntOrNull() ?: continue
                if (episodeId <= 0) continue // Skip invalid episode IDs
                episodeCount++

                val dto = XtreamEpisode(
                    id = episodeId,
                    seriesId = seriesId,
                    seriesName = seriesName,
                    seasonNumber = seasonNum,
                    episodeNumber = ep.episode_num,
                    title = ep.title ?: "",
                    containerExtension = ep.container_extension,
                    added = ep.added?.toLongOrNull(),
                    plot = ep.info?.plot,
                    thumbnail = ep.info?.movie_image,
                    rating = ep.info?.rating,
                    episodeTmdbId = ep.info?.tmdb_id,
                )

                val raw = dto.toRawMediaMetadata(seriesNameOverride = seriesName)

                // XtreamIdCodec composite format for episodes
                val expectedSourceId = XtreamIdCodec.episodeComposite(
                    seriesId = seriesId,
                    season = seasonNum,
                    episodeNum = ep.episode_num,
                )
                assertEquals(expectedSourceId, raw.sourceId, "sourceId")
                assertTrue(raw.sourceId.startsWith("xtream:episode:series:"), "sourceId prefix")
                assertTrue(raw.sourceId.contains(":s${seasonNum}:e${ep.episode_num}"), "sourceId season/episode")

                // Core fields
                assertEquals(MediaType.SERIES_EPISODE, raw.mediaType, "mediaType")
                assertEquals(seasonNum, raw.season, "season")
                assertEquals(ep.episode_num, raw.episode, "episode")
                assertEquals("", raw.globalId, "globalId must be empty")

                // Timestamps
                val expectedTs = ep.added?.toLongOrNull()
                assertEquals(expectedTs, raw.addedTimestamp, "addedTimestamp")
                assertEquals(expectedTs, raw.lastModifiedTimestamp, "lastModifiedTimestamp")

                if (episodeCount <= 5) {
                    println("  ✅ S${seasonNum}E${ep.episode_num}: ${ep.title?.take(30) ?: "(no title)"}")
                }
            }
        }

        assertTrue(episodeCount > 0, "Should have parsed episodes")
        println("  📊 Total: $episodeCount episodes")
    }
}
