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
class LeakDiagnosticsImpl @Inject constructor() : LeakDiagnostics {

    override val isAvailable: Boolean = false

    override fun openLeakUi(context: Context): Boolean = false

    override suspend fun exportLeakReport(context: Context, uri: Uri): Result<Unit> =
        Result.failure(IllegalStateException("LeakCanary not available in release build"))

    override fun getSummary(): LeakSummary =
        LeakSummary(
            leakCount = 0,
            lastLeakUptimeMs = null,
            note = "LeakCanary not available (release build)"
        )

    override fun getLatestHeapDumpPath(): String? = null
}
