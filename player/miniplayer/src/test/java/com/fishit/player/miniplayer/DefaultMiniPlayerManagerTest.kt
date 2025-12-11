package com.fishit.player.miniplayer

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for DefaultMiniPlayerManager.
 *
 * ════════════════════════════════════════════════════════════════════════════════
 * PHASE 5 – MiniPlayer Tests
 * ════════════════════════════════════════════════════════════════════════════════
 */
class DefaultMiniPlayerManagerTest {

    private lateinit var manager: DefaultMiniPlayerManager

    @Before
    fun setup() {
        manager = DefaultMiniPlayerManager()
        manager.resetForTesting()
    }

    @Test
    fun `initial state is not visible`() {
        val state = manager.state.value
        assertFalse(state.visible)
        assertEquals(MiniPlayerMode.NORMAL, state.mode)
        assertEquals(MiniPlayerAnchor.BOTTOM_RIGHT, state.anchor)
    }

    @Test
    fun `enterMiniPlayer sets visible and stores return context`() {
        manager.enterMiniPlayer(
            fromRoute = "library",
            mediaId = 123L,
            rowIndex = 2,
            itemIndex = 5
        )

        val state = manager.state.value
        assertTrue(state.visible)
        assertEquals(MiniPlayerMode.NORMAL, state.mode)
        assertEquals("library", state.returnRoute)
        assertEquals(123L, state.returnMediaId)
        assertEquals(2, state.returnRowIndex)
        assertEquals(5, state.returnItemIndex)
    }

    @Test
    fun `exitMiniPlayer clears visibility`() {
        manager.enterMiniPlayer(fromRoute = "library")
        manager.exitMiniPlayer(returnToFullPlayer = false)

        val state = manager.state.value
        assertFalse(state.visible)
        assertNull(state.returnRoute)
    }

    @Test
    fun `exitMiniPlayer with returnToFullPlayer keeps return context`() {
        manager.enterMiniPlayer(fromRoute = "library", mediaId = 123L)
        manager.exitMiniPlayer(returnToFullPlayer = true)

        val state = manager.state.value
        assertFalse(state.visible)
        assertEquals("library", state.returnRoute)
        assertEquals(123L, state.returnMediaId)
    }

    @Test
    fun `enterResizeMode changes mode and stores previous values`() {
        manager.enterMiniPlayer(fromRoute = "library")
        val originalSize = manager.state.value.size

        manager.enterResizeMode()

        val state = manager.state.value
        assertEquals(MiniPlayerMode.RESIZE, state.mode)
        assertEquals(originalSize, state.previousSize)
    }

    @Test
    fun `enterResizeMode does nothing if not visible`() {
        manager.enterResizeMode()

        val state = manager.state.value
        assertEquals(MiniPlayerMode.NORMAL, state.mode)
    }

    @Test
    fun `applyResize changes size within bounds`() {
        manager.enterMiniPlayer(fromRoute = "library")
        manager.enterResizeMode()

        val originalSize = manager.state.value.size
        manager.applyResize(RESIZE_SIZE_DELTA)

        val state = manager.state.value
        assertEquals(originalSize.width + RESIZE_SIZE_DELTA.width, state.size.width)
        assertEquals(originalSize.height + RESIZE_SIZE_DELTA.height, state.size.height)
    }

    @Test
    fun `applyResize clamps to max size`() {
        manager.enterMiniPlayer(fromRoute = "library")
        manager.updateSize(MAX_MINI_SIZE)
        manager.enterResizeMode()

        manager.applyResize(DpSize(100.dp, 100.dp))

        val state = manager.state.value
        assertEquals(MAX_MINI_SIZE.width, state.size.width)
        assertEquals(MAX_MINI_SIZE.height, state.size.height)
    }

    @Test
    fun `applyResize clamps to min size`() {
        manager.enterMiniPlayer(fromRoute = "library")
        manager.updateSize(MIN_MINI_SIZE)
        manager.enterResizeMode()

        manager.applyResize(DpSize((-100).dp, (-100).dp))

        val state = manager.state.value
        assertEquals(MIN_MINI_SIZE.width, state.size.width)
        assertEquals(MIN_MINI_SIZE.height, state.size.height)
    }

    @Test
    fun `moveBy updates position`() {
        manager.enterMiniPlayer(fromRoute = "library")
        manager.enterResizeMode()

        manager.moveBy(Offset(50f, 30f))

        val state = manager.state.value
        assertEquals(50f, state.position?.x ?: 0f, 0.01f)
        assertEquals(30f, state.position?.y ?: 0f, 0.01f)
    }

    @Test
    fun `confirmResize returns to normal mode and clears previous values`() {
        manager.enterMiniPlayer(fromRoute = "library")
        manager.enterResizeMode()
        manager.applyResize(RESIZE_SIZE_DELTA)

        val sizeBeforeConfirm = manager.state.value.size

        manager.confirmResize()

        val state = manager.state.value
        assertEquals(MiniPlayerMode.NORMAL, state.mode)
        assertNull(state.previousSize)
        assertEquals(sizeBeforeConfirm, state.size)
    }

    @Test
    fun `cancelResize restores previous size and position`() {
        manager.enterMiniPlayer(fromRoute = "library")
        val originalSize = manager.state.value.size

        manager.enterResizeMode()
        manager.applyResize(RESIZE_SIZE_DELTA)
        manager.moveBy(Offset(100f, 100f))

        manager.cancelResize()

        val state = manager.state.value
        assertEquals(MiniPlayerMode.NORMAL, state.mode)
        assertEquals(originalSize, state.size)
        assertNull(state.previousSize)
    }

    @Test
    fun `updateAnchor changes anchor position`() {
        manager.enterMiniPlayer(fromRoute = "library")
        manager.updateAnchor(MiniPlayerAnchor.TOP_LEFT)

        assertEquals(MiniPlayerAnchor.TOP_LEFT, manager.state.value.anchor)
    }

    @Test
    fun `reset returns to initial state`() {
        manager.enterMiniPlayer(fromRoute = "library", mediaId = 123L)
        manager.enterResizeMode()
        manager.applyResize(RESIZE_SIZE_DELTA)

        manager.reset()

        assertEquals(MiniPlayerState.INITIAL, manager.state.value)
    }

    @Test
    fun `markFirstTimeHintShown sets flag`() {
        manager.markFirstTimeHintShown()
        assertTrue(manager.state.value.hasShownFirstTimeHint)
    }
}
