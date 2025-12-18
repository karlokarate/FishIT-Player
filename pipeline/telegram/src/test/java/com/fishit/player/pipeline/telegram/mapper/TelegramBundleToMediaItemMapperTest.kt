package com.fishit.player.pipeline.telegram.mapper

import com.fishit.player.infra.transport.telegram.api.TgContent
import com.fishit.player.infra.transport.telegram.api.TgMessage
import com.fishit.player.infra.transport.telegram.api.TgPhotoSize
import com.fishit.player.pipeline.telegram.grouper.StructuredMetadata
import com.fishit.player.pipeline.telegram.grouper.TelegramMessageBundle
import com.fishit.player.pipeline.telegram.grouper.TelegramStructuredMetadataExtractor
import com.fishit.player.pipeline.telegram.model.TelegramBundleType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [TelegramBundleToMediaItemMapper].
 *
 * Per TELEGRAM_STRUCTURED_BUNDLES_CONTRACT.md Section 6:
 * - MM-001: Multi-video lossless emission
 * - MM-002: Multi-video shared externalIds
 * - MM-003: Poster selection max pixel area
 */
class TelegramBundleToMediaItemMapperTest {

    private val metadataExtractor = TelegramStructuredMetadataExtractor()
    private val mapper = TelegramBundleToMediaItemMapper(metadataExtractor)

    // ========== Helper Functions ==========

    private fun createVideoMessage(
        messageId: Long,
        chatId: Long = 123L,
        timestamp: Long = 1000L,
        fileName: String = "movie.mkv",
        remoteId: String = "remote_$messageId",
        duration: Int = 7200,
        width: Int = 1920,
        height: Int = 1080,
        fileSize: Long = 1_000_000_000L,
        caption: String? = null,
    ): TgMessage = TgMessage(
        messageId = messageId,
        chatId = chatId,
        date = timestamp,
        content = TgContent.Video(
            fileId = messageId.toInt(),
            remoteId = remoteId,
            fileName = fileName,
            mimeType = "video/x-matroska",
            duration = duration,
            width = width,
            height = height,
            fileSize = fileSize,
            supportsStreaming = true,
            caption = caption,
        ),
    )

    private fun createTextMessage(
        messageId: Long,
        chatId: Long = 123L,
        timestamp: Long = 1000L,
        text: String,
    ): TgMessage = TgMessage(
        messageId = messageId,
        chatId = chatId,
        date = timestamp,
        content = TgContent.Text(text = text),
    )

    private fun createPhotoMessage(
        messageId: Long,
        chatId: Long = 123L,
        timestamp: Long = 1000L,
        sizes: List<TgPhotoSize>,
        caption: String? = null,
    ): TgMessage = TgMessage(
        messageId = messageId,
        chatId = chatId,
        date = timestamp,
        content = TgContent.Photo(sizes = sizes, caption = caption),
    )

    private fun createPhotoSize(
        width: Int,
        height: Int,
        remoteId: String = "photo_${width}x$height",
        fileSize: Long = (width * height).toLong(),
    ): TgPhotoSize = TgPhotoSize(
        fileId = width * height,
        remoteId = remoteId,
        width = width,
        height = height,
        fileSize = fileSize,
    )

    // ========== Single Video Bundle Tests ==========

    @Test
    fun `mapBundle - single video returns one item`() {
        val video = createVideoMessage(messageId = 100)
        val bundle = TelegramMessageBundle.single(video)

        val items = mapper.mapBundle(bundle)

        assertEquals(1, items.size)
        assertEquals(100L, items[0].messageId)
        assertEquals(TelegramBundleType.SINGLE, items[0].bundleType)
    }

    @Test
    fun `mapBundle - video properties are correctly mapped`() {
        val video = createVideoMessage(
            messageId = 100,
            chatId = 456,
            fileName = "Movie.2020.1080p.BluRay.mkv",
            duration = 7200,
            width = 1920,
            height = 1080,
            fileSize = 5_000_000_000L,
            remoteId = "stable_remote_id",
        )
        val bundle = TelegramMessageBundle.single(video)

        val items = mapper.mapBundle(bundle)
        val item = items[0]

        assertEquals(456L, item.chatId)
        assertEquals(100L, item.messageId)
        assertEquals("Movie.2020.1080p.BluRay.mkv", item.fileName)
        assertEquals("Movie.2020.1080p.BluRay.mkv", item.title)
        assertEquals(7200, item.durationSecs)
        assertEquals(1920, item.width)
        assertEquals(1080, item.height)
        assertEquals(5_000_000_000L, item.sizeBytes)
        assertEquals("stable_remote_id", item.remoteId)
        assertEquals(true, item.supportsStreaming)
    }

    @Test
    fun `mapBundle - uses caption as title fallback`() {
        val video = TgMessage(
            messageId = 100,
            chatId = 123,
            date = 1000L,
            content = TgContent.Video(
                fileId = 1,
                remoteId = "remote",
                fileName = null,
                mimeType = "video/mp4",
                duration = 120,
                width = 1920,
                height = 1080,
                fileSize = 1000,
                caption = "This is a great movie!",
            ),
        )
        val bundle = TelegramMessageBundle.single(video)

        val items = mapper.mapBundle(bundle)

        assertEquals("This is a great movie!", items[0].title)
    }

    // ========== MM-001: Multi-video Lossless Emission ==========

    @Test
    fun `mapBundle - multi-video bundle emits one item per video (MM-001)`() {
        val video1 = createVideoMessage(messageId = 100, fileName = "video1.mkv")
        val video2 = createVideoMessage(messageId = 101, fileName = "video2.mkv")
        val video3 = createVideoMessage(messageId = 102, fileName = "video3.mkv")

        val bundle = TelegramMessageBundle(
            timestamp = 1000L,
            messages = listOf(video1, video2, video3),
            bundleType = TelegramBundleType.SINGLE, // 3 videos, no text/photo
            videoMessages = listOf(video1, video2, video3),
            textMessage = null,
            photoMessage = null,
        )

        val items = mapper.mapBundle(bundle)

        assertEquals("Lossless emission: 3 videos → 3 items", 3, items.size)
        assertEquals(100L, items[0].messageId)
        assertEquals(101L, items[1].messageId)
        assertEquals(102L, items[2].messageId)
    }

    // ========== MM-002: Multi-video Shared externalIds ==========

    @Test
    fun `mapBundle - all videos share same structured metadata (MM-002)`() {
        val structuredText = """
            tmdbUrl: https://www.themoviedb.org/movie/550-fight-club
            tmdbRating: 8.4
            year: 1999
            fsk: 18
            genres: ["Drama", "Thriller"]
            director: David Fincher
            lengthMinutes: 139
        """.trimIndent()

        val text = createTextMessage(messageId = 100, text = structuredText)
        val video1 = createVideoMessage(messageId = 101, fileName = "fightclub_720p.mkv")
        val video2 = createVideoMessage(messageId = 102, fileName = "fightclub_1080p.mkv")

        val bundle = TelegramMessageBundle(
            timestamp = 1000L,
            messages = listOf(text, video1, video2),
            bundleType = TelegramBundleType.COMPACT_2ER,
            videoMessages = listOf(video1, video2),
            textMessage = text,
            photoMessage = null,
        )

        val items = mapper.mapBundle(bundle)

        assertEquals(2, items.size)

        // Both items share the same structured metadata
        items.forEach { item ->
            assertEquals("TMDB ID shared", 550, item.structuredTmdbId)
            assertEquals("Rating shared", 8.4, item.structuredRating!!, 0.01)
            assertEquals("Year shared", 1999, item.structuredYear)
            assertEquals("FSK shared", 18, item.structuredFsk)
            assertTrue("Genres shared", item.structuredGenres?.contains("Drama") == true)
            assertEquals("Director shared", "David Fincher", item.structuredDirector)
            assertEquals("Length shared", 139, item.structuredLengthMinutes)
        }

        // Each item has unique video properties
        assertEquals("fightclub_720p.mkv", items[0].fileName)
        assertEquals("fightclub_1080p.mkv", items[1].fileName)
    }

    // ========== MM-003: Poster Selection Max Pixel Area ==========

    @Test
    fun `selectBestPhotoSize - selects max pixel area (MM-003)`() {
        val sizes = listOf(
            createPhotoSize(width = 320, height = 240),    // 76,800 px
            createPhotoSize(width = 1280, height = 720),   // 921,600 px
            createPhotoSize(width = 800, height = 600),    // 480,000 px
        )

        val best = TelegramBundleToMediaItemMapper.selectBestPhotoSize(sizes)

        assertNotNull(best)
        assertEquals(1280, best!!.width)
        assertEquals(720, best.height)
    }

    @Test
    fun `selectBestPhotoSize - tie breaking by height then width`() {
        // Same pixel area (1,000,000), different dimensions
        val sizes = listOf(
            createPhotoSize(width = 1000, height = 1000, remoteId = "square"),
            createPhotoSize(width = 2000, height = 500, remoteId = "wide"),
            createPhotoSize(width = 500, height = 2000, remoteId = "tall"),
        )

        val best = TelegramBundleToMediaItemMapper.selectBestPhotoSize(sizes)

        // Tie: largest height → "tall" (500x2000)
        assertNotNull(best)
        assertEquals("tall", best!!.remoteId)
        assertEquals(500, best.width)
        assertEquals(2000, best.height)
    }

    @Test
    fun `selectBestPhotoSize - empty list returns null`() {
        val best = TelegramBundleToMediaItemMapper.selectBestPhotoSize(emptyList())
        assertNull(best)
    }

    @Test
    fun `mapBundle - poster attached to all emitted items`() {
        val photoSizes = listOf(
            createPhotoSize(width = 320, height = 480, remoteId = "small"),
            createPhotoSize(width = 1920, height = 1080, remoteId = "large"),
        )
        val photo = createPhotoMessage(messageId = 100, sizes = photoSizes)
        val video1 = createVideoMessage(messageId = 101)
        val video2 = createVideoMessage(messageId = 102)

        val bundle = TelegramMessageBundle(
            timestamp = 1000L,
            messages = listOf(photo, video1, video2),
            bundleType = TelegramBundleType.COMPACT_2ER,
            videoMessages = listOf(video1, video2),
            textMessage = null,
            photoMessage = photo,
        )

        val items = mapper.mapBundle(bundle)

        assertEquals(2, items.size)
        items.forEach { item ->
            assertEquals("large", item.thumbRemoteId)
            assertEquals(1920, item.thumbnailWidth)
            assertEquals(1080, item.thumbnailHeight)
        }
    }

    // ========== FULL_3ER Bundle Tests ==========

    @Test
    fun `mapBundle - FULL_3ER bundle with all components`() {
        val structuredText = """
            tmdbUrl: https://www.themoviedb.org/movie/12345
            year: 2020
            fsk: 12
        """.trimIndent()

        val photoSizes = listOf(createPhotoSize(width = 1000, height = 1500))
        val photo = createPhotoMessage(messageId = 100, sizes = photoSizes)
        val text = createTextMessage(messageId = 101, text = structuredText)
        val video = createVideoMessage(messageId = 102)

        val bundle = TelegramMessageBundle(
            timestamp = 1000L,
            messages = listOf(photo, text, video),
            bundleType = TelegramBundleType.FULL_3ER,
            videoMessages = listOf(video),
            textMessage = text,
            photoMessage = photo,
        )

        val items = mapper.mapBundle(bundle)

        assertEquals(1, items.size)
        val item = items[0]

        assertEquals(TelegramBundleType.FULL_3ER, item.bundleType)
        assertEquals(12345, item.structuredTmdbId)
        assertEquals(2020, item.structuredYear)
        assertEquals(12, item.structuredFsk)
        assertEquals(photoSizes[0].remoteId, item.thumbRemoteId)
        assertEquals(101L, item.textMessageId)
        assertEquals(100L, item.photoMessageId)
    }

    // ========== Empty/Edge Case Tests ==========

    @Test
    fun `mapBundle - empty video list returns empty`() {
        val bundle = TelegramMessageBundle(
            timestamp = 1000L,
            messages = emptyList(),
            bundleType = TelegramBundleType.SINGLE,
            videoMessages = emptyList(),
            textMessage = null,
            photoMessage = null,
        )

        val items = mapper.mapBundle(bundle)

        assertTrue(items.isEmpty())
    }

    @Test
    fun `mapBundle - without text message has no structured metadata`() {
        val video = createVideoMessage(messageId = 100)
        val bundle = TelegramMessageBundle.single(video)

        val items = mapper.mapBundle(bundle)

        assertEquals(1, items.size)
        assertNull(items[0].structuredTmdbId)
        assertNull(items[0].structuredYear)
        assertNull(items[0].structuredFsk)
    }

    @Test
    fun `mapBundle - text message without structured fields`() {
        val text = createTextMessage(messageId = 100, text = "Just some random text")
        val video = createVideoMessage(messageId = 101)

        val bundle = TelegramMessageBundle(
            timestamp = 1000L,
            messages = listOf(text, video),
            bundleType = TelegramBundleType.COMPACT_2ER,
            videoMessages = listOf(video),
            textMessage = text,
            photoMessage = null,
        )

        val items = mapper.mapBundle(bundle)

        assertEquals(1, items.size)
        assertNull(items[0].structuredTmdbId)
    }

    // ========== mapBundles convenience method ==========

    @Test
    fun `mapBundles - processes multiple bundles`() {
        val video1 = createVideoMessage(messageId = 100)
        val video2 = createVideoMessage(messageId = 200)
        val video3 = createVideoMessage(messageId = 201)

        val bundle1 = TelegramMessageBundle.single(video1)
        val bundle2 = TelegramMessageBundle(
            timestamp = 2000L,
            messages = listOf(video2, video3),
            bundleType = TelegramBundleType.SINGLE,
            videoMessages = listOf(video2, video3),
            textMessage = null,
            photoMessage = null,
        )

        val items = mapper.mapBundles(listOf(bundle1, bundle2))

        assertEquals(3, items.size)
        assertEquals(100L, items[0].messageId)
        assertEquals(200L, items[1].messageId)
        assertEquals(201L, items[2].messageId)
    }

    // ========== convertPhotoSizes Tests ==========

    @Test
    fun `convertPhotoSizes - converts TgPhotoSize to TelegramPhotoSize`() {
        val tgSizes = listOf(
            TgPhotoSize(fileId = 1, remoteId = "r1", width = 320, height = 240, fileSize = 10000),
            TgPhotoSize(fileId = 2, remoteId = "r2", width = 1920, height = 1080, fileSize = 100000),
        )

        val converted = TelegramBundleToMediaItemMapper.convertPhotoSizes(tgSizes)

        assertEquals(2, converted.size)
        assertEquals("r1", converted[0].remoteId)
        assertEquals(320, converted[0].width)
        assertEquals(240, converted[0].height)
        assertEquals(10000L, converted[0].sizeBytes)
        assertEquals("r2", converted[1].remoteId)
        assertEquals(1920, converted[1].width)
        assertEquals(1080, converted[1].height)
    }
}
