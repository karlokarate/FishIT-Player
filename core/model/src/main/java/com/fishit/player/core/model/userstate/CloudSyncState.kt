package com.fishit.player.core.model.userstate

/**
 * Cloud sync state for user-owned data (resume, favorites, ratings, etc.).
 *
 * - CLEAN: persisted locally and matches last known cloud state (or cloud not enabled yet)
 * - DIRTY: local changes pending upload (outbox should eventually flush this)
 */
enum class CloudSyncState {
    CLEAN,
    DIRTY,
}
