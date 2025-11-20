package com.chris.m3usuite.telegram.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.chris.m3usuite.telegram.logging.LogLevel
import android.content.Intent

/**
 * Telegram Log Screen - displays all Telegram logging events.
 * Supports filtering by level and source, and exporting logs.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TelegramLogScreen(
    onBack: () -> Unit,
    viewModel: TelegramLogViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    var showFilterDialog by remember { mutableStateOf(false) }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Telegram Log") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    // Filter button
                    IconButton(onClick = { showFilterDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.FilterList,
                            contentDescription = "Filter"
                        )
                    }
                    
                    // Clear logs button
                    IconButton(onClick = { viewModel.clearLogs() }) {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = "Clear"
                        )
                    }
                    
                    // Share/Export button
                    IconButton(onClick = {
                        val logText = viewModel.exportLogsAsText()
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, logText)
                            putExtra(Intent.EXTRA_SUBJECT, "Telegram Logs")
                        }
                        context.startActivity(Intent.createChooser(intent, "Share Logs"))
                    }) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Share"
                        )
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (state.entries.isEmpty()) {
                // Empty state
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "No log entries",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Telegram events will appear here",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                // Log entries list
                val listState = rememberLazyListState()
                
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(8.dp)
                ) {
                    // Active filters info
                    if (state.filterLevel != null || state.filterSource != null) {
                        item {
                            FilterChip(
                                selected = true,
                                onClick = { viewModel.clearFilters() },
                                label = {
                                    Text(
                                        buildString {
                                            append("Filtered: ")
                                            if (state.filterLevel != null) {
                                                append("Level ≥ ${state.filterLevel}")
                                            }
                                            if (state.filterSource != null) {
                                                if (state.filterLevel != null) append(", ")
                                                append("Source: ${state.filterSource}")
                                            }
                                        }
                                    )
                                },
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                    
                    items(state.entries, key = { it.timestamp }) { entry ->
                        LogEntryItem(entry = entry)
                    }
                }
            }
        }
    }
    
    // Filter dialog
    if (showFilterDialog) {
        FilterDialog(
            currentLevel = state.filterLevel,
            currentSource = state.filterSource,
            onDismiss = { showFilterDialog = false },
            onApply = { level, source ->
                viewModel.setLevelFilter(level)
                viewModel.setSourceFilter(source)
                showFilterDialog = false
            }
        )
    }
}

@Composable
private fun LogEntryItem(entry: com.chris.m3usuite.telegram.logging.TgLogEntry) {
    val levelColor = when (entry.level) {
        LogLevel.ERROR -> Color.Red
        LogLevel.WARN -> Color(0xFFFFA500) // Orange
        LogLevel.INFO -> Color.Blue
        LogLevel.DEBUG -> Color.Gray
    }
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            // Header: timestamp, level, source
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = entry.formattedTimestamp(),
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = entry.level.name,
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = levelColor,
                        modifier = Modifier
                            .background(levelColor.copy(alpha = 0.1f), MaterialTheme.shapes.small)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                    
                    Text(
                        text = entry.source,
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Message
            Text(
                text = entry.message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            
            // Details (if present)
            if (entry.details != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = entry.details,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            // Throwable (if present)
            if (entry.throwable != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Error: ${entry.throwable.message}",
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = Color.Red.copy(alpha = 0.8f)
                )
            }
        }
    }
}

@Composable
private fun FilterDialog(
    currentLevel: LogLevel?,
    currentSource: String?,
    onDismiss: () -> Unit,
    onApply: (LogLevel?, String?) -> Unit
) {
    var selectedLevel by remember { mutableStateOf(currentLevel) }
    var sourceText by remember { mutableStateOf(currentSource ?: "") }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Filter Logs") },
        text = {
            Column {
                Text("Minimum Level:", style = MaterialTheme.typography.labelMedium)
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    FilterChip(
                        selected = selectedLevel == null,
                        onClick = { selectedLevel = null },
                        label = { Text("All") }
                    )
                    LogLevel.entries.forEach { level ->
                        FilterChip(
                            selected = selectedLevel == level,
                            onClick = { selectedLevel = level },
                            label = { Text(level.name) }
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                OutlinedTextField(
                    value = sourceText,
                    onValueChange = { sourceText = it },
                    label = { Text("Source (optional)") },
                    placeholder = { Text("e.g. ServiceClient") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                onApply(selectedLevel, sourceText.takeIf { it.isNotBlank() })
            }) {
                Text("Apply")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
