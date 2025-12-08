package com.chris.m3usuite.telegram.parser

import kotlinx.serialization.json.Json
import java.io.File

/**
 * Test fixture loader for JSON export files.
 *
 * Loads files from docs/telegram/exports/exports and deserializes
 * them to ExportMessage lists for testing.
 *
 * NOT used in production - only for unit tests.
 */
object ExportFixtures {
    /**
     * JSON decoder with lenient parsing for test fixtures.
     */
    private val json =
        Json {
            ignoreUnknownKeys = true
            isLenient = true
            coerceInputValues = true
        }

    /**
     * Base path to export fixtures (relative to project root).
     */
    private const val EXPORTS_PATH = "docs/telegram/exports/exports"

    /**
     * Load a chat export from a JSON file.
     *
     * @param fileName Name of the JSON file (e.g., "-1001180440610.json")
     * @return ChatExport with parsed messages, or null if file not found
     */
    fun loadChatExport(fileName: String): ChatExport? {
        val file = findExportFile(fileName) ?: return null
        return loadChatExportFromFile(file)
    }

    /**
     * Load a chat export from a File.
     *
     * @param file The JSON file to load
     * @return ChatExport with parsed messages
     */
    fun loadChatExportFromFile(file: File): ChatExport? =
        try {
            val content = file.readText()
            json.decodeFromString<ChatExport>(content)
        } catch (e: Exception) {
            System.err.println("Failed to load ${file.name}: ${e.message}")
            null
        }

    /**
     * Load a chat export and convert raw messages to typed ExportMessages.
     *
     * @param fileName Name of the JSON file
     * @return List of typed ExportMessage objects
     */
    fun loadMessages(fileName: String): List<ExportMessage> {
        val export = loadChatExport(fileName) ?: return emptyList()
        return export.messages.map { it.toExportMessage() }
    }

    /**
     * Load messages from a File.
     *
     * @param file The JSON file to load
     * @return List of typed ExportMessage objects
     */
    fun loadMessagesFromFile(file: File): List<ExportMessage> {
        val export = loadChatExportFromFile(file) ?: return emptyList()
        return export.messages.map { it.toExportMessage() }
    }

    /**
     * List all available export files.
     *
     * @return List of File objects for all JSON exports
     */
    fun listExportFiles(): List<File> {
        val dir = findExportsDirectory() ?: return emptyList()
        return dir.listFiles { file -> file.extension == "json" }?.toList() ?: emptyList()
    }

    /**
     * Load all exports from the exports directory.
     *
     * @return Map of chatId to ChatExport
     */
    fun loadAllExports(): Map<Long, ChatExport> =
        listExportFiles()
            .mapNotNull { file -> loadChatExportFromFile(file) }
            .associateBy { it.chatId }

    /**
     * Find export file by name, searching from different working directories.
     */
    private fun findExportFile(fileName: String): File? {
        // Try relative to current directory
        val paths =
            listOf(
                EXPORTS_PATH,
                "../$EXPORTS_PATH",
                "../../$EXPORTS_PATH",
                "../../../$EXPORTS_PATH",
            )

        for (basePath in paths) {
            val file = File("$basePath/$fileName")
            if (file.exists()) return file
        }

        return null
    }

    /**
     * Find the exports directory.
     */
    private fun findExportsDirectory(): File? {
        val paths =
            listOf(
                EXPORTS_PATH,
                "../$EXPORTS_PATH",
                "../../$EXPORTS_PATH",
                "../../../$EXPORTS_PATH",
            )

        for (path in paths) {
            val dir = File(path)
            if (dir.exists() && dir.isDirectory) return dir
        }

        return null
    }

    /**
     * Get fixture statistics for debugging.
     */
    fun getFixtureStats(): FixtureStats {
        val files = listExportFiles()
        var totalMessages = 0
        var videoCount = 0
        var photoCount = 0
        var textCount = 0
        var documentCount = 0
        var audioCount = 0
        var otherCount = 0

        for (file in files) {
            val messages = loadMessagesFromFile(file)
            totalMessages += messages.size

            for (msg in messages) {
                when (msg) {
                    is ExportVideo -> videoCount++
                    is ExportPhoto -> photoCount++
                    is ExportText -> textCount++
                    is ExportDocument -> documentCount++
                    is ExportAudio -> audioCount++
                    is ExportOtherRaw -> otherCount++
                }
            }
        }

        return FixtureStats(
            fileCount = files.size,
            totalMessages = totalMessages,
            videoCount = videoCount,
            photoCount = photoCount,
            textCount = textCount,
            documentCount = documentCount,
            audioCount = audioCount,
            otherCount = otherCount,
        )
    }

    /**
     * Statistics about loaded fixtures.
     */
    data class FixtureStats(
        val fileCount: Int,
        val totalMessages: Int,
        val videoCount: Int,
        val photoCount: Int,
        val textCount: Int,
        val documentCount: Int,
        val audioCount: Int,
        val otherCount: Int,
    )
}
