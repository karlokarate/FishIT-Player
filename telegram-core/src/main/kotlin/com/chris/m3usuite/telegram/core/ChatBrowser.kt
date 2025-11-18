package com.chris.m3usuite.telegram.core

import dev.g000sha256.tdl.dto.*

/**
 * Chat browser for navigating and retrieving chat messages with paging support.
 * 
 * This class provides functionality to:
 * - Load and list available chats
 * - Retrieve chat history with pagination
 * - Navigate through messages in 20-message pages
 * 
 * In an Android app, this would typically be used by a ViewModel or Repository
 * to provide data to the UI layer.
 * 
 * @param session The TelegramSession instance providing client access
 */
class ChatBrowser(
    private val session: TelegramSession
) {

    private val client get() = session.client

    /**
     * Load all available chats.
     * 
     * @param limit Maximum number of chats to retrieve (default: 200)
     * @return List of Chat objects
     */
    suspend fun loadChats(limit: Int = 200): List<Chat> {
        val chatsResult = client.getChats(ChatListMain(), limit).getOrThrow()
        val chatIds: LongArray = chatsResult.chatIds ?: LongArray(0)

        val chats = mutableListOf<Chat>()
        for (id in chatIds) {
            try {
                val chat = client.getChat(id).getOrThrow()
                chats += chat
            } catch (t: Throwable) {
                // In production, log this error properly
                // For now, skip failed chats
            }
        }

        return chats
    }

    /**
     * Load chat history with pagination.
     * 
     * @param chatId The ID of the chat to load messages from
     * @param fromMessageId Starting message ID (0 for most recent messages)
     * @param limit Number of messages to retrieve (default: 20)
     * @return List of messages in reverse chronological order
     */
    suspend fun loadChatHistory(
        chatId: Long,
        fromMessageId: Long = 0L,
        limit: Int = 20
    ): List<Message> {
        val history = client.getChatHistory(
            chatId = chatId,
            fromMessageId = fromMessageId,
            offset = 0,
            limit = limit,
            onlyLocal = false
        ).getOrThrow()

        val msgsArray: Array<Message?> = history.messages ?: emptyArray()
        return msgsArray.filterNotNull()
    }

    /**
     * Load all messages from a chat in pages.
     * 
     * @param chatId The ID of the chat to load messages from
     * @param pageSize Number of messages per page (default: 20)
     * @param onPage Callback invoked for each page of messages
     */
    suspend fun loadAllMessages(
        chatId: Long,
        pageSize: Int = 20,
        onPage: (messages: List<Message>) -> Unit
    ) {
        var fromMessageId = 0L

        while (true) {
            val messages = loadChatHistory(chatId, fromMessageId, pageSize)
            if (messages.isEmpty()) break

            onPage(messages)
            fromMessageId = messages.last().id
        }
    }

    /**
     * Get a specific chat by ID.
     * 
     * @param chatId The ID of the chat to retrieve
     * @return The Chat object
     */
    suspend fun getChat(chatId: Long): Chat {
        return client.getChat(chatId).getOrThrow()
    }
}
