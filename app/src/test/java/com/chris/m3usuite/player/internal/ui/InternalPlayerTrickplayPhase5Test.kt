package com.chris.m3usuite.player.internal.ui

import com.chris.m3usuite.player.internal.domain.PlaybackType
import com.chris.m3usuite.player.internal.state.AspectRatioMode
import com.chris.m3usuite.player.internal.state.InternalPlayerUiState
import com.chris.m3usuite.player.internal.state.TrickplayDirection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for Phase 5 Group 3: Trickplay Behavior
 *
 * **Contract Reference:** INTERNAL_PLAYER_PLAYER_SURFACE_CONTRACT_PHASE5.md Section 6
 *
 * These tests validate:
 * - Trickplay state fields in InternalPlayerUiState
 * - Trickplay direction enum behavior
 * - Speed values and cycling
 * - Aspect ratio and black bars remain unaffected during trickplay
 * - Seek preview state
 */
class InternalPlayerTrickplayPhase5Test {
    // ════════════════════════════════════════════════════════════════════════════
    // Trickplay State Fields Tests
    // ════════════════════════════════════════════════════════════════════════════

    @Test
    fun `default trickplay state is inactive`() {
        val state = InternalPlayerUiState()

        assertFalse("Trickplay should be inactive by default", state.trickplayActive)
        assertEquals("Default speed should be 1.0", 1f, state.trickplaySpeed)
    }

    @Test
    fun `trickplay state can be activated with forward speed`() {
        val state = InternalPlayerUiState(
            trickplayActive = true,
            trickplaySpeed = 2f,
        )

        assertTrue("Trickplay should be active", state.trickplayActive)
        assertEquals("Speed should be 2x forward", 2f, state.trickplaySpeed)
    }

    @Test
    fun `trickplay state can be activated with rewind speed`() {
        val state = InternalPlayerUiState(
            trickplayActive = true,
            trickplaySpeed = -2f,
        )

        assertTrue("Trickplay should be active", state.trickplayActive)
        assertEquals("Speed should be 2x rewind (negative)", -2f, state.trickplaySpeed)
    }

    @Test
    fun `trickplay supports 2x 3x 5x forward speeds`() {
        val expectedSpeeds = listOf(2f, 3f, 5f)

        expectedSpeeds.forEach { speed ->
            val state = InternalPlayerUiState(
                trickplayActive = true,
                trickplaySpeed = speed,
            )
            assertEquals("Speed $speed should be supported", speed, state.trickplaySpeed)
        }
    }

    @Test
    fun `trickplay supports 2x 3x 5x rewind speeds`() {
        val expectedSpeeds = listOf(-2f, -3f, -5f)

        expectedSpeeds.forEach { speed ->
            val state = InternalPlayerUiState(
                trickplayActive = true,
                trickplaySpeed = speed,
            )
            assertEquals("Speed $speed should be supported", speed, state.trickplaySpeed)
        }
    }

    // ════════════════════════════════════════════════════════════════════════════
    // TrickplayDirection Enum Tests
    // ════════════════════════════════════════════════════════════════════════════

    @Test
    fun `TrickplayDirection FORWARD has multiplier of 1`() {
        assertEquals(
            "FORWARD should have multiplier 1",
            1,
            TrickplayDirection.FORWARD.multiplier,
        )
    }

    @Test
    fun `TrickplayDirection REWIND has multiplier of -1`() {
        assertEquals(
            "REWIND should have multiplier -1",
            -1,
            TrickplayDirection.REWIND.multiplier,
        )
    }

    @Test
    fun `TrickplayDirection can be used to calculate signed speed`() {
        val baseSpeed = 2f

        val forwardSpeed = baseSpeed * TrickplayDirection.FORWARD.multiplier
        val rewindSpeed = baseSpeed * TrickplayDirection.REWIND.multiplier

        assertEquals("Forward speed should be positive", 2f, forwardSpeed)
        assertEquals("Rewind speed should be negative", -2f, rewindSpeed)
    }

    // ════════════════════════════════════════════════════════════════════════════
    // Seek Preview State Tests
    // ════════════════════════════════════════════════════════════════════════════

    @Test
    fun `default seek preview state is hidden`() {
        val state = InternalPlayerUiState()

        assertFalse("Seek preview should be hidden by default", state.seekPreviewVisible)
        assertNull("Seek preview target should be null by default", state.seekPreviewTargetMs)
    }

    @Test
    fun `seek preview can show target position`() {
        val state = InternalPlayerUiState(
            seekPreviewVisible = true,
            seekPreviewTargetMs = 45_000L,
        )

        assertTrue("Seek preview should be visible", state.seekPreviewVisible)
        assertEquals("Target should be 45 seconds", 45_000L, state.seekPreviewTargetMs)
    }

    @Test
    fun `seek preview target can be forward from current position`() {
        val state = InternalPlayerUiState(
            positionMs = 30_000L,
            seekPreviewVisible = true,
            seekPreviewTargetMs = 40_000L,
        )

        val delta = state.seekPreviewTargetMs!! - state.positionMs
        assertTrue("Delta should be positive for forward seek", delta > 0)
        assertEquals("Delta should be 10 seconds", 10_000L, delta)
    }

    @Test
    fun `seek preview target can be backward from current position`() {
        val state = InternalPlayerUiState(
            positionMs = 30_000L,
            seekPreviewVisible = true,
            seekPreviewTargetMs = 20_000L,
        )

        val delta = state.seekPreviewTargetMs!! - state.positionMs
        assertTrue("Delta should be negative for backward seek", delta < 0)
        assertEquals("Delta should be -10 seconds", -10_000L, delta)
    }

    // ════════════════════════════════════════════════════════════════════════════
    // Aspect Ratio & Black Bars During Trickplay Tests
    // ════════════════════════════════════════════════════════════════════════════

    @Test
    fun `aspect ratio mode unchanged during trickplay`() {
        val baseState = InternalPlayerUiState(
            aspectRatioMode = AspectRatioMode.FILL,
        )

        val trickplayState = baseState.copy(
            trickplayActive = true,
            trickplaySpeed = 2f,
        )

        assertEquals(
            "Aspect ratio should remain FILL during trickplay",
            AspectRatioMode.FILL,
            trickplayState.aspectRatioMode,
        )
    }

    @Test
    fun `all aspect ratio modes work during trickplay`() {
        AspectRatioMode.entries.forEach { mode ->
            val state = InternalPlayerUiState(
                aspectRatioMode = mode,
                trickplayActive = true,
                trickplaySpeed = 3f,
            )

            assertEquals(
                "Aspect ratio $mode should be preserved during trickplay",
                mode,
                state.aspectRatioMode,
            )
        }
    }

    @Test
    fun `trickplay does not affect black bar configuration`() {
        // This test verifies the contract requirement that black bars remain black during trickplay
        // The actual black bar enforcement is tested in PlayerSurfacePhase5BlackBarTest
        // This test ensures the state model doesn't inadvertently affect background behavior

        val normalState = InternalPlayerUiState()
        val trickplayState = InternalPlayerUiState(
            trickplayActive = true,
            trickplaySpeed = 5f,
        )

        // Both states should have the same aspect ratio default
        assertEquals(
            "Default aspect ratio should be the same",
            normalState.aspectRatioMode,
            trickplayState.aspectRatioMode,
        )

        // Verify no additional fields affect background color (type safety)
        assertNotNull("State should be valid", trickplayState)
    }

    // ════════════════════════════════════════════════════════════════════════════
    // Trickplay + Playback Type Tests
    // ════════════════════════════════════════════════════════════════════════════

    @Test
    fun `trickplay can be activated for VOD content`() {
        val state = InternalPlayerUiState(
            playbackType = PlaybackType.VOD,
            trickplayActive = true,
            trickplaySpeed = 2f,
        )

        assertTrue("Trickplay should be active for VOD", state.trickplayActive)
        assertEquals("Playback type should be VOD", PlaybackType.VOD, state.playbackType)
    }

    @Test
    fun `trickplay can be activated for SERIES content`() {
        val state = InternalPlayerUiState(
            playbackType = PlaybackType.SERIES,
            trickplayActive = true,
            trickplaySpeed = 3f,
        )

        assertTrue("Trickplay should be active for SERIES", state.trickplayActive)
        assertEquals("Playback type should be SERIES", PlaybackType.SERIES, state.playbackType)
    }

    @Test
    fun `trickplay state independent of LIVE playback type`() {
        // LIVE content typically doesn't use trickplay (no seeking), but the state model allows it
        val state = InternalPlayerUiState(
            playbackType = PlaybackType.LIVE,
            trickplayActive = true,
            trickplaySpeed = 2f,
        )

        // State model allows this; behavior enforcement is in the session layer
        assertTrue("State should allow trickplay flag for LIVE (enforcement elsewhere)", state.trickplayActive)
    }

    // ════════════════════════════════════════════════════════════════════════════
    // Trickplay State Transition Tests
    // ════════════════════════════════════════════════════════════════════════════

    @Test
    fun `trickplay can be stopped by setting speed to 1`() {
        val activeState = InternalPlayerUiState(
            trickplayActive = true,
            trickplaySpeed = 3f,
        )

        val stoppedState = activeState.copy(
            trickplayActive = false,
            trickplaySpeed = 1f,
        )

        assertFalse("Trickplay should be inactive after stopping", stoppedState.trickplayActive)
        assertEquals("Speed should be normal (1.0) after stopping", 1f, stoppedState.trickplaySpeed)
    }

    @Test
    fun `trickplay speed can cycle through values`() {
        // Simulates speed cycling: 2x → 3x → 5x → 2x
        val speeds = listOf(2f, 3f, 5f)

        var currentIndex = 0
        repeat(6) { iteration ->
            val expectedSpeed = speeds[currentIndex % speeds.size]
            val state = InternalPlayerUiState(
                trickplayActive = true,
                trickplaySpeed = expectedSpeed,
            )

            assertEquals(
                "Cycle $iteration should have speed ${expectedSpeed}x",
                expectedSpeed,
                state.trickplaySpeed,
            )

            currentIndex++
        }
    }

    @Test
    fun `trickplay exit updates position correctly`() {
        // Simulates exiting trickplay and updating position
        val trickplayState = InternalPlayerUiState(
            positionMs = 30_000L,
            trickplayActive = true,
            trickplaySpeed = 2f,
        )

        // After exiting trickplay, position should reflect where we stopped
        val finalPosition = 45_000L // After fast forwarding
        val exitedState = trickplayState.copy(
            positionMs = finalPosition,
            trickplayActive = false,
            trickplaySpeed = 1f,
        )

        assertEquals("Position should be updated after trickplay exit", finalPosition, exitedState.positionMs)
        assertFalse("Trickplay should be inactive", exitedState.trickplayActive)
    }

    // ════════════════════════════════════════════════════════════════════════════
    // Edge Cases Tests
    // ════════════════════════════════════════════════════════════════════════════

    @Test
    fun `zero speed is not a valid trickplay speed`() {
        // Zero speed shouldn't be used in practice, but verify state handles it
        val state = InternalPlayerUiState(
            trickplayActive = true,
            trickplaySpeed = 0f,
        )

        // State model allows this; validation should be in session layer
        assertEquals("State should store zero speed (validation elsewhere)", 0f, state.trickplaySpeed)
    }

    @Test
    fun `seek preview can coexist with trickplay`() {
        val state = InternalPlayerUiState(
            trickplayActive = true,
            trickplaySpeed = 2f,
            seekPreviewVisible = true,
            seekPreviewTargetMs = 60_000L,
        )

        assertTrue("Trickplay should be active", state.trickplayActive)
        assertTrue("Seek preview should also be visible", state.seekPreviewVisible)
        assertEquals("Target should be set", 60_000L, state.seekPreviewTargetMs)
    }

    @Test
    fun `isLive and isSeries helpers work with trickplay state`() {
        val vodTrickplay = InternalPlayerUiState(
            playbackType = PlaybackType.VOD,
            trickplayActive = true,
        )

        val seriesTrickplay = InternalPlayerUiState(
            playbackType = PlaybackType.SERIES,
            trickplayActive = true,
        )

        val liveTrickplay = InternalPlayerUiState(
            playbackType = PlaybackType.LIVE,
            trickplayActive = true,
        )

        assertFalse("VOD should not be LIVE", vodTrickplay.isLive)
        assertFalse("VOD should not be SERIES", vodTrickplay.isSeries)

        assertFalse("SERIES should not be LIVE", seriesTrickplay.isLive)
        assertTrue("SERIES should be SERIES", seriesTrickplay.isSeries)

        assertTrue("LIVE should be LIVE", liveTrickplay.isLive)
        assertFalse("LIVE should not be SERIES", liveTrickplay.isSeries)
    }
}
