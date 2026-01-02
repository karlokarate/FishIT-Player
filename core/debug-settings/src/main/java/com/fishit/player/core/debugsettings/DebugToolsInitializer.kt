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
                // Enable all watchers
                AppWatcher.config =
                    AppWatcher.config.copy(
                        watchActivities = true,
                        watchFragments = true,
                        watchFragmentViews = true,
                        watchViewModels = true,
                        watchDurationMillis = 10_000L, // 10s delay before considering retained
                    )

                // Enable heap dumps
                LeakCanary.config =
                    LeakCanary.config.copy(
                        dumpHeapWhenDebugging = false, // Still don't auto-dump when debugger attached
                        retainedVisibleThreshold = 5, // Dump when 5+ objects retained
                    )

                UnifiedLog.i(TAG) { "LeakCanary watchers ENABLED (Activities, Fragments, ViewModels)" }
            } else {
                // Disable all watchers (DEFAULT)
                AppWatcher.config =
                    AppWatcher.config.copy(
                        watchActivities = false,
                        watchFragments = false,
                        watchFragmentViews = false,
                        watchViewModels = false,
                    )

                // Disable heap dumps
                LeakCanary.config =
                    LeakCanary.config.copy(
                        dumpHeapWhenDebugging = false,
                        retainedVisibleThreshold = Int.MAX_VALUE, // Effectively disable auto-dump
                    )

                UnifiedLog.i(TAG) { "LeakCanary watchers DISABLED (no automatic heap dumps)" }
            }
        }

        private companion object {
            private const val TAG = "DebugToolsInitializer"
        }
    }
