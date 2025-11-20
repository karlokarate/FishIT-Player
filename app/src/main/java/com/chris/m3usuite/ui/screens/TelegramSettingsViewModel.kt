package com.chris.m3usuite.ui.screens

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.chris.m3usuite.prefs.SettingsStore
import com.chris.m3usuite.telegram.browser.ChatBrowser
import com.chris.m3usuite.telegram.config.ConfigLoader
import com.chris.m3usuite.telegram.session.AuthEvent
import com.chris.m3usuite.telegram.session.TelegramSession
import dev.g000sha256.tdl.TdlClient
import dev.g000sha256.tdl.dto.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * ViewModel for Telegram settings section.
 * Manages authentication state, chat selection, and sync configuration.
 */
class TelegramSettingsViewModel(
    private val app: Application,
    private val store: SettingsStore
) : ViewModel() {

    // TDLib components
    private var client: TdlClient? = null
    private var session: TelegramSession? = null
    private var browser: ChatBrowser? = null

    // State flows
    private val _state = MutableStateFlow(TelegramSettingsState())
    val state: StateFlow<TelegramSettingsState> = _state.asStateFlow()

    init {
        // Load initial state from settings
        viewModelScope.launch {
            combine(
                store.tgEnabled,
                store.tgApiId,
                store.tgApiHash,
                store.tgSelectedChatsCsv,
                store.tgCacheLimitGb
            ) { enabled, apiId, apiHash, chats, cacheLimit ->
                _state.update { current ->
                    current.copy(
                        enabled = enabled,
                        apiId = apiId.toString(),
                        apiHash = apiHash,
                        selectedChats = chats.split(",").filter { it.isNotBlank() },
                        cacheLimitGb = cacheLimit
                    )
                }
            }.collect()
        }
    }

    /**
     * Toggle Telegram integration on/off.
     */
    fun onToggleEnabled(enabled: Boolean) {
        viewModelScope.launch {
            store.setTgEnabled(enabled)
            _state.update { it.copy(enabled = enabled) }
            if (!enabled) {
                cleanup()
            }
        }
    }

    /**
     * Update API credentials.
     */
    fun onUpdateCredentials(apiId: String, apiHash: String) {
        viewModelScope.launch {
            val idInt = apiId.toIntOrNull() ?: 0
            store.setTelegramApiId(idInt)
            store.setTelegramApiHash(apiHash)
            _state.update { it.copy(apiId = apiId, apiHash = apiHash) }
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
                    _state.update { it.copy(
                        isConnecting = false,
                        errorMessage = "Bitte API-ID und API-Hash eingeben"
                    )}
                    return@launch
                }

                // Create client and config
                client = TdlClient.create()
                val config = ConfigLoader.load(
                    context = app,
                    apiId = apiId,
                    apiHash = apiHash,
                    phoneNumber = phoneNumber
                )

                // Create session and browser
                session = TelegramSession(client!!, config, viewModelScope)
                browser = ChatBrowser(session!!)

                // Listen to auth events
                collectAuthEvents()

                // Start login
                session!!.login()
                
            } catch (e: Exception) {
                _state.update { it.copy(
                    isConnecting = false,
                    errorMessage = "Verbindung fehlgeschlagen: ${e.message}"
                )}
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
                session?.sendCode(code)
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
                session?.sendPassword(password)
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
                val chats = browser?.loadChats() ?: emptyList()
                _state.update { it.copy(
                    isLoadingChats = false,
                    availableChats = chats.map { chat ->
                        ChatInfo(
                            id = chat.id,
                            title = chat.title,
                            type = when (chat.type) {
                                is ChatTypePrivate -> "Privat"
                                is ChatTypeBasicGroup -> "Gruppe"
                                is ChatTypeSupergroup -> "Kanal"
                                is ChatTypeSecret -> "Geheim"
                                else -> "Unbekannt"
                            }
                        )
                    }
                )}
            } catch (e: Exception) {
                _state.update { it.copy(
                    isLoadingChats = false,
                    errorMessage = "Chats laden fehlgeschlagen: ${e.message}"
                )}
            }
        }
    }

    /**
     * Update selected chats for content parsing.
     */
    fun onUpdateSelectedChats(chatIds: List<String>) {
        viewModelScope.launch {
            val csv = chatIds.joinToString(",")
            store.setTgSelectedChatsCsv(csv)
            _state.update { it.copy(selectedChats = chatIds) }
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
                session?.logout()
                cleanup()
                _state.update { it.copy(
                    authState = TelegramAuthState.DISCONNECTED,
                    selectedChats = emptyList()
                )}
            } catch (e: Exception) {
                _state.update { it.copy(errorMessage = "Trennung fehlgeschlagen: ${e.message}") }
            }
        }
    }

    /**
     * Collect authentication events and update state.
     */
    private fun collectAuthEvents() {
        viewModelScope.launch {
            session?.authEvents?.collect { event ->
                when (event) {
                    is AuthEvent.StateChanged -> handleAuthState(event.state)
                    is AuthEvent.Ready -> {
                        _state.update { it.copy(
                            isConnecting = false,
                            authState = TelegramAuthState.READY
                        )}
                        onLoadChats()  // Auto-load chats when ready
                    }
                    is AuthEvent.Error -> {
                        _state.update { it.copy(
                            isConnecting = false,
                            errorMessage = event.message
                        )}
                    }
                }
            }
        }
    }

    /**
     * Handle specific authorization states.
     */
    private fun handleAuthState(authState: AuthorizationState) {
        val newState = when (authState) {
            is AuthorizationStateWaitPhoneNumber -> TelegramAuthState.WAITING_FOR_PHONE
            is AuthorizationStateWaitCode -> TelegramAuthState.WAITING_FOR_CODE
            is AuthorizationStateWaitPassword -> TelegramAuthState.WAITING_FOR_PASSWORD
            is AuthorizationStateReady -> TelegramAuthState.READY
            else -> TelegramAuthState.DISCONNECTED
        }
        _state.update { it.copy(authState = newState) }
    }

    /**
     * Cleanup resources.
     */
    private fun cleanup() {
        browser = null
        session = null
        client = null
        _state.update { it.copy(availableChats = emptyList()) }
    }

    override fun onCleared() {
        super.onCleared()
        cleanup()
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
    val isConnecting: Boolean = false,
    val isLoadingChats: Boolean = false,
    val errorMessage: String? = null,
    val selectedChats: List<String> = emptyList(),
    val availableChats: List<ChatInfo> = emptyList(),
    val cacheLimitGb: Int = 5
)

/**
 * Simplified chat info for UI display.
 */
data class ChatInfo(
    val id: Long,
    val title: String,
    val type: String
)

/**
 * Telegram authentication state enum.
 */
enum class TelegramAuthState {
    DISCONNECTED,
    WAITING_FOR_PHONE,
    WAITING_FOR_CODE,
    WAITING_FOR_PASSWORD,
    READY
}
