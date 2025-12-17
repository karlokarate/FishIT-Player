package com.fishit.player.feature.detail.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Hd
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Timelapse
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.fishit.player.core.imaging.compose.FishImage
import com.fishit.player.core.model.CanonicalMediaId
import com.fishit.player.core.model.MediaSourceRef
import com.fishit.player.core.model.SourceType
import com.fishit.player.core.model.ids.asPipelineItemId
import com.fishit.player.core.ui.theme.FishColors
import com.fishit.player.core.ui.theme.FishShapes
import com.fishit.player.feature.detail.UnifiedDetailEvent
import com.fishit.player.feature.detail.UnifiedDetailState
import com.fishit.player.feature.detail.UnifiedDetailViewModel

/**
 * Detail Screen - Shows media details with playback options
 *
 * Displays:
 * - Hero backdrop image
 * - Title, year, rating, duration
 * - Source badges for multi-source content
 * - Play/Resume actions
 * - Synopsis/Overview
 *
 * Follows v1 legacy visual style adapted to v2 architecture.
 */
@Composable
fun DetailScreen(
    mediaId: String,
    sourceType: SourceType,
    onBack: () -> Unit,
    onPlayback: (UnifiedDetailEvent.StartPlayback) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: UnifiedDetailViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()

    // Load media on first composition
    LaunchedEffect(mediaId) {
        viewModel.loadBySourceId(mediaId.asPipelineItemId())
    }

    // Handle events
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is UnifiedDetailEvent.StartPlayback -> onPlayback(event)
                else -> { /* Handle other events */ }
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        when {
            state.isLoading -> {
                LoadingContent()
            }
            state.error != null -> {
                ErrorContent(
                    error = state.error!!,
                    onBack = onBack
                )
            }
            state.media != null -> {
                DetailContent(
                    state = state,
                    onBack = onBack,
                    onPlay = viewModel::play,
                    onResume = viewModel::resume,
                    onPlayFromStart = viewModel::playFromStart,
                    onShowSourcePicker = viewModel::showSourcePicker,
                    onSelectSource = viewModel::selectSource
                )
            }
        }
    }
}

@Composable
private fun DetailContent(
    state: UnifiedDetailState,
    onBack: () -> Unit,
    onPlay: () -> Unit,
    onResume: () -> Unit,
    onPlayFromStart: () -> Unit,
    onShowSourcePicker: () -> Unit,
    onSelectSource: (MediaSourceRef) -> Unit
) {
    val media = state.media ?: return
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
    ) {
        // Hero Section with backdrop
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
        ) {
            // Backdrop image
            FishImage(
                imageRef = media.backdrop ?: media.poster,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            // Gradient overlay for text readability
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.3f),
                                Color.Black.copy(alpha = 0.1f),
                                Color.Black.copy(alpha = 0.7f)
                            )
                        )
                    )
            )

            // Back button
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(16.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White
                )
            }

            // Title overlay at bottom
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(24.dp)
            ) {
                Text(
                    text = media.canonicalTitle,
                    style = MaterialTheme.typography.headlineMedium,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Meta chips row
                MetaChipsRow(
                    year = media.year,
                    rating = media.rating?.toFloat(),
                    durationMinutes = state.selectedSource?.durationMs?.let { it / 60000 }?.toInt() ?: media.durationMinutes,
                    quality = state.selectedQualityLabel
                )
            }
        }

        // Content section
        Column(
            modifier = Modifier.padding(24.dp)
        ) {
            // Source badges if multiple sources
            if (state.hasMultipleSources) {
                SourceBadgesRow(
                    sourceTypes = state.availableSourceTypes,
                    selectedSource = state.selectedSource,
                    onShowPicker = onShowSourcePicker
                )
                Spacer(modifier = Modifier.height(20.dp))
            }

            // Action buttons
            ActionButtonsRow(
                canResume = state.canResume,
                resumeProgress = state.resumeProgressPercent,
                onPlay = onPlay,
                onResume = onResume,
                onPlayFromStart = onPlayFromStart
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Overview/Synopsis
            media.plot?.let { overview ->
                Text(
                    text = "Overview",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = overview,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Additional metadata
            Spacer(modifier = Modifier.height(24.dp))

            // Genres
            media.genres?.let { genreString ->
                if (genreString.isNotEmpty()) {
                    Text(
                        text = "Genres",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = genreString,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Bottom padding for overscan
            Spacer(modifier = Modifier.height(48.dp))
        }
    }
}

@Composable
private fun MetaChipsRow(
    year: Int?,
    rating: Float?,
    durationMinutes: Int?,
    quality: String?
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        year?.let {
            MetaChip(
                icon = Icons.Default.CalendarToday,
                text = it.toString()
            )
        }

        rating?.let {
            MetaChip(
                icon = Icons.Default.Star,
                text = String.format("%.1f", it),
                iconTint = FishColors.Rating
            )
        }

        durationMinutes?.let {
            MetaChip(
                icon = Icons.Default.Timelapse,
                text = "${it}m"
            )
        }

        quality?.let {
            MetaChip(
                icon = Icons.Default.Hd,
                text = it
            )
        }
    }
}

@Composable
private fun MetaChip(
    icon: ImageVector,
    text: String,
    iconTint: Color = Color.White.copy(alpha = 0.8f)
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(FishShapes.Chip)
            .background(Color.Black.copy(alpha = 0.4f))
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = Color.White
        )
    }
}

@Composable
private fun SourceBadgesRow(
    sourceTypes: List<SourceType>,
    selectedSource: MediaSourceRef?,
    onShowPicker: () -> Unit
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Available from:",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        sourceTypes.forEach { type ->
            SourceBadgeChip(sourceType = type)
        }

        if (sourceTypes.size > 1) {
            OutlinedButton(
                onClick = onShowPicker,
                modifier = Modifier.height(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Tune,
                    contentDescription = "Pick source",
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("Pick", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun ActionButtonsRow(
    canResume: Boolean,
    resumeProgress: Int,
    onPlay: () -> Unit,
    onResume: () -> Unit,
    onPlayFromStart: () -> Unit
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        if (canResume) {
            // Resume button (primary)
            Button(
                onClick = onResume,
                colors = ButtonDefaults.buttonColors(
                    containerColor = FishColors.Primary
                ),
                modifier = Modifier.weight(1f).height(56.dp)
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Resume ($resumeProgress%)")
            }

            // Play from start (secondary)
            OutlinedButton(
                onClick = onPlayFromStart,
                modifier = Modifier.weight(1f).height(56.dp)
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Start Over")
            }
        } else {
            // Single play button
            Button(
                onClick = onPlay,
                colors = ButtonDefaults.buttonColors(
                    containerColor = FishColors.Primary
                ),
                modifier = Modifier.fillMaxWidth().height(56.dp)
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Play")
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
                text = "Loading details...",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ErrorContent(
    error: String,
    onBack: () -> Unit
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
                text = "Couldn't load details",
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
            Button(onClick = onBack) {
                Text("Go Back")
            }
        }
    }
}
