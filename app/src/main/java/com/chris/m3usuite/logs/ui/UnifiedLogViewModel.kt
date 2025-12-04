package com.chris.m3usuite.logs.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.chris.m3usuite.core.logging.UnifiedLog
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File

/**
 * ViewModel for the UnifiedLogViewerScreen.
 *
 * Manages log filtering, export, and UI state.
 */
class UnifiedLogViewModel(application: Application) : AndroidViewModel(application) {

    private val log = UnifiedLog

    /**
     * UI State for the log viewer.
     */
    data class State(
        val filteredEntries: List<UnifiedLog.Entry> = emptyList(),
        val filter: UnifiedLog.FilterState = UnifiedLog.FilterState(),
        val statistics: UnifiedLog.Statistics = UnifiedLog.Statistics(),
        val hasActiveFilters: Boolean = false,
        val hasFileBuffer: Boolean = false,
        val isAutoScrollEnabled: Boolean = true,
        val scrollToFirstErrorTrigger: Int = 0,
    )

    private val _autoScrollEnabled = MutableStateFlow(true)
    private val _scrollToErrorTrigger = MutableStateFlow(0)

    /**
     * Combined state flow for the UI.
     */
    val state: StateFlow<State> = combine(
        log.entries,
        log.filterState,
        _autoScrollEnabled,
        _scrollToErrorTrigger,
    ) { entries, filter, autoScroll, errorTrigger ->
        val filtered = entries.filter { entry ->
            entry.level in filter.enabledLevels &&
                entry.category in filter.enabledCategories &&
                (filter.searchQuery.isEmpty() ||
                    entry.message.contains(filter.searchQuery, ignoreCase = true) ||
                    entry.source.contains(filter.searchQuery, ignoreCase = true) ||
                    entry.details?.any { (k, v) ->
                        k.contains(filter.searchQuery, ignoreCase = true) ||
                            v.contains(filter.searchQuery, ignoreCase = true)
                    } == true)
        }

        val stats = UnifiedLog.Statistics(
            total = entries.size,
            verbose = entries.count { it.level == UnifiedLog.Level.VERBOSE },
            error = entries.count { it.level == UnifiedLog.Level.ERROR },
            warn = entries.count { it.level == UnifiedLog.Level.WARN },
            info = entries.count { it.level == UnifiedLog.Level.INFO },
            debug = entries.count { it.level == UnifiedLog.Level.DEBUG },
            filtered = filtered.size,
        )

        val hasActiveFilters = filter.enabledLevels.size < UnifiedLog.Level.entries.size ||
            filter.enabledCategories.size < UnifiedLog.SourceCategory.entries.size ||
            filter.searchQuery.isNotEmpty()

        State(
            filteredEntries = filtered,
            filter = filter,
            statistics = stats,
            hasActiveFilters = hasActiveFilters,
            hasFileBuffer = log.isFileBufferEnabled(),
            isAutoScrollEnabled = autoScroll,
            scrollToFirstErrorTrigger = errorTrigger,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = State(),
    )

    /**
     * Toggle a log level filter.
     */
    fun toggleLevel(level: UnifiedLog.Level) {
        viewModelScope.launch {
            log.toggleLevel(level)
        }
    }

    /**
     * Toggle a source category filter.
     */
    fun toggleCategory(category: UnifiedLog.SourceCategory) {
        viewModelScope.launch {
            log.toggleCategory(category)
        }
    }

    /**
     * Set search query.
     */
    fun setSearchQuery(query: String) {
        viewModelScope.launch {
            log.setSearchQuery(query)
        }
    }

    /**
     * Reset all filters to default (show all).
     */
    fun resetFilters() {
        viewModelScope.launch {
            log.resetFilters()
        }
    }

    /**
     * Filter to show only a specific level and scroll to first match.
     */
    fun filterToLevel(level: UnifiedLog.Level) {
        viewModelScope.launch {
            // Enable only this level
            UnifiedLog.Level.entries.forEach { l ->
                if (l != level && l in state.value.filter.enabledLevels) {
                    log.toggleLevel(l)
                } else if (l == level && l !in state.value.filter.enabledLevels) {
                    log.toggleLevel(l)
                }
            }
            // Trigger scroll to first error (for error level)
            if (level == UnifiedLog.Level.ERROR) {
                _scrollToErrorTrigger.value++
            }
        }
    }

    /**
     * Toggle auto-scroll to latest entry.
     */
    fun toggleAutoScroll() {
        _autoScrollEnabled.value = !_autoScrollEnabled.value
    }

    /**
     * Toggle file buffer for full session export.
     */
    fun toggleFileBuffer() {
        viewModelScope.launch {
            if (log.isFileBufferEnabled()) {
                log.disableFileBuffer()
            } else {
                log.enableFileBuffer()
            }
        }
    }

    /**
     * Clear all logs.
     */
    fun clearLogs() {
        log.clear()
    }

    /**
     * Export currently filtered logs as text.
     */
    fun exportFiltered(): String {
        return buildString {
            appendLine("=== FishIT App Logs ===")
            appendLine("Exported: ${java.time.Instant.now()}")
            appendLine("Entries: ${state.value.filteredEntries.size}")
            appendLine()

            state.value.filteredEntries.forEach { entry ->
                appendLine("[${entry.formattedTime()}] [${entry.level}] [${entry.source}] ${entry.message}")
                entry.formattedDetails()?.let { details ->
                    appendLine("  $details")
                }
            }
        }
    }

    /**
     * Export full session (from file buffer if available).
     */
    fun exportFullSession(): String? {
        return log.exportFullSession()
    }

    /**
     * Save logs to a file and return the file.
     */
    fun saveToFile(): File? {
        return log.saveExportToFile()
    }
}
