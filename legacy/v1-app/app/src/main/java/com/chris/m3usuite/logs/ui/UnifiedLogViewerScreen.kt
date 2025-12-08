package com.chris.m3usuite.logs.ui

import android.content.Intent
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
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
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.chris.m3usuite.core.logging.UnifiedLog
import kotlinx.coroutines.launch

/**
 * Unified Log Viewer Screen - View and manage all application logs.
 *
 * Features:
 * - Scrollable log list with color-coded levels
 * - Filter by level (clickable statistics)
 * - Filter by source category (Playback, Telegram, UI, etc.)
 * - Search functionality
 * - Statistics overview with clickable counts
 * - Export logs via share intent
 * - Save to file option
 * - File buffer for full session export
 * - Clear logs
 * - Auto-scroll to latest entries
 * - DPAD/TV navigation support
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnifiedLogViewerScreen(
    viewModel: UnifiedLogViewModel = viewModel(),
    onBack: () -> Unit = {},
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    var showFilterSheet by remember { mutableStateOf(false) }
    var showExportMenu by remember { mutableStateOf(false) }
    var showClearConfirmDialog by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    // Auto-scroll to latest entry when enabled
    LaunchedEffect(state.filteredEntries.size, state.isAutoScrollEnabled) {
        if (state.isAutoScrollEnabled && state.filteredEntries.isNotEmpty()) {
            listState.animateScrollToItem(state.filteredEntries.lastIndex)
        }
    }

    // Scroll to first error when clicking on error count
    LaunchedEffect(state.scrollToFirstErrorTrigger) {
        if (state.scrollToFirstErrorTrigger > 0) {
            val firstErrorIndex = state.filteredEntries.indexOfFirst {
                it.level == UnifiedLog.Level.ERROR
            }
            if (firstErrorIndex >= 0) {
                listState.animateScrollToItem(firstErrorIndex)
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("App Logs") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück")
                    }
                },
                actions = {
                    // Auto-scroll toggle
                    IconButton(onClick = { viewModel.toggleAutoScroll() }) {
                        Icon(
                            if (state.isAutoScrollEnabled) Icons.Default.VerticalAlignBottom
                            else Icons.Default.VerticalAlignCenter,
                            contentDescription = if (state.isAutoScrollEnabled) "Auto-Scroll aktiv" else "Auto-Scroll inaktiv",
                            tint = if (state.isAutoScrollEnabled) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    // Filter button
                    IconButton(onClick = { showFilterSheet = true }) {
                        BadgedBox(
                            badge = {
                                if (state.hasActiveFilters) {
                                    Badge { Text("!") }
                                }
                            }
                        ) {
                            Icon(Icons.Default.FilterList, contentDescription = "Filter")
                        }
                    }

                    // Export menu
                    Box {
                        IconButton(onClick = { showExportMenu = true }) {
                            Icon(Icons.Default.Share, contentDescription = "Exportieren")
                        }
                        DropdownMenu(
                            expanded = showExportMenu,
                            onDismissRequest = { showExportMenu = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text("Teilen (gefiltert)") },
                                onClick = {
                                    showExportMenu = false
                                    val text = viewModel.exportFiltered()
                                    val intent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_TEXT, text)
                                        putExtra(Intent.EXTRA_SUBJECT, "FishIT App Logs")
                                    }
                                    context.startActivity(Intent.createChooser(intent, "Logs teilen"))
                                },
                                leadingIcon = { Icon(Icons.Default.Share, null) },
                            )
                            DropdownMenuItem(
                                text = { Text("Als Datei speichern") },
                                onClick = {
                                    showExportMenu = false
                                    val file = viewModel.saveToFile()
                                    if (file != null) {
                                        coroutineScope.launch {
                                            snackbarHostState.showSnackbar(
                                                message = "Gespeichert: ${file.name}",
                                                actionLabel = "Teilen",
                                            ).let { result ->
                                                if (result == SnackbarResult.ActionPerformed) {
                                                    val uri = FileProvider.getUriForFile(
                                                        context,
                                                        "${context.packageName}.fileprovider",
                                                        file,
                                                    )
                                                    val intent = Intent(Intent.ACTION_SEND).apply {
                                                        type = "text/plain"
                                                        putExtra(Intent.EXTRA_STREAM, uri)
                                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                    }
                                                    context.startActivity(Intent.createChooser(intent, "Datei teilen"))
                                                }
                                            }
                                        }
                                    }
                                },
                                leadingIcon = { Icon(Icons.Default.Save, null) },
                            )
                            if (state.hasFileBuffer) {
                                DropdownMenuItem(
                                    text = { Text("Volle Session teilen") },
                                    onClick = {
                                        showExportMenu = false
                                        val text = viewModel.exportFullSession()
                                        if (text != null) {
                                            val intent = Intent(Intent.ACTION_SEND).apply {
                                                type = "text/plain"
                                                putExtra(Intent.EXTRA_TEXT, text)
                                                putExtra(Intent.EXTRA_SUBJECT, "FishIT Full Session Log")
                                            }
                                            context.startActivity(Intent.createChooser(intent, "Session teilen"))
                                        } else {
                                            coroutineScope.launch {
                                                snackbarHostState.showSnackbar("Keine Session-Datei vorhanden")
                                            }
                                        }
                                    },
                                    leadingIcon = { Icon(Icons.Default.History, null) },
                                )
                            }
                        }
                    }

                    // Clear button
                    IconButton(onClick = { showClearConfirmDialog = true }) {
                        Icon(Icons.Default.Clear, contentDescription = "Löschen")
                    }
                },
            )
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
        ) {
            Spacer(Modifier.height(8.dp))

            // Statistics overview (clickable)
            LogStatisticsCard(
                statistics = state.statistics,
                onLevelClick = { level ->
                    viewModel.filterToLevel(level)
                },
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(12.dp))

            // Quick category filter chips
            QuickCategoryFilters(
                enabledCategories = state.filter.enabledCategories,
                onToggleCategory = { viewModel.toggleCategory(it) },
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(12.dp))

            // Active filters indicator
            if (state.hasActiveFilters) {
                ActiveFiltersCard(
                    filter = state.filter,
                    onClearFilters = { viewModel.resetFilters() },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
            }

            // Search bar
            OutlinedTextField(
                value = state.filter.searchQuery,
                onValueChange = { viewModel.setSearchQuery(it) },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Suchen...") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                trailingIcon = {
                    if (state.filter.searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.setSearchQuery("") }) {
                            Icon(Icons.Default.Clear, "Suche löschen")
                        }
                    }
                },
                singleLine = true,
            )

            Spacer(Modifier.height(12.dp))

            // Log entries list
            if (state.filteredEntries.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = if (state.statistics.total == 0) "Keine Log-Einträge"
                            else "Keine Einträge für aktuelle Filter",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (state.hasActiveFilters) {
                            Spacer(Modifier.height(8.dp))
                            TextButton(onClick = { viewModel.resetFilters() }) {
                                Text("Filter zurücksetzen")
                            }
                        }
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 16.dp),
                ) {
                    items(
                        items = state.filteredEntries,
                        key = { it.id },
                    ) { entry ->
                        LogEntryCard(
                            entry = entry,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }

    // Filter bottom sheet
    if (showFilterSheet) {
        FilterBottomSheet(
            filter = state.filter,
            onDismiss = { showFilterSheet = false },
            onToggleLevel = { viewModel.toggleLevel(it) },
            onToggleCategory = { viewModel.toggleCategory(it) },
            onResetFilters = { viewModel.resetFilters() },
            onToggleFileBuffer = { viewModel.toggleFileBuffer() },
            fileBufferEnabled = state.hasFileBuffer,
        )
    }

    // Clear confirmation dialog
    if (showClearConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showClearConfirmDialog = false },
            title = { Text("Logs löschen?") },
            text = { Text("Alle ${state.statistics.total} Log-Einträge werden gelöscht. Diese Aktion kann nicht rückgängig gemacht werden.") },
            confirmButton = {
                Button(
                    onClick = {
                        showClearConfirmDialog = false
                        viewModel.clearLogs()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                    ),
                ) {
                    Text("Löschen")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirmDialog = false }) {
                    Text("Abbrechen")
                }
            },
        )
    }
}

/**
 * Statistics card with clickable counts.
 */
@Composable
private fun LogStatisticsCard(
    statistics: UnifiedLog.Statistics,
    onLevelClick: (UnifiedLog.Level) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Statistiken",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                StatItem("Gesamt", statistics.total, Color.Gray, null)
                StatItem("Error", statistics.error, Color(0xFFF44336), { onLevelClick(UnifiedLog.Level.ERROR) })
                StatItem("Warn", statistics.warn, Color(0xFFFF9800), { onLevelClick(UnifiedLog.Level.WARN) })
                StatItem("Info", statistics.info, Color(0xFF2196F3), { onLevelClick(UnifiedLog.Level.INFO) })
                StatItem("Debug", statistics.debug, Color(0xFF4CAF50), { onLevelClick(UnifiedLog.Level.DEBUG) })
            }
            if (statistics.filtered < statistics.total) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Zeige ${statistics.filtered} von ${statistics.total} Einträgen",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun StatItem(
    label: String,
    count: Int,
    color: Color,
    onClick: (() -> Unit)?,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier,
    ) {
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
 * Quick category filter chips.
 */
@Composable
private fun QuickCategoryFilters(
    enabledCategories: Set<UnifiedLog.SourceCategory>,
    onToggleCategory: (UnifiedLog.SourceCategory) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        UnifiedLog.SourceCategory.entries.filter { it != UnifiedLog.SourceCategory.OTHER }.forEach { category ->
            FilterChip(
                selected = category in enabledCategories,
                onClick = { onToggleCategory(category) },
                label = { Text(category.displayName) },
            )
        }
    }
}

/**
 * Active filters indicator card.
 */
@Composable
private fun ActiveFiltersCard(
    filter: UnifiedLog.FilterState,
    onClearFilters: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Aktive Filter",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                )
                if (filter.enabledLevels.size < UnifiedLog.Level.entries.size) {
                    Text(
                        "Level: ${filter.enabledLevels.joinToString(", ") { it.name }}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                if (filter.enabledCategories.size < UnifiedLog.SourceCategory.entries.size) {
                    Text(
                        "Kategorien: ${filter.enabledCategories.size}/${UnifiedLog.SourceCategory.entries.size}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                if (filter.searchQuery.isNotEmpty()) {
                    Text(
                        "Suche: \"${filter.searchQuery}\"",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            TextButton(onClick = onClearFilters) {
                Text("Zurücksetzen")
            }
        }
    }
}

/**
 * Individual log entry card.
 */
@Composable
private fun LogEntryCard(
    entry: UnifiedLog.Entry,
    modifier: Modifier = Modifier,
) {
    val levelColor = Color(entry.level.color)

    Card(
        modifier = modifier.border(2.dp, levelColor, RoundedCornerShape(8.dp)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Header: Time + Level + Source + Category
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
                    // Level badge
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
                    // Source badge
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

            Spacer(Modifier.height(8.dp))

            // Message
            Text(
                text = entry.message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )

            // Details (if present)
            entry.formattedDetails()?.let { details ->
                Spacer(Modifier.height(4.dp))
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
 * Filter bottom sheet for detailed filter options.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterBottomSheet(
    filter: UnifiedLog.FilterState,
    onDismiss: () -> Unit,
    onToggleLevel: (UnifiedLog.Level) -> Unit,
    onToggleCategory: (UnifiedLog.SourceCategory) -> Unit,
    onResetFilters: () -> Unit,
    onToggleFileBuffer: () -> Unit,
    fileBufferEnabled: Boolean,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Filter",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                TextButton(onClick = onResetFilters) {
                    Text("Zurücksetzen")
                }
            }

            Spacer(Modifier.height(16.dp))

            // Level filters
            Text(
                text = "Log Level",
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                UnifiedLog.Level.entries.forEach { level ->
                    FilterChip(
                        selected = level in filter.enabledLevels,
                        onClick = { onToggleLevel(level) },
                        label = { Text(level.name) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(level.color).copy(alpha = 0.3f),
                        ),
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // Category filters
            Text(
                text = "Kategorien",
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                UnifiedLog.SourceCategory.entries.chunked(3).forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        row.forEach { category ->
                            FilterChip(
                                selected = category in filter.enabledCategories,
                                onClick = { onToggleCategory(category) },
                                label = { Text(category.displayName) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                        // Fill empty slots
                        repeat(3 - row.size) {
                            Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))

            // File buffer toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Session-Datei aktivieren",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        text = "Speichert alle Logs in Datei für vollständigen Export",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = fileBufferEnabled,
                    onCheckedChange = { onToggleFileBuffer() },
                )
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}
