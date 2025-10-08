package com.chris.m3usuite.telegram

import android.content.Context
import android.os.Build
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.first
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

    enum class AuthState { UNKNOWN, UNAUTHENTICATED, WAIT_ENCRYPTION_KEY, WAIT_FOR_NUMBER, WAIT_FOR_CODE, WAIT_FOR_PASSWORD, WAIT_OTHER_DEVICE, AUTHENTICATED, LOGGING_OUT }

    data class PhoneAuthSettings(
        val allowFlashCall: Boolean = false,
        val allowMissedCall: Boolean = false,
        val isCurrentPhoneNumber: Boolean = false,
        val hasUnknownPhoneNumber: Boolean = false,
        val allowSmsRetrieverApi: Boolean = false,
        val authenticationTokens: List<String> = emptyList()
    )

    sealed class ChatListTag {
        object Main : ChatListTag()
        object Archive : ChatListTag()
        data class Folder(val id: Int) : ChatListTag()

        override fun toString(): String = when (this) {
            Main -> "main"
            Archive -> "archive"
            is Folder -> "folder:${id}"
        }

        companion object {
            fun fromString(value: String?): ChatListTag? = when {
                value == "main" -> Main
                value == "archive" -> Archive
                value != null && value.startsWith("folder:") -> value.removePrefix("folder:").toIntOrNull()?.let { Folder(it) }
                else -> null
            }
        }
    }

    // Some builds ship TDLib under different Java package names. Try both.
    private val PKGS = listOf(
        "org.drinkless.td.libcore.telegram",
        "org.drinkless.tdlib"
    )

    fun available(): Boolean = PKGS.any { p ->
        try {
            // Check for presence without triggering static initializers
            Class.forName("$p.Client", /* initialize = */ false, TdLibReflection::class.java.classLoader)
            Class.forName("$p.TdApi", /* initialize = */ false, TdLibReflection::class.java.classLoader)
            true
        } catch (_: Throwable) { false }
    }

    /** Shallow verification that Java TdApi contains expected classes used by our JNI. */
    fun verifyBindings(): Boolean = PKGS.any { p ->
        try {
            Class.forName("$p.TdApi\$AuthorizationStateWaitCode", false, TdLibReflection::class.java.classLoader)
            // Optional legacy/new classes; just probe a couple of likely ones
            runCatching { Class.forName("$p.TdApi\$AuthorizationStateWaitOtherDeviceConfirmation", false, TdLibReflection::class.java.classLoader) }
            runCatching { Class.forName("$p.TdApi\$AuthorizationStateWaitEmailAddress", false, TdLibReflection::class.java.classLoader) }
            true
        } catch (_: Throwable) { false }
    }

    @Volatile private var updateListener: ((Any) -> Unit)? = null
    fun setUpdateListener(l: ((Any) -> Unit)?) { updateListener = l }

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

    /** Create a TDLib client and start auth/download state flows */
    fun createClient(authStateFlow: MutableStateFlow<AuthState>, downloadActive: MutableStateFlow<Boolean>? = null): ClientHandle? {
        if (!available()) return null
        val clientCls = td("Client")
        val resultHandlerCls = td("Client\$ResultHandler")
        val exceptionHandlerCls = td("Client\$ExceptionHandler")
        if (!verifyBindings()) {
            android.util.Log.e("TdLib", "TdApi bindings likely mismatched with JNI – please align Java/JNI versions.")
        }

        val resultHandler = java.lang.reflect.Proxy.newProxyInstance(
            resultHandlerCls.classLoader, arrayOf(resultHandlerCls)
        ) { _, method, args ->
            if (method.name == "onResult") {
                val obj = args?.getOrNull(0)
                if (obj != null) {
                    // Forward all updates to listener first (updates-first indexing), then handle built-ins
                    kotlin.runCatching { updateListener?.invoke(obj) }
                    handleResult(obj, authStateFlow, downloadActive)
                }
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

        val nonNullClient = client ?: return null
        return ClientHandle(nonNullClient, send)
    }

    /** Returns a shared TDLib client; initializes parameters from BuildConfig TG_API_ID/HASH or Settings overrides. */
    @Synchronized
    fun getOrCreateClient(context: Context, authStateFlow: MutableStateFlow<AuthState>): ClientHandle? {
        if (!available()) return null
        if (singleton == null) {
            singleton = createClient(authStateFlow, null)
            // best-effort set parameters
            // 1) Try BuildConfig (ENV/.tg.secrets.properties/-P at build time)
            var apiId = 0
            var apiHash = ""
            try {
                val bc = Class.forName(context.packageName + ".BuildConfig")
                val fid = bc.getDeclaredField("TG_API_ID"); fid.isAccessible = true
                val fhash = bc.getDeclaredField("TG_API_HASH"); fhash.isAccessible = true
                apiId = (fid.get(null) as? Int) ?: 0
                apiHash = (fhash.get(null) as? String) ?: ""
            } catch (_: Throwable) { /* ignored */ }

            // 2) Runtime fallback: read Settings overrides synchron synchronously if BuildConfig is empty
            if (apiId <= 0 || apiHash.isBlank()) {
                try {
                    val store = com.chris.m3usuite.prefs.SettingsStore(context)
                    val id = kotlinx.coroutines.runBlocking { store.tgApiId.first() }
                    val hash = kotlinx.coroutines.runBlocking { store.tgApiHash.first() }
                    if (id > 0 && !hash.isNullOrBlank()) {
                        apiId = id
                        apiHash = hash
                    }
                } catch (_: Throwable) { /* ignored */ }
            }

            if (apiId <= 0 || apiHash.isBlank()) {
                android.util.Log.w("TdLib", "TG_API_ID/HASH missing – set via ENV/.tg.secrets.properties/-P or in Settings (tg_api_id/hash)")
            } else {
                val params = buildTdlibParameters(context, apiId, apiHash)
                if (params != null) {
                    sendSetTdlibParameters(singleton!!, params)
                    // Provide database key early so the client can proceed past WAIT_ENCRYPTION_KEY without user action
                    kotlin.runCatching {
                        val key = com.chris.m3usuite.telegram.TelegramKeyStore.getOrCreateDatabaseKey(context)
                        sendCheckDatabaseEncryptionKey(singleton!!, key)
                    }
                }
            }
        }
        return singleton
    }

    private fun handleResult(obj: Any, authStateFlow: MutableStateFlow<AuthState>, downloadActive: MutableStateFlow<Boolean>? = null) {
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
                in PKGS.map { "$it.TdApi\$AuthorizationStateWaitOtherDeviceConfirmation" } -> AuthState.WAIT_OTHER_DEVICE
                in PKGS.map { "$it.TdApi\$AuthorizationStateReady" } -> AuthState.AUTHENTICATED
                in PKGS.map { "$it.TdApi\$AuthorizationStateLoggingOut" } -> AuthState.LOGGING_OUT
                else -> AuthState.UNKNOWN
            }
            authStateFlow.value = state
        }
        if (PKGS.any { clsName == "$it.TdApi\$UpdateFile" }) {
            runCatching {
                val f = obj.javaClass.getDeclaredField("file"); f.isAccessible = true
                val fileObj = f.get(obj)
                extractFileInfo(fileObj ?: return)
            }.onSuccess { info ->
                if (info != null) downloadActive?.value = (info.downloadingActive && !info.downloadingCompleted)
            }
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
        runCatching { set("useChatInfoDatabase", true) }
        runCatching { set("useFileDatabase", true) }
        set("useSecretChats", true)
        set("systemLanguageCode", Locale.getDefault().language)
        val base = context.filesDir.absolutePath
        set("databaseDirectory", base)
        runCatching { set("filesDirectory", base) }
        set("deviceModel", Build.MODEL)
        set("systemVersion", Build.VERSION.RELEASE)
        // Prefer app's VERSION_NAME; fallback to a sane semantic string
        val appVer = runCatching {
            val bc = Class.forName(context.packageName + ".BuildConfig")
            val f = bc.getDeclaredField("VERSION_NAME"); f.isAccessible = true
            (f.get(null) as? String)?.ifBlank { null }
        }.getOrNull() ?: "1.0.0"
        set("applicationVersion", appVer)
        set("enableStorageOptimizer", true)
        // Database encryption key (32 bytes) via KeyStore helper
        val key = TelegramKeyStore.getOrCreateDatabaseKey(context)
        runCatching { set("databaseEncryptionKey", key) }
        return params
    }

    // --- Chat list builders ---
    fun buildChatListMain(): Any? = try { new("TdApi\$ChatListMain") } catch (_: Throwable) { null }
    fun buildChatListArchive(): Any? = try { new("TdApi\$ChatListArchive") } catch (_: Throwable) { null }
    fun buildChatListFolder(folderId: Int): Any? = try { new("TdApi\$ChatListFolder", arrayOf(Int::class.javaPrimitiveType!!), arrayOf(folderId)) } catch (_: Throwable) { null }
    fun buildGetChats(chatList: Any, limit: Int): Any? = try { new("TdApi\$GetChats", arrayOf(td("TdApi\$ChatList"), Int::class.javaPrimitiveType!!), arrayOf(chatList, limit)) } catch (_: Throwable) { null }
    fun buildLoadChats(chatList: Any, limit: Int): Any? = try { new("TdApi\$LoadChats", arrayOf(td("TdApi\$ChatList"), Int::class.javaPrimitiveType!!), arrayOf(chatList, limit)) } catch (_: Throwable) { null }
    fun buildGetChat(chatId: Long): Any? = try { new("TdApi\$GetChat", arrayOf(Long::class.javaPrimitiveType!!), arrayOf(chatId)) } catch (_: Throwable) { null }

    fun extractChatsIds(chatsObj: Any): LongArray? = try {
        val f = chatsObj.javaClass.getDeclaredField("chatIds"); f.isAccessible = true
        f.get(chatsObj) as? LongArray
    } catch (_: Throwable) { null }

    fun extractChatId(chatObj: Any): Long? = try {
        val f = chatObj.javaClass.getDeclaredField("id"); f.isAccessible = true
        (f.get(chatObj) as? Long)
    } catch (_: Throwable) { null }

    fun extractChatTitle(chatObj: Any): String? = try {
        val f = chatObj.javaClass.getDeclaredField("title"); f.isAccessible = true
        f.get(chatObj) as? String
    } catch (_: Throwable) { null }

    fun extractChatPositions(chatObj: Any): List<Any> = try {
        val f = chatObj.javaClass.getDeclaredField("positions"); f.isAccessible = true
        val arr = f.get(chatObj)
        when (arr) {
            is Array<*> -> arr.filterNotNull()
            is Iterable<*> -> arr.filterNotNull()
            else -> emptyList()
        }
    } catch (_: Throwable) { emptyList() }

    fun classifyChatList(obj: Any?): ChatListTag? {
        if (obj == null) return null
        val name = obj.javaClass.name
        return when {
            PKGS.any { name == "$it.TdApi\$ChatListMain" } -> ChatListTag.Main
            PKGS.any { name == "$it.TdApi\$ChatListArchive" } -> ChatListTag.Archive
            PKGS.any { name == "$it.TdApi\$ChatListFolder" } -> {
                val id = runCatching { obj.javaClass.getDeclaredField("chatFolderId").apply { isAccessible = true }.getInt(obj) }.getOrNull()
                id?.let { ChatListTag.Folder(it) }
            }
            else -> null
        }
    }

    fun extractChatPositionOrder(positionObj: Any?): Long? {
        if (positionObj == null) return null
        return try {
            val f = positionObj.javaClass.getDeclaredField("order"); f.isAccessible = true
            f.get(positionObj) as? Long
        } catch (_: Throwable) { null }
    }

    fun extractChatPositionList(positionObj: Any?): Any? {
        if (positionObj == null) return null
        return try {
            val f = positionObj.javaClass.getDeclaredField("list"); f.isAccessible = true
            f.get(positionObj)
        } catch (_: Throwable) { null }
    }

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
            in PKGS.map { "$it.TdApi\$AuthorizationStateWaitOtherDeviceConfirmation" } -> AuthState.WAIT_OTHER_DEVICE
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

    fun buildPhoneNumberAuthenticationSettings(settings: PhoneAuthSettings): Any? {
        val tokens = settings.authenticationTokens.distinct().take(20).toTypedArray()
        val firebaseClass = runCatching { td("TdApi\$FirebaseAuthenticationSettings") }.getOrNull() ?: return null
        val paramTypes = arrayOf(
            Boolean::class.javaPrimitiveType!!,
            Boolean::class.javaPrimitiveType!!,
            Boolean::class.javaPrimitiveType!!,
            Boolean::class.javaPrimitiveType!!,
            Boolean::class.javaPrimitiveType!!,
            firebaseClass,
            Array<String>::class.java
        )
        val args: Array<Any?> = arrayOf(
            settings.allowFlashCall,
            settings.allowMissedCall,
            settings.isCurrentPhoneNumber,
            settings.hasUnknownPhoneNumber,
            settings.allowSmsRetrieverApi,
            null,
            tokens
        )
        return runCatching { new("TdApi\$PhoneNumberAuthenticationSettings", paramTypes, args) }.getOrNull()
    }

    fun sendSetPhoneNumber(client: ClientHandle, phone: String, settings: PhoneAuthSettings = PhoneAuthSettings()) {
        val settingsObj = buildPhoneNumberAuthenticationSettings(settings)
            ?: new(
                "TdApi\$PhoneNumberAuthenticationSettings",
                arrayOf(
                    Boolean::class.javaPrimitiveType!!,
                    Boolean::class.javaPrimitiveType!!,
                    Boolean::class.javaPrimitiveType!!,
                    Boolean::class.javaPrimitiveType!!,
                    Array<String>::class.java
                ),
                arrayOf(
                    settings.allowFlashCall,
                    settings.allowMissedCall,
                    settings.isCurrentPhoneNumber,
                    settings.hasUnknownPhoneNumber,
                    settings.authenticationTokens.distinct().take(20).toTypedArray()
                )
            )
        val fn = new(
            "TdApi\$SetAuthenticationPhoneNumber",
            arrayOf(String::class.java, settingsObj.javaClass),
            arrayOf(phone, settingsObj)
        )
        val handlerType = td("Client\$ResultHandler")
        val exceptionHandlerType = td("Client\$ExceptionHandler")
        // Forward function results (incl. TdApi.Error) to the global update listener
        val rh = java.lang.reflect.Proxy.newProxyInstance(handlerType.classLoader, arrayOf(handlerType)) { _, _, args ->
            val obj = args?.getOrNull(0)
            if (obj != null) kotlin.runCatching { updateListener?.invoke(obj) }
            null
        }
        val eh = java.lang.reflect.Proxy.newProxyInstance(exceptionHandlerType.classLoader, arrayOf(exceptionHandlerType)) { _, _, _ -> null }
        client.sendMethod.invoke(client.client, fn, rh, eh)
        // Proactively request current auth state to refresh UI promptly
        runCatching { sendGetAuthorizationState(client) }
    }

    fun sendCheckCode(client: ClientHandle, code: String) {
        val fn = new("TdApi\$CheckAuthenticationCode", arrayOf(String::class.java), arrayOf(code))
        val handlerType = td("Client\$ResultHandler")
        val exceptionHandlerType = td("Client\$ExceptionHandler")
        val rh = java.lang.reflect.Proxy.newProxyInstance(handlerType.classLoader, arrayOf(handlerType)) { _, _, args ->
            val obj = args?.getOrNull(0)
            if (obj != null) kotlin.runCatching { updateListener?.invoke(obj) }
            null
        }
        val eh = java.lang.reflect.Proxy.newProxyInstance(exceptionHandlerType.classLoader, arrayOf(exceptionHandlerType)) { _, _, _ -> null }
        client.sendMethod.invoke(client.client, fn, rh, eh)
    }

    fun sendGetAuthorizationState(client: ClientHandle) {
        val fn = new("TdApi\$GetAuthorizationState")
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

    // Request QR-Code-based login (user scans from another device)
    fun sendRequestQrCodeAuthentication(client: ClientHandle) {
        val fn = runCatching { new("TdApi\$RequestQrCodeAuthentication") }.getOrNull() ?: return
        val handlerType = td("Client\$ResultHandler")
        val exceptionHandlerType = td("Client\$ExceptionHandler")
        val rh = java.lang.reflect.Proxy.newProxyInstance(handlerType.classLoader, arrayOf(handlerType)) { _, _, _ -> null }
        val eh = java.lang.reflect.Proxy.newProxyInstance(exceptionHandlerType.classLoader, arrayOf(exceptionHandlerType)) { _, _, _ -> null }
        client.sendMethod.invoke(client.client, fn, rh, eh)
    }

    // Extract QR link for AuthorizationStateWaitOtherDeviceConfirmation
    fun extractQrLink(authStateObj: Any?): String? {
        return try {
            if (authStateObj == null) null else {
                val name = authStateObj.javaClass.name
                if (PKGS.any { name == "$it.TdApi\$AuthorizationStateWaitOtherDeviceConfirmation" }) {
                    val f = authStateObj.javaClass.getDeclaredField("link"); f.isAccessible = true
                    f.get(authStateObj) as? String
                } else null
            }
        } catch (_: Throwable) { null }
    }

    fun sendCheckPassword(client: ClientHandle, password: String) {
        val fn = new("TdApi\$CheckAuthenticationPassword", arrayOf(String::class.java), arrayOf(password))
        val handlerType = td("Client\$ResultHandler")
        val exceptionHandlerType = td("Client\$ExceptionHandler")
        val rh = java.lang.reflect.Proxy.newProxyInstance(handlerType.classLoader, arrayOf(handlerType)) { _, _, args ->
            val obj = args?.getOrNull(0)
            if (obj != null) kotlin.runCatching { updateListener?.invoke(obj) }
            null
        }
        val eh = java.lang.reflect.Proxy.newProxyInstance(exceptionHandlerType.classLoader, arrayOf(exceptionHandlerType)) { _, _, _ -> null }
        client.sendMethod.invoke(client.client, fn, rh, eh)
    }

    fun sendLogOut(client: ClientHandle) {
        val fn = runCatching { new("TdApi\$LogOut") }.getOrNull() ?: return
        val handlerType = td("Client\$ResultHandler")
        val exceptionHandlerType = td("Client\$ExceptionHandler")
        val rh = java.lang.reflect.Proxy.newProxyInstance(handlerType.classLoader, arrayOf(handlerType)) { _, _, _ -> null }
        val eh = java.lang.reflect.Proxy.newProxyInstance(exceptionHandlerType.classLoader, arrayOf(exceptionHandlerType)) { _, _, _ -> null }
        client.sendMethod.invoke(client.client, fn, rh, eh)
    }

    fun sendClose(client: ClientHandle) {
        val fn = runCatching { new("TdApi\$Close") }.getOrNull() ?: return
        val handlerType = td("Client\$ResultHandler")
        val exceptionHandlerType = td("Client\$ExceptionHandler")
        val rh = java.lang.reflect.Proxy.newProxyInstance(handlerType.classLoader, arrayOf(handlerType)) { _, _, _ -> null }
        val eh = java.lang.reflect.Proxy.newProxyInstance(exceptionHandlerType.classLoader, arrayOf(exceptionHandlerType)) { _, _, _ -> null }
        client.sendMethod.invoke(client.client, fn, rh, eh)
    }

    // --- Push integration (FCM) ---
    fun sendRegisterFcm(client: ClientHandle, fcmToken: String) {
        val handlerType = td("Client\$ResultHandler")
        val exceptionHandlerType = td("Client\$ExceptionHandler")
        val rh = java.lang.reflect.Proxy.newProxyInstance(handlerType.classLoader, arrayOf(handlerType)) { _, _, _ -> null }
        val eh = java.lang.reflect.Proxy.newProxyInstance(exceptionHandlerType.classLoader, arrayOf(exceptionHandlerType)) { _, _, _ -> null }
        // Build DeviceTokenFirebaseCloudMessaging with best-effort constructor resolution
        val tokenObj = runCatching {
            // Newer: (String token, boolean encrypt, String data)
            new("TdApi\$DeviceTokenFirebaseCloudMessaging", arrayOf(String::class.java, Boolean::class.javaPrimitiveType!!, String::class.java), arrayOf(fcmToken, false, ""))
        }.getOrElse {
            runCatching { new("TdApi\$DeviceTokenFirebaseCloudMessaging", arrayOf(String::class.java, Boolean::class.javaPrimitiveType!!), arrayOf(fcmToken, false)) }.getOrNull()
        } ?: return
        val superCls = (tokenObj.javaClass.superclass as Class<*>)
        val arrCls = java.lang.reflect.Array.newInstance(superCls, 1).javaClass // DeviceToken[]
        val tokensArray = java.lang.reflect.Array.newInstance(superCls, 1).apply { java.lang.reflect.Array.set(this, 0, tokenObj) }
        val reg = runCatching { new("TdApi\$RegisterDevice", arrayOf(arrCls), arrayOf(tokensArray)) }.getOrNull() ?: return
        client.sendMethod.invoke(client.client, reg, rh, eh)
    }

    fun sendProcessPushNotification(client: ClientHandle, payload: String) {
        val handlerType = td("Client\$ResultHandler")
        val exceptionHandlerType = td("Client\$ExceptionHandler")
        val rh = java.lang.reflect.Proxy.newProxyInstance(handlerType.classLoader, arrayOf(handlerType)) { _, _, _ -> null }
        val eh = java.lang.reflect.Proxy.newProxyInstance(exceptionHandlerType.classLoader, arrayOf(exceptionHandlerType)) { _, _, _ -> null }
        val fn = runCatching { new("TdApi\$ProcessPushNotification", arrayOf(String::class.java), arrayOf(payload)) }.getOrNull() ?: return
        client.sendMethod.invoke(client.client, fn, rh, eh)
    }

    // --- App lifecycle/network hooks ---
    fun sendSetInBackground(client: ClientHandle, isInBackground: Boolean) {
        val fn = runCatching { new("TdApi\$SetInBackground", arrayOf(Boolean::class.javaPrimitiveType!!), arrayOf(isInBackground)) }.getOrNull() ?: return
        val handlerType = td("Client\$ResultHandler"); val exceptionHandlerType = td("Client\$ExceptionHandler")
        val rh = java.lang.reflect.Proxy.newProxyInstance(handlerType.classLoader, arrayOf(handlerType)) { _, _, _ -> null }
        val eh = java.lang.reflect.Proxy.newProxyInstance(exceptionHandlerType.classLoader, arrayOf(exceptionHandlerType)) { _, _, _ -> null }
        client.sendMethod.invoke(client.client, fn, rh, eh)
    }

    enum class Net { NONE, WIFI, MOBILE, OTHER }

    fun sendSetNetworkType(client: ClientHandle, net: Net) {
        val nt = when (net) {
            Net.NONE -> runCatching { new("TdApi\$NetworkTypeNone") }.getOrNull()
            Net.WIFI -> runCatching { new("TdApi\$NetworkTypeWiFi") }.getOrNull()
            Net.MOBILE -> runCatching { new("TdApi\$NetworkTypeMobile") }.getOrNull()
            Net.OTHER -> runCatching { new("TdApi\$NetworkTypeOther") }.getOrNull()
        } ?: return
        val fn = runCatching { new("TdApi\$SetNetworkType", arrayOf(td("TdApi\$NetworkType")), arrayOf(nt)) }.getOrNull() ?: return
        val handlerType = td("Client\$ResultHandler"); val exceptionHandlerType = td("Client\$ExceptionHandler")
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
    fun buildCancelDownloadFile(fileId: Int, onlyIfPending: Boolean): Any? = try {
        new("TdApi\$CancelDownloadFile", arrayOf(Int::class.javaPrimitiveType!!, Boolean::class.javaPrimitiveType!!), arrayOf(fileId, onlyIfPending))
    } catch (_: Throwable) { null }

    data class FileInfo(val fileId: Int, val localPath: String?, val downloadingActive: Boolean, val downloadingCompleted: Boolean, val downloadedSize: Long, val expectedSize: Long)

    /** Attempts to extract TdApi.File info, including local.path/download flags. */
    fun extractFileInfo(fileObj: Any): FileInfo? = try {
        val id = fileObj.javaClass.getDeclaredField("id").apply { isAccessible = true }.getInt(fileObj)
        val local = runCatching { fileObj.javaClass.getDeclaredField("local").apply { isAccessible = true }.get(fileObj) }.getOrNull()
        var path: String? = null
        var active = false
        var completed = false
        var downloaded: Long = 0
        if (local != null) {
            path = runCatching { local.javaClass.getDeclaredField("path").apply { isAccessible = true }.get(local) as? String }.getOrNull()
            active = runCatching { local.javaClass.getDeclaredField("isDownloadingActive").apply { isAccessible = true }.getBoolean(local) }.getOrDefault(false)
            completed = runCatching { local.javaClass.getDeclaredField("isDownloadingCompleted").apply { isAccessible = true }.getBoolean(local) }.getOrDefault(false)
            downloaded = runCatching { local.javaClass.getDeclaredField("downloadedSize").apply { isAccessible = true }.getInt(local).toLong() }.getOrDefault(0L)
        }
        val expected: Long = runCatching { fileObj.javaClass.getDeclaredField("expectedSize").apply { isAccessible = true }.getInt(fileObj).toLong() }
            .recoverCatching { fileObj.javaClass.getDeclaredField("size").apply { isAccessible = true }.getInt(fileObj).toLong() }
            .getOrDefault(0L)
        FileInfo(id, path, active, completed, downloaded, expected)
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

    /** Extract best-effort duration in seconds from nested content (video/audio/document). */
    fun extractDurationSecs(contentObj: Any?): Int? {
        if (contentObj == null) return null
        // direct field
        runCatching { contentObj.javaClass.getDeclaredField("duration").apply { isAccessible = true }.getInt(contentObj) }
            .onSuccess { return it }
        // try nested fields likely to carry media (video/audio/document)
        contentObj.javaClass.declaredFields.forEach { f ->
            f.isAccessible = true
            val v = runCatching { f.get(contentObj) }.getOrNull() ?: return@forEach
            val dur = runCatching { v.javaClass.getDeclaredField("duration").apply { isAccessible = true }.getInt(v) }.getOrNull()
            if (dur != null) return dur
        }
        return null
    }

    /** Extract mimeType from nested content; fallback to fileName extension guesses. */
    fun extractMimeType(contentObj: Any?): String? {
        if (contentObj == null) return null
        // Try common mimeType fields
        runCatching { contentObj.javaClass.getDeclaredField("mimeType").apply { isAccessible = true }.get(contentObj) as? String }
            .onSuccess { if (!it.isNullOrBlank()) return it }
        // Nested
        contentObj.javaClass.declaredFields.forEach { f ->
            f.isAccessible = true
            val v = runCatching { f.get(contentObj) }.getOrNull() ?: return@forEach
            val mt = runCatching { v.javaClass.getDeclaredField("mimeType").apply { isAccessible = true }.get(v) as? String }.getOrNull()
            if (!mt.isNullOrBlank()) return mt
        }
        // Guess from filename
        val name = extractFileName(contentObj)
        if (!name.isNullOrBlank()) {
            val ext = name.substringAfterLast('.', "").lowercase()
            when (ext) {
                "mp4" -> return "video/mp4"
                "mkv" -> return "video/x-matroska"
                "avi" -> return "video/x-msvideo"
                "mov" -> return "video/quicktime"
                "mp3" -> return "audio/mpeg"
                "m4a" -> return "audio/mp4"
            }
        }
        return null
    }

    /** Extract width/height from nested video content if present. */
    fun extractVideoDimensions(contentObj: Any?): Pair<Int, Int>? {
        if (contentObj == null) return null
        fun dims(obj: Any?): Pair<Int, Int>? {
            if (obj == null) return null
            val w = runCatching { obj.javaClass.getDeclaredField("width").apply { isAccessible = true }.getInt(obj) }.getOrNull()
            val h = runCatching { obj.javaClass.getDeclaredField("height").apply { isAccessible = true }.getInt(obj) }.getOrNull()
            return if (w != null && h != null && w > 0 && h > 0) Pair(w, h) else null
        }
        dims(contentObj)?.let { return it }
        contentObj.javaClass.declaredFields.forEach { f ->
            f.isAccessible = true
            val v = runCatching { f.get(contentObj) }.getOrNull()
            val d = dims(v)
            if (d != null) return d
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
