package com.chris.m3usuite.tv.input

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Unit tests for MiniPlayer visibility input filtering.
 *
 * Phase 7: Tests verify that when ctx.isMiniPlayerVisible is true, ROW_FAST_SCROLL_*
 * actions are blocked while all other actions pass through.
 *
 * Contract Reference: INTERNAL_PLAYER_PLAYBACK_SESSION_CONTRACT_PHASE7.md Section 5
 */
class TvInputMiniPlayerFilterTest {
    // ══════════════════════════════════════════════════════════════════
    // BLOCKED ACTIONS WHEN MINIPLAYER IS VISIBLE
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `ROW_FAST_SCROLL_FORWARD is blocked when MiniPlayer is visible`() {
        val ctx = TvScreenContext.library(isMiniPlayerVisible = true)
        val result = filterForMiniPlayer(TvAction.ROW_FAST_SCROLL_FORWARD, ctx)

        assertNull("ROW_FAST_SCROLL_FORWARD should be blocked", result)
    }

    @Test
    fun `ROW_FAST_SCROLL_BACKWARD is blocked when MiniPlayer is visible`() {
        val ctx = TvScreenContext.library(isMiniPlayerVisible = true)
        val result = filterForMiniPlayer(TvAction.ROW_FAST_SCROLL_BACKWARD, ctx)

        assertNull("ROW_FAST_SCROLL_BACKWARD should be blocked", result)
    }

    // ══════════════════════════════════════════════════════════════════
    // ALLOWED ACTIONS WHEN MINIPLAYER IS VISIBLE
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `NAVIGATE_UP is allowed when MiniPlayer is visible`() {
        val ctx = TvScreenContext.library(isMiniPlayerVisible = true)
        val result = filterForMiniPlayer(TvAction.NAVIGATE_UP, ctx)

        assertEquals(TvAction.NAVIGATE_UP, result)
    }

    @Test
    fun `NAVIGATE_DOWN is allowed when MiniPlayer is visible`() {
        val ctx = TvScreenContext.library(isMiniPlayerVisible = true)
        val result = filterForMiniPlayer(TvAction.NAVIGATE_DOWN, ctx)

        assertEquals(TvAction.NAVIGATE_DOWN, result)
    }

    @Test
    fun `PLAY_PAUSE is allowed when MiniPlayer is visible`() {
        val ctx = TvScreenContext.library(isMiniPlayerVisible = true)
        val result = filterForMiniPlayer(TvAction.PLAY_PAUSE, ctx)

        assertEquals(TvAction.PLAY_PAUSE, result)
    }

    @Test
    fun `BACK is allowed when MiniPlayer is visible`() {
        val ctx = TvScreenContext.library(isMiniPlayerVisible = true)
        val result = filterForMiniPlayer(TvAction.BACK, ctx)

        assertEquals(TvAction.BACK, result)
    }

    @Test
    fun `OPEN_DETAILS is allowed when MiniPlayer is visible`() {
        val ctx = TvScreenContext.library(isMiniPlayerVisible = true)
        val result = filterForMiniPlayer(TvAction.OPEN_DETAILS, ctx)

        assertEquals(TvAction.OPEN_DETAILS, result)
    }

    // ══════════════════════════════════════════════════════════════════
    // NO BLOCKING WHEN MINIPLAYER IS NOT VISIBLE
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `ROW_FAST_SCROLL_FORWARD is allowed when MiniPlayer is not visible`() {
        val ctx = TvScreenContext.library(isMiniPlayerVisible = false)
        val result = filterForMiniPlayer(TvAction.ROW_FAST_SCROLL_FORWARD, ctx)

        assertEquals(TvAction.ROW_FAST_SCROLL_FORWARD, result)
    }

    @Test
    fun `ROW_FAST_SCROLL_BACKWARD is allowed when MiniPlayer is not visible`() {
        val ctx = TvScreenContext.library(isMiniPlayerVisible = false)
        val result = filterForMiniPlayer(TvAction.ROW_FAST_SCROLL_BACKWARD, ctx)

        assertEquals(TvAction.ROW_FAST_SCROLL_BACKWARD, result)
    }

    @Test
    fun `Fast scroll allowed on start screen when MiniPlayer is not visible`() {
        val ctx = TvScreenContext.start(isMiniPlayerVisible = false)
        val result = filterForMiniPlayer(TvAction.ROW_FAST_SCROLL_FORWARD, ctx)

        assertEquals(TvAction.ROW_FAST_SCROLL_FORWARD, result)
    }

    // ══════════════════════════════════════════════════════════════════
    // NULL ACTION HANDLING
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `null action passes through regardless of MiniPlayer visibility`() {
        val ctxVisible = TvScreenContext.library(isMiniPlayerVisible = true)
        val ctxHidden = TvScreenContext.library(isMiniPlayerVisible = false)

        assertNull(filterForMiniPlayer(null, ctxVisible))
        assertNull(filterForMiniPlayer(null, ctxHidden))
    }

    // ══════════════════════════════════════════════════════════════════
    // INTEGRATION WITH RESOLVE FUNCTION
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `resolve() blocks ROW_FAST_SCROLL when MiniPlayer is visible`() {
        // Create a config that maps FF to ROW_FAST_SCROLL_FORWARD
        val config = TvScreenInputConfig(
            screenId = TvScreenId.LIBRARY,
            bindings = mapOf(TvKeyRole.FAST_FORWARD to TvAction.ROW_FAST_SCROLL_FORWARD),
        )
        val ctx = TvScreenContext.library(isMiniPlayerVisible = true)

        val result = resolve(config, TvKeyRole.FAST_FORWARD, ctx)

        assertNull("ROW_FAST_SCROLL_FORWARD should be blocked via resolve()", result)
    }

    @Test
    fun `resolve() allows ROW_FAST_SCROLL when MiniPlayer is not visible`() {
        val config = TvScreenInputConfig(
            screenId = TvScreenId.LIBRARY,
            bindings = mapOf(TvKeyRole.FAST_FORWARD to TvAction.ROW_FAST_SCROLL_FORWARD),
        )
        val ctx = TvScreenContext.library(isMiniPlayerVisible = false)

        val result = resolve(config, TvKeyRole.FAST_FORWARD, ctx)

        assertEquals(TvAction.ROW_FAST_SCROLL_FORWARD, result)
    }

    @Test
    fun `resolve() applies MiniPlayer filter after overlay filter`() {
        // Verify that MiniPlayer filter is applied even when overlay is not blocking
        val config = TvScreenInputConfig(
            screenId = TvScreenId.LIBRARY,
            bindings = mapOf(TvKeyRole.REWIND to TvAction.ROW_FAST_SCROLL_BACKWARD),
        )
        val ctx = TvScreenContext.library(
            hasBlockingOverlay = false,
            isMiniPlayerVisible = true,
        )

        val result = resolve(config, TvKeyRole.REWIND, ctx)

        assertNull("ROW_FAST_SCROLL_BACKWARD should be blocked", result)
    }

    // ══════════════════════════════════════════════════════════════════
    // TVSCREENCONTEXT FACTORY METHODS
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `library() factory method supports isMiniPlayerVisible parameter`() {
        val ctxWithMiniPlayer = TvScreenContext.library(isMiniPlayerVisible = true)
        val ctxWithoutMiniPlayer = TvScreenContext.library(isMiniPlayerVisible = false)

        assertEquals(true, ctxWithMiniPlayer.isMiniPlayerVisible)
        assertEquals(false, ctxWithoutMiniPlayer.isMiniPlayerVisible)
    }

    @Test
    fun `start() factory method supports isMiniPlayerVisible parameter`() {
        val ctxWithMiniPlayer = TvScreenContext.start(isMiniPlayerVisible = true)
        val ctxWithoutMiniPlayer = TvScreenContext.start(isMiniPlayerVisible = false)

        assertEquals(true, ctxWithMiniPlayer.isMiniPlayerVisible)
        assertEquals(false, ctxWithoutMiniPlayer.isMiniPlayerVisible)
    }

    @Test
    fun `default isMiniPlayerVisible is false`() {
        val ctx = TvScreenContext(screenId = TvScreenId.LIBRARY)

        assertEquals(false, ctx.isMiniPlayerVisible)
    }
}
