package com.chris.m3usuite.logs

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.chris.m3usuite.core.logging.CrashHandler
import com.chris.m3usuite.core.logging.UnifiedLogRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/**
 * UI state for the Unified Log Screen.
 */
data class UnifiedLogState(
    val entries: List<UnifiedLogRepository.UnifiedLogEntry> = emptyList(),
    val selectedLevel: UnifiedLogRepository.Level? = null,
    val selectedCategory: String? = null,
    val selectedSource: String? = null,
    val availableCategories: List<String> = emptyList(),
    val availableSources: List<String> = emptyList(),
    val isAutoScrollEnabled: Boolean = true,
    val lastCrash: CrashHandler.CrashReport? = null,
) {
    /**
     * Get filtered entries based on current filters.
     */
    val filteredEntries: List<UnifiedLogRepository.UnifiedLogEntry>
        get() =
            entries.filter { entry ->
                (selectedLevel == null || entry.level == selectedLevel) &&
                    (selectedCategory == null || entry.category == selectedCategory) &&
                    (selectedSource == null || entry.source == selectedSource)
            }

    /**
     * Count entries by level.
     */
    fun countByLevel(level: UnifiedLogRepository.Level): Int = entries.count { it.level == level }
}

/**
 * ViewModel for the Unified Log Screen.
 *
 * Features:
 * - Real-time log updates via StateFlow from UnifiedLogRepository
 * - Filtering by level, category, and source
 * - Statistics (count by level)
 * - Export functionality
 * - Clear logs
 * - Last crash display
 *
 * Usage in Compose:
 * ```
 * val viewModel: UnifiedLogViewModel = viewModel(factory = UnifiedLogViewModel.factory(app))
 * val state by viewModel.state.collectAsState()
 * ```
 */
class UnifiedLogViewModel(
    application: Application,
) : AndroidViewModel(application) {
    private val _state = MutableStateFlow(UnifiedLogState())
    val state: StateFlow<UnifiedLogState> = _state.asStateFlow()

    init {
        // Check for last crash on startup
        val lastCrash = CrashHandler.readLastCrash(application)
        if (lastCrash != null) {
            _state.value = _state.value.copy(lastCrash = lastCrash)
        }

        // Subscribe to log repository updates
        UnifiedLogRepository.entries
            .onEach { entries ->
                _state.value =
                    _state.value.copy(
                        entries = entries,
                        availableCategories = UnifiedLogRepository.getAllCategories(),
                        availableSources = UnifiedLogRepository.getAllSources(),
                    )
            }.launchIn(viewModelScope)
    }

    /**
     * Filter logs by level.
     */
    fun filterByLevel(level: UnifiedLogRepository.Level?) {
        _state.value = _state.value.copy(selectedLevel = level)
    }

    /**
     * Filter logs by category.
     */
    fun filterByCategory(category: String?) {
        _state.value = _state.value.copy(selectedCategory = category)
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
                selectedCategory = null,
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
        UnifiedLogRepository.clear()
    }

    /**
     * Export logs as text.
     */
    fun exportLogs(): String {
        val currentState = _state.value
        return UnifiedLogRepository.exportAsText(
            level = currentState.selectedLevel,
            category = currentState.selectedCategory,
        )
    }

    /**
     * Dismiss the last crash report.
     */
    fun dismissCrash() {
        CrashHandler.clearLastCrash(getApplication())
        _state.value = _state.value.copy(lastCrash = null)
    }

    /**
     * Export last crash report as text.
     */
    fun exportCrashReport(): String? {
        val crash = _state.value.lastCrash ?: return null
        return CrashHandler.exportCrashAsText(crash)
    }

    /**
     * Get statistics for display.
     */
    fun getStatistics(): LogStatistics {
        val currentState = _state.value
        return LogStatistics(
            total = currentState.entries.size,
            verbose = currentState.countByLevel(UnifiedLogRepository.Level.VERBOSE),
            debug = currentState.countByLevel(UnifiedLogRepository.Level.DEBUG),
            info = currentState.countByLevel(UnifiedLogRepository.Level.INFO),
            warn = currentState.countByLevel(UnifiedLogRepository.Level.WARN),
            error = currentState.countByLevel(UnifiedLogRepository.Level.ERROR),
            crash = currentState.countByLevel(UnifiedLogRepository.Level.CRASH),
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
        val crash: Int,
        val filtered: Int,
    )

    companion object {
        fun factory(app: Application): androidx.lifecycle.ViewModelProvider.Factory {
            return object : androidx.lifecycle.ViewModelProvider.Factory {
                override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                    @Suppress("UNCHECKED_CAST")
                    return UnifiedLogViewModel(app) as T
                }
            }
        }
    }
}
