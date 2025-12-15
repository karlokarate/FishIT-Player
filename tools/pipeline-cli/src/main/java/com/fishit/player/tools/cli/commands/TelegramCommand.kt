package com.fishit.player.tools.cli.commands

import com.fishit.player.tools.cli.CliPipelines
import com.fishit.player.pipeline.telegram.catalog.TelegramChatMediaClassifier
import com.fishit.player.pipeline.telegram.debug.ChatFilter
import com.fishit.player.pipeline.telegram.debug.TelegramDebugServiceImpl
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.enum
import com.github.ajalt.clikt.parameters.types.int
import com.github.ajalt.clikt.parameters.types.long
import kotlinx.coroutines.runBlocking
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Telegram pipeline commands.
 *
 * Subcommands:
 * - tg status: Show pipeline status
 * - tg list-chats: List chats with optional filter
 * - tg sample-media: Sample media from a chat
 */
class TelegramCommand(private val pipelines: CliPipelines) : CliktCommand(
    name = "tg",
    help = "Telegram pipeline commands"
) {
    override fun run() = Unit

    init {
        subcommands(
            TgAuthCommand(),
            TgStatusCommand(pipelines),
            TgListChatsCommand(pipelines),
            TgSampleMediaCommand(pipelines),
        )
    }
}

class TgStatusCommand(private val pipelines: CliPipelines) : CliktCommand(
    name = "status",
    help = "Show Telegram pipeline status"
) {
    private val json by option("--json", help = "Output as JSON").flag()

    override fun run() = runBlocking {
        val adapter = pipelines.telegram
        if (adapter == null) {
            echo("❌ Telegram pipeline not available")
            return@runBlocking
        }

        val service = TelegramDebugServiceImpl(adapter, TelegramChatMediaClassifier())
        val status = service.getStatus()

        if (json) {
            echo("""
                |{
                |  "isAuthenticated": ${status.isAuthenticated},
                |  "sessionDir": "${status.sessionDir}",
                |  "chatCount": ${status.chatCount},
                |  "hotChats": ${status.hotChats},
                |  "warmChats": ${status.warmChats},
                |  "coldChats": ${status.coldChats}
                |}
            """.trimMargin())
        } else {
            echo("📱 Telegram Pipeline Status")
            echo("━".repeat(40))
            echo("Authenticated: ${if (status.isAuthenticated) "✅ Yes" else "❌ No"}")
            echo("Session Dir:   ${status.sessionDir.ifEmpty { "(not set)" }}")
            echo("━".repeat(40))
            echo("Total Chats:   ${status.chatCount}")
            echo("  🔥 Hot:      ${status.hotChats}")
            echo("  🌡️ Warm:     ${status.warmChats}")
            echo("  ❄️ Cold:     ${status.coldChats}")
        }
    }
}

class TgListChatsCommand(private val pipelines: CliPipelines) : CliktCommand(
    name = "list-chats",
    help = "List Telegram chats"
) {
    private val filter by option("--class", help = "Filter by classification")
        .enum<ChatFilter>()
        .default(ChatFilter.ALL)
    private val limit by option("--limit", help = "Maximum chats to show")
        .int()
        .default(20)
    private val json by option("--json", help = "Output as JSON").flag()

    override fun run() = runBlocking {
        val adapter = pipelines.telegram
        if (adapter == null) {
            echo("❌ Telegram pipeline not available")
            return@runBlocking
        }

        val service = TelegramDebugServiceImpl(adapter, TelegramChatMediaClassifier())
        val chats = service.listChats(filter, limit)

        if (json) {
            echo("[")
            chats.forEachIndexed { index, chat ->
                val comma = if (index < chats.size - 1) "," else ""
                echo("""  {"chatId": ${chat.chatId}, "title": "${chat.title.replace("\"", "\\\"")}", "mediaClass": "${chat.mediaClass}", "mediaCount": ${chat.mediaCountEstimate}}$comma""")
            }
            echo("]")
        } else {
            echo("📋 Telegram Chats (filter: ${filter.name}, limit: $limit)")
            echo("━".repeat(80))
            echo(String.format("%-16s %-40s %-8s %s", "Chat ID", "Title", "Class", "Media"))
            echo("─".repeat(80))

            for (chat in chats) {
                val classIcon = when (chat.mediaClass) {
                    "HOT" -> "🔥"
                    "WARM" -> "🌡️"
                    "COLD" -> "❄️"
                    else -> "?"
                }
                val title = chat.title.take(38).padEnd(40)
                echo(String.format("%-16d %s %s%-5s %d", chat.chatId, title, classIcon, chat.mediaClass, chat.mediaCountEstimate))
            }

            echo("─".repeat(80))
            echo("Total: ${chats.size} chats")
        }
    }
}

class TgSampleMediaCommand(private val pipelines: CliPipelines) : CliktCommand(
    name = "sample-media",
    help = "Sample media from a Telegram chat"
) {
    private val chatId by option("--chat-id", help = "Chat ID to sample from")
        .long()
        .required()
    private val limit by option("--limit", help = "Maximum messages to sample")
        .int()
        .default(10)
    private val json by option("--json", help = "Output as JSON").flag()

    override fun run() = runBlocking {
        val adapter = pipelines.telegram
        if (adapter == null) {
            echo("❌ Telegram pipeline not available")
            return@runBlocking
        }

        val service = TelegramDebugServiceImpl(adapter!!, TelegramChatMediaClassifier())
        val media = service.sampleMedia(chatId, limit)

        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)

        if (json) {
            echo("[")
            media.forEachIndexed { index, item ->
                val comma = if (index < media.size - 1) "," else ""
                echo("""  {"messageId": ${item.messageId}, "timestamp": ${item.timestampMillis}, "mime": "${item.mimeType ?: ""}", "size": ${item.sizeBytes ?: 0}, "title": "${(item.normalizedTitle ?: "").replace("\"", "\\\"")}", "type": "${item.normalizedMediaType}"}$comma""")
            }
            echo("]")
        } else {
            echo("🎬 Media Sample from Chat $chatId (limit: $limit)")
            echo("━".repeat(100))
            echo(String.format("%-12s %-18s %-20s %-10s %-30s %s", "Msg ID", "Date", "MIME", "Size", "Title", "Type"))
            echo("─".repeat(100))

            for (item in media) {
                val date = dateFormat.format(Date(item.timestampMillis))
                val size = item.sizeBytes?.let { formatBytes(it) } ?: "-"
                val title = (item.normalizedTitle ?: "Untitled").take(28)
                echo(String.format("%-12d %-18s %-20s %-10s %-30s %s",
                    item.messageId, date, item.mimeType ?: "-", size, title, item.normalizedMediaType))
            }

            echo("─".repeat(100))
            echo("Total: ${media.size} media items")
        }
    }

    private fun formatBytes(bytes: Long): String {
        return when {
            bytes >= 1_000_000_000 -> String.format("%.1f GB", bytes / 1_000_000_000.0)
            bytes >= 1_000_000 -> String.format("%.1f MB", bytes / 1_000_000.0)
            bytes >= 1_000 -> String.format("%.1f KB", bytes / 1_000.0)
            else -> "$bytes B"
        }
    }
}
