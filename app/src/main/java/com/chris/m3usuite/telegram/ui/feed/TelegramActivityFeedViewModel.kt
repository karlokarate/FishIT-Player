package com.chris.m3usuite.telegram.ui.feed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chris.m3usuite.data.repo.TelegramContentRepository
import com.chris.m3usuite.telegram.core.T_TelegramServiceClient
import com.chris.m3usuite.telegram.core.TgActivityEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for Telegram Activity Feed Screen.
 * Displays recent Telegram activity (new messages, downloads, parsing results).
 */
class TelegramActivityFeedViewModel(
    private val serviceClient: T_TelegramServiceClient,
    private val contentRepository: TelegramContentRepository
) : ViewModel() {
    
    /**
     * Feed item representing an activity event.
     */
    data class FeedItem(
        val id: String,
        val timestamp: Long,
        val title: String,
        val subtitle: String?,
        val type: FeedItemType,
        val chatId: Long? = null,
        val messageId: Long? = null,
        val fileId: Int? = null
    )
    
    enum class FeedItemType {
        NEW_MESSAGE,
        DOWNLOAD_STARTED,
        DOWNLOAD_COMPLETE,
        PARSE_COMPLETE
    }
    
    /**
     * UI State for the feed.
     */
    data class FeedState(
        val items: List<FeedItem> = emptyList(),
        val isLoading: Boolean = false,
        val error: String? = null
    )
    
    private val _state = MutableStateFlow(FeedState())
    val state: StateFlow<FeedState> = _state.asStateFlow()
    
    // In-memory list of recent feed items (ringbuffer)
    private val feedItems = mutableListOf<FeedItem>()
    private val maxFeedItems = 100
    
    init {
        // Collect activity events from service client
        viewModelScope.launch {
            serviceClient.activityEvents.collect { event ->
                handleActivityEvent(event)
            }
        }
    }
    
    private fun handleActivityEvent(event: TgActivityEvent) {
        val feedItem = when (event) {
            is TgActivityEvent.NewMessage -> {
                FeedItem(
                    id = "msg_${event.chatId}_${event.messageId}_${System.currentTimeMillis()}",
                    timestamp = System.currentTimeMillis(),
                    title = "New message",
                    subtitle = "Chat ${event.chatId}",
                    type = FeedItemType.NEW_MESSAGE,
                    chatId = event.chatId,
                    messageId = event.messageId
                )
            }
            is TgActivityEvent.NewDownload -> {
                FeedItem(
                    id = "dl_start_${event.fileId}_${System.currentTimeMillis()}",
                    timestamp = System.currentTimeMillis(),
                    title = "Download started",
                    subtitle = event.fileName,
                    type = FeedItemType.DOWNLOAD_STARTED,
                    fileId = event.fileId
                )
            }
            is TgActivityEvent.DownloadComplete -> {
                FeedItem(
                    id = "dl_complete_${event.fileId}_${System.currentTimeMillis()}",
                    timestamp = System.currentTimeMillis(),
                    title = "Download completed",
                    subtitle = event.fileName,
                    type = FeedItemType.DOWNLOAD_COMPLETE,
                    fileId = event.fileId
                )
            }
            is TgActivityEvent.ParseComplete -> {
                FeedItem(
                    id = "parse_${event.chatId}_${System.currentTimeMillis()}",
                    timestamp = System.currentTimeMillis(),
                    title = "Parsing completed",
                    subtitle = "${event.itemsFound} items found in chat ${event.chatId}",
                    type = FeedItemType.PARSE_COMPLETE,
                    chatId = event.chatId
                )
            }
        }
        
        // Add to feed and maintain max size
        feedItems.add(0, feedItem) // Add to front
        if (feedItems.size > maxFeedItems) {
            feedItems.removeLast()
        }
        
        // Update state
        _state.value = _state.value.copy(
            items = feedItems.toList()
        )
    }
    
    /**
     * Refresh feed (reload from repository if needed).
     */
    fun refresh() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true)
            try {
                // Could load recent items from repository here if needed
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = null
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = e.message ?: "Unknown error"
                )
            }
        }
    }
    
    /**
     * Clear the feed.
     */
    fun clearFeed() {
        feedItems.clear()
        _state.value = _state.value.copy(items = emptyList())
    }
}
