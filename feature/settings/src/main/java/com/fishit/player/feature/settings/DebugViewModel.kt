package com.fishit.player.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fishit.player.core.catalogsync.CatalogSyncService
import com.fishit.player.core.catalogsync.SyncConfig
import com.fishit.player.core.catalogsync.SyncStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onCompletion
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
    
    // === Sync Status (v2.1) ===
    val isSyncingTelegram: Boolean = false,
    val isSyncingXtream: Boolean = false,
    val telegramSyncProgress: SyncProgress? = null,
    val xtreamSyncProgress: SyncProgress? = null,
)

/**
 * Progress information for sync operations.
 */
data class SyncProgress(
    val itemsDiscovered: Long,
    val itemsPersisted: Long,
    val currentPhase: String? = null,
    val durationMs: Long = 0L,
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
 * Provides manual catalog sync for Telegram and Xtream sources,
 * with progress tracking and error handling.
 */
@HiltViewModel
class DebugViewModel @Inject constructor(
    private val catalogSyncService: CatalogSyncService,
    // TODO: Inject actual services when available
    // private val telegramClient: TelegramTransportClient,
    // private val cacheManager: CacheManager,
    // private val logRepository: LogRepository
) : ViewModel() {

    private val _state = MutableStateFlow(DebugState())
    val state: StateFlow<DebugState> = _state.asStateFlow()
    
    private var telegramSyncJob: Job? = null
    private var xtreamSyncJob: Job? = null

    init {
        loadDebugInfo()
    }

    private fun loadDebugInfo() {
        viewModelScope.launch {
            // TODO: Load from actual services
            _state.update {
                it.copy(
                    deviceModel = android.os.Build.MODEL,
                    androidVersion = "Android ${android.os.Build.VERSION.RELEASE}",
                    // Demo data for UI validation
                    telegramConnected = true,
                    telegramUser = "Demo User",
                    xtreamConnected = false,
                    telegramCacheSize = "128 MB",
                    imageCacheSize = "45 MB",
                    dbSize = "12 MB",
                    telegramMediaCount = 256,
                    xtreamVodCount = 0,
                    xtreamSeriesCount = 0,
                    xtreamLiveCount = 0,
                    recentLogs = generateDemoLogs()
                )
            }
        }
    }

    fun refreshInfo() {
        loadDebugInfo()
    }

    fun clearTelegramCache() {
        viewModelScope.launch {
            _state.update { it.copy(isClearingCache = true) }
            kotlinx.coroutines.delay(1000) // Simulate work
            _state.update {
                it.copy(
                    isClearingCache = false,
                    telegramCacheSize = "0 MB",
                    lastActionResult = "Telegram cache cleared"
                )
            }
        }
    }

    fun clearImageCache() {
        viewModelScope.launch {
            _state.update { it.copy(isClearingCache = true) }
            kotlinx.coroutines.delay(500)
            _state.update {
                it.copy(
                    isClearingCache = false,
                    imageCacheSize = "0 MB",
                    lastActionResult = "Image cache cleared"
                )
            }
        }
    }

    fun clearAllCaches() {
        viewModelScope.launch {
            _state.update { it.copy(isClearingCache = true) }
            kotlinx.coroutines.delay(1500)
            _state.update {
                it.copy(
                    isClearingCache = false,
                    telegramCacheSize = "0 MB",
                    imageCacheSize = "0 MB",
                    lastActionResult = "All caches cleared"
                )
            }
        }
    }

    fun loadMoreLogs() {
        viewModelScope.launch {
            _state.update { it.copy(isLoadingLogs = true) }
            kotlinx.coroutines.delay(500)
            val moreLogs = generateDemoLogs()
            _state.update {
                it.copy(
                    isLoadingLogs = false,
                    recentLogs = it.recentLogs + moreLogs
                )
            }
        }
    }

    fun dismissActionResult() {
        _state.update { it.copy(lastActionResult = null) }
    }

    // ========== Manual Sync Actions ==========

    /**
     * Trigger manual Telegram catalog sync.
     * 
     * Scans all connected Telegram chats and persists discovered media items.
     * Progress is updated live via [DebugState.telegramSyncProgress].
     */
    fun syncTelegram() {
        if (_state.value.isSyncingTelegram) {
            // Cancel existing sync
            telegramSyncJob?.cancel()
            _state.update { 
                it.copy(
                    isSyncingTelegram = false, 
                    telegramSyncProgress = null,
                    lastActionResult = "Telegram sync cancelled"
                ) 
            }
            return
        }
        
        telegramSyncJob = viewModelScope.launch {
            _state.update { 
                it.copy(
                    isSyncingTelegram = true, 
                    telegramSyncProgress = SyncProgress(0, 0, "Starting...")
                ) 
            }
            
            catalogSyncService.syncTelegram(
                chatIds = null, // All chats
                syncConfig = SyncConfig.DEFAULT,
            )
                .catch { error ->
                    _state.update { 
                        it.copy(
                            isSyncingTelegram = false,
                            telegramSyncProgress = null,
                            lastActionResult = "Telegram sync error: ${error.message}"
                        ) 
                    }
                }
                .onCompletion {
                    if (_state.value.isSyncingTelegram) {
                        _state.update { 
                            it.copy(isSyncingTelegram = false) 
                        }
                    }
                }
                .collect { status ->
                    handleTelegramSyncStatus(status)
                }
        }
    }
    
    private fun handleTelegramSyncStatus(status: SyncStatus) {
        when (status) {
            is SyncStatus.Started -> {
                _state.update { 
                    it.copy(telegramSyncProgress = SyncProgress(0, 0, "Started...")) 
                }
            }
            is SyncStatus.InProgress -> {
                _state.update { 
                    it.copy(
                        telegramSyncProgress = SyncProgress(
                            itemsDiscovered = status.itemsDiscovered,
                            itemsPersisted = status.itemsPersisted,
                            currentPhase = status.currentPhase,
                        )
                    ) 
                }
            }
            is SyncStatus.Completed -> {
                _state.update { 
                    it.copy(
                        isSyncingTelegram = false,
                        telegramSyncProgress = SyncProgress(
                            itemsDiscovered = status.totalItems,
                            itemsPersisted = status.totalItems,
                            durationMs = status.durationMs,
                        ),
                        telegramMediaCount = status.totalItems.toInt(),
                        lastActionResult = "Telegram sync complete: ${status.totalItems} items in ${status.durationMs / 1000}s"
                    ) 
                }
            }
            is SyncStatus.Cancelled -> {
                _state.update { 
                    it.copy(
                        isSyncingTelegram = false,
                        telegramSyncProgress = null,
                        lastActionResult = "Telegram sync cancelled (${status.itemsPersisted} items saved)"
                    ) 
                }
            }
            is SyncStatus.Error -> {
                _state.update { 
                    it.copy(
                        isSyncingTelegram = false,
                        telegramSyncProgress = null,
                        lastActionResult = "Telegram sync error: ${status.message}"
                    ) 
                }
            }
        }
    }

    /**
     * Trigger manual Xtream catalog sync.
     * 
     * Syncs VOD, Series, Episodes, and Live channels from all configured Xtream sources.
     * Progress is updated live via [DebugState.xtreamSyncProgress].
     */
    fun syncXtream() {
        if (_state.value.isSyncingXtream) {
            // Cancel existing sync
            xtreamSyncJob?.cancel()
            _state.update { 
                it.copy(
                    isSyncingXtream = false, 
                    xtreamSyncProgress = null,
                    lastActionResult = "Xtream sync cancelled"
                ) 
            }
            return
        }
        
        xtreamSyncJob = viewModelScope.launch {
            _state.update { 
                it.copy(
                    isSyncingXtream = true, 
                    xtreamSyncProgress = SyncProgress(0, 0, "Starting...")
                ) 
            }
            
            catalogSyncService.syncXtream(
                includeVod = true,
                includeSeries = true,
                includeEpisodes = true,
                includeLive = true,
                syncConfig = SyncConfig.DEFAULT,
            )
                .catch { error ->
                    _state.update { 
                        it.copy(
                            isSyncingXtream = false,
                            xtreamSyncProgress = null,
                            lastActionResult = "Xtream sync error: ${error.message}"
                        ) 
                    }
                }
                .onCompletion {
                    if (_state.value.isSyncingXtream) {
                        _state.update { 
                            it.copy(isSyncingXtream = false) 
                        }
                    }
                }
                .collect { status ->
                    handleXtreamSyncStatus(status)
                }
        }
    }
    
    private fun handleXtreamSyncStatus(status: SyncStatus) {
        when (status) {
            is SyncStatus.Started -> {
                _state.update { 
                    it.copy(xtreamSyncProgress = SyncProgress(0, 0, "Started...")) 
                }
            }
            is SyncStatus.InProgress -> {
                _state.update { 
                    it.copy(
                        xtreamSyncProgress = SyncProgress(
                            itemsDiscovered = status.itemsDiscovered,
                            itemsPersisted = status.itemsPersisted,
                            currentPhase = status.currentPhase,
                        )
                    ) 
                }
            }
            is SyncStatus.Completed -> {
                _state.update { 
                    it.copy(
                        isSyncingXtream = false,
                        xtreamSyncProgress = SyncProgress(
                            itemsDiscovered = status.totalItems,
                            itemsPersisted = status.totalItems,
                            durationMs = status.durationMs,
                        ),
                        lastActionResult = "Xtream sync complete: ${status.totalItems} items in ${status.durationMs / 1000}s"
                    ) 
                }
            }
            is SyncStatus.Cancelled -> {
                _state.update { 
                    it.copy(
                        isSyncingXtream = false,
                        xtreamSyncProgress = null,
                        lastActionResult = "Xtream sync cancelled (${status.itemsPersisted} items saved)"
                    ) 
                }
            }
            is SyncStatus.Error -> {
                _state.update { 
                    it.copy(
                        isSyncingXtream = false,
                        xtreamSyncProgress = null,
                        lastActionResult = "Xtream sync error: ${status.message}"
                    ) 
                }
            }
        }
    }

    private fun generateDemoLogs(): List<LogEntry> {
        val now = System.currentTimeMillis()
        return listOf(
            LogEntry(now - 1000, LogLevel.INFO, "HomeViewModel", "Content loaded successfully"),
            LogEntry(now - 2000, LogLevel.DEBUG, "TelegramPipeline", "Fetched 25 new messages"),
            LogEntry(now - 5000, LogLevel.WARN, "ImageCache", "Memory pressure, evicting 10 items"),
            LogEntry(now - 10000, LogLevel.INFO, "Player", "Playback started: Movie Title"),
            LogEntry(now - 15000, LogLevel.ERROR, "NetworkClient", "Connection timeout"),
            LogEntry(now - 30000, LogLevel.DEBUG, "ObjectBox", "DB query completed in 12ms"),
            LogEntry(now - 60000, LogLevel.INFO, "CacheManager", "Cache warm-up complete")
        )
    }
}
