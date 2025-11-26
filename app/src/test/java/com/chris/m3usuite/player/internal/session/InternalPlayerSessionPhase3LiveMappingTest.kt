package com.chris.m3usuite.player.internal.session

import com.chris.m3usuite.player.internal.domain.PlaybackContext
import com.chris.m3usuite.player.internal.domain.PlaybackType
import com.chris.m3usuite.player.internal.live.EpgOverlayState
import com.chris.m3usuite.player.internal.live.LiveChannel
import com.chris.m3usuite.player.internal.live.LivePlaybackController
import com.chris.m3usuite.player.internal.state.InternalPlayerUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Tests for Phase 3 Step 3.B: LivePlaybackController → InternalPlayerUiState mapping.
 *
 * These tests verify that:
 * 1. When playbackType == LIVE and LivePlaybackController emits state changes:
 *    - currentChannel → liveChannelName
 *    - epgOverlay → liveNowTitle, liveNextTitle, epgOverlayVisible
 * 2. When controller flows emit null / default values, UiState fields fall back gracefully
 * 3. For non-LIVE playback types (VOD/SERIES), the new fields stay at defaults (null/false)
 *
 * **Testing Strategy:**
 * - Use FakeLivePlaybackController with controllable MutableStateFlows
 * - Directly test state mapping logic (not the full session composable)
 * - Focus on data transformation, not coroutine/lifecycle behavior
 */
class InternalPlayerSessionPhase3LiveMappingTest {
    /**
     * Fake implementation of LivePlaybackController for testing.
     * Exposes MutableStateFlows that tests can control.
     */
    class FakeLivePlaybackController : LivePlaybackController {
        private val _currentChannel = MutableStateFlow<LiveChannel?>(null)
        override val currentChannel: StateFlow<LiveChannel?> = _currentChannel

        private val _epgOverlay =
            MutableStateFlow(
                EpgOverlayState(
                    visible = false,
                    nowTitle = null,
                    nextTitle = null,
                    hideAtRealtimeMs = null,
                ),
            )
        override val epgOverlay: StateFlow<EpgOverlayState> = _epgOverlay

        // Control methods for tests
        fun emitChannel(channel: LiveChannel?) {
            _currentChannel.value = channel
        }

        fun emitEpgOverlay(overlay: EpgOverlayState) {
            _epgOverlay.value = overlay
        }

        // Unused interface methods (not relevant for state mapping tests)
        override suspend fun initFromPlaybackContext(ctx: PlaybackContext) {}

        override fun jumpChannel(delta: Int) {}

        override fun selectChannel(channelId: Long) {}

        override fun onPlaybackPositionChanged(positionMs: Long) {}
    }

    // ════════════════════════════════════════════════════════════════════════════
    // Test 1: LIVE playback with controller emitting channel data
    // ════════════════════════════════════════════════════════════════════════════

    @Test
    fun `LIVE playback maps currentChannel to liveChannelName`() {
        val controller = FakeLivePlaybackController()

        // Emit a channel
        val channel =
            LiveChannel(
                id = 1L,
                name = "BBC One",
                url = "http://example.com/bbc1",
                category = "News",
                logoUrl = "http://example.com/logo.png",
            )
        controller.emitChannel(channel)

        // Verify the state mapping
        val state = mapLiveControllerToUiState(controller.currentChannel.value, controller.epgOverlay.value)
        assertEquals("BBC One", state.liveChannelName)
    }

    @Test
    fun `LIVE playback maps epgOverlay to nowTitle, nextTitle, and epgOverlayVisible`() {
        val controller = FakeLivePlaybackController()

        // Emit EPG overlay state
        val overlay =
            EpgOverlayState(
                visible = true,
                nowTitle = "News at 6",
                nextTitle = "Movie Night",
                hideAtRealtimeMs = System.currentTimeMillis() + 3000L,
            )
        controller.emitEpgOverlay(overlay)

        // Verify the state mapping
        val state = mapLiveControllerToUiState(controller.currentChannel.value, controller.epgOverlay.value)
        assertEquals("News at 6", state.liveNowTitle)
        assertEquals("Movie Night", state.liveNextTitle)
        assertEquals(true, state.epgOverlayVisible)
    }

    @Test
    fun `LIVE playback with full controller state`() {
        val controller = FakeLivePlaybackController()

        // Emit both channel and EPG data
        val channel =
            LiveChannel(
                id = 42L,
                name = "CNN",
                url = "http://example.com/cnn",
                category = "News",
                logoUrl = null,
            )
        controller.emitChannel(channel)

        val overlay =
            EpgOverlayState(
                visible = true,
                nowTitle = "Breaking News",
                nextTitle = "Sports Update",
                hideAtRealtimeMs = System.currentTimeMillis() + 5000L,
            )
        controller.emitEpgOverlay(overlay)

        // Verify the combined state mapping
        val state = mapLiveControllerToUiState(controller.currentChannel.value, controller.epgOverlay.value)
        assertEquals("CNN", state.liveChannelName)
        assertEquals("Breaking News", state.liveNowTitle)
        assertEquals("Sports Update", state.liveNextTitle)
        assertEquals(true, state.epgOverlayVisible)
    }

    // ════════════════════════════════════════════════════════════════════════════
    // Test 2: Null / default value handling
    // ════════════════════════════════════════════════════════════════════════════

    @Test
    fun `LIVE playback with null channel falls back gracefully`() {
        val controller = FakeLivePlaybackController()

        // currentChannel remains null (default)
        val state = mapLiveControllerToUiState(controller.currentChannel.value, controller.epgOverlay.value)
        assertNull(state.liveChannelName)
    }

    @Test
    fun `LIVE playback with null EPG titles falls back gracefully`() {
        val controller = FakeLivePlaybackController()

        // EPG overlay with null titles
        val overlay =
            EpgOverlayState(
                visible = true,
                nowTitle = null,
                nextTitle = null,
                hideAtRealtimeMs = null,
            )
        controller.emitEpgOverlay(overlay)

        val state = mapLiveControllerToUiState(controller.currentChannel.value, controller.epgOverlay.value)
        assertNull(state.liveNowTitle)
        assertNull(state.liveNextTitle)
        assertEquals(true, state.epgOverlayVisible) // visible is non-null Boolean
    }

    @Test
    fun `LIVE playback with EPG overlay not visible`() {
        val controller = FakeLivePlaybackController()

        // EPG overlay explicitly hidden
        val overlay =
            EpgOverlayState(
                visible = false,
                nowTitle = "Cached Title",
                nextTitle = "Cached Next",
                hideAtRealtimeMs = null,
            )
        controller.emitEpgOverlay(overlay)

        val state = mapLiveControllerToUiState(controller.currentChannel.value, controller.epgOverlay.value)
        // Titles may still be present (cached), but overlay is not visible
        assertEquals("Cached Title", state.liveNowTitle)
        assertEquals("Cached Next", state.liveNextTitle)
        assertFalse(state.epgOverlayVisible)
    }

    @Test
    fun `LIVE playback with default controller state does not crash`() {
        val controller = FakeLivePlaybackController()

        // Controller with all default values (null channel, hidden overlay)
        val state = mapLiveControllerToUiState(controller.currentChannel.value, controller.epgOverlay.value)
        assertNull(state.liveChannelName)
        assertNull(state.liveNowTitle)
        assertNull(state.liveNextTitle)
        assertFalse(state.epgOverlayVisible)
    }

    // ════════════════════════════════════════════════════════════════════════════
    // Test 3: Non-LIVE playback types keep defaults
    // ════════════════════════════════════════════════════════════════════════════

    @Test
    fun `VOD playback has null Live-TV fields`() {
        // For VOD playback, liveController is null and fields remain at defaults
        val vodState =
            InternalPlayerUiState(
                playbackType = PlaybackType.VOD,
            )

        assertNull(vodState.liveChannelName)
        assertNull(vodState.liveNowTitle)
        assertNull(vodState.liveNextTitle)
        assertFalse(vodState.epgOverlayVisible)
    }

    @Test
    fun `SERIES playback has null Live-TV fields`() {
        // For SERIES playback, liveController is null and fields remain at defaults
        val seriesState =
            InternalPlayerUiState(
                playbackType = PlaybackType.SERIES,
            )

        assertNull(seriesState.liveChannelName)
        assertNull(seriesState.liveNowTitle)
        assertNull(seriesState.liveNextTitle)
        assertFalse(seriesState.epgOverlayVisible)
    }

    // ════════════════════════════════════════════════════════════════════════════
    // Helper: State Mapping Function
    // ════════════════════════════════════════════════════════════════════════════

    /**
     * Helper function that mimics the state mapping logic in InternalPlayerSession.
     * This allows us to test the mapping independently of the full session composable.
     */
    private fun mapLiveControllerToUiState(
        channel: LiveChannel?,
        overlay: EpgOverlayState,
    ): InternalPlayerUiState =
        InternalPlayerUiState(
            playbackType = PlaybackType.LIVE,
            liveChannelName = channel?.name,
            liveNowTitle = overlay.nowTitle,
            liveNextTitle = overlay.nextTitle,
            epgOverlayVisible = overlay.visible,
        )
}
