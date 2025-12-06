package com.fishit.player.pipeline.io

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for IoMediaItem domain model.
 *
 * These tests verify the IoMediaItem structure and helper methods.
 */
class IoMediaItemTest {
    @Test
    fun `IoMediaItem can be constructed with minimal fields`() {
        val item =
            IoMediaItem(
                id = "test-id",
                source = IoSource.LocalFile("/path/to/video.mp4"),
                title = "Test Video",
                fileName = "video.mp4",
            )

        assertEquals("test-id", item.id)
        assertEquals("Test Video", item.title)
        assertEquals("video.mp4", item.fileName)
    }

    @Test
    fun `IoMediaItem generates correct ContentId`() {
        val item =
            IoMediaItem(
                id = "test-id",
                source = IoSource.LocalFile("/storage/emulated/0/video.mp4"),
                title = "Test",
                fileName = "video.mp4",
            )

        val contentId = item.toContentId()

        assertTrue(contentId.startsWith("io:file:"))
        assertTrue(contentId.contains("video.mp4"))
    }

    @Test
    fun `IoMediaItem fake factory creates valid item`() {
        val item = IoMediaItem.fake()

        assertNotNull(item.id)
        assertNotNull(item.title)
        assertNotNull(item.fileName)
        assertTrue(item.source is IoSource.LocalFile)
    }

    @Test
    fun `IoMediaItem fake factory accepts custom parameters`() {
        val item =
            IoMediaItem.fake(
                id = "custom-id",
                path = "/custom/path/video.mp4",
                title = "Custom Title",
            )

        assertEquals("custom-id", item.id)
        assertEquals("Custom Title", item.title)
        assertTrue(item.fileName.contains("video.mp4"))
    }

    @Test
    fun `IoMediaItem supports optional metadata`() {
        val item =
            IoMediaItem(
                id = "test-id",
                source = IoSource.LocalFile("/path/video.mp4"),
                title = "Test",
                fileName = "video.mp4",
                metadata =
                    mapOf(
                        "codec" to "h264",
                        "resolution" to "1920x1080",
                    ),
            )

        assertEquals("h264", item.metadata["codec"])
        assertEquals("1920x1080", item.metadata["resolution"])
    }
}
