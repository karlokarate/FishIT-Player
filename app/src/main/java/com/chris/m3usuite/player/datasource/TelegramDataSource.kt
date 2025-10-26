package com.chris.m3usuite.player.datasource

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import com.chris.m3usuite.tg.TgGate
import com.chris.m3usuite.telegram.service.TelegramServiceClient
import java.io.IOException

@UnstableApi
class TelegramDataSource(
    private val service: TelegramServiceClient,
) : DataSource {

    private var source: TdlibRandomAccessSource? = null
    private var currentUri: Uri? = null
    private var position: Long = 0
    private var bytesRemaining: Long = C.LENGTH_UNSET.toLong()
    private var opened = false
    private var transferListener: TransferListener? = null
    private var currentDataSpec: DataSpec? = null
    private var boundService = false

    override fun addTransferListener(transferListener: TransferListener) {
        this.transferListener = transferListener
    }

    @Throws(IOException::class)
    override fun open(dataSpec: DataSpec): Long {
        check(TgGate.mirrorOnly()) { "TelegramDataSource darf nur im Mirror-Only-Modus verwendet werden." }
        val uri = dataSpec.uri
        currentUri = uri
        require(uri.scheme.equals("tg", ignoreCase = true) && uri.host.equals("file", true)) {
            "Nur tg://file/<fileId> wird unterstützt."
        }
        if (!boundService) {
            service.bind()
            boundService = true
        }
        val fileId = uri.lastPathSegment?.toIntOrNull()
            ?: throw IOException("Ungültige tg://file/<fileId>-URI: $uri")
        val src = TdlibRandomAccessSource(fileId, service, service.updateFilesFlow)
        source = src
        position = dataSpec.position
        val resolvedLength = src.open(dataSpec)
        bytesRemaining = when {
            dataSpec.length != C.LENGTH_UNSET.toLong() -> dataSpec.length
            resolvedLength >= 0 -> resolvedLength
            else -> C.LENGTH_UNSET.toLong()
        }
        opened = true
        currentDataSpec = dataSpec
        transferListener?.onTransferStart(this, dataSpec, true)
        return bytesRemaining
    }

    override fun read(buffer: ByteArray, offset: Int, readLength: Int): Int {
        if (readLength == 0) return 0
        val src = source ?: return C.RESULT_END_OF_INPUT
        val read = src.read(buffer, offset, readLength)
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
        if (boundService) {
            runCatching { service.unbind() }
            boundService = false
        }
    }
}
