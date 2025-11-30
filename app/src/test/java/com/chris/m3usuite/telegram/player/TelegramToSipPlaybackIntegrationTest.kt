package com.chris.m3usuite.telegram.player

import com.chris.m3usuite.telegram.domain.TelegramImageRef
import com.chris.m3usuite.telegram.domain.TelegramItem
import com.chris.m3usuite.telegram.domain.TelegramItemType
import com.chris.m3usuite.telegram.domain.TelegramMediaRef
import com.chris.m3usuite.telegram.domain.TelegramMetadata
import com.chris.m3usuite.telegram.util.TelegramPlayUrl
import org.junit.Test

/**
 * Integration tests for Telegram → SIP Player playback flow.
 *
 * Per TELEGRAM_SIP_PLAYER_INTEGRATION.md:
 * - TelegramItem is built from parser pipeline
 * - Play action creates TelegramPlaybackRequest from TelegramMediaRef
 * - URL is built via TelegramPlayUrl.build(request)
 * - URL is passed to PlayerChooser → openInternal → InternalPlayerEntry
 * - DataSource resolves file via remoteId-first strategy
 *
 * This test verifies the contract WITHOUT requiring actual TDLib or ExoPlayer.
 */
class TelegramToSipPlaybackIntegrationTest {
    // ==========================================================================
    // TELEGRAM_MEDIA_ID_OFFSET for mediaId encoding
    // ==========================================================================

    /**
     * MediaId offset for Telegram content to avoid collisions with Xtream content.
     * Per TelegramDetailScreen.kt: TELEGRAM_MEDIA_ID_OFFSET = 4_000_000_000_000L
     */
    private val TELEGRAM_MEDIA_ID_OFFSET = 4_000_000_000_000L

    // ==========================================================================
    // Test Fixtures
    // ==========================================================================

    private fun createTestMovie(): TelegramItem =
        TelegramItem(
            chatId = -1001234567890L,
            anchorMessageId = 12345678L,
            type = TelegramItemType.MOVIE,
            videoRef =
                TelegramMediaRef(
                    remoteId = "AgACAgIAAxkBAAIBNmF1Y2xxxxx",
                    uniqueId = "AQADCAAH1234",
                    fileId = 12345,
                    sizeBytes = 2_000_000_000L,
                    mimeType = "video/mp4",
                    durationSeconds = 7200, // 2 hours
                    width = 1920,
                    height = 1080,
                ),
            documentRef = null, // MOVIE uses videoRef, not documentRef
            posterRef =
                TelegramImageRef(
                    remoteId = "AgACAgIAAxkBAAIBNmF1Y2ppppp",
                    uniqueId = "AQADCAAH5678",
                    fileId = 54321,
                    width = 680,
                    height = 1000,
                    sizeBytes = 500_000L,
                ),
            backdropRef = null,
            textMessageId = 12345679L,
            photoMessageId = 12345680L,
            createdAtIso = "2025-01-15T10:30:00Z",
            metadata =
                TelegramMetadata(
                    title = "Test Movie",
                    originalTitle = "Test Movie Original",
                    year = 2024,
                    lengthMinutes = 120,
                    fsk = 12,
                    productionCountry = "US",
                    collection = null,
                    director = "Test Director",
                    tmdbRating = 7.5,
                    genres = listOf("Action", "Thriller"),
                    tmdbUrl = "https://themoviedb.org/movie/12345",
                    isAdult = false,
                ),
        )

    private fun createTestSeriesEpisode(): TelegramItem =
        TelegramItem(
            chatId = -1001234567890L,
            anchorMessageId = 22345678L,
            type = TelegramItemType.SERIES_EPISODE,
            videoRef =
                TelegramMediaRef(
                    remoteId = "AgACAgIAAxkBAAIBNmF1YmVppp",
                    uniqueId = "AQADCAAH2222",
                    fileId = 22222,
                    sizeBytes = 500_000_000L,
                    mimeType = "video/mp4",
                    durationSeconds = 2700, // 45 minutes
                    width = 1920,
                    height = 1080,
                ),
            documentRef = null,
            posterRef = null,
            backdropRef = null,
            textMessageId = null,
            photoMessageId = null,
            createdAtIso = "2025-01-15T12:00:00Z",
            metadata =
                TelegramMetadata(
                    title = "Test Series S01E05",
                    originalTitle = null,
                    year = 2024,
                    lengthMinutes = 45,
                    fsk = 16,
                    productionCountry = "UK",
                    collection = null,
                    director = null,
                    tmdbRating = 8.0,
                    genres = listOf("Drama"),
                    tmdbUrl = null,
                    isAdult = false,
                ),
        )

    private fun createTestClip(): TelegramItem =
        TelegramItem(
            chatId = -1001234567890L,
            anchorMessageId = 32345678L,
            type = TelegramItemType.CLIP,
            videoRef =
                TelegramMediaRef(
                    remoteId = "AgACAgIAAxkBAAIBNmF1YmNsaXA=",
                    uniqueId = "AQADCAAH3333",
                    fileId = 33333,
                    sizeBytes = 50_000_000L,
                    mimeType = "video/mp4",
                    durationSeconds = 180, // 3 minutes
                    width = 1280,
                    height = 720,
                ),
            documentRef = null,
            posterRef = null,
            backdropRef = null,
            textMessageId = null,
            photoMessageId = null,
            createdAtIso = "2025-01-15T14:00:00Z",
            metadata =
                TelegramMetadata(
                    title = "Test Clip",
                    originalTitle = null,
                    year = null,
                    lengthMinutes = 3,
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

    // ==========================================================================
    // Full Flow Tests
    // ==========================================================================

    @Test
    fun `MOVIE item produces valid playback URL with remoteId`() {
        val item = createTestMovie()

        // Step 1: Extract videoRef (per contract)
        val videoRef = item.videoRef
        assert(videoRef != null) { "MOVIE must have videoRef" }

        // Step 2: Build TelegramPlaybackRequest (per contract)
        val request =
            videoRef!!.toPlaybackRequest(
                chatId = item.chatId,
                anchorMessageId = item.anchorMessageId,
            )

        // Verify request has PRIMARY identifiers
        assert(request.remoteId.isNotEmpty()) { "Request must have remoteId" }
        assert(request.uniqueId.isNotEmpty()) { "Request must have uniqueId" }

        // Step 3: Build URL (per contract)
        val url = TelegramPlayUrl.build(request)

        // Verify URL format
        assert(url.startsWith("tg://file/")) { "URL must use tg://file/ scheme" }
        assert(url.contains("chatId=${item.chatId}")) { "URL must contain chatId" }
        assert(url.contains("messageId=${item.anchorMessageId}")) { "URL must contain messageId" }
        assert(url.contains("remoteId=")) { "URL must contain remoteId for DataSource resolution" }
        assert(url.contains("uniqueId=")) { "URL must contain uniqueId" }
    }

    @Test
    fun `SERIES_EPISODE item produces valid playback URL with remoteId`() {
        val item = createTestSeriesEpisode()

        val videoRef = item.videoRef
        assert(videoRef != null) { "SERIES_EPISODE must have videoRef" }

        val request =
            videoRef!!.toPlaybackRequest(
                chatId = item.chatId,
                anchorMessageId = item.anchorMessageId,
            )

        val url = TelegramPlayUrl.build(request)

        assert(url.startsWith("tg://file/")) { "URL must use tg://file/ scheme" }
        assert(url.contains("remoteId=")) { "URL must contain remoteId" }
    }

    @Test
    fun `CLIP item produces valid playback URL with remoteId`() {
        val item = createTestClip()

        val videoRef = item.videoRef
        assert(videoRef != null) { "CLIP must have videoRef" }

        val request =
            videoRef!!.toPlaybackRequest(
                chatId = item.chatId,
                anchorMessageId = item.anchorMessageId,
            )

        val url = TelegramPlayUrl.build(request)

        assert(url.startsWith("tg://file/")) { "URL must use tg://file/ scheme" }
        assert(url.contains("remoteId=")) { "URL must contain remoteId" }
    }

    // ==========================================================================
    // MediaId Encoding Tests
    // ==========================================================================

    @Test
    fun `mediaId is correctly encoded with TELEGRAM_MEDIA_ID_OFFSET`() {
        val item = createTestMovie()

        // Per contract: mediaId = TELEGRAM_MEDIA_ID_OFFSET + anchorMessageId
        val mediaId = TELEGRAM_MEDIA_ID_OFFSET + item.anchorMessageId

        // Verify offset avoids collision with Xtream IDs
        assert(mediaId >= TELEGRAM_MEDIA_ID_OFFSET) {
            "MediaId must be >= TELEGRAM_MEDIA_ID_OFFSET to avoid Xtream ID collision"
        }

        // Verify we can decode back
        val decodedMessageId = mediaId - TELEGRAM_MEDIA_ID_OFFSET
        assert(decodedMessageId == item.anchorMessageId) {
            "Should be able to decode anchorMessageId from mediaId"
        }
    }

    @Test
    fun `mediaId encoding works for large message IDs`() {
        // Telegram message IDs can be large
        val largeMessageId = 999_999_999L
        val mediaId = TELEGRAM_MEDIA_ID_OFFSET + largeMessageId

        assert(mediaId == 4_000_999_999_999L) {
            "Large messageId should produce correct mediaId"
        }

        val decodedMessageId = mediaId - TELEGRAM_MEDIA_ID_OFFSET
        assert(decodedMessageId == largeMessageId) {
            "Should decode back to original messageId"
        }
    }

    // ==========================================================================
    // URL Format Tests
    // ==========================================================================

    @Test
    fun `URL with null fileId uses 0 in path for remoteId resolution`() {
        val videoRef =
            TelegramMediaRef(
                remoteId = "AgACAgIAAxkBAAIBNmF1Y2xxxxx",
                uniqueId = "AQADCAAH1234",
                fileId = null, // Stale/missing fileId
                sizeBytes = 1_000_000_000L,
                mimeType = "video/mp4",
                durationSeconds = 7200,
                width = 1920,
                height = 1080,
            )

        val request =
            videoRef.toPlaybackRequest(
                chatId = -1001234567890L,
                anchorMessageId = 12345678L,
            )

        val url = TelegramPlayUrl.build(request)

        // Path should contain 0 when fileId is null
        assert(url.startsWith("tg://file/0?")) {
            "URL should use 0 as fileId path when fileId is null"
        }

        // But remoteId must be present for resolution
        assert(url.contains("remoteId=AgACAgIAAxkBAAIBNmF1Y2xxxxx")) {
            "URL must contain remoteId for DataSource resolution"
        }
    }

    @Test
    fun `URL with valid fileId includes it in path for fast-path`() {
        val videoRef =
            TelegramMediaRef(
                remoteId = "AgACAgIAAxkBAAIBNmF1Y2xxxxx",
                uniqueId = "AQADCAAH1234",
                fileId = 12345, // Valid fileId
                sizeBytes = 1_000_000_000L,
                mimeType = "video/mp4",
                durationSeconds = 7200,
                width = 1920,
                height = 1080,
            )

        val request =
            videoRef.toPlaybackRequest(
                chatId = -1001234567890L,
                anchorMessageId = 12345678L,
            )

        val url = TelegramPlayUrl.build(request)

        // Path should contain the actual fileId for fast-path
        assert(url.startsWith("tg://file/12345?")) {
            "URL should include fileId in path for fast-path resolution"
        }

        // RemoteId still present as fallback
        assert(url.contains("remoteId=")) {
            "URL must still contain remoteId as fallback"
        }
    }

    // ==========================================================================
    // Non-Playable Types Tests
    // ==========================================================================

    @Test
    fun `AUDIOBOOK has no videoRef and cannot use video playback`() {
        val audiobook =
            TelegramItem(
                chatId = -1001234567890L,
                anchorMessageId = 42345678L,
                type = TelegramItemType.AUDIOBOOK,
                videoRef = null, // AUDIOBOOK uses documentRef
                documentRef =
                    com.chris.m3usuite.telegram.domain.TelegramDocumentRef(
                        remoteId = "AgACAgIAAxkBAAIBNmF1YaB1ZGlv",
                        uniqueId = "AQADCAAH4444",
                        fileId = 44444,
                        sizeBytes = 100_000_000L,
                        mimeType = "audio/mpeg",
                        fileName = "audiobook.mp3",
                    ),
                posterRef = null,
                backdropRef = null,
                textMessageId = null,
                photoMessageId = null,
                createdAtIso = "2025-01-15T16:00:00Z",
                metadata =
                    TelegramMetadata(
                        title = "Test Audiobook",
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

        // AUDIOBOOK has no videoRef
        assert(audiobook.videoRef == null) {
            "AUDIOBOOK must not have videoRef (contract validation)"
        }

        // AUDIOBOOK has documentRef instead
        assert(audiobook.documentRef != null) {
            "AUDIOBOOK must have documentRef"
        }
    }

    @Test
    fun `RAR_ITEM has no videoRef and cannot use video playback`() {
        val rarItem =
            TelegramItem(
                chatId = -1001234567890L,
                anchorMessageId = 52345678L,
                type = TelegramItemType.RAR_ITEM,
                videoRef = null, // RAR_ITEM uses documentRef
                documentRef =
                    com.chris.m3usuite.telegram.domain.TelegramDocumentRef(
                        remoteId = "AgACAgIAAxkBAAIBNmF1YXJhcg==",
                        uniqueId = "AQADCAAH5555",
                        fileId = 55555,
                        sizeBytes = 5_000_000_000L,
                        mimeType = "application/x-rar-compressed",
                        fileName = "archive.rar",
                    ),
                posterRef = null,
                backdropRef = null,
                textMessageId = null,
                photoMessageId = null,
                createdAtIso = "2025-01-15T18:00:00Z",
                metadata =
                    TelegramMetadata(
                        title = "Test RAR Archive",
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

        assert(rarItem.videoRef == null) {
            "RAR_ITEM must not have videoRef"
        }

        assert(rarItem.documentRef != null) {
            "RAR_ITEM must have documentRef"
        }
    }

    @Test
    fun `POSTER_ONLY has no videoRef or documentRef`() {
        val posterOnly =
            TelegramItem(
                chatId = -1001234567890L,
                anchorMessageId = 62345678L,
                type = TelegramItemType.POSTER_ONLY,
                videoRef = null,
                documentRef = null,
                posterRef =
                    TelegramImageRef(
                        remoteId = "AgACAgIAAxkBAAIBNmF1YXBvc3Rlcg==",
                        uniqueId = "AQADCAAH6666",
                        fileId = 66666,
                        width = 680,
                        height = 1000,
                        sizeBytes = 500_000L,
                    ),
                backdropRef = null,
                textMessageId = 62345679L,
                photoMessageId = 62345680L,
                createdAtIso = "2025-01-15T20:00:00Z",
                metadata =
                    TelegramMetadata(
                        title = "Test Poster Only",
                        originalTitle = null,
                        year = 2024,
                        lengthMinutes = null,
                        fsk = null,
                        productionCountry = null,
                        collection = null,
                        director = null,
                        tmdbRating = null,
                        genres = listOf("Documentary"),
                        tmdbUrl = null,
                        isAdult = false,
                    ),
            )

        assert(posterOnly.videoRef == null) {
            "POSTER_ONLY must not have videoRef"
        }

        assert(posterOnly.documentRef == null) {
            "POSTER_ONLY must not have documentRef"
        }

        assert(posterOnly.posterRef != null) {
            "POSTER_ONLY must have posterRef"
        }
    }

    // ==========================================================================
    // Contract Compliance Tests
    // ==========================================================================

    @Test
    fun `playable types (MOVIE, SERIES_EPISODE, CLIP) all have videoRef`() {
        val playableTypes =
            listOf(
                TelegramItemType.MOVIE,
                TelegramItemType.SERIES_EPISODE,
                TelegramItemType.CLIP,
            )

        // Create test items for each playable type
        val items =
            listOf(
                createTestMovie(),
                createTestSeriesEpisode(),
                createTestClip(),
            )

        items.forEachIndexed { index, item ->
            assert(item.type in playableTypes) {
                "Item at index $index should be playable type"
            }
            assert(item.videoRef != null) {
                "${item.type} must have videoRef for playback"
            }
            assert(item.videoRef!!.remoteId.isNotEmpty()) {
                "${item.type} videoRef must have remoteId"
            }
            assert(item.videoRef!!.uniqueId.isNotEmpty()) {
                "${item.type} videoRef must have uniqueId"
            }
        }
    }

    @Test
    fun `remoteId is never empty for playable content`() {
        val item = createTestMovie()
        val videoRef = item.videoRef!!

        assert(videoRef.remoteId.isNotBlank()) {
            "remoteId must never be empty - it's the PRIMARY identifier for resolution"
        }
    }

    @Test
    fun `uniqueId is never empty for playable content`() {
        val item = createTestMovie()
        val videoRef = item.videoRef!!

        assert(videoRef.uniqueId.isNotBlank()) {
            "uniqueId must never be empty - it's the PRIMARY identifier for validation"
        }
    }
}
