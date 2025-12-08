package com.chris.m3usuite.ui.state

import androidx.compose.runtime.staticCompositionLocalOf
import com.chris.m3usuite.model.MediaItem

data class AssignSelectionContext(
    val enabled: Boolean,
    val isSelected: (MediaItem) -> Boolean,
    val toggle: (MediaItem) -> Unit,
    // Start selection from tiles even when not in assign mode yet.
    // Implemented by StartScreen to enable assign mode and select the given item.
    val start: (MediaItem) -> Unit = { _ -> },
)

val LocalAssignSelection =
    staticCompositionLocalOf<AssignSelectionContext> {
        AssignSelectionContext(
            enabled = false,
            isSelected = { false },
            toggle = { _ -> },
            start = { _ -> },
        )
    }

// Controls whether tiles should render a global assign badge.
// Default false; StartScreen provides this as true only for profiles with permission.
val LocalAssignBadgeVisible = staticCompositionLocalOf { false }
