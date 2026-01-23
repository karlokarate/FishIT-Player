package com.fishit.player.feature.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.fishit.player.core.catalogsync.SyncUiState
import com.fishit.player.core.home.domain.HomeMediaItem
import com.fishit.player.core.model.SourceType
import com.fishit.player.core.model.filter.PresetGenreFilter
import com.fishit.player.core.sourceactivation.SourceId
import com.fishit.player.core.ui.layout.FishTile
import com.fishit.player.core.ui.theme.FishColors
import com.fishit.player.core.ui.theme.LocalFishDimens

/**
 * Home Screen - Main content browsing screen
 *
 * Displays:
 * - Continue Watching row
 * - Recently Added row
 * - Source-specific rows (Telegram, Xtream VOD/Series/Live)
 *
 * Navigation:
 * - Top bar with Home/Library/Settings/Debug icons
 * - DPAD navigation between rows and tiles
 */
@Composable
fun HomeScreen(
    onItemClick: (HomeMediaItem) -> Unit,
    onSettingsClick: () -> Unit,
    onDebugClick: () -> Unit,
    onLibraryClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    // Use filteredState for search/filter support
    val state by viewModel.filteredState.collectAsState()
    val dimens = LocalFishDimens.current

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top Bar with sync indicator and search button
            HomeTopBar(
                syncState = state.syncState,
                isSearchActive = state.isSearchVisible || state.isFilterActive,
                onSearchClick = viewModel::toggleSearch,
                onRefreshClick = viewModel::refresh,
                onSettingsClick = onSettingsClick,
                onDebugClick = onDebugClick,
                onLibraryClick = onLibraryClick,
            )

            // Search & Filter Panel
            AnimatedVisibility(
                visible = state.isSearchVisible,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                SearchFilterPanel(
                    searchQuery = state.searchQuery,
                    selectedGenre = state.selectedGenre,
                    onSearchQueryChange = viewModel::setSearchQuery,
                    onGenreSelected = viewModel::setGenreFilter,
                    onClearFilters = viewModel::clearFilters,
                )
            }

            // Content
            when {
                state.isLoading -> {
                    LoadingContent()
                }

                state.error != null -> {
                    ErrorContent(
                        error = state.error!!,
                        onRetry = viewModel::refresh,
                    )
                }

                else -> {
                    HomeContent(
                        state = state,
                        onItemClick = onItemClick,
                        onAddSourceClick = onSettingsClick,
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeTopBar(
    syncState: SyncUiState,
    isSearchActive: Boolean,
    onSearchClick: () -> Unit,
    onRefreshClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onDebugClick: () -> Unit,
    onLibraryClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Logo/Title
        Row(
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "🐟",
                style = MaterialTheme.typography.headlineMedium,
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = "FishIT",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        // Sync Status Indicator (Contract: STARTUP_TRIGGER_CONTRACT O-1)
        SyncStatusIndicator(syncState = syncState)

        Spacer(modifier = Modifier.width(16.dp))

        // Actions - use LazyRow would be better but Row is sufficient for fixed items
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Search Button with active indicator
            IconButton(onClick = onSearchClick) {
                Icon(
                    Icons.Default.Search,
                    contentDescription = "Search",
                    tint = if (isSearchActive) FishColors.Primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // Library Button - Browse all content with advanced filters
            IconButton(onClick = onLibraryClick) {
                Icon(
                    Icons.Default.VideoLibrary,
                    contentDescription = "Library",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            IconButton(onClick = onRefreshClick) {
                Icon(
                    Icons.Default.Refresh,
                    contentDescription = "Refresh",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            IconButton(onClick = onDebugClick) {
                Icon(
                    Icons.Default.Info,
                    contentDescription = "Debug",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            IconButton(onClick = onSettingsClick) {
                Icon(
                    Icons.Default.Settings,
                    contentDescription = "Settings",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * Sync status indicator showing current catalog sync state.
 *
 * Contract: STARTUP_TRIGGER_CONTRACT (O-1)
 * - Passive indicator: IDLE, RUNNING, SUCCESS, FAILED
 */
@Composable
private fun SyncStatusIndicator(
    syncState: SyncUiState,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        when (syncState) {
            is SyncUiState.Idle -> {
                // No indicator when idle
            }
            is SyncUiState.Running -> {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = FishColors.Primary,
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Syncing…",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            is SyncUiState.Success -> {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = "Sync complete",
                    modifier = Modifier.size(16.dp),
                    tint = FishColors.Success,
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Synced",
                    style = MaterialTheme.typography.labelMedium,
                    color = FishColors.Success,
                )
            }
            is SyncUiState.Failed -> {
                Icon(
                    Icons.Default.Error,
                    contentDescription = "Sync failed",
                    modifier = Modifier.size(16.dp),
                    tint = FishColors.Error,
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = syncState.reason.toDisplayString(),
                    style = MaterialTheme.typography.labelMedium,
                    color = FishColors.Error,
                )
            }
        }
    }
}

/**
 * Convert SyncFailureReason to user-friendly display string.
 */
private fun com.fishit.player.core.catalogsync.SyncFailureReason.toDisplayString(): String =
    when (this) {
        com.fishit.player.core.catalogsync.SyncFailureReason.LOGIN_REQUIRED -> "Login required"
        com.fishit.player.core.catalogsync.SyncFailureReason.INVALID_CREDENTIALS -> "Invalid credentials"
        com.fishit.player.core.catalogsync.SyncFailureReason.PERMISSION_MISSING -> "Permission missing"
        com.fishit.player.core.catalogsync.SyncFailureReason.NETWORK_GUARD -> "Network unavailable"
        com.fishit.player.core.catalogsync.SyncFailureReason.UNKNOWN -> "Sync failed"
    }

/**
 * Search and Genre Filter Panel
 *
 * Provides:
 * - Text search field (searches title + year)
 * - Genre filter chips (horizontal scrollable row)
 * - Clear filters button
 */
@Composable
private fun SearchFilterPanel(
    searchQuery: String,
    selectedGenre: PresetGenreFilter,
    onSearchQueryChange: (String) -> Unit,
    onGenreSelected: (PresetGenreFilter) -> Unit,
    onClearFilters: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dimens = LocalFishDimens.current
    val focusRequester = remember { FocusRequester() }

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                .padding(horizontal = dimens.contentPaddingHorizontal, vertical = 12.dp),
    ) {
        // Search Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Search TextField
            Box(
                modifier =
                    Modifier
                        .weight(1f)
                        .height(48.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .border(
                            width = 1.dp,
                            color = if (searchQuery.isNotEmpty()) FishColors.Primary else MaterialTheme.colorScheme.outline,
                            shape = RoundedCornerShape(24.dp),
                        ).padding(horizontal = 16.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    BasicTextField(
                        value = searchQuery,
                        onValueChange = onSearchQueryChange,
                        modifier =
                            Modifier
                                .weight(1f)
                                .focusRequester(focusRequester),
                        textStyle =
                            TextStyle(
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = MaterialTheme.typography.bodyLarge.fontSize,
                            ),
                        singleLine = true,
                        cursorBrush = SolidColor(FishColors.Primary),
                        decorationBox = { innerTextField ->
                            Box {
                                if (searchQuery.isEmpty()) {
                                    Text(
                                        text = "Suche nach Titel, Jahr...",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                innerTextField()
                            }
                        },
                    )
                    if (searchQuery.isNotEmpty()) {
                        IconButton(
                            onClick = { onSearchQueryChange("") },
                            modifier = Modifier.size(24.dp),
                        ) {
                            Icon(
                                Icons.Default.Clear,
                                contentDescription = "Clear search",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
            }

            // Clear All Button (only visible when filters are active)
            if (searchQuery.isNotEmpty() || selectedGenre != PresetGenreFilter.ALL) {
                IconButton(onClick = onClearFilters) {
                    Icon(
                        Icons.Default.Clear,
                        contentDescription = "Clear all filters",
                        tint = FishColors.Error,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Genre Filter Chips
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            items(PresetGenreFilter.all) { genre ->
                FilterChip(
                    selected = selectedGenre == genre,
                    onClick = { onGenreSelected(genre) },
                    label = {
                        Text(
                            text = genre.displayName,
                            style = MaterialTheme.typography.labelMedium,
                        )
                    },
                    colors =
                        FilterChipDefaults.filterChipColors(
                            selectedContainerColor = FishColors.Primary,
                            selectedLabelColor = Color.White,
                        ),
                )
            }
        }
    }
}

@Composable
private fun HomeContent(
    state: HomeState,
    onItemClick: (HomeMediaItem) -> Unit,
    onAddSourceClick: () -> Unit,
) {
    val listState = rememberLazyListState()

    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        // Continue Watching
        if (state.continueWatchingItems.isNotEmpty()) {
            item(key = "continue_watching") {
                MediaRow(
                    title = "Continue Watching",
                    icon = Icons.Default.PlayCircle,
                    items = state.continueWatchingItems,
                    onItemClick = onItemClick,
                )
            }
        }

        // Recently Added
        if (state.recentlyAddedItems.isNotEmpty()) {
            item(key = "recently_added") {
                MediaRow(
                    title = "Recently Added",
                    icon = Icons.Default.Home,
                    items = state.recentlyAddedItems,
                    onItemClick = onItemClick,
                )
            }
        }

        // Live TV (Xtream only)
        if (state.xtreamLiveItems.isNotEmpty()) {
            item(key = "live_tv") {
                MediaRow(
                    title = "Live TV",
                    icon = Icons.Default.LiveTv,
                    iconTint = FishColors.SourceXtream,
                    items = state.xtreamLiveItems,
                    onItemClick = onItemClick,
                )
            }
        }

        // Movies (cross-pipeline: Xtream + Telegram)
        if (state.moviesItems.isNotEmpty()) {
            item(key = "movies") {
                MediaRow(
                    title = "Movies",
                    icon = Icons.Default.Movie,
                    items = state.moviesItems,
                    onItemClick = onItemClick,
                )
            }
        }

        // Series (cross-pipeline: Xtream + Telegram)
        if (state.seriesItems.isNotEmpty()) {
            item(key = "series") {
                MediaRow(
                    title = "Series",
                    icon = Icons.Default.Tv,
                    items = state.seriesItems,
                    onItemClick = onItemClick,
                )
            }
        }

        // Clips (Telegram only)
        if (state.clipsItems.isNotEmpty()) {
            item(key = "clips") {
                MediaRow(
                    title = "Clips",
                    icon = Icons.Default.VideoLibrary,
                    iconTint = FishColors.SourceTelegram,
                    items = state.clipsItems,
                    onItemClick = onItemClick,
                )
            }
        }

        // Empty state if no content at all
        // Contract: STARTUP_TRIGGER_CONTRACT (U-1)
        // - Shows different states based on source activation and sync status
        if (!state.hasContent) {
            item(key = "empty") {
                SmartEmptyContent(
                    hasActiveSources = state.sourceActivation.hasActiveSources,
                    syncState = state.syncState,
                    activeSources = state.sourceActivation.activeSources,
                    onAddSourceClick = onAddSourceClick,
                )
            }
        }

        // Bottom spacing for TV overscan
        item { Spacer(modifier = Modifier.height(48.dp)) }
    }
}

@Composable
private fun MediaRow(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    items: List<HomeMediaItem>,
    onItemClick: (HomeMediaItem) -> Unit,
    modifier: Modifier = Modifier,
    iconTint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    val dimens = LocalFishDimens.current
    val listState = rememberLazyListState()

    // Count items per source type
    val telegramCount = items.count { it.sourceType == SourceType.TELEGRAM }
    val xtreamCount = items.count { it.sourceType == SourceType.XTREAM }
    val otherCount = items.size - telegramCount - xtreamCount

    Column(
        modifier = modifier.fillMaxWidth(),
    ) {
        // Row Header
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier =
                Modifier.padding(
                    horizontal = dimens.contentPaddingHorizontal,
                    vertical = 8.dp,
                ),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(24.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(modifier = Modifier.width(8.dp))
            // Source-specific counts with colors
            SourceCountBadge(
                telegramCount = telegramCount,
                xtreamCount = xtreamCount,
                otherCount = otherCount,
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Tiles Row
        LazyRow(
            state = listState,
            contentPadding =
                PaddingValues(
                    horizontal = dimens.contentPaddingHorizontal,
                    vertical = 8.dp,
                ),
            horizontalArrangement = Arrangement.spacedBy(dimens.tileSpacing),
            modifier =
                Modifier
                    .fillMaxWidth()
                    .focusGroup(),
        ) {
            items(
                items = items,
                key = { it.id },
            ) { item ->
                FishTile(
                    title = item.title,
                    poster = item.poster,
                    placeholder = item.placeholderThumbnail,
                    sourceColors = getSourceColors(item.sourceTypes),
                    resumeFraction = item.resumeFraction,
                    isNew = item.isNew,
                    onClick = { onItemClick(item) },
                )
            }
        }
    }
}

@Composable
private fun LoadingContent() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CircularProgressIndicator(
                color = FishColors.Primary,
                modifier = Modifier.size(48.dp),
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Loading content...",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ErrorContent(
    error: String,
    onRetry: () -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "😿",
                style = MaterialTheme.typography.displayMedium,
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Something went wrong",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = error,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(24.dp))
            androidx.compose.material3.Button(onClick = onRetry) {
                Text("Retry")
            }
        }
    }
}

@Composable
private fun EmptyHomeContent() {
    SmartEmptyContent(
        hasActiveSources = false,
        syncState = SyncUiState.Idle,
        activeSources = emptySet(),
        onAddSourceClick = {},
    )
}

/**
 * Smart empty content that shows different states based on source activation and sync status.
 *
 * Contract: STARTUP_TRIGGER_CONTRACT (U-1)
 * - No sources → "Add source" button
 * - Sources active + syncing → "Sync pending" with indicator
 * - Sources active + after sync → "No content found"
 */
@Composable
private fun SmartEmptyContent(
    hasActiveSources: Boolean,
    syncState: SyncUiState,
    activeSources: Set<SourceId>,
    onAddSourceClick: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            when {
                // No sources configured → Prompt to add source
                !hasActiveSources -> {
                    Text(
                        text = "🐟",
                        style = MaterialTheme.typography.displayLarge,
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        text = "No sources configured",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Connect Telegram or an Xtream server to start browsing",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(onClick = onAddSourceClick) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Add Source")
                    }
                }

                // Sources active but sync is running → Show sync pending
                syncState is SyncUiState.Running -> {
                    CircularProgressIndicator(
                        modifier = Modifier.size(48.dp),
                        color = FishColors.Primary,
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        text = "Syncing catalog…",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = buildActiveSourcesText(activeSources),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                // Sources active, sync failed → Show error with retry hint
                syncState is SyncUiState.Failed -> {
                    Text(
                        text = "😿",
                        style = MaterialTheme.typography.displayLarge,
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        text = "Sync failed",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = syncState.reason.toDisplayString(),
                        style = MaterialTheme.typography.bodyLarge,
                        color = FishColors.Error,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Check your connection and try again",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                // Sources active, sync complete, but no content → Library is empty
                else -> {
                    Text(
                        text = "🐟",
                        style = MaterialTheme.typography.displayLarge,
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        text = "No content found",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Your connected sources don't have any media yet",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/**
 * Build a user-friendly string showing which sources are active.
 */
private fun buildActiveSourcesText(activeSources: Set<SourceId>): String {
    if (activeSources.isEmpty()) return "Checking sources…"

    val sourceNames =
        activeSources.map { source ->
            when (source) {
                SourceId.XTREAM -> "Xtream"
                SourceId.TELEGRAM -> "Telegram"
                SourceId.IO -> "Local Files"
            }
        }

    return when (sourceNames.size) {
        1 -> "Fetching from ${sourceNames.first()}"
        2 -> "Fetching from ${sourceNames[0]} and ${sourceNames[1]}"
        else -> "Fetching from ${sourceNames.dropLast(1).joinToString(", ")} and ${sourceNames.last()}"
    }
}

/**
 * Displays source-specific counts in a compact format.
 *
 * Shows counts like "848/442" with:
 * - Telegram count in blue (FishColors.SourceTelegram)
 * - Xtream count in red (FishColors.SourceXtream)
 * - Other counts in gray (if present)
 *
 * Examples:
 * - "848/442" (848 from Telegram, 442 from Xtream)
 * - "848" (only Telegram)
 * - "442" (only Xtream)
 * - "(123)" (only other sources, in gray)
 */
@Composable
private fun SourceCountBadge(
    telegramCount: Int,
    xtreamCount: Int,
    otherCount: Int,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val hasTelegram = telegramCount > 0
        val hasXtream = xtreamCount > 0
        val hasOther = otherCount > 0

        Text(
            text = "(",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        when {
            // Both Telegram and Xtream
            hasTelegram && hasXtream -> {
                Text(
                    text = "$telegramCount",
                    style = MaterialTheme.typography.bodyMedium,
                    color = FishColors.SourceTelegram,
                )
                Text(
                    text = "/",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "$xtreamCount",
                    style = MaterialTheme.typography.bodyMedium,
                    color = FishColors.SourceXtream,
                )
                if (hasOther) {
                    Text(
                        text = "+$otherCount",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            // Only Telegram
            hasTelegram -> {
                Text(
                    text = "$telegramCount",
                    style = MaterialTheme.typography.bodyMedium,
                    color = FishColors.SourceTelegram,
                )
                if (hasOther) {
                    Text(
                        text = "+$otherCount",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            // Only Xtream
            hasXtream -> {
                Text(
                    text = "$xtreamCount",
                    style = MaterialTheme.typography.bodyMedium,
                    color = FishColors.SourceXtream,
                )
                if (hasOther) {
                    Text(
                        text = "+$otherCount",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            // Only other sources
            hasOther -> {
                Text(
                    text = "$otherCount",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            // Empty (shouldn't happen but handle gracefully)
            else -> {
                Text(
                    text = "0",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Text(
            text = ")",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Get source-specific colors for tile borders.
 *
 * Multi-source items get a gradient: Xtream (Red) -> Lila -> Telegram (Blue)
 * Single-source items get a solid color.
 */
private fun getSourceColors(sourceTypes: List<SourceType>): List<Color> {
    // Filter to only include known sources with colors
    val relevantSources =
        sourceTypes
            .filter {
                it == SourceType.XTREAM || it == SourceType.TELEGRAM || it == SourceType.LOCAL
            }.distinct()

    return when {
        relevantSources.isEmpty() -> emptyList()
        relevantSources.size == 1 -> {
            // Single source: solid color
            listOf(sourceTypeToColor(relevantSources.first()))
        }
        else -> {
            // Multi-source: gradient with purple blend in the middle
            // Sort: XTREAM first (red on left), TELEGRAM second (blue on right)
            val sorted =
                relevantSources.sortedByDescending { source ->
                    when (source) {
                        SourceType.XTREAM -> 3
                        SourceType.TELEGRAM -> 2
                        SourceType.LOCAL -> 1
                        else -> 0
                    }
                }
            // Create gradient: first color -> purple blend -> second color
            if (sorted.size >= 2) {
                listOf(
                    sourceTypeToColor(sorted[0]), // Xtream Red
                    FishColors.SourceMultiBlend, // Purple blend in middle
                    sourceTypeToColor(sorted[1]), // Telegram Blue
                )
            } else {
                sorted.map { sourceTypeToColor(it) }
            }
        }
    }
}

/**
 * Map a single SourceType to its display color.
 */
private fun sourceTypeToColor(sourceType: SourceType): Color =
    when (sourceType) {
        SourceType.TELEGRAM -> FishColors.SourceTelegram
        SourceType.XTREAM -> FishColors.SourceXtream
        SourceType.LOCAL -> FishColors.SourceLocal
        else -> Color.Transparent
    }
