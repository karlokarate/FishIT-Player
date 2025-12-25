package com.fishit.player.v2.work

import android.content.Context
import androidx.lifecycle.asFlow
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.fishit.player.core.catalogsync.SyncFailureReason
import com.fishit.player.core.catalogsync.SyncStateObserver
import com.fishit.player.core.catalogsync.SyncUiState
import com.fishit.player.infra.logging.UnifiedLog
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * WorkManager-based implementation of [SyncStateObserver].
 *
 * Observes the `catalog_sync_global` unique work and converts WorkInfo states
 * to simple UI-consumable [SyncUiState].
 *
 * Implements [SyncStateObserver] to allow feature modules to observe sync state
 * without direct WorkManager dependency.
 *
 * **Architecture:**
 * - Lives in app-v2 (wiring layer)
 * - Implements core contract [SyncStateObserver]
 * - No business logic; state mapping only
 *
 * Contract: CATALOG_SYNC_WORKERS_CONTRACT_V2
 * - No business logic; state mapping only
 * - Uses only `catalog_sync_global` work name
 */
@Singleton
class WorkManagerSyncStateObserver
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : SyncStateObserver {
        private val workManager: WorkManager
            get() = WorkManager.getInstance(context)

        /**
         * Observe the current sync UI state as a Flow.
         *
         * Maps WorkManager [WorkInfo.State] to [SyncUiState]:
         * - ENQUEUED, RUNNING → Running
         * - SUCCEEDED → Success
         * - FAILED, CANCELLED → Failed/Idle
         * - BLOCKED → Running (waiting for constraints)
         * - No work → Idle
         */
        override fun observeSyncState(): Flow<SyncUiState> =
            workManager
                .getWorkInfosForUniqueWorkLiveData(WORK_NAME)
                .asFlow()
                .map { workInfos -> mapToSyncUiState(workInfos) }
                .distinctUntilChanged()

        /**
         * Get current sync state.
         *
         * Returns [SyncUiState.Idle] as default since the actual state
         * will be pushed via [observeSyncState] Flow immediately.
         *
         * Note: Avoided synchronous WorkManager query to prevent Guava dependency issues.
         */
        override fun getCurrentState(): SyncUiState {
            // Return Idle as safe default. The actual state will be
            // pushed via observeSyncState() Flow shortly after.
            return SyncUiState.Idle
        }

        private fun mapToSyncUiState(workInfos: List<WorkInfo>): SyncUiState {
            // Get the most recent work info (unique work should have at most one)
            val workInfo = workInfos.firstOrNull()

            if (workInfo == null) {
                return SyncUiState.Idle
            }

            return when (workInfo.state) {
                WorkInfo.State.ENQUEUED -> {
                    UnifiedLog.d(TAG) { "Sync state: ENQUEUED" }
                    SyncUiState.Running
                }
                WorkInfo.State.RUNNING -> {
                    UnifiedLog.d(TAG) { "Sync state: RUNNING" }
                    SyncUiState.Running
                }
                WorkInfo.State.SUCCEEDED -> {
                    UnifiedLog.d(TAG) { "Sync state: SUCCEEDED" }
                    SyncUiState.Success
                }
                WorkInfo.State.FAILED -> {
                    val reason = extractFailureReason(workInfo)
                    UnifiedLog.d(TAG) { "Sync state: FAILED (reason=$reason)" }
                    SyncUiState.Failed(reason)
                }
                WorkInfo.State.BLOCKED -> {
                    // Blocked = waiting for constraints (network, etc.)
                    UnifiedLog.d(TAG) { "Sync state: BLOCKED (waiting for constraints)" }
                    SyncUiState.Running
                }
                WorkInfo.State.CANCELLED -> {
                    UnifiedLog.d(TAG) { "Sync state: CANCELLED" }
                    SyncUiState.Idle
                }
            }
        }

        private fun extractFailureReason(workInfo: WorkInfo): SyncFailureReason {
            // Try to extract failure reason from output data
            val outputData = workInfo.outputData
            val reasonString = outputData.getString(KEY_FAILURE_REASON)

            return when (reasonString) {
                "LOGIN_REQUIRED" -> SyncFailureReason.LOGIN_REQUIRED
                "INVALID_CREDENTIALS" -> SyncFailureReason.INVALID_CREDENTIALS
                "PERMISSION_MISSING" -> SyncFailureReason.PERMISSION_MISSING
                "NETWORK_GUARD" -> SyncFailureReason.NETWORK_GUARD
                else -> SyncFailureReason.UNKNOWN
            }
        }

        private companion object {
            private const val TAG = "WorkManagerSyncStateObserver"
            private const val WORK_NAME = "catalog_sync_global"
            private const val KEY_FAILURE_REASON = "failure_reason"
        }
    }
