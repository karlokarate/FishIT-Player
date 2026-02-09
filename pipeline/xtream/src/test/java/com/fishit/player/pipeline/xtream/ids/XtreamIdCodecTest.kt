package com.fishit.player.pipeline.xtream.ids

import com.fishit.player.core.model.ids.XtreamParsedSourceId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Tests for [XtreamIdCodec] - Single Source of Truth for Xtream source ID formatting.
 *
 * Per xtream_290126.md Blocker #1:
 * - ONE format per content type
 * - No whitespace
 * - Round-trip stability (format → parse → format = identical)
 */
class XtreamIdCodecTest {

    // =========================================================================
    // VOD Format Tests
    // =========================================================================

    @Test
    fun `vod format produces canonical string`() {
        assertEquals("xtream:vod:123", XtreamIdCodec.vod(123L))
        assertEquals("xtream:vod:999", XtreamIdCodec.vod(999))
    }

    @Test
    fun `vod format with typed wrapper`() {
        val id = XtreamVodId(456L)
        assertEquals("xtream:vod:456", XtreamIdCodec.vod(id))
    }

    @Test
    fun `vod format contains no whitespace`() {
        val result = XtreamIdCodec.vod(123)
        assertEquals(result, result.trim())
        assertEquals(-1, result.indexOf(' '))
    }

    // =========================================================================
    // Series Format Tests
    // =========================================================================

    @Test
    fun `series format produces canonical string`() {
        assertEquals("xtream:series:789", XtreamIdCodec.series(789L))
        assertEquals("xtream:series:100", XtreamIdCodec.series(100))
    }

    @Test
    fun `series format with typed wrapper`() {
        val id = XtreamSeriesId(222L)
        assertEquals("xtream:series:222", XtreamIdCodec.series(id))
    }

    @Test
    fun `series format contains no whitespace`() {
        val result = XtreamIdCodec.series(999)
        assertEquals(result, result.trim())
        assertEquals(-1, result.indexOf(' '))
    }

    // =========================================================================
    // Episode Format Tests
    // =========================================================================

    @Test
    fun `episode format produces canonical string`() {
        assertEquals("xtream:episode:333", XtreamIdCodec.episode(333L))
        assertEquals("xtream:episode:444", XtreamIdCodec.episode(444))
    }

    @Test
    fun `episode format with typed wrapper`() {
        val id = XtreamEpisodeId(555L)
        assertEquals("xtream:episode:555", XtreamIdCodec.episode(id))
    }

    @Test
    fun `episodeComposite format produces canonical string`() {
        assertEquals(
            "xtream:episode:series:100:s1:e5",
            XtreamIdCodec.episodeComposite(100L, 1, 5),
        )
        assertEquals(
            "xtream:episode:series:200:s2:e10",
            XtreamIdCodec.episodeComposite(200, 2, 10),
        )
    }

    @Test
    fun `episodeComposite handles season 0 specials`() {
        assertEquals(
            "xtream:episode:series:100:s0:e1",
            XtreamIdCodec.episodeComposite(100, 0, 1),
        )
    }

    // =========================================================================
    // Live Format Tests
    // =========================================================================

    @Test
    fun `live format produces canonical string`() {
        assertEquals("xtream:live:666", XtreamIdCodec.live(666L))
        assertEquals("xtream:live:777", XtreamIdCodec.live(777))
    }

    @Test
    fun `live format with typed wrapper`() {
        val id = XtreamChannelId(888L)
        assertEquals("xtream:live:888", XtreamIdCodec.live(id))
    }

    @Test
    fun `live format contains no whitespace`() {
        val result = XtreamIdCodec.live(999)
        assertEquals(result, result.trim())
        assertEquals(-1, result.indexOf(' '))
    }

    // =========================================================================
    // Parse Tests
    // =========================================================================

    @Test
    fun `parse vod returns correct type`() {
        val parsed = XtreamIdCodec.parse("xtream:vod:123")
        assertNotNull(parsed)
        assertEquals(XtreamParsedSourceId.Vod(123L), parsed)
    }

    @Test
    fun `parse series returns correct type`() {
        val parsed = XtreamIdCodec.parse("xtream:series:456")
        assertNotNull(parsed)
        assertEquals(XtreamParsedSourceId.Series(456L), parsed)
    }

    @Test
    fun `parse episode with simple ID returns correct type`() {
        val parsed = XtreamIdCodec.parse("xtream:episode:789")
        assertNotNull(parsed)
        assertEquals(XtreamParsedSourceId.Episode(789L), parsed)
    }

    @Test
    fun `parse episode composite returns correct type`() {
        val parsed = XtreamIdCodec.parse("xtream:episode:series:100:s2:e5")
        assertNotNull(parsed)
        assertEquals(
            XtreamParsedSourceId.EpisodeComposite(
                seriesId = 100L,
                season = 2,
                episode = 5,
            ),
            parsed,
        )
    }

    @Test
    fun `parse live returns correct type`() {
        val parsed = XtreamIdCodec.parse("xtream:live:999")
        assertNotNull(parsed)
        assertEquals(XtreamParsedSourceId.Live(999L), parsed)
    }

    @Test
    fun `parse returns null for invalid prefix`() {
        assertNull(XtreamIdCodec.parse("telegram:channel:123"))
        assertNull(XtreamIdCodec.parse("invalid"))
        assertNull(XtreamIdCodec.parse(""))
    }

    @Test
    fun `parse returns null for unknown type`() {
        assertNull(XtreamIdCodec.parse("xtream:unknown:123"))
    }

    @Test
    fun `parse returns null for invalid number`() {
        assertNull(XtreamIdCodec.parse("xtream:vod:abc"))
        assertNull(XtreamIdCodec.parse("xtream:vod:"))
    }

    // =========================================================================
    // Round-Trip Tests (CRITICAL for stability)
    // =========================================================================

    @Test
    fun `roundTrip vod format then parse then format is stable`() {
        val original = XtreamIdCodec.vod(12345)
        val parsed = XtreamIdCodec.parse(original)
        assertNotNull(parsed)
        val reformatted = XtreamIdCodec.format(parsed!!)
        assertEquals(original, reformatted)
    }

    @Test
    fun `roundTrip series format then parse then format is stable`() {
        val original = XtreamIdCodec.series(67890)
        val parsed = XtreamIdCodec.parse(original)
        assertNotNull(parsed)
        val reformatted = XtreamIdCodec.format(parsed!!)
        assertEquals(original, reformatted)
    }

    @Test
    fun `roundTrip episode format then parse then format is stable`() {
        val original = XtreamIdCodec.episode(11111)
        val parsed = XtreamIdCodec.parse(original)
        assertNotNull(parsed)
        val reformatted = XtreamIdCodec.format(parsed!!)
        assertEquals(original, reformatted)
    }

    @Test
    fun `roundTrip episodeComposite format then parse then format is stable`() {
        val original = XtreamIdCodec.episodeComposite(100, 3, 7)
        val parsed = XtreamIdCodec.parse(original)
        assertNotNull(parsed)
        val reformatted = XtreamIdCodec.format(parsed!!)
        assertEquals(original, reformatted)
    }

    @Test
    fun `roundTrip live format then parse then format is stable`() {
        val original = XtreamIdCodec.live(99999)
        val parsed = XtreamIdCodec.parse(original)
        assertNotNull(parsed)
        val reformatted = XtreamIdCodec.format(parsed!!)
        assertEquals(original, reformatted)
    }

    // =========================================================================
    // Edge Cases
    // =========================================================================

    @Test
    fun `large IDs are handled correctly`() {
        val largeId = Int.MAX_VALUE.toLong() + 1
        val formatted = XtreamIdCodec.vod(largeId)
        assertEquals("xtream:vod:2147483648", formatted)
        val parsed = XtreamIdCodec.parse(formatted)
        assertEquals(XtreamParsedSourceId.Vod(largeId), parsed)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `vod rejects zero ID`() {
        XtreamIdCodec.vod(0L)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `vod rejects negative ID`() {
        XtreamIdCodec.vod(-1L)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `episodeComposite rejects negative season`() {
        XtreamIdCodec.episodeComposite(100L, -1, 1)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `episodeComposite rejects negative episode`() {
        XtreamIdCodec.episodeComposite(100L, 1, -1)
    }
}
