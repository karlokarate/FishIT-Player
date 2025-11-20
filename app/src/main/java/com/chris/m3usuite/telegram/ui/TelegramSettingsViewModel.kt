package com.chris.m3usuite.telegram.ui

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.chris.m3usuite.prefs.SettingsStore
import com.chris.m3usuite.telegram.core.*
import com.chris.m3usuite.work.SchedulingGateway
import dev.g000sha256.tdl.dto.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * ViewModel for Telegram settings section.
 *
 * Completely wired to T_TelegramServiceClient (Core layer).
 * No direct TdlClient access - all operations go through ServiceClient.
 *
 * Manages:
 * - Authentication state and login flow
 * - Chat selection for VOD/Series/Feed
 * - Sync configuration
 * - API credentials
 * - Cache limits
 *
 * Key responsibilities:
 * - Expose auth state from ServiceClient
 * - Handle user actions (phone, code, password entry)
 * - Load and manage chat list
 * - Save chat selections to SettingsStore
 * - Trigger sync operations
 */
class TelegramSettingsViewModel(
    private val app: Application,
    private val store: SettingsStore,
) : ViewModel() {
    // Service client - single source of truth
    private val serviceClient = T_TelegramServiceClient.getInstance(app)

    // State flows
    private val _state = MutableStateFlow(TelegramSettingsState())
    val state: StateFlow<TelegramSettingsState> = _state.asStateFlow()

    init {
        // Load initial state from settings and wire to ServiceClient
        // Note: These collectors run in viewModelScope which automatically cancels
        // when the ViewModel is cleared, preventing memory leaks. The singleton
        // ServiceClient properly handles multiple collectors from different ViewModels.
        viewModelScope.launch {
            // Collect settings
            launch {
                combine(
                    store.tgEnabled,
                    store.tgApiId,
                    store.tgApiHash,
                    store.tgSelectedChatsCsv,
                    store.tgCacheLimitGb,
                ) { enabled, apiId, apiHash, chats, cacheLimit ->
                    _state.update { current ->
                        current.copy(
                            enabled = enabled,
                            apiId = apiId.toString(),
                            apiHash = apiHash,
                            selectedChats = chats.split(",").filter { it.isNotBlank() },
                            cacheLimitGb = cacheLimit,
                        )
                    }
                }.collect()
            }

            // Collect auth state
            launch {
                serviceClient.authState.collect { authState ->
                    _state.update { current ->
                        current.copy(authState = mapCoreAuthStateToUI(authState))
                    }
                }
            }

            // Collect connection state
            launch {
                serviceClient.connectionState.collect { connState ->
                    _state.update { current ->
                        current.copy(connectionState = connState)
                    }
                }
            }
        }
    }

    /**
     * Toggle Telegram integration on/off.
     * Triggers an initial full sync when enabled for the first time.
     */
    fun onToggleEnabled(enabled: Boolean) {
        viewModelScope.launch {
            val wasEnabled = _state.value.enabled
            store.setTgEnabled(enabled)
            _state.update { it.copy(enabled = enabled) }

            if (!enabled) {
                serviceClient.shutdown()
            } else if (!wasEnabled && enabled) {
                // First activation - trigger initial full sync if chats are selected
                val selectedChats = store.tgSelectedChatsCsv.first()
                if (selectedChats.isNotBlank() && _state.value.authState == TelegramAuthState.READY) {
                    SchedulingGateway.scheduleTelegramSync(
                        ctx = app,
                        mode = "all",
                        refreshHome = true
                    )
                }
            }
        }
    }

    /**
     * Update API credentials.
     */
    fun onUpdateCredentials(
        apiId: String,
        apiHash: String,
    ) {
        viewModelScope.launch {
            val idInt = apiId.toIntOrNull() ?: 0
            store.setTelegramApiId(idInt)
            store.setTelegramApiHash(apiHash)
            _state.update { it.copy(apiId = apiId, apiHash = apiHash) }
        }
    }

    /**
     * Initialize TDLib connection and start auth flow.
     */
    fun onStartConnection() {
        viewModelScope.launch {
            try {
                _state.update { it.copy(isConnecting = true, errorMessage = null) }

                // Ensure service is started
                serviceClient.ensureStarted(app, store)

                // Start login flow
                serviceClient.login()

                _state.update { it.copy(isConnecting = false) }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        isConnecting = false,
                        errorMessage = "Verbindung fehlgeschlagen: ${e.message}",
                    )
                }
            }
        }
    }

    /**
     * Initialize TDLib connection with phone number.
     */
    fun onConnectWithPhone(phoneNumber: String) {
        viewModelScope.launch {
            try {
                _state.update { it.copy(isConnecting = true, errorMessage = null) }

                val apiId = _state.value.apiId.toIntOrNull() ?: 0
                val apiHash = _state.value.apiHash

                if (apiId == 0 || apiHash.isBlank()) {
                    _state.update {
                        it.copy(
                            isConnecting = false,
                            errorMessage = "Bitte API-ID und API-Hash eingeben",
                        )
                    }
                    return@launch
                }

                // Ensure service is started
                serviceClient.ensureStarted(app, store)

                // Submit phone number
                serviceClient.login(phone = phoneNumber)

                _state.update { it.copy(isConnecting = false) }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        isConnecting = false,
                        errorMessage = "Verbindung fehlgeschlagen: ${e.message}",
                    )
                }
            }
        }
    }

    /**
     * Send verification code.
     */
    fun onSendCode(code: String) {
        viewModelScope.launch {
            try {
                _state.update { it.copy(errorMessage = null) }
                serviceClient.login(code = code)
            } catch (e: Exception) {
                _state.update { it.copy(errorMessage = "Ungültiger Code: ${e.message}") }
            }
        }
    }

    /**
     * Send 2FA password.
     */
    fun onSendPassword(password: String) {
        viewModelScope.launch {
            try {
                _state.update { it.copy(errorMessage = null) }
                serviceClient.login(password = password)
            } catch (e: Exception) {
                _state.update { it.copy(errorMessage = "Ungültiges Passwort: ${e.message}") }
            }
        }
    }

    /**
     * Load available chats for selection.
     */
    fun onLoadChats() {
        viewModelScope.launch {
            try {
                _state.update { it.copy(isLoadingChats = true) }

                // Get chats from ServiceClient
                val chats = serviceClient.listChats(app, limit = 200)

                _state.update {
                    it.copy(
                        isLoadingChats = false,
                        availableChats =
                            chats.map { chat ->
                                ChatInfo(
                                    id = chat.id,
                                    title = chat.title,
                                    type =
                                        when (chat.type) {
                                            is ChatTypePrivate -> "Privat"
                                            is ChatTypeBasicGroup -> "Gruppe"
                                            is ChatTypeSupergroup -> "Kanal"
                                            is ChatTypeSecret -> "Geheim"
                                            else -> "Unbekannt"
                                        },
                                )
                            },
                    )
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        isLoadingChats = false,
                        errorMessage = "Chats laden fehlgeschlagen: ${e.message}",
                    )
                }
            }
        }
    }

    /**
     * Update selected chats for content parsing.
     * Triggers a sync with MODE_SELECTION_CHANGED after updating settings.
     */
    fun onUpdateSelectedChats(chatIds: List<String>) {
        viewModelScope.launch {
            val csv = chatIds.joinToString(",")
            store.setTgSelectedChatsCsv(csv)
            _state.update { it.copy(selectedChats = chatIds) }
            
            // Trigger sync after chat selection changes
            if (chatIds.isNotEmpty() && _state.value.enabled) {
                SchedulingGateway.scheduleTelegramSync(
                    ctx = app,
                    mode = "selection_changed",
                    refreshHome = true
                )
            }
        }
    }

    /**
     * Update cache limit.
     */
    fun onUpdateCacheLimit(limitGb: Int) {
        viewModelScope.launch {
            store.setTgCacheLimitGb(limitGb)
            _state.update { it.copy(cacheLimitGb = limitGb) }
        }
    }

    /**
     * Disconnect from Telegram.
     */
    fun onDisconnect() {
        viewModelScope.launch {
            try {
                serviceClient.shutdown()
                _state.update {
                    it.copy(
                        authState = TelegramAuthState.DISCONNECTED,
                        selectedChats = emptyList(),
                    )
                }
            } catch (e: Exception) {
                _state.update { it.copy(errorMessage = "Trennung fehlgeschlagen: ${e.message}") }
            }
        }
    }

    /**
     * Map core auth state to UI auth state.
     */
    private fun mapCoreAuthStateToUI(coreState: com.chris.m3usuite.telegram.core.TelegramAuthState): TelegramAuthState =
        when (coreState) {
            is com.chris.m3usuite.telegram.core.TelegramAuthState.Idle -> TelegramAuthState.DISCONNECTED
            is com.chris.m3usuite.telegram.core.TelegramAuthState.Connecting -> TelegramAuthState.DISCONNECTED
            is com.chris.m3usuite.telegram.core.TelegramAuthState.WaitingForPhone -> TelegramAuthState.WAITING_FOR_PHONE
            is com.chris.m3usuite.telegram.core.TelegramAuthState.WaitingForCode -> TelegramAuthState.WAITING_FOR_CODE
            is com.chris.m3usuite.telegram.core.TelegramAuthState.WaitingForPassword -> TelegramAuthState.WAITING_FOR_PASSWORD
            is com.chris.m3usuite.telegram.core.TelegramAuthState.Ready -> TelegramAuthState.READY
            is com.chris.m3usuite.telegram.core.TelegramAuthState.Error -> TelegramAuthState.DISCONNECTED
        }

    override fun onCleared() {
        super.onCleared()
        // ServiceClient lifecycle is managed by ProcessLifecycleOwner
        // We don't shutdown here to allow persistent connection
    }

    companion object {
        fun factory(app: Application): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    val store = SettingsStore(app)
                    return TelegramSettingsViewModel(app, store) as T
                }
            }
        }
    }
}

/**
 * State for Telegram settings UI.
 */
data class TelegramSettingsState(
    val enabled: Boolean = false,
    val apiId: String = "",
    val apiHash: String = "",
    val authState: TelegramAuthState = TelegramAuthState.DISCONNECTED,
    val connectionState: TgConnectionState = TgConnectionState.Disconnected,
    val isConnecting: Boolean = false,
    val isLoadingChats: Boolean = false,
    val errorMessage: String? = null,
    val selectedChats: List<String> = emptyList(),
    val availableChats: List<ChatInfo> = emptyList(),
    val cacheLimitGb: Int = 5,
)

/**
 * Simplified chat info for UI display.
 */
data class ChatInfo(
    val id: Long,
    val title: String,
    val type: String,
)

/**
 * Telegram authentication state enum for UI.
 */
enum class TelegramAuthState {
    DISCONNECTED,
    WAITING_FOR_PHONE,
    WAITING_FOR_CODE,
    WAITING_FOR_PASSWORD,
    READY,
}
