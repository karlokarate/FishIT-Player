/**
 * Architecture test: ensures all fields are properly mapped between layers.
 *
 * NX_CONSOLIDATION_PLAN Phase 8 — Catches mapping gaps at compile/test time.
 *
 * If a test fails after adding a new field, either:
 * 1. Add the mapping in the appropriate builder/mapper
 * 2. Add the field to the justified-unmapped set with a reason comment
 */
package com.fishit.player.infra.data.nx.mapper

import com.fishit.player.core.model.NormalizedMediaMetadata
import com.fishit.player.core.model.repository.NxWorkRepository
import com.fishit.player.core.persistence.obx.NX_Work
import kotlin.reflect.full.memberProperties
import kotlin.test.assertTrue
import org.junit.Test

class MappingCompletenessTest {

    // =========================================================================
    // Test 1: NormalizedMediaMetadata → WorkEntityBuilder → Work
    // =========================================================================

    @Test
    fun `all NormalizedMediaMetadata fields are mapped in WorkEntityBuilder`() {
        val normalizedFields = NormalizedMediaMetadata::class.memberProperties
            .map { it.name }
            .toSet()

        // Fields that ARE mapped in WorkEntityBuilder.build()
        val builderMappedFields = setOf(
            "canonicalTitle",    // → Work.displayTitle + sortTitle + titleNormalized
            "mediaType",         // → Work.type via MediaTypeMapper.toWorkType()
            "year",              // → Work.year
            "season",            // → Work.season
            "episode",           // → Work.episode
            "tmdb",              // → Work.tmdbId + recognitionState
            "externalIds",       // → Work.tmdbId (fallback), imdbId, tvdbId
            "poster",            // → Work.poster (ImageRef direct)
            "backdrop",          // → Work.backdrop (ImageRef direct)
            "thumbnail",         // → Work.thumbnail (ImageRef direct)
            "plot",              // → Work.plot
            "genres",            // → Work.genres
            "director",          // → Work.director
            "cast",              // → Work.cast
            "rating",            // → Work.rating
            "durationMs",        // → Work.runtimeMs
            "trailer",           // → Work.trailer
            "releaseDate",       // → Work.releaseDate
            "isAdult",           // → Work.isAdult
            "addedTimestamp",    // → Work.createdAtMs
        )

        // Fields INTENTIONALLY not mapped in WorkEntityBuilder (with justification)
        val justifiedUnmapped = setOf(
            "placeholderThumbnail", // Stored in NX_WorkVariant, not NX_Work
            "epgChannelId",         // Stored in NX_WorkSourceRef.sourceKey for live channels
            "tvArchive",            // Stored in NX_WorkSourceRef playbackHints
            "tvArchiveDuration",    // Stored in NX_WorkSourceRef playbackHints
            "categoryId",          // Stored separately in category association
        )

        val allAccountedFor = builderMappedFields + justifiedUnmapped
        val unmapped = normalizedFields - allAccountedFor

        assertTrue(
            unmapped.isEmpty(),
            "Unmapped NormalizedMediaMetadata fields in WorkEntityBuilder: $unmapped\n" +
                "Add mapping in WorkEntityBuilder.build() or add to justifiedUnmapped with reason.",
        )

        // Sanity check: no phantom mapped fields (fields that don't exist in source)
        val phantomMapped = builderMappedFields - normalizedFields
        assertTrue(
            phantomMapped.isEmpty(),
            "Phantom mapped fields (not in NormalizedMediaMetadata): $phantomMapped\n" +
                "Remove from builderMappedFields set.",
        )
    }

    // =========================================================================
    // Test 2: NX_Work entity ↔ Work domain via WorkMapper
    // =========================================================================

    @Test
    fun `all NX_Work entity fields are covered by WorkMapper toDomain`() {
        val entityFields = NX_Work::class.memberProperties
            .map { it.name }
            .toSet()

        // Fields mapped in NX_Work.toDomain() extension
        val mappedToDomain = setOf(
            "workKey",              // → Work.workKey
            "workType",             // → Work.type via WorkTypeMapper
            "canonicalTitle",       // → Work.displayTitle + sortTitle
            "canonicalTitleLower",  // → Work.titleNormalized
            "year",                 // → Work.year
            "season",               // → Work.season
            "episode",              // → Work.episode
            "durationMs",           // → Work.runtimeMs
            "poster",               // → Work.poster (ImageRef direct)
            "backdrop",             // → Work.backdrop (ImageRef direct)
            "thumbnail",            // → Work.thumbnail (ImageRef direct)
            "rating",               // → Work.rating
            "genres",               // → Work.genres
            "plot",                 // → Work.plot
            "director",             // → Work.director
            "cast",                 // → Work.cast
            "trailer",              // → Work.trailer
            "releaseDate",          // → Work.releaseDate
            "tmdbId",               // → Work.tmdbId
            "imdbId",               // → Work.imdbId
            "tvdbId",               // → Work.tvdbId
            "isAdult",              // → Work.isAdult
            "recognitionState",     // → Work.recognitionState via MappingUtils.safeEnumFromString
            "createdAt",            // → Work.createdAtMs
            "updatedAt",            // → Work.updatedAtMs
        )

        // Entity fields intentionally NOT mapped to domain (with justification)
        val justifiedUnmapped = setOf(
            "id",                   // ObjectBox internal ID, not exposed to domain
            "authorityKey",         // Computed key, not a domain field (used internally)
            "needsReview",          // @Deprecated: Legacy field, replaced by recognitionState
            "sourceRefs",           // ToMany relation — accessed via separate repository
            "variants",             // ToMany relation — accessed via separate repository
            "childRelations",       // ToMany relation — accessed via separate repository
        )

        val allAccountedFor = mappedToDomain + justifiedUnmapped
        val unmapped = entityFields - allAccountedFor

        assertTrue(
            unmapped.isEmpty(),
            "Unmapped NX_Work fields in WorkMapper.toDomain(): $unmapped\n" +
                "Add mapping or add to justifiedUnmapped with reason.",
        )
    }

    @Test
    fun `all Work domain fields are covered by WorkMapper toEntity`() {
        val workFields = NxWorkRepository.Work::class.memberProperties
            .map { it.name }
            .toSet()

        // Fields mapped in Work.toEntity() extension
        val mappedToEntity = setOf(
            "workKey",              // → entity.workKey
            "type",                 // → entity.workType via WorkTypeMapper.toEntityString()
            "displayTitle",         // → entity.canonicalTitle
            "titleNormalized",      // → entity.canonicalTitleLower
            "year",                 // → entity.year
            "season",               // → entity.season
            "episode",              // → entity.episode
            "runtimeMs",            // → entity.durationMs
            "poster",               // → entity.poster (ImageRef direct)
            "backdrop",             // → entity.backdrop (ImageRef direct)
            "thumbnail",            // → entity.thumbnail (ImageRef direct)
            "rating",               // → entity.rating
            "genres",               // → entity.genres
            "plot",                 // → entity.plot
            "director",             // → entity.director
            "cast",                 // → entity.cast
            "trailer",              // → entity.trailer
            "releaseDate",          // → entity.releaseDate
            "tmdbId",               // → entity.tmdbId
            "imdbId",               // → entity.imdbId
            "tvdbId",               // → entity.tvdbId
            "isAdult",              // → entity.isAdult
            "recognitionState",     // → entity.recognitionState + needsReview (legacy)
            "createdAtMs",          // → entity.createdAt
            "updatedAtMs",          // → entity.updatedAt
        )

        // Work fields intentionally NOT written to entity (with justification)
        val justifiedUnmapped = setOf(
            "sortTitle",            // Derived from displayTitle, not stored separately
            "isDeleted",            // ObjectBox soft delete not implemented yet
        )

        val allAccountedFor = mappedToEntity + justifiedUnmapped
        val unmapped = workFields - allAccountedFor

        assertTrue(
            unmapped.isEmpty(),
            "Unmapped Work fields in WorkMapper.toEntity(): $unmapped\n" +
                "Add mapping or add to justifiedUnmapped with reason.",
        )
    }

    // =========================================================================
    // Test 3: enrichIfAbsent field guard coverage
    // =========================================================================

    @Test
    fun `enrichIfAbsent covers all enrichable Work fields`() {
        val workFields = NxWorkRepository.Work::class.memberProperties
            .map { it.name }
            .toSet()

        // All Enrichment fields must map 1:1 to Work fields
        val enrichmentFields = NxWorkRepository.Enrichment::class.memberProperties
            .map { it.name }
            .toSet()

        // Fields handled by enrichIfAbsent (with their guard category)
        val enrichedFields = enrichmentFields + setOf(
            // IMMUTABLE — skipped, never overwritten:
            "workKey",
            "type",
            "displayTitle",
            "titleNormalized",
            "year",
            // AUTO — managed by system:
            "updatedAtMs",
            "createdAtMs",
        )

        // Fields intentionally NOT enriched
        val justifiedNotEnriched = setOf(
            "sortTitle",    // Derived from displayTitle
            "isDeleted",    // Not enrichable — administrative flag
            "isAdult",      // Set once at creation, not enriched
        )

        val allAccountedFor = enrichedFields + justifiedNotEnriched
        val unaccounted = workFields - allAccountedFor

        assertTrue(
            unaccounted.isEmpty(),
            "Work fields not accounted for in enrichIfAbsent: $unaccounted\n" +
                "Add to enrichedFields with guard category or justifiedNotEnriched with reason.",
        )
    }
}
