package com.fishit.player.tools.cli

import com.fishit.player.tools.cli.commands.MetaCommand
import com.fishit.player.tools.cli.commands.TelegramCommand
import com.fishit.player.tools.cli.commands.XtreamCommand
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.subcommands
import kotlinx.coroutines.runBlocking

/**
 * FishIT Pipeline CLI.
 *
 * Command-line interface for testing and debugging FishIT pipelines.
 * Works in both Codespaces and local environments.
 *
 * **Usage:**
 * ```
 * fishit-cli tg status
 * fishit-cli tg list-chats --class hot
 * fishit-cli xt status
 * fishit-cli xt list-vod --limit 20
 * fishit-cli meta normalize-sample --source tg
 * ```
 *
 * **Environment Variables:**
 * - TG_API_ID, TG_API_HASH: Telegram credentials
 * - XTREAM_BASE_URL, XTREAM_USERNAME, XTREAM_PASSWORD: Xtream credentials
 */
class FishitCli : CliktCommand(
    name = "fishit-cli",
    help = "FishIT Pipeline CLI - Debug and test FishIT v2 pipelines"
) {
    override fun run() = Unit
}

/**
 * Application entry point.
 */
fun main(args: Array<String>) = runBlocking {
    // Check available pipelines
    val (hasTelegram, hasXtream) = CliConfigLoader.checkAvailability()

    if (!hasTelegram && !hasXtream) {
        println("⚠️  No pipelines configured!")
        println()
        println("Set environment variables to enable pipelines:")
        println()
        println("Telegram:")
        println("  TG_API_ID=<your_api_id>")
        println("  TG_API_HASH=<your_api_hash>")
        println()
        println("Xtream:")
        println("  XTREAM_BASE_URL=http://example.com:8080")
        println("  XTREAM_USERNAME=<username>")
        println("  XTREAM_PASSWORD=<password>")
        println()
        return@runBlocking
    }

    // Initialize pipelines
    val config = CliConfigLoader.loadConfig()

    println("🚀 Initializing pipelines...")

    val pipelines: CliPipelines = try {
        CliPipelineInitializer.initializePipelines(config)
    } catch (e: Exception) {
        println("❌ Failed to initialize pipelines: ${e.message}")
        return@runBlocking
    }

    if (pipelines.hasTelegram) {
        println("✅ Telegram pipeline ready")
    }
    if (pipelines.hasXtream) {
        println("✅ Xtream pipeline ready")
    }
    println()

    // Build and run CLI
    FishitCli()
        .subcommands(
            TelegramCommand(pipelines),
            XtreamCommand(pipelines),
            MetaCommand(pipelines),
        )
        .main(args)
}
