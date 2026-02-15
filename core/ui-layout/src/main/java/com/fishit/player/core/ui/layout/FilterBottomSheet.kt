package com.fishit.player.core.ui.layout

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Filter configuration for content lists.
 */
data class UiFilterConfig(
    val hideAdult: Boolean = true,
    val selectedGenres: Set<String> = emptySet(),
    val excludedGenres: Set<String> = emptySet(),
    val minRating: Float? = null,
    val yearRange: IntRange? = null,
) {
    val hasActiveFilters: Boolean
        get() =
            hideAdult ||
                selectedGenres.isNotEmpty() ||
                excludedGenres.isNotEmpty() ||
                minRating != null ||
                yearRange != null

    val activeFilterCount: Int
        get() =
            listOf(
                hideAdult,
                selectedGenres.isNotEmpty(),
                excludedGenres.isNotEmpty(),
                minRating != null,
                yearRange != null,
            ).count { it }

    companion object {
        val DEFAULT = UiFilterConfig(hideAdult = true)
        val NONE = UiFilterConfig(hideAdult = false)
    }
}

/**
 * Bottom sheet for configuring content filters.
 *
 * Features:
 * - Adult content toggle
 * - Genre include/exclude chips
 * - Rating slider
 * - Year range slider
 * - Reset button
 *
 * @param isVisible Whether the sheet is visible
 * @param currentFilter Current filter configuration
 * @param availableGenres List of available genres
 * @param yearRange Available year range (min to max)
 * @param onFilterChanged Called when filter changes
 * @param onDismiss Called when sheet is dismissed
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FilterBottomSheet(
    isVisible: Boolean,
    currentFilter: UiFilterConfig,
    availableGenres: List<String> = emptyList(),
    yearRange: IntRange = 1900..2025,
    onFilterChanged: (UiFilterConfig) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Local state for filter changes
    var localFilter by remember(currentFilter) { mutableStateOf(currentFilter) }

    if (isVisible) {
        ModalBottomSheet(
            onDismissRequest = {
                onFilterChanged(localFilter)
                onDismiss()
            },
            sheetState = sheetState,
            modifier = modifier,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
            dragHandle = {
                Box(
                    modifier =
                        Modifier
                            .padding(vertical = 12.dp)
                            .width(32.dp)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)),
                )
            },
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .verticalScroll(rememberScrollState()),
            ) {
                // Header with reset button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Filter",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )

                    if (localFilter.hasActiveFilters) {
                        TextButton(
                            onClick = {
                                localFilter = UiFilterConfig.NONE
                            },
                        ) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = null,
                                modifier = Modifier.padding(end = 4.dp),
                            )
                            Text("Zurücksetzen")
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Adult content toggle
                FilterSection(title = "Inhalte") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Inhalte für Erwachsene ausblenden",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Switch(
                            checked = localFilter.hideAdult,
                            onCheckedChange = { hide ->
                                localFilter = localFilter.copy(hideAdult = hide)
                            },
                        )
                    }
                }

                // Genres
                if (availableGenres.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    FilterSection(title = "Genres") {
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            availableGenres.forEach { genre ->
                                val isSelected = genre in localFilter.selectedGenres
                                val isExcluded = genre in localFilter.excludedGenres

                                FilterChip(
                                    selected = isSelected || isExcluded,
                                    onClick = {
                                        localFilter =
                                            when {
                                                isSelected -> {
                                                    // Selected -> Excluded
                                                    localFilter.copy(
                                                        selectedGenres = localFilter.selectedGenres - genre,
                                                        excludedGenres = localFilter.excludedGenres + genre,
                                                    )
                                                }
                                                isExcluded -> {
                                                    // Excluded -> Unselected
                                                    localFilter.copy(
                                                        excludedGenres = localFilter.excludedGenres - genre,
                                                    )
                                                }
                                                else -> {
                                                    // Unselected -> Selected
                                                    localFilter.copy(
                                                        selectedGenres = localFilter.selectedGenres + genre,
                                                    )
                                                }
                                            }
                                    },
                                    label = {
                                        Text(
                                            text = genre,
                                            style = MaterialTheme.typography.labelMedium,
                                        )
                                    },
                                    leadingIcon =
                                        if (isSelected) {
                                            {
                                                Icon(
                                                    imageVector = Icons.Default.Check,
                                                    contentDescription = "Ausgewählt",
                                                )
                                            }
                                        } else if (isExcluded) {
                                            {
                                                Icon(
                                                    imageVector = Icons.Default.Clear,
                                                    contentDescription = "Ausgeschlossen",
                                                )
                                            }
                                        } else {
                                            null
                                        },
                                    colors =
                                        FilterChipDefaults.filterChipColors(
                                            selectedContainerColor =
                                                if (isExcluded) {
                                                    MaterialTheme.colorScheme.errorContainer
                                                } else {
                                                    MaterialTheme.colorScheme.primaryContainer
                                                },
                                            selectedLabelColor =
                                                if (isExcluded) {
                                                    MaterialTheme.colorScheme.onErrorContainer
                                                } else {
                                                    MaterialTheme.colorScheme.onPrimaryContainer
                                                },
                                        ),
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Tippe einmal zum Auswählen, zweimal zum Ausschließen",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                // Rating filter
                Spacer(modifier = Modifier.height(16.dp))
                FilterSection(title = "Mindestbewertung") {
                    val ratingValue = localFilter.minRating ?: 0f
                    Column {
                        Text(
                            text =
                                if (ratingValue > 0f) {
                                    "★ ${String.format("%.1f", ratingValue)} und höher"
                                } else {
                                    "Alle Bewertungen"
                                },
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(bottom = 8.dp),
                        )
                        Slider(
                            value = ratingValue,
                            onValueChange = { newValue ->
                                localFilter =
                                    localFilter.copy(
                                        minRating = if (newValue > 0f) newValue else null,
                                    )
                            },
                            valueRange = 0f..10f,
                            steps = 19, // 0.5 increments
                        )
                    }
                }

                // Year range filter
                Spacer(modifier = Modifier.height(16.dp))
                FilterSection(title = "Erscheinungsjahr") {
                    val rangeStart = (localFilter.yearRange?.first ?: yearRange.first).toFloat()
                    val rangeEnd = (localFilter.yearRange?.last ?: yearRange.last).toFloat()

                    Column {
                        Text(
                            text =
                                if (localFilter.yearRange != null) {
                                    "${rangeStart.toInt()} - ${rangeEnd.toInt()}"
                                } else {
                                    "Alle Jahre"
                                },
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(bottom = 8.dp),
                        )
                        RangeSlider(
                            value = rangeStart..rangeEnd,
                            onValueChange = { range ->
                                val newRange = range.start.toInt()..range.endInclusive.toInt()
                                localFilter =
                                    localFilter.copy(
                                        yearRange = if (newRange == yearRange) null else newRange,
                                    )
                            },
                            valueRange = yearRange.first.toFloat()..yearRange.last.toFloat(),
                        )
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

@Composable
private fun FilterSection(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        content()
    }
}
