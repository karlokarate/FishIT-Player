package com.fishit.player.feature.settings.debug

import androidx.work.Data
import androidx.work.WorkInfo
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Serializable-ish snapshot of WorkManager state for in-app diagnostics and export.
 *
 * Contract:
 * - Must NOT include secrets (no passwords, tokens, or full credential URLs).
 * - Must be safe to export into debug bundles.
 */
data class WorkManagerSnapshot(
    val capturedAtEpochMs: Long,
    val catalogSyncUniqueWork: List<WorkTaskInfo>,
    val tmdbUniqueWork: List<WorkTaskInfo>,
    val taggedCatalogSyncWork: List<WorkTaskInfo>,
    val taggedTmdbWork: List<WorkTaskInfo>,
) {
    fun toExportText(): String =
        buildString {
            appendLine("FishIT Player - WorkManager Snapshot")
            appendLine("=".repeat(40))
            appendLine("Captured at: ${capturedAtEpochMs.toIso8601()}")
            appendLine()

            appendSection("Unique Work: catalog_sync_global", catalogSyncUniqueWork)
            appendLine()
            appendSection("Unique Work: tmdb_enrichment_global", tmdbUniqueWork)
            appendLine()
            appendSection("Tag: catalog_sync", taggedCatalogSyncWork)
            appendLine()
            appendSection("Tag: source_tmdb / tmdb_enrichment", taggedTmdbWork)
        }

    private fun StringBuilder.appendSection(
        title: String,
        items: List<WorkTaskInfo>,
    ) {
        appendLine(title)
        appendLine("-".repeat(title.length))
        if (items.isEmpty()) {
            appendLine("(no work infos)")
            return
        }
        items.forEach { item ->
            appendLine(
                "• ${item.state} attempts=${item.runAttemptCount} id=${item.id} tags=${item.tags.sorted().joinToString()}",
            )
            if (item.progressKeys.isNotEmpty()) {
                appendLine("  progressKeys=${item.progressKeys.joinToString()}")
            }
            if (item.outputKeys.isNotEmpty()) {
                appendLine("  outputKeys=${item.outputKeys.joinToString()}")
            }
            if (item.failureReason != null) {
                appendLine("  failureReason=${item.failureReason}")
            }
        }
    }

    companion object {
        fun empty(): WorkManagerSnapshot =
            WorkManagerSnapshot(
                capturedAtEpochMs = 0L,
                catalogSyncUniqueWork = emptyList(),
                tmdbUniqueWork = emptyList(),
                taggedCatalogSyncWork = emptyList(),
                taggedTmdbWork = emptyList(),
            )
    }
}

data class WorkTaskInfo(
    val id: String,
    val state: WorkInfo.State,
    val runAttemptCount: Int,
    val tags: Set<String>,
    val progressKeys: List<String>,
    val outputKeys: List<String>,
    val failureReason: String?,
)

internal fun WorkInfo.toTaskInfo(failureReasonKey: String? = null): WorkTaskInfo {
    val failureReason =
        failureReasonKey
            ?.takeIf { it.isNotBlank() }
            ?.let { key -> outputData.getString(key) }
            ?.takeIf { it.isNotBlank() }

    return WorkTaskInfo(
        id = id.toString(),
        state = state,
        runAttemptCount = runAttemptCount,
        tags = tags,
        progressKeys = progress.keyValueMap.keys.sorted(),
        outputKeys = outputData.keyValueMap.keys.sorted(),
        failureReason = failureReason,
    )
}

private fun Long.toIso8601(): String {
    if (this <= 0L) return "N/A"
    val df = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
    return df.format(Date(this))
}

@Suppress("Unused")
private fun Data.safeKeys(): List<String> = keyValueMap.keys.sorted()
