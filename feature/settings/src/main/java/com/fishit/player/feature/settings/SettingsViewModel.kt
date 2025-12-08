package com.fishit.player.feature.settings

import androidx.lifecycle.ViewModel
import com.fishit.player.core.feature.AppFeatures
import com.fishit.player.core.feature.FeatureRegistry
import com.fishit.player.core.feature.LoggingFeatures
import com.fishit.player.core.feature.SettingsFeatures
import com.fishit.player.infra.logging.UnifiedLog
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/**
 * ViewModel for the Settings screen.
 *
 * Uses [FeatureRegistry] to determine which settings and management
 * features are available.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val featureRegistry: FeatureRegistry,
) : ViewModel() {

    init {
        logFeatureAvailability()
    }

    /**
     * Checks if cache management features are available.
     */
    fun hasCacheManagement(): Boolean {
        return featureRegistry.isSupported(AppFeatures.CACHE_MANAGEMENT)
    }

    /**
     * Checks if settings persistence is available.
     */
    fun hasSettingsPersistence(): Boolean {
        return featureRegistry.isSupported(SettingsFeatures.CORE_SINGLE_DATASTORE)
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
        UnifiedLog.d(TAG, "=== Settings Screen Feature Availability ===")
        UnifiedLog.d(TAG, "Cache Management: ${hasCacheManagement()}")
        UnifiedLog.d(TAG, "Settings Persistence: ${hasSettingsPersistence()}")
        UnifiedLog.d(TAG, "Unified Logging: ${hasLogging()}")
        UnifiedLog.d(TAG, "============================================")
    }

    companion object {
        private const val TAG = "SettingsViewModel"
    }
}
