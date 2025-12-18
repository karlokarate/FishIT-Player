package com.fishit.player.tools.cli.commands

import com.fishit.player.tools.cli.CliPipelines
import com.fishit.player.pipeline.telegram.catalog.TelegramChatMediaClassifier
import com.fishit.player.pipeline.telegram.debug.TelegramDebugServiceImpl
import com.fishit.player.pipeline.xtream.debug.XtreamDebugServiceImpl
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.choice
import com.github.ajalt.clikt.parameters.types.int
import kotlinx.coroutines.runBlocking

/**
 * Metadata and cross-pipeline commands.
 *
 * Subcommands:
 * - meta normalize-sample: Sample and show normalized metadata
 */
class MetaCommand(private val pipelines: CliPipelines) :
        CliktCommand(name = "meta", help = "Metadata normalization commands") {
    override fun run() = Unit

    init {
        subcommands(
                NormalizeSampleCommand(pipelines),
        )
    }
}

class NormalizeSampleCommand(private val pipelines: CliPipelines) :
        CliktCommand(
                name = "normalize-sample",
                help = "Sample and show normalized metadata from a pipeline"
        ) {
    private val source by
            option("--source", help = "Source pipeline (tg or xc)").choice("tg", "xc").default("tg")
    private val limit by option("--limit", help = "Maximum items to sample").int().default(20)
    private val json by option("--json", help = "Output as JSON").flag()

    override fun run() = runBlocking {
        when (source) {
            "tg" -> sampleTelegram()
            "xc" -> sampleXtream()
        }
    }

    private suspend fun sampleTelegram() {
        val adapter = pipelines.telegram
        if (adapter == null) {
            echo("❌ Telegram pipeline not available")
            return
        }

        val service = TelegramDebugServiceImpl(adapter, TelegramChatMediaClassifier())

        // Get first hot or warm chat
        val chats =
                service.listChats(com.fishit.player.pipeline.telegram.debug.ChatFilter.HOT, 1)
                        .ifEmpty {
                            service.listChats(
                                    com.fishit.player.pipeline.telegram.debug.ChatFilter.WARM,
                                    1
                            )
                        }
                        .ifEmpty {
                            service.listChats(
                                    com.fishit.player.pipeline.telegram.debug.ChatFilter.ALL,
                                    1
                            )
                        }

        if (chats.isEmpty()) {
            echo("❌ No chats available for sampling")
            return
        }

        val chatId = chats.first().chatId
        val media = service.sampleMedia(chatId, limit)

        if (json) {
            echo("[")
            media.forEachIndexed { index, item ->
                val comma = if (index < media.size - 1) "," else ""
                echo(
                        """  {"title": "${(item.normalizedTitle ?: "").replace("\"", "\\\"")}", "type": "${item.normalizedMediaType}", "mime": "${item.mimeType ?: ""}"}$comma"""
                )
            }
            echo("]")
        } else {
            echo("📋 Normalized Metadata Sample (Telegram, limit: $limit)")
            echo("━".repeat(90))
            echo(String.format("%-50s %-20s %s", "Title", "Type", "MIME"))
            echo("─".repeat(90))

            for (item in media) {
                val title = (item.normalizedTitle ?: "Untitled").take(48).padEnd(50)
                val type = item.normalizedMediaType.name.padEnd(20)
                echo(String.format("%s %s %s", title, type, item.mimeType ?: "-"))
            }

            echo("─".repeat(90))
            echo("Total: ${media.size} items")
        }
    }

    private suspend fun sampleXtream() {
        val adapter = pipelines.xtream
        if (adapter == null) {
            echo("❌ Xtream pipeline not available")
            return
        }

        val service = XtreamDebugServiceImpl(adapter)
        val items = service.listVod(limit)

        if (json) {
            echo("[")
            items.forEachIndexed { index, item ->
                val comma = if (index < items.size - 1) "," else ""
                echo(
                        """  {"title": "${item.title.replace("\"", "\\\"")}", "type": "${item.normalizedMediaType}", "year": ${item.year ?: "null"}}$comma"""
                )
            }
            echo("]")
        } else {
            echo("📋 Normalized Metadata Sample (Xtream, limit: $limit)")
            echo("━".repeat(90))
            echo(String.format("%-50s %-20s %s", "Title", "Type", "Year"))
            echo("─".repeat(90))

            for (item in items) {
                val title = item.title.take(48).padEnd(50)
                val type = item.normalizedMediaType.name.padEnd(20)
                val year = item.year?.toString() ?: "-"
                echo(String.format("%s %s %s", title, type, year))
            }

            echo("─".repeat(90))
            echo("Total: ${items.size} items")
        }
    }
}
