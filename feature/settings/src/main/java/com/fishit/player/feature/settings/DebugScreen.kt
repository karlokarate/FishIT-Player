package com.fishit.player.feature.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WorkHistory
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.work.WorkInfo
import com.fishit.player.core.catalogsync.SyncUiState
import com.fishit.player.feature.settings.debug.MemoryStats
import com.fishit.player.feature.settings.debug.RetentionSeverity
import com.fishit.player.feature.settings.debug.WorkManagerSnapshot
import com.fishit.player.feature.settings.debug.WorkTaskInfo
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Debug Screen - Mini diagnostics view
 *
 * Shows:
 * - System info (app version, device)
 * - Connection status (Telegram, Xtream)
 * - Cache sizes and clear actions
 * - Pipeline statistics
 * - Memory / LeakCanary diagnostics
 * - Recent logs
 * - Debug playback (test player)
 */
@Composable
fun DebugScreen(
    onBack: () -> Unit,
    onDebugPlayback: () -> Unit = {},
    onDatabaseInspector: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: DebugViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    val clipboardManager = LocalClipboardManager.current

    var selectionMode by remember { mutableStateOf(false) }
    var selectedKeys by remember { mutableStateOf<Set<String>>(emptySet()) }

    val exportLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.CreateDocument("text/plain"),
            onResult = { uri ->
                if (uri != null) {
                    viewModel.exportAllLogs(uri)
                }
            },
        )

    // LeakCanary report export launcher
    val exportLeakReportLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.CreateDocument("text/plain"),
            onResult = { uri ->
                if (uri != null) {
                    viewModel.exportLeakReport(uri)
                }
            },
        )

    // Debug bundle (ZIP) export launcher
    val exportDebugBundleLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.CreateDocument("application/zip"),
            onResult = { uri ->
                if (uri != null) {
                    viewModel.exportDebugBundle(uri)
                }
            },
        )

    // WorkManager snapshot export launcher
    val exportWorkManagerSnapshotLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.CreateDocument("text/plain"),
            onResult = { uri ->
                if (uri != null) {
                    viewModel.exportWorkManagerSnapshot(uri)
                }
            },
        )

    Box(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top bar
            DebugTopBar(onBack = onBack, onRefresh = viewModel::refreshInfo)

            // Content
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item { Spacer(modifier = Modifier.height(8.dp)) }

                // Debug Playback Section (test player)
                item {
                    DebugSection(title = "Playback Test", icon = Icons.Default.BugReport) {
                        Text(
                            text =
                                "Test the internal player with a sample video stream (Big Buck Bunny).",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(onClick = onDebugPlayback, modifier = Modifier.fillMaxWidth()) {
                            Icon(
                                imageVector = Icons.Default.BugReport,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Open Debug Player")
                        }
                    }
                }

                // System Info Section
                item {
                    DebugSection(title = "System Info", icon = Icons.Default.Info) {
                        InfoRow("App Version", state.appVersion)
                        InfoRow("Build Type", state.buildType)
                        InfoRow("Device", state.deviceModel)
                        InfoRow("Android", state.androidVersion)
                    }
                }

                // API Credentials Section (separate from connection status!)
                item {
                    DebugSection(title = "API Credentials", icon = Icons.Default.Info) {
                        // Telegram API credentials (BuildConfig)
                        CredentialStatusRow(
                            name = "Telegram API",
                            isConfigured = state.telegramCredentialsConfigured,
                            details = state.telegramCredentialStatus,
                        )
                        // TMDB API key (BuildConfig)
                        CredentialStatusRow(
                            name = "TMDB API",
                            isConfigured = state.tmdbApiKeyConfigured,
                            details =
                                if (state.tmdbApiKeyConfigured) {
                                    "Configured"
                                } else {
                                    "Missing (TMDB_API_KEY not set)"
                                },
                        )
                    }
                }

                // Connection Status Section
                item {
                    DebugSection(title = "Connections", icon = Icons.Default.Cloud) {
                        ConnectionRow(
                            name = "Telegram",
                            icon = Icons.Default.Send,
                            isConnected = state.telegramConnected,
                            details = state.telegramUser,
                        )
                        ConnectionRow(
                            name = "Xtream",
                            icon = Icons.Default.Cloud,
                            isConnected = state.xtreamConnected,
                            details = state.xtreamServer,
                        )
                    }
                }

                // === Catalog Sync (SSOT via WorkManager) ===
                item {
                    DebugSection(title = "Catalog Sync", icon = Icons.Default.Refresh) {
                        // Sync Status Line
                        SyncStatusRow(syncState = state.syncState)

                        Spacer(modifier = Modifier.height(12.dp))

                        // Sync Action Buttons
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            // Sync Now Button
                            Button(
                                onClick = viewModel::syncAll,
                                enabled = !state.syncState.isRunning,
                                modifier = Modifier.weight(1f),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Sync,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Sync")
                            }

                            // Force Rescan Button
                            OutlinedButton(
                                onClick = viewModel::forceRescan,
                                modifier = Modifier.weight(1f),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Force")
                            }

                            // Cancel Button (only enabled when running)
                            OutlinedButton(
                                onClick = viewModel::cancelSync,
                                enabled = state.syncState.isRunning,
                                modifier = Modifier.weight(1f),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Cancel")
                            }
                        }
                    }
                }

                // === TMDB Enrichment (W-22) ===
                item {
                    DebugSection(title = "TMDB Enrichment", icon = Icons.Default.Cloud) {
                        Text(
                            text =
                                "Enriches catalog items with TMDB metadata (posters, backdrops, ratings).",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            // Enrich Button
                            Button(
                                onClick = viewModel::enqueueTmdbEnrichment,
                                modifier = Modifier.weight(1f),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Cloud,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Enrich")
                            }

                            // Force Refresh Button
                            OutlinedButton(
                                onClick = viewModel::forceTmdbRefresh,
                                modifier = Modifier.weight(1f),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Force")
                            }
                        }
                    }
                }

                // === WorkManager Snapshot (Diagnostics) ===
                item {
                    WorkManagerSnapshotSection(
                        snapshot = state.workManagerSnapshot,
                        onCopy = {
                            val text = viewModel.getWorkManagerSnapshotText()
                            clipboardManager.setText(AnnotatedString(text))
                            viewModel.setActionResult("WorkManager snapshot copied")
                        },
                        onExport = {
                            val timestamp =
                                SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
                                    .format(Date())
                            exportWorkManagerSnapshotLauncher.launch(
                                "fishit_workmanager_$timestamp.txt",
                            )
                        },
                    )
                }

                // Cache Section
                item {
                    DebugSection(title = "Cache", icon = Icons.Default.Storage) {
                        CacheRow(
                            name = "Telegram Cache",
                            size = state.telegramCacheSize,
                            onClear = viewModel::clearTelegramCache,
                            isClearing = state.isClearingCache,
                        )
                        CacheRow(
                            name = "Image Cache",
                            size = state.imageCacheSize,
                            onClear = viewModel::clearImageCache,
                            isClearing = state.isClearingCache,
                        )
                        CacheRow(
                            name = "Database",
                            size = state.dbSize,
                            onClear = null, // DB can't be cleared
                            isClearing = false,
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedButton(
                            onClick = viewModel::clearAllCaches,
                            enabled = !state.isClearingCache,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            if (state.isClearingCache) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                )
                            } else {
                                Icon(Icons.Default.Delete, contentDescription = null)
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Clear All Caches")
                        }
                    }
                }

                // Pipeline Stats Section
                item {
                    DebugSection(title = "Pipeline Stats", icon = Icons.Default.BugReport) {
                        StatsRow("Telegram Media", state.telegramMediaCount)
                        StatsRow("Xtream VOD", state.xtreamVodCount)
                        StatsRow("Xtream Series", state.xtreamSeriesCount)
                        StatsRow("Xtream Live", state.xtreamLiveCount)
                    }
                }

                // Chucker HTTP Inspector Section
                item {
                    DebugSection(title = "HTTP Inspector", icon = Icons.Default.Cloud) {
                        if (state.isChuckerAvailable) {
                            Text(
                                text = "Chucker captures all HTTP requests for debugging.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            Text(
                                text = "Chucker not available (release build)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Button(
                            onClick = { viewModel.openChuckerUi() },
                            enabled = state.isChuckerAvailable,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(
                                imageVector = Icons.Default.BugReport,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Open HTTP Inspector")
                        }
                    }
                }

                // Memory / LeakCanary Section
                item {
                    LeakCanaryDiagnosticsSection(
                        state = state,
                        onOpenLeakCanary = { viewModel.openLeakCanaryUi() },
                        onRefresh = { viewModel.refreshLeakSummary() },
                        onRequestGc = { viewModel.requestGarbageCollection() },
                        onTriggerHeapDump = { viewModel.triggerHeapDump() },
                        onExportReport = {
                            val fileName = buildLeakExportFileName()
                            exportLeakReportLauncher.launch(fileName)
                        },
                    )
                }

                // Database Tools
                item {
                    DebugSection(title = "Database Tools", icon = Icons.Default.Storage) {
                        Text(
                            text =
                                "Inspect and edit ObjectBox rows. This is a power tool — changes apply immediately.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(onClick = onDatabaseInspector, modifier = Modifier.fillMaxWidth()) {
                            Icon(
                                imageVector = Icons.Default.Storage,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Open DB Inspector")
                        }
                    }
                }

                // Recent Logs Section
                item {
                    DebugSection(title = "Recent Logs", icon = Icons.Default.BugReport) {
                        LogActionsRow(
                            selectionMode = selectionMode,
                            selectionCount = selectedKeys.size,
                            totalVisible = state.recentLogs.size,
                            onToggleSelectionMode = {
                                val next = !selectionMode
                                selectionMode = next
                                if (!next) {
                                    selectedKeys = emptySet()
                                }
                            },
                            onSelectAllVisible = {
                                selectedKeys =
                                    state.recentLogs.map { it.selectionKey() }.toSet()
                            },
                            onClearSelection = { selectedKeys = emptySet() },
                            onCopySelected = {
                                val selected =
                                    state.recentLogs.filter {
                                        selectedKeys.contains(it.selectionKey())
                                    }
                                val text =
                                    selected.joinToString(separator = "\n") {
                                        it.toClipboardLine()
                                    }
                                clipboardManager.setText(AnnotatedString(text))
                                viewModel.setActionResult(
                                    "Copied ${selected.size} log(s) to clipboard",
                                )
                            },
                            onExportAll = {
                                val fileName = buildLogExportFileName()
                                exportLauncher.launch(fileName)
                            },
                            onClearLogs = {
                                viewModel.clearLogs()
                                selectionMode = false
                                selectedKeys = emptySet()
                            },
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // Debug Bundle Export
                        OutlinedButton(
                            onClick = {
                                val fileName = buildDebugBundleFileName()
                                exportDebugBundleLauncher.launch(fileName)
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(
                                imageVector = Icons.Default.FolderZip,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Export Debug Bundle (ZIP)")
                        }
                        Text(
                            text = "Includes logs, leak report, and device info.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }

                // Log entries
                items(items = state.recentLogs, key = { it.selectionKey() }) { log ->
                    val key = log.selectionKey()
                    val isSelected = selectedKeys.contains(key)
                    LogEntryRow(
                        log = log,
                        selectionMode = selectionMode,
                        isSelected = isSelected,
                        onToggleSelected = {
                            selectedKeys =
                                if (isSelected) {
                                    selectedKeys - key
                                } else {
                                    selectedKeys + key
                                }
                        },
                        onCopy = {
                            // Long-press copies a single entry by default.
                            // If selection mode is active and this entry is selected, copy the
                            // whole selection.
                            val shouldCopySelection =
                                selectionMode && isSelected && selectedKeys.isNotEmpty()
                            if (shouldCopySelection) {
                                val selected =
                                    state.recentLogs.filter {
                                        selectedKeys.contains(it.selectionKey())
                                    }
                                val text =
                                    selected.joinToString(separator = "\n") {
                                        it.toClipboardLine()
                                    }
                                clipboardManager.setText(AnnotatedString(text))
                                viewModel.setActionResult(
                                    "Copied ${selected.size} selected log(s) to clipboard",
                                )
                            } else {
                                clipboardManager.setText(AnnotatedString(log.toClipboardLine()))
                                viewModel.setActionResult("Log copied to clipboard")
                            }
                        },
                    )
                }

                // Load more button
                if (state.recentLogs.isNotEmpty()) {
                    item {
                        TextButton(
                            onClick = viewModel::loadMoreLogs,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            if (state.isLoadingLogs) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                )
                            } else {
                                Text("Load More Logs")
                            }
                        }
                    }
                }

                item { Spacer(modifier = Modifier.height(48.dp)) }
            }
        }

        // Snackbar for action results
        state.lastActionResult?.let { result ->
            Snackbar(
                modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
                action = {
                    TextButton(onClick = viewModel::dismissActionResult) { Text("Dismiss") }
                },
            ) { Text(result) }
        }
    }
}

@Composable
private fun DebugTopBar(
    onBack: () -> Unit,
    onRefresh: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = MaterialTheme.colorScheme.onBackground,
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        Icon(
            Icons.Default.BugReport,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(28.dp),
        )

        Spacer(modifier = Modifier.width(8.dp))

        Text(
            text = "Debug & Diagnostics",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
        )

        Spacer(modifier = Modifier.weight(1f))

        IconButton(onClick = onRefresh) {
            Icon(
                Icons.Default.Refresh,
                contentDescription = "Refresh",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DebugSection(
    title: String,
    icon: ImageVector,
    content: @Composable () -> Unit,
) {
    Card(
        colors =
            CardDefaults.cardColors(
                containerColor =
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            content()
        }
    }
}

@Composable
private fun InfoRow(
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun ConnectionRow(
    name: String,
    icon: ImageVector,
    isConnected: Boolean,
    details: String?,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (isConnected) Color(0xFF4CAF50) else Color(0xFFBDBDBD),
            modifier = Modifier.size(20.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = name,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        if (isConnected) {
            details?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Icon(
                Icons.Default.Check,
                contentDescription = "Connected",
                tint = Color(0xFF4CAF50),
                modifier = Modifier.size(16.dp),
            )
        } else {
            Icon(
                Icons.Default.Close,
                contentDescription = "Disconnected",
                tint = Color(0xFFBDBDBD),
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

/**
 * Row displaying API credential configuration status.
 *
 * **Important distinction from ConnectionRow:**
 * - CredentialStatusRow shows if credentials are CONFIGURED (BuildConfig)
 * - ConnectionRow shows if the user is CONNECTED/AUTHORIZED (runtime state)
 */
@Composable
private fun CredentialStatusRow(
    name: String,
    isConfigured: Boolean,
    details: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector =
                if (isConfigured) Icons.Default.CheckCircle else Icons.Default.Warning,
            contentDescription = null,
            tint = if (isConfigured) Color(0xFF4CAF50) else Color(0xFFFF9800),
            modifier = Modifier.size(20.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = details,
                style = MaterialTheme.typography.bodySmall,
                color =
                    if (isConfigured) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        Color(0xFFFF9800)
                    },
            )
        }
    }
}

@Composable
private fun CacheRow(
    name: String,
    size: String,
    onClear: (() -> Unit)?,
    isClearing: Boolean,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = name,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = size,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (onClear != null) {
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(onClick = onClear, enabled = !isClearing, modifier = Modifier.size(32.dp)) {
                if (isClearing) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                } else {
                    Icon(
                        Icons.Default.Clear,
                        contentDescription = "Clear",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun StatsRow(
    name: String,
    count: Int,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = name,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun LogEntryRow(
    log: LogEntry,
    selectionMode: Boolean,
    isSelected: Boolean,
    onToggleSelected: () -> Unit,
    onCopy: () -> Unit,
) {
    val timeFormatter = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())

    val levelColor =
        when (log.level) {
            LogLevel.DEBUG -> Color(0xFF9E9E9E)
            LogLevel.INFO -> Color(0xFF2196F3)
            LogLevel.WARN -> Color(0xFFFF9800)
            LogLevel.ERROR -> Color(0xFFF44336)
        }

    val levelIcon =
        when (log.level) {
            LogLevel.DEBUG -> Icons.Default.BugReport
            LogLevel.INFO -> Icons.Default.Info
            LogLevel.WARN -> Icons.Default.Warning
            LogLevel.ERROR -> Icons.Default.Close
        }

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(
                    color =
                        if (isSelected) {
                            MaterialTheme.colorScheme.primaryContainer.copy(
                                alpha = 0.35f,
                            )
                        } else {
                            Color.Transparent
                        },
                    shape = MaterialTheme.shapes.small,
                ).combinedClickable(
                    onClick = {
                        if (selectionMode) {
                            onToggleSelected()
                        }
                    },
                    onLongClick = onCopy,
                ).padding(vertical = 8.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        if (selectionMode) {
            Checkbox(
                checked = isSelected,
                onCheckedChange = { onToggleSelected() },
                modifier =
                    Modifier
                        .semantics { contentDescription = "Select log entry" }
                        .padding(top = 2.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
        }

        Icon(
            imageVector = levelIcon,
            contentDescription = log.level.name,
            tint = levelColor,
            modifier = Modifier.size(16.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row {
                Text(
                    text = timeFormatter.format(Date(log.timestamp)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = log.tag,
                    style = MaterialTheme.typography.labelSmall,
                    color = levelColor,
                )
            }
            Text(
                text = log.message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun LogActionsRow(
    selectionMode: Boolean,
    selectionCount: Int,
    totalVisible: Int,
    onToggleSelectionMode: () -> Unit,
    onSelectAllVisible: () -> Unit,
    onClearSelection: () -> Unit,
    onCopySelected: () -> Unit,
    onExportAll: () -> Unit,
    onClearLogs: () -> Unit,
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedButton(
                onClick = onToggleSelectionMode,
            ) { Text(if (selectionMode) "Done" else "Select") }

            OutlinedButton(
                onClick = onExportAll,
            ) { Text("Export") }

            OutlinedButton(
                onClick = onClearLogs,
            ) { Text("Clear") }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (selectionMode) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Selected $selectionCount / $totalVisible",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )

                TextButton(
                    onClick = onSelectAllVisible,
                    enabled = totalVisible > 0,
                ) { Text("All") }

                TextButton(
                    onClick = onClearSelection,
                    enabled = selectionCount > 0,
                ) { Text("None") }

                TextButton(
                    onClick = onCopySelected,
                    enabled = selectionCount > 0,
                ) { Text("Copy") }
            }
        } else {
            Text(
                text = "Tip: long-press any log entry to copy it.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun LogEntry.selectionKey(): String {
    // Stable-ish key for UI selection. Uses a hash of the message to avoid huge keys.
    val msgHash = message.hashCode()
    return "${timestamp}_${level.name}_${tag}_$msgHash"
}

private fun LogEntry.toClipboardLine(): String {
    val formatter = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
    val t = formatter.format(Date(timestamp))
    return "$t ${level.name} $tag: $message"
}

private fun buildLogExportFileName(): String {
    val formatter = SimpleDateFormat("yyyy-MM-dd_HHmmss", Locale.US)
    val ts = formatter.format(Date())
    return "fishit_logs_$ts.txt"
}

private fun buildLeakExportFileName(): String {
    val formatter = SimpleDateFormat("yyyy-MM-dd_HH-mm", Locale.US)
    val ts = formatter.format(Date())
    return "fishit_leaks_$ts.txt"
}

private fun buildDebugBundleFileName(): String {
    val formatter = SimpleDateFormat("yyyy-MM-dd_HH-mm", Locale.US)
    val ts = formatter.format(Date())
    return "fishit_debug_bundle_$ts.zip"
}

/** Compact sync status row showing current WorkManager state. */
@Composable
private fun SyncStatusRow(
    syncState: SyncUiState,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .background(
                    color =
                        when (syncState) {
                            is SyncUiState.Running ->
                                MaterialTheme.colorScheme.primaryContainer
                                    .copy(alpha = 0.3f)
                            is SyncUiState.Success ->
                                MaterialTheme.colorScheme.secondaryContainer
                                    .copy(alpha = 0.3f)
                            is SyncUiState.Failed ->
                                MaterialTheme.colorScheme.errorContainer
                                    .copy(alpha = 0.3f)
                            is SyncUiState.Idle -> Color.Transparent
                        },
                    shape = MaterialTheme.shapes.small,
                ).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Status Icon
        when (syncState) {
            is SyncUiState.Running -> {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            is SyncUiState.Success -> {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Sync successful",
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            is SyncUiState.Failed -> {
                Icon(
                    imageVector = Icons.Default.Error,
                    contentDescription = "Sync failed",
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.error,
                )
            }
            is SyncUiState.Idle -> {
                Icon(
                    imageVector = Icons.Default.HourglassEmpty,
                    contentDescription = "Sync idle",
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // Status Text
        Text(
            text =
                when (syncState) {
                    is SyncUiState.Running -> "Syncing..."
                    is SyncUiState.Success -> "Last sync: Success"
                    is SyncUiState.Failed ->
                        "Failed: ${syncState.reason.name.lowercase().replace('_', ' ')}"
                    is SyncUiState.Idle -> "Ready to sync"
                },
            style = MaterialTheme.typography.bodySmall,
            color =
                when (syncState) {
                    is SyncUiState.Failed -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
        )
    }
}

// ============================================================================
// WorkManager Snapshot Section
// ============================================================================

/**
 * WorkManager Snapshot diagnostics section.
 *
 * Shows current WorkManager state with Copy/Export buttons.
 */
@Composable
private fun WorkManagerSnapshotSection(
    snapshot: WorkManagerSnapshot,
    onCopy: () -> Unit,
    onExport: () -> Unit,
) {
    DebugSection(
        title = "WorkManager Snapshot",
        icon = Icons.Default.WorkHistory,
    ) {
        // Action buttons row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(onClick = onCopy, modifier = Modifier.weight(1f)) {
                Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("Copy")
            }

            Button(onClick = onExport, modifier = Modifier.weight(1f)) {
                Icon(
                    imageVector = Icons.Default.FolderZip,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("Export")
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Unique work sections
        WorkInfoGroupSection(
            title = "Unique: catalog_sync_global",
            items = snapshot.catalogSyncUniqueWork,
        )

        WorkInfoGroupSection(
            title = "Unique: tmdb_enrichment_global",
            items = snapshot.tmdbUniqueWork,
        )

        WorkInfoGroupSection(title = "Tag: catalog_sync", items = snapshot.taggedCatalogSyncWork)

        WorkInfoGroupSection(title = "Tag: source_tmdb", items = snapshot.taggedTmdbWork)
    }
}

@Composable
private fun WorkInfoGroupSection(
    title: String,
    items: List<WorkTaskInfo>,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.padding(vertical = 4.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        if (items.isEmpty()) {
            Text(
                text = "(no work infos)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            items.forEach { info -> WorkInfoItemRow(info) }
        }
    }
}

@Composable
private fun WorkInfoItemRow(
    info: WorkTaskInfo,
    modifier: Modifier = Modifier,
) {
    val stateColor =
        when (info.state) {
            WorkInfo.State.RUNNING -> MaterialTheme.colorScheme.primary
            WorkInfo.State.SUCCEEDED -> MaterialTheme.colorScheme.tertiary
            WorkInfo.State.FAILED -> MaterialTheme.colorScheme.error
            WorkInfo.State.CANCELLED -> MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
            WorkInfo.State.ENQUEUED -> MaterialTheme.colorScheme.secondary
            WorkInfo.State.BLOCKED -> MaterialTheme.colorScheme.onSurfaceVariant
        }

    Row(
        modifier = modifier.fillMaxWidth().padding(start = 8.dp, top = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // State indicator
        Box(
            modifier =
                Modifier
                    .size(8.dp)
                    .background(stateColor, shape = MaterialTheme.shapes.extraSmall),
        )
        Spacer(modifier = Modifier.width(8.dp))

        // Info text
        Text(
            text = "${info.state.name} attempts=${info.runAttemptCount}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }

    // Show failure reason if present
    info.failureReason?.let { reason ->
        Text(
            text = "  → $reason",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(start = 16.dp),
        )
    }
}

// ============================================================================
// LeakCanary Diagnostics Section (Gold Standard Noise Control)
// ============================================================================

/**
 * LeakCanary Diagnostics Section with Noise Control.
 *
 * Features:
 * - Severity-based color coding (NONE/LOW/MEDIUM/HIGH)
 * - Detailed status message explaining retention level
 * - Memory statistics display
 * - Actions: Open UI, Refresh, Request GC, Trigger Heap Dump, Export Report
 */
@Composable
private fun LeakCanaryDiagnosticsSection(
    state: DebugState,
    onOpenLeakCanary: () -> Unit,
    onRefresh: () -> Unit,
    onRequestGc: () -> Unit,
    onTriggerHeapDump: () -> Unit,
    onExportReport: () -> Unit,
) {
    DebugSection(title = "Memory / LeakCanary", icon = Icons.Default.Memory) {
        if (state.isLeakCanaryAvailable) {
            val detailedStatus = state.leakDetailedStatus

            // Severity Banner
            if (detailedStatus != null) {
                LeakSeverityBanner(
                    severity = detailedStatus.severity,
                    statusMessage = detailedStatus.statusMessage,
                    retainedCount = detailedStatus.retainedObjectCount,
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Memory Stats
                MemoryStatsRow(memoryStats = detailedStatus.memoryStats)

                Spacer(modifier = Modifier.height(8.dp))

                // Config Info (collapsed by default)
                Text(
                    text =
                        "Watch: ${detailedStatus.config.watchDurationMillis}ms | Threshold: ${detailedStatus.config.retainedVisibleThreshold}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                // Fallback to simple summary
                Text(
                    text = "Retained objects: ${state.leakSummary.leakCount}",
                    style = MaterialTheme.typography.bodyMedium,
                )
                state.leakSummary.note?.let { note ->
                    Text(
                        text = note,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            Text(
                text = "LeakCanary not available (release build)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Primary Action Row: Open + Refresh
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                onClick = onOpenLeakCanary,
                enabled = state.isLeakCanaryAvailable,
                modifier = Modifier.weight(1f),
            ) {
                Icon(
                    imageVector = Icons.Default.Memory,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("Open")
            }

            OutlinedButton(
                onClick = onRefresh,
                enabled = state.isLeakCanaryAvailable,
                modifier = Modifier.weight(1f),
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("Refresh")
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Secondary Action Row: GC + Heap Dump
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(
                onClick = onRequestGc,
                enabled = state.isLeakCanaryAvailable,
                modifier = Modifier.weight(1f),
            ) {
                Icon(
                    imageVector = Icons.Default.Clear,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("GC")
            }

            OutlinedButton(
                onClick = onTriggerHeapDump,
                enabled = state.isLeakCanaryAvailable,
                modifier = Modifier.weight(1f),
            ) {
                Icon(
                    imageVector = Icons.Default.BugReport,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("Dump")
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Export Action
        OutlinedButton(
            onClick = onExportReport,
            enabled = state.isLeakCanaryAvailable,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(
                imageVector = Icons.Default.FolderZip,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Export Leak Report")
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = "For heap dump export, use 'Share heap dump' in LeakCanary UI.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Severity banner with color coding. */
@Composable
private fun LeakSeverityBanner(
    severity: com.fishit.player.feature.settings.debug.RetentionSeverity,
    statusMessage: String,
    retainedCount: Int,
) {
    val (backgroundColor, textColor, icon) =
        when (severity) {
            com.fishit.player.feature.settings.debug.RetentionSeverity.NONE ->
                Triple(
                    MaterialTheme.colorScheme.surfaceVariant,
                    MaterialTheme.colorScheme.onSurfaceVariant,
                    Icons.Default.CheckCircle,
                )
            com.fishit.player.feature.settings.debug.RetentionSeverity.LOW ->
                Triple(
                    MaterialTheme.colorScheme.tertiaryContainer,
                    MaterialTheme.colorScheme.onTertiaryContainer,
                    Icons.Default.Info,
                )
            com.fishit.player.feature.settings.debug.RetentionSeverity.MEDIUM ->
                Triple(
                    MaterialTheme.colorScheme.secondaryContainer,
                    MaterialTheme.colorScheme.onSecondaryContainer,
                    Icons.Default.Warning,
                )
            com.fishit.player.feature.settings.debug.RetentionSeverity.HIGH ->
                Triple(
                    MaterialTheme.colorScheme.errorContainer,
                    MaterialTheme.colorScheme.onErrorContainer,
                    Icons.Default.Error,
                )
        }

    Card(
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = textColor,
                modifier = Modifier.size(24.dp),
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Retained: $retainedCount",
                    style = MaterialTheme.typography.titleSmall,
                    color = textColor,
                )
                Text(
                    text = statusMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = textColor.copy(alpha = 0.8f),
                )
            }
        }
    }
}

/** Memory statistics row. */
@Composable
private fun MemoryStatsRow(memoryStats: com.fishit.player.feature.settings.debug.MemoryStats) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Column {
            Text(
                text = "Memory",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "${memoryStats.usedMemoryMb}MB / ${memoryStats.maxMemoryMb}MB",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = "Usage",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "${memoryStats.usagePercentage}%",
                style = MaterialTheme.typography.bodySmall,
                color =
                    when {
                        memoryStats.usagePercentage > 80 -> MaterialTheme.colorScheme.error
                        memoryStats.usagePercentage > 60 ->
                            MaterialTheme.colorScheme.tertiary
                        else -> MaterialTheme.colorScheme.onSurface
                    },
            )
        }
    }
}
