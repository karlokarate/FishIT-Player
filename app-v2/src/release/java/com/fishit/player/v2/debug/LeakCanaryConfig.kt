package com.fishit.player.v2.debug

/**
 * No-op stub for release builds.
 *
 * LeakCanary is not included in release builds, so this provides
 * empty implementations to allow shared code to compile.
 */
@Suppress("UNUSED_PARAMETER")
object LeakCanaryConfig {
    fun install(app: android.app.Application) {
        // No-op in release
    }

    fun watchPlayerObject(
        watchedObject: Any,
        description: String,
    ) {
        // No-op in release
    }

    fun watchTelegramObject(
        watchedObject: Any,
        description: String,
    ) {
        // No-op in release
    }

    fun watchViewModel(
        watchedObject: Any,
        description: String,
    ) {
        // No-op in release
    }

    fun getRetainedObjectCount(): Int = 0

    fun hasRetainedObjects(): Boolean = false

    fun dumpHeapNow() {
        // No-op in release
    }
}
