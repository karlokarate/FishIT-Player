package com.fishit.player.core.debugsettings

import com.fishit.player.infra.logging.UnifiedLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Initializer that syncs DataStore settings to runtime flags and configures debug tools.
 *
 * **Issue #564 Compile-Time Gating:**
 * - Uses reflection to configure LeakCanary when available
 * - When LeakCanary is not in the classpath (disabled via Gradle properties),
 *   the configuration is silently skipped
 *
 * **Contract:**
 * - Starts on app launch (called from Application.onCreate)
 * - Collects settings flows and updates DebugFlagsHolder atomics
 * - Configures LeakCanary watchers based on settings (when available)
 * - Logs toggle state transitions
 *
 * **Lifecycle:**
 * - Application scope (stays active for entire app lifecycle)
 * - No memory leaks (uses app-scoped CoroutineScope)
 */
@Singleton
class DebugToolsInitializer
    @Inject
    constructor(
        private val settingsRepo: DebugToolsSettingsRepository,
        private val flagsHolder: DebugFlagsHolder,
    ) {
        private val isLeakCanaryAvailable: Boolean by lazy {
            try {
                Class.forName("leakcanary.LeakCanary")
                true
            } catch (e: ClassNotFoundException) {
                false
            }
        }

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
                    if (isLeakCanaryAvailable) {
                        configureLeakCanary(enabled)
                    } else {
                        UnifiedLog.d(TAG) { "LeakCanary not available (disabled via compile-time gating)" }
                    }
                    UnifiedLog.i(TAG) { "LeakCanary enabled=$enabled (available=$isLeakCanaryAvailable)" }
                }.launchIn(appScope)

            UnifiedLog.i(TAG) { "DebugToolsInitializer started (LeakCanary available=$isLeakCanaryAvailable)" }
        }

        /**
         * Configure LeakCanary based on enabled state using reflection.
         *
         * When enabled:
         * - Enable watchers (Activities, Fragments, ViewModels)
         * - Show launcher icon
         * - Allow heap dumps and analysis
         *
         * When disabled (DEFAULT):
         * - Disable watchers
         * - Hide launcher icon
         * - Disable automatic heap dumps
         */
        private fun configureLeakCanary(enabled: Boolean) {
            try {
                val appWatcherClass = Class.forName("leakcanary.AppWatcher")
                val leakCanaryClass = Class.forName("leakcanary.LeakCanary")

                // Get current configs
                val appWatcherConfigField = appWatcherClass.getField("config")
                val leakCanaryConfigField = leakCanaryClass.getField("config")

                val currentAppWatcherConfig = appWatcherConfigField.get(null)
                val currentLeakCanaryConfig = leakCanaryConfigField.get(null)

                val appWatcherConfigClass = currentAppWatcherConfig.javaClass
                val leakCanaryConfigClass = currentLeakCanaryConfig.javaClass

                if (enabled) {
                    // Enable watchers
                    val copyMethod =
                        appWatcherConfigClass.getMethod(
                            "copy",
                            Boolean::class.java, // watchActivities
                            Boolean::class.java, // watchFragments
                            Boolean::class.java, // watchFragmentViews
                            Boolean::class.java, // watchViewModels
                            Boolean::class.java, // enabled
                        )
                    val newConfig = copyMethod.invoke(currentAppWatcherConfig, true, true, true, true, true)
                    appWatcherConfigField.set(null, newConfig)

                    // Show launcher icon
                    val showIconMethod = leakCanaryClass.getMethod("showLeakDisplayActivityLauncherIcon", Boolean::class.java)
                    showIconMethod.invoke(null, true)

                    UnifiedLog.i(TAG) { "LeakCanary ENABLED (watchers active, heap dumps allowed)" }
                } else {
                    // Disable watchers
                    val copyMethod =
                        appWatcherConfigClass.getMethod(
                            "copy",
                            Boolean::class.java, // watchActivities
                            Boolean::class.java, // watchFragments
                            Boolean::class.java, // watchFragmentViews
                            Boolean::class.java, // watchViewModels
                            Boolean::class.java, // enabled
                        )
                    val newConfig = copyMethod.invoke(currentAppWatcherConfig, false, false, false, false, false)
                    appWatcherConfigField.set(null, newConfig)

                    // Hide launcher icon
                    val showIconMethod = leakCanaryClass.getMethod("showLeakDisplayActivityLauncherIcon", Boolean::class.java)
                    showIconMethod.invoke(null, false)

                    UnifiedLog.i(TAG) { "LeakCanary DISABLED (watchers stopped, no automatic heap dumps)" }
                }
            } catch (e: Exception) {
                UnifiedLog.w(TAG) { "Failed to configure LeakCanary: ${e.message}" }
            }
        }

        private companion object {
            private const val TAG = "DebugToolsInitializer"
        }
    }
