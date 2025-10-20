package com.chris.m3usuite.telegram.service

import android.content.*
import android.os.*
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.resume
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine

class TelegramServiceClient(private val context: Context) {
    data class ChatSyncResult(
        val chatId: Long,
        val processedMessages: Int,
        val newVod: Int,
        val newSeriesEpisodes: Int,
        val newSeriesIds: LongArray
    )

    data class ServiceError(
        val message: String,
        val code: Int?,
        val rawMessage: String?,
        val type: String?
    )

    sealed interface AuthEvent {
        data class CodeSent(val timeoutSec: Int, val nextType: String?) : AuthEvent
        data class Error(val userMessage: String, val retryAfterSec: Int? = null) : AuthEvent
        object PasswordRequired : AuthEvent
        object SignedIn : AuthEvent
    }

    private var serviceMessenger: Messenger? = null
    // Queue outbound commands until the service binding is established to avoid races
    private val pending = ArrayDeque<Message>()
    private val pendingChatList = mutableMapOf<Int, (LongArray, Array<String>) -> Unit>()
    private val pendingTitles = mutableMapOf<Int, (LongArray, Array<String>) -> Unit>()
    private val pendingPulls = mutableMapOf<Int, (ChatSyncResult) -> Unit>()
    private val nextReqId = AtomicInteger(1)
    private var pendingFolderList: ((IntArray) -> Unit)? = null
    private val authEventsChannel = Channel<AuthEvent>(Channel.BUFFERED)
    val authEvents = authEventsChannel.receiveAsFlow()
    private val resendState = MutableStateFlow(0)
    val resendInSec: StateFlow<Int> = resendState.asStateFlow()
    private var resendJob: Job? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val incoming = object : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message) {
            when (msg.what) {
                TelegramTdlibService.REPLY_AUTH_STATE -> {
                    val st = msg.data.getString("state") ?: return
                    _authCallbacks.forEach { it(st) }
                    val qr = msg.data.getString("qr")
                    if (!qr.isNullOrBlank()) _qrCallbacks.forEach { it(qr) }
                    when (st.uppercase()) {
                        "WAIT_FOR_PASSWORD" -> authEventsChannel.trySend(AuthEvent.PasswordRequired)
                        "AUTHENTICATED" -> {
                            authEventsChannel.trySend(AuthEvent.SignedIn)
                            stopResendCountdown()
                        }
                    }
                }
                TelegramTdlibService.REPLY_ERROR -> {
                    val em = msg.data.getString("message") ?: return
                    val code = if (msg.data.containsKey("errorCode")) msg.data.getInt("errorCode") else null
                    val raw = msg.data.getString("errorRaw")
                    val type = msg.data.getString("errorType")
                    val error = ServiceError(em, code, raw, type)
                    _errorCallbacks.forEach { it(error) }
                    val (userMessage, retryAfter) = mapAuthError(error)
                    authEventsChannel.trySend(AuthEvent.Error(userMessage, retryAfter))
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
                TelegramTdlibService.REPLY_AUTH_EVENT -> {
                    val type = msg.data.getString("type") ?: return
                    when (type) {
                        "code_sent" -> {
                            val timeout = msg.data.getInt("timeout", 0)
                            val next = msg.data.getString("nextType")
                            startResendCountdown(timeout)
                            authEventsChannel.trySend(AuthEvent.CodeSent(timeout, next))
                        }
                        "password_required" -> authEventsChannel.trySend(AuthEvent.PasswordRequired)
                        "signed_in" -> {
                            authEventsChannel.trySend(AuthEvent.SignedIn)
                            stopResendCountdown()
                        }
                    }
                }
                else -> super.handleMessage(msg)
            }
        }
    }
    private val replyMessenger = Messenger(incoming)
    private val _authCallbacks = mutableSetOf<(String)->Unit>()
    private val _errorCallbacks = mutableSetOf<(ServiceError)->Unit>()
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
        stopResendCountdown()
        scope.coroutineContext[Job]?.cancelChildren()
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

    fun errors(): Flow<ServiceError> = callbackFlow {
        val cb: (ServiceError)->Unit = { trySend(it).isSuccess }
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
    fun resendCode() {
        if (resendState.value > 0) return
        send(TelegramTdlibService.CMD_RESEND_CODE)
    }
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

    suspend fun resolveChatTitle(chatId: Long): String? {
        return resolveChatTitles(longArrayOf(chatId)).firstOrNull()?.second
    }

    suspend fun requestCode(phone: String, isCurrentDevice: Boolean = false) {
        sendPhone(
            phone = phone,
            isCurrentDevice = isCurrentDevice,
            allowFlashCall = false,
            allowMissedCall = false,
            allowSmsRetriever = true
        )
        getAuth()
    }

    suspend fun submitCode(code: String) {
        sendCode(code)
        getAuth()
    }

    suspend fun submitPassword(password: String) {
        sendPassword(password)
        getAuth()
    }

    private fun startResendCountdown(timeout: Int) {
        if (timeout <= 0) {
            resendState.tryEmit(0)
            return
        }
        stopResendCountdown()
        resendJob = scope.launch(Dispatchers.Main.immediate) {
            var remaining = timeout
            while (remaining > 0) {
                resendState.emit(remaining)
                delay(1000)
                remaining--
            }
            resendState.emit(0)
            send(TelegramTdlibService.CMD_RESEND_CODE)
        }
    }

    private fun stopResendCountdown() {
        resendJob?.cancel()
        resendJob = null
        resendState.tryEmit(0)
    }

    private fun mapAuthError(error: ServiceError): Pair<String, Int?> {
        val raw = (error.rawMessage ?: error.message).uppercase()
        val flood = Regex("FLOOD_WAIT_(\\d+)").find(raw)?.groupValues?.getOrNull(1)?.toIntOrNull()
        val message = when {
            raw.contains("PHONE_CODE_INVALID") || raw.contains("PHONE_CODE_EMPTY") -> "Der eingegebene Code ist ungültig."
            raw.contains("PHONE_CODE_EXPIRED") -> "Der Code ist abgelaufen. Bitte erneut senden."
            raw.contains("PHONE_NUMBER_FLOOD") || raw.contains("CODE_TOO_MUCH") || raw.contains("TOO MANY REQUESTS") ->
                "Zu viele Anfragen. Bitte warte einen Moment."
            raw.contains("SESSION_PASSWORD_NEEDED") -> "2‑Faktor‑Passwort erforderlich."
            raw.contains("PHONE_NUMBER_INVALID") -> "Die Telefonnummer ist ungültig."
            else -> "Anmeldung fehlgeschlagen: ${error.message}"
        }
        return message to flood
    }
}
