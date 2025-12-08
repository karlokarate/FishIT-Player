package com.chris.m3usuite.telegram.repository

import android.content.Context
import com.chris.m3usuite.data.obx.ObxChatScanState
import com.chris.m3usuite.data.obx.ObxChatScanState_
import com.chris.m3usuite.data.obx.ObxStore
import com.chris.m3usuite.telegram.domain.ChatScanState
import com.chris.m3usuite.telegram.domain.toDomain
import com.chris.m3usuite.telegram.domain.toObx
import io.objectbox.Box
import io.objectbox.kotlin.boxFor
import io.objectbox.kotlin.query
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

/**
 * Repository for managing per-chat scan state persistence.
 *
 * Per TELEGRAM_PARSER_CONTRACT.md Section 7.1:
 * - Tracks ingestion progress per chat
 * - Used by TelegramIngestionCoordinator to resume scans across app restarts
 */
class TelegramSyncStateRepository(
    private val context: Context,
) {
    private val obxStore = ObxStore.get(context)
    private val scanStateBox: Box<ObxChatScanState> = obxStore.boxFor()

    /**
     * Observe all chat scan states.
     *
     * @return Flow emitting list of all ChatScanState objects
     */
    fun observeScanStates(): Flow<List<ChatScanState>> =
        flow {
            val states =
                scanStateBox
                    .query {
                        orderDesc(ObxChatScanState_.updatedAt)
                    }.find()
                    .map { it.toDomain() }
            emit(states)
        }.flowOn(Dispatchers.IO)

    /**
     * Update or insert a chat scan state.
     *
     * @param state The ChatScanState to upsert
     */
    suspend fun updateScanState(state: ChatScanState) =
        withContext(Dispatchers.IO) {
            // Find existing by chatId
            val existing =
                scanStateBox
                    .query {
                        equal(ObxChatScanState_.chatId, state.chatId)
                    }.findFirst()

            val obx = state.toObx()
            if (existing != null) {
                obx.id = existing.id
            }
            scanStateBox.put(obx)
        }

    /**
     * Get scan state for a specific chat.
     *
     * @param chatId The chat ID to query
     * @return ChatScanState or null if not found
     */
    suspend fun getScanState(chatId: Long): ChatScanState? =
        withContext(Dispatchers.IO) {
            scanStateBox
                .query {
                    equal(ObxChatScanState_.chatId, chatId)
                }.findFirst()
                ?.toDomain()
        }

    /**
     * Clear scan state for a specific chat.
     *
     * @param chatId The chat ID to clear
     */
    suspend fun clearScanState(chatId: Long) =
        withContext(Dispatchers.IO) {
            val existing =
                scanStateBox
                    .query {
                        equal(ObxChatScanState_.chatId, chatId)
                    }.findFirst()
            existing?.let { scanStateBox.remove(it) }
        }

    /**
     * Clear all scan states.
     */
    suspend fun clearAllScanStates() =
        withContext(Dispatchers.IO) {
            scanStateBox.removeAll()
        }
}
