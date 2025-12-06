package com.fishit.player.pipeline.telegram.stub

import com.fishit.player.pipeline.telegram.model.TelegramChatSummary
import com.fishit.player.pipeline.telegram.model.TelegramMediaItem
import com.fishit.player.pipeline.telegram.repository.TelegramContentRepository

/**
 * Stub implementation of TelegramContentRepository for Phase 2 Task 3 (P2-T3).
 *
 * This implementation returns deterministic empty results and is used for:
 * - Interface validation
 * - Testing without real TDLib integration
 * - Early integration testing with UI layers
 *
 * NO REAL TDLib integration - all methods return empty or mock data.
 */
open class StubTelegramContentRepository : TelegramContentRepository {
    override suspend fun getAllMediaItems(
        limit: Int,
        offset: Int,
    ): List<TelegramMediaItem> {
        // STUB: Return empty list
        return emptyList()
    }

    override suspend fun getMediaItemsByChat(
        chatId: Long,
        limit: Int,
        offset: Int,
    ): List<TelegramMediaItem> {
        // STUB: Return empty list
        return emptyList()
    }

    override suspend fun getRecentMediaItems(limit: Int): List<TelegramMediaItem> {
        // STUB: Return empty list
        return emptyList()
    }

    override suspend fun searchMediaItems(
        query: String,
        limit: Int,
    ): List<TelegramMediaItem> {
        // STUB: Return empty list
        return emptyList()
    }

    override suspend fun getSeriesMediaItems(
        seriesName: String,
        limit: Int,
    ): List<TelegramMediaItem> {
        // STUB: Return empty list
        return emptyList()
    }

    override suspend fun getMediaItemById(id: Long): TelegramMediaItem? {
        // STUB: Return null (not found)
        return null
    }

    override suspend fun getAllChats(): List<TelegramChatSummary> {
        // STUB: Return empty list
        return emptyList()
    }

    override suspend fun refresh() {
        // STUB: No-op
    }

    companion object {
        /**
         * Creates a stub repository with mock data for testing.
         * Returns a small set of deterministic media items.
         */
        fun withMockData(): StubTelegramContentRepository {
            return StubTelegramContentRepositoryWithMockData()
        }
    }
}

/**
 * Stub implementation that returns a small set of mock data for testing.
 */
private class StubTelegramContentRepositoryWithMockData : StubTelegramContentRepository() {
    private val mockMediaItems = listOf(
        TelegramMediaItem(
            id = 1,
            chatId = 12345,
            messageId = 1001,
            fileId = 1001,
            remoteId = "stub_remote_1",
            title = "Mock Video 1",
            fileName = "mock_video_1.mp4",
            mimeType = "video/mp4",
            sizeBytes = 1024000,
            durationSecs = 120,
            width = 1920,
            height = 1080,
            supportsStreaming = true,
        ),
        TelegramMediaItem(
            id = 2,
            chatId = 12345,
            messageId = 1002,
            fileId = 1002,
            remoteId = "stub_remote_2",
            title = "Mock Video 2",
            fileName = "mock_video_2.mp4",
            mimeType = "video/mp4",
            sizeBytes = 2048000,
            durationSecs = 180,
            width = 1920,
            height = 1080,
            supportsStreaming = true,
            isSeries = true,
            seriesName = "Mock Series",
            seasonNumber = 1,
            episodeNumber = 1,
        ),
    )

    private val mockChats = listOf(
        TelegramChatSummary(
            chatId = 12345,
            title = "Mock Chat",
            type = "channel",
            mediaCount = 2,
            lastMessageDate = System.currentTimeMillis() / 1000,
        ),
    )

    override suspend fun getAllMediaItems(
        limit: Int,
        offset: Int,
    ): List<TelegramMediaItem> {
        return mockMediaItems.drop(offset).take(limit)
    }

    override suspend fun getMediaItemsByChat(
        chatId: Long,
        limit: Int,
        offset: Int,
    ): List<TelegramMediaItem> {
        return mockMediaItems
            .filter { it.chatId == chatId }
            .drop(offset)
            .take(limit)
    }

    override suspend fun getRecentMediaItems(limit: Int): List<TelegramMediaItem> {
        return mockMediaItems.take(limit)
    }

    override suspend fun getMediaItemById(id: Long): TelegramMediaItem? {
        return mockMediaItems.find { it.id == id }
    }

    override suspend fun getAllChats(): List<TelegramChatSummary> {
        return mockChats
    }
}
