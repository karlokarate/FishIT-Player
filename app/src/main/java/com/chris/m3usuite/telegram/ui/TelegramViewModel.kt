package com.chris.m3usuite.telegram.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chris.m3usuite.telegram.browser.ChatBrowser
import com.chris.m3usuite.telegram.config.ConfigLoader
import com.chris.m3usuite.telegram.session.AuthEvent
import com.chris.m3usuite.telegram.session.TelegramSession
import dev.g000sha256.tdl.TdlClient
import dev.g000sha256.tdl.dto.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * ViewModel example demonstrating TelegramSession integration.
 * This shows how to manage authentication and chat browsing in a ViewModel.
 *
 * Usage in a real app:
 * - Inject dependencies via Hilt/Koin
 * - Store API credentials securely
 * - Persist authentication state
 * - Handle configuration changes properly
 *
 * @deprecated Legacy TDLib integration - do not use in new code. Use TelegramSettingsViewModel or specialized VMs instead.
 */
@Deprecated(
    message = "Legacy TDLib integration - do not use in new code. Use TelegramSettingsViewModel or specialized VMs instead.",
    level = DeprecationLevel.WARNING
)
class TelegramViewModel(
    private val context: Context,
    private val apiId: Int,
    private val apiHash: String,
) : ViewModel() {
    // TDLib client and session
    private var client: TdlClient? = null
    private var session: TelegramSession? = null
    private var browser: ChatBrowser? = null

    // UI State
    private val _uiState = MutableStateFlow<TelegramUiState>(TelegramUiState.Idle)
    val uiState: StateFlow<TelegramUiState> = _uiState.asStateFlow()

    // Chats list
    private val _chats = MutableStateFlow<List<Chat>>(emptyList())
    val chats: StateFlow<List<Chat>> = _chats.asStateFlow()

    /**
     * Initialize TDLib client and start authentication.
     * Call this when the user wants to connect to Telegram.
     */
    fun initialize(phoneNumber: String) {
        viewModelScope.launch {
            try {
                _uiState.value = TelegramUiState.Initializing

                // Create client and configuration
                client = TdlClient.create()
                val config =
                    ConfigLoader.load(
                        context = context,
                        apiId = apiId,
                        apiHash = apiHash,
                        phoneNumber = phoneNumber,
                    )

                // Create session
                session = TelegramSession(client!!, config, viewModelScope)
                browser = ChatBrowser(session!!)

                // Listen to auth events
                collectAuthEvents()

                // Start login
                session!!.login()
            } catch (e: Exception) {
                _uiState.value = TelegramUiState.Error("Failed to initialize: ${e.message}")
            }
        }
    }

    /**
     * Send phone number for authentication.
     */
    fun sendPhoneNumber(phoneNumber: String) {
        viewModelScope.launch {
            try {
                _uiState.value = TelegramUiState.SendingPhoneNumber
                session?.sendPhoneNumber(phoneNumber)
            } catch (e: Exception) {
                _uiState.value = TelegramUiState.Error("Failed to send phone: ${e.message}")
            }
        }
    }

    /**
     * Send verification code.
     */
    fun sendCode(code: String) {
        viewModelScope.launch {
            try {
                _uiState.value = TelegramUiState.SendingCode
                session?.sendCode(code)
            } catch (e: Exception) {
                _uiState.value = TelegramUiState.Error("Invalid code: ${e.message}")
            }
        }
    }

    /**
     * Send 2FA password.
     */
    fun sendPassword(password: String) {
        viewModelScope.launch {
            try {
                _uiState.value = TelegramUiState.SendingPassword
                session?.sendPassword(password)
            } catch (e: Exception) {
                _uiState.value = TelegramUiState.Error("Invalid password: ${e.message}")
            }
        }
    }

    /**
     * Load chats after authentication.
     */
    fun loadChats() {
        viewModelScope.launch {
            try {
                _uiState.value = TelegramUiState.LoadingChats
                val chatList = browser?.loadChats() ?: emptyList()
                _chats.value = chatList
                _uiState.value = TelegramUiState.ChatsLoaded(chatList.size)
            } catch (e: Exception) {
                _uiState.value = TelegramUiState.Error("Failed to load chats: ${e.message}")
            }
        }
    }

    /**
     * Load messages from a specific chat.
     */
    fun loadMessages(chatId: Long): Flow<List<Message>> =
        flow {
            val messages = browser?.loadChatHistory(chatId) ?: emptyList()
            emit(messages)
        }

    /**
     * Logout from Telegram.
     */
    fun logout() {
        viewModelScope.launch {
            try {
                _uiState.value = TelegramUiState.LoggingOut
                session?.logout()
                cleanup()
                _uiState.value = TelegramUiState.Idle
            } catch (e: Exception) {
                _uiState.value = TelegramUiState.Error("Logout failed: ${e.message}")
            }
        }
    }

    /**
     * Collect authentication events and update UI state accordingly.
     */
    private fun collectAuthEvents() {
        viewModelScope.launch {
            session?.authEvents?.collect { event ->
                when (event) {
                    is AuthEvent.StateChanged -> handleAuthState(event.state)
                    is AuthEvent.Ready -> {
                        _uiState.value = TelegramUiState.Ready
                        loadChats() // Auto-load chats when ready
                    }
                    is AuthEvent.Error -> {
                        _uiState.value = TelegramUiState.Error(event.message)
                    }
                }
            }
        }
    }

    /**
     * Handle specific authorization states.
     */
    private fun handleAuthState(state: AuthorizationState) {
        _uiState.value =
            when (state) {
                is AuthorizationStateWaitTdlibParameters -> TelegramUiState.WaitingForParameters
                is AuthorizationStateWaitPhoneNumber -> TelegramUiState.WaitingForPhoneNumber
                is AuthorizationStateWaitCode -> TelegramUiState.WaitingForCode
                is AuthorizationStateWaitPassword -> TelegramUiState.WaitingForPassword
                is AuthorizationStateReady -> TelegramUiState.Ready
                else -> TelegramUiState.Unknown(state::class.simpleName ?: "Unknown")
            }
    }

    /**
     * Cleanup resources.
     */
    private fun cleanup() {
        browser = null
        session = null
        client = null
        _chats.value = emptyList()
    }

    override fun onCleared() {
        super.onCleared()
        cleanup()
    }
}

/**
 * UI State representing different stages of Telegram integration.
 */
sealed class TelegramUiState {
    object Idle : TelegramUiState()

    object Initializing : TelegramUiState()

    object WaitingForParameters : TelegramUiState()

    object WaitingForPhoneNumber : TelegramUiState()

    object SendingPhoneNumber : TelegramUiState()

    object WaitingForCode : TelegramUiState()

    object SendingCode : TelegramUiState()

    object WaitingForPassword : TelegramUiState()

    object SendingPassword : TelegramUiState()

    object Ready : TelegramUiState()

    object LoadingChats : TelegramUiState()

    data class ChatsLoaded(
        val count: Int,
    ) : TelegramUiState()

    object LoggingOut : TelegramUiState()

    data class Error(
        val message: String,
    ) : TelegramUiState()

    data class Unknown(
        val stateName: String,
    ) : TelegramUiState()
}
