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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import com.fishit.player.core.model.MediaSourceRef
import com.fishit.player.core.model.MediaType
import com.fishit.player.core.model.SourceType
import com.fishit.player.core.ui.theme.FishColors
import com.fishit.player.core.ui.theme.FishShapes
import com.fishit.player.feature.detail.UnifiedDetailEvent
import com.fishit.player.feature.detail.UnifiedDetailState
import com.fishit.player.feature.detail.UnifiedDetailViewModel
import com.fishit.player.feature.detail.ui.helper.DetailEpisodeItem
import com.fishit.player.feature.detail.ui.helper.DetailLiveSectionBadge
import com.fishit.player.feature.detail.ui.helper.DetailLiveSectionNowPlaying
import com.fishit.player.feature.detail.ui.helper.DetailSeriesSectionEpisodeList
import com.fishit.player.feature.detail.ui.helper.DetailSeriesSectionSeasonSelector

/**
 * Unified Detail Screen - Shows media details for ALL content types.
 *
 * This screen adapts its content based on the media type:
 * - **MOVIE/CLIP**: Standard detail view with Play/Resume buttons
 * - **SERIES**: Adds season selector and episode list
 * - **SERIES_EPISODE**: Shows episode info with link to series
 * - **LIVE**: Shows channel info with Live badge and EPG data
 * - **AUDIOBOOK/PODCAST**: Shows chapter/episode list
 *
 * Displays:
 * - Hero backdrop image
 * - Title, year, rating, duration (when applicable)
 * - Source badges for multi-source content
 * - MediaType-specific content sections
 * - Play/Resume actions
 * - Synopsis/Overview
 *
 * ## Unified ID Resolution
 *
 * The DetailScreen accepts a `mediaId` that can be either:
 * - A **canonical key** (e.g., `movie:inception:2010`) from Continue Watching, Recently Added
 * - A **source ID** (e.g., `msg:123:456`, `xtream:vod:123`) from Telegram/Xtream rows
 *
 * The ViewModel automatically detects the ID type and routes to the appropriate lookup.
 */
@Suppress("UNUSED_PARAMETER")
@Composable
fun DetailScreen(
    mediaId: String,
    sourceType: SourceType,
    onBack: () -> Unit,
    onPlayback: (UnifiedDetailEvent.StartPlayback) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: UnifiedDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    // Load media on first composition using smart ID detection
    LaunchedEffect(mediaId) { viewModel.loadByMediaId(mediaId) }

    // Handle events
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is UnifiedDetailEvent.StartPlayback -> onPlayback(event)
                else -> {
                    // Handle other events
                }
            }
        }
    }

    Box(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        when {
            state.isLoading -> {
                LoadingContent()
            }
            state.error != null -> {
                ErrorContent(error = state.error!!, onBack = onBack)
            }
            state.media != null -> {
                DetailContent(
                    state = state,
                    onBack = onBack,
                    onPlay = { if (state.isLive) viewModel.playLive() else viewModel.play() },
                    onResume = viewModel::resume,
                    onPlayFromStart = viewModel::playFromStart,
                    onShowSourcePicker = viewModel::showSourcePicker,
                    onSelectSource = viewModel::selectSource,
                    onSeasonSelected = viewModel::selectSeason,
                    onEpisodeClick = viewModel::playEpisode,
                )
            }
        }
    }
}

@Suppress("UNUSED_PARAMETER")
@Composable
private fun DetailContent(
    state: UnifiedDetailState,
    onBack: () -> Unit,
    onPlay: () -> Unit,
    onResume: () -> Unit,
    onPlayFromStart: () -> Unit,
    onShowSourcePicker: () -> Unit,
    onSelectSource: (MediaSourceRef) -> Unit,
    onSeasonSelected: (Int) -> Unit,
    onEpisodeClick: (DetailEpisodeItem) -> Unit,
) {
    val media = state.media ?: return
    val scrollState = rememberScrollState()

    Column(modifier = Modifier.fillMaxSize().verticalScroll(scrollState)) {
        // Hero Section with backdrop
        Box(modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f)) {
            // Backdrop image
            FishImage(
                imageRef = media.backdrop ?: media.poster,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )

            // Gradient overlay for text readability
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors =
                                    listOf(
                                        Color.Black.copy(alpha = 0.3f),
                                        Color.Black.copy(alpha = 0.1f),
                                        Color.Black.copy(alpha = 0.7f),
                                    ),
                            ),
                        ),
            )

            // Back button
            IconButton(
                onClick = onBack,
                modifier = Modifier.align(Alignment.TopStart).padding(16.dp),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White,
                )
            }

            // Live badge for live content
            if (state.isLive) {
                DetailLiveSectionBadge(
                    isLive = true,
                    modifier = Modifier.align(Alignment.TopEnd).padding(16.dp),
                )
            }

            // Title overlay at bottom
            Column(modifier = Modifier.align(Alignment.BottomStart).padding(24.dp)) {
                Text(
                    text = media.canonicalTitle,
                    style = MaterialTheme.typography.headlineMedium,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Meta chips row (adapted by media type)
                MetaChipsRow(
                    mediaType = state.effectiveMediaType,
                    year = media.year,
                    rating = media.rating?.toFloat(),
                    durationMs =
                        if (!state.isLive) {
                            state.activeSource?.durationMs ?: media.durationMs
                        } else {
                            null
                        },
                    quality = state.activeSourceQualityLabel,
                    season = media.season,
                    episode = media.episode,
                )
            }
        }

        // Content section
        Column(modifier = Modifier.padding(24.dp)) {
            // Source badges if multiple sources
            if (state.hasMultipleSources) {
                SourceBadgesRow(
                    sourceTypes = state.availableSourceTypes,
                    activeSource = state.activeSource,
                    onShowPicker = onShowSourcePicker,
                )
                Spacer(modifier = Modifier.height(20.dp))
            }

            // MediaType-specific content BEFORE action buttons
            when {
                state.isLive -> {
                    // Live: Show now playing info
                    state.liveNowPlaying?.let { program ->
                        DetailLiveSectionNowPlaying(
                            programTitle = program.title,
                            programDescription = program.description,
                            startTime = program.startTime,
                            endTime = program.endTime,
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                    }
                }
                state.isSeries -> {
                    // Series: Show season selector
                    if (state.seasons.isNotEmpty()) {
                        DetailSeriesSectionSeasonSelector(
                            seasons = state.seasons,
                            selectedSeason =
                                state.selectedSeason
                                    ?: state.seasons.firstOrNull() ?: 1,
                            onSeasonSelected = onSeasonSelected,
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
            }

            // Action buttons (adapted for media type)
            ActionButtonsRow(
                mediaType = state.effectiveMediaType,
                canResume = state.canResume && !state.isLive,
                resumeProgress = state.resumeProgressPercent,
                onPlay = onPlay,
                onResume = onResume,
                onPlayFromStart = onPlayFromStart,
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Series episodes (AFTER action buttons)
            if (state.isSeries) {
                if (state.episodesLoading) {
                    // Show loading indicator
                    Box(
                        modifier = Modifier.fillMaxWidth().height(200.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            CircularProgressIndicator()
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "Loading episodes...",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                } else if (state.displayedEpisodes.isNotEmpty()) {
                    DetailSeriesSectionEpisodeList(
                        episodes = state.displayedEpisodes,
                        onEpisodeClick = onEpisodeClick,
                    )
                } else {
                    // DEBUG: Show why no episodes are displayed
                    Card(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp),
                        colors =
                            CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                            ),
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                "🐛 DEBUG INFO:",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                            )
                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                "Seasons loaded: ${state.seasons}",
                                color = MaterialTheme.colorScheme.onErrorContainer,
                            )
                            Text(
                                "Selected season: ${state.selectedSeason}",
                                color = MaterialTheme.colorScheme.onErrorContainer,
                            )
                            Text(
                                "Total episodes count: ${state.episodes.size}",
                                color = MaterialTheme.colorScheme.onErrorContainer,
                            )
                            Text(
                                "Displayed episodes count: ${state.displayedEpisodes.size}",
                                color = MaterialTheme.colorScheme.onErrorContainer,
                            )

                            if (state.episodes.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    "First episode season number: ${state.episodes.first().season}",
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                )
                                Text(
                                    "First episode title: ${state.episodes.first().title}",
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                )

                                // Show season distribution
                                val seasonDistribution = state.episodes.groupingBy { it.season }.eachCount()
                                Text(
                                    "Episodes per season: $seasonDistribution",
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "Filter condition: selectedSeason=${state.selectedSeason}, episodes filtered by season=${state.selectedSeason}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }

            // Overview/Synopsis (not for live)
            if (!state.isLive) {
                media.plot?.let { overview ->
                    Text(
                        text = "Overview",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = overview,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                }

                // Genres
                media.genres?.let { genreString ->
                    if (genreString.isNotEmpty()) {
                        Text(
                            text = "Genres",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = genreString,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                // Director (for movies)
                media.director?.let { director ->
                    if (director.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Director",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = director,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                // Cast
                media.cast?.let { cast ->
                    if (cast.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Cast",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = cast,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // Bottom padding for overscan
            Spacer(modifier = Modifier.height(48.dp))
        }
    }
}

@Composable
private fun MetaChipsRow(
    mediaType: MediaType,
    year: Int?,
    rating: Float?,
    durationMs: Long?,
    quality: String?,
    season: Int? = null,
    episode: Int? = null,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Season/Episode chip for episodes
        if (mediaType == MediaType.SERIES_EPISODE && season != null && episode != null) {
            MetaChip(
                icon = null,
                text = "S${season}E$episode",
                backgroundColor = FishColors.Primary.copy(alpha = 0.8f),
            )
        }

        year?.let { MetaChip(icon = Icons.Default.CalendarToday, text = it.toString()) }

        rating?.let {
            MetaChip(
                icon = Icons.Default.Star,
                text = String.format("%.1f", it),
                iconTint = FishColors.Rating,
            )
        }

        durationMs?.let {
            val minutes = (it / 60_000).toInt()
            if (minutes > 0) {
                MetaChip(icon = Icons.Default.Timelapse, text = "${minutes}m")
            }
        }

        quality?.let { MetaChip(icon = Icons.Default.Hd, text = it) }
    }
}

@Composable
private fun MetaChip(
    icon: ImageVector?,
    text: String,
    iconTint: Color = Color.White.copy(alpha = 0.8f),
    backgroundColor: Color = Color.Black.copy(alpha = 0.4f),
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            Modifier
                .clip(FishShapes.Chip)
                .background(backgroundColor)
                .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        icon?.let {
            Icon(
                imageVector = it,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(16.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
        }
        Text(text = text, style = MaterialTheme.typography.labelMedium, color = Color.White)
    }
}

@Suppress("UNUSED_PARAMETER")
@Composable
private fun SourceBadgesRow(
    sourceTypes: List<SourceType>,
    activeSource: MediaSourceRef?,
    onShowPicker: () -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Available from:",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        sourceTypes.forEach { type -> SourceBadgeChip(sourceType = type) }

        if (sourceTypes.size > 1) {
            OutlinedButton(onClick = onShowPicker, modifier = Modifier.height(32.dp)) {
                Icon(
                    imageVector = Icons.Default.Tune,
                    contentDescription = "Pick source",
                    modifier = Modifier.size(16.dp),
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("Pick", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun ActionButtonsRow(
    mediaType: MediaType,
    canResume: Boolean,
    resumeProgress: Int,
    onPlay: () -> Unit,
    onResume: () -> Unit,
    onPlayFromStart: () -> Unit,
) {
    // Determine button label based on media type
    val playLabel =
        when (mediaType) {
            MediaType.LIVE -> "Watch Live"
            MediaType.AUDIOBOOK, MediaType.PODCAST -> "Listen"
            MediaType.SERIES -> "Play Latest"
            else -> "Play"
        }

    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth()) {
        if (canResume) {
            // Resume button (primary)
            Button(
                onClick = onResume,
                colors = ButtonDefaults.buttonColors(containerColor = FishColors.Primary),
                modifier = Modifier.weight(1f).height(56.dp),
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Resume ($resumeProgress%)")
            }

            // Play from start (secondary)
            OutlinedButton(
                onClick = onPlayFromStart,
                modifier = Modifier.weight(1f).height(56.dp),
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Start Over")
            }
        } else {
            // Single play button
            Button(
                onClick = onPlay,
                colors = ButtonDefaults.buttonColors(containerColor = FishColors.Primary),
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(playLabel)
            }
        }
    }
}

@Composable
private fun LoadingContent() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = FishColors.Primary, modifier = Modifier.size(48.dp))
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Loading details...",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ErrorContent(
    error: String,
    onBack: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = "😿", style = MaterialTheme.typography.displayMedium)
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Couldn't load details",
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
            Button(onClick = onBack) { Text("Go Back") }
        }
    }
}
