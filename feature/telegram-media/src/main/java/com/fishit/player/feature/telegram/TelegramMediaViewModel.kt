package com.fishit.player.feature.telegram

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fishit.player.core.feature.FeatureRegistry
import com.fishit.player.core.feature.TelegramFeatures
import com.fishit.player.feature.telegram.domain.TelegramMediaItem
import com.fishit.player.feature.telegram.domain.TelegramMediaRepository
import com.fishit.player.infra.logging.UnifiedLog
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the Telegram Media feature screen.
 *
 * Uses [FeatureRegistry] to determine which Telegram capabilities are available
 * and adjusts behavior accordingly.
 *
 * Feature checks:
 * - [TelegramFeatures.FULL_HISTORY_STREAMING] - enables complete chat history scanning
 * - [TelegramFeatures.LAZY_THUMBNAILS] - enables on-demand thumbnail loading
 */
@HiltViewModel
class TelegramMediaViewModel @Inject constructor(
    private val featureRegistry: FeatureRegistry,
    private val telegramRepository: TelegramMediaRepository,
    private val tapToPlayUseCase: TelegramTapToPlayUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(TelegramMediaUiState())
    val uiState: StateFlow<TelegramMediaUiState> = _uiState.asStateFlow()

    /**
     * Telegram media items from the repository.
     *
     * Observes all Telegram content and updates UI state.
     */
    val mediaItems: StateFlow<List<TelegramMediaItem>> = telegramRepository
        .observeAll()
        .catch { e ->
            UnifiedLog.e(TAG, e) { "Failed to load Telegram media: ${e.message}" }
            emit(emptyList())
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    /**
     * Whether full history streaming is supported.
     * When true, the UI can show a "sync all" option.
     */
    val supportsFullHistoryStreaming: Boolean
        get() = featureRegistry.isSupported(TelegramFeatures.FULL_HISTORY_STREAMING)

    /**
     * Whether lazy thumbnail loading is supported.
     * When true, thumbnails are loaded on-demand as items become visible.
     */
    val supportsLazyThumbnails: Boolean
        get() = featureRegistry.isSupported(TelegramFeatures.LAZY_THUMBNAILS)

    init {
        logFeatureCapabilities()
    }

    /**
     * Handles tap-to-play for a Telegram media item.
     *
     * Converts the item to a PlaybackContext and starts playback via the player.
     */
    fun onItemTap(item: TelegramMediaItem) {
        viewModelScope.launch {
            _uiState.update { it.copy(isPlaybackStarting = true, errorMessage = null) }

            try {
                tapToPlayUseCase.play(item)
                // Playback started successfully
                _uiState.update { it.copy(isPlaybackStarting = false) }
            } catch (e: Exception) {
                UnifiedLog.e(TAG, e) { "Failed to start playback: ${e.message}" }
                _uiState.update {
                    it.copy(
                        isPlaybackStarting = false,
                        errorMessage = "Failed to start playback: ${e.message}"
                    )
                }
            }
        }
    }

    /**
     * Clears the error message.
     */
    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    private fun logFeatureCapabilities() {
        UnifiedLog.d(TAG) {
            "Telegram feature capabilities: " +
                "fullHistory=$supportsFullHistoryStreaming, " +
                "lazyThumbs=$supportsLazyThumbnails"
        }

        // Log owners for debugging
        featureRegistry.ownerOf(TelegramFeatures.FULL_HISTORY_STREAMING)?.let { owner ->
            UnifiedLog.d(TAG) { "Full history streaming owned by: ${owner.moduleName}" }
        }
        featureRegistry.ownerOf(TelegramFeatures.LAZY_THUMBNAILS)?.let { owner ->
            UnifiedLog.d(TAG) { "Lazy thumbnails owned by: ${owner.moduleName}" }
        }
    }

    companion object {
        private const val TAG = "TelegramMediaViewModel"
    }
}

/**
 * UI state for the Telegram Media screen.
 */
data class TelegramMediaUiState(
    val isLoading: Boolean = false,
    val isPlaybackStarting: Boolean = false,
    val errorMessage: String? = null,
)
