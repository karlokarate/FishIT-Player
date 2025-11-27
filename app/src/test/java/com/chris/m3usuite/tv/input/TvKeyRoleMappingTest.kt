package com.chris.m3usuite.tv.input

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit tests for TvKeyRole mapping via TvKeyMapper.
 *
 * Tests verify:
 * - All keycodes map to correct TvKeyRole
 * - Unsupported keycodes return null
 * - Mapping is deterministic
 * - TvKeyRole helper methods work correctly
 *
 * Contract Reference: INTERNAL_PLAYER_TV_INPUT_CONTRACT_PHASE6.md Section 10.1
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TvKeyRoleMappingTest {
    // ══════════════════════════════════════════════════════════════════
    // DPAD NAVIGATION MAPPING TESTS
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `DPAD_UP keycode maps to DPAD_UP role`() {
        assertEquals(TvKeyRole.DPAD_UP, TvKeyMapper.mapKeyCode(KeyEvent.KEYCODE_DPAD_UP))
    }

    @Test
    fun `DPAD_DOWN keycode maps to DPAD_DOWN role`() {
        assertEquals(TvKeyRole.DPAD_DOWN, TvKeyMapper.mapKeyCode(KeyEvent.KEYCODE_DPAD_DOWN))
    }

    @Test
    fun `DPAD_LEFT keycode maps to DPAD_LEFT role`() {
        assertEquals(TvKeyRole.DPAD_LEFT, TvKeyMapper.mapKeyCode(KeyEvent.KEYCODE_DPAD_LEFT))
    }

    @Test
    fun `DPAD_RIGHT keycode maps to DPAD_RIGHT role`() {
        assertEquals(TvKeyRole.DPAD_RIGHT, TvKeyMapper.mapKeyCode(KeyEvent.KEYCODE_DPAD_RIGHT))
    }

    @Test
    fun `DPAD_CENTER keycode maps to DPAD_CENTER role`() {
        assertEquals(TvKeyRole.DPAD_CENTER, TvKeyMapper.mapKeyCode(KeyEvent.KEYCODE_DPAD_CENTER))
    }

    @Test
    fun `ENTER keycode maps to DPAD_CENTER role`() {
        assertEquals(TvKeyRole.DPAD_CENTER, TvKeyMapper.mapKeyCode(KeyEvent.KEYCODE_ENTER))
    }

    @Test
    fun `NUMPAD_ENTER keycode maps to DPAD_CENTER role`() {
        assertEquals(TvKeyRole.DPAD_CENTER, TvKeyMapper.mapKeyCode(KeyEvent.KEYCODE_NUMPAD_ENTER))
    }

    // ══════════════════════════════════════════════════════════════════
    // MEDIA KEY MAPPING TESTS
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `MEDIA_PLAY_PAUSE keycode maps to PLAY_PAUSE role`() {
        assertEquals(TvKeyRole.PLAY_PAUSE, TvKeyMapper.mapKeyCode(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE))
    }

    @Test
    fun `MEDIA_PLAY keycode maps to PLAY_PAUSE role`() {
        assertEquals(TvKeyRole.PLAY_PAUSE, TvKeyMapper.mapKeyCode(KeyEvent.KEYCODE_MEDIA_PLAY))
    }

    @Test
    fun `MEDIA_PAUSE keycode maps to PLAY_PAUSE role`() {
        assertEquals(TvKeyRole.PLAY_PAUSE, TvKeyMapper.mapKeyCode(KeyEvent.KEYCODE_MEDIA_PAUSE))
    }

    @Test
    fun `MEDIA_FAST_FORWARD keycode maps to FAST_FORWARD role`() {
        assertEquals(TvKeyRole.FAST_FORWARD, TvKeyMapper.mapKeyCode(KeyEvent.KEYCODE_MEDIA_FAST_FORWARD))
    }

    @Test
    fun `MEDIA_SKIP_FORWARD keycode maps to FAST_FORWARD role`() {
        assertEquals(TvKeyRole.FAST_FORWARD, TvKeyMapper.mapKeyCode(KeyEvent.KEYCODE_MEDIA_SKIP_FORWARD))
    }

    @Test
    fun `MEDIA_REWIND keycode maps to REWIND role`() {
        assertEquals(TvKeyRole.REWIND, TvKeyMapper.mapKeyCode(KeyEvent.KEYCODE_MEDIA_REWIND))
    }

    @Test
    fun `MEDIA_SKIP_BACKWARD keycode maps to REWIND role`() {
        assertEquals(TvKeyRole.REWIND, TvKeyMapper.mapKeyCode(KeyEvent.KEYCODE_MEDIA_SKIP_BACKWARD))
    }

    // ══════════════════════════════════════════════════════════════════
    // MENU & NAVIGATION KEY MAPPING TESTS
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `MENU keycode maps to MENU role`() {
        assertEquals(TvKeyRole.MENU, TvKeyMapper.mapKeyCode(KeyEvent.KEYCODE_MENU))
    }

    @Test
    fun `SETTINGS keycode maps to MENU role`() {
        assertEquals(TvKeyRole.MENU, TvKeyMapper.mapKeyCode(KeyEvent.KEYCODE_SETTINGS))
    }

    @Test
    fun `BACK keycode maps to BACK role`() {
        assertEquals(TvKeyRole.BACK, TvKeyMapper.mapKeyCode(KeyEvent.KEYCODE_BACK))
    }

    @Test
    fun `ESCAPE keycode maps to BACK role`() {
        assertEquals(TvKeyRole.BACK, TvKeyMapper.mapKeyCode(KeyEvent.KEYCODE_ESCAPE))
    }

    // ══════════════════════════════════════════════════════════════════
    // CHANNEL KEY MAPPING TESTS
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `CHANNEL_UP keycode maps to CHANNEL_UP role`() {
        assertEquals(TvKeyRole.CHANNEL_UP, TvKeyMapper.mapKeyCode(KeyEvent.KEYCODE_CHANNEL_UP))
    }

    @Test
    fun `PAGE_UP keycode maps to CHANNEL_UP role`() {
        assertEquals(TvKeyRole.CHANNEL_UP, TvKeyMapper.mapKeyCode(KeyEvent.KEYCODE_PAGE_UP))
    }

    @Test
    fun `CHANNEL_DOWN keycode maps to CHANNEL_DOWN role`() {
        assertEquals(TvKeyRole.CHANNEL_DOWN, TvKeyMapper.mapKeyCode(KeyEvent.KEYCODE_CHANNEL_DOWN))
    }

    @Test
    fun `PAGE_DOWN keycode maps to CHANNEL_DOWN role`() {
        assertEquals(TvKeyRole.CHANNEL_DOWN, TvKeyMapper.mapKeyCode(KeyEvent.KEYCODE_PAGE_DOWN))
    }

    // ══════════════════════════════════════════════════════════════════
    // INFO KEY MAPPING TESTS
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `INFO keycode maps to INFO role`() {
        assertEquals(TvKeyRole.INFO, TvKeyMapper.mapKeyCode(KeyEvent.KEYCODE_INFO))
    }

    @Test
    fun `GUIDE keycode maps to GUIDE role`() {
        assertEquals(TvKeyRole.GUIDE, TvKeyMapper.mapKeyCode(KeyEvent.KEYCODE_GUIDE))
    }

    // ══════════════════════════════════════════════════════════════════
    // NUMBER KEY MAPPING TESTS
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `number key 0 maps to NUM_0 role`() {
        assertEquals(TvKeyRole.NUM_0, TvKeyMapper.mapKeyCode(KeyEvent.KEYCODE_0))
        assertEquals(TvKeyRole.NUM_0, TvKeyMapper.mapKeyCode(KeyEvent.KEYCODE_NUMPAD_0))
    }

    @Test
    fun `number key 1 maps to NUM_1 role`() {
        assertEquals(TvKeyRole.NUM_1, TvKeyMapper.mapKeyCode(KeyEvent.KEYCODE_1))
        assertEquals(TvKeyRole.NUM_1, TvKeyMapper.mapKeyCode(KeyEvent.KEYCODE_NUMPAD_1))
    }

    @Test
    fun `number key 2 maps to NUM_2 role`() {
        assertEquals(TvKeyRole.NUM_2, TvKeyMapper.mapKeyCode(KeyEvent.KEYCODE_2))
        assertEquals(TvKeyRole.NUM_2, TvKeyMapper.mapKeyCode(KeyEvent.KEYCODE_NUMPAD_2))
    }

    @Test
    fun `number key 3 maps to NUM_3 role`() {
        assertEquals(TvKeyRole.NUM_3, TvKeyMapper.mapKeyCode(KeyEvent.KEYCODE_3))
        assertEquals(TvKeyRole.NUM_3, TvKeyMapper.mapKeyCode(KeyEvent.KEYCODE_NUMPAD_3))
    }

    @Test
    fun `number key 4 maps to NUM_4 role`() {
        assertEquals(TvKeyRole.NUM_4, TvKeyMapper.mapKeyCode(KeyEvent.KEYCODE_4))
        assertEquals(TvKeyRole.NUM_4, TvKeyMapper.mapKeyCode(KeyEvent.KEYCODE_NUMPAD_4))
    }

    @Test
    fun `number key 5 maps to NUM_5 role`() {
        assertEquals(TvKeyRole.NUM_5, TvKeyMapper.mapKeyCode(KeyEvent.KEYCODE_5))
        assertEquals(TvKeyRole.NUM_5, TvKeyMapper.mapKeyCode(KeyEvent.KEYCODE_NUMPAD_5))
    }

    @Test
    fun `number key 6 maps to NUM_6 role`() {
        assertEquals(TvKeyRole.NUM_6, TvKeyMapper.mapKeyCode(KeyEvent.KEYCODE_6))
        assertEquals(TvKeyRole.NUM_6, TvKeyMapper.mapKeyCode(KeyEvent.KEYCODE_NUMPAD_6))
    }

    @Test
    fun `number key 7 maps to NUM_7 role`() {
        assertEquals(TvKeyRole.NUM_7, TvKeyMapper.mapKeyCode(KeyEvent.KEYCODE_7))
        assertEquals(TvKeyRole.NUM_7, TvKeyMapper.mapKeyCode(KeyEvent.KEYCODE_NUMPAD_7))
    }

    @Test
    fun `number key 8 maps to NUM_8 role`() {
        assertEquals(TvKeyRole.NUM_8, TvKeyMapper.mapKeyCode(KeyEvent.KEYCODE_8))
        assertEquals(TvKeyRole.NUM_8, TvKeyMapper.mapKeyCode(KeyEvent.KEYCODE_NUMPAD_8))
    }

    @Test
    fun `number key 9 maps to NUM_9 role`() {
        assertEquals(TvKeyRole.NUM_9, TvKeyMapper.mapKeyCode(KeyEvent.KEYCODE_9))
        assertEquals(TvKeyRole.NUM_9, TvKeyMapper.mapKeyCode(KeyEvent.KEYCODE_NUMPAD_9))
    }

    // ══════════════════════════════════════════════════════════════════
    // UNSUPPORTED KEYCODES TESTS
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `unsupported keycode returns null`() {
        assertNull(TvKeyMapper.mapKeyCode(KeyEvent.KEYCODE_A))
        assertNull(TvKeyMapper.mapKeyCode(KeyEvent.KEYCODE_B))
        assertNull(TvKeyMapper.mapKeyCode(KeyEvent.KEYCODE_SPACE))
        assertNull(TvKeyMapper.mapKeyCode(KeyEvent.KEYCODE_VOLUME_UP))
        assertNull(TvKeyMapper.mapKeyCode(KeyEvent.KEYCODE_VOLUME_DOWN))
        assertNull(TvKeyMapper.mapKeyCode(KeyEvent.KEYCODE_POWER))
        assertNull(TvKeyMapper.mapKeyCode(-1))
        assertNull(TvKeyMapper.mapKeyCode(999999))
    }

    // ══════════════════════════════════════════════════════════════════
    // DETERMINISTIC MAPPING TESTS
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `mapping is deterministic - same input produces same output`() {
        repeat(100) {
            assertEquals(TvKeyRole.DPAD_UP, TvKeyMapper.mapKeyCode(KeyEvent.KEYCODE_DPAD_UP))
            assertEquals(TvKeyRole.PLAY_PAUSE, TvKeyMapper.mapKeyCode(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE))
            assertEquals(TvKeyRole.BACK, TvKeyMapper.mapKeyCode(KeyEvent.KEYCODE_BACK))
        }
    }

    // ══════════════════════════════════════════════════════════════════
    // KeyEvent MAPPING TESTS
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `map function accepts KeyEvent and returns correct role`() {
        val event = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_UP)
        assertEquals(TvKeyRole.DPAD_UP, TvKeyMapper.map(event))
    }

    @Test
    fun `mapDebounced function accepts KeyEvent and returns correct role`() {
        val event = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
        assertEquals(TvKeyRole.PLAY_PAUSE, TvKeyMapper.mapDebounced(event))
    }

    // ══════════════════════════════════════════════════════════════════
    // isSupported TESTS
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `isSupported returns true for supported keycodes`() {
        assertTrue(TvKeyMapper.isSupported(KeyEvent.KEYCODE_DPAD_UP))
        assertTrue(TvKeyMapper.isSupported(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE))
        assertTrue(TvKeyMapper.isSupported(KeyEvent.KEYCODE_BACK))
        assertTrue(TvKeyMapper.isSupported(KeyEvent.KEYCODE_0))
    }

    @Test
    fun `isSupported returns false for unsupported keycodes`() {
        assertFalse(TvKeyMapper.isSupported(KeyEvent.KEYCODE_A))
        assertFalse(TvKeyMapper.isSupported(KeyEvent.KEYCODE_VOLUME_UP))
        assertFalse(TvKeyMapper.isSupported(-1))
    }

    // ══════════════════════════════════════════════════════════════════
    // TvKeyRole ENUM COMPLETENESS TESTS
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `TvKeyRole enum contains all required DPAD roles`() {
        assertNotNull(TvKeyRole.DPAD_UP)
        assertNotNull(TvKeyRole.DPAD_DOWN)
        assertNotNull(TvKeyRole.DPAD_LEFT)
        assertNotNull(TvKeyRole.DPAD_RIGHT)
        assertNotNull(TvKeyRole.DPAD_CENTER)
    }

    @Test
    fun `TvKeyRole enum contains all required media roles`() {
        assertNotNull(TvKeyRole.PLAY_PAUSE)
        assertNotNull(TvKeyRole.FAST_FORWARD)
        assertNotNull(TvKeyRole.REWIND)
    }

    @Test
    fun `TvKeyRole enum contains all required menu roles`() {
        assertNotNull(TvKeyRole.MENU)
        assertNotNull(TvKeyRole.BACK)
    }

    @Test
    fun `TvKeyRole enum contains all required channel roles`() {
        assertNotNull(TvKeyRole.CHANNEL_UP)
        assertNotNull(TvKeyRole.CHANNEL_DOWN)
    }

    @Test
    fun `TvKeyRole enum contains all required info roles`() {
        assertNotNull(TvKeyRole.INFO)
        assertNotNull(TvKeyRole.GUIDE)
    }

    @Test
    fun `TvKeyRole enum contains all required number roles`() {
        assertNotNull(TvKeyRole.NUM_0)
        assertNotNull(TvKeyRole.NUM_1)
        assertNotNull(TvKeyRole.NUM_2)
        assertNotNull(TvKeyRole.NUM_3)
        assertNotNull(TvKeyRole.NUM_4)
        assertNotNull(TvKeyRole.NUM_5)
        assertNotNull(TvKeyRole.NUM_6)
        assertNotNull(TvKeyRole.NUM_7)
        assertNotNull(TvKeyRole.NUM_8)
        assertNotNull(TvKeyRole.NUM_9)
    }

    @Test
    fun `TvKeyRole enum has exactly 24 values`() {
        // 5 DPAD + 3 Media + 2 Menu + 2 Channel + 2 Info + 10 Numbers = 24 total
        assertEquals(24, TvKeyRole.entries.size)
    }

    // ══════════════════════════════════════════════════════════════════
    // TvKeyRole HELPER METHOD TESTS
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `isDpad returns true for DPAD roles`() {
        with(TvKeyRole.Companion) {
            assertTrue(TvKeyRole.DPAD_UP.isDpad())
            assertTrue(TvKeyRole.DPAD_DOWN.isDpad())
            assertTrue(TvKeyRole.DPAD_LEFT.isDpad())
            assertTrue(TvKeyRole.DPAD_RIGHT.isDpad())
            assertTrue(TvKeyRole.DPAD_CENTER.isDpad())
        }
    }

    @Test
    fun `isDpad returns false for non-DPAD roles`() {
        with(TvKeyRole.Companion) {
            assertFalse(TvKeyRole.PLAY_PAUSE.isDpad())
            assertFalse(TvKeyRole.MENU.isDpad())
            assertFalse(TvKeyRole.NUM_0.isDpad())
        }
    }

    @Test
    fun `isMediaKey returns true for media roles`() {
        with(TvKeyRole.Companion) {
            assertTrue(TvKeyRole.PLAY_PAUSE.isMediaKey())
            assertTrue(TvKeyRole.FAST_FORWARD.isMediaKey())
            assertTrue(TvKeyRole.REWIND.isMediaKey())
        }
    }

    @Test
    fun `isMediaKey returns false for non-media roles`() {
        with(TvKeyRole.Companion) {
            assertFalse(TvKeyRole.DPAD_UP.isMediaKey())
            assertFalse(TvKeyRole.MENU.isMediaKey())
            assertFalse(TvKeyRole.NUM_0.isMediaKey())
        }
    }

    @Test
    fun `isNumberKey returns true for number roles`() {
        with(TvKeyRole.Companion) {
            assertTrue(TvKeyRole.NUM_0.isNumberKey())
            assertTrue(TvKeyRole.NUM_5.isNumberKey())
            assertTrue(TvKeyRole.NUM_9.isNumberKey())
        }
    }

    @Test
    fun `isNumberKey returns false for non-number roles`() {
        with(TvKeyRole.Companion) {
            assertFalse(TvKeyRole.DPAD_UP.isNumberKey())
            assertFalse(TvKeyRole.PLAY_PAUSE.isNumberKey())
            assertFalse(TvKeyRole.MENU.isNumberKey())
        }
    }

    @Test
    fun `toDigit returns correct values for number keys`() {
        with(TvKeyRole.Companion) {
            assertEquals(0, TvKeyRole.NUM_0.toDigit())
            assertEquals(1, TvKeyRole.NUM_1.toDigit())
            assertEquals(2, TvKeyRole.NUM_2.toDigit())
            assertEquals(3, TvKeyRole.NUM_3.toDigit())
            assertEquals(4, TvKeyRole.NUM_4.toDigit())
            assertEquals(5, TvKeyRole.NUM_5.toDigit())
            assertEquals(6, TvKeyRole.NUM_6.toDigit())
            assertEquals(7, TvKeyRole.NUM_7.toDigit())
            assertEquals(8, TvKeyRole.NUM_8.toDigit())
            assertEquals(9, TvKeyRole.NUM_9.toDigit())
        }
    }

    @Test
    fun `toDigit returns null for non-number keys`() {
        with(TvKeyRole.Companion) {
            assertNull(TvKeyRole.DPAD_UP.toDigit())
            assertNull(TvKeyRole.PLAY_PAUSE.toDigit())
            assertNull(TvKeyRole.MENU.toDigit())
        }
    }
}
