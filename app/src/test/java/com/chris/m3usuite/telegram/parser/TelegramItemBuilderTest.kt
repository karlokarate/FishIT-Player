package com.chris.m3usuite.telegram.parser

import com.chris.m3usuite.telegram.domain.MessageBlock
import com.chris.m3usuite.telegram.domain.TelegramItemType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for TelegramItemBuilder.
 */
class TelegramItemBuilderTest {
    @Test
    fun `build returns null for empty block`() {
        val block = MessageBlock(chatId = -1001L, messages = emptyList())

        val item = TelegramItemBuilder.build(block, chatTitle = "Test Chat")

        assertNull("Empty block should produce null", item)
    }

    @Test
    fun `build creates MOVIE item from video message`() {
        val video = createVideoMessage(id = 1, duration = 5400) // 90 minutes

        val block = MessageBlock(chatId = -1001L, messages = listOf(video))
        val item = TelegramItemBuilder.build(block, chatTitle = "HD Movies")

        assertNotNull("Should create item from video", item)
        assertEquals("Item type should be MOVIE", TelegramItemType.MOVIE, item?.type)
        assertNotNull("Should have video ref", item?.videoRef)
        assertNull("Should not have document ref", item?.documentRef)
        assertEquals("Anchor message ID should match video", 1L, item?.anchorMessageId)
    }

    @Test
    fun `build creates SERIES_EPISODE item when filename has episode pattern`() {
        val video = createVideoMessage(id = 1, duration = 2700, fileName = "Series.S01E05.1080p.mp4")

        val block = MessageBlock(chatId = -1001L, messages = listOf(video))
        val item = TelegramItemBuilder.build(block, chatTitle = "TV Series")

        assertEquals("Item type should be SERIES_EPISODE", TelegramItemType.SERIES_EPISODE, item?.type)
    }

    @Test
    fun `build creates CLIP item for short video without metadata`() {
        val video = createVideoMessage(id = 1, duration = 300) // 5 minutes

        val block = MessageBlock(chatId = -1001L, messages = listOf(video))
        val item = TelegramItemBuilder.build(block, chatTitle = "Clips Channel")

        assertEquals("Short video should be CLIP", TelegramItemType.CLIP, item?.type)
    }

    @Test
    fun `build extracts metadata from text message`() {
        val video = createVideoMessage(id = 1, duration = 5400)
        val text =
            ExportText(
                id = 2,
                chatId = -1001L,
                dateEpochSeconds = 1000L,
                dateIso = "2023-01-01T00:00:00Z",
                text = "Movie info",
                title = "Test Movie",
                year = 2023,
                genres = listOf("Action", "Drama"),
                tmdbUrl = "https://themoviedb.org/movie/12345",
            )

        val block = MessageBlock(chatId = -1001L, messages = listOf(video, text))
        val item = TelegramItemBuilder.build(block, chatTitle = "Movies")

        assertEquals("Should extract title", "Test Movie", item?.metadata?.title)
        assertEquals("Should extract year", 2023, item?.metadata?.year)
        assertEquals("Should extract genres", listOf("Action", "Drama"), item?.metadata?.genres)
        assertEquals("Should extract TMDb URL", "https://themoviedb.org/movie/12345", item?.metadata?.tmdbUrl)
        assertEquals("Text message ID should be recorded", 2L, item?.textMessageId)
    }

    @Test
    fun `build selects best video by resolution`() {
        val lowRes = createVideoMessage(id = 1, width = 720, height = 480, duration = 5400)
        val highRes = createVideoMessage(id = 2, width = 1920, height = 1080, duration = 5400)

        val block = MessageBlock(chatId = -1001L, messages = listOf(lowRes, highRes))
        val item = TelegramItemBuilder.build(block, chatTitle = "Movies")

        assertEquals("Should select highest resolution video", 2L, item?.anchorMessageId)
        assertEquals("Video ref should have high res dimensions", 1920, item?.videoRef?.width)
    }

    @Test
    fun `build creates POSTER_ONLY item from photo and text without video`() {
        val photo = createPhotoMessage(id = 1)
        val text =
            ExportText(
                id = 2,
                chatId = -1001L,
                dateEpochSeconds = 1000L,
                dateIso = "2023-01-01T00:00:00Z",
                text = "Movie poster info",
                title = "Upcoming Movie",
            )

        val block = MessageBlock(chatId = -1001L, messages = listOf(photo, text))
        val item = TelegramItemBuilder.build(block, chatTitle = "Movies")

        assertEquals("Should be POSTER_ONLY", TelegramItemType.POSTER_ONLY, item?.type)
        assertNull("Should not have video ref", item?.videoRef)
        assertNull("Should not have document ref", item?.documentRef)
        assertNotNull("Should have poster ref", item?.posterRef)
    }

    @Test
    fun `build creates RAR_ITEM from document with archive extension`() {
        val document = createDocumentMessage(id = 1, fileName = "Movie.part1.rar")

        val block = MessageBlock(chatId = -1001L, messages = listOf(document))
        val item = TelegramItemBuilder.build(block, chatTitle = "Archives")

        assertEquals("Should be RAR_ITEM", TelegramItemType.RAR_ITEM, item?.type)
        assertNotNull("Should have document ref", item?.documentRef)
        assertNull("Should not have video ref", item?.videoRef)
    }

    @Test
    fun `build creates AUDIOBOOK from audio message`() {
        val audio = createAudioMessage(id = 1, title = "Book Chapter 1", performer = "Narrator")

        val block = MessageBlock(chatId = -1001L, messages = listOf(audio))
        val item = TelegramItemBuilder.build(block, chatTitle = "Audiobooks")

        assertEquals("Should be AUDIOBOOK", TelegramItemType.AUDIOBOOK, item?.type)
        assertNotNull("Should have document ref", item?.documentRef)
    }

    @Test
    fun `build detects adult content from chat title`() {
        val video = createVideoMessage(id = 1, duration = 5400)

        val block = MessageBlock(chatId = -1001L, messages = listOf(video))
        val item = TelegramItemBuilder.build(block, chatTitle = "Adult Content 18+")

        assertTrue("Should be marked as adult", item?.metadata?.isAdult == true)
    }

    @Test
    fun `build does not mark non-adult chat as adult`() {
        val video = createVideoMessage(id = 1, duration = 5400)

        val block = MessageBlock(chatId = -1001L, messages = listOf(video))
        val item = TelegramItemBuilder.build(block, chatTitle = "HD Movies")

        assertFalse("Should not be marked as adult", item?.metadata?.isAdult == true)
    }

    @Test
    fun `build selects poster with portrait aspect ratio`() {
        val video = createVideoMessage(id = 1, duration = 5400)
        val portraitPhoto = createPhotoMessage(id = 2, width = 200, height = 300) // 0.67 aspect ratio
        val landscapePhoto = createPhotoMessage(id = 3, width = 1920, height = 1080) // 1.78 aspect ratio

        val block = MessageBlock(chatId = -1001L, messages = listOf(video, portraitPhoto, landscapePhoto))
        val item = TelegramItemBuilder.build(block, chatTitle = "Movies")

        // Portrait photo should be selected as poster due to aspect ratio <= 0.85
        assertEquals("Poster should have portrait dimensions", 200, item?.posterRef?.width)
        assertEquals("Poster height should match portrait photo", 300, item?.posterRef?.height)
    }

    @Test
    fun `build selects backdrop with landscape aspect ratio`() {
        val video = createVideoMessage(id = 1, duration = 5400)
        val portraitPhoto = createPhotoMessage(id = 2, width = 200, height = 300) // 0.67 aspect ratio
        val landscapePhoto = createPhotoMessage(id = 3, width = 1920, height = 1080) // 1.78 aspect ratio

        val block = MessageBlock(chatId = -1001L, messages = listOf(video, portraitPhoto, landscapePhoto))
        val item = TelegramItemBuilder.build(block, chatTitle = "Movies")

        // Landscape photo should be selected as backdrop due to aspect ratio >= 1.6
        assertEquals("Backdrop should have landscape width", 1920, item?.backdropRef?.width)
    }

    @Test
    fun `build falls back to video thumbnail when no photos`() {
        val videoWithThumbnail =
            createVideoMessage(
                id = 1,
                duration = 5400,
                thumbnailWidth = 320,
                thumbnailHeight = 180,
            )

        val block = MessageBlock(chatId = -1001L, messages = listOf(videoWithThumbnail))
        val item = TelegramItemBuilder.build(block, chatTitle = "Movies")

        // When no photos, video thumbnail should be used as poster
        assertNotNull("Should have poster ref from thumbnail", item?.posterRef)
        assertEquals("Poster width should match thumbnail", 320, item?.posterRef?.width)
    }

    // Helper functions to create test messages

    private fun createVideoMessage(
        id: Long,
        duration: Int = 5400,
        width: Int = 1920,
        height: Int = 1080,
        fileName: String = "video.mp4",
        thumbnailWidth: Int? = null,
        thumbnailHeight: Int? = null,
    ): ExportVideo {
        val thumbnail =
            if (thumbnailWidth != null && thumbnailHeight != null) {
                ExportThumbnail(
                    width = thumbnailWidth,
                    height = thumbnailHeight,
                    file =
                        ExportFile(
                            id = 200,
                            size = 10000L,
                            remote =
                                ExportRemoteFile(
                                    id = "thumb_remote_$id",
                                    uniqueId = "thumb_unique_$id",
                                ),
                        ),
                )
            } else {
                null
            }

        return ExportVideo(
            id = id,
            chatId = -1001L,
            dateEpochSeconds = 1000L,
            dateIso = "2023-01-01T00:00:00Z",
            video =
                ExportVideoContent(
                    duration = duration,
                    width = width,
                    height = height,
                    fileName = fileName,
                    mimeType = "video/mp4",
                    thumbnail = thumbnail,
                    video =
                        ExportFile(
                            id = 100,
                            size = 1000000000L,
                            remote =
                                ExportRemoteFile(
                                    id = "remote_$id",
                                    uniqueId = "unique_$id",
                                ),
                        ),
                ),
            caption = null,
        )
    }

    private fun createPhotoMessage(
        id: Long,
        width: Int = 800,
        height: Int = 600,
    ): ExportPhoto =
        ExportPhoto(
            id = id,
            chatId = -1001L,
            dateEpochSeconds = 1000L,
            dateIso = "2023-01-01T00:00:00Z",
            sizes =
                listOf(
                    ExportPhotoSize(
                        type = "x",
                        width = width,
                        height = height,
                        photo =
                            ExportFile(
                                id = 300,
                                size = 50000L,
                                remote =
                                    ExportRemoteFile(
                                        id = "photo_remote_$id",
                                        uniqueId = "photo_unique_$id",
                                    ),
                            ),
                    ),
                ),
            caption = null,
        )

    private fun createDocumentMessage(
        id: Long,
        fileName: String = "document.pdf",
    ): ExportDocument =
        ExportDocument(
            id = id,
            chatId = -1001L,
            dateEpochSeconds = 1000L,
            dateIso = "2023-01-01T00:00:00Z",
            document =
                ExportDocumentContent(
                    fileName = fileName,
                    mimeType = "application/octet-stream",
                    document =
                        ExportFile(
                            id = 400,
                            size = 500000000L,
                            remote =
                                ExportRemoteFile(
                                    id = "doc_remote_$id",
                                    uniqueId = "doc_unique_$id",
                                ),
                        ),
                ),
            caption = null,
        )

    private fun createAudioMessage(
        id: Long,
        title: String = "Audio Track",
        performer: String = "Artist",
    ): ExportAudio =
        ExportAudio(
            id = id,
            chatId = -1001L,
            dateEpochSeconds = 1000L,
            dateIso = "2023-01-01T00:00:00Z",
            audio =
                ExportAudioContent(
                    duration = 3600,
                    title = title,
                    performer = performer,
                    fileName = "$performer - $title.mp3",
                    mimeType = "audio/mpeg",
                    audio =
                        ExportFile(
                            id = 500,
                            size = 50000000L,
                            remote =
                                ExportRemoteFile(
                                    id = "audio_remote_$id",
                                    uniqueId = "audio_unique_$id",
                                ),
                        ),
                ),
            caption = null,
        )
}
