package com.chris.m3usuite.player.internal.state

import com.chris.m3usuite.player.internal.domain.PlaybackType
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for Phase 3 Step 3.A Live-TV fields in InternalPlayerUiState.
 *
 * These tests validate that:
 * - Live-TV fields have correct default values
 * - Live-TV fields can be set and retrieved
 * - Data class copy preserves Live-TV fields
 * - Fields are independent and nullable as required
 */
class InternalPlayerUiStatePhase3LiveTest {
    // ════════════════════════════════════════════════════════════════════════════
    // Default Values Tests
    // ════════════════════════════════════════════════════════════════════════════

    @Test
    fun `default InternalPlayerUiState has null Live-TV fields`() {
        val state = InternalPlayerUiState()

        assertNull("liveChannelName should be null by default", state.liveChannelName)
        assertNull("liveNowTitle should be null by default", state.liveNowTitle)
        assertNull("liveNextTitle should be null by default", state.liveNextTitle)
        assertFalse("epgOverlayVisible should be false by default", state.epgOverlayVisible)
    }

    // ════════════════════════════════════════════════════════════════════════════
    // Field Setting and Retrieval Tests
    // ════════════════════════════════════════════════════════════════════════════

    @Test
    fun `can set liveChannelName`() {
        val state = InternalPlayerUiState(
            liveChannelName = "BBC One"
        )

        assertEquals("BBC One", state.liveChannelName)
    }

    @Test
    fun `can set liveNowTitle`() {
        val state = InternalPlayerUiState(
            liveNowTitle = "Evening News"
        )

        assertEquals("Evening News", state.liveNowTitle)
    }

    @Test
    fun `can set liveNextTitle`() {
        val state = InternalPlayerUiState(
            liveNextTitle = "Primetime Drama"
        )

        assertEquals("Primetime Drama", state.liveNextTitle)
    }

    @Test
    fun `can set epgOverlayVisible`() {
        val state = InternalPlayerUiState(
            epgOverlayVisible = true
        )

        assertTrue(state.epgOverlayVisible)
    }

    @Test
    fun `can set all Live-TV fields together`() {
        val state = InternalPlayerUiState(
            playbackType = PlaybackType.LIVE,
            liveChannelName = "CNN",
            liveNowTitle = "Breaking News",
            liveNextTitle = "World Today",
            epgOverlayVisible = true
        )

        assertEquals(PlaybackType.LIVE, state.playbackType)
        assertEquals("CNN", state.liveChannelName)
        assertEquals("Breaking News", state.liveNowTitle)
        assertEquals("World Today", state.liveNextTitle)
        assertTrue(state.epgOverlayVisible)
    }

    // ════════════════════════════════════════════════════════════════════════════
    // Data Class Copy Tests
    // ════════════════════════════════════════════════════════════════════════════

    @Test
    fun `copy preserves Live-TV fields`() {
        val original = InternalPlayerUiState(
            liveChannelName = "ESPN",
            liveNowTitle = "Live Sports",
            liveNextTitle = "Sports Center",
            epgOverlayVisible = true
        )

        val copy = original.copy()

        assertEquals(original.liveChannelName, copy.liveChannelName)
        assertEquals(original.liveNowTitle, copy.liveNowTitle)
        assertEquals(original.liveNextTitle, copy.liveNextTitle)
        assertEquals(original.epgOverlayVisible, copy.epgOverlayVisible)
    }

    @Test
    fun `copy can modify liveChannelName`() {
        val original = InternalPlayerUiState(liveChannelName = "HBO")
        val modified = original.copy(liveChannelName = "HBO Max")

        assertEquals("HBO Max", modified.liveChannelName)
    }

    @Test
    fun `copy can modify liveNowTitle`() {
        val original = InternalPlayerUiState(liveNowTitle = "Show 1")
        val modified = original.copy(liveNowTitle = "Show 2")

        assertEquals("Show 2", modified.liveNowTitle)
    }

    @Test
    fun `copy can modify liveNextTitle`() {
        val original = InternalPlayerUiState(liveNextTitle = "Next 1")
        val modified = original.copy(liveNextTitle = "Next 2")

        assertEquals("Next 2", modified.liveNextTitle)
    }

    @Test
    fun `copy can toggle epgOverlayVisible`() {
        val original = InternalPlayerUiState(epgOverlayVisible = false)
        val modified = original.copy(epgOverlayVisible = true)

        assertTrue(modified.epgOverlayVisible)
        assertFalse(original.epgOverlayVisible)
    }

    // ════════════════════════════════════════════════════════════════════════════
    // Field Independence Tests
    // ════════════════════════════════════════════════════════════════════════════

    @Test
    fun `liveChannelName is independent of other fields`() {
        val state1 = InternalPlayerUiState(liveChannelName = "Channel 1")
        val state2 = InternalPlayerUiState(liveNowTitle = "Now 1")

        assertEquals("Channel 1", state1.liveChannelName)
        assertNull(state1.liveNowTitle)
        assertNull(state2.liveChannelName)
        assertEquals("Now 1", state2.liveNowTitle)
    }

    @Test
    fun `epgOverlayVisible is independent of other Live-TV fields`() {
        val state = InternalPlayerUiState(
            epgOverlayVisible = true,
            liveChannelName = null,
            liveNowTitle = null,
            liveNextTitle = null
        )

        assertTrue(state.epgOverlayVisible)
        assertNull(state.liveChannelName)
        assertNull(state.liveNowTitle)
        assertNull(state.liveNextTitle)
    }

    // ════════════════════════════════════════════════════════════════════════════
    // Integration with Existing Fields Tests
    // ════════════════════════════════════════════════════════════════════════════

    @Test
    fun `Live-TV fields do not affect isLive property`() {
        val vodState = InternalPlayerUiState(
            playbackType = PlaybackType.VOD,
            liveChannelName = "Test Channel"
        )
        val liveState = InternalPlayerUiState(
            playbackType = PlaybackType.LIVE,
            liveChannelName = null
        )

        assertFalse(vodState.isLive)
        assertTrue(liveState.isLive)
    }

    @Test
    fun `Live-TV fields work with LIVE playbackType`() {
        val state = InternalPlayerUiState(
            playbackType = PlaybackType.LIVE,
            liveChannelName = "National Geographic",
            liveNowTitle = "Wildlife Documentary",
            liveNextTitle = "Ocean Explorers",
            epgOverlayVisible = true
        )

        assertTrue("isLive should be true", state.isLive)
        assertEquals("National Geographic", state.liveChannelName)
        assertEquals("Wildlife Documentary", state.liveNowTitle)
        assertEquals("Ocean Explorers", state.liveNextTitle)
        assertTrue(state.epgOverlayVisible)
    }

    @Test
    fun `Live-TV fields are nullable for VOD and SERIES types`() {
        val vodState = InternalPlayerUiState(playbackType = PlaybackType.VOD)
        val seriesState = InternalPlayerUiState(playbackType = PlaybackType.SERIES)

        assertNull(vodState.liveChannelName)
        assertNull(vodState.liveNowTitle)
        assertNull(vodState.liveNextTitle)
        assertFalse(vodState.epgOverlayVisible)

        assertNull(seriesState.liveChannelName)
        assertNull(seriesState.liveNowTitle)
        assertNull(seriesState.liveNextTitle)
        assertFalse(seriesState.epgOverlayVisible)
    }

    // ════════════════════════════════════════════════════════════════════════════
    // Edge Case Tests
    // ════════════════════════════════════════════════════════════════════════════

    @Test
    fun `empty strings are valid for Live-TV title fields`() {
        val state = InternalPlayerUiState(
            liveChannelName = "",
            liveNowTitle = "",
            liveNextTitle = ""
        )

        assertEquals("", state.liveChannelName)
        assertEquals("", state.liveNowTitle)
        assertEquals("", state.liveNextTitle)
    }

    @Test
    fun `long strings are valid for Live-TV title fields`() {
        val longChannelName = "A".repeat(1000)
        val longNowTitle = "B".repeat(1000)
        val longNextTitle = "C".repeat(1000)

        val state = InternalPlayerUiState(
            liveChannelName = longChannelName,
            liveNowTitle = longNowTitle,
            liveNextTitle = longNextTitle
        )

        assertEquals(1000, state.liveChannelName?.length)
        assertEquals(1000, state.liveNowTitle?.length)
        assertEquals(1000, state.liveNextTitle?.length)
    }

    @Test
    fun `special characters are valid in Live-TV title fields`() {
        val state = InternalPlayerUiState(
            liveChannelName = "Test & Co. (HD) #1",
            liveNowTitle = "Show: \"The Best\" - Episode 5",
            liveNextTitle = "News @ 9:00 PM"
        )

        assertEquals("Test & Co. (HD) #1", state.liveChannelName)
        assertEquals("Show: \"The Best\" - Episode 5", state.liveNowTitle)
        assertEquals("News @ 9:00 PM", state.liveNextTitle)
    }
}
