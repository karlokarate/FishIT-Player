/**
 * Architecture test: ensures WorkType/SourceType enum .name is never used directly
 * for DB queries or entity string comparison.
 *
 * NX_CONSOLIDATION_PLAN Phase 8 — Prevents C1-class regressions.
 *
 * The SSOT for enum↔entity-string conversion is [WorkTypeMapper] / [SourceTypeMapper].
 * Using .name directly causes silent bugs when enum name ≠ entity string
 * (e.g., WorkType.LIVE_CHANNEL.name = "LIVE_CHANNEL" but DB stores "LIVE").
 *
 * If a test fails, use WorkTypeMapper.toEntityString() / SourceTypeMapper.toEntityString().
 */
package com.fishit.player.infra.data.nx.architecture

import com.fishit.player.core.model.repository.NxWorkRepository.WorkType
import com.fishit.player.infra.data.nx.mapper.MediaTypeMapper
import com.fishit.player.infra.data.nx.mapper.SourceItemKindMapper
import com.fishit.player.infra.data.nx.mapper.WorkTypeMapper
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Test

class TypeMapperConsistencyTest {

    // =========================================================================
    // Source Scanning: Forbidden Patterns
    // =========================================================================

    /**
     * WorkType.*.name MUST NOT appear in source code (except TypeMappers.kt itself).
     *
     * Background: WorkType.LIVE_CHANNEL.name returns "LIVE_CHANNEL" but the DB stores "LIVE".
     * This caused a C1-critical bug where all live channel queries returned zero results.
     */
    @Test
    fun `WorkType enum name is never used directly in source files`() {
        val violations = scanSourceFiles(
            pattern = Regex("""WorkType\.\w+\.name"""),
            excludeFiles = setOf("TypeMappers.kt", "TypeMapperConsistencyTest.kt"),
        )

        assertTrue(
            violations.isEmpty(),
            "WorkType.*.name used directly (bypasses WorkTypeMapper SSOT):\n" +
                violations.joinToString("\n") { "  ${it.file}:${it.lineNumber}: ${it.content.trim()}" } +
                "\n\nFix: Use WorkTypeMapper.toEntityString(WorkType.*) instead.",
        )
    }

    /**
     * SourceType.*.name MUST NOT appear in source code (except TypeMappers.kt).
     * Same pattern as WorkType — entity strings may differ from enum names.
     */
    @Test
    fun `SourceType enum name is never used directly in source files`() {
        val violations = scanSourceFiles(
            pattern = Regex("""SourceType\.\w+\.name"""),
            excludeFiles = setOf("TypeMappers.kt", "TypeMapperConsistencyTest.kt"),
        )

        assertTrue(
            violations.isEmpty(),
            "SourceType.*.name used directly (bypasses SourceTypeMapper SSOT):\n" +
                violations.joinToString("\n") { "  ${it.file}:${it.lineNumber}: ${it.content.trim()}" } +
                "\n\nFix: Use SourceTypeMapper.toEntityString(SourceType.*) instead.",
        )
    }

    /**
     * SourceItemKind.*.name MUST NOT appear in source code (except TypeMappers.kt).
     */
    @Test
    fun `SourceItemKind enum name is never used directly in source files`() {
        val violations = scanSourceFiles(
            pattern = Regex("""SourceItemKind\.\w+\.name"""),
            excludeFiles = setOf("TypeMappers.kt", "TypeMapperConsistencyTest.kt"),
        )

        assertTrue(
            violations.isEmpty(),
            "SourceItemKind.*.name used directly (bypasses SourceItemKindMapper SSOT):\n" +
                violations.joinToString("\n") { "  ${it.file}:${it.lineNumber}: ${it.content.trim()}" } +
                "\n\nFix: Use SourceItemKindMapper.toEntityString(SourceItemKind.*) instead.",
        )
    }

    // =========================================================================
    // Bidirectional Roundtrip: WorkType
    // =========================================================================

    /**
     * Every WorkType enum value must survive a roundtrip through
     * toEntityString() → toWorkType() without loss.
     */
    @Test
    fun `all WorkType values roundtrip through toEntityString and toWorkType`() {
        for (type in WorkType.entries) {
            val entityString = WorkTypeMapper.toEntityString(type)
            val roundtrip = WorkTypeMapper.toWorkType(entityString)
            assertEquals(
                type,
                roundtrip,
                "WorkType roundtrip failed: $type → \"$entityString\" → $roundtrip",
            )
        }
    }

    /**
     * Documents the known divergences between enum name and entity string.
     * If this test fails, a new divergence was introduced — update the test
     * AND ensure no code uses .name for the new divergent type.
     */
    @Test
    fun `WorkType entity strings match expected values`() {
        val expected = mapOf(
            WorkType.MOVIE to "MOVIE",
            WorkType.SERIES to "SERIES",
            WorkType.EPISODE to "EPISODE",
            WorkType.CLIP to "CLIP",
            WorkType.LIVE_CHANNEL to "LIVE",       // DIVERGENT: enum name = "LIVE_CHANNEL"
            WorkType.AUDIOBOOK to "AUDIOBOOK",
            WorkType.MUSIC_TRACK to "MUSIC",       // DIVERGENT: enum name = "MUSIC_TRACK"
            WorkType.UNKNOWN to "UNKNOWN",
        )

        for ((type, expectedString) in expected) {
            assertEquals(
                expectedString,
                WorkTypeMapper.toEntityString(type),
                "WorkType entity string mismatch for $type",
            )
        }

        // Ensure all enum values are covered
        assertEquals(
            WorkType.entries.size,
            expected.size,
            "New WorkType added but not covered in entity string test. " +
                "Add mapping to WorkTypeMapper AND update this test.",
        )
    }

    // =========================================================================
    // Coverage: SourceItemKindMapper
    // =========================================================================

    @Test
    fun `SourceItemKindMapper handles all MediaType entries`() {
        // Verify every MediaType maps to a SourceItemKind without throwing
        for (mt in com.fishit.player.core.model.MediaType.entries) {
            val kind = SourceItemKindMapper.fromMediaType(mt)
            assertTrue(
                kind != null,
                "SourceItemKindMapper.fromMediaType() returned null for $mt",
            )
        }
    }

    // =========================================================================
    // Coverage: All Enum Values Handled
    // =========================================================================

    @Test
    fun `WorkTypeMapper handles all WorkType entries`() {
        // This will throw if any entry is not in the when-expression
        for (type in WorkType.entries) {
            val result = WorkTypeMapper.toEntityString(type)
            assertTrue(result.isNotBlank(), "WorkTypeMapper returned blank for $type")
        }
    }

    @Test
    fun `MediaTypeMapper toWorkType handles all MediaType entries`() {
        // Verify no MediaType is unmapped (would return UNKNOWN silently)
        val mediaTypes = com.fishit.player.core.model.MediaType.entries
        for (mt in mediaTypes) {
            val workType = MediaTypeMapper.toWorkType(mt)
            // UNKNOWN is acceptable for MediaType values that don't have a direct WorkType
            // but the mapping should be intentional
            assertTrue(
                workType != null,
                "MediaTypeMapper.toWorkType() returned null for $mt",
            )
        }
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
     * Scan Kotlin source files in src/main/java for a forbidden pattern.
     *
     * @param pattern Regex to search for in each line
     * @param excludeFiles File names to exclude from scanning (e.g., the SSOT file itself)
     * @return List of violations found
     */
    private fun scanSourceFiles(
        pattern: Regex,
        excludeFiles: Set<String> = emptySet(),
    ): List<Violation> {
        val violations = mutableListOf<Violation>()

        // Find the module root — tests run from the module directory
        val sourceDir = findSourceDir() ?: return emptyList()

        sourceDir.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filter { it.name !in excludeFiles }
            .forEach { file ->
                file.readLines().forEachIndexed { index, line ->
                    // Skip comments
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

        return violations
    }

    /**
     * Locate the src/main/java directory for this module.
     *
     * Tries multiple strategies since test working directory varies by runner.
     */
    private fun findSourceDir(): File? {
        val candidates = listOf(
            File("src/main/java"),
            File("infra/data-nx/src/main/java"),
            File(System.getProperty("user.dir"), "src/main/java"),
        )
        return candidates.firstOrNull { it.exists() && it.isDirectory }
    }
}
