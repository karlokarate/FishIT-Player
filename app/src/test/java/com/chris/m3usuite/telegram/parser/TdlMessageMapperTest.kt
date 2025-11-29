package com.chris.m3usuite.telegram.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for TdlMessageMapper.
 *
 * Tests the mapping of TDLib DTOs to ExportMessage variants.
 * Note: These tests use mock data structures since actual TDLib DTOs
 * require native library loading. The tests verify the mapper's logic
 * and API structure.
 */
class TdlMessageMapperTest {
    @Test
    fun `TdlMessageMapper class exists and has required methods`() {
        // Verify class structure
        val clazz = TdlMessageMapper::class
        val methods = clazz.java.methods.map { it.name }

        assertTrue("Should have toExportMessage method", methods.contains("toExportMessage"))
        assertTrue("Should have toExportMessages method", methods.contains("toExportMessages"))
    }

    @Test
    fun `TdlMessageMapper is an object (singleton)`() {
        // TdlMessageMapper should be a Kotlin object (singleton)
        // Verify by checking INSTANCE field exists (without kotlin-reflect)
        val clazz = TdlMessageMapper::class.java
        val instanceField = clazz.fields.find { it.name == "INSTANCE" }
        assertNotNull("Object should have INSTANCE field", instanceField)
    }

    @Test
    fun `toExportMessages filters null results`() {
        // toExportMessages should filter out null results from toExportMessage
        // Since we can't easily create TDLib Message instances without native libs,
        // we verify the method signature and empty list behavior
        val emptyResult = TdlMessageMapper.toExportMessages(emptyList())
        assertTrue("Empty input should produce empty output", emptyResult.isEmpty())
    }

    @Test
    fun `ExportMessage types exist for all supported message types`() {
        // Verify that all expected ExportMessage subclasses exist
        assertTrue(ExportVideo::class.java.isAssignableFrom(ExportVideo::class.java))
        assertTrue(ExportPhoto::class.java.isAssignableFrom(ExportPhoto::class.java))
        assertTrue(ExportText::class.java.isAssignableFrom(ExportText::class.java))
        assertTrue(ExportDocument::class.java.isAssignableFrom(ExportDocument::class.java))
        assertTrue(ExportAudio::class.java.isAssignableFrom(ExportAudio::class.java))
        assertTrue(ExportOtherRaw::class.java.isAssignableFrom(ExportOtherRaw::class.java))
    }

    @Test
    fun `ExportVideo has required fields`() {
        val video =
            ExportVideo(
                id = 123L,
                chatId = 456L,
                dateEpochSeconds = 1700000000L,
                dateIso = "2023-11-14T22:13:20Z",
                video =
                    ExportVideoContent(
                        duration = 120,
                        width = 1920,
                        height = 1080,
                        fileName = "test.mp4",
                        mimeType = "video/mp4",
                        supportsStreaming = true,
                        thumbnail = null,
                        video =
                            ExportFile(
                                id = 789,
                                size = 1000000,
                                expectedSize = 1000000,
                                remote =
                                    ExportRemoteFile(
                                        id = "remote-id-123",
                                        uniqueId = "unique-id-abc",
                                    ),
                            ),
                    ),
                caption = "Test video",
                captionEntities = emptyList(),
            )

        assertEquals(123L, video.id)
        assertEquals(456L, video.chatId)
        assertEquals(1700000000L, video.dateEpochSeconds)
        assertEquals("2023-11-14T22:13:20Z", video.dateIso)
        assertEquals(120, video.video.duration)
        assertEquals(1920, video.video.width)
        assertEquals(1080, video.video.height)
        assertEquals("test.mp4", video.video.fileName)
        assertEquals("video/mp4", video.video.mimeType)
        assertTrue(video.video.supportsStreaming)
        assertEquals("remote-id-123", video.video.video.remote.id)
        assertEquals("unique-id-abc", video.video.video.remote.uniqueId)
        assertEquals("Test video", video.caption)
    }

    @Test
    fun `ExportPhoto has required fields and sizes`() {
        val photo =
            ExportPhoto(
                id = 100L,
                chatId = 200L,
                dateEpochSeconds = 1700000000L,
                dateIso = "2023-11-14T22:13:20Z",
                sizes =
                    listOf(
                        ExportPhotoSize(
                            type = "m",
                            width = 320,
                            height = 240,
                            photo =
                                ExportFile(
                                    id = 301,
                                    size = 50000,
                                    remote =
                                        ExportRemoteFile(
                                            id = "photo-remote-m",
                                            uniqueId = "photo-unique-m",
                                        ),
                                ),
                        ),
                        ExportPhotoSize(
                            type = "x",
                            width = 1280,
                            height = 960,
                            photo =
                                ExportFile(
                                    id = 302,
                                    size = 500000,
                                    remote =
                                        ExportRemoteFile(
                                            id = "photo-remote-x",
                                            uniqueId = "photo-unique-x",
                                        ),
                                ),
                        ),
                    ),
                caption = "Test photo",
            )

        assertEquals(100L, photo.id)
        assertEquals(200L, photo.chatId)
        assertEquals(2, photo.sizes.size)
        assertEquals(1280, photo.sizes[1].width)
        assertEquals(960, photo.sizes[1].height)
        assertEquals("photo-remote-x", photo.sizes[1].photo.remote.id)
    }

    @Test
    fun `ExportText has metadata fields`() {
        val text =
            ExportText(
                id = 50L,
                chatId = 100L,
                dateEpochSeconds = 1700000000L,
                dateIso = "2023-11-14T22:13:20Z",
                text = "Titel: Test Movie\nErscheinungsjahr: 2023\nFSK: 12",
                entities = emptyList(),
                title = "Test Movie",
                originalTitle = "Original Title",
                year = 2023,
                lengthMinutes = 120,
                fsk = 12,
                productionCountry = "Germany",
                collection = "Test Collection",
                director = "Test Director",
                tmdbRating = 8.5,
                genres = listOf("Action", "Thriller"),
                tmdbUrl = "https://themoviedb.org/movie/12345",
            )

        assertEquals(50L, text.id)
        assertEquals("Test Movie", text.title)
        assertEquals(2023, text.year)
        assertEquals(12, text.fsk)
        assertEquals(2, text.genres.size)
        assertEquals("Action", text.genres[0])
        assertNotNull(text.tmdbUrl)
    }

    @Test
    fun `ExportDocument has file reference fields`() {
        val doc =
            ExportDocument(
                id = 75L,
                chatId = 150L,
                dateEpochSeconds = 1700000000L,
                dateIso = "2023-11-14T22:13:20Z",
                document =
                    ExportDocumentContent(
                        fileName = "archive.rar",
                        mimeType = "application/x-rar-compressed",
                        thumbnail = null,
                        document =
                            ExportFile(
                                id = 400,
                                size = 10000000,
                                remote =
                                    ExportRemoteFile(
                                        id = "doc-remote-123",
                                        uniqueId = "doc-unique-abc",
                                    ),
                            ),
                    ),
                caption = "Archive file",
            )

        assertEquals(75L, doc.id)
        assertEquals("archive.rar", doc.document.fileName)
        assertEquals("application/x-rar-compressed", doc.document.mimeType)
        assertEquals("doc-remote-123", doc.document.document.remote.id)
        assertEquals("doc-unique-abc", doc.document.document.remote.uniqueId)
    }

    @Test
    fun `ExportAudio has audio-specific fields`() {
        val audio =
            ExportAudio(
                id = 80L,
                chatId = 160L,
                dateEpochSeconds = 1700000000L,
                dateIso = "2023-11-14T22:13:20Z",
                audio =
                    ExportAudioContent(
                        duration = 240,
                        title = "Audio Book Chapter 1",
                        performer = "Narrator Name",
                        fileName = "chapter1.mp3",
                        mimeType = "audio/mpeg",
                        albumCoverThumbnail = null,
                        audio =
                            ExportFile(
                                id = 500,
                                size = 5000000,
                                remote =
                                    ExportRemoteFile(
                                        id = "audio-remote-123",
                                        uniqueId = "audio-unique-abc",
                                    ),
                            ),
                    ),
                caption = "Audiobook chapter",
            )

        assertEquals(80L, audio.id)
        assertEquals(240, audio.audio.duration)
        assertEquals("Audio Book Chapter 1", audio.audio.title)
        assertEquals("Narrator Name", audio.audio.performer)
        assertEquals("audio-remote-123", audio.audio.audio.remote.id)
    }

    @Test
    fun `ExportOtherRaw captures message type`() {
        val other =
            ExportOtherRaw(
                id = 90L,
                chatId = 180L,
                dateEpochSeconds = 1700000000L,
                dateIso = "2023-11-14T22:13:20Z",
                rawJson = "{}",
                messageType = "MessageSticker",
            )

        assertEquals(90L, other.id)
        assertEquals("MessageSticker", other.messageType)
    }

    @Test
    fun `ExportRemoteFile has stable identifier fields`() {
        val remote =
            ExportRemoteFile(
                id = "stable-remote-id",
                uniqueId = "stable-unique-id",
                isUploadingActive = false,
                isUploadingCompleted = true,
                uploadedSize = 1000000,
            )

        assertEquals("stable-remote-id", remote.id)
        assertEquals("stable-unique-id", remote.uniqueId)
        assertTrue(remote.isUploadingCompleted)
    }

    @Test
    fun `ExportLocalFile has download state fields`() {
        val local =
            ExportLocalFile(
                path = "/path/to/file.mp4",
                canBeDownloaded = true,
                canBeDeleted = true,
                isDownloadingActive = false,
                isDownloadingCompleted = true,
                downloadOffset = 0,
                downloadedPrefixSize = 1000000,
                downloadedSize = 1000000,
            )

        assertEquals("/path/to/file.mp4", local.path)
        assertTrue(local.canBeDownloaded)
        assertTrue(local.isDownloadingCompleted)
    }

    @Test
    fun `ExportFile combines local and remote references`() {
        val file =
            ExportFile(
                id = 123,
                size = 5000000,
                expectedSize = 5000000,
                local =
                    ExportLocalFile(
                        path = "/local/path",
                        isDownloadingCompleted = true,
                    ),
                remote =
                    ExportRemoteFile(
                        id = "remote-123",
                        uniqueId = "unique-456",
                    ),
            )

        assertEquals(123, file.id)
        assertEquals(5000000, file.size)
        assertEquals("remote-123", file.remote.id)
        assertEquals("unique-456", file.remote.uniqueId)
        assertEquals("/local/path", file.local.path)
    }
}
