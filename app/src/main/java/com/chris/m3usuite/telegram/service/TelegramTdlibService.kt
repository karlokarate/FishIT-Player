package com.chris.m3usuite.telegram.service

import android.app.Service
import android.content.Intent
import android.os.*
import android.util.Log
import com.chris.m3usuite.telegram.TdLibReflection
import com.chris.m3usuite.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/**
 * Dedicated TDLib service running in its own process. Minimal IPC via Messenger.
 * Clients send a Message with replyTo to register; we broadcast auth state changes.
 */
class TelegramTdlibService : Service() {
    companion object {
        const val CMD_START = 1
        const val CMD_SEND_PHONE = 2
        const val CMD_SEND_CODE = 3
        const val CMD_SEND_PASSWORD = 4
        const val CMD_REQUEST_QR = 5
        const val CMD_GET_AUTH = 6
        const val CMD_LOGOUT = 7
        const val CMD_REGISTER_FCM = 8
        const val CMD_PROCESS_PUSH = 9
        const val CMD_SET_IN_BACKGROUND = 10

        const val REPLY_AUTH_STATE = 101
        const val REPLY_ERROR = 199
    }

    private val scope = CoroutineScope(Dispatchers.Default)
    private val clients = mutableSetOf<Messenger>()
    private val authFlow = MutableStateFlow(TdLibReflection.AuthState.UNKNOWN)
    private val downloadFlow = MutableStateFlow(false)
    private var clientHandle: TdLibReflection.ClientHandle? = null

    private val handler = object : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message) {
            val data = msg.data
            when (msg.what) {
                CMD_START -> {
                    msg.replyTo?.let { clients.add(it) }
                    val apiId = data.getInt("apiId", 0)
                    val apiHash = data.getString("apiHash", "") ?: ""
                    startTdlib(apiId, apiHash)
                }
                CMD_SEND_PHONE -> data.getString("phone")?.let { sendPhone(it) }
                CMD_SEND_CODE -> data.getString("code")?.let { sendCode(it) }
                CMD_SEND_PASSWORD -> data.getString("password")?.let { sendPassword(it) }
                CMD_REQUEST_QR -> requestQr()
                CMD_GET_AUTH -> sendAuthState(msg.replyTo)
                CMD_LOGOUT -> logout()
                CMD_REGISTER_FCM -> data.getString("token")?.let { registerFcm(it) }
                CMD_PROCESS_PUSH -> data.getString("payload")?.let { processPush(it) }
                CMD_SET_IN_BACKGROUND -> setInBackground(data.getBoolean("inBg", true))
                else -> super.handleMessage(msg)
            }
        }
    }

    private val messenger = Messenger(handler)

    override fun onBind(intent: Intent?): IBinder = messenger.binder

    private fun startTdlib(apiId: Int, apiHash: String) {
        if (!TdLibReflection.available()) {
            sendErrorToAll("TDLib not available")
            return
        }
        var id = apiId
        var hash = apiHash
        if (id <= 0 || hash.isBlank()) {
            id = BuildConfig.TG_API_ID
            hash = BuildConfig.TG_API_HASH
        }
        if (id <= 0 || hash.isBlank()) {
            sendErrorToAll("API keys missing")
            return
        }
        if (clientHandle == null) {
            clientHandle = TdLibReflection.createClient(authFlow, downloadFlow)
            // Wire updates-first listener: index new/changed messages minimally into Room
            TdLibReflection.setUpdateListener { obj ->
                try {
                    val name = obj.javaClass.name
                    when {
                        // Surface TDLib errors to UI so the dialog doesn't stall silently
                        name.endsWith("TdApi\$Error") -> {
                            val code = runCatching { obj.javaClass.getDeclaredField("code").apply { isAccessible = true }.getInt(obj) }.getOrDefault(-1)
                            val message = runCatching { obj.javaClass.getDeclaredField("message").apply { isAccessible = true }.get(obj) as? String }.getOrNull()
                            val em = "TDLib Fehler ${code}: ${message ?: "Unbekannt"}"
                            Log.w("TdSvc", em)
                            sendErrorToAll(em)
                            if (code == 406 && (message?.contains("UPDATE_APP_TO_LOGIN", ignoreCase = true) == true)) {
                                clientHandle?.let { ch ->
                                    Log.i("TdSvc", "Trigger QR login due to UPDATE_APP_TO_LOGIN")
                                    TdLibReflection.sendRequestQrCodeAuthentication(ch)
                                    runCatching { TdLibReflection.sendGetAuthorizationState(ch) }
                                }
                            }
                        }
                        // UpdateAuthorizationState: respond to required steps
                        name.endsWith("TdApi\$UpdateAuthorizationState") -> {
                            val st = runCatching { obj.javaClass.getDeclaredField("authorizationState").apply { isAccessible = true }.get(obj) }.getOrNull()
                            val mapped = TdLibReflection.mapAuthorizationState(st)
                            when (mapped) {
                                TdLibReflection.AuthState.UNAUTHENTICATED -> {
                                    val p = TdLibReflection.buildTdlibParameters(applicationContext, BuildConfig.TG_API_ID, BuildConfig.TG_API_HASH)
                                    if (p != null) clientHandle?.let { TdLibReflection.sendSetTdlibParameters(it, p) }
                                }
                                TdLibReflection.AuthState.WAIT_ENCRYPTION_KEY -> {
                                    val key = com.chris.m3usuite.telegram.TelegramKeyStore.getOrCreateDatabaseKey(applicationContext)
                                    clientHandle?.let { TdLibReflection.sendCheckDatabaseEncryptionKey(it, key) }
                                }
                                else -> Unit
                            }
                        }
                        // UpdateNewMessage: carries a full message object
                        name.endsWith("TdApi\$UpdateNewMessage") -> {
                            val msg = obj.javaClass.getDeclaredField("message").apply { isAccessible = true }.get(obj) ?: return@setUpdateListener
                            indexMessage(msg)
                        }
                        // UpdateMessageContent: carries chatId/messageId and new content
                        name.endsWith("TdApi\$UpdateMessageContent") -> {
                            val chatId = obj.javaClass.getDeclaredField("chatId").apply { isAccessible = true }.getLong(obj)
                            val messageId = obj.javaClass.getDeclaredField("messageId").apply { isAccessible = true }.getLong(obj)
                            val content = obj.javaClass.getDeclaredField("newContent").apply { isAccessible = true }.get(obj)
                            indexMessageContent(chatId, messageId, content)
                        }
                        // UpdateFile: persist localPath when available
                        name.endsWith("TdApi\$UpdateFile") -> {
                            val f = obj.javaClass.getDeclaredField("file").apply { isAccessible = true }.get(obj) ?: return@setUpdateListener
                            val info = TdLibReflection.extractFileInfo(f) ?: return@setUpdateListener
                            if (!info.localPath.isNullOrBlank())
                                scope.launch(Dispatchers.IO) {
                                    kotlin.runCatching {
                                        val box = com.chris.m3usuite.data.obx.ObxStore.get(applicationContext).boxFor(com.chris.m3usuite.data.obx.ObxTelegramMessage::class.java)
                                        val row = box.query(com.chris.m3usuite.data.obx.ObxTelegramMessage_.fileId.equal(info.fileId.toLong())).build().findFirst()
                                        if (row != null) {
                                            row.localPath = info.localPath
                                            box.put(row)
                                        }
                                    }
                                }
                        }
                    }
                } catch (_: Throwable) {}
            }
            // Observe auth changes and broadcast
            scope.launch {
                authFlow.collect { st ->
                    broadcastAuthState(st)
                    // Foreground during interactive auth, stop when authenticated
                    updateForeground(st, downloadFlow.value)
                }
            }
            scope.launch {
                downloadFlow.collect { active ->
                    updateForeground(authFlow.value, active)
                }
            }
        }
        val params = TdLibReflection.buildTdlibParameters(applicationContext, id, hash)
        if (params == null) {
            sendErrorToAll("Failed to build TdlibParameters")
            return
        }
        clientHandle?.let {
            Log.i("TdSvc", "Sending SetTdlibParameters (apiId present=${id>0})")
            TdLibReflection.sendSetTdlibParameters(it, params)
            val key = com.chris.m3usuite.telegram.TelegramKeyStore.getOrCreateDatabaseKey(applicationContext)
            Log.i("TdSvc", "Sending CheckDatabaseEncryptionKey (${key.size} bytes)")
            TdLibReflection.sendCheckDatabaseEncryptionKey(it, key)
        }
    }

    private fun indexMessage(msg: Any) {
        try {
            val chatId = msg.javaClass.getDeclaredField("chatId").apply { isAccessible = true }.getLong(msg)
            val messageId = TdLibReflection.extractMessageId(msg) ?: return
            val content = runCatching { msg.javaClass.getDeclaredField("content").apply { isAccessible = true }.get(msg) }.getOrNull()
            indexMessageContent(chatId, messageId, content)
        } catch (_: Throwable) {}
    }

    private fun indexMessageContent(chatId: Long, messageId: Long, content: Any?) {
        try {
            val fileObj = TdLibReflection.findFirstFile(content) ?: return
            val info = TdLibReflection.extractFileInfo(fileObj) ?: return
            val unique = TdLibReflection.extractFileUniqueId(fileObj)
            val supports = TdLibReflection.extractSupportsStreaming(content)
            val caption = kotlin.runCatching {
                // fabricate a temporary message-like container to use helper
                TdLibReflection.extractFileName(content) ?: "Telegram $messageId"
            }.getOrNull()
            val date = System.currentTimeMillis() / 1000
            val thumbFileId = TdLibReflection.extractThumbFileId(content)
            scope.launch(Dispatchers.IO) {
                kotlin.runCatching {
                    val box = com.chris.m3usuite.data.obx.ObxStore.get(applicationContext).boxFor(com.chris.m3usuite.data.obx.ObxTelegramMessage::class.java)
                    val existing = box.query(
                        com.chris.m3usuite.data.obx.ObxTelegramMessage_.chatId.equal(chatId)
                            .and(com.chris.m3usuite.data.obx.ObxTelegramMessage_.messageId.equal(messageId))
                    ).build().findFirst()
                    val row = existing ?: com.chris.m3usuite.data.obx.ObxTelegramMessage(chatId = chatId, messageId = messageId)
                    row.fileId = info.fileId
                    row.fileUniqueId = unique
                    row.supportsStreaming = supports
                    row.caption = caption
                    row.date = date
                    row.localPath = info.localPath
                    row.thumbFileId = thumbFileId
                    box.put(row)
                }
            }
        } catch (_: Throwable) {}
    }

    private fun sendPhone(phone: String) {
        clientHandle?.let {
            try {
                val sanitized = sanitizePhone(phone)
                android.util.Log.i("TdSvc", "Submitting phone number to TDLib (masked)")
                TdLibReflection.sendSetPhoneNumber(it, sanitized)
                // Ask for updated auth state right away
                TdLibReflection.sendGetAuthorizationState(it)
            } catch (e: Throwable) {
                sendErrorToAll("Failed to send phone: ${e.message}")
            }
        } ?: sendErrorToAll("Not started")
    }

    private fun sanitizePhone(raw: String): String {
        // Remove spaces, dashes, parentheses
        var s = raw.replace(Regex("[\\s\\-()]+"), "").trim()
        // Convert leading 00 to +
        if (s.startsWith("00")) s = "+" + s.drop(2)
        return s
    }

    private fun sendCode(code: String) {
        clientHandle?.let { TdLibReflection.sendCheckCode(it, code) } ?: sendErrorToAll("Not started")
    }

    private fun sendPassword(password: String) {
        clientHandle?.let { TdLibReflection.sendCheckPassword(it, password) } ?: sendErrorToAll("Not started")
    }

    private fun requestQr() {
        clientHandle?.let { TdLibReflection.sendRequestQrCodeAuthentication(it) } ?: sendErrorToAll("Not started")
    }

    private fun logout() {
        clientHandle?.let { TdLibReflection.sendLogOut(it) }
    }

    private fun registerFcm(token: String) {
        if (clientHandle == null) {
            val apiId = BuildConfig.TG_API_ID
            val apiHash = BuildConfig.TG_API_HASH
            if (apiId > 0 && apiHash.isNotBlank()) startTdlib(apiId, apiHash) else {
                sendErrorToAll("API keys missing for FCM register")
                return
            }
        }
        clientHandle?.let { TdLibReflection.sendRegisterFcm(it, token) } ?: sendErrorToAll("Not started")
    }

    private fun processPush(payload: String) {
        if (clientHandle == null) {
            // Lazy start with BuildConfig keys to handle push without opening UI
            val apiId = BuildConfig.TG_API_ID
            val apiHash = BuildConfig.TG_API_HASH
            if (apiId > 0 && apiHash.isNotBlank()) startTdlib(apiId, apiHash) else {
                sendErrorToAll("API keys missing for push")
                return
            }
        }
        clientHandle?.let { TdLibReflection.sendProcessPushNotification(it, payload) } ?: sendErrorToAll("Not started")
    }

    private fun setInBackground(inBg: Boolean) {
        clientHandle?.let { TdLibReflection.sendSetInBackground(it, inBg) }
    }

    private fun sendAuthState(target: Messenger?) {
        target ?: return
        val m = Message.obtain(null, REPLY_AUTH_STATE)
        m.data = Bundle().apply {
            putString("state", authFlow.value.name)
            if (authFlow.value == TdLibReflection.AuthState.WAIT_OTHER_DEVICE) {
                val qr = runCatching {
                    val c = clientHandle ?: return@runCatching null
                    val obj = TdLibReflection.buildGetAuthorizationState()?.let { TdLibReflection.sendForResult(c, it, 500) }
                    TdLibReflection.extractQrLink(obj)
                }.getOrNull()
                if (!qr.isNullOrBlank()) putString("qr", qr)
            }
        }
        try { target.send(m) } catch (e: Exception) { Log.w("TdSvc", "sendAuthState failed", e) }
    }

    private fun broadcastAuthState(state: TdLibReflection.AuthState) {
        val m = Message.obtain(null, REPLY_AUTH_STATE)
        m.data = Bundle().apply {
            putString("state", state.name)
            if (state == TdLibReflection.AuthState.WAIT_OTHER_DEVICE) {
                val qr = runCatching {
                    val c = clientHandle ?: return@runCatching null
                    val obj = TdLibReflection.buildGetAuthorizationState()?.let { TdLibReflection.sendForResult(c, it, 500) }
                    TdLibReflection.extractQrLink(obj)
                }.getOrNull()
                if (!qr.isNullOrBlank()) putString("qr", qr)
            }
        }
        clients.toList().forEach { c ->
            try { c.send(m) } catch (_: Exception) { clients.remove(c) }
        }
    }

    private fun sendErrorToAll(message: String) {
        val m = Message.obtain(null, REPLY_ERROR)
        m.data = Bundle().apply { putString("message", message) }
        clients.toList().forEach { c ->
            try { c.send(m) } catch (_: Exception) { clients.remove(c) }
        }
    }

    private fun updateForeground(auth: TdLibReflection.AuthState, downloading: Boolean) {
        if (auth == TdLibReflection.AuthState.AUTHENTICATED && !downloading) stopFgSafe() else startFgSafe()
    }

    // Monitor connectivity and inform TDLib about network type changes
    override fun onCreate() {
        super.onCreate()
        val cm = getSystemService(CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
        val req = android.net.NetworkRequest.Builder().build()
        cm.registerNetworkCallback(req, object : android.net.ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: android.net.Network) { setNetType(cm) }
            override fun onLost(network: android.net.Network) { setNetType(cm) }
            override fun onCapabilitiesChanged(network: android.net.Network, caps: android.net.NetworkCapabilities) { setNetType(cm) }
        })
    }

    private fun setNetType(cm: android.net.ConnectivityManager) {
        val client = clientHandle ?: return
        val active = cm.activeNetwork
        val caps = cm.getNetworkCapabilities(active)
        val net = when {
            caps == null -> TdLibReflection.Net.NONE
            caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) -> TdLibReflection.Net.WIFI
            caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR) -> TdLibReflection.Net.MOBILE
            else -> TdLibReflection.Net.OTHER
        }
        TdLibReflection.sendSetNetworkType(client, net)
    }

    // --- Foreground helpers ---
    private fun startFgSafe() {
        try {
            val nm = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
            val chId = "tdlib"
            if (android.os.Build.VERSION.SDK_INT >= 26) {
                val ch = android.app.NotificationChannel(chId, "Telegram", android.app.NotificationManager.IMPORTANCE_LOW)
                nm.createNotificationChannel(ch)
            }
            val notif = androidx.core.app.NotificationCompat.Builder(this, chId)
                .setContentTitle("Telegram aktiv")
                .setContentText("Anmeldung/Sync läuft…")
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setOngoing(true)
                .build()
            startForeground(1001, notif)
        } catch (_: Throwable) {}
    }

    private fun stopFgSafe() {
        try { stopForeground(STOP_FOREGROUND_DETACH) } catch (_: Throwable) {}
    }
}
