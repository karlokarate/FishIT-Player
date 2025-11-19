package com.chris.m3usuite.telegram.browser

import dev.g000sha256.tdl.dto.*

/**
 * Utility for formatting Telegram messages for display or logging.
 */
object MessageFormatter {

    /**
     * Format a message as a single line string for display in lists.
     */
    fun formatMessageLine(msg: Message): String {
        val sender = when (val s = msg.senderId) {
            is MessageSenderUser -> "User(${s.userId})"
            is MessageSenderChat -> "Chat(${s.chatId})"
            else -> "Sender(?)"
        }

        val preview = getContentPreview(msg.content)

        return "#${msg.id} [$sender] $preview"
    }

    /**
     * Get a short preview text for message content.
     */
    fun getContentPreview(content: MessageContent): String {
        return when (content) {
            is MessageText -> content.text.text.replace("\n", " ").take(100)
            is MessageDocument -> "[Document] ${content.document.fileName}"
            is MessageVideo -> "[Video] ${content.video.fileName}"
            is MessagePhoto -> "[Photo]"
            is MessageAnimation -> "[Animation]"
            is MessageAudio -> "[Audio] ${content.audio.fileName}"
            is MessageVoiceNote -> "[Voice Note]"
            is MessageSticker -> "[Sticker]"
            is MessageLocation -> "[Location]"
            is MessageVenue -> "[Venue] ${content.venue.title}"
            is MessageContact -> "[Contact]"
            is MessagePoll -> "[Poll] ${content.poll.question}"
            else -> "[${content::class.simpleName}]"
        }
    }

    /**
     * Get full content details for a message.
     */
    fun formatMessageDetails(msg: Message): String {
        val sb = StringBuilder()
        sb.append("Message ID: ${msg.id}\n")
        sb.append("Chat ID: ${msg.chatId}\n")
        sb.append("Date: ${msg.date}\n")
        
        when (val s = msg.senderId) {
            is MessageSenderUser -> sb.append("Sender: User ${s.userId}\n")
            is MessageSenderChat -> sb.append("Sender: Chat ${s.chatId}\n")
        }

        sb.append("Content: ${getContentPreview(msg.content)}\n")

        return sb.toString()
    }

    /**
     * Extract text content from a message if available.
     */
    fun getMessageText(msg: Message): String? {
        return when (val content = msg.content) {
            is MessageText -> content.text.text
            is MessageVideo -> content.caption?.text
            is MessageDocument -> content.caption?.text
            is MessagePhoto -> content.caption?.text
            else -> null
        }
    }

    /**
     * Check if a message has media content (video, document, photo, etc.)
     */
    fun hasMediaContent(msg: Message): Boolean {
        return when (msg.content) {
            is MessageVideo,
            is MessageDocument,
            is MessagePhoto,
            is MessageAnimation,
            is MessageAudio -> true
            else -> false
        }
    }
}
