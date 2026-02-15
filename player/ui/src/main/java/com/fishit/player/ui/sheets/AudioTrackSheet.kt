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
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.ExperimentalMaterial3Api
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
import com.fishit.player.core.playermodel.AudioChannelLayout
import com.fishit.player.core.playermodel.AudioSelectionState
import com.fishit.player.core.playermodel.AudioTrack
import com.fishit.player.core.playermodel.AudioTrackId
import com.fishit.player.internal.R

/**
 * Bottom sheet for audio track selection.
 *
 * Displays all available audio tracks with:
 * - Language display name
 * - Channel configuration (Stereo, 5.1, etc.)
 * - Selection indicator for the current track
 *
 * Follows the TV-first design pattern for accessibility.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioTrackSheet(
    audioState: AudioSelectionState,
    onSelectTrack: (AudioTrackId) -> Unit,
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
                    imageVector = Icons.Default.MusicNote,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = stringResource(R.string.player_audio_track_title),
                    style = MaterialTheme.typography.titleLarge,
                )
            }

            if (audioState.availableTracks.isEmpty()) {
                // Empty state
                Text(
                    text = stringResource(R.string.player_audio_track_none),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                )
            } else {
                // Track list
                LazyColumn {
                    items(audioState.availableTracks) { track ->
                        AudioTrackItem(
                            track = track,
                            isSelected = track.id == audioState.selectedTrack?.id,
                            onClick = { onSelectTrack(track.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AudioTrackItem(
    track: AudioTrack,
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
        // Selection indicator (width reserved for alignment)
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
            // Show channel layout as secondary info if not UNKNOWN
            val channelLayoutText =
                when (track.channelLayout) {
                    AudioChannelLayout.MONO -> "Mono"
                    AudioChannelLayout.STEREO -> "Stereo"
                    AudioChannelLayout.SURROUND_5_1 -> "5.1 Surround"
                    AudioChannelLayout.SURROUND_7_1 -> "7.1 Surround"
                    AudioChannelLayout.ATMOS -> "Dolby Atmos"
                    AudioChannelLayout.UNKNOWN -> null
                }
            if (channelLayoutText != null) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = channelLayoutText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
