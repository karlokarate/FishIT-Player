package com.chris.m3usuite.telegram.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chris.m3usuite.telegram.logging.TelegramLogRepository
import com.chris.m3usuite.telegram.logging.TgLogEntry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/**
 * UI state for the Telegram Log Screen.
 */
data class TelegramLogState(
    val entries: List<TgLogEntry> = emptyList(),
    val selectedLevel: TgLogEntry.LogLevel? = null,
    val selectedSource: String? = null,
    val availableSources: List<String> = emptyList(),
    val isAutoScrollEnabled: Boolean = true,
) {
    /**
     * Get filtered entries based on current filters.
     */
    val filteredEntries: List<TgLogEntry>
        get() =
            entries.filter { entry ->
                (selectedLevel == null || entry.level == selectedLevel) &&
                    (selectedSource == null || entry.source == selectedSource)
            }

    /**
     * Count entries by level.
     */
    fun countByLevel(level: TgLogEntry.LogLevel): Int = entries.count { it.level == level }
}

/**
 * ViewModel for the Telegram Log Screen.
 *
 * Features:
 * - Real-time log updates via StateFlow
 * - Filtering by level and source
 * - Statistics (count by level)
 * - Export functionality
 * - Clear logs
 *
 * Usage in Compose:
 * ```
 * val viewModel: TelegramLogViewModel = viewModel()
 * val state by viewModel.state.collectAsState()
 * ```
 */
class TelegramLogViewModel : ViewModel() {
    private val _state = MutableStateFlow(TelegramLogState())
    val state: StateFlow<TelegramLogState> = _state.asStateFlow()

    init {
        // Subscribe to log repository updates
        TelegramLogRepository.entries
            .onEach { entries ->
                _state.value =
                    _state.value.copy(
                        entries = entries,
                        availableSources = TelegramLogRepository.getAllSources(),
                    )
            }
            .launchIn(viewModelScope)
    }

    /**
     * Filter logs by level.
     */
    fun filterByLevel(level: TgLogEntry.LogLevel?) {
        _state.value = _state.value.copy(selectedLevel = level)
    }

    /**
     * Filter logs by source.
     */
    fun filterBySource(source: String?) {
        _state.value = _state.value.copy(selectedSource = source)
    }

    /**
     * Clear all filters.
     */
    fun clearFilters() {
        _state.value =
            _state.value.copy(
                selectedLevel = null,
                selectedSource = null,
            )
    }

    /**
     * Toggle auto-scroll (automatically scroll to latest log entry).
     */
    fun toggleAutoScroll() {
        _state.value = _state.value.copy(isAutoScrollEnabled = !_state.value.isAutoScrollEnabled)
    }

    /**
     * Clear all log entries.
     */
    fun clearLogs() {
        viewModelScope.launch {
            TelegramLogRepository.clear()
        }
    }

    /**
     * Export logs as text.
     */
    fun exportLogs(): String {
        val currentState = _state.value
        return TelegramLogRepository.exportAsText(
            level = currentState.selectedLevel,
            source = currentState.selectedSource,
        )
    }

    /**
     * Get statistics for display.
     */
    fun getStatistics(): LogStatistics {
        val currentState = _state.value
        return LogStatistics(
            total = currentState.entries.size,
            verbose = currentState.countByLevel(TgLogEntry.LogLevel.VERBOSE),
            debug = currentState.countByLevel(TgLogEntry.LogLevel.DEBUG),
            info = currentState.countByLevel(TgLogEntry.LogLevel.INFO),
            warn = currentState.countByLevel(TgLogEntry.LogLevel.WARN),
            error = currentState.countByLevel(TgLogEntry.LogLevel.ERROR),
            filtered = currentState.filteredEntries.size,
        )
    }

    /**
     * Log statistics for display in UI.
     */
    data class LogStatistics(
        val total: Int,
        val verbose: Int,
        val debug: Int,
        val info: Int,
        val warn: Int,
        val error: Int,
        val filtered: Int,
    )
}
