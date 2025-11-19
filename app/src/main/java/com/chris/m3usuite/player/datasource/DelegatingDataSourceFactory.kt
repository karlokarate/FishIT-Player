package com.chris.m3usuite.player.datasource

import android.content.Context
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import java.io.IOException
import java.util.Locale

@UnstableApi
class DelegatingDataSourceFactory(
    private val context: Context,
    private val fallback: DataSource.Factory,
) : DataSource.Factory {
    override fun createDataSource(): DataSource = DelegatingDataSource(context, fallback)
}

@UnstableApi
private class DelegatingDataSource(
    private val context: Context,
    private val fallback: DataSource.Factory,
) : DataSource {

    private var delegate: DataSource? = null
    private var transferListener: TransferListener? = null

    override fun addTransferListener(transferListener: TransferListener) {
        this.transferListener = transferListener
    }

    @Throws(IOException::class)
    override fun open(dataSpec: DataSpec): Long {
        val scheme = dataSpec.uri.scheme?.lowercase(Locale.US)
        val target: DataSource = when {
            scheme == "tg" -> {
                // For Telegram files, we need TelegramSession
                // For now, create a placeholder that will be replaced with proper implementation
                throw IOException("Telegram playback requires TelegramSession - not yet wired")
            }
            scheme == "rar" -> RarDataSource(context)
            else -> fallback.createDataSource()
        }
        delegate = target
        transferListener?.let { target.addTransferListener(it) }
        return target.open(dataSpec)
    }

    override fun read(buffer: ByteArray, offset: Int, readLength: Int): Int {
        val d = delegate ?: return C.RESULT_END_OF_INPUT
        return d.read(buffer, offset, readLength)
    }

    override fun getUri() = delegate?.uri

    override fun close() {
        runCatching { delegate?.close() }
        delegate = null
    }
}
