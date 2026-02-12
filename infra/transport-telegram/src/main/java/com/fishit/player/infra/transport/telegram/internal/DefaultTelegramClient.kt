package com.fishit.player.infra.transport.telegram.internal

import com.fishit.player.infra.logging.UnifiedLog
import com.fishit.player.infra.transport.telegram.ResolvedTelegramMedia
import com.fishit.player.infra.transport.telegram.TelegramClient
import com.fishit.player.infra.transport.telegram.TelegramRemoteId
import com.fishit.player.infra.transport.telegram.TgFileUpdate
import com.fishit.player.infra.transport.telegram.TgStorageStats
import com.fishit.player.infra.transport.telegram.TgThumbnailRef
import com.fishit.player.infra.transport.telegram.api.TransportAuthState
import com.fishit.player.infra.transport.telegram.api.TgChat
import com.fishit.player.infra.transport.telegram.api.TgContent
import com.fishit.player.infra.transport.telegram.api.TgFile
import com.fishit.player.infra.transport.telegram.api.TgMessage
import com.fishit.player.infra.transport.telegram.api.TgThumbnail
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.longOrNull
import java.io.File

/**
 * Proxy-based implementation of [TelegramClient] using the Telethon HTTP sidecar.
 *
 * Replaces the former Telegram API-based DefaultTelegramClient. All Telegram operations
 * are delegated to the Telethon Python proxy via HTTP calls through [TelethonProxyClient].
 *
 * **Architecture:**
 * ```
 * Kotlin → OkHttp → localhost:8089 → Python asyncio → Telethon → Telegram MTProto
 * ```
 *
 * **SSOT:** This is the SINGLE implementation of [TelegramClient]. All typed
 * interfaces in Hilt resolve to this same instance.
 */
class DefaultTelegramClient(
    private val proxyClient: TelethonProxyClient,
    private val proxyLifecycle: TelethonProxyLifecycle,
    private val cacheDir: File,
) : TelegramClient {

    companion object {
        private const val TAG = "DefaultTelegramClient"
    }

    // ── Auth ────────────────────────────────────────────────────────────────

    private val _authState = MutableStateFlow<TransportAuthState>(TransportAuthState.Connecting)

    /** Phone number from the last [sendPhoneNumber] call, needed for [sendCode]. */
    private var lastPhone: String? = null
    /** Phone code hash from the last [sendPhoneNumber] call, needed for [sendCode]. */
    private var lastPhoneCodeHash: String? = null

    override val authState: Flow<TransportAuthState> = _authState.asStateFlow()

    override suspend fun ensureAuthorized(): Unit = withContext(Dispatchers.IO) {
        proxyLifecycle.start()
        val ready = proxyLifecycle.awaitReady()
        if (!ready) {
            UnifiedLog.e(TAG) { "Proxy failed to become ready" }
            _authState.value = TransportAuthState.Connecting
            return@withContext
        }

        val authorized = proxyClient.getAuthStatus()
        val newState = if (authorized) {
            TransportAuthState.Ready
        } else {
            TransportAuthState.WaitPhoneNumber()
        }
        UnifiedLog.i(TAG) { "ensureAuthorized → ${newState::class.simpleName}" }
        _authState.value = newState
    }

    override suspend fun isAuthorized(): Boolean = withContext(Dispatchers.IO) {
        try { proxyClient.getAuthStatus() } catch (e: Exception) {
            UnifiedLog.w(TAG) { "isAuthorized check failed: ${e.message}" }
            false
        }
    }

    override suspend fun sendPhoneNumber(phoneNumber: String): Unit = withContext(Dispatchers.IO) {
        UnifiedLog.d(TAG) { "sendPhoneNumber: ***${phoneNumber.takeLast(4)}" }
        val phoneCodeHash = proxyClient.sendPhone(phoneNumber)
        lastPhone = phoneNumber
        lastPhoneCodeHash = phoneCodeHash
        _authState.value = TransportAuthState.WaitCode()
    }

    override suspend fun sendCode(code: String): Unit = withContext(Dispatchers.IO) {
        val phone = lastPhone ?: error("sendPhoneNumber must be called before sendCode")
        val hash = lastPhoneCodeHash ?: error("sendPhoneNumber must be called before sendCode")
        try {
            proxyClient.sendCode(phone, code, hash)
            UnifiedLog.i(TAG) { "sendCode → Ready" }
            _authState.value = TransportAuthState.Ready
        } catch (e: TelethonProxyException) {
            if (e.httpCode == 401 || e.message?.contains("password", ignoreCase = true) == true) {
                UnifiedLog.i(TAG) { "sendCode → WaitPassword (2FA required)" }
                _authState.value = TransportAuthState.WaitPassword()
            } else {
                UnifiedLog.e(TAG, e) { "sendCode failed" }
                throw e
            }
        }
    }

    override suspend fun sendPassword(password: String): Unit = withContext(Dispatchers.IO) {
        proxyClient.sendPassword(password)
        UnifiedLog.i(TAG) { "sendPassword → Ready" }
        _authState.value = TransportAuthState.Ready
    }

    override suspend fun logout(): Unit = withContext(Dispatchers.IO) {
        proxyClient.logout()
        UnifiedLog.i(TAG) { "logout → LoggedOut" }
        _authState.value = TransportAuthState.LoggedOut
    }

    override suspend fun getCurrentUserId(): Long? = withContext(Dispatchers.IO) {
        val id = proxyClient.getCurrentUserId()
        if (id > 0) id else null
    }

    // ── History ─────────────────────────────────────────────────────────────

    private val _messageUpdates = MutableStateFlow<TgMessage?>(null)

    override val messageUpdates: Flow<TgMessage>
        get() = _messageUpdates.filterNotNull()

    override suspend fun getChats(limit: Int): List<TgChat> = withContext(Dispatchers.IO) {
        proxyClient.getChats(limit).map { element ->
            val obj = element.jsonObject
            TgChat(
                chatId = obj["id"]!!.jsonPrimitive.long,
                title = obj["title"]?.jsonPrimitive?.content,
                type = "unknown",
                memberCount = obj["memberCount"]?.jsonPrimitive?.int ?: 0,
            )
        }
    }

    override suspend fun getChat(chatId: Long): TgChat? = withContext(Dispatchers.IO) {
        try {
            val obj = proxyClient.getChat(chatId)
            TgChat(
                chatId = obj["id"]!!.jsonPrimitive.long,
                title = obj["title"]?.jsonPrimitive?.content,
                type = "unknown",
                memberCount = obj["memberCount"]?.jsonPrimitive?.int ?: 0,
            )
        } catch (e: Exception) {
            UnifiedLog.w(TAG) { "getChat($chatId) failed: ${e.message}" }
            null
        }
    }

    override suspend fun fetchMessages(
        chatId: Long,
        limit: Int,
        fromMessageId: Long,
        offset: Int,
    ): List<TgMessage> = withContext(Dispatchers.IO) {
        proxyClient.getMessages(chatId, limit, fromMessageId.toInt())
            .map { MessageParser.parse(it.jsonObject) }
    }

    override suspend fun loadAllMessages(
        chatId: Long,
        pageSize: Int,
        maxMessages: Int,
        onProgress: ((loaded: Int) -> Unit)?,
    ): List<TgMessage> = withContext(Dispatchers.IO) {
        val all = mutableListOf<TgMessage>()
        var offsetId = 0

        while (all.size < maxMessages) {
            val page = proxyClient.getMessages(chatId, pageSize, offsetId)
            if (page.isEmpty()) break

            val messages = page.map { MessageParser.parse(it.jsonObject) }
            all.addAll(messages)
            onProgress?.invoke(all.size)

            offsetId = messages.last().messageId.toInt()
            if (messages.size < pageSize) break
        }
        all
    }

    override suspend fun searchMessages(
        chatId: Long,
        query: String,
        limit: Int,
    ): List<TgMessage> = withContext(Dispatchers.IO) {
        proxyClient.searchMessages(chatId, query, limit)
            .map { MessageParser.parse(it.jsonObject) }
    }

    // ── File ────────────────────────────────────────────────────────────────

    private val _fileUpdates = MutableStateFlow<TgFileUpdate?>(null)

    override val fileUpdates: Flow<TgFileUpdate>
        get() = _fileUpdates.filterNotNull()

    override suspend fun startDownload(fileId: Int, priority: Int, offset: Long, limit: Long) {
        // No-op: In proxy architecture, downloads are HTTP-streamed on demand via /file endpoint
    }

    override suspend fun cancelDownload(fileId: Int, deleteLocalCopy: Boolean) {
        // No-op: No Telegram API download queue in proxy architecture
    }

    override suspend fun getFile(fileId: Int): TgFile? = null

    override suspend fun resolveRemoteId(remoteId: String): TgFile? = null

    override suspend fun getDownloadedPrefixSize(fileId: Int): Long = 0L

    override suspend fun getStorageStats(): TgStorageStats =
        TgStorageStats(totalSize = 0L, photoCount = 0, videoCount = 0, documentCount = 0, audioCount = 0, otherCount = 0)

    override suspend fun optimizeStorage(maxSizeBytes: Long, maxAgeDays: Int): Long = 0L

    // ── Thumbnails ──────────────────────────────────────────────────────────

    override suspend fun fetchThumbnail(thumbRef: TgThumbnailRef): String? = withContext(Dispatchers.IO) {
        val (chatId, messageId) = parseRemoteIdParts(thumbRef.remoteId) ?: return@withContext null
        val bytes = proxyClient.getThumbnail(chatId, messageId) ?: return@withContext null

        try {
            val thumbDir = File(cacheDir, "thumbs")
            thumbDir.mkdirs()
            val cacheFile = File(thumbDir, "${chatId}_${messageId}.jpg")
            cacheFile.writeBytes(bytes)
            UnifiedLog.d(TAG) { "fetchThumbnail: cached ${bytes.size} bytes → ${cacheFile.name}" }
            cacheFile.absolutePath
        } catch (e: Exception) {
            UnifiedLog.e(TAG, e) { "fetchThumbnail: disk write failed for chat=$chatId msg=$messageId" }
            null
        }
    }

    override suspend fun isCached(thumbRef: TgThumbnailRef): Boolean {
        val (chatId, messageId) = parseRemoteIdParts(thumbRef.remoteId) ?: return false
        return File(cacheDir, "thumbs/${chatId}_${messageId}.jpg").exists()
    }

    override suspend fun prefetch(thumbRefs: List<TgThumbnailRef>) {
        thumbRefs.forEach { ref -> runCatching { fetchThumbnail(ref) } }
    }

    override suspend fun clearFailedCache() {
        File(cacheDir, "thumbs").deleteRecursively()
    }

    // ── Remote Resolver ─────────────────────────────────────────────────────

    override suspend fun resolveMedia(remoteId: TelegramRemoteId): ResolvedTelegramMedia? =
        withContext(Dispatchers.IO) {
            try {
                val info = proxyClient.getFileInfo(remoteId.chatId, remoteId.messageId)
                ResolvedTelegramMedia(
                    mediaFileId = 0,
                    mimeType = info["mimeType"]?.jsonPrimitive?.content,
                    durationSecs = info["duration"]?.jsonPrimitive?.int,
                    sizeBytes = info["fileSize"]?.jsonPrimitive?.longOrNull ?: 0L,
                    width = info["width"]?.jsonPrimitive?.int ?: 0,
                    height = info["height"]?.jsonPrimitive?.int ?: 0,
                    supportsStreaming = info["supportsStreaming"]?.jsonPrimitive?.content?.toBoolean() ?: false,
                )
            } catch (e: Exception) {
                UnifiedLog.w(TAG) { "resolveMedia(chat=${remoteId.chatId}, msg=${remoteId.messageId}) failed: ${e.message}" }
                null
            }
        }

    // ── Internal ────────────────────────────────────────────────────────────

    private fun parseRemoteIdParts(remoteId: String): Pair<Long, Long>? {
        val parts = if (remoteId.startsWith("msg:")) {
            remoteId.removePrefix("msg:").split(":")
        } else {
            remoteId.split(":")
        }
        if (parts.size < 2) return null
        val chatId = parts[0].toLongOrNull() ?: return null
        val messageId = parts[1].toLongOrNull() ?: return null
        return chatId to messageId
    }
}

/**
 * Parses JSON objects from the Telethon proxy into [TgMessage] DTOs.
 */
internal object MessageParser {

    fun parse(obj: kotlinx.serialization.json.JsonObject): TgMessage {
        val content = obj["content"]?.jsonObject?.let { c ->
            val type = c["type"]?.jsonPrimitive?.content
            when (type) {
                "video" -> TgContent.Video(
                    fileId = 0,
                    remoteId = c["remoteId"]?.jsonPrimitive?.content ?: "",
                    mimeType = c["mimeType"]?.jsonPrimitive?.content ?: "video/mp4",
                    duration = c["duration"]?.jsonPrimitive?.int ?: 0,
                    width = c["width"]?.jsonPrimitive?.int ?: 0,
                    height = c["height"]?.jsonPrimitive?.int ?: 0,
                    fileSize = c["fileSize"]?.jsonPrimitive?.longOrNull ?: 0L,
                    fileName = c["fileName"]?.jsonPrimitive?.content,
                    supportsStreaming = c["supportsStreaming"]?.jsonPrimitive?.content?.toBoolean() ?: false,
                    thumbnail = c["thumbnail"]?.jsonObject?.let { t ->
                        TgThumbnail(
                            fileId = 0, remoteId = "",
                            width = t["width"]?.jsonPrimitive?.int ?: 0,
                            height = t["height"]?.jsonPrimitive?.int ?: 0,
                            fileSize = 0L,
                        )
                    },
                    caption = c["caption"]?.jsonPrimitive?.content ?: "",
                )
                "audio" -> TgContent.Audio(
                    fileId = 0,
                    remoteId = c["remoteId"]?.jsonPrimitive?.content ?: "",
                    mimeType = c["mimeType"]?.jsonPrimitive?.content ?: "audio/mpeg",
                    duration = c["duration"]?.jsonPrimitive?.int ?: 0,
                    fileSize = c["fileSize"]?.jsonPrimitive?.longOrNull ?: 0L,
                    fileName = c["fileName"]?.jsonPrimitive?.content,
                    title = null, performer = null, thumbnail = null,
                    caption = c["caption"]?.jsonPrimitive?.content ?: "",
                )
                "document" -> TgContent.Document(
                    fileId = 0,
                    remoteId = c["remoteId"]?.jsonPrimitive?.content ?: "",
                    mimeType = c["mimeType"]?.jsonPrimitive?.content ?: "application/octet-stream",
                    fileSize = c["fileSize"]?.jsonPrimitive?.longOrNull ?: 0L,
                    fileName = c["fileName"]?.jsonPrimitive?.content,
                    thumbnail = null,
                    caption = c["caption"]?.jsonPrimitive?.content ?: "",
                )
                "animation" -> TgContent.Animation(
                    fileId = 0,
                    remoteId = c["remoteId"]?.jsonPrimitive?.content ?: "",
                    mimeType = c["mimeType"]?.jsonPrimitive?.content ?: "video/mp4",
                    duration = c["duration"]?.jsonPrimitive?.int ?: 0,
                    width = c["width"]?.jsonPrimitive?.int ?: 0,
                    height = c["height"]?.jsonPrimitive?.int ?: 0,
                    fileSize = c["fileSize"]?.jsonPrimitive?.longOrNull ?: 0L,
                    fileName = c["fileName"]?.jsonPrimitive?.content,
                    thumbnail = null,
                    caption = c["caption"]?.jsonPrimitive?.content ?: "",
                )
                "photo" -> TgContent.Photo(
                    sizes = emptyList(),
                    caption = c["caption"]?.jsonPrimitive?.content ?: "",
                )
                else -> null
            }
        }

        return TgMessage(
            messageId = obj["id"]!!.jsonPrimitive.long,
            chatId = obj["chatId"]?.jsonPrimitive?.longOrNull ?: 0L,
            date = parseIsoDate(obj["date"]?.jsonPrimitive?.content),
            content = content,
        )
    }

    /** Parse ISO-8601 date string to epoch seconds, or 0 on failure. */
    private fun parseIsoDate(iso: String?): Long {
        if (iso == null) return 0L
        return try {
            java.time.Instant.parse(iso).epochSecond
        } catch (_: Exception) {
            try {
                java.time.OffsetDateTime.parse(iso).toEpochSecond()
            } catch (_: Exception) {
                0L
            }
        }
    }
}
