package com.fishit.player.feature.telegram

import androidx.lifecycle.ViewModel
import com.fishit.player.core.feature.FeatureRegistry
import com.fishit.player.core.feature.TelegramFeatures
import com.fishit.player.infra.logging.UnifiedLog
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

    companion object {
        private const val TAG = "TelegramMediaViewModel"
    }
}

/**
 * UI state for the Telegram Media screen.
 */
data class TelegramMediaUiState(
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
)
