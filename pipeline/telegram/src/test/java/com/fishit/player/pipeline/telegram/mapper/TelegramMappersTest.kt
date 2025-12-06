package com.fishit.player.pipeline.telegram.mapper

import com.fishit.player.core.persistence.obx.ObxTelegramMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for TelegramMappers.
 *
 * Phase 2 Task 3 (P2-T3) - validates mapping structure without real TDLib.
 */
class TelegramMappersTest {
    @Test
    fun `maps ObxTelegramMessage to TelegramMediaItem`() {
        val obxMessage = createTestObxMessage(
            id = 1,
            chatId = 12345,
            messageId = 67890,
            fileId = 123,
            title = "Test Movie",
        )

        val mediaItem = TelegramMappers.fromObxTelegramMessage(obxMessage)

        assertEquals(1, mediaItem.id)
        assertEquals(12345, mediaItem.chatId)
        assertEquals(67890, mediaItem.messageId)
        assertEquals(123, mediaItem.fileId)
        assertEquals("Test Movie", mediaItem.title)
    }

    @Test
    fun `maps all fields from ObxTelegramMessage`() {
        val obxMessage = createTestObxMessage(
            id = 1,
            chatId = 12345,
            messageId = 67890,
            fileId = 123,
            remoteId = "remote_123",
            title = "Test Movie",
            fileName = "movie.mp4",
            caption = "A test caption",
            mimeType = "video/mp4",
            sizeBytes = 1024000,
            durationSecs = 7200,
            width = 1920,
            height = 1080,
            supportsStreaming = true,
            localPath = "/path/to/local",
            thumbLocalPath = "/path/to/thumb",
            date = 1609459200,
        )

        val mediaItem = TelegramMappers.fromObxTelegramMessage(obxMessage)

        assertEquals("remote_123", mediaItem.remoteId)
        assertEquals("movie.mp4", mediaItem.fileName)
        assertEquals("A test caption", mediaItem.caption)
        assertEquals("video/mp4", mediaItem.mimeType)
        assertEquals(1024000L, mediaItem.sizeBytes)
        assertEquals(7200, mediaItem.durationSecs)
        assertEquals(1920, mediaItem.width)
        assertEquals(1080, mediaItem.height)
        assertEquals(true, mediaItem.supportsStreaming)
        assertEquals("/path/to/local", mediaItem.localPath)
        assertEquals("/path/to/thumb", mediaItem.thumbnailPath)
        assertEquals(1609459200L, mediaItem.date)
    }

    @Test
    fun `maps series metadata from ObxTelegramMessage`() {
        val obxMessage = createTestObxMessage(
            title = "Test Series Episode",
            isSeries = true,
            seriesName = "My Series",
            seasonNumber = 2,
            episodeNumber = 5,
            episodeTitle = "The Big Episode",
            year = 2021,
            genres = "Action, Drama",
            description = "An exciting episode",
        )

        val mediaItem = TelegramMappers.fromObxTelegramMessage(obxMessage)

        assertTrue(mediaItem.isSeries)
        assertEquals("My Series", mediaItem.seriesName)
        assertEquals(2, mediaItem.seasonNumber)
        assertEquals(5, mediaItem.episodeNumber)
        assertEquals("The Big Episode", mediaItem.episodeTitle)
        assertEquals(2021, mediaItem.year)
        assertEquals("Action, Drama", mediaItem.genres)
        assertEquals("An exciting episode", mediaItem.description)
    }

    @Test
    fun `extracts title from title field`() {
        val obxMessage = createTestObxMessage(
            title = "Movie Title",
            episodeTitle = "Episode Title",
            caption = "Caption",
            fileName = "file.mp4",
        )

        val mediaItem = TelegramMappers.fromObxTelegramMessage(obxMessage)

        assertEquals("Movie Title", mediaItem.title)
    }

    @Test
    fun `extracts title from episodeTitle when title is null`() {
        val obxMessage = createTestObxMessage(
            title = null,
            episodeTitle = "Episode Title",
            caption = "Caption",
            fileName = "file.mp4",
        )

        val mediaItem = TelegramMappers.fromObxTelegramMessage(obxMessage)

        assertEquals("Episode Title", mediaItem.title)
    }

    @Test
    fun `extracts title from caption when title and episodeTitle are null`() {
        val obxMessage = createTestObxMessage(
            title = null,
            episodeTitle = null,
            caption = "Caption",
            fileName = "file.mp4",
        )

        val mediaItem = TelegramMappers.fromObxTelegramMessage(obxMessage)

        assertEquals("Caption", mediaItem.title)
    }

    @Test
    fun `extracts title from fileName when other fields are null`() {
        val obxMessage = createTestObxMessage(
            title = null,
            episodeTitle = null,
            caption = null,
            fileName = "file.mp4",
        )

        val mediaItem = TelegramMappers.fromObxTelegramMessage(obxMessage)

        assertEquals("file.mp4", mediaItem.title)
    }

    @Test
    fun `generates fallback title when all fields are null`() {
        val obxMessage = createTestObxMessage(
            messageId = 12345,
            title = null,
            episodeTitle = null,
            caption = null,
            fileName = null,
        )

        val mediaItem = TelegramMappers.fromObxTelegramMessage(obxMessage)

        assertEquals("Untitled Media 12345", mediaItem.title)
    }

    @Test
    fun `maps list of ObxTelegramMessages`() {
        val obxMessages = listOf(
            createTestObxMessage(id = 1, title = "Movie 1"),
            createTestObxMessage(id = 2, title = "Movie 2"),
            createTestObxMessage(id = 3, title = "Movie 3"),
        )

        val mediaItems = TelegramMappers.fromObxTelegramMessages(obxMessages)

        assertEquals(3, mediaItems.size)
        assertEquals("Movie 1", mediaItems[0].title)
        assertEquals("Movie 2", mediaItems[1].title)
        assertEquals("Movie 3", mediaItems[2].title)
    }

    @Test
    fun `converts TelegramMediaItem back to ObxTelegramMessage`() {
        val mediaItem = createTestMediaItem(
            chatId = 12345,
            messageId = 67890,
            fileId = 123,
            title = "Test Movie",
        )

        val obxMessage = TelegramMappers.toObxTelegramMessage(mediaItem)

        assertEquals(12345, obxMessage.chatId)
        assertEquals(67890, obxMessage.messageId)
        assertEquals(123, obxMessage.fileId)
        assertEquals("Test Movie", obxMessage.title)
    }

    @Test
    fun `round trip conversion preserves data`() {
        val originalObx = createTestObxMessage(
            id = 1,
            chatId = 12345,
            messageId = 67890,
            fileId = 123,
            remoteId = "remote_123",
            title = "Test Movie",
            fileName = "movie.mp4",
            mimeType = "video/mp4",
            sizeBytes = 1024000,
        )

        val mediaItem = TelegramMappers.fromObxTelegramMessage(originalObx)
        val convertedObx = TelegramMappers.toObxTelegramMessage(mediaItem, existingId = 1)

        assertEquals(originalObx.id, convertedObx.id)
        assertEquals(originalObx.chatId, convertedObx.chatId)
        assertEquals(originalObx.messageId, convertedObx.messageId)
        assertEquals(originalObx.fileId, convertedObx.fileId)
        assertEquals(originalObx.remoteId, convertedObx.remoteId)
        assertEquals(originalObx.title, convertedObx.title)
        assertEquals(originalObx.fileName, convertedObx.fileName)
        assertEquals(originalObx.mimeType, convertedObx.mimeType)
        assertEquals(originalObx.sizeBytes, convertedObx.sizeBytes)
    }

    @Test
    fun `handles null fields in mapping`() {
        val obxMessage = ObxTelegramMessage(
            id = 1,
            chatId = 12345,
            messageId = 67890,
        )

        val mediaItem = TelegramMappers.fromObxTelegramMessage(obxMessage)

        assertNull(mediaItem.fileId)
        assertNull(mediaItem.remoteId)
        assertNull(mediaItem.fileName)
        assertNull(mediaItem.mimeType)
        assertEquals("Untitled Media 67890", mediaItem.title)
    }

    @Test
    fun `preserves internal fields when existingMessage is provided`() {
        val existingObx = createTestObxMessage(
            id = 1,
            chatId = 12345,
            messageId = 67890,
            fileId = 123,
            fileUniqueId = "unique_file_id_123",
            thumbFileId = 456,
            language = "en",
            fsk = 12,
            posterFileId = 789,
            posterLocalPath = "/path/to/poster.jpg",
        )

        // Create a media item from the existing message
        val mediaItem = TelegramMappers.fromObxTelegramMessage(existingObx)
        
        // Convert back with the existing message to preserve internal fields
        val convertedObx = TelegramMappers.toObxTelegramMessage(
            mediaItem = mediaItem,
            existingId = 1,
            existingMessage = existingObx,
        )

        // Verify internal fields are preserved
        assertEquals("unique_file_id_123", convertedObx.fileUniqueId)
        assertEquals(456, convertedObx.thumbFileId)
        assertEquals("en", convertedObx.language)
        assertEquals(12, convertedObx.fsk)
        assertEquals(789, convertedObx.posterFileId)
        assertEquals("/path/to/poster.jpg", convertedObx.posterLocalPath)
    }

    @Test
    fun `sets internal fields to null when no existingMessage provided`() {
        val mediaItem = createTestMediaItem(
            chatId = 12345,
            messageId = 67890,
            fileId = 123,
            title = "Test Movie",
        )

        val obxMessage = TelegramMappers.toObxTelegramMessage(mediaItem)

        // Verify internal fields are null when no existing message
        assertNull(obxMessage.fileUniqueId)
        assertNull(obxMessage.thumbFileId)
        assertNull(obxMessage.language)
        assertNull(obxMessage.fsk)
        assertNull(obxMessage.posterFileId)
        assertNull(obxMessage.posterLocalPath)
    }

    @Test
    fun `round trip with existingMessage preserves all fields`() {
        val originalObx = createTestObxMessage(
            id = 1,
            chatId = 12345,
            messageId = 67890,
            fileId = 123,
            fileUniqueId = "unique_id",
            remoteId = "remote_123",
            title = "Test Movie",
            fileName = "movie.mp4",
            mimeType = "video/mp4",
            sizeBytes = 1024000,
            thumbFileId = 999,
            language = "de",
            fsk = 16,
            posterFileId = 888,
            posterLocalPath = "/poster.jpg",
        )

        val mediaItem = TelegramMappers.fromObxTelegramMessage(originalObx)
        val convertedObx = TelegramMappers.toObxTelegramMessage(
            mediaItem = mediaItem,
            existingId = 1,
            existingMessage = originalObx,
        )

        // Verify all fields including internal ones are preserved
        assertEquals(originalObx.id, convertedObx.id)
        assertEquals(originalObx.chatId, convertedObx.chatId)
        assertEquals(originalObx.messageId, convertedObx.messageId)
        assertEquals(originalObx.fileId, convertedObx.fileId)
        assertEquals(originalObx.fileUniqueId, convertedObx.fileUniqueId)
        assertEquals(originalObx.remoteId, convertedObx.remoteId)
        assertEquals(originalObx.title, convertedObx.title)
        assertEquals(originalObx.fileName, convertedObx.fileName)
        assertEquals(originalObx.mimeType, convertedObx.mimeType)
        assertEquals(originalObx.sizeBytes, convertedObx.sizeBytes)
        assertEquals(originalObx.thumbFileId, convertedObx.thumbFileId)
        assertEquals(originalObx.language, convertedObx.language)
        assertEquals(originalObx.fsk, convertedObx.fsk)
        assertEquals(originalObx.posterFileId, convertedObx.posterFileId)
        assertEquals(originalObx.posterLocalPath, convertedObx.posterLocalPath)
    }

    private fun createTestObxMessage(
        id: Long = 0,
        chatId: Long = 0,
        messageId: Long = 0,
        fileId: Int? = null,
        fileUniqueId: String? = null,
        remoteId: String? = null,
        title: String? = null,
        fileName: String? = null,
        caption: String? = null,
        mimeType: String? = null,
        sizeBytes: Long? = null,
        durationSecs: Int? = null,
        width: Int? = null,
        height: Int? = null,
        supportsStreaming: Boolean? = null,
        localPath: String? = null,
        thumbFileId: Int? = null,
        thumbLocalPath: String? = null,
        date: Long? = null,
        language: String? = null,
        fsk: Int? = null,
        posterFileId: Int? = null,
        posterLocalPath: String? = null,
        isSeries: Boolean = false,
        seriesName: String? = null,
        seasonNumber: Int? = null,
        episodeNumber: Int? = null,
        episodeTitle: String? = null,
        year: Int? = null,
        genres: String? = null,
        description: String? = null,
    ): ObxTelegramMessage {
        return ObxTelegramMessage(
            id = id,
            chatId = chatId,
            messageId = messageId,
            fileId = fileId,
            fileUniqueId = fileUniqueId,
            remoteId = remoteId,
            title = title,
            fileName = fileName,
            caption = caption,
            mimeType = mimeType,
            sizeBytes = sizeBytes,
            durationSecs = durationSecs,
            width = width,
            height = height,
            supportsStreaming = supportsStreaming,
            localPath = localPath,
            thumbFileId = thumbFileId,
            thumbLocalPath = thumbLocalPath,
            date = date,
            language = language,
            fsk = fsk,
            posterFileId = posterFileId,
            posterLocalPath = posterLocalPath,
            isSeries = isSeries,
            seriesName = seriesName,
            seasonNumber = seasonNumber,
            episodeNumber = episodeNumber,
            episodeTitle = episodeTitle,
            year = year,
            genres = genres,
            description = description,
        )
    }

    private fun createTestMediaItem(
        chatId: Long = 0,
        messageId: Long = 0,
        fileId: Int? = null,
        title: String = "",
    ): com.fishit.player.pipeline.telegram.model.TelegramMediaItem {
        return com.fishit.player.pipeline.telegram.model.TelegramMediaItem(
            chatId = chatId,
            messageId = messageId,
            fileId = fileId,
            title = title,
        )
    }
}
