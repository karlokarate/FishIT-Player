package com.chris.m3usuite

import android.app.Application
import android.util.Log

/**
 * Debug-only initializer for quality tools.
 *
 * This is automatically picked up in debug builds and runs:
 * - LeakCanary (automatically initialized via ContentProvider)
 * - Coroutine debugging
 * - ProfileInstaller (works in all builds)
 */
object DebugToolsInitializer {
    private const val TAG = "DebugTools"

    /**
     * Initialize debug tools.
     * Call from Application.onCreate() in debug builds.
     */
    fun initialize(application: Application) {
        Log.d(TAG, "Initializing debug tools...")

        // Enable coroutine debugging
        // This adds coroutine names to stack traces and enables debug logging
        System.setProperty("kotlinx.coroutines.debug", "on")
        Log.d(TAG, "✓ Coroutine debugging enabled")

        // LeakCanary is automatically initialized via ContentProvider
        // No explicit setup needed - just add debugImplementation dependency
        Log.d(TAG, "✓ LeakCanary auto-initialized (via ContentProvider)")

        // ProfileInstaller works automatically in both debug and release
        Log.d(TAG, "✓ ProfileInstaller active (improves startup time on TV)")

        Log.d(TAG, "Debug tools initialization complete")
    }
}
