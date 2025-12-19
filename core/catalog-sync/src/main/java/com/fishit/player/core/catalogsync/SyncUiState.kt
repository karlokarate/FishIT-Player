package com.fishit.player.core.catalogsync

/**
 * UI state for catalog sync operations.
 * 
 * Maps WorkManager work states to simple UI-consumable states.
 * Contract: CATALOG_SYNC_WORKERS_CONTRACT_V2
 */
sealed interface SyncUiState {
    /** No sync running */
    data object Idle : SyncUiState
    
    /** Sync is currently running */
    data object Running : SyncUiState
    
    /** Sync completed successfully */
    data object Success : SyncUiState
    
    /** Sync failed */
    data class Failed(val reason: SyncFailureReason) : SyncUiState
    
    /** Returns true if sync is currently running */
    val isRunning: Boolean
        get() = this is Running
    
    /** Returns true if sync has completed (success or failure) */
    val isCompleted: Boolean
        get() = this is Success || this is Failed
}

/**
 * Reasons why sync can fail.
 * 
 * Maps to user-friendly error messages.
 */
enum class SyncFailureReason {
    /** User must log in again */
    LOGIN_REQUIRED,
    /** Credentials are invalid or expired */
    INVALID_CREDENTIALS,
    /** Required permission (e.g., storage) is missing */
    PERMISSION_MISSING,
    /** Network guard prevented sync (e.g., WiFi-only setting) */
    NETWORK_GUARD,
    /** Unknown or unhandled error */
    UNKNOWN
}

/**
 * Extension to get a human-readable description of the failure.
 */
fun SyncFailureReason.toDisplayString(): String = when (this) {
    SyncFailureReason.LOGIN_REQUIRED -> "Login required"
    SyncFailureReason.INVALID_CREDENTIALS -> "Invalid credentials"
    SyncFailureReason.PERMISSION_MISSING -> "Permission missing"
    SyncFailureReason.NETWORK_GUARD -> "Network unavailable"
    SyncFailureReason.UNKNOWN -> "Unknown error"
}
