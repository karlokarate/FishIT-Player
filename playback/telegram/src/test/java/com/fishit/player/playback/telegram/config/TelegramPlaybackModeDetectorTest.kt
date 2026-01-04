package com.fishit.player.playback.telegram.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [TelegramPlaybackModeDetector].
 *
 * Tests cover:
 * - MP4 container detection
 * - MKV/non-progressive container detection
 * - Initial mode selection logic
 * - Edge cases (null MIME type, unknown types)
 */
class TelegramPlaybackModeDetectorTest {
    // ========== isMp4Container Tests ==========

    @Test
    fun `isMp4Container returns true for video_mp4`() {
        assertTrue(TelegramPlaybackModeDetector.isMp4Container("video/mp4"))
    }

    @Test
    fun `isMp4Container returns true for video_quicktime`() {
        assertTrue(TelegramPlaybackModeDetector.isMp4Container("video/quicktime"))
    }

    @Test
    fun `isMp4Container returns true for video_x-m4v`() {
        assertTrue(TelegramPlaybackModeDetector.isMp4Container("video/x-m4v"))
    }

    @Test
    fun `isMp4Container returns true for any MIME containing mp4`() {
        assertTrue(TelegramPlaybackModeDetector.isMp4Container("video/mp4-special"))
        assertTrue(TelegramPlaybackModeDetector.isMp4Container("application/mp4"))
    }

    @Test
    fun `isMp4Container returns false for MKV`() {
        assertFalse(TelegramPlaybackModeDetector.isMp4Container("video/x-matroska"))
    }

    @Test
    fun `isMp4Container returns false for WebM`() {
        assertFalse(TelegramPlaybackModeDetector.isMp4Container("video/webm"))
    }

    @Test
    fun `isMp4Container returns false for AVI`() {
        assertFalse(TelegramPlaybackModeDetector.isMp4Container("video/avi"))
        assertFalse(TelegramPlaybackModeDetector.isMp4Container("video/x-msvideo"))
    }

    @Test
    fun `isMp4Container returns true for null (assumes MP4)`() {
        // Default behavior: assume MP4 for unknown types
        assertTrue(TelegramPlaybackModeDetector.isMp4Container(null))
    }

    @Test
    fun `isMp4Container is case-insensitive`() {
        assertTrue(TelegramPlaybackModeDetector.isMp4Container("VIDEO/MP4"))
        assertTrue(TelegramPlaybackModeDetector.isMp4Container("Video/QuickTime"))
    }

    // ========== requiresFullDownload Tests ==========

    @Test
    fun `requiresFullDownload returns true for MKV`() {
        assertTrue(TelegramPlaybackModeDetector.requiresFullDownload("video/x-matroska"))
        assertTrue(TelegramPlaybackModeDetector.requiresFullDownload("video/matroska"))
    }

    @Test
    fun `requiresFullDownload returns true for WebM`() {
        assertTrue(TelegramPlaybackModeDetector.requiresFullDownload("video/webm"))
    }

    @Test
    fun `requiresFullDownload returns true for AVI`() {
        assertTrue(TelegramPlaybackModeDetector.requiresFullDownload("video/avi"))
        assertTrue(TelegramPlaybackModeDetector.requiresFullDownload("video/x-msvideo"))
    }

    @Test
    fun `requiresFullDownload returns true for MIME containing matroska`() {
        assertTrue(TelegramPlaybackModeDetector.requiresFullDownload("video/x-matroska-special"))
    }

    @Test
    fun `requiresFullDownload returns true for MIME containing mkv`() {
        assertTrue(TelegramPlaybackModeDetector.requiresFullDownload("video/mkv"))
    }

    @Test
    fun `requiresFullDownload returns false for MP4`() {
        assertFalse(TelegramPlaybackModeDetector.requiresFullDownload("video/mp4"))
    }

    @Test
    fun `requiresFullDownload returns false for null`() {
        // Default behavior: try progressive first for unknown types
        assertFalse(TelegramPlaybackModeDetector.requiresFullDownload(null))
    }

    @Test
    fun `requiresFullDownload is case-insensitive`() {
        assertTrue(TelegramPlaybackModeDetector.requiresFullDownload("VIDEO/X-MATROSKA"))
        assertTrue(TelegramPlaybackModeDetector.requiresFullDownload("Video/WebM"))
    }

    // ========== selectInitialMode Tests ==========

    @Test
    fun `selectInitialMode returns PROGRESSIVE_FILE for MP4`() {
        val mode = TelegramPlaybackModeDetector.selectInitialMode("video/mp4")
        assertEquals(TelegramPlaybackMode.PROGRESSIVE_FILE, mode)
    }

    @Test
    fun `selectInitialMode returns PROGRESSIVE_FILE for QuickTime`() {
        val mode = TelegramPlaybackModeDetector.selectInitialMode("video/quicktime")
        assertEquals(TelegramPlaybackMode.PROGRESSIVE_FILE, mode)
    }

    @Test
    fun `selectInitialMode returns FULL_FILE for MKV`() {
        val mode = TelegramPlaybackModeDetector.selectInitialMode("video/x-matroska")
        assertEquals(TelegramPlaybackMode.FULL_FILE, mode)
    }

    @Test
    fun `selectInitialMode returns FULL_FILE for WebM`() {
        val mode = TelegramPlaybackModeDetector.selectInitialMode("video/webm")
        assertEquals(TelegramPlaybackMode.FULL_FILE, mode)
    }

    @Test
    fun `selectInitialMode returns FULL_FILE for AVI`() {
        val mode = TelegramPlaybackModeDetector.selectInitialMode("video/avi")
        assertEquals(TelegramPlaybackMode.FULL_FILE, mode)
    }

    @Test
    fun `selectInitialMode returns PROGRESSIVE_FILE for null (unknown)`() {
        // Default behavior: try progressive first for unknown types
        val mode = TelegramPlaybackModeDetector.selectInitialMode(null)
        assertEquals(TelegramPlaybackMode.PROGRESSIVE_FILE, mode)
    }

    @Test
    fun `selectInitialMode returns PROGRESSIVE_FILE for unknown MIME`() {
        val mode = TelegramPlaybackModeDetector.selectInitialMode("video/unknown")
        assertEquals(TelegramPlaybackMode.PROGRESSIVE_FILE, mode)
    }

    // ========== describeMode Tests ==========

    @Test
    fun `describeMode returns correct description for MKV`() {
        val description = TelegramPlaybackModeDetector.describeMode("video/x-matroska")
        assertTrue(description.contains("MKV"))
        assertTrue(description.contains("full download"))
    }

    @Test
    fun `describeMode returns correct description for WebM`() {
        val description = TelegramPlaybackModeDetector.describeMode("video/webm")
        assertTrue(description.contains("WebM"))
        assertTrue(description.contains("full download"))
    }

    @Test
    fun `describeMode returns correct description for AVI`() {
        val description = TelegramPlaybackModeDetector.describeMode("video/avi")
        assertTrue(description.contains("AVI"))
        assertTrue(description.contains("full download"))
    }

    @Test
    fun `describeMode returns correct description for MP4`() {
        val description = TelegramPlaybackModeDetector.describeMode("video/mp4")
        assertTrue(description.contains("MP4"))
        assertTrue(description.contains("progressive"))
    }

    @Test
    fun `describeMode returns correct description for null`() {
        val description = TelegramPlaybackModeDetector.describeMode(null)
        assertTrue(description.contains("Unknown"))
        assertTrue(description.contains("progressive"))
    }

    @Test
    fun `describeMode handles case-insensitive MIME types`() {
        val description = TelegramPlaybackModeDetector.describeMode("VIDEO/X-MATROSKA")
        assertTrue(description.contains("MKV"))
    }
}
