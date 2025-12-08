package com.fishit.player.feature.library

import androidx.lifecycle.ViewModel
import com.fishit.player.core.feature.FeatureRegistry
import com.fishit.player.core.feature.UiFeatures
import com.fishit.player.core.feature.XtreamFeatures
import com.fishit.player.infra.logging.UnifiedLog
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/**
 * ViewModel for the Library screen.
 *
 * Uses [FeatureRegistry] to determine which content sources are available
 * and configures the library view accordingly.
 */
@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val featureRegistry: FeatureRegistry,
) : ViewModel() {

    init {
        logFeatureAvailability()
    }

    /**
     * Checks if Xtream VOD content is available.
     */
    fun hasXtreamVod(): Boolean {
        return featureRegistry.isSupported(XtreamFeatures.VOD_PLAYBACK)
    }

    /**
     * Checks if Xtream series content is available.
     */
    fun hasXtreamSeries(): Boolean {
        return featureRegistry.isSupported(XtreamFeatures.SERIES_METADATA)
    }

    /**
     * Checks if Telegram content is available.
     */
    fun hasTelegramContent(): Boolean {
        return featureRegistry.isSupported(UiFeatures.SCREEN_TELEGRAM)
    }

    /**
     * Logs available features for debugging.
     */
    private fun logFeatureAvailability() {
        UnifiedLog.d(TAG, "=== Library Screen Feature Availability ===")
        UnifiedLog.d(TAG, "Xtream VOD: ${hasXtreamVod()}")
        UnifiedLog.d(TAG, "Xtream Series: ${hasXtreamSeries()}")
        UnifiedLog.d(TAG, "Telegram Content: ${hasTelegramContent()}")
        UnifiedLog.d(TAG, "===========================================")
    }

    companion object {
        private const val TAG = "LibraryViewModel"
    }
}
