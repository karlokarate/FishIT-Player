package com.fishit.player.feature.detail.ui.helper

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.fishit.player.core.imaging.compose.FishImage
import com.fishit.player.core.model.CanonicalMediaId
import com.fishit.player.core.model.ImageRef
import com.fishit.player.core.model.MediaSourceRef
import com.fishit.player.core.ui.layout.FishChipRow
import com.fishit.player.core.ui.theme.FishColors
import com.fishit.player.core.ui.theme.FishShapes
import kotlinx.coroutines.delay

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

/** Data class for episode display in series detail. */
data class DetailEpisodeItem(
    val id: String,
    val canonicalId: CanonicalMediaId,
    val season: Int,
    val episode: Int,
    val title: String,
    val thumbnail: ImageRef?,
    val durationMs: Long?,
    /** Full plot — unlimited, never truncated */
    val plot: String?,
    val rating: Double? = null,
    val airDate: String? = null,
    /** Video quality height for badges (e.g. 1080 → "HD", 2160 → "4K") */
    val qualityHeight: Int? = null,
    /** Video codec for badges (e.g. "hevc" → "HEVC") */
    val videoCodec: String? = null,
    /** Audio codec for badges (e.g. "ac3" → "AC3") */
    val audioCodec: String? = null,
    /** Audio channels for badges (e.g. 6 → "5.1") */
    val audioChannels: Int? = null,
    val sources: List<MediaSourceRef> = emptyList(),
    val hasResume: Boolean = false,
    val resumePercent: Int = 0,
)

/** Season selector row with chips for each season. */
@Composable
fun DetailSeriesSectionSeasonSelector(
    seasons: List<Int>,
    selectedSeason: Int,
    onSeasonSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    FishChipRow(
        title = "Staffeln",
        items = seasons,
        selectedItem = selectedSeason,
        itemLabel = { season -> "Staffel $season" },
        onItemSelected = onSeasonSelected,
        modifier = modifier,
    )
}

/**
 * Vertical episode list for the selected season.
 * Episodes are listed top-to-bottom (Episode 1 → x).
 * Each row: thumbnail left, title + metadata right.
 *
 * Interactions:
 * - **TV**: DPAD focus with 500ms delay → plot expands (animated). Unfocus → collapses.
 * - **Mobile**: Short tap = play. Long press = toggle plot visibility.
 */
@Composable
fun DetailSeriesSectionEpisodeList(
    episodes: List<DetailEpisodeItem>,
    onEpisodeClick: (DetailEpisodeItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        episodes.forEach { episode ->
            DetailSeriesSectionEpisodeRow(
                episode = episode,
                onPlay = { onEpisodeClick(episode) },
            )
        }
    }
}

/**
 * Single episode row — horizontal layout: thumbnail left (120×68dp), info right.
 *
 * - Quality badge overlaid on thumbnail (top-right: 4K / FHD / HD)
 * - Play icon overlaid on thumbnail center
 * - Resume progress bar at thumbnail bottom
 * - Title + episode number, duration, metadata badges (rating, airDate, codec, audio)
 * - Animated expandable plot section below
 *
 * TV: focus + 500ms → auto-expand plot, unfocus → auto-collapse.
 * Mobile: long-press toggles plot, short tap = play.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DetailSeriesSectionEpisodeRow(
    episode: DetailEpisodeItem,
    onPlay: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var plotExpanded by remember { mutableStateOf(false) }
    var isFocused by remember { mutableStateOf(false) }

    // TV: auto-expand plot after focus delay, collapse when unfocused
    LaunchedEffect(isFocused) {
        if (isFocused) {
            delay(500L)
            plotExpanded = true
        } else {
            plotExpanded = false
        }
    }

    val focusBorderColor = if (isFocused) FishColors.FocusGlow else Color.Transparent

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (isFocused) {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        },
        modifier = modifier
            .fillMaxWidth()
            .border(
                width = if (isFocused) 2.dp else 0.dp,
                color = focusBorderColor,
                shape = RoundedCornerShape(8.dp),
            )
            .focusable()
            .onFocusChanged { focusState -> isFocused = focusState.isFocused }
            .combinedClickable(
                onClick = onPlay,
                onLongClick = { plotExpanded = !plotExpanded },
            ),
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            // ── Main row: thumbnail + info ──
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Thumbnail (left) — standardized 120×68dp
                Box(
                    modifier = Modifier
                        .size(width = 120.dp, height = 68.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    if (episode.thumbnail != null) {
                        FishImage(
                            imageRef = episode.thumbnail,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.matchParentSize(),
                        )
                    }

                    // Play icon (center)
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .background(Color.Black.copy(alpha = 0.25f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Play",
                            tint = Color.White.copy(alpha = 0.9f),
                            modifier = Modifier.size(28.dp),
                        )
                    }

                    // Quality badge (top-right)
                    EpisodeQualityBadge(
                        qualityHeight = episode.qualityHeight,
                        modifier = Modifier.align(Alignment.TopEnd),
                    )

                    // Resume progress bar (bottom)
                    if (episode.hasResume && episode.resumePercent > 0) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .fillMaxWidth()
                                .height(3.dp)
                                .background(Color.Gray.copy(alpha = 0.5f)),
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(episode.resumePercent / 100f)
                                    .height(3.dp)
                                    .background(FishColors.Primary),
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Info section (right)
                Column(modifier = Modifier.weight(1f)) {
                    // Title
                    Text(
                        text = episode.title,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = FontWeight.SemiBold,
                    )

                    Spacer(modifier = Modifier.height(2.dp))

                    // Episode number + duration
                    Text(
                        text = buildString {
                            append("S${episode.season} • E${episode.episode}")
                            episode.durationMs?.let { ms ->
                                val minutes = (ms / 60_000).toInt()
                                if (minutes > 0) append(" • ${minutes} min")
                            }
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    // Metadata badges row
                    EpisodeMetadataBadges(episode = episode)
                }
            }

            // ── Expandable plot section (animated) ──
            AnimatedVisibility(
                visible = plotExpanded && !episode.plot.isNullOrBlank(),
                enter = expandVertically(
                    expandFrom = Alignment.Top,
                ) + fadeIn(),
                exit = shrinkVertically(
                    shrinkTowards = Alignment.Top,
                ) + fadeOut(),
            ) {
                Column(modifier = Modifier.padding(top = 8.dp)) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 120.dp)
                            .verticalScroll(rememberScrollState()),
                    ) {
                        Text(
                            text = episode.plot ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

/** Quality badge overlay — renders 4K / FHD / HD on thumbnail. */
@Composable
private fun EpisodeQualityBadge(
    qualityHeight: Int?,
    modifier: Modifier = Modifier,
) {
    val label = qualityHeight?.let { h ->
        com.fishit.player.core.model.util.ResolutionLabel.badgeLabel(h)
    }
    if (label != null) {
        Box(
            modifier = modifier
                .padding(4.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(FishColors.Primary.copy(alpha = 0.85f))
                .padding(horizontal = 6.dp, vertical = 2.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

/** Metadata badges row — rating, airDate, video/audio codec, channels. */
@Composable
private fun EpisodeMetadataBadges(
    episode: DetailEpisodeItem,
    modifier: Modifier = Modifier,
) {
    val badges = buildList {
        episode.rating?.let { add("★ ${"%.1f".format(it)}") }
        episode.airDate?.takeIf { it.isNotBlank() }?.let { add(it) }
        episode.videoCodec?.uppercase()?.let { add(it) }
        episode.audioCodec?.uppercase()?.let { add(it) }
        episode.audioChannels?.let { ch ->
            val label = when (ch) {
                1 -> "Mono"
                2 -> "Stereo"
                6 -> "5.1"
                8 -> "7.1"
                else -> "${ch}ch"
            }
            add(label)
        }
    }
    if (badges.isNotEmpty()) {
        Spacer(modifier = Modifier.height(4.dp))
        Row(
            modifier = modifier,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            badges.forEach { badge ->
                Text(
                    text = badge,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}

// =============================================================================
// Live TV Section Helpers
// =============================================================================

/** Live TV indicator badge with pulsing dot. */
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

/** Now playing info for live channel (EPG data). */
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

/** Data class for audio chapter/track display. */
data class DetailAudioChapter(
    val id: String,
    val number: Int,
    val title: String,
    val durationMs: Long,
    val hasResume: Boolean = false,
    val resumePercent: Int = 0,
)

/** Chapter list for audiobooks or podcast episodes. */
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

/** Single chapter row for audio content. */
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
