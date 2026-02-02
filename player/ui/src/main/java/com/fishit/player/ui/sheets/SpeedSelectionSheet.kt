package com.fishit.player.ui.sheets

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Speed
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fishit.player.internal.R

/**
 * Playback speed options with display labels.
 * Industry standard: 0.5x - 2x (Netflix, YouTube, Plex)
 * 0.25x removed as it's rarely used and clutters UI.
 */
private val SPEED_OPTIONS = listOf(
    0.5f to "0.5x",
    0.75f to "0.75x",
    1.0f to "Normal",
    1.25f to "1.25x",
    1.5f to "1.5x",
    1.75f to "1.75x",
    2.0f to "2x",
)

/**
 * Bottom sheet for playback speed selection.
 *
 * Displays common playback speed options (0.25x to 2x).
 * Highlights the current speed with a checkmark.
 *
 * Follows the TV-first design pattern for accessibility.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpeedSelectionSheet(
    currentSpeed: Float,
    onSelectSpeed: (Float) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp),
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Default.Speed,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = stringResource(R.string.player_speed_title),
                    style = MaterialTheme.typography.titleLarge,
                )
            }

            // Speed options
            LazyColumn {
                items(SPEED_OPTIONS) { (speed, label) ->
                    SpeedOptionItem(
                        speed = speed,
                        label = if (speed == 1.0f) stringResource(R.string.player_speed_normal) else label,
                        isSelected = kotlin.math.abs(currentSpeed - speed) < 0.01f,
                        onClick = { onSelectSpeed(speed) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SpeedOptionItem(
    speed: Float,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    // TV Focus state for D-Pad navigation
    var isFocused by remember { mutableStateOf(false) }
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .then(
                if (isFocused) {
                    Modifier.border(
                        width = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(8.dp)
                    )
                } else {
                    Modifier
                }
            )
            .clickable(onClick = onClick)
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

        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = if (isSelected || isFocused) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurface
            },
        )
    }
}
