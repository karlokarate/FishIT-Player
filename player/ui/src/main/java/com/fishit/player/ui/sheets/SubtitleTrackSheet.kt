package com.fishit.player.ui.sheets

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.ClosedCaptionOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fishit.player.core.playermodel.SubtitleSelectionState
import com.fishit.player.core.playermodel.SubtitleTrack
import com.fishit.player.core.playermodel.SubtitleTrackId
import com.fishit.player.internal.R

/**
 * Bottom sheet for subtitle track selection.
 *
 * Displays all available subtitle tracks with:
 * - Language display name
 * - Selection indicator for the current track
 * - "Off" option to disable subtitles
 *
 * Follows the TV-first design pattern for accessibility.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubtitleTrackSheet(
    subtitleState: SubtitleSelectionState,
    onSelectTrack: (SubtitleTrackId) -> Unit,
    onDisableSubtitles: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = 32.dp),
        ) {
            // Header
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Default.ClosedCaption,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = stringResource(R.string.player_subtitle_title),
                    style = MaterialTheme.typography.titleLarge,
                )
            }

            // "Off" option
            SubtitleOffItem(
                isSelected = subtitleState.selectedTrack == null,
                onClick = onDisableSubtitles,
            )

            if (subtitleState.availableTracks.isNotEmpty()) {
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 24.dp),
                )

                // Track list
                LazyColumn {
                    items(subtitleState.availableTracks) { track ->
                        SubtitleTrackItem(
                            track = track,
                            isSelected = track.id == subtitleState.selectedTrack?.id,
                            onClick = { onSelectTrack(track.id) },
                        )
                    }
                }
            } else if (subtitleState.selectedTrack == null) {
                // Only show empty state if there are no tracks and nothing is selected
                Text(
                    text = stringResource(R.string.player_subtitle_none),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                )
            }
        }
    }
}

@Composable
private fun SubtitleOffItem(
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    // TV Focus state for D-Pad navigation
    var isFocused by remember { mutableStateOf(false) }

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .onFocusChanged { isFocused = it.isFocused }
                .focusable()
                .then(
                    if (isFocused) {
                        Modifier.border(
                            width = 2.dp,
                            color = MaterialTheme.colorScheme.primary,
                            shape = RoundedCornerShape(8.dp),
                        )
                    } else {
                        Modifier
                    },
                ).clickable(onClick = onClick)
                .padding(horizontal = 24.dp, vertical = 12.dp)
                // Accessibility: announce selection state
                .semantics {
                    role = Role.RadioButton
                    selected = isSelected
                },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Selection indicator
        if (isSelected) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        } else {
            Spacer(modifier = Modifier.width(24.dp))
        }

        Spacer(modifier = Modifier.width(16.dp))

        Icon(
            imageVector = Icons.Default.ClosedCaptionOff,
            contentDescription = null,
            tint = if (isFocused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(modifier = Modifier.width(12.dp))

        Text(
            text = stringResource(R.string.player_subtitle_off),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = if (isFocused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun SubtitleTrackItem(
    track: SubtitleTrack,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    // TV Focus state for D-Pad navigation
    var isFocused by remember { mutableStateOf(false) }

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .onFocusChanged { isFocused = it.isFocused }
                .focusable()
                .then(
                    if (isFocused) {
                        Modifier.border(
                            width = 2.dp,
                            color = MaterialTheme.colorScheme.primary,
                            shape = RoundedCornerShape(8.dp),
                        )
                    } else {
                        Modifier
                    },
                ).clickable(onClick = onClick)
                .padding(horizontal = 24.dp, vertical = 12.dp)
                // Accessibility: announce selection state
                .semantics {
                    role = Role.RadioButton
                    selected = isSelected
                },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Selection indicator
        if (isSelected) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        } else {
            Spacer(modifier = Modifier.width(24.dp))
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.label,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                color = if (isFocused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            )
            if (track.isForced) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Forced",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
