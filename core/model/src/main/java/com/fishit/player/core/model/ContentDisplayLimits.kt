/**
 * Centralized content display limits for all UI screens.
 *
 * **Architecture:**
 * - Lives in core/model so all data modules can access it
 * - Provides consistent limits across Home, Library, Live screens
 * - Avoids duplication and inconsistency between screens
 *
 * **Design rationale:**
 * - Using asFlowWithLimit(), these are DB-level limits (efficient!)
 * - Limits are high enough for meaningful browsing
 * - TV rows typically show 5-7 visible items; user scrolls for more
 * - For large catalogs (60K+ items), these limits prevent OOM
 *
 * **Usage:**
 * ```kotlin
 * workRepository.observeByType(WorkType.MOVIE, limit = ContentDisplayLimits.MOVIES)
 * ```
 */
package com.fishit.player.core.model

/**
 * Shared content display limits for all UI screens.
 *
 * All limits apply to Flow-based queries with [asFlowWithLimit].
 * Paging-based queries (PagingData) load incrementally and don't use these limits.
 */
object ContentDisplayLimits {
    // ==================== Special Rows (Flow-based) ====================
    //
    // Note: Movies, Series, Clips, Live use PAGING (not Flow limits).
    // Large catalogs (40K+ items) must use PagingData for memory efficiency.
    // Only small, special-purpose rows use Flow-based queries with limits.

    /**
     * Continue Watching row limit.
     * Lower limit since this shows user's personal history.
     */
    const val CONTINUE_WATCHING = 30

    /**
     * Recently Added row limit.
     * Shows newest content in the catalog.
     */
    const val RECENTLY_ADDED = 100

    /**
     * Search results limit.
     * Keeps search responsive.
     */
    const val SEARCH = 100

    // ==================== Time Windows ====================

    /**
     * Time window for "new" badge on recently added content.
     * Content added within this window shows a "NEW" badge.
     */
    const val NEW_BADGE_WINDOW_MS = 7 * 24 * 60 * 60 * 1000L // 7 days

    /**
     * Time window for "New Episodes" badge on series.
     * Series with episodes updated within this window show the badge.
     */
    const val NEW_EPISODES_WINDOW_MS = 48 * 60 * 60 * 1000L // 48 hours

    /**
     * Cache duration for new episodes badge lookup.
     * Avoids repeated DB queries on every Flow emission.
     */
    const val NEW_EPISODES_CACHE_MS = 60_000L // 1 minute

    // ==================== Paging ====================

    /**
     * Paging configuration for home screen horizontal rows.
     */
    object HomePaging {
        const val PAGE_SIZE = 20
        const val INITIAL_LOAD_SIZE = 40
        const val PREFETCH_DISTANCE = 10
    }

    /**
     * Paging configuration for library grid views.
     */
    object LibraryPaging {
        const val PAGE_SIZE = 50
        const val INITIAL_LOAD_SIZE = 150
        const val PREFETCH_DISTANCE = 50
    }

    // ==================== Fallback Behavior ====================

    /**
     * Default profile key for user states when ProfileManager not implemented.
     */
    const val DEFAULT_PROFILE_KEY = "default"
}
