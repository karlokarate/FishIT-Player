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

    override fun addTransferListener(transferListener: TransferListener) { listener = transferListener }

    override fun getUri(): Uri? = currentUri ?: delegate?.uri

    override fun open(dataSpec: DataSpec): Long {
        val uri = dataSpec.uri
        currentUri = uri
        if (uri.scheme?.lowercase() != "tg") {
            val d = fallbackFactory.createDataSource().also { delegate = it; listener?.let(it::addTransferListener) }
            opened = true
            return d.open(dataSpec)
        }

        // Gate by Settings flag first (avoid any TDLib touch when disabled)
        val store = com.chris.m3usuite.prefs.SettingsStore(context)
        val enabled = runBlocking { store.tgEnabled.first() }
        if (!enabled) {
            val d = routingFallback.createDataSource().also { delegate = it; listener?.let(it::addTransferListener) }
            opened = true
            return d.open(dataSpec)
        }

        // Need TDLib presence
        if (!TdLibReflection.available()) {
            val d = routingFallback.createDataSource().also { delegate = it; listener?.let(it::addTransferListener) }
            opened = true
            return d.open(dataSpec)
        }

        val authFlow = kotlinx.coroutines.flow.MutableStateFlow(AuthState.UNKNOWN)
        val client = TdLibReflection.getOrCreateClient(context, authFlow)
        if (client == null) {
            val d = routingFallback.createDataSource().also { delegate = it; listener?.let(it::addTransferListener) }
            opened = true
            return d.open(dataSpec)
        }

        val chatId = uri.getQueryParameter("chatId")?.toLongOrNull()
        val msgId = uri.getQueryParameter("messageId")?.toLongOrNull()
        if (chatId == null || msgId == null) throw IOException("tg:// uri missing chatId/messageId")

        // Ensure authenticated before using TDLib
        runCatching {
            val a = TdLibReflection.buildGetAuthorizationState()?.let { TdLibReflection.sendForResult(client, it, 500) }
            val st = TdLibReflection.mapAuthorizationState(a)
            if (st != AuthState.AUTHENTICATED) throw IOException("TDLib not authenticated")
        }.onFailure {
            if (!authFallbackNotified) {
                val msg = "Telegram nicht verbunden – lokale Dateien werden verwendet"
                if (notify != null) notify.invoke(msg) else try { android.widget.Toast.makeText(context.applicationContext, msg, android.widget.Toast.LENGTH_SHORT).show() } catch (_: Throwable) {}
                authFallbackNotified = true
            }
            val d = routingFallback.createDataSource().also { delegate = it; listener?.let(it::addTransferListener) }
            opened = true
            return d.open(dataSpec)
        }

        // 1) Get message -> extract file
        val getMsg = TdLibReflection.buildGetMessage(chatId, msgId) ?: throw IOException("tdlib build GetMessage failed")
        val msgObj = TdLibReflection.sendForResult(client, getMsg) ?: throw IOException("tdlib GetMessage timeout")
        // message has field 'message' or is directly TdApi.Message
        val message = if (msgObj.javaClass.name.endsWith("TdApi\$Message")) msgObj else runCatching {
            msgObj.javaClass.getDeclaredField("message").apply { isAccessible = true }.get(msgObj)
        }.getOrElse { msgObj }
        val content = runCatching { message.javaClass.getDeclaredField("content").apply { isAccessible = true }.get(message) }.getOrNull()
        val fileObj = TdLibReflection.findFirstFile(content) ?: throw IOException("tdlib: no file in message content")
        val initialInfo = TdLibReflection.extractFileInfo(fileObj) ?: throw IOException("tdlib: cannot read file info")
        fileId = initialInfo.fileId

        // 2) Start/continue download from requested offset
        val desiredOffset = dataSpec.position.toInt()
        val dl = TdLibReflection.buildDownloadFile(fileId, 32, desiredOffset, 0, false)
        if (dl != null) TdLibReflection.sendForResult(client, dl, 100)

        // 3) Resolve local path & open RAF
        var info = initialInfo
        var attempts = 0
        while ((info.localPath.isNullOrBlank() || !java.io.File(info.localPath).exists()) && attempts < 50) {
            Thread.sleep(100)
            val getFile = TdLibReflection.buildGetFile(fileId) ?: break
            val f = TdLibReflection.sendForResult(client, getFile) ?: break
            TdLibReflection.extractFileInfo(f)?.let { info = it }
            attempts++
        }
        val path = info.localPath ?: throw IOException("tdlib: file path unavailable")
        val file = java.io.File(path)
        if (!file.exists()) throw IOException("tdlib: local file missing")

        // Best-effort: persist localPath in ObjectBox telegram_messages
        runBlocking {
            runCatching {
                val box = com.chris.m3usuite.data.obx.ObxStore.get(context)
                val b = box.boxFor(com.chris.m3usuite.data.obx.ObxTelegramMessage::class.java)
                val q = b.query(com.chris.m3usuite.data.obx.ObxTelegramMessage_.chatId.equal(chatId).and(com.chris.m3usuite.data.obx.ObxTelegramMessage_.messageId.equal(msgId))).build()
                val row = q.findFirst() ?: com.chris.m3usuite.data.obx.ObxTelegramMessage(chatId = chatId, messageId = msgId)
                row.localPath = path
                b.put(row)
            }
        }

        expectedSize = if (info.expectedSize > 0) info.expectedSize else -1
        completed = info.downloadingCompleted

        try {
            raf = RandomAccessFile(file, "r")
            if (dataSpec.position > 0) raf!!.seek(dataSpec.position)
        } catch (e: Throwable) {
            throw IOException("open RAF failed", e)
        }
        opened = true
        // If known, return remaining length; otherwise LENGTH_UNSET (-1)
        return if (expectedSize > 0) kotlin.math.max(0L, expectedSize - dataSpec.position) else -1L
    }

    override fun read(buffer: ByteArray, offset: Int, readLength: Int): Int {
        delegate?.let { return it.read(buffer, offset, readLength) }
        val f = raf ?: throw IllegalStateException("not opened")
        if (readLength == 0) return 0

        while (true) {
            val available = (try { f.length() } catch (_: Throwable) { 0L }) - f.filePointer
            if (available > 0) {
                val toRead = kotlin.math.min(readLength.toLong(), available).toInt()
                return try { f.read(buffer, offset, toRead) } catch (e: Throwable) { throw IOException(e) }
            }
            if (completed) return -1
            // Poll TDLib for completion and wait for more bytes
            if (fileId > 0 && TdLibReflection.available()) {
                val client = TdLibReflection.getOrCreateClient(context, kotlinx.coroutines.flow.MutableStateFlow(AuthState.UNKNOWN))
                val gf = TdLibReflection.buildGetFile(fileId)
                if (client != null && gf != null) {
                    val fo = TdLibReflection.sendForResult(client, gf)
                    val info = fo?.let { TdLibReflection.extractFileInfo(it) }
                    if (info != null) completed = info.downloadingCompleted
                }
            }
            try { Thread.sleep(120) } catch (_: InterruptedException) { }
        }
    }

    override fun close() {
        runCatching { delegate?.close() }
        delegate = null
        runCatching { raf?.close() }
        raf = null
        opened = false
    }

    class Factory(
        private val context: Context,
        private val fallback: DataSource.Factory,
        private val notify: ((String) -> Unit)? = null
    ) : DataSource.Factory {
        override fun createDataSource(): DataSource = TelegramTdlibDataSource(context, fallback, TelegramRoutingDataSource.Factory(context, fallback), notify)
    }
}
