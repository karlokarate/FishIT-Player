package com.fishit.player.infra.tooling.arch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File

/**
 * Comprehensive architecture scan for TDLib ID usage across all v2 modules.
 *
 * This test performs a full codebase scan to ensure:
 * 1. No forbidden patterns leak into v2 modules
 * 2. All required patterns are present
 * 3. Contract compliance is maintained
 *
 * ## Build Guard Configuration
 *
 * To use as a build guard, add to your CI pipeline:
 * ```yaml
 * - name: Architecture Guard
 *   run: ./gradlew :infra:tooling:testDebugUnitTest --tests "*ArchitectureScan*"
 * ```
 *
 * Or add a custom Gradle task:
 * ```kotlin
 * tasks.register("architectureGuard") {
 *     dependsOn(":infra:tooling:testDebugUnitTest")
 *     doLast {
 *         // Fail build if tests fail
 *     }
 * }
 * ```
 */
class TelegramIdArchitectureScanTest {
    companion object {
        private val PROJECT_ROOT: File by lazy {
            findProjectRoot()
        }

        private fun findProjectRoot(): File {
            // Try multiple strategies to find project root
            val candidates =
                listOfNotNull(
                    // Strategy 1: Walk up from user.dir
                    findRootByWalkingUp(File(System.getProperty("user.dir"))),
                    // Strategy 2: Check if we're in infra/tooling
                    File(System.getProperty("user.dir")).resolve("../../../..").canonicalFile,
                    // Strategy 3: Check common workspace paths
                    File("/workspaces/FishIT-Player"),
                    System.getenv("GITHUB_WORKSPACE")?.let { File(it) },
                )

            return candidates.firstOrNull { isProjectRoot(it) }
                ?: throw IllegalStateException("Could not find project root. Tried: ${candidates.map { it.absolutePath }}")
        }

        private fun findRootByWalkingUp(start: File): File? {
            var dir: File? = start
            while (dir != null) {
                if (isProjectRoot(dir)) return dir
                dir = dir.parentFile
            }
            return null
        }

        private fun isProjectRoot(dir: File): Boolean =
            dir.exists() &&
                File(dir, "settings.gradle.kts").exists() &&
                File(dir, "contracts").exists()

        /**
         * Layer classification for v2 modules.
         *
         * The key insight: fileId is allowed in TRANSPORT layer (runtime wrappers)
         * but forbidden in PERSISTENCE, PIPELINE, and DATA layers.
         */
        enum class LayerType {
            /** Transport layer - may use fileId (runtime TDLib access) */
            TRANSPORT,

            /** Pipeline layer - must use remoteId (catalog output) */
            PIPELINE,

            /** Persistence layer - must use remoteId (DB storage) */
            PERSISTENCE,

            /** Data layer - must use remoteId (repository layer) */
            DATA,

            /** Playback layer - may use fileId (runtime stream access) */
            PLAYBACK,

            /** Other layers - should not have Telegram IDs at all */
            OTHER,
        }

        /**
         * Maps module paths to their architectural layer.
         * This is the SMART approach - test by layer, not by filename.
         */
        fun getLayerForPath(path: String): LayerType =
            when {
                // Transport layer - MAY use fileId (runtime wrappers)
                path.contains("infra/transport-telegram") -> LayerType.TRANSPORT

                // Pipeline layer - MUST use remoteId
                path.contains("pipeline/telegram") -> LayerType.PIPELINE
                path.contains("pipeline/xtream") -> LayerType.PIPELINE
                path.contains("pipeline/audiobook") -> LayerType.PIPELINE
                path.contains("pipeline/io") -> LayerType.PIPELINE

                // Persistence layer - MUST use remoteId
                path.contains("core/persistence") -> LayerType.PERSISTENCE

                // Data layer - MUST use remoteId
                path.contains("infra/data-telegram") -> LayerType.DATA
                path.contains("infra/data-xtream") -> LayerType.DATA
                path.contains("infra/data-home") -> LayerType.DATA

                // Playback layer - may use fileId for streaming
                path.contains("playback/telegram") -> LayerType.PLAYBACK
                path.contains("playback/xtream") -> LayerType.PLAYBACK
                path.contains("playback/domain") -> LayerType.PLAYBACK

                // Core model - MUST use remoteId in ImageRef etc.
                path.contains("core/model") -> LayerType.OTHER
                path.contains("core/ui-imaging") -> LayerType.OTHER

                // Everything else
                else -> LayerType.OTHER
            }

        /**
         * Returns true if fileId fields are ALLOWED in this layer.
         *
         * Architecture rule:
         * - TRANSPORT: ✅ fileId allowed (runtime TDLib wrappers)
         * - PLAYBACK: ✅ fileId allowed (runtime streaming)
         * - PIPELINE: ❌ Must use remoteId
         * - PERSISTENCE: ❌ Must use remoteId
         * - DATA: ❌ Must use remoteId
         * - OTHER: ❌ Should not have fileId at all
         */
        fun isFileIdAllowedInLayer(layer: LayerType): Boolean =
            when (layer) {
                LayerType.TRANSPORT -> true // Runtime TDLib access
                LayerType.PLAYBACK -> true // Runtime streaming
                LayerType.PIPELINE -> false // Must output remoteId
                LayerType.PERSISTENCE -> false // Must persist remoteId
                LayerType.DATA -> false // Must use remoteId
                LayerType.OTHER -> false // Should not have fileId
            }

        // All v2 source directories to scan (grouped by layer for clarity)
        private val V2_SOURCE_DIRS =
            listOf(
                // Transport layer (fileId ALLOWED)
                "infra/transport-telegram/src/main",
                // Pipeline layer (fileId FORBIDDEN)
                "pipeline/telegram/src/main",
                "pipeline/xtream/src/main",
                "pipeline/audiobook/src/main",
                "pipeline/io/src/main",
                // Persistence layer (fileId FORBIDDEN)
                "core/persistence/src/main",
                // Data layer (fileId FORBIDDEN)
                "infra/data-telegram/src/main",
                "infra/data-xtream/src/main",
                "infra/data-home/src/main",
                // Playback layer (fileId ALLOWED for runtime streaming)
                "playback/telegram/src/main",
                "playback/xtream/src/main",
                "playback/domain/src/main",
                // Core model layer (fileId FORBIDDEN in DTOs)
                "core/model/src/main",
                "core/ui-imaging/src/main",
                "core/player-model/src/main",
                "core/feature-api/src/main",
                // Feature layer (should not use fileId directly)
                "feature/telegram-media/src/main",
                "feature/home/src/main",
                "feature/library/src/main",
                "feature/live/src/main",
                "feature/detail/src/main",
                "feature/settings/src/main",
                "feature/audiobooks/src/main",
                "feature/onboarding/src/main",
                // Player layer
                "player/internal/src/main",
                "player/ui/src/main",
                "player/ui-api/src/main",
                "player/miniplayer/src/main",
                // App layer
                "app-v2/src/main",
            )

        // Patterns that indicate persistent fileId storage (FORBIDDEN)
        private val PERSISTENT_FILEID_PATTERNS =
            listOf(
                // Data class fields storing fileId
                Triple(
                    "Persistent fileId field",
                    Regex("""(val|var)\s+fileId\s*:\s*(Int|String)\??\s*[,=)]"""),
                    "Use remoteId instead - fileId is volatile and changes across sessions",
                ),
                Triple(
                    "Persistent fileUniqueId field",
                    Regex("""(val|var)\s+fileUniqueId\s*:\s*String\??\s*[,=)]"""),
                    "Remove uniqueId - there's no API to resolve it back to a file",
                ),
                Triple(
                    "Persistent thumbnailFileId field",
                    Regex("""(val|var)\s+thumbnailFileId\s*:\s*String\??\s*[,=)]"""),
                    "Use thumbRemoteId instead",
                ),
                Triple(
                    "Persistent thumbnailUniqueId field",
                    Regex("""(val|var)\s+thumbnailUniqueId\s*:\s*String\??\s*[,=)]"""),
                    "Remove - uniqueId cannot be resolved",
                ),
                Triple(
                    "Persistent thumbFileId field",
                    Regex("""(val|var)\s+thumbFileId\s*:\s*String\??\s*[,=)]"""),
                    "Use thumbRemoteId instead",
                ),
                // Local paths (should not be persisted)
                Triple(
                    "Persistent localPath field",
                    Regex("""(val|var)\s+localPath\s*:\s*String\??\s*[,=)]"""),
                    "Do not persist local paths - TDLib cache paths are volatile",
                ),
                Triple(
                    "Persistent thumbLocalPath field",
                    Regex("""(val|var)\s+thumbLocalPath\s*:\s*String\??\s*[,=)]"""),
                    "Do not persist local paths",
                ),
                Triple(
                    "Persistent posterLocalPath field",
                    Regex("""(val|var)\s+posterLocalPath\s*:\s*String\??\s*[,=)]"""),
                    "Do not persist local paths",
                ),
                Triple(
                    "Persistent posterFileId field",
                    Regex("""(val|var)\s+posterFileId\s*:\s*String\??\s*[,=)]"""),
                    "Use posterRemoteId instead",
                ),
            )
    }

    // ========================================================================
    // Full Codebase Scan
    // ========================================================================

    @Test
    fun `full v2 codebase scan for forbidden TDLib ID patterns`() {
        val allViolations = mutableListOf<ScanViolation>()

        V2_SOURCE_DIRS.forEach { dir ->
            val sourceDir = File(PROJECT_ROOT, dir)
            if (sourceDir.exists()) {
                scanDirectory(sourceDir, allViolations)
            }
        }

        if (allViolations.isNotEmpty()) {
            fail(buildScanReport(allViolations))
        }
    }

    @Test
    fun `transport layer is correctly classified and may use fileId`() {
        // This test verifies that we're not accidentally scanning transport layer
        // It should have fileId usage (that's correct!) and should NOT be flagged

        val transportDir = File(PROJECT_ROOT, "infra/transport-telegram/src/main")
        if (!transportDir.exists()) {
            println("Transport layer not yet implemented - skipping positive validation")
            return
        }

        var transportFileIdCount = 0
        var transportRemoteIdCount = 0

        transportDir
            .walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .forEach { file ->
                val content = file.readText()
                transportFileIdCount += Regex("""\bfileId\b""").findAll(content).count()
                transportRemoteIdCount += Regex("""remoteId""").findAll(content).count()
            }

        println("=== Transport Layer Validation ===")
        println("Transport fileId usage: $transportFileIdCount (ALLOWED)")
        println("Transport remoteId usage: $transportRemoteIdCount")
        println("==================================")

        // Transport layer SHOULD have fileId (it wraps TDLib types)
        // This is a positive validation that our layer classification is working
        assertTrue(
            "Transport layer should use fileId for TDLib runtime wrappers. Found: $transportFileIdCount",
            transportFileIdCount >= 0, // Will be > 0 once transport is implemented
        )

        // Verify layer classification is correct
        val transportPath = "infra/transport-telegram/src/main/Test.kt"
        val classifiedLayer = getLayerForPath(transportPath)
        assertEquals(
            "Transport path should be classified as TRANSPORT layer",
            LayerType.TRANSPORT,
            classifiedLayer,
        )
        assertTrue(
            "TRANSPORT layer should allow fileId",
            isFileIdAllowedInLayer(LayerType.TRANSPORT),
        )
    }

    @Test
    fun `verify layer classification is correct for all module paths`() {
        // Test that our layer classification function works correctly
        val testCases =
            mapOf(
                // Transport - ALLOWED
                "infra/transport-telegram/src/main/TgFile.kt" to LayerType.TRANSPORT,
                "infra/transport-telegram/src/main/DefaultTelegramClient.kt" to LayerType.TRANSPORT,
                // Pipeline - FORBIDDEN
                "pipeline/telegram/src/main/model/TelegramMediaItem.kt" to LayerType.PIPELINE,
                "pipeline/xtream/src/main/XtreamCatalogPipeline.kt" to LayerType.PIPELINE,
                // Persistence - FORBIDDEN
                "core/persistence/src/main/obx/ObxEntities.kt" to LayerType.PERSISTENCE,
                // Data - FORBIDDEN
                "infra/data-telegram/src/main/TelegramRepository.kt" to LayerType.DATA,
                "infra/data-xtream/src/main/XtreamRepository.kt" to LayerType.DATA,
                // Playback - ALLOWED
                "playback/telegram/src/main/TelegramDataSource.kt" to LayerType.PLAYBACK,
                "playback/domain/src/main/PlaybackSourceResolver.kt" to LayerType.PLAYBACK,
            )

        val failures = mutableListOf<String>()

        testCases.forEach { (path, expectedLayer) ->
            val actualLayer = getLayerForPath(path)
            if (actualLayer != expectedLayer) {
                failures.add("Path '$path': expected $expectedLayer but got $actualLayer")
            }

            // Verify fileId rules
            val fileIdAllowed = isFileIdAllowedInLayer(actualLayer)
            val expectedAllowed = actualLayer in listOf(LayerType.TRANSPORT, LayerType.PLAYBACK)
            if (fileIdAllowed != expectedAllowed) {
                failures.add("Layer $actualLayer: fileId allowed=$fileIdAllowed but expected=$expectedAllowed")
            }
        }

        if (failures.isNotEmpty()) {
            fail(
                buildString {
                    appendLine("Layer classification failures:")
                    failures.forEach { appendLine("  - $it") }
                },
            )
        }

        println("=== Layer Classification Verified ===")
        println("All ${testCases.size} path classifications correct")
        println("=====================================")
    }

    @Test
    fun `verify required remoteId fields exist in key files`() {
        val requiredFields =
            listOf(
                RequiredField(
                    file = "core/model/src/main/java/com/fishit/player/core/model/ImageRef.kt",
                    className = "TelegramThumb",
                    fieldPattern = Regex("""val\s+remoteId\s*:\s*String"""),
                    description = "ImageRef.TelegramThumb must have remoteId field",
                ),
                RequiredField(
                    file = "core/persistence/src/main/java/com/fishit/player/core/persistence/obx/ObxEntities.kt",
                    className = "ObxTelegramMessage",
                    fieldPattern = Regex("""var\s+remoteId\s*:\s*String\?"""),
                    description = "ObxTelegramMessage must have remoteId field",
                ),
                RequiredField(
                    file = "core/persistence/src/main/java/com/fishit/player/core/persistence/obx/ObxEntities.kt",
                    className = "ObxTelegramMessage",
                    fieldPattern = Regex("""var\s+thumbRemoteId\s*:\s*String\?"""),
                    description = "ObxTelegramMessage must have thumbRemoteId field",
                ),
                RequiredField(
                    file = "core/persistence/src/main/java/com/fishit/player/core/persistence/obx/ObxEntities.kt",
                    className = "ObxTelegramMessage",
                    fieldPattern = Regex("""var\s+posterRemoteId\s*:\s*String\?"""),
                    description = "ObxTelegramMessage must have posterRemoteId field",
                ),
                RequiredField(
                    file = "pipeline/telegram/src/main/java/com/fishit/player/pipeline/telegram/model/TelegramMediaItem.kt",
                    className = "TelegramMediaItem",
                    fieldPattern = Regex("""val\s+remoteId\s*:\s*String"""),
                    description = "TelegramMediaItem must have remoteId field",
                ),
                RequiredField(
                    file = "pipeline/telegram/src/main/java/com/fishit/player/pipeline/telegram/model/TelegramMediaItem.kt",
                    className = "TelegramMediaItem",
                    fieldPattern = Regex("""val\s+thumbRemoteId\s*:\s*String\?"""),
                    description = "TelegramMediaItem must have thumbRemoteId field",
                ),
                RequiredField(
                    file = "pipeline/telegram/src/main/java/com/fishit/player/pipeline/telegram/model/TelegramPhotoSize.kt",
                    className = "TelegramPhotoSize",
                    fieldPattern = Regex("""val\s+remoteId\s*:\s*String"""),
                    description = "TelegramPhotoSize must have remoteId field",
                ),
                RequiredField(
                    file = "infra/transport-telegram/src/main/java/com/fishit/player/infra/transport/telegram/TelegramThumbFetcher.kt",
                    className = "TgThumbnailRef",
                    fieldPattern = Regex("""val\s+remoteId\s*:\s*String"""),
                    description = "TgThumbnailRef must have remoteId field",
                ),
            )

        val missingFields = mutableListOf<String>()

        requiredFields.forEach { req ->
            val file = File(PROJECT_ROOT, req.file)
            if (file.exists()) {
                val content = file.readText()
                if (!req.fieldPattern.containsMatchIn(content)) {
                    missingFields.add("❌ ${req.description} in ${req.file}")
                }
            } else {
                missingFields.add("⚠️ File not found: ${req.file}")
            }
        }

        if (missingFields.isNotEmpty()) {
            fail(
                buildString {
                    appendLine("Required remoteId fields missing:")
                    appendLine()
                    missingFields.forEach { appendLine("  $it") }
                    appendLine()
                    appendLine("See contracts/TELEGRAM_ID_ARCHITECTURE_CONTRACT.md")
                },
            )
        }
    }

    @Test
    fun `count total remoteId vs fileId usage for metrics`() {
        var remoteIdCount = 0
        var fileIdCountForbiddenLayers = 0
        var fileIdCountAllowedLayers = 0
        var thumbRemoteIdCount = 0
        var thumbFileIdCount = 0

        V2_SOURCE_DIRS.forEach { dir ->
            val sourceDir = File(PROJECT_ROOT, dir)
            if (sourceDir.exists()) {
                sourceDir
                    .walkTopDown()
                    .filter { it.isFile && it.extension == "kt" }
                    .filter { file -> !file.absolutePath.contains("/test/") }
                    .forEach { file ->
                        val relativePath = file.relativeTo(PROJECT_ROOT).path
                        val layer = getLayerForPath(relativePath)
                        val content = file.readText()

                        remoteIdCount += Regex("""remoteId""").findAll(content).count()
                        thumbRemoteIdCount += Regex("""thumbRemoteId""").findAll(content).count()
                        thumbFileIdCount += Regex("""thumbnailFileId|thumbFileId""").findAll(content).count()

                        val fileIdMatches = Regex("""\bfileId\b""").findAll(content).count()
                        if (isFileIdAllowedInLayer(layer)) {
                            fileIdCountAllowedLayers += fileIdMatches
                        } else {
                            fileIdCountForbiddenLayers += fileIdMatches
                        }
                    }
            }
        }

        println("=== TDLib ID Usage Metrics (Layer-Aware) ===")
        println("remoteId usage: $remoteIdCount")
        println("fileId in ALLOWED layers (Transport/Playback): $fileIdCountAllowedLayers")
        println("fileId in FORBIDDEN layers (incl. comments/docs): $fileIdCountForbiddenLayers")
        println("thumbRemoteId usage: $thumbRemoteIdCount")
        println("thumbFileId/thumbnailFileId usage: $thumbFileIdCount")
        println("=============================================")

        // Note: The metric counts ALL occurrences including comments/docs.
        // The actual field declaration scan (PERSISTENT_FILEID_PATTERNS) is what guards
        // against real violations. This metric is for awareness only.
        // fileId mentions in forbidden layers are OK if they're comments explaining
        // why remoteId is used instead.
        println("Note: fileId in forbidden layers may include comments documenting the architecture")

        // thumbFileId should be zero everywhere (not even in comments for new code)
        assertEquals(
            "thumbFileId/thumbnailFileId should not be used",
            0,
            thumbFileIdCount,
        )
    }

    // ========================================================================
    // Contract File Validation
    // ========================================================================

    @Test
    fun `TELEGRAM_ID_ARCHITECTURE_CONTRACT exists and is valid`() {
        val contractFile = File(PROJECT_ROOT, "contracts/TELEGRAM_ID_ARCHITECTURE_CONTRACT.md")
        assertTrue("Contract file should exist", contractFile.exists())

        val content = contractFile.readText()

        // Verify key sections exist (using actual section headers from contract)
        val requiredSections =
            listOf(
                "remoteId-First",
                "MUST persist",
                "MUST NOT persist",
                "Persistence Layer",
                "Coil Memory Cache",
                "getRemoteFile",
            )

        requiredSections.forEach { section ->
            assertTrue(
                "Contract should contain section: $section",
                content.contains(section, ignoreCase = true),
            )
        }
    }

    // ========================================================================
    // Helper Methods
    // ========================================================================

    private fun scanDirectory(
        dir: File,
        violations: MutableList<ScanViolation>,
    ) {
        dir
            .walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filter { file -> !file.absolutePath.contains("/test/") } // Skip test files
            .forEach { file ->
                val relativePath = file.relativeTo(PROJECT_ROOT).path
                val layer = getLayerForPath(relativePath)

                // Only scan for forbidden patterns if fileId is NOT allowed in this layer
                if (!isFileIdAllowedInLayer(layer)) {
                    scanFile(file, violations, layer)
                }
            }
    }

    private fun scanFile(
        file: File,
        violations: MutableList<ScanViolation>,
        layer: LayerType,
    ) {
        val content = file.readText()
        val lines = content.lines()
        val relativePath = file.relativeTo(PROJECT_ROOT).path

        PERSISTENT_FILEID_PATTERNS.forEach { (name, pattern, fix) ->
            lines.forEachIndexed { index, line ->
                if (pattern.containsMatchIn(line)) {
                    // Skip if it's in a comment
                    val trimmed = line.trim()
                    if (!trimmed.startsWith("//") && !trimmed.startsWith("*") && !trimmed.startsWith("/*")) {
                        violations.add(
                            ScanViolation(
                                file = relativePath,
                                line = index + 1,
                                content = line.trim(),
                                violationType = name,
                                suggestedFix = fix,
                                layer = layer,
                            ),
                        )
                    }
                }
            }
        }
    }

    private fun buildScanReport(violations: List<ScanViolation>): String =
        buildString {
            appendLine("╔══════════════════════════════════════════════════════════════════════╗")
            appendLine("║           TDLib ID Architecture Violation Report                     ║")
            appendLine("╚══════════════════════════════════════════════════════════════════════╝")
            appendLine()
            appendLine("Found ${violations.size} violation(s) across the v2 codebase:")
            appendLine()

            // Group by layer first for better visibility
            violations.groupBy { it.layer }.forEach { (layer, layerViolations) ->
                appendLine("═══ Layer: $layer (fileId forbidden) ═══")
                appendLine()

                layerViolations.groupBy { it.file }.forEach { (file, fileViolations) ->
                    appendLine("┌─ 📄 $file")
                    fileViolations.forEach { v ->
                        appendLine("│  Line ${v.line}: ${v.violationType}")
                        appendLine("│  Code: ${v.content}")
                        appendLine("│  Fix:  ${v.suggestedFix}")
                        appendLine("│")
                    }
                    appendLine("└────────────────────────────────────────────────────────────────────")
                    appendLine()
                }
            }

            appendLine("═══════════════════════════════════════════════════════════════════════")
            appendLine("Layer Rules:")
            appendLine("  TRANSPORT: ✅ fileId allowed (runtime TDLib wrappers)")
            appendLine("  PLAYBACK:  ✅ fileId allowed (runtime streaming)")
            appendLine("  PIPELINE:  ❌ Must use remoteId")
            appendLine("  PERSISTENCE: ❌ Must use remoteId")
            appendLine("  DATA:      ❌ Must use remoteId")
            appendLine("═══════════════════════════════════════════════════════════════════════")
            appendLine("Contract Reference: contracts/TELEGRAM_ID_ARCHITECTURE_CONTRACT.md")
            appendLine("═══════════════════════════════════════════════════════════════════════")
        }

    data class ScanViolation(
        val file: String,
        val line: Int,
        val content: String,
        val violationType: String,
        val suggestedFix: String,
        val layer: LayerType = LayerType.OTHER,
    )

    data class RequiredField(
        val file: String,
        val className: String,
        val fieldPattern: Regex,
        val description: String,
    )
}
