package com.fishit.player.pipeline.io

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * Tests for IoSource sealed class hierarchy.
 *
 * These tests verify the IoSource type system and URI generation.
 */
class IoSourceTest {
    @Test
    fun `LocalFile generates correct file URI`() {
        val source = IoSource.LocalFile("/storage/emulated/0/Movies/video.mp4")
        val uri = source.toUriString()

        assertEquals("file:///storage/emulated/0/Movies/video.mp4", uri)
    }

    @Test
    fun `Saf returns content URI unchanged`() {
        val contentUri = "content://com.android.providers.media.documents/document/video%3A1234"
        val source = IoSource.Saf(contentUri)
        val uri = source.toUriString()

        assertEquals(contentUri, uri)
    }

    @Test
    fun `Smb returns SMB URI unchanged`() {
        val smbUri = "smb://192.168.1.100/share/Movies/video.mp4"
        val source = IoSource.Smb(smbUri)
        val uri = source.toUriString()

        assertEquals(smbUri, uri)
    }

    @Test
    fun `GenericUri returns URI unchanged`() {
        val genericUri = "http://example.com/video.mp4"
        val source = IoSource.GenericUri(genericUri)
        val uri = source.toUriString()

        assertEquals(genericUri, uri)
    }

    @Test
    fun `IoSource subclasses are data classes`() {
        val source1 = IoSource.LocalFile("/path/to/file")
        val source2 = IoSource.LocalFile("/path/to/file")
        val source3 = IoSource.LocalFile("/path/to/other")

        // Data class equality
        assertEquals(source1, source2)
        assert(source1 != source3)
    }
}
