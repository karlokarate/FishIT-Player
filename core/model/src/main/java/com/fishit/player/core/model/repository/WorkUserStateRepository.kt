package com.fishit.player.core.model.repository

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
 * User state is uniquely identified by: profileId + workKey
 *
 * ## Resume Position
 * Resume is stored as:
 * - `positionMs`: Current position in milliseconds
 * - `durationMs`: Total duration for percentage calculation
 * - Percentage = `positionMs / durationMs`
 * - Works are considered "watched" when percentage > 90% or explicitly marked
 *
 * ## Continue Watching Logic
 * Continue Watching shows works where:
 * - `positionMs > 0`
 * - Resume percentage < 90%
 * - Ordered by `lastWatchedAt` DESC
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
    /**
     * Domain model representing per-profile, per-work user state.
     *
     * This is a pure domain model without persistence annotations.
     * The implementation layer maps this to/from persistence entities.
     */
    data class WorkUserState(
        val id: Long = 0,
        // Keys
        val profileId: Long,
        val workKey: String,
        // Watch State
        val positionMs: Long = 0,
        val durationMs: Long = 0,
        val isWatched: Boolean = false,
        val watchCount: Int = 0,
        // User Actions
        val isFavorite: Boolean = false,
        val userRating: Int? = null,
        val inWatchlist: Boolean = false,
        val isHidden: Boolean = false,
        // Timestamps
        val lastWatchedAt: Long? = null,
        val createdAt: Long = System.currentTimeMillis(),
        val updatedAt: Long = System.currentTimeMillis(),
    ) {
        /**
         * Calculate resume percentage (0.0 to 1.0).
         */
        val resumePercentage: Float
            get() = if (durationMs > 0) positionMs.toFloat() / durationMs else 0f

        /**
         * Check if this qualifies for "Continue Watching" (resume > 0 and < 90%).
         */
        val isContinueWatching: Boolean
            get() = positionMs > 0 && resumePercentage < 0.9f
    }

    // ═══════════════════════════════════════════════════════════════════════
    // CRUD Operations
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Find user state by ID.
     *
     * @param id Entity ID
     * @return User state if found, null otherwise
     */
    suspend fun findById(id: Long): WorkUserState?

    /**
     * Find user state by profile and work key (composite key lookup).
     *
     * @param profileId Profile ID
     * @param workKey Work key
     * @return User state if found, null otherwise
     */
    suspend fun findByProfileAndWork(
        profileId: Long,
        workKey: String,
    ): WorkUserState?

    /**
     * Insert or update user state.
     *
     * If state with same (profileId + workKey) exists, updates it.
     * Otherwise creates new state.
     *
     * Updates `updatedAt` timestamp automatically.
     *
     * @param state User state to upsert
     * @return Updated state with ID populated
     */
    suspend fun upsert(state: WorkUserState): WorkUserState

    /**
     * Delete user state by ID.
     *
     * @param id Entity ID
     * @return true if deleted, false if not found
     */
    suspend fun delete(id: Long): Boolean

    /**
     * Delete user state by profile and work key.
     *
     * @param profileId Profile ID
     * @param workKey Work key
     * @return true if deleted, false if not found
     */
    suspend fun deleteByProfileAndWork(
        profileId: Long,
        workKey: String,
    ): Boolean

    // ═══════════════════════════════════════════════════════════════════════
    // Profile Queries
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Find user states for a profile (paginated).
     *
     * @param profileId Profile ID
     * @param limit Maximum results (default 100)
     * @return List of user states for the profile
     */
    suspend fun findByProfileId(
        profileId: Long,
        limit: Int = 100,
    ): List<WorkUserState>

    /**
     * Find all user states for a profile.
     *
     * ⚠️ WARNING: Use with caution on large datasets. Consider pagination with [findByProfileId].
     *
     * @param profileId Profile ID
     * @return List of all user states for the profile
     */
    suspend fun findAllForProfile(profileId: Long): List<WorkUserState>

    /**
     * Delete all user states for a profile.
     *
     * Useful for profile cleanup or reset.
     *
     * @param profileId Profile ID to clean
     * @return Number of states deleted
     */
    suspend fun deleteAllForProfile(profileId: Long): Int

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
     * @param profileId Profile ID
     * @return List of user states (may be fewer than workKeys if some have no state)
     */
    suspend fun findByWorkKeys(
        workKeys: List<String>,
        profileId: Long,
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
     * Creates state if it doesn't exist. Updates `lastWatchedAt` timestamp.
     * Automatically marks as watched if percentage > 90%.
     *
     * @param profileId Profile ID
     * @param workKey Work key
     * @param positionMs Resume position in milliseconds
     * @param durationMs Total duration in milliseconds
     * @return Updated user state
     */
    suspend fun updateResumePosition(
        profileId: Long,
        workKey: String,
        positionMs: Long,
        durationMs: Long,
    ): WorkUserState

    /**
     * Get resume position in milliseconds.
     *
     * @param profileId Profile ID
     * @param workKey Work key
     * @return Resume position in milliseconds, null if no state or position is 0
     */
    suspend fun getResumePosition(
        profileId: Long,
        workKey: String,
    ): Long?

    /**
     * Get resume percentage (0.0 to 1.0).
     *
     * @param profileId Profile ID
     * @param workKey Work key
     * @return Resume percentage (0.0 to 1.0), null if no state or duration is 0
     */
    suspend fun getResumePercentage(
        profileId: Long,
        workKey: String,
    ): Float?

    /**
     * Clear resume position (set to 0).
     *
     * Does NOT delete the state - preserves other fields like favorites, ratings, etc.
     *
     * @param profileId Profile ID
     * @param workKey Work key
     * @return true if cleared, false if no state found
     */
    suspend fun clearResumePosition(
        profileId: Long,
        workKey: String,
    ): Boolean

    /**
     * Find all states with resume position > 0 for a profile.
     *
     * Ordered by `lastWatchedAt` DESC.
     *
     * @param profileId Profile ID
     * @param limit Maximum results (default 50)
     * @return List of user states with resume position
     */
    suspend fun findWithResumePosition(
        profileId: Long,
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
     * - Ordered by `lastWatchedAt` DESC
     *
     * @param profileId Profile ID
     * @param limit Maximum results (default 20)
     * @return List of user states for continue watching
     */
    suspend fun findContinueWatching(
        profileId: Long,
        limit: Int = 20,
    ): List<WorkUserState>

    /**
     * Observe works in progress for "Continue Watching" row (reactive).
     *
     * @param profileId Profile ID
     * @param limit Maximum results (default 20)
     * @return Flow of user states for continue watching
     */
    fun observeContinueWatching(
        profileId: Long,
        limit: Int = 20,
    ): Flow<List<WorkUserState>>

    // ═══════════════════════════════════════════════════════════════════════
    // Watched Status Operations
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Mark work as watched.
     *
     * Sets `isWatched = true`, increments `watchCount`, updates `lastWatchedAt`.
     * Creates state if it doesn't exist.
     *
     * @param profileId Profile ID
     * @param workKey Work key
     * @return Updated user state
     */
    suspend fun markAsWatched(
        profileId: Long,
        workKey: String,
    ): WorkUserState

    /**
     * Mark work as unwatched.
     *
     * Sets `isWatched = false`, does NOT clear resume position.
     *
     * @param profileId Profile ID
     * @param workKey Work key
     * @return Updated user state (or creates if doesn't exist)
     */
    suspend fun markAsUnwatched(
        profileId: Long,
        workKey: String,
    ): WorkUserState

    /**
     * Check if work is marked as watched.
     *
     * @param profileId Profile ID
     * @param workKey Work key
     * @return true if watched, false otherwise
     */
    suspend fun isWatched(
        profileId: Long,
        workKey: String,
    ): Boolean

    /**
     * Find all watched works for a profile.
     *
     * Ordered by `lastWatchedAt` DESC.
     *
     * @param profileId Profile ID
     * @param limit Maximum results (default 100)
     * @return List of watched user states
     */
    suspend fun findWatched(
        profileId: Long,
        limit: Int = 100,
    ): List<WorkUserState>

    /**
     * Find all unwatched works for a profile.
     *
     * @param profileId Profile ID
     * @param limit Maximum results (default 100)
     * @return List of unwatched user states
     */
    suspend fun findUnwatched(
        profileId: Long,
        limit: Int = 100,
    ): List<WorkUserState>

    /**
     * Get watch count for a work.
     *
     * @param profileId Profile ID
     * @param workKey Work key
     * @return Watch count (0 if no state)
     */
    suspend fun getWatchCount(
        profileId: Long,
        workKey: String,
    ): Int

    /**
     * Increment watch count for a work.
     *
     * Creates state if it doesn't exist. Updates `lastWatchedAt`.
     *
     * @param profileId Profile ID
     * @param workKey Work key
     * @return Updated user state
     */
    suspend fun incrementWatchCount(
        profileId: Long,
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
     * @param profileId Profile ID
     * @param workKey Work key
     * @return Updated user state
     */
    suspend fun addToFavorites(
        profileId: Long,
        workKey: String,
    ): WorkUserState

    /**
     * Remove work from favorites.
     *
     * Sets `isFavorite = false`.
     *
     * @param profileId Profile ID
     * @param workKey Work key
     * @return Updated user state (or creates if doesn't exist)
     */
    suspend fun removeFromFavorites(
        profileId: Long,
        workKey: String,
    ): WorkUserState

    /**
     * Check if work is in favorites.
     *
     * @param profileId Profile ID
     * @param workKey Work key
     * @return true if favorite, false otherwise
     */
    suspend fun isFavorite(
        profileId: Long,
        workKey: String,
    ): Boolean

    /**
     * Find all favorite works for a profile.
     *
     * Ordered by `updatedAt` DESC (most recently favorited first).
     *
     * @param profileId Profile ID
     * @param limit Maximum results (default 100)
     * @return List of favorite user states
     */
    suspend fun findFavorites(
        profileId: Long,
        limit: Int = 100,
    ): List<WorkUserState>

    /**
     * Observe favorite works for a profile (reactive).
     *
     * @param profileId Profile ID
     * @return Flow of favorite user states
     */
    fun observeFavorites(profileId: Long): Flow<List<WorkUserState>>

    // ═══════════════════════════════════════════════════════════════════════
    // Watchlist Operations
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Add work to watchlist.
     *
     * Sets `inWatchlist = true`. Creates state if it doesn't exist.
     *
     * @param profileId Profile ID
     * @param workKey Work key
     * @return Updated user state
     */
    suspend fun addToWatchlist(
        profileId: Long,
        workKey: String,
    ): WorkUserState

    /**
     * Remove work from watchlist.
     *
     * Sets `inWatchlist = false`.
     *
     * @param profileId Profile ID
     * @param workKey Work key
     * @return Updated user state (or creates if doesn't exist)
     */
    suspend fun removeFromWatchlist(
        profileId: Long,
        workKey: String,
    ): WorkUserState

    /**
     * Check if work is in watchlist.
     *
     * @param profileId Profile ID
     * @param workKey Work key
     * @return true if in watchlist, false otherwise
     */
    suspend fun isInWatchlist(
        profileId: Long,
        workKey: String,
    ): Boolean

    /**
     * Find all watchlist works for a profile.
     *
     * Ordered by `updatedAt` DESC (most recently added first).
     *
     * @param profileId Profile ID
     * @param limit Maximum results (default 100)
     * @return List of watchlist user states
     */
    suspend fun findWatchlist(
        profileId: Long,
        limit: Int = 100,
    ): List<WorkUserState>

    /**
     * Observe watchlist works for a profile (reactive).
     *
     * @param profileId Profile ID
     * @return Flow of watchlist user states
     */
    fun observeWatchlist(profileId: Long): Flow<List<WorkUserState>>

    // ═══════════════════════════════════════════════════════════════════════
    // User Rating Operations
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Set user rating for a work.
     *
     * Creates state if it doesn't exist.
     *
     * @param profileId Profile ID
     * @param workKey Work key
     * @param rating User rating (1-5 stars)
     * @return Updated user state
     * @throws IllegalArgumentException if rating is not in 1..5 range
     */
    suspend fun setRating(
        profileId: Long,
        workKey: String,
        rating: Int,
    ): WorkUserState

    /**
     * Clear user rating for a work.
     *
     * Sets `userRating = null`.
     *
     * @param profileId Profile ID
     * @param workKey Work key
     * @return Updated user state (or creates if doesn't exist)
     */
    suspend fun clearRating(
        profileId: Long,
        workKey: String,
    ): WorkUserState

    /**
     * Get user rating for a work.
     *
     * @param profileId Profile ID
     * @param workKey Work key
     * @return User rating (1-5), null if not rated or no state
     */
    suspend fun getRating(
        profileId: Long,
        workKey: String,
    ): Int?

    /**
     * Find all rated works for a profile.
     *
     * Ordered by `updatedAt` DESC (most recently rated first).
     *
     * @param profileId Profile ID
     * @param limit Maximum results (default 100)
     * @return List of rated user states
     */
    suspend fun findRated(
        profileId: Long,
        limit: Int = 100,
    ): List<WorkUserState>

    /**
     * Find works with rating >= minimum rating.
     *
     * Ordered by `userRating` DESC, then `updatedAt` DESC.
     *
     * @param profileId Profile ID
     * @param minRating Minimum rating (1-5)
     * @param limit Maximum results (default 100)
     * @return List of user states with rating >= minRating
     */
    suspend fun findByMinRating(
        profileId: Long,
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
     * @param profileId Profile ID
     * @param workKey Work key
     * @return Updated user state
     */
    suspend fun hideFromRecommendations(
        profileId: Long,
        workKey: String,
    ): WorkUserState

    /**
     * Unhide work from recommendations.
     *
     * Sets `isHidden = false`.
     *
     * @param profileId Profile ID
     * @param workKey Work key
     * @return Updated user state (or creates if doesn't exist)
     */
    suspend fun unhideFromRecommendations(
        profileId: Long,
        workKey: String,
    ): WorkUserState

    /**
     * Check if work is hidden from recommendations.
     *
     * @param profileId Profile ID
     * @param workKey Work key
     * @return true if hidden, false otherwise
     */
    suspend fun isHidden(
        profileId: Long,
        workKey: String,
    ): Boolean

    /**
     * Find all hidden works for a profile.
     *
     * @param profileId Profile ID
     * @param limit Maximum results (default 100)
     * @return List of hidden user states
     */
    suspend fun findHidden(
        profileId: Long,
        limit: Int = 100,
    ): List<WorkUserState>

    // ═══════════════════════════════════════════════════════════════════════
    // Recently Watched Queries
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Find recently watched works.
     *
     * Returns all states with `lastWatchedAt != null`, ordered by `lastWatchedAt` DESC.
     *
     * @param profileId Profile ID
     * @param limit Maximum results (default 20)
     * @return List of recently watched user states
     */
    suspend fun findRecentlyWatched(
        profileId: Long,
        limit: Int = 20,
    ): List<WorkUserState>

    /**
     * Observe recently watched works (reactive).
     *
     * @param profileId Profile ID
     * @param limit Maximum results (default 20)
     * @return Flow of recently watched user states
     */
    fun observeRecentlyWatched(
        profileId: Long,
        limit: Int = 20,
    ): Flow<List<WorkUserState>>

    /**
     * Find works watched since a timestamp.
     *
     * @param profileId Profile ID
     * @param sinceTimestamp Unix timestamp in milliseconds
     * @return List of user states watched since timestamp
     */
    suspend fun findWatchedSince(
        profileId: Long,
        sinceTimestamp: Long,
    ): List<WorkUserState>

    // ═══════════════════════════════════════════════════════════════════════
    // Reactive Streams (Flow)
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Observe user state for a specific profile and work (reactive).
     *
     * @param profileId Profile ID
     * @param workKey Work key
     * @return Flow of user state (null if doesn't exist)
     */
    fun observeByProfileAndWork(
        profileId: Long,
        workKey: String,
    ): Flow<WorkUserState?>

    /**
     * Observe all user states for a profile (reactive).
     *
     * @param profileId Profile ID
     * @return Flow of user states for the profile
     */
    fun observeByProfileId(profileId: Long): Flow<List<WorkUserState>>

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
     * @param profileId Profile ID
     * @return Count of user states for the profile
     */
    suspend fun countByProfileId(profileId: Long): Int

    /**
     * Count watched works for a profile.
     *
     * @param profileId Profile ID
     * @return Count of watched works
     */
    suspend fun countWatched(profileId: Long): Int

    /**
     * Count favorite works for a profile.
     *
     * @param profileId Profile ID
     * @return Count of favorite works
     */
    suspend fun countFavorites(profileId: Long): Int

    /**
     * Count watchlist works for a profile.
     *
     * @param profileId Profile ID
     * @return Count of watchlist works
     */
    suspend fun countWatchlist(profileId: Long): Int

    /**
     * Count rated works for a profile.
     *
     * @param profileId Profile ID
     * @return Count of rated works
     */
    suspend fun countRated(profileId: Long): Int

    /**
     * Get total watch time in milliseconds for a profile.
     *
     * Sums all `positionMs` values for the profile.
     *
     * @param profileId Profile ID
     * @return Total watch time in milliseconds
     */
    suspend fun getTotalWatchTimeMs(profileId: Long): Long

    /**
     * Get average rating for a profile.
     *
     * @param profileId Profile ID
     * @return Average rating (1.0 to 5.0), null if no ratings
     */
    suspend fun getAverageRating(profileId: Long): Float?

    // ═══════════════════════════════════════════════════════════════════════
    // Batch Operations
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Insert or update multiple user states.
     *
     * ⚠️ Should respect batch size limits from ObxWriteConfig.
     *
     * @param states List of user states to upsert
     * @return List of updated states with IDs populated
     */
    suspend fun upsertBatch(states: List<WorkUserState>): List<WorkUserState>

    /**
     * Delete multiple user states by ID.
     *
     * @param ids List of entity IDs
     * @return Number of states deleted
     */
    suspend fun deleteBatch(ids: List<Long>): Int

    /**
     * Mark multiple works as watched.
     *
     * @param profileId Profile ID
     * @param workKeys List of work keys
     * @return Number of states updated
     */
    suspend fun markBatchAsWatched(
        profileId: Long,
        workKeys: List<String>,
    ): Int

    /**
     * Add multiple works to favorites.
     *
     * @param profileId Profile ID
     * @param workKeys List of work keys
     * @return Number of states updated
     */
    suspend fun addBatchToFavorites(
        profileId: Long,
        workKeys: List<String>,
    ): Int

    // ═══════════════════════════════════════════════════════════════════════
    // Validation & Health
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Check if user state exists for profile and work.
     *
     * @param profileId Profile ID
     * @param workKey Work key
     * @return true if state exists, false otherwise
     */
    suspend fun exists(
        profileId: Long,
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
     * Find duplicate user states (same profileId + workKey).
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
     * - profileId > 0
     * - workKey is not blank
     * - userRating is null or in 1..5 range
     * - positionMs >= 0
     * - durationMs >= 0
     * - positionMs <= durationMs
     *
     * @param state User state to validate
     * @return List of validation error messages (empty if valid)
     */
    suspend fun validateState(state: WorkUserState): List<String>
}
