package com.fishit.player.infra.tooling.arch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File

/**
 * Architecture tests for the remoteId-First TDLib ID architecture.
 *
 * These tests validate that:
 * 1. Forbidden patterns (fileId, fileUniqueId fields) are NOT used in v2 modules
 * 2. Required patterns (remoteId, thumbRemoteId) ARE used correctly
 * 3. The architecture contract is enforced across the codebase
 *
 * ## Contract Reference
 * See `contracts/TELEGRAM_ID_ARCHITECTURE_CONTRACT.md` for the binding rules.
 *
 * ## Build Guard Usage
 * These tests can be configured as build guards by:
 * 1. Running them in CI on every PR
 * 2. Adding them to a custom Gradle task that fails the build
 * 3. Integrating with Detekt custom rules
 *
 * @see contracts/TELEGRAM_ID_ARCHITECTURE_CONTRACT.md
 */
class TelegramIdArchitectureTest {

    companion object {
        // Root directory - works both in IDE and Gradle
        private val PROJECT_ROOT: File by lazy {
            findProjectRoot()
        }
        
        private fun findProjectRoot(): File {
            // Try multiple strategies to find project root
            val candidates = listOfNotNull(
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
        
        private fun isProjectRoot(dir: File): Boolean {
            return dir.exists() && 
                   File(dir, "settings.gradle.kts").exists() &&
                   File(dir, "contracts").exists()
        }

        // v2 modules to scan (excluding legacy)
        private val V2_MODULE_PATHS = listOf(
            "core/model",
            "core/persistence",
            "core/ui-imaging",
            "pipeline/telegram",
            "infra/transport-telegram",
            "infra/data-telegram",
            "playback/telegram",
            "feature/telegram-media",
        )

        // Forbidden field patterns in DTOs/Entities (not in transport layer APIs)
        private val FORBIDDEN_FIELD_PATTERNS = listOf(
            // Direct fileId/fileUniqueId field declarations
            Regex("""val\s+fileId\s*:\s*(Int|String)\??\s*[,=)]"""),
            Regex("""val\s+fileUniqueId\s*:\s*String\??\s*[,=)]"""),
            Regex("""var\s+fileId\s*:\s*(Int|String)\??\s*[,=)]"""),
            Regex("""var\s+fileUniqueId\s*:\s*String\??\s*[,=)]"""),
            // Thumbnail fileId fields
            Regex("""val\s+thumbnailFileId\s*:\s*String\??\s*[,=)]"""),
            Regex("""val\s+thumbnailUniqueId\s*:\s*String\??\s*[,=)]"""),
            Regex("""val\s+thumbFileId\s*:\s*String\??\s*[,=)]"""),
            // Local path fields (should not be persisted)
            Regex("""val\s+localPath\s*:\s*String\??\s*[,=)]"""),
            Regex("""val\s+thumbLocalPath\s*:\s*String\??\s*[,=)]"""),
            Regex("""val\s+posterLocalPath\s*:\s*String\??\s*[,=)]"""),
            Regex("""var\s+localPath\s*:\s*String\??\s*[,=)]"""),
            Regex("""var\s+thumbLocalPath\s*:\s*String\??\s*[,=)]"""),
            Regex("""var\s+posterLocalPath\s*:\s*String\??\s*[,=)]"""),
        )

        // Files/Paths to exclude from forbidden pattern checks
        // These are allowed to use fileId because they're transport-layer APIs
        private val EXCLUDED_PATHS = listOf(
            "TelegramFileClient.kt",      // Transport API - uses fileId for downloads
            "TelegramThumbFetcher.kt",    // Interface definition
            "TelegramThumbFetcherImpl.kt", // Implementation that resolves remoteId→fileId
            "TelegramFileDownloadManager.kt", // Download manager
            "TelegramFileDataSource.kt",  // DataSource uses runtime fileId
            "TelegramFileReadyEnsurer.kt", // Runtime file handling
            "TelegramTransportClient.kt", // Transport wrapper types
            "TgFile.kt",                  // Runtime file state
            "TgContent.kt",               // Transport content types
            "TgMessage.kt",               // Transport message type
            "TgChat.kt",                  // Transport chat type
            "/test/",                      // Test files may use old patterns for legacy compat
        )

        // Required patterns that MUST exist in specific files
        private val REQUIRED_PATTERNS = mapOf(
            // ImageRef.TelegramThumb must have remoteId
            "core/model" to listOf(
                Regex("""data class TelegramThumb\([^)]*remoteId\s*:\s*String"""),
            ),
            // ObxTelegramMessage must have thumbRemoteId and posterRemoteId
            "core/persistence" to listOf(
                Regex("""var\s+thumbRemoteId\s*:\s*String\?"""),
                Regex("""var\s+posterRemoteId\s*:\s*String\?"""),
                Regex("""var\s+remoteId\s*:\s*String\?"""),
            ),
            // TelegramMediaItem must have remoteId and thumbRemoteId
            "pipeline/telegram" to listOf(
                Regex("""val\s+remoteId\s*:\s*String"""),
                Regex("""val\s+thumbRemoteId\s*:\s*String\?"""),
            ),
            // TelegramPhotoSize must have remoteId only
            "pipeline/telegram" to listOf(
                Regex("""data class TelegramPhotoSize\([^)]*remoteId\s*:\s*String"""),
            ),
        )
    }

    // ========================================================================
    // Forbidden Pattern Tests
    // ========================================================================

    @Test
    fun `no fileId fields in pipeline telegram module`() {
        assertNoForbiddenPatterns("pipeline/telegram")
    }

    @Test
    fun `no fileId fields in core persistence module`() {
        assertNoForbiddenPatterns("core/persistence")
    }

    @Test
    fun `no fileId fields in core model module`() {
        assertNoForbiddenPatterns("core/model")
    }

    @Test
    fun `no fileId fields in infra data-telegram module`() {
        assertNoForbiddenPatterns("infra/data-telegram")
    }

    @Test
    fun `no localPath fields persisted in core persistence`() {
        val modulePath = File(PROJECT_ROOT, "core/persistence")
        val violations = findForbiddenPatterns(modulePath, listOf(
            Regex("""var\s+localPath\s*:\s*String\?"""),
            Regex("""var\s+thumbLocalPath\s*:\s*String\?"""),
            Regex("""var\s+posterLocalPath\s*:\s*String\?"""),
        ))
        
        if (violations.isNotEmpty()) {
            fail(buildViolationMessage("localPath fields in persistence", violations))
        }
    }

    // ========================================================================
    // Required Pattern Tests
    // ========================================================================

    @Test
    fun `ImageRef TelegramThumb uses remoteId only`() {
        val imageRefFile = File(PROJECT_ROOT, "core/model/src/main/java/com/fishit/player/core/model/ImageRef.kt")
        assertTrue("ImageRef.kt should exist", imageRefFile.exists())
        
        val content = imageRefFile.readText()
        
        // Must have remoteId
        assertTrue(
            "TelegramThumb must have remoteId field",
            content.contains(Regex("""data class TelegramThumb\([^)]*val\s+remoteId\s*:\s*String"""))
        )
        
        // Must NOT have fileId or uniqueId as data class fields
        val telegramThumbBlock = extractDataClassBlock(content, "TelegramThumb")
        if (telegramThumbBlock != null) {
            assertTrue(
                "TelegramThumb must NOT have fileId field",
                !telegramThumbBlock.contains(Regex("""val\s+fileId\s*:"""))
            )
            assertTrue(
                "TelegramThumb must NOT have uniqueId field",
                !telegramThumbBlock.contains(Regex("""val\s+uniqueId\s*:"""))
            )
        }
    }

    @Test
    fun `ObxTelegramMessage has remoteId fields`() {
        val entitiesFile = File(PROJECT_ROOT, "core/persistence/src/main/java/com/fishit/player/core/persistence/obx/ObxEntities.kt")
        assertTrue("ObxEntities.kt should exist", entitiesFile.exists())
        
        val content = entitiesFile.readText()
        
        // Must have remoteId fields
        assertTrue(
            "ObxTelegramMessage must have remoteId field",
            content.contains(Regex("""var\s+remoteId\s*:\s*String\?"""))
        )
        assertTrue(
            "ObxTelegramMessage must have thumbRemoteId field",
            content.contains(Regex("""var\s+thumbRemoteId\s*:\s*String\?"""))
        )
        assertTrue(
            "ObxTelegramMessage must have posterRemoteId field",
            content.contains(Regex("""var\s+posterRemoteId\s*:\s*String\?"""))
        )
    }

    @Test
    fun `ObxTelegramMessage does NOT have forbidden fields`() {
        val entitiesFile = File(PROJECT_ROOT, "core/persistence/src/main/java/com/fishit/player/core/persistence/obx/ObxEntities.kt")
        assertTrue("ObxEntities.kt should exist", entitiesFile.exists())
        
        val content = entitiesFile.readText()
        val entityBlock = extractEntityBlock(content, "ObxTelegramMessage")
        
        if (entityBlock != null) {
            // Must NOT have these fields
            val forbiddenFields = listOf(
                "fileId" to Regex("""var\s+fileId\s*:"""),
                "fileUniqueId" to Regex("""var\s+fileUniqueId\s*:"""),
                "thumbFileId" to Regex("""var\s+thumbFileId\s*:"""),
                "localPath" to Regex("""var\s+localPath\s*:"""),
                "thumbLocalPath" to Regex("""var\s+thumbLocalPath\s*:"""),
                "posterLocalPath" to Regex("""var\s+posterLocalPath\s*:"""),
                "posterFileId" to Regex("""var\s+posterFileId\s*:"""),
            )
            
            forbiddenFields.forEach { (name, pattern) ->
                assertTrue(
                    "ObxTelegramMessage must NOT have $name field",
                    !entityBlock.contains(pattern)
                )
            }
        }
    }

    @Test
    fun `TelegramMediaItem uses remoteId and thumbRemoteId`() {
        val mediaItemFile = File(PROJECT_ROOT, "pipeline/telegram/src/main/java/com/fishit/player/pipeline/telegram/model/TelegramMediaItem.kt")
        assertTrue("TelegramMediaItem.kt should exist", mediaItemFile.exists())
        
        val content = mediaItemFile.readText()
        
        // Must have correct fields
        assertTrue(
            "TelegramMediaItem must have remoteId field",
            content.contains(Regex("""val\s+remoteId\s*:\s*String"""))
        )
        assertTrue(
            "TelegramMediaItem must have thumbRemoteId field",
            content.contains(Regex("""val\s+thumbRemoteId\s*:\s*String\?"""))
        )
        
        // Must NOT have forbidden fields
        val forbiddenInMediaItem = listOf(
            "fileId" to Regex("""val\s+fileId\s*:\s*(Int|String)"""),
            "fileUniqueId" to Regex("""val\s+fileUniqueId\s*:"""),
            "thumbnailFileId" to Regex("""val\s+thumbnailFileId\s*:"""),
            "thumbnailUniqueId" to Regex("""val\s+thumbnailUniqueId\s*:"""),
        )
        
        forbiddenInMediaItem.forEach { (name, pattern) ->
            assertTrue(
                "TelegramMediaItem must NOT have $name field",
                !content.contains(pattern)
            )
        }
    }

    @Test
    fun `TelegramPhotoSize uses remoteId only`() {
        val photoSizeFile = File(PROJECT_ROOT, "pipeline/telegram/src/main/java/com/fishit/player/pipeline/telegram/model/TelegramPhotoSize.kt")
        assertTrue("TelegramPhotoSize.kt should exist", photoSizeFile.exists())
        
        val content = photoSizeFile.readText()
        
        // Must have remoteId
        assertTrue(
            "TelegramPhotoSize must have remoteId field",
            content.contains(Regex("""val\s+remoteId\s*:\s*String"""))
        )
        
        // Must NOT have fileId or fileUniqueId
        assertTrue(
            "TelegramPhotoSize must NOT have fileId field",
            !content.contains(Regex("""val\s+fileId\s*:\s*String"""))
        )
        assertTrue(
            "TelegramPhotoSize must NOT have fileUniqueId field",
            !content.contains(Regex("""val\s+fileUniqueId\s*:"""))
        )
    }

    @Test
    fun `TgThumbnailRef uses remoteId only`() {
        val thumbFetcherFile = File(PROJECT_ROOT, "infra/transport-telegram/src/main/java/com/fishit/player/infra/transport/telegram/TelegramThumbFetcher.kt")
        assertTrue("TelegramThumbFetcher.kt should exist", thumbFetcherFile.exists())
        
        val content = thumbFetcherFile.readText()
        
        // TgThumbnailRef must have remoteId
        assertTrue(
            "TgThumbnailRef must have remoteId field",
            content.contains(Regex("""val\s+remoteId\s*:\s*String"""))
        )
        
        // TgThumbnailRef must NOT have fileId as a stored field
        // (it's okay in comments/docs)
        val interfaceBlock = extractInterfaceBlock(content, "TgThumbnailRef")
        if (interfaceBlock != null) {
            assertTrue(
                "TgThumbnailRef must NOT have fileId property",
                !interfaceBlock.contains(Regex("""val\s+fileId\s*:\s*Int"""))
            )
        }
    }

    // ========================================================================
    // Cross-Module Consistency Tests
    // ========================================================================

    @Test
    fun `all Telegram modules use consistent remoteId naming`() {
        val expectedRemoteIdUsage = mapOf(
            "pipeline/telegram/src/main/java/com/fishit/player/pipeline/telegram/model/TelegramMediaItem.kt" to "remoteId",
            "pipeline/telegram/src/main/java/com/fishit/player/pipeline/telegram/model/TelegramPhotoSize.kt" to "remoteId",
            "core/persistence/src/main/java/com/fishit/player/core/persistence/obx/ObxEntities.kt" to "remoteId",
            "core/model/src/main/java/com/fishit/player/core/model/ImageRef.kt" to "remoteId",
        )
        
        expectedRemoteIdUsage.forEach { (path, fieldName) ->
            val file = File(PROJECT_ROOT, path)
            if (file.exists()) {
                val content = file.readText()
                assertTrue(
                    "File $path must contain '$fieldName' field",
                    content.contains(Regex("""(val|var)\s+$fieldName\s*:\s*String"""))
                )
            }
        }
    }

    @Test
    fun `ImageRefKeyer uses remoteId for cache key`() {
        val keyerFile = File(PROJECT_ROOT, "core/ui-imaging/src/main/java/com/fishit/player/core/ui/imaging/ImageRefFetcher.kt")
        if (keyerFile.exists()) {
            val content = keyerFile.readText()
            
            // Cache key should use remoteId, not uniqueId
            assertTrue(
                "ImageRefKeyer should use remoteId for cache key",
                content.contains("remoteId") && content.contains("tg:")
            )
            
            // Should NOT use uniqueId for cache key
            assertTrue(
                "ImageRefKeyer should NOT use uniqueId for cache key",
                !content.contains(Regex("""tg:\$\{.*uniqueId"""))
            )
        }
    }

    @Test
    fun `ImageRef URI format uses remoteId`() {
        val imageRefFile = File(PROJECT_ROOT, "core/model/src/main/java/com/fishit/player/core/model/ImageRef.kt")
        assertTrue("ImageRef.kt should exist", imageRefFile.exists())
        
        val content = imageRefFile.readText()
        
        // URI format should be tg://thumb/<remoteId>?...
        assertTrue(
            "ImageRef should build URI with remoteId",
            content.contains("tg://thumb/") && content.contains("remoteId")
        )
        
        // Should NOT have old format with fileId/uniqueId in path
        assertTrue(
            "ImageRef should NOT use old URI format with fileId/uniqueId",
            !content.contains(Regex("""tg://thumb/\$\{[^}]*fileId"""))
        )
    }

    // ========================================================================
    // Helper Methods
    // ========================================================================

    private fun assertNoForbiddenPatterns(modulePath: String) {
        val moduleDir = File(PROJECT_ROOT, modulePath)
        val violations = findForbiddenPatterns(moduleDir, FORBIDDEN_FIELD_PATTERNS)
        
        if (violations.isNotEmpty()) {
            fail(buildViolationMessage("Forbidden TDLib ID patterns in $modulePath", violations))
        }
    }

    private fun findForbiddenPatterns(
        moduleDir: File,
        patterns: List<Regex>
    ): List<Violation> {
        val violations = mutableListOf<Violation>()
        
        if (!moduleDir.exists()) return violations
        
        moduleDir.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filter { file -> EXCLUDED_PATHS.none { excluded -> file.absolutePath.contains(excluded) } }
            .forEach { file ->
                val content = file.readText()
                val lines = content.lines()
                
                patterns.forEach { pattern ->
                    lines.forEachIndexed { index, line ->
                        if (pattern.containsMatchIn(line)) {
                            violations.add(Violation(
                                file = file.relativeTo(PROJECT_ROOT).path,
                                line = index + 1,
                                content = line.trim(),
                                pattern = pattern.pattern
                            ))
                        }
                    }
                }
            }
        
        return violations
    }

    private fun extractDataClassBlock(content: String, className: String): String? {
        val regex = Regex("""data class $className\s*\([^)]+\)""", RegexOption.DOT_MATCHES_ALL)
        return regex.find(content)?.value
    }

    private fun extractEntityBlock(content: String, entityName: String): String? {
        val startIndex = content.indexOf("@Entity\ndata class $entityName")
        if (startIndex == -1) {
            // Try without newline
            val altStartIndex = content.indexOf("@Entity data class $entityName")
            if (altStartIndex == -1) return null
        }
        
        val classStart = content.indexOf("data class $entityName", startIndex.coerceAtLeast(0))
        if (classStart == -1) return null
        
        // Find the closing of the data class constructor
        var depth = 0
        var inClass = false
        var endIndex = classStart
        
        for (i in classStart until content.length) {
            when (content[i]) {
                '(' -> {
                    inClass = true
                    depth++
                }
                ')' -> {
                    depth--
                    if (inClass && depth == 0) {
                        endIndex = i
                        break
                    }
                }
            }
        }
        
        return content.substring(classStart, endIndex + 1)
    }

    private fun extractInterfaceBlock(content: String, interfaceName: String): String? {
        val startIndex = content.indexOf("interface $interfaceName")
        if (startIndex == -1) {
            // Try data class
            val dataClassIndex = content.indexOf("data class $interfaceName")
            if (dataClassIndex == -1) return null
            return extractDataClassBlock(content, interfaceName)
        }
        
        // Find the closing brace
        var depth = 0
        var inInterface = false
        var endIndex = startIndex
        
        for (i in startIndex until content.length) {
            when (content[i]) {
                '{' -> {
                    inInterface = true
                    depth++
                }
                '}' -> {
                    depth--
                    if (inInterface && depth == 0) {
                        endIndex = i
                        break
                    }
                }
            }
        }
        
        return content.substring(startIndex, endIndex + 1)
    }

    private fun buildViolationMessage(context: String, violations: List<Violation>): String {
        return buildString {
            appendLine("$context found ${violations.size} violation(s):")
            appendLine()
            violations.groupBy { it.file }.forEach { (file, fileViolations) ->
                appendLine("  📄 $file:")
                fileViolations.forEach { v ->
                    appendLine("    Line ${v.line}: ${v.content}")
                    appendLine("    Pattern: ${v.pattern}")
                }
            }
            appendLine()
            appendLine("See contracts/TELEGRAM_ID_ARCHITECTURE_CONTRACT.md for the correct patterns.")
        }
    }

    data class Violation(
        val file: String,
        val line: Int,
        val content: String,
        val pattern: String
    )
}
