package com.fishit.player.core.debugsettings

import com.fishit.player.infra.logging.UnifiedLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Release variant of DebugToolsInitializer - no-op implementation.
 *
 * **Contract:**
 * - Syncs Chucker enabled state (Chucker uses no-op in release)
 * - Ignores LeakCanary settings (LeakCanary is not available in release)
 * - Minimal overhead (no debug tools active)
 */
@Singleton
class DebugToolsInitializer
    @Inject
    constructor(
        private val settingsRepo: DebugToolsSettingsRepository,
        private val flagsHolder: DebugFlagsHolder,
    ) {
        /**
         * Start observing settings and updating runtime flags.
         * Release builds only sync Chucker state (which uses no-op).
         *
         * @param appScope Application-scoped CoroutineScope
         */
        fun start(appScope: CoroutineScope) {
            // Sync Chucker enabled state (uses no-op ChuckerInterceptor in release)
            settingsRepo.networkInspectorEnabledFlow
                .onEach { enabled ->
                    flagsHolder.chuckerEnabled.set(enabled)
                    UnifiedLog.i(TAG) { "Chucker enabled=$enabled (release no-op)" }
                }.launchIn(appScope)

            // LeakCanary settings are ignored in release builds
            settingsRepo.leakCanaryEnabledFlow
                .onEach { enabled ->
                    flagsHolder.leakCanaryEnabled.set(enabled)
                    UnifiedLog.i(TAG) { "LeakCanary enabled=$enabled (release no-op)" }
                }.launchIn(appScope)

            UnifiedLog.i(TAG) { "DebugToolsInitializer started (release no-op variant)" }
        }

        private companion object {
            private const val TAG = "DebugToolsInitializer"
        }
    }
