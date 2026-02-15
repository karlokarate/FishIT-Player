package com.fishit.player.feature.settings

import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fishit.player.core.model.repository.NxCategorySelectionRepository.CategorySelection
import com.fishit.player.core.model.repository.NxCategorySelectionRepository.XtreamCategoryType

/**
 * Category Selection Screen for Xtream sources.
 *
 * Part of Issue #669 - Sync by Category Implementation.
 *
 * **Architecture (AGENTS.md Section 4 compliant):**
 * - Uses [CategorySelection] domain type from repository
 * - DOES NOT import pipeline types - layer boundary respected
 *
 * **Features:**
 * - Tabs for VOD, Series, Live categories
 * - Toggle individual categories on/off
 * - Select All / Deselect All actions
 * - Pull-to-refresh categories from server
 */
@Composable
fun CategorySelectionScreen(
    viewModel: CategorySelectionViewModel = hiltViewModel(),
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize()) {
        // Top Bar
        CategorySelectionTopBar(
            onBack = onBack,
            onRefresh = viewModel::refreshCategories,
            isLoading = state.isLoading,
        )

        // Tabs
        CategoryTabs(
            selectedTab = state.selectedTab,
            onTabSelected = viewModel::setSelectedTab,
        )

        // Content
        CategoryContent(
            state = state,
            onToggle = { categoryId, type, isSelected ->
                viewModel.toggleCategory(categoryId, type, isSelected)
            },
            onSelectAll = viewModel::selectAll,
            onDeselectAll = viewModel::deselectAll,
        )

        // Save & Sync Button
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = {
                viewModel.saveAndSync()
                onBack()
            },
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Icon(
                imageVector = Icons.Default.Save,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Speichern & Synchronisieren")
        }
    }
}

@Composable
private fun CategorySelectionTopBar(
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    isLoading: Boolean,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Zurück",
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                imageVector = Icons.Default.Folder,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Kategorien",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        IconButton(onClick = onRefresh, enabled = !isLoading) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Aktualisieren",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun CategoryTabs(
    selectedTab: CategoryTab,
    onTabSelected: (CategoryTab) -> Unit,
) {
    TabRow(
        selectedTabIndex = selectedTab.ordinal,
        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
    ) {
        CategoryTab.entries.forEach { tab ->
            Tab(
                selected = selectedTab == tab,
                onClick = { onTabSelected(tab) },
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = tab.icon,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(tab.displayName)
                    }
                },
            )
        }
    }
}

private val CategoryTab.icon
    get() =
        when (this) {
            CategoryTab.VOD -> Icons.Default.Movie
            CategoryTab.SERIES -> Icons.Default.Tv
            CategoryTab.LIVE -> Icons.Default.LiveTv
        }

private val CategoryTab.displayName
    get() =
        when (this) {
            CategoryTab.VOD -> "Filme"
            CategoryTab.SERIES -> "Serien"
            CategoryTab.LIVE -> "Live TV"
        }

@Composable
private fun CategoryContent(
    state: CategorySelectionUiState,
    onToggle: (String, XtreamCategoryType, Boolean) -> Unit,
    onSelectAll: (XtreamCategoryType) -> Unit,
    onDeselectAll: (XtreamCategoryType) -> Unit,
) {
    val (categories, categoryType) =
        when (state.selectedTab) {
            CategoryTab.VOD -> state.vodCategories to XtreamCategoryType.VOD
            CategoryTab.SERIES -> state.seriesCategories to XtreamCategoryType.SERIES
            CategoryTab.LIVE -> state.liveCategories to XtreamCategoryType.LIVE
        }

    when {
        state.isLoading && categories.isEmpty() -> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        }
        state.error != null -> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Fehler beim Laden",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = state.error,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        categories.isEmpty() -> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Keine Kategorien verfügbar",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        else -> {
            Column(modifier = Modifier.fillMaxSize()) {
                // Bulk Actions
                BulkActionRow(
                    onSelectAll = { onSelectAll(categoryType) },
                    onDeselectAll = { onDeselectAll(categoryType) },
                    selectedCount = categories.count { it.isSelected },
                    totalCount = categories.size,
                )

                // Category List
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(categories, key = { it.sourceCategoryId }) { category ->
                        CategoryItem(
                            category = category,
                            onToggle = { selected ->
                                onToggle(category.sourceCategoryId, categoryType, selected)
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BulkActionRow(
    onSelectAll: () -> Unit,
    onDeselectAll: () -> Unit,
    selectedCount: Int,
    totalCount: Int,
) {
    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            ),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "$selectedCount von $totalCount ausgewählt",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row {
                TextButton(onClick = onSelectAll) {
                    Icon(
                        imageVector = Icons.Default.SelectAll,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Alle")
                }
                TextButton(onClick = onDeselectAll) {
                    Text("Keine")
                }
            }
        }
    }
}

@Composable
private fun CategoryItem(
    category: CategorySelection,
    onToggle: (Boolean) -> Unit,
) {
    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        colors =
            CardDefaults.cardColors(
                containerColor =
                    if (category.isSelected) {
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                    },
            ),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = { onToggle(!category.isSelected) },
                        )
                    },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = category.categoryName,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Checkbox(
                checked = category.isSelected,
                onCheckedChange = onToggle,
            )
        }
    }
}
