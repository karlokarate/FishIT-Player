package com.fishit.player.core.ui.layout

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.fishit.player.core.ui.theme.FishColors
import com.fishit.player.core.ui.theme.FishShapes

/**
 * Action model for media action buttons
 */
data class MediaAction(
    val id: MediaActionId,
    val label: String,
    val icon: ImageVector? = null,
    val enabled: Boolean = true,
    val primary: Boolean = false,
    val badge: String? = null,
    val onClick: () -> Unit,
)

/**
 * Predefined action types
 */
enum class MediaActionId {
    Play,
    Resume,
    Trailer,
    AddToList,
    RemoveFromList,
    OpenEpg,
    Share,
    AddToKids,
    Download,
    MarkWatched,
}

/**
 * Action button for media details
 */
@Composable
fun MediaActionButton(
    action: MediaAction,
    modifier: Modifier = Modifier,
) {
    val backgroundColor =
        if (action.primary) {
            FishColors.Primary
        } else {
            FishColors.SurfaceVariant
        }

    val contentColor =
        if (action.primary) {
            FishColors.OnPrimary
        } else {
            MaterialTheme.colorScheme.onSurface
        }

    Row(
        modifier =
            modifier
                .background(backgroundColor, FishShapes.Button)
                .clickable(enabled = action.enabled, onClick = action.onClick)
                .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        action.icon?.let { icon ->
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(20.dp),
            )
        }

        Text(
            text = action.badge?.let { "${action.label} • $it" } ?: action.label,
            style = MaterialTheme.typography.labelLarge,
            color = if (action.enabled) contentColor else contentColor.copy(alpha = 0.5f),
        )
    }
}

/**
 * Horizontal bar of media actions
 */
@Composable
fun MediaActionBar(
    actions: List<MediaAction>,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        actions.forEach { action ->
            MediaActionButton(action = action)
        }
    }
}
