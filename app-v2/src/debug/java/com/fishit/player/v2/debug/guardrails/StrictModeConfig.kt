package com.fishit.player.v2.debug.guardrails

import android.os.Build
import android.os.StrictMode
import android.util.Log
import java.util.concurrent.Executors

/**
 * StrictMode Configuration for FishIT Player v2 Debug Builds.
 *
 * **Purpose:**
 * - Detect disk reads/writes on main thread
 * - Detect network calls on main thread
 * - Detect custom violations for ObjectBox operations
 * - Crash debug builds early to prevent performance issues reaching production
 *
 * **Related Issues:**
 * - #609: Performance Baseline and Regression Guards
 * - #608: ObxWriteConfig Optimization (PR merged)
 *
 * **Contract:**
 * Per `OBX_PERFORMANCE_BASELINE.md`:
 * - Main thread DB access is a **CRITICAL** violation
 * - Network on main thread is **FORBIDDEN**
 * - Violations should crash debug builds (not just log)
 *
 * **Usage:**
 * Call [enable] from Application.onCreate() in debug builds ONLY.
 *
 * **Architecture:**
 * - Thread Policy: Detect disk/network violations
 * - VM Policy: Detect leaked objects, unclosed resources
 * - Custom Listener: Enhanced logging for ObjectBox-specific violations
 *
 * @see OBX_PERFORMANCE_BASELINE.md for performance targets
 * @see RepoBoundaryChecker for N+1 query detection
 */
object StrictModeConfig {
    private const val TAG = "StrictModeConfig"

    /**
     * Enable StrictMode with comprehensive checks.
     *
     * **Call this from Application.onCreate() in debug builds:**
     * ```kotlin
     * if (BuildConfig.DEBUG) {
     *     StrictModeConfig.enable()
     * }
     * ```
     *
     * **Violations trigger:**
     * 1. Log with full stack trace
     * 2. Custom listener notification
     * 3. Death penalty (crash) for critical violations
     */
    fun enable() {
        enableThreadPolicy()
        enableVmPolicy()
        Log.i(TAG, "StrictMode enabled - violations will CRASH debug builds")
    }

    /**
     * Enable Thread Policy - detects main thread violations.
     *
     * **Detected Violations:**
     * - Disk reads (e.g., ObjectBox query on main thread)
     * - Disk writes (e.g., ObjectBox put on main thread)
     * - Network calls (e.g., HTTP request without coroutine)
     * - Custom slow calls (e.g., synchronous image decoding)
     *
     * **Penalty:**
     * - Log full violation
     * - Trigger custom listener (ObjectBoxViolationListener)
     * - Death penalty (CRASH) for critical violations
     */
    private fun enableThreadPolicy() {
        val threadPolicyBuilder = StrictMode.ThreadPolicy.Builder()
            .detectDiskReads()   // ❌ ObjectBox query on main thread
            .detectDiskWrites()  // ❌ ObjectBox put on main thread
            .detectNetwork()     // ❌ HTTP/TDLib calls without coroutines
            .penaltyLog()        // Always log violations

        // Custom slow call detection (API 23+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            threadPolicyBuilder.detectCustomSlowCalls()
        }

        // Resource mismatches (API 23+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            threadPolicyBuilder.detectResourceMismatches()
        }

        // Unbuffered I/O (API 26+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            threadPolicyBuilder.detectUnbufferedIo()
        }

        // Custom listener for enhanced logging
        threadPolicyBuilder.penaltyListener(Executors.newSingleThreadExecutor()) { violation ->
            handleThreadViolation(violation)
        }

        // Death penalty - crash on violations
        threadPolicyBuilder.penaltyDeath()

        StrictMode.setThreadPolicy(threadPolicyBuilder.build())
        Log.i(TAG, "Thread Policy enabled: disk reads, disk writes, network on main thread will CRASH")
    }

    /**
     * Enable VM Policy - detects leaked objects and unclosed resources.
     *
     * **Detected Violations:**
     * - Activity leaks (not released after onDestroy)
     * - SQLite/Cursor leaks (not closed)
     * - Closeable leaks (e.g., unclosed Streams)
     * - Content Provider leaks
     * - Cleartext network traffic (non-HTTPS)
     *
     * **Penalty:**
     * - Log violation with stack trace
     * - No death penalty for VM violations (too aggressive)
     */
    private fun enableVmPolicy() {
        val vmPolicyBuilder = StrictMode.VmPolicy.Builder()
            .detectActivityLeaks()
            .detectLeakedClosableObjects()
            .detectLeakedSqliteObjects()
            .penaltyLog()

        // Leak of registration objects (API 16+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            vmPolicyBuilder.detectLeakedRegistrationObjects()
        }

        // File URI exposure (API 18+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
            vmPolicyBuilder.detectFileUriExposure()
        }

        // Cleartext network (API 23+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            vmPolicyBuilder.detectCleartextNetwork()
        }

        // Content provider not closed (API 24+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            vmPolicyBuilder.detectContentUriWithoutPermission()
        }

        // Untagged sockets (API 26+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vmPolicyBuilder.detectUntaggedSockets()
        }

        // Implicit direct boot (API 29+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            vmPolicyBuilder.detectImplicitDirectBoot()
        }

        // Credential protected while locked (API 29+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            vmPolicyBuilder.detectCredentialProtectedWhileLocked()
        }

        // Incorrect context use (API 31+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            vmPolicyBuilder.detectIncorrectContextUse()
        }

        // Unsafe intent launch (API 31+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            vmPolicyBuilder.detectUnsafeIntentLaunch()
        }

        StrictMode.setVmPolicy(vmPolicyBuilder.build())
        Log.i(TAG, "VM Policy enabled: leaked objects, unclosed resources will be logged")
    }

    /**
     * Handle thread violations with enhanced logging.
     *
     * **Enhanced Detection for ObjectBox:**
     * - Check stack trace for ObjectBox method calls
     * - Identify specific violation type (query vs put)
     * - Log violation category for easier debugging
     *
     * @param violation The StrictMode violation
     */
    private fun handleThreadViolation(violation: Throwable) {
        val stackTrace = violation.stackTraceToString()
        val violationCategory = categorizeViolation(stackTrace)

        Log.e(
            TAG,
            """
            ========================================
            STRICTMODE VIOLATION DETECTED!
            ========================================
            Category: $violationCategory
            Thread: ${Thread.currentThread().name}
            
            Stack Trace:
            $stackTrace
            ========================================
            """.trimIndent()
        )

        // Additional context for ObjectBox violations
        if (violationCategory.startsWith("ObjectBox")) {
            Log.e(TAG, """
                
                ⚠️  OBJECTBOX VIOLATION DETAILS:
                - This indicates a database operation on the main thread
                - Use Dispatchers.IO for all ObjectBox operations
                - Use reactive Flows with .flowOn(Dispatchers.IO)
                
                Example Fix:
                ```kotlin
                // ❌ WRONG
                val items = box.all
                
                // ✅ CORRECT
                viewModelScope.launch(Dispatchers.IO) {
                    val items = box.all
                    withContext(Dispatchers.Main) {
                        _state.value = items
                    }
                }
                ```
                
                See: OBX_PERFORMANCE_BASELINE.md
                See: OBJECTBOX_REACTIVE_PATTERNS.md
                
            """.trimIndent())
        }
    }

    /**
     * Categorize violation based on stack trace analysis.
     *
     * **Categories:**
     * - ObjectBox Query: box.query(), box.all, box.get()
     * - ObjectBox Write: box.put(), box.remove()
     * - Network: OkHttp, HttpURLConnection
     * - TDLib: TdlClient calls
     * - Generic: Unknown disk I/O
     *
     * @param stackTrace Full stack trace string
     * @return Human-readable violation category
     */
    private fun categorizeViolation(stackTrace: String): String {
        return when {
            // ObjectBox violations
            stackTrace.contains("io.objectbox.Box.query") ||
                stackTrace.contains("io.objectbox.Box.all") ||
                stackTrace.contains("io.objectbox.Box.get") ||
                stackTrace.contains("io.objectbox.Query.find") -> {
                "ObjectBox Query (Disk Read on Main Thread)"
            }

            stackTrace.contains("io.objectbox.Box.put") ||
                stackTrace.contains("io.objectbox.Box.remove") -> {
                "ObjectBox Write (Disk Write on Main Thread)"
            }

            // Network violations
            stackTrace.contains("okhttp3") ||
                stackTrace.contains("java.net.HttpURLConnection") ||
                stackTrace.contains("java.net.URL.openConnection") -> {
                "Network Call on Main Thread"
            }

            // TDLib violations
            stackTrace.contains("org.drinkless.td") ||
                stackTrace.contains("dev.g000sha256.tdl") -> {
                "TDLib Call on Main Thread"
            }

            // Image loading (should be rare with Coil)
            stackTrace.contains("android.graphics.BitmapFactory") -> {
                "Image Decoding on Main Thread"
            }

            // Generic disk I/O
            stackTrace.contains("java.io.FileInputStream") ||
                stackTrace.contains("java.io.FileOutputStream") -> {
                "File I/O on Main Thread"
            }

            else -> "Unknown Disk I/O on Main Thread"
        }
    }

    /**
     * Disable StrictMode (for specific test scenarios).
     *
     * **Use sparingly!** Only disable for:
     * - Benchmark tests that intentionally violate policies
     * - Integration tests with mock data providers
     *
     * **Re-enable immediately after test:**
     * ```kotlin
     * StrictModeConfig.disable()
     * try {
     *     // Test that violates policy
     * } finally {
     *     StrictModeConfig.enable()
     * }
     * ```
     */
    fun disable() {
        StrictMode.setThreadPolicy(StrictMode.ThreadPolicy.LAX)
        StrictMode.setVmPolicy(StrictMode.VmPolicy.LAX)
        Log.w(TAG, "StrictMode DISABLED - re-enable after test!")
    }

    /**
     * Check if StrictMode is currently enabled.
     *
     * @return true if thread policy is active, false if LAX
     */
    fun isEnabled(): Boolean {
        // There's no direct way to check policy state, so we rely on our own tracking
        // For now, we assume it's enabled if we called enable() and not disable()
        // This is a simplified check - in production, track state with a flag
        return try {
            val threadPolicy = StrictMode.getThreadPolicy()
            threadPolicy != StrictMode.ThreadPolicy.LAX
        } catch (e: Exception) {
            false
        }
    }
}
