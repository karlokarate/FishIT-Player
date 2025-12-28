package com.fishit.player.core.catalogsync

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Unit tests for [XtreamSyncCheckpoint].
 *
 * Tests checkpoint encoding/decoding roundtrips and phase advancement logic.
 */
class XtreamSyncCheckpointTest {
    @Test
    fun `encode and decode roundtrip for INITIAL checkpoint`() {
        val checkpoint = XtreamSyncCheckpoint.INITIAL
        val encoded = checkpoint.encode()
        val decoded = XtreamSyncCheckpoint.decode(encoded)

        assertEquals(checkpoint.phase, decoded.phase)
        assertEquals(checkpoint.offset, decoded.offset)
        assertEquals(checkpoint.seriesIndex, decoded.seriesIndex)
        assertNull(decoded.lastVodInfoId)
        assertNull(decoded.lastSeriesInfoId)
    }

    @Test
    fun `encode and decode roundtrip with all fields`() {
        val checkpoint =
            XtreamSyncCheckpoint(
                phase = XtreamSyncPhase.VOD_INFO,
                offset = 150,
                seriesIndex = 42,
                lastVodInfoId = 12345,
                lastSeriesInfoId = 6789,
            )
        val encoded = checkpoint.encode()
        val decoded = XtreamSyncCheckpoint.decode(encoded)

        assertEquals(checkpoint.phase, decoded.phase)
        assertEquals(checkpoint.offset, decoded.offset)
        assertEquals(checkpoint.seriesIndex, decoded.seriesIndex)
        assertEquals(checkpoint.lastVodInfoId, decoded.lastVodInfoId)
        assertEquals(checkpoint.lastSeriesInfoId, decoded.lastSeriesInfoId)
    }

    @Test
    fun `decode null returns INITIAL`() {
        val decoded = XtreamSyncCheckpoint.decode(null)
        assertEquals(XtreamSyncCheckpoint.INITIAL, decoded)
    }

    @Test
    fun `decode empty string returns INITIAL`() {
        val decoded = XtreamSyncCheckpoint.decode("")
        assertEquals(XtreamSyncCheckpoint.INITIAL, decoded)
    }

    @Test
    fun `decode invalid prefix returns INITIAL`() {
        val decoded = XtreamSyncCheckpoint.decode("telegram|phase=VOD_LIST|offset=0")
        assertEquals(XtreamSyncCheckpoint.INITIAL, decoded)
    }

    @Test
    fun `decode malformed string returns INITIAL`() {
        val decoded = XtreamSyncCheckpoint.decode("xtream|garbage")
        assertEquals(XtreamSyncCheckpoint.INITIAL, decoded)
    }

    @Test
    fun `advancePhase follows correct order`() {
        var checkpoint = XtreamSyncCheckpoint.INITIAL

        // VOD_LIST -> SERIES_LIST
        checkpoint = checkpoint.advancePhase()
        assertEquals(XtreamSyncPhase.SERIES_LIST, checkpoint.phase)

        // SERIES_LIST -> SERIES_EPISODES
        checkpoint = checkpoint.advancePhase()
        assertEquals(XtreamSyncPhase.SERIES_EPISODES, checkpoint.phase)

        // SERIES_EPISODES -> LIVE_LIST
        checkpoint = checkpoint.advancePhase()
        assertEquals(XtreamSyncPhase.LIVE_LIST, checkpoint.phase)

        // LIVE_LIST -> VOD_INFO
        checkpoint = checkpoint.advancePhase()
        assertEquals(XtreamSyncPhase.VOD_INFO, checkpoint.phase)

        // VOD_INFO -> SERIES_INFO
        checkpoint = checkpoint.advancePhase()
        assertEquals(XtreamSyncPhase.SERIES_INFO, checkpoint.phase)

        // SERIES_INFO -> COMPLETED
        checkpoint = checkpoint.advancePhase()
        assertEquals(XtreamSyncPhase.COMPLETED, checkpoint.phase)

        // COMPLETED stays COMPLETED
        checkpoint = checkpoint.advancePhase()
        assertEquals(XtreamSyncPhase.COMPLETED, checkpoint.phase)
    }

    @Test
    fun `isCompleted returns true only for COMPLETED phase`() {
        assertFalse(XtreamSyncCheckpoint(XtreamSyncPhase.VOD_LIST).isCompleted)
        assertFalse(XtreamSyncCheckpoint(XtreamSyncPhase.SERIES_LIST).isCompleted)
        assertFalse(XtreamSyncCheckpoint(XtreamSyncPhase.LIVE_LIST).isCompleted)
        assertFalse(XtreamSyncCheckpoint(XtreamSyncPhase.VOD_INFO).isCompleted)
        assertFalse(XtreamSyncCheckpoint(XtreamSyncPhase.SERIES_INFO).isCompleted)
        assertTrue(XtreamSyncCheckpoint(XtreamSyncPhase.COMPLETED).isCompleted)
    }

    @Test
    fun `isListPhase returns true only for list phases`() {
        assertTrue(XtreamSyncCheckpoint(XtreamSyncPhase.VOD_LIST).isListPhase)
        assertTrue(XtreamSyncCheckpoint(XtreamSyncPhase.SERIES_LIST).isListPhase)
        assertTrue(XtreamSyncCheckpoint(XtreamSyncPhase.LIVE_LIST).isListPhase)
        assertFalse(XtreamSyncCheckpoint(XtreamSyncPhase.SERIES_EPISODES).isListPhase)
        assertFalse(XtreamSyncCheckpoint(XtreamSyncPhase.VOD_INFO).isListPhase)
        assertFalse(XtreamSyncCheckpoint(XtreamSyncPhase.SERIES_INFO).isListPhase)
        assertFalse(XtreamSyncCheckpoint(XtreamSyncPhase.COMPLETED).isListPhase)
    }

    @Test
    fun `isInfoBackfillPhase returns true only for info phases`() {
        assertTrue(XtreamSyncCheckpoint(XtreamSyncPhase.VOD_INFO).isInfoBackfillPhase)
        assertTrue(XtreamSyncCheckpoint(XtreamSyncPhase.SERIES_INFO).isInfoBackfillPhase)
        assertFalse(XtreamSyncCheckpoint(XtreamSyncPhase.VOD_LIST).isInfoBackfillPhase)
        assertFalse(XtreamSyncCheckpoint(XtreamSyncPhase.SERIES_LIST).isInfoBackfillPhase)
        assertFalse(XtreamSyncCheckpoint(XtreamSyncPhase.LIVE_LIST).isInfoBackfillPhase)
        assertFalse(XtreamSyncCheckpoint(XtreamSyncPhase.COMPLETED).isInfoBackfillPhase)
    }

    @Test
    fun `withOffset preserves other fields`() {
        val original =
            XtreamSyncCheckpoint(
                phase = XtreamSyncPhase.VOD_LIST,
                offset = 10,
                seriesIndex = 5,
            )
        val updated = original.withOffset(100)

        assertEquals(100, updated.offset)
        assertEquals(original.phase, updated.phase)
        assertEquals(original.seriesIndex, updated.seriesIndex)
    }

    @Test
    fun `withSeriesIndex preserves other fields`() {
        val original =
            XtreamSyncCheckpoint(
                phase = XtreamSyncPhase.SERIES_EPISODES,
                offset = 10,
                seriesIndex = 5,
            )
        val updated = original.withSeriesIndex(99)

        assertEquals(99, updated.seriesIndex)
        assertEquals(original.phase, updated.phase)
        assertEquals(original.offset, updated.offset)
    }

    @Test
    fun `withLastVodInfoId preserves other fields`() {
        val original =
            XtreamSyncCheckpoint(
                phase = XtreamSyncPhase.VOD_INFO,
                offset = 0,
                lastVodInfoId = 100,
            )
        val updated = original.withLastVodInfoId(500)

        assertEquals(500, updated.lastVodInfoId)
        assertEquals(original.phase, updated.phase)
    }

    @Test
    fun `encoded string format is stable and parseable`() {
        val checkpoint =
            XtreamSyncCheckpoint(
                phase = XtreamSyncPhase.SERIES_EPISODES,
                offset = 42,
                seriesIndex = 7,
            )
        val encoded = checkpoint.encode()

        // Format should be: xtream|phase=SERIES_EPISODES|offset=42|series_index=7
        assertTrue(encoded.startsWith("xtream|"))
        assertTrue(encoded.contains("phase=SERIES_EPISODES"))
        assertTrue(encoded.contains("offset=42"))
        assertTrue(encoded.contains("series_index=7"))
    }

    @Test
    fun `decode handles missing optional fields gracefully`() {
        // Only required fields
        val encoded = "xtream|phase=VOD_LIST|offset=0"
        val decoded = XtreamSyncCheckpoint.decode(encoded)

        assertEquals(XtreamSyncPhase.VOD_LIST, decoded.phase)
        assertEquals(0, decoded.offset)
        assertEquals(0, decoded.seriesIndex) // Default
        assertNull(decoded.lastVodInfoId)
        assertNull(decoded.lastSeriesInfoId)
    }
}
