package com.chris.m3usuite.telegram.ui

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.chris.m3usuite.prefs.SettingsStore
import com.chris.m3usuite.telegram.core.*
import com.chris.m3usuite.telegram.logging.TelegramLogRepository
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
            // Task 1: Collect unified engine state
            launch {
                serviceClient.engineState.collect { engineState ->
                    _state.update { current ->
                        current.copy(
                            // isEnabled is synced from SettingsStore (below)
                            authState = mapCoreAuthStateToUI(engineState.authState),
                            isEngineHealthy = engineState.isEngineHealthy,
                            recentError = engineState.recentError,
                        )
                    }
                }
            }

            // Collect settings
            launch {
                combine(
                    store.tgEnabled,
                    store.tgApiId,
                    store.tgApiHash,
                    store.tgSelectedChatsCsv,
                    store.tgCacheLimitGb,
                ) { enabled, apiId, apiHash, chats, cacheLimit ->
                    // Task 1: Also update ServiceClient when enabled changes from settings
                    serviceClient.setTelegramEnabled(enabled)

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

            // Collect connection state
            launch {
                serviceClient.connectionState.collect { connState ->
                    _state.update { current ->
                        current.copy(connectionState = connState)
                    }
                }
            }

            // Auto-start if enabled is persisted as true but auth state is still Idle
            // This ensures the toggle OFF/ON workaround is no longer needed
            ensureStartedIfEnabled()
        }
    }

    /**
     * Auto-start Telegram engine if enabled is persisted as true and API credentials exist.
     * This fixes the issue where users had to toggle OFF/ON after app restart.
     */
    private fun ensureStartedIfEnabled() {
        viewModelScope.launch {
            val enabled = store.tgEnabled.first()
            val apiId = store.tgApiId.first()
            val apiHash = store.tgApiHash.first()
            val hasApiCreds = apiId != 0 && apiHash.isNotBlank()

            if (enabled && hasApiCreds && !_state.value.isConnecting) {
                TelegramLogRepository.info(
                    source = "TelegramSettingsViewModel",
                    message = "Auto-starting engine on ViewModel init (enabled=true persisted)",
                )
                try {
                    serviceClient.ensureStarted(app, store)
                    serviceClient.login() // Let TDLib determine if session is valid
                } catch (e: Exception) {
                    TelegramLogRepository.warn(
                        source = "TelegramSettingsViewModel",
                        message = "Auto-start failed: ${e.message}",
                    )
                }
            }
        }
    }

    /**
     * Toggle Telegram integration on/off.
     * Triggers an initial full sync when enabled for the first time.
     *
     * **Task 3: Entry point with stack trace logging**
     *
     * This is the ONLY entry point for changing the enabled state.
     * Logs a stack trace (in debug builds only) to audit unexpected callers.
     */
    fun onToggleEnabled(enabled: Boolean) {
        // Log stack trace for auditing (debug builds only to avoid performance impact)
        if (com.chris.m3usuite.BuildConfig.DEBUG) {
            TelegramLogRepository.info(
                source = "TelegramSettingsViewModel",
                message = "setTelegramEnabled called",
                details =
                    mapOf(
                        "enabled" to enabled.toString(),
                        "caller" to
                            Thread
                                .currentThread()
                                .stackTrace
                                .drop(
                                    2,
                                ).take(5)
                                .joinToString(" -> ") { "${it.className}.${it.methodName}:${it.lineNumber}" },
                    ),
            )
        } else {
            TelegramLogRepository.info(
                source = "TelegramSettingsViewModel",
                message = "setTelegramEnabled called",
                details = mapOf("enabled" to enabled.toString()),
            )
        }

        viewModelScope.launch {
            val wasEnabled = _state.value.enabled

            // Task 1: Use ServiceClient's setTelegramEnabled method
            setTelegramEnabledInternal(enabled)
            serviceClient.setTelegramEnabled(enabled)

            if (!enabled) {
                TelegramLogRepository.info(
                    source = "TelegramSettingsViewModel",
                    message = "Telegram disabled by user",
                )
                serviceClient.shutdown()
            } else if (!wasEnabled && enabled) {
                TelegramLogRepository.info(
                    source = "TelegramSettingsViewModel",
                    message = "Telegram enabled by user - warm-up starting",
                )

                // Warm-up: Start service and login flow without requiring phone
                try {
                    serviceClient.ensureStarted(app, store)
                    serviceClient.login() // Let TDLib determine if session is valid
                    // Task 3: Mark engine as healthy after successful startup
                    // (already handled in T_TelegramServiceClient.ensureStarted)
                } catch (e: Exception) {
                    TelegramLogRepository.info(
                        source = "TelegramSettingsViewModel",
                        message = "Warm-up failed: ${e.message}",
                    )
                    // Task 3: Engine errors affect engine health, NOT isEnabled
                    // The error is already logged and tracked by ServiceClient
                    // We do NOT flip isEnabled back to false
                }

                // Note: Initial sync is NOT triggered here because login() is asynchronous
                // and auth state won't be READY immediately. The sync should be triggered
                // by a separate observer that monitors auth state transitions to READY,
                // or manually by the user after authentication completes.
            }
        }
    }

    /**
     * Internal method to update enabled state.
     * Only called by onToggleEnabled to ensure stack trace logging.
     */
    private suspend fun setTelegramEnabledInternal(enabled: Boolean) {
        try {
            store.setTgEnabled(enabled)
            _state.update { it.copy(enabled = enabled) }
        } catch (e: Exception) {
            TelegramLogRepository.error(
                source = "TelegramSettingsViewModel",
                message = "Failed to persist enabled state",
                exception = e,
            )
            // State update failed - throw to caller
            throw e
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
                        // Task 3: Don't manually set isEngineHealthy - it's managed by ServiceClient
                        errorMessage = "Verbindung fehlgeschlagen: ${e.message}",
                    )
                }
                TelegramLogRepository.error(
                    source = "TelegramSettingsViewModel",
                    message = "Connection start failed",
                    exception = e,
                )
            }
        }
    }

    /**
     * Initialize TDLib connection with phone number.
     */
    fun onConnectWithPhone(phoneNumber: String) {
        viewModelScope.launch {
            try {
                _state.update { it.copy(isConnecting = true, errorMessage = null, recentError = null) }

                // Save phone number persistently
                store.setTelegramPhoneNumber(phoneNumber)
                TelegramLogRepository.info(
                    source = "TelegramSettingsViewModel",
                    message = "Phone number saved persistently",
                )

                // Ensure service is started
                serviceClient.ensureStarted(app, store)

                // Submit phone number
                serviceClient.login(phone = phoneNumber)

                _state.update { it.copy(isConnecting = false) }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        isConnecting = false,
                        // Task 3: Don't manually set isEngineHealthy - it's managed by ServiceClient
                        errorMessage = "Verbindung fehlgeschlagen: ${e.message}",
                    )
                }
                TelegramLogRepository.error(
                    source = "TelegramSettingsViewModel",
                    message = "Connection with phone failed",
                    exception = e,
                )
            }
        }
    }

    /**
     * Send verification code.
     */
    fun onSendCode(code: String) {
        viewModelScope.launch {
            try {
                _state.update { it.copy(errorMessage = null, recentError = null) }
                serviceClient.login(code = code)
                // Task 3: Don't manually set isEngineHealthy - it's managed by ServiceClient
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        // Task 3: Don't manually set isEngineHealthy - it's managed by ServiceClient
                        errorMessage = "Ungültiger Code: ${e.message}",
                    )
                }
                TelegramLogRepository.error(
                    source = "TelegramSettingsViewModel",
                    message = "Code verification failed",
                    exception = e,
                )
            }
        }
    }

    /**
     * Send 2FA password.
     */
    fun onSendPassword(password: String) {
        viewModelScope.launch {
            try {
                _state.update { it.copy(errorMessage = null, recentError = null) }
                serviceClient.login(password = password)
                // Task 3: Don't manually set isEngineHealthy - it's managed by ServiceClient
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        // Task 3: Don't manually set isEngineHealthy - it's managed by ServiceClient
                        errorMessage = "Ungültiges Passwort: ${e.message}",
                    )
                }
                TelegramLogRepository.error(
                    source = "TelegramSettingsViewModel",
                    message = "Password verification failed",
                    exception = e,
                )
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
                        // Task 3: Don't manually set isEngineHealthy - it's managed by ServiceClient
                        errorMessage = "Chats laden fehlgeschlagen: ${e.message}",
                    )
                }
                TelegramLogRepository.error(
                    source = "TelegramSettingsViewModel",
                    message = "Failed to load chats",
                    exception = e,
                )
            }
        }
    }

    /**
     * Update selected chats for content parsing.
     * Triggers a sync with MODE_SELECTION_CHANGED after updating settings.
     *
     * Note: Writes to all three CSV settings (general, VOD, Series) so that
     * StartViewModel and LibraryScreen properly display Telegram rows.
     */
    fun onUpdateSelectedChats(chatIds: List<String>) {
        viewModelScope.launch {
            TelegramLogRepository.info(
                source = "TelegramSettingsViewModel",
                message = "User updated Telegram chat selection",
                details = mapOf("selectedCount" to chatIds.size.toString()),
            )

            val csv = chatIds.joinToString(",")
            // Write to general CSV (unified selection)
            store.setTgSelectedChatsCsv(csv)
            // Also write to VOD and Series CSVs so StartViewModel and LibraryScreen
            // can display rows using their per-type observers
            store.setTelegramSelectedVodChatsCsv(csv)
            store.setTelegramSelectedSeriesChatsCsv(csv)
            _state.update { it.copy(selectedChats = chatIds) }

            // Clear prefetcher cache when chat selection changes
            com.chris.m3usuite.telegram.prefetch.TelegramPrefetcherHolder
                .clear()

            // Trigger sync after chat selection changes
            if (chatIds.isNotEmpty() && _state.value.enabled) {
                SchedulingGateway.scheduleTelegramSync(
                    ctx = app,
                    mode = "selection_changed",
                    refreshHome = true,
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
                _state.update {
                    it.copy(
                        isEngineHealthy = false,
                        recentError = "Disconnect failed: ${e.message}",
                        errorMessage = "Trennung fehlgeschlagen: ${e.message}",
                    )
                }
                TelegramLogRepository.error(
                    source = "TelegramSettingsViewModel",
                    message = "Disconnect failed",
                    exception = e,
                )
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
 *
 * **Task 3: Separate User Decision from Engine Health**
 *
 * This model separates:
 * - `enabled`: User's decision to enable/disable Telegram (only changed by explicit user actions)
 * - `isEngineHealthy`: Runtime health of Telegram engine (changed by engine failures/recoveries)
 * - `recentError`: Most recent error message for display (does not affect enabled toggle)
 *
 * The `enabled` toggle should ONLY be changed from:
 * - Settings screen user action
 * - Explicit user toggle
 * - Initial app setup
 *
 * Engine failures and crashes should ONLY affect `isEngineHealthy`, NOT `enabled`.
 */
data class TelegramSettingsState(
    // User decision - only changed by explicit user actions
    val enabled: Boolean = false,
    // Engine health - changed by engine failures/recoveries
    val isEngineHealthy: Boolean = true,
    // Recent error for display (does not affect enabled toggle)
    val recentError: String? = null,
    // API credentials
    val apiId: String = "",
    val apiHash: String = "",
    // Auth and connection state
    val authState: TelegramAuthState = TelegramAuthState.DISCONNECTED,
    val connectionState: TgConnectionState = TgConnectionState.Disconnected,
    // Loading states
    val isConnecting: Boolean = false,
    val isLoadingChats: Boolean = false,
    // Legacy error message (will be replaced by recentError)
    val errorMessage: String? = null,
    // Chat selection
    val selectedChats: List<String> = emptyList(),
    val availableChats: List<ChatInfo> = emptyList(),
    // Cache settings
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
