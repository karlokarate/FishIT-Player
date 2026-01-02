package com.fishit.player.infra.data.home

import com.fishit.player.core.home.domain.HomeContentRepository
import com.fishit.player.core.home.domain.HomeMediaItem
import com.fishit.player.core.model.MediaType
import com.fishit.player.core.model.RawMediaMetadata
import com.fishit.player.core.model.SourceType
import com.fishit.player.core.persistence.ObjectBoxFlow.asFlow
import com.fishit.player.core.persistence.cache.CacheKey
import com.fishit.player.core.persistence.cache.CachedSection
import com.fishit.player.core.persistence.cache.HomeContentCache
import com.fishit.player.core.persistence.obx.ObxCanonicalMedia
import com.fishit.player.core.persistence.obx.ObxCanonicalMedia_
import com.fishit.player.core.persistence.obx.ObxCanonicalResumeMark
import com.fishit.player.core.persistence.obx.ObxCanonicalResumeMark_
import com.fishit.player.core.persistence.obx.ObxMediaSourceRef
import com.fishit.player.core.persistence.obx.ObxMediaSourceRef_
import com.fishit.player.infra.data.telegram.TelegramContentRepository
import com.fishit.player.infra.data.xtream.XtreamCatalogRepository
import com.fishit.player.infra.data.xtream.XtreamLiveRepository
import com.fishit.player.infra.logging.UnifiedLog
import io.objectbox.BoxStore
import io.objectbox.kotlin.boxFor
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

/**
 * Adapter that implements the Home feature's HomeContentRepository interface.
 *
 * **Architecture (Dependency Inversion):**
 * - Feature layer (feature:home) defines HomeContentRepository interface
 * - Data layer (infra:data-home) provides this implementation
 * - Feature depends on interface (not on infra module)
 * - Infra depends on feature (to implement interface)
 *
 * **Responsibility:**
 * - Aggregates multiple data repositories (Telegram, Xtream VOD/Live/Series)
 * - Maps RawMediaMetadata → HomeMediaItem (feature-domain model)
 * - Shields Home feature from direct data layer dependencies
 * - Provides error handling and empty fallbacks for missing sources
 *
 * **Phase 2 Cache Integration:**
 * - L1 (Memory) cache via HomeContentCache
 * - Check cache first, fallback to DB
 * - Update cache after DB query
 * - 5-minute TTL per section
 *
 * **Design:** This is a composite adapter that coordinates multiple underlying repositories and
 * performs mapping to the feature's domain model. If a source is unavailable (e.g., no Telegram
 * auth), it returns empty flows instead of failing, allowing the Home screen to gracefully handle
 * missing sources.
 */
@Singleton
class HomeContentRepositoryAdapter
@Inject
constructor(
        private val boxStore: BoxStore,
        private val homeContentCache: HomeContentCache, // ✅ Phase 2: Inject cache
        private val telegramContentRepository: TelegramContentRepository,
        private val xtreamCatalogRepository: XtreamCatalogRepository,
        private val xtreamLiveRepository: XtreamLiveRepository,
) : HomeContentRepository {

    private val canonicalMediaBox by lazy { boxStore.boxFor<ObxCanonicalMedia>() }
    private val canonicalResumeBox by lazy { boxStore.boxFor<ObxCanonicalResumeMark>() }
    private val sourceRefBox by lazy { boxStore.boxFor<ObxMediaSourceRef>() }

    // ==================== Phase 3: Batch Fetch Helpers ====================

    /**
     * Batch-fetch sources for multiple canonical IDs in a single query.
     * 
     * **Phase 3 Performance:**
     * - Eliminates N+1 queries (1 query instead of N)
     * - Returns Map for O(1) lookup
     * - Called once per repository method, not per item
     * 
     * **Thread Safety:** Safe to call from Flow.map {} (synchronous)
     */
    private fun batchFetchSources(canonicalIds: List<Long>): Map<Long, List<ObxMediaSourceRef>> {
        if (canonicalIds.isEmpty()) return emptyMap()
        
        // Query all source refs that belong to any of the canonical IDs
        // Use link() to query via ToOne relation, then apply in_() on the linked entity's ID
        val query = sourceRefBox.query()
        query.link(ObxMediaSourceRef_.canonicalMedia).`in`(ObxCanonicalMedia_.id, canonicalIds.toLongArray())
        val allSources = query.build().find()
        
        // Group by canonical media ID for O(1) lookup
        // canonicalMedia.targetId accesses the ToOne relation's target ID (safe after query)
        return allSources.groupBy { it.canonicalMedia.targetId }
    }

    /**
     * Select the best source type using strict priority order.
     *
     * **Phase 3:** Accepts List instead of ToMany (for batch-fetched sources).
     * 
     * Priority: XTREAM > TELEGRAM > IO > UNKNOWN Never returns SourceType.OTHER (ambiguous
     * routing).
     */
    private fun selectBestSourceType(sources: List<ObxMediaSourceRef>): SourceType {
        val sourceTypes = sources.map { it.sourceType.uppercase() }.toSet()
        return when {
            "XTREAM" in sourceTypes -> SourceType.XTREAM
            "TELEGRAM" in sourceTypes -> SourceType.TELEGRAM
            "IO" in sourceTypes -> SourceType.IO
            else -> SourceType.UNKNOWN
        }
    }

    /**
     * Extract all distinct source types from a canonical media's sources.
     *
     * **Phase 3:** Accepts List instead of ToMany (for batch-fetched sources).
     * 
     * Used for multi-source gradient border display on Home tiles. Returns list sorted by priority
     * (XTREAM > TELEGRAM > IO) for consistent gradient direction.
     */
    private fun extractAllSourceTypes(sources: List<ObxMediaSourceRef>): List<SourceType> {
        val sourceTypes =
                sources
                        .mapNotNull { ref ->
                            when (ref.sourceType.uppercase()) {
                                "XTREAM" -> SourceType.XTREAM
                                "TELEGRAM" -> SourceType.TELEGRAM
                                "IO" -> SourceType.IO
                                else -> null
                            }
                        }
                        .distinct()
        // Sort by priority for consistent gradient: XTREAM (left/red) -> TELEGRAM (right/blue)
        return sourceTypes.sortedByDescending { source ->
            when (source) {
                SourceType.XTREAM -> 3
                SourceType.TELEGRAM -> 2
                SourceType.IO -> 1
                else -> 0
            }
        }
    }

    /**
     * Observe Continue Watching (cross-pipeline).
     * - Queries ObxCanonicalResumeMark for items with positionPercent > 0 AND isCompleted = false
     * - Batch-fetches all matching CanonicalMedia entities in ONE query (IN clause)
     * - Joins in-memory to avoid per-item DB lookups
     * - Sorted by updatedAt DESC (most recently watched first)
     * - Limited to [CONTINUE_WATCHING_LIMIT] items (FireTV-safe)
     *
     * **Phase 2 Cache:**
     * - Check L1 cache first (fast return on cache hit)
     * - DB Flow runs unconditionally to maintain reactivity
     * - Update cache on every DB emission
     *
     * **Profile Note:** Currently uses profileId = 0 (default profile). Multi-profile support will
     * require passing the active profileId from the UI layer.
     */
    override fun observeContinueWatching(): Flow<List<HomeMediaItem>> {
        // Query resume marks: position > 0 AND not completed, sorted by last watched
        val query =
                canonicalResumeBox
                        .query()
                        .greater(ObxCanonicalResumeMark_.positionPercent, 0.0)
                        .equal(ObxCanonicalResumeMark_.isCompleted, false)
                        // Note: No .eager() needed - canonicalKey is String field, not ToOne relation
                        // Canonical media is batch-fetched below via IN clause
                        .orderDesc(ObxCanonicalResumeMark_.updatedAt)
                        .build()

        return query.asFlow()
                .map { resumeMarks ->
                    // ✅ Phase 2: Try cache first (synchronous check)
                    val cached = homeContentCache.get(CacheKey.ContinueWatching)
                    if (cached != null) {
                        @Suppress("UNCHECKED_CAST")
                        return@map cached.items as List<HomeMediaItem> // Fast path: return cached
                    }

                    // Cache miss: compute from DB
                    val topResumeMarks = resumeMarks.take(CONTINUE_WATCHING_LIMIT)

                    if (topResumeMarks.isEmpty()) {
                        return@map emptyList()
                    }

                    // Extract all canonical keys for batch fetch
                    val canonicalKeys = topResumeMarks.map { it.canonicalKey }.toTypedArray()

                    // BATCH FETCH: Single query with IN clause instead of N+1 findFirst() calls
                    val canonicalMediaMap =
                            canonicalMediaBox
                                    .query(ObxCanonicalMedia_.canonicalKey.oneOf(canonicalKeys))
                                    .build()
                                    .find()
                                    .associateBy { it.canonicalKey }

                    // In-memory join: match resume marks with canonical media
                    val items = topResumeMarks.mapNotNull { resume ->
                        val canonical =
                                canonicalMediaMap[resume.canonicalKey] ?: return@mapNotNull null
                        mapResumeToHomeMediaItem(resume, canonical)
                    }

                    // ✅ Phase 2: Update cache (background, non-blocking)
                    homeContentCache.put(
                            CacheKey.ContinueWatching,
                            CachedSection(items, ttl = 300.seconds) // 5 minutes
                    )

                    items
                }
                .catch { throwable ->
                    UnifiedLog.e(TAG, throwable) { "Failed to observe continue watching" }
                    emit(emptyList())
                }
    }

    /**
     * Observe recently added items across all sources.
     *
     * **PLATINUM Episode Filtering:**
     * - Excludes SERIES_EPISODE mediaType - episodes NEVER appear as standalone tiles
     * - Episodes belong inside SeriesDetail, accessed via Series tile navigation
     * - Shows only top-level content: MOVIE, SERIES, LIVE, CLIP, AUDIOBOOK, etc.
     *
     * **Implementation:**
     * - Queries ObxCanonicalMedia sorted by createdAt DESC
     * - Filters OUT mediaType = SERIES_EPISODE
     * - Limited to [RECENTLY_ADDED_LIMIT] items (FireTV-safe)
     * - Maps to HomeMediaItem with isNew = true for items added in last 7 days
     * - Determines navigationSource deterministically using source priority: XTREAM > TELEGRAM > IO
     * (never SourceType.OTHER)
     *
     * **Phase 2 Cache:**
     * - Check L1 cache first (fast return on cache hit)
     * - DB Flow runs unconditionally to maintain reactivity
     * - Update cache on every DB emission
     */
    override fun observeRecentlyAdded(): Flow<List<HomeMediaItem>> {
        // PLATINUM: Exclude SERIES_EPISODE - episodes belong inside SeriesDetail
        val query =
                canonicalMediaBox
                        .query(ObxCanonicalMedia_.mediaType.notEqual("SERIES_EPISODE"))
                        // ✅ Phase 3: Remove .eager() - use batch fetch instead
                        .orderDesc(ObxCanonicalMedia_.createdAt)
                        .build()

        return query.asFlow()
                .map { canonicalMediaList ->
                    // ✅ Phase 2: Try cache first (synchronous check)
                    val cached = homeContentCache.get(CacheKey.RecentlyAdded)
                    if (cached != null) {
                        @Suppress("UNCHECKED_CAST")
                        return@map cached.items as List<HomeMediaItem> // Fast path: return cached
                    }

                    // Cache miss: compute from DB
                    val now = System.currentTimeMillis()
                    val sevenDaysAgo = now - SEVEN_DAYS_MS

                    if (canonicalMediaList.isEmpty()) {
                        return@map emptyList()
                    }

                    // Apply Recently Added limit (200 max) to prevent overwhelming scroll
                    val limitedList = canonicalMediaList.take(RECENTLY_ADDED_LIMIT)

                    // ✅ Phase 3: Batch-fetch sources (1 query instead of N)
                    val canonicalIds = limitedList.map { it.id }
                    val sourcesMap = batchFetchSources(canonicalIds)

                    // Build map of canonical key -> best source type
                    val items = limitedList.map { canonical ->
                        // O(1) lookup from batch-fetched map
                        val sources = sourcesMap[canonical.id] ?: emptyList()
                        val bestSource =
                                if (sources.isEmpty()) {
                                    SourceType.UNKNOWN
                                } else {
                                    selectBestSourceType(sources)
                                }
                        val allSourceTypes =
                                if (sources.isEmpty()) {
                                    listOf(SourceType.UNKNOWN)
                                } else {
                                    extractAllSourceTypes(sources)
                                }

                        canonical.toHomeMediaItem(
                                isNew = canonical.createdAt >= sevenDaysAgo,
                                navigationSource = bestSource,
                                sourceTypes = allSourceTypes
                        )
                    }

                    // ✅ Phase 2: Update cache (background, non-blocking)
                    homeContentCache.put(
                            CacheKey.RecentlyAdded,
                            CachedSection(items, ttl = 300.seconds) // 5 minutes
                    )

                    items
                }
                .catch { throwable ->
                    UnifiedLog.e(TAG, throwable) { "Failed to observe recently added" }
                    emit(emptyList())
                }
    }

    override fun observeTelegramMedia(): Flow<List<HomeMediaItem>> {
        return telegramContentRepository
                .observeAll()
                .map { items -> items.map { it.toHomeMediaItem() } }
                .catch { throwable ->
                    UnifiedLog.e(TAG, throwable) { "Failed to observe Telegram media content" }
                    emit(emptyList())
                }
    }

    override fun observeXtreamLive(): Flow<List<HomeMediaItem>> {
        return xtreamLiveRepository
                .observeChannels()
                .map { items -> items.map { it.toHomeMediaItem() } }
                .catch { throwable ->
                    UnifiedLog.e(TAG, throwable) { "Failed to observe Xtream live TV channels" }
                    emit(emptyList())
                }
    }

    override fun observeXtreamVod(): Flow<List<HomeMediaItem>> {
        return xtreamCatalogRepository
                .observeVod()
                .map { items -> items.map { it.toHomeMediaItem() } }
                .catch { throwable ->
                    UnifiedLog.e(TAG, throwable) { "Failed to observe Xtream VOD content" }
                    emit(emptyList())
                }
    }

    override fun observeXtreamSeries(): Flow<List<HomeMediaItem>> {
        return xtreamCatalogRepository
                .observeSeries()
                .map { items -> items.map { it.toHomeMediaItem() } }
                .catch { throwable ->
                    UnifiedLog.e(TAG, throwable) { "Failed to observe Xtream series content" }
                    emit(emptyList())
                }
    }

    // ==================== Cross-Pipeline Content Methods ====================

    /**
     * Observe all movies from all sources (cross-pipeline).
     *
     * Queries ObxCanonicalMedia where mediaType = "MOVIE". Items from Xtream and Telegram are
     * unified by canonical key.
     *
     * **Phase 2 Cache:**
     * - Check L1 cache first (fast return on cache hit)
     * - DB Flow runs unconditionally to maintain reactivity
     * - Update cache on every DB emission
     */
    override fun observeMovies(): Flow<List<HomeMediaItem>> {
        val query =
                canonicalMediaBox
                        .query(ObxCanonicalMedia_.mediaType.equal("MOVIE"))
                        // ✅ Phase 3: Remove .eager() - use batch fetch instead
                        .orderDesc(ObxCanonicalMedia_.createdAt)
                        .build()

        return query.asFlow()
                .map { canonicalMediaList ->
                    // ✅ Phase 2: Try cache first (synchronous check)
                    val cached = homeContentCache.get(CacheKey.Movies)
                    if (cached != null) {
                        @Suppress("UNCHECKED_CAST")
                        return@map cached.items as List<HomeMediaItem> // Fast path: return cached
                    }

                    // Cache miss: compute from DB
                    val now = System.currentTimeMillis()
                    val sevenDaysAgo = now - SEVEN_DAYS_MS

                    if (canonicalMediaList.isEmpty()) {
                        return@map emptyList()
                    }

                    // ✅ Phase 3: Batch-fetch sources (1 query instead of N)
                    val canonicalIds = canonicalMediaList.map { it.id }
                    val sourcesMap = batchFetchSources(canonicalIds)

                    // No limit - LazyRow handles virtualization
                    val items = canonicalMediaList.map { canonical ->
                        // O(1) lookup from batch-fetched map
                        val sources = sourcesMap[canonical.id] ?: emptyList()
                        val bestSource =
                                if (sources.isEmpty()) {
                                    SourceType.UNKNOWN
                                } else {
                                    selectBestSourceType(sources)
                                }
                        val allSourceTypes =
                                if (sources.isEmpty()) {
                                    listOf(SourceType.UNKNOWN)
                                } else {
                                    extractAllSourceTypes(sources)
                                }

                        canonical.toHomeMediaItem(
                                isNew = canonical.createdAt >= sevenDaysAgo,
                                navigationSource = bestSource,
                                sourceTypes = allSourceTypes
                        )
                    }

                    // ✅ Phase 2: Update cache (background, non-blocking)
                    homeContentCache.put(
                            CacheKey.Movies,
                            CachedSection(items, ttl = 300.seconds) // 5 minutes
                    )

                    items
                }
                .catch { throwable ->
                    UnifiedLog.e(TAG, throwable) { "Failed to observe movies" }
                    emit(emptyList())
                }
    }

    /**
     * Observe all series from all sources (cross-pipeline).
     *
     * **PLATINUM Episode Filtering:**
     * - Queries ONLY mediaType = "SERIES" - series entries created during sync
     * - Does NOT query SERIES_EPISODE - episodes belong inside SeriesDetail
     * - Each series tile navigates to SeriesDetail where seasons/episodes are loaded
     *
     * **Architecture:**
     * - Series tiles → SeriesDetail → SeasonSelector → EpisodeList
     * - Episodes are NEVER shown as standalone tiles
     * - Episode data lives in XtreamSeriesIndexRepository (lazy loaded)
     *
     * **Phase 2 Cache:**
     * - Check L1 cache first (fast return on cache hit)
     * - DB Flow runs unconditionally to maintain reactivity
     * - Update cache on every DB emission
     */
    override fun observeSeries(): Flow<List<HomeMediaItem>> {
        // PLATINUM: Query ONLY SERIES - episodes are accessed via SeriesDetail
        val query =
                canonicalMediaBox
                        .query(ObxCanonicalMedia_.mediaType.equal("SERIES"))
                        // ✅ Phase 3: Remove .eager() - use batch fetch instead
                        .orderDesc(ObxCanonicalMedia_.createdAt)
                        .build()

        return query.asFlow()
                .map { canonicalMediaList ->
                    // ✅ Phase 2: Try cache first (synchronous check)
                    val cached = homeContentCache.get(CacheKey.Series)
                    if (cached != null) {
                        @Suppress("UNCHECKED_CAST")
                        return@map cached.items as List<HomeMediaItem> // Fast path: return cached
                    }

                    // Cache miss: compute from DB
                    if (canonicalMediaList.isEmpty()) {
                        return@map emptyList()
                    }

                    val now = System.currentTimeMillis()
                    val sevenDaysAgo = now - SEVEN_DAYS_MS

                    // ✅ Phase 3: Batch-fetch sources (1 query instead of N)
                    val canonicalIds = canonicalMediaList.map { it.id }
                    val sourcesMap = batchFetchSources(canonicalIds)

                    // No deduplication needed - SERIES entries are already unique per series title
                    // (episodes have their own canonical keys like "episode:title:S01E01")
                    val items = canonicalMediaList.map { canonical ->
                        // O(1) lookup from batch-fetched map
                        val sources = sourcesMap[canonical.id] ?: emptyList()
                        val bestSource =
                                if (sources.isEmpty()) {
                                    SourceType.UNKNOWN
                                } else {
                                    selectBestSourceType(sources)
                                }
                        val allSourceTypes =
                                if (sources.isEmpty()) {
                                    listOf(SourceType.UNKNOWN)
                                } else {
                                    extractAllSourceTypes(sources)
                                }

                        canonical.toHomeMediaItem(
                                isNew = canonical.createdAt >= sevenDaysAgo,
                                navigationSource = bestSource,
                                sourceTypes = allSourceTypes
                        )
                    }

                    // ✅ Phase 2: Update cache (background, non-blocking)
                    homeContentCache.put(
                            CacheKey.Series,
                            CachedSection(items, ttl = 300.seconds) // 5 minutes
                    )

                    items
                }
                .catch { throwable ->
                    UnifiedLog.e(TAG, throwable) { "Failed to observe series" }
                    emit(emptyList())
                }
    }

    /**
     * Observe Telegram clips (short videos without TMDB match).
     *
     * Clips are:
     * - From Telegram source only
     * - MediaType is CLIP (short-form video content)
     * - No TMDB ID (not a recognized movie/series)
     *
     * NOTE: Items without TMDB ID but with MOVIE/SERIES mediaType should NOT appear here - they
     * belong in Movies/Series rows even without enrichment.
     *
     * **Phase 2 Cache:**
     * - Check L1 cache first (fast return on cache hit)
     * - DB Flow runs unconditionally to maintain reactivity
     * - Update cache on every DB emission
     */
    override fun observeClips(): Flow<List<HomeMediaItem>> {
        // Query canonical media: mediaType = CLIP (short-form content only)
        val query =
                canonicalMediaBox
                        .query(ObxCanonicalMedia_.mediaType.equal("CLIP"))
                        // ✅ Phase 3: Remove .eager() - use batch fetch instead
                        .orderDesc(ObxCanonicalMedia_.createdAt)
                        .build()

        return query.asFlow()
                .map { canonicalMediaList ->
                    // ✅ Phase 2: Try cache first (synchronous check)
                    val cached = homeContentCache.get(CacheKey.Clips)
                    if (cached != null) {
                        @Suppress("UNCHECKED_CAST")
                        return@map cached.items as List<HomeMediaItem> // Fast path: return cached
                    }

                    // Cache miss: compute from DB
                    if (canonicalMediaList.isEmpty()) {
                        return@map emptyList()
                    }

                    val now = System.currentTimeMillis()
                    val sevenDaysAgo = now - SEVEN_DAYS_MS

                    // ✅ Phase 3: Batch-fetch sources (1 query instead of N)
                    val canonicalIds = canonicalMediaList.map { it.id }
                    val sourcesMap = batchFetchSources(canonicalIds)

                    // No limit - LazyRow handles virtualization
                    val items = canonicalMediaList.map { canonical ->
                        // O(1) lookup from batch-fetched map
                        val sources = sourcesMap[canonical.id] ?: emptyList()
                        val bestSource =
                                if (sources.isEmpty()) {
                                    SourceType.TELEGRAM
                                } else {
                                    selectBestSourceType(sources)
                                }
                        canonical.toHomeMediaItem(
                                isNew = canonical.createdAt >= sevenDaysAgo,
                                navigationSource = bestSource,
                                sourceTypes =
                                        if (sources.isEmpty()) {
                                            listOf(SourceType.TELEGRAM)
                                        } else {
                                            extractAllSourceTypes(sources)
                                        }
                        )
                    }

                    // ✅ Phase 2: Update cache (background, non-blocking)
                    homeContentCache.put(
                            CacheKey.Clips,
                            CachedSection(items, ttl = 300.seconds) // 5 minutes
                    )

                    items
                }
                .catch { throwable ->
                    UnifiedLog.e(TAG, throwable) { "Failed to observe clips" }
                    emit(emptyList())
                }
    }

    companion object {
        private const val TAG = "HomeContentRepositoryAdapter"

        /**
         * Maximum items for Continue Watching row. Keep this limited since it's a "curated" row,
         * not a full catalog.
         */
        private const val CONTINUE_WATCHING_LIMIT = 50

        /**
         * Maximum items for Recently Added row. Limited to prevent overwhelming scroll on large
         * catalogs.
         */
        private const val RECENTLY_ADDED_LIMIT = 200

        /** Seven days in milliseconds for "new" badge */
        private const val SEVEN_DAYS_MS = 7 * 24 * 60 * 60 * 1000L

        // NOTE: Movies, Series, Clips, Recently Added have NO limits.
        // The UI uses LazyRow which handles virtualization.
        // ObjectBox query returns all matching items; Compose only renders visible tiles.
    }

    /**
     * Maps an ObxCanonicalResumeMark to HomeMediaItem using pre-fetched canonical media.
     *
     * @param resume The resume mark from persistence
     * @param canonical The pre-fetched canonical media entity
     * @return HomeMediaItem with resume data
     */
    private fun mapResumeToHomeMediaItem(
            resume: ObxCanonicalResumeMark,
            canonical: ObxCanonicalMedia
    ): HomeMediaItem {
        val sourceType = resume.lastSourceType?.toSourceType() ?: SourceType.UNKNOWN
        val allSourceTypes =
                if (canonical.sources.isEmpty()) {
                    listOf(sourceType)
                } else {
                    extractAllSourceTypes(canonical.sources)
                }
        return HomeMediaItem(
                id = canonical.canonicalKey,
                title = canonical.canonicalTitle,
                poster = canonical.poster,
                placeholderThumbnail = canonical.thumbnail,
                backdrop = canonical.backdrop,
                mediaType = canonical.kind.toMediaType(),
                sourceType = sourceType,
                sourceTypes = allSourceTypes,
                resumePosition = resume.positionMs,
                duration = resume.durationMs,
                isNew = false, // Continue watching items are not "new"
                year = canonical.year,
                rating = canonical.rating?.toFloat(),
                genres = canonical.genres,
                navigationId = canonical.canonicalKey,
                navigationSource = sourceType
        )
    }
}

/**
 * Maps RawMediaMetadata to HomeMediaItem (feature-domain model).
 *
 * This mapping extracts only the fields needed for Home screen display, keeping the feature layer
 * decoupled from the full RawMediaMetadata structure.
 */
private fun RawMediaMetadata.toHomeMediaItem(): HomeMediaItem {
    val bestPoster = poster ?: thumbnail
    val bestBackdrop = backdrop ?: thumbnail
    return HomeMediaItem(
            id = sourceId,
            title = originalTitle.ifBlank { sourceLabel },
            poster = bestPoster,
            placeholderThumbnail = placeholderThumbnail,
            backdrop = bestBackdrop,
            mediaType = mediaType,
            sourceType = sourceType,
            duration = durationMs ?: 0L,
            year = year,
            genres = genres,
            navigationId = sourceId,
            navigationSource = sourceType
    )
}

/**
 * Maps ObxCanonicalMedia to HomeMediaItem.
 *
 * Used for "Recently Added" items where we don't have resume data.
 *
 * @param isNew Whether to mark this item as newly added
 * @param navigationSource Deterministic source for navigation (never OTHER)
 * @param sourceTypes All source types for multi-source gradient border
 */
private fun ObxCanonicalMedia.toHomeMediaItem(
        isNew: Boolean = false,
        navigationSource: SourceType = SourceType.UNKNOWN,
        sourceTypes: List<SourceType> = listOf(navigationSource)
): HomeMediaItem {
    // Prefer the full mediaType field over the legacy kind field
    val effectiveMediaType = mediaType.toMediaTypeEnum()

    return HomeMediaItem(
            id = canonicalKey,
            title = canonicalTitle,
            poster = poster,
            placeholderThumbnail = thumbnail,
            backdrop = backdrop,
            mediaType = effectiveMediaType,
            sourceType = navigationSource,
            sourceTypes = sourceTypes,
            resumePosition = 0L,
            duration = durationMs ?: 0L,
            isNew = isNew,
            year = year,
            rating = rating?.toFloat(),
            genres = genres,
            navigationId = canonicalKey,
            navigationSource = navigationSource
    )
}

/**
 * Converts ObxCanonicalMedia.mediaType string to MediaType enum. Falls back to kind-based
 * conversion for legacy data.
 */
private fun String.toMediaTypeEnum(): MediaType =
        when (this.uppercase()) {
            "MOVIE" -> MediaType.MOVIE
            "SERIES" -> MediaType.SERIES
            "SERIES_EPISODE" -> MediaType.SERIES_EPISODE
            "LIVE" -> MediaType.LIVE
            "CLIP" -> MediaType.CLIP
            "AUDIOBOOK" -> MediaType.AUDIOBOOK
            "MUSIC" -> MediaType.MUSIC
            "PODCAST" -> MediaType.PODCAST
            // Legacy kind-based fallback
            "EPISODE" -> MediaType.SERIES_EPISODE
            else -> MediaType.UNKNOWN
        }

/** Converts ObxCanonicalMedia.kind string to MediaType (legacy support). */
private fun String.toMediaType(): MediaType =
        when (this.lowercase()) {
            "movie" -> MediaType.MOVIE
            "episode" -> MediaType.SERIES_EPISODE
            "series" -> MediaType.SERIES
            "live" -> MediaType.LIVE
            else -> MediaType.UNKNOWN
        }

/**
 * Converts source type string (from ObxCanonicalResumeMark.lastSourceType) to SourceType. Never
 * returns SourceType.OTHER to ensure deterministic navigation routing.
 */
private fun String.toSourceType(): SourceType =
        when (this.uppercase()) {
            "TELEGRAM" -> SourceType.TELEGRAM
            "XTREAM" -> SourceType.XTREAM
            "IO", "LOCAL" -> SourceType.IO
            "AUDIOBOOK" -> SourceType.AUDIOBOOK
            else -> SourceType.UNKNOWN
        }
