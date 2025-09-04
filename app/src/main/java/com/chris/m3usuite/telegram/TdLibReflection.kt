package com.chris.m3usuite.telegram

import android.content.Context
import android.os.Build
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.asStateFlow
import java.lang.reflect.Method
import java.util.Locale

/**
 * Minimal reflection-based TDLib bridge so the app compiles without tdlib classes.
 * If tdlib is present at runtime (org.drinkless.td.libcore.telegram), this provides:
 * - Client.create(resultHandler, exceptionHandler, logHandler)
 * - send(Function, ResultHandler, ExceptionHandler)
 * - Construct common TdApi objects (TdlibParameters, SetAuthenticationPhoneNumber, CheckAuthenticationCode,
 *   SetTdlibParameters, GetAuthorizationState)
 * - Observe UpdateAuthorizationState and expose a lightweight AuthState enum
 */
object TdLibReflection {

    enum class AuthState { UNKNOWN, UNAUTHENTICATED, WAIT_ENCRYPTION_KEY, WAIT_FOR_NUMBER, WAIT_FOR_CODE, WAIT_FOR_PASSWORD, AUTHENTICATED, LOGGING_OUT }

    // Some builds ship TDLib under different Java package names. Try both.
    private val PKGS = listOf(
        "org.drinkless.td.libcore.telegram",
        "org.drinkless.tdlib"
    )

    fun available(): Boolean = PKGS.any { p ->
        try {
            Class.forName("$p.Client")
            Class.forName("$p.TdApi")
            true
        } catch (_: Throwable) { false }
    }

    class ClientHandle internal constructor(
        internal val client: Any,
        internal val sendMethod: Method
    )

    private fun td(className: String): Class<*> {
        for (p in PKGS) {
            try { return Class.forName("$p.$className") } catch (_: Throwable) { }
        }
        throw ClassNotFoundException(className)
    }

    private var singleton: ClientHandle? = null

    /** Create a TDLib client and start auth state flow */
    fun createClient(authStateFlow: MutableStateFlow<AuthState>): ClientHandle? {
        if (!available()) return null
        val clientCls = td("Client")
        val resultHandlerCls = td("Client\$ResultHandler")
        val exceptionHandlerCls = td("Client\$ExceptionHandler")

        val resultHandler = java.lang.reflect.Proxy.newProxyInstance(
            resultHandlerCls.classLoader, arrayOf(resultHandlerCls)
        ) { _, method, args ->
            if (method.name == "onResult") {
                val obj = args?.getOrNull(0)
                if (obj != null) handleResult(obj, authStateFlow)
            }
            null
        }
        val excHandler = java.lang.reflect.Proxy.newProxyInstance(
            exceptionHandlerCls.classLoader, arrayOf(exceptionHandlerCls)
        ) { _, _, _ -> null }

        val create = clientCls.getMethod("create", resultHandlerCls, exceptionHandlerCls, exceptionHandlerCls)
        val client = create.invoke(null, resultHandler, excHandler, excHandler)
        val send = clientCls.getMethod("send", td("TdApi\$Function"), resultHandlerCls, exceptionHandlerCls)

        // reduce verbosity and request initial auth state
        val verbosity = new("TdApi\$SetLogVerbosityLevel", arrayOf(Int::class.javaPrimitiveType!!), arrayOf(1))
        send.invoke(client, verbosity, resultHandler, excHandler)
        val getAuth = new("TdApi\$GetAuthorizationState")
        send.invoke(client, getAuth, resultHandler, excHandler)

        return ClientHandle(client, send)
    }

    /** Returns a shared TDLib client; initializes parameters from BuildConfig TG_API_ID/HASH if possible. */
    @Synchronized
    fun getOrCreateClient(context: Context, authStateFlow: MutableStateFlow<AuthState>): ClientHandle? {
        if (!available()) return null
        if (singleton == null) {
            singleton = createClient(authStateFlow)
            // best-effort set parameters
            val (apiId, apiHash) = try {
                val bc = Class.forName(context.packageName + ".BuildConfig")
                val fid = bc.getDeclaredField("TG_API_ID"); fid.isAccessible = true
                val fhash = bc.getDeclaredField("TG_API_HASH"); fhash.isAccessible = true
                (fid.get(null) as? Int ?: 0) to (fhash.get(null) as? String ?: "")
            } catch (_: Throwable) { 0 to "" }
            if (apiId <= 0 || apiHash.isBlank()) {
                android.util.Log.w("TdLib", "TG_API_ID/HASH missing – set via ENV/ .tg.secrets.properties / -P props")
            }
            val params = buildTdlibParameters(context, apiId, apiHash)
            if (params != null) sendSetTdlibParameters(singleton!!, params)
        }
        return singleton
    }

    private fun handleResult(obj: Any, authStateFlow: MutableStateFlow<AuthState>) {
        val clsName = obj.javaClass.name
        if (PKGS.any { clsName == "$it.TdApi\$UpdateAuthorizationState" }) {
            val field = obj.javaClass.getDeclaredField("authorizationState").apply { isAccessible = true }
            val st = field.get(obj)
            val stName = st?.javaClass?.name
            val state = when (stName) {
                in PKGS.map { "$it.TdApi\$AuthorizationStateWaitEncryptionKey" } -> AuthState.WAIT_ENCRYPTION_KEY
                in PKGS.map { "$it.TdApi\$AuthorizationStateWaitTdlibParameters" } -> AuthState.UNAUTHENTICATED
                in PKGS.map { "$it.TdApi\$AuthorizationStateWaitPhoneNumber" } -> AuthState.WAIT_FOR_NUMBER
                in PKGS.map { "$it.TdApi\$AuthorizationStateWaitCode" } -> AuthState.WAIT_FOR_CODE
                in PKGS.map { "$it.TdApi\$AuthorizationStateWaitPassword" } -> AuthState.WAIT_FOR_PASSWORD
                in PKGS.map { "$it.TdApi\$AuthorizationStateReady" } -> AuthState.AUTHENTICATED
                in PKGS.map { "$it.TdApi\$AuthorizationStateLoggingOut" } -> AuthState.LOGGING_OUT
                else -> AuthState.UNKNOWN
            }
            authStateFlow.value = state
        }
    }

    private fun new(name: String, paramTypes: Array<Class<*>> = emptyArray(), args: Array<Any?> = emptyArray()): Any {
        val c = td(name)
        val ctor = c.getConstructor(*paramTypes)
        return ctor.newInstance(*args)
    }

    fun buildTdlibParameters(context: Context, apiId: Int, apiHash: String): Any? {
        if (!available()) return null
        val params = new("TdApi\$TdlibParameters")
        fun set(field: String, value: Any?) {
            val f = params.javaClass.getDeclaredField(field)
            f.isAccessible = true
            f.set(params, value)
        }
        set("apiId", apiId)
        set("apiHash", apiHash)
        set("useMessageDatabase", true)
        set("useSecretChats", true)
        set("systemLanguageCode", Locale.getDefault().language)
        set("databaseDirectory", context.filesDir.absolutePath)
        set("deviceModel", Build.MODEL)
        set("systemVersion", Build.VERSION.RELEASE)
        set("applicationVersion", "m3uSuite-telemetry-0")
        set("enableStorageOptimizer", true)
        return params
    }

    // --- Chat list builders ---
    fun buildChatListMain(): Any? = try { new("TdApi\$ChatListMain") } catch (_: Throwable) { null }
    fun buildChatListArchive(): Any? = try { new("TdApi\$ChatListArchive") } catch (_: Throwable) { null }
    fun buildGetChats(chatList: Any, limit: Int): Any? = try { new("TdApi\$GetChats", arrayOf(td("TdApi\$ChatList"), Int::class.javaPrimitiveType!!), arrayOf(chatList, limit)) } catch (_: Throwable) { null }
    fun buildGetChat(chatId: Long): Any? = try { new("TdApi\$GetChat", arrayOf(Long::class.javaPrimitiveType!!), arrayOf(chatId)) } catch (_: Throwable) { null }

    fun extractChatsIds(chatsObj: Any): LongArray? = try {
        val f = chatsObj.javaClass.getDeclaredField("chatIds"); f.isAccessible = true
        f.get(chatsObj) as? LongArray
    } catch (_: Throwable) { null }

    fun extractChatTitle(chatObj: Any): String? = try {
        val f = chatObj.javaClass.getDeclaredField("title"); f.isAccessible = true
        f.get(chatObj) as? String
    } catch (_: Throwable) { null }

    fun buildGetChatHistory(chatId: Long, fromMessageId: Long, offset: Int, limit: Int, onlyLocal: Boolean): Any? =
        try { new("TdApi\$GetChatHistory", arrayOf(Long::class.javaPrimitiveType!!, Long::class.javaPrimitiveType!!, Int::class.javaPrimitiveType!!, Int::class.javaPrimitiveType!!, Boolean::class.javaPrimitiveType!!), arrayOf(chatId, fromMessageId, offset, limit, onlyLocal)) } catch (_: Throwable) { null }

    fun extractMessagesArray(messagesObj: Any): List<Any> = try {
        val f = messagesObj.javaClass.getDeclaredField("messages"); f.isAccessible = true
        val arr = f.get(messagesObj) as? Array<*> ?: return emptyList()
        arr.filterNotNull()
    } catch (_: Throwable) { emptyList() }

    fun extractMessageId(messageObj: Any): Long? = try {
        val f = messageObj.javaClass.getDeclaredField("id"); f.isAccessible = true
        (f.get(messageObj) as? Long)
    } catch (_: Throwable) { null }

    fun extractCaptionOrText(messageObj: Any): String? {
        return try {
            val content = messageObj.javaClass.getDeclaredField("content").apply { isAccessible = true }.get(messageObj)
            val caption = runCatching { content.javaClass.getDeclaredField("caption").apply { isAccessible = true }.get(content) }.getOrNull()
            val captionText = caption?.let { cap ->
                runCatching { cap.javaClass.getDeclaredField("text").apply { isAccessible = true }.get(cap) as? String }.getOrNull()
            }
            if (!captionText.isNullOrBlank()) return captionText
            val textField = runCatching { content.javaClass.getDeclaredField("text").apply { isAccessible = true }.get(content) }.getOrNull()
            val textText = textField?.let { tf ->
                runCatching { tf.javaClass.getDeclaredField("text").apply { isAccessible = true }.get(tf) as? String }.getOrNull()
            }
            textText
        } catch (_: Throwable) { null }
    }

    // --- Authorization helpers ---
    fun buildGetAuthorizationState(): Any? = try { new("TdApi\$GetAuthorizationState") } catch (_: Throwable) { null }

    fun mapAuthorizationState(obj: Any?): AuthState {
        if (obj == null) return AuthState.UNKNOWN
        val name = obj.javaClass.name
        return when (name) {
            in PKGS.map { "$it.TdApi\$AuthorizationStateWaitEncryptionKey" } -> AuthState.WAIT_ENCRYPTION_KEY
            in PKGS.map { "$it.TdApi\$AuthorizationStateWaitTdlibParameters" } -> AuthState.UNAUTHENTICATED
            in PKGS.map { "$it.TdApi\$AuthorizationStateWaitPhoneNumber" } -> AuthState.WAIT_FOR_NUMBER
            in PKGS.map { "$it.TdApi\$AuthorizationStateWaitCode" } -> AuthState.WAIT_FOR_CODE
            in PKGS.map { "$it.TdApi\$AuthorizationStateWaitPassword" } -> AuthState.WAIT_FOR_PASSWORD
            in PKGS.map { "$it.TdApi\$AuthorizationStateReady" } -> AuthState.AUTHENTICATED
            in PKGS.map { "$it.TdApi\$AuthorizationStateLoggingOut" } -> AuthState.LOGGING_OUT
            else -> AuthState.UNKNOWN
        }
    }

    fun sendSetTdlibParameters(client: ClientHandle, parameters: Any) {
        val set = new("TdApi\$SetTdlibParameters", arrayOf(td("TdApi\$TdlibParameters")), arrayOf(parameters))
        val handlerType = td("Client\$ResultHandler")
        val exceptionHandlerType = td("Client\$ExceptionHandler")
        val rh = java.lang.reflect.Proxy.newProxyInstance(handlerType.classLoader, arrayOf(handlerType)) { _, _, _ -> null }
        val eh = java.lang.reflect.Proxy.newProxyInstance(exceptionHandlerType.classLoader, arrayOf(exceptionHandlerType)) { _, _, _ -> null }
        client.sendMethod.invoke(client.client, set, rh, eh)
    }

    fun sendSetPhoneNumber(client: ClientHandle, phone: String) {
        val settings = new("TdApi\$PhoneNumberAuthenticationSettings", arrayOf(Boolean::class.javaPrimitiveType!!, Boolean::class.javaPrimitiveType!!, Boolean::class.javaPrimitiveType!!, Boolean::class.javaPrimitiveType!!, Array<String>::class.java), arrayOf(false, false, false, false, emptyArray<String>()))
        val fn = new("TdApi\$SetAuthenticationPhoneNumber", arrayOf(String::class.java, settings.javaClass), arrayOf(phone, settings))
        val handlerType = td("Client\$ResultHandler")
        val exceptionHandlerType = td("Client\$ExceptionHandler")
        val rh = java.lang.reflect.Proxy.newProxyInstance(handlerType.classLoader, arrayOf(handlerType)) { _, _, _ -> null }
        val eh = java.lang.reflect.Proxy.newProxyInstance(exceptionHandlerType.classLoader, arrayOf(exceptionHandlerType)) { _, _, _ -> null }
        client.sendMethod.invoke(client.client, fn, rh, eh)
    }

    fun sendCheckCode(client: ClientHandle, code: String) {
        val fn = new("TdApi\$CheckAuthenticationCode", arrayOf(String::class.java), arrayOf(code))
        val handlerType = td("Client\$ResultHandler")
        val exceptionHandlerType = td("Client\$ExceptionHandler")
        val rh = java.lang.reflect.Proxy.newProxyInstance(handlerType.classLoader, arrayOf(handlerType)) { _, _, _ -> null }
        val eh = java.lang.reflect.Proxy.newProxyInstance(exceptionHandlerType.classLoader, arrayOf(exceptionHandlerType)) { _, _, _ -> null }
        client.sendMethod.invoke(client.client, fn, rh, eh)
    }

    fun sendCheckDatabaseEncryptionKey(client: ClientHandle, key: ByteArray = ByteArray(0)) {
        val fn = try {
            new("TdApi\$CheckDatabaseEncryptionKey", arrayOf(ByteArray::class.java), arrayOf(key))
        } catch (_: Throwable) {
            new("TdApi\$CheckDatabaseEncryptionKey")
        }
        val handlerType = td("Client\$ResultHandler")
        val exceptionHandlerType = td("Client\$ExceptionHandler")
        val rh = java.lang.reflect.Proxy.newProxyInstance(handlerType.classLoader, arrayOf(handlerType)) { _, _, _ -> null }
        val eh = java.lang.reflect.Proxy.newProxyInstance(exceptionHandlerType.classLoader, arrayOf(exceptionHandlerType)) { _, _, _ -> null }
        client.sendMethod.invoke(client.client, fn, rh, eh)
    }

    fun sendCheckPassword(client: ClientHandle, password: String) {
        val fn = new("TdApi\$CheckAuthenticationPassword", arrayOf(String::class.java), arrayOf(password))
        val handlerType = td("Client\$ResultHandler")
        val exceptionHandlerType = td("Client\$ExceptionHandler")
        val rh = java.lang.reflect.Proxy.newProxyInstance(handlerType.classLoader, arrayOf(handlerType)) { _, _, _ -> null }
        val eh = java.lang.reflect.Proxy.newProxyInstance(exceptionHandlerType.classLoader, arrayOf(exceptionHandlerType)) { _, _, _ -> null }
        client.sendMethod.invoke(client.client, fn, rh, eh)
    }

    /** Generic send with one-off result handler that blocks until a result arrives or timeout. */
    fun sendForResult(client: ClientHandle, functionObj: Any, timeoutMs: Long = 7000): Any? {
        val handlerType = try { td("Client\$ResultHandler") } catch (_: Throwable) { return null }
        val exceptionHandlerType = td("Client\$ExceptionHandler")
        val box = java.util.concurrent.ArrayBlockingQueue<Any>(1)
        val rh = java.lang.reflect.Proxy.newProxyInstance(handlerType.classLoader, arrayOf(handlerType)) { _, _, args ->
            if (args != null && args.isNotEmpty() && args[0] != null) {
                box.offer(args[0])
            } else box.offer(Any())
            null
        }
        val eh = java.lang.reflect.Proxy.newProxyInstance(exceptionHandlerType.classLoader, arrayOf(exceptionHandlerType)) { _, _, _ -> null }
        client.sendMethod.invoke(client.client, functionObj, rh, eh)
        return box.poll(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS)
    }

    // Builders for common TdApi functions
    fun buildGetMessage(chatId: Long, messageId: Long): Any? = try { new("TdApi\$GetMessage", arrayOf(Long::class.javaPrimitiveType!!, Long::class.javaPrimitiveType!!), arrayOf(chatId, messageId)) } catch (_: Throwable) { null }
    fun buildGetFile(fileId: Int): Any? = try { new("TdApi\$GetFile", arrayOf(Int::class.javaPrimitiveType!!), arrayOf(fileId)) } catch (_: Throwable) { null }
    fun buildDownloadFile(fileId: Int, priority: Int, offset: Int, limit: Int, synchronous: Boolean): Any? = try {
        // DownloadFile(int fileId, int priority, int offset, int limit, boolean synchronous)
        new("TdApi\$DownloadFile", arrayOf(Int::class.javaPrimitiveType!!, Int::class.javaPrimitiveType!!, Int::class.javaPrimitiveType!!, Int::class.javaPrimitiveType!!, Boolean::class.javaPrimitiveType!!), arrayOf(fileId, priority, offset, limit, synchronous))
    } catch (_: Throwable) { null }

    data class FileInfo(val fileId: Int, val localPath: String?, val downloadingCompleted: Boolean, val downloadedSize: Long, val expectedSize: Long)

    /** Attempts to extract TdApi.File info, including local.path/download flags. */
    fun extractFileInfo(fileObj: Any): FileInfo? = try {
        val id = fileObj.javaClass.getDeclaredField("id").apply { isAccessible = true }.getInt(fileObj)
        val local = runCatching { fileObj.javaClass.getDeclaredField("local").apply { isAccessible = true }.get(fileObj) }.getOrNull()
        var path: String? = null
        var completed = false
        var downloaded: Long = 0
        if (local != null) {
            path = runCatching { local.javaClass.getDeclaredField("path").apply { isAccessible = true }.get(local) as? String }.getOrNull()
            completed = runCatching { local.javaClass.getDeclaredField("isDownloadingCompleted").apply { isAccessible = true }.getBoolean(local) }.getOrDefault(false)
            downloaded = runCatching { local.javaClass.getDeclaredField("downloadedSize").apply { isAccessible = true }.getInt(local).toLong() }.getOrDefault(0L)
        }
        val expected: Long = runCatching { fileObj.javaClass.getDeclaredField("expectedSize").apply { isAccessible = true }.getInt(fileObj).toLong() }
            .recoverCatching { fileObj.javaClass.getDeclaredField("size").apply { isAccessible = true }.getInt(fileObj).toLong() }
            .getOrDefault(0L)
        FileInfo(id, path, completed, downloaded, expected)
    } catch (_: Throwable) { null }

    /** Attempts to extract File.remote.uniqueId if present. */
    fun extractFileUniqueId(fileObj: Any): String? = try {
        val remote = runCatching { fileObj.javaClass.getDeclaredField("remote").apply { isAccessible = true }.get(fileObj) }.getOrNull()
        if (remote != null) {
            runCatching { remote.javaClass.getDeclaredField("uniqueId").apply { isAccessible = true }.get(remote) as? String }.getOrNull()
        } else null
    } catch (_: Throwable) { null }

    /** Extracts a boolean supportsStreaming field from nested content if present (e.g., TdApi.Video.supportsStreaming). */
    fun extractSupportsStreaming(contentObj: Any?): Boolean? {
        if (contentObj == null) return null
        // direct field on content
        runCatching { contentObj.javaClass.getDeclaredField("supportsStreaming").apply { isAccessible = true }.getBoolean(contentObj) }
            .onSuccess { return it }
        // try nested media field (e.g., video)
        val nested = contentObj.javaClass.declaredFields
            .onEach { it.isAccessible = true }
            .mapNotNull { runCatching { it.get(contentObj) }.getOrNull() }
        for (n in nested) {
            val v = runCatching { n.javaClass.getDeclaredField("supportsStreaming").apply { isAccessible = true }.getBoolean(n) }.getOrNull()
            if (v != null) return v
        }
        return null
    }

    /** Try to locate a thumbnail file id in a content object (e.g., video.thumbnail.file.id). */
    fun extractThumbFileId(contentObj: Any?): Int? {
        if (contentObj == null) return null
        // Common path: content.video.thumbnail.file.id
        fun tryFileId(obj: Any?): Int? {
            if (obj == null) return null
            // If this is a File, return id
            if (PKGS.any { obj.javaClass.name == "$it.TdApi\$File" }) {
                return runCatching { obj.javaClass.getDeclaredField("id").apply { isAccessible = true }.getInt(obj) }.getOrNull()
            }
            // If has field 'file', dive
            val fileField = runCatching { obj.javaClass.getDeclaredField("file").apply { isAccessible = true }.get(obj) }.getOrNull()
            if (fileField != null) return tryFileId(fileField)
            return null
        }
        // Look for 'thumbnail' field first
        val thumb = runCatching { contentObj.javaClass.getDeclaredField("thumbnail").apply { isAccessible = true }.get(contentObj) }.getOrNull()
        tryFileId(thumb)?.let { return it }
        // Fallback: scan nested fields for a 'thumbnail' that contains a File
        contentObj.javaClass.declaredFields.forEach { f ->
            f.isAccessible = true
            val v = runCatching { f.get(contentObj) }.getOrNull()
            val id = tryFileId(v)
            if (id != null) return id
        }
        return null
    }

    /** Extract message unix timestamp if present. */
    fun extractMessageDate(messageObj: Any): Long? = try {
        val f = messageObj.javaClass.getDeclaredField("date"); f.isAccessible = true
        (f.getInt(messageObj)).toLong()
    } catch (_: Throwable) { null }

    /** Best-effort filename extraction from content/message. */
    fun extractFileName(contentObj: Any?): String? {
        if (contentObj == null) return null
        // Common fields: fileName, originalFileName, name
        val fields = listOf("fileName", "originalFileName", "name")
        for (fn in fields) {
            val v = runCatching { contentObj.javaClass.getDeclaredField(fn).apply { isAccessible = true }.get(contentObj) as? String }.getOrNull()
            if (!v.isNullOrBlank()) return v
        }
        // Try nested one level
        contentObj.javaClass.declaredFields.forEach { f ->
            f.isAccessible = true
            val nested = runCatching { f.get(contentObj) }.getOrNull()
            if (nested != null) for (fn in fields) {
                val v = runCatching { nested.javaClass.getDeclaredField(fn).apply { isAccessible = true }.get(nested) as? String }.getOrNull()
                if (!v.isNullOrBlank()) return v
            }
        }
        return null
    }

    /** Recursively search first TdApi.File within an object graph (content → media → file). */
    fun findFirstFile(obj: Any?): Any? {
        if (obj == null) return null
        val clsName = obj.javaClass.name
        if (PKGS.any { clsName == "$it.TdApi\$File" }) return obj
        // Arrays/Lists
        if (obj is Array<*>) {
            for (e in obj) findFirstFile(e)?.let { return it }
            return null
        }
        if (obj is Iterable<*>) {
            for (e in obj) findFirstFile(e)?.let { return it }
            return null
        }
        // Recurse fields
        obj.javaClass.declaredFields.forEach { f ->
            f.isAccessible = true
            val v = runCatching { f.get(obj) }.getOrNull()
            val found = findFirstFile(v)
            if (found != null) return found
        }
        return null
    }
}
