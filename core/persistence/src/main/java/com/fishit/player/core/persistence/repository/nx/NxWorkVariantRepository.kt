package com.fishit.player.core.persistence.repository.nx

import com.fishit.player.core.persistence.obx.NX_WorkVariant
import kotlinx.coroutines.flow.Flow

/**
 * Repository for NX_WorkVariant entities - playback variants per work.
 *
 * **SSOT Contract:** docs/v2/NX_SSOT_CONTRACT.md
 * **Roadmap:** docs/v2/OBX_PLATIN_REFACTOR_ROADMAP.md
 *
 * ## Invariants (BINDING)
 * - INV-04: variantKey is globally unique
 * - INV-11: Every NX_Work has ≥1 NX_WorkVariant with valid playbackHints
 *
 * ## Key Format
 * variantKey: `<sourceKey>#<qualityTag>:<languageTag>`
 *
 * Examples:
 * - `telegram:tg:123456789:chat:-100123456:msg:789012#source:original`
 * - `xtream:xtream:provider.com:john:vod:12345#1080p:en`
 * - `local:local:device-abc123:file:/storage/movies/matrix.mkv#source:original`
 *
 * ## Architectural Note
 * This repository interface is in `core/persistence/repository/nx/` because
 * NX entities ARE the domain model (SSOT). See NxWorkRepository for full explanation.
 *
 * @see NX_WorkVariant
 * @see NxKeyGenerator
 * @see NxWorkRepository
 */
@Suppress("TooManyFunctions") // Repository interfaces legitimately need comprehensive data access methods
interface NxWorkVariantRepository {

    // ═══════════════════════════════════════════════════════════════════════
    // CRUD Operations
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Find variant by unique variantKey.
     *
     * @param variantKey Variant key (e.g., "telegram:tg:123:chat:-100:msg:456#1080p:en")
     * @return Variant if found, null otherwise
     */
    suspend fun findByVariantKey(variantKey: String): NX_WorkVariant?

    /**
     * Find variant by ObjectBox ID.
     *
     * @param id ObjectBox entity ID
     * @return Variant if found, null otherwise
     */
    suspend fun findById(id: Long): NX_WorkVariant?

    /**
     * Insert or update a variant.
     *
     * If variant with same variantKey exists, updates it.
     * Otherwise creates new variant.
     *
     * **INV-11:** Variants must have valid playbackHints (at least playbackUrl or playbackMethod).
     *
     * @param variant Variant to upsert
     * @return Updated variant with ID populated
     */
    suspend fun upsert(variant: NX_WorkVariant): NX_WorkVariant

    /**
     * Delete variant by variantKey.
     *
     * ⚠️ WARNING: May violate INV-11 if this is the last variant for a work.
     *
     * @param variantKey Variant key to delete
     * @return true if deleted, false if not found
     */
    suspend fun delete(variantKey: String): Boolean

    /**
     * Delete variant by ID.
     *
     * @param id ObjectBox entity ID
     * @return true if deleted, false if not found
     */
    suspend fun deleteById(id: Long): Boolean

    // ═══════════════════════════════════════════════════════════════════════
    // Work Relationship Queries
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Find all variants for a work.
     *
     * @param workKey Work key to find variants for
     * @return List of variants linked to the work
     */
    suspend fun findByWorkKey(workKey: String): List<NX_WorkVariant>

    /**
     * Find all variants for a work by work ID.
     *
     * @param workId ObjectBox work ID
     * @return List of variants linked to the work
     */
    suspend fun findByWorkId(workId: Long): List<NX_WorkVariant>

    /**
     * Link a variant to a work.
     *
     * @param variantKey Variant key
     * @param workKey Work key to link to
     * @return true if linked successfully
     */
    suspend fun linkToWork(
        variantKey: String,
        workKey: String,
    ): Boolean

    /**
     * Unlink a variant from its work.
     *
     * ⚠️ WARNING: This may violate INV-11 if it's the last variant for the work.
     *
     * @param variantKey Variant key to unlink
     * @return true if unlinked, false if not found
     */
    suspend fun unlinkFromWork(variantKey: String): Boolean

    // ═══════════════════════════════════════════════════════════════════════
    // Source Reference Queries
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Find all variants for a source.
     *
     * @param sourceKey Source key
     * @return List of variants referencing the source
     */
    suspend fun findBySourceKey(sourceKey: String): List<NX_WorkVariant>

    /**
     * Find variants by multiple source keys.
     *
     * @param sourceKeys List of source keys
     * @return List of variants matching any of the source keys
     */
    suspend fun findBySourceKeys(sourceKeys: List<String>): List<NX_WorkVariant>

    /**
     * Delete all variants for a source.
     *
     * @param sourceKey Source key
     * @return Number of variants deleted
     */
    suspend fun deleteBySourceKey(sourceKey: String): Int

    // ═══════════════════════════════════════════════════════════════════════
    // Quality & Language Queries
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Find variants by quality tag.
     *
     * @param qualityTag Quality tag (source, 1080p, 720p, 480p, 4k, etc.)
     * @param limit Maximum results (default 100)
     * @return List of variants with given quality
     */
    suspend fun findByQualityTag(
        qualityTag: String,
        limit: Int = 100,
    ): List<NX_WorkVariant>

    /**
     * Find variants by language tag.
     *
     * @param languageTag Language tag (original, en, de, es, etc.)
     * @param limit Maximum results (default 100)
     * @return List of variants with given language
     */
    suspend fun findByLanguageTag(
        languageTag: String,
        limit: Int = 100,
    ): List<NX_WorkVariant>

    /**
     * Find variants by quality AND language.
     *
     * @param qualityTag Quality tag filter
     * @param languageTag Language tag filter
     * @param limit Maximum results (default 100)
     * @return List of variants matching both filters
     */
    suspend fun findByQualityAndLanguage(
        qualityTag: String,
        languageTag: String,
        limit: Int = 100,
    ): List<NX_WorkVariant>

    /**
     * Find best quality variant for a work.
     *
     * Selects highest quality based on resolution/bitrate.
     * Priority: 4k > 1080p > 720p > 480p > source
     *
     * @param workKey Work key
     * @return Best quality variant, or null if work has no variants
     */
    suspend fun findBestQualityForWork(workKey: String): NX_WorkVariant?

    /**
     * Get available quality tags for a work.
     *
     * @param workKey Work key
     * @return List of unique quality tags available for the work
     */
    suspend fun getAvailableQualitiesForWork(workKey: String): List<String>

    /**
     * Get available language tags for a work.
     *
     * @param workKey Work key
     * @return List of unique language tags available for the work
     */
    suspend fun getAvailableLanguagesForWork(workKey: String): List<String>

    // ═══════════════════════════════════════════════════════════════════════
    // Playback Method Queries
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Find variants by playback method.
     *
     * @param playbackMethod Playback method (DIRECT, STREAMING, DOWNLOAD_FIRST)
     * @param limit Maximum results (default 100)
     * @return List of variants with given playback method
     */
    suspend fun findByPlaybackMethod(
        playbackMethod: String,
        limit: Int = 100,
    ): List<NX_WorkVariant>

    /**
     * Find variants with direct playback URL for a work.
     *
     * Filters for variants with playbackMethod=DIRECT and non-null playbackUrl.
     *
     * @param workKey Work key
     * @return List of direct playable variants
     */
    suspend fun findDirectPlayableForWork(workKey: String): List<NX_WorkVariant>

    /**
     * Find variants with streaming playback for a work.
     *
     * Filters for variants with playbackMethod=STREAMING.
     *
     * @param workKey Work key
     * @return List of streamable variants
     */
    suspend fun findStreamableForWork(workKey: String): List<NX_WorkVariant>

    // ═══════════════════════════════════════════════════════════════════════
    // Technical Metadata Queries
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Find variants by video codec.
     *
     * @param videoCodec Video codec (h264, h265, vp9, etc.)
     * @param limit Maximum results (default 100)
     * @return List of variants with given video codec
     */
    suspend fun findByVideoCodec(
        videoCodec: String,
        limit: Int = 100,
    ): List<NX_WorkVariant>

    /**
     * Find variants by container format.
     *
     * @param containerFormat Container format (mp4, mkv, avi, etc.)
     * @param limit Maximum results (default 100)
     * @return List of variants with given container format
     */
    suspend fun findByContainerFormat(
        containerFormat: String,
        limit: Int = 100,
    ): List<NX_WorkVariant>

    /**
     * Find variants with minimum resolution.
     *
     * @param minWidth Minimum width in pixels
     * @param minHeight Minimum height in pixels
     * @param limit Maximum results (default 100)
     * @return List of variants meeting or exceeding resolution requirements
     */
    suspend fun findByMinResolution(
        minWidth: Int,
        minHeight: Int,
        limit: Int = 100,
    ): List<NX_WorkVariant>

    /**
     * Find variants with high bitrate.
     *
     * @param minBitrateBps Minimum bitrate in bits per second
     * @param limit Maximum results (default 100)
     * @return List of variants meeting or exceeding bitrate requirement
     */
    suspend fun findHighBitrate(
        minBitrateBps: Long,
        limit: Int = 100,
    ): List<NX_WorkVariant>

    // ═══════════════════════════════════════════════════════════════════════
    // Reactive Streams (Flow)
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Observe variant by variantKey.
     *
     * Emits null if variant doesn't exist or is deleted.
     *
     * @param variantKey Variant key to observe
     * @return Flow emitting variant on changes
     */
    fun observeByVariantKey(variantKey: String): Flow<NX_WorkVariant?>

    /**
     * Observe all variants for a work.
     *
     * @param workKey Work key to observe variants for
     * @return Flow emitting list on changes
     */
    fun observeByWorkKey(workKey: String): Flow<List<NX_WorkVariant>>

    /**
     * Observe variants for a source.
     *
     * @param sourceKey Source key to observe variants for
     * @return Flow emitting list on changes
     */
    fun observeBySourceKey(sourceKey: String): Flow<List<NX_WorkVariant>>

    /**
     * Observe all variants.
     *
     * ⚠️ WARNING: Can be expensive for large datasets. Use with caution.
     *
     * @return Flow emitting all variants on changes
     */
    fun observeAll(): Flow<List<NX_WorkVariant>>

    // ═══════════════════════════════════════════════════════════════════════
    // Counts & Statistics
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Count total variants.
     *
     * @return Total variant count
     */
    suspend fun count(): Long

    /**
     * Count variants for a work.
     *
     * @param workKey Work key
     * @return Count of variants linked to the work
     */
    suspend fun countByWorkKey(workKey: String): Int

    /**
     * Count variants by quality tag.
     *
     * @param qualityTag Quality tag filter
     * @return Count of variants with given quality
     */
    suspend fun countByQualityTag(qualityTag: String): Long

    /**
     * Count variants by language tag.
     *
     * @param languageTag Language tag filter
     * @return Count of variants with given language
     */
    suspend fun countByLanguageTag(languageTag: String): Long

    /**
     * Get quality tag distribution.
     *
     * @return Map of quality tag to count
     */
    suspend fun getQualityDistribution(): Map<String, Long>

    /**
     * Get language tag distribution.
     *
     * @return Map of language tag to count
     */
    suspend fun getLanguageDistribution(): Map<String, Long>

    /**
     * Get video codec distribution.
     *
     * @return Map of codec name to count
     */
    suspend fun getCodecDistribution(): Map<String, Long>

    // ═══════════════════════════════════════════════════════════════════════
    // Batch Operations
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Upsert multiple variants in a single transaction.
     *
     * **Batch Size Limits (per ObxWriteConfig):**
     * - FireTV/Low-RAM: Max 35 items (FIRETV_BATCH_CAP)
     * - Phone/Tablet: Recommended 100-600 items depending on item complexity
     *
     * Large batches should be split according to device class using
     * `ObxWriteConfig.getBatchSize()` to prevent OOM on resource-constrained devices.
     *
     * @param variants List of variants to upsert
     * @return List of upserted variants with IDs populated
     * @see ObxWriteConfig for device-aware batch size recommendations
     */
    suspend fun upsertBatch(variants: List<NX_WorkVariant>): List<NX_WorkVariant>

    /**
     * Delete multiple variants by variantKeys.
     *
     * @param variantKeys List of variant keys to delete
     * @return Number of variants deleted
     */
    suspend fun deleteBatch(variantKeys: List<String>): Int

    /**
     * Find multiple variants by variantKeys.
     *
     * @param variantKeys List of variant keys
     * @return List of found variants
     */
    suspend fun findByVariantKeys(variantKeys: List<String>): List<NX_WorkVariant>

    /**
     * Delete all variants for a work.
     *
     * ⚠️ WARNING: This will violate INV-11. Only use during work deletion.
     *
     * @param workKey Work key
     * @return Number of variants deleted
     */
    suspend fun deleteByWorkKey(workKey: String): Int

    // ═══════════════════════════════════════════════════════════════════════
    // Validation & Health
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Check if variantKey exists.
     *
     * @param variantKey Variant key to check
     * @return true if exists, false otherwise
     */
    suspend fun exists(variantKey: String): Boolean

    /**
     * Find orphaned variants (not linked to any work).
     *
     * @param limit Maximum results (default 100)
     * @return List of variants without a work link
     */
    suspend fun findOrphanedVariants(limit: Int = 100): List<NX_WorkVariant>

    /**
     * Find variants without playback URL.
     *
     * Useful for identifying variants that may need URL resolution.
     *
     * @param limit Maximum results (default 100)
     * @return List of variants with null playbackUrl
     */
    suspend fun findVariantsWithoutPlaybackUrl(limit: Int = 100): List<NX_WorkVariant>

    /**
     * Find variants with invalid source key reference.
     *
     * Checks if sourceKey references a non-existent NX_WorkSourceRef.
     *
     * @param limit Maximum results (default 100)
     * @return List of variants with invalid sourceKey
     */
    suspend fun findVariantsWithInvalidSourceKey(limit: Int = 100): List<NX_WorkVariant>

    /**
     * Validate a variant.
     *
     * Checks for common issues:
     * - Empty variantKey
     * - Invalid key format
     * - Missing playback hints
     * - Invalid quality/language tags
     *
     * @param variant Variant to validate
     * @return List of validation errors (empty if valid)
     */
    suspend fun validateVariant(variant: NX_WorkVariant): List<String>
}
