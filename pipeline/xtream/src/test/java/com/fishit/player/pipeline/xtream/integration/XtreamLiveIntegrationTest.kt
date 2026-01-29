package com.fishit.player.pipeline.xtream.integration

import com.fishit.player.core.model.MediaType
import com.fishit.player.core.model.SourceType
import com.fishit.player.pipeline.xtream.ids.XtreamIdCodec
import com.fishit.player.pipeline.xtream.mapper.toRawMediaMetadata
import com.fishit.player.pipeline.xtream.model.XtreamChannel
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Live API Integration Tests using REAL captured Xtream responses.
 *
 * API Calls tested:
 * - get_live_streams (Live channel list)
 *
 * Test data: `/test-data/xtream-responses/`
 */
class XtreamLiveIntegrationTest {

    private val testDataDir = File("test-data/xtream-responses")
    private val accountName = "test-account"

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    // Helper to mirror the mapper's cleanLiveChannelName() function
    // (private in mapper, so we duplicate the logic for test verification)
    private val unicodeDecorators = Regex("[\\u2580-\\u259F\\u2500-\\u257F▌▐▀▄█▀▄▃▅▆]+")
    private val whitespaceCollapse = Regex("\\s+")
    private fun cleanLiveChannelName(name: String): String =
        name.replace(unicodeDecorators, " ").replace(whitespaceCollapse, " ").trim()

    // =========================================================================
    // API DTOs for parsing real Live JSON
    // =========================================================================

    @Serializable
    data class ApiLiveStream(
        val stream_id: Int,
        val name: String,
        val stream_type: String? = null,
        val stream_icon: String? = null,
        val epg_channel_id: String? = null,
        val category_id: String? = null,
        val added: String? = null,
        val tv_archive: Int = 0,
        val tv_archive_duration: Int = 0,
    )

    // =========================================================================
    // TEST: Live Streams API (get_live_streams)
    // Chain: JSON → ApiLiveStream → XtreamChannel → RawMediaMetadata
    //
    // NOTE: The mapper cleans Unicode decorators from live channel names.
    // This test mirrors that behavior with cleanLiveChannelName().
    // =========================================================================

    @Test
    fun `LIVE_LIST - field mapping and XtreamIdCodec`() {
        val file = File(testDataDir, "live_streams.json")
        if (!file.exists()) {
            println("SKIP: live_streams.json not found")
            return
        }

        val apiItems: List<ApiLiveStream> = json.decodeFromString(file.readText())
        assertTrue(apiItems.isNotEmpty(), "Live list should not be empty")
        println("📺 Live List: ${apiItems.size} items")

        val count = minOf(5, apiItems.size)
        for (idx in 0 until count) {
            val api: ApiLiveStream = apiItems[idx]

            val dto = XtreamChannel(
                id = api.stream_id,
                name = api.name,
                streamIcon = api.stream_icon,
                epgChannelId = api.epg_channel_id,
                tvArchive = api.tv_archive,
                tvArchiveDuration = api.tv_archive_duration,
                categoryId = api.category_id,
                added = api.added?.toLongOrNull(),
            )

            val raw = dto.toRawMediaMetadata(accountName = accountName)

            // XtreamIdCodec
            assertEquals("xtream:live:${api.stream_id}", raw.sourceId, "sourceId")
            assertEquals(XtreamIdCodec.live(api.stream_id), raw.sourceId, "XtreamIdCodec format")

            // Core fields - NOTE: name is cleaned of Unicode decorators
            val expectedTitle = cleanLiveChannelName(api.name)
            assertEquals(expectedTitle, raw.originalTitle, "originalTitle (cleaned)")
            assertEquals(MediaType.LIVE, raw.mediaType, "mediaType")
            assertEquals(SourceType.XTREAM, raw.sourceType, "sourceType")
            assertEquals("", raw.globalId, "globalId must be empty")

            // Timestamps
            val expectedTs = api.added?.toLongOrNull()
            assertEquals(expectedTs, raw.addedTimestamp, "addedTimestamp")
            assertEquals(expectedTs, raw.lastModifiedTimestamp, "lastModifiedTimestamp")

            // Category
            assertEquals(api.category_id, raw.categoryId, "categoryId")

            println("  ✅ [${api.stream_id}] ${raw.originalTitle}")
            println("     timestamps: added=$expectedTs, lastMod=${raw.lastModifiedTimestamp}")
        }
    }

    @Test
    fun `LIVE_LIST - EPG and Archive field propagation`() {
        val file = File(testDataDir, "live_streams.json")
        if (!file.exists()) return

        val apiItems: List<ApiLiveStream> = json.decodeFromString(file.readText())
        var testedCount = 0

        for (idx in apiItems.indices) {
            val api: ApiLiveStream = apiItems[idx]
            if (api.epg_channel_id.isNullOrBlank()) continue
            if (testedCount >= 3) break

            val dto = XtreamChannel(
                id = api.stream_id,
                name = api.name,
                epgChannelId = api.epg_channel_id,
                tvArchive = api.tv_archive,
                tvArchiveDuration = api.tv_archive_duration,
            )
            val raw = dto.toRawMediaMetadata()

            // Direct field assertions
            assertEquals(api.epg_channel_id, raw.epgChannelId)
            assertEquals(api.tv_archive, raw.tvArchive)
            assertEquals(api.tv_archive_duration, raw.tvArchiveDuration)

            println("  ✅ EPG: ${raw.epgChannelId} | Archive: ${raw.tvArchive}d")
            testedCount++
        }
        assertTrue(testedCount > 0, "Should have found channels with EPG")
    }
}
