package com.fishit.player.feature.settings

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
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
 * - Recent logs
 * - Debug playback (test player)
 */
@Composable
fun DebugScreen(
    onBack: () -> Unit,
    onDebugPlayback: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: DebugViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top bar
            DebugTopBar(
                onBack = onBack,
                onRefresh = viewModel::refreshInfo
            )

            // Content
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item { Spacer(modifier = Modifier.height(8.dp)) }

                // Debug Playback Section (test player)
                item {
                    DebugSection(title = "Playback Test", icon = Icons.Default.BugReport) {
                        Text(
                            text = "Test the internal player with a sample video stream (Big Buck Bunny).",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = onDebugPlayback,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Icons.Default.BugReport,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
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

                // Connection Status Section
                item {
                    DebugSection(title = "Connections", icon = Icons.Default.Cloud) {
                        ConnectionRow(
                            name = "Telegram",
                            icon = Icons.Default.Send,
                            isConnected = state.telegramConnected,
                            details = state.telegramUser
                        )
                        ConnectionRow(
                            name = "Xtream",
                            icon = Icons.Default.Cloud,
                            isConnected = state.xtreamConnected,
                            details = state.xtreamServer
                        )
                    }
                }

                // === Manual Catalog Sync ===
                item {
                    DebugSection(title = "Manual Sync", icon = Icons.Default.Refresh) {
                        Text(
                            text = "Manually trigger catalog sync from connected sources.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        // Telegram Sync Button
                        SyncButton(
                            name = "Telegram",
                            icon = Icons.Default.Send,
                            isSyncing = state.isSyncingTelegram,
                            isEnabled = state.telegramConnected,
                            progress = state.telegramSyncProgress,
                            onClick = viewModel::syncTelegram
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        // Xtream Sync Button
                        SyncButton(
                            name = "Xtream",
                            icon = Icons.Default.Cloud,
                            isSyncing = state.isSyncingXtream,
                            isEnabled = state.xtreamConnected,
                            progress = state.xtreamSyncProgress,
                            onClick = viewModel::syncXtream
                        )
                    }
                }

                // Cache Section
                item {
                    DebugSection(title = "Cache", icon = Icons.Default.Storage) {
                        CacheRow(
                            name = "Telegram Cache",
                            size = state.telegramCacheSize,
                            onClear = viewModel::clearTelegramCache,
                            isClearing = state.isClearingCache
                        )
                        CacheRow(
                            name = "Image Cache",
                            size = state.imageCacheSize,
                            onClear = viewModel::clearImageCache,
                            isClearing = state.isClearingCache
                        )
                        CacheRow(
                            name = "Database",
                            size = state.dbSize,
                            onClear = null, // DB can't be cleared
                            isClearing = false
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedButton(
                            onClick = viewModel::clearAllCaches,
                            enabled = !state.isClearingCache,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (state.isClearingCache) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp
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

                // Recent Logs Section
                item {
                    DebugSection(title = "Recent Logs", icon = Icons.Default.BugReport) {
                        // No content in header
                    }
                }

                // Log entries
                items(state.recentLogs) { log ->
                    LogEntryRow(log = log)
                }

                // Load more button
                if (state.recentLogs.isNotEmpty()) {
                    item {
                        TextButton(
                            onClick = viewModel::loadMoreLogs,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (state.isLoadingLogs) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp
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
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp),
                action = {
                    TextButton(onClick = viewModel::dismissActionResult) {
                        Text("Dismiss")
                    }
                }
            ) {
                Text(result)
            }
        }
    }
}

@Composable
private fun DebugTopBar(
    onBack: () -> Unit,
    onRefresh: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = MaterialTheme.colorScheme.onBackground
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        Icon(
            Icons.Default.BugReport,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(28.dp)
        )

        Spacer(modifier = Modifier.width(8.dp))

        Text(
            text = "Debug & Diagnostics",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.weight(1f))

        IconButton(onClick = onRefresh) {
            Icon(
                Icons.Default.Refresh,
                contentDescription = "Refresh",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun DebugSection(
    title: String,
    icon: ImageVector,
    content: @Composable () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            content()
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun ConnectionRow(
    name: String,
    icon: ImageVector,
    isConnected: Boolean,
    details: String?
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (isConnected) Color(0xFF4CAF50) else Color(0xFFBDBDBD),
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = name,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        if (isConnected) {
            details?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Icon(
                Icons.Default.Check,
                contentDescription = "Connected",
                tint = Color(0xFF4CAF50),
                modifier = Modifier.size(16.dp)
            )
        } else {
            Icon(
                Icons.Default.Close,
                contentDescription = "Disconnected",
                tint = Color(0xFFBDBDBD),
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
private fun CacheRow(
    name: String,
    size: String,
    onClear: (() -> Unit)?,
    isClearing: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = name,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = size,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (onClear != null) {
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(
                onClick = onClear,
                enabled = !isClearing,
                modifier = Modifier.size(32.dp)
            ) {
                if (isClearing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        Icons.Default.Clear,
                        contentDescription = "Clear",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun StatsRow(name: String, count: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = name,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun LogEntryRow(log: LogEntry) {
    val timeFormatter = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    val levelColor = when (log.level) {
        LogLevel.DEBUG -> Color(0xFF9E9E9E)
        LogLevel.INFO -> Color(0xFF2196F3)
        LogLevel.WARN -> Color(0xFFFF9800)
        LogLevel.ERROR -> Color(0xFFF44336)
    }

    val levelIcon = when (log.level) {
        LogLevel.DEBUG -> Icons.Default.BugReport
        LogLevel.INFO -> Icons.Default.Info
        LogLevel.WARN -> Icons.Default.Warning
        LogLevel.ERROR -> Icons.Default.Close
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            imageVector = levelIcon,
            contentDescription = log.level.name,
            tint = levelColor,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row {
                Text(
                    text = timeFormatter.format(Date(log.timestamp)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = log.tag,
                    style = MaterialTheme.typography.labelSmall,
                    color = levelColor
                )
            }
            Text(
                text = log.message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

/**
 * Sync button with progress indicator.
 * 
 * Shows:
 * - Normal state: Button with icon and label
 * - Syncing state: Progress indicator with item counts
 * - Disabled state: Grayed out when source not connected
 * 
 * Click while syncing cancels the sync.
 */
@Composable
private fun SyncButton(
    name: String,
    icon: ImageVector,
    isSyncing: Boolean,
    isEnabled: Boolean,
    progress: SyncProgress?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val buttonText = when {
        isSyncing -> "Cancel $name Sync"
        else -> "Sync $name"
    }
    
    val buttonColors = if (isSyncing) {
        androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
            contentColor = MaterialTheme.colorScheme.error
        )
    } else {
        androidx.compose.material3.ButtonDefaults.buttonColors()
    }

    Column(modifier = modifier.fillMaxWidth()) {
        if (isSyncing) {
            OutlinedButton(
                onClick = onClick,
                modifier = Modifier.fillMaxWidth(),
                colors = buttonColors,
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(buttonText)
            }
            
            // Progress details
            progress?.let { p ->
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics {
                            contentDescription = "Syncing ${p.currentPhase ?: "in progress"}: ${p.itemsPersisted} of ${p.itemsDiscovered} items"
                        },
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = p.currentPhase ?: "Syncing...",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${p.itemsPersisted}/${p.itemsDiscovered} items",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        } else {
            Button(
                onClick = onClick,
                enabled = isEnabled,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = "$name sync",
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(buttonText)
            }
            
            if (!isEnabled) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "$name not connected",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}
