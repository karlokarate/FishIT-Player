package com.fishit.player.v2

import kotlinx.coroutines.CoroutineScope
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Release no-op implementation of DebugBootstraps.
 *
 * **Contract:**
 * - No debug tools in release builds
 * - Zero overhead (no-op methods)
 */
@Singleton
class DebugBootstraps
    @Inject
    constructor() {
        fun start(appScope: CoroutineScope) {
            // No-op in release
        }
    }
