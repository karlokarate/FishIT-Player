package com.fishit.player.core.debugsettings

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Release variant - No-op implementation of DebugToolsSettingsRepository.
 *
 * **Contract:**
 * - All settings return false (debug tools disabled)
 * - All setters are no-op
 * - Zero overhead
 */
@Singleton
class DataStoreDebugToolsSettingsRepository
    @Inject
    constructor() : DebugToolsSettingsRepository {
        override val networkInspectorEnabledFlow: Flow<Boolean> = flowOf(false)

        override val leakCanaryEnabledFlow: Flow<Boolean> = flowOf(false)

        override suspend fun setNetworkInspectorEnabled(enabled: Boolean) {
            // No-op in release
        }

        override suspend fun setLeakCanaryEnabled(enabled: Boolean) {
            // No-op in release
        }
    }
