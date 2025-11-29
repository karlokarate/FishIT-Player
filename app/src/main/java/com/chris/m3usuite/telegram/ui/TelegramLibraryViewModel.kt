package com.chris.m3usuite.telegram.ui

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.chris.m3usuite.data.repo.TelegramContentRepository
import com.chris.m3usuite.prefs.SettingsStore
import com.chris.m3usuite.telegram.domain.ChatScanState
import com.chris.m3usuite.telegram.domain.TelegramItem
import com.chris.m3usuite.telegram.repository.TelegramSyncStateRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * ViewModel for Telegram library/content screens.
 *
 * Per Phase D.1 requirements:
 * - Injects TelegramContentRepository and TelegramSyncStateRepository
 * - Exposes TelegramItem flows for UI consumption
 * - No lifecycle manipulation (standard viewModelScope usage)
 *
 * UI components can use this ViewModel to:
 * - Display Telegram items in lists/rows (FishTelegramContent)
 * - Show scan progress for diagnostics
 * - Load individual items for detail screens
 */
class TelegramLibraryViewModel(
    private val app: Application,
    private val store: SettingsStore,
) : ViewModel() {
    private val contentRepository = TelegramContentRepository(app, store)
    private val syncStateRepository = TelegramSyncStateRepository(app)

    // =========================================================================
    // All Items Flow
    // =========================================================================

    /**
     * StateFlow of all TelegramItems.
     *
     * Uses stateIn to convert repository Flow to StateFlow.
     * SharingStarted.WhileSubscribed ensures efficient resource usage.
     */
    val allItems: StateFlow<List<TelegramItem>> =
        contentRepository
            .observeAllItems()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList(),
            )

    // =========================================================================
    // Items By Chat
    // =========================================================================

    // Cache for chat-specific flows to avoid re-creation
    private val chatItemsCache = mutableMapOf<Long, StateFlow<List<TelegramItem>>>()

    /**
     * Get a StateFlow of TelegramItems for a specific chat.
     *
     * Flows are cached per chatId to avoid re-creation on recomposition.
     *
     * @param chatId Chat ID to filter by
     * @return StateFlow of TelegramItems for the specified chat
     */
    fun itemsByChat(chatId: Long): StateFlow<List<TelegramItem>> =
        chatItemsCache.getOrPut(chatId) {
            contentRepository
                .observeItemsByChat(chatId)
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(5000),
                    initialValue = emptyList(),
                )
        }

    // =========================================================================
    // Scan States
    // =========================================================================

    /**
     * StateFlow of all ChatScanStates for diagnostics.
     */
    val scanStates: StateFlow<List<ChatScanState>> =
        syncStateRepository
            .observeScanStates()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList(),
            )

    // =========================================================================
    // Single Item Loading
    // =========================================================================

    // State for single-item loading (used by detail screens)
    private val _selectedItem = MutableStateFlow<TelegramItem?>(null)
    val selectedItem: StateFlow<TelegramItem?> = _selectedItem.asStateFlow()

    private val _isLoadingItem = MutableStateFlow(false)
    val isLoadingItem: StateFlow<Boolean> = _isLoadingItem.asStateFlow()

    /**
     * Load a single TelegramItem by its logical key (chatId, anchorMessageId).
     *
     * Used by TelegramDetailScreen to load item details.
     *
     * @param chatId Chat ID
     * @param anchorMessageId Anchor message ID
     */
    fun loadItem(
        chatId: Long,
        anchorMessageId: Long,
    ) {
        viewModelScope.launch {
            _isLoadingItem.value = true
            try {
                _selectedItem.value = contentRepository.getItem(chatId, anchorMessageId)
            } finally {
                _isLoadingItem.value = false
            }
        }
    }

    /**
     * Clear the currently selected item.
     */
    fun clearSelectedItem() {
        _selectedItem.value = null
    }

    // =========================================================================
    // Item Count (for diagnostics)
    // =========================================================================

    private val _itemCount = MutableStateFlow(0L)
    val itemCount: StateFlow<Long> = _itemCount.asStateFlow()

    /**
     * Refresh the total item count.
     */
    fun refreshItemCount() {
        viewModelScope.launch {
            _itemCount.value = contentRepository.getTelegramItemCount()
        }
    }

    // =========================================================================
    // Factory
    // =========================================================================

    companion object {
        fun factory(app: Application): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    val store = SettingsStore(app)
                    return TelegramLibraryViewModel(app, store) as T
                }
            }
    }
}
