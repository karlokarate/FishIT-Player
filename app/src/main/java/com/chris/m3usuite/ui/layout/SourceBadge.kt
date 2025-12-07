package com.chris.m3usuite.ui.layout

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Help
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fishit.player.core.model.MediaSourceRef
import com.fishit.player.core.model.SourceBadge
import com.fishit.player.core.model.SourceType

/**
 * Visual badge component for identifying media source/pipeline.
 *
 * Displays a colored badge indicating which pipeline (Telegram, Xtream, IO, etc.) a media item
 * originates from. Essential for unified detail screens where multiple sources of the same media
 * are shown.
 *
 * Usage:
 * ```
 * SourceBadgeChip(SourceType.TELEGRAM)
 * SourceBadgeChip(SourceBadge.XTREAM, style = SourceBadgeStyle.COMPACT)
 * ```
 */

/** Badge display style */
enum class SourceBadgeStyle {
    /** Icon + Text (e.g., "📱 TG") - default for detail screens */
    FULL,
    /** Icon only - for tiles and compact spaces */
    ICON_ONLY,
    /** Text only (e.g., "TG") - for lists */
    TEXT_ONLY,
    /** Compact dot with letter - for tight spaces */
    COMPACT,
}

/** Badge colors by source type */
object SourceBadgeColors {
    val Telegram = Color(0xFF2AABEE) // Telegram blue
    val Xtream = Color(0xFF9C27B0) // Material purple
    val Io = Color(0xFF4CAF50) // Material green
    val Audiobook = Color(0xFFFF9800) // Material orange
    val Plex = Color(0xFFE5A00D) // Plex yellow/orange
    val Other = Color(0xFF9E9E9E) // Material grey

    fun forSourceType(type: SourceType): Color =
            when (type) {
                SourceType.TELEGRAM -> Telegram
                SourceType.XTREAM -> Xtream
                SourceType.IO -> Io
                SourceType.LOCAL -> Io
                SourceType.AUDIOBOOK -> Audiobook
                SourceType.PLEX -> Plex
                SourceType.OTHER -> Other
            }

    fun forBadge(badge: SourceBadge): Color =
            when (badge) {
                SourceBadge.TELEGRAM -> Telegram
                SourceBadge.XTREAM -> Xtream
                SourceBadge.IO -> Io
                SourceBadge.AUDIOBOOK -> Audiobook
                SourceBadge.PLEX -> Plex
                SourceBadge.OTHER -> Other
            }
}

/**
 * Source badge chip for tiles and detail screens.
 *
 * @param sourceType The pipeline source type
 * @param style Display style (FULL, ICON_ONLY, TEXT_ONLY, COMPACT)
 * @param modifier Modifier for customization
 */
@Composable
fun SourceBadgeChip(
        sourceType: SourceType,
        modifier: Modifier = Modifier,
        style: SourceBadgeStyle = SourceBadgeStyle.FULL,
) {
    val badge = SourceBadge.fromSourceType(sourceType)
    SourceBadgeChip(badge = badge, modifier = modifier, style = style)
}

/** Source badge chip from SourceBadge enum. */
@Composable
fun SourceBadgeChip(
        badge: SourceBadge,
        modifier: Modifier = Modifier,
        style: SourceBadgeStyle = SourceBadgeStyle.FULL,
) {
    val color = SourceBadgeColors.forBadge(badge)
    val icon = iconForBadge(badge)
    val text = badge.displayText

    when (style) {
        SourceBadgeStyle.FULL -> FullBadge(color, icon, text, modifier)
        SourceBadgeStyle.ICON_ONLY -> IconOnlyBadge(color, icon, modifier)
        SourceBadgeStyle.TEXT_ONLY -> TextOnlyBadge(color, text, modifier)
        SourceBadgeStyle.COMPACT -> CompactBadge(color, text.first(), modifier)
    }
}

/** Full badge with icon and text. */
@Composable
private fun FullBadge(
        color: Color,
        icon: ImageVector,
        text: String,
        modifier: Modifier = Modifier,
) {
    Row(
            modifier =
                    modifier.clip(RoundedCornerShape(12.dp))
                            .background(color.copy(alpha = 0.9f))
                            .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = Color.White,
        )
        Text(
                text = text,
                style =
                        MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp,
                        ),
                color = Color.White,
        )
    }
}

/** Icon-only badge (for tiles). */
@Composable
private fun IconOnlyBadge(
        color: Color,
        icon: ImageVector,
        modifier: Modifier = Modifier,
        size: Dp = 20.dp,
) {
    Box(
            modifier = modifier.size(size).clip(CircleShape).background(color.copy(alpha = 0.9f)),
            contentAlignment = Alignment.Center,
    ) {
        Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(size * 0.6f),
                tint = Color.White,
        )
    }
}

/** Text-only badge (for lists). */
@Composable
private fun TextOnlyBadge(
        color: Color,
        text: String,
        modifier: Modifier = Modifier,
) {
    Box(
            modifier =
                    modifier.clip(RoundedCornerShape(4.dp))
                            .background(color.copy(alpha = 0.9f))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
            contentAlignment = Alignment.Center,
    ) {
        Text(
                text = text,
                style =
                        MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 9.sp,
                        ),
                color = Color.White,
        )
    }
}

/** Compact dot badge with single letter (for tight spaces). */
@Composable
private fun CompactBadge(
        color: Color,
        letter: Char,
        modifier: Modifier = Modifier,
        size: Dp = 16.dp,
) {
    Box(
            modifier =
                    modifier.size(size)
                            .clip(CircleShape)
                            .background(color.copy(alpha = 0.9f))
                            .border(1.dp, Color.White.copy(alpha = 0.3f), CircleShape),
            contentAlignment = Alignment.Center,
    ) {
        Text(
                text = letter.toString(),
                style =
                        MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = (size.value * 0.5f).sp,
                        ),
                color = Color.White,
        )
    }
}

/** Get icon for badge type. */
private fun iconForBadge(badge: SourceBadge): ImageVector =
        when (badge) {
            SourceBadge.TELEGRAM -> TelegramIcon
            SourceBadge.XTREAM -> Icons.Default.PlayCircle
            SourceBadge.IO -> Icons.Default.Folder
            SourceBadge.AUDIOBOOK -> Icons.Default.Headphones
            SourceBadge.PLEX -> PlexIcon
            SourceBadge.OTHER -> Icons.Default.Help
        }

/**
 * Custom Telegram icon (simplified paper plane). Note: In production, use a proper vector asset.
 */
private val TelegramIcon: ImageVector
    get() = Icons.Default.PlayCircle // Placeholder - replace with actual Telegram vector

/** Custom Plex icon. Note: In production, use a proper vector asset. */
private val PlexIcon: ImageVector
    get() = Icons.Default.PlayCircle // Placeholder - replace with actual Plex vector

/**
 * Multi-source badge row showing all available sources.
 *
 * Use on detail screens to show which pipelines have this media available.
 *
 * @param sourceTypes List of source types
 * @param onSourceClick Callback when a source badge is clicked
 */
@Composable
fun SourceBadgeRow(
        sourceTypes: List<SourceType>,
        modifier: Modifier = Modifier,
        style: SourceBadgeStyle = SourceBadgeStyle.COMPACT,
        onSourceClick: ((SourceType) -> Unit)? = null,
) {
    if (sourceTypes.isEmpty()) return

    Row(
            modifier = modifier,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
    ) {
        sourceTypes.forEach { sourceType ->
            SourceBadgeChip(
                    sourceType = sourceType,
                    style = style,
            )
        }
    }
}

/**
 * "Available on" section for detail screens.
 *
 * Shows a label and badges for all sources where this media is available.
 */
@Composable
fun AvailableOnSection(
        sourceTypes: List<SourceType>,
        modifier: Modifier = Modifier,
        label: String = "Available on",
) {
    if (sourceTypes.isEmpty()) return

    Row(
            modifier = modifier,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SourceBadgeRow(
                sourceTypes = sourceTypes,
                style = SourceBadgeStyle.FULL,
        )
    }
}

/**
 * Quality badge for media sources.
 *
 * Shows resolution and codec info (e.g., "4K HDR HEVC").
 */
@Composable
fun QualityBadge(
        label: String,
        modifier: Modifier = Modifier,
        isHdr: Boolean = false,
        is4K: Boolean = false,
) {
    val backgroundColor =
            when {
                is4K && isHdr -> Color(0xFF6200EE) // Deep purple for 4K HDR
                is4K -> Color(0xFF1976D2) // Blue for 4K
                isHdr -> Color(0xFFE65100) // Orange for HDR
                else -> MaterialTheme.colorScheme.surfaceVariant
            }

    val textColor =
            when {
                is4K || isHdr -> Color.White
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            }

    Box(
            modifier =
                    modifier.clip(RoundedCornerShape(4.dp))
                            .background(backgroundColor)
                            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(
                text = label,
                style =
                        MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Medium,
                                fontSize = 9.sp,
                        ),
                color = textColor,
        )
    }
}

// ========== Source Comparison Components ==========
// For detail screens showing multiple sources of the same media

/**
 * Source comparison card for multi-source media.
 *
 * IMPORTANT: Different sources of the same media are NOT identical:
 * - Different file sizes (encoding, quality)
 * - Different durations (cuts, credits, frame rates)
 * - Different formats (container, codecs, languages)
 *
 * This card shows all differences to help users choose the best source.
 *
 * @param sourceRef The source reference to display
 * @param isSelected Whether this source is currently selected
 * @param resumePositionMs Resume position for THIS source (may differ from others!)
 * @param onClick Callback when card is clicked
 */
@Composable
fun SourceComparisonCard(
        sourceRef: com.fishit.player.core.model.MediaSourceRef,
        modifier: Modifier = Modifier,
        isSelected: Boolean = false,
        resumePositionMs: Long? = null,
        onClick: () -> Unit = {},
) {
    val borderColor =
            if (isSelected) {
                SourceBadgeColors.forSourceType(sourceRef.sourceType)
            } else {
                Color.Transparent
            }

    Card(
            modifier =
                    modifier.border(
                            width = if (isSelected) 2.dp else 0.dp,
                            color = borderColor,
                            shape = RoundedCornerShape(8.dp)
                    ),
            onClick = onClick,
    ) {
        Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Header: Badge + Label
            Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
            ) {
                SourceBadgeChip(sourceRef.sourceType, style = SourceBadgeStyle.FULL)
                Text(
                        text = sourceRef.sourceLabel,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        maxLines = 1,
                )
            }

            // Details row: Quality, Size, Duration
            Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
            ) {
                // Quality
                sourceRef.quality?.toDisplayLabel()?.takeIf { it.isNotBlank() }?.let { q ->
                    SourceDetailChip(label = q, icon = "📺")
                }

                // File size
                sourceRef.sizeBytes?.let { size ->
                    SourceDetailChip(label = formatFileSize(size), icon = "💾")
                }

                // Duration (IMPORTANT: varies per source!)
                sourceRef.durationMs?.let { duration ->
                    SourceDetailChip(
                            label = formatDuration(duration),
                            icon = "⏱️",
                            emphasis = true // Highlight that duration differs
                    )
                }
            }

            // Format and language info
            Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
            ) {
                sourceRef.format?.container?.let { container ->
                    SourceDetailChip(label = container.uppercase(), icon = "📦")
                }

                sourceRef.languages?.toDisplayLabel()?.takeIf { it.isNotBlank() }?.let { lang ->
                    SourceDetailChip(label = lang, icon = "🗣️")
                }
            }

            // Resume indicator (source-specific!)
            resumePositionMs?.takeIf { it > 0 }?.let { pos ->
                val duration = sourceRef.durationMs ?: 0L
                val percent = if (duration > 0) (pos.toFloat() / duration * 100).toInt() else 0
                SourceResumeIndicator(
                        positionMs = pos,
                        durationMs = duration,
                        percent = percent,
                )
            }
        }
    }
}

/** Detail chip for source comparison. */
@Composable
private fun SourceDetailChip(
        label: String,
        icon: String,
        modifier: Modifier = Modifier,
        emphasis: Boolean = false,
) {
    Row(
            modifier = modifier,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
                text = icon,
                style = MaterialTheme.typography.labelSmall,
        )
        Text(
                text = label,
                style =
                        MaterialTheme.typography.labelSmall.copy(
                                fontWeight = if (emphasis) FontWeight.Bold else FontWeight.Normal,
                        ),
                color =
                        if (emphasis) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
        )
    }
}

/** Resume progress indicator for a specific source. */
@Composable
private fun SourceResumeIndicator(
        positionMs: Long,
        durationMs: Long,
        percent: Int,
        modifier: Modifier = Modifier,
) {
    Column(
            modifier = modifier,
            verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                    text = "▶️ Resume at ${formatDuration(positionMs)}",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary,
            )
            Text(
                    text = "(${percent}%)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // Progress bar
        Box(
                modifier =
                        Modifier.fillMaxWidth()
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Box(
                    modifier =
                            Modifier.fillMaxWidth(percent / 100f)
                                    .height(4.dp)
                                    .background(MaterialTheme.colorScheme.primary)
            )
        }
    }
}

/**
 * Resume approximation notice.
 *
 * Shows when resuming on a different source than the one where playback stopped. The position is
 * approximated based on percentage, not exact milliseconds.
 */
@Composable
fun ResumeApproximationNotice(
        originalSourceLabel: String,
        currentSourceLabel: String,
        percent: Int,
        modifier: Modifier = Modifier,
) {
    Row(
            modifier =
                    modifier.clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.tertiaryContainer)
                            .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = "ℹ️", style = MaterialTheme.typography.bodyMedium)
        Column {
            Text(
                    text = "Resume position approximated",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
            )
            Text(
                    text =
                            "Last watched on $originalSourceLabel • Resuming at ~$percent% on $currentSourceLabel",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f),
            )
        }
    }
}

private fun formatFileSize(bytes: Long): String {
    val gb = bytes / (1024.0 * 1024.0 * 1024.0)
    val mb = bytes / (1024.0 * 1024.0)
    return when {
        gb >= 1.0 -> String.format("%.1f GB", gb)
        mb >= 1.0 -> String.format("%.0f MB", mb)
        else -> String.format("%d KB", bytes / 1024)
    }
}

private fun formatDuration(ms: Long): String {
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return when {
        hours > 0 -> String.format("%d:%02d:%02d", hours, minutes, seconds)
        else -> String.format("%d:%02d", minutes, seconds)
    }
}
