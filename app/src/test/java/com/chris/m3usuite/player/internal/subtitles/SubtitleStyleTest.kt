package com.chris.m3usuite.player.internal.subtitles

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for SubtitleStyle and related domain models.
 *
 * Contract Reference: `INTERNAL_PLAYER_SUBTITLE_CC_CONTRACT_PHASE4.md` Section 4
 */
class SubtitleStyleTest {

    @Test
    fun `default values match contract`() {
        val style = SubtitleStyle()

        assertEquals(1.0f, style.textScale, 0.001f)
        assertEquals(0xFFFFFFFF.toInt(), style.foregroundColor)
        assertEquals(0x99000000.toInt(), style.backgroundColor)
        assertEquals(1.0f, style.foregroundOpacity, 0.001f)
        assertEquals(0.6f, style.backgroundOpacity, 0.001f)
        assertEquals(EdgeStyle.OUTLINE, style.edgeStyle)
    }

    @Test
    fun `textScale range validation`() {
        // Valid ranges
        assertTrue(SubtitleStyle(textScale = 0.5f).isValid())
        assertTrue(SubtitleStyle(textScale = 1.0f).isValid())
        assertTrue(SubtitleStyle(textScale = 2.0f).isValid())

        // Invalid ranges throw
        try {
            SubtitleStyle(textScale = 0.4f)
            throw AssertionError("Should have thrown for textScale < 0.5")
        } catch (e: IllegalArgumentException) {
            // Expected
        }

        try {
            SubtitleStyle(textScale = 2.1f)
            throw AssertionError("Should have thrown for textScale > 2.0")
        } catch (e: IllegalArgumentException) {
            // Expected
        }
    }

    @Test
    fun `opacity range validation`() {
        // Valid opacity ranges
        assertTrue(SubtitleStyle(foregroundOpacity = 0.5f).isValid())
        assertTrue(SubtitleStyle(foregroundOpacity = 1.0f).isValid())
        assertTrue(SubtitleStyle(backgroundOpacity = 0.0f).isValid())
        assertTrue(SubtitleStyle(backgroundOpacity = 1.0f).isValid())

        // Invalid ranges throw
        try {
            SubtitleStyle(foregroundOpacity = 0.4f)
            throw AssertionError("Should have thrown for foregroundOpacity < 0.5")
        } catch (e: IllegalArgumentException) {
            // Expected
        }

        try {
            SubtitleStyle(backgroundOpacity = 1.1f)
            throw AssertionError("Should have thrown for backgroundOpacity > 1.0")
        } catch (e: IllegalArgumentException) {
            // Expected
        }
    }

    @Test
    fun `preset conversion produces valid styles`() {
        // All presets must produce valid styles
        SubtitlePreset.values().forEach { preset ->
            val style = preset.toStyle()
            assertTrue("Preset $preset produced invalid style", style.isValid())
        }
    }

    @Test
    fun `default preset matches contract defaults`() {
        val style = SubtitlePreset.DEFAULT.toStyle()

        assertEquals(1.0f, style.textScale, 0.001f)
        assertEquals(0xFFFFFFFF.toInt(), style.foregroundColor)
        assertEquals(0x99000000.toInt(), style.backgroundColor)
    }

    @Test
    fun `high contrast preset has maximum readability`() {
        val style = SubtitlePreset.HIGH_CONTRAST.toStyle()

        assertEquals(0xFFFFFF00.toInt(), style.foregroundColor) // Yellow
        assertEquals(0xFF000000.toInt(), style.backgroundColor) // Solid black
        assertEquals(1.0f, style.foregroundOpacity, 0.001f)
        assertEquals(1.0f, style.backgroundOpacity, 0.001f)
    }

    @Test
    fun `tv large preset has increased scale`() {
        val style = SubtitlePreset.TV_LARGE.toStyle()

        assertEquals(1.5f, style.textScale, 0.001f)
        assertEquals(EdgeStyle.OUTLINE, style.edgeStyle)
    }

    @Test
    fun `minimal preset has reduced visibility`() {
        val style = SubtitlePreset.MINIMAL.toStyle()

        assertEquals(0.8f, style.textScale, 0.001f)
        assertEquals(0.3f, style.backgroundOpacity, 0.001f)
    }

    @Test
    fun `edge style enum has all required values`() {
        val values = EdgeStyle.values()

        assertTrue(values.contains(EdgeStyle.NONE))
        assertTrue(values.contains(EdgeStyle.OUTLINE))
        assertTrue(values.contains(EdgeStyle.SHADOW))
        assertTrue(values.contains(EdgeStyle.GLOW))
    }

    @Test
    fun `data class copy works correctly`() {
        val original = SubtitleStyle(textScale = 1.2f, edgeStyle = EdgeStyle.SHADOW)
        val copy = original.copy(textScale = 1.5f)

        assertEquals(1.5f, copy.textScale, 0.001f)
        assertEquals(EdgeStyle.SHADOW, copy.edgeStyle)
        assertEquals(original.foregroundColor, copy.foregroundColor)
    }
}
