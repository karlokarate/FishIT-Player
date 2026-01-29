package com.fishit.player.pipeline.xtream.integration

import com.fishit.player.pipeline.xtream.ids.XtreamIdCodec
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * XtreamIdCodec Format Tests
 *
 * Verifies consistent ID format across all content types:
 * - VOD: xtream:vod:{id}
 * - Series: xtream:series:{id}
 * - Episode: xtream:episode:{id} or xtream:episode:series:{seriesId}:s{season}:e{episode}
 * - Live: xtream:live:{id}
 */
class XtreamIdCodecIntegrationTest {

    @Test
    fun `CODEC - VOD format`() {
        assertEquals("xtream:vod:123", XtreamIdCodec.vod(123))
        assertEquals("xtream:vod:1", XtreamIdCodec.vod(1))
        assertEquals("xtream:vod:999999", XtreamIdCodec.vod(999999))
        println("  ✅ XtreamIdCodec.vod() format verified")
    }

    @Test
    fun `CODEC - Series format`() {
        assertEquals("xtream:series:456", XtreamIdCodec.series(456))
        assertEquals("xtream:series:1", XtreamIdCodec.series(1))
        assertEquals("xtream:series:999999", XtreamIdCodec.series(999999))
        println("  ✅ XtreamIdCodec.series() format verified")
    }

    @Test
    fun `CODEC - Live format`() {
        assertEquals("xtream:live:789", XtreamIdCodec.live(789))
        assertEquals("xtream:live:1", XtreamIdCodec.live(1))
        assertEquals("xtream:live:999999", XtreamIdCodec.live(999999))
        println("  ✅ XtreamIdCodec.live() format verified")
    }

    @Test
    fun `CODEC - Episode direct ID format`() {
        assertEquals("xtream:episode:12345", XtreamIdCodec.episode(12345))
        assertEquals("xtream:episode:1", XtreamIdCodec.episode(1))
        assertEquals("xtream:episode:999999", XtreamIdCodec.episode(999999))
        println("  ✅ XtreamIdCodec.episode(id) format verified")
    }

    @Test
    fun `CODEC - Episode composite format`() {
        assertEquals(
            "xtream:episode:series:100:s2:e5",
            XtreamIdCodec.episodeComposite(seriesId = 100, season = 2, episodeNum = 5)
        )
        assertEquals(
            "xtream:episode:series:1:s1:e1",
            XtreamIdCodec.episodeComposite(seriesId = 1, season = 1, episodeNum = 1)
        )
        assertEquals(
            "xtream:episode:series:999:s10:e25",
            XtreamIdCodec.episodeComposite(seriesId = 999, season = 10, episodeNum = 25)
        )
        println("  ✅ XtreamIdCodec.episodeComposite() format verified")
    }

    @Test
    fun `CODEC - all formats are unique`() {
        // Same numeric ID should produce different sourceIds per type
        val vodId = XtreamIdCodec.vod(100)
        val seriesId = XtreamIdCodec.series(100)
        val liveId = XtreamIdCodec.live(100)
        val episodeId = XtreamIdCodec.episode(100)

        val ids = setOf(vodId, seriesId, liveId, episodeId)
        assertEquals(4, ids.size, "All sourceIds should be unique")

        println("  ✅ All content types produce unique sourceIds")
        println("     vod:100      → $vodId")
        println("     series:100   → $seriesId")
        println("     live:100     → $liveId")
        println("     episode:100  → $episodeId")
    }
}
