package com.fishit.player.feature.settings.debug

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import com.fishit.player.infra.logging.UnifiedLog
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import leakcanary.AppWatcher
import leakcanary.LeakCanary

/**
 * Debug implementation of [LeakDiagnostics] using LeakCanary 2.14.
 *
 * **Gold Standard Implementation:**
 * - Uses LeakCanary's public APIs correctly
 * - Provides accurate retained object count (not analyzed leaks)
 * - Directs users to LeakCanary UI for full leak details
 * - Exports a diagnostic report (not full heap dumps - those use LeakCanary's share)
 *
 * **Important Distinctions:**
 * - `retainedObjectCount`: Objects retained but not yet analyzed
 * - `hasRetainedObjects`: Quick check if anything is retained
 * - Full leak history: Only available via LeakCanary UI (internal DB)
 *
 * Provides:
 * - Opening LeakCanary's built-in UI
 * - Exporting a text-based leak report via SAF
 * - Summary of detected leaks
 * - Path to latest heap dump (if available)
 */
@Singleton
class LeakDiagnosticsImpl @Inject constructor() : LeakDiagnostics {

    override val isAvailable: Boolean = true

    override fun openLeakUi(context: Context): Boolean {
        return try {
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
    }

    private fun tryOpenInternalLeakActivity(context: Context): Boolean {
        return try {
            val intent = Intent().apply {
                setClassName(
                    context.packageName,
                    "leakcanary.internal.activity.LeakActivity"
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
    }

    override suspend fun exportLeakReport(context: Context, uri: Uri): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val report = buildLeakReport(context)
                context.contentResolver.openOutputStream(uri, "wt")?.use { out ->
                    out.write(report.toByteArray(Charsets.UTF_8))
                    out.flush()
                } ?: return@withContext Result.failure(
                    IllegalStateException("Could not open output stream")
                )

                UnifiedLog.i(TAG) { "Exported leak report to SAF destination" }
                Result.success(Unit)
            } catch (e: Exception) {
                UnifiedLog.e(TAG, e) { "Failed to export leak report" }
                Result.failure(e)
            }
        }

    override fun getSummary(): LeakSummary {
        return try {
            val retainedCount = AppWatcher.objectWatcher.retainedObjectCount
            val hasRetained = AppWatcher.objectWatcher.hasRetainedObjects

            // Note: LeakCanary doesn't expose historical leak count via public API
            // The retainedObjectCount is objects currently retained but not yet analyzed
            // For full leak history, users must open LeakCanary UI
            LeakSummary(
                leakCount = retainedCount,
                lastLeakUptimeMs = null, // Not available via public API
                note = when {
                    retainedCount == 0 && !hasRetained -> "No objects retained"
                    retainedCount > 0 -> "$retainedCount object(s) retained - tap 'Open LeakCanary' for details"
                    else -> "Monitoring active"
                }
            )
        } catch (e: Exception) {
            UnifiedLog.w(TAG) { "Failed to get leak summary: ${e.message}" }
            LeakSummary(
                leakCount = 0,
                lastLeakUptimeMs = null,
                note = "Unable to read leak info: ${e.message}"
            )
        }
    }

    override fun getLatestHeapDumpPath(): String? {
        // LeakCanary 2.x stores heap dumps internally and manages them
        // Users should use "Share heap dump" in LeakCanary UI for export
        // We don't expose the internal path as it's an implementation detail
        return null
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

        val packageInfo = try {
            context.packageManager.getPackageInfo(context.packageName, 0)
        } catch (e: Exception) {
            null
        }

        val versionName = packageInfo?.versionName ?: "unknown"
        @Suppress("DEPRECATION")
        val versionCode = packageInfo?.versionCode ?: 0

        val summary = getSummary()
        val configInfo = getLeakCanaryConfigInfo()

        return buildString {
            appendLine("=" .repeat(60))
            appendLine("FishIT Player - Memory Diagnostics Report")
            appendLine("=" .repeat(60))
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
            appendLine("Memory Status")
            appendLine("-".repeat(40))
            appendLine("Retained Objects: ${summary.leakCount}")
            appendLine("Has Retained: ${AppWatcher.objectWatcher.hasRetainedObjects}")
            summary.note?.let { appendLine("Status: $it") }
            appendLine()
            appendLine("-".repeat(40))
            appendLine("LeakCanary Configuration")
            appendLine("-".repeat(40))
            configInfo.forEach { (key, value) ->
                appendLine("$key: $value")
            }
            appendLine()
            appendLine("-".repeat(40))
            appendLine("Runtime Memory")
            appendLine("-".repeat(40))
            val runtime = Runtime.getRuntime()
            val usedMB = (runtime.totalMemory() - runtime.freeMemory()) / 1024 / 1024
            val totalMB = runtime.totalMemory() / 1024 / 1024
            val maxMB = runtime.maxMemory() / 1024 / 1024
            appendLine("Used: ${usedMB}MB")
            appendLine("Total: ${totalMB}MB")
            appendLine("Max: ${maxMB}MB")
            appendLine()
            appendLine("-".repeat(40))
            appendLine("How to Get Full Leak Details")
            appendLine("-".repeat(40))
            appendLine("1. Open LeakCanary UI from Debug Screen")
            appendLine("2. View individual leak traces")
            appendLine("3. Use 'Share heap dump' for detailed analysis")
            appendLine("4. Import .hprof into Android Studio Profiler")
            appendLine()
            appendLine("=" .repeat(60))
            appendLine("End of Report")
            appendLine("=" .repeat(60))
        }
    }

    /**
     * Get LeakCanary configuration info for diagnostics.
     */
    private fun getLeakCanaryConfigInfo(): Map<String, String> {
        return try {
            val config = LeakCanary.config
            val watcherConfig = AppWatcher.config
            mapOf(
                "Retained Threshold" to config.retainedVisibleThreshold.toString(),
                "Compute Retained Size" to config.computeRetainedHeapSize.toString(),
                "Max Stored Dumps" to config.maxStoredHeapDumps.toString(),
                "Watch Activities" to watcherConfig.watchActivities.toString(),
                "Watch Fragments" to watcherConfig.watchFragments.toString(),
                "Watch ViewModels" to watcherConfig.watchViewModels.toString(),
                "Watch Services" to watcherConfig.watchServices.toString(),
                "Watch Duration" to "${watcherConfig.watchDurationMillis}ms",
            )
        } catch (e: Exception) {
            mapOf("Error" to "Could not read config: ${e.message}")
        }
    }

    private companion object {
        private const val TAG = "LeakDiagnostics"
    }
}
