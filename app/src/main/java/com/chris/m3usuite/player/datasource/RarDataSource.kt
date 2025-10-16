package com.chris.m3usuite.player.datasource

import android.content.Context
import android.net.Uri
import androidx.media3.common.C
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import com.chris.m3usuite.data.obx.ObxStore
import com.chris.m3usuite.data.obx.ObxTelegramMessage
import com.chris.m3usuite.data.obx.ObxTelegramMessage_
import kotlinx.coroutines.runBlocking
import java.io.File
import java.io.IOException

class RarDataSource(private val context: Context) : DataSource {

    private var source: RarEntryRandomAccessSource? = null
    private var currentUri: Uri? = null
    private var currentDataSpec: DataSpec? = null
    private var position: Long = 0
    private var bytesRemaining: Long = C.LENGTH_UNSET.toLong()
    private var opened = false
    private var transferListener: TransferListener? = null

    override fun addTransferListener(transferListener: TransferListener) {
        this.transferListener = transferListener
    }

    @Throws(IOException::class)
    override fun open(dataSpec: DataSpec): Long {
        val uri = dataSpec.uri
        currentUri = uri
        val segments = uri.pathSegments
        if (segments.size < 3 || segments[0] != "msg") {
            throw IOException("Invalid rar:// uri: $uri")
        }
        val msgId = segments[1].toLongOrNull() ?: throw IOException("Invalid message id in $uri")
        val entryName = segments.subList(2, segments.size).joinToString("/")
        val row = findMessage(msgId) ?: throw IOException("Message $msgId not found")
        val rarPath = row.localPath ?: throw IOException("RAR message missing local path")
        val file = File(rarPath)
        if (!file.exists()) throw IOException("RAR file missing: $rarPath")
        val cacheDir = File(context.cacheDir, "rar")
        val src = RarEntryRandomAccessSource(file, entryName, cacheDir)
        source = src
        currentDataSpec = dataSpec
        position = dataSpec.position
        val totalSize = src.size()
        bytesRemaining = if (totalSize >= 0) totalSize - position else C.LENGTH_UNSET.toLong()
        opened = true
        transferListener?.onTransferStart(this, dataSpec, true)
        return if (bytesRemaining >= 0) bytesRemaining else C.LENGTH_UNSET.toLong()
    }

    override fun read(buffer: ByteArray, offset: Int, readLength: Int): Int {
        if (readLength == 0) return 0
        val src = source ?: return C.RESULT_END_OF_INPUT
        val read = src.read(position, buffer, offset, readLength)
        if (read == -1) return C.RESULT_END_OF_INPUT
        if (read > 0) {
            position += read
            if (bytesRemaining != C.LENGTH_UNSET.toLong()) {
                bytesRemaining = (bytesRemaining - read).coerceAtLeast(0)
            }
            currentDataSpec?.let { spec ->
                transferListener?.onBytesTransferred(this, spec, true, read)
            }
        }
        return read
    }

    override fun getUri(): Uri? = currentUri

    override fun close() {
        if (!opened) return
        opened = false
        runCatching { source?.close() }
        source = null
        currentDataSpec?.let { spec ->
            transferListener?.onTransferEnd(this, spec, true)
        }
        currentDataSpec = null
    }

    private fun findMessage(messageId: Long): ObxTelegramMessage? = runBlocking {
        val store = ObxStore.get(context)
        val box = store.boxFor(ObxTelegramMessage::class.java)
        box.query(ObxTelegramMessage_.messageId.equal(messageId)).build().findFirst()
    }
}
