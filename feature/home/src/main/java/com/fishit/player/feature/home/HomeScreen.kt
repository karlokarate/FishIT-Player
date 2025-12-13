package com.fishit.player.feature.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.fishit.player.core.model.MediaType
import com.fishit.player.core.model.SourceType
import com.fishit.player.core.ui.layout.FishRow
import com.fishit.player.core.ui.layout.FishRowEmpty
import com.fishit.player.core.ui.layout.FishTile
import com.fishit.player.core.ui.theme.FishColors
import com.fishit.player.core.ui.theme.LocalFishDimens
import com.fishit.player.feature.home.domain.HomeMediaItem

/**
 * Home Screen - Main content browsing screen
 *
 * Displays:
 * - Continue Watching row
 * - Recently Added row
 * - Source-specific rows (Telegram, Xtream VOD/Series/Live)
 *
 * Navigation:
 * - Top bar with Home/Settings/Debug icons
 * - DPAD navigation between rows and tiles
 */
@Composable
fun HomeScreen(
    onItemClick: (HomeMediaItem) -> Unit,
    onSettingsClick: () -> Unit,
    onDebugClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val dimens = LocalFishDimens.current

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top Bar
            HomeTopBar(
                onRefreshClick = viewModel::refresh,
                onSettingsClick = onSettingsClick,
                onDebugClick = onDebugClick
            )

            // Content
            when {
                state.isLoading -> {
                    LoadingContent()
                }

                state.error != null -> {
                    ErrorContent(
                        error = state.error!!,
                        onRetry = viewModel::refresh
                    )
                }

                else -> {
                    HomeContent(
                        state = state,
                        onItemClick = onItemClick
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeTopBar(
    onRefreshClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onDebugClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Logo/Title
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "🐟",
                style = MaterialTheme.typography.headlineMedium
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = "FishIT",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        // Actions
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IconButton(onClick = onRefreshClick) {
                Icon(
                    Icons.Default.Refresh,
                    contentDescription = "Refresh",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            IconButton(onClick = onDebugClick) {
                Icon(
                    Icons.Default.Info,
                    contentDescription = "Debug",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            IconButton(onClick = onSettingsClick) {
                Icon(
                    Icons.Default.Settings,
                    contentDescription = "Settings",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun HomeContent(
    state: HomeState,
    onItemClick: (HomeMediaItem) -> Unit
) {
    val listState = rememberLazyListState()

    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        // Continue Watching
        if (state.continueWatchingItems.isNotEmpty()) {
            item(key = "continue_watching") {
                MediaRow(
                    title = "Continue Watching",
                    icon = Icons.Default.PlayCircle,
                    items = state.continueWatchingItems,
                    onItemClick = onItemClick
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
                    onItemClick = onItemClick
                )
            }
        }

        // Telegram Media
        if (state.hasTelegramSource && state.telegramMediaItems.isNotEmpty()) {
            item(key = "telegram_media") {
                MediaRow(
                    title = "Telegram Media",
                    icon = Icons.Default.Send,
                    iconTint = FishColors.SourceTelegram,
                    items = state.telegramMediaItems,
                    onItemClick = onItemClick
                )
            }
        }

        // Xtream VOD
        if (state.hasXtreamSource && state.xtreamVodItems.isNotEmpty()) {
            item(key = "xtream_vod") {
                MediaRow(
                    title = "Movies",
                    icon = Icons.Default.PlayCircle,
                    iconTint = FishColors.SourceXtream,
                    items = state.xtreamVodItems,
                    onItemClick = onItemClick
                )
            }
        }

        // Xtream Series
        if (state.hasXtreamSource && state.xtreamSeriesItems.isNotEmpty()) {
            item(key = "xtream_series") {
                MediaRow(
                    title = "Series",
                    icon = Icons.Default.Videocam,
                    iconTint = FishColors.SourceXtream,
                    items = state.xtreamSeriesItems,
                    onItemClick = onItemClick
                )
            }
        }

        // Xtream Live
        if (state.hasXtreamSource && state.xtreamLiveItems.isNotEmpty()) {
            item(key = "xtream_live") {
                MediaRow(
                    title = "Live TV",
                    icon = Icons.Default.Videocam,
                    iconTint = FishColors.SourceXtream,
                    items = state.xtreamLiveItems,
                    onItemClick = onItemClick
                )
            }
        }

        // Empty state if no content at all
        if (state.continueWatchingItems.isEmpty() &&
            state.recentlyAddedItems.isEmpty() &&
            state.telegramMediaItems.isEmpty() &&
            state.xtreamVodItems.isEmpty()
        ) {
            item(key = "empty") {
                EmptyHomeContent()
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
    iconTint: Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    val dimens = LocalFishDimens.current
    val listState = rememberLazyListState()

    Column(
        modifier = modifier.fillMaxWidth()
    ) {
        // Row Header
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(
                horizontal = dimens.contentPaddingHorizontal,
                vertical = 8.dp
            )
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "(${items.size})",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Tiles Row
        LazyRow(
            state = listState,
            contentPadding = PaddingValues(
                horizontal = dimens.contentPaddingHorizontal,
                vertical = 8.dp
            ),
            horizontalArrangement = Arrangement.spacedBy(dimens.tileSpacing),
            modifier = Modifier
                .fillMaxWidth()
                .focusGroup()
        ) {
            items(
                items = items,
                key = { it.id }
            ) { item ->
                FishTile(
                    title = item.title,
                    poster = item.poster,
                    placeholder = item.placeholderThumbnail,
                    sourceColors = getSourceColors(item.sourceType),
                    resumeFraction = item.resumeFraction,
                    isNew = item.isNew,
                    onClick = { onItemClick(item) }
                )
            }
        }
    }
}

@Composable
private fun LoadingContent() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CircularProgressIndicator(
                color = FishColors.Primary,
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Loading content...",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ErrorContent(
    error: String,
    onRetry: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "😿",
                style = MaterialTheme.typography.displayMedium
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Something went wrong",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = error,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
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
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "🐟",
                style = MaterialTheme.typography.displayLarge
            )
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "No content yet",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Connect Telegram or an Xtream server to start browsing",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Get source-specific colors for tile borders
 */
private fun getSourceColors(sourceType: SourceType): List<Color> {
    return when (sourceType) {
        SourceType.TELEGRAM -> listOf(FishColors.SourceTelegram)
        SourceType.XTREAM -> listOf(FishColors.SourceXtream)
        SourceType.LOCAL -> listOf(FishColors.SourceLocal)
        else -> emptyList()
    }
}
