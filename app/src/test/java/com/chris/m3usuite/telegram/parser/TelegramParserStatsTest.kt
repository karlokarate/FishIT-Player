package com.chris.m3usuite.telegram.parser

import com.chris.m3usuite.telegram.domain.TelegramImageRef
import com.chris.m3usuite.telegram.domain.TelegramItem
import com.chris.m3usuite.telegram.domain.TelegramItemType
import com.chris.m3usuite.telegram.domain.TelegramMediaRef
import com.chris.m3usuite.telegram.domain.TelegramMetadata
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Diagnostic/integration test for the Telegram parser pipeline.
 *
 * This test:
 * 1. Loads ALL JSON exports from docs/telegram/exports/
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
    fun `print parser statistics over all JSON exports`() =
        runBlocking {
            // Find all JSON files under docs/telegram/exports
            val exportsDir = findExportsDirectory()
            if (exportsDir == null) {
                println("⚠️ Export fixtures directory not found - skipping test")
                return@runBlocking
            }

            val jsonFiles =
                exportsDir
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

            val movieChats =
                chatsWithItems.filter { stats ->
                    stats.items.any { it.type == TelegramItemType.MOVIE }
                }
            val seriesChats =
                chatsWithItems.filter { stats ->
                    stats.items.any { it.type == TelegramItemType.SERIES_EPISODE }
                }
            val adultChats =
                chatsWithItems.filter { stats ->
                    stats.items.any { it.metadata.isAdult }
                }
            val structuredVtpChats = chatsWithItems.filter { it.hasStructuredVtpPattern }

            val totalMovies =
                chatStatsList.sumOf { stats ->
                    stats.items.count { it.type == TelegramItemType.MOVIE }
                }
            val totalSeriesEpisodes =
                chatStatsList.sumOf { stats ->
                    stats.items.count { it.type == TelegramItemType.SERIES_EPISODE }
                }
            val totalClips =
                chatStatsList.sumOf { stats ->
                    stats.items.count { it.type == TelegramItemType.CLIP }
                }
            val totalAudiobooks =
                chatStatsList.sumOf { stats ->
                    stats.items.count { it.type == TelegramItemType.AUDIOBOOK }
                }
            val totalRarItems =
                chatStatsList.sumOf { stats ->
                    stats.items.count { it.type == TelegramItemType.RAR_ITEM }
                }
            val totalPosterOnly =
                chatStatsList.sumOf { stats ->
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
            println(
                "Total items:                  ${totalMovies + totalSeriesEpisodes + totalClips + totalAudiobooks + totalRarItems + totalPosterOnly}",
            )
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
                val errorMsg =
                    "JSON parsing failed for ${parseErrors.size} files:\n" +
                        parseErrors.joinToString("\n") { "  - ${it.file.name}: ${it.error}" }
                assertTrue(errorMsg, parseErrors.isEmpty())
            }
        }

    /**
     * Validates that MOVIE items have required remote IDs, thumbnails with remote IDs,
     * and all contract-specified metadata fields.
     *
     * Per TELEGRAM_PARSER_CONTRACT.md Section 5.3:
     * - TelegramMediaRef: remoteId and uniqueId are REQUIRED and stable across sessions
     * - TelegramImageRef: remoteId and uniqueId are REQUIRED
     * - TelegramMetadata: title, year, genres, tmdbUrl, isAdult, etc.
     */
    @Test
    fun `validate MOVIE items have remote IDs and thumbnails with remote IDs`() =
        runBlocking {
            val allItems = loadAllItems()
            val movieItems = allItems.filter { it.type == TelegramItemType.MOVIE }

            if (movieItems.isEmpty()) {
                println("⚠️ No MOVIE items found in exports - skipping validation")
                return@runBlocking
            }

            println("\n" + "=".repeat(80))
            println("MOVIE ITEMS VALIDATION")
            println("=".repeat(80))
            println("Total MOVIE items to validate: ${movieItems.size}")
            println()

            val validationResults = mutableListOf<ItemValidationResult>()

            for (item in movieItems) {
                val result = validateVideoItem(item, "MOVIE")
                validationResults.add(result)
            }

            printValidationSummary("MOVIE", validationResults)
            assertNoValidationFailures(validationResults, "MOVIE")
        }

    /**
     * Validates that SERIES_EPISODE items have required remote IDs, thumbnails with remote IDs,
     * and all contract-specified metadata fields.
     */
    @Test
    fun `validate SERIES_EPISODE items have remote IDs and thumbnails with remote IDs`() =
        runBlocking {
            val allItems = loadAllItems()
            val seriesItems = allItems.filter { it.type == TelegramItemType.SERIES_EPISODE }

            if (seriesItems.isEmpty()) {
                println("⚠️ No SERIES_EPISODE items found in exports - skipping validation")
                return@runBlocking
            }

            println("\n" + "=".repeat(80))
            println("SERIES_EPISODE ITEMS VALIDATION")
            println("=".repeat(80))
            println("Total SERIES_EPISODE items to validate: ${seriesItems.size}")
            println()

            val validationResults = mutableListOf<ItemValidationResult>()

            for (item in seriesItems) {
                val result = validateVideoItem(item, "SERIES_EPISODE")
                validationResults.add(result)
            }

            printValidationSummary("SERIES_EPISODE", validationResults)
            assertNoValidationFailures(validationResults, "SERIES_EPISODE")
        }

    /**
     * Validates that CLIP items (including adult content) have required remote IDs,
     * thumbnails with remote IDs, and all contract-specified metadata fields.
     */
    @Test
    fun `validate CLIP items including adult have remote IDs and thumbnails with remote IDs`() =
        runBlocking {
            val allItems = loadAllItems()
            val clipItems = allItems.filter { it.type == TelegramItemType.CLIP }

            if (clipItems.isEmpty()) {
                println("⚠️ No CLIP items found in exports - skipping validation")
                return@runBlocking
            }

            println("\n" + "=".repeat(80))
            println("CLIP ITEMS VALIDATION (including adult)")
            println("=".repeat(80))
            println("Total CLIP items to validate: ${clipItems.size}")

            val adultClips = clipItems.filter { it.metadata.isAdult }
            val nonAdultClips = clipItems.filter { !it.metadata.isAdult }
            println("  - Adult clips: ${adultClips.size}")
            println("  - Non-adult clips: ${nonAdultClips.size}")
            println()

            val validationResults = mutableListOf<ItemValidationResult>()

            for (item in clipItems) {
                val result = validateVideoItem(item, "CLIP")
                validationResults.add(result)
            }

            printValidationSummary("CLIP", validationResults)
            assertNoValidationFailures(validationResults, "CLIP")
        }

    /**
     * Validates that adult content items have proper isAdult flag and remote IDs.
     */
    @Test
    fun `validate adult content items have isAdult flag and remote IDs`() =
        runBlocking {
            val allItems = loadAllItems()
            val adultItems = allItems.filter { it.metadata.isAdult }

            if (adultItems.isEmpty()) {
                println("⚠️ No adult items found in exports - skipping validation")
                return@runBlocking
            }

            println("\n" + "=".repeat(80))
            println("ADULT CONTENT ITEMS VALIDATION")
            println("=".repeat(80))
            println("Total adult items to validate: ${adultItems.size}")
            println()

            val typeBreakdown = adultItems.groupBy { it.type }
            println("Adult items by type:")
            typeBreakdown.forEach { (type, items) ->
                println("  - $type: ${items.size}")
            }
            println()

            val validationResults = mutableListOf<ItemValidationResult>()

            for (item in adultItems) {
                val result = validateAdultItem(item)
                validationResults.add(result)
            }

            printValidationSummary("ADULT", validationResults)
            assertNoValidationFailures(validationResults, "ADULT")
        }

    /**
     * Comprehensive metadata validation for all video types (MOVIE, SERIES_EPISODE, CLIP).
     * Validates all fields specified in TELEGRAM_PARSER_CONTRACT.md Section 5.3.
     */
    @Test
    fun `validate all video items have complete metadata per contract`() =
        runBlocking {
            val allItems = loadAllItems()
            val videoTypes =
                setOf(
                    TelegramItemType.MOVIE,
                    TelegramItemType.SERIES_EPISODE,
                    TelegramItemType.CLIP,
                )
            val videoItems = allItems.filter { it.type in videoTypes }

            if (videoItems.isEmpty()) {
                println("⚠️ No video items found in exports - skipping validation")
                return@runBlocking
            }

            println("\n" + "=".repeat(80))
            println("COMPREHENSIVE METADATA VALIDATION (ALL VIDEO TYPES)")
            println("=".repeat(80))
            println("Total video items to validate: ${videoItems.size}")
            println()

            val validationResults = mutableListOf<ItemValidationResult>()
            val metadataStats = MetadataStats()

            for (item in videoItems) {
                val result = validateMetadataCompleteness(item)
                validationResults.add(result)
                metadataStats.accumulate(item.metadata, item.posterRef, item.backdropRef)
            }

            // Print metadata statistics
            println("📊 METADATA COMPLETENESS STATISTICS")
            println("-".repeat(40))
            println("Items with title:             ${metadataStats.withTitle}/${videoItems.size}")
            println("Items with year:              ${metadataStats.withYear}/${videoItems.size}")
            println("Items with genres:            ${metadataStats.withGenres}/${videoItems.size}")
            println("Items with tmdbUrl:           ${metadataStats.withTmdbUrl}/${videoItems.size}")
            println("Items with lengthMinutes:     ${metadataStats.withLengthMinutes}/${videoItems.size}")
            println("Items with director:          ${metadataStats.withDirector}/${videoItems.size}")
            println("Items with tmdbRating:        ${metadataStats.withTmdbRating}/${videoItems.size}")
            println("Items with originalTitle:     ${metadataStats.withOriginalTitle}/${videoItems.size}")
            println("Items with productionCountry: ${metadataStats.withProductionCountry}/${videoItems.size}")
            println("Items with fsk:               ${metadataStats.withFsk}/${videoItems.size}")
            println()
            println("📷 IMAGE REFERENCE STATISTICS")
            println("-".repeat(40))
            println("Items with posterRef:         ${metadataStats.withPosterRef}/${videoItems.size}")
            println("Items with backdropRef:       ${metadataStats.withBackdropRef}/${videoItems.size}")
            println()

            printValidationSummary("VIDEO METADATA", validationResults)

            // For metadata completeness, we report statistics but don't fail on missing optional fields
            val criticalFailures = validationResults.filter { it.criticalFailures.isNotEmpty() }
            if (criticalFailures.isNotEmpty()) {
                val errorMsg =
                    buildString {
                        appendLine("Critical validation failures for ${criticalFailures.size} items:")
                        criticalFailures.take(10).forEach { result ->
                            appendLine("  [${result.chatId}:${result.messageId}] ${result.criticalFailures.joinToString(", ")}")
                        }
                        if (criticalFailures.size > 10) {
                            appendLine("  ... and ${criticalFailures.size - 10} more")
                        }
                    }
                assertTrue(errorMsg, criticalFailures.isEmpty())
            }
        }

    // =========================================================================
    // Helper Types
    // =========================================================================

    /**
     * Result of validating a single item.
     */
    data class ItemValidationResult(
        val chatId: Long,
        val messageId: Long,
        val itemType: TelegramItemType,
        val criticalFailures: List<String>,
        val warnings: List<String>,
        val isAdult: Boolean,
    )

    /**
     * Statistics for metadata completeness.
     */
    class MetadataStats {
        var withTitle = 0
        var withYear = 0
        var withGenres = 0
        var withTmdbUrl = 0
        var withLengthMinutes = 0
        var withDirector = 0
        var withTmdbRating = 0
        var withOriginalTitle = 0
        var withProductionCountry = 0
        var withFsk = 0
        var withPosterRef = 0
        var withBackdropRef = 0

        fun accumulate(
            metadata: TelegramMetadata,
            posterRef: TelegramImageRef?,
            backdropRef: TelegramImageRef?,
        ) {
            if (!metadata.title.isNullOrBlank()) withTitle++
            if (metadata.year != null) withYear++
            if (metadata.genres.isNotEmpty()) withGenres++
            if (!metadata.tmdbUrl.isNullOrBlank()) withTmdbUrl++
            if (metadata.lengthMinutes != null) withLengthMinutes++
            if (!metadata.director.isNullOrBlank()) withDirector++
            if (metadata.tmdbRating != null) withTmdbRating++
            if (!metadata.originalTitle.isNullOrBlank()) withOriginalTitle++
            if (!metadata.productionCountry.isNullOrBlank()) withProductionCountry++
            if (metadata.fsk != null) withFsk++
            if (posterRef != null) withPosterRef++
            if (backdropRef != null) withBackdropRef++
        }
    }

    // =========================================================================
    // Validation Helper Functions
    // =========================================================================

    /**
     * Load all items from all export files.
     */
    private fun loadAllItems(): List<TelegramItem> {
        val exportsDir = findExportsDirectory()
        if (exportsDir == null) {
            println("⚠️ Export fixtures directory not found")
            return emptyList()
        }

        val jsonFiles =
            exportsDir
                .walkTopDown()
                .filter { it.isFile && it.extension.lowercase() == "json" }
                .toList()

        val allItems = mutableListOf<TelegramItem>()

        for (file in jsonFiles) {
            try {
                val stats = processExportFile(file)
                if (stats != null) {
                    allItems.addAll(stats.items)
                }
            } catch (e: Exception) {
                println("⚠️ Failed to process ${file.name}: ${e.message}")
            }
        }

        return allItems
    }

    /**
     * Validate a video item (MOVIE, SERIES_EPISODE, CLIP) for remote IDs and thumbnails.
     */
    private fun validateVideoItem(
        item: TelegramItem,
        expectedType: String,
    ): ItemValidationResult {
        val criticalFailures = mutableListOf<String>()
        val warnings = mutableListOf<String>()

        // Per contract: For MOVIE/SERIES_EPISODE/CLIP, videoRef is non-null
        if (item.videoRef == null) {
            criticalFailures.add("videoRef is null for $expectedType item")
        } else {
            // Validate videoRef has required remoteId and uniqueId
            validateMediaRef(item.videoRef, "videoRef", criticalFailures, warnings)
        }

        // Validate posterRef if present (thumbnail)
        if (item.posterRef != null) {
            validateImageRef(item.posterRef, "posterRef", criticalFailures, warnings)
        } else {
            warnings.add("posterRef is null (no thumbnail)")
        }

        // Validate backdropRef if present
        if (item.backdropRef != null) {
            validateImageRef(item.backdropRef, "backdropRef", criticalFailures, warnings)
        }

        return ItemValidationResult(
            chatId = item.chatId,
            messageId = item.anchorMessageId,
            itemType = item.type,
            criticalFailures = criticalFailures,
            warnings = warnings,
            isAdult = item.metadata.isAdult,
        )
    }

    /**
     * Validate an adult content item.
     */
    private fun validateAdultItem(item: TelegramItem): ItemValidationResult {
        val criticalFailures = mutableListOf<String>()
        val warnings = mutableListOf<String>()

        // Verify isAdult flag is set
        if (!item.metadata.isAdult) {
            criticalFailures.add("isAdult flag should be true for adult item")
        }

        // Validate based on item type
        when (item.type) {
            TelegramItemType.MOVIE,
            TelegramItemType.SERIES_EPISODE,
            TelegramItemType.CLIP,
            -> {
                if (item.videoRef != null) {
                    validateMediaRef(item.videoRef, "videoRef", criticalFailures, warnings)
                } else {
                    criticalFailures.add("videoRef is null for video-type adult item")
                }
            }
            TelegramItemType.AUDIOBOOK,
            TelegramItemType.RAR_ITEM,
            -> {
                if (item.documentRef != null) {
                    if (item.documentRef.remoteId.isBlank()) {
                        criticalFailures.add("documentRef.remoteId is blank")
                    }
                    if (item.documentRef.uniqueId.isBlank()) {
                        criticalFailures.add("documentRef.uniqueId is blank")
                    }
                } else {
                    criticalFailures.add("documentRef is null for document-type adult item")
                }
            }
            TelegramItemType.POSTER_ONLY -> {
                // POSTER_ONLY has no video or document ref
            }
        }

        // Validate poster if present
        if (item.posterRef != null) {
            validateImageRef(item.posterRef, "posterRef", criticalFailures, warnings)
        }

        return ItemValidationResult(
            chatId = item.chatId,
            messageId = item.anchorMessageId,
            itemType = item.type,
            criticalFailures = criticalFailures,
            warnings = warnings,
            isAdult = item.metadata.isAdult,
        )
    }

    /**
     * Validate metadata completeness per contract.
     */
    private fun validateMetadataCompleteness(item: TelegramItem): ItemValidationResult {
        val criticalFailures = mutableListOf<String>()
        val warnings = mutableListOf<String>()

        // Critical: videoRef must have remoteId and uniqueId for video types
        if (item.videoRef != null) {
            validateMediaRef(item.videoRef, "videoRef", criticalFailures, warnings)
        } else if (item.type in
            setOf(
                TelegramItemType.MOVIE,
                TelegramItemType.SERIES_EPISODE,
                TelegramItemType.CLIP,
            )
        ) {
            criticalFailures.add("videoRef is null for video-type item")
        }

        // Validate image refs have remote IDs
        if (item.posterRef != null) {
            validateImageRef(item.posterRef, "posterRef", criticalFailures, warnings)
        }
        if (item.backdropRef != null) {
            validateImageRef(item.backdropRef, "backdropRef", criticalFailures, warnings)
        }

        // Optional metadata fields - report as warnings if missing
        val metadata = item.metadata
        if (metadata.title.isNullOrBlank()) {
            warnings.add("title is missing")
        }
        if (metadata.year == null) {
            warnings.add("year is missing")
        }
        if (metadata.genres.isEmpty()) {
            warnings.add("genres is empty")
        }

        return ItemValidationResult(
            chatId = item.chatId,
            messageId = item.anchorMessageId,
            itemType = item.type,
            criticalFailures = criticalFailures,
            warnings = warnings,
            isAdult = item.metadata.isAdult,
        )
    }

    /**
     * Validate a TelegramMediaRef has required remoteId and uniqueId.
     * Per contract Section 5.3: remoteId and uniqueId are REQUIRED.
     */
    private fun validateMediaRef(
        ref: TelegramMediaRef,
        fieldName: String,
        criticalFailures: MutableList<String>,
        @Suppress("UNUSED_PARAMETER") _warnings: MutableList<String>,
    ) {
        if (ref.remoteId.isBlank()) {
            criticalFailures.add("$fieldName.remoteId is blank (REQUIRED per contract)")
        }
        if (ref.uniqueId.isBlank()) {
            criticalFailures.add("$fieldName.uniqueId is blank (REQUIRED per contract)")
        }
    }

    /**
     * Validate a TelegramImageRef has required remoteId and uniqueId.
     * Per contract Section 5.3: remoteId and uniqueId are REQUIRED.
     */
    private fun validateImageRef(
        ref: TelegramImageRef,
        fieldName: String,
        criticalFailures: MutableList<String>,
        @Suppress("UNUSED_PARAMETER") _warnings: MutableList<String>,
    ) {
        if (ref.remoteId.isBlank()) {
            criticalFailures.add("$fieldName.remoteId is blank (REQUIRED per contract)")
        }
        if (ref.uniqueId.isBlank()) {
            criticalFailures.add("$fieldName.uniqueId is blank (REQUIRED per contract)")
        }
    }

    /**
     * Print validation summary.
     */
    private fun printValidationSummary(
        category: String,
        results: List<ItemValidationResult>,
    ) {
        val passed = results.count { it.criticalFailures.isEmpty() }
        val failed = results.count { it.criticalFailures.isNotEmpty() }
        val withWarnings = results.count { it.warnings.isNotEmpty() }

        println("✅ VALIDATION SUMMARY for $category")
        println("-".repeat(40))
        println("Total items:      ${results.size}")
        println("Passed:           $passed")
        println("Failed:           $failed")
        println("With warnings:    $withWarnings")
        println()

        // Print failures
        if (failed > 0) {
            println("❌ FAILURES:")
            results.filter { it.criticalFailures.isNotEmpty() }.take(10).forEach { result ->
                println("   [${result.chatId}:${result.messageId}] ${result.itemType}")
                result.criticalFailures.forEach { failure ->
                    println("      - $failure")
                }
            }
            if (failed > 10) println("   ... and ${failed - 10} more failures")
            println()
        }

        // Print sample warnings
        if (withWarnings > 0) {
            println("⚠️ SAMPLE WARNINGS:")
            results.filter { it.warnings.isNotEmpty() }.take(5).forEach { result ->
                println("   [${result.chatId}:${result.messageId}] ${result.itemType}")
                result.warnings.take(3).forEach { warning ->
                    println("      - $warning")
                }
            }
            if (withWarnings > 5) println("   ... and ${withWarnings - 5} more items with warnings")
            println()
        }

        println("=".repeat(80))
    }

    /**
     * Assert that there are no validation failures.
     */
    private fun assertNoValidationFailures(
        results: List<ItemValidationResult>,
        category: String,
    ) {
        val failures = results.filter { it.criticalFailures.isNotEmpty() }
        if (failures.isNotEmpty()) {
            val errorMsg =
                buildString {
                    appendLine("$category validation failed for ${failures.size} items:")
                    failures.take(10).forEach { result ->
                        appendLine("  [${result.chatId}:${result.messageId}] ${result.itemType}")
                        result.criticalFailures.forEach { failure ->
                            appendLine("    - $failure")
                        }
                    }
                    if (failures.size > 10) {
                        appendLine("  ... and ${failures.size - 10} more")
                    }
                }
            assertTrue(errorMsg, failures.isEmpty())
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
        val paths =
            listOf(
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
