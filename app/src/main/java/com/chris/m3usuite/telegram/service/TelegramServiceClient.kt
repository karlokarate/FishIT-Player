package com.chris.m3usuite.telegram.service

import android.content.*
import android.os.*
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

class TelegramServiceClient(private val context: Context) {
    data class ChatSyncResult(
        val chatId: Long,
        val processedMessages: Int,
        val newVod: Int,
        val newSeriesEpisodes: Int,
        val newSeriesIds: LongArray
    )

    private var serviceMessenger: Messenger? = null
    // Queue outbound commands until the service binding is established to avoid races
    private val pending = ArrayDeque<Message>()
    private val pendingChatList = mutableMapOf<Int, (LongArray, Array<String>) -> Unit>()
    private val pendingTitles = mutableMapOf<Int, (LongArray, Array<String>) -> Unit>()
    private val pendingPulls = mutableMapOf<Int, (ChatSyncResult) -> Unit>()
    private val nextReqId = AtomicInteger(1)
    private var pendingFolderList: ((IntArray) -> Unit)? = null
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
                TelegramTdlibService.REPLY_CHAT_LIST -> {
                    val reqId = msg.data.getInt("reqId")
                    val ids = msg.data.getLongArray("ids") ?: longArrayOf()
                    val titles = msg.data.getStringArray("titles") ?: emptyArray()
                    pendingChatList.remove(reqId)?.invoke(ids, titles)
                }
                TelegramTdlibService.REPLY_CHAT_TITLES -> {
                    val reqId = msg.data.getInt("reqId")
                    val ids = msg.data.getLongArray("ids") ?: longArrayOf()
                    val titles = msg.data.getStringArray("titles") ?: emptyArray()
                    pendingTitles.remove(reqId)?.invoke(ids, titles)
                }
                TelegramTdlibService.REPLY_FOLDERS -> {
                    val folders = msg.data.getIntArray("folders") ?: intArrayOf()
                    pendingFolderList?.invoke(folders)
                    pendingFolderList = null
                }
                TelegramTdlibService.REPLY_PULL_DONE -> {
                    val reqId = msg.data.getInt("reqId", -1)
                    val chatId = msg.data.getLong("chatId")
                    val count = msg.data.getInt("count", 0)
                    val vodNew = msg.data.getInt("vodNew", 0)
                    val seriesEpisodes = msg.data.getInt("seriesEpisodeNew", 0)
                    val seriesIds = msg.data.getLongArray("seriesIds") ?: longArrayOf()
                    val result = ChatSyncResult(chatId, count, vodNew, seriesEpisodes, seriesIds)
                    synchronized(pendingPulls) {
                        pendingPulls.remove(reqId)?.invoke(result)
                    }
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
        synchronized(pendingChatList) {
            val callbacks = pendingChatList.values.toList()
            pendingChatList.clear()
            callbacks.forEach { it(longArrayOf(), emptyArray()) }
        }
        synchronized(pendingTitles) {
            val callbacks = pendingTitles.values.toList()
            pendingTitles.clear()
            callbacks.forEach { it(longArrayOf(), emptyArray()) }
        }
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
    fun sendPhone(
        phone: String,
        isCurrentDevice: Boolean = false,
        allowFlashCall: Boolean = false,
        allowMissedCall: Boolean = false,
        allowSmsRetriever: Boolean = false
    ) = send(TelegramTdlibService.CMD_SEND_PHONE) {
        putString("phone", phone)
        putBoolean("isCurrent", isCurrentDevice)
        putBoolean("allowFlash", allowFlashCall)
        putBoolean("allowMissed", allowMissedCall)
        putBoolean("allowSmsRetriever", allowSmsRetriever)
    }
    fun sendCode(code: String) = send(TelegramTdlibService.CMD_SEND_CODE) { putString("code", code) }
    fun sendPassword(pw: String) = send(TelegramTdlibService.CMD_SEND_PASSWORD) { putString("password", pw) }
    fun getAuth() = send(TelegramTdlibService.CMD_GET_AUTH)
    fun logout() = send(TelegramTdlibService.CMD_LOGOUT)
    fun registerFcm(token: String) = send(TelegramTdlibService.CMD_REGISTER_FCM) { putString("token", token) }
    fun processPush(payload: String) = send(TelegramTdlibService.CMD_PROCESS_PUSH) { putString("payload", payload) }
    fun setInBackground(inBg: Boolean) = send(TelegramTdlibService.CMD_SET_IN_BACKGROUND) { putBoolean("inBg", inBg) }
    fun setPreferIpv6(enabled: Boolean) = send(TelegramTdlibService.CMD_SET_IPV6) { putBoolean("enabled", enabled) }
    fun setStayOnline(enabled: Boolean) = send(TelegramTdlibService.CMD_SET_ONLINE) { putBoolean("enabled", enabled) }
    fun setLogVerbosity(level: Int) = send(TelegramTdlibService.CMD_SET_LOG_VERBOSITY) { putInt("level", level) }
    fun setStorageOptimizer(enabled: Boolean) = send(TelegramTdlibService.CMD_SET_STORAGE_OPTIMIZER) { putBoolean("enabled", enabled) }
    fun optimizeStorage() = send(TelegramTdlibService.CMD_OPTIMIZE_STORAGE)
    fun setIgnoreFileNames(enabled: Boolean) = send(TelegramTdlibService.CMD_SET_IGNORE_FILE_NAMES) { putBoolean("enabled", enabled) }
    fun applyProxy(
        type: String,
        host: String,
        port: Int,
        username: String,
        password: String,
        secret: String,
        enabled: Boolean
    ) = send(TelegramTdlibService.CMD_APPLY_PROXY) {
        putString("type", type)
        putString("host", host)
        putInt("port", port)
        putString("username", username)
        putString("password", password)
        putString("secret", secret)
        putBoolean("enabled", enabled)
    }
    fun disableProxy() = send(TelegramTdlibService.CMD_DISABLE_PROXY)
    fun setAutoDownload(
        type: String,
        enabled: Boolean,
        preloadLarge: Boolean,
        preloadNext: Boolean,
        preloadStories: Boolean,
        lessDataCalls: Boolean
    ) = send(TelegramTdlibService.CMD_SET_AUTO_DOWNLOAD) {
        putString("type", type)
        putBoolean("enabled", enabled)
        putBoolean("preloadLarge", preloadLarge)
        putBoolean("preloadNext", preloadNext)
        putBoolean("preloadStories", preloadStories)
        putBoolean("lessDataCalls", lessDataCalls)
    }
    fun applyAllSettings() = send(TelegramTdlibService.CMD_APPLY_ALL_SETTINGS)

    suspend fun listFolders(): IntArray = suspendCancellableCoroutine { cont ->
        pendingFolderList = { arr -> cont.resume(arr) }
        cont.invokeOnCancellation { pendingFolderList = null }
        send(TelegramTdlibService.CMD_LIST_FOLDERS)
    }

    suspend fun listChats(list: String, limit: Int = 200, query: String? = null): List<Pair<Long, String>> =
        suspendCancellableCoroutine { cont ->
            val reqId = nextReqId.getAndIncrement()
            synchronized(pendingChatList) {
                pendingChatList[reqId] = { ids, titles ->
                    val pairs = ids.zip(titles.asList()) { id, title -> id to title }
                    cont.resume(pairs)
                }
            }
            cont.invokeOnCancellation {
                synchronized(pendingChatList) { pendingChatList.remove(reqId) }
            }
            send(TelegramTdlibService.CMD_LIST_CHATS) {
                putInt("reqId", reqId)
                putString("list", list)
                putInt("limit", limit)
                if (!query.isNullOrBlank()) putString("query", query)
            }
        }

    suspend fun resolveChatTitles(ids: LongArray): List<Pair<Long, String>> =
        suspendCancellableCoroutine { cont ->
            val reqId = nextReqId.getAndIncrement()
            synchronized(pendingTitles) {
                pendingTitles[reqId] = { resolvedIds, titles ->
                    val pairs = resolvedIds.zip(titles.asList()) { id, title -> id to title }
                    cont.resume(pairs)
                }
            }
            cont.invokeOnCancellation {
                synchronized(pendingTitles) { pendingTitles.remove(reqId) }
            }
            send(TelegramTdlibService.CMD_RESOLVE_CHAT_TITLES) {
                putInt("reqId", reqId)
                putLongArray("ids", ids)
            }
        }

    fun pullChatHistory(chatId: Long, limit: Int = 200, fetchAll: Boolean = false) =
        send(TelegramTdlibService.CMD_PULL_CHAT_HISTORY) {
            putLong("chatId", chatId)
            putInt("limit", limit)
            putBoolean("fetchAll", fetchAll)
        }

    suspend fun pullChatHistoryAwait(chatId: Long, limit: Int = 200, fetchAll: Boolean = false): ChatSyncResult =
        suspendCancellableCoroutine { cont ->
            val reqId = nextReqId.getAndIncrement()
            synchronized(pendingPulls) {
                pendingPulls[reqId] = { result -> cont.resume(result) }
            }
            cont.invokeOnCancellation { synchronized(pendingPulls) { pendingPulls.remove(reqId) } }
            send(TelegramTdlibService.CMD_PULL_CHAT_HISTORY) {
                putInt("reqId", reqId)
                putLong("chatId", chatId)
                putInt("limit", limit)
                putBoolean("fetchAll", fetchAll)
            }
        }
}
