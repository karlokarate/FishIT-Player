package com.chris.m3usuite.player.miniplayer

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for MiniPlayer snapping and bounds logic.
 *
 * ════════════════════════════════════════════════════════════════════════════════
 * PHASE 7 – MiniPlayer Bounds & Snapping Tests
 * ════════════════════════════════════════════════════════════════════════════════
 *
 * Tests verify:
 * - snapToNearestAnchor() correctly determines the nearest anchor based on position
 * - clampToSafeArea() keeps MiniPlayer within screen bounds
 * - Center snap threshold works correctly
 * - All corner anchors are detected properly
 *
 * **Contract Reference:**
 * - INTERNAL_PLAYER_PLAYBACK_SESSION_CONTRACT_PHASE7.md Section 4.1
 */
class MiniPlayerBoundsTest {
    // Test density (1:1 mapping for simplicity)
    private val testDensity = Density(1f, 1f)

    // Test screen dimensions (1920x1080 pixels)
    private val screenWidthPx = 1920f
    private val screenHeightPx = 1080f

    @Before
    fun setUp() {
        DefaultMiniPlayerManager.resetForTesting()
    }

    @After
    fun tearDown() {
        DefaultMiniPlayerManager.resetForTesting()
    }

    // ══════════════════════════════════════════════════════════════════
    // snapToNearestAnchor() Tests
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `snapToNearestAnchor snaps to TOP_LEFT when position is in top-left quadrant`() {
        DefaultMiniPlayerManager.enterMiniPlayer(fromRoute = "library")
        // Position in top-left quadrant
        DefaultMiniPlayerManager.updatePosition(Offset(100f, 100f))

        DefaultMiniPlayerManager.snapToNearestAnchor(
            screenWidthPx = screenWidthPx,
            screenHeightPx = screenHeightPx,
            density = testDensity,
        )

        assertEquals(MiniPlayerAnchor.TOP_LEFT, DefaultMiniPlayerManager.state.value.anchor)
        // Position should be reset after snapping
        assertNull(DefaultMiniPlayerManager.state.value.position)
    }

    @Test
    fun `snapToNearestAnchor snaps to TOP_RIGHT when position is in top-right quadrant`() {
        DefaultMiniPlayerManager.enterMiniPlayer(fromRoute = "library")
        // Position in top-right quadrant
        DefaultMiniPlayerManager.updatePosition(Offset(1500f, 100f))

        DefaultMiniPlayerManager.snapToNearestAnchor(
            screenWidthPx = screenWidthPx,
            screenHeightPx = screenHeightPx,
            density = testDensity,
        )

        assertEquals(MiniPlayerAnchor.TOP_RIGHT, DefaultMiniPlayerManager.state.value.anchor)
    }

    @Test
    fun `snapToNearestAnchor snaps to BOTTOM_LEFT when position is in bottom-left quadrant`() {
        DefaultMiniPlayerManager.enterMiniPlayer(fromRoute = "library")
        // Position in bottom-left quadrant
        DefaultMiniPlayerManager.updatePosition(Offset(100f, 800f))

        DefaultMiniPlayerManager.snapToNearestAnchor(
            screenWidthPx = screenWidthPx,
            screenHeightPx = screenHeightPx,
            density = testDensity,
        )

        assertEquals(MiniPlayerAnchor.BOTTOM_LEFT, DefaultMiniPlayerManager.state.value.anchor)
    }

    @Test
    fun `snapToNearestAnchor snaps to BOTTOM_RIGHT when position is in bottom-right quadrant`() {
        DefaultMiniPlayerManager.enterMiniPlayer(fromRoute = "library")
        // Position in bottom-right quadrant
        DefaultMiniPlayerManager.updatePosition(Offset(1500f, 800f))

        DefaultMiniPlayerManager.snapToNearestAnchor(
            screenWidthPx = screenWidthPx,
            screenHeightPx = screenHeightPx,
            density = testDensity,
        )

        assertEquals(MiniPlayerAnchor.BOTTOM_RIGHT, DefaultMiniPlayerManager.state.value.anchor)
    }

    @Test
    fun `snapToNearestAnchor snaps to CENTER_TOP when near horizontal center and in top half`() {
        DefaultMiniPlayerManager.enterMiniPlayer(fromRoute = "library")
        // Use a smaller size to make center detection easier
        DefaultMiniPlayerManager.updateSize(DpSize(200.dp, 112.dp))
        // Position near horizontal center, top half
        // Center X should be: position.x + width/2 = close to screenWidth/2 (960)
        // So position.x = 960 - 100 = 860
        DefaultMiniPlayerManager.updatePosition(Offset(860f, 100f))

        DefaultMiniPlayerManager.snapToNearestAnchor(
            screenWidthPx = screenWidthPx,
            screenHeightPx = screenHeightPx,
            density = testDensity,
        )

        assertEquals(MiniPlayerAnchor.CENTER_TOP, DefaultMiniPlayerManager.state.value.anchor)
    }

    @Test
    fun `snapToNearestAnchor snaps to CENTER_BOTTOM when near horizontal center and in bottom half`() {
        DefaultMiniPlayerManager.enterMiniPlayer(fromRoute = "library")
        DefaultMiniPlayerManager.updateSize(DpSize(200.dp, 112.dp))
        // Position near horizontal center, bottom half
        DefaultMiniPlayerManager.updatePosition(Offset(860f, 800f))

        DefaultMiniPlayerManager.snapToNearestAnchor(
            screenWidthPx = screenWidthPx,
            screenHeightPx = screenHeightPx,
            density = testDensity,
        )

        assertEquals(MiniPlayerAnchor.CENTER_BOTTOM, DefaultMiniPlayerManager.state.value.anchor)
    }

    @Test
    fun `snapToNearestAnchor does nothing when MiniPlayer is not visible`() {
        // MiniPlayer is not visible
        DefaultMiniPlayerManager.updatePosition(Offset(100f, 100f))

        DefaultMiniPlayerManager.snapToNearestAnchor(
            screenWidthPx = screenWidthPx,
            screenHeightPx = screenHeightPx,
            density = testDensity,
        )

        // Anchor should remain default
        assertEquals(MiniPlayerAnchor.BOTTOM_RIGHT, DefaultMiniPlayerManager.state.value.anchor)
    }

    // ══════════════════════════════════════════════════════════════════
    // clampToSafeArea() Tests
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `clampToSafeArea does nothing when position is null`() {
        DefaultMiniPlayerManager.enterMiniPlayer(fromRoute = "library")
        // Position is null by default

        DefaultMiniPlayerManager.clampToSafeArea(
            screenWidthPx = screenWidthPx,
            screenHeightPx = screenHeightPx,
            density = testDensity,
        )

        assertNull(DefaultMiniPlayerManager.state.value.position)
    }

    @Test
    fun `clampToSafeArea does nothing when MiniPlayer is not visible`() {
        // MiniPlayer is not visible
        DefaultMiniPlayerManager.updatePosition(Offset(10000f, 10000f))

        DefaultMiniPlayerManager.clampToSafeArea(
            screenWidthPx = screenWidthPx,
            screenHeightPx = screenHeightPx,
            density = testDensity,
        )

        // Position should remain unchanged (it's not visible anyway)
        assertEquals(Offset(10000f, 10000f), DefaultMiniPlayerManager.state.value.position)
    }

    @Test
    fun `clampToSafeArea clamps position within bounds`() {
        DefaultMiniPlayerManager.enterMiniPlayer(fromRoute = "library")
        // Set a very large position offset that exceeds bounds
        DefaultMiniPlayerManager.updatePosition(Offset(5000f, 5000f))

        DefaultMiniPlayerManager.clampToSafeArea(
            screenWidthPx = screenWidthPx,
            screenHeightPx = screenHeightPx,
            density = testDensity,
        )

        val clampedPosition = DefaultMiniPlayerManager.state.value.position
        // Position should be clamped to valid range
        assert(clampedPosition != null)
        assert(clampedPosition!!.x <= screenWidthPx)
        assert(clampedPosition.y <= screenHeightPx)
    }

    // ══════════════════════════════════════════════════════════════════
    // markFirstTimeHintShown() Tests
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `markFirstTimeHintShown sets hasShownFirstTimeHint to true`() {
        DefaultMiniPlayerManager.enterMiniPlayer(fromRoute = "library")
        assertEquals(false, DefaultMiniPlayerManager.state.value.hasShownFirstTimeHint)

        DefaultMiniPlayerManager.markFirstTimeHintShown()

        assertEquals(true, DefaultMiniPlayerManager.state.value.hasShownFirstTimeHint)
    }

    @Test
    fun `hasShownFirstTimeHint persists across mode changes`() {
        DefaultMiniPlayerManager.enterMiniPlayer(fromRoute = "library")
        DefaultMiniPlayerManager.markFirstTimeHintShown()

        // Change mode
        DefaultMiniPlayerManager.updateMode(MiniPlayerMode.RESIZE)
        DefaultMiniPlayerManager.updateMode(MiniPlayerMode.NORMAL)

        assertEquals(true, DefaultMiniPlayerManager.state.value.hasShownFirstTimeHint)
    }

    @Test
    fun `hasShownFirstTimeHint is reset on full reset`() {
        DefaultMiniPlayerManager.enterMiniPlayer(fromRoute = "library")
        DefaultMiniPlayerManager.markFirstTimeHintShown()

        DefaultMiniPlayerManager.reset()

        assertEquals(false, DefaultMiniPlayerManager.state.value.hasShownFirstTimeHint)
    }

    // ══════════════════════════════════════════════════════════════════
    // New Anchor Types Tests
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `CENTER_TOP anchor is available`() {
        DefaultMiniPlayerManager.updateAnchor(MiniPlayerAnchor.CENTER_TOP)
        assertEquals(MiniPlayerAnchor.CENTER_TOP, DefaultMiniPlayerManager.state.value.anchor)
    }

    @Test
    fun `CENTER_BOTTOM anchor is available`() {
        DefaultMiniPlayerManager.updateAnchor(MiniPlayerAnchor.CENTER_BOTTOM)
        assertEquals(MiniPlayerAnchor.CENTER_BOTTOM, DefaultMiniPlayerManager.state.value.anchor)
    }

    @Test
    fun `all six anchors can be cycled through`() {
        val allAnchors = MiniPlayerAnchor.entries.toList()
        assertEquals(6, allAnchors.size)

        for (anchor in allAnchors) {
            DefaultMiniPlayerManager.updateAnchor(anchor)
            assertEquals(anchor, DefaultMiniPlayerManager.state.value.anchor)
        }
    }
}
