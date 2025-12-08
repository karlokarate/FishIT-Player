package com.chris.m3usuite.player.internal.state

import com.chris.m3usuite.player.internal.domain.PlaybackType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for PlayerHotState and PlayerColdState.
 *
 * ════════════════════════════════════════════════════════════════════════════════════════════════════
 * PHASE 8 – Task 5: Compose & FocusKit Performance Hardening
 * Contract: INTERNAL_PLAYER_PHASE8_PERFORMANCE_LIFECYCLE_CONTRACT.md Section 9.1
 * ════════════════════════════════════════════════════════════════════════════════════════════════════
 *
 * These tests verify:
 * - Hot state contains only frequently updating fields
 * - Cold state contains only rarely updating fields
 * - Extension functions correctly extract state from InternalPlayerUiState
 * - State classes are correctly structured as @Immutable data classes
 */
class HotColdStateSplitTest {
    // ══════════════════════════════════════════════════════════════════
    // PlayerHotState Tests
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `PlayerHotState default values are correct`() {
        val hotState = PlayerHotState()

        assertEquals(0L, hotState.positionMs)
        assertEquals(0L, hotState.durationMs)
        assertFalse(hotState.isPlaying)
        assertFalse(hotState.isBuffering)
        assertFalse(hotState.trickplayActive)
        assertEquals(1f, hotState.trickplaySpeed, 0.001f)
        assertFalse(hotState.seekPreviewVisible)
        assertNull(hotState.seekPreviewTargetMs)
        assertTrue(hotState.controlsVisible)
        assertEquals(0, hotState.controlsTick)
    }

    @Test
    fun `PlayerHotState progressFraction calculates correctly`() {
        val hotState = PlayerHotState(positionMs = 5000L, durationMs = 10000L)
        assertEquals(0.5f, hotState.progressFraction, 0.001f)
    }

    @Test
    fun `PlayerHotState progressFraction handles zero duration`() {
        val hotState = PlayerHotState(positionMs = 5000L, durationMs = 0L)
        assertEquals(0f, hotState.progressFraction, 0.001f)
    }

    @Test
    fun `PlayerHotState progressFraction clamps to 0-1 range`() {
        val hotState = PlayerHotState(positionMs = 15000L, durationMs = 10000L)
        assertEquals(1f, hotState.progressFraction, 0.001f)
    }

    @Test
    fun `PlayerHotState copy preserves unmodified fields`() {
        val original =
            PlayerHotState(
                positionMs = 1000L,
                durationMs = 60000L,
                isPlaying = true,
                isBuffering = false,
            )

        val modified = original.copy(positionMs = 2000L)

        assertEquals(2000L, modified.positionMs)
        assertEquals(original.durationMs, modified.durationMs)
        assertEquals(original.isPlaying, modified.isPlaying)
        assertEquals(original.isBuffering, modified.isBuffering)
    }

    // ══════════════════════════════════════════════════════════════════
    // PlayerColdState Tests
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `PlayerColdState default values are correct`() {
        val coldState = PlayerColdState()

        assertEquals(PlaybackType.VOD, coldState.playbackType)
        assertEquals(1f, coldState.playbackSpeed, 0.001f)
        assertFalse(coldState.isLooping)
        assertNull(coldState.playbackError)
        assertNull(coldState.sleepTimerRemainingMs)
        assertFalse(coldState.kidActive)
        assertFalse(coldState.kidBlocked)
        assertNull(coldState.kidProfileId)
        assertNull(coldState.remainingKidsMinutes)
        assertFalse(coldState.showSettingsDialog)
        assertFalse(coldState.showTracksDialog)
        assertFalse(coldState.showSpeedDialog)
        assertFalse(coldState.showSleepTimerDialog)
        assertFalse(coldState.showCcMenuDialog)
        assertFalse(coldState.showDebugInfo)
        assertEquals(AspectRatioMode.FIT, coldState.aspectRatioMode)
        assertNull(coldState.liveChannelName)
        assertNull(coldState.liveNowTitle)
        assertNull(coldState.liveNextTitle)
        assertFalse(coldState.epgOverlayVisible)
    }

    @Test
    fun `PlayerColdState isLive returns true for LIVE playbackType`() {
        val coldState =
            PlayerColdState(
                playbackType = PlaybackType.LIVE,
            )
        assertTrue(coldState.isLive)
        assertFalse(coldState.isSeries)
    }

    @Test
    fun `PlayerColdState isSeries returns true for SERIES playbackType`() {
        val coldState =
            PlayerColdState(
                playbackType = PlaybackType.SERIES,
            )
        assertTrue(coldState.isSeries)
        assertFalse(coldState.isLive)
    }

    @Test
    fun `PlayerColdState hasBlockingOverlay detects settings dialog`() {
        val coldState = PlayerColdState(showSettingsDialog = true)
        assertTrue(coldState.hasBlockingOverlay)
    }

    @Test
    fun `PlayerColdState hasBlockingOverlay detects CC menu`() {
        val coldState = PlayerColdState(showCcMenuDialog = true)
        assertTrue(coldState.hasBlockingOverlay)
    }

    @Test
    fun `PlayerColdState hasBlockingOverlay detects kid blocked`() {
        val coldState = PlayerColdState(kidBlocked = true)
        assertTrue(coldState.hasBlockingOverlay)
    }

    @Test
    fun `PlayerColdState hasBlockingOverlay returns false when nothing open`() {
        val coldState = PlayerColdState()
        assertFalse(coldState.hasBlockingOverlay)
    }

    // ══════════════════════════════════════════════════════════════════
    // Extension Function Tests
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `toHotState extracts hot fields correctly`() {
        val fullState =
            InternalPlayerUiState(
                positionMs = 5000L,
                durationMs = 60000L,
                isPlaying = true,
                isBuffering = false,
                trickplayActive = true,
                trickplaySpeed = 2f,
                seekPreviewVisible = true,
                seekPreviewTargetMs = 10000L,
                controlsVisible = false,
                controlsTick = 5,
                // Cold fields (should not affect hot state)
                kidActive = true,
                showSettingsDialog = true,
            )

        val hotState = fullState.toHotState()

        assertEquals(fullState.positionMs, hotState.positionMs)
        assertEquals(fullState.durationMs, hotState.durationMs)
        assertEquals(fullState.isPlaying, hotState.isPlaying)
        assertEquals(fullState.isBuffering, hotState.isBuffering)
        assertEquals(fullState.trickplayActive, hotState.trickplayActive)
        assertEquals(fullState.trickplaySpeed, hotState.trickplaySpeed, 0.001f)
        assertEquals(fullState.seekPreviewVisible, hotState.seekPreviewVisible)
        assertEquals(fullState.seekPreviewTargetMs, hotState.seekPreviewTargetMs)
        assertEquals(fullState.controlsVisible, hotState.controlsVisible)
        assertEquals(fullState.controlsTick, hotState.controlsTick)
    }

    @Test
    fun `toColdState extracts cold fields correctly`() {
        val fullState =
            InternalPlayerUiState(
                playbackType = PlaybackType.LIVE,
                playbackSpeed = 1.5f,
                isLooping = true,
                kidActive = true,
                kidBlocked = true,
                kidProfileId = 123L,
                remainingKidsMinutes = 15,
                showSettingsDialog = true,
                showCcMenuDialog = true,
                aspectRatioMode = AspectRatioMode.FILL,
                liveChannelName = "Test Channel",
                liveNowTitle = "Now Playing",
                liveNextTitle = "Up Next",
                epgOverlayVisible = true,
                // Hot fields (should not affect cold state)
                positionMs = 5000L,
                isPlaying = true,
            )

        val coldState = fullState.toColdState()

        assertEquals(fullState.playbackType, coldState.playbackType)
        assertEquals(fullState.playbackSpeed, coldState.playbackSpeed, 0.001f)
        assertEquals(fullState.isLooping, coldState.isLooping)
        assertEquals(fullState.kidActive, coldState.kidActive)
        assertEquals(fullState.kidBlocked, coldState.kidBlocked)
        assertEquals(fullState.kidProfileId, coldState.kidProfileId)
        assertEquals(fullState.remainingKidsMinutes, coldState.remainingKidsMinutes)
        assertEquals(fullState.showSettingsDialog, coldState.showSettingsDialog)
        assertEquals(fullState.showCcMenuDialog, coldState.showCcMenuDialog)
        assertEquals(fullState.aspectRatioMode, coldState.aspectRatioMode)
        assertEquals(fullState.liveChannelName, coldState.liveChannelName)
        assertEquals(fullState.liveNowTitle, coldState.liveNowTitle)
        assertEquals(fullState.liveNextTitle, coldState.liveNextTitle)
        assertEquals(fullState.epgOverlayVisible, coldState.epgOverlayVisible)
    }

    // ══════════════════════════════════════════════════════════════════
    // State Separation Correctness Tests
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `hot state changes do not affect cold state extraction`() {
        val state1 =
            InternalPlayerUiState(
                positionMs = 1000L,
                kidActive = true,
            )
        val state2 = state1.copy(positionMs = 2000L)

        val cold1 = state1.toColdState()
        val cold2 = state2.toColdState()

        // Cold state should be equal since only hot field changed
        assertEquals(cold1, cold2)
    }

    @Test
    fun `cold state changes do not affect hot state extraction`() {
        val state1 =
            InternalPlayerUiState(
                positionMs = 1000L,
                kidActive = false,
            )
        val state2 = state1.copy(kidActive = true)

        val hot1 = state1.toHotState()
        val hot2 = state2.toHotState()

        // Hot state should be equal since only cold field changed
        assertEquals(hot1, hot2)
    }

    // ══════════════════════════════════════════════════════════════════
    // Immutability Tests
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `PlayerHotState is a data class`() {
        val state1 = PlayerHotState(positionMs = 1000L)
        val state2 = PlayerHotState(positionMs = 1000L)

        assertEquals(state1, state2)
        assertEquals(state1.hashCode(), state2.hashCode())
    }

    @Test
    fun `PlayerColdState is a data class`() {
        val state1 = PlayerColdState(kidActive = true)
        val state2 = PlayerColdState(kidActive = true)

        assertEquals(state1, state2)
        assertEquals(state1.hashCode(), state2.hashCode())
    }
}
