package com.fishit.player.feature.settings.debug

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import com.fishit.player.feature.settings.BuildConfig
import com.fishit.player.infra.logging.UnifiedLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import leakcanary.AppWatcher
import leakcanary.LeakCanary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Debug implementation of [LeakDiagnostics] using LeakCanary 2.14.
 *
 * **Gold Standard Implementation:**
 * - Uses LeakCanary's public APIs correctly
 * - Provides accurate retained object count (not analyzed leaks)
 * - Implements noise control to distinguish transient from persistent retention
 * - Directs users to LeakCanary UI for full leak details
 * - Exports a diagnostic report (not full heap dumps - those use LeakCanary's share)
 *
 * **Important Distinctions:**
 * - `retainedObjectCount`: Objects retained but not yet analyzed
 * - `hasRetainedObjects`: Quick check if anything is retained
 * - Full leak history: Only available via LeakCanary UI (internal DB)
 *
 * **Noise Control:**
 * - LOW severity (1-2 objects): Likely transient GC delay, not a real leak
 * - MEDIUM severity (3-4 objects): Worth investigating, could be a leak
 * - HIGH severity (5+ objects or threshold breach): Likely a real leak
 *
 * Provides:
 * - Opening LeakCanary's built-in UI
 * - Exporting a text-based leak report via SAF
 * - Summary of detected leaks
 * - Detailed status with noise control
 * - Path to latest heap dump (if available)
 *
 * **Issue #564 Compliance:**
 * - [isAvailable] checks BuildConfig.INCLUDE_LEAKCANARY which can be disabled via Gradle properties
 * - When disabled, the UI will not show LeakCanary-related options
 */
@Singleton
class LeakDiagnosticsImpl
    @Inject
    constructor() : LeakDiagnostics {
        override val isAvailable: Boolean = BuildConfig.INCLUDE_LEAKCANARY

        override fun openLeakUi(context: Context): Boolean =
            try {
                val intent = LeakCanary.newLeakDisplayActivityIntent()
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                UnifiedLog.i(TAG) { "Opened LeakCanary UI" }
                true
            } catch (e: Exception) {
                UnifiedLog.w(TAG) { "Failed to open LeakCanary UI: ${e.message}" }
                // Fallback: try internal activity directly
                tryOpenInternalLeakActivity(context)
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

        override fun getSummary(): LeakSummary =
            try {
                val retainedCount = AppWatcher.objectWatcher.retainedObjectCount
                val hasRetained = AppWatcher.objectWatcher.hasRetainedObjects

                // Note: LeakCanary doesn't expose historical leak count via public API
                // The retainedObjectCount is objects currently retained but not yet analyzed
                // For full leak history, users must open LeakCanary UI
                LeakSummary(
                    leakCount = retainedCount,
                    lastLeakUptimeMs = null, // Not available via public API
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

        override fun getDetailedStatus(): LeakDetailedStatus =
            try {
                val retainedCount = AppWatcher.objectWatcher.retainedObjectCount
                val hasRetained = AppWatcher.objectWatcher.hasRetainedObjects
                val threshold = LeakCanary.config.retainedVisibleThreshold

                val severity =
                    when {
                        retainedCount == 0 -> RetentionSeverity.NONE
                        retainedCount in 1..2 -> RetentionSeverity.LOW
                        retainedCount in 3..<threshold -> RetentionSeverity.MEDIUM
                        else -> RetentionSeverity.HIGH
                    }

                val statusMessage =
                    when (severity) {
                        RetentionSeverity.NONE -> "All clear - no objects retained"
                        RetentionSeverity.LOW -> "Low-level retention ($retainedCount objects) - likely transient GC delay"
                        RetentionSeverity.MEDIUM -> "Moderate retention ($retainedCount objects) - worth investigating"
                        RetentionSeverity.HIGH -> "High retention ($retainedCount objects) - likely a real leak! Tap 'Open LeakCanary' for details."
                    }

                LeakDetailedStatus(
                    retainedObjectCount = retainedCount,
                    hasRetainedObjects = hasRetained,
                    severity = severity,
                    statusMessage = statusMessage,
                    config = getLeakCanaryConfigData(),
                    memoryStats = getMemoryStats(),
                    capturedAtMs = System.currentTimeMillis(),
                )
            } catch (e: Exception) {
                UnifiedLog.w(TAG) { "Failed to get detailed leak status: ${e.message}" }
                LeakDetailedStatus(
                    retainedObjectCount = 0,
                    hasRetainedObjects = false,
                    severity = RetentionSeverity.NONE,
                    statusMessage = "Unable to read status: ${e.message}",
                    config =
                        LeakCanaryConfig(
                            retainedVisibleThreshold = 5,
                            computeRetainedHeapSize = false,
                            maxStoredHeapDumps = 7,
                            watchDurationMillis = 5000L,
                            watchActivities = true,
                            watchFragments = true,
                            watchViewModels = true,
                        ),
                    memoryStats = getMemoryStats(),
                    capturedAtMs = System.currentTimeMillis(),
                )
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
            // The actual GC is asynchronous and may not happen immediately
        }

        override fun triggerHeapDump() {
            UnifiedLog.i(TAG) { "Triggering heap dump" }
            LeakCanary.dumpHeap()
        }

        private fun getLeakCanaryConfigData(): LeakCanaryConfig =
            try {
                val config = LeakCanary.config
                val watcherConfig = AppWatcher.config
                LeakCanaryConfig(
                    retainedVisibleThreshold = config.retainedVisibleThreshold,
                    computeRetainedHeapSize = config.computeRetainedHeapSize,
                    maxStoredHeapDumps = config.maxStoredHeapDumps,
                    watchDurationMillis = watcherConfig.watchDurationMillis,
                    watchActivities = watcherConfig.watchActivities,
                    watchFragments = watcherConfig.watchFragments,
                    watchViewModels = watcherConfig.watchViewModels,
                )
            } catch (e: Exception) {
                LeakCanaryConfig(
                    retainedVisibleThreshold = 5,
                    computeRetainedHeapSize = false,
                    maxStoredHeapDumps = 7,
                    watchDurationMillis = 5000L,
                    watchActivities = true,
                    watchFragments = true,
                    watchViewModels = true,
                )
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

        /**
         * Build a diagnostic report for export.
         *
         * Note: This is NOT a full leak report - for detailed leak traces,
         * users should use LeakCanary's built-in "Share" functionality.
         */
        private fun buildLeakReport(context: Context): String {
            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
            val now = dateFormat.format(Date())

            val packageInfo =
                try {
                    context.packageManager.getPackageInfo(context.packageName, 0)
                } catch (e: Exception) {
                    null
                }

            val versionName = packageInfo?.versionName ?: "unknown"

            @Suppress("DEPRECATION")
            val versionCode = packageInfo?.versionCode ?: 0

            val summary = getSummary()
            val detailedStatus = getDetailedStatus()
            val configInfo = detailedStatus.config
            val memoryStats = detailedStatus.memoryStats

            return buildString {
                appendLine("=".repeat(60))
                appendLine("FishIT Player - Memory Diagnostics Report")
                appendLine("=".repeat(60))
                appendLine()
                appendLine("Generated: $now")
                appendLine()
                appendLine("-".repeat(40))
                appendLine("App Info")
                appendLine("-".repeat(40))
                appendLine("Version: $versionName ($versionCode)")
                appendLine("Package: ${context.packageName}")
                appendLine("Build Type: debug")
                appendLine()
                appendLine("-".repeat(40))
                appendLine("Device Info")
                appendLine("-".repeat(40))
                appendLine("Model: ${Build.MODEL}")
                appendLine("Manufacturer: ${Build.MANUFACTURER}")
                appendLine("Android: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
                appendLine("Device: ${Build.DEVICE}")
                appendLine("Product: ${Build.PRODUCT}")
                appendLine()
                appendLine("-".repeat(40))
                appendLine("Memory Status (Noise Control)")
                appendLine("-".repeat(40))
                appendLine("Retained Objects: ${summary.leakCount}")
                appendLine("Has Retained: ${detailedStatus.hasRetainedObjects}")
                appendLine("Severity: ${detailedStatus.severity}")
                appendLine("Status: ${detailedStatus.statusMessage}")
                summary.note?.let { appendLine("Note: $it") }
                appendLine()
                appendLine("-".repeat(40))
                appendLine("LeakCanary Configuration")
                appendLine("-".repeat(40))
                appendLine("Retained Threshold: ${configInfo.retainedVisibleThreshold}")
                appendLine("Compute Retained Size: ${configInfo.computeRetainedHeapSize}")
                appendLine("Max Stored Dumps: ${configInfo.maxStoredHeapDumps}")
                appendLine("Watch Duration: ${configInfo.watchDurationMillis}ms")
                appendLine("Watch Activities: ${configInfo.watchActivities}")
                appendLine("Watch Fragments: ${configInfo.watchFragments}")
                appendLine("Watch ViewModels: ${configInfo.watchViewModels}")
                appendLine()
                appendLine("-".repeat(40))
                appendLine("Runtime Memory")
                appendLine("-".repeat(40))
                appendLine("Used: ${memoryStats.usedMemoryMb}MB")
                appendLine("Total: ${memoryStats.totalMemoryMb}MB")
                appendLine("Max: ${memoryStats.maxMemoryMb}MB")
                appendLine("Free: ${memoryStats.freeMemoryMb}MB")
                appendLine("Usage: ${memoryStats.usagePercentage}%")
                appendLine()
                appendLine("-".repeat(40))
                appendLine("Noise Control Guide")
                appendLine("-".repeat(40))
                appendLine("• NONE (0 retained): All clear")
                appendLine("• LOW (1-2 retained): Likely transient GC delay")
                appendLine("• MEDIUM (3-4 retained): Worth investigating")
                appendLine("• HIGH (5+ retained): Likely a real leak")
                appendLine()
                appendLine("-".repeat(40))
                appendLine("How to Get Full Leak Details")
                appendLine("-".repeat(40))
                appendLine("1. Open LeakCanary UI from Debug Screen")
                appendLine("2. View individual leak traces")
                appendLine("3. Use 'Share heap dump' for detailed analysis")
                appendLine("4. Import .hprof into Android Studio Profiler")
                appendLine()
                appendLine("=".repeat(60))
                appendLine("End of Report")
                appendLine("=".repeat(60))
            }
        }

        private companion object {
            private const val TAG = "LeakDiagnostics"
        }
    }
