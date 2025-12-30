package com.fishit.player.v2.debug

import android.app.Application
import leakcanary.AppWatcher
import leakcanary.LeakCanary
import shark.AndroidReferenceMatchers

/**
 * LeakCanary Gold Standard Configuration for FishIT Player v2.
 *
 * **What LeakCanary 2.x watches by default:**
 * - Activities (after onDestroy)
 * - Fragments (after onDestroy/onDetach)
 * - Fragment Views (after onDestroyView)
 * - ViewModels (after onCleared)
 * - Services (after onDestroy)
 * - Root Views (after removeView)
 *
 * **What we add:**
 * - Custom watchers for Player components
 * - TDLib-related objects that hold native resources
 * - Long-lived coroutine scopes that might retain Context
 *
 * **Usage:**
 * Call [install] from Application.onCreate() in debug builds.
 *
 * **Reference:**
 * - https://square.github.io/leakcanary/fundamentals-how-leakcanary-works/
 * - https://square.github.io/leakcanary/recipes/#watching-objects-with-a-lifecycle
 */
object LeakCanaryConfig {

    private const val TAG = "LeakCanaryConfig"

    /**
     * Install LeakCanary with FishIT-specific configuration.
     *
     * Must be called from Application.onCreate() AFTER Hilt injection.
     */
    fun install(app: Application) {
        // Configure LeakCanary behavior
        LeakCanary.config = LeakCanary.config.copy(
            // Dump heap when 5 retained objects are detected (default is 5)
            retainedVisibleThreshold = 5,
            
            // Show notification for leaks
            dumpHeapWhenDebugging = false, // Don't auto-dump when debugger attached
            
            // Reference matchers - include known Android framework leaks
            referenceMatchers = AndroidReferenceMatchers.appDefaults +
                // Add FishIT-specific known leaks to ignore (if any framework bugs exist)
                listOf(
                    // Example: If we find a framework leak, add it here
                    // AndroidReferenceMatchers.instanceFieldLeak(
                    //     className = "android.app.ActivityThread",
                    //     fieldName = "mLastIntentSender",
                    //     description = "Framework leak in ActivityThread"
                    // )
                ),
            
            // Compute retained heap size (slightly slower but more informative)
            computeRetainedHeapSize = true,
            
            // Max stored heap dumps (prevents filling storage)
            maxStoredHeapDumps = 7,
            
            // Request write external storage permission for heap dump export
            requestWriteExternalStoragePermission = false, // We use SAF
        )

        // Configure AppWatcher for additional object types
        AppWatcher.config = AppWatcher.config.copy(
            // Watch all default types
            watchActivities = true,
            watchFragments = true,
            watchFragmentViews = true,
            watchViewModels = true,
            
            // Delay before considering object retained (default 5s, we use 10s for player)
            // This gives player more time to clean up after screen rotation
            watchDurationMillis = 10_000L,
        )

        android.util.Log.i(TAG, "LeakCanary configured for FishIT Player v2")
    }

    /**
     * Watch a player-related object for leaks.
     *
     * Call this when a player session is being destroyed to ensure
     * it gets garbage collected properly.
     *
     * @param watchedObject The object to watch (e.g., PlayerSession, PlayerView)
     * @param description Human-readable description for leak reports
     */
    fun watchPlayerObject(watchedObject: Any, description: String) {
        AppWatcher.objectWatcher.expectWeaklyReachable(
            watchedObject = watchedObject,
            description = "Player: $description"
        )
    }

    /**
     * Watch a TDLib-related object for leaks.
     *
     * TDLib objects may hold native resources and MUST be properly released.
     *
     * @param watchedObject The object to watch
     * @param description Human-readable description
     */
    fun watchTdLibObject(watchedObject: Any, description: String) {
        AppWatcher.objectWatcher.expectWeaklyReachable(
            watchedObject = watchedObject,
            description = "TDLib: $description"
        )
    }

    /**
     * Watch a ViewModel that should be cleared.
     *
     * Note: LeakCanary already watches ViewModels by default,
     * but this allows explicit tracking of specific VMs.
     *
     * @param watchedObject The ViewModel to watch
     * @param description Human-readable description
     */
    fun watchViewModel(watchedObject: Any, description: String) {
        AppWatcher.objectWatcher.expectWeaklyReachable(
            watchedObject = watchedObject,
            description = "ViewModel: $description"
        )
    }

    /**
     * Get current retained object count.
     *
     * This returns objects that are retained but not yet analyzed.
     * For full leak history, use LeakCanary UI.
     */
    fun getRetainedObjectCount(): Int {
        return AppWatcher.objectWatcher.retainedObjectCount
    }

    /**
     * Check if there are any retained objects waiting for analysis.
     */
    fun hasRetainedObjects(): Boolean {
        return AppWatcher.objectWatcher.hasRetainedObjects
    }

    /**
     * Trigger a heap dump and analysis now.
     *
     * Use sparingly - this freezes the app for several seconds.
     */
    fun dumpHeapNow() {
        LeakCanary.dumpHeap()
    }
}
