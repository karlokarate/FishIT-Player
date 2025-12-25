package com.fishit.player.core.catalogsync

/**
 * Source identifiers for the three supported pipeline sources.
 *
 * Contract: CATALOG_SYNC_WORKERS_CONTRACT_V2 (W-1)
 * - Sources are independent: Xtream, Telegram, IO can be ACTIVE/INACTIVE separately
 * - No source is ever required
 */
enum class SourceId {
    XTREAM,
    TELEGRAM,
    IO,
}

/**
 * Error reasons for source activation failures.
 *
 * These map to specific failure conditions that can be surfaced to the UI.
 */
enum class SourceErrorReason {
    /** User must log in again */
    LOGIN_REQUIRED,

    /** Credentials are invalid or expired */
    INVALID_CREDENTIALS,

    /** Required permission (e.g., storage) is missing */
    PERMISSION_MISSING,

    /** Network or transport-level error */
    TRANSPORT_ERROR,
}

/**
 * Activation state for a single source.
 */
sealed interface SourceActivationState {
    /** Source is not active (user has not configured/enabled it) */
    data object Inactive : SourceActivationState

    /** Source is active and ready for sync */
    data object Active : SourceActivationState

    /** Source failed to activate due to an error */
    data class Error(
        val reason: SourceErrorReason,
    ) : SourceActivationState

    /** Returns true if this state represents an active, working source */
    val isActive: Boolean
        get() = this is Active
}

/**
 * Snapshot of all source activation states.
 *
 * Immutable data class emitted by [SourceActivationStore.observeStates].
 */
data class SourceActivationSnapshot(
    val xtream: SourceActivationState = SourceActivationState.Inactive,
    val telegram: SourceActivationState = SourceActivationState.Inactive,
    val io: SourceActivationState = SourceActivationState.Inactive,
) {
    /** Returns set of currently active source IDs */
    val activeSources: Set<SourceId>
        get() =
            buildSet {
                if (xtream.isActive) add(SourceId.XTREAM)
                if (telegram.isActive) add(SourceId.TELEGRAM)
                if (io.isActive) add(SourceId.IO)
            }

    /** Returns true if at least one source is active */
    val hasActiveSources: Boolean
        get() = activeSources.isNotEmpty()

    companion object {
        /** Default snapshot with all sources inactive */
        val EMPTY = SourceActivationSnapshot()
    }
}
