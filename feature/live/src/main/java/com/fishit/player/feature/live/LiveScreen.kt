package com.fishit.player.feature.live

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fishit.player.core.live.domain.LiveCategory
import com.fishit.player.core.live.domain.LiveChannel

/**
 * Live TV screen displaying channels in a grid with category filtering.
 *
 * Features:
 * - Channel grid with logos
 * - Category filter chips
 * - Recent channels row
 * - Favorites tab
 * - Search functionality
 * - Current program info (when EPG available)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveScreen(
    viewModel: LiveViewModel = hiltViewModel(),
    onChannelClick: (LiveChannel) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val channels by viewModel.channels.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val selectedCategoryId by viewModel.selectedCategoryId.collectAsStateWithLifecycle()
    val recentChannels by viewModel.recentChannels.collectAsStateWithLifecycle()
    val viewMode by viewModel.viewMode.collectAsStateWithLifecycle()
    val isSearchActive by viewModel.isSearchActive.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val searchResults by viewModel.searchResults.collectAsStateWithLifecycle()

    Column(
        modifier = modifier.fillMaxSize()
    ) {
        // Header with tabs and search
        LiveHeader(
            viewMode = viewMode,
            isSearchActive = isSearchActive,
            searchQuery = searchQuery,
            onViewModeChange = viewModel::setViewMode,
            onSearchActiveChange = viewModel::setSearchActive,
            onSearchQueryChange = viewModel::search
        )

        if (isSearchActive) {
            // Search results
            SearchResultsContent(
                results = searchResults,
                onChannelClick = { channel ->
                    viewModel.onChannelPlayed(channel)
                    onChannelClick(channel)
                },
                onFavoriteClick = viewModel::toggleFavorite
            )
        } else {
            // Main content
            LazyColumn(
                modifier = Modifier.fillMaxSize()
            ) {
                // Recent channels
                if (recentChannels.isNotEmpty() && viewMode == LiveViewModel.ViewMode.ALL) {
                    item {
                        RecentChannelsRow(
                            channels = recentChannels,
                            onChannelClick = { channel ->
                                viewModel.onChannelPlayed(channel)
                                onChannelClick(channel)
                            }
                        )
                    }
                }

                // Category filter (only in ALL mode)
                if (viewMode == LiveViewModel.ViewMode.ALL && categories.isNotEmpty()) {
                    item {
                        CategoryFilterRow(
                            categories = categories,
                            selectedId = selectedCategoryId,
                            onCategorySelect = viewModel::selectCategory
                        )
                    }
                }

                // Channel grid
                item {
                    ChannelGrid(
                        channels = channels,
                        onChannelClick = { channel ->
                            viewModel.onChannelPlayed(channel)
                            onChannelClick(channel)
                        },
                        onFavoriteClick = viewModel::toggleFavorite
                    )
                }
            }
        }
    }
}

@Composable
private fun LiveHeader(
    viewMode: LiveViewModel.ViewMode,
    isSearchActive: Boolean,
    searchQuery: String,
    onViewModeChange: (LiveViewModel.ViewMode) -> Unit,
    onSearchActiveChange: (Boolean) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Live TV",
                style = MaterialTheme.typography.headlineMedium
            )

            IconButton(onClick = { onSearchActiveChange(!isSearchActive) }) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search"
                )
            }
        }

        if (isSearchActive) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                placeholder = { Text("Search channels...") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                singleLine = true,
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = null)
                }
            )
        } else {
            TabRow(
                selectedTabIndex = viewMode.ordinal,
                modifier = Modifier.fillMaxWidth()
            ) {
                Tab(
                    selected = viewMode == LiveViewModel.ViewMode.ALL,
                    onClick = { onViewModeChange(LiveViewModel.ViewMode.ALL) },
                    text = { Text("All Channels") }
                )
                Tab(
                    selected = viewMode == LiveViewModel.ViewMode.FAVORITES,
                    onClick = { onViewModeChange(LiveViewModel.ViewMode.FAVORITES) },
                    text = { Text("Favorites") }
                )
            }
        }
    }
}

@Composable
private fun RecentChannelsRow(
    channels: List<LiveChannel>,
    onChannelClick: (LiveChannel) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.padding(vertical = 8.dp)) {
        Text(
            text = "Recently Watched",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )

        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(channels, key = { it.id }) { channel ->
                RecentChannelItem(
                    channel = channel,
                    onClick = { onChannelClick(channel) }
                )
            }
        }
    }
}

@Composable
private fun RecentChannelItem(
    channel: LiveChannel,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .width(80.dp)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Logo placeholder
        Box(
            modifier = Modifier
                .size(60.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = channel.name.take(2).uppercase(),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = channel.name,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun CategoryFilterRow(
    categories: List<LiveCategory>,
    selectedId: String?,
    onCategorySelect: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyRow(
        modifier = modifier.padding(vertical = 8.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            FilterChip(
                selected = selectedId == null,
                onClick = { onCategorySelect(null) },
                label = { Text("All") }
            )
        }

        items(categories, key = { it.id }) { category ->
            FilterChip(
                selected = selectedId == category.id,
                onClick = { onCategorySelect(category.id) },
                label = { Text("${category.name} (${category.channelCount})") }
            )
        }
    }
}

@Composable
private fun ChannelGrid(
    channels: List<LiveChannel>,
    onChannelClick: (LiveChannel) -> Unit,
    onFavoriteClick: (LiveChannel) -> Unit,
    modifier: Modifier = Modifier
) {
    if (channels.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(200.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "No channels found",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    // Using Column with chunked items instead of LazyVerticalGrid inside LazyColumn
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        channels.chunked(3).forEach { rowChannels ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                rowChannels.forEach { channel ->
                    ChannelCard(
                        channel = channel,
                        onClick = { onChannelClick(channel) },
                        onFavoriteClick = { onFavoriteClick(channel) },
                        modifier = Modifier.weight(1f)
                    )
                }
                // Fill empty slots
                repeat(3 - rowChannels.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun ChannelCard(
    channel: LiveChannel,
    onClick: () -> Unit,
    onFavoriteClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column {
            // Logo area
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                // Channel number badge
                channel.channelNumber?.let { num ->
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(4.dp),
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.primary
                    ) {
                        Text(
                            text = num.toString(),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }

                // Logo placeholder
                Text(
                    text = channel.name.take(3).uppercase(),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Favorite button
                IconButton(
                    onClick = onFavoriteClick,
                    modifier = Modifier.align(Alignment.TopEnd)
                ) {
                    Icon(
                        imageVector = if (channel.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = if (channel.isFavorite) "Remove from favorites" else "Add to favorites",
                        tint = if (channel.isFavorite) Color.Red else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Channel info
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
            ) {
                Text(
                    text = channel.name,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                channel.currentProgram?.let { program ->
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = program,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchResultsContent(
    results: List<LiveChannel>,
    onChannelClick: (LiveChannel) -> Unit,
    onFavoriteClick: (LiveChannel) -> Unit,
    modifier: Modifier = Modifier
) {
    if (results.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "No channels found",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(results, key = { it.id }) { channel ->
            ChannelCard(
                channel = channel,
                onClick = { onChannelClick(channel) },
                onFavoriteClick = { onFavoriteClick(channel) }
            )
        }
    }
}
