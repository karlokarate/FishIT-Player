package com.fishit.player.pipeline.telegram.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * Tests for new Telegram DTOs based on real export analysis.
 *
 * Validates TelegramMediaType, TelegramPhotoSize, and TelegramMetadataMessage.
 */
class TelegramDtosTest {
    @Test
    fun `TelegramPhotoSize holds photo dimensions and file IDs`() {
        val photoSize =
            TelegramPhotoSize(
                width = 1707,
                height = 2560,
                fileId = "AgACAgQAAx0CRlwYIgACbTlpKbJSWU3nI6QBGruLlim8mT2bqAACCgtrG1A4NFGAjRHYPL9biAEAAwIAA3gAAzgE",
                fileUniqueId = "AQADCgtrG1A4NFF9",
                sizeBytes = 81037,
            )

        assertEquals(1707, photoSize.width)
        assertEquals(2560, photoSize.height)
        assertNotNull(photoSize.fileId)
        assertNotNull(photoSize.fileUniqueId)
        assertEquals(81037L, photoSize.sizeBytes)
    }

    @Test
    fun `TelegramMetadataMessage holds raw metadata fields`() {
        val metadataMsg =
            TelegramMetadataMessage(
                chatId = -1001180440610,
                messageId = 29320282112,
                date = 1764294604,
                title = "Das Ende der Welt - Die 12 Prophezeiungen der Maya",
                originalTitle = "The 12 Disasters of Christmas",
                year = 2012,
                lengthMinutes = 89,
                fsk = 12,
                productionCountry = "Kanada",
                director = "Steven R. Monroe",
                genres = listOf("TV-Film", "Action", "Science Fiction"),
                tmdbUrl = "https://www.themoviedb.org/movie/149722-The-12-Disasters-of-Christmas?language=de-DE",
                tmdbRating = 4.456,
                rawText = "󠄿Titel: Das Ende der Welt...",
            )

        assertEquals("Das Ende der Welt - Die 12 Prophezeiungen der Maya", metadataMsg.title)
        assertEquals("The 12 Disasters of Christmas", metadataMsg.originalTitle)
        assertEquals(2012, metadataMsg.year)
        assertEquals(89, metadataMsg.lengthMinutes)
        assertEquals(12, metadataMsg.fsk)
        assertEquals("Kanada", metadataMsg.productionCountry)
        assertEquals(3, metadataMsg.genres.size)
        assertEquals("TV-Film", metadataMsg.genres[0])
        assertNotNull(metadataMsg.tmdbUrl)
        assertEquals(4.456, metadataMsg.tmdbRating!!, 0.001)
    }

    @Test
    fun `TelegramMetadataMessage handles missing optional fields`() {
        val metadataMsg =
            TelegramMetadataMessage(
                chatId = -1001414928982,
                messageId = 802160640,
                date = 1759584864,
                title = "The Bondsman",
                rawText = "Some text",
            )

        assertEquals("The Bondsman", metadataMsg.title)
        assertEquals(null, metadataMsg.year)
        assertEquals(null, metadataMsg.lengthMinutes)
        assertEquals(null, metadataMsg.fsk)
        assertEquals(null, metadataMsg.tmdbUrl)
        assertEquals(0, metadataMsg.genres.size)
    }

    @Test
    fun `TelegramMediaType enum has all expected values`() {
        val types = TelegramMediaType.values()

        assertEquals(6, types.size)
        assert(types.contains(TelegramMediaType.VIDEO))
        assert(types.contains(TelegramMediaType.DOCUMENT))
        assert(types.contains(TelegramMediaType.AUDIO))
        assert(types.contains(TelegramMediaType.PHOTO))
        assert(types.contains(TelegramMediaType.TEXT_METADATA))
        assert(types.contains(TelegramMediaType.OTHER))
    }

    @Test
    fun `TelegramMediaItem with photoSizes list`() {
        val sizes =
            listOf(
                TelegramPhotoSize(1707, 2560, "fileId1", "uniqueId1", 926278),
                TelegramPhotoSize(853, 1280, "fileId2", "uniqueId2", 255472),
                TelegramPhotoSize(213, 320, "fileId3", "uniqueId3", 23401),
            )

        val mediaItem =
            TelegramMediaItem(
                chatId = -1001414928982,
                messageId = 801112064,
                mediaType = TelegramMediaType.PHOTO,
                photoSizes = sizes,
            )

        assertEquals(TelegramMediaType.PHOTO, mediaItem.mediaType)
        assertEquals(3, mediaItem.photoSizes.size)
        assertEquals(1707, mediaItem.photoSizes[0].width)
        assertEquals(853, mediaItem.photoSizes[1].width)
        assertEquals(213, mediaItem.photoSizes[2].width)
    }

    @Test
    fun `TelegramMediaItem preserves scene-style fileName`() {
        val mediaItem =
            TelegramMediaItem(
                chatId = -1001180440610,
                messageId = 29321330688,
                mediaType = TelegramMediaType.VIDEO,
                fileName = "Das.Ende.der.Welt.2012.1080p.BluRay.x264-AWESOME.mkv",
                mimeType = "video/x-matroska",
                caption = "Das Ende der Welt - Die 12 Prophezeiungen der Maya - 2012",
            )

        // Filename preserved exactly (no cleaning)
        assertEquals("Das.Ende.der.Welt.2012.1080p.BluRay.x264-AWESOME.mkv", mediaItem.fileName)
        // Caption also preserved as-is
        assertEquals("Das Ende der Welt - Die 12 Prophezeiungen der Maya - 2012", mediaItem.caption)
    }

    @Test
    fun `TelegramMediaItem with thumbnail metadata fields`() {
        val mediaItem =
            TelegramMediaItem(
                chatId = -1001180440610,
                messageId = 29321330688,
                mediaType = TelegramMediaType.VIDEO,
                thumbnailFileId = "AAMCAgADHQJGXBgiAAJtO2kpslLHK8bxgsIVnj2Bn_5qfdQyAAL7hgAC1ZoAAUkQTW-gfd-o5AEAB20AAzgE",
                thumbnailUniqueId = "AQAD-4YAAtWaAAFJcg",
                thumbnailWidth = 320,
                thumbnailHeight = 179,
            )

        assertNotNull(mediaItem.thumbnailFileId)
        assertNotNull(mediaItem.thumbnailUniqueId)
        assertEquals(320, mediaItem.thumbnailWidth)
        assertEquals(179, mediaItem.thumbnailHeight)
    }
}
