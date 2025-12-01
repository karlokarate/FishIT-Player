package com.chris.m3usuite.logs.ui

import android.app.Application
import android.content.Intent
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.chris.m3usuite.core.logging.UnifiedLogRepository
import com.chris.m3usuite.logs.UnifiedLogViewModel

/**
 * Unified Log Screen - View and manage all application logs.
 *
 * Features:
 * - Scrollable log list with color-coded levels
 * - Filter by level, category, and source
 * - Statistics overview (total, by level)
 * - Export logs via share intent
 * - Clear logs
 * - TV DPAD navigation support
 * - Auto-scroll to latest entries
 * - Crash report display and export
 *
 * This screen is accessible regardless of Telegram enable state,
 * providing unified logging visibility for all app components.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnifiedLogScreen(onBack: () -> Unit = {}) {
    val app = LocalContext.current.applicationContext as Application
    val viewModel: UnifiedLogViewModel = viewModel(factory = UnifiedLogViewModel.factory(app))
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val listState = rememberLazyListState()
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
                title = { Text("Unified Logs") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
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
                                    putExtra(Intent.EXTRA_SUBJECT, "FishIT-Player Logs Export")
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
            // Crash alert card (if pending crash)
            state.lastCrash?.let { crash ->
                CrashAlertCard(
                    crashReport = crash,
                    onDismiss = { viewModel.dismissCrash() },
                    onShare = {
                        viewModel.exportCrashReport()?.let { crashText ->
                            val shareIntent =
                                Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, crashText)
                                    putExtra(Intent.EXTRA_SUBJECT, "FishIT-Player Crash Report")
                                }
                            context.startActivity(Intent.createChooser(shareIntent, "Share Crash Report"))
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Statistics overview
            StatisticsCard(
                statistics = viewModel.getStatistics(),
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Active filters indicator
            if (state.selectedLevel != null || state.selectedCategory != null || state.selectedSource != null) {
                ActiveFiltersCard(
                    selectedLevel = state.selectedLevel,
                    selectedCategory = state.selectedCategory,
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
            currentCategory = state.selectedCategory,
            currentSource = state.selectedSource,
            availableCategories = state.availableCategories,
            availableSources = state.availableSources,
            onDismiss = { showFilterDialog = false },
            onLevelSelected = { level ->
                viewModel.filterByLevel(level)
            },
            onCategorySelected = { category ->
                viewModel.filterByCategory(category)
            },
            onSourceSelected = { source ->
                viewModel.filterBySource(source)
            },
        )
    }
}

/**
 * Crash alert card with dismiss and share options.
 */
@Composable
private fun CrashAlertCard(
    crashReport: com.chris.m3usuite.core.logging.CrashHandler.CrashReport,
    onDismiss: () -> Unit,
    onShare: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                )
                Text(
                    text = "Crash Detected",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "${crashReport.exceptionType}: ${crashReport.message?.take(100) ?: "Unknown"}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                horizontalArrangement = Arrangement.End,
                modifier = Modifier.fillMaxWidth(),
            ) {
                TextButton(onClick = onDismiss) {
                    Text("Dismiss")
                }
                TextButton(onClick = onShare) {
                    Text("Share Report")
                }
            }
        }
    }
}

/**
 * Statistics card showing log counts by level.
 */
@Composable
private fun StatisticsCard(
    statistics: UnifiedLogViewModel.LogStatistics,
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
    selectedLevel: UnifiedLogRepository.Level?,
    selectedCategory: String?,
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
                selectedCategory?.let {
                    Text("Category: $it", style = MaterialTheme.typography.bodySmall)
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
    entry: UnifiedLogRepository.UnifiedLogEntry,
    modifier: Modifier = Modifier,
) {
    val levelColor =
        when (entry.level) {
            UnifiedLogRepository.Level.VERBOSE -> Color.LightGray
            UnifiedLogRepository.Level.DEBUG -> Color.Green
            UnifiedLogRepository.Level.INFO -> Color.Blue
            UnifiedLogRepository.Level.WARN -> Color(0xFFFFA500) // Orange
            UnifiedLogRepository.Level.ERROR -> Color.Red
            UnifiedLogRepository.Level.CRASH -> Color(0xFFB00020) // Dark red
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
            // Header: Time + Level + Category + Source
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
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Surface(
                        color = levelColor.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(4.dp),
                    ) {
                        Text(
                            text = entry.level.name,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
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
                            text = entry.category,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    }
                    Surface(
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        shape = RoundedCornerShape(4.dp),
                    ) {
                        Text(
                            text = entry.source.take(20),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
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
 * Filter dialog for selecting level, category, or source.
 */
@Composable
private fun FilterDialog(
    currentLevel: UnifiedLogRepository.Level?,
    currentCategory: String?,
    currentSource: String?,
    availableCategories: List<String>,
    availableSources: List<String>,
    onDismiss: () -> Unit,
    onLevelSelected: (UnifiedLogRepository.Level?) -> Unit,
    onCategorySelected: (String?) -> Unit,
    onSourceSelected: (String?) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Filter Logs") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // Level filter
                Text("Filter by Level:", style = MaterialTheme.typography.titleSmall)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    FilterChip(
                        selected = currentLevel == null,
                        onClick = { onLevelSelected(null) },
                        label = { Text("All") },
                    )
                    listOf(
                        UnifiedLogRepository.Level.ERROR,
                        UnifiedLogRepository.Level.WARN,
                        UnifiedLogRepository.Level.INFO,
                    ).forEach { level ->
                        FilterChip(
                            selected = currentLevel == level,
                            onClick = { onLevelSelected(level) },
                            label = { Text(level.name) },
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Category filter
                if (availableCategories.isNotEmpty()) {
                    Text("Filter by Category:", style = MaterialTheme.typography.titleSmall)
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        FilterChip(
                            selected = currentCategory == null,
                            onClick = { onCategorySelected(null) },
                            label = { Text("All") },
                        )
                        availableCategories.take(6).forEach { category ->
                            FilterChip(
                                selected = currentCategory == category,
                                onClick = { onCategorySelected(category) },
                                label = { Text(category) },
                            )
                        }
                    }
                }

                // Source filter (limited to 6 most common)
                if (availableSources.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Filter by Source:", style = MaterialTheme.typography.titleSmall)
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        FilterChip(
                            selected = currentSource == null,
                            onClick = { onSourceSelected(null) },
                            label = { Text("All") },
                        )
                        availableSources.take(4).forEach { source ->
                            FilterChip(
                                selected = currentSource == source,
                                onClick = { onSourceSelected(source) },
                                label = { Text(source.take(20)) },
                            )
                        }
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
