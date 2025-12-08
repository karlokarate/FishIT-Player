package com.fishit.player.feature.telegram

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fishit.player.core.feature.FeatureRegistry
import com.fishit.player.core.feature.TelegramFeatures
import com.fishit.player.infra.logging.UnifiedLog
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
 *
 * **ARCHITECTURE NOTE:** This ViewModel uses the feature registry to gate behaviors.
 * Actual Telegram operations would be delegated to a repository/use-case from pipeline/telegram,
 * but for Phase 1.7, we demonstrate the feature-gated pattern without full implementation.
 */
@HiltViewModel
class TelegramMediaViewModel @Inject constructor(
    private val featureRegistry: FeatureRegistry,
) : ViewModel() {

    private val _uiState = MutableStateFlow(TelegramMediaUiState())
    val uiState: StateFlow<TelegramMediaUiState> = _uiState.asStateFlow()

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
        updateFeatureState()
    }

    private fun logFeatureCapabilities() {
        val features = buildString {
            append("Telegram feature capabilities: ")
            append("fullHistory=${supportsFullHistoryStreaming}, ")
            append("lazyThumbs=${supportsLazyThumbnails}")
        }
        UnifiedLog.d(TAG, features)

        // Log owners for debugging
        featureRegistry.ownerOf(TelegramFeatures.FULL_HISTORY_STREAMING)?.let { owner ->
            UnifiedLog.d(TAG, "Full history streaming owned by: ${owner.moduleName}")
        }
        featureRegistry.ownerOf(TelegramFeatures.LAZY_THUMBNAILS)?.let { owner ->
            UnifiedLog.d(TAG, "Lazy thumbnails owned by: ${owner.moduleName}")
        }
    }

    /**
     * Update UI state based on feature availability.
     *
     * This demonstrates feature-gated behavior: UI elements are enabled/disabled
     * based on what the FeatureRegistry reports.
     */
    private fun updateFeatureState() {
        _uiState.update { state ->
            state.copy(
                canSyncFullHistory = supportsFullHistoryStreaming,
                canLoadThumbnailsLazily = supportsLazyThumbnails
            )
        }
    }

    /**
     * Initiate full chat history sync.
     *
     * This would be the entry point for the "Sync All" button in the UI.
     * Only callable when FULL_HISTORY_STREAMING feature is supported.
     *
     * **ARCHITECTURE NOTE:** Actual implementation would call into a repository/use-case.
     * For Phase 1.7, this demonstrates the feature-gated pattern.
     */
    fun syncFullHistory() {
        if (!supportsFullHistoryStreaming) {
            UnifiedLog.w(TAG, "syncFullHistory called but feature not supported")
            _uiState.update { it.copy(errorMessage = "Full history sync not available") }
            return
        }

        viewModelScope.launch {
            UnifiedLog.d(TAG, "syncFullHistory - feature check passed, would initiate sync")
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }

            // TODO: Call into TelegramContentRepository or use-case here
            // Example: telegramRepository.syncAllChats(onProgress = { ... })

            // For now, just log and update state
            _uiState.update {
                it.copy(
                    isLoading = false,
                    lastSyncStatus = "Full history sync would be initiated here"
                )
            }
        }
    }

    /**
     * Load thumbnails for visible items.
     *
     * This would be called when items scroll into view.
     * Only executes when LAZY_THUMBNAILS feature is supported.
     *
     * @param remoteIds List of remoteIds for thumbnails to load
     *
     * **ARCHITECTURE NOTE:** Actual implementation would call into thumbnail loader service.
     */
    fun loadThumbnails(remoteIds: List<String>) {
        if (!supportsLazyThumbnails) {
            UnifiedLog.d(TAG, "loadThumbnails called but lazy thumbnails not supported, skipping")
            return
        }

        if (remoteIds.isEmpty()) return

        UnifiedLog.d(TAG, "loadThumbnails - feature check passed, would load ${remoteIds.size} thumbnails")

        // TODO: Call into thumbnail loader service
        // Example: telegramClient.requestThumbnailDownload(remoteId, priority = 8)
    }

    /**
     * Clear any error message.
     */
    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    companion object {
        private const val TAG = "TelegramMediaViewModel"
    }
}

/**
 * UI state for the Telegram Media screen.
 *
 * Demonstrates feature-gated state management.
 */
data class TelegramMediaUiState(
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val canSyncFullHistory: Boolean = false,
    val canLoadThumbnailsLazily: Boolean = false,
    val lastSyncStatus: String? = null,
)
