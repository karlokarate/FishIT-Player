package com.chris.m3usuite.data.repo

import android.content.Context
import com.chris.m3usuite.telegram.TdLibReflection
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Repository to manage Telegram authentication using reflection-based TDLib bridge.
 * Requires TDLib native/libs present at runtime. Otherwise, it remains inactive.
 */
class TelegramAuthRepository(private val context: Context, private val apiId: Int, private val apiHash: String) {
    private val _authState = MutableStateFlow(TdLibReflection.AuthState.UNKNOWN)
    val authState: StateFlow<TdLibReflection.AuthState> get() = _authState

    private var client: TdLibReflection.ClientHandle? = null

    fun isAvailable(): Boolean = TdLibReflection.available()

    /** Initialize client and send TdlibParameters to transition towards phone number step. */
    fun start(): Boolean {
        if (!isAvailable()) return false
        if (client == null) client = TdLibReflection.createClient(_authState)
        val params = TdLibReflection.buildTdlibParameters(context, apiId, apiHash) ?: return false
        client?.let { TdLibReflection.sendSetTdlibParameters(it, params) }
        return true
    }

    fun sendPhoneNumber(phone: String) {
        val c = client ?: return
        TdLibReflection.sendSetPhoneNumber(c, phone)
    }

    fun sendCode(code: String) {
        val c = client ?: return
        TdLibReflection.sendCheckCode(c, code)
    }

    fun checkDbKey() {
        val c = client ?: return
        TdLibReflection.sendCheckDatabaseEncryptionKey(c)
    }

    fun sendPassword(password: String) {
        val c = client ?: return
        TdLibReflection.sendCheckPassword(c, password)
    }
}
