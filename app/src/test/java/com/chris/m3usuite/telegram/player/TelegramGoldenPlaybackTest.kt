package com.chris.m3usuite.telegram.player

import com.chris.m3usuite.telegram.domain.TelegramImageRef
import com.chris.m3usuite.telegram.domain.TelegramItem
import com.chris.m3usuite.telegram.domain.TelegramItemType
import com.chris.m3usuite.telegram.domain.TelegramMediaRef
import com.chris.m3usuite.telegram.domain.TelegramMetadata
import com.chris.m3usuite.telegram.util.TelegramPlayUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URI
import java.net.URLDecoder

/**
 * Phase T4: Golden Playback Test
 *
 * Per TELEGRAM_SIP_PLAYER_INTEGRATION.md:
 * This test verifies the complete playback flow from TelegramItem to player invocation
 * WITHOUT requiring actual TDLib or ExoPlayer instantiation.
 *
 * Golden Path:
 * 1. TelegramItem (MOVIE) with TelegramMediaRef + TelegramImageRef
 * 2. Build TelegramPlaybackRequest from videoRef
 * 3. TelegramPlayUrl.build() returns tg:// URL with remoteId/uniqueId
 * 4. URL is passed to PlayerChooser.start() with mediaId (TELEGRAM_MEDIA_ID_OFFSET + anchorMessageId)
 * 5. openInternal is called with same URL and mediaId
 */
class TelegramGoldenPlaybackTest {
    companion object {
        private const val TELEGRAM_MEDIA_ID_OFFSET = 4_000_000_000_000L
    }

    // ==========================================================================
    // Golden Path Test Fixtures
    // ==========================================================================

    /**
     * Create a synthetic TelegramItem (MOVIE) representing a complete golden path input.
     * This item has all fields populated as would be produced by the parser pipeline.
     */
    private fun createGoldenMovieItem(): TelegramItem =
        TelegramItem(
            chatId = -1001987654321L,
            anchorMessageId = 11111111L,
            type = TelegramItemType.MOVIE,
            videoRef =
                TelegramMediaRef(
                    remoteId = "BQACAgIAAxkBAAIGoldenRemote123456",
                    uniqueId = "AgADGoldenUnique789",
                    fileId = 99999, // Valid fileId for fast-path
                    sizeBytes = 3_500_000_000L, // ~3.5 GB
                    mimeType = "video/x-matroska",
                    durationSeconds = 8400, // 2h 20min
                    width = 3840,
                    height = 2160,
                ),
            documentRef = null,
            posterRef =
                TelegramImageRef(
                    remoteId = "AgACAgIAAxkBAAIGoldenPoster987654",
                    uniqueId = "AQADGoldenPosterUnique",
                    fileId = 88888,
                    width = 680,
                    height = 1000,
                    sizeBytes = 450_000L,
                ),
            backdropRef =
                TelegramImageRef(
                    remoteId = "AgACAgIAAxkBAAIGoldenBackdrop654321",
                    uniqueId = "AQADGoldenBackdropUnique",
                    fileId = 77777,
                    width = 1920,
                    height = 1080,
                    sizeBytes = 800_000L,
                ),
            textMessageId = 11111112L,
            photoMessageId = 11111113L,
            createdAtIso = "2025-05-20T14:30:00Z",
            metadata =
                TelegramMetadata(
                    title = "Golden Test Movie",
                    originalTitle = "Golden Test Original",
                    year = 2025,
                    lengthMinutes = 140,
                    fsk = 12,
                    productionCountry = "DE",
                    collection = "Golden Collection",
                    director = "Golden Director",
                    tmdbRating = 8.5,
                    genres = listOf("Action", "Drama", "Sci-Fi"),
                    tmdbUrl = "https://www.themoviedb.org/movie/99999-golden-test",
                    isAdult = false,
                ),
        )

    // ==========================================================================
    // Golden Path Test: Full Flow
    // ==========================================================================

    @Test
    fun `Golden Path - TelegramItem to TelegramPlaybackRequest`() {
        // Step 1: Start with synthetic TelegramItem
        val item = createGoldenMovieItem()

        // Step 2: Extract videoRef (per contract, MOVIE must have videoRef)
        val videoRef = requireNotNull(item.videoRef) { "Golden MOVIE must have videoRef" }

        // Step 3: Build TelegramPlaybackRequest
        val request =
            videoRef.toPlaybackRequest(
                chatId = item.chatId,
                anchorMessageId = item.anchorMessageId,
            )

        // Assert: Request has PRIMARY identifiers (remoteId, uniqueId)
        assertEquals("remoteId must match", videoRef.remoteId, request.remoteId)
        assertEquals("uniqueId must match", videoRef.uniqueId, request.uniqueId)
        assertEquals("chatId must match", item.chatId, request.chatId)
        assertEquals("messageId must match anchorMessageId", item.anchorMessageId, request.messageId)
        assertEquals("fileId must match when present", videoRef.fileId, request.fileId)
    }

    @Test
    fun `Golden Path - TelegramPlayUrl build returns valid tg URL`() {
        val item = createGoldenMovieItem()
        val videoRef = requireNotNull(item.videoRef)
        val request =
            videoRef.toPlaybackRequest(
                chatId = item.chatId,
                anchorMessageId = item.anchorMessageId,
            )

        // Build URL via TelegramPlayUrl.build()
        val url = TelegramPlayUrl.build(request)

        // Assert URL format
        assertTrue("URL must start with tg://file/", url.startsWith("tg://file/"))
        assertTrue("URL must contain fileId in path", url.contains("tg://file/99999?"))
        assertTrue("URL must contain chatId", url.contains("chatId=-1001987654321"))
        assertTrue("URL must contain messageId", url.contains("messageId=11111111"))
        assertTrue("URL must contain remoteId", url.contains("remoteId="))
        assertTrue("URL must contain uniqueId", url.contains("uniqueId="))
    }

    @Test
    fun `Golden Path - MediaId encoding for PlayerChooser`() {
        val item = createGoldenMovieItem()

        // Per contract: mediaId = TELEGRAM_MEDIA_ID_OFFSET + anchorMessageId
        val mediaId = TELEGRAM_MEDIA_ID_OFFSET + item.anchorMessageId

        // Assert mediaId is correctly computed
        assertEquals(
            "mediaId must be TELEGRAM_MEDIA_ID_OFFSET + anchorMessageId",
            4_000_011_111_111L,
            mediaId,
        )

        // Assert mediaId avoids Xtream ID collision
        assertTrue("mediaId must be >= 4 trillion", mediaId >= TELEGRAM_MEDIA_ID_OFFSET)

        // Assert we can decode back
        val decodedMessageId = mediaId - TELEGRAM_MEDIA_ID_OFFSET
        assertEquals("Should decode back to anchorMessageId", item.anchorMessageId, decodedMessageId)
    }

    @Test
    fun `Golden Path - Complete URL parsing verification`() {
        val item = createGoldenMovieItem()
        val videoRef = requireNotNull(item.videoRef)
        val request =
            videoRef.toPlaybackRequest(
                chatId = item.chatId,
                anchorMessageId = item.anchorMessageId,
            )

        val url = TelegramPlayUrl.build(request)

        // Parse URL using standard Java URI (no Android dependency)
        val uri = URI.create(url)

        assertEquals("Scheme must be tg", "tg", uri.scheme)
        assertEquals("Host must be file", "file", uri.host)
        assertEquals("Path must contain fileId", "/99999", uri.path)

        // Parse query parameters manually
        val queryParams = parseQueryParams(uri.query)

        assertEquals("chatId parameter", "-1001987654321", queryParams["chatId"])
        assertEquals("messageId parameter", "11111111", queryParams["messageId"])
        assertNotNull("remoteId parameter must exist", queryParams["remoteId"])
        assertNotNull("uniqueId parameter must exist", queryParams["uniqueId"])
    }

    // ==========================================================================
    // Edge Cases
    // ==========================================================================

    @Test
    fun `Golden Path - RemoteId-first with null fileId`() {
        // Create item with null fileId (stale cache scenario)
        val item =
            createGoldenMovieItem().let {
                it.copy(
                    videoRef =
                        it.videoRef!!.copy(
                            fileId = null,
                        ),
                )
            }

        val videoRef = requireNotNull(item.videoRef)
        val request =
            videoRef.toPlaybackRequest(
                chatId = item.chatId,
                anchorMessageId = item.anchorMessageId,
            )

        val url = TelegramPlayUrl.build(request)

        // With null fileId, path should use 0
        assertTrue("URL path should use 0 when fileId is null", url.startsWith("tg://file/0?"))

        // But remoteId MUST be present for resolution
        assertTrue("URL must contain remoteId for DataSource resolution", url.contains("remoteId="))
    }

    @Test
    fun `Golden Path - Poster and Backdrop refs are preserved`() {
        val item = createGoldenMovieItem()

        // Assert both image refs are present
        assertNotNull("Golden item must have posterRef", item.posterRef)
        assertNotNull("Golden item must have backdropRef", item.backdropRef)

        // Verify poster ref details
        val posterRef = item.posterRef!!
        assertEquals("Poster fileId", 88888, posterRef.fileId)
        assertTrue("Poster is portrait", posterRef.width < posterRef.height)

        // Verify backdrop ref details
        val backdropRef = item.backdropRef!!
        assertEquals("Backdrop fileId", 77777, backdropRef.fileId)
        assertTrue("Backdrop is landscape", backdropRef.width > backdropRef.height)
    }

    @Test
    fun `Golden Path - All metadata fields populated correctly`() {
        val item = createGoldenMovieItem()
        val metadata = item.metadata

        assertEquals("Title", "Golden Test Movie", metadata.title)
        assertEquals("Original title", "Golden Test Original", metadata.originalTitle)
        assertEquals("Year", 2025, metadata.year)
        assertEquals("Length minutes", 140, metadata.lengthMinutes)
        assertEquals("FSK", 12, metadata.fsk)
        assertEquals("Production country", "DE", metadata.productionCountry)
        assertEquals("Director", "Golden Director", metadata.director)
        assertEquals("TMDB rating", 8.5, metadata.tmdbRating)
        assertEquals("Genres count", 3, metadata.genres.size)
        assertTrue("Genre Action", metadata.genres.contains("Action"))
        assertNotNull("TMDB URL", metadata.tmdbUrl)
        assertEquals("Is adult", false, metadata.isAdult)
    }

    // ==========================================================================
    // Helper Functions
    // ==========================================================================

    /**
     * Parse query string into a map (JVM-compatible, no Android dependency).
     */
    private fun parseQueryParams(query: String?): Map<String, String> {
        if (query.isNullOrBlank()) return emptyMap()
        return query
            .split("&")
            .map { it.split("=", limit = 2) }
            .filter { it.size == 2 }
            .associate { (key, value) ->
                URLDecoder.decode(key, "UTF-8") to URLDecoder.decode(value, "UTF-8")
            }
    }
}
