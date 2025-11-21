package com.chris.m3usuite.logs

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File

data class LogEntry(
    val raw: String,
    val source: String?, // z. B. "TelegramDataSource"
)

data class LogViewerState(
    val logFiles: List<File> = emptyList(),
    val selectedFile: File? = null,
    val entries: List<LogEntry> = emptyList(),
    val filteredContent: String = "",
    val availableSources: List<String> = emptyList(),
    val activeSources: Set<String> = emptySet(), // leer = "alle aktiv"
    val searchQuery: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
)

class LogViewerViewModel(
    application: Application,
) : AndroidViewModel(application) {

    private val _state = MutableStateFlow(LogViewerState())
    val state: StateFlow<LogViewerState> = _state

    private val logsDir: File by lazy {
        File(getApplication<Application>().filesDir, "telegram_logs")
    }

    init {
        refreshLogFiles()
    }

    fun refreshLogFiles() {
        viewModelScope.launch(Dispatchers.IO) {
            _state.value = _state.value.copy(isLoading = true, errorMessage = null)

            if (!logsDir.exists()) {
                logsDir.mkdirs()
            }

            val files = logsDir
                .listFiles { f -> f.isFile && f.name.endsWith(".txt") }
                ?.sortedByDescending { it.lastModified() }
                ?: emptyList()

            val selected = _state.value.selectedFile ?: files.firstOrNull()

            _state.value = _state.value.copy(
                logFiles = files,
                selectedFile = selected,
                isLoading = false,
            )

            if (selected != null) {
                loadFile(selected)
            } else {
                _state.value = _state.value.copy(entries = emptyList(), filteredContent = "")
            }
        }
    }

    fun selectFile(file: File) {
        viewModelScope.launch(Dispatchers.IO) {
            _state.value = _state.value.copy(
                selectedFile = file,
                isLoading = true,
                errorMessage = null,
            )
            loadFile(file)
        }
    }

    private fun parseLogFile(file: File): List<LogEntry> {
        if (!file.exists()) return emptyList()

        return file.readLines().map { line ->
            LogEntry(
                raw = line,
                source = extractSource(line),
            )
        }
    }

    /**
     * Extract source/tag from log line.
     * Supports multiple formats:
     * 1. Space-separated: "2025-..T..Z DEBUG TelegramDataSource ..."
     * 2. JSON: {"source":"TelegramDataSource",...}
     * 3. Bracketed: "[TelegramDataSource] ..."
     */
    private fun extractSource(line: String): String? {
        // Try JSON format first
        if (line.trim().startsWith("{")) {
            val sourceMatch = Regex(""""source"\s*:\s*"([^"]+)"""").find(line)
            if (sourceMatch != null) {
                return sourceMatch.groupValues[1]
            }
        }
        
        // Try bracketed format [Source]
        val bracketMatch = Regex("""\[([^\]]+)\]""").find(line)
        if (bracketMatch != null) {
            return bracketMatch.groupValues[1]
        }
        
        // Default: space-separated format (third token)
        val parts = line.split(" ", limit = 4)
        if (parts.size >= 3) {
            val candidate = parts[2]
            // Return if it looks like a source (starts with capital or T_)
            if (candidate.isNotEmpty() && 
                (candidate[0].isUpperCase() || candidate.startsWith("T_"))) {
                return candidate
            }
        }
        
        return null
    }

    private fun applyFilter(
        entries: List<LogEntry>,
        activeSources: Set<String>,
        searchQuery: String,
    ): String {
        return entries
            .asSequence()
            .filter { entry ->
                activeSources.isEmpty() || (entry.source != null && entry.source in activeSources)
            }
            .filter { entry ->
                searchQuery.isBlank() ||
                    entry.raw.contains(searchQuery, ignoreCase = true)
            }
            .joinToString("\n") { it.raw }
    }

    private fun updateFilteredContent() {
        val current = _state.value
        val newContent = applyFilter(
            entries = current.entries,
            activeSources = current.activeSources,
            searchQuery = current.searchQuery,
        )
        _state.value = current.copy(filteredContent = newContent)
    }

    private fun loadFile(file: File) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val entries = parseLogFile(file)
                val sources = entries.mapNotNull { it.source }.distinct().sorted()

                val current = _state.value
                val activeSources =
                    if (current.activeSources.isEmpty()) sources.toSet() // initial: alle aktiv
                    else current.activeSources.intersect(sources.toSet())

                val newState = current.copy(
                    selectedFile = file,
                    entries = entries,
                    availableSources = sources,
                    activeSources = activeSources,
                    isLoading = false,
                    errorMessage = null,
                )
                _state.value = newState
                updateFilteredContent()
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    entries = emptyList(),
                    filteredContent = "",
                    isLoading = false,
                    errorMessage = "Fehler beim Lesen des Logfiles: ${e.message}",
                )
            }
        }
    }

    fun toggleSourceFilter(source: String) {
        val current = _state.value
        val newSet = current.activeSources.toMutableSet().apply {
            if (contains(source)) remove(source) else add(source)
        }
        _state.value = current.copy(activeSources = newSet)
        updateFilteredContent()
    }

    fun setSearchQuery(query: String) {
        _state.value = _state.value.copy(searchQuery = query)
        updateFilteredContent()
    }

    fun currentFileOrNull(): File? = _state.value.selectedFile

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
}
