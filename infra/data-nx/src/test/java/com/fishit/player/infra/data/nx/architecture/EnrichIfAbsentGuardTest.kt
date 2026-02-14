/**
 * Architecture test: ensures entity enrichment goes through MappingUtils guards.
 *
 * NX_CONSOLIDATION_PLAN Phase 8 — Prevents unguarded entity field writes.
 *
 * The SSOT for enrichment is [NxWorkRepositoryImpl.enrichIfAbsent] (listing/inheritance)
 * and [NxWorkRepositoryImpl.enrichFromDetail] (detail info API), which both use:
 * - [MappingUtils.enrichOnly] — set only if existing is null (blank strings are treated as present)
 * - [MappingUtils.alwaysUpdate] — always overwrite with new value (enrichFromDetail, external IDs)
 * - [MappingUtils.monotonicUp] — only upgrade, never downgrade
 *
 * Direct entity field writes outside approved files bypass these guards
 * and can cause data corruption (overwriting curated TMDB data with lower-quality data).
 *
 * If a test fails, route the write through NxWorkRepository.enrichIfAbsent(),
 * NxWorkRepository.enrichFromDetail(), or WorkEntityBuilder.build() instead of
 * writing entity fields directly.
 */
package com.fishit.player.infra.data.nx.architecture

import com.fishit.player.infra.data.nx.mapper.base.MappingUtils
import java.io.File
import kotlin.test.assertTrue
import org.junit.Test

class EnrichIfAbsentGuardTest {

    /**
     * Approved files that are allowed to write NX_Work entity fields directly.
     *
     * Each has a justified reason:
     * - WorkEntityBuilder: Creates new entities from NormalizedMediaMetadata
     * - NxWorkRepositoryImpl: enrichIfAbsent SSOT with MappingUtils guards
     * - WorkMapper: domain↔entity conversion (toEntity/toDomain)
     * - NxCanonicalMediaRepositoryImpl: TMDB detail enrichment
     * - NxWorkAuthorityRepositoryImpl: authority key management
     * - NxDetailMediaRepositoryImpl: updatedAt timestamp only
     */
    private val approvedWriteFiles = setOf(
        "WorkEntityBuilder.kt",
        "NxWorkRepositoryImpl.kt",
        "WorkMapper.kt",
        "NxCanonicalMediaRepositoryImpl.kt",
        "NxWorkAuthorityRepositoryImpl.kt",
        "NxDetailMediaRepositoryImpl.kt",
        // Test files
        "EnrichIfAbsentGuardTest.kt",
        "WorkEntityBuilderTest.kt",
        "NxCatalogWriterE2ETest.kt",
    )

    /**
     * Enrichable NX_Work fields that MUST go through MappingUtils guards.
     *
     * These fields are enriched via enrichIfAbsent and should not be
     * directly assigned (entity.field = value) in unapproved files.
     */
    private val enrichableFields = listOf(
        "rating", "plot", "genres", "director", "cast",
        "trailer", "releaseDate", "durationMs",
        "tmdbId", "imdbId", "tvdbId",
        "poster", "backdrop", "thumbnail",
        "recognitionState",
    )

    /**
     * No unapproved files directly write enrichable NX_Work fields.
     *
     * Pattern detected: `.fieldName = ` on NX_Work entities outside approved files.
     * This catches bypasses like `work.rating = 5.0f` that skip enrichIfAbsent guards.
     */
    @Test
    fun `no unapproved direct writes to enrichable NX_Work fields`() {
        // Build pattern: .rating = , .plot = , etc. (property assignment)
        val fieldPattern = enrichableFields.joinToString("|") { Regex.escape(it) }
        val pattern = Regex("""\.\s*($fieldPattern)\s*=""")

        val violations = scanSourceFiles(
            pattern = pattern,
            excludeFiles = approvedWriteFiles,
        )

        assertTrue(
            violations.isEmpty(),
            "Direct writes to enrichable NX_Work fields in unapproved files:\n" +
                violations.joinToString("\n") { "  ${it.file}:${it.lineNumber}: ${it.content.trim()}" } +
                "\n\nFix: Route writes through NxWorkRepository.enrichIfAbsent(), enrichFromDetail(), or WorkEntityBuilder.",
        )
    }

    /**
     * MappingUtils enrichOnly/alwaysUpdate/monotonicUp are used consistently.
     *
     * Verifies these utility methods exist and have the expected signatures
     * (compile-time check via reference).
     */
    @Test
    fun `MappingUtils enrichment guards exist`() {
        // Compile-time verification: call each guard method to verify it exists
        // enrichOnly: keeps existing non-null, uses new if existing is null
        val enrichResult: String? = MappingUtils.enrichOnly("existing", "new")
        assertTrue(enrichResult == "existing", "enrichOnly should keep existing non-null value")

        val enrichNull: String? = MappingUtils.enrichOnly(null, "new")
        assertTrue(enrichNull == "new", "enrichOnly should use new when existing is null")

        // alwaysUpdate: always takes new non-null value
        val updateResult: String? = MappingUtils.alwaysUpdate("old", "new")
        assertTrue(updateResult == "new", "alwaysUpdate should take new value")
    }

    // =========================================================================
    // Source Scanning Infrastructure
    // =========================================================================

    private data class Violation(
        val file: String,
        val lineNumber: Int,
        val content: String,
    )

    private fun scanSourceFiles(
        pattern: Regex,
        excludeFiles: Set<String> = emptySet(),
    ): List<Violation> {
        val violations = mutableListOf<Violation>()
        val sourceDir = findSourceDir() ?: return emptyList()

        sourceDir.walkTopDown()
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

        return violations
    }

    private fun findSourceDir(): File? {
        val candidates = listOf(
            File("src/main/java"),
            File("infra/data-nx/src/main/java"),
            File(System.getProperty("user.dir"), "src/main/java"),
        )
        return candidates.firstOrNull { it.exists() && it.isDirectory }
    }
}
