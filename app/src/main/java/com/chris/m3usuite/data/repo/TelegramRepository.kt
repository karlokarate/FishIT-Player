package com.chris.m3usuite.data.repo

import android.content.Context
import android.net.Uri
import com.chris.m3usuite.data.obx.ObxStore
import com.chris.m3usuite.data.obx.ObxTelegramMessage
import com.chris.m3usuite.model.MediaItem
import com.chris.m3usuite.prefs.SettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Telegram repository (stub/default-off):
 * - Resolves local playback paths for items imported with source = "TG".
 * - No TDLib binding here to keep build safe without native libs.
 */
class TelegramRepository(
    private val context: Context,
    private val settings: SettingsStore
) {
    private val box by lazy { ObxStore.get(context) }

    suspend fun isEnabled(): Boolean = settings.tgEnabled.first()

    /** Returns a file:// Uri if a local path is known for the item's Telegram message. */
    suspend fun resolvePlaybackUriFor(item: MediaItem): Uri? = withContext(Dispatchers.IO) {
        if (item.source != "TG") return@withContext null
        val chatId = item.tgChatId ?: return@withContext null
        val msgId = item.tgMessageId ?: return@withContext null
        val tg = box.boxFor(ObxTelegramMessage::class.java).query(com.chris.m3usuite.data.obx.ObxTelegramMessage_.chatId.equal(chatId).and(com.chris.m3usuite.data.obx.ObxTelegramMessage_.messageId.equal(msgId))).build().findFirst() ?: return@withContext null
        val path = tg.localPath ?: return@withContext null
        val f = File(path)
        if (f.exists()) Uri.fromFile(f) else null
    }
}
