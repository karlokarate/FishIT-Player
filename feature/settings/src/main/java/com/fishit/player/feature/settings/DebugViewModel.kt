package com.fishit.player.feature.settings

import android.content.Context
import android.net.Uri
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkManager
import com.fishit.player.core.catalogsync.CatalogSyncWorkScheduler
import com.fishit.player.core.catalogsync.SyncStateObserver
import com.fishit.player.core.catalogsync.SyncUiState
import com.fishit.player.core.catalogsync.TmdbEnrichmentScheduler
import com.fishit.player.feature.settings.debug.ChuckerDiagnostics
import com.fishit.player.feature.settings.debug.LeakDetailedStatus
import com.fishit.player.feature.settings.debug.LeakDiagnostics
import com.fishit.player.feature.settings.debug.LeakSummary
import com.fishit.player.feature.settings.debug.WorkManagerDebugConstants
import com.fishit.player.feature.settings.debug.WorkManagerSnapshot
import com.fishit.player.feature.settings.debug.toTaskInfo
import com.fishit.player.infra.logging.BufferedLogEntry
import com.fishit.player.infra.logging.LogBufferProvider
import com.fishit.player.infra.logging.UnifiedLog
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Debug screen state */
data class DebugState(
        // System info
        val appVersion: String = "2.0.0-dev",
        val buildType: String = "debug",
        val deviceModel: String = "",
        val androidVersion: String = "",

        // Connection status
        val telegramConnected: Boolean = false,
        val telegramUser: String? = null,
        val xtreamConnected: Boolean = false,
        val xtreamServer: String? = null,

        // API credential status (separate from connection status!)
        val telegramCredentialsConfigured: Boolean = false,
        val telegramCredentialStatus: String = "Unknown",
        val tmdbApiKeyConfigured: Boolean = false,

        // Cache info
        val telegramCacheSize: String = "0 MB",
        val imageCacheSize: String = "0 MB",
        val dbSize: String = "0 MB",

        // Pipeline stats
        val telegramMediaCount: Int = 0,
        val xtreamVodCount: Int = 0,
        val xtreamSeriesCount: Int = 0,
        val xtreamLiveCount: Int = 0,

        // Logs
        val recentLogs: List<LogEntry> = emptyList(),
        val isLoadingLogs: Boolean = false,

        // Actions
        val isClearingCache: Boolean = false,
        val lastActionResult: String? = null,

        // === Catalog Sync (SSOT via WorkManager) ===
        val syncState: SyncUiState = SyncUiState.Idle,

        // === Debug Tools Runtime Toggles ===
        val networkInspectorEnabled: Boolean = false,
        val leakCanaryEnabled: Boolean = false,

        // === LeakCanary (Memory Diagnostics) ===
        val leakSummary: LeakSummary = LeakSummary(0, null, null),
        /** Detailed leak status with noise control */
        val leakDetailedStatus: LeakDetailedStatus? = null,
        val isLeakCanaryAvailable: Boolean = false,

        // === Chucker (HTTP Inspector) ===
        val isChuckerAvailable: Boolean = false,

        // === WorkManager Snapshot (Diagnostics) ===
        val workManagerSnapshot: WorkManagerSnapshot = WorkManagerSnapshot.empty(),
)

/** Simple log entry for display */
data class LogEntry(val timestamp: Long, val level: LogLevel, val tag: String, val message: String)

enum class LogLevel {
    DEBUG,
    INFO,
    WARN,
    ERROR
}

/**
 * Debug ViewModel - Manages debug/diagnostics screen
 *
 * **REAL DATA SOURCES:**
 * - [LogBufferProvider] for in-memory log buffer
 * - [DebugInfoProvider] for connection status, cache sizes, content counts
 * - [SyncStateObserver] for catalog sync state (WorkManager)
 * - [LeakDiagnostics] for memory leak detection (LeakCanary in debug builds)
 * - [ChuckerDiagnostics] for HTTP traffic inspection (Chucker in debug builds)
 *
 * Uses CatalogSyncWorkScheduler (SSOT) for all sync triggers. Contract:
 * CATALOG_SYNC_WORKERS_CONTRACT_V2
 */
@HiltViewModel
class DebugViewModel
@Inject
constructor(
        private val catalogSyncWorkScheduler: CatalogSyncWorkScheduler,
        private val syncStateObserver: SyncStateObserver,
        private val tmdbEnrichmentScheduler: TmdbEnrichmentScheduler,
        private val logBufferProvider: LogBufferProvider,
        private val debugInfoProvider: DebugInfoProvider,
        private val leakDiagnostics: LeakDiagnostics,
        private val chuckerDiagnostics: ChuckerDiagnostics,
        @ApplicationContext private val appContext: Context,
) : ViewModel() {

    private val _state = MutableStateFlow(DebugState())
    val state: StateFlow<DebugState> = _state.asStateFlow()

    init {
        loadSystemInfo()
        loadCredentialStatus()
        loadLeakSummary()
        loadChuckerAvailability()
        observeSyncState()
        observeWorkManager()
        observeConnectionStatus()
        observeContentCounts()
        observeLogs()
        loadCacheSizes()
    }

    /**
     * Load API credential configuration status.
     *
     * **Important:** This is separate from connection status!
     * - Credentials = BuildConfig values (TG_API_ID, TG_API_HASH, TMDB_API_KEY)
     * - Connection = Runtime auth state (logged in or not)
     */
    private fun loadCredentialStatus() {
        val telegramStatus = debugInfoProvider.getTelegramCredentialStatus()
        val tmdbConfigured = debugInfoProvider.isTmdbApiKeyConfigured()

        _state.update {
            it.copy(
                    telegramCredentialsConfigured = telegramStatus.isConfigured,
                    telegramCredentialStatus = telegramStatus.statusMessage,
                    tmdbApiKeyConfigured = tmdbConfigured
            )
        }
    }

    /** Load LeakCanary summary and detailed status. */
    private fun loadLeakSummary() {
        val summary = leakDiagnostics.getSummary()
        val detailedStatus =
                if (leakDiagnostics.isAvailable) {
                    leakDiagnostics.getDetailedStatus()
                } else null

        _state.update {
            it.copy(
                    leakSummary = summary,
                    leakDetailedStatus = detailedStatus,
                    isLeakCanaryAvailable = leakDiagnostics.isAvailable
            )
        }
    }

    /** Load Chucker availability. */
    private fun loadChuckerAvailability() {
        _state.update { it.copy(isChuckerAvailable = chuckerDiagnostics.isAvailable) }
    }

    /** Observe sync state from WorkManager via SyncStateObserver. */
    private fun observeSyncState() {
        viewModelScope.launch {
            syncStateObserver.observeSyncState().collect { syncState ->
                _state.update { it.copy(syncState = syncState) }
            }
        }
    }

    /**
     * Observe WorkManager state directly for diagnostics.
     *
     * We observe 4 flows:
     * - Unique work: catalog_sync_global
     * - Unique work: tmdb_enrichment_global
     * - Tagged work: catalog_sync
     * - Tagged work: source_tmdb
     */
    private fun observeWorkManager() {
        val wm = WorkManager.getInstance(appContext)

        // Flow 1: Unique catalog sync work
        viewModelScope.launch {
            wm.getWorkInfosForUniqueWorkFlow(WorkManagerDebugConstants.WORK_NAME_CATALOG_SYNC)
                    .collect { infos ->
                        updateWorkManagerSnapshot { current ->
                            current.copy(catalogSyncUniqueWork = infos.map { it.toTaskInfo() })
                        }
                    }
        }

        // Flow 2: Unique TMDB enrichment work
        viewModelScope.launch {
            wm.getWorkInfosForUniqueWorkFlow(WorkManagerDebugConstants.WORK_NAME_TMDB_ENRICHMENT)
                    .collect { infos ->
                        updateWorkManagerSnapshot { current ->
                            current.copy(tmdbUniqueWork = infos.map { it.toTaskInfo() })
                        }
                    }
        }

        // Flow 3: Tagged catalog sync work
        viewModelScope.launch {
            wm.getWorkInfosByTagFlow(WorkManagerDebugConstants.TAG_CATALOG_SYNC).collect { infos ->
                updateWorkManagerSnapshot { current ->
                    current.copy(taggedCatalogSyncWork = infos.map { it.toTaskInfo() })
                }
            }
        }

        // Flow 4: Tagged TMDB work
        viewModelScope.launch {
            wm.getWorkInfosByTagFlow(WorkManagerDebugConstants.TAG_SOURCE_TMDB).collect { infos ->
                updateWorkManagerSnapshot { current ->
                    current.copy(taggedTmdbWork = infos.map { it.toTaskInfo() })
                }
            }
        }
    }

    private inline fun updateWorkManagerSnapshot(
            transform: (WorkManagerSnapshot) -> WorkManagerSnapshot
    ) {
        _state.update { state ->
            val current = state.workManagerSnapshot
            val updated = transform(current).copy(capturedAtEpochMs = System.currentTimeMillis())
            state.copy(workManagerSnapshot = updated)
        }
    }

    /** Load static system info. */
    private fun loadSystemInfo() {
        _state.update {
            it.copy(
                    deviceModel = android.os.Build.MODEL,
                    androidVersion = "Android ${android.os.Build.VERSION.RELEASE}",
            )
        }
    }

    /** Observe real connection status from auth repositories. */
    private fun observeConnectionStatus() {
        viewModelScope.launch {
            combine(
                            debugInfoProvider.observeTelegramConnection(),
                            debugInfoProvider.observeXtreamConnection()
                    ) { telegram, xtream -> Pair(telegram, xtream) }
                    .collect { (telegram, xtream) ->
                        _state.update {
                            it.copy(
                                    telegramConnected = telegram.isConnected,
                                    telegramUser = telegram.details,
                                    xtreamConnected = xtream.isConnected,
                                    xtreamServer = xtream.details
                            )
                        }
                    }
        }
    }

    /** Observe real content counts from data repositories. */
    private fun observeContentCounts() {
        viewModelScope.launch {
            debugInfoProvider.observeContentCounts().collect { counts ->
                _state.update {
                    it.copy(
                            telegramMediaCount = counts.telegramMediaCount,
                            xtreamVodCount = counts.xtreamVodCount,
                            xtreamSeriesCount = counts.xtreamSeriesCount,
                            xtreamLiveCount = counts.xtreamLiveCount
                    )
                }
            }
        }
    }

    /** Observe real logs from LogBufferProvider. */
    private fun observeLogs() {
        viewModelScope.launch {
            logBufferProvider.observeLogs(limit = 100).collect { bufferedLogs ->
                val logEntries = bufferedLogs.map { it.toLogEntry() }
                _state.update { it.copy(recentLogs = logEntries) }
            }
        }
    }

    /** Load real cache sizes. */
    private fun loadCacheSizes() {
        viewModelScope.launch {
            try {
                val telegramSize = debugInfoProvider.getTelegramCacheSize()
                val imageSize = debugInfoProvider.getImageCacheSize()
                val dbSize = debugInfoProvider.getDatabaseSize()

                _state.update {
                    it.copy(
                        telegramCacheSize = telegramSize?.formatAsSize() ?: "N/A",
                        imageCacheSize = imageSize?.formatAsSize() ?: "N/A",
                        dbSize = dbSize?.formatAsSize() ?: "N/A",
                    )
                }
            } catch (e: Exception) {
                UnifiedLog.w(TAG) { "loadCacheSizes: unexpected error: ${e.message}" }
                _state.update { it.copy(lastActionResult = "Failed to load cache sizes") }
            }
        }
    }

    fun refreshInfo() {
        loadCacheSizes()
    }

    fun clearTelegramCache() {
        viewModelScope.launch {
            _state.update { it.copy(isClearingCache = true) }
            try {
                val success = debugInfoProvider.clearTelegramCache()
                loadCacheSizes() // Refresh sizes
                _state.update {
                    it.copy(
                        lastActionResult =
                            if (success) "Telegram cache cleared" else "Failed to clear cache",
                    )
                }
            } catch (e: Exception) {
                UnifiedLog.w(TAG) { "clearTelegramCache: unexpected error: ${e.message}" }
                _state.update { it.copy(lastActionResult = "Failed to clear Telegram cache") }
            } finally {
                _state.update { it.copy(isClearingCache = false) }
            }
        }
    }

    fun clearImageCache() {
        viewModelScope.launch {
            _state.update { it.copy(isClearingCache = true) }
            try {
                val success = debugInfoProvider.clearImageCache()
                loadCacheSizes() // Refresh sizes
                _state.update {
                    it.copy(
                        lastActionResult =
                            if (success) "Image cache cleared" else "Failed to clear cache",
                    )
                }
            } catch (e: Exception) {
                UnifiedLog.w(TAG) { "clearImageCache: unexpected error: ${e.message}" }
                _state.update { it.copy(lastActionResult = "Failed to clear image cache") }
            } finally {
                _state.update { it.copy(isClearingCache = false) }
            }
        }
    }

    fun clearAllCaches() {
        viewModelScope.launch {
            _state.update { it.copy(isClearingCache = true) }
            try {
                val telegramOk = debugInfoProvider.clearTelegramCache()
                val imageOk = debugInfoProvider.clearImageCache()
                loadCacheSizes() // Refresh sizes
                _state.update {
                    it.copy(
                        lastActionResult =
                            if (telegramOk && imageOk) "All caches cleared"
                            else "Some caches failed to clear",
                    )
                }
            } catch (e: Exception) {
                UnifiedLog.w(TAG) { "clearAllCaches: unexpected error: ${e.message}" }
                _state.update { it.copy(lastActionResult = "Failed to clear caches") }
            } finally {
                _state.update { it.copy(isClearingCache = false) }
            }
        }
    }

    fun loadMoreLogs() {
        // No-op: Logs are already observed via Flow
        // Could increase limit in future
    }

    fun clearLogs() {
        logBufferProvider.clearLogs()
        _state.update { it.copy(lastActionResult = "Logs cleared") }
    }

    /**
     * Export all currently buffered logs to a user-selected destination Uri.
     *
     * Note: This exports the in-memory ring buffer (LogBufferTree). This is the set of "saved logs"
     * available in v2 right now.
     */
    fun exportAllLogs(destinationUri: Uri) {
        viewModelScope.launch {
            val total = logBufferProvider.getLogCount()
            val logs = logBufferProvider.getLogs(limit = total)
            val exportText = logs.joinToString(separator = "\n") { it.toExportLine() }

            try {
                val resolver = appContext.contentResolver
                resolver.openOutputStream(destinationUri, "w")?.use { out ->
                    out.write(exportText.toByteArray(Charsets.UTF_8))
                    out.flush()
                }
                        ?: run {
                            _state.update {
                                it.copy(
                                        lastActionResult =
                                                "Export failed: could not open output stream"
                                )
                            }
                            return@launch
                        }

                _state.update { it.copy(lastActionResult = "Exported ${logs.size} log(s)") }
            } catch (e: SecurityException) {
                UnifiedLog.w(TAG) { "exportAllLogs: security exception while writing logs" }
                _state.update { it.copy(lastActionResult = "Export failed: permission denied") }
            } catch (e: IOException) {
                UnifiedLog.w(TAG) { "exportAllLogs: IO error while writing logs" }
                _state.update { it.copy(lastActionResult = "Export failed: IO error") }
            } catch (e: Exception) {
                UnifiedLog.w(TAG) { "exportAllLogs: unexpected error while writing logs" }
                _state.update { it.copy(lastActionResult = "Export failed: unexpected error") }
            }
        }
    }

    fun setActionResult(message: String) {
        _state.update { it.copy(lastActionResult = message) }
    }

    fun dismissActionResult() {
        _state.update { it.copy(lastActionResult = null) }
    }

    // ========== Manual Sync Actions (SSOT via CatalogSyncWorkScheduler) ==========

    /**
     * Trigger manual catalog sync for all configured sources.
     *
     * Uses WorkManager via CatalogSyncWorkScheduler (SSOT). Contract:
     * CATALOG_SYNC_WORKERS_CONTRACT_V2
     */
    fun syncAll() {
        UnifiedLog.i(TAG) { "User triggered: Sync All (enqueueExpertSyncNow)" }
        catalogSyncWorkScheduler.enqueueExpertSyncNow()
        _state.update { it.copy(lastActionResult = "Catalog sync enqueued") }
    }

    /** Force rescan - cancels any running sync and starts fresh. */
    fun forceRescan() {
        UnifiedLog.i(TAG) { "User triggered: Force Rescan (enqueueForceRescan)" }
        catalogSyncWorkScheduler.enqueueForceRescan()
        _state.update { it.copy(lastActionResult = "Force rescan started") }
    }

    /** Cancel any running catalog sync. */
    fun cancelSync() {
        UnifiedLog.i(TAG) { "User triggered: Cancel Sync" }
        catalogSyncWorkScheduler.cancelSync()
        _state.update { it.copy(lastActionResult = "Sync cancelled") }
    }

    // ========== TMDB Enrichment Actions ==========

    /**
     * Trigger TMDB enrichment.
     *
     * Uses WorkManager via TmdbEnrichmentScheduler (SSOT). Contract:
     * CATALOG_SYNC_WORKERS_CONTRACT_V2 (W-22)
     */
    fun enqueueTmdbEnrichment() {
        UnifiedLog.i(TAG) { "User triggered: TMDB Enrichment" }
        tmdbEnrichmentScheduler.enqueueEnrichment()
        _state.update { it.copy(lastActionResult = "TMDB enrichment enqueued") }
    }

    /** Force TMDB refresh - re-enriches all items. */
    fun forceTmdbRefresh() {
        UnifiedLog.i(TAG) { "User triggered: Force TMDB Refresh" }
        tmdbEnrichmentScheduler.enqueueForceRefresh()
        _state.update { it.copy(lastActionResult = "TMDB force refresh started") }
    }

    // ========== WorkManager Diagnostics ==========

    /** Export WorkManager snapshot to SAF destination. */
    fun exportWorkManagerSnapshot(destinationUri: Uri) {
        viewModelScope.launch {
            try {
                val snapshot = _state.value.workManagerSnapshot
                val content = snapshot.toExportText()

                val resolver = appContext.contentResolver
                resolver.openOutputStream(destinationUri, "w")?.use { out ->
                    out.write(content.toByteArray(Charsets.UTF_8))
                    out.flush()
                }
                        ?: run {
                            _state.update {
                                it.copy(lastActionResult = "Export failed: could not open output")
                            }
                            return@launch
                        }

                _state.update { it.copy(lastActionResult = "WorkManager snapshot exported") }
                UnifiedLog.i(TAG) { "WorkManager snapshot exported successfully" }
            } catch (e: SecurityException) {
                UnifiedLog.w(TAG) { "exportWorkManagerSnapshot: security exception" }
                _state.update { it.copy(lastActionResult = "Export failed: permission denied") }
            } catch (e: IOException) {
                UnifiedLog.w(TAG) { "exportWorkManagerSnapshot: IO error" }
                _state.update { it.copy(lastActionResult = "Export failed: IO error") }
            } catch (e: Exception) {
                UnifiedLog.w(TAG) { "exportWorkManagerSnapshot: unexpected error: ${e.message}" }
                _state.update { it.copy(lastActionResult = "Export failed: ${e.message}") }
            }
        }
    }

    /** Get current WorkManager snapshot text for clipboard copy. */
    fun getWorkManagerSnapshotText(): String = _state.value.workManagerSnapshot.toExportText()

    // ========== Chucker Actions ==========

    /** Open Chucker HTTP Inspector UI. Returns true if successfully opened. */
    fun openChuckerUi(): Boolean {
        val success = chuckerDiagnostics.openChuckerUi(appContext)
        if (!success) {
            _state.update { it.copy(lastActionResult = "Could not open Chucker UI") }
        }
        return success
    }

    // ========== LeakCanary Actions ==========

    /** Open LeakCanary UI. Returns true if successfully opened. */
    fun openLeakCanaryUi(): Boolean {
        val success = leakDiagnostics.openLeakUi(appContext)
        if (!success) {
            _state.update { it.copy(lastActionResult = "Could not open LeakCanary UI") }
        }
        return success
    }

    /** Refresh leak summary and detailed status. */
    fun refreshLeakSummary() {
        loadLeakSummary()
    }

    /** Request garbage collection to reduce noise. */
    fun requestGarbageCollection() {
        leakDiagnostics.requestGarbageCollection()
        // Wait briefly then refresh
        viewModelScope.launch {
            kotlinx.coroutines.delay(500L)
            loadLeakSummary()
            _state.update { it.copy(lastActionResult = "GC requested - status refreshed") }
        }
    }

    /** Trigger a heap dump for analysis. */
    fun triggerHeapDump() {
        UnifiedLog.i(TAG) { "User triggered heap dump" }
        leakDiagnostics.triggerHeapDump()
        _state.update { it.copy(lastActionResult = "Heap dump triggered - check LeakCanary UI") }
    }

    /** Export leak report to SAF destination. */
    fun exportLeakReport(destinationUri: Uri) {
        viewModelScope.launch {
            val result = leakDiagnostics.exportLeakReport(appContext, destinationUri)
            result.fold(
                    onSuccess = {
                        _state.update { it.copy(lastActionResult = "Leak report exported") }
                        UnifiedLog.i(TAG) { "Leak report exported successfully" }
                    },
                    onFailure = { e ->
                        _state.update { it.copy(lastActionResult = "Export failed: ${e.message}") }
                        UnifiedLog.w(TAG) { "Leak report export failed: ${e.message}" }
                    }
            )
        }
    }

    // ========== Debug Bundle Export ==========

    /**
     * Export a debug bundle (ZIP) containing:
     * - logs.txt (all buffered logs)
     * - leaks.txt (leak report from LeakCanary)
     * - device_info.txt (device and app info)
     */
    fun exportDebugBundle(destinationUri: Uri) {
        viewModelScope.launch {
            try {
                val resolver = appContext.contentResolver
                resolver.openOutputStream(destinationUri, "w")?.use { out ->
                    ZipOutputStream(out).use { zip ->
                        // 1. logs.txt
                        val logsContent = buildLogsContent()
                        zip.putNextEntry(ZipEntry("logs.txt"))
                        zip.write(logsContent.toByteArray(Charsets.UTF_8))
                        zip.closeEntry()

                        // 2. leaks.txt (if LeakCanary available)
                        val leaksContent = buildLeaksContent()
                        zip.putNextEntry(ZipEntry("leaks.txt"))
                        zip.write(leaksContent.toByteArray(Charsets.UTF_8))
                        zip.closeEntry()

                        // 3. device_info.txt
                        val deviceInfoContent = buildDeviceInfoContent()
                        zip.putNextEntry(ZipEntry("device_info.txt"))
                        zip.write(deviceInfoContent.toByteArray(Charsets.UTF_8))
                        zip.closeEntry()

                        // 4. workmanager.txt (WorkManager diagnostics)
                        val workManagerContent = _state.value.workManagerSnapshot.toExportText()
                        zip.putNextEntry(ZipEntry("workmanager.txt"))
                        zip.write(workManagerContent.toByteArray(Charsets.UTF_8))
                        zip.closeEntry()
                    }
                }
                        ?: run {
                            _state.update {
                                it.copy(lastActionResult = "Export failed: could not open output")
                            }
                            return@launch
                        }

                _state.update { it.copy(lastActionResult = "Debug bundle exported") }
                UnifiedLog.i(TAG) { "Debug bundle exported successfully" }
            } catch (e: SecurityException) {
                UnifiedLog.w(TAG) { "exportDebugBundle: security exception" }
                _state.update { it.copy(lastActionResult = "Export failed: permission denied") }
            } catch (e: IOException) {
                UnifiedLog.w(TAG) { "exportDebugBundle: IO error" }
                _state.update { it.copy(lastActionResult = "Export failed: IO error") }
            } catch (e: Exception) {
                UnifiedLog.w(TAG) { "exportDebugBundle: unexpected error: ${e.message}" }
                _state.update { it.copy(lastActionResult = "Export failed: ${e.message}") }
            }
        }
    }

    private fun buildLogsContent(): String {
        val total = logBufferProvider.getLogCount()
        val logs = logBufferProvider.getLogs(limit = total)
        return logs.joinToString(separator = "\n") { it.toExportLine() }
    }

    private fun buildLeaksContent(): String {
        val summary = leakDiagnostics.getSummary()
        return buildString {
            appendLine("FishIT Player - Leak Summary")
            appendLine("=".repeat(40))
            appendLine()
            appendLine("LeakCanary Available: ${leakDiagnostics.isAvailable}")
            appendLine("Leaks Detected: ${summary.leakCount}")
            summary.lastLeakUptimeMs?.let { appendLine("Last Leak (uptime): ${it}ms") }
            summary.note?.let { appendLine("Note: $it") }
            appendLine()
            if (!leakDiagnostics.isAvailable) {
                appendLine("(Full leak details not available in release builds)")
            }
        }
    }

    private fun buildDeviceInfoContent(): String {
        val packageInfo =
                try {
                    appContext.packageManager.getPackageInfo(appContext.packageName, 0)
                } catch (e: Exception) {
                    null
                }
        val versionName = packageInfo?.versionName ?: "unknown"
        @Suppress("DEPRECATION") val versionCode = packageInfo?.versionCode ?: 0

        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        val now = dateFormat.format(Date())

        return buildString {
            appendLine("FishIT Player - Device Info")
            appendLine("=".repeat(40))
            appendLine()
            appendLine("Generated: $now")
            appendLine()
            appendLine("App Info")
            appendLine("-".repeat(30))
            appendLine("Version: $versionName ($versionCode)")
            appendLine("Package: ${appContext.packageName}")
            appendLine()
            appendLine("Device Info")
            appendLine("-".repeat(30))
            appendLine("Model: ${Build.MODEL}")
            appendLine("Manufacturer: ${Build.MANUFACTURER}")
            appendLine("Android: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
            appendLine("Device: ${Build.DEVICE}")
            appendLine("Product: ${Build.PRODUCT}")
            appendLine("Board: ${Build.BOARD}")
            appendLine("Hardware: ${Build.HARDWARE}")
        }
    }

    private companion object {
        private const val TAG = "DebugViewModel"
    }
}

/** Convert BufferedLogEntry to UI LogEntry. */
private fun BufferedLogEntry.toLogEntry(): LogEntry =
        LogEntry(
                timestamp = timestamp,
                level =
                        when (priorityString()) {
                            "INFO" -> LogLevel.INFO
                            "WARN" -> LogLevel.WARN
                            "ERROR" -> LogLevel.ERROR
                            else -> LogLevel.DEBUG
                        },
                tag = tag ?: "Unknown",
                message =
                        buildString {
                            append(message)
                            throwableInfo?.let { info ->
                                append(" ")
                                append(info.toString())
                            }
                        }
        )

private fun BufferedLogEntry.toExportLine(): String {
    val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
    val ts = formatter.format(Date(timestamp))
    val level = priorityString()
    val safeTag = tag ?: "Unknown"
    val base = "$ts $level $safeTag: $message"
    return throwableInfo?.let { "$base ${it}" } ?: base
}
