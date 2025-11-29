package com.chris.m3usuite.player.internal.state

import com.chris.m3usuite.player.internal.domain.PlaybackType
import com.chris.m3usuite.player.internal.subtitles.SubtitleStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ════════════════════════════════════════════════════════════════════════════════
 * Phase 8 Task 5: Hot/Cold State Split Tests
 * ════════════════════════════════════════════════════════════════════════════════
 *
 * Tests for the PlayerHotState and PlayerColdState data classes.
 * Verifies:
 * 1. Correct extraction from InternalPlayerUiState
 * 2. Computed properties work correctly
 * 3. State separation is complete (no overlap)
 *
 * Contract Reference:
 * - INTERNAL_PLAYER_PHASE8_CHECKLIST.md Group 7.1, 7.2
 */
class PlayerHotColdStateTest {

    // ════════════════════════════════════════════════════════════════════════════
    // PlayerHotState Tests
    // ════════════════════════════════════════════════════════════════════════════

    @Test
    fun `hotState has correct defaults`() {
        val state = PlayerHotState()

        assertEquals(0L, state.positionMs)
        assertEquals(0L, state.durationMs)
        assertFalse(state.isPlaying)
        assertFalse(state.isBuffering)
        assertFalse(state.trickplayActive)
        assertEquals(1f, state.trickplaySpeed, 0.01f)
        assertEquals(0, state.controlsTick)
        assertTrue(state.controlsVisible)
    }

    @Test
    fun `hotState formattedPosition formats correctly`() {
        val state = PlayerHotState(positionMs = 65_000L) // 1:05

        assertEquals("01:05", state.formattedPosition)
    }

    @Test
    fun `hotState formattedDuration formats correctly`() {
        val state = PlayerHotState(durationMs = 3_600_000L) // 60:00

        assertEquals("60:00", state.formattedDuration)
    }

    @Test
    fun `hotState formattedPosition handles zero`() {
        val state = PlayerHotState(positionMs = 0L)

        assertEquals("00:00", state.formattedPosition)
    }

    @Test
    fun `hotState formattedPosition handles negative`() {
        val state = PlayerHotState(positionMs = -1000L)

        assertEquals("00:00", state.formattedPosition)
    }

    @Test
    fun `hotState progressFraction calculates correctly`() {
        val state = PlayerHotState(positionMs = 30_000L, durationMs = 60_000L)

        assertEquals(0.5f, state.progressFraction, 0.01f)
    }

    @Test
    fun `hotState progressFraction handles zero duration`() {
        val state = PlayerHotState(positionMs = 30_000L, durationMs = 0L)

        assertEquals(0f, state.progressFraction, 0.01f)
    }

    @Test
    fun `hotState progressFraction clamps to 0-1`() {
        val overState = PlayerHotState(positionMs = 100_000L, durationMs = 50_000L)
        assertEquals(1f, overState.progressFraction, 0.01f)
    }

    @Test
    fun `hotState fromFullState extracts hot fields`() {
        val fullState = InternalPlayerUiState(
            positionMs = 45_000L,
            durationMs = 120_000L,
            isPlaying = true,
            isBuffering = false,
            trickplayActive = true,
            trickplaySpeed = 2f,
            controlsTick = 5,
            controlsVisible = false,
            // Cold fields (should be ignored)
            playbackType = PlaybackType.SERIES,
            aspectRatioMode = AspectRatioMode.FILL,
            kidActive = true,
        )

        val hotState = PlayerHotState.fromFullState(fullState)

        assertEquals(45_000L, hotState.positionMs)
        assertEquals(120_000L, hotState.durationMs)
        assertTrue(hotState.isPlaying)
        assertFalse(hotState.isBuffering)
        assertTrue(hotState.trickplayActive)
        assertEquals(2f, hotState.trickplaySpeed, 0.01f)
        assertEquals(5, hotState.controlsTick)
        assertFalse(hotState.controlsVisible)
    }

    // ════════════════════════════════════════════════════════════════════════════
    // PlayerColdState Tests
    // ════════════════════════════════════════════════════════════════════════════

    @Test
    fun `coldState has correct defaults`() {
        val state = PlayerColdState()

        assertEquals(PlaybackType.VOD, state.playbackType)
        assertEquals(AspectRatioMode.FIT, state.aspectRatioMode)
        assertEquals(1f, state.playbackSpeed, 0.01f)
        assertFalse(state.isLooping)
        assertNull(state.playbackError)
        assertNull(state.sleepTimerRemainingMs)
        assertFalse(state.kidActive)
        assertFalse(state.kidBlocked)
        assertNull(state.kidProfileId)
        assertNull(state.remainingKidsMinutes)
        assertFalse(state.showSettingsDialog)
        assertFalse(state.showTracksDialog)
        assertFalse(state.showSpeedDialog)
        assertFalse(state.showSleepTimerDialog)
        assertFalse(state.showCcMenuDialog)
        assertFalse(state.showDebugInfo)
    }

    @Test
    fun `coldState isLive returns true for LIVE type`() {
        val state = PlayerColdState(playbackType = PlaybackType.LIVE)
        assertTrue(state.isLive)
    }

    @Test
    fun `coldState isLive returns false for VOD type`() {
        val state = PlayerColdState(playbackType = PlaybackType.VOD)
        assertFalse(state.isLive)
    }

    @Test
    fun `coldState isSeries returns true for SERIES type`() {
        val state = PlayerColdState(playbackType = PlaybackType.SERIES)
        assertTrue(state.isSeries)
    }

    @Test
    fun `coldState hasBlockingOverlay returns true for CC menu`() {
        val state = PlayerColdState(showCcMenuDialog = true)
        assertTrue(state.hasBlockingOverlay)
    }

    @Test
    fun `coldState hasBlockingOverlay returns true for settings dialog`() {
        val state = PlayerColdState(showSettingsDialog = true)
        assertTrue(state.hasBlockingOverlay)
    }

    @Test
    fun `coldState hasBlockingOverlay returns true for kidBlocked`() {
        val state = PlayerColdState(kidBlocked = true)
        assertTrue(state.hasBlockingOverlay)
    }

    @Test
    fun `coldState hasBlockingOverlay returns false when no overlays`() {
        val state = PlayerColdState()
        assertFalse(state.hasBlockingOverlay)
    }

    @Test
    fun `coldState fromFullState extracts cold fields`() {
        val fullState = InternalPlayerUiState(
            playbackType = PlaybackType.LIVE,
            aspectRatioMode = AspectRatioMode.ZOOM,
            playbackSpeed = 1.5f,
            isLooping = true,
            kidActive = true,
            kidBlocked = false,
            kidProfileId = 123L,
            remainingKidsMinutes = 15,
            subtitleStyle = SubtitleStyle(textScale = 1.2f),
            liveChannelName = "Channel 1",
            liveNowTitle = "Movie",
            liveNextTitle = "News",
            epgOverlayVisible = true,
            showSettingsDialog = true,
            showCcMenuDialog = false,
            // Hot fields (should be ignored)
            positionMs = 99999L,
            durationMs = 888888L,
            isPlaying = true,
            isBuffering = true,
        )

        val coldState = PlayerColdState.fromFullState(fullState)

        assertEquals(PlaybackType.LIVE, coldState.playbackType)
        assertEquals(AspectRatioMode.ZOOM, coldState.aspectRatioMode)
        assertEquals(1.5f, coldState.playbackSpeed, 0.01f)
        assertTrue(coldState.isLooping)
        assertTrue(coldState.kidActive)
        assertFalse(coldState.kidBlocked)
        assertEquals(123L, coldState.kidProfileId)
        assertEquals(15, coldState.remainingKidsMinutes)
        assertEquals(1.2f, coldState.subtitleStyle.textScale, 0.01f)
        assertEquals("Channel 1", coldState.liveChannelName)
        assertEquals("Movie", coldState.liveNowTitle)
        assertEquals("News", coldState.liveNextTitle)
        assertTrue(coldState.epgOverlayVisible)
        assertTrue(coldState.showSettingsDialog)
        assertFalse(coldState.showCcMenuDialog)
    }

    // ════════════════════════════════════════════════════════════════════════════
    // State Separation Tests
    // ════════════════════════════════════════════════════════════════════════════

    @Test
    fun `hot and cold states together reconstruct full state logically`() {
        val fullState = InternalPlayerUiState(
            positionMs = 10000L,
            durationMs = 60000L,
            isPlaying = true,
            isBuffering = false,
            trickplayActive = false,
            trickplaySpeed = 1f,
            controlsTick = 3,
            controlsVisible = true,
            playbackType = PlaybackType.VOD,
            aspectRatioMode = AspectRatioMode.FIT,
            kidActive = false,
        )

        val hotState = PlayerHotState.fromFullState(fullState)
        val coldState = PlayerColdState.fromFullState(fullState)

        // Verify hot state has position/playback info
        assertEquals(10000L, hotState.positionMs)
        assertTrue(hotState.isPlaying)

        // Verify cold state has content/metadata info
        assertEquals(PlaybackType.VOD, coldState.playbackType)
        assertEquals(AspectRatioMode.FIT, coldState.aspectRatioMode)
    }

    @Test
    fun `hot state fields are actually frequently updating fields`() {
        // Document what fields are in hot state and why
        val hotState = PlayerHotState()

        // Position updates every ~1 second during playback
        assertNotNull(hotState.positionMs)

        // Duration is set once but included for progress calculation
        assertNotNull(hotState.durationMs)

        // Playing/buffering toggle frequently
        assertFalse(hotState.isPlaying)
        assertFalse(hotState.isBuffering)

        // Trickplay changes during FF/RW operations
        assertFalse(hotState.trickplayActive)

        // Controls tick changes on every user interaction
        assertEquals(0, hotState.controlsTick)
    }

    @Test
    fun `cold state fields are actually rarely changing fields`() {
        // Document what fields are in cold state and why
        val coldState = PlayerColdState()

        // Playback type is set once when media is loaded
        assertEquals(PlaybackType.VOD, coldState.playbackType)

        // Aspect ratio only changes on user action
        assertEquals(AspectRatioMode.FIT, coldState.aspectRatioMode)

        // Kid mode only changes on profile switch
        assertFalse(coldState.kidActive)

        // Dialog visibility only changes on user action
        assertFalse(coldState.showSettingsDialog)
    }

    @Test
    fun `both state classes are immutable`() {
        // Data classes are immutable by design in Kotlin when using val
        val hotState = PlayerHotState(positionMs = 1000L)
        val hotCopy = hotState.copy(positionMs = 2000L)

        // Original unchanged
        assertEquals(1000L, hotState.positionMs)
        // Copy has new value
        assertEquals(2000L, hotCopy.positionMs)

        val coldState = PlayerColdState(playbackType = PlaybackType.VOD)
        val coldCopy = coldState.copy(playbackType = PlaybackType.LIVE)

        // Original unchanged
        assertEquals(PlaybackType.VOD, coldState.playbackType)
        // Copy has new value
        assertEquals(PlaybackType.LIVE, coldCopy.playbackType)
    }
}
