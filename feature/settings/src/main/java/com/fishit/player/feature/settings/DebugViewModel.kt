package com.fishit.player.feature.settings

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fishit.player.core.catalogsync.CatalogSyncWorkScheduler
import com.fishit.player.core.catalogsync.SyncStateObserver
import com.fishit.player.core.catalogsync.SyncUiState
import com.fishit.player.core.catalogsync.TmdbEnrichmentScheduler
import com.fishit.player.infra.logging.BufferedLogEntry
import com.fishit.player.infra.logging.LogBufferProvider
import com.fishit.player.infra.logging.UnifiedLog
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Debug screen state
 */
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
)

/**
 * Simple log entry for display
 */
data class LogEntry(
    val timestamp: Long,
    val level: LogLevel,
    val tag: String,
    val message: String
)

enum class LogLevel {
    DEBUG, INFO, WARN, ERROR
}

/**
 * Debug ViewModel - Manages debug/diagnostics screen
 * 
 * **REAL DATA SOURCES:**
 * - [LogBufferProvider] for in-memory log buffer
 * - [DebugInfoProvider] for connection status, cache sizes, content counts
 * - [SyncStateObserver] for catalog sync state (WorkManager)
 * 
 * Uses CatalogSyncWorkScheduler (SSOT) for all sync triggers.
 * Contract: CATALOG_SYNC_WORKERS_CONTRACT_V2
 */
@HiltViewModel
class DebugViewModel @Inject constructor(
    private val catalogSyncWorkScheduler: CatalogSyncWorkScheduler,
    private val syncStateObserver: SyncStateObserver,
    private val tmdbEnrichmentScheduler: TmdbEnrichmentScheduler,
    private val logBufferProvider: LogBufferProvider,
    private val debugInfoProvider: DebugInfoProvider,
) : ViewModel() {

    private val _state = MutableStateFlow(DebugState())
    val state: StateFlow<DebugState> = _state.asStateFlow()

    init {
        loadSystemInfo()
        loadCredentialStatus()
        observeSyncState()
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
    
    /**
     * Observe sync state from WorkManager via SyncStateObserver.
     */
    private fun observeSyncState() {
        viewModelScope.launch {
            syncStateObserver.observeSyncState().collect { syncState ->
                _state.update { it.copy(syncState = syncState) }
            }
        }
    }

    /**
     * Load static system info.
     */
    private fun loadSystemInfo() {
        _state.update {
            it.copy(
                deviceModel = android.os.Build.MODEL,
                androidVersion = "Android ${android.os.Build.VERSION.RELEASE}",
            )
        }
    }

    /**
     * Observe real connection status from auth repositories.
     */
    private fun observeConnectionStatus() {
        viewModelScope.launch {
            combine(
                debugInfoProvider.observeTelegramConnection(),
                debugInfoProvider.observeXtreamConnection()
            ) { telegram, xtream ->
                Pair(telegram, xtream)
            }.collect { (telegram, xtream) ->
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

    /**
     * Observe real content counts from data repositories.
     */
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

    /**
     * Observe real logs from LogBufferProvider.
     */
    private fun observeLogs() {
        viewModelScope.launch {
            logBufferProvider.observeLogs(limit = 100).collect { bufferedLogs ->
                val logEntries = bufferedLogs.map { it.toLogEntry() }
                _state.update { it.copy(recentLogs = logEntries) }
            }
        }
    }

    /**
     * Load real cache sizes.
     */
    private fun loadCacheSizes() {
        viewModelScope.launch {
            val telegramSize = debugInfoProvider.getTelegramCacheSize()
            val imageSize = debugInfoProvider.getImageCacheSize()
            val dbSize = debugInfoProvider.getDatabaseSize()

            _state.update {
                it.copy(
                    telegramCacheSize = telegramSize?.formatAsSize() ?: "N/A",
                    imageCacheSize = imageSize?.formatAsSize() ?: "N/A",
                    dbSize = dbSize?.formatAsSize() ?: "N/A"
                )
            }
        }
    }

    fun refreshInfo() {
        loadCacheSizes()
    }

    fun clearTelegramCache() {
        viewModelScope.launch {
            _state.update { it.copy(isClearingCache = true) }
            val success = debugInfoProvider.clearTelegramCache()
            loadCacheSizes() // Refresh sizes
            _state.update {
                it.copy(
                    isClearingCache = false,
                    lastActionResult = if (success) "Telegram cache cleared" else "Failed to clear cache"
                )
            }
        }
    }

    fun clearImageCache() {
        viewModelScope.launch {
            _state.update { it.copy(isClearingCache = true) }
            val success = debugInfoProvider.clearImageCache()
            loadCacheSizes() // Refresh sizes
            _state.update {
                it.copy(
                    isClearingCache = false,
                    lastActionResult = if (success) "Image cache cleared" else "Failed to clear cache"
                )
            }
        }
    }

    fun clearAllCaches() {
        viewModelScope.launch {
            _state.update { it.copy(isClearingCache = true) }
            val telegramOk = debugInfoProvider.clearTelegramCache()
            val imageOk = debugInfoProvider.clearImageCache()
            loadCacheSizes() // Refresh sizes
            _state.update {
                it.copy(
                    isClearingCache = false,
                    lastActionResult = if (telegramOk && imageOk) "All caches cleared" else "Some caches failed to clear"
                )
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

    fun dismissActionResult() {
        _state.update { it.copy(lastActionResult = null) }
    }

    // ========== Manual Sync Actions (SSOT via CatalogSyncWorkScheduler) ==========

    /**
     * Trigger manual catalog sync for all configured sources.
     * 
     * Uses WorkManager via CatalogSyncWorkScheduler (SSOT).
     * Contract: CATALOG_SYNC_WORKERS_CONTRACT_V2
     */
    fun syncAll() {
        UnifiedLog.i(TAG) { "User triggered: Sync All (enqueueExpertSyncNow)" }
        catalogSyncWorkScheduler.enqueueExpertSyncNow()
        _state.update { 
            it.copy(lastActionResult = "Catalog sync enqueued")
        }
    }

    /**
     * Force rescan - cancels any running sync and starts fresh.
     */
    fun forceRescan() {
        UnifiedLog.i(TAG) { "User triggered: Force Rescan (enqueueForceRescan)" }
        catalogSyncWorkScheduler.enqueueForceRescan()
        _state.update { 
            it.copy(lastActionResult = "Force rescan started")
        }
    }

    /**
     * Cancel any running catalog sync.
     */
    fun cancelSync() {
        UnifiedLog.i(TAG) { "User triggered: Cancel Sync" }
        catalogSyncWorkScheduler.cancelSync()
        _state.update { 
            it.copy(lastActionResult = "Sync cancelled")
        }
    }

    // ========== TMDB Enrichment Actions ==========

    /**
     * Trigger TMDB enrichment.
     * 
     * Uses WorkManager via TmdbEnrichmentScheduler (SSOT).
     * Contract: CATALOG_SYNC_WORKERS_CONTRACT_V2 (W-22)
     */
    fun enqueueTmdbEnrichment() {
        UnifiedLog.i(TAG) { "User triggered: TMDB Enrichment" }
        tmdbEnrichmentScheduler.enqueueEnrichment()
        _state.update { 
            it.copy(lastActionResult = "TMDB enrichment enqueued")
        }
    }

    /**
     * Force TMDB refresh - re-enriches all items.
     */
    fun forceTmdbRefresh() {
        UnifiedLog.i(TAG) { "User triggered: Force TMDB Refresh" }
        tmdbEnrichmentScheduler.enqueueForceRefresh()
        _state.update { 
            it.copy(lastActionResult = "TMDB force refresh started")
        }
    }
    
    private companion object {
        private const val TAG = "DebugViewModel"
    }
}

/**
 * Convert BufferedLogEntry to UI LogEntry.
 */
private fun BufferedLogEntry.toLogEntry(): LogEntry = LogEntry(
    timestamp = timestamp,
    level = when (priority) {
        Log.DEBUG -> LogLevel.DEBUG
        Log.INFO -> LogLevel.INFO
        Log.WARN -> LogLevel.WARN
        Log.ERROR -> LogLevel.ERROR
        else -> LogLevel.DEBUG
    },
    tag = tag ?: "Unknown",
    message = message
)
