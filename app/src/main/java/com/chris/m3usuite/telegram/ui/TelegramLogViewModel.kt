package com.chris.m3usuite.telegram.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chris.m3usuite.telegram.logging.LogLevel
import com.chris.m3usuite.telegram.logging.TelegramLogRepository
import com.chris.m3usuite.telegram.logging.TgLogEntry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * ViewModel for Telegram Log Screen.
 * Displays logged Telegram events with filtering capabilities.
 */
class TelegramLogViewModel(
    private val logRepository: TelegramLogRepository = TelegramLogRepository.getInstance()
) : ViewModel() {
    
    /**
     * UI State for the log screen.
     */
    data class LogScreenState(
        val entries: List<TgLogEntry> = emptyList(),
        val filterLevel: LogLevel? = null,
        val filterSource: String? = null,
        val isLoading: Boolean = false
    )
    
    private val _state = MutableStateFlow(LogScreenState())
    val state: StateFlow<LogScreenState> = _state.asStateFlow()
    
    private val _filterLevel = MutableStateFlow<LogLevel?>(null)
    private val _filterSource = MutableStateFlow<String?>(null)
    
    init {
        // Observe log entries and apply filters
        viewModelScope.launch {
            combine(
                logRepository.entriesFlow,
                _filterLevel,
                _filterSource
            ) { entries, level, source ->
                val filtered = if (level == null && source == null) {
                    entries
                } else {
                    logRepository.getFiltered(minLevel = level, source = source)
                }
                
                LogScreenState(
                    entries = filtered.sortedByDescending { it.timestamp },
                    filterLevel = level,
                    filterSource = source,
                    isLoading = false
                )
            }.collect { newState ->
                _state.value = newState
            }
        }
    }
    
    /**
     * Set minimum log level filter.
     */
    fun setLevelFilter(level: LogLevel?) {
        _filterLevel.value = level
    }
    
    /**
     * Set source filter (partial match, case-insensitive).
     */
    fun setSourceFilter(source: String?) {
        _filterSource.value = source?.takeIf { it.isNotBlank() }
    }
    
    /**
     * Clear all filters.
     */
    fun clearFilters() {
        _filterLevel.value = null
        _filterSource.value = null
    }
    
    /**
     * Clear all log entries.
     */
    fun clearLogs() {
        logRepository.clear()
    }
    
    /**
     * Export logs as text (for sharing).
     */
    fun exportLogsAsText(): String {
        return state.value.entries.joinToString("\n\n") { entry ->
            buildString {
                append("[${entry.formattedTimestamp()}] ")
                append("[${entry.level}] ")
                append("[${entry.source}] ")
                append(entry.message)
                if (entry.details != null) {
                    append("\n  Details: ${entry.details}")
                }
                if (entry.throwable != null) {
                    append("\n  Error: ${entry.throwable.message}")
                }
            }
        }
    }
}
