package com.chris.m3usuite.telegram.repo

import com.chris.m3usuite.data.obx.ObxTelegramItem
import com.chris.m3usuite.telegram.domain.TelegramDocumentRef
import com.chris.m3usuite.telegram.domain.TelegramImageRef
import com.chris.m3usuite.telegram.domain.TelegramItem
import com.chris.m3usuite.telegram.domain.TelegramItemType
import com.chris.m3usuite.telegram.domain.TelegramMediaRef
import com.chris.m3usuite.telegram.domain.TelegramMetadata
import com.chris.m3usuite.telegram.domain.toDomain
import com.chris.m3usuite.telegram.domain.toObx
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Unit tests for TelegramItem <-> ObxTelegramItem round-trip mapping.
 *
 * Verifies that TelegramItem domain objects can be converted to ObjectBox entities
 * and back without data loss.
 */
class ObxTelegramItemRoundTripTest {
    @Test
    fun `movie item with video ref round-trips correctly`() {
        val original = createMovieItem()
        val obx = original.toObx()
        val roundTripped = obx.toDomain()

        // Identity
        assertEquals(original.chatId, roundTripped.chatId)
        assertEquals(original.anchorMessageId, roundTripped.anchorMessageId)
        assertEquals(original.type, roundTripped.type)

        // Video ref
        assertNotNull(roundTripped.videoRef)
        assertEquals(original.videoRef?.remoteId, roundTripped.videoRef?.remoteId)
        assertEquals(original.videoRef?.uniqueId, roundTripped.videoRef?.uniqueId)
        assertEquals(original.videoRef?.fileId, roundTripped.videoRef?.fileId)
        assertEquals(original.videoRef?.sizeBytes, roundTripped.videoRef?.sizeBytes)
        assertEquals(original.videoRef?.mimeType, roundTripped.videoRef?.mimeType)
        assertEquals(original.videoRef?.durationSeconds, roundTripped.videoRef?.durationSeconds)
        assertEquals(original.videoRef?.width, roundTripped.videoRef?.width)
        assertEquals(original.videoRef?.height, roundTripped.videoRef?.height)

        // Should not have document ref
        assertNull(roundTripped.documentRef)
    }

    @Test
    fun `movie item metadata round-trips correctly`() {
        val original = createMovieItem()
        val obx = original.toObx()
        val roundTripped = obx.toDomain()

        // Metadata
        assertEquals(original.metadata.title, roundTripped.metadata.title)
        assertEquals(original.metadata.originalTitle, roundTripped.metadata.originalTitle)
        assertEquals(original.metadata.year, roundTripped.metadata.year)
        assertEquals(original.metadata.lengthMinutes, roundTripped.metadata.lengthMinutes)
        assertEquals(original.metadata.fsk, roundTripped.metadata.fsk)
        assertEquals(original.metadata.productionCountry, roundTripped.metadata.productionCountry)
        assertEquals(original.metadata.collection, roundTripped.metadata.collection)
        assertEquals(original.metadata.director, roundTripped.metadata.director)
        assertEquals(original.metadata.tmdbRating, roundTripped.metadata.tmdbRating)
        assertEquals(original.metadata.tmdbUrl, roundTripped.metadata.tmdbUrl)
        assertEquals(original.metadata.isAdult, roundTripped.metadata.isAdult)
        assertEquals(original.metadata.genres, roundTripped.metadata.genres)
    }

    @Test
    fun `poster and backdrop refs round-trip correctly`() {
        val original = createMovieItem()
        val obx = original.toObx()
        val roundTripped = obx.toDomain()

        // Poster ref
        assertNotNull(roundTripped.posterRef)
        assertEquals(original.posterRef?.remoteId, roundTripped.posterRef?.remoteId)
        assertEquals(original.posterRef?.uniqueId, roundTripped.posterRef?.uniqueId)
        assertEquals(original.posterRef?.fileId, roundTripped.posterRef?.fileId)
        assertEquals(original.posterRef?.width, roundTripped.posterRef?.width)
        assertEquals(original.posterRef?.height, roundTripped.posterRef?.height)
        assertEquals(original.posterRef?.sizeBytes, roundTripped.posterRef?.sizeBytes)

        // Backdrop ref
        assertNotNull(roundTripped.backdropRef)
        assertEquals(original.backdropRef?.remoteId, roundTripped.backdropRef?.remoteId)
        assertEquals(original.backdropRef?.uniqueId, roundTripped.backdropRef?.uniqueId)
        assertEquals(original.backdropRef?.fileId, roundTripped.backdropRef?.fileId)
        assertEquals(original.backdropRef?.width, roundTripped.backdropRef?.width)
        assertEquals(original.backdropRef?.height, roundTripped.backdropRef?.height)
        assertEquals(original.backdropRef?.sizeBytes, roundTripped.backdropRef?.sizeBytes)
    }

    @Test
    fun `message references round-trip correctly`() {
        val original = createMovieItem()
        val obx = original.toObx()
        val roundTripped = obx.toDomain()

        assertEquals(original.textMessageId, roundTripped.textMessageId)
        assertEquals(original.photoMessageId, roundTripped.photoMessageId)
        assertEquals(original.createdAtIso, roundTripped.createdAtIso)
    }

    @Test
    fun `document item round-trips correctly`() {
        val original = createDocumentItem()
        val obx = original.toObx()
        val roundTripped = obx.toDomain()

        // Type
        assertEquals(TelegramItemType.RAR_ITEM, roundTripped.type)

        // Should not have video ref
        assertNull(roundTripped.videoRef)

        // Document ref
        assertNotNull(roundTripped.documentRef)
        assertEquals(original.documentRef?.remoteId, roundTripped.documentRef?.remoteId)
        assertEquals(original.documentRef?.uniqueId, roundTripped.documentRef?.uniqueId)
        assertEquals(original.documentRef?.fileId, roundTripped.documentRef?.fileId)
        assertEquals(original.documentRef?.sizeBytes, roundTripped.documentRef?.sizeBytes)
        assertEquals(original.documentRef?.mimeType, roundTripped.documentRef?.mimeType)
        assertEquals(original.documentRef?.fileName, roundTripped.documentRef?.fileName)
    }

    @Test
    fun `poster-only item round-trips correctly`() {
        val original = createPosterOnlyItem()
        val obx = original.toObx()
        val roundTripped = obx.toDomain()

        assertEquals(TelegramItemType.POSTER_ONLY, roundTripped.type)
        assertNull(roundTripped.videoRef)
        assertNull(roundTripped.documentRef)
        assertNotNull(roundTripped.posterRef)
    }

    @Test
    fun `item with null optional fields round-trips correctly`() {
        val original = createMinimalItem()
        val obx = original.toObx()
        val roundTripped = obx.toDomain()

        assertEquals(original.chatId, roundTripped.chatId)
        assertEquals(original.anchorMessageId, roundTripped.anchorMessageId)
        assertEquals(original.type, roundTripped.type)
        assertNull(roundTripped.posterRef)
        assertNull(roundTripped.backdropRef)
        assertNull(roundTripped.textMessageId)
        assertNull(roundTripped.photoMessageId)
    }

    @Test
    fun `genres serialize and deserialize correctly`() {
        val original = createMovieItem()
        val obx = original.toObx()

        // Verify genres are serialized
        assertNotNull(obx.genresJson)
        assert(obx.genresJson!!.contains("Action"))
        assert(obx.genresJson!!.contains("Sci-Fi"))

        val roundTripped = obx.toDomain()
        assertEquals(listOf("Action", "Sci-Fi", "Drama"), roundTripped.metadata.genres)
    }

    @Test
    fun `empty genres list round-trips correctly`() {
        val original =
            createMovieItem().copy(
                metadata =
                    createMovieItem().metadata.copy(
                        genres = emptyList(),
                    ),
            )
        val obx = original.toObx()
        val roundTripped = obx.toDomain()

        assertEquals(emptyList<String>(), roundTripped.metadata.genres)
    }

    @Test
    fun `ObxTelegramItem default values are correct`() {
        val obx = ObxTelegramItem()

        assertEquals(0L, obx.id)
        assertEquals(0L, obx.chatId)
        assertEquals(0L, obx.anchorMessageId)
        assertEquals("", obx.itemType)
        assertEquals(false, obx.isAdult)
        assertNull(obx.videoRemoteId)
        assertNull(obx.documentRemoteId)
    }

    @Test
    fun `item type enum converts correctly`() {
        for (type in TelegramItemType.entries) {
            val item =
                when (type) {
                    TelegramItemType.MOVIE,
                    TelegramItemType.SERIES_EPISODE,
                    TelegramItemType.CLIP,
                    -> createMovieItem().copy(type = type)
                    TelegramItemType.AUDIOBOOK,
                    TelegramItemType.RAR_ITEM,
                    -> createDocumentItem().copy(type = type)
                    TelegramItemType.POSTER_ONLY -> createPosterOnlyItem()
                }

            val obx = item.toObx()
            assertEquals(type.name, obx.itemType)

            val roundTripped = obx.toDomain()
            assertEquals(type, roundTripped.type)
        }
    }

    // Helper functions to create test items

    private fun createMovieItem(): TelegramItem =
        TelegramItem(
            chatId = -1001234567890L,
            anchorMessageId = 12345L,
            type = TelegramItemType.MOVIE,
            videoRef =
                TelegramMediaRef(
                    remoteId = "BQAabc123",
                    uniqueId = "AgAabc123xyz",
                    fileId = 42,
                    sizeBytes = 1_500_000_000L,
                    mimeType = "video/mp4",
                    durationSeconds = 7200,
                    width = 1920,
                    height = 1080,
                ),
            documentRef = null,
            posterRef =
                TelegramImageRef(
                    remoteId = "AgAposter123",
                    uniqueId = "AgAposter123xyz",
                    fileId = 100,
                    width = 500,
                    height = 750,
                    sizeBytes = 50_000L,
                ),
            backdropRef =
                TelegramImageRef(
                    remoteId = "AgAbackdrop123",
                    uniqueId = "AgAbackdrop123xyz",
                    fileId = 101,
                    width = 1920,
                    height = 1080,
                    sizeBytes = 200_000L,
                ),
            textMessageId = 12344L,
            photoMessageId = 12343L,
            createdAtIso = "2024-01-15T12:00:00Z",
            metadata =
                TelegramMetadata(
                    title = "Test Movie",
                    originalTitle = "Test Movie Original",
                    year = 2024,
                    lengthMinutes = 120,
                    fsk = 12,
                    productionCountry = "USA",
                    collection = "Test Collection",
                    director = "Test Director",
                    tmdbRating = 7.5,
                    genres = listOf("Action", "Sci-Fi", "Drama"),
                    tmdbUrl = "https://www.themoviedb.org/movie/12345",
                    isAdult = false,
                ),
        )

    private fun createDocumentItem(): TelegramItem =
        TelegramItem(
            chatId = -1001234567890L,
            anchorMessageId = 22345L,
            type = TelegramItemType.RAR_ITEM,
            videoRef = null,
            documentRef =
                TelegramDocumentRef(
                    remoteId = "BQAdoc123",
                    uniqueId = "AgAdoc123xyz",
                    fileId = 200,
                    sizeBytes = 5_000_000_000L,
                    mimeType = "application/x-rar-compressed",
                    fileName = "archive.part1.rar",
                ),
            posterRef =
                TelegramImageRef(
                    remoteId = "AgAposter456",
                    uniqueId = "AgAposter456xyz",
                    fileId = 201,
                    width = 500,
                    height = 750,
                    sizeBytes = 50_000L,
                ),
            backdropRef = null,
            textMessageId = 22344L,
            photoMessageId = 22343L,
            createdAtIso = "2024-01-16T12:00:00Z",
            metadata =
                TelegramMetadata(
                    title = "Archive Content",
                    originalTitle = null,
                    year = null,
                    lengthMinutes = null,
                    fsk = null,
                    productionCountry = null,
                    collection = null,
                    director = null,
                    tmdbRating = null,
                    genres = emptyList(),
                    tmdbUrl = null,
                    isAdult = false,
                ),
        )

    private fun createPosterOnlyItem(): TelegramItem =
        TelegramItem(
            chatId = -1001234567890L,
            anchorMessageId = 32345L,
            type = TelegramItemType.POSTER_ONLY,
            videoRef = null,
            documentRef = null,
            posterRef =
                TelegramImageRef(
                    remoteId = "AgAposter789",
                    uniqueId = "AgAposter789xyz",
                    fileId = 300,
                    width = 500,
                    height = 750,
                    sizeBytes = 50_000L,
                ),
            backdropRef = null,
            textMessageId = 32344L,
            photoMessageId = 32343L,
            createdAtIso = "2024-01-17T12:00:00Z",
            metadata =
                TelegramMetadata(
                    title = "Poster Only Item",
                    originalTitle = null,
                    year = 2024,
                    lengthMinutes = null,
                    fsk = null,
                    productionCountry = null,
                    collection = null,
                    director = null,
                    tmdbRating = null,
                    genres = listOf("Drama"),
                    tmdbUrl = null,
                    isAdult = false,
                ),
        )

    private fun createMinimalItem(): TelegramItem =
        TelegramItem(
            chatId = -1001234567890L,
            anchorMessageId = 42345L,
            type = TelegramItemType.CLIP,
            videoRef =
                TelegramMediaRef(
                    remoteId = "BQAmin123",
                    uniqueId = "AgAmin123xyz",
                    fileId = null,
                    sizeBytes = 10_000_000L,
                    mimeType = null,
                    durationSeconds = null,
                    width = null,
                    height = null,
                ),
            documentRef = null,
            posterRef = null,
            backdropRef = null,
            textMessageId = null,
            photoMessageId = null,
            createdAtIso = "2024-01-18T12:00:00Z",
            metadata =
                TelegramMetadata(
                    title = "Minimal Clip",
                    originalTitle = null,
                    year = null,
                    lengthMinutes = null,
                    fsk = null,
                    productionCountry = null,
                    collection = null,
                    director = null,
                    tmdbRating = null,
                    genres = emptyList(),
                    tmdbUrl = null,
                    isAdult = false,
                ),
        )
}
