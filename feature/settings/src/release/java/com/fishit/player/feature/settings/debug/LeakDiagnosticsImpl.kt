package com.fishit.player.feature.settings.debug

import android.content.Context
import android.net.Uri
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Release (no-op) implementation of [LeakDiagnostics].
 *
 * LeakCanary is not available in release builds. All methods return
 * safe defaults or failure results.
 */
@Singleton
class LeakDiagnosticsImpl
    @Inject
    constructor() : LeakDiagnostics {
        override val isAvailable: Boolean = false

        override fun openLeakUi(context: Context): Boolean = false

        override suspend fun exportLeakReport(
            context: Context,
            uri: Uri,
        ): Result<Unit> = Result.failure(IllegalStateException("LeakCanary not available in release build"))

        override fun getSummary(): LeakSummary =
            LeakSummary(
                leakCount = 0,
                lastLeakUptimeMs = null,
                note = "LeakCanary not available (release build)",
            )

        override fun getDetailedStatus(): LeakDetailedStatus =
            LeakDetailedStatus(
                retainedObjectCount = 0,
                hasRetainedObjects = false,
                severity = RetentionSeverity.NONE,
                statusMessage = "LeakCanary not available (release build)",
                config =
                    LeakCanaryConfig(
                        retainedVisibleThreshold = 5,
                        computeRetainedHeapSize = false,
                        maxStoredHeapDumps = 7,
                        watchDurationMillis = 5000L,
                        watchActivities = false,
                        watchFragments = false,
                        watchViewModels = false,
                    ),
                memoryStats =
                    MemoryStats(
                        usedMemoryMb = 0,
                        totalMemoryMb = 0,
                        maxMemoryMb = 0,
                        freeMemoryMb = 0,
                    ),
                capturedAtMs = System.currentTimeMillis(),
            )

        override fun getLatestHeapDumpPath(): String? = null

        override fun requestGarbageCollection() {
            // No-op in release
        }

        override fun triggerHeapDump() {
            // No-op in release
        }
    }
