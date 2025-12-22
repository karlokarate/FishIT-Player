package com.fishit.player.infra.data.home

import com.fishit.player.core.model.MediaType
import com.fishit.player.core.model.RawMediaMetadata
import com.fishit.player.core.model.SourceType
import com.fishit.player.core.persistence.ObjectBoxFlow.asFlow
import com.fishit.player.core.persistence.obx.ObxCanonicalMedia
import com.fishit.player.core.persistence.obx.ObxCanonicalMedia_
import com.fishit.player.core.persistence.obx.ObxCanonicalResumeMark
import com.fishit.player.core.persistence.obx.ObxCanonicalResumeMark_
import com.fishit.player.core.persistence.obx.ObxMediaSourceRef
import com.fishit.player.feature.home.domain.HomeContentRepository
import com.fishit.player.feature.home.domain.HomeMediaItem
import com.fishit.player.infra.data.telegram.TelegramContentRepository
import com.fishit.player.infra.data.xtream.XtreamCatalogRepository
import com.fishit.player.infra.data.xtream.XtreamLiveRepository
import com.fishit.player.infra.logging.UnifiedLog
import io.objectbox.BoxStore
import io.objectbox.kotlin.boxFor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

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
 * **Design:**
 * This is a composite adapter that coordinates multiple underlying repositories
 * and performs mapping to the feature's domain model. If a source is unavailable
 * (e.g., no Telegram auth), it returns empty flows instead of failing, allowing
 * the Home screen to gracefully handle missing sources.
 */
@Singleton
class HomeContentRepositoryAdapter @Inject constructor(
    private val boxStore: BoxStore,
    private val telegramContentRepository: TelegramContentRepository,
    private val xtreamCatalogRepository: XtreamCatalogRepository,
    private val xtreamLiveRepository: XtreamLiveRepository,
) : HomeContentRepository {

    private val canonicalMediaBox by lazy { boxStore.boxFor<ObxCanonicalMedia>() }
    private val canonicalResumeBox by lazy { boxStore.boxFor<ObxCanonicalResumeMark>() }

    /**
     * Observe items the user has started but not finished watching.
     *
     * **Implementation (N+1 optimized):**
     * - Queries ObxCanonicalResumeMark for items with positionPercent > 0 AND isCompleted = false
     * - Batch-fetches all matching CanonicalMedia entities in ONE query (IN clause)
     * - Joins in-memory to avoid per-item DB lookups
     * - Sorted by updatedAt DESC (most recently watched first)
     * - Limited to [CONTINUE_WATCHING_LIMIT] items (FireTV-safe)
     *
     * **Profile Note:**
     * Currently uses profileId = 0 (default profile). Multi-profile support will require
     * passing the active profileId from the UI layer.
     */
    override fun observeContinueWatching(): Flow<List<HomeMediaItem>> {
        // Query resume marks: position > 0 AND not completed, sorted by last watched
        val query = canonicalResumeBox.query()
            .greater(ObxCanonicalResumeMark_.positionPercent, 0.0)
            .equal(ObxCanonicalResumeMark_.isCompleted, false)
            .orderDesc(ObxCanonicalResumeMark_.updatedAt)
            .build()

        return query.asFlow()
            .map { resumeMarks ->
                // Take top N resume marks first (FireTV-safe limit)
                val topResumeMarks = resumeMarks.take(CONTINUE_WATCHING_LIMIT)
                
                if (topResumeMarks.isEmpty()) {
                    return@map emptyList()
                }
                
                // Extract all canonical keys for batch fetch
                val canonicalKeys = topResumeMarks.map { it.canonicalKey }.toTypedArray()
                
                // BATCH FETCH: Single query with IN clause instead of N+1 findFirst() calls
                val canonicalMediaMap = canonicalMediaBox
                    .query(ObxCanonicalMedia_.canonicalKey.oneOf(canonicalKeys))
                    .build()
                    .find()
                    .associateBy { it.canonicalKey }
                
                // In-memory join: match resume marks with canonical media
                topResumeMarks.mapNotNull { resume ->
                    val canonical = canonicalMediaMap[resume.canonicalKey] ?: return@mapNotNull null
                    mapResumeToHomeMediaItem(resume, canonical)
                }
            }
            .catch { throwable ->
                UnifiedLog.e(TAG, throwable) { "Failed to observe continue watching" }
                emit(emptyList())
            }
    }

    /**
     * Observe recently added items across all sources.
     *
     * **Implementation:**
     * - Queries ObxCanonicalMedia sorted by createdAt DESC
     * - Limited to [RECENTLY_ADDED_LIMIT] items (FireTV-safe)
     * - Maps to HomeMediaItem with isNew = true for items added in last 7 days
     * - Determines navigationSource deterministically using source priority:
     *   XTREAM > TELEGRAM > IO (never SourceType.OTHER)
     */
    override fun observeRecentlyAdded(): Flow<List<HomeMediaItem>> {
        val query = canonicalMediaBox.query()
            .orderDesc(ObxCanonicalMedia_.createdAt)
            .build()

        return query.asFlow()
            .map { canonicalMediaList ->
                val now = System.currentTimeMillis()
                val sevenDaysAgo = now - SEVEN_DAYS_MS
                
                // Take top N items first (FireTV-safe limit)
                val topItems = canonicalMediaList.take(RECENTLY_ADDED_LIMIT)
                
                if (topItems.isEmpty()) {
                    return@map emptyList()
                }
                
                // Build map of canonical key -> best source type
                // Use sources backlink on canonical entity (no extra query needed)
                topItems.map { canonical ->
                    // Access the eager-loaded sources ToMany relation
                    val sourcesLoaded = canonical.sources
                    val bestSource = if (sourcesLoaded.isEmpty()) {
                        SourceType.UNKNOWN
                    } else {
                        selectBestSourceType(sourcesLoaded)
                    }
                    
                    canonical.toHomeMediaItem(
                        isNew = canonical.createdAt >= sevenDaysAgo,
                        navigationSource = bestSource
                    )
                }
            }
            .catch { throwable ->
                UnifiedLog.e(TAG, throwable) { "Failed to observe recently added" }
                emit(emptyList())
            }
    }
    
    /**
     * Select the best source type using strict priority order.
     *
     * Priority: XTREAM > TELEGRAM > IO > UNKNOWN
     * Never returns SourceType.OTHER (ambiguous routing).
     */
    private fun selectBestSourceType(sources: io.objectbox.relation.ToMany<ObxMediaSourceRef>): SourceType {
        val sourceTypes = sources.map { it.sourceType.uppercase() }.toSet()
        return when {
            "XTREAM" in sourceTypes -> SourceType.XTREAM
            "TELEGRAM" in sourceTypes -> SourceType.TELEGRAM
            "IO" in sourceTypes -> SourceType.IO
            else -> SourceType.UNKNOWN
        }
    }

    override fun observeTelegramMedia(): Flow<List<HomeMediaItem>> {
        return telegramContentRepository.observeAll()
            .map { items -> items.map { it.toHomeMediaItem() } }
            .catch { throwable ->
                UnifiedLog.e(TAG, throwable) { "Failed to observe Telegram media content" }
                emit(emptyList())
            }
    }

    override fun observeXtreamLive(): Flow<List<HomeMediaItem>> {
        return xtreamLiveRepository.observeChannels()
            .map { items -> items.map { it.toHomeMediaItem() } }
            .catch { throwable ->
                UnifiedLog.e(TAG, throwable) { "Failed to observe Xtream live TV channels" }
                emit(emptyList())
            }
    }

    override fun observeXtreamVod(): Flow<List<HomeMediaItem>> {
        return xtreamCatalogRepository.observeVod()
            .map { items -> items.map { it.toHomeMediaItem() } }
            .catch { throwable ->
                UnifiedLog.e(TAG, throwable) { "Failed to observe Xtream VOD content" }
                emit(emptyList())
            }
    }

    override fun observeXtreamSeries(): Flow<List<HomeMediaItem>> {
        return xtreamCatalogRepository.observeSeries()
            .map { items -> items.map { it.toHomeMediaItem() } }
            .catch { throwable ->
                UnifiedLog.e(TAG, throwable) { "Failed to observe Xtream series content" }
                emit(emptyList())
            }
    }

    companion object {
        private const val TAG = "HomeContentRepositoryAdapter"
        
        /** Maximum items for Continue Watching row (FireTV-safe) */
        private const val CONTINUE_WATCHING_LIMIT = 30
        
        /** Maximum items for Recently Added row (FireTV-safe) */
        private const val RECENTLY_ADDED_LIMIT = 60
        
        /** Seven days in milliseconds for "new" badge */
        private const val SEVEN_DAYS_MS = 7 * 24 * 60 * 60 * 1000L
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
        return HomeMediaItem(
            id = canonical.canonicalKey,
            title = canonical.canonicalTitle,
            poster = canonical.poster,
            placeholderThumbnail = canonical.thumbnail,
            backdrop = canonical.backdrop,
            mediaType = canonical.kind.toMediaType(),
            sourceType = sourceType,
            resumePosition = resume.positionMs,
            duration = resume.durationMs,
            isNew = false, // Continue watching items are not "new"
            year = canonical.year,
            rating = canonical.rating?.toFloat(),
            navigationId = canonical.canonicalKey,
            navigationSource = sourceType
        )
    }
}

/**
 * Maps RawMediaMetadata to HomeMediaItem (feature-domain model).
 *
 * This mapping extracts only the fields needed for Home screen display,
 * keeping the feature layer decoupled from the full RawMediaMetadata structure.
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
 */
private fun ObxCanonicalMedia.toHomeMediaItem(
    isNew: Boolean = false,
    navigationSource: SourceType = SourceType.UNKNOWN
): HomeMediaItem {
    return HomeMediaItem(
        id = canonicalKey,
        title = canonicalTitle,
        poster = poster,
        placeholderThumbnail = thumbnail,
        backdrop = backdrop,
        mediaType = kind.toMediaType(),
        sourceType = navigationSource,
        resumePosition = 0L,
        duration = durationMs ?: 0L,
        isNew = isNew,
        year = year,
        rating = rating?.toFloat(),
        navigationId = canonicalKey,
        navigationSource = navigationSource
    )
}

/**
 * Converts ObxCanonicalMedia.kind string to MediaType.
 */
private fun String.toMediaType(): MediaType = when (this.lowercase()) {
    "movie" -> MediaType.MOVIE
    "episode" -> MediaType.SERIES_EPISODE
    "series" -> MediaType.SERIES
    "live" -> MediaType.LIVE
    else -> MediaType.UNKNOWN
}

/**
 * Converts source type string (from ObxCanonicalResumeMark.lastSourceType) to SourceType.
 * Never returns SourceType.OTHER to ensure deterministic navigation routing.
 */
private fun String.toSourceType(): SourceType = when (this.uppercase()) {
    "TELEGRAM" -> SourceType.TELEGRAM
    "XTREAM" -> SourceType.XTREAM
    "IO", "LOCAL" -> SourceType.IO
    "AUDIOBOOK" -> SourceType.AUDIOBOOK
    else -> SourceType.UNKNOWN
}
