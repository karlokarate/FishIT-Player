package com.chris.m3usuite.telegram

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.lang.reflect.Method
import java.util.Locale
import java.util.concurrent.TimeUnit

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

    private const val LOG_TAG = "TdLib"
    private val NULL_MARKER = Any()
    private const val MAX_BACKOFF_MS = 20_000L

    enum class AuthState { UNKNOWN, UNAUTHENTICATED, WAIT_ENCRYPTION_KEY, WAIT_FOR_NUMBER, WAIT_FOR_CODE, WAIT_FOR_PASSWORD, WAIT_OTHER_DEVICE, AUTHENTICATED, LOGGING_OUT }

    data class PhoneAuthSettings(
        val allowFlashCall: Boolean = false,
        val allowMissedCall: Boolean = false,
        val isCurrentPhoneNumber: Boolean = false,
        val hasUnknownPhoneNumber: Boolean = false,
        val allowSmsRetrieverApi: Boolean = false,
        val authenticationTokens: List<String> = emptyList()
    )

    enum class ProxyKind { NONE, SOCKS5, HTTP, MTPROTO }

    data class ProxyConfig(
        val kind: ProxyKind,
        val host: String,
        val port: Int,
        val username: String = "",
        val password: String = "",
        val secret: String = "",
        val enabled: Boolean = false
    )

    data class AutoDownloadSettings(
        val isAutoDownloadEnabled: Boolean,
        val maxPhotoFileSize: Long,
        val maxVideoFileSize: Long,
        val maxOtherFileSize: Long,
        val videoUploadBitrate: Int,
        val preloadLargeVideos: Boolean,
        val preloadNextAudio: Boolean,
        val preloadStories: Boolean,
        val useLessDataForCalls: Boolean
    )

    data class AutoDownloadPresets(
        val wifi: AutoDownloadSettings,
        val mobile: AutoDownloadSettings,
        val roaming: AutoDownloadSettings
    )

    enum class AutoDownloadNetwork { WIFI, MOBILE, ROAMING }

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

    private val updateListeners = java.util.concurrent.CopyOnWriteArraySet<(Any) -> Unit>()

    /**
     * Registers an additional TDLib update listener. Call [AutoCloseable.close] on the
     * returned handle to unregister the listener again. Existing callers of [setUpdateListener]
     * are supported by delegating to this API.
     */
    fun addUpdateListener(listener: (Any) -> Unit): AutoCloseable {
        updateListeners += listener
        return AutoCloseable { removeUpdateListener(listener) }
    }

    fun removeUpdateListener(listener: (Any) -> Unit) {
        updateListeners -= listener
    }

    @Deprecated("Use addUpdateListener/removeUpdateListener instead")
    fun setUpdateListener(l: ((Any) -> Unit)?) {
        updateListeners.clear()
        if (l != null) {
            updateListeners += l
        }
    }

    private fun dispatchUpdate(obj: Any) {
        if (updateListeners.isEmpty()) return
        updateListeners.forEach { listener ->
            runCatching { listener.invoke(obj) }
        }
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
                    dispatchUpdate(obj)
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
        runCatching { set("useTestDc", false) }
        val locale = if (Build.VERSION.SDK_INT >= 24) {
            context.resources.configuration.locales.takeIf { it.size() > 0 }?.get(0)
        } else {
            @Suppress("DEPRECATION")
            context.resources.configuration.locale
        }
        val languageCode = locale?.language?.takeIf { it.isNotBlank() }
            ?.lowercase(Locale.ROOT)
            ?: "en"
        set("systemLanguageCode", languageCode)
        val installId = TelegramKeyStore.getOrCreateInstallId(context)
        val dbRoot = File(context.filesDir, "tdlib/$installId").apply {
            if (!exists()) mkdirs()
        }
        set("databaseDirectory", dbRoot.absolutePath)
        runCatching { set("filesDirectory", dbRoot.absolutePath) }
        val manufacturer = Build.MANUFACTURER?.takeIf { it.isNotBlank() } ?: "Android"
        val model = Build.MODEL?.takeIf { it.isNotBlank() } ?: manufacturer
        val deviceModel = listOf(manufacturer, model)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .joinToString(separator = " ")
            .ifBlank { "Android Device" }
        set("deviceModel", deviceModel)
        val release = Build.VERSION.RELEASE?.takeIf { it.isNotBlank() } ?: "Unknown"
        val systemVersion = "Android ${release} (API ${Build.VERSION.SDK_INT})"
        set("systemVersion", systemVersion)
        val pkgName = context.packageName
        val pmVersion = runCatching {
            val pm = context.packageManager
            if (Build.VERSION.SDK_INT >= 33) {
                pm.getPackageInfo(pkgName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(pkgName, 0)
            }
        }.getOrNull()?.versionName?.takeIf { !it.isNullOrBlank() }
        val appVer = pmVersion
            ?: runCatching {
                val bc = Class.forName("$pkgName.BuildConfig")
                val f = bc.getDeclaredField("VERSION_NAME"); f.isAccessible = true
                (f.get(null) as? String)?.ifBlank { null }
            }.getOrNull()
            ?: "1.0.0"
        set("applicationVersion", appVer)
        set("enableStorageOptimizer", true)
        // Database encryption key (32 bytes) via KeyStore helper
        val key = TelegramKeyStore.getOrCreateDatabaseKey(context)
        runCatching { set("databaseEncryptionKey", key) }
        return params
    }

    private fun buildOptionValueBoolean(value: Boolean): Any? = runCatching {
        new(
            "TdApi\$OptionValueBoolean",
            arrayOf(Boolean::class.javaPrimitiveType!!),
            arrayOf(value)
        )
    }.getOrNull()

    fun sendSetOptionBoolean(client: ClientHandle, name: String, value: Boolean) {
        val optionValue = buildOptionValueBoolean(value) ?: return
        val fn = runCatching {
            new(
                "TdApi\$SetOption",
                arrayOf<Class<*>>(String::class.java, td("TdApi\$OptionValue")),
                arrayOf(name, optionValue)
            )
        }.getOrNull() ?: return
        val handlerType = td("Client\$ResultHandler")
        val exceptionHandlerType = td("Client\$ExceptionHandler")
        val rh = java.lang.reflect.Proxy.newProxyInstance(handlerType.classLoader, arrayOf(handlerType)) { _, _, _ -> null }
        val eh = java.lang.reflect.Proxy.newProxyInstance(exceptionHandlerType.classLoader, arrayOf(exceptionHandlerType)) { _, _, _ -> null }
        client.sendMethod.invoke(client.client, fn, rh, eh)
    }

    fun setLogVerbosityLevel(level: Int) {
        val normalized = level.coerceIn(0, 5)
        val fn = new("TdApi\$SetLogVerbosityLevel", arrayOf(Int::class.javaPrimitiveType!!), arrayOf(normalized))
        val clientCls = td("Client")
        val exec = clientCls.getMethod("execute", td("TdApi\$Function"))
        exec.invoke(null, fn)
    }

    fun sendSetLogVerbosityLevel(client: ClientHandle, level: Int) {
        val normalized = level.coerceIn(0, 5)
        val fn = new("TdApi\$SetLogVerbosityLevel", arrayOf(Int::class.javaPrimitiveType!!), arrayOf(normalized))
        val handlerType = td("Client\$ResultHandler")
        val exceptionHandlerType = td("Client\$ExceptionHandler")
        val rh = java.lang.reflect.Proxy.newProxyInstance(handlerType.classLoader, arrayOf(handlerType)) { _, _, _ -> null }
        val eh = java.lang.reflect.Proxy.newProxyInstance(exceptionHandlerType.classLoader, arrayOf(exceptionHandlerType)) { _, _, _ -> null }
        client.sendMethod.invoke(client.client, fn, rh, eh)
    }

    fun sendOptimizeStorage(client: ClientHandle) {
        val fn = runCatching { new("TdApi\$OptimizeStorage") }.getOrElse {
            val fileTypeClass = td("TdApi\$FileType")
            val fileTypeArray = java.lang.reflect.Array.newInstance(fileTypeClass, 0)
            val emptyLongArray = LongArray(0)
            new(
                "TdApi\$OptimizeStorage",
                arrayOf(
                    Long::class.javaPrimitiveType!!,
                    Int::class.javaPrimitiveType!!,
                    Int::class.javaPrimitiveType!!,
                    Int::class.javaPrimitiveType!!,
                    fileTypeArray.javaClass,
                    LongArray::class.java,
                    LongArray::class.java,
                    Boolean::class.javaPrimitiveType!!,
                    Int::class.javaPrimitiveType!!
                ),
                arrayOf(0L, 0, 0, 0, fileTypeArray, emptyLongArray, emptyLongArray, false, 0)
            )
        } ?: return
        val handlerType = td("Client\$ResultHandler")
        val exceptionHandlerType = td("Client\$ExceptionHandler")
        val rh = java.lang.reflect.Proxy.newProxyInstance(handlerType.classLoader, arrayOf(handlerType)) { _, _, _ -> null }
        val eh = java.lang.reflect.Proxy.newProxyInstance(exceptionHandlerType.classLoader, arrayOf(exceptionHandlerType)) { _, _, _ -> null }
        client.sendMethod.invoke(client.client, fn, rh, eh)
    }

    private fun parseAutoDownloadSettings(obj: Any?): AutoDownloadSettings? {
        if (obj == null) return null
        return try {
            val cls = obj.javaClass
            val enabled = cls.getDeclaredField("isAutoDownloadEnabled").apply { isAccessible = true }.getBoolean(obj)
            val maxPhoto = cls.getDeclaredField("maxPhotoFileSize").apply { isAccessible = true }.getLong(obj)
            val maxVideo = cls.getDeclaredField("maxVideoFileSize").apply { isAccessible = true }.getLong(obj)
            val maxOther = cls.getDeclaredField("maxOtherFileSize").apply { isAccessible = true }.getLong(obj)
            val uploadBitrate = cls.getDeclaredField("videoUploadBitrate").apply { isAccessible = true }.getInt(obj)
            val preloadLarge = cls.getDeclaredField("preloadLargeVideos").apply { isAccessible = true }.getBoolean(obj)
            val preloadNext = cls.getDeclaredField("preloadNextAudio").apply { isAccessible = true }.getBoolean(obj)
            val preloadStories = runCatching { cls.getDeclaredField("preloadStories").apply { isAccessible = true }.getBoolean(obj) }.getOrDefault(false)
            val lessDataCalls = runCatching { cls.getDeclaredField("useLessDataForCalls").apply { isAccessible = true }.getBoolean(obj) }.getOrDefault(false)
            AutoDownloadSettings(
                enabled,
                maxPhoto,
                maxVideo,
                maxOther,
                uploadBitrate,
                preloadLarge,
                preloadNext,
                preloadStories,
                lessDataCalls
            )
        } catch (_: Throwable) {
            null
        }
    }

    private fun buildAutoDownloadSettings(settings: AutoDownloadSettings): Any? {
        val paramTypes: Array<Class<*>> = arrayOf(
            Boolean::class.javaPrimitiveType!!,
            Long::class.javaPrimitiveType!!,
            Long::class.javaPrimitiveType!!,
            Long::class.javaPrimitiveType!!,
            Int::class.javaPrimitiveType!!,
            Boolean::class.javaPrimitiveType!!,
            Boolean::class.javaPrimitiveType!!,
            Boolean::class.javaPrimitiveType!!,
            Boolean::class.javaPrimitiveType!!
        )
        val args: Array<Any?> = arrayOf(
            settings.isAutoDownloadEnabled,
            settings.maxPhotoFileSize,
            settings.maxVideoFileSize,
            settings.maxOtherFileSize,
            settings.videoUploadBitrate,
            settings.preloadLargeVideos,
            settings.preloadNextAudio,
            settings.preloadStories,
            settings.useLessDataForCalls
        )
        return runCatching { new("TdApi\$AutoDownloadSettings", paramTypes, args) }.getOrNull()
    }

    private fun buildNetworkType(network: AutoDownloadNetwork): Any? = when (network) {
        AutoDownloadNetwork.WIFI -> runCatching { new("TdApi\$NetworkTypeWiFi") }.getOrNull()
        AutoDownloadNetwork.MOBILE -> runCatching { new("TdApi\$NetworkTypeMobile") }.getOrNull()
        AutoDownloadNetwork.ROAMING -> runCatching { new("TdApi\$NetworkTypeMobileRoaming") }.getOrNull()
    }

    fun fetchAutoDownloadSettingsPresets(client: ClientHandle): AutoDownloadPresets? {
        val fn = runCatching { new("TdApi\$GetAutoDownloadSettingsPresets") }.getOrNull() ?: return null
        val result = sendForResult(client, fn, timeoutMs = 1_500, retries = 1, traceTag = "GetAutoDownloadSettingsPresets") ?: return null
        return try {
            val cls = result.javaClass
            val low = cls.getDeclaredField("low").apply { isAccessible = true }.get(result)
            val medium = cls.getDeclaredField("medium").apply { isAccessible = true }.get(result)
            val high = cls.getDeclaredField("high").apply { isAccessible = true }.get(result)
            val mobile = parseAutoDownloadSettings(low)
            val wifi = parseAutoDownloadSettings(medium)
            val roaming = parseAutoDownloadSettings(high)
            if (mobile != null && wifi != null && roaming != null) {
                AutoDownloadPresets(wifi = wifi, mobile = mobile, roaming = roaming)
            } else null
        } catch (_: Throwable) {
            null
        }
    }

    fun sendSetAutoDownloadSettings(client: ClientHandle, settings: AutoDownloadSettings, network: AutoDownloadNetwork) {
        val settingsObj = buildAutoDownloadSettings(settings) ?: return
        val networkObj = buildNetworkType(network) ?: return
        val fn = runCatching {
            new(
                "TdApi\$SetAutoDownloadSettings",
                arrayOf<Class<*>>(td("TdApi\$AutoDownloadSettings"), td("TdApi\$NetworkType")),
                arrayOf(settingsObj, networkObj)
            )
        }.getOrNull() ?: return
        val handlerType = td("Client\$ResultHandler")
        val exceptionHandlerType = td("Client\$ExceptionHandler")
        val rh = java.lang.reflect.Proxy.newProxyInstance(handlerType.classLoader, arrayOf(handlerType)) { _, _, _ -> null }
        val eh = java.lang.reflect.Proxy.newProxyInstance(exceptionHandlerType.classLoader, arrayOf(exceptionHandlerType)) { _, _, _ -> null }
        client.sendMethod.invoke(client.client, fn, rh, eh)
    }

    private fun fetchProxies(client: ClientHandle): List<Any> {
        val fn = runCatching { new("TdApi\$GetProxies") }.getOrNull() ?: return emptyList()
        val result = sendForResult(client, fn, timeoutMs = 1_500, retries = 1, traceTag = "GetProxies") ?: return emptyList()
        return try {
            val field = result.javaClass.getDeclaredField("proxies").apply { isAccessible = true }
            val arr = field.get(result)
            when (arr) {
                is Array<*> -> arr.filterNotNull().map { it as Any }
                is Iterable<*> -> arr.filterNotNull().map { it as Any }
                else -> emptyList()
            }
        } catch (_: Throwable) {
            emptyList()
        }
    }

    private fun extractProxyId(proxyObj: Any?): Int? = try {
        proxyObj?.javaClass?.getDeclaredField("id")?.apply { isAccessible = true }?.getInt(proxyObj)
    } catch (_: Throwable) { null }

    private fun removeProxy(client: ClientHandle, proxyId: Int) {
        val fn = runCatching { new("TdApi\$RemoveProxy", arrayOf(Int::class.javaPrimitiveType!!), arrayOf(proxyId)) }.getOrNull() ?: return
        val handlerType = td("Client\$ResultHandler")
        val exceptionHandlerType = td("Client\$ExceptionHandler")
        val rh = java.lang.reflect.Proxy.newProxyInstance(handlerType.classLoader, arrayOf(handlerType)) { _, _, _ -> null }
        val eh = java.lang.reflect.Proxy.newProxyInstance(exceptionHandlerType.classLoader, arrayOf(exceptionHandlerType)) { _, _, _ -> null }
        client.sendMethod.invoke(client.client, fn, rh, eh)
    }

    fun disableProxy(client: ClientHandle) {
        val fn = runCatching { new("TdApi\$DisableProxy") }.getOrNull() ?: return
        val handlerType = td("Client\$ResultHandler")
        val exceptionHandlerType = td("Client\$ExceptionHandler")
        val rh = java.lang.reflect.Proxy.newProxyInstance(handlerType.classLoader, arrayOf(handlerType)) { _, _, _ -> null }
        val eh = java.lang.reflect.Proxy.newProxyInstance(exceptionHandlerType.classLoader, arrayOf(exceptionHandlerType)) { _, _, _ -> null }
        client.sendMethod.invoke(client.client, fn, rh, eh)
        fetchProxies(client).mapNotNull { extractProxyId(it) }.forEach { removeProxy(client, it) }
    }

    private fun enableProxy(client: ClientHandle, proxyId: Int) {
        val fn = runCatching { new("TdApi\$EnableProxy", arrayOf(Int::class.javaPrimitiveType!!), arrayOf(proxyId)) }.getOrNull() ?: return
        val handlerType = td("Client\$ResultHandler")
        val exceptionHandlerType = td("Client\$ExceptionHandler")
        val rh = java.lang.reflect.Proxy.newProxyInstance(handlerType.classLoader, arrayOf(handlerType)) { _, _, _ -> null }
        val eh = java.lang.reflect.Proxy.newProxyInstance(exceptionHandlerType.classLoader, arrayOf(exceptionHandlerType)) { _, _, _ -> null }
        client.sendMethod.invoke(client.client, fn, rh, eh)
    }

    fun configureProxy(client: ClientHandle, config: ProxyConfig): Boolean {
        if (config.kind == ProxyKind.NONE || config.host.isBlank() || config.port <= 0 || !config.enabled) {
            disableProxy(client)
            return false
        }
        val proxyType = when (config.kind) {
            ProxyKind.SOCKS5 -> runCatching {
                new(
                    "TdApi\$ProxyTypeSocks5",
                    arrayOf<Class<*>>(String::class.java, String::class.java),
                    arrayOf(config.username, config.password)
                )
            }.getOrNull()
            ProxyKind.HTTP -> runCatching {
                new(
                    "TdApi\$ProxyTypeHttp",
                    arrayOf<Class<*>>(String::class.java, String::class.java, Boolean::class.javaPrimitiveType!!),
                    arrayOf(config.username, config.password, false)
                )
            }.getOrNull()
            ProxyKind.MTPROTO -> runCatching {
                new(
                    "TdApi\$ProxyTypeMtproto",
                    arrayOf<Class<*>>(String::class.java),
                    arrayOf(config.secret)
                )
            }.getOrNull()
            ProxyKind.NONE -> null
        } ?: return false

        fetchProxies(client).mapNotNull { extractProxyId(it) }.forEach { removeProxy(client, it) }

        val fn = runCatching {
            new(
                "TdApi\$AddProxy",
                arrayOf<Class<*>>(String::class.java, Int::class.javaPrimitiveType!!, Boolean::class.javaPrimitiveType!!, td("TdApi\$ProxyType")),
                arrayOf(config.host, config.port, config.enabled, proxyType)
            )
        }.getOrNull() ?: return false

        val handlerType = td("Client\$ResultHandler")
        val exceptionHandlerType = td("Client\$ExceptionHandler")
        val resultHolder = arrayOfNulls<Any>(1)
        val rh = java.lang.reflect.Proxy.newProxyInstance(handlerType.classLoader, arrayOf(handlerType)) { _, _, args ->
            resultHolder[0] = args?.getOrNull(0)
            null
        }
        val eh = java.lang.reflect.Proxy.newProxyInstance(exceptionHandlerType.classLoader, arrayOf(exceptionHandlerType)) { _, _, _ -> null }
        client.sendMethod.invoke(client.client, fn, rh, eh)
        val proxyObj = resultHolder[0]
        val proxyId = extractProxyId(proxyObj)
        if (config.enabled && proxyId != null) {
            enableProxy(client, proxyId)
        }
        return proxyId != null
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

    /** Extract best (largest) chat photo File.id if available. */
    fun extractChatPhotoBigFileId(chatObj: Any): Int? {
        return try {
            val photo = chatObj.javaClass.getDeclaredField("photo").apply { isAccessible = true }.get(chatObj) ?: return null
            val sizes = runCatching { photo.javaClass.getDeclaredField("sizes").apply { isAccessible = true }.get(photo) }.getOrNull()
            val list: List<Any> = when (sizes) {
                is Array<*> -> sizes.filterNotNull().map { it as Any }
                is Iterable<*> -> sizes.filterNotNull().map { it as Any }
                else -> emptyList()
            }
            if (list.isEmpty()) return null
            // choose last (typically the largest variant)
            val last = list.last()
            val pf = runCatching { last.javaClass.getDeclaredField("photo").apply { isAccessible = true }.get(last) }.getOrNull()
            if (pf == null) return null
            // pf should be TdApi.File
            runCatching { pf.javaClass.getDeclaredField("id").apply { isAccessible = true }.getInt(pf) }.getOrNull()
        } catch (_: Throwable) { null }
    }

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
        val set = new("TdApi\$SetTdlibParameters", arrayOf<Class<*>>(td("TdApi\$TdlibParameters")), arrayOf(parameters))
        val handlerType = td("Client\$ResultHandler")
        val exceptionHandlerType = td("Client\$ExceptionHandler")
        val rh = java.lang.reflect.Proxy.newProxyInstance(handlerType.classLoader, arrayOf(handlerType)) { _, _, _ -> null }
        val eh = java.lang.reflect.Proxy.newProxyInstance(exceptionHandlerType.classLoader, arrayOf(exceptionHandlerType)) { _, _, _ -> null }
        client.sendMethod.invoke(client.client, set, rh, eh)
    }

    fun buildPhoneNumberAuthenticationSettings(settings: PhoneAuthSettings): Any? {
        val tokens = settings.authenticationTokens.distinct().take(20).toTypedArray()
        val firebaseClass = runCatching { td("TdApi\$FirebaseAuthenticationSettings") }.getOrNull() ?: return null
        val paramTypes: Array<Class<*>> = arrayOf(
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
                    arrayOf<Class<*>>(
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
            arrayOf<Class<*>>(String::class.java, settingsObj.javaClass),
            arrayOf(phone, settingsObj)
        )
        val handlerType = td("Client\$ResultHandler")
        val exceptionHandlerType = td("Client\$ExceptionHandler")
        // Forward function results (incl. TdApi.Error) to the global update listener
        val rh = java.lang.reflect.Proxy.newProxyInstance(handlerType.classLoader, arrayOf(handlerType)) { _, _, args ->
            val obj = args?.getOrNull(0)
            if (obj != null) dispatchUpdate(obj)
            null
        }
        val eh = java.lang.reflect.Proxy.newProxyInstance(exceptionHandlerType.classLoader, arrayOf(exceptionHandlerType)) { _, _, _ -> null }
        client.sendMethod.invoke(client.client, fn, rh, eh)
        // Proactively request current auth state to refresh UI promptly
        runCatching { sendGetAuthorizationState(client) }
    }

    fun sendCheckCode(client: ClientHandle, code: String) {
        val fn = new("TdApi\$CheckAuthenticationCode", arrayOf<Class<*>>(String::class.java), arrayOf(code))
        val handlerType = td("Client\$ResultHandler")
        val exceptionHandlerType = td("Client\$ExceptionHandler")
        val rh = java.lang.reflect.Proxy.newProxyInstance(handlerType.classLoader, arrayOf(handlerType)) { _, _, args ->
            val obj = args?.getOrNull(0)
            if (obj != null) dispatchUpdate(obj)
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

    fun sendResendAuthenticationCode(client: ClientHandle) {
        val fn = runCatching { new("TdApi\$ResendAuthenticationCode") }.getOrNull() ?: return
        val handlerType = td("Client\$ResultHandler")
        val exceptionHandlerType = td("Client\$ExceptionHandler")
        val rh = java.lang.reflect.Proxy.newProxyInstance(handlerType.classLoader, arrayOf(handlerType)) { _, _, args ->
            val obj = args?.getOrNull(0)
            if (obj != null) dispatchUpdate(obj)
            null
        }
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
        val fn = new("TdApi\$CheckAuthenticationPassword", arrayOf<Class<*>>(String::class.java), arrayOf(password))
        val handlerType = td("Client\$ResultHandler")
        val exceptionHandlerType = td("Client\$ExceptionHandler")
        val rh = java.lang.reflect.Proxy.newProxyInstance(handlerType.classLoader, arrayOf(handlerType)) { _, _, args ->
            val obj = args?.getOrNull(0)
            if (obj != null) dispatchUpdate(obj)
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
            new("TdApi\$DeviceTokenFirebaseCloudMessaging", arrayOf<Class<*>>(String::class.java, Boolean::class.javaPrimitiveType!!, String::class.java), arrayOf(fcmToken, false, ""))
        }.getOrElse {
            runCatching { new("TdApi\$DeviceTokenFirebaseCloudMessaging", arrayOf<Class<*>>(String::class.java, Boolean::class.javaPrimitiveType!!), arrayOf(fcmToken, false)) }.getOrNull()
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
        val fn = runCatching { new("TdApi\$ProcessPushNotification", arrayOf<Class<*>>(String::class.java), arrayOf(payload)) }.getOrNull() ?: return
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

    private fun isTdError(obj: Any): Boolean = PKGS.any { pkg -> obj.javaClass.name == "$pkg.TdApi\$Error" }

    private fun extractTdError(obj: Any): Pair<Int, String> {
        val code = runCatching {
            obj.javaClass.getDeclaredField("code").apply { isAccessible = true }.getInt(obj)
        }.getOrDefault(-1)
        val message = runCatching {
            obj.javaClass.getDeclaredField("message").apply { isAccessible = true }.get(obj) as? String
        }.getOrNull() ?: ""
        return code to message
    }

    private fun shouldRetryTdError(code: Int, message: String): Boolean {
        if (code in 500..599) return true
        if (code == 429 || code == 420) return true
        val normalized = message.lowercase(Locale.getDefault())
        if (normalized.contains("timeout") || normalized.contains("temporarily") || normalized.contains("try again")) return true
        if (normalized.contains("flood") || normalized.contains("too many requests")) return true
        return false
    }

    private fun describeFunction(functionObj: Any, traceTag: String?): String {
        val base = functionObj.javaClass.simpleName.ifBlank {
            functionObj.javaClass.name.substringAfterLast('.')
        }
        return traceTag?.let { "$it/$base" } ?: base
    }

    /** Generic send with one-off result handler that blocks until a result arrives or timeout. */
    fun sendForResult(
        client: ClientHandle,
        functionObj: Any,
        timeoutMs: Long = 7000,
        retries: Int = 2,
        traceTag: String? = null
    ): Any? {
        val handlerType = try { td("Client\$ResultHandler") } catch (_: Throwable) { return null }
        val exceptionHandlerType = td("Client\$ExceptionHandler")
        val operation = describeFunction(functionObj, traceTag)
        var attempt = 0
        var waitMs = timeoutMs.coerceAtLeast(50L)
        while (true) {
            val queue = java.util.concurrent.LinkedBlockingQueue<Any?>(1)
            val resultHandler = java.lang.reflect.Proxy.newProxyInstance(handlerType.classLoader, arrayOf(handlerType)) { _, method, args ->
                if (method?.name == "onResult") {
                    val payload = args?.getOrNull(0) ?: NULL_MARKER
                    queue.offer(payload)
                }
                null
            }
            val exceptionHandler = java.lang.reflect.Proxy.newProxyInstance(exceptionHandlerType.classLoader, arrayOf(exceptionHandlerType)) { _, method, args ->
                if (method?.name == "onException") {
                    val throwable = args?.getOrNull(0) as? Throwable
                    queue.offer(throwable ?: NULL_MARKER)
                }
                null
            }
            try {
                client.sendMethod.invoke(client.client, functionObj, resultHandler, exceptionHandler)
            } catch (e: Throwable) {
                Log.w(LOG_TAG, "sendForResult invoke failed ($operation attempt=${attempt + 1}): ${e.message}")
                if (attempt >= retries) {
                    return null
                }
            }
            val result = try {
                queue.poll(waitMs, TimeUnit.MILLISECONDS)
            } catch (ie: InterruptedException) {
                Thread.currentThread().interrupt()
                return null
            }
            when {
                result == null -> {
                    Log.w(LOG_TAG, "sendForResult timeout after ${waitMs}ms ($operation attempt=${attempt + 1}/${retries + 1})")
                }
                result === NULL_MARKER -> {
                    return null
                }
                result is Throwable -> {
                    Log.w(LOG_TAG, "sendForResult exception ($operation attempt=${attempt + 1}/${retries + 1}): ${result.message}")
                }
                isTdError(result) -> {
                    val (code, message) = extractTdError(result)
                    val retry = attempt < retries && shouldRetryTdError(code, message)
                    Log.w(
                        LOG_TAG,
                        "sendForResult error code=$code message='${message}' ($operation attempt=${attempt + 1}/${retries + 1}, retry=$retry)"
                    )
                    if (!retry) {
                        return null
                    }
                }
                else -> {
                    return result
                }
            }
            if (attempt >= retries) {
                return null
            }
            attempt++
            waitMs = (waitMs + waitMs / 2).coerceAtMost(MAX_BACKOFF_MS)
            try {
                Thread.sleep((100L * (attempt + 1)).coerceAtMost(750L))
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                return null
            }
        }
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
        fun tryFileId(obj: Any?): Int? {
            if (obj == null) return null
            if (PKGS.any { obj.javaClass.name == "$it.TdApi\$File" }) {
                return runCatching { obj.javaClass.getDeclaredField("id").apply { isAccessible = true }.getInt(obj) }.getOrNull()
            }
            // common holder: Thumbnail{ file=File }
            val fileField = runCatching { obj.javaClass.getDeclaredField("file").apply { isAccessible = true }.get(obj) }.getOrNull()
            if (fileField != null) return tryFileId(fileField)
            return null
        }
        // Direct thumbnail on content
        runCatching { contentObj.javaClass.getDeclaredField("thumbnail").apply { isAccessible = true }.get(contentObj) }
            .onSuccess { tryFileId(it)?.let { id -> return id } }
        // Nested media containers
        val candidates = listOf("video", "document", "animation")
        for (name in candidates) {
            val media = runCatching { contentObj.javaClass.getDeclaredField(name).apply { isAccessible = true }.get(contentObj) }.getOrNull()
            if (media != null) {
                val thumb = runCatching { media.javaClass.getDeclaredField("thumbnail").apply { isAccessible = true }.get(media) }.getOrNull()
                val id = tryFileId(thumb)
                if (id != null) return id
            }
        }
        // MessagePhoto: pick largest size
        val photo = runCatching { contentObj.javaClass.getDeclaredField("photo").apply { isAccessible = true }.get(contentObj) }.getOrNull()
        if (photo != null) {
            val sizes = runCatching { photo.javaClass.getDeclaredField("sizes").apply { isAccessible = true }.get(photo) }.getOrNull()
            val array: List<Any> = when (sizes) {
                is Array<*> -> sizes.filterNotNull().map { it as Any }
                is Iterable<*> -> sizes.filterNotNull().map { it as Any }
                else -> emptyList()
            }
            // take the last (often largest)
            val last = array.lastOrNull()
            if (last != null) {
                val p = runCatching { last.javaClass.getDeclaredField("photo").apply { isAccessible = true }.get(last) }.getOrNull()
                val id = tryFileId(p)
                if (id != null) return id
            }
        }
        // Fallback: scan all nested fields recursively
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
                "webm" -> return "video/webm"
                "avi" -> return "video/x-msvideo"
                "mov" -> return "video/quicktime"
                "ts", "m2ts" -> return "video/MP2T"
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

    /**
     * Try to find the primary media file for common content types, preferring the main video/document over thumbnails.
     * Falls back to [findFirstFile] if no prioritized path is found.
     */
    fun findPrimaryFile(contentObj: Any?): Any? {
        if (contentObj == null) return null
        // Heuristic: look for nested fields named 'video', 'document', 'animation', 'audio' first and try their internal 'file' (or 'video')
        fun tryField(obj: Any?, name: String, inner: String = "file"): Any? {
            if (obj == null) return null
            val fld = runCatching { obj.javaClass.getDeclaredField(name).apply { isAccessible = true }.get(obj) }.getOrNull() ?: return null
            // nested file inside (e.g., Video.video, Document.document)
            val innerVal = runCatching { fld.javaClass.getDeclaredField(inner).apply { isAccessible = true }.get(fld) }.getOrNull()
            if (innerVal != null && PKGS.any { innerVal.javaClass.name == "$it.TdApi\$File" }) return innerVal
            return findFirstFile(fld)
        }
        // MessageVideo: content.video.video (File)
        tryField(contentObj, "video", inner = "video")?.let { return it }
        // MessageDocument: content.document.document (File)
        tryField(contentObj, "document", inner = "document")?.let { return it }
        // MessageAnimation: content.animation.video (File)
        tryField(contentObj, "animation", inner = "video")?.let { return it }
        // MessageAudio: content.audio.audio (File)
        tryField(contentObj, "audio", inner = "audio")?.let { return it }
        return findFirstFile(contentObj)
    }
}
