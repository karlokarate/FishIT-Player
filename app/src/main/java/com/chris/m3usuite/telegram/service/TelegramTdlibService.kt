package com.chris.m3usuite.telegram.service

import android.app.Service
import android.content.Intent
import android.os.*
import android.util.Log
import androidx.core.app.NotificationCompat
import com.chris.m3usuite.BuildConfig
import com.chris.m3usuite.telegram.PhoneNumberSanitizer
import com.chris.m3usuite.telegram.TdLibReflection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first

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
        const val CMD_LIST_FOLDERS = 14
        const val CMD_SET_IPV6 = 15
        const val CMD_SET_ONLINE = 16
        const val CMD_SET_LOG_VERBOSITY = 17
        const val CMD_SET_STORAGE_OPTIMIZER = 18
        const val CMD_OPTIMIZE_STORAGE = 19
        const val CMD_SET_IGNORE_FILE_NAMES = 20
        const val CMD_APPLY_PROXY = 21
        const val CMD_DISABLE_PROXY = 22
        const val CMD_SET_AUTO_DOWNLOAD = 23
        const val CMD_APPLY_ALL_SETTINGS = 24

        const val REPLY_AUTH_STATE = 101
        const val REPLY_ERROR = 199
        const val REPLY_CHAT_LIST = 201
        const val REPLY_CHAT_TITLES = 202
        const val REPLY_FOLDERS = 203
        const val REPLY_PULL_DONE = 204
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val clients = mutableSetOf<Messenger>()
    private val authFlow = MutableStateFlow(TdLibReflection.AuthState.UNKNOWN)
    private val downloadFlow = MutableStateFlow(false)
    private var clientHandle: TdLibReflection.ClientHandle? = null
    private var updateSubscription: AutoCloseable? = null
    private var lastApiId: Int = 0
    private var lastApiHash: String = ""
    private val authLock = Any()
    private val settingsStore by lazy { com.chris.m3usuite.prefs.SettingsStore(applicationContext) }
    @Volatile private var pendingApplyOptions: Boolean = false
    private var autoDownloadDefaults: TdLibReflection.AutoDownloadPresets? = null
    private data class PendingPhone(
        val phone: String,
        val isCurrent: Boolean,
        val allowFlash: Boolean,
        val allowMissed: Boolean,
        val allowSmsRetriever: Boolean
    )
    @Volatile private var pendingPhone: PendingPhone? = null
    @Volatile private var pendingCode: String? = null
    @Volatile private var pendingPassword: String? = null
    @Volatile private var didResendParamsAfterInitError: Boolean = false

    private data class ChatSummary(
        val id: Long,
        var title: String,
        val orders: MutableMap<TdLibReflection.ChatListTag, Long>
    )

    private val chatCache = mutableMapOf<Long, ChatSummary>()
    private val authTokens = ArrayDeque<String>()

    private data class BackfillStats(
        val processed: Int,
        val newVod: Int,
        val newSeriesEpisodes: Int,
        val newSeriesIds: LongArray
    )

    private data class IndexedMessageOutcome(
        val isNew: Boolean,
        val kind: IndexedKind,
        val seriesId: Long? = null
    )

    private enum class IndexedKind { UNKNOWN, VOD, SERIES }

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
                    queuePhone(phone, isCurrent, allowFlash, allowMissed, allowSmsRetriever)
                    maybeDispatchAuthAction(authFlow.value)
                }
                CMD_SEND_CODE -> data.getString("code")?.let {
                    queueCode(it)
                    maybeDispatchAuthAction(authFlow.value)
                }
                CMD_SEND_PASSWORD -> data.getString("password")?.let {
                    queuePassword(it)
                    maybeDispatchAuthAction(authFlow.value)
                }
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
                    val fetchAll = data.getBoolean("fetchAll", false)
                    val target = msg.replyTo
                    val reqId = data.getInt("reqId", -1)
                    scope.launch(Dispatchers.IO) {
                        val stats = backfillChatHistory(chatId, limit, fetchAll)
                        // Acknowledge completion to caller with processed count
                        if (target != null) {
                            val m = Message.obtain(null, REPLY_PULL_DONE)
                            m.data = Bundle().apply {
                                putInt("reqId", reqId)
                                putLong("chatId", chatId)
                                putInt("count", stats.processed)
                                putInt("vodNew", stats.newVod)
                                putInt("seriesEpisodeNew", stats.newSeriesEpisodes)
                                putLongArray("seriesIds", stats.newSeriesIds)
                            }
                            runCatching { target.send(m) }
                        }
                    }
                }
                CMD_LIST_FOLDERS -> {
                    listFolders(msg.replyTo)
                }
                CMD_SET_IPV6 -> {
                    val enabled = data.getBoolean("enabled", true)
                    val client = clientHandle
                    if (client != null) {
                        TdLibReflection.sendSetOptionBoolean(client, "prefer_ipv6", enabled)
                    } else {
                        pendingApplyOptions = true
                    }
                }
                CMD_SET_ONLINE -> {
                    val enabled = data.getBoolean("enabled", true)
                    val client = clientHandle
                    if (client != null) {
                        TdLibReflection.sendSetOptionBoolean(client, "online", enabled)
                    } else {
                        pendingApplyOptions = true
                    }
                }
                CMD_SET_LOG_VERBOSITY -> {
                    val level = data.getInt("level", 1)
                    val client = clientHandle
                    TdLibReflection.setLogVerbosityLevel(level)
                    if (client != null) {
                        TdLibReflection.sendSetLogVerbosityLevel(client, level)
                    } else {
                        pendingApplyOptions = true
                    }
                }
                CMD_SET_STORAGE_OPTIMIZER -> {
                    val enabled = data.getBoolean("enabled", true)
                    val client = clientHandle
                    if (client != null) {
                        TdLibReflection.sendSetOptionBoolean(client, "use_storage_optimizer", enabled)
                    } else {
                        pendingApplyOptions = true
                    }
                }
                CMD_OPTIMIZE_STORAGE -> {
                    val client = clientHandle
                    if (client != null) {
                        scope.launch(Dispatchers.IO) { TdLibReflection.sendOptimizeStorage(client) }
                    }
                }
                CMD_SET_IGNORE_FILE_NAMES -> {
                    val enabled = data.getBoolean("enabled", false)
                    val client = clientHandle
                    if (client != null) {
                        TdLibReflection.sendSetOptionBoolean(client, "ignore_file_names", enabled)
                    } else {
                        pendingApplyOptions = true
                    }
                }
                CMD_APPLY_PROXY -> {
                    val client = clientHandle
                    if (client != null) {
                        val kind = parseProxyKind(data.getString("type"))
                        val host = data.getString("host") ?: ""
                        val port = data.getInt("port", 0)
                        val username = data.getString("username") ?: ""
                        val password = data.getString("password") ?: ""
                        val secret = data.getString("secret") ?: ""
                        val enabled = data.getBoolean("enabled", false)
                        scope.launch(Dispatchers.IO) {
                            TdLibReflection.configureProxy(
                                client,
                                TdLibReflection.ProxyConfig(kind, host, port, username, password, secret, enabled)
                            )
                        }
                    } else {
                        pendingApplyOptions = true
                    }
                }
                CMD_DISABLE_PROXY -> {
                    clientHandle?.let { handle -> scope.launch(Dispatchers.IO) { TdLibReflection.disableProxy(handle) } }
                }
                CMD_SET_AUTO_DOWNLOAD -> {
                    val client = clientHandle
                    if (client != null) {
                        val type = data.getString("type") ?: "wifi"
                        val enabled = data.getBoolean("enabled", true)
                        val preloadLarge = data.getBoolean("preloadLarge", false)
                        val preloadNext = data.getBoolean("preloadNext", false)
                        val preloadStories = data.getBoolean("preloadStories", false)
                        val lessDataCalls = data.getBoolean("lessDataCalls", false)
                        scope.launch(Dispatchers.IO) {
                            applyAutoDownloadOverride(
                                type,
                                enabled,
                                preloadLarge,
                                preloadNext,
                                preloadStories,
                                lessDataCalls
                            )
                        }
                    } else {
                        pendingApplyOptions = true
                    }
                }
                CMD_APPLY_ALL_SETTINGS -> {
                    scope.launch(Dispatchers.IO) { applyRuntimeOptionsFromSettings() }
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
        updateSubscription?.close()
        updateSubscription = null
    }

    private fun startTdlib(apiId: Int, apiHash: String) {
        if (!TdLibReflection.available()) {
            sendErrorToAll("TDLib not available")
            return
        }
        // Reset one-shot guards and clear any stale pending inputs on fresh start
        didResendParamsAfterInitError = false
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
            updateSubscription?.close()
            updateSubscription = TdLibReflection.addUpdateListener listener@{ obj ->
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
                                Log.i(
                                    "TdSvc",
                                    "TDLib requested an app update for login – keeping phone/code flow active until the user opts into QR."
                                )
                            } else if (code == 400 && (message?.contains("Initialization parameters are needed", ignoreCase = true) == true)) {
                                // TDLib expects parameters again. Resend once and wait for proper state.
                                if (!didResendParamsAfterInitError) {
                                    didResendParamsAfterInitError = true
                                    val effectiveId = lastApiId.takeIf { it > 0 } ?: BuildConfig.TG_API_ID
                                    val effectiveHash = lastApiHash.takeIf { it.isNotBlank() } ?: BuildConfig.TG_API_HASH
                                    val params = TdLibReflection.buildTdlibParameters(applicationContext, effectiveId, effectiveHash)
                                    if (params != null) clientHandle?.let { ch ->
                                        TdLibReflection.sendSetTdlibParameters(ch, params)
                                        val key = com.chris.m3usuite.telegram.TelegramKeyStore.getOrCreateDatabaseKey(applicationContext)
                                        TdLibReflection.sendCheckDatabaseEncryptionKey(ch, key)
                                        scope.launch(Dispatchers.IO) { applyRuntimeOptionsFromSettings() }
                                    }
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
                                    if (params != null) clientHandle?.let { ch ->
                                        TdLibReflection.sendSetTdlibParameters(ch, params)
                                        scope.launch(Dispatchers.IO) { applyRuntimeOptionsFromSettings() }
                                    }
                                }
                                TdLibReflection.AuthState.WAIT_ENCRYPTION_KEY -> {
                                    val key = com.chris.m3usuite.telegram.TelegramKeyStore.getOrCreateDatabaseKey(applicationContext)
                                    clientHandle?.let { ch ->
                                        TdLibReflection.sendCheckDatabaseEncryptionKey(ch, key)
                                        scope.launch(Dispatchers.IO) { applyRuntimeOptionsFromSettings() }
                                    }
                                }
                                else -> Unit
                            }
                            // Try to dispatch any queued auth action for this state
                            maybeDispatchAuthAction(mapped)
                        }
                        // UpdateNewMessage: carries a full message object
                        name.endsWith("TdApi\$UpdateNewMessage") -> {
                            val msg = obj.javaClass.getDeclaredField("message").apply { isAccessible = true }.get(obj) ?: return@listener
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
                            val f = obj.javaClass.getDeclaredField("file").apply { isAccessible = true }.get(obj) ?: return@listener
                            val info = TdLibReflection.extractFileInfo(f) ?: return@listener
                            if (!info.localPath.isNullOrBlank())
                                scope.launch(Dispatchers.IO) {
                                    kotlin.runCatching {
                                        val store = com.chris.m3usuite.data.obx.ObxStore.get(applicationContext)
                                        val tBox = store.boxFor(com.chris.m3usuite.data.obx.ObxTelegramMessage::class.java)
                                        // Update main media local path
                                        val rowMain = tBox.query(com.chris.m3usuite.data.obx.ObxTelegramMessage_.fileId.equal(info.fileId.toLong())).build().findFirst()
                                        if (rowMain != null) { rowMain.localPath = info.localPath; tBox.put(rowMain) }
                                        // Update thumbnail local path if this UpdateFile refers to the thumb and cascade to episodes' poster
                                        val rowThumb = tBox.query(com.chris.m3usuite.data.obx.ObxTelegramMessage_.thumbFileId.equal(info.fileId)).build().findFirst()
                                        if (rowThumb != null) {
                                            rowThumb.thumbLocalPath = info.localPath; tBox.put(rowThumb)
                                            kotlin.runCatching {
                                                val epBox = store.boxFor(com.chris.m3usuite.data.obx.ObxEpisode::class.java)
                                                val eps = epBox.query(
                                                    com.chris.m3usuite.data.obx.ObxEpisode_.tgChatId.equal(rowThumb.chatId)
                                                        .and(com.chris.m3usuite.data.obx.ObxEpisode_.tgMessageId.equal(rowThumb.messageId))
                                                ).build().find()
                                                if (eps.isNotEmpty()) {
                                                    var changed = false
                                                    eps.forEach { if (it.imageUrl.isNullOrBlank()) { it.imageUrl = info.localPath; changed = true } }
                                                    if (changed) epBox.put(eps)
                                                }
                                            }
                                            // Also enrich series poster if missing: derive seriesId from caption
                                            kotlin.runCatching {
                                                val cap = rowThumb.caption
                                                if (!cap.isNullOrBlank()) {
                                                    val parsed = com.chris.m3usuite.telegram.TelegramHeuristics.parse(cap)
                                                    val seriesTitle = parsed.seriesTitle
                                                    if (!seriesTitle.isNullOrBlank()) {
                                                        val sid = seriesIdFor(normalizeSeriesKey(seriesTitle))
                                                        val sBox = store.boxFor(com.chris.m3usuite.data.obx.ObxSeries::class.java)
                                                        val sRow = sBox.query(com.chris.m3usuite.data.obx.ObxSeries_.seriesId.equal(sid.toLong())).build().findFirst()
                                                        if (sRow != null) {
                                                            val current = sRow.imagesJson
                                                            if (current.isNullOrBlank()) {
                                                                val arr = org.json.JSONArray().apply { put(info.localPath) }
                                                                sRow.imagesJson = arr.toString(); sBox.put(sRow)
                                                            } else {
                                                                val arr = org.json.JSONArray(current)
                                                                var exists = false
                                                                for (i in 0 until arr.length()) if (arr.optString(i) == info.localPath) { exists = true; break }
                                                                if (!exists) {
                                                                    val out = org.json.JSONArray()
                                                                    out.put(info.localPath)
                                                                    var added = 1
                                                                    for (i in 0 until arr.length()) {
                                                                        val v = arr.optString(i)
                                                                        if (v != info.localPath) { out.put(v); added++; if (added >= 3) break }
                                                                    }
                                                                    sRow.imagesJson = out.toString(); sBox.put(sRow)
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            }
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
                    // Attempt to send any queued auth step when TDLib asks for it
                    maybeDispatchAuthAction(st)
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
        clientHandle?.let { ch ->
            Log.i("TdSvc", "Sending SetTdlibParameters (apiId present=${id > 0})")
            TdLibReflection.sendSetTdlibParameters(ch, params)
            val key = com.chris.m3usuite.telegram.TelegramKeyStore.getOrCreateDatabaseKey(applicationContext)
            Log.i("TdSvc", "Sending CheckDatabaseEncryptionKey (${key.size} bytes)")
            TdLibReflection.sendCheckDatabaseEncryptionKey(ch, key)
            scope.launch(Dispatchers.IO) { applyRuntimeOptionsFromSettings() }
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

    private fun indexMessageContent(
        chatId: Long,
        messageId: Long,
        content: Any?,
        messageDate: Long? = null,
        asyncWrite: Boolean = true
    ): IndexedMessageOutcome? {
        try {
            val safeContent = content ?: return null
            val fileObj = TdLibReflection.findPrimaryFile(safeContent) ?: return null
            val info = TdLibReflection.extractFileInfo(fileObj) ?: return null
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
            val fileName = TdLibReflection.extractFileName(safeContent)

            val writer: suspend () -> IndexedMessageOutcome = {
                android.util.Log.i(
                    "TdSvc",
                    "prepare upsert tg_message chatId=${chatId} messageId=${messageId} fileId=${info.fileId} mime=${mime ?: ""}"
                )
                kotlin.runCatching {
                    val store = com.chris.m3usuite.data.obx.ObxStore.get(applicationContext)
                    val box = store.boxFor(com.chris.m3usuite.data.obx.ObxTelegramMessage::class.java)
                    val existing = box.query(
                        com.chris.m3usuite.data.obx.ObxTelegramMessage_.chatId.equal(chatId)
                            .and(com.chris.m3usuite.data.obx.ObxTelegramMessage_.messageId.equal(messageId))
                    ).build().findFirst()
                    val isNew = existing == null

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
                    row.fileName = fileName
                    box.put(row)
                    android.util.Log.i("TdSvc", "OBX upsert tg_message chatId=$chatId messageId=$messageId fileId=${info.fileId} thumbFileId=$thumbFileId local=${info.localPath != null}")
                    // Kick off thumbnail download asynchronously to populate poster
                    requestThumbnailDownloadIfNeeded(thumbFileId)
                    val kind = when {
                        parsed.isSeries && !parsed.seriesTitle.isNullOrBlank() && parsed.season != null && parsed.episode != null -> IndexedKind.SERIES
                        parsed.isSeries -> IndexedKind.UNKNOWN
                        else -> IndexedKind.VOD
                    }
                    val seriesId = if (kind == IndexedKind.SERIES) {
                        val normalized = normalizeSeriesKey(parsed.seriesTitle!!)
                        seriesIdFor(normalized).toLong()
                    } else null
                    IndexedMessageOutcome(isNew = isNew, kind = kind, seriesId = seriesId)
                }.onFailure { e ->
                    android.util.Log.w("TdSvc", "OBX upsert failed chatId=${chatId} messageId=${messageId}: ${e.message}")
                    throw e
                }.getOrThrow()
            }
            return if (asyncWrite) {
                scope.launch(Dispatchers.IO) { kotlin.runCatching { writer() } }
                null
            } else {
                kotlinx.coroutines.runBlocking(Dispatchers.IO) { runCatching { writer() }.getOrNull() }
            }
        } catch (_: Throwable) {
            return null
        }
    }

    private fun requestThumbnailDownloadIfNeeded(thumbFileId: Int?) {
        if (thumbFileId == null || thumbFileId <= 0) return
        val ch = clientHandle ?: return
        runCatching {
            TdLibReflection.buildDownloadFile(thumbFileId, /* priority */ 8, /* offset */ 0, /* limit */ 0, /* synchronous */ false)
                ?.let { fn ->
                    TdLibReflection.sendForResult(
                        ch,
                        fn,
                        timeoutMs = 200,
                        retries = 1,
                        traceTag = "ThumbDownload[$thumbFileId]"
                    )
                }
        }
    }

    private fun normalizeSeriesKey(raw: String): String {
        val s = raw.trim().lowercase()
        val nfd = java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD)
        val noMarks = nfd.replace(Regex("\\p{M}+"), "")
        val replaced = noMarks.replace(Regex("[^a-z0-9]+"), " ")
        return replaced.trim().replace(Regex("\\s+"), " ")
    }

    private fun seriesIdFor(normTitle: String): Int {
        val sha = java.security.MessageDigest.getInstance("SHA-1").digest(normTitle.toByteArray())
        var h = 0
        for (i in 0 until 4) { h = (h shl 8) or (sha[i].toInt() and 0xFF) }
        if (h < 0) h = -h
        val base = 1_500_000_000
        val span = 400_000_000
        return base + (h % span)
    }

    // --- Auth queueing/conformance helpers ---
    private fun queuePhone(phone: String, isCurrentDevice: Boolean, allowFlash: Boolean, allowMissed: Boolean, allowSmsRetriever: Boolean) {
        synchronized(authLock) {
            pendingPhone = PendingPhone(phone, isCurrentDevice, allowFlash, allowMissed, allowSmsRetriever)
        }
        Log.i("TdSvc", "Queued phone number for TDLib (will send when WAIT_FOR_NUMBER)")
    }

    private fun queueCode(code: String) {
        synchronized(authLock) { pendingCode = code }
        Log.i("TdSvc", "Queued auth code (will send when WAIT_FOR_CODE)")
    }

    private fun queuePassword(password: String) {
        synchronized(authLock) { pendingPassword = password }
        Log.i("TdSvc", "Queued password (will send when WAIT_FOR_PASSWORD)")
    }

    private fun maybeDispatchAuthAction(state: TdLibReflection.AuthState) {
        val ch = clientHandle ?: return
        when (state) {
            TdLibReflection.AuthState.WAIT_FOR_NUMBER -> {
                val ph = synchronized(authLock) { val v = pendingPhone; pendingPhone = null; v }
                if (ph != null) {
                    sendPhone(ph.phone, ph.isCurrent, ph.allowFlash, ph.allowMissed, ph.allowSmsRetriever)
                    // After sending phone, ask for next state
                    runCatching { TdLibReflection.sendGetAuthorizationState(ch) }
                }
            }
            TdLibReflection.AuthState.WAIT_FOR_CODE -> {
                val code = synchronized(authLock) { val v = pendingCode; pendingCode = null; v }
                if (!code.isNullOrBlank()) {
                    sendCode(code)
                    runCatching { TdLibReflection.sendGetAuthorizationState(ch) }
                }
            }
            TdLibReflection.AuthState.WAIT_FOR_PASSWORD -> {
                val pw = synchronized(authLock) { val v = pendingPassword; pendingPassword = null; v }
                if (!pw.isNullOrBlank()) {
                    sendPassword(pw)
                    runCatching { TdLibReflection.sendGetAuthorizationState(ch) }
                }
            }
            else -> Unit
        }
    }

    private fun sendPhone(phone: String, isCurrentDevice: Boolean, allowFlash: Boolean, allowMissed: Boolean, allowSmsRetriever: Boolean) {
        clientHandle?.let {
            try {
                val sanitized = PhoneNumberSanitizer.sanitize(applicationContext, phone)
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
        listObj?.let { load ->
            TdLibReflection.buildLoadChats(load, limit)?.let { fn ->
                TdLibReflection.sendForResult(
                    ch,
                    fn,
                    timeoutMs = 1_500,
                    retries = 1,
                    traceTag = "Chats:Load[$list]"
                )
            }
        }
        var results = snapshotChats(tag?.toString() ?: list, query, limit)
        if (results.isEmpty() && listObj != null) {
            val fallback = TdLibReflection.buildGetChats(listObj, limit)
            val response = fallback?.let {
                TdLibReflection.sendForResult(ch, it, timeoutMs = 2_000, retries = 1, traceTag = "Chats:Get[$list]")
            }
            val ids = response?.let { TdLibReflection.extractChatsIds(it) } ?: longArrayOf()
            ids.forEach { id ->
                val chatObj = TdLibReflection.buildGetChat(id)?.let { fn ->
                    TdLibReflection.sendForResult(
                        ch,
                        fn,
                        timeoutMs = 1_500,
                        retries = 1,
                        traceTag = "Chats:GetChat[$id]"
                    )
                }
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
                val chatObj = TdLibReflection.buildGetChat(id)?.let { fn ->
                    TdLibReflection.sendForResult(
                        ch,
                        fn,
                        timeoutMs = 1_000,
                        retries = 1,
                        traceTag = "Chats:Resolve[$id]"
                    )
                }
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

    private fun backfillChatHistory(chatId: Long, limit: Int, fetchAll: Boolean = false): BackfillStats {
        android.util.Log.i("TdSvc", "backfill start chatId=${chatId} limit=${limit} fetchAll=${fetchAll}")
        if (chatId == 0L || limit <= 0) return BackfillStats(0, 0, 0, longArrayOf())
        if (!ensureStartedOrError()) return BackfillStats(0, 0, 0, longArrayOf())
        val ch = clientHandle ?: return BackfillStats(0, 0, 0, longArrayOf())

        var fromId = 0L
        var page = 0
        var keepGoing = true
        var lastFirstOnPage: Long = -1L
        val perPage = limit.coerceAtLeast(1)

        var processed = 0
        var newVod = 0
        var newSeriesEpisodes = 0
        val newSeriesIds = mutableSetOf<Long>()
        while (keepGoing) {
            // TDLib paging semantics:
            // - First page: from_message_id=0, offset=0 → newest messages
            // - Older pages: from_message_id=oldestId, offset=-1 → strictly older than oldestId
            val effectiveFromId = fromId
            val offset = if (page == 0) 0 else -1
            val fn = TdLibReflection.buildGetChatHistory(chatId, effectiveFromId, offset, perPage, false) ?: break
            val result = TdLibReflection.sendForResult(
                ch,
                fn,
                timeoutMs = 10_000,
                retries = 2,
                traceTag = "History[$chatId:$page]"
            ) ?: break
            com.chris.m3usuite.telegram.TgRawLogger.log(
                prefix = "GetChatHistory[page=${page}] chatId=$chatId fromId=$effectiveFromId offset=$offset limit=$perPage",
                obj = result
            )
            val messages = TdLibReflection.extractMessagesArray(result)
            if (messages.isEmpty()) break
            var oldestId: Long = Long.MAX_VALUE
            var firstIdThisPage: Long = -1L
            messages.forEach { messageObj ->
                com.chris.m3usuite.telegram.TgRawLogger.log(
                    prefix = "Message chatId=$chatId id=${TdLibReflection.extractMessageId(messageObj) ?: 0L}",
                    obj = messageObj
                )
                val messageId = TdLibReflection.extractMessageId(messageObj) ?: return@forEach
                if (firstIdThisPage < 0) firstIdThisPage = messageId
                if (messageId < oldestId) oldestId = messageId
                val content = runCatching {
                    messageObj.javaClass.getDeclaredField("content").apply { isAccessible = true }.get(messageObj)
                }.getOrNull()
                val messageDate = runCatching { TdLibReflection.extractMessageDate(messageObj) }.getOrNull()
                val outcome = indexMessageContent(chatId, messageId, content, messageDate, asyncWrite = false)
                if (outcome?.isNew == true) {
                    when (outcome.kind) {
                        IndexedKind.VOD -> newVod++
                        IndexedKind.SERIES -> {
                            newSeriesEpisodes++
                            outcome.seriesId?.let { newSeriesIds += it }
                        }
                        IndexedKind.UNKNOWN -> Unit
                    }
                }
                processed++
            }
            page++
            // Prepare next page (older messages)
            val nextFrom = if (oldestId == Long.MAX_VALUE) 0L else oldestId
            // Duplicate guard: if page returned the same leading id as previous page, stop
            if (firstIdThisPage > 0 && firstIdThisPage == lastFirstOnPage) break
            lastFirstOnPage = firstIdThisPage
            if (nextFrom <= 0L || nextFrom == fromId) break
            fromId = nextFrom

            // Continue only if fetchAll requested; otherwise single page
            keepGoing = fetchAll
        }
        android.util.Log.i("TdSvc", "backfill done chatId=${chatId} pages=${page} processed=${processed} newVod=${newVod} newSeriesEpisodes=${newSeriesEpisodes}")
        return BackfillStats(processed, newVod, newSeriesEpisodes, newSeriesIds.toLongArray())
    }

    private fun ensureStartedOrError(): Boolean {
        if (clientHandle != null) return true
        // Try last provided keys, then BuildConfig, finally SettingsStore (blocking read)
        var apiId = lastApiId.takeIf { it > 0 } ?: BuildConfig.TG_API_ID
        var apiHash = lastApiHash.takeIf { it.isNotBlank() } ?: BuildConfig.TG_API_HASH
        if (apiId <= 0 || apiHash.isBlank()) {
            try {
                // Minimal blocking read; called rarely and only when TDLib is first needed.
                val store = com.chris.m3usuite.prefs.SettingsStore(applicationContext)
                val enabled = kotlinx.coroutines.runBlocking { store.tgEnabled.first() }
                if (enabled) {
                    val idFromStore = kotlinx.coroutines.runBlocking { store.tgApiId.first() }
                    val hashFromStore = kotlinx.coroutines.runBlocking { store.tgApiHash.first() }
                    if (idFromStore > 0) apiId = idFromStore
                    if (hashFromStore.isNotBlank()) apiHash = hashFromStore
                }
            } catch (_: Throwable) { }
        }
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
                    val obj = TdLibReflection.buildGetAuthorizationState()?.let {
                        TdLibReflection.sendForResult(c, it, timeoutMs = 500, retries = 1, traceTag = "AuthQueue:state")
                    }
                    TdLibReflection.extractQrLink(obj)
                }.getOrNull()
                if (!qr.isNullOrBlank()) putString("qr", qr)
            }
        }
        try { target.send(m) } catch (e: Exception) { Log.w("TdSvc", "sendAuthState failed", e) }
    }

    private fun listFolders(target: Messenger?) {
        target ?: return
        val ids: IntArray = synchronized(chatCache) {
            chatCache.values
                .flatMap { it.orders.keys }
                .mapNotNull {
                    when (it) {
                        is TdLibReflection.ChatListTag.Folder -> it.id
                        else -> null
                    }
                }
                .distinct()
                .sorted()
                .toIntArray()
        }
        val m = Message.obtain(null, REPLY_FOLDERS)
        m.data = Bundle().apply { putIntArray("folders", ids) }
        try { target.send(m) } catch (_: Exception) { }
    }

    private fun broadcastAuthState(state: TdLibReflection.AuthState) {
        val m = Message.obtain(null, REPLY_AUTH_STATE)
        m.data = Bundle().apply {
            putString("state", state.name)
            if (state == TdLibReflection.AuthState.WAIT_OTHER_DEVICE) {
                val qr = runCatching {
                    val c = clientHandle ?: return@runCatching null
                    val obj = TdLibReflection.buildGetAuthorizationState()?.let {
                        TdLibReflection.sendForResult(c, it, timeoutMs = 500, retries = 1, traceTag = "AuthQueue:state")
                    }
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

    private fun parseProxyKind(value: String?): TdLibReflection.ProxyKind {
        val normalized = value?.lowercase(Locale.getDefault()) ?: ""
        return when (normalized) {
            "socks", "socks5" -> TdLibReflection.ProxyKind.SOCKS5
            "http", "https" -> TdLibReflection.ProxyKind.HTTP
            "mtproto", "mtproxy" -> TdLibReflection.ProxyKind.MTPROTO
            else -> TdLibReflection.ProxyKind.NONE
        }
    }

    private suspend fun ensureAutoDownloadPresets(client: TdLibReflection.ClientHandle): TdLibReflection.AutoDownloadPresets? {
        val cached = autoDownloadDefaults
        if (cached != null) return cached
        val presets = TdLibReflection.fetchAutoDownloadSettingsPresets(client)
        if (presets != null) autoDownloadDefaults = presets
        return presets
    }

    private suspend fun applyAutoDownloadFromStore(client: TdLibReflection.ClientHandle) {
        val presets = ensureAutoDownloadPresets(client) ?: return
        val wifi = presets.wifi.copy(
            isAutoDownloadEnabled = runCatching { settingsStore.tgAutoWifiEnabled.first() }.getOrDefault(true),
            preloadLargeVideos = runCatching { settingsStore.tgAutoWifiPreloadLarge.first() }.getOrDefault(true),
            preloadNextAudio = runCatching { settingsStore.tgAutoWifiPreloadNextAudio.first() }.getOrDefault(true),
            preloadStories = runCatching { settingsStore.tgAutoWifiPreloadStories.first() }.getOrDefault(false),
            useLessDataForCalls = runCatching { settingsStore.tgAutoWifiLessDataCalls.first() }.getOrDefault(false)
        )
        val mobile = presets.mobile.copy(
            isAutoDownloadEnabled = runCatching { settingsStore.tgAutoMobileEnabled.first() }.getOrDefault(true),
            preloadLargeVideos = runCatching { settingsStore.tgAutoMobilePreloadLarge.first() }.getOrDefault(false),
            preloadNextAudio = runCatching { settingsStore.tgAutoMobilePreloadNextAudio.first() }.getOrDefault(false),
            preloadStories = runCatching { settingsStore.tgAutoMobilePreloadStories.first() }.getOrDefault(false),
            useLessDataForCalls = runCatching { settingsStore.tgAutoMobileLessDataCalls.first() }.getOrDefault(true)
        )
        val roaming = presets.roaming.copy(
            isAutoDownloadEnabled = runCatching { settingsStore.tgAutoRoamingEnabled.first() }.getOrDefault(false),
            preloadLargeVideos = runCatching { settingsStore.tgAutoRoamingPreloadLarge.first() }.getOrDefault(false),
            preloadNextAudio = runCatching { settingsStore.tgAutoRoamingPreloadNextAudio.first() }.getOrDefault(false),
            preloadStories = runCatching { settingsStore.tgAutoRoamingPreloadStories.first() }.getOrDefault(false),
            useLessDataForCalls = runCatching { settingsStore.tgAutoRoamingLessDataCalls.first() }.getOrDefault(true)
        )
        TdLibReflection.sendSetAutoDownloadSettings(client, wifi, TdLibReflection.AutoDownloadNetwork.WIFI)
        TdLibReflection.sendSetAutoDownloadSettings(client, mobile, TdLibReflection.AutoDownloadNetwork.MOBILE)
        TdLibReflection.sendSetAutoDownloadSettings(client, roaming, TdLibReflection.AutoDownloadNetwork.ROAMING)
        autoDownloadDefaults = TdLibReflection.AutoDownloadPresets(wifi, mobile, roaming)
    }

    private suspend fun applyAutoDownloadOverride(
        type: String,
        enabled: Boolean,
        preloadLarge: Boolean,
        preloadNext: Boolean,
        preloadStories: Boolean,
        lessDataCalls: Boolean
    ) {
        val client = clientHandle ?: run { pendingApplyOptions = true; return }
        val network = when (type.lowercase(Locale.getDefault())) {
            "mobile" -> TdLibReflection.AutoDownloadNetwork.MOBILE
            "roaming" -> TdLibReflection.AutoDownloadNetwork.ROAMING
            else -> TdLibReflection.AutoDownloadNetwork.WIFI
        }
        val presets = ensureAutoDownloadPresets(client) ?: return
        val updated = when (network) {
            TdLibReflection.AutoDownloadNetwork.WIFI -> presets.wifi.copy(
                isAutoDownloadEnabled = enabled,
                preloadLargeVideos = preloadLarge,
                preloadNextAudio = preloadNext,
                preloadStories = preloadStories,
                useLessDataForCalls = lessDataCalls
            )
            TdLibReflection.AutoDownloadNetwork.MOBILE -> presets.mobile.copy(
                isAutoDownloadEnabled = enabled,
                preloadLargeVideos = preloadLarge,
                preloadNextAudio = preloadNext,
                preloadStories = preloadStories,
                useLessDataForCalls = lessDataCalls
            )
            TdLibReflection.AutoDownloadNetwork.ROAMING -> presets.roaming.copy(
                isAutoDownloadEnabled = enabled,
                preloadLargeVideos = preloadLarge,
                preloadNextAudio = preloadNext,
                preloadStories = preloadStories,
                useLessDataForCalls = lessDataCalls
            )
        }
        TdLibReflection.sendSetAutoDownloadSettings(client, updated, network)
        autoDownloadDefaults = when (network) {
            TdLibReflection.AutoDownloadNetwork.WIFI -> presets.copy(wifi = updated)
            TdLibReflection.AutoDownloadNetwork.MOBILE -> presets.copy(mobile = updated)
            TdLibReflection.AutoDownloadNetwork.ROAMING -> presets.copy(roaming = updated)
        }
    }

    private suspend fun applyProxyFromStore(client: TdLibReflection.ClientHandle) {
        val kindValue = runCatching { settingsStore.tgProxyType.first() }.getOrDefault("")
        val host = runCatching { settingsStore.tgProxyHost.first() }.getOrDefault("")
        val port = runCatching { settingsStore.tgProxyPort.first() }.getOrDefault(0)
        val username = runCatching { settingsStore.tgProxyUsername.first() }.getOrDefault("")
        val password = runCatching { settingsStore.tgProxyPassword.first() }.getOrDefault("")
        val secret = runCatching { settingsStore.tgProxySecret.first() }.getOrDefault("")
        val enabled = runCatching { settingsStore.tgProxyEnabled.first() }.getOrDefault(false)
        TdLibReflection.configureProxy(
            client,
            TdLibReflection.ProxyConfig(parseProxyKind(kindValue), host, port, username, password, secret, enabled)
        )
    }

    private suspend fun applyRuntimeOptionsFromSettings() {
        val client = clientHandle ?: run {
            pendingApplyOptions = true
            return
        }
        pendingApplyOptions = false
        val preferIpv6 = runCatching { settingsStore.tgPreferIpv6.first() }.getOrDefault(true)
        TdLibReflection.sendSetOptionBoolean(client, "prefer_ipv6", preferIpv6)
        val stayOnline = runCatching { settingsStore.tgStayOnline.first() }.getOrDefault(true)
        TdLibReflection.sendSetOptionBoolean(client, "online", stayOnline)
        val storageOptimizer = runCatching { settingsStore.tgStorageOptimizerEnabled.first() }.getOrDefault(true)
        TdLibReflection.sendSetOptionBoolean(client, "use_storage_optimizer", storageOptimizer)
        val ignoreNames = runCatching { settingsStore.tgIgnoreFileNames.first() }.getOrDefault(false)
        TdLibReflection.sendSetOptionBoolean(client, "ignore_file_names", ignoreNames)
        val logLevel = runCatching { settingsStore.tgLogVerbosity.first() }.getOrDefault(1).coerceIn(0, 5)
        TdLibReflection.setLogVerbosityLevel(logLevel)
        TdLibReflection.sendSetLogVerbosityLevel(client, logLevel)
        applyProxyFromStore(client)
        applyAutoDownloadFromStore(client)
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
