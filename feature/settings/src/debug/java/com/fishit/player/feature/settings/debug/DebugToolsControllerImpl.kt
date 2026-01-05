package com.fishit.player.feature.settings.debug

import com.fishit.player.core.debugsettings.DebugToolsSettingsRepository
import com.fishit.player.feature.settings.BuildConfig
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Debug implementation of [DebugToolsController].
 *
 * Delegates to [DebugToolsSettingsRepository] from :core:debug-settings module.
 * This implementation is only compiled in debug builds.
 *
 * **Issue #564 Compliance:**
 * - [isAvailable] checks BuildConfig.INCLUDE_CHUCKER || BuildConfig.INCLUDE_LEAKCANARY
 * - When both are disabled via Gradle properties, the entire debug tools section is hidden
 */
@Singleton
class DebugToolsControllerImpl
    @Inject
    constructor(
        private val settingsRepository: DebugToolsSettingsRepository,
    ) : DebugToolsController {
        override val isAvailable: Boolean = BuildConfig.INCLUDE_CHUCKER || BuildConfig.INCLUDE_LEAKCANARY

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
