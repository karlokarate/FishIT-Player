package com.fishit.player.v2

import com.fishit.player.core.debugsettings.DebugToolsInitializer
import com.fishit.player.infra.logging.UnifiedLog
import kotlinx.coroutines.CoroutineScope
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Debug-specific bootstraps for FishItV2Application.
 *
 * **Contract:**
 * - Starts DebugToolsInitializer (syncs DataStore to runtime flags)
 * - Configures LeakCanary (OFF by default)
 * - Configures Chucker (OFF by default via GatedChuckerInterceptor)
 *
 * **Usage:**
 * ```kotlin
 * // In FishItV2Application.onCreate():
 * if (BuildConfig.DEBUG) {
 *     debugBootstraps.start(appScope)
 * }
 * ```
 */
@Singleton
class DebugBootstraps
    @Inject
    constructor(
        private val debugToolsInitializer: DebugToolsInitializer,
    ) {
        fun start(appScope: CoroutineScope) {
            // Start DebugToolsInitializer to sync DataStore settings to runtime flags
            debugToolsInitializer.start(appScope)

            UnifiedLog.i(TAG) { "Debug bootstraps started (DebugToolsInitializer)" }
        }

        private companion object {
            private const val TAG = "DebugBootstraps"
        }
    }
