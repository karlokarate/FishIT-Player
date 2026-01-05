package com.fishit.player.feature.settings.debug

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Release (no-op) implementation of [DebugToolsController].
 *
 * **Compile-time Gating:**
 * This implementation has ZERO dependencies on :core:debug-settings module.
 * All methods return disabled/no-op behavior.
 */
@Singleton
class DebugToolsControllerImpl
    @Inject
    constructor() : DebugToolsController {
        override val isAvailable: Boolean = false

        override val networkInspectorEnabledFlow: Flow<Boolean> = flowOf(false)

        override val leakCanaryEnabledFlow: Flow<Boolean> = flowOf(false)

        override suspend fun setNetworkInspectorEnabled(enabled: Boolean) {
            // No-op in release builds
        }

        override suspend fun setLeakCanaryEnabled(enabled: Boolean) {
            // No-op in release builds
        }
    }
