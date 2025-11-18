package com.chris.m3usuite.telegram.core

import dev.g000sha256.tdl.dto.*

/**
 * Utility object for formatting and printing message information.
 * This is useful for debugging and logging messages in a human-readable format.
 */
object MessagePrinter {

    /**
     * Format a message into a single-line string representation.
     * 
     * @param msg The message to format
     * @return A formatted string containing message ID, sender, and content preview
     */
    fun formatMessageLine(msg: Message): String {
        val sender = when (val s = msg.senderId) {
            is MessageSenderUser -> "User(${s.userId})"
            is MessageSenderChat -> "Chat(${s.chatId})"
            else                 -> "Sender(?)"
        }

        val preview = when (val content = msg.content) {
            is MessageText     -> content.text.text
            is MessageDocument -> "[Dokument] ${content.document.fileName}"
            is MessageVideo    -> "[Video] ${content.video.fileName}"
            is MessagePhoto    -> "[Foto]"
            else               -> "[${content::class.simpleName}]"
        }

        return String.format(
            "#%-12d [%s] %s",
            msg.id,
            sender,
            preview.replace("\n", " ")
        )
    }

    /**
     * Print a message line to standard output.
     * This is primarily for CLI/debugging purposes.
     * 
     * @param msg The message to print
     */
    fun printMessageLine(msg: Message) {
        println(formatMessageLine(msg))
    }
}
