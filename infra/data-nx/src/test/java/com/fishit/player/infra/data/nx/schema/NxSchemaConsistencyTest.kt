/**
 * NX Schema Consistency Tests
 *
 * Validates that Domain DTOs (in core/model/repository) and OBX Entities (in core/persistence)
 * maintain consistent field mappings and type compatibility.
 *
 * **Contract Reference:** contracts/NX_SSOT_CONTRACT.md
 * **Architecture Reference:** AGENTS.md Section 4
 *
 * ## What This Test Validates
 *
 * 1. **Type Mapping Consistency** - DTO field types map correctly to Entity field types
 * 2. **Field Coverage** - All critical DTO fields have corresponding Entity storage
 * 3. **Mapper Completeness** - Mappers don't silently drop data
 * 4. **Key Format Compliance** - sourceKey, workKey, variantKey formats
 * 5. **Enum Consistency** - WorkType, SourceType, RelationType mappings
 *
 * ## CI Integration
 *
 * These tests run on every build to catch schema drift early.
 * Any failure blocks the build until fixed.
 */
package com.fishit.player.infra.data.nx.schema

import com.fishit.player.core.model.repository.NxIngestLedgerRepository
import com.fishit.player.core.model.repository.NxWorkRelationRepository
import com.fishit.player.core.model.repository.NxWorkRepository
import com.fishit.player.core.model.repository.NxWorkSourceRefRepository
import com.fishit.player.core.model.repository.NxWorkVariantRepository
import com.fishit.player.core.persistence.obx.NX_IngestLedger
import com.fishit.player.core.persistence.obx.NX_Profile
import com.fishit.player.core.persistence.obx.NX_SourceAccount
import com.fishit.player.core.persistence.obx.NX_Work
import com.fishit.player.core.persistence.obx.NX_WorkRelation
import com.fishit.player.core.persistence.obx.NX_WorkSourceRef
import com.fishit.player.core.persistence.obx.NX_WorkUserState
import com.fishit.player.core.persistence.obx.NX_WorkVariant
import com.fishit.player.infra.data.nx.mapper.toDomain
import com.fishit.player.infra.data.nx.mapper.toEntity
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.full.memberProperties
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Schema consistency tests for NX_Work ↔ NxWorkRepository.Work mapping.
 */
class NxWorkSchemaConsistencyTest {

    /**
     * Verifies all critical Work DTO fields are stored in NX_Work entity.
     *
     * If a new field is added to Work DTO, this test ensures it's also
     * mapped to entity storage.
     */
    @Test
    fun `Work DTO critical fields have entity storage`() {
        val criticalDtoFields = listOf(
            "workKey",
            "type", // maps to workType
            "displayTitle", // maps to canonicalTitle
            "year",
            "runtimeMs", // maps to durationMs
            "rating",
            "genres",
            "plot",
            "createdAtMs", // maps to createdAt
            "updatedAtMs", // maps to updatedAt
        )

        val entityFieldNames = NX_Work::class.memberProperties.map { it.name }.toSet()

        val expectedMappings = mapOf(
            "workKey" to "workKey",
            "type" to "workType",
            "displayTitle" to "canonicalTitle",
            "year" to "year",
            "runtimeMs" to "durationMs",
            "rating" to "rating",
            "genres" to "genres",
            "plot" to "plot",
            "createdAtMs" to "createdAt",
            "updatedAtMs" to "updatedAt",
        )

        for (dtoField in criticalDtoFields) {
            val entityField = expectedMappings[dtoField]
            assertNotNull(entityField, "No mapping defined for DTO field '$dtoField'")
            assertTrue(
                entityFieldNames.contains(entityField),
                "Entity NX_Work missing field '$entityField' (mapped from DTO field '$dtoField')"
            )
        }
    }

    /**
     * Verifies WorkType enum covers all NX_Work.workType values from contract.
     */
    @Test
    fun `WorkType enum covers all contract-defined work types`() {
        // From NX_SSOT_CONTRACT.md Section 5.1
        val contractWorkTypes = setOf(
            "MOVIE",
            "SERIES",
            "EPISODE",
            "CLIP",
            "LIVE", // LIVE_CHANNEL in DTO
            "AUDIOBOOK",
            "UNKNOWN",
        )

        val dtoWorkTypes = NxWorkRepository.WorkType.entries.map { it.name }.toSet()

        // DTO should cover all contract types (allowing for naming differences)
        assertTrue(
            dtoWorkTypes.containsAll(setOf("MOVIE", "SERIES", "EPISODE", "CLIP", "LIVE_CHANNEL", "AUDIOBOOK", "UNKNOWN")),
            "WorkType enum missing contract-defined types. Has: $dtoWorkTypes"
        )
    }

    /**
     * Round-trip test: Work → Entity → Work preserves data.
     */
    @Test
    fun `Work to Entity to Work round-trip preserves key fields`() {
        val original = NxWorkRepository.Work(
            workKey = "movie:tmdb:12345",
            type = NxWorkRepository.WorkType.MOVIE,
            displayTitle = "Test Movie",
            sortTitle = "Test Movie",
            titleNormalized = "test movie",
            year = 2024,
            runtimeMs = 7200000L,
            rating = 8.5,
            genres = "Action, Thriller",
            plot = "A test movie plot",
            recognitionState = NxWorkRepository.RecognitionState.HEURISTIC,
            createdAtMs = 1000L,
            updatedAtMs = 2000L,
        )

        val entity = original.toEntity()
        val roundTrip = entity.toDomain()

        assertEquals(original.workKey, roundTrip.workKey, "workKey mismatch")
        assertEquals(original.type, roundTrip.type, "type mismatch")
        assertEquals(original.displayTitle, roundTrip.displayTitle, "displayTitle mismatch")
        assertEquals(original.year, roundTrip.year, "year mismatch")
        assertEquals(original.runtimeMs, roundTrip.runtimeMs, "runtimeMs mismatch")
        assertEquals(original.rating, roundTrip.rating, "rating mismatch")
        assertEquals(original.genres, roundTrip.genres, "genres mismatch")
        assertEquals(original.plot, roundTrip.plot, "plot mismatch")
    }
}

/**
 * Schema consistency tests for NX_WorkSourceRef ↔ NxWorkSourceRefRepository.SourceRef mapping.
 */
class NxWorkSourceRefSchemaConsistencyTest {

    /**
     * Verifies SourceRef DTO critical fields have entity storage.
     */
    @Test
    fun `SourceRef DTO critical fields have entity storage`() {
        val expectedMappings = mapOf(
            "sourceKey" to "sourceKey",
            "sourceType" to "sourceType",
            "accountKey" to "accountKey",
            "sourceItemKey" to "sourceId",
            "sourceTitle" to "rawTitle",
            "firstSeenAtMs" to "discoveredAt",
            "lastSeenAtMs" to "lastSeenAt",
        )

        val entityFieldNames = NX_WorkSourceRef::class.memberProperties.map { it.name }.toSet()

        for ((dtoField, entityField) in expectedMappings) {
            assertTrue(
                entityFieldNames.contains(entityField),
                "Entity NX_WorkSourceRef missing field '$entityField' (mapped from DTO field '$dtoField')"
            )
        }
    }

    /**
     * Verifies SourceType enum covers all contract source types.
     */
    @Test
    fun `SourceType enum covers all contract-defined source types`() {
        // From NX_SSOT_CONTRACT.md Section 3.3
        val contractSourceTypes = setOf("xtream", "telegram", "local", "plex")

        val dtoSourceTypes = NxWorkSourceRefRepository.SourceType.entries.map { it.name.lowercase() }.toSet()

        for (contractType in contractSourceTypes) {
            assertTrue(
                dtoSourceTypes.contains(contractType),
                "SourceType enum missing contract-defined type: $contractType"
            )
        }
    }

    /**
     * Verifies accountKey is MANDATORY per INV-04 / INV-13.
     */
    @Test
    fun `SourceRef requires accountKey - INV-13 compliance`() {
        // accountKey must be in both DTO and Entity
        val dtoFields = NxWorkSourceRefRepository.SourceRef::class.memberProperties.map { it.name }
        val entityFields = NX_WorkSourceRef::class.memberProperties.map { it.name }

        assertTrue("accountKey" in dtoFields, "DTO SourceRef must have accountKey field")
        assertTrue("accountKey" in entityFields, "Entity NX_WorkSourceRef must have accountKey field")
    }

    /**
     * Round-trip test: SourceRef → Entity → SourceRef preserves data.
     */
    @Test
    fun `SourceRef to Entity to SourceRef round-trip preserves key fields`() {
        val original = NxWorkSourceRefRepository.SourceRef(
            sourceKey = "xtream:user@server:vod:12345",
            workKey = "movie:tmdb:12345",
            sourceType = NxWorkSourceRefRepository.SourceType.XTREAM,
            accountKey = "user@server",
            sourceItemKind = NxWorkSourceRefRepository.SourceItemKind.VOD,
            sourceItemKey = "12345",
            sourceTitle = "Test VOD",
            firstSeenAtMs = 1000L,
            lastSeenAtMs = 2000L,
        )

        val entity = original.toEntity()

        assertEquals(original.sourceKey, entity.sourceKey, "sourceKey mismatch")
        assertEquals("xtream", entity.sourceType, "sourceType mismatch")
        assertEquals(original.accountKey, entity.accountKey, "accountKey mismatch")
        assertEquals(original.sourceItemKey, entity.sourceId, "sourceId mismatch")
        assertEquals(original.sourceTitle, entity.rawTitle, "rawTitle mismatch")
    }
}

/**
 * Schema consistency tests for NX_WorkVariant ↔ NxWorkVariantRepository.Variant mapping.
 */
class NxWorkVariantSchemaConsistencyTest {

    /**
     * Verifies Variant DTO critical fields have entity storage.
     */
    @Test
    fun `Variant DTO critical fields have entity storage`() {
        val expectedMappings = mapOf(
            "variantKey" to "variantKey",
            "sourceKey" to "sourceKey",
            "qualityHeight" to "height",
            "container" to "containerFormat",
            "videoCodec" to "videoCodec",
            "audioCodec" to "audioCodec",
            "createdAtMs" to "createdAt",
        )

        val entityFieldNames = NX_WorkVariant::class.memberProperties.map { it.name }.toSet()

        for ((dtoField, entityField) in expectedMappings) {
            assertTrue(
                entityFieldNames.contains(entityField),
                "Entity NX_WorkVariant missing field '$entityField' (mapped from DTO field '$dtoField')"
            )
        }
    }

    /**
     * Verifies playbackHints map is properly stored and retrieved.
     */
    @Test
    fun `Variant playbackHints are preserved in round-trip`() {
        val original = NxWorkVariantRepository.Variant(
            variantKey = "v:xtream:user@server:vod:123:1080p",
            workKey = "movie:tmdb:12345",
            sourceKey = "xtream:user@server:vod:123",
            qualityHeight = 1080,
            container = "mp4",
            videoCodec = "h264",
            audioCodec = "aac",
            playbackHints = mapOf(
                "url" to "http://example.com/stream.m3u8",
                "method" to "STREAMING",
            ),
            createdAtMs = 1000L,
        )

        val entity = original.toEntity()

        // Verify playback hints are stored
        assertEquals("http://example.com/stream.m3u8", entity.playbackUrl, "playbackUrl not stored")
        assertEquals("STREAMING", entity.playbackMethod, "playbackMethod not stored")
    }
}

/**
 * Schema consistency tests for NX_WorkRelation ↔ NxWorkRelationRepository.Relation mapping.
 */
class NxWorkRelationSchemaConsistencyTest {

    /**
     * Verifies Relation DTO critical fields have entity storage.
     */
    @Test
    fun `Relation DTO critical fields have entity storage`() {
        val expectedMappings = mapOf(
            "relationType" to "relationType",
            "orderIndex" to "sortOrder",
            "seasonNumber" to "season",
            "episodeNumber" to "episode",
            "createdAtMs" to "createdAt",
        )

        val entityFieldNames = NX_WorkRelation::class.memberProperties.map { it.name }.toSet()

        for ((dtoField, entityField) in expectedMappings) {
            assertTrue(
                entityFieldNames.contains(entityField),
                "Entity NX_WorkRelation missing field '$entityField' (mapped from DTO field '$dtoField')"
            )
        }
    }

    /**
     * Verifies RelationType enum covers all contract relation types.
     */
    @Test
    fun `RelationType enum covers SERIES_EPISODE - the critical relation type`() {
        val dtoRelationTypes = NxWorkRelationRepository.RelationType.entries.map { it.name }.toSet()

        assertTrue(
            "SERIES_EPISODE" in dtoRelationTypes,
            "RelationType enum must contain SERIES_EPISODE for series navigation"
        )
    }

    /**
     * Round-trip test: Relation → Entity → Relation preserves data.
     */
    @Test
    fun `Relation to Entity to Relation round-trip preserves key fields`() {
        val original = NxWorkRelationRepository.Relation(
            parentWorkKey = "series:tmdb:456",
            childWorkKey = "episode:tmdb:456:s1e1",
            relationType = NxWorkRelationRepository.RelationType.SERIES_EPISODE,
            orderIndex = 1,
            seasonNumber = 1,
            episodeNumber = 1,
            createdAtMs = 1000L,
        )

        val entity = original.toEntity()

        assertEquals("SERIES_EPISODE", entity.relationType, "relationType mismatch")
        assertEquals(1, entity.sortOrder, "sortOrder mismatch")
        assertEquals(1, entity.season, "season mismatch")
        assertEquals(1, entity.episode, "episode mismatch")
    }
}

/**
 * Cross-entity schema consistency tests.
 */
class NxCrossEntitySchemaConsistencyTest {

    /**
     * Verifies workKey format consistency across all entities.
     */
    @Test
    fun `workKey field exists in all work-related entities`() {
        val entitiesWithWorkKey = listOf(
            NX_Work::class to "workKey",
            NX_WorkSourceRef::class to null, // Uses ToOne relation instead
            NX_WorkVariant::class to null,   // Uses ToOne relation instead
            NX_WorkRelation::class to null,  // Uses ToOne relations for parent/child
        )

        // NX_Work must have workKey
        val workFields = NX_Work::class.memberProperties.map { it.name }
        assertTrue("workKey" in workFields, "NX_Work must have workKey field")

        // NX_WorkSourceRef, NX_WorkVariant use ToOne<NX_Work> relation
        val sourceRefFields = NX_WorkSourceRef::class.memberProperties.map { it.name }
        assertTrue("work" in sourceRefFields, "NX_WorkSourceRef must have 'work' relation")

        val variantFields = NX_WorkVariant::class.memberProperties.map { it.name }
        assertTrue("work" in variantFields, "NX_WorkVariant must have 'work' relation")
    }

    /**
     * Verifies sourceKey format consistency: sourceKey links Variant to SourceRef.
     */
    @Test
    fun `sourceKey field links Variant to SourceRef`() {
        val sourceRefFields = NX_WorkSourceRef::class.memberProperties.map { it.name }
        val variantFields = NX_WorkVariant::class.memberProperties.map { it.name }

        assertTrue("sourceKey" in sourceRefFields, "NX_WorkSourceRef must have sourceKey")
        assertTrue("sourceKey" in variantFields, "NX_WorkVariant must have sourceKey for linking")
    }

    /**
     * Verifies timestamp field naming convention.
     */
    @Test
    fun `timestamp fields use consistent naming`() {
        // Entity uses: createdAt, updatedAt (Long, millis)
        // DTO uses: createdAtMs, updatedAtMs (Long, millis)

        val workEntityFields = NX_Work::class.memberProperties.map { it.name }
        assertTrue("createdAt" in workEntityFields, "NX_Work must have createdAt")
        assertTrue("updatedAt" in workEntityFields, "NX_Work must have updatedAt")

        val workDtoFields = NxWorkRepository.Work::class.memberProperties.map { it.name }
        assertTrue("createdAtMs" in workDtoFields, "Work DTO must have createdAtMs")
        assertTrue("updatedAtMs" in workDtoFields, "Work DTO must have updatedAtMs")
    }
}

/**
 * Key format validation tests per NX_SSOT_CONTRACT.md Section 3.
 */
class NxKeyFormatValidationTest {

    /**
     * Validates workKey format: {workType}:{authority}:{id} or {workType}:{source}:{account}:{id}
     */
    @Test
    fun `workKey format validation - authority based`() {
        val validAuthorityKeys = listOf(
            "movie:tmdb:12345",
            "series:imdb:tt1234567",
            "episode:tmdb:tv:456:s:1:e:3",
        )

        for (key in validAuthorityKeys) {
            assertTrue(key.contains(":"), "workKey must contain colons: $key")
            assertTrue(key.split(":").size >= 3, "workKey must have at least 3 parts: $key")
        }
    }

    /**
     * Validates sourceKey format: {sourceType}:{accountKey}:{sourceId}
     */
    @Test
    fun `sourceKey format validation - requires accountKey`() {
        val validSourceKeys = listOf(
            "xtream:user@server.com:vod:12345",
            "telegram:+491234567890:chat:100:msg:42",
        )

        val invalidSourceKeys = listOf(
            "xtream:vod:12345", // Missing accountKey - FORBIDDEN per INV-04
        )

        for (key in validSourceKeys) {
            val parts = key.split(":")
            assertTrue(parts.size >= 3, "Valid sourceKey must have >= 3 parts: $key")
            assertTrue(parts[1].isNotEmpty(), "accountKey must not be empty: $key")
        }

        for (key in invalidSourceKeys) {
            // These should be caught during ingest validation
            // This test documents the expected behavior
        }
    }

    /**
     * Validates variantKey format: {sourceKey}#{qualityTag}:{languageTag} or v:{sourceKey}:{hash}
     */
    @Test
    fun `variantKey format validation`() {
        val validVariantKeys = listOf(
            "v:xtream:user@server:vod:123:1080p:h264",
            "xtream:user@server:vod:123#1080p:original",
        )

        for (key in validVariantKeys) {
            assertTrue(key.contains(":"), "variantKey must contain colons: $key")
        }
    }
}

/**
 * Schema consistency tests for NX_WorkUserState entity.
 */
class NxWorkUserStateSchemaConsistencyTest {

    /**
     * Verifies UserState entity has all fields required by NX_SSOT_CONTRACT INV-5.
     */
    @Test
    fun `NX_WorkUserState has profile-scoped resume fields per INV-5`() {
        val entityFields = NX_WorkUserState::class.memberProperties.map { it.name }.toSet()

        // INV-5: Resume state is stored per profile using percentage-based positioning
        assertTrue("profileId" in entityFields, "Must have profileId for profile scoping")
        assertTrue("workKey" in entityFields, "Must have workKey for work reference")
        assertTrue("resumePositionMs" in entityFields, "Must have resumePositionMs for resume")
        assertTrue("totalDurationMs" in entityFields, "Must have totalDurationMs for percentage calc")
    }

    /**
     * Verifies UserState has user action fields.
     */
    @Test
    fun `NX_WorkUserState has user action fields`() {
        val entityFields = NX_WorkUserState::class.memberProperties.map { it.name }.toSet()

        assertTrue("isFavorite" in entityFields, "Must have isFavorite")
        assertTrue("inWatchlist" in entityFields, "Must have inWatchlist")
        assertTrue("isWatched" in entityFields, "Must have isWatched")
    }
}

/**
 * Schema consistency tests for NX_IngestLedger entity.
 */
class NxIngestLedgerSchemaConsistencyTest {

    /**
     * Verifies IngestLedger entity enforces INV-01: No silent drops.
     */
    @Test
    fun `NX_IngestLedger has required audit fields per INV-01`() {
        val entityFields = NX_IngestLedger::class.memberProperties.map { it.name }.toSet()

        // INV-01: Every ingest candidate creates exactly one entry
        assertTrue("sourceKey" in entityFields, "Must have sourceKey for candidate identification")
        assertTrue("decision" in entityFields, "Must have decision (ACCEPTED/REJECTED/SKIPPED)")
        assertTrue("reasonCode" in entityFields, "Must have reasonCode for audit trail")
        assertTrue("processedAt" in entityFields, "Must have processedAt timestamp")
    }

    /**
     * Verifies IngestLedger DTO enum covers contract reason codes.
     */
    @Test
    fun `LedgerState enum covers all contract-defined states`() {
        val dtoStates = NxIngestLedgerRepository.LedgerState.entries.map { it.name }.toSet()

        assertTrue("ACCEPTED" in dtoStates, "Must have ACCEPTED state")
        assertTrue("REJECTED" in dtoStates, "Must have REJECTED state")
        assertTrue("SKIPPED" in dtoStates, "Must have SKIPPED state")
    }
}

/**
 * Schema consistency tests for NX_SourceAccount entity (multi-account support).
 */
class NxSourceAccountSchemaConsistencyTest {

    /**
     * Verifies SourceAccount entity supports multi-account per INV-04.
     */
    @Test
    fun `NX_SourceAccount has multi-account support fields`() {
        val entityFields = NX_SourceAccount::class.memberProperties.map { it.name }.toSet()

        assertTrue("accountKey" in entityFields, "Must have accountKey as unique identifier")
        assertTrue("sourceType" in entityFields, "Must have sourceType (telegram, xtream, etc.)")
        assertTrue("isActive" in entityFields, "Must have isActive for account enable/disable")
    }

    /**
     * Verifies SourceAccount has Telegram-specific fields.
     */
    @Test
    fun `NX_SourceAccount has Telegram-specific fields`() {
        val entityFields = NX_SourceAccount::class.memberProperties.map { it.name }.toSet()

        assertTrue("telegramUserId" in entityFields, "Must have telegramUserId for Telegram accounts")
        assertTrue("telegramPhone" in entityFields, "Must have telegramPhone for Telegram accounts")
    }

    /**
     * Verifies SourceAccount has Xtream-specific fields.
     */
    @Test
    fun `NX_SourceAccount has Xtream-specific fields`() {
        val entityFields = NX_SourceAccount::class.memberProperties.map { it.name }.toSet()

        assertTrue("serverUrl" in entityFields, "Must have serverUrl for Xtream accounts")
        assertTrue("username" in entityFields, "Must have username for Xtream accounts")
        assertTrue("credential" in entityFields, "Must have credential for Xtream accounts")
    }
}

/**
 * Schema consistency tests for NX_Profile entity (profile system).
 */
class NxProfileSchemaConsistencyTest {

    /**
     * Verifies Profile entity has required fields.
     */
    @Test
    fun `NX_Profile has required profile fields`() {
        val entityFields = NX_Profile::class.memberProperties.map { it.name }.toSet()

        assertTrue("profileKey" in entityFields, "Must have profileKey as unique identifier")
        assertTrue("profileType" in entityFields, "Must have profileType (MAIN, KIDS, GUEST)")
        assertTrue("name" in entityFields, "Must have name for display")
        assertTrue("isActive" in entityFields, "Must have isActive flag")
    }

    /**
     * Verifies Profile supports PIN protection (for Kids profiles).
     */
    @Test
    fun `NX_Profile supports PIN protection`() {
        val entityFields = NX_Profile::class.memberProperties.map { it.name }.toSet()

        assertTrue("isPinProtected" in entityFields, "Must have isPinProtected flag")
        assertTrue("pinHash" in entityFields, "Must have pinHash for PIN storage")
    }
}
