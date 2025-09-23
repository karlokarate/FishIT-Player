package com.chris.m3usuite.telegram.service

import android.content.*
import android.os.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

class TelegramServiceClient(private val context: Context) {
    private var serviceMessenger: Messenger? = null
    // Queue outbound commands until the service binding is established to avoid races
    private val pending = ArrayDeque<Message>()
    private val incoming = object : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message) {
            when (msg.what) {
                TelegramTdlibService.REPLY_AUTH_STATE -> {
                    val st = msg.data.getString("state") ?: return
                    _authCallbacks.forEach { it(st) }
                    val qr = msg.data.getString("qr")
                    if (!qr.isNullOrBlank()) _qrCallbacks.forEach { it(qr) }
                }
                TelegramTdlibService.REPLY_ERROR -> {
                    val em = msg.data.getString("message") ?: return
                    _errorCallbacks.forEach { it(em) }
                }
                else -> super.handleMessage(msg)
            }
        }
    }
    private val replyMessenger = Messenger(incoming)
    private val _authCallbacks = mutableSetOf<(String)->Unit>()
    private val _errorCallbacks = mutableSetOf<(String)->Unit>()
    private val _qrCallbacks = mutableSetOf<(String)->Unit>()

    private val conn = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            serviceMessenger = Messenger(binder)
            // Flush any queued messages (in original order) now that we are connected
            flushPending()
        }
        override fun onServiceDisconnected(name: ComponentName?) {
            serviceMessenger = null
        }
    }

    fun bind() {
        val intent = Intent(context, TelegramTdlibService::class.java)
        context.bindService(intent, conn, Context.BIND_AUTO_CREATE)
    }

    fun unbind() {
        runCatching { context.unbindService(conn) }
        serviceMessenger = null
        _authCallbacks.clear()
        _errorCallbacks.clear()
        _qrCallbacks.clear()
    }

    fun authStates(): Flow<String> = callbackFlow {
        val cb: (String)->Unit = { trySend(it).isSuccess }
        _authCallbacks.add(cb)
        awaitClose { _authCallbacks.remove(cb) }
    }

    fun errors(): Flow<String> = callbackFlow {
        val cb: (String)->Unit = { trySend(it).isSuccess }
        _errorCallbacks.add(cb)
        awaitClose { _errorCallbacks.remove(cb) }
    }

    fun qrLinks(): Flow<String> = callbackFlow {
        val cb: (String)->Unit = { trySend(it).isSuccess }
        _qrCallbacks.add(cb)
        awaitClose { _qrCallbacks.remove(cb) }
    }

    private fun send(cmd: Int, bundle: Bundle.() -> Unit = {}) {
        val m = Message.obtain(null, cmd)
        m.replyTo = replyMessenger
        m.data = Bundle().apply(bundle)
        val sm = serviceMessenger
        if (sm != null) {
            sm.send(m)
        } else {
            synchronized(pending) { pending.addLast(m) }
        }
    }

    private fun flushPending() {
        val sm = serviceMessenger ?: return
        synchronized(pending) {
            while (pending.isNotEmpty()) {
                val m = pending.removeFirst()
                try { sm.send(m) } catch (_: Throwable) { /* ignore send failures */ }
            }
        }
    }

    fun start(apiId: Int, apiHash: String) = send(TelegramTdlibService.CMD_START) {
        putInt("apiId", apiId); putString("apiHash", apiHash)
    }
    fun requestQr() = send(TelegramTdlibService.CMD_REQUEST_QR)
    fun sendPhone(phone: String) = send(TelegramTdlibService.CMD_SEND_PHONE) { putString("phone", phone) }
    fun sendCode(code: String) = send(TelegramTdlibService.CMD_SEND_CODE) { putString("code", code) }
    fun sendPassword(pw: String) = send(TelegramTdlibService.CMD_SEND_PASSWORD) { putString("password", pw) }
    fun getAuth() = send(TelegramTdlibService.CMD_GET_AUTH)
    fun logout() = send(TelegramTdlibService.CMD_LOGOUT)
    fun registerFcm(token: String) = send(TelegramTdlibService.CMD_REGISTER_FCM) { putString("token", token) }
    fun processPush(payload: String) = send(TelegramTdlibService.CMD_PROCESS_PUSH) { putString("payload", payload) }
    fun setInBackground(inBg: Boolean) = send(TelegramTdlibService.CMD_SET_IN_BACKGROUND) { putBoolean("inBg", inBg) }
}
