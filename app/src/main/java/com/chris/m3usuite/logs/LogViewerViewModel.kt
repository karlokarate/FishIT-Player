package com.chris.m3usuite.logs

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.chris.m3usuite.core.logging.AppLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class LogEntry(
    val raw: String,
    val source: String?, // category
)

data class LogViewerState(
    val entries: List<LogEntry> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
)

class LogViewerViewModel(
    application: Application,
) : AndroidViewModel(application) {
    private val _state = MutableStateFlow(LogViewerState())
    val state: StateFlow<LogViewerState> = _state
    private val entries = mutableListOf<LogEntry>()
    private val maxEntries = 1_000

    companion object {
        fun factory(app: Application): androidx.lifecycle.ViewModelProvider.Factory {
            return object : androidx.lifecycle.ViewModelProvider.Factory {
                override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                    @Suppress("UNCHECKED_CAST")
                    return LogViewerViewModel(app) as T
                }
            }
        }
    }

    init {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, errorMessage = null)
            // Seed with current history
            entries.clear()
            entries.addAll(AppLog.history.value.map(::toLogEntry))
            _state.value =
                _state.value.copy(
                    isLoading = false,
                    entries = entries.toList(),
                )
            // Append live events
            AppLog.events.collect { e ->
                entries.add(toLogEntry(e))
                if (entries.size > maxEntries) {
                    val drop = entries.size - maxEntries
                    repeat(drop) { entries.removeAt(0) }
                }
                _state.value = _state.value.copy(entries = entries.toList())
            }
        }
    }

    private fun toLogEntry(e: AppLog.Entry): LogEntry {
        val extras =
            if (e.extras.isNotEmpty()) {
                " " + e.extras.entries.joinToString(",") { (k, v) -> "$k=$v" }
            } else {
                ""
            }
        return LogEntry(
            raw = "[${e.level}] ${e.category}: ${e.message}$extras",
            source = e.category,
        )
    }
}
