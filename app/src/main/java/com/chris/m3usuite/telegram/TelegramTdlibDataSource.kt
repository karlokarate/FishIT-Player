package com.chris.m3usuite.telegram

import android.content.Context
import android.net.Uri
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import com.chris.m3usuite.telegram.TdLibReflection.AuthState
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import java.io.IOException
import java.io.File
import java.io.RandomAccessFile

/**
 * Streaming DataSource that uses TDLib (via reflection) to download a Telegram message file on demand
 * and serves reads from the growing local file. Supports seeking (via DataSpec.position).
 * Falls back to [fallbackFactory] if TDLib is unavailable; otherwise attempts best-effort.
 */
@UnstableApi
class TelegramTdlibDataSource(
    private val context: Context,
    private val fallbackFactory: DataSource.Factory,
    private val routingFallback: DataSource.Factory = TelegramRoutingDataSource.Factory(context, fallbackFactory),
    private val notify: ((String) -> Unit)? = null
) : DataSource {

    private var opened = false
    private var raf: RandomAccessFile? = null
    private var currentUri: Uri? = null
    private var expectedSize: Long = -1
    private var completed: Boolean = false
    private var fileId: Int = -1

    private var delegate: DataSource? = null
    private var listener: TransferListener? = null
    private var authFallbackNotified: Boolean = false
    private var activeClient: TdLibReflection.ClientHandle? = null
    private var requireCompleteDownload: Boolean = false
    private var currentChatId: Long = 0L
    private var currentMessageId: Long = 0L

    override fun addTransferListener(transferListener: TransferListener) { listener = transferListener }

    override fun getUri(): Uri? = currentUri ?: delegate?.uri

    override fun open(dataSpec: DataSpec): Long {
        val uri = dataSpec.uri
        currentUri = uri
        if (uri.scheme?.lowercase() != "tg") {
            val d = fallbackFactory.createDataSource().also {
                delegate = it
                listener?.let(it::addTransferListener)
            }
            opened = true
            return d.open(dataSpec)
        }

        val chatId = uri.getQueryParameter("chatId")?.toLongOrNull()
        val msgId = uri.getQueryParameter("messageId")?.toLongOrNull()
        if (chatId == null || msgId == null) throw IOException("tg:// uri missing chatId/messageId")

        currentChatId = chatId
        currentMessageId = msgId
        requireCompleteDownload = false

        val store = com.chris.m3usuite.prefs.SettingsStore(context)
        val enabled = runBlocking { store.tgEnabled.first() }
        val tdAvailable = TdLibReflection.available()
        val authFlow = kotlinx.coroutines.flow.MutableStateFlow(AuthState.UNKNOWN)
        val client = if (tdAvailable) TdLibReflection.getOrCreateClient(context, authFlow) else null
        activeClient = client

        val fallback: (String?, Boolean) -> Long = { message, markAuth ->
            activeClient = null
            openFallback(dataSpec, message, markAuth)
        }

        if (!enabled || !tdAvailable || client == null) {
            val message = if (!enabled) {
                "Telegram deaktiviert – lokale Dateien werden verwendet"
            } else {
                "Telegram nicht verbunden – lokale Dateien werden verwendet"
            }
            return fallback(message, markAuth = true)
        }

        val authOk = runCatching {
            val fn = TdLibReflection.buildGetAuthorizationState()
            val obj = fn?.let {
                TdLibReflection.sendForResult(client, it, timeoutMs = 700, retries = 1, traceTag = "AuthState")
            }
            TdLibReflection.mapAuthorizationState(obj) == AuthState.AUTHENTICATED
        }.getOrDefault(false)
        if (!authOk) {
            return fallback("Telegram nicht verbunden – lokale Dateien werden verwendet", markAuth = true)
        }

        val cachedRow = fetchMessageRow(chatId, msgId)
        val cachedPath = cachedRow?.localPath?.takeIf { !it.isNullOrBlank() && File(it).exists() }
        val cachedSupports = cachedRow?.supportsStreaming
        val cachedMime = cachedRow?.mimeType
        val cachedSize = cachedRow?.sizeBytes ?: -1L
        val cachedFileId = cachedRow?.fileId ?: -1
        val cachedName = cachedRow?.fileName

        val getMsg = TdLibReflection.buildGetMessage(chatId, msgId)
            ?: return fallback("Telegram-Datei konnte nicht vorbereitet werden", markAuth = false)
        val msgObj = TdLibReflection.sendForResult(
            client,
            getMsg,
            timeoutMs = 5_000,
            retries = 2,
            traceTag = "GetMessage[$chatId/$msgId]"
        ) ?: return fallback("Telegram-Datei nicht abrufbar – lokale Kopie wird verwendet", markAuth = false)

        val message = if (msgObj.javaClass.name.endsWith("TdApi\$Message")) msgObj else runCatching {
            msgObj.javaClass.getDeclaredField("message").apply { isAccessible = true }.get(msgObj)
        }.getOrElse { msgObj }
        val content = runCatching {
            message.javaClass.getDeclaredField("content").apply { isAccessible = true }.get(message)
        }.getOrNull()
        val fileObj = TdLibReflection.findPrimaryFile(content)
            ?: return fallback("Telegram-Datei ohne Mediendaten – lokale Kopie wird verwendet", markAuth = false)
        var info = TdLibReflection.extractFileInfo(fileObj)
            ?: return fallback("Telegram-Datei konnte nicht gelesen werden", markAuth = false)
        fileId = info.fileId.takeIf { it > 0 } ?: cachedFileId
        if (fileId <= 0) {
            return fallback("Telegram-Datei ohne File-ID – lokale Kopie wird verwendet", markAuth = false)
        }

        val supportsStreaming = TdLibReflection.extractSupportsStreaming(content) ?: cachedSupports
        val mimeType = TdLibReflection.extractMimeType(content) ?: cachedMime
        val fileName = TdLibReflection.extractFileName(content) ?: cachedName

        val isProgressive = isLikelyProgressiveContainer(mimeType, fileName)
        val canStream = when {
            supportsStreaming == true -> true
            supportsStreaming == false && !isProgressive -> false
            else -> true
        }
        requireCompleteDownload = !canStream
        if (requireCompleteDownload && cachedPath == null) {
            showUserMessage("Telegram-Video wird vollständig heruntergeladen, bevor es startet …")
        }

        val offset = if (canStream) dataSpec.position.coerceAtLeast(0L).toInt() else 0
        val priority = if (requireCompleteDownload) 64 else 32
        TdLibReflection.buildDownloadFile(fileId, priority, offset, 0, false)?.let { fn ->
            TdLibReflection.sendForResult(client, fn, timeoutMs = 800, retries = 1, traceTag = "DownloadFile[$fileId]")
        }

        val waited = waitForFile(
            client = client,
            fileId = fileId,
            initial = info,
            expectCompletion = requireCompleteDownload,
            maxWaitMs = if (requireCompleteDownload) 180_000 else 60_000,
            traceTag = "GetFile[$fileId]"
        )
        info = waited

        val path = when {
            !info.localPath.isNullOrBlank() && File(info.localPath).exists() -> info.localPath
            cachedPath != null -> cachedPath
            else -> null
        }
        val file = path?.let { File(it) }
        if (file == null || !file.exists()) {
            return fallback("Telegram-Datei nicht lokal verfügbar – lokale Kopie wird verwendet", markAuth = false)
        }

        persistLocalPath(chatId, msgId, file.absolutePath)

        expectedSize = when {
            info.expectedSize > 0 -> info.expectedSize
            cachedSize > 0 -> cachedSize
            else -> -1
        }
        completed = info.downloadingCompleted || (requireCompleteDownload && expectedSize > 0 && file.length() >= expectedSize)

        try {
            raf = RandomAccessFile(file, "r")
            if (dataSpec.position > 0) {
                raf!!.seek(dataSpec.position)
            }
        } catch (e: Throwable) {
            return fallback("Telegram-Datei konnte nicht geöffnet werden – lokale Kopie wird verwendet", markAuth = false)
        }
        opened = true
        return if (expectedSize > 0) kotlin.math.max(0L, expectedSize - dataSpec.position) else -1L
    }

    override fun read(buffer: ByteArray, offset: Int, readLength: Int): Int {
        delegate?.let { return it.read(buffer, offset, readLength) }
        val fileHandle = raf ?: throw IllegalStateException("not opened")
        if (readLength == 0) return 0

        var idleLoops = 0
        while (true) {
            val length = runCatching { fileHandle.length() }.getOrDefault(0L)
            val available = length - fileHandle.filePointer
            if (available > 0) {
                val toRead = kotlin.math.min(readLength.toLong(), available).toInt()
                return try {
                    fileHandle.read(buffer, offset, toRead)
                } catch (e: Throwable) {
                    throw IOException(e)
                }
            }
            if (!completed && expectedSize > 0 && length >= expectedSize) {
                completed = true
            }
            if (completed) return -1

            val client = if (fileId > 0 && TdLibReflection.available()) {
                activeClient ?: TdLibReflection.getOrCreateClient(context, kotlinx.coroutines.flow.MutableStateFlow(AuthState.UNKNOWN)).also {
                    if (it != null) activeClient = it
                }
            } else null
            if (client != null) {
                val fn = TdLibReflection.buildGetFile(fileId)
                val result = fn?.let {
                    TdLibReflection.sendForResult(client, it, timeoutMs = 1_000, retries = 1, traceTag = "Read:GetFile[$fileId]")
                }
                val info = result?.let { TdLibReflection.extractFileInfo(it) }
                if (info != null) {
                    completed = info.downloadingCompleted
                    if (expectedSize <= 0 && info.expectedSize > 0) {
                        expectedSize = info.expectedSize
                    }
                    if (!info.localPath.isNullOrBlank() && File(info.localPath).exists()) {
                        persistLocalPath(currentChatId, currentMessageId, info.localPath!!)
                    }
                }
            }

            idleLoops++
            val sleep = when {
                idleLoops < 5 -> 180L
                idleLoops < 15 -> 260L
                else -> 400L
            }
            try {
                Thread.sleep(sleep)
            } catch (ie: InterruptedException) {
                Thread.currentThread().interrupt()
                throw IOException("interrupted")
            }
        }
    }

    override fun close() {
        runCatching { delegate?.close() }
        delegate = null
        runCatching { raf?.close() }
        raf = null
        if (fileId > 0 && !completed && TdLibReflection.available()) {
            runCatching {
                val client = activeClient ?: TdLibReflection.getOrCreateClient(
                    context,
                    kotlinx.coroutines.flow.MutableStateFlow(AuthState.UNKNOWN)
                )?.also { activeClient = it }
                val cancel = TdLibReflection.buildCancelDownloadFile(fileId, /* onlyIfPending = */ false)
                if (client != null && cancel != null) {
                    TdLibReflection.sendForResult(client, cancel, timeoutMs = 500, retries = 1, traceTag = "CancelDownload[$fileId]")
                }
            }
        }
        opened = false
        if (!completed && expectedSize > 0 && fileId > 0) {
            // leave TDLib to continue download in background to finish caching
        }
        fileId = -1
        currentChatId = 0L
        currentMessageId = 0L
        requireCompleteDownload = false
        activeClient = null
    }

    private fun fetchMessageRow(chatId: Long, messageId: Long): com.chris.m3usuite.data.obx.ObxTelegramMessage? = runBlocking {
        runCatching {
            val box = com.chris.m3usuite.data.obx.ObxStore.get(context).boxFor(com.chris.m3usuite.data.obx.ObxTelegramMessage::class.java)
            val query = box.query(
                com.chris.m3usuite.data.obx.ObxTelegramMessage_.chatId.equal(chatId)
                    .and(com.chris.m3usuite.data.obx.ObxTelegramMessage_.messageId.equal(messageId))
            ).build()
            val result = query.findFirst()
            query.close()
            result
        }.getOrNull()
    }

    private fun persistLocalPath(chatId: Long, messageId: Long, path: String) {
        runBlocking {
            runCatching {
                val box = com.chris.m3usuite.data.obx.ObxStore.get(context).boxFor(com.chris.m3usuite.data.obx.ObxTelegramMessage::class.java)
                val query = box.query(
                    com.chris.m3usuite.data.obx.ObxTelegramMessage_.chatId.equal(chatId)
                        .and(com.chris.m3usuite.data.obx.ObxTelegramMessage_.messageId.equal(messageId))
                ).build()
                val row = query.findFirst() ?: com.chris.m3usuite.data.obx.ObxTelegramMessage(chatId = chatId, messageId = messageId)
                query.close()
                if (row.localPath != path) {
                    row.localPath = path
                    box.put(row)
                }
            }
        }
    }

    private fun isLikelyProgressiveContainer(mimeType: String?, fileName: String?): Boolean {
        val mime = mimeType?.lowercase() ?: ""
        if (mime.startsWith("video/")) return true
        if (mime.contains("x-matroska") || mime.contains("matroska") || mime.contains("webm") || mime.contains("quicktime") || mime.contains("x-msvideo") || mime.contains("mp2t")) {
            return true
        }
        val name = fileName?.lowercase() ?: return false
        return listOf(".mp4", ".m4v", ".mkv", ".webm", ".mov", ".avi", ".ts", ".m2ts", ".mpg", ".mpeg").any { name.endsWith(it) }
    }

    private fun waitForFile(
        client: TdLibReflection.ClientHandle,
        fileId: Int,
        initial: TdLibReflection.FileInfo,
        expectCompletion: Boolean,
        maxWaitMs: Long,
        traceTag: String
    ): TdLibReflection.FileInfo {
        var info = initial
        val deadline = android.os.SystemClock.elapsedRealtime() + maxWaitMs
        var sleepMs = 200L
        while (android.os.SystemClock.elapsedRealtime() <= deadline) {
            val pathReady = !info.localPath.isNullOrBlank() && File(info.localPath).exists()
            if (pathReady && (!expectCompletion || info.downloadingCompleted)) {
                return info
            }
            val fn = TdLibReflection.buildGetFile(fileId)
            val result = fn?.let {
                TdLibReflection.sendForResult(client, it, timeoutMs = 1_200, retries = 2, traceTag = traceTag)
            }
            val updated = result?.let { TdLibReflection.extractFileInfo(it) }
            if (updated != null) {
                info = updated
                val updatedReady = !info.localPath.isNullOrBlank() && File(info.localPath).exists()
                if (updatedReady && (!expectCompletion || info.downloadingCompleted)) {
                    return info
                }
            }
            try {
                Thread.sleep(sleepMs)
            } catch (ie: InterruptedException) {
                Thread.currentThread().interrupt()
                break
            }
            sleepMs = (sleepMs + sleepMs / 4).coerceAtMost(2_000L)
        }
        return info
    }

    private fun openFallback(dataSpec: DataSpec, message: String?, markAuth: Boolean): Long {
        if (!message.isNullOrBlank()) {
            if (markAuth) {
                if (!authFallbackNotified) {
                    showUserMessage(message)
                    authFallbackNotified = true
                }
            } else {
                showUserMessage(message)
            }
        }
        val d = routingFallback.createDataSource().also {
            delegate = it
            listener?.let(it::addTransferListener)
        }
        opened = true
        return d.open(dataSpec)
    }

    private fun showUserMessage(message: String) {
        if (notify != null) {
            notify.invoke(message)
        } else {
            runCatching {
                android.widget.Toast.makeText(context.applicationContext, message, android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    class Factory(
        private val context: Context,
        private val fallback: DataSource.Factory,
        private val notify: ((String) -> Unit)? = null
    ) : DataSource.Factory {
        override fun createDataSource(): DataSource = TelegramTdlibDataSource(context, fallback, TelegramRoutingDataSource.Factory(context, fallback), notify)
    }
}
