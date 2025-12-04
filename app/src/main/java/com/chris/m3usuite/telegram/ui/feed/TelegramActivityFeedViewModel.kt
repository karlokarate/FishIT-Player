package com.chris.m3usuite.telegram.ui.feed

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.chris.m3usuite.core.logging.UnifiedLog
import com.chris.m3usuite.data.repo.TelegramContentRepository
import com.chris.m3usuite.model.MediaItem
import com.chris.m3usuite.prefs.SettingsStore
import com.chris.m3usuite.telegram.core.T_TelegramServiceClient
import com.chris.m3usuite.telegram.core.TgActivityEvent
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * ViewModel for Telegram Activity Feed.
 *
 * Consumes activityEvents from T_TelegramServiceClient and resolves them
 * to MediaItems using TelegramContentRepository.
 *
 * Provides a UI-friendly feed state showing recent Telegram activity:
 * - New messages
 * - New downloads
 * - Completed downloads
 * - Parse results
 */
class TelegramActivityFeedViewModel(
    private val app: Application,
    private val store: SettingsStore,
) : ViewModel() {
    private val serviceClient = T_TelegramServiceClient.getInstance(app)
    private val repository = TelegramContentRepository(app, store)

    // State flow for feed UI
    private val _feedState = MutableStateFlow(FeedState())
    val feedState: StateFlow<FeedState> = _feedState.asStateFlow()

    // Recent activity events (last 50)
    private val _activityLog = MutableStateFlow<List<ActivityLogEntry>>(emptyList())
    val activityLog: StateFlow<List<ActivityLogEntry>> = _activityLog.asStateFlow()

    init {
        UnifiedLog.info(
            source = "TelegramActivityFeedViewModel",
            message = "Initializing Activity Feed",
        )

        // Collect activity events from service client
        viewModelScope.launch {
            try {
                serviceClient.activityEvents.collect { event ->
                    handleActivityEvent(event)
                }
            } catch (e: Exception) {
                UnifiedLog.error(
                    source = "TelegramActivityFeedViewModel",
                    message = "Error collecting activity events",
                    exception = e,
                )
                _feedState.update {
                    it.copy(
                        errorMessage = "Fehler beim Laden der Aktivitäten: ${e.message}",
                        isLoading = false,
                    )
                }
            }
        }

        // Load initial feed items
        viewModelScope.launch {
            _feedState.update { it.copy(isLoading = true) }
            try {
                repository.getTelegramFeedItems().collect { items ->
                    UnifiedLog.debug(
                        source = "TelegramActivityFeedViewModel",
                        message = "Loaded feed items",
                        details = mapOf("count" to items.size.toString()),
                    )
                    _feedState.update {
                        it.copy(
                            feedItems = items,
                            isLoading = false,
                            errorMessage = null,
                        )
                    }
                }
            } catch (e: Exception) {
                UnifiedLog.error(
                    source = "TelegramActivityFeedViewModel",
                    message = "Error loading feed items",
                    exception = e,
                )
                _feedState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = "Fehler beim Laden der Medien: ${e.message}",
                    )
                }
            }
        }
    }

    /**
     * Handle incoming activity event.
     */
    private fun handleActivityEvent(event: TgActivityEvent) {
        val logEntry =
            when (event) {
                is TgActivityEvent.NewMessage -> {
                    ActivityLogEntry(
                        timestamp = System.currentTimeMillis(),
                        type = ActivityType.NEW_MESSAGE,
                        title = "Neue Nachricht",
                        description = "Chat ${event.chatId}, Message ${event.messageId}",
                        chatId = event.chatId,
                        messageId = event.messageId,
                    )
                }
                is TgActivityEvent.NewDownload -> {
                    ActivityLogEntry(
                        timestamp = System.currentTimeMillis(),
                        type = ActivityType.NEW_DOWNLOAD,
                        title = "Download gestartet",
                        description = event.fileName,
                        fileId = event.fileId,
                    )
                }
                is TgActivityEvent.DownloadComplete -> {
                    ActivityLogEntry(
                        timestamp = System.currentTimeMillis(),
                        type = ActivityType.DOWNLOAD_COMPLETE,
                        title = "Download abgeschlossen",
                        description = event.fileName,
                        fileId = event.fileId,
                    )
                }
                is TgActivityEvent.ParseComplete -> {
                    ActivityLogEntry(
                        timestamp = System.currentTimeMillis(),
                        type = ActivityType.PARSE_COMPLETE,
                        title = "Parsing abgeschlossen",
                        description = "${event.itemsFound} Elemente gefunden",
                        chatId = event.chatId,
                    )
                }
            }

        // Add to activity log (keep last 50 entries)
        _activityLog.update { current ->
            (listOf(logEntry) + current).take(50)
        }

        // Update feed state
        _feedState.update {
            it.copy(
                lastActivityTimestamp = System.currentTimeMillis(),
                hasNewActivity = true,
            )
        }
    }

    /**
     * Mark feed as viewed (clear "new activity" flag).
     */
    fun markFeedAsViewed() {
        _feedState.update { it.copy(hasNewActivity = false) }
    }

    /**
     * Refresh feed items.
     */
    fun refreshFeed() {
        viewModelScope.launch {
            _feedState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                repository.getTelegramFeedItems().first().let { items ->
                    _feedState.update { it.copy(feedItems = items, isLoading = false) }
                }
            } catch (e: Exception) {
                _feedState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = "Fehler beim Aktualisieren: ${e.message}",
                    )
                }
            }
        }
    }

    companion object {
        fun factory(app: Application): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    val store = SettingsStore(app)
                    return TelegramActivityFeedViewModel(app, store) as T
                }
            }
        }
    }
}

/**
 * State for Telegram Activity Feed UI.
 */
data class FeedState(
    val feedItems: List<MediaItem> = emptyList(),
    val lastActivityTimestamp: Long = 0,
    val hasNewActivity: Boolean = false,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
)

/**
 * Activity log entry for feed display.
 */
data class ActivityLogEntry(
    val timestamp: Long,
    val type: ActivityType,
    val title: String,
    val description: String,
    val chatId: Long? = null,
    val messageId: Long? = null,
    val fileId: Int? = null,
)

/**
 * Type of activity event.
 */
enum class ActivityType {
    NEW_MESSAGE,
    NEW_DOWNLOAD,
    DOWNLOAD_COMPLETE,
    PARSE_COMPLETE,
}
