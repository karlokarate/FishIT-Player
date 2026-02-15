package com.fishit.player.core.ui.layout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Combined sort and filter bar with chips.
 *
 * Displays current sort and filter status with interactive chips
 * that open the respective bottom sheets.
 *
 * @param currentSort Current sort option
 * @param currentFilter Current filter configuration
 * @param availableSortFields Available sort fields for this content type
 * @param availableGenres Available genres for filtering
 * @param yearRange Available year range
 * @param onSortChanged Called when sort changes
 * @param onFilterChanged Called when filter changes
 */
@Composable
fun SortFilterBar(
    currentSort: UiSortOption,
    currentFilter: UiFilterConfig,
    availableSortFields: List<UiSortField> = UiSortField.entries,
    availableGenres: List<String> = emptyList(),
    yearRange: IntRange = 1900..2025,
    onSortChanged: (UiSortOption) -> Unit,
    onFilterChanged: (UiFilterConfig) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showSortSheet by remember { mutableStateOf(false) }
    var showFilterSheet by remember { mutableStateOf(false) }

    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Sort chip
        FilterChip(
            selected = true,
            onClick = { showSortSheet = true },
            label = {
                Text(
                    text = currentSort.field.displayName,
                    style = MaterialTheme.typography.labelMedium,
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Sort,
                    contentDescription = "Sortieren",
                    modifier = Modifier.size(18.dp),
                )
            },
            colors =
                FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    selectedLeadingIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                ),
        )

        // Filter chip with badge
        BadgedBox(
            badge = {
                if (currentFilter.activeFilterCount > 0) {
                    Badge(
                        containerColor = MaterialTheme.colorScheme.primary,
                    ) {
                        Text(
                            text = currentFilter.activeFilterCount.toString(),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
            },
        ) {
            FilterChip(
                selected = currentFilter.hasActiveFilters,
                onClick = { showFilterSheet = true },
                label = {
                    Text(
                        text = "Filter",
                        style = MaterialTheme.typography.labelMedium,
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.FilterList,
                        contentDescription = "Filtern",
                        modifier = Modifier.size(18.dp),
                    )
                },
                colors =
                    FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ),
            )
        }
    }

    // Sort bottom sheet
    SortBottomSheet(
        isVisible = showSortSheet,
        currentSort = currentSort,
        availableFields = availableSortFields,
        onSortSelected = { sort ->
            onSortChanged(sort)
            showSortSheet = false
        },
        onDismiss = { showSortSheet = false },
    )

    // Filter bottom sheet
    FilterBottomSheet(
        isVisible = showFilterSheet,
        currentFilter = currentFilter,
        availableGenres = availableGenres,
        yearRange = yearRange,
        onFilterChanged = onFilterChanged,
        onDismiss = { showFilterSheet = false },
    )
}
