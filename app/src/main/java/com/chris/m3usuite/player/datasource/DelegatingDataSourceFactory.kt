package com.chris.m3usuite.player.datasource

import android.content.Context
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import com.chris.m3usuite.telegram.core.T_TelegramServiceClient
import com.chris.m3usuite.telegram.player.TelegramFileDataSource
import java.io.IOException
import java.util.Locale

/**
 * Factory for creating DataSource instances with custom routing logic.
 * Routes requests to appropriate DataSource implementations based on URL scheme.
 *
 * Supported schemes:
 * - tg:// - Telegram files via TelegramFileDataSource (zero-copy with FileDataSource)
 * - rar:// - RAR archive entries via RarDataSource
 * - http://, https://, file:// - Standard sources via fallback factory
 */
@UnstableApi
class DelegatingDataSourceFactory(
    private val context: Context,
    private val fallback: DataSource.Factory,
) : DataSource.Factory {
    override fun createDataSource(): DataSource = DelegatingDataSource(context, fallback)
}

/**
 * Internal DataSource that delegates to scheme-specific implementations.
 */
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
        val target: DataSource =
            when {
                scheme == "tg" -> {
                    // Route Telegram files to TelegramFileDataSource (zero-copy with FileDataSource)
                    // Get T_TelegramServiceClient singleton instance
                    val serviceClient =
                        try {
                            T_TelegramServiceClient.getInstance(context)
                        } catch (e: Exception) {
                            throw IOException("Failed to get Telegram service client: ${e.message}", e)
                        }
                    TelegramFileDataSource(serviceClient)
                }
                scheme == "rar" -> RarDataSource(context)
                else -> fallback.createDataSource()
            }
        delegate = target
        transferListener?.let { target.addTransferListener(it) }
        return target.open(dataSpec)
    }

    override fun read(
        buffer: ByteArray,
        offset: Int,
        readLength: Int,
    ): Int {
        val d = delegate ?: return C.RESULT_END_OF_INPUT
        return d.read(buffer, offset, readLength)
    }

    override fun getUri() = delegate?.uri

    override fun close() {
        runCatching { delegate?.close() }
        delegate = null
    }
}
