package com.chris.m3usuite.tv.input

import com.chris.m3usuite.tv.input.TvAction.Companion.getSeekDeltaMs
import com.chris.m3usuite.tv.input.TvAction.Companion.isChannelAction
import com.chris.m3usuite.tv.input.TvAction.Companion.isFocusAction
import com.chris.m3usuite.tv.input.TvAction.Companion.isNavigationAction
import com.chris.m3usuite.tv.input.TvAction.Companion.isOverlayAction
import com.chris.m3usuite.tv.input.TvAction.Companion.isPlaybackAction
import com.chris.m3usuite.tv.input.TvAction.Companion.isSeekAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for TvAction enum.
 *
 * Tests verify existence and integrity of all actions defined in the
 * Phase 6 contract.
 *
 * Contract Reference: INTERNAL_PLAYER_TV_INPUT_CONTRACT_PHASE6.md Section 3.1
 */
class TvActionEnumTest {
    // ══════════════════════════════════════════════════════════════════
    // PLAYBACK ACTION EXISTENCE TESTS
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `PLAY_PAUSE action exists`() {
        assertNotNull(TvAction.PLAY_PAUSE)
    }

    @Test
    fun `SEEK_FORWARD_10S action exists`() {
        assertNotNull(TvAction.SEEK_FORWARD_10S)
    }

    @Test
    fun `SEEK_FORWARD_30S action exists`() {
        assertNotNull(TvAction.SEEK_FORWARD_30S)
    }

    @Test
    fun `SEEK_BACKWARD_10S action exists`() {
        assertNotNull(TvAction.SEEK_BACKWARD_10S)
    }

    @Test
    fun `SEEK_BACKWARD_30S action exists`() {
        assertNotNull(TvAction.SEEK_BACKWARD_30S)
    }

    // ══════════════════════════════════════════════════════════════════
    // MENU/OVERLAY ACTION EXISTENCE TESTS
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `OPEN_CC_MENU action exists`() {
        assertNotNull(TvAction.OPEN_CC_MENU)
    }

    @Test
    fun `OPEN_ASPECT_MENU action exists`() {
        assertNotNull(TvAction.OPEN_ASPECT_MENU)
    }

    @Test
    fun `OPEN_QUICK_ACTIONS action exists`() {
        assertNotNull(TvAction.OPEN_QUICK_ACTIONS)
    }

    @Test
    fun `OPEN_LIVE_LIST action exists`() {
        assertNotNull(TvAction.OPEN_LIVE_LIST)
    }

    // ══════════════════════════════════════════════════════════════════
    // PAGINATION ACTION EXISTENCE TESTS
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `PAGE_UP action exists`() {
        assertNotNull(TvAction.PAGE_UP)
    }

    @Test
    fun `PAGE_DOWN action exists`() {
        assertNotNull(TvAction.PAGE_DOWN)
    }

    // ══════════════════════════════════════════════════════════════════
    // FOCUS ACTION EXISTENCE TESTS
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `FOCUS_QUICK_ACTIONS action exists`() {
        assertNotNull(TvAction.FOCUS_QUICK_ACTIONS)
    }

    @Test
    fun `FOCUS_TIMELINE action exists`() {
        assertNotNull(TvAction.FOCUS_TIMELINE)
    }

    // ══════════════════════════════════════════════════════════════════
    // NAVIGATION ACTION EXISTENCE TESTS
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `NAVIGATE_UP action exists`() {
        assertNotNull(TvAction.NAVIGATE_UP)
    }

    @Test
    fun `NAVIGATE_DOWN action exists`() {
        assertNotNull(TvAction.NAVIGATE_DOWN)
    }

    @Test
    fun `NAVIGATE_LEFT action exists`() {
        assertNotNull(TvAction.NAVIGATE_LEFT)
    }

    @Test
    fun `NAVIGATE_RIGHT action exists`() {
        assertNotNull(TvAction.NAVIGATE_RIGHT)
    }

    // ══════════════════════════════════════════════════════════════════
    // CHANNEL ACTION EXISTENCE TESTS
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `CHANNEL_UP action exists`() {
        assertNotNull(TvAction.CHANNEL_UP)
    }

    @Test
    fun `CHANNEL_DOWN action exists`() {
        assertNotNull(TvAction.CHANNEL_DOWN)
    }

    // ══════════════════════════════════════════════════════════════════
    // SYSTEM ACTION EXISTENCE TESTS
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `BACK action exists`() {
        assertNotNull(TvAction.BACK)
    }

    // ══════════════════════════════════════════════════════════════════
    // ENUM COMPLETENESS TESTS
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `TvAction enum has expected number of values`() {
        // Per contract: 5 playback + 4 overlay + 2 pagination + 2 focus + 4 navigation + 2 channel + 1 system = 20
        assertEquals(20, TvAction.entries.size)
    }

    @Test
    fun `all playback actions are present`() {
        val playbackActions =
            TvAction.entries.filter {
                with(TvAction.Companion) { it.isPlaybackAction() }
            }
        assertEquals(5, playbackActions.size)
        assertTrue(playbackActions.contains(TvAction.PLAY_PAUSE))
        assertTrue(playbackActions.contains(TvAction.SEEK_FORWARD_10S))
        assertTrue(playbackActions.contains(TvAction.SEEK_FORWARD_30S))
        assertTrue(playbackActions.contains(TvAction.SEEK_BACKWARD_10S))
        assertTrue(playbackActions.contains(TvAction.SEEK_BACKWARD_30S))
    }

    @Test
    fun `all overlay actions are present`() {
        val overlayActions =
            TvAction.entries.filter {
                with(TvAction.Companion) { it.isOverlayAction() }
            }
        assertEquals(4, overlayActions.size)
        assertTrue(overlayActions.contains(TvAction.OPEN_CC_MENU))
        assertTrue(overlayActions.contains(TvAction.OPEN_ASPECT_MENU))
        assertTrue(overlayActions.contains(TvAction.OPEN_QUICK_ACTIONS))
        assertTrue(overlayActions.contains(TvAction.OPEN_LIVE_LIST))
    }

    @Test
    fun `all navigation actions are present`() {
        val navActions =
            TvAction.entries.filter {
                with(TvAction.Companion) { it.isNavigationAction() }
            }
        assertEquals(4, navActions.size)
        assertTrue(navActions.contains(TvAction.NAVIGATE_UP))
        assertTrue(navActions.contains(TvAction.NAVIGATE_DOWN))
        assertTrue(navActions.contains(TvAction.NAVIGATE_LEFT))
        assertTrue(navActions.contains(TvAction.NAVIGATE_RIGHT))
    }

    @Test
    fun `all focus actions are present`() {
        val focusActions =
            TvAction.entries.filter {
                with(TvAction.Companion) { it.isFocusAction() }
            }
        assertEquals(2, focusActions.size)
        assertTrue(focusActions.contains(TvAction.FOCUS_QUICK_ACTIONS))
        assertTrue(focusActions.contains(TvAction.FOCUS_TIMELINE))
    }

    @Test
    fun `all channel actions are present`() {
        val channelActions =
            TvAction.entries.filter {
                with(TvAction.Companion) { it.isChannelAction() }
            }
        assertEquals(2, channelActions.size)
        assertTrue(channelActions.contains(TvAction.CHANNEL_UP))
        assertTrue(channelActions.contains(TvAction.CHANNEL_DOWN))
    }

    // ══════════════════════════════════════════════════════════════════
    // HELPER METHOD TESTS - isPlaybackAction
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `isPlaybackAction returns true for playback actions`() {
        assertTrue(TvAction.PLAY_PAUSE.isPlaybackAction())
        assertTrue(TvAction.SEEK_FORWARD_10S.isPlaybackAction())
        assertTrue(TvAction.SEEK_FORWARD_30S.isPlaybackAction())
        assertTrue(TvAction.SEEK_BACKWARD_10S.isPlaybackAction())
        assertTrue(TvAction.SEEK_BACKWARD_30S.isPlaybackAction())
    }

    @Test
    fun `isPlaybackAction returns false for non-playback actions`() {
        assertFalse(TvAction.BACK.isPlaybackAction())
        assertFalse(TvAction.NAVIGATE_UP.isPlaybackAction())
        assertFalse(TvAction.OPEN_CC_MENU.isPlaybackAction())
    }

    // ══════════════════════════════════════════════════════════════════
    // HELPER METHOD TESTS - isOverlayAction
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `isOverlayAction returns true for overlay actions`() {
        assertTrue(TvAction.OPEN_CC_MENU.isOverlayAction())
        assertTrue(TvAction.OPEN_ASPECT_MENU.isOverlayAction())
        assertTrue(TvAction.OPEN_QUICK_ACTIONS.isOverlayAction())
        assertTrue(TvAction.OPEN_LIVE_LIST.isOverlayAction())
    }

    @Test
    fun `isOverlayAction returns false for non-overlay actions`() {
        assertFalse(TvAction.PLAY_PAUSE.isOverlayAction())
        assertFalse(TvAction.NAVIGATE_UP.isOverlayAction())
        assertFalse(TvAction.BACK.isOverlayAction())
    }

    // ══════════════════════════════════════════════════════════════════
    // HELPER METHOD TESTS - isNavigationAction
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `isNavigationAction returns true for navigation actions`() {
        assertTrue(TvAction.NAVIGATE_UP.isNavigationAction())
        assertTrue(TvAction.NAVIGATE_DOWN.isNavigationAction())
        assertTrue(TvAction.NAVIGATE_LEFT.isNavigationAction())
        assertTrue(TvAction.NAVIGATE_RIGHT.isNavigationAction())
    }

    @Test
    fun `isNavigationAction returns false for non-navigation actions`() {
        assertFalse(TvAction.PLAY_PAUSE.isNavigationAction())
        assertFalse(TvAction.BACK.isNavigationAction())
        assertFalse(TvAction.FOCUS_TIMELINE.isNavigationAction())
    }

    // ══════════════════════════════════════════════════════════════════
    // HELPER METHOD TESTS - isFocusAction
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `isFocusAction returns true for focus actions`() {
        assertTrue(TvAction.FOCUS_QUICK_ACTIONS.isFocusAction())
        assertTrue(TvAction.FOCUS_TIMELINE.isFocusAction())
    }

    @Test
    fun `isFocusAction returns false for non-focus actions`() {
        assertFalse(TvAction.PLAY_PAUSE.isFocusAction())
        assertFalse(TvAction.NAVIGATE_UP.isFocusAction())
        assertFalse(TvAction.BACK.isFocusAction())
    }

    // ══════════════════════════════════════════════════════════════════
    // HELPER METHOD TESTS - isSeekAction
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `isSeekAction returns true for seek actions`() {
        assertTrue(TvAction.SEEK_FORWARD_10S.isSeekAction())
        assertTrue(TvAction.SEEK_FORWARD_30S.isSeekAction())
        assertTrue(TvAction.SEEK_BACKWARD_10S.isSeekAction())
        assertTrue(TvAction.SEEK_BACKWARD_30S.isSeekAction())
    }

    @Test
    fun `isSeekAction returns false for non-seek actions`() {
        assertFalse(TvAction.PLAY_PAUSE.isSeekAction())
        assertFalse(TvAction.NAVIGATE_UP.isSeekAction())
        assertFalse(TvAction.BACK.isSeekAction())
    }

    // ══════════════════════════════════════════════════════════════════
    // HELPER METHOD TESTS - isChannelAction
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `isChannelAction returns true for channel actions`() {
        assertTrue(TvAction.CHANNEL_UP.isChannelAction())
        assertTrue(TvAction.CHANNEL_DOWN.isChannelAction())
    }

    @Test
    fun `isChannelAction returns false for non-channel actions`() {
        assertFalse(TvAction.PLAY_PAUSE.isChannelAction())
        assertFalse(TvAction.NAVIGATE_UP.isChannelAction())
        assertFalse(TvAction.BACK.isChannelAction())
    }

    // ══════════════════════════════════════════════════════════════════
    // HELPER METHOD TESTS - getSeekDeltaMs
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `getSeekDeltaMs returns correct values for seek actions`() {
        assertEquals(10_000L, TvAction.SEEK_FORWARD_10S.getSeekDeltaMs())
        assertEquals(30_000L, TvAction.SEEK_FORWARD_30S.getSeekDeltaMs())
        assertEquals(-10_000L, TvAction.SEEK_BACKWARD_10S.getSeekDeltaMs())
        assertEquals(-30_000L, TvAction.SEEK_BACKWARD_30S.getSeekDeltaMs())
    }

    @Test
    fun `getSeekDeltaMs returns null for non-seek actions`() {
        assertNull(TvAction.PLAY_PAUSE.getSeekDeltaMs())
        assertNull(TvAction.NAVIGATE_UP.getSeekDeltaMs())
        assertNull(TvAction.BACK.getSeekDeltaMs())
        assertNull(TvAction.OPEN_CC_MENU.getSeekDeltaMs())
    }

    // ══════════════════════════════════════════════════════════════════
    // ENUM ORDERING STABILITY TESTS
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `enum ordinal values are stable`() {
        // Verify specific ordinal positions to catch unintended reordering
        // This helps ensure serialization/deserialization stability
        assertEquals(0, TvAction.PLAY_PAUSE.ordinal)
        assertEquals(1, TvAction.SEEK_FORWARD_10S.ordinal)
        assertEquals(2, TvAction.SEEK_FORWARD_30S.ordinal)
        assertEquals(3, TvAction.SEEK_BACKWARD_10S.ordinal)
        assertEquals(4, TvAction.SEEK_BACKWARD_30S.ordinal)
    }

    // ══════════════════════════════════════════════════════════════════
    // CONTRACT COMPLIANCE TESTS
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `all contract-required actions exist`() {
        // List of actions required by INTERNAL_PLAYER_TV_INPUT_CONTRACT_PHASE6.md
        val requiredActions =
            listOf(
                "PLAY_PAUSE",
                "SEEK_FORWARD_10S",
                "SEEK_FORWARD_30S",
                "SEEK_BACKWARD_10S",
                "SEEK_BACKWARD_30S",
                "OPEN_CC_MENU",
                "OPEN_ASPECT_MENU",
                "OPEN_QUICK_ACTIONS",
                "OPEN_LIVE_LIST",
                "PAGE_UP",
                "PAGE_DOWN",
                "FOCUS_QUICK_ACTIONS",
                "FOCUS_TIMELINE",
                "NAVIGATE_UP",
                "NAVIGATE_DOWN",
                "NAVIGATE_LEFT",
                "NAVIGATE_RIGHT",
                "CHANNEL_UP",
                "CHANNEL_DOWN",
                "BACK",
            )

        val enumNames = TvAction.entries.map { it.name }

        for (action in requiredActions) {
            assertTrue("Required action $action should exist", enumNames.contains(action))
        }
    }
}
