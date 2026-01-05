package com.fishit.player.feature.settings.debug

import com.fishit.player.core.debugsettings.DebugToolsSettingsRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Debug implementation of [DebugToolsController].
 *
 * Delegates to [DebugToolsSettingsRepository] from :core:debug-settings module.
 * This implementation is only compiled in debug builds.
 */
@Singleton
class DebugToolsControllerImpl
    @Inject
    constructor(
        private val settingsRepository: DebugToolsSettingsRepository,
    ) : DebugToolsController {
        override val isAvailable: Boolean = true

        override val networkInspectorEnabledFlow: Flow<Boolean>
            get() = settingsRepository.networkInspectorEnabledFlow

        override val leakCanaryEnabledFlow: Flow<Boolean>
            get() = settingsRepository.leakCanaryEnabledFlow

        override suspend fun setNetworkInspectorEnabled(enabled: Boolean) {
            settingsRepository.setNetworkInspectorEnabled(enabled)
        }

        override suspend fun setLeakCanaryEnabled(enabled: Boolean) {
            settingsRepository.setLeakCanaryEnabled(enabled)
        }
    }
