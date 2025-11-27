package com.chris.m3usuite.tv.input

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for TvScreenContext and TvScreenId data models.
 *
 * Tests verify sanity of the data model including factory methods,
 * default values, and screen identification.
 *
 * Contract Reference: INTERNAL_PLAYER_TV_INPUT_CONTRACT_PHASE6.md Section 4.1, 4.2
 */
class TvScreenContextTest {
    // ══════════════════════════════════════════════════════════════════
    // TvScreenId EXISTENCE TESTS
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `START screen id exists`() {
        assertNotNull(TvScreenId.START)
    }

    @Test
    fun `LIBRARY screen id exists`() {
        assertNotNull(TvScreenId.LIBRARY)
    }

    @Test
    fun `PLAYER screen id exists`() {
        assertNotNull(TvScreenId.PLAYER)
    }

    @Test
    fun `SETTINGS screen id exists`() {
        assertNotNull(TvScreenId.SETTINGS)
    }

    @Test
    fun `DETAIL screen id exists`() {
        assertNotNull(TvScreenId.DETAIL)
    }

    @Test
    fun `PROFILE_GATE screen id exists`() {
        assertNotNull(TvScreenId.PROFILE_GATE)
    }

    @Test
    fun `LIVE_LIST screen id exists`() {
        assertNotNull(TvScreenId.LIVE_LIST)
    }

    @Test
    fun `CC_MENU screen id exists`() {
        assertNotNull(TvScreenId.CC_MENU)
    }

    @Test
    fun `ASPECT_MENU screen id exists`() {
        assertNotNull(TvScreenId.ASPECT_MENU)
    }

    @Test
    fun `SEARCH screen id exists`() {
        assertNotNull(TvScreenId.SEARCH)
    }

    // ══════════════════════════════════════════════════════════════════
    // TvScreenId OVERLAY CLASSIFICATION TESTS
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `isOverlay returns true for overlay screens`() {
        assertTrue(TvScreenId.LIVE_LIST.isOverlay())
        assertTrue(TvScreenId.CC_MENU.isOverlay())
        assertTrue(TvScreenId.ASPECT_MENU.isOverlay())
        assertTrue(TvScreenId.QUICK_ACTIONS.isOverlay())
        assertTrue(TvScreenId.EPG_GUIDE.isOverlay())
        assertTrue(TvScreenId.ERROR_DIALOG.isOverlay())
        assertTrue(TvScreenId.SLEEP_TIMER.isOverlay())
        assertTrue(TvScreenId.SPEED_DIALOG.isOverlay())
        assertTrue(TvScreenId.TRACKS_DIALOG.isOverlay())
    }

    @Test
    fun `isOverlay returns false for non-overlay screens`() {
        assertFalse(TvScreenId.START.isOverlay())
        assertFalse(TvScreenId.LIBRARY.isOverlay())
        assertFalse(TvScreenId.PLAYER.isOverlay())
        assertFalse(TvScreenId.SETTINGS.isOverlay())
        assertFalse(TvScreenId.DETAIL.isOverlay())
        assertFalse(TvScreenId.SEARCH.isOverlay())
    }

    @Test
    fun `isBlockingOverlay returns true for blocking overlays`() {
        assertTrue(TvScreenId.CC_MENU.isBlockingOverlay())
        assertTrue(TvScreenId.ASPECT_MENU.isBlockingOverlay())
        assertTrue(TvScreenId.LIVE_LIST.isBlockingOverlay())
        assertTrue(TvScreenId.SLEEP_TIMER.isBlockingOverlay())
        assertTrue(TvScreenId.SPEED_DIALOG.isBlockingOverlay())
        assertTrue(TvScreenId.TRACKS_DIALOG.isBlockingOverlay())
        assertTrue(TvScreenId.ERROR_DIALOG.isBlockingOverlay())
        assertTrue(TvScreenId.PROFILE_GATE.isBlockingOverlay())
    }

    @Test
    fun `isBlockingOverlay returns false for non-blocking screens`() {
        assertFalse(TvScreenId.START.isBlockingOverlay())
        assertFalse(TvScreenId.LIBRARY.isBlockingOverlay())
        assertFalse(TvScreenId.PLAYER.isBlockingOverlay())
        assertFalse(TvScreenId.SETTINGS.isBlockingOverlay())
        assertFalse(TvScreenId.DETAIL.isBlockingOverlay())
        assertFalse(TvScreenId.SEARCH.isBlockingOverlay())
    }

    // ══════════════════════════════════════════════════════════════════
    // TvScreenContext DEFAULT VALUES TESTS
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `TvScreenContext has correct default values`() {
        val context = TvScreenContext(screenId = TvScreenId.LIBRARY)

        assertEquals(TvScreenId.LIBRARY, context.screenId)
        assertFalse(context.isPlayerScreen)
        assertFalse(context.isLive)
        assertFalse(context.isKidProfile)
        assertFalse(context.hasBlockingOverlay)
    }

    // ══════════════════════════════════════════════════════════════════
    // TvScreenContext FACTORY METHOD TESTS - player()
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `player factory creates context with PLAYER screen id`() {
        val context = TvScreenContext.player()
        assertEquals(TvScreenId.PLAYER, context.screenId)
    }

    @Test
    fun `player factory sets isPlayerScreen to true`() {
        val context = TvScreenContext.player()
        assertTrue(context.isPlayerScreen)
    }

    @Test
    fun `player factory accepts isLive parameter`() {
        val vodContext = TvScreenContext.player(isLive = false)
        val liveContext = TvScreenContext.player(isLive = true)

        assertFalse(vodContext.isLive)
        assertTrue(liveContext.isLive)
    }

    @Test
    fun `player factory accepts isKidProfile parameter`() {
        val normalContext = TvScreenContext.player(isKidProfile = false)
        val kidContext = TvScreenContext.player(isKidProfile = true)

        assertFalse(normalContext.isKidProfile)
        assertTrue(kidContext.isKidProfile)
    }

    @Test
    fun `player factory accepts hasBlockingOverlay parameter`() {
        val noOverlayContext = TvScreenContext.player(hasBlockingOverlay = false)
        val overlayContext = TvScreenContext.player(hasBlockingOverlay = true)

        assertFalse(noOverlayContext.hasBlockingOverlay)
        assertTrue(overlayContext.hasBlockingOverlay)
    }

    @Test
    fun `player factory creates context with all parameters`() {
        val context =
            TvScreenContext.player(
                isLive = true,
                isKidProfile = true,
                hasBlockingOverlay = true,
            )

        assertEquals(TvScreenId.PLAYER, context.screenId)
        assertTrue(context.isPlayerScreen)
        assertTrue(context.isLive)
        assertTrue(context.isKidProfile)
        assertTrue(context.hasBlockingOverlay)
    }

    // ══════════════════════════════════════════════════════════════════
    // TvScreenContext FACTORY METHOD TESTS - library()
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `library factory creates context with LIBRARY screen id`() {
        val context = TvScreenContext.library()
        assertEquals(TvScreenId.LIBRARY, context.screenId)
    }

    @Test
    fun `library factory sets isPlayerScreen to false`() {
        val context = TvScreenContext.library()
        assertFalse(context.isPlayerScreen)
    }

    @Test
    fun `library factory accepts isKidProfile parameter`() {
        val normalContext = TvScreenContext.library(isKidProfile = false)
        val kidContext = TvScreenContext.library(isKidProfile = true)

        assertFalse(normalContext.isKidProfile)
        assertTrue(kidContext.isKidProfile)
    }

    // ══════════════════════════════════════════════════════════════════
    // TvScreenContext FACTORY METHOD TESTS - settings()
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `settings factory creates context with SETTINGS screen id`() {
        val context = TvScreenContext.settings()
        assertEquals(TvScreenId.SETTINGS, context.screenId)
    }

    @Test
    fun `settings factory sets isPlayerScreen to false`() {
        val context = TvScreenContext.settings()
        assertFalse(context.isPlayerScreen)
    }

    @Test
    fun `settings factory accepts isKidProfile parameter`() {
        val kidContext = TvScreenContext.settings(isKidProfile = true)
        assertTrue(kidContext.isKidProfile)
    }

    // ══════════════════════════════════════════════════════════════════
    // TvScreenContext FACTORY METHOD TESTS - profileGate()
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `profileGate factory creates context with PROFILE_GATE screen id`() {
        val context = TvScreenContext.profileGate()
        assertEquals(TvScreenId.PROFILE_GATE, context.screenId)
    }

    @Test
    fun `profileGate factory sets hasBlockingOverlay to true`() {
        val context = TvScreenContext.profileGate()
        assertTrue(context.hasBlockingOverlay)
    }

    @Test
    fun `profileGate factory sets isKidProfile to false`() {
        val context = TvScreenContext.profileGate()
        assertFalse(context.isKidProfile)
    }

    // ══════════════════════════════════════════════════════════════════
    // TvScreenContext FACTORY METHOD TESTS - detail()
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `detail factory creates context with DETAIL screen id`() {
        val context = TvScreenContext.detail()
        assertEquals(TvScreenId.DETAIL, context.screenId)
    }

    @Test
    fun `detail factory accepts isKidProfile parameter`() {
        val kidContext = TvScreenContext.detail(isKidProfile = true)
        assertTrue(kidContext.isKidProfile)
    }

    // ══════════════════════════════════════════════════════════════════
    // TvScreenContext FACTORY METHOD TESTS - blockingOverlay()
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `blockingOverlay factory creates context with specified screen id`() {
        val ccContext = TvScreenContext.blockingOverlay(TvScreenId.CC_MENU)
        val liveListContext = TvScreenContext.blockingOverlay(TvScreenId.LIVE_LIST)

        assertEquals(TvScreenId.CC_MENU, ccContext.screenId)
        assertEquals(TvScreenId.LIVE_LIST, liveListContext.screenId)
    }

    @Test
    fun `blockingOverlay factory sets hasBlockingOverlay to true`() {
        val context = TvScreenContext.blockingOverlay(TvScreenId.CC_MENU)
        assertTrue(context.hasBlockingOverlay)
    }

    @Test
    fun `blockingOverlay factory accepts isKidProfile parameter`() {
        val kidContext = TvScreenContext.blockingOverlay(TvScreenId.CC_MENU, isKidProfile = true)
        assertTrue(kidContext.isKidProfile)
    }

    // ══════════════════════════════════════════════════════════════════
    // TvScreenContext FACTORY METHOD TESTS - unknown()
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `unknown factory creates context with UNKNOWN screen id`() {
        val context = TvScreenContext.unknown()
        assertEquals(TvScreenId.UNKNOWN, context.screenId)
    }

    @Test
    fun `unknown factory creates context with all false flags`() {
        val context = TvScreenContext.unknown()
        assertFalse(context.isPlayerScreen)
        assertFalse(context.isLive)
        assertFalse(context.isKidProfile)
        assertFalse(context.hasBlockingOverlay)
    }

    // ══════════════════════════════════════════════════════════════════
    // DATA CLASS EQUALITY TESTS
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `TvScreenContext equality works correctly`() {
        val context1 = TvScreenContext.player(isLive = true, isKidProfile = false)
        val context2 = TvScreenContext.player(isLive = true, isKidProfile = false)
        val context3 = TvScreenContext.player(isLive = false, isKidProfile = false)

        assertEquals(context1, context2)
        assertFalse(context1 == context3)
    }

    @Test
    fun `TvScreenContext copy works correctly`() {
        val original = TvScreenContext.player(isLive = true)
        val copied = original.copy(isKidProfile = true)

        assertTrue(original.isLive)
        assertFalse(original.isKidProfile)
        assertTrue(copied.isLive)
        assertTrue(copied.isKidProfile)
    }

    // ══════════════════════════════════════════════════════════════════
    // CONTRACT COMPLIANCE TESTS
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `all contract-required screen ids exist`() {
        // List of screen IDs required by contract
        val requiredScreenIds =
            listOf(
                "START",
                "LIBRARY",
                "PLAYER",
                "SETTINGS",
                "DETAIL",
                "PROFILE_GATE",
                "LIVE_LIST",
                "CC_MENU",
                "ASPECT_MENU",
                "SEARCH",
            )

        val enumNames = TvScreenId.entries.map { it.name }

        for (screenId in requiredScreenIds) {
            assertTrue("Required screen ID $screenId should exist", enumNames.contains(screenId))
        }
    }

    @Test
    fun `TvScreenContext has all required fields from contract`() {
        val context =
            TvScreenContext(
                screenId = TvScreenId.PLAYER,
                isPlayerScreen = true,
                isLive = true,
                isKidProfile = true,
                hasBlockingOverlay = true,
            )

        // Verify all fields are accessible (compile-time check, but explicit here for documentation)
        assertNotNull(context.screenId)
        assertNotNull(context.isPlayerScreen)
        assertNotNull(context.isLive)
        assertNotNull(context.isKidProfile)
        assertNotNull(context.hasBlockingOverlay)
    }
}
