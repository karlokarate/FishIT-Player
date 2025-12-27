package com.fishit.player.v2.work

import android.content.Context
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.fishit.player.core.catalogsync.CatalogSyncWorkScheduler
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

internal const val WORK_NAME = "catalog_sync_global"
private const val CATALOG_SYNC_TAG = "catalog_sync"
private const val WORKER_TAG = "worker/CatalogSyncOrchestratorWorker"

private const val KEY_SYNC_RUN_ID = "sync_run_id"
private const val KEY_SYNC_MODE = "sync_mode"
private const val KEY_ACTIVE_SOURCES = "active_sources"
private const val KEY_WIFI_ONLY = "wifi_only"
private const val KEY_MAX_RUNTIME_MS = "max_runtime_ms"
private const val KEY_DEVICE_CLASS = "device_class"

/**
 * SSOT implementation of [CatalogSyncWorkScheduler] using WorkManager.
 *
 * Contract: CATALOG_SYNC_WORKERS_CONTRACT_V2
 * - uniqueWorkName = "catalog_sync_global"
 * - All sync triggers MUST go through this scheduler
 * - No UI/ViewModel may call CatalogSyncService directly
 */
@Singleton
class CatalogSyncWorkSchedulerImpl
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : CatalogSyncWorkScheduler {
        // ========== Interface Implementation ==========

        override fun enqueueAutoSync() {
            schedule(
                CatalogSyncWorkRequest(
                    syncRunId = UUID.randomUUID().toString(),
                    mode = CatalogSyncWorkMode.AUTO,
                ),
            )
        }

        override fun enqueueExpertSyncNow() {
            schedule(
                CatalogSyncWorkRequest(
                    syncRunId = UUID.randomUUID().toString(),
                    mode = CatalogSyncWorkMode.EXPERT_NOW,
                ),
            )
        }

        override fun enqueueForceRescan() {
            schedule(
                CatalogSyncWorkRequest(
                    syncRunId = UUID.randomUUID().toString(),
                    mode = CatalogSyncWorkMode.FORCE_RESCAN,
                ),
            )
        }

        override fun cancelSync() {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }

        // ========== Internal Implementation ==========

        private fun schedule(request: CatalogSyncWorkRequest) {
            WorkManager
                .getInstance(context)
                .enqueueUniqueWork(
                    WORK_NAME,
                    request.mode.workPolicy,
                    buildRequest(request),
                )
        }

        private fun buildRequest(request: CatalogSyncWorkRequest): OneTimeWorkRequest {
            val builder =
                Data
                    .Builder()
                    .putString(KEY_SYNC_RUN_ID, request.syncRunId)
                    .putString(KEY_SYNC_MODE, request.mode.storageValue)
                    .putStringArray(KEY_ACTIVE_SOURCES, request.activeSources.toTypedArray())
                    .putBoolean(KEY_WIFI_ONLY, request.wifiOnly)
                    .putString(KEY_DEVICE_CLASS, request.deviceClass.orEmpty())

            // Only set max_runtime_ms if explicitly provided and > 0
            // Otherwise, let the worker apply its DEFAULT_MAX_RUNTIME_MS
            // This fixes the bug where null maxRuntimeMs was written as 0ms
            request.maxRuntimeMs?.takeIf { it > 0 }?.let {
                builder.putLong(KEY_MAX_RUNTIME_MS, it)
            }

            val inputData = builder.build()

            return OneTimeWorkRequestBuilder<CatalogSyncOrchestratorWorker>()
                .setInputData(inputData)
                .addTag(CATALOG_SYNC_TAG)
                .addTag(request.mode.tagValue)
                .addTag(WORKER_TAG)
                .build()
        }
    }

data class CatalogSyncWorkRequest(
    val syncRunId: String,
    val mode: CatalogSyncWorkMode,
    val activeSources: List<String> = emptyList(),
    val wifiOnly: Boolean = false,
    val maxRuntimeMs: Long? = null,
    val deviceClass: String? = null,
)

enum class CatalogSyncWorkMode(
    val storageValue: String,
    val tagValue: String,
    val workPolicy: ExistingWorkPolicy,
) {
    AUTO(
        storageValue = "auto",
        tagValue = "mode_auto",
        workPolicy = ExistingWorkPolicy.KEEP,
    ),
    EXPERT_NOW(
        storageValue = "expert_now",
        tagValue = "mode_expert_now",
        workPolicy = ExistingWorkPolicy.KEEP,
    ),
    FORCE_RESCAN(
        storageValue = "force_rescan",
        tagValue = "mode_force_rescan",
        workPolicy = ExistingWorkPolicy.REPLACE,
    ),
}
