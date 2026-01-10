package com.fishit.player.core.model.repository

import com.fishit.player.core.model.userstate.WorkUserState
import kotlinx.coroutines.flow.Flow

/**
 * Repository for managing per-profile, per-work user state.
 *
 * **SSOT Contract:** docs/v2/NX_SSOT_CONTRACT.md
 * **Roadmap:** docs/v2/OBX_PLATIN_REFACTOR_ROADMAP.md
 *
 * ## State Types Managed
 * - Resume position (percentage-based, cross-source)
 * - Watched status (>90% or explicit mark)
 * - Favorites
 * - Watchlist
 * - User ratings (1-5 stars)
 * - Hidden from recommendations
 *
 * ## Composite Key
 * User state is uniquely identified by: profileKey + workKey
 *
 * ## Resume Position
 * Resume is stored as:
 * - `positionMs`: Current position in milliseconds
 * - `durationMsAtLastPlay`: Total duration for percentage calculation
 * - Percentage = `positionMs / durationMsAtLastPlay`
 * - Works are considered "watched" when percentage > 90% or explicitly marked
 *
 * ## Continue Watching Logic
 * Continue Watching shows works where:
 * - `positionMs > 0`
 * - Resume percentage < 90%
 * - Ordered by `lastPlayedAtMs` DESC
 *
 * ## Return Type Patterns
 * This interface uses standard Kotlin patterns for operation results:
 * - **Entity returns**: `upsert()`, `updateResumePosition()` etc. return the domain model
 * - **Boolean returns**: `delete()`, `markAsWatched()` etc. return success/failure
 * - **Nullable returns**: `find*()` methods return null when not found
 * - **Int returns**: Count operations return number of affected records
 *
 * For error details beyond boolean success/failure, implementations may throw
 * typed exceptions (e.g., IllegalArgumentException for invalid rating values).
 *
 * ## Batch Size Limits
 * Batch operations should respect device-aware batch size limits defined in
 * `core/persistence/config/ObxWriteConfig.kt`. Implementations should use:
 * - `ObxWriteConfig.getBatchSize(context)` for device-aware sizing
 * - Default batch size: 100 items (normal devices)
 * - FireTV batch cap: 35 items
 *
 * ## Domain Model Architecture
 * This repository interface is in `core/model/repository/` and uses domain types only.
 * The implementation in `infra/data-nx/` maps between domain models and persistence entities.
 * This avoids circular dependencies and keeps domain logic separate from storage details.
 *
 * @see WorkUserState
 */
@Suppress("TooManyFunctions") // Repository interfaces legitimately need comprehensive data access methods
interface WorkUserStateRepository {
    // ═══════════════════════════════════════════════════════════════════════
    // CRUD Operations
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Find user state by profile and work key (composite key lookup).
     *
     * @param profileKey Profile key (stable across devices)
     * @param workKey Work key
     * @return User state if found, null otherwise
     */
    suspend fun findByProfileAndWork(
        profileKey: String,
        workKey: String,
    ): WorkUserState?

    /**
     * Insert or update user state.
     *
     * If state with same (profileKey + workKey) exists, updates it.
     * Otherwise creates new state.
     *
     * Updates `lastUpdatedAtMs` timestamp automatically.
     *
     * @param state User state to upsert
     * @return Updated state
     */
    suspend fun upsert(state: WorkUserState): WorkUserState

    /**
     * Delete user state by profile and work key.
     *
     * @param profileKey Profile key
     * @param workKey Work key
     * @return true if deleted, false if not found
     */
    suspend fun deleteByProfileAndWork(
        profileKey: String,
        workKey: String,
    ): Boolean

    // ═══════════════════════════════════════════════════════════════════════
    // Profile Queries
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Find user states for a profile (paginated).
     *
     * @param profileKey Profile key
     * @param limit Maximum results (default 100)
     * @return List of user states for the profile
     */
    suspend fun findByProfileKey(
        profileKey: String,
        limit: Int = 100,
    ): List<WorkUserState>

    /**
     * Find all user states for a profile.
     *
     * ⚠️ WARNING: Use with caution on large datasets. Consider pagination with [findByProfileKey].
     *
     * @param profileKey Profile key
     * @return List of all user states for the profile
     */
    suspend fun findAllForProfile(profileKey: String): List<WorkUserState>

    /**
     * Delete all user states for a profile.
     *
     * Useful for profile cleanup or reset.
     *
     * @param profileKey Profile key to clean
     * @return Number of states deleted
     */
    suspend fun deleteAllForProfile(profileKey: String): Int

    // ═══════════════════════════════════════════════════════════════════════
    // Work Queries
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Find all user states for a work (across all profiles).
     *
     * @param workKey Work key
     * @return List of user states for the work
     */
    suspend fun findByWorkKey(workKey: String): List<WorkUserState>

    /**
     * Find user states for multiple works (for a specific profile).
     *
     * Used for batch loading state for a list of works (e.g., home screen rows).
     *
     * @param workKeys List of work keys
     * @param profileKey Profile key
     * @return List of user states (may be fewer than workKeys if some have no state)
     */
    suspend fun findByWorkKeys(
        workKeys: List<String>,
        profileKey: String,
    ): List<WorkUserState>

    /**
     * Delete all user states for a work (across all profiles).
     *
     * Useful for work cleanup when work is deleted.
     *
     * @param workKey Work key to clean
     * @return Number of states deleted
     */
    suspend fun deleteByWorkKey(workKey: String): Int

    // ═══════════════════════════════════════════════════════════════════════
    // Resume Position Operations
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Update resume position for a work.
     *
     * Creates state if it doesn't exist. Updates `lastPlayedAtMs` timestamp.
     * Automatically marks as watched if percentage > 90%.
     *
     * @param profileKey Profile key
     * @param workKey Work key
     * @param positionMs Resume position in milliseconds
     * @param durationMs Total duration in milliseconds
     * @return Updated user state
     */
    suspend fun updateResumePosition(
        profileKey: String,
        workKey: String,
        positionMs: Long,
        durationMs: Long,
    ): WorkUserState

    /**
     * Get resume position in milliseconds.
     *
     * @param profileKey Profile key
     * @param workKey Work key
     * @return Resume position in milliseconds, null if no state or position is 0
     */
    suspend fun getResumePosition(
        profileKey: String,
        workKey: String,
    ): Long?

    /**
     * Get resume percentage (0.0 to 1.0).
     *
     * @param profileKey Profile key
     * @param workKey Work key
     * @return Resume percentage (0.0 to 1.0), null if no state or duration is 0
     */
    suspend fun getResumePercentage(
        profileKey: String,
        workKey: String,
    ): Float?

    /**
     * Clear resume position (set to 0).
     *
     * Does NOT delete the state - preserves other fields like favorites, ratings, etc.
     *
     * @param profileKey Profile key
     * @param workKey Work key
     * @return true if cleared, false if no state found
     */
    suspend fun clearResumePosition(
        profileKey: String,
        workKey: String,
    ): Boolean

    /**
     * Find all states with resume position > 0 for a profile.
     *
     * Ordered by `lastPlayedAtMs` DESC.
     *
     * @param profileKey Profile key
     * @param limit Maximum results (default 50)
     * @return List of user states with resume position
     */
    suspend fun findWithResumePosition(
        profileKey: String,
        limit: Int = 50,
    ): List<WorkUserState>

    // ═══════════════════════════════════════════════════════════════════════
    // Continue Watching (Resume > 0 and < 90%)
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Find works in progress for "Continue Watching" row.
     *
     * Returns states where:
     * - `positionMs > 0`
     * - Resume percentage < 90%
     * - Ordered by `lastPlayedAtMs` DESC
     *
     * @param profileKey Profile key
     * @param limit Maximum results (default 20)
     * @return List of user states for continue watching
     */
    suspend fun findContinueWatching(
        profileKey: String,
        limit: Int = 20,
    ): List<WorkUserState>

    /**
     * Observe works in progress for "Continue Watching" row (reactive).
     *
     * @param profileKey Profile key
     * @param limit Maximum results (default 20)
     * @return Flow of user states for continue watching
     */
    fun observeContinueWatching(
        profileKey: String,
        limit: Int = 20,
    ): Flow<List<WorkUserState>>

    // ═══════════════════════════════════════════════════════════════════════
    // Watched Status Operations
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Mark work as watched.
     *
     * Sets `isWatched = true`, increments `watchCount`, updates `lastPlayedAtMs`.
     * Creates state if it doesn't exist.
     *
     * @param profileKey Profile key
     * @param workKey Work key
     * @return Updated user state
     */
    suspend fun markAsWatched(
        profileKey: String,
        workKey: String,
    ): WorkUserState

    /**
     * Mark work as unwatched.
     *
     * Sets `isWatched = false`, does NOT clear resume position.
     *
     * @param profileKey Profile key
     * @param workKey Work key
     * @return Updated user state (or creates if doesn't exist)
     */
    suspend fun markAsUnwatched(
        profileKey: String,
        workKey: String,
    ): WorkUserState

    /**
     * Check if work is marked as watched.
     *
     * @param profileKey Profile key
     * @param workKey Work key
     * @return true if watched, false otherwise
     */
    suspend fun isWatched(
        profileKey: String,
        workKey: String,
    ): Boolean

    /**
     * Find all watched works for a profile.
     *
     * Ordered by `lastPlayedAtMs` DESC.
     *
     * @param profileKey Profile key
     * @param limit Maximum results (default 100)
     * @return List of watched user states
     */
    suspend fun findWatched(
        profileKey: String,
        limit: Int = 100,
    ): List<WorkUserState>

    /**
     * Find all unwatched works for a profile.
     *
     * @param profileKey Profile key
     * @param limit Maximum results (default 100)
     * @return List of unwatched user states
     */
    suspend fun findUnwatched(
        profileKey: String,
        limit: Int = 100,
    ): List<WorkUserState>

    /**
     * Get watch count for a work.
     *
     * @param profileKey Profile key
     * @param workKey Work key
     * @return Watch count (0 if no state)
     */
    suspend fun getWatchCount(
        profileKey: String,
        workKey: String,
    ): Int

    /**
     * Increment watch count for a work.
     *
     * Creates state if it doesn't exist. Updates `lastPlayedAtMs`.
     *
     * @param profileKey Profile key
     * @param workKey Work key
     * @return Updated user state
     */
    suspend fun incrementWatchCount(
        profileKey: String,
        workKey: String,
    ): WorkUserState

    // ═══════════════════════════════════════════════════════════════════════
    // Favorites Operations
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Add work to favorites.
     *
     * Sets `isFavorite = true`. Creates state if it doesn't exist.
     *
     * @param profileKey Profile key
     * @param workKey Work key
     * @return Updated user state
     */
    suspend fun addToFavorites(
        profileKey: String,
        workKey: String,
    ): WorkUserState

    /**
     * Remove work from favorites.
     *
     * Sets `isFavorite = false`.
     *
     * @param profileKey Profile key
     * @param workKey Work key
     * @return Updated user state (or creates if doesn't exist)
     */
    suspend fun removeFromFavorites(
        profileKey: String,
        workKey: String,
    ): WorkUserState

    /**
     * Check if work is in favorites.
     *
     * @param profileKey Profile key
     * @param workKey Work key
     * @return true if favorite, false otherwise
     */
    suspend fun isFavorite(
        profileKey: String,
        workKey: String,
    ): Boolean

    /**
     * Find all favorite works for a profile.
     *
     * Ordered by `lastUpdatedAtMs` DESC (most recently favorited first).
     *
     * @param profileKey Profile key
     * @param limit Maximum results (default 100)
     * @return List of favorite user states
     */
    suspend fun findFavorites(
        profileKey: String,
        limit: Int = 100,
    ): List<WorkUserState>

    /**
     * Observe favorite works for a profile (reactive).
     *
     * @param profileKey Profile key
     * @return Flow of favorite user states
     */
    fun observeFavorites(profileKey: String): Flow<List<WorkUserState>>

    // ═══════════════════════════════════════════════════════════════════════
    // Watchlist Operations
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Add work to watchlist.
     *
     * Sets `inWatchlist = true`. Creates state if it doesn't exist.
     *
     * @param profileKey Profile key
     * @param workKey Work key
     * @return Updated user state
     */
    suspend fun addToWatchlist(
        profileKey: String,
        workKey: String,
    ): WorkUserState

    /**
     * Remove work from watchlist.
     *
     * Sets `inWatchlist = false`.
     *
     * @param profileKey Profile key
     * @param workKey Work key
     * @return Updated user state (or creates if doesn't exist)
     */
    suspend fun removeFromWatchlist(
        profileKey: String,
        workKey: String,
    ): WorkUserState

    /**
     * Check if work is in watchlist.
     *
     * @param profileKey Profile key
     * @param workKey Work key
     * @return true if in watchlist, false otherwise
     */
    suspend fun isInWatchlist(
        profileKey: String,
        workKey: String,
    ): Boolean

    /**
     * Find all watchlist works for a profile.
     *
     * Ordered by `lastUpdatedAtMs` DESC (most recently added first).
     *
     * @param profileKey Profile key
     * @param limit Maximum results (default 100)
     * @return List of watchlist user states
     */
    suspend fun findWatchlist(
        profileKey: String,
        limit: Int = 100,
    ): List<WorkUserState>

    /**
     * Observe watchlist works for a profile (reactive).
     *
     * @param profileKey Profile key
     * @return Flow of watchlist user states
     */
    fun observeWatchlist(profileKey: String): Flow<List<WorkUserState>>

    // ═══════════════════════════════════════════════════════════════════════
    // User Rating Operations
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Set user rating for a work.
     *
     * Creates state if it doesn't exist.
     *
     * @param profileKey Profile key
     * @param workKey Work key
     * @param rating User rating (1-5 stars)
     * @return Updated user state
     * @throws IllegalArgumentException if rating is not in 1..5 range
     */
    suspend fun setRating(
        profileKey: String,
        workKey: String,
        rating: Int,
    ): WorkUserState

    /**
     * Clear user rating for a work.
     *
     * Sets `userRating = null`.
     *
     * @param profileKey Profile key
     * @param workKey Work key
     * @return Updated user state (or creates if doesn't exist)
     */
    suspend fun clearRating(
        profileKey: String,
        workKey: String,
    ): WorkUserState

    /**
     * Get user rating for a work.
     *
     * @param profileKey Profile key
     * @param workKey Work key
     * @return User rating (1-5), null if not rated or no state
     */
    suspend fun getRating(
        profileKey: String,
        workKey: String,
    ): Int?

    /**
     * Find all rated works for a profile.
     *
     * Ordered by `lastUpdatedAtMs` DESC (most recently rated first).
     *
     * @param profileKey Profile key
     * @param limit Maximum results (default 100)
     * @return List of rated user states
     */
    suspend fun findRated(
        profileKey: String,
        limit: Int = 100,
    ): List<WorkUserState>

    /**
     * Find works with rating >= minimum rating.
     *
     * Ordered by `userRating` DESC, then `lastUpdatedAtMs` DESC.
     *
     * @param profileKey Profile key
     * @param minRating Minimum rating (1-5)
     * @param limit Maximum results (default 100)
     * @return List of user states with rating >= minRating
     */
    suspend fun findByMinRating(
        profileKey: String,
        minRating: Int,
        limit: Int = 100,
    ): List<WorkUserState>

    // ═══════════════════════════════════════════════════════════════════════
    // Hidden Operations
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Hide work from recommendations.
     *
     * Sets `isHidden = true`. Creates state if it doesn't exist.
     *
     * @param profileKey Profile key
     * @param workKey Work key
     * @return Updated user state
     */
    suspend fun hideFromRecommendations(
        profileKey: String,
        workKey: String,
    ): WorkUserState

    /**
     * Unhide work from recommendations.
     *
     * Sets `isHidden = false`.
     *
     * @param profileKey Profile key
     * @param workKey Work key
     * @return Updated user state (or creates if doesn't exist)
     */
    suspend fun unhideFromRecommendations(
        profileKey: String,
        workKey: String,
    ): WorkUserState

    /**
     * Check if work is hidden from recommendations.
     *
     * @param profileKey Profile key
     * @param workKey Work key
     * @return true if hidden, false otherwise
     */
    suspend fun isHidden(
        profileKey: String,
        workKey: String,
    ): Boolean

    /**
     * Find all hidden works for a profile.
     *
     * @param profileKey Profile key
     * @param limit Maximum results (default 100)
     * @return List of hidden user states
     */
    suspend fun findHidden(
        profileKey: String,
        limit: Int = 100,
    ): List<WorkUserState>

    // ═══════════════════════════════════════════════════════════════════════
    // Recently Watched Queries
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Find recently watched works.
     *
     * Returns all states with `lastPlayedAtMs != null`, ordered by `lastPlayedAtMs` DESC.
     *
     * @param profileKey Profile key
     * @param limit Maximum results (default 20)
     * @return List of recently watched user states
     */
    suspend fun findRecentlyWatched(
        profileKey: String,
        limit: Int = 20,
    ): List<WorkUserState>

    /**
     * Observe recently watched works (reactive).
     *
     * @param profileKey Profile key
     * @param limit Maximum results (default 20)
     * @return Flow of recently watched user states
     */
    fun observeRecentlyWatched(
        profileKey: String,
        limit: Int = 20,
    ): Flow<List<WorkUserState>>

    /**
     * Find works watched since a timestamp.
     *
     * @param profileKey Profile key
     * @param sinceTimestamp Unix timestamp in milliseconds
     * @return List of user states watched since timestamp
     */
    suspend fun findWatchedSince(
        profileKey: String,
        sinceTimestamp: Long,
    ): List<WorkUserState>

    // ═══════════════════════════════════════════════════════════════════════
    // Reactive Streams (Flow)
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Observe user state for a specific profile and work (reactive).
     *
     * @param profileKey Profile key
     * @param workKey Work key
     * @return Flow of user state (null if doesn't exist)
     */
    fun observeByProfileAndWork(
        profileKey: String,
        workKey: String,
    ): Flow<WorkUserState?>

    /**
     * Observe all user states for a profile (reactive).
     *
     * @param profileKey Profile key
     * @return Flow of user states for the profile
     */
    fun observeByProfileKey(profileKey: String): Flow<List<WorkUserState>>

    /**
     * Observe all user states (reactive).
     *
     * ⚠️ WARNING: Use with caution on large datasets.
     *
     * @return Flow of all user states
     */
    fun observeAll(): Flow<List<WorkUserState>>

    // ═══════════════════════════════════════════════════════════════════════
    // Counts & Statistics
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Count total user states in database.
     *
     * @return Total count of all user states
     */
    suspend fun count(): Long

    /**
     * Count user states for a profile.
     *
     * @param profileKey Profile key
     * @return Count of user states for the profile
     */
    suspend fun countByProfileKey(profileKey: String): Int

    /**
     * Count watched works for a profile.
     *
     * @param profileKey Profile key
     * @return Count of watched works
     */
    suspend fun countWatched(profileKey: String): Int

    /**
     * Count favorite works for a profile.
     *
     * @param profileKey Profile key
     * @return Count of favorite works
     */
    suspend fun countFavorites(profileKey: String): Int

    /**
     * Count watchlist works for a profile.
     *
     * @param profileKey Profile key
     * @return Count of watchlist works
     */
    suspend fun countWatchlist(profileKey: String): Int

    /**
     * Count rated works for a profile.
     *
     * @param profileKey Profile key
     * @return Count of rated works
     */
    suspend fun countRated(profileKey: String): Int

    /**
     * Get total watch time in milliseconds for a profile.
     *
     * Sums all `positionMs` values for the profile.
     *
     * @param profileKey Profile key
     * @return Total watch time in milliseconds
     */
    suspend fun getTotalWatchTimeMs(profileKey: String): Long

    /**
     * Get average rating for a profile.
     *
     * @param profileKey Profile key
     * @return Average rating (1.0 to 5.0), null if no ratings
     */
    suspend fun getAverageRating(profileKey: String): Float?

    // ═══════════════════════════════════════════════════════════════════════
    // Batch Operations
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Insert or update multiple user states.
     *
     * ⚠️ Should respect batch size limits from ObxWriteConfig.
     *
     * @param states List of user states to upsert
     * @return List of updated states
     */
    suspend fun upsertBatch(states: List<WorkUserState>): List<WorkUserState>

    /**
     * Mark multiple works as watched.
     *
     * @param profileKey Profile key
     * @param workKeys List of work keys
     * @return Number of states updated
     */
    suspend fun markBatchAsWatched(
        profileKey: String,
        workKeys: List<String>,
    ): Int

    /**
     * Add multiple works to favorites.
     *
     * @param profileKey Profile key
     * @param workKeys List of work keys
     * @return Number of states updated
     */
    suspend fun addBatchToFavorites(
        profileKey: String,
        workKeys: List<String>,
    ): Int

    // ═══════════════════════════════════════════════════════════════════════
    // Validation & Health
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Check if user state exists for profile and work.
     *
     * @param profileKey Profile key
     * @param workKey Work key
     * @return true if state exists, false otherwise
     */
    suspend fun exists(
        profileKey: String,
        workKey: String,
    ): Boolean

    /**
     * Find orphaned user states (work no longer exists).
     *
     * Useful for database cleanup.
     *
     * @param limit Maximum results (default 100)
     * @return List of orphaned user states
     */
    suspend fun findOrphanedStates(limit: Int = 100): List<WorkUserState>

    /**
     * Find user states with invalid rating values.
     *
     * Finds states where `userRating` is not null and not in 1..5 range.
     *
     * @param limit Maximum results (default 100)
     * @return List of user states with invalid ratings
     */
    suspend fun findInvalidRatings(limit: Int = 100): List<WorkUserState>

    /**
     * Find duplicate user states (same profileKey + workKey).
     *
     * Should not exist due to composite key uniqueness, but useful for validation.
     *
     * @param limit Maximum results (default 100)
     * @return List of duplicate user states
     */
    suspend fun findDuplicateStates(limit: Int = 100): List<WorkUserState>

    /**
     * Validate user state entity.
     *
     * Checks:
     * - profileKey is not blank
     * - workKey is not blank
     * - userRating is null or in 1..5 range
     * - positionMs >= 0
     * - durationMsAtLastPlay >= 0
     * - positionMs <= durationMsAtLastPlay
     *
     * @param state User state to validate
     * @return List of validation error messages (empty if valid)
     */
    suspend fun validateState(state: WorkUserState): List<String>
}
