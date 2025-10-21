
package com.chris.m3usuite.ui.models.telegram

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.chris.m3usuite.prefs.SettingsStore
import com.chris.m3usuite.telegram.service.TelegramServiceClient
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ProxyUiState(
    val type: String = "none",                 // "none"|"socks5"|"http"|"mtproto"
    val host: String = "",
    val port: Int = 0,
    val username: String = "",
    val password: String = "",
    val secret: String = "",
    val enabled: Boolean = false,
    val isApplying: Boolean = false
)

class TelegramProxyViewModel(
    private val app: Application,
    private val store: SettingsStore,
    private val tg: TelegramServiceClient,
    private val io: CoroutineDispatcher = Dispatchers.IO
) : ViewModel() {

    private val _state = MutableStateFlow(ProxyUiState())
    val state: StateFlow<ProxyUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                store.tgProxyType,
                store.tgProxyHost,
                store.tgProxyPort,
                store.tgProxyUsername,
                store.tgProxyPassword,
                store.tgProxySecret,
                store.tgProxyEnabled
            ) { values: Array<Any?> ->
                ProxyUiState(
                    type = (values[0] as String).ifBlank { "none" },
                    host = values[1] as String,
                    port = values[2] as Int,
                    username = values[3] as String,
                    password = values[4] as String,
                    secret = values[5] as String,
                    enabled = values[6] as Boolean
                )
            }.collectLatest { ui -> _state.value = ui }
        }
    }

    fun onChange(
        type: String? = null,
        host: String? = null,
        port: Int? = null,
        user: String? = null,
        pass: String? = null,
        secret: String? = null,
        enabled: Boolean? = null
    ) {
        _state.update {
            it.copy(
                type = type ?: it.type,
                host = host ?: it.host,
                port = port ?: it.port,
                username = user ?: it.username,
                password = pass ?: it.password,
                secret = secret ?: it.secret,
                enabled = enabled ?: it.enabled
            )
        }
    }

    fun persist() {
        val s = _state.value
        viewModelScope.launch {
            withContext(io) {
                store.setTgProxyType(s.type)
                store.setTgProxyHost(s.host)
                store.setTgProxyPort(s.port)
                store.setTgProxyUsername(s.username)
                store.setTgProxyPassword(s.password)
                store.setTgProxySecret(s.secret)
                store.setTgProxyEnabled(s.enabled)
            }
        }
    }

    fun applyNow() {
        val s = _state.value
        _state.update { it.copy(isApplying = true) }
        viewModelScope.launch {
            runCatching {
                tg.applyProxy(
                    type = s.type,
                    host = s.host,
                    port = s.port,
                    username = s.username,
                    password = s.password,
                    secret = s.secret,
                    enabled = s.enabled
                )
                persist()
            }
            _state.update { it.copy(isApplying = false) }
        }
    }

    companion object {
        fun Factory(app: Application): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                if (modelClass.isAssignableFrom(TelegramProxyViewModel::class.java)) {
                    val store = SettingsStore(app.applicationContext)
                    val service = TelegramServiceClient(app.applicationContext)
                    return TelegramProxyViewModel(app, store, service) as T
                }
                throw IllegalArgumentException("Unknown ViewModel class")
            }
        }
    }
}
