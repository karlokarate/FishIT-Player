package com.fishit.player.feature.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Theaters
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import com.fishit.player.core.library.domain.LibraryCategory
import com.fishit.player.core.library.domain.LibraryFilterConfig
import com.fishit.player.core.library.domain.LibraryMediaItem
import com.fishit.player.core.library.domain.LibrarySortDirection
import com.fishit.player.core.library.domain.LibrarySortField
import com.fishit.player.core.library.domain.LibrarySortOption
import com.fishit.player.core.model.ImageRef
import com.fishit.player.core.model.MediaType
import com.fishit.player.core.ui.layout.SortFilterBar
import com.fishit.player.core.ui.layout.UiFilterConfig
import com.fishit.player.core.ui.layout.UiSortDirection
import com.fishit.player.core.ui.layout.UiSortField
import com.fishit.player.core.ui.layout.UiSortOption

/**
 * Library Screen - Browse VOD and Series content.
 *
 * Features:
 * - Tab switching between VOD and Series
 * - Category filtering
 * - Search functionality
 * - Grid display of media items
 *
 * @param onItemClick Callback when a media item is clicked
 * @param viewModel ViewModel for state management
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    onItemClick: (LibraryMediaItem) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Library") },
                actions = {
                    if (!state.isSearchActive) {
                        IconButton(onClick = { viewModel.startSearch() }) {
                            Icon(Icons.Default.Search, contentDescription = "Search")
                        }
                    }
                },
            )
        },
        modifier = modifier,
    ) { paddingValues ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
        ) {
            // Search bar (when active)
            if (state.isSearchActive) {
                SearchBar(
                    query = state.searchQuery,
                    onQueryChange = { viewModel.search(it) },
                    onClose = { viewModel.cancelSearch() },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            } else {
                // Tabs
                TabRow(
                    selectedTabIndex = if (state.currentTab == LibraryTab.VOD) 0 else 1,
                ) {
                    Tab(
                        selected = state.currentTab == LibraryTab.VOD,
                        onClick = { viewModel.selectTab(LibraryTab.VOD) },
                        text = { Text("Movies") },
                        icon = { Icon(Icons.Default.Theaters, contentDescription = null) },
                    )
                    Tab(
                        selected = state.currentTab == LibraryTab.SERIES,
                        onClick = { viewModel.selectTab(LibraryTab.SERIES) },
                        text = { Text("Series") },
                        icon = { Icon(Icons.Default.PlayCircle, contentDescription = null) },
                    )
                }

                // Category filter
                if (state.currentCategories.isNotEmpty()) {
                    CategoryFilterRow(
                        categories = state.currentCategories,
                        selectedCategory = state.currentSelectedCategory,
                        onCategorySelected = { viewModel.selectCategory(it) },
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }

                // Sort & Filter bar
                SortFilterBar(
                    currentSort = state.currentSortOption.toUiSortOption(),
                    currentFilter = state.currentFilterConfig.toUiFilterConfig(),
                    availableGenres = state.availableGenres.toList(),
                    yearRange = state.yearRange?.let { it.first..it.second } ?: (1900..2025),
                    onSortChanged = { uiSort ->
                        viewModel.updateSort(uiSort.toLibrarySortOption())
                    },
                    onFilterChanged = { uiFilter ->
                        viewModel.updateFilter(uiFilter.toLibraryFilterConfig())
                    },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }

            // Content
            when {
                state.isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                }

                state.currentItems.isEmpty() -> {
                    EmptyState(
                        isSearchActive = state.isSearchActive,
                        currentTab = state.currentTab,
                    )
                }

                else -> {
                    MediaGrid(
                        items = state.currentItems,
                        onItemClick = onItemClick,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier.fillMaxWidth(),
        placeholder = { Text("Search movies and series...") },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        trailingIcon = {
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, contentDescription = "Close search")
            }
        },
        singleLine = true,
    )
}

@Composable
private fun CategoryFilterRow(
    categories: List<LibraryCategory>,
    selectedCategory: String?,
    onCategorySelected: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedCategoryName = categories.find { it.id == selectedCategory }?.name ?: "All"

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Category:",
            style = MaterialTheme.typography.bodyMedium,
        )

        Box {
            FilterChip(
                selected = selectedCategory != null,
                onClick = { expanded = true },
                label = { Text(selectedCategoryName) },
            )

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                DropdownMenuItem(
                    text = { Text("All") },
                    onClick = {
                        onCategorySelected(null)
                        expanded = false
                    },
                )
                categories.forEach { category ->
                    DropdownMenuItem(
                        text = { Text("${category.name} (${category.itemCount})") },
                        onClick = {
                            onCategorySelected(category.id)
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun MediaGrid(
    items: List<LibraryMediaItem>,
    onItemClick: (LibraryMediaItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 120.dp),
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(items, key = { it.id }) { item ->
            MediaCard(
                item = item,
                onClick = { onItemClick(item) },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MediaCard(
    item: LibraryMediaItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onClick,
        modifier = modifier.width(120.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
    ) {
        Column {
            // Poster
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(180.dp),
            ) {
                val posterModel =
                    when (val poster = item.poster) {
                        is ImageRef.Http -> poster.url
                        is ImageRef.LocalFile -> poster.path
                        else -> null
                    }
                if (posterModel != null) {
                    AsyncImage(
                        model = posterModel,
                        contentDescription = item.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector =
                                if (item.mediaType == MediaType.MOVIE) {
                                    Icons.Default.Theaters
                                } else {
                                    Icons.Default.PlayCircle
                                },
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                // Rating badge
                item.rating?.let { rating ->
                    Text(
                        text = String.format("%.1f", rating),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier =
                            Modifier
                                .align(Alignment.TopEnd)
                                .padding(4.dp),
                    )
                }
            }

            // Title
            Column(
                modifier = Modifier.padding(8.dp),
            ) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )

                item.year?.let { year ->
                    Text(
                        text = year.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyState(
    isSearchActive: Boolean,
    currentTab: LibraryTab,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = if (currentTab == LibraryTab.VOD) Icons.Default.Theaters else Icons.Default.PlayCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text =
                    if (isSearchActive) {
                        "No results found"
                    } else {
                        "No ${if (currentTab == LibraryTab.VOD) "movies" else "series"} available"
                    },
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!isSearchActive) {
                Text(
                    text = "Add an Xtream source to see content",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// ===== Mapping Extensions: Domain ↔ UI =====

/** Convert domain LibrarySortOption to UI UiSortOption */
private fun LibrarySortOption.toUiSortOption(): UiSortOption =
    UiSortOption(
        field =
            when (field) {
                LibrarySortField.TITLE -> UiSortField.TITLE
                LibrarySortField.YEAR -> UiSortField.YEAR
                LibrarySortField.RATING -> UiSortField.RATING
                LibrarySortField.RECENTLY_ADDED -> UiSortField.RECENTLY_ADDED
                LibrarySortField.RECENTLY_UPDATED -> UiSortField.RECENTLY_UPDATED
                LibrarySortField.DURATION -> UiSortField.DURATION
            },
        direction =
            when (direction) {
                LibrarySortDirection.ASCENDING -> UiSortDirection.ASCENDING
                LibrarySortDirection.DESCENDING -> UiSortDirection.DESCENDING
            },
    )

/** Convert UI UiSortOption to domain LibrarySortOption */
private fun UiSortOption.toLibrarySortOption(): LibrarySortOption =
    LibrarySortOption(
        field =
            when (field) {
                UiSortField.TITLE -> LibrarySortField.TITLE
                UiSortField.YEAR -> LibrarySortField.YEAR
                UiSortField.RATING -> LibrarySortField.RATING
                UiSortField.RECENTLY_ADDED -> LibrarySortField.RECENTLY_ADDED
                UiSortField.RECENTLY_UPDATED -> LibrarySortField.RECENTLY_UPDATED
                UiSortField.DURATION -> LibrarySortField.DURATION
            },
        direction =
            when (direction) {
                UiSortDirection.ASCENDING -> LibrarySortDirection.ASCENDING
                UiSortDirection.DESCENDING -> LibrarySortDirection.DESCENDING
            },
    )

/** Convert domain LibraryFilterConfig to UI UiFilterConfig */
private fun LibraryFilterConfig.toUiFilterConfig(): UiFilterConfig =
    UiFilterConfig(
        hideAdult = hideAdult,
        selectedGenres = includeGenres ?: emptySet(),
        excludedGenres = excludeGenres ?: emptySet(),
        minRating = minRating?.toFloat(),
        yearRange = yearRange,
    )

/** Convert UI UiFilterConfig to domain LibraryFilterConfig */
private fun UiFilterConfig.toLibraryFilterConfig(): LibraryFilterConfig =
    LibraryFilterConfig(
        hideAdult = hideAdult,
        includeGenres = selectedGenres.takeIf { it.isNotEmpty() },
        excludeGenres = excludedGenres.takeIf { it.isNotEmpty() },
        minRating = minRating?.toDouble(),
        yearRange = yearRange,
    )
