package com.chris.m3usuite.telegram.browser

import com.chris.m3usuite.telegram.session.TelegramSession
import com.chris.m3usuite.telegram.session.getOrThrow
import dev.g000sha256.tdl.dto.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map

/**
 * Browser for navigating Telegram chats and messages.
 * Provides paging support for both chat lists and message history.
 * Includes real-time message updates and chat caching.
 */
class ChatBrowser(
    private val session: TelegramSession
) {

    private val client get() = session.client
    
    // Cache for chat metadata to reduce API calls
    private val chatCache = mutableMapOf<Long, Chat>()

    /**
     * Load a list of chats from the main chat list.
     * 
     * @param limit Maximum number of chats to load
     * @param retries Number of retry attempts on failure (default 3)
     * @return List of Chat objects
     */
    suspend fun loadChats(limit: Int = 200, retries: Int = 3): List<Chat> {
        println("[ChatBrowser] Loading chats (limit=$limit)...")
        
        var lastError: Throwable? = null
        repeat(retries) { attempt ->
            try {
                val chatsResult = client.getChats(ChatListMain(), limit).getOrThrow()
                val chatIds: LongArray = chatsResult.chatIds ?: LongArray(0)

                val chats = mutableListOf<Chat>()
                for (id in chatIds) {
                    try {
                        val chat = client.getChat(id).getOrThrow()
                        chatCache[id] = chat  // Update cache
                        chats += chat
                    } catch (t: Throwable) {
                        println("[ChatBrowser] Error loading chat $id: ${t.message}")
                    }
                }

                println("[ChatBrowser] Loaded ${chats.size} chats")
                return chats
            } catch (t: Throwable) {
                lastError = t
                println("[ChatBrowser] Error loading chats (attempt ${attempt + 1}/$retries): ${t.message}")
                
                if (attempt < retries - 1) {
                    kotlinx.coroutines.delay(1000L * (attempt + 1))  // Exponential backoff
                }
            }
        }
        
        // All retries failed
        println("[ChatBrowser] Failed to load chats after $retries attempts: ${lastError?.message}")
        throw lastError ?: Exception("Failed to load chats")
    }

    /**
     * Load a single chat by ID.
     * Uses cache to reduce API calls.
     */
    suspend fun getChat(chatId: Long, useCache: Boolean = true): Chat? {
        // Check cache first if enabled
        if (useCache) {
            chatCache[chatId]?.let { return it }
        }
        
        return try {
            val chat = client.getChat(chatId).getOrThrow()
            chatCache[chatId] = chat  // Update cache
            chat
        } catch (t: Throwable) {
            println("[ChatBrowser] Error loading chat $chatId: ${t.message}")
            null
        }
    }
    
    /**
     * Clear the chat cache.
     */
    fun clearCache() {
        chatCache.clear()
        println("[ChatBrowser] Cache cleared")
    }

    /**
     * Load message history from a chat with paging support.
     * 
     * @param chatId Chat ID to load messages from
     * @param fromMessageId Starting message ID (0 for latest messages)
     * @param limit Number of messages to load (default 20)
     * @param retries Number of retry attempts on failure (default 3)
     * @return List of messages in reverse chronological order
     */
    suspend fun loadChatHistory(
        chatId: Long,
        fromMessageId: Long = 0L,
        limit: Int = 20,
        retries: Int = 3
    ): List<Message> {
        println("[ChatBrowser] Loading chat history (chatId=$chatId, from=$fromMessageId, limit=$limit)")

        var lastError: Throwable? = null
        repeat(retries) { attempt ->
            try {
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
                return messages
            } catch (t: Throwable) {
                lastError = t
                println("[ChatBrowser] Error loading chat history (attempt ${attempt + 1}/$retries): ${t.message}")
                
                if (attempt < retries - 1) {
                    kotlinx.coroutines.delay(500L * (attempt + 1))
                }
            }
        }
        
        println("[ChatBrowser] Failed to load chat history after $retries attempts: ${lastError?.message}")
        return emptyList()  // Return empty list instead of throwing
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
                filter = null
            ).getOrThrow()

            val messages = result.messages?.filterNotNull() ?: emptyList()

            println("[ChatBrowser] Found ${messages.size} matching messages")
            messages
        } catch (t: Throwable) {
            println("[ChatBrowser] Error searching messages: ${t.message}")
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
    fun observeNewMessages(chatId: Long): Flow<Message> {
        return client.newMessageUpdates
            .filter { update -> update.message.chatId == chatId }
            .map { update -> update.message }
    }
    
    /**
     * Observe all new messages across all chats.
     * 
     * @return Flow of all new messages
     */
    fun observeAllNewMessages(): Flow<Message> {
        return client.newMessageUpdates
            .map { update -> update.message }
    }
    
    /**
     * Observe chat list updates (new chats, position changes, etc.)
     * 
     * @return Flow of chat updates
     */
    fun observeChatUpdates(): Flow<UpdateChatPosition> {
        return client.chatPositionUpdates
    }
}
