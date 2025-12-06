package com.fishit.player.pipeline.io

import com.fishit.player.pipeline.io.IoMediaItem
import com.fishit.player.pipeline.io.IoSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Tests for StubIoPlaybackSourceFactory.
 *
 * These tests verify the stub implementation behaves deterministically
 * and satisfies the IoPlaybackSourceFactory interface contract.
 */
class StubIoPlaybackSourceFactoryTest {
    private lateinit var factory: StubIoPlaybackSourceFactory

    @Before
    fun setup() {
        factory = StubIoPlaybackSourceFactory()
    }

    @Test
    fun `createSource returns non-null result`() {
        val item = IoMediaItem.fake()

        val source = factory.createSource(item)

        assertNotNull(source)
    }

    @Test
    fun `createSource returns consistent result for same item`() {
        val item = IoMediaItem.fake(id = "test-id", title = "Test")

        val source1 = factory.createSource(item)
        val source2 = factory.createSource(item)

        assertEquals(source1, source2)
    }

    @Test
    fun `createSource result contains expected fields`() {
        val item =
            IoMediaItem.fake(
                id = "test-id",
                path = "/path/to/video.mp4",
                title = "Test Video",
            )

        val source = factory.createSource(item)

        // Stub returns a map
        assertTrue(source is Map<*, *>)
        val map = source as Map<*, *>
        assertEquals("io", map["type"])
        assertEquals("test-id", map["id"])
        assertEquals("Test Video", map["title"])
        assertTrue(map["uri"].toString().contains("video.mp4"))
    }

    @Test
    fun `supportsSource returns true for LocalFile`() {
        val source = IoSource.LocalFile("/path/to/file")

        val supported = factory.supportsSource(source)

        assertTrue(supported)
    }

    @Test
    fun `supportsSource returns false for Saf`() {
        val source = IoSource.Saf("content://provider/document/123")

        val supported = factory.supportsSource(source)

        assertFalse(supported)
    }

    @Test
    fun `supportsSource returns false for Smb`() {
        val source = IoSource.Smb("smb://server/share/file")

        val supported = factory.supportsSource(source)

        assertFalse(supported)
    }

    @Test
    fun `supportsSource returns false for GenericUri`() {
        val source = IoSource.GenericUri("http://example.com/video.mp4")

        val supported = factory.supportsSource(source)

        assertFalse(supported)
    }
}
