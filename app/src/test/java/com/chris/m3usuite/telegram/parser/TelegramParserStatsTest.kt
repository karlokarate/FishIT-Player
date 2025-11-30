package com.chris.m3usuite.telegram.parser

import com.chris.m3usuite.telegram.domain.TelegramItem
import com.chris.m3usuite.telegram.domain.TelegramItemType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Diagnostic/integration test for the Telegram parser pipeline.
 *
 * This test:
 * 1. Loads ALL JSON exports from docs/telegram/exports/**
 * 2. Runs the parser pipeline: JSON → ExportMessage → Blocks → Items
 * 3. Produces human-readable statistics about parsing results
 * 4. Validates JSON structural integrity (fails if any file cannot be parsed)
 *
 * Output includes:
 * - Total chats and message counts
 * - Chats filtered out (no items produced)
 * - MOVIE, SERIES, and ADULT chat classifications
 * - Video+Text+Photo pattern detection
 * - Suggested files for manual inspection
 */
class TelegramParserStatsTest {

    /**
     * Statistics for a single chat export.
     */
    data class ChatStats(
        val chatId: Long,
        val title: String,
        val exportFile: File,
        val messageCount: Int,
        val items: List<TelegramItem>,
        val hasStructuredVtpPattern: Boolean,
        val unknownContentTypes: Set<String>,
    )

    /**
     * Error encountered during JSON parsing.
     */
    data class ParseError(
        val file: File,
        val error: String,
    )

    @Test
    fun `print parser statistics over all JSON exports`() = runBlocking {
        // Find all JSON files under docs/telegram/exports
        val exportsDir = findExportsDirectory()
        if (exportsDir == null) {
            println("⚠️ Export fixtures directory not found - skipping test")
            return@runBlocking
        }

        val jsonFiles = exportsDir
            .walkTopDown()
            .filter { it.isFile && it.extension.lowercase() == "json" }
            .toList()

        println("\n" + "=".repeat(80))
        println("TELEGRAM PARSER STATISTICS")
        println("=".repeat(80))
        println("Found ${jsonFiles.size} JSON export files in $exportsDir")
        println()

        val chatStatsList = mutableListOf<ChatStats>()
        val parseErrors = mutableListOf<ParseError>()
        val allUnknownContentTypes = mutableSetOf<String>()

        // Process each JSON file
        for (file in jsonFiles) {
            try {
                val stats = processExportFile(file)
                if (stats != null) {
                    chatStatsList.add(stats)
                    allUnknownContentTypes.addAll(stats.unknownContentTypes)
                } else {
                    parseErrors.add(ParseError(file, "loadChatExportFromFile returned null"))
                }
            } catch (e: Exception) {
                parseErrors.add(ParseError(file, e.message ?: e.javaClass.simpleName))
            }
        }

        // Print parse errors
        if (parseErrors.isNotEmpty()) {
            println("❌ PARSE ERRORS (${parseErrors.size} files failed):")
            parseErrors.forEach { error ->
                println("   - ${error.file.name}: ${error.error}")
            }
            println()
        }

        // Compute statistics
        val totalChats = chatStatsList.size
        val chatsWithNoItems = chatStatsList.filter { it.items.isEmpty() }
        val chatsWithItems = chatStatsList.filter { it.items.isNotEmpty() }

        val movieChats = chatsWithItems.filter { stats ->
            stats.items.any { it.type == TelegramItemType.MOVIE }
        }
        val seriesChats = chatsWithItems.filter { stats ->
            stats.items.any { it.type == TelegramItemType.SERIES_EPISODE }
        }
        val adultChats = chatsWithItems.filter { stats ->
            stats.items.any { it.metadata.isAdult }
        }
        val structuredVtpChats = chatsWithItems.filter { it.hasStructuredVtpPattern }

        val totalMovies = chatStatsList.sumOf { stats ->
            stats.items.count { it.type == TelegramItemType.MOVIE }
        }
        val totalSeriesEpisodes = chatStatsList.sumOf { stats ->
            stats.items.count { it.type == TelegramItemType.SERIES_EPISODE }
        }
        val totalClips = chatStatsList.sumOf { stats ->
            stats.items.count { it.type == TelegramItemType.CLIP }
        }
        val totalAudiobooks = chatStatsList.sumOf { stats ->
            stats.items.count { it.type == TelegramItemType.AUDIOBOOK }
        }
        val totalRarItems = chatStatsList.sumOf { stats ->
            stats.items.count { it.type == TelegramItemType.RAR_ITEM }
        }
        val totalPosterOnly = chatStatsList.sumOf { stats ->
            stats.items.count { it.type == TelegramItemType.POSTER_ONLY }
        }
        val totalMessages = chatStatsList.sumOf { it.messageCount }

        // Print summary statistics
        println("📊 SUMMARY STATISTICS")
        println("-".repeat(40))
        println("Total chats processed:        $totalChats")
        println("Total messages parsed:        $totalMessages")
        println("Chats with items:             ${chatsWithItems.size}")
        println("Chats filtered out (no items): ${chatsWithNoItems.size}")
        println()

        println("📦 ITEM COUNTS BY TYPE")
        println("-".repeat(40))
        println("MOVIE items:                  $totalMovies")
        println("SERIES_EPISODE items:         $totalSeriesEpisodes")
        println("CLIP items:                   $totalClips")
        println("AUDIOBOOK items:              $totalAudiobooks")
        println("RAR_ITEM items:               $totalRarItems")
        println("POSTER_ONLY items:            $totalPosterOnly")
        println("Total items:                  ${totalMovies + totalSeriesEpisodes + totalClips + totalAudiobooks + totalRarItems + totalPosterOnly}")
        println()

        println("🎬 MOVIE CHATS (${movieChats.size}):")
        println("-".repeat(40))
        movieChats.take(10).forEach { stats ->
            val movieCount = stats.items.count { it.type == TelegramItemType.MOVIE }
            println("   [${stats.chatId}] ${stats.title} ($movieCount movies)")
        }
        if (movieChats.size > 10) println("   ... and ${movieChats.size - 10} more")
        println()

        println("📺 SERIES CHATS (${seriesChats.size}):")
        println("-".repeat(40))
        seriesChats.take(10).forEach { stats ->
            val episodeCount = stats.items.count { it.type == TelegramItemType.SERIES_EPISODE }
            println("   [${stats.chatId}] ${stats.title} ($episodeCount episodes)")
        }
        if (seriesChats.size > 10) println("   ... and ${seriesChats.size - 10} more")
        println()

        println("🔞 ADULT CHATS (${adultChats.size}):")
        println("-".repeat(40))
        adultChats.take(10).forEach { stats ->
            println("   [${stats.chatId}] ${stats.title}")
        }
        if (adultChats.size > 10) println("   ... and ${adultChats.size - 10} more")
        println()

        println("📷 VIDEO+TEXT+PHOTO (VTP) PATTERN CHATS (${structuredVtpChats.size}):")
        println("-".repeat(40))
        structuredVtpChats.take(10).forEach { stats ->
            println("   [${stats.chatId}] ${stats.title}")
        }
        if (structuredVtpChats.size > 10) println("   ... and ${structuredVtpChats.size - 10} more")
        println()

        // Print unknown content types for investigation
        if (allUnknownContentTypes.isNotEmpty()) {
            println("⚠️ UNKNOWN CONTENT TYPES DETECTED:")
            println("-".repeat(40))
            allUnknownContentTypes.forEach { type ->
                println("   - $type")
            }
            println()
        }

        // Print suggested files for manual inspection
        println("📋 MANUAL INSPECTION SUGGESTIONS")
        println("-".repeat(40))
        chatsWithNoItems.firstOrNull()?.let { stats ->
            println("   Chat with no items:        ${stats.exportFile.name}")
        }
        movieChats.firstOrNull()?.let { stats ->
            println("   Representative MOVIE chat: ${stats.exportFile.name}")
        }
        seriesChats.firstOrNull()?.let { stats ->
            println("   Representative SERIES chat: ${stats.exportFile.name}")
        }
        adultChats.firstOrNull()?.let { stats ->
            println("   Representative ADULT chat: ${stats.exportFile.name}")
        }
        structuredVtpChats.firstOrNull()?.let { stats ->
            println("   Representative VTP chat:   ${stats.exportFile.name}")
        }
        println()

        println("=".repeat(80))

        // Fail test if there were parse errors
        if (parseErrors.isNotEmpty()) {
            val errorMsg = "JSON parsing failed for ${parseErrors.size} files:\n" +
                parseErrors.joinToString("\n") { "  - ${it.file.name}: ${it.error}" }
            assertTrue(errorMsg, parseErrors.isEmpty())
        }
    }

    /**
     * Process a single export file and return statistics.
     */
    private fun processExportFile(file: File): ChatStats? {
        val export = ExportFixtures.loadChatExportFromFile(file) ?: return null

        // Convert raw messages to typed ExportMessages
        val messages = export.messages.map { it.toExportMessage() }

        // Track unknown content types
        val unknownTypes = mutableSetOf<String>()
        messages.filterIsInstance<ExportOtherRaw>().forEach { raw ->
            unknownTypes.add(raw.messageType)
        }

        // Group messages into blocks
        val blocks = TelegramBlockGrouper.group(messages)

        // Build items from blocks
        val items = mutableListOf<TelegramItem>()
        var hasVtpPattern = false

        for (block in blocks) {
            // Check for Video+Text+Photo pattern
            val hasVideo = block.messages.any { it is ExportVideo }
            val hasPhoto = block.messages.any { it is ExportPhoto }
            val hasText = block.messages.any { it is ExportText }

            if (hasVideo && hasPhoto && hasText) {
                hasVtpPattern = true
            }

            val item = TelegramItemBuilder.build(block, export.title)
            if (item != null) {
                items.add(item)
            }
        }

        return ChatStats(
            chatId = export.chatId,
            title = export.title,
            exportFile = file,
            messageCount = messages.size,
            items = items,
            hasStructuredVtpPattern = hasVtpPattern,
            unknownContentTypes = unknownTypes,
        )
    }

    /**
     * Find the exports directory, trying different relative paths.
     */
    private fun findExportsDirectory(): File? {
        val paths = listOf(
            "docs/telegram/exports",
            "../docs/telegram/exports",
            "../../docs/telegram/exports",
            "../../../docs/telegram/exports",
        )

        for (path in paths) {
            val dir = File(path)
            if (dir.exists() && dir.isDirectory) return dir
        }

        return null
    }
}
