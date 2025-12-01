package com.chris.m3usuite.player.internal.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for PlayerUiMode detection logic.
 *
 * Tests the device type detection used for responsive player overlay layout.
 *
 * **Note:** TV detection is delegated to FocusKit.isTvDevice(), which is the
 * centralized method used app-wide. These tests verify the PlayerUiMode logic
 * that wraps the TV detection result with screen width classification.
 */
class PlayerUiModeTest {
    // ════════════════════════════════════════════════════════════════════════════
    // TV Detection Tests
    // ════════════════════════════════════════════════════════════════════════════

    @Test
    fun `detectPlayerUiMode returns TV when isTvDevice is true`() {
        val result = detectPlayerUiMode(
            isTvDevice = true,
            screenWidthDp = 320,
        )

        assertEquals("TV device should be detected as TV", PlayerUiMode.TV, result)
    }

    @Test
    fun `detectPlayerUiMode returns TV regardless of screen width when isTvDevice is true`() {
        // Even with a large screen width, TV should take precedence
        val result = detectPlayerUiMode(
            isTvDevice = true,
            screenWidthDp = 1920,
        )

        assertEquals("TV should take precedence over screen width", PlayerUiMode.TV, result)
    }

    @Test
    fun `detectPlayerUiMode returns TV with small screen when isTvDevice is true`() {
        val result = detectPlayerUiMode(
            isTvDevice = true,
            screenWidthDp = 320,
        )

        assertEquals("TV detection should not depend on screen width", PlayerUiMode.TV, result)
    }

    // ════════════════════════════════════════════════════════════════════════════
    // Tablet Detection Tests
    // ════════════════════════════════════════════════════════════════════════════

    @Test
    fun `detectPlayerUiMode returns TABLET when screenWidthDp equals threshold`() {
        val result = detectPlayerUiMode(
            isTvDevice = false,
            screenWidthDp = TABLET_WIDTH_THRESHOLD_DP,
        )

        assertEquals("Screen width at threshold should be detected as TABLET", PlayerUiMode.TABLET, result)
    }

    @Test
    fun `detectPlayerUiMode returns TABLET when screenWidthDp exceeds threshold`() {
        val result = detectPlayerUiMode(
            isTvDevice = false,
            screenWidthDp = 800,
        )

        assertEquals("Screen width above threshold should be detected as TABLET", PlayerUiMode.TABLET, result)
    }

    @Test
    fun `detectPlayerUiMode returns TABLET for large tablets`() {
        val result = detectPlayerUiMode(
            isTvDevice = false,
            screenWidthDp = 1024,
        )

        assertEquals("Large tablet should be detected as TABLET", PlayerUiMode.TABLET, result)
    }

    // ════════════════════════════════════════════════════════════════════════════
    // Phone Detection Tests
    // ════════════════════════════════════════════════════════════════════════════

    @Test
    fun `detectPlayerUiMode returns PHONE when screenWidthDp below threshold`() {
        val result = detectPlayerUiMode(
            isTvDevice = false,
            screenWidthDp = 320,
        )

        assertEquals("Screen width below threshold should be detected as PHONE", PlayerUiMode.PHONE, result)
    }

    @Test
    fun `detectPlayerUiMode returns PHONE for typical phone screen width`() {
        val result = detectPlayerUiMode(
            isTvDevice = false,
            screenWidthDp = 360,
        )

        assertEquals("Typical phone screen should be detected as PHONE", PlayerUiMode.PHONE, result)
    }

    @Test
    fun `detectPlayerUiMode returns PHONE for large phone screen width`() {
        val result = detectPlayerUiMode(
            isTvDevice = false,
            screenWidthDp = 480,
        )

        assertEquals("Large phone screen should be detected as PHONE", PlayerUiMode.PHONE, result)
    }

    @Test
    fun `detectPlayerUiMode returns PHONE when just below threshold`() {
        val result = detectPlayerUiMode(
            isTvDevice = false,
            screenWidthDp = TABLET_WIDTH_THRESHOLD_DP - 1,
        )

        assertEquals("Screen width just below threshold should be PHONE", PlayerUiMode.PHONE, result)
    }

    // ════════════════════════════════════════════════════════════════════════════
    // Edge Case Tests
    // ════════════════════════════════════════════════════════════════════════════

    @Test
    fun `detectPlayerUiMode handles zero screen width as PHONE`() {
        val result = detectPlayerUiMode(
            isTvDevice = false,
            screenWidthDp = 0,
        )

        assertEquals("Zero screen width should be detected as PHONE", PlayerUiMode.PHONE, result)
    }

    @Test
    fun `detectPlayerUiMode handles negative screen width as PHONE`() {
        val result = detectPlayerUiMode(
            isTvDevice = false,
            screenWidthDp = -1,
        )

        assertEquals("Negative screen width should be detected as PHONE", PlayerUiMode.PHONE, result)
    }

    // ════════════════════════════════════════════════════════════════════════════
    // Constant Value Tests
    // ════════════════════════════════════════════════════════════════════════════

    @Test
    fun `TABLET_WIDTH_THRESHOLD_DP is 600`() {
        assertEquals("Tablet threshold should be 600dp", 600, TABLET_WIDTH_THRESHOLD_DP)
    }
}
