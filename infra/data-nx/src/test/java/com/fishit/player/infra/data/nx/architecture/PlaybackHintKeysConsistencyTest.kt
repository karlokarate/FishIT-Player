/**
 * Architecture test: ensures PlaybackHintKeys constants are used instead of raw strings.
 *
 * NX_CONSOLIDATION_PLAN Phase 8 — Prevents inconsistent key strings.
 *
 * The SSOT for PlaybackHint key names is [PlaybackHintKeys].
 * Using raw strings like "xtream.streamId" creates silent bugs when:
 * - One producer writes "streamId" while consumer reads "xtream.streamId"
 * - Key names are changed in PlaybackHintKeys but raw strings remain
 *
 * If a test fails, replace the raw string with the appropriate PlaybackHintKeys constant.
 */
package com.fishit.player.infra.data.nx.architecture

import org.junit.Test
import java.io.File
import kotlin.test.assertTrue

class PlaybackHintKeysConsistencyTest {
    /**
     * No raw "xtream.*" playback hint key strings in source files.
     *
     * Raw strings like `"xtream.streamId"` MUST use PlaybackHintKeys.Xtream.STREAM_ID instead.
     * Excludes PlaybackHintKeys.kt (the SSOT) and test files.
     */
    @Test
    fun `no raw xtream hint key strings in main source files`() {
        val violations =
            scanSourceFiles(
                pattern = Regex(""""xtream\.\w+""""),
                excludeFiles =
                    setOf(
                        "PlaybackHintKeys.kt",
                        "PlaybackHintKeysConsistencyTest.kt",
                    ),
            )

        assertTrue(
            violations.isEmpty(),
            "Raw xtream.* hint key strings found (should use PlaybackHintKeys.Xtream.*):\n" +
                violations.joinToString("\n") { "  ${it.file}:${it.lineNumber}: ${it.content.trim()}" } +
                "\n\nFix: Use PlaybackHintKeys.Xtream.* constants instead of raw strings.",
        )
    }

    /**
     * No raw "telegram.*" playback hint key strings in source files.
     *
     * Raw strings like `"telegram.chatId"` MUST use PlaybackHintKeys.Telegram.CHAT_ID instead.
     */
    @Test
    fun `no raw telegram hint key strings in main source files`() {
        val violations =
            scanSourceFiles(
                pattern = Regex(""""telegram\.\w+""""),
                excludeFiles =
                    setOf(
                        "PlaybackHintKeys.kt",
                        "PlaybackHintKeysConsistencyTest.kt",
                    ),
            )

        assertTrue(
            violations.isEmpty(),
            "Raw telegram.* hint key strings found (should use PlaybackHintKeys.Telegram.*):\n" +
                violations.joinToString("\n") { "  ${it.file}:${it.lineNumber}: ${it.content.trim()}" } +
                "\n\nFix: Use PlaybackHintKeys.Telegram.* constants instead of raw strings.",
        )
    }

    /**
     * No raw "video.*" or "audio.*" playback hint key strings in source files.
     *
     * These common-codec hint keys are defined in PlaybackHintKeys top-level.
     */
    @Test
    fun `no raw codec hint key strings in main source files`() {
        val codecPatterns =
            listOf(
                Regex(""""video\.codec""""),
                Regex(""""video\.width""""),
                Regex(""""video\.height""""),
                Regex(""""audio\.codec""""),
                Regex(""""audio\.channels""""),
            )

        val violations = mutableListOf<Violation>()
        for (pattern in codecPatterns) {
            violations +=
                scanSourceFiles(
                    pattern = pattern,
                    excludeFiles =
                        setOf(
                            "PlaybackHintKeys.kt",
                            "PlaybackHintKeysConsistencyTest.kt",
                        ),
                )
        }

        assertTrue(
            violations.isEmpty(),
            "Raw codec hint key strings found (should use PlaybackHintKeys.*):\n" +
                violations.joinToString("\n") { "  ${it.file}:${it.lineNumber}: ${it.content.trim()}" } +
                "\n\nFix: Use PlaybackHintKeys.VIDEO_CODEC etc. instead of raw strings.",
        )
    }

    // =========================================================================
    // Source Scanning Infrastructure
    // =========================================================================

    private data class Violation(
        val file: String,
        val lineNumber: Int,
        val content: String,
    )

    /**
     * Scan Kotlin source files across the project for a forbidden pattern.
     *
     * Scans multiple module src/main/java directories to catch violations
     * in pipeline, playback, player, and data layers.
     */
    private fun scanSourceFiles(
        pattern: Regex,
        excludeFiles: Set<String> = emptySet(),
    ): List<Violation> {
        val violations = mutableListOf<Violation>()

        for (sourceDir in findSourceDirs()) {
            sourceDir
                .walkTopDown()
                .filter { it.isFile && it.extension == "kt" }
                .filter { it.name !in excludeFiles }
                .forEach { file ->
                    file.readLines().forEachIndexed { index, line ->
                        val trimmed = line.trim()
                        if (trimmed.startsWith("//") || trimmed.startsWith("*") || trimmed.startsWith("/*")) {
                            return@forEachIndexed
                        }
                        if (pattern.containsMatchIn(line)) {
                            val relativePath = file.relativeTo(sourceDir).path
                            violations.add(Violation(relativePath, index + 1, line))
                        }
                    }
                }
        }

        return violations
    }

    /**
     * Find src/main/java directories across relevant modules.
     *
     * PlaybackHintKeys are used across many layers, so we scan broadly.
     */
    private fun findSourceDirs(): List<File> {
        // Try to find the project root
        val projectRoot =
            listOf(
                File(System.getProperty("user.dir")),
                File(System.getProperty("user.dir")).parentFile,
            ).firstOrNull { root ->
                File(root, "settings.gradle.kts").exists() ||
                    File(root, "infra/data-nx/src/main/java").exists()
            }

        if (projectRoot == null) {
            return listOfNotNull(
                File("src/main/java").takeIf { it.exists() },
            )
        }

        // Scan all module source directories where PlaybackHintKeys might be used
        val modulePaths =
            listOf(
                "infra/data-nx/src/main/java",
                "pipeline/xtream/src/main/java",
                "pipeline/telegram/src/main/java",
                "playback/src/main/java",
                "player/src/main/java",
                "core/model/src/main/java",
            )

        return modulePaths
            .map { File(projectRoot, it) }
            .filter { it.exists() && it.isDirectory }
    }
}
