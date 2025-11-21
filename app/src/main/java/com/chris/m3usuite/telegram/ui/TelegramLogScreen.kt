package com.chris.m3usuite.telegram.ui

import android.content.Intent
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.chris.m3usuite.telegram.logging.TgLogEntry

/**
 * Telegram Log Screen - View and manage Telegram operation logs.
 *
 * Features:
 * - Scrollable log list with color-coded levels
 * - Filter by level and source
 * - Statistics overview
 * - Export logs via share intent
 * - Clear logs
 * - TV DPAD navigation support
 * - Auto-scroll to latest entries
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TelegramLogScreen(
    viewModel: TelegramLogViewModel = viewModel(),
    onBack: () -> Unit = {},
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    var showFilterDialog by remember { mutableStateOf(false) }

    // Auto-scroll to latest entry when enabled
    LaunchedEffect(state.filteredEntries.size, state.isAutoScrollEnabled) {
        if (state.isAutoScrollEnabled && state.filteredEntries.isNotEmpty()) {
            listState.animateScrollToItem(state.filteredEntries.lastIndex)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Telegram Logs") },
                actions = {
                    // Filter button
                    IconButton(onClick = { showFilterDialog = true }) {
                        Icon(Icons.Default.FilterList, contentDescription = "Filter")
                    }

                    // Share button
                    IconButton(
                        onClick = {
                            val logText = viewModel.exportLogs()
                            val shareIntent =
                                Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, logText)
                                    putExtra(Intent.EXTRA_SUBJECT, "Telegram Logs Export")
                                }
                            context.startActivity(Intent.createChooser(shareIntent, "Export Logs"))
                        },
                    ) {
                        Icon(Icons.Default.Share, contentDescription = "Share")
                    }

                    // Clear button
                    IconButton(onClick = { viewModel.clearLogs() }) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear")
                    }
                },
            )
        },
    ) { paddingValues ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp),
        ) {
            // Statistics overview
            LogStatisticsCard(
                statistics = viewModel.getStatistics(),
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Active filters indicator
            if (state.selectedLevel != null || state.selectedSource != null) {
                ActiveFiltersCard(
                    selectedLevel = state.selectedLevel,
                    selectedSource = state.selectedSource,
                    onClearFilters = { viewModel.clearFilters() },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Log entries list
            if (state.filteredEntries.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "No log entries",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(state.filteredEntries, key = { "${it.timestamp}_${it.hashCode()}" }) { entry ->
                        LogEntryCard(
                            entry = entry,
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .focusable(),
                        )
                    }
                }
            }
        }
    }

    // Filter dialog
    if (showFilterDialog) {
        FilterDialog(
            currentLevel = state.selectedLevel,
            currentSource = state.selectedSource,
            availableSources = state.availableSources,
            onDismiss = { showFilterDialog = false },
            onLevelSelected = { level ->
                viewModel.filterByLevel(level)
                showFilterDialog = false
            },
            onSourceSelected = { source ->
                viewModel.filterBySource(source)
                showFilterDialog = false
            },
        )
    }
}

/**
 * Statistics card showing log counts by level.
 */
@Composable
private fun LogStatisticsCard(
    statistics: TelegramLogViewModel.LogStatistics,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
        ) {
            Text(
                text = "Statistics",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                StatItem("Total", statistics.total, Color.Gray)
                StatItem("Error", statistics.error, Color.Red)
                StatItem("Warn", statistics.warn, Color(0xFFFFA500))
                StatItem("Info", statistics.info, Color.Blue)
                StatItem("Debug", statistics.debug, Color.Green)
                StatItem("Verbose", statistics.verbose, Color.LightGray)
            }
            if (statistics.filtered < statistics.total) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Showing ${statistics.filtered} of ${statistics.total} entries",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * Individual statistic item.
 */
@Composable
private fun StatItem(
    label: String,
    count: Int,
    color: Color,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.titleLarge,
            color = color,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Active filters indicator card.
 */
@Composable
private fun ActiveFiltersCard(
    selectedLevel: TgLogEntry.LogLevel?,
    selectedSource: String?,
    onClearFilters: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    text = "Active Filters",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                )
                selectedLevel?.let {
                    Text("Level: ${it.name}", style = MaterialTheme.typography.bodySmall)
                }
                selectedSource?.let {
                    Text("Source: $it", style = MaterialTheme.typography.bodySmall)
                }
            }
            TextButton(onClick = onClearFilters) {
                Text("Clear")
            }
        }
    }
}

/**
 * Individual log entry card.
 */
@Composable
private fun LogEntryCard(
    entry: TgLogEntry,
    modifier: Modifier = Modifier,
) {
    val levelColor =
        when (entry.level) {
            TgLogEntry.LogLevel.VERBOSE -> Color.LightGray
            TgLogEntry.LogLevel.DEBUG -> Color.Green
            TgLogEntry.LogLevel.INFO -> Color.Blue
            TgLogEntry.LogLevel.WARN -> Color(0xFFFFA500) // Orange
            TgLogEntry.LogLevel.ERROR -> Color.Red
        }

    Card(
        modifier =
            modifier
                .border(2.dp, levelColor, RoundedCornerShape(8.dp)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
        ) {
            // Header: Time + Level + Source
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = entry.formattedTime(),
                    style = MaterialTheme.typography.labelMedium,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(
                        color = levelColor.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(4.dp),
                    ) {
                        Text(
                            text = entry.level.name,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = levelColor,
                        )
                    }
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = RoundedCornerShape(4.dp),
                    ) {
                        Text(
                            text = entry.source,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Message
            Text(
                text = entry.message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )

            // Details (if present)
            entry.formattedDetails()?.let { details ->
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = details,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                )
            }
        }
    }
}

/**
 * Filter dialog for selecting level or source.
 */
@Composable
private fun FilterDialog(
    currentLevel: TgLogEntry.LogLevel?,
    currentSource: String?,
    availableSources: List<String>,
    onDismiss: () -> Unit,
    onLevelSelected: (TgLogEntry.LogLevel?) -> Unit,
    onSourceSelected: (String?) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Filter Logs") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Filter by Level:", style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = currentLevel == null,
                        onClick = { onLevelSelected(null) },
                        label = { Text("All") },
                    )
                    TgLogEntry.LogLevel.entries.forEach { level ->
                        FilterChip(
                            selected = currentLevel == level,
                            onClick = { onLevelSelected(level) },
                            label = { Text(level.name) },
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text("Filter by Source:", style = MaterialTheme.typography.titleSmall)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    FilterChip(
                        selected = currentSource == null,
                        onClick = { onSourceSelected(null) },
                        label = { Text("All") },
                    )
                    availableSources.forEach { source ->
                        FilterChip(
                            selected = currentSource == source,
                            onClick = { onSourceSelected(source) },
                            label = { Text(source) },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        },
    )
}
