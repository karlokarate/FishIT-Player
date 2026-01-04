package com.fishit.player.core.ui.layout

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.fishit.player.core.ui.theme.FishColors
import com.fishit.player.core.ui.theme.FishShapes

/**
 * FishChipRow
 *
 * TV + mobile friendly chip selector row.
 *
 * Goals (Gold patterns):
 * - DPAD focus support with visible focus indicator
 * - Simple, reusable selection pattern for filters, seasons, tabs
 * - No dependency on feature modules
 */
@Composable
fun <T> FishChipRow(
    title: String,
    items: List<T>,
    selectedItem: T?,
    itemLabel: (T) -> String,
    onItemSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(end = 16.dp),
) {
    Column(modifier = modifier) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(modifier = Modifier.height(8.dp))

        LazyRow(
            modifier = Modifier.focusGroup(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = contentPadding,
        ) {
            items(items) { item ->
                val isSelected = selectedItem == item
                FishChip(
                    label = itemLabel(item),
                    selected = isSelected,
                    onClick = { onItemSelected(item) },
                )
            }
        }
    }
}

@Composable
private fun FishChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = if (selected) FishColors.Primary else MaterialTheme.colorScheme.surfaceVariant,
        contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
        shape = FishShapes.Chip,
        modifier =
            modifier
                .clip(FishShapes.Chip)
                .tvFocusable(
                    onClick = onClick,
                    enableScale = true,
                    enableGlow = true,
                ).padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(
            text = label,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}
