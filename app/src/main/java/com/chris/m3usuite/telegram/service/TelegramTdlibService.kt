package com.chris.m3usuite.telegram.service

import android.app.Service
import android.content.Intent
import android.os.*
import android.util.Log
import androidx.core.app.NotificationCompat
import com.chris.m3usuite.BuildConfig
import com.chris.m3usuite.telegram.TdLibReflection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
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
        const val CMD_LIST_CHATS = 11
        const val CMD_RESOLVE_CHAT_TITLES = 12
        const val CMD_PULL_CHAT_HISTORY = 13

        const val REPLY_AUTH_STATE = 101
        const val REPLY_ERROR = 199
        const val REPLY_CHAT_LIST = 201
        const val REPLY_CHAT_TITLES = 202
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val clients = mutableSetOf<Messenger>()
    private val authFlow = MutableStateFlow(TdLibReflection.AuthState.UNKNOWN)
    private val downloadFlow = MutableStateFlow(false)
    private var clientHandle: TdLibReflection.ClientHandle? = null
    private var lastApiId: Int = 0
    private var lastApiHash: String = ""

    private data class ChatSummary(
        val id: Long,
        var title: String,
        val orders: MutableMap<TdLibReflection.ChatListTag, Long>
    )

    private val chatCache = mutableMapOf<Long, ChatSummary>()
    private val authTokens = ArrayDeque<String>()

    // Connectivity tracking
    private var netCallback: android.net.ConnectivityManager.NetworkCallback? = null
    private var lastNetType: TdLibReflection.Net? = null

    // Foreground state
    @Volatile private var isForeground = false

    private val handler = object : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message) {
            val data = msg.data
            when (msg.what) {
                CMD_START -> {
                    msg.replyTo?.let { clients.add(it) }
                    val apiId = data.getInt("apiId", 0)
                    val apiHash = data.getString("apiHash", "") ?: ""
                    lastApiId = apiId
                    lastApiHash = apiHash
                    startTdlib(apiId, apiHash)
                }
                CMD_SEND_PHONE -> {
                    val phone = data.getString("phone") ?: return
                    val isCurrent = data.getBoolean("isCurrent", false)
                    val allowFlash = data.getBoolean("allowFlash", false)
                    val allowMissed = data.getBoolean("allowMissed", false)
                    val allowSmsRetriever = data.getBoolean("allowSmsRetriever", false)
                    sendPhone(phone, isCurrent, allowFlash, allowMissed, allowSmsRetriever)
                }
                CMD_SEND_CODE -> data.getString("code")?.let { sendCode(it) }
                CMD_SEND_PASSWORD -> data.getString("password")?.let { sendPassword(it) }
                CMD_REQUEST_QR -> requestQr()
                CMD_GET_AUTH -> sendAuthState(msg.replyTo)
                CMD_LOGOUT -> logout()
                CMD_REGISTER_FCM -> data.getString("token")?.let { registerFcm(it) }
                CMD_PROCESS_PUSH -> data.getString("payload")?.let { processPush(it) }
                CMD_SET_IN_BACKGROUND -> setInBackground(data.getBoolean("inBg", true))
                CMD_LIST_CHATS -> {
                    val reqId = data.getInt("reqId")
                    val list = data.getString("list") ?: "main"
                    val limit = data.getInt("limit", 200)
                    val query = data.getString("query")
                    listChats(msg.replyTo, reqId, list, limit, query)
                }
                CMD_RESOLVE_CHAT_TITLES -> {
                    val reqId = data.getInt("reqId")
                    val ids = data.getLongArray("ids") ?: longArrayOf()
                    resolveChatTitles(msg.replyTo, reqId, ids)
                }
                CMD_PULL_CHAT_HISTORY -> {
                    val chatId = data.getLong("chatId")
                    val limit = data.getInt("limit", 200)
                    scope.launch(Dispatchers.IO) { backfillChatHistory(chatId, limit) }
                }
                else -> super.handleMessage(msg)
            }
        }
    }

    private val messenger = Messenger(handler)

    override fun onBind(intent: Intent?): IBinder = messenger.binder

    override fun onCreate() {
        super.onCreate()
        val cm = getSystemService(CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
        val req = android.net.NetworkRequest.Builder().build()
        val cb = object : android.net.ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: android.net.Network) { setNetType(cm) }
            override fun onLost(network: android.net.Network) { setNetType(cm) }
            override fun onCapabilitiesChanged(network: android.net.Network, caps: android.net.NetworkCapabilities) { setNetType(cm) }
        }
        netCallback = cb
        kotlin.runCatching { cm.registerNetworkCallback(req, cb) }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Unregister connectivity callback
        kotlin.runCatching {
            val cm = getSystemService(CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
            netCallback?.let { cm.unregisterNetworkCallback(it) }
        }
        netCallback = null
        // Stop foreground if running
        stopFgSafe()
        // Cancel coroutines
        kotlin.runCatching { scope.cancel() }
        // Best-effort: clear clients
        synchronized(clients) { clients.clear() }
    }

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
                                    val effectiveId = lastApiId.takeIf { it > 0 } ?: BuildConfig.TG_API_ID
                                    val effectiveHash = lastApiHash.takeIf { it.isNotBlank() } ?: BuildConfig.TG_API_HASH
                                    val params = TdLibReflection.buildTdlibParameters(applicationContext, effectiveId, effectiveHash)
                                    if (params != null) clientHandle?.let { TdLibReflection.sendSetTdlibParameters(it, params) }
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
                            // Date unknown in this delta; preserve existing or derive if possible inside indexer
                            indexMessageContent(chatId, messageId, content, null)
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
                        // Chat updates
                        name.endsWith("TdApi\$UpdateNewChat") -> {
                            val chat = runCatching { obj.javaClass.getDeclaredField("chat").apply { isAccessible = true }.get(obj) }.getOrNull()
                            if (chat != null) updateChatFromObject(chat)
                        }
                        name.endsWith("TdApi\$UpdateChatTitle") -> {
                            val chatId = runCatching { obj.javaClass.getDeclaredField("chatId").apply { isAccessible = true }.getLong(obj) }.getOrNull()
                            val title = runCatching { obj.javaClass.getDeclaredField("title").apply { isAccessible = true }.get(obj) as? String }.getOrNull()
                            if (chatId != null) updateChatTitle(chatId, title)
                        }
                        name.endsWith("TdApi\$UpdateChatLastMessage") -> {
                            val chatId = runCatching { obj.javaClass.getDeclaredField("chatId").apply { isAccessible = true }.getLong(obj) }.getOrNull()
                            val positions = runCatching { obj.javaClass.getDeclaredField("positions").apply { isAccessible = true }.get(obj) }.getOrNull()
                            if (chatId != null && positions != null) {
                                val list: List<Any> = when (positions) {
                                    is Array<*> -> positions.filterNotNull().map { it as Any }
                                    is Iterable<*> -> positions.filterNotNull().map { it as Any }
                                    else -> emptyList()
                                }
                                if (list.isNotEmpty()) updateChatPositions(chatId, list)
                            }
                        }
                        name.endsWith("TdApi\$UpdateChatPosition") -> {
                            val chatId = runCatching { obj.javaClass.getDeclaredField("chatId").apply { isAccessible = true }.getLong(obj) }.getOrNull()
                            val position = runCatching { obj.javaClass.getDeclaredField("position").apply { isAccessible = true }.get(obj) }.getOrNull()
                            if (chatId != null) updateChatPosition(chatId, position)
                        }
                        name.endsWith("TdApi\$UpdateChatAddedToList") -> {
                            val chatId = runCatching { obj.javaClass.getDeclaredField("chatId").apply { isAccessible = true }.getLong(obj) }.getOrNull()
                            val chatList = runCatching { obj.javaClass.getDeclaredField("chatList").apply { isAccessible = true }.get(obj) }.getOrNull()
                            if (chatId != null) markChatListMembership(chatId, chatList, true)
                        }
                        name.endsWith("TdApi\$UpdateChatRemovedFromList") -> {
                            val chatId = runCatching { obj.javaClass.getDeclaredField("chatId").apply { isAccessible = true }.getLong(obj) }.getOrNull()
                            val chatList = runCatching { obj.javaClass.getDeclaredField("chatList").apply { isAccessible = true }.get(obj) }.getOrNull()
                            if (chatId != null) markChatListMembership(chatId, chatList, false)
                        }
                        // Watch for auth option tokens (for SMS retriever / anti-spam tokens)
                        name.endsWith("TdApi\$UpdateOption") -> {
                            val optionName = runCatching { obj.javaClass.getDeclaredField("name").apply { isAccessible = true }.get(obj) as? String }.getOrNull()
                            if (optionName == "authentication_token") {
                                val valueObj = runCatching { obj.javaClass.getDeclaredField("value").apply { isAccessible = true }.get(obj) }.getOrNull()
                                val token = if (valueObj?.javaClass?.name?.contains("OptionValueString") == true) {
                                    runCatching {
                                        valueObj.javaClass.getDeclaredField("value").apply { isAccessible = true }.get(valueObj) as? String
                                    }.getOrNull()
                                } else null
                                synchronized(authTokens) {
                                    if (token.isNullOrBlank()) {
                                        authTokens.clear()
                                    } else {
                                        authTokens.remove(token)
                                        authTokens.addFirst(token)
                                        while (authTokens.size > 20) authTokens.removeLast()
                                    }
                                }
                            }
                        }
                    }
                } catch (_: Throwable) { }
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
            Log.i("TdSvc", "Sending SetTdlibParameters (apiId present=${id > 0})")
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
            val messageDate = runCatching { TdLibReflection.extractMessageDate(msg) }.getOrNull()
            indexMessageContent(chatId, messageId, content, messageDate)
        } catch (_: Throwable) {}
    }

    private fun indexMessageContent(chatId: Long, messageId: Long, content: Any?, messageDate: Long? = null) {
        try {
            val safeContent = content ?: return
            val fileObj = TdLibReflection.findFirstFile(safeContent) ?: return
            val info = TdLibReflection.extractFileInfo(fileObj) ?: return
            val unique = TdLibReflection.extractFileUniqueId(fileObj)
            val supports = TdLibReflection.extractSupportsStreaming(safeContent)
            val caption = kotlin.runCatching {
                TdLibReflection.extractCaptionOrText(safeContent)
                    ?: TdLibReflection.extractFileName(safeContent)
                    ?: "Telegram $messageId"
            }.getOrDefault("Telegram $messageId")
            val duration = TdLibReflection.extractDurationSecs(safeContent)
            val mime = TdLibReflection.extractMimeType(safeContent)
            val dims = TdLibReflection.extractVideoDimensions(safeContent)
            val parsed = com.chris.m3usuite.telegram.TelegramHeuristics.parse(caption)
            val thumbFileId = TdLibReflection.extractThumbFileId(safeContent)

            scope.launch(Dispatchers.IO) {
                kotlin.runCatching {
                    val store = com.chris.m3usuite.data.obx.ObxStore.get(applicationContext)
                    val box = store.boxFor(com.chris.m3usuite.data.obx.ObxTelegramMessage::class.java)
                    val existing = box.query(
                        com.chris.m3usuite.data.obx.ObxTelegramMessage_.chatId.equal(chatId)
                            .and(com.chris.m3usuite.data.obx.ObxTelegramMessage_.messageId.equal(messageId))
                    ).build().findFirst()

                    // Resolve best-effort message date:
                    // 1) explicit argument (UpdateNewMessage/history)
                    // 2) attempt from content if possible
                    // 3) keep existing row date if present
                    // 4) fallback to current time in seconds
                    val derivedFromContent = runCatching { TdLibReflection.extractMessageDate(safeContent) }.getOrNull()
                    val resolvedDate = messageDate
                        ?: derivedFromContent
                        ?: existing?.date
                        ?: (System.currentTimeMillis() / 1000)

                    val row = existing ?: com.chris.m3usuite.data.obx.ObxTelegramMessage(chatId = chatId, messageId = messageId)
                    row.fileId = info.fileId
                    row.fileUniqueId = unique
                    row.supportsStreaming = supports
                    row.caption = caption
                    row.captionLower = caption.lowercase()
                    row.date = resolvedDate
                    row.localPath = info.localPath
                    row.thumbFileId = thumbFileId
                    row.durationSecs = duration
                    row.mimeType = mime
                    row.sizeBytes = info.expectedSize.takeIf { it > 0 }
                    row.width = dims?.first
                    row.height = dims?.second
                    row.language = parsed.language
                    box.put(row)
                }
            }
        } catch (_: Throwable) {}
    }

    private fun sendPhone(phone: String, isCurrentDevice: Boolean, allowFlash: Boolean, allowMissed: Boolean, allowSmsRetriever: Boolean) {
        clientHandle?.let {
            try {
                val sanitized = sanitizePhone(phone)
                Log.i("TdSvc", "Submitting phone number to TDLib (masked)")
                val tokens = synchronized(authTokens) { authTokens.toList() }
                val settings = TdLibReflection.PhoneAuthSettings(
                    allowFlashCall = allowFlash,
                    allowMissedCall = allowMissed,
                    isCurrentPhoneNumber = isCurrentDevice,
                    hasUnknownPhoneNumber = false,
                    allowSmsRetrieverApi = allowSmsRetriever,
                    authenticationTokens = tokens
                )
                TdLibReflection.sendSetPhoneNumber(it, sanitized, settings)
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

    private fun listChats(target: Messenger?, reqId: Int, list: String, limit: Int, query: String?) {
        target ?: return
        if (!ensureStartedOrError()) {
            val failure = Message.obtain(null, REPLY_CHAT_LIST)
            failure.data = Bundle().apply {
                putInt("reqId", reqId)
                putString("list", list)
                putLongArray("ids", longArrayOf())
                putStringArray("titles", emptyArray())
            }
            runCatching { target.send(failure) }
            return
        }
        val ch = clientHandle ?: return
        val tag = TdLibReflection.ChatListTag.fromString(list.lowercase())
        val listObj = when (tag) {
            TdLibReflection.ChatListTag.Archive -> TdLibReflection.buildChatListArchive()
            TdLibReflection.ChatListTag.Main -> TdLibReflection.buildChatListMain()
            is TdLibReflection.ChatListTag.Folder -> TdLibReflection.buildChatListFolder(tag.id)
            null -> null
        }
        listObj?.let { load -> TdLibReflection.buildLoadChats(load, limit)?.let { TdLibReflection.sendForResult(ch, it, 1500) } }
        var results = snapshotChats(tag?.toString() ?: list, query, limit)
        if (results.isEmpty() && listObj != null) {
            val fallback = TdLibReflection.buildGetChats(listObj, limit)
            val response = fallback?.let { TdLibReflection.sendForResult(ch, it, 2000) }
            val ids = response?.let { TdLibReflection.extractChatsIds(it) } ?: longArrayOf()
            ids.forEach { id ->
                val chatObj = TdLibReflection.buildGetChat(id)?.let { TdLibReflection.sendForResult(ch, it, 1500) }
                if (chatObj != null) updateChatFromObject(chatObj)
            }
            results = snapshotChats(tag?.toString() ?: list, query, limit)
        }
        val outIds = results.map { it.first }
        val outTitles = results.map { it.second }
        val message = Message.obtain(null, REPLY_CHAT_LIST)
        message.data = Bundle().apply {
            putInt("reqId", reqId)
            putString("list", list)
            putLongArray("ids", outIds.toLongArray())
            putStringArray("titles", outTitles.toTypedArray())
        }
        runCatching { target.send(message) }
    }

    private fun resolveChatTitles(target: Messenger?, reqId: Int, ids: LongArray) {
        target ?: return
        if (!ensureStartedOrError()) {
            val failure = Message.obtain(null, REPLY_CHAT_TITLES)
            failure.data = Bundle().apply {
                putInt("reqId", reqId)
                putLongArray("ids", ids)
                putStringArray("titles", emptyArray())
            }
            runCatching { target.send(failure) }
            return
        }
        val ch = clientHandle ?: return
        val outTitles = ArrayList<String>(ids.size)
        ids.forEach { id ->
            val fromCache = synchronized(chatCache) { chatCache[id]?.title }
            if (fromCache != null) {
                outTitles += fromCache
            } else {
                val chatObj = TdLibReflection.buildGetChat(id)?.let { TdLibReflection.sendForResult(ch, it, 1000) }
                if (chatObj != null) {
                    updateChatFromObject(chatObj)
                }
                val title = chatObj?.let { TdLibReflection.extractChatTitle(it) } ?: id.toString()
                outTitles += title
            }
        }
        val message = Message.obtain(null, REPLY_CHAT_TITLES)
        message.data = Bundle().apply {
            putInt("reqId", reqId)
            putLongArray("ids", ids)
            putStringArray("titles", outTitles.toTypedArray())
        }
        runCatching { target.send(message) }
    }

    private fun backfillChatHistory(chatId: Long, limit: Int) {
        if (chatId == 0L || limit <= 0) return
        if (!ensureStartedOrError()) return
        val ch = clientHandle ?: return
        val function = TdLibReflection.buildGetChatHistory(chatId, 0L, 0, limit, false) ?: return
        val result = TdLibReflection.sendForResult(ch, function, 7000) ?: return
        val messages = TdLibReflection.extractMessagesArray(result)
        messages.forEach { messageObj ->
            val messageId = TdLibReflection.extractMessageId(messageObj) ?: return@forEach
            val content = runCatching {
                messageObj.javaClass.getDeclaredField("content").apply { isAccessible = true }.get(messageObj)
            }.getOrNull()
            val messageDate = runCatching { TdLibReflection.extractMessageDate(messageObj) }.getOrNull()
            indexMessageContent(chatId, messageId, content, messageDate)
        }
    }

    private fun ensureStartedOrError(): Boolean {
        if (clientHandle != null) return true
        val apiId = lastApiId.takeIf { it > 0 } ?: BuildConfig.TG_API_ID
        val apiHash = lastApiHash.takeIf { it.isNotBlank() } ?: BuildConfig.TG_API_HASH
        return if (apiId > 0 && apiHash.isNotBlank()) {
            startTdlib(apiId, apiHash)
            clientHandle != null
        } else {
            sendErrorToAll("API keys missing")
            false
        }
    }

    private fun updateChatFromObject(chatObj: Any) {
        val id = TdLibReflection.extractChatId(chatObj) ?: return
        val title = TdLibReflection.extractChatTitle(chatObj) ?: id.toString()
        val positions = TdLibReflection.extractChatPositions(chatObj)
        synchronized(chatCache) {
            val summary = chatCache.getOrPut(id) { ChatSummary(id, title, mutableMapOf()) }
            summary.title = title
            if (positions.isNotEmpty()) applyPositions(summary, positions)
        }
    }

    private fun updateChatTitle(chatId: Long, title: String?) {
        if (title.isNullOrBlank()) return
        synchronized(chatCache) {
            val summary = chatCache[chatId]
            if (summary != null) {
                summary.title = title
            } else {
                chatCache[chatId] = ChatSummary(chatId, title, mutableMapOf())
            }
        }
    }

    private fun updateChatPositions(chatId: Long, positions: List<Any>) {
        synchronized(chatCache) {
            val summary = chatCache.getOrPut(chatId) { ChatSummary(chatId, chatId.toString(), mutableMapOf()) }
            applyPositions(summary, positions)
        }
    }

    private fun applyPositions(summary: ChatSummary, positions: List<Any>) {
        positions.forEach { pos ->
            val tag = TdLibReflection.classifyChatList(TdLibReflection.extractChatPositionList(pos)) ?: return@forEach
            val order = TdLibReflection.extractChatPositionOrder(pos) ?: 0L
            if (order == 0L) summary.orders.remove(tag) else summary.orders[tag] = order
        }
    }

    private fun updateChatPosition(chatId: Long, positionObj: Any?) {
        val tag = TdLibReflection.classifyChatList(TdLibReflection.extractChatPositionList(positionObj)) ?: return
        val order = TdLibReflection.extractChatPositionOrder(positionObj) ?: 0L
        synchronized(chatCache) {
            val summary = chatCache.getOrPut(chatId) { ChatSummary(chatId, chatId.toString(), mutableMapOf()) }
            if (order == 0L) summary.orders.remove(tag) else summary.orders[tag] = order
        }
    }

    private fun markChatListMembership(chatId: Long, chatListObj: Any?, present: Boolean) {
        val tag = TdLibReflection.classifyChatList(chatListObj) ?: return
        synchronized(chatCache) {
            val summary = chatCache.getOrPut(chatId) { ChatSummary(chatId, chatId.toString(), mutableMapOf()) }
            if (present) {
                if (!summary.orders.containsKey(tag)) summary.orders[tag] = 0L
            } else {
                summary.orders.remove(tag)
            }
        }
    }

    private fun snapshotChats(list: String, query: String?, limit: Int): List<Pair<Long, String>> {
        val targetTag = TdLibReflection.ChatListTag.fromString(list.lowercase())
        val all = synchronized(chatCache) { chatCache.values.map { it.copy(orders = it.orders.toMutableMap()) } }
        val filtered = all.filter { summary ->
            val matchesList = when {
                targetTag == null -> summary.orders.isNotEmpty()
                else -> summary.orders.containsKey(targetTag)
            }
            if (!matchesList) return@filter false
            if (query.isNullOrBlank()) return@filter true
            val q = query.lowercase()
            summary.title.lowercase().contains(q) || summary.id.toString().contains(q)
        }
        val sorter: (ChatSummary) -> Long = { summary ->
            val tag = targetTag
            if (tag != null) summary.orders[tag] ?: Long.MIN_VALUE else summary.orders.values.maxOrNull() ?: Long.MIN_VALUE
        }
        return filtered.sortedByDescending(sorter).take(limit).map { it.id to it.title }
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
        if (auth == TdLibReflection.AuthState.AUTHENTICATED && !downloading) {
            stopFgSafe()
        } else {
            startFgSafe()
        }
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
        if (lastNetType != net) {
            lastNetType = net
            TdLibReflection.sendSetNetworkType(client, net)
        }
    }

    // --- Foreground helpers ---
    private fun startFgSafe() {
        if (isForeground) return
        try {
            val nm = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
            val chId = "tdlib"
            if (Build.VERSION.SDK_INT >= 26) {
                val ch = android.app.NotificationChannel(chId, "Telegram", android.app.NotificationManager.IMPORTANCE_LOW)
                nm.createNotificationChannel(ch)
            }
            val notif = NotificationCompat.Builder(this, chId)
                .setContentTitle("Telegram aktiv")
                .setContentText("Anmeldung/Sync läuft…")
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setOngoing(true)
                .build()
            startForeground(1001, notif)
            isForeground = true
        } catch (_: Throwable) {}
    }

    private fun stopFgSafe() {
        if (!isForeground) return
        try {
            if (Build.VERSION.SDK_INT >= 24) {
                stopForeground(Service.STOP_FOREGROUND_DETACH)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(true)
            }
        } catch (_: Throwable) {} finally {
            isForeground = false
        }
    }
}
