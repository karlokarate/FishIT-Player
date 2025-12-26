package com.fishit.player.core.sourceactivation

/**
 * Activation state for a single source.
 *
 * **Location:** This interface lives in `core:source-activation-api` to allow both
 * `catalog-sync` (implementation) and `data-*` modules (activation calls)
 * to share it without circular dependencies.
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
