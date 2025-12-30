package com.fishit.player.feature.detail.ui.helper

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Timelapse
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.fishit.player.core.imaging.compose.FishImage
import com.fishit.player.core.model.CanonicalMediaId
import com.fishit.player.core.model.ImageRef
import com.fishit.player.core.model.MediaSourceRef
import com.fishit.player.core.ui.theme.FishColors
import com.fishit.player.core.ui.theme.FishShapes

/**
 * Helper composables for MediaType-specific sections in DetailScreen.
 *
 * Naming Convention:
 * - `DetailSeriesSection*` - Series/Episode specific helpers
 * - `DetailLiveSection*` - Live TV specific helpers
 * - `DetailAudioSection*` - Audiobook/Podcast specific helpers
 *
 * These are internal helpers, not standalone screens.
 */

// =============================================================================
// Series/Episode Section Helpers
// =============================================================================

/**
 * Data class for episode display in series detail.
 */
data class DetailEpisodeItem(
    val id: String,
    val canonicalId: CanonicalMediaId,
    val season: Int,
    val episode: Int,
    val title: String,
    val thumbnail: ImageRef?,
    val durationMs: Long?,
    val plot: String?,
    val sources: List<MediaSourceRef> = emptyList(),
    val hasResume: Boolean = false,
    val resumePercent: Int = 0,
)

/**
 * Season selector row with chips for each season.
 */
@Composable
fun DetailSeriesSectionSeasonSelector(
    seasons: List<Int>,
    selectedSeason: Int,
    onSeasonSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = "Staffeln",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(modifier = Modifier.height(8.dp))
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(end = 16.dp),
        ) {
            items(seasons) { season ->
                FilterChip(
                    selected = season == selectedSeason,
                    onClick = { onSeasonSelected(season) },
                    label = { Text("Staffel $season") },
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

/**
 * Episode list for the selected season.
 */
@Composable
fun DetailSeriesSectionEpisodeList(
    episodes: List<DetailEpisodeItem>,
    onEpisodeClick: (DetailEpisodeItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = "Episoden",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(modifier = Modifier.height(12.dp))
        episodes.forEach { episode ->
            DetailSeriesSectionEpisodeCard(
                episode = episode,
                onClick = { onEpisodeClick(episode) },
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

/**
 * Single episode card with thumbnail, title, and play button.
 */
@Composable
fun DetailSeriesSectionEpisodeCard(
    episode: DetailEpisodeItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Thumbnail with play overlay
            Box(
                modifier =
                    Modifier
                        .size(120.dp, 68.dp)
                        .clip(RoundedCornerShape(6.dp)),
            ) {
                FishImage(
                    imageRef = episode.thumbnail,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.matchParentSize(),
                )
                // Play icon overlay
                Box(
                    modifier =
                        Modifier
                            .matchParentSize()
                            .background(Color.Black.copy(alpha = 0.3f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Play",
                        tint = Color.White,
                        modifier = Modifier.size(32.dp),
                    )
                }
                // Resume progress bar
                if (episode.hasResume && episode.resumePercent > 0) {
                    Box(
                        modifier =
                            Modifier
                                .align(Alignment.BottomStart)
                                .fillMaxWidth()
                                .height(3.dp)
                                .background(Color.Gray.copy(alpha = 0.5f)),
                    ) {
                        Box(
                            modifier =
                                Modifier
                                    .fillMaxWidth(episode.resumePercent / 100f)
                                    .height(3.dp)
                                    .background(FishColors.Primary),
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Episode info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "S${episode.season}E${episode.episode}",
                    style = MaterialTheme.typography.labelMedium,
                    color = FishColors.Primary,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = episode.title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                episode.durationMs?.let { durationMs ->
                    val minutes = (durationMs / 60_000).toInt()
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Timelapse,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "${minutes}m",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

// =============================================================================
// Live TV Section Helpers
// =============================================================================

/**
 * Live TV indicator badge with pulsing dot.
 */
@Composable
fun DetailLiveSectionBadge(
    isLive: Boolean = true,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            modifier
                .clip(FishShapes.Chip)
                .background(if (isLive) Color.Red else Color.Gray)
                .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Icon(
            imageVector = if (isLive) Icons.Default.Circle else Icons.Default.LiveTv,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(8.dp),
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = if (isLive) "LIVE" else "TV",
            style = MaterialTheme.typography.labelMedium,
            color = Color.White,
            fontWeight = FontWeight.Bold,
        )
    }
}

/**
 * Now playing info for live channel (EPG data).
 */
@Composable
fun DetailLiveSectionNowPlaying(
    programTitle: String?,
    programDescription: String?,
    startTime: String?,
    endTime: String?,
    modifier: Modifier = Modifier,
) {
    if (programTitle == null) return

    Column(modifier = modifier) {
        Text(
            text = "Jetzt läuft",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = programTitle,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.Bold,
        )
        if (startTime != null && endTime != null) {
            Text(
                text = "$startTime - $endTime",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        programDescription?.let { desc ->
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = desc,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

// =============================================================================
// Audio (Audiobook/Podcast) Section Helpers
// =============================================================================

/**
 * Data class for audio chapter/track display.
 */
data class DetailAudioChapter(
    val id: String,
    val number: Int,
    val title: String,
    val durationMs: Long,
    val hasResume: Boolean = false,
    val resumePercent: Int = 0,
)

/**
 * Chapter list for audiobooks or podcast episodes.
 */
@Composable
fun DetailAudioSectionChapterList(
    chapters: List<DetailAudioChapter>,
    onChapterClick: (DetailAudioChapter) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = "Kapitel",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(modifier = Modifier.height(12.dp))
        chapters.forEach { chapter ->
            DetailAudioSectionChapterRow(
                chapter = chapter,
                onClick = { onChapterClick(chapter) },
            )
            Spacer(modifier = Modifier.height(4.dp))
        }
    }
}

/**
 * Single chapter row for audio content.
 */
@Composable
fun DetailAudioSectionChapterRow(
    chapter: DetailAudioChapter,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Chapter number
            Box(
                modifier =
                    Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(FishColors.Primary.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "${chapter.number}",
                    style = MaterialTheme.typography.titleMedium,
                    color = FishColors.Primary,
                    fontWeight = FontWeight.Bold,
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Chapter title and duration
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = chapter.title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val minutes = (chapter.durationMs / 60_000).toInt()
                Text(
                    text = "$minutes Min.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // Resume indicator
            if (chapter.hasResume) {
                Text(
                    text = "${chapter.resumePercent}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = FishColors.Primary,
                )
            }

            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = "Play",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
