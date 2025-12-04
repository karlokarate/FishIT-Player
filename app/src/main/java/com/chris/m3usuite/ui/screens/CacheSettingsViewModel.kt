package com.chris.m3usuite.ui.screens

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.chris.m3usuite.core.cache.CacheManager
import com.chris.m3usuite.core.cache.CacheResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Cache operation type for tracking which operation is in progress.
 */
enum class CacheOperationType {
    NONE,
    LOG,
    TDLIB,
    XTREAM,
    ALL,
}

/**
 * State for cache management operations.
 *
 * @param isOperationInProgress Whether any cache operation is currently running
 * @param currentOperation Which cache operation is currently running
 * @param lastResult Result of the last cache operation
 * @param showResultDialog Whether to show the result dialog
 */
data class CacheSettingsState(
    val isOperationInProgress: Boolean = false,
    val currentOperation: CacheOperationType = CacheOperationType.NONE,
    val lastResult: CacheResult? = null,
    val showResultDialog: Boolean = false,
)

/**
 * ViewModel for cache management in Settings screen.
 *
 * Provides methods to clear various subsystem caches:
 * - Log cache
 * - TDLib (Telegram) cache
 * - Xtream/ExoPlayer cache
 * - All caches
 *
 * All operations run on background threads and update UI state accordingly.
 */
class CacheSettingsViewModel(
    app: Application,
) : AndroidViewModel(app) {
    private val cacheManager = CacheManager(app)

    private val _state = MutableStateFlow(CacheSettingsState())
    val state: StateFlow<CacheSettingsState> = _state

    /**
     * Clear log cache.
     */
    fun clearLogCache() {
        if (_state.value.isOperationInProgress) return

        _state.update {
            it.copy(
                isOperationInProgress = true,
                currentOperation = CacheOperationType.LOG,
                lastResult = null,
                showResultDialog = false,
            )
        }

        viewModelScope.launch {
            val result = cacheManager.clearLogCache()
            _state.update {
                it.copy(
                    isOperationInProgress = false,
                    currentOperation = CacheOperationType.NONE,
                    lastResult = result,
                    showResultDialog = true,
                )
            }
        }
    }

    /**
     * Clear TDLib (Telegram) cache.
     */
    fun clearTdlibCache() {
        if (_state.value.isOperationInProgress) return

        _state.update {
            it.copy(
                isOperationInProgress = true,
                currentOperation = CacheOperationType.TDLIB,
                lastResult = null,
                showResultDialog = false,
            )
        }

        viewModelScope.launch {
            val result = cacheManager.clearTdlibCache()
            _state.update {
                it.copy(
                    isOperationInProgress = false,
                    currentOperation = CacheOperationType.NONE,
                    lastResult = result,
                    showResultDialog = true,
                )
            }
        }
    }

    /**
     * Clear Xtream/ExoPlayer cache.
     */
    fun clearXtreamCache() {
        if (_state.value.isOperationInProgress) return

        _state.update {
            it.copy(
                isOperationInProgress = true,
                currentOperation = CacheOperationType.XTREAM,
                lastResult = null,
                showResultDialog = false,
            )
        }

        viewModelScope.launch {
            val result = cacheManager.clearXtreamCache()
            _state.update {
                it.copy(
                    isOperationInProgress = false,
                    currentOperation = CacheOperationType.NONE,
                    lastResult = result,
                    showResultDialog = true,
                )
            }
        }
    }

    /**
     * Clear all caches.
     */
    fun clearAllCaches() {
        if (_state.value.isOperationInProgress) return

        _state.update {
            it.copy(
                isOperationInProgress = true,
                currentOperation = CacheOperationType.ALL,
                lastResult = null,
                showResultDialog = false,
            )
        }

        viewModelScope.launch {
            val result = cacheManager.clearAllCaches()
            _state.update {
                it.copy(
                    isOperationInProgress = false,
                    currentOperation = CacheOperationType.NONE,
                    lastResult = result,
                    showResultDialog = true,
                )
            }
        }
    }

    /**
     * Dismiss the result dialog.
     */
    fun dismissResultDialog() {
        _state.update { it.copy(showResultDialog = false) }
    }

    companion object {
        fun factory(app: Application): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    if (modelClass.isAssignableFrom(CacheSettingsViewModel::class.java)) {
                        return CacheSettingsViewModel(app) as T
                    }
                    throw IllegalArgumentException("Unknown ViewModel class")
                }
            }
    }
}
