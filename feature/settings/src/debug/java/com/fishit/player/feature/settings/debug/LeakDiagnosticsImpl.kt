package com.fishit.player.feature.settings.debug

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import com.fishit.player.feature.settings.BuildConfig
import com.fishit.player.infra.logging.UnifiedLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Debug implementation of [LeakDiagnostics] using LeakCanary 2.14.
 *
 * **Issue #564 Compliance:**
 * - [isAvailable] checks BuildConfig.INCLUDE_LEAKCANARY AND runtime class availability
 * - When disabled via Gradle properties, LeakCanary classes are not in the APK
 * - Uses reflection/try-catch to safely handle missing LeakCanary classes
 * - All LeakCanary API calls are wrapped in try-catch to handle ClassNotFoundException
 *
 * **Gold Standard Implementation (when LeakCanary is present):**
 * - Uses LeakCanary's public APIs correctly
 * - Provides accurate retained object count (not analyzed leaks)
 * - Implements noise control to distinguish transient from persistent retention
 * - Directs users to LeakCanary UI for full leak details
 * - Exports a diagnostic report (not full heap dumps - those use LeakCanary's share)
 */
@Singleton
class LeakDiagnosticsImpl
    @Inject
    constructor() : LeakDiagnostics {
        // Check both BuildConfig flag AND actual class presence (compile-time gating)
        override val isAvailable: Boolean = BuildConfig.INCLUDE_LEAKCANARY && isLeakCanaryPresent()

        private fun isLeakCanaryPresent(): Boolean =
            try {
                Class.forName("leakcanary.LeakCanary")
                true
            } catch (e: ClassNotFoundException) {
                false
            }

        override fun openLeakUi(context: Context): Boolean {
            if (!isAvailable) {
                UnifiedLog.w(TAG) { "LeakCanary not available (disabled via compile-time gating)" }
                return false
            }
            return try {
                // Dynamic invocation to avoid compile-time dependency
                val leakCanaryClass = Class.forName("leakcanary.LeakCanary")
                val getIntentMethod = leakCanaryClass.getMethod("newLeakDisplayActivityIntent")
                val intent = getIntentMethod.invoke(null) as Intent
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                UnifiedLog.i(TAG) { "Opened LeakCanary UI" }
                true
            } catch (e: Exception) {
                UnifiedLog.w(TAG) { "Failed to open LeakCanary UI: ${e.message}" }
                tryOpenInternalLeakActivity(context)
            }
        }

        private fun tryOpenInternalLeakActivity(context: Context): Boolean =
            try {
                val intent =
                    Intent().apply {
                        setClassName(
                            context.packageName,
                            "leakcanary.internal.activity.LeakActivity",
                        )
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                context.startActivity(intent)
                UnifiedLog.i(TAG) { "Opened LeakCanary UI via fallback" }
                true
            } catch (e: Exception) {
                UnifiedLog.e(TAG, e) { "Failed to open LeakCanary UI (fallback also failed)" }
                false
            }

        override suspend fun exportLeakReport(
            context: Context,
            uri: Uri,
        ): Result<Unit> =
            withContext(Dispatchers.IO) {
                if (!isAvailable) {
                    return@withContext Result.failure(
                        IllegalStateException("LeakCanary not available (disabled via compile-time gating)"),
                    )
                }
                try {
                    val report = buildLeakReport(context)
                    context.contentResolver.openOutputStream(uri, "wt")?.use { out ->
                        out.write(report.toByteArray(Charsets.UTF_8))
                        out.flush()
                    } ?: return@withContext Result.failure(
                        IllegalStateException("Could not open output stream"),
                    )

                    UnifiedLog.i(TAG) { "Exported leak report to SAF destination" }
                    Result.success(Unit)
                } catch (e: Exception) {
                    UnifiedLog.e(TAG, e) { "Failed to export leak report" }
                    Result.failure(e)
                }
            }

        override fun getSummary(): LeakSummary {
            if (!isAvailable) {
                return LeakSummary(
                    leakCount = 0,
                    lastLeakUptimeMs = null,
                    note = "LeakCanary disabled (compile-time gating)",
                )
            }
            return try {
                // Use reflection for LeakCanary API calls
                val appWatcherClass = Class.forName("leakcanary.AppWatcher")
                val objectWatcherField = appWatcherClass.getField("objectWatcher")
                val objectWatcher = objectWatcherField.get(null)
                val objectWatcherClass = Class.forName("leakcanary.ObjectWatcher")

                val retainedCountMethod = objectWatcherClass.getMethod("getRetainedObjectCount")
                val hasRetainedMethod = objectWatcherClass.getMethod("getHasRetainedObjects")

                val retainedCount = retainedCountMethod.invoke(objectWatcher) as Int
                val hasRetained = hasRetainedMethod.invoke(objectWatcher) as Boolean

                LeakSummary(
                    leakCount = retainedCount,
                    lastLeakUptimeMs = null,
                    note =
                        when {
                            retainedCount == 0 && !hasRetained -> "No objects retained"
                            retainedCount in 1..2 -> "$retainedCount retained (likely transient)"
                            retainedCount in 3..4 -> "$retainedCount retained - investigate"
                            retainedCount > 0 -> "$retainedCount retained - potential leak!"
                            else -> "Monitoring active"
                        },
                )
            } catch (e: Exception) {
                UnifiedLog.w(TAG) { "Failed to get leak summary: ${e.message}" }
                LeakSummary(
                    leakCount = 0,
                    lastLeakUptimeMs = null,
                    note = "Unable to read leak info: ${e.message}",
                )
            }
        }

        override fun getDetailedStatus(): LeakDetailedStatus {
            val defaultConfig =
                LeakCanaryConfig(
                    retainedVisibleThreshold = 5,
                    computeRetainedHeapSize = false,
                    maxStoredHeapDumps = 7,
                    watchDurationMillis = 5000L,
                    watchActivities = true,
                    watchFragments = true,
                    watchViewModels = true,
                )

            if (!isAvailable) {
                return LeakDetailedStatus(
                    retainedObjectCount = 0,
                    hasRetainedObjects = false,
                    severity = RetentionSeverity.NONE,
                    statusMessage = "LeakCanary disabled (compile-time gating)",
                    config = defaultConfig,
                    memoryStats = getMemoryStats(),
                    capturedAtMs = System.currentTimeMillis(),
                )
            }
            return try {
                val appWatcherClass = Class.forName("leakcanary.AppWatcher")
                val leakCanaryClass = Class.forName("leakcanary.LeakCanary")
                val objectWatcherField = appWatcherClass.getField("objectWatcher")
                val objectWatcher = objectWatcherField.get(null)
                val objectWatcherClass = Class.forName("leakcanary.ObjectWatcher")

                val retainedCountMethod = objectWatcherClass.getMethod("getRetainedObjectCount")
                val hasRetainedMethod = objectWatcherClass.getMethod("getHasRetainedObjects")

                val retainedCount = retainedCountMethod.invoke(objectWatcher) as Int
                val hasRetained = hasRetainedMethod.invoke(objectWatcher) as Boolean

                // Get LeakCanary config via reflection
                val configField = leakCanaryClass.getField("config")
                val config = configField.get(null)
                val configClass = config.javaClass

                val threshold = configClass.getField("retainedVisibleThreshold").get(config) as Int
                val computeSize = configClass.getField("computeRetainedHeapSize").get(config) as Boolean
                val maxDumps = configClass.getField("maxStoredHeapDumps").get(config) as Int

                // Get AppWatcher config
                val watcherConfigField = appWatcherClass.getField("config")
                val watcherConfig = watcherConfigField.get(null)
                val watcherConfigClass = watcherConfig.javaClass

                val watchDuration = watcherConfigClass.getField("watchDurationMillis").get(watcherConfig) as Long
                val watchActivities = watcherConfigClass.getField("watchActivities").get(watcherConfig) as Boolean
                val watchFragments = watcherConfigClass.getField("watchFragments").get(watcherConfig) as Boolean
                val watchViewModels = watcherConfigClass.getField("watchViewModels").get(watcherConfig) as Boolean

                val severity =
                    when {
                        retainedCount == 0 -> RetentionSeverity.NONE
                        retainedCount in 1..2 -> RetentionSeverity.LOW
                        retainedCount in 3..<threshold -> RetentionSeverity.MEDIUM
                        else -> RetentionSeverity.HIGH
                    }

                LeakDetailedStatus(
                    retainedObjectCount = retainedCount,
                    hasRetainedObjects = hasRetained,
                    severity = severity,
                    statusMessage = buildStatusMessage(retainedCount, hasRetained, severity),
                    config =
                        LeakCanaryConfig(
                            retainedVisibleThreshold = threshold,
                            computeRetainedHeapSize = computeSize,
                            maxStoredHeapDumps = maxDumps,
                            watchDurationMillis = watchDuration,
                            watchActivities = watchActivities,
                            watchFragments = watchFragments,
                            watchViewModels = watchViewModels,
                        ),
                    memoryStats = getMemoryStats(),
                    capturedAtMs = System.currentTimeMillis(),
                )
            } catch (e: Exception) {
                UnifiedLog.w(TAG) { "Failed to get detailed status: ${e.message}" }
                LeakDetailedStatus(
                    retainedObjectCount = 0,
                    hasRetainedObjects = false,
                    severity = RetentionSeverity.NONE,
                    statusMessage = "Error: ${e.message}",
                    config = defaultConfig,
                    memoryStats = getMemoryStats(),
                    capturedAtMs = System.currentTimeMillis(),
                )
            }
        }

        override fun getLatestHeapDumpPath(): String? {
            // LeakCanary 2.x stores heap dumps internally and manages them
            // Users should use "Share heap dump" in LeakCanary UI for export
            // We don't expose the internal path as it's an implementation detail
            return null
        }

        override fun requestGarbageCollection() {
            UnifiedLog.d(TAG) { "Requesting garbage collection" }
            System.gc()
            // Note: This only requests GC, doesn't guarantee immediate collection
        }

        override fun triggerHeapDump() {
            if (!isAvailable) {
                UnifiedLog.w(TAG) { "Cannot trigger heap dump - LeakCanary not available" }
                return
            }
            try {
                UnifiedLog.i(TAG) { "Triggering heap dump" }
                val leakCanaryClass = Class.forName("leakcanary.LeakCanary")
                val dumpHeapMethod = leakCanaryClass.getMethod("dumpHeap")
                dumpHeapMethod.invoke(null)
            } catch (e: Exception) {
                UnifiedLog.w(TAG) { "Failed to trigger heap dump: ${e.message}" }
            }
        }

        private fun getMemoryStats(): MemoryStats {
            val runtime = Runtime.getRuntime()
            val usedMB = (runtime.totalMemory() - runtime.freeMemory()) / 1024 / 1024
            val totalMB = runtime.totalMemory() / 1024 / 1024
            val maxMB = runtime.maxMemory() / 1024 / 1024
            val freeMB = runtime.freeMemory() / 1024 / 1024
            return MemoryStats(
                usedMemoryMb = usedMB,
                totalMemoryMb = totalMB,
                maxMemoryMb = maxMB,
                freeMemoryMb = freeMB,
            )
        }

        private fun buildStatusMessage(
            retainedCount: Int,
            hasRetained: Boolean,
            severity: RetentionSeverity,
        ): String =
            buildString {
                append("Retained: $retainedCount objects")
                when (severity) {
                    RetentionSeverity.NONE -> append(" ✅")
                    RetentionSeverity.LOW -> append(" (likely transient GC delay)")
                    RetentionSeverity.MEDIUM -> append(" ⚠️ Worth investigating")
                    RetentionSeverity.HIGH -> append(" 🔴 Potential memory leak!")
                }
            }

        private suspend fun buildLeakReport(context: Context): String =
            buildString {
                appendLine("=== FishIT-Player Leak Diagnostics Report ===")
                appendLine("Generated: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())}")
                appendLine()

                // Device info
                appendLine("## Device Information")
                appendLine("- Model: ${Build.MODEL}")
                appendLine("- Manufacturer: ${Build.MANUFACTURER}")
                appendLine("- Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
                appendLine()

                // App info
                appendLine("## App Information")
                try {
                    val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
                    appendLine("- Package: ${context.packageName}")
                    appendLine("- Version: ${packageInfo.versionName}")
                    @Suppress("DEPRECATION")
                    appendLine("- Version Code: ${packageInfo.versionCode}")
                } catch (e: Exception) {
                    appendLine("- Unable to read package info: ${e.message}")
                }
                appendLine()

                // Leak status
                appendLine("## Leak Status")
                val summary = getSummary()
                appendLine("- Retained Objects: ${summary.leakCount}")
                appendLine("- Status: ${summary.note}")
                appendLine()

                val detailed = getDetailedStatus()
                appendLine("## Detailed Status")
                appendLine("- Severity: ${detailed.severity}")
                appendLine("- Threshold: ${detailed.config.retainedVisibleThreshold}")
                appendLine("- Message: ${detailed.statusMessage}")
                appendLine()

                // Memory stats
                appendLine("## Memory")
                appendLine("- Used: ${detailed.memoryStats.usedMemoryMb}MB")
                appendLine("- Total: ${detailed.memoryStats.totalMemoryMb}MB")
                appendLine("- Max: ${detailed.memoryStats.maxMemoryMb}MB")
                appendLine("- Usage: ${detailed.memoryStats.usagePercentage}%")
                appendLine()

                // Config info
                appendLine("## LeakCanary Config")
                appendLine("- Watch Activities: ${detailed.config.watchActivities}")
                appendLine("- Watch Fragments: ${detailed.config.watchFragments}")
                appendLine("- Watch ViewModels: ${detailed.config.watchViewModels}")
                appendLine("- Watch Duration: ${detailed.config.watchDurationMillis}ms")
                appendLine()

                appendLine("=== End of Report ===")
            }

        private companion object {
            private const val TAG = "LeakDiagnostics"
        }
    }
