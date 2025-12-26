package com.fishit.player.core.sourceactivation

import kotlinx.coroutines.flow.Flow

/**
 * Single Source of Truth (SSOT) for source activation state.
 *
 * Contract: CATALOG_SYNC_WORKERS_CONTRACT_V2 (W-1)
 * - Sources are independent: Xtream, Telegram, IO can be ACTIVE/INACTIVE separately
 * - No source is ever required
 * - States are persisted across app restarts
 *
 * This store maintains the activation state for all pipeline sources and provides
 * a reactive interface for observing changes. When activation state changes,
 * the catalog sync scheduler should be notified to enqueue/cancel work accordingly.
 *
 * **Important:** This store does NOT trigger syncs directly. It only maintains state.
 * Scheduling logic belongs in SourceActivationObserver which reacts to state changes.
 *
 * **Location:** This interface lives in `core:source-activation-api` to allow both
 * `catalog-sync` (implementation) and `data-*` modules (activation calls)
 * to share it without circular dependencies. The implementation lives in `infra:work`.
 */
interface SourceActivationStore {
    /**
     * Observe all source activation states as a [Flow].
     *
     * Emits a new [SourceActivationSnapshot] whenever any source state changes.
     * Initial emission reflects persisted state from last app session.
     *
     * @return Flow of activation snapshots
     */
    fun observeStates(): Flow<SourceActivationSnapshot>

    /**
     * Get current activation snapshot synchronously.
     *
     * @return Current snapshot of all source states
     */
    fun getCurrentSnapshot(): SourceActivationSnapshot

    /**
     * Get set of currently active source IDs.
     *
     * Convenience method equivalent to `getCurrentSnapshot().activeSources`.
     *
     * @return Set of active source IDs (may be empty)
     */
    fun getActiveSources(): Set<SourceId>

    // =========================================================================
    // Xtream Activation
    // =========================================================================

    /**
     * Set Xtream source as active.
     *
     * Call this when:
     * - Xtream credentials are validated successfully
     * - User completes Xtream login flow
     */
    suspend fun setXtreamActive()

    /**
     * Set Xtream source as inactive.
     *
     * Call this when:
     * - User logs out of Xtream
     * - User manually disables Xtream source
     *
     * @param reason Optional error reason if deactivation is due to an error
     */
    suspend fun setXtreamInactive(reason: SourceErrorReason? = null)

    // =========================================================================
    // Telegram Activation
    // =========================================================================

    /**
     * Set Telegram source as active.
     *
     * Call this when:
     * - TDLib becomes authorized (TdlibAuthState.Ready)
     * - User completes Telegram login flow
     */
    suspend fun setTelegramActive()

    /**
     * Set Telegram source as inactive.
     *
     * Call this when:
     * - TDLib becomes unauthorized (LoggedOut, Closed)
     * - User manually disables Telegram source
     *
     * @param reason Optional error reason if deactivation is due to an error
     */
    suspend fun setTelegramInactive(reason: SourceErrorReason? = null)

    // =========================================================================
    // IO Activation
    // =========================================================================

    /**
     * Set IO (local files) source as active.
     *
     * Call this when:
     * - Storage permission is granted
     * - User selects a local media folder
     */
    suspend fun setIoActive()

    /**
     * Set IO source as inactive.
     *
     * Call this when:
     * - Storage permission is revoked
     * - User manually disables IO source
     *
     * @param reason Optional error reason if deactivation is due to an error
     */
    suspend fun setIoInactive(reason: SourceErrorReason? = null)
}
