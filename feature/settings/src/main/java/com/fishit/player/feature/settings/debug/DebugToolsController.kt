package com.fishit.player.feature.settings.debug

import kotlinx.coroutines.flow.Flow

/**
 * Abstraction for debug tools runtime control (LeakCanary, Chucker toggles).
 *
 * **Compile-time Gating:**
 * - Debug builds: Real implementation with :core:debug-settings integration
 * - Release builds: No-op implementation that returns disabled state
 *
 * This ensures NO references to :core:debug-settings appear in release builds,
 * providing complete compile-time isolation.
 *
 * @see DebugToolsControllerImpl (debug source set)
 * @see DebugToolsControllerImpl (release source set - no-op)
 */
interface DebugToolsController {
    /**
     * Whether debug tools runtime control is available.
     * Returns false in release builds.
     */
    val isAvailable: Boolean

    /**
     * Flow of network inspector (Chucker) enabled state.
     * Always emits false in release builds.
     */
    val networkInspectorEnabledFlow: Flow<Boolean>

    /**
     * Flow of leak detection (LeakCanary) enabled state.
     * Always emits false in release builds.
     */
    val leakCanaryEnabledFlow: Flow<Boolean>

    /**
     * Enable or disable the network inspector (Chucker).
     * No-op in release builds.
     */
    suspend fun setNetworkInspectorEnabled(enabled: Boolean)

    /**
     * Enable or disable leak detection (LeakCanary).
     * No-op in release builds.
     */
    suspend fun setLeakCanaryEnabled(enabled: Boolean)
}
