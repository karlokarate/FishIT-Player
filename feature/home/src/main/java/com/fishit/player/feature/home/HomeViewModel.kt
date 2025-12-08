package com.fishit.player.feature.home

import androidx.lifecycle.ViewModel
import com.fishit.player.core.feature.AppFeatures
import com.fishit.player.core.feature.FeatureRegistry
import com.fishit.player.core.feature.LoggingFeatures
import com.fishit.player.core.feature.TelegramFeatures
import com.fishit.player.core.feature.UiFeatures
import com.fishit.player.core.feature.XtreamFeatures
import com.fishit.player.infra.logging.UnifiedLog
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/**
 * ViewModel for the Home screen.
 *
 * Uses [FeatureRegistry] to determine which features are available and
 * configures the home screen UI accordingly.
 */
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val featureRegistry: FeatureRegistry,
) : ViewModel() {

    init {
        logFeatureAvailability()
    }

    /**
     * Checks if Xtream features (live streaming or VOD) are available.
     */
    fun hasXtreamFeatures(): Boolean {
        return featureRegistry.isSupported(XtreamFeatures.LIVE_STREAMING) ||
            featureRegistry.isSupported(XtreamFeatures.VOD_PLAYBACK)
    }

    /**
     * Checks if Telegram media screen is available.
     */
    fun hasTelegramScreen(): Boolean {
        return featureRegistry.isSupported(UiFeatures.SCREEN_TELEGRAM)
    }

    /**
     * Checks if unified logging is available.
     */
    fun hasLogging(): Boolean {
        return featureRegistry.isSupported(LoggingFeatures.UNIFIED_LOGGING)
    }

    /**
     * Logs available features for debugging.
     */
    private fun logFeatureAvailability() {
        UnifiedLog.d(TAG, "=== Home Screen Feature Availability ===")
        
        UnifiedLog.d(TAG, "Xtream Live Streaming: ${featureRegistry.isSupported(XtreamFeatures.LIVE_STREAMING)}")
        UnifiedLog.d(TAG, "Xtream VOD Playback: ${featureRegistry.isSupported(XtreamFeatures.VOD_PLAYBACK)}")
        UnifiedLog.d(TAG, "Xtream Series Metadata: ${featureRegistry.isSupported(XtreamFeatures.SERIES_METADATA)}")
        
        UnifiedLog.d(TAG, "Telegram Full History: ${featureRegistry.isSupported(TelegramFeatures.FULL_HISTORY_STREAMING)}")
        UnifiedLog.d(TAG, "Telegram Lazy Thumbnails: ${featureRegistry.isSupported(TelegramFeatures.LAZY_THUMBNAILS)}")
        
        UnifiedLog.d(TAG, "UI Screen Home: ${featureRegistry.isSupported(UiFeatures.SCREEN_HOME)}")
        UnifiedLog.d(TAG, "UI Screen Library: ${featureRegistry.isSupported(UiFeatures.SCREEN_LIBRARY)}")
        UnifiedLog.d(TAG, "UI Screen Telegram: ${featureRegistry.isSupported(UiFeatures.SCREEN_TELEGRAM)}")
        UnifiedLog.d(TAG, "UI Screen Settings: ${featureRegistry.isSupported(UiFeatures.SCREEN_SETTINGS)}")
        
        UnifiedLog.d(TAG, "Unified Logging: ${featureRegistry.isSupported(LoggingFeatures.UNIFIED_LOGGING)}")
        UnifiedLog.d(TAG, "Cache Management: ${featureRegistry.isSupported(AppFeatures.CACHE_MANAGEMENT)}")
        
        UnifiedLog.d(TAG, "========================================")
    }

    companion object {
        private const val TAG = "HomeViewModel"
    }
}
