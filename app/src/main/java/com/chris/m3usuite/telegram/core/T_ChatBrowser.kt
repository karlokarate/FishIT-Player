package com.chris.m3usuite.telegram.core

import dev.g000sha256.tdl.dto.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import java.util.concurrent.ConcurrentHashMap

/**
 * Browser for navigating Telegram chats and messages.
 *
 * This class DOES NOT create its own TdlClient - it receives an injected session
 * from T_TelegramServiceClient. All operations use the ServiceClient's scope.
 *
 * Key responsibilities:
 * - Chat list retrieval with paging
 * - Individual chat retrieval with caching
 * - Message history with paging support
 * - Full chat history loading
 * - Message search within chats
 * - Real-time message updates via Flows
 * - Chat position updates via Flows
 *
 * All operations include retry logic for robustness.
 */
class T_ChatBrowser(
    private val session: T_TelegramSession,
) {
    private val client get() = session.client

    // Cache for chat metadata to reduce API calls - thread-safe
    private val chatCache = ConcurrentHashMap<Long, Chat>()

    /**
     * Get top chats from the main chat list.
     * This is the primary method for retrieving chats.
     *
     * @param limit Maximum number of chats to load (default 100)
     * @param retries Number of retry attempts on failure (default 3)
     * @return List of Chat objects
     */
    suspend fun getTopChats(
        limit: Int = 100,
        retries: Int = 3,
    ): List<Chat> {
        println("[T_ChatBrowser] Loading top chats (limit=$limit)...")

        var lastError: Throwable? = null
        repeat(retries) { attempt ->
            try {
                val chatsResult = client.getChats(ChatListMain(), limit).getOrThrow()
                val chatIds: LongArray = chatsResult.chatIds ?: LongArray(0)

                val chats = mutableListOf<Chat>()
                for (id in chatIds) {
                    try {
                        val chat = client.getChat(id).getOrThrow()
                        chatCache[id] = chat // Update cache
                        chats += chat
                    } catch (t: Throwable) {
                        println("[T_ChatBrowser] Error loading chat $id: ${t.message}")
                    }
                }

                println("[T_ChatBrowser] Loaded ${chats.size} chats")
                return chats
            } catch (t: Throwable) {
                lastError = t
                println("[T_ChatBrowser] Error loading chats (attempt ${attempt + 1}/$retries): ${t.message}")

                if (attempt < retries - 1) {
                    delay(1000L * (attempt + 1)) // Exponential backoff
                }
            }
        }

        // All retries failed
        println("[T_ChatBrowser] Failed to load chats after $retries attempts: ${lastError?.message}")
        throw lastError ?: Exception("Failed to load chats")
    }

    /**
     * Get a single chat by ID.
     * Uses cache to reduce API calls.
     *
     * @param chatId Chat ID
     * @param useCache Whether to use cache (default true)
     * @return Chat object or null if not found
     */
    suspend fun getChat(
        chatId: Long,
        useCache: Boolean = true,
    ): Chat? {
        // Check cache first if enabled
        if (useCache) {
            chatCache[chatId]?.let { return it }
        }

        return try {
            val chat = client.getChat(chatId).getOrThrow()
            chatCache[chatId] = chat // Update cache
            chat
        } catch (t: Throwable) {
            println("[T_ChatBrowser] Error loading chat $chatId: ${t.message}")
            null
        }
    }

    /**
     * Load message history from a chat with paging support.
     * This is the primary method for retrieving messages.
     *
     * @param chatId Chat ID to load messages from
     * @param fromMessageId Starting message ID (0 for latest messages)
     * @param offset Offset from fromMessageId (default 0)
     * @param limit Number of messages to load (default 20)
     * @param retries Number of retry attempts on failure (default 3)
     * @return List of messages in reverse chronological order
     */
    suspend fun loadMessagesPaged(
        chatId: Long,
        fromMessageId: Long = 0L,
        offset: Int = 0,
        limit: Int = 20,
        retries: Int = 3,
    ): List<Message> {
        println("[T_ChatBrowser] Loading messages (chatId=$chatId, from=$fromMessageId, offset=$offset, limit=$limit)")

        var lastError: Throwable? = null
        repeat(retries) { attempt ->
            try {
                val history =
                    client
                        .getChatHistory(
                            chatId = chatId,
                            fromMessageId = fromMessageId,
                            offset = offset,
                            limit = limit,
                            onlyLocal = false,
                        ).getOrThrow()

                val msgsArray: Array<Message?> = history.messages ?: emptyArray()
                val messages = msgsArray.filterNotNull()

                println("[T_ChatBrowser] Loaded ${messages.size} messages")
                return messages
            } catch (t: Throwable) {
                lastError = t
                println("[T_ChatBrowser] Error loading messages (attempt ${attempt + 1}/$retries): ${t.message}")

                if (attempt < retries - 1) {
                    delay(500L * (attempt + 1))
                }
            }
        }

        println("[T_ChatBrowser] Failed to load messages after $retries attempts: ${lastError?.message}")
        return emptyList() // Return empty list instead of throwing
    }

    /**
     * Load all messages from a chat by paging through the entire history.
     * Use with caution for large chats.
     *
     * @param chatId Chat ID to load all messages from
     * @param pageSize Number of messages per page (default 100)
     * @param maxMessages Maximum total messages to load as safety limit (default 10000)
     * @return Complete list of messages
     */
    suspend fun loadAllMessages(
        chatId: Long,
        pageSize: Int = 100,
        maxMessages: Int = 10000,
    ): List<Message> {
        println("[T_ChatBrowser] Loading all messages (chatId=$chatId, pageSize=$pageSize, max=$maxMessages)")

        val allMessages = mutableListOf<Message>()
        var fromMessageId = 0L

        while (allMessages.size < maxMessages) {
            val batch = loadMessagesPaged(chatId, fromMessageId, limit = pageSize)

            if (batch.isEmpty()) {
                println("[T_ChatBrowser] No more messages, stopping")
                break
            }

            allMessages.addAll(batch)
            fromMessageId = batch.last().id

            println("[T_ChatBrowser] Progress: ${allMessages.size} messages loaded")

            // Safety check to prevent infinite loops
            if (batch.size < pageSize) {
                println("[T_ChatBrowser] Received partial batch, assuming end of history")
                break
            }
        }

        println("[T_ChatBrowser] Total messages loaded: ${allMessages.size}")
        return allMessages
    }

    /**
     * Search for messages in a chat.
     *
     * @param chatId Chat to search in
     * @param query Search query
     * @param limit Maximum results (default 100)
     * @return List of matching messages
     */
    suspend fun searchChatMessages(
        chatId: Long,
        query: String,
        limit: Int = 100,
    ): List<Message> {
        println("[T_ChatBrowser] Searching chat (chatId=$chatId, query='$query', limit=$limit)")

        return try {
            val result =
                client
                    .searchChatMessages(
                        chatId = chatId,
                        query = query,
                        senderId = null,
                        fromMessageId = 0L,
                        offset = 0,
                        limit = limit,
                        filter = null,
                    ).getOrThrow()

            val messages = result.messages?.filterNotNull() ?: emptyList()

            println("[T_ChatBrowser] Found ${messages.size} matching messages")
            messages
        } catch (t: Throwable) {
            println("[T_ChatBrowser] Error searching messages: ${t.message}")
            emptyList()
        }
    }

    /**
     * Observe real-time new messages for a specific chat.
     * Returns a Flow that emits new messages as they arrive.
     *
     * Based on tdlib-coroutines documentation for message updates.
     *
     * @param chatId Chat ID to monitor
     * @return Flow of new messages
     */
    fun observeMessages(chatId: Long): Flow<Message> =
        client.newMessageUpdates
            .filter { update -> update.message.chatId == chatId }
            .map { update -> update.message }

    /**
     * Observe all new messages across all chats.
     *
     * @return Flow of all new messages
     */
    fun observeAllNewMessages(): Flow<Message> =
        client.newMessageUpdates
            .map { update -> update.message }

    /**
     * Observe chat list updates (new chats, position changes, etc.)
     *
     * @return Flow of chat position updates
     */
    fun observeChatUpdates(): Flow<UpdateChatPosition> = client.chatPositionUpdates

    /**
     * Clear the chat cache.
     * Useful when you want to force fresh data from TDLib.
     */
    fun clearCache() {
        chatCache.clear()
        println("[T_ChatBrowser] Cache cleared")
    }

    /**
     * Get cached chat count.
     *
     * @return Number of chats in cache
     */
    fun getCacheSize(): Int = chatCache.size
}
