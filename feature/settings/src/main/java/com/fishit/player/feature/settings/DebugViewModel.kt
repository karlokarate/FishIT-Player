package com.fishit.player.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    val lastActionResult: String? = null
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
 */
@HiltViewModel
class DebugViewModel @Inject constructor(
    // TODO: Inject actual services when available
    // private val telegramClient: TelegramTransportClient,
    // private val cacheManager: CacheManager,
    // private val logRepository: LogRepository
) : ViewModel() {

    private val _state = MutableStateFlow(DebugState())
    val state: StateFlow<DebugState> = _state.asStateFlow()

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
