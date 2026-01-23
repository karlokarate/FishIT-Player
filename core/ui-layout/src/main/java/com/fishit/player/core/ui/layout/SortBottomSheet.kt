package com.fishit.player.core.ui.layout

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Sort field for content lists.
 */
enum class UiSortField(val displayName: String) {
    TITLE("Name"),
    YEAR("Jahr"),
    RATING("Bewertung"),
    RECENTLY_ADDED("Zuletzt hinzugefügt"),
    RECENTLY_UPDATED("Zuletzt aktualisiert"),
    DURATION("Laufzeit"),
}

/**
 * Sort direction.
 */
enum class UiSortDirection {
    ASCENDING,
    DESCENDING,
}

/**
 * Combined sort option.
 */
data class UiSortOption(
    val field: UiSortField,
    val direction: UiSortDirection = when (field) {
        UiSortField.TITLE -> UiSortDirection.ASCENDING
        else -> UiSortDirection.DESCENDING
    },
) {
    companion object {
        val DEFAULT = UiSortOption(UiSortField.TITLE)
    }
}

/**
 * Bottom sheet for selecting sort options.
 *
 * Features:
 * - List of sort fields with icons
 * - Toggle sort direction
 * - Visual indicator of current selection
 * - Animated transitions
 *
 * @param isVisible Whether the sheet is visible
 * @param currentSort Current sort option
 * @param availableFields List of available sort fields
 * @param onSortSelected Called when a sort option is selected
 * @param onDismiss Called when the sheet is dismissed
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SortBottomSheet(
    isVisible: Boolean,
    currentSort: UiSortOption,
    availableFields: List<UiSortField> = UiSortField.entries,
    onSortSelected: (UiSortOption) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sheetState = rememberModalBottomSheetState()

    if (isVisible) {
        ModalBottomSheet(
            onDismissRequest = onDismiss,
            sheetState = sheetState,
            modifier = modifier,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
            dragHandle = {
                Box(
                    modifier = Modifier
                        .padding(vertical = 12.dp)
                        .width(32.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)),
                )
            },
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                // Header
                Text(
                    text = "Sortieren nach",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 16.dp),
                )

                // Sort options
                availableFields.forEach { field ->
                    val isSelected = currentSort.field == field
                    SortOptionItem(
                        field = field,
                        direction = if (isSelected) currentSort.direction else null,
                        isSelected = isSelected,
                        onClick = {
                            val newDirection = if (isSelected) {
                                // Toggle direction
                                if (currentSort.direction == UiSortDirection.ASCENDING) {
                                    UiSortDirection.DESCENDING
                                } else {
                                    UiSortDirection.ASCENDING
                                }
                            } else {
                                // Use default direction for field
                                when (field) {
                                    UiSortField.TITLE -> UiSortDirection.ASCENDING
                                    else -> UiSortDirection.DESCENDING
                                }
                            }
                            onSortSelected(UiSortOption(field, newDirection))
                        },
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun SortOptionItem(
    field: UiSortField,
    direction: UiSortDirection?,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val backgroundColor = if (isSelected) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
    } else {
        Color.Transparent
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        color = backgroundColor,
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Selection indicator
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                }

                Text(
                    text = field.displayName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                )
            }

            // Direction indicator
            if (direction != null) {
                Icon(
                    imageVector = if (direction == UiSortDirection.ASCENDING) {
                        Icons.Default.ArrowUpward
                    } else {
                        Icons.Default.ArrowDownward
                    },
                    contentDescription = if (direction == UiSortDirection.ASCENDING) {
                        "Aufsteigend"
                    } else {
                        "Absteigend"
                    },
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}
