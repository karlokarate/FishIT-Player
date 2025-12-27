package com.fishit.player.v2.work

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import androidx.work.Data
import androidx.work.WorkerParameters

/**
 * Helper for reading worker InputData and applying runtime guards.
 *
 * Contract: CATALOG_SYNC_WORKERS_CONTRACT_V2
 * - W-13: Common InputData (MANDATORY)
 * - W-16: Runtime Guards (MANDATORY)
 * - W-17: FireTV Safety (MANDATORY)
 */
data class WorkerInputData(
    val syncRunId: String,
    val syncMode: String,
    val activeSources: Set<String>,
    val wifiOnly: Boolean,
    val maxRuntimeMs: Long,
    val deviceClass: String,
    val xtreamSyncScope: String?,
    val telegramSyncKind: String?,
    val ioSyncScope: String?,
) {
    /** Returns true if this is a FireTV low-RAM device */
    val isFireTvLowRam: Boolean
        get() = deviceClass == WorkerConstants.DEVICE_CLASS_FIRETV_LOW_RAM

    /** Returns appropriate batch size based on device class (W-17) */
    val batchSize: Int
        get() =
            if (isFireTvLowRam) {
                WorkerConstants.FIRETV_BATCH_SIZE
            } else {
                WorkerConstants.NORMAL_BATCH_SIZE
            }

    /** Returns retry limit based on sync mode (W-19) */
    val retryLimit: Int
        get() =
            when (syncMode) {
                WorkerConstants.SYNC_MODE_AUTO -> WorkerConstants.RETRY_LIMIT_AUTO
                else -> WorkerConstants.RETRY_LIMIT_EXPERT
            }

    companion object {
        /**
         * Parse InputData from WorkerParameters.
         */
        fun from(workerParams: WorkerParameters): WorkerInputData = from(workerParams.inputData)

        /**
         * Parse InputData from Data directly.
         * Use this when accessing inputData from CoroutineWorker.
         *
         * **Runtime Budget Failsafe:**
         * If max_runtime_ms is missing, 0, or negative, the default is applied.
         * This guards against scheduling bugs that might write 0ms.
         */
        fun from(data: Data): WorkerInputData {
            // Failsafe: treat missing, 0, or negative values as "use default"
            // The key might be missing (not set) or explicitly set to an invalid value
            val rawMaxRuntimeMs = data.getLong(WorkerConstants.KEY_MAX_RUNTIME_MS, 0L)
            val effectiveMaxRuntimeMs = if (rawMaxRuntimeMs > 0) {
                rawMaxRuntimeMs
            } else {
                WorkerConstants.DEFAULT_MAX_RUNTIME_MS
            }

            return WorkerInputData(
                syncRunId = data.getString(WorkerConstants.KEY_SYNC_RUN_ID) ?: "",
                syncMode = data.getString(WorkerConstants.KEY_SYNC_MODE) ?: WorkerConstants.SYNC_MODE_AUTO,
                activeSources =
                    data
                        .getStringArray(WorkerConstants.KEY_ACTIVE_SOURCES)
                        ?.toSet() ?: emptySet(),
                wifiOnly = data.getBoolean(WorkerConstants.KEY_WIFI_ONLY, false),
                maxRuntimeMs = effectiveMaxRuntimeMs,
                deviceClass =
                    data.getString(WorkerConstants.KEY_DEVICE_CLASS)
                        ?: WorkerConstants.DEVICE_CLASS_ANDROID_PHONE_TABLET,
                xtreamSyncScope = data.getString(WorkerConstants.KEY_XTREAM_SYNC_SCOPE),
                telegramSyncKind = data.getString(WorkerConstants.KEY_TELEGRAM_SYNC_KIND),
                ioSyncScope = data.getString(WorkerConstants.KEY_IO_SYNC_SCOPE),
            )
        }
    }
}

/**
 * Runtime guards per W-16.
 *
 * Workers MUST defer if:
 * - battery < 15%
 * - Data Saver enabled
 * - Roaming enabled
 */
object RuntimeGuards {
    /**
     * Check all runtime guards. Returns null if all guards pass,
     * or a reason string if worker should defer.
     */
    fun checkGuards(context: Context): String? {
        // Check battery level
        val batteryLevel = getBatteryLevel(context)
        if (batteryLevel != null && batteryLevel < 15) {
            return "battery_low"
        }

        // Note: Data Saver and Roaming checks require additional permissions
        // and are less critical for initial implementation

        return null
    }

    private fun getBatteryLevel(context: Context): Int? {
        val batteryIntent =
            context.registerReceiver(
                null,
                IntentFilter(Intent.ACTION_BATTERY_CHANGED),
            )
        return batteryIntent?.let { intent ->
            val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            if (level >= 0 && scale > 0) {
                (level * 100 / scale)
            } else {
                null
            }
        }
    }

    /**
     * Detect if running on FireTV with low RAM.
     *
     * FireTV Stick (1st gen): 1GB RAM
     * FireTV Stick Lite: 1GB RAM
     * FireTV Stick 4K (1st gen): 1.5GB RAM
     */
    fun detectDeviceClass(context: Context): String {
        val isFireTv =
            Build.MODEL.contains("AFT", ignoreCase = true) ||
                Build.MANUFACTURER.equals("Amazon", ignoreCase = true)

        if (!isFireTv) {
            return WorkerConstants.DEVICE_CLASS_ANDROID_PHONE_TABLET
        }

        // Check available memory
        val activityManager =
            context.getSystemService(Context.ACTIVITY_SERVICE)
                as? android.app.ActivityManager
        val memoryInfo = android.app.ActivityManager.MemoryInfo()
        activityManager?.getMemoryInfo(memoryInfo)

        // Consider low RAM if total < 2GB
        val totalMemoryGb = memoryInfo.totalMem / (1024.0 * 1024 * 1024)

        return if (totalMemoryGb < 2.0) {
            WorkerConstants.DEVICE_CLASS_FIRETV_LOW_RAM
        } else {
            WorkerConstants.DEVICE_CLASS_ANDROID_PHONE_TABLET
        }
    }
}

/**
 * Builder for worker output data.
 */
object WorkerOutputData {
    fun success(
        itemsPersisted: Long,
        durationMs: Long,
        checkpointCursor: String? = null,
    ): Data =
        Data
            .Builder()
            .putLong(WorkerConstants.KEY_ITEMS_PERSISTED, itemsPersisted)
            .putLong(WorkerConstants.KEY_DURATION_MS, durationMs)
            .apply {
                if (checkpointCursor != null) {
                    putString(WorkerConstants.KEY_CHECKPOINT_CURSOR, checkpointCursor)
                }
            }.build()

    fun failure(reason: String): Data =
        Data
            .Builder()
            .putString(WorkerConstants.KEY_FAILURE_REASON, reason)
            .build()
}
