package com.chris.m3usuite.telegram.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for TelegramBlockGrouper.
 */
class TelegramBlockGrouperTest {
    @Test
    fun `group returns empty list for empty input`() {
        val result = TelegramBlockGrouper.group(emptyList())
        assertTrue("Empty input should return empty list", result.isEmpty())
    }

    @Test
    fun `group creates single block for messages within window`() {
        val messages =
            listOf(
                createTextMessage(id = 1, date = 1000L),
                createTextMessage(id = 2, date = 1050L),
                createTextMessage(id = 3, date = 1100L),
            )

        val blocks = TelegramBlockGrouper.group(messages)

        assertEquals("Should create single block", 1, blocks.size)
        assertEquals("Block should contain all messages", 3, blocks[0].messages.size)
    }

    @Test
    fun `group creates multiple blocks for messages outside window`() {
        val messages =
            listOf(
                createTextMessage(id = 1, date = 1000L),
                createTextMessage(id = 2, date = 1050L),
                createTextMessage(id = 3, date = 1500L), // 500 seconds later - outside 120s window
            )

        val blocks = TelegramBlockGrouper.group(messages)

        assertEquals("Should create two blocks", 2, blocks.size)
    }

    @Test
    fun `group respects custom window size`() {
        val messages =
            listOf(
                createTextMessage(id = 1, date = 1000L),
                createTextMessage(id = 2, date = 1050L),
                createTextMessage(id = 3, date = 1100L),
            )

        // With 30 second window, each message is more than 30s apart from the next
        // so we get 3 blocks (each message 50s apart exceeds 30s window)
        val blocks = TelegramBlockGrouper.group(messages, windowSeconds = 30L)

        assertEquals("Should create 3 blocks with small window", 3, blocks.size)
    }

    @Test
    fun `group preserves chatId in blocks`() {
        val chatId = -1001234567L
        val messages =
            listOf(
                createTextMessage(id = 1, date = 1000L, chatId = chatId),
                createTextMessage(id = 2, date = 1050L, chatId = chatId),
            )

        val blocks = TelegramBlockGrouper.group(messages)

        assertEquals("Block should have correct chatId", chatId, blocks[0].chatId)
    }

    @Test
    fun `group sorts messages by date descending`() {
        val messages =
            listOf(
                createTextMessage(id = 3, date = 1100L),
                createTextMessage(id = 1, date = 1000L),
                createTextMessage(id = 2, date = 1050L),
            )

        val blocks = TelegramBlockGrouper.group(messages)

        // Newest first
        assertEquals("First message should be newest", 3L, blocks[0].messages[0].id)
        assertEquals("Second message should be middle", 2L, blocks[0].messages[1].id)
        assertEquals("Third message should be oldest", 1L, blocks[0].messages[2].id)
    }

    @Test
    fun `groupByChatThenTime separates different chats`() {
        val messages =
            listOf(
                createTextMessage(id = 1, date = 1000L, chatId = -1001L),
                createTextMessage(id = 2, date = 1050L, chatId = -1001L),
                createTextMessage(id = 3, date = 1000L, chatId = -1002L),
                createTextMessage(id = 4, date = 1050L, chatId = -1002L),
            )

        val blocks = TelegramBlockGrouper.groupByChatThenTime(messages)

        assertEquals("Should create two blocks (one per chat)", 2, blocks.size)

        val chat1Block = blocks.find { it.chatId == -1001L }
        val chat2Block = blocks.find { it.chatId == -1002L }

        assertEquals("Chat 1 block should have 2 messages", 2, chat1Block?.messages?.size)
        assertEquals("Chat 2 block should have 2 messages", 2, chat2Block?.messages?.size)
    }

    @Test
    fun `areWithinWindow returns true for messages within window`() {
        val msg1 = createTextMessage(id = 1, date = 1000L)
        val msg2 = createTextMessage(id = 2, date = 1100L)

        assertTrue("Messages 100s apart should be within 120s window", TelegramBlockGrouper.areWithinWindow(msg1, msg2))
    }

    @Test
    fun `areWithinWindow returns false for messages outside window`() {
        val msg1 = createTextMessage(id = 1, date = 1000L)
        val msg2 = createTextMessage(id = 2, date = 1200L)

        assertTrue(
            "Messages 200s apart should NOT be within 120s window",
            !TelegramBlockGrouper.areWithinWindow(msg1, msg2),
        )
    }

    @Test
    fun `group handles exact 120 second boundary`() {
        val messages =
            listOf(
                createTextMessage(id = 1, date = 1000L),
                createTextMessage(id = 2, date = 1120L), // Exactly 120 seconds later
            )

        val blocks = TelegramBlockGrouper.group(messages)

        assertEquals("Messages exactly 120s apart should be in same block", 1, blocks.size)
        assertEquals("Block should contain both messages", 2, blocks[0].messages.size)
    }

    @Test
    fun `group handles 121 second gap`() {
        val messages =
            listOf(
                createTextMessage(id = 1, date = 1000L),
                createTextMessage(id = 2, date = 1121L), // 121 seconds later - just outside
            )

        val blocks = TelegramBlockGrouper.group(messages)

        assertEquals("Messages 121s apart should be in different blocks", 2, blocks.size)
    }

    // Helper function to create test messages
    private fun createTextMessage(
        id: Long,
        date: Long,
        chatId: Long = -1001234567L,
        text: String = "Test",
    ): ExportText =
        ExportText(
            id = id,
            chatId = chatId,
            dateEpochSeconds = date,
            dateIso = "2023-01-01T00:00:00Z",
            text = text,
        )
}
