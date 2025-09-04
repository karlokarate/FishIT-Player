package com.chris.m3usuite.telegram

import android.content.Context
import android.net.Uri
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.FileDataSource
import com.chris.m3usuite.data.db.DbProvider
import kotlinx.coroutines.runBlocking
import java.io.EOFException
import java.io.IOException

/**
 * Routing DataSource supporting tg://message?chatId=..&messageId=.. URIs by delegating
 * to a local file via Room lookup (telegram_messages.localPath). If no localPath is present,
 * it attempts to trigger a TDLib download (if available), then waits briefly for the path to appear.
 * Falls back to error if still unavailable.
 */
class TelegramRoutingDataSource(
    private val context: Context,
    private val fallbackFactory: DataSource.Factory
) : DataSource {

    private var current: DataSource? = null
    private var overrideSpec: DataSpec? = null
    private var opened = false

    override fun open(dataSpec: DataSpec): Long {
        val uri = dataSpec.uri
        val scheme = uri.scheme?.lowercase()
        val delegate: DataSource = if (scheme == "tg") {
            buildTelegramDataSource(uri) ?: throw IOException("Telegram file not available")
        } else {
            fallbackFactory.createDataSource()
        }
        current = delegate
        val spec = overrideSpec ?: dataSpec
        val len = delegate.open(spec)
        opened = true
        return len
    }

    private fun buildTelegramDataSource(uri: Uri): DataSource? {
        val chatId = uri.getQueryParameter("chatId")?.toLongOrNull() ?: return null
        val msgId = uri.getQueryParameter("messageId")?.toLongOrNull() ?: return null
        val db = DbProvider.get(context)
        // 1) Try local path
        val path = runBlocking { db.telegramDao().byKey(chatId, msgId)?.localPath }
        if (path != null) {
            val f = java.io.File(path)
            if (f.exists()) {
                overrideSpec = null // will replace below
                overrideSpec = DataSpec.Builder().setUri(Uri.fromFile(f)).build()
                return FileDataSource()
            }
        }
        // 2) Try to trigger download via TDLib if available and fileId is known in DB
        runCatching {
            if (TdLibReflection.available()) {
                val flow = kotlinx.coroutines.flow.MutableStateFlow(TdLibReflection.AuthState.UNKNOWN)
                val client = TdLibReflection.getOrCreateClient(context, flow)
                if (client != null) {
                    // only if authenticated
                    val auth = TdLibReflection.mapAuthorizationState(
                        TdLibReflection.buildGetAuthorizationState()?.let { TdLibReflection.sendForResult(client, it, 500) }
                    )
                    if (auth == TdLibReflection.AuthState.AUTHENTICATED) {
                        val tg = runBlocking { db.telegramDao().byKey(chatId, msgId) }
                        val fid = tg?.fileId
                        if (fid != null && fid > 0) {
                            val dl = TdLibReflection.buildDownloadFile(fid, 16, 0, 0, false)
                            if (dl != null) TdLibReflection.sendForResult(client, dl, 100)
                            // Also poll getFile for local path and persist when available
                            var attempts = 0
                            while (attempts < 20) {
                                val gf = TdLibReflection.buildGetFile(fid)
                                val fo = if (gf != null) TdLibReflection.sendForResult(client, gf) else null
                                val info = fo?.let { TdLibReflection.extractFileInfo(it) }
                                val pnow = info?.localPath
                                if (!pnow.isNullOrBlank() && java.io.File(pnow).exists()) {
                                    runBlocking { db.telegramDao().updateLocalPath(chatId, msgId, pnow) }
                                    break
                                }
                                Thread.sleep(120)
                                attempts++
                            }
                        }
                    }
                }
            }
        }
        // Short wait-loop (up to ~2s) for localPath to appear
        repeat(10) {
            val p = runBlocking { db.telegramDao().byKey(chatId, msgId)?.localPath }
            if (p != null) {
                val f = java.io.File(p)
                if (f.exists()) {
                    overrideSpec = DataSpec.Builder().setUri(Uri.fromFile(f)).build()
                    return FileDataSource()
                }
            }
            try { Thread.sleep(200) } catch (_: InterruptedException) {}
        }
        return null
    }

    override fun read(buffer: ByteArray, offset: Int, readLength: Int): Int {
        val d = current ?: throw IllegalStateException("DataSource not opened")
        return d.read(buffer, offset, readLength)
    }

    override fun getUri(): Uri? = current?.uri

    override fun addTransferListener(transferListener: androidx.media3.datasource.TransferListener) {
        current?.addTransferListener(transferListener)
    }

    override fun close() {
        if (!opened) return
        runCatching { current?.close() }
        opened = false
        current = null
    }

    class Factory(private val context: Context, private val fallback: DataSource.Factory) : DataSource.Factory {
        override fun createDataSource(): DataSource = TelegramRoutingDataSource(context, fallback)
    }
}
