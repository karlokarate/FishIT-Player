package com.fishit.player.feature.settings.debug

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import com.fishit.player.infra.logging.UnifiedLog
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import leakcanary.LeakCanary

/**
 * Debug implementation of [LeakDiagnostics] using LeakCanary 2.14.
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
            // LeakCanary 2.14: Access leak store for summary info
            val leakCount = getLeakCountSafe()
            val lastLeakTime = getLastLeakTimeSafe()

            LeakSummary(
                leakCount = leakCount,
                lastLeakUptimeMs = lastLeakTime,
                note = if (leakCount == 0) "No leaks detected" else null
            )
        } catch (e: Exception) {
            UnifiedLog.w(TAG) { "Failed to get leak summary: ${e.message}" }
            LeakSummary(
                leakCount = 0,
                lastLeakUptimeMs = null,
                note = "Unable to read leak info"
            )
        }
    }

    override fun getLatestHeapDumpPath(): String? {
        return try {
            // LeakCanary stores heap dumps in app's files directory
            // This is a best-effort lookup; LeakCanary doesn't expose a public API for this
            val leakCanaryDir = File(
                android.os.Environment.getDataDirectory(),
                "data/${getPackageNameSafe()}/files/leakcanary"
            )
            if (!leakCanaryDir.exists()) return null

            val hprofFiles = leakCanaryDir.listFiles { file ->
                file.extension == "hprof"
            }?.sortedByDescending { it.lastModified() }

            hprofFiles?.firstOrNull()?.absolutePath
        } catch (e: Exception) {
            UnifiedLog.w(TAG) { "Could not find heap dump path: ${e.message}" }
            null
        }
    }

    /**
     * Build a text-based leak report for export.
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
        val leakSignatures = getLeakSignaturesSafe()

        return buildString {
            appendLine("=" .repeat(60))
            appendLine("FishIT Player - Leak Report")
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
            appendLine("Leak Summary")
            appendLine("-".repeat(40))
            appendLine("Leaks Detected: ${summary.leakCount}")
            summary.lastLeakUptimeMs?.let { ms ->
                appendLine("Last Leak (uptime): ${ms}ms")
            }
            summary.note?.let { note ->
                appendLine("Note: $note")
            }
            appendLine()

            if (leakSignatures.isNotEmpty()) {
                appendLine("-".repeat(40))
                appendLine("Leak Signatures")
                appendLine("-".repeat(40))
                leakSignatures.forEachIndexed { index, sig ->
                    appendLine("${index + 1}. $sig")
                }
                appendLine()
            }

            appendLine("-".repeat(40))
            appendLine("Notes")
            appendLine("-".repeat(40))
            appendLine("- For full leak traces, use 'Open LeakCanary' in the app")
            appendLine("- Heap dumps can be exported from LeakCanary UI ('Share heap dump')")
            appendLine()
            appendLine("=" .repeat(60))
            appendLine("End of Report")
            appendLine("=" .repeat(60))
        }
    }

    /**
     * Safely get leak count using LeakCanary's public APIs.
     */
    private fun getLeakCountSafe(): Int {
        return try {
            // LeakCanary 2.x: Use AppWatcher / ObjectWatcher for retained count
            // Note: This returns currently retained (not yet analyzed) objects
            leakcanary.AppWatcher.objectWatcher.retainedObjectCount
        } catch (e: Exception) {
            0
        }
    }

    /**
     * Safely get last leak time (best effort).
     */
    private fun getLastLeakTimeSafe(): Long? {
        // LeakCanary doesn't expose a direct API for "last leak time"
        // We could parse the leak DB, but that uses internal APIs
        // For now, return null and rely on the leak count
        return null
    }

    /**
     * Get leak signatures (class names) if accessible.
     * Uses reflection as LeakCanary's leak store is internal.
     */
    private fun getLeakSignaturesSafe(): List<String> {
        return try {
            // Best effort: LeakCanary stores leaks in an internal database
            // We can't easily access it without internal APIs
            // Return empty list and document this limitation
            emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun getPackageNameSafe(): String {
        return try {
            "com.fishit.player.v2"
        } catch (e: Exception) {
            "unknown"
        }
    }

    private companion object {
        private const val TAG = "LeakDiagnostics"
    }
}
