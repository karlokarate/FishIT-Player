package com.fishit.player.v2.debug

import android.app.Application

/**
 * LeakCanary Configuration for FishIT Player v2.
 *
 * **Issue #564 Compile-Time Gating:**
 * - Uses reflection to access LeakCanary APIs
 * - When LeakCanary is not in the classpath (disabled via Gradle properties),
 *   all methods become no-ops
 *
 * **What LeakCanary 2.x watches by default (when available):**
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

    /** Checks if LeakCanary is available in the classpath */
    val isAvailable: Boolean by lazy {
        try {
            Class.forName("leakcanary.LeakCanary")
            true
        } catch (e: ClassNotFoundException) {
            false
        }
    }

    /**
     * Install LeakCanary with FishIT-specific configuration.
     *
     * Must be called from Application.onCreate() AFTER Hilt injection.
     * No-op if LeakCanary is not available.
     */
    fun install(app: Application) {
        if (!isAvailable) {
            android.util.Log.i(TAG, "LeakCanary not available (disabled via compile-time gating)")
            return
        }

        try {
            // Get LeakCanary and AppWatcher classes
            val leakCanaryClass = Class.forName("leakcanary.LeakCanary")
            val appWatcherClass = Class.forName("leakcanary.AppWatcher")
            val androidReferenceMatchersClass = Class.forName("shark.AndroidReferenceMatchers")

            // Get current LeakCanary config
            val leakCanaryConfigField = leakCanaryClass.getField("config")
            val currentLeakCanaryConfig = leakCanaryConfigField.get(null)
            val leakCanaryConfigClass = currentLeakCanaryConfig.javaClass

            // Get default reference matchers
            val appDefaultsMethod = androidReferenceMatchersClass.getMethod("appDefaults")

            @Suppress("UNCHECKED_CAST")
            val appDefaults = appDefaultsMethod.invoke(null) as List<Any>

            // Configure LeakCanary via reflection
            val copyMethod =
                leakCanaryConfigClass.methods.firstOrNull {
                    it.name == "copy" && it.parameterCount > 5
                }

            if (copyMethod != null) {
                // Use default parameter values where possible
                val newConfig =
                    leakCanaryConfigClass
                        .getMethod(
                            "copy",
                            Int::class.java, // retainedVisibleThreshold
                            Boolean::class.java, // dumpHeapWhenDebugging
                            List::class.java, // referenceMatchers
                            Boolean::class.java, // computeRetainedHeapSize
                            Int::class.java, // maxStoredHeapDumps
                            Boolean::class.java, // requestWriteExternalStoragePermission
                        ).invoke(
                            currentLeakCanaryConfig,
                            5, // retainedVisibleThreshold
                            false, // dumpHeapWhenDebugging
                            appDefaults, // referenceMatchers
                            true, // computeRetainedHeapSize
                            7, // maxStoredHeapDumps
                            false, // requestWriteExternalStoragePermission
                        )
                leakCanaryConfigField.set(null, newConfig)
            }

            // Get current AppWatcher config
            val appWatcherConfigField = appWatcherClass.getField("config")
            val currentAppWatcherConfig = appWatcherConfigField.get(null)
            val appWatcherConfigClass = currentAppWatcherConfig.javaClass

            // Configure AppWatcher via reflection
            val appWatcherCopyMethod =
                appWatcherConfigClass.getMethod(
                    "copy",
                    Boolean::class.java, // watchActivities
                    Boolean::class.java, // watchFragments
                    Boolean::class.java, // watchFragmentViews
                    Boolean::class.java, // watchViewModels
                    Long::class.java, // watchDurationMillis
                )

            val newAppWatcherConfig =
                appWatcherCopyMethod.invoke(
                    currentAppWatcherConfig,
                    true, // watchActivities
                    true, // watchFragments
                    true, // watchFragmentViews
                    true, // watchViewModels
                    10_000L, // watchDurationMillis (10s for player)
                )
            appWatcherConfigField.set(null, newAppWatcherConfig)

            android.util.Log.i(TAG, "LeakCanary configured for FishIT Player v2")
        } catch (e: Exception) {
            android.util.Log.w(TAG, "Failed to configure LeakCanary: ${e.message}")
        }
    }

    /**
     * Watch a player-related object for leaks.
     *
     * Call this when a player session is being destroyed to ensure
     * it gets garbage collected properly.
     * No-op if LeakCanary is not available.
     *
     * @param watchedObject The object to watch (e.g., PlayerSession, PlayerView)
     * @param description Human-readable description for leak reports
     */
    fun watchPlayerObject(
        watchedObject: Any,
        description: String,
    ) {
        watchObject(watchedObject, "Player: $description")
    }

    /**
     * Watch a TDLib-related object for leaks.
     *
     * TDLib objects may hold native resources and MUST be properly released.
     * No-op if LeakCanary is not available.
     *
     * @param watchedObject The object to watch
     * @param description Human-readable description
     */
    fun watchTdLibObject(
        watchedObject: Any,
        description: String,
    ) {
        watchObject(watchedObject, "TDLib: $description")
    }

    /**
     * Watch a ViewModel that should be cleared.
     *
     * Note: LeakCanary already watches ViewModels by default,
     * but this allows explicit tracking of specific VMs.
     * No-op if LeakCanary is not available.
     *
     * @param watchedObject The ViewModel to watch
     * @param description Human-readable description
     */
    fun watchViewModel(
        watchedObject: Any,
        description: String,
    ) {
        watchObject(watchedObject, "ViewModel: $description")
    }

    /**
     * Internal helper to watch objects via reflection.
     */
    private fun watchObject(
        watchedObject: Any,
        description: String,
    ) {
        if (!isAvailable) return

        try {
            val appWatcherClass = Class.forName("leakcanary.AppWatcher")
            val objectWatcherField = appWatcherClass.getField("objectWatcher")
            val objectWatcher = objectWatcherField.get(null)
            val expectWeaklyReachableMethod =
                objectWatcher.javaClass.getMethod(
                    "expectWeaklyReachable",
                    Any::class.java,
                    String::class.java,
                )
            expectWeaklyReachableMethod.invoke(objectWatcher, watchedObject, description)
        } catch (e: Exception) {
            // Silently ignore - LeakCanary might not be fully initialized
        }
    }

    /**
     * Get current retained object count.
     *
     * This returns objects that are retained but not yet analyzed.
     * For full leak history, use LeakCanary UI.
     *
     * @return retained object count, or 0 if LeakCanary not available
     */
    fun getRetainedObjectCount(): Int {
        if (!isAvailable) return 0

        return try {
            val appWatcherClass = Class.forName("leakcanary.AppWatcher")
            val objectWatcherField = appWatcherClass.getField("objectWatcher")
            val objectWatcher = objectWatcherField.get(null)
            val countMethod = objectWatcher.javaClass.getMethod("getRetainedObjectCount")
            countMethod.invoke(objectWatcher) as Int
        } catch (e: Exception) {
            0
        }
    }

    /**
     * Check if there are any retained objects waiting for analysis.
     *
     * @return true if retained objects exist, false otherwise or if LeakCanary not available
     */
    fun hasRetainedObjects(): Boolean {
        if (!isAvailable) return false

        return try {
            val appWatcherClass = Class.forName("leakcanary.AppWatcher")
            val objectWatcherField = appWatcherClass.getField("objectWatcher")
            val objectWatcher = objectWatcherField.get(null)
            val hasRetainedMethod = objectWatcher.javaClass.getMethod("getHasRetainedObjects")
            hasRetainedMethod.invoke(objectWatcher) as Boolean
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Trigger a heap dump and analysis now.
     *
     * Use sparingly - this freezes the app for several seconds.
     * No-op if LeakCanary is not available.
     */
    fun dumpHeapNow() {
        if (!isAvailable) return

        try {
            val leakCanaryClass = Class.forName("leakcanary.LeakCanary")
            val dumpHeapMethod = leakCanaryClass.getMethod("dumpHeap")
            dumpHeapMethod.invoke(null)
        } catch (e: Exception) {
            android.util.Log.w(TAG, "Failed to dump heap: ${e.message}")
        }
    }
}
