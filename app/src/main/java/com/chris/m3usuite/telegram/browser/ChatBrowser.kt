package com.chris.m3usuite.telegram.browser

import com.chris.m3usuite.telegram.session.TelegramSession
import com.chris.m3usuite.telegram.session.getOrThrow
import dev.g000sha256.tdl.dto.*

/**
 * Browser for navigating Telegram chats and messages.
 * Provides paging support for both chat lists and message history.
 */
class ChatBrowser(
    private val session: TelegramSession
) {

    private val client get() = session.client

    /**
     * Load a list of chats from the main chat list.
     * 
     * @param limit Maximum number of chats to load
     * @return List of Chat objects
     */
    suspend fun loadChats(limit: Int = 200): List<Chat> {
        println("[ChatBrowser] Loading chats (limit=$limit)...")

        val chatsResult = client.getChats(ChatListMain(), limit.toLong()).getOrThrow()
        val chatIds: LongArray = chatsResult.chatIds ?: LongArray(0)

        val chats = mutableListOf<Chat>()
        for (id in chatIds) {
            try {
                val chat = client.getChat(id).getOrThrow()
                chats += chat
            } catch (t: Throwable) {
                println("[ChatBrowser] Error loading chat $id: ${t.message}")
            }
        }

        println("[ChatBrowser] Loaded ${chats.size} chats")
        return chats
    }

    /**
     * Load a single chat by ID.
     */
    suspend fun getChat(chatId: Long): Chat? {
        return try {
            client.getChat(chatId).getOrThrow()
        } catch (t: Throwable) {
            println("[ChatBrowser] Error loading chat $chatId: ${t.message}")
            null
        }
    }

    /**
     * Load message history from a chat with paging support.
     * 
     * @param chatId Chat ID to load messages from
     * @param fromMessageId Starting message ID (0 for latest messages)
     * @param limit Number of messages to load (default 20)
     * @return List of messages in reverse chronological order
     */
    suspend fun loadChatHistory(
        chatId: Long,
        fromMessageId: Long = 0L,
        limit: Int = 20
    ): List<Message> {
        println("[ChatBrowser] Loading chat history (chatId=$chatId, from=$fromMessageId, limit=$limit)")

        return try {
            val history = client.getChatHistory(
                chatId = chatId,
                fromMessageId = fromMessageId,
                offset = 0,
                limit = limit,
                onlyLocal = false
            ).getOrThrow()

            val msgsArray: Array<Message?> = history.messages ?: emptyArray()
            val messages = msgsArray.filterNotNull()

            println("[ChatBrowser] Loaded ${messages.size} messages")
            messages
        } catch (t: Throwable) {
            println("[ChatBrowser] Error loading chat history: ${t.message}")
            emptyList()
        }
    }

    /**
     * Load all messages from a chat by paging through the entire history.
     * Use with caution for large chats.
     * 
     * @param chatId Chat ID to load all messages from
     * @param pageSize Number of messages per page
     * @param maxMessages Maximum total messages to load (safety limit)
     * @return Complete list of messages
     */
    suspend fun loadAllMessages(
        chatId: Long,
        pageSize: Int = 100,
        maxMessages: Int = 10000
    ): List<Message> {
        println("[ChatBrowser] Loading all messages (chatId=$chatId, pageSize=$pageSize, max=$maxMessages)")

        val allMessages = mutableListOf<Message>()
        var fromMessageId = 0L

        while (allMessages.size < maxMessages) {
            val batch = loadChatHistory(chatId, fromMessageId, pageSize)
            
            if (batch.isEmpty()) {
                println("[ChatBrowser] No more messages, stopping")
                break
            }

            allMessages.addAll(batch)
            fromMessageId = batch.last().id

            println("[ChatBrowser] Progress: ${allMessages.size} messages loaded")

            // Safety check to prevent infinite loops
            if (batch.size < pageSize) {
                println("[ChatBrowser] Received partial batch, assuming end of history")
                break
            }
        }

        println("[ChatBrowser] Total messages loaded: ${allMessages.size}")
        return allMessages
    }

    /**
     * Search for messages in a chat.
     * 
     * @param chatId Chat to search in
     * @param query Search query
     * @param limit Maximum results
     * @return List of matching messages
     */
    suspend fun searchChatMessages(
        chatId: Long,
        query: String,
        limit: Int = 100
    ): List<Message> {
        println("[ChatBrowser] Searching chat (chatId=$chatId, query='$query', limit=$limit)")

        return try {
            val result = client.searchChatMessages(
                chatId = chatId,
                query = query,
                senderId = null,
                fromMessageId = 0L,
                offset = 0,
                limit = limit,
                filter = null,
                messageThreadId = 0L,
                savedMessagesTopicId = 0L
            ).getOrThrow()

            val msgsArray: Array<Message?> = result.messages ?: emptyArray()
            val messages = msgsArray.filterNotNull()

            println("[ChatBrowser] Found ${messages.size} matching messages")
            messages
        } catch (t: Throwable) {
            println("[ChatBrowser] Error searching messages: ${t.message}")
            emptyList()
        }
    }
}
