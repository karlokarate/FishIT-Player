package com.fishit.player.core.debugsettings

import kotlinx.coroutines.flow.Flow

/**
 * Repository for debug tools settings (Chucker, LeakCanary).
 *
 * **Defaults (MANDATORY):**
 * - networkInspectorEnabled = false
 * - leakCanaryEnabled = false
 *
 * **Purpose:**
 * Provides runtime control over debug tooling without reinstall.
 * User can enable/disable via Settings switches.
 *
 * **Contract:**
 * - DEBUG builds only (no release implementation)
 * - Backed by DataStore Preferences
 * - All defaults are OFF
 * - No global mutable singletons (uses Flow for reactive state)
 *
 * **Usage:**
 * ```kotlin
 * // Observe state
 * settingsRepo.networkInspectorEnabledFlow.collect { enabled ->
 *     if (enabled) startChucker() else stopChucker()
 * }
 *
 * // Toggle state
 * settingsRepo.setNetworkInspectorEnabled(true)
 * ```
 */
interface DebugToolsSettingsRepository {
    /**
     * Flow of network inspector (Chucker) enabled state.
     * Default: false
     */
    val networkInspectorEnabledFlow: Flow<Boolean>

    /**
     * Flow of leak detection (LeakCanary) enabled state.
     * Default: false
     */
    val leakCanaryEnabledFlow: Flow<Boolean>

    /**
     * Enable or disable the network inspector (Chucker).
     * Takes effect immediately via Flow.
     */
    suspend fun setNetworkInspectorEnabled(enabled: Boolean)

    /**
     * Enable or disable leak detection (LeakCanary).
     * Takes effect immediately via Flow.
     */
    suspend fun setLeakCanaryEnabled(enabled: Boolean)
}
