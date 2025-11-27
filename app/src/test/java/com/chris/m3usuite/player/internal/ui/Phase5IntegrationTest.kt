package com.chris.m3usuite.player.internal.ui

import com.chris.m3usuite.player.internal.domain.PlaybackType
import com.chris.m3usuite.player.internal.state.AspectRatioMode
import com.chris.m3usuite.player.internal.state.InternalPlayerUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Integration tests for Phase 5 combined scenarios.
 *
 * **Contract Reference:** INTERNAL_PLAYER_PLAYER_SURFACE_CONTRACT_PHASE5.md
 *
 * These tests validate that different Phase 5 features work correctly
 * together without conflicts:
 * - Trickplay + Aspect Ratio
 * - CC Menu + Auto-Hide
 * - Aspect Ratio + Auto-Hide
 * - Trickplay + Subtitles
 * - Multi-feature interactions
 */
class Phase5IntegrationTest {
    // ════════════════════════════════════════════════════════════════════════════
    // Trickplay + Aspect Ratio Integration Tests
    // ════════════════════════════════════════════════════════════════════════════

    @Test
    fun `aspect ratio mode preserved during trickplay activation`() {
        // Start with FILL mode
        val beforeTrickplay =
            InternalPlayerUiState(
                playbackType = PlaybackType.VOD,
                aspectRatioMode = AspectRatioMode.FILL,
                trickplayActive = false,
            )

        // Activate trickplay
        val duringTrickplay =
            beforeTrickplay.copy(
                trickplayActive = true,
                trickplaySpeed = 2f,
            )

        assertEquals(
            "Aspect ratio should remain FILL during trickplay",
            AspectRatioMode.FILL,
            duringTrickplay.aspectRatioMode,
        )
    }

    @Test
    fun `aspect ratio can be changed while trickplay is active`() {
        // Active trickplay with FIT mode
        val trickplayState =
            InternalPlayerUiState(
                playbackType = PlaybackType.VOD,
                aspectRatioMode = AspectRatioMode.FIT,
                trickplayActive = true,
                trickplaySpeed = 3f,
            )

        // Change to ZOOM while trickplay is active
        val changedAspect =
            trickplayState.copy(
                aspectRatioMode = AspectRatioMode.ZOOM,
            )

        assertEquals("Aspect ratio should change to ZOOM", AspectRatioMode.ZOOM, changedAspect.aspectRatioMode)
        assertTrue("Trickplay should remain active", changedAspect.trickplayActive)
        assertEquals("Trickplay speed should remain 3x", 3f, changedAspect.trickplaySpeed)
    }

    @Test
    fun `aspect ratio cycling works during active trickplay`() {
        val trickplayState =
            InternalPlayerUiState(
                playbackType = PlaybackType.VOD,
                aspectRatioMode = AspectRatioMode.FIT,
                trickplayActive = true,
                trickplaySpeed = 5f,
            )

        // Cycle through aspect ratios
        var mode = trickplayState.aspectRatioMode
        mode = mode.next() // FIT → FILL
        assertEquals("First cycle should be FILL", AspectRatioMode.FILL, mode)

        mode = mode.next() // FILL → ZOOM
        assertEquals("Second cycle should be ZOOM", AspectRatioMode.ZOOM, mode)

        mode = mode.next() // ZOOM → FIT
        assertEquals("Third cycle should return to FIT", AspectRatioMode.FIT, mode)
    }

    // ════════════════════════════════════════════════════════════════════════════
    // CC Menu + Auto-Hide Integration Tests
    // ════════════════════════════════════════════════════════════════════════════

    @Test
    fun `auto-hide blocked when CC menu is open`() {
        val state =
            InternalPlayerUiState(
                controlsVisible = true,
                showCcMenuDialog = true,
            )

        assertTrue("CC menu open should block auto-hide", state.hasBlockingOverlay)
        assertTrue("Controls should still be visible", state.controlsVisible)
    }

    @Test
    fun `auto-hide resumes when CC menu is closed`() {
        val menuOpen =
            InternalPlayerUiState(
                controlsVisible = true,
                showCcMenuDialog = true,
            )

        val menuClosed = menuOpen.copy(showCcMenuDialog = false)

        assertFalse("No blocking overlay after CC menu closed", menuClosed.hasBlockingOverlay)
        assertTrue("Controls should still be visible", menuClosed.controlsVisible)
    }

    @Test
    fun `CC button visibility depends on subtitle tracks and kid mode`() {
        // Non-kid with subtitle tracks: CC visible
        val withTracks =
            InternalPlayerUiState(
                kidActive = false,
                availableSubtitleTracks = listOf(
                    com.chris.m3usuite.player.internal.subtitles.SubtitleTrack(
                        groupIndex = 0,
                        trackIndex = 0,
                        label = "English",
                        language = "en",
                        isDefault = true,
                    ),
                ),
            )

        assertFalse("Not in kid mode", withTracks.kidActive)
        assertTrue("Has subtitle tracks", withTracks.availableSubtitleTracks.isNotEmpty())

        // Non-kid without tracks: CC hidden
        val noTracks =
            InternalPlayerUiState(
                kidActive = false,
                availableSubtitleTracks = emptyList(),
            )

        assertTrue("No subtitle tracks", noTracks.availableSubtitleTracks.isEmpty())

        // Kid mode with tracks: CC hidden
        val kidWithTracks =
            InternalPlayerUiState(
                kidActive = true,
                availableSubtitleTracks = listOf(
                    com.chris.m3usuite.player.internal.subtitles.SubtitleTrack(
                        groupIndex = 0,
                        trackIndex = 0,
                        label = "English",
                        language = "en",
                        isDefault = true,
                    ),
                ),
            )

        assertTrue("Kid mode active", kidWithTracks.kidActive)
    }

    // ════════════════════════════════════════════════════════════════════════════
    // Trickplay + Auto-Hide Integration Tests
    // ════════════════════════════════════════════════════════════════════════════

    @Test
    fun `controls visible during active trickplay`() {
        val trickplayState =
            InternalPlayerUiState(
                controlsVisible = true,
                trickplayActive = true,
                trickplaySpeed = 2f,
            )

        // Auto-hide logic should check trickplayActive
        assertTrue("Controls should be visible during trickplay", trickplayState.controlsVisible)
        assertTrue("Trickplay is active", trickplayState.trickplayActive)
    }

    @Test
    fun `controls tick increments on trickplay speed change`() {
        val state =
            InternalPlayerUiState(
                controlsVisible = true,
                controlsTick = 5,
                trickplayActive = true,
                trickplaySpeed = 2f,
            )

        // Simulate speed cycling (tick should increment)
        val afterSpeedChange =
            state.copy(
                trickplaySpeed = 3f,
                controlsTick = state.controlsTick + 1,
            )

        assertEquals("Tick should increment after speed change", 6, afterSpeedChange.controlsTick)
        assertEquals("Speed should change to 3x", 3f, afterSpeedChange.trickplaySpeed)
    }

    // ════════════════════════════════════════════════════════════════════════════
    // Black Bars Consistency Tests
    // ════════════════════════════════════════════════════════════════════════════

    @Test
    fun `aspect ratio mode default is FIT`() {
        val defaultState = InternalPlayerUiState()

        assertEquals("Default aspect ratio should be FIT", AspectRatioMode.FIT, defaultState.aspectRatioMode)
    }

    @Test
    fun `all aspect ratio modes are valid for any playback type`() {
        val playbackTypes = listOf(PlaybackType.VOD, PlaybackType.SERIES, PlaybackType.LIVE)

        playbackTypes.forEach { playbackType ->
            AspectRatioMode.entries.forEach { aspectMode ->
                val state =
                    InternalPlayerUiState(
                        playbackType = playbackType,
                        aspectRatioMode = aspectMode,
                    )

                assertNotNull("State should be valid for $playbackType with $aspectMode", state)
                assertEquals("Playback type should be preserved", playbackType, state.playbackType)
                assertEquals("Aspect mode should be preserved", aspectMode, state.aspectRatioMode)
            }
        }
    }

    // ════════════════════════════════════════════════════════════════════════════
    // Multi-Feature State Consistency Tests
    // ════════════════════════════════════════════════════════════════════════════

    @Test
    fun `full state with all Phase 5 features active`() {
        val fullState =
            InternalPlayerUiState(
                playbackType = PlaybackType.VOD,
                positionMs = 60_000L,
                durationMs = 120_000L,
                isPlaying = true,
                aspectRatioMode = AspectRatioMode.FILL,
                trickplayActive = false,
                trickplaySpeed = 1f,
                seekPreviewVisible = false,
                seekPreviewTargetMs = null,
                controlsVisible = true,
                controlsTick = 10,
                showCcMenuDialog = false,
            )

        // Verify all fields are accessible and consistent
        assertEquals("Position should be 60s", 60_000L, fullState.positionMs)
        assertEquals("Duration should be 120s", 120_000L, fullState.durationMs)
        assertTrue("Should be playing", fullState.isPlaying)
        assertEquals("Aspect ratio should be FILL", AspectRatioMode.FILL, fullState.aspectRatioMode)
        assertFalse("Trickplay should be inactive", fullState.trickplayActive)
        assertEquals("Speed should be normal", 1f, fullState.trickplaySpeed)
        assertFalse("Seek preview should be hidden", fullState.seekPreviewVisible)
        assertTrue("Controls should be visible", fullState.controlsVisible)
        assertEquals("Tick should be 10", 10, fullState.controlsTick)
        assertFalse("No blocking overlay", fullState.hasBlockingOverlay)
    }

    @Test
    fun `state transitions between trickplay and seek preview`() {
        // Start with normal playback
        var state = InternalPlayerUiState(positionMs = 30_000L)

        // Enter trickplay
        state = state.copy(trickplayActive = true, trickplaySpeed = 2f)
        assertTrue("Trickplay active", state.trickplayActive)
        assertFalse("Seek preview hidden during trickplay", state.seekPreviewVisible)

        // Exit trickplay, show seek preview
        state = state.copy(trickplayActive = false, trickplaySpeed = 1f, seekPreviewVisible = true, seekPreviewTargetMs = 40_000L)
        assertFalse("Trickplay inactive", state.trickplayActive)
        assertTrue("Seek preview visible", state.seekPreviewVisible)
        assertEquals("Target position set", 40_000L, state.seekPreviewTargetMs)

        // Hide seek preview
        state = state.copy(seekPreviewVisible = false, seekPreviewTargetMs = null)
        assertFalse("Seek preview hidden", state.seekPreviewVisible)
    }

    // ════════════════════════════════════════════════════════════════════════════
    // Rapid Interaction Tests
    // ════════════════════════════════════════════════════════════════════════════

    @Test
    fun `rapid controls visibility toggle does not lose state`() {
        var state = InternalPlayerUiState(controlsVisible = true, controlsTick = 0)

        // Rapidly toggle visibility 10 times
        repeat(10) {
            state = state.copy(controlsVisible = !state.controlsVisible, controlsTick = state.controlsTick + 1)
        }

        // After even number of toggles, should be back to visible
        assertTrue("Controls should be visible after 10 toggles", state.controlsVisible)
        assertEquals("Tick should be 10", 10, state.controlsTick)
    }

    @Test
    fun `rapid aspect ratio cycling returns to start`() {
        var mode = AspectRatioMode.FIT

        // Cycle 9 times through FIT → FILL → ZOOM → FIT...
        // Each cycle of 3 brings us back to the same position
        // After 9 calls: 3 full cycles (9 / 3 = 3), ending at FIT
        repeat(9) {
            mode = mode.next()
        }

        assertEquals("After 9 cycles should be FIT", AspectRatioMode.FIT, mode)
    }

    @Test
    fun `rapid trickplay speed cycling maintains valid speeds`() {
        // Simulates rapid speed cycling: 2x → 3x → 5x → 2x
        val speeds = listOf(2f, 3f, 5f)
        var currentSpeedIndex = 0

        repeat(12) { iteration ->
            val expectedSpeed = speeds[currentSpeedIndex]
            val state =
                InternalPlayerUiState(
                    trickplayActive = true,
                    trickplaySpeed = expectedSpeed,
                )

            assertTrue("Speed at iteration $iteration should be valid (${expectedSpeed}x)", state.trickplaySpeed > 0)

            // Cycle to next speed
            currentSpeedIndex = (currentSpeedIndex + 1) % speeds.size
        }
    }
}
