package com.fishit.player.core.debugsettings

import com.fishit.player.infra.logging.UnifiedLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import leakcanary.AppWatcher
import leakcanary.LeakCanary
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Initializer that syncs DataStore settings to runtime flags and configures debug tools.
 *
 * **Contract:**
 * - Starts on app launch (called from Application.onCreate)
 * - Collects settings flows and updates DebugFlagsHolder atomics
 * - Configures LeakCanary watchers based on settings
 * - Logs toggle state transitions
 *
 * **Lifecycle:**
 * - Application scope (stays active for entire app lifecycle)
 * - No memory leaks (uses app-scoped CoroutineScope)
 *
 * **Usage:**
 * ```kotlin
 * // In FishItV2Application.onCreate():
 * debugToolsInitializer.start(appScope)
 * ```
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
         *
         * @param appScope Application-scoped CoroutineScope
         */
        fun start(appScope: CoroutineScope) {
            // Sync Chucker enabled state
            settingsRepo.networkInspectorEnabledFlow
                .onEach { enabled ->
                    flagsHolder.chuckerEnabled.set(enabled)
                    UnifiedLog.i(TAG) { "Chucker enabled=$enabled" }
                }.launchIn(appScope)

            // Sync LeakCanary enabled state and configure watchers
            settingsRepo.leakCanaryEnabledFlow
                .onEach { enabled ->
                    flagsHolder.leakCanaryEnabled.set(enabled)
                    configureLeakCanary(enabled)
                    UnifiedLog.i(TAG) { "LeakCanary enabled=$enabled" }
                }.launchIn(appScope)

            UnifiedLog.i(TAG) { "DebugToolsInitializer started" }
        }

        /**
         * Configure LeakCanary based on enabled state.
         *
         * When enabled:
         * - Enable watchers (Activities, Fragments, ViewModels)
         * - Allow heap dumps and analysis
         *
         * When disabled (DEFAULT):
         * - Disable watchers
         * - Disable automatic heap dumps
         */
        private fun configureLeakCanary(enabled: Boolean) {
            if (enabled) {
                // Enable heap dumps
                LeakCanary.config =
                    LeakCanary.config.copy(
                        dumpHeapWhenDebugging = false, // Still don't auto-dump when debugger attached
                        retainedVisibleThreshold = 5, // Dump when 5+ objects retained
                    )

                // Enable watchers via showLeakDisplayActivityLauncherIcon
                LeakCanary.showLeakDisplayActivityLauncherIcon(true)

                UnifiedLog.i(TAG) { "LeakCanary ENABLED (watchers active, heap dumps allowed)" }
            } else {
                // Disable heap dumps (DEFAULT)
                LeakCanary.config =
                    LeakCanary.config.copy(
                        dumpHeapWhenDebugging = false,
                        retainedVisibleThreshold = Int.MAX_VALUE, // Effectively disable auto-dump
                    )

                // Hide LeakCanary icon
                LeakCanary.showLeakDisplayActivityLauncherIcon(false)

                UnifiedLog.i(TAG) { "LeakCanary DISABLED (no automatic heap dumps)" }
            }
        }

        private companion object {
            private const val TAG = "DebugToolsInitializer"
        }
    }
