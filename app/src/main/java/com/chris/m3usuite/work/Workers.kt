package com.chris.m3usuite.work

import android.content.Context
import androidx.work.*
import com.chris.m3usuite.data.repo.PlaylistRepository
import com.chris.m3usuite.prefs.SettingsStore
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first

class XtreamRefreshWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        // No-op by policy: Xtream periodic refresh is disabled; use on-demand lazy loading instead
        return Result.success()
    }

    companion object {
        fun schedule(context: Context) {
            // No-op: disabled scheduling per new policy
        }
    }
}

class XtreamEnrichmentWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        // No-op by policy: on-demand enrichment is handled in detail screens
        return Result.success()
    }

    companion object {
        fun schedule(context: Context) {
            // No-op: disabled scheduling per new policy
        }
    }
}
