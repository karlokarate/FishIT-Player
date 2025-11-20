package com.chris.m3usuite.ui.screens

import android.content.Context
import android.net.Uri
import com.chris.m3usuite.telegram.core.T_TelegramServiceClient
import com.chris.m3usuite.telegram.core.TelegramAuthState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map

/**
 * DEPRECATED: Legacy placeholder for SettingsViewModel compatibility.
 * Use TelegramSettingsViewModel and T_TelegramServiceClient instead.
 * 
 * This class exists only to prevent compilation errors in legacy code.
 * All functionality should be migrated to the new telegram.ui package.
 */
@Deprecated(
    message = "Legacy placeholder - use TelegramSettingsViewModel and T_TelegramServiceClient",
    level = DeprecationLevel.WARNING
)
class TelegramServiceClient(
    private val context: Context,
) {
    sealed class AuthState {
        object Idle : AuthState()
        object CodeSent : AuthState()
        object PasswordRequired : AuthState()
        object SignedIn : AuthState()
        object Error : AuthState()
    }
    
    sealed class AuthEvent {
        object CodeSent : AuthEvent()
        object PasswordRequired : AuthEvent()
        object SignedIn : AuthEvent()
        object Error : AuthEvent()
    }
    
    private val _authState = MutableStateFlow<AuthState>(AuthState.Idle)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()
    
    private val _authEvents = MutableSharedFlow<AuthEvent>()
    val authEvents: Flow<AuthEvent> = _authEvents.asSharedFlow()
    
    private val _resendInSec = MutableStateFlow(0)
    val resendInSec: StateFlow<Int> = _resendInSec.asStateFlow()
    
    // No-op stubs for compatibility
    suspend fun start(apiId: String, apiHash: String) {}
    suspend fun getAuth(): AuthState = AuthState.Idle
    suspend fun requestCode(phone: String) {}
    suspend fun submitCode(code: String) {}
    suspend fun submitPassword(password: String) {}
    suspend fun resendCode() {}
    suspend fun logout() {}
    fun persistTreePermission(uri: Uri) {}
    suspend fun resolveChatTitles(ids: List<Long>): List<Pair<Long, String>> = emptyList()
    suspend fun resolveChatTitle(id: Long): String = ""
}
