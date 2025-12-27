package com.fishit.player.v2.work

import android.content.Context
import android.os.Build
import androidx.lifecycle.asFlow
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.fishit.player.core.catalogsync.SyncFailureReason
import com.fishit.player.core.catalogsync.SyncStateObserver
import com.fishit.player.core.catalogsync.SyncUiState
import com.fishit.player.infra.logging.UnifiedLog
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * WorkManager-based implementation of [SyncStateObserver].
 *
 * Observes the `catalog_sync_global` unique work and converts WorkInfo states to simple
 * UI-consumable [SyncUiState].
 *
 * Implements [SyncStateObserver] to allow feature modules to observe sync state without direct
 * WorkManager dependency.
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
     * Returns [SyncUiState.Idle] as default since the actual state will be pushed via
     * [observeSyncState] Flow immediately.
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
                logWorkInfoDiagnostics(workInfo, "ENQUEUED")
                SyncUiState.Running
            }
            WorkInfo.State.RUNNING -> {
                logWorkInfoDiagnostics(workInfo, "RUNNING")
                SyncUiState.Running
            }
            WorkInfo.State.SUCCEEDED -> {
                UnifiedLog.i(TAG) { "Sync state: SUCCEEDED" }
                SyncUiState.Success
            }
            WorkInfo.State.FAILED -> {
                val reason = extractFailureReason(workInfo)
                logWorkInfoDiagnostics(workInfo, "FAILED")
                SyncUiState.Failed(reason)
            }
            WorkInfo.State.BLOCKED -> {
                // Blocked = waiting for constraints (network, etc.)
                logWorkInfoDiagnostics(workInfo, "BLOCKED")
                SyncUiState.Running
            }
            WorkInfo.State.CANCELLED -> {
                UnifiedLog.w(TAG) { "Sync state: CANCELLED" }
                SyncUiState.Idle
            }
        }
    }

    /**
     * Log comprehensive WorkInfo diagnostics.
     *
     * This helps diagnose why workers are stuck in ENQUEUED:
     * - runAttemptCount: How many times WorkManager tried to run
     * - tags: Which worker and mode
     * - stopReason (API 31+): Why the previous run stopped
     * - outputData: Any failure reasons from previous attempts
     */
    private fun logWorkInfoDiagnostics(workInfo: WorkInfo, state: String) {
        val id = workInfo.id
        val tags = workInfo.tags.joinToString(", ")
        val runAttemptCount = workInfo.runAttemptCount

        // Build diagnostic message
        val diagnostics = buildString {
            append("Sync state: $state")
            append(" | id=${id.toString().take(8)}")
            append(" | attempts=$runAttemptCount")
            append(" | tags=[$tags]")

            // API 31+: Check stop reason
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val stopReason = workInfo.stopReason
                if (stopReason != WorkInfo.STOP_REASON_NOT_STOPPED) {
                    append(" | stopReason=${stopReasonToString(stopReason)}")
                }
            }

            // Check output data for failure hints
            val failureReason = workInfo.outputData.getString(KEY_FAILURE_REASON)
            if (!failureReason.isNullOrEmpty()) {
                append(" | outputFailure=$failureReason")
            }

            // Check progress data if available
            val progressPhase = workInfo.progress.getString("phase")
            if (!progressPhase.isNullOrEmpty()) {
                append(" | phase=$progressPhase")
            }
        }

        // Use warning level for stuck states (ENQUEUED with high attempt count or BLOCKED)
        when {
            state == "ENQUEUED" && runAttemptCount > 0 -> {
                UnifiedLog.w(TAG) { "$diagnostics (⚠️ retry after previous attempt)" }
            }
            state == "BLOCKED" -> {
                UnifiedLog.w(TAG) { "$diagnostics (⚠️ waiting for constraints)" }
            }
            else -> {
                UnifiedLog.d(TAG) { diagnostics }
            }
        }
    }

    /** Convert stopReason int to readable string (API 31+). */
    private fun stopReasonToString(stopReason: Int): String =
            when (stopReason) {
                WorkInfo.STOP_REASON_NOT_STOPPED -> "NOT_STOPPED"
                WorkInfo.STOP_REASON_CANCELLED_BY_APP -> "CANCELLED_BY_APP"
                WorkInfo.STOP_REASON_PREEMPT -> "PREEMPT"
                WorkInfo.STOP_REASON_TIMEOUT -> "TIMEOUT"
                WorkInfo.STOP_REASON_DEVICE_STATE -> "DEVICE_STATE"
                WorkInfo.STOP_REASON_CONSTRAINT_BATTERY_NOT_LOW -> "CONSTRAINT_BATTERY_NOT_LOW"
                WorkInfo.STOP_REASON_CONSTRAINT_CHARGING -> "CONSTRAINT_CHARGING"
                WorkInfo.STOP_REASON_CONSTRAINT_CONNECTIVITY -> "CONSTRAINT_CONNECTIVITY"
                WorkInfo.STOP_REASON_CONSTRAINT_DEVICE_IDLE -> "CONSTRAINT_DEVICE_IDLE"
                WorkInfo.STOP_REASON_CONSTRAINT_STORAGE_NOT_LOW -> "CONSTRAINT_STORAGE_NOT_LOW"
                WorkInfo.STOP_REASON_QUOTA -> "QUOTA"
                WorkInfo.STOP_REASON_BACKGROUND_RESTRICTION -> "BACKGROUND_RESTRICTION"
                WorkInfo.STOP_REASON_APP_STANDBY -> "APP_STANDBY"
                WorkInfo.STOP_REASON_USER -> "USER"
                WorkInfo.STOP_REASON_SYSTEM_PROCESSING -> "SYSTEM_PROCESSING"
                WorkInfo.STOP_REASON_ESTIMATED_APP_LAUNCH_TIME_CHANGED ->
                        "ESTIMATED_APP_LAUNCH_TIME_CHANGED"
                else -> "UNKNOWN($stopReason)"
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
