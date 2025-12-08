package com.chris.m3usuite.telegram.parser

import com.chris.m3usuite.telegram.domain.MessageBlock

/**
 * Groups Telegram messages into MessageBlocks using 120-second time windows.
 *
 * Per TELEGRAM_PARSER_CONTRACT.md Section 6.1:
 * - Sort messages by dateEpochSeconds descending
 * - Group into MessageBlocks where the gap ≤ 120 seconds
 * - The 120-second time-window is the CANONICAL grouping mechanism
 * - Structured triplet detection (Video+Text+Photo) is optional refinement within blocks
 */
object TelegramBlockGrouper {
    /**
     * Default grouping window in seconds.
     * Messages within this time gap are grouped together.
     */
    private const val BLOCK_WINDOW_SECONDS = 120L

    /**
     * Group a list of messages into MessageBlocks based on time proximity.
     *
     * Algorithm:
     * 1. Sort messages by dateEpochSeconds descending (newest first)
     * 2. Iterate through messages, starting new blocks when:
     *    - The time gap to the previous message exceeds BLOCK_WINDOW_SECONDS
     *    - Or the chatId differs (should not happen in normal use)
     *
     * @param messages List of ExportMessage to group
     * @param windowSeconds Optional custom window size (defaults to 120 seconds)
     * @return List of MessageBlock, each containing related messages
     */
    fun group(
        messages: List<ExportMessage>,
        windowSeconds: Long = BLOCK_WINDOW_SECONDS,
    ): List<MessageBlock> {
        if (messages.isEmpty()) return emptyList()

        // Sort by date descending (newest first)
        val sorted = messages.sortedByDescending { it.dateEpochSeconds }

        val blocks = mutableListOf<MessageBlock>()
        var currentBlockMessages = mutableListOf<ExportMessage>()
        var previousMessage: ExportMessage? = null

        for (message in sorted) {
            if (previousMessage == null) {
                // First message starts a new block
                currentBlockMessages.add(message)
            } else {
                // Check if this message belongs to the current block
                val timeDiff = previousMessage.dateEpochSeconds - message.dateEpochSeconds
                val sameChatId = message.chatId == previousMessage.chatId

                if (sameChatId && timeDiff <= windowSeconds) {
                    // Within window, add to current block
                    currentBlockMessages.add(message)
                } else {
                    // Gap too large or different chat, finalize current block and start new one
                    if (currentBlockMessages.isNotEmpty()) {
                        blocks.add(
                            MessageBlock(
                                chatId = currentBlockMessages.first().chatId,
                                messages = currentBlockMessages.toList(),
                            ),
                        )
                    }
                    currentBlockMessages = mutableListOf(message)
                }
            }
            previousMessage = message
        }

        // Don't forget the last block
        if (currentBlockMessages.isNotEmpty()) {
            blocks.add(
                MessageBlock(
                    chatId = currentBlockMessages.first().chatId,
                    messages = currentBlockMessages.toList(),
                ),
            )
        }

        return blocks
    }

    /**
     * Group messages by chat, then within each chat by time windows.
     *
     * This is useful when processing messages from multiple chats at once.
     *
     * @param messages Mixed list of messages from potentially different chats
     * @param windowSeconds Optional custom window size (defaults to 120 seconds)
     * @return List of MessageBlock for all chats
     */
    fun groupByChatThenTime(
        messages: List<ExportMessage>,
        windowSeconds: Long = BLOCK_WINDOW_SECONDS,
    ): List<MessageBlock> {
        // First, group by chat ID
        val byChat = messages.groupBy { it.chatId }

        // Then, apply time-window grouping to each chat
        return byChat.flatMap { (_, chatMessages) ->
            group(chatMessages, windowSeconds)
        }
    }

    /**
     * Check if two messages are within the grouping time window.
     *
     * @param msg1 First message
     * @param msg2 Second message
     * @param windowSeconds Window size (defaults to 120 seconds)
     * @return True if messages are within the time window
     */
    fun areWithinWindow(
        msg1: ExportMessage,
        msg2: ExportMessage,
        windowSeconds: Long = BLOCK_WINDOW_SECONDS,
    ): Boolean {
        val timeDiff = kotlin.math.abs(msg1.dateEpochSeconds - msg2.dateEpochSeconds)
        return timeDiff <= windowSeconds
    }
}
