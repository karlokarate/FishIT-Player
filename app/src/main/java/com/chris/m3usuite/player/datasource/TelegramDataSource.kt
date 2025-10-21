package com.chris.m3usuite.player.datasource

import android.content.Context
import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import java.io.IOException

@UnstableApi
class TelegramDataSource(private val context: Context) : DataSource {

    private var source: TdlibRandomAccessSource? = null
    private var currentUri: Uri? = null
    private var position: Long = 0
    private var bytesRemaining: Long = C.LENGTH_UNSET.toLong()
    private var opened = false
    private var transferListener: TransferListener? = null
    private var currentDataSpec: DataSpec? = null

    override fun addTransferListener(transferListener: TransferListener) {
        this.transferListener = transferListener
    }

    @Throws(IOException::class)
    override fun open(dataSpec: DataSpec): Long {
        val uri = dataSpec.uri
        currentUri = uri
        val fileId = uri.lastPathSegment?.toIntOrNull()
            ?: throw IOException("tg:// uri missing file id: $uri")
        val source = TdlibRandomAccessSource(context, fileId)
        this.source = source
        position = dataSpec.position
        val totalSize = source.size()
        bytesRemaining = if (totalSize >= 0) totalSize - position else C.LENGTH_UNSET.toLong()
        opened = true
        currentDataSpec = dataSpec
        transferListener?.onTransferStart(this, dataSpec, true)
        return if (bytesRemaining >= 0) bytesRemaining else C.LENGTH_UNSET.toLong()
    }

    override fun read(buffer: ByteArray, offset: Int, readLength: Int): Int {
        if (readLength == 0) return 0
        val src = source ?: return C.RESULT_END_OF_INPUT
        val read = src.read(position, buffer, offset, readLength)
        if (read == -1) {
            return C.RESULT_END_OF_INPUT
        }
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
}
