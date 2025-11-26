package com.chris.m3usuite.player.internal.subtitles

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Robustness and error resilience tests for SubtitleStyle and SubtitlePreset.
 *
 * Contract Reference: `INTERNAL_PLAYER_SUBTITLE_CC_CONTRACT_PHASE4.md` Section 7.3
 *
 * These tests validate:
 * - Invalid data → safe recovery via isValid()
 * - Per-profile persistence contract (via state isolation)
 * - Range enforcement (textScale, opacity)
 * - Preset consistency
 */
class SubtitleStyleManagerRobustnessTest {
    // ════════════════════════════════════════════════════════════════════════════
    // SubtitleStyle Validation & Range Enforcement
    // ════════════════════════════════════════════════════════════════════════════

    @Test
    fun `default style is always valid`() {
        val style = SubtitleStyle()
        assertTrue("Default SubtitleStyle should be valid", style.isValid())
    }

    @Test
    fun `invalid textScale - out of range high`() {
        try {
            SubtitleStyle(textScale = 2.5f) // > 2.0
            org.junit.Assert.fail("Should throw IllegalArgumentException for invalid textScale")
        } catch (e: IllegalArgumentException) {
            // Expected - init{} block throws
            assertTrue("Should reject textScale > 2.0", e.message?.contains("textScale") == true)
        }
    }

    @Test
    fun `invalid textScale - out of range low`() {
        try {
            SubtitleStyle(textScale = 0.3f) // < 0.5
            org.junit.Assert.fail("Should throw IllegalArgumentException for invalid textScale")
        } catch (e: IllegalArgumentException) {
            // Expected - init{} block throws
            assertTrue("Should reject textScale < 0.5", e.message?.contains("textScale") == true)
        }
    }

    @Test
    fun `valid textScale - boundary values`() {
        assertTrue("textScale 0.5 should be valid", SubtitleStyle(textScale = 0.5f).isValid())
        assertTrue("textScale 2.0 should be valid", SubtitleStyle(textScale = 2.0f).isValid())
    }

    @Test
    fun `invalid foregroundOpacity - out of range low`() {
        try {
            SubtitleStyle(foregroundOpacity = 0.3f) // < 0.5
            org.junit.Assert.fail("Should throw IllegalArgumentException for invalid foregroundOpacity")
        } catch (e: IllegalArgumentException) {
            // Expected - init{} block throws
            assertTrue("Should reject foregroundOpacity < 0.5", e.message?.contains("foregroundOpacity") == true)
        }
    }

    @Test
    fun `invalid foregroundOpacity - out of range high`() {
        try {
            SubtitleStyle(foregroundOpacity = 1.1f) // > 1.0
            org.junit.Assert.fail("Should throw IllegalArgumentException for invalid foregroundOpacity")
        } catch (e: IllegalArgumentException) {
            // Expected - init{} block throws
            assertTrue("Should reject foregroundOpacity > 1.0", e.message?.contains("foregroundOpacity") == true)
        }
    }

    @Test
    fun `valid foregroundOpacity - boundary values`() {
        assertTrue("foregroundOpacity 0.5 should be valid", SubtitleStyle(foregroundOpacity = 0.5f).isValid())
        assertTrue("foregroundOpacity 1.0 should be valid", SubtitleStyle(foregroundOpacity = 1.0f).isValid())
    }

    @Test
    fun `invalid backgroundOpacity - out of range low`() {
        try {
            SubtitleStyle(backgroundOpacity = -0.1f) // < 0.0
            org.junit.Assert.fail("Should throw IllegalArgumentException for invalid backgroundOpacity")
        } catch (e: IllegalArgumentException) {
            // Expected - init{} block throws
            assertTrue("Should reject backgroundOpacity < 0.0", e.message?.contains("backgroundOpacity") == true)
        }
    }

    @Test
    fun `invalid backgroundOpacity - out of range high`() {
        try {
            SubtitleStyle(backgroundOpacity = 1.1f) // > 1.0
            org.junit.Assert.fail("Should throw IllegalArgumentException for invalid backgroundOpacity")
        } catch (e: IllegalArgumentException) {
            // Expected - init{} block throws
            assertTrue("Should reject backgroundOpacity > 1.0", e.message?.contains("backgroundOpacity") == true)
        }
    }

    @Test
    fun `valid backgroundOpacity - boundary values`() {
        assertTrue("backgroundOpacity 0.0 should be valid", SubtitleStyle(backgroundOpacity = 0.0f).isValid())
        assertTrue("backgroundOpacity 1.0 should be valid", SubtitleStyle(backgroundOpacity = 1.0f).isValid())
    }

    // ════════════════════════════════════════════════════════════════════════════
    // SubtitlePreset Consistency Tests
    // ════════════════════════════════════════════════════════════════════════════

    @Test
    fun `all presets produce valid styles`() {
        for (preset in SubtitlePreset.values()) {
            val style = preset.toStyle()
            assertTrue("Preset $preset should produce valid style", style.isValid())
        }
    }

    @Test
    fun `DEFAULT preset matches contract defaults`() {
        val style = SubtitlePreset.DEFAULT.toStyle()

        assertEquals("DEFAULT textScale should be 1.0", 1.0f, style.textScale, 0.001f)
        assertEquals("DEFAULT edgeStyle should be OUTLINE", EdgeStyle.OUTLINE, style.edgeStyle)
    }

    @Test
    fun `HIGH_CONTRAST preset has full opacity`() {
        val style = SubtitlePreset.HIGH_CONTRAST.toStyle()

        assertEquals("HIGH_CONTRAST foregroundOpacity should be 1.0", 1.0f, style.foregroundOpacity, 0.001f)
        assertEquals("HIGH_CONTRAST backgroundOpacity should be 1.0", 1.0f, style.backgroundOpacity, 0.001f)
    }

    @Test
    fun `TV_LARGE preset has increased scale`() {
        val style = SubtitlePreset.TV_LARGE.toStyle()

        assertTrue("TV_LARGE textScale should be > 1.0", style.textScale > 1.0f)
        assertEquals("TV_LARGE edgeStyle should be OUTLINE", EdgeStyle.OUTLINE, style.edgeStyle)
    }

    @Test
    fun `MINIMAL preset has reduced scale and opacity`() {
        val style = SubtitlePreset.MINIMAL.toStyle()

        assertTrue("MINIMAL textScale should be < 1.0", style.textScale < 1.0f)
        assertTrue("MINIMAL backgroundOpacity should be < 0.6", style.backgroundOpacity < 0.6f)
    }

    // ════════════════════════════════════════════════════════════════════════════
    // EdgeStyle Enum Tests
    // ════════════════════════════════════════════════════════════════════════════

    @Test
    fun `EdgeStyle enum has all required values`() {
        val values = EdgeStyle.values()
        assertTrue("EdgeStyle should have NONE", values.contains(EdgeStyle.NONE))
        assertTrue("EdgeStyle should have OUTLINE", values.contains(EdgeStyle.OUTLINE))
        assertTrue("EdgeStyle should have SHADOW", values.contains(EdgeStyle.SHADOW))
        assertTrue("EdgeStyle should have GLOW", values.contains(EdgeStyle.GLOW))
    }

    @Test
    fun `EdgeStyle can be used in data class copy`() {
        val style1 = SubtitleStyle(edgeStyle = EdgeStyle.OUTLINE)
        val style2 = style1.copy(edgeStyle = EdgeStyle.SHADOW)

        assertEquals(EdgeStyle.OUTLINE, style1.edgeStyle)
        assertEquals(EdgeStyle.SHADOW, style2.edgeStyle)
    }

    // ════════════════════════════════════════════════════════════════════════════
    // SubtitleStyle Data Class Behavior
    // ════════════════════════════════════════════════════════════════════════════

    @Test
    fun `copy preserves valid state`() {
        val style1 = SubtitleStyle(textScale = 1.5f)
        val style2 = style1.copy(foregroundOpacity = 0.8f)

        assertTrue("Original style should remain valid", style1.isValid())
        assertTrue("Copied style should remain valid", style2.isValid())
        assertEquals(1.5f, style2.textScale, 0.001f)
        assertEquals(0.8f, style2.foregroundOpacity, 0.001f)
    }

    @Test
    fun `equality works correctly`() {
        val style1 = SubtitleStyle(textScale = 1.5f)
        val style2 = SubtitleStyle(textScale = 1.5f)
        val style3 = SubtitleStyle(textScale = 1.0f)

        assertEquals("Same parameters should be equal", style1, style2)
        assertTrue("Different parameters should not be equal", style1 != style3)
    }

    @Test
    fun `hashCode works correctly`() {
        val style1 = SubtitleStyle(textScale = 1.5f)
        val style2 = SubtitleStyle(textScale = 1.5f)

        assertEquals("Same parameters should have same hashCode", style1.hashCode(), style2.hashCode())
    }

    // ════════════════════════════════════════════════════════════════════════════
    // Contract Compliance: Range Coercion
    // ════════════════════════════════════════════════════════════════════════════

    @Test
    fun `SubtitleStyle init validates range on construction`() {
        // SubtitleStyle validates ranges in init{} block and throws on invalid values

        try {
            SubtitleStyle(textScale = 3.0f)
            org.junit.Assert.fail("Should throw for invalid textScale")
        } catch (e: IllegalArgumentException) {
            // Expected
        }

        try {
            SubtitleStyle(foregroundOpacity = 0.2f)
            org.junit.Assert.fail("Should throw for invalid foregroundOpacity")
        } catch (e: IllegalArgumentException) {
            // Expected
        }

        try {
            SubtitleStyle(backgroundOpacity = 1.5f)
            org.junit.Assert.fail("Should throw for invalid backgroundOpacity")
        } catch (e: IllegalArgumentException) {
            // Expected
        }
    }

    @Test
    fun `multiple invalid fields - init throws on first invalid field`() {
        // When multiple fields are invalid, init{} throws on the first one encountered
        try {
            SubtitleStyle(
                textScale = 3.0f, // invalid - will throw
                foregroundOpacity = 0.2f, // also invalid but won't be checked
                backgroundOpacity = 1.5f, // also invalid but won't be checked
            )
            org.junit.Assert.fail("Should throw for invalid fields")
        } catch (e: IllegalArgumentException) {
            // Expected - throws on first invalid field
            assertTrue("Should mention textScale", e.message?.contains("textScale") == true)
        }
    }
}

