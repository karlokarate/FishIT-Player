package com.chris.m3usuite.ui.layout

import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.unit.dp
import com.chris.m3usuite.ui.common.AppIcon
import com.chris.m3usuite.ui.common.AppIconButton
import com.chris.m3usuite.ui.focus.FocusKit

/** Shared action builders for Fish tiles. */
object FishActions {
    @Composable
    fun AssignBadge(selected: Boolean, onToggle: () -> Unit) {
        Surface(
            color = Color.Black.copy(alpha = 0.55f),
            contentColor = Color.White,
            shape = CircleShape,
            modifier = FocusKit.run {
                Modifier.tvClickable(focusBorderWidth = 0.dp, onClick = onToggle)
            }
        ) {
            val label = if (selected) "✓" else "+"
            Text(label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
        }
    }

    @Composable
    fun RowScope.VodBottomActions(onPlay: (() -> Unit)?, enabled: Boolean = true) {
        if (enabled && onPlay != null) {
            AppIconButton(
                icon = AppIcon.PlayCircle,
                contentDescription = "Abspielen",
                onClick = onPlay,
                size = 24.dp,
                modifier = Modifier.focusProperties { canFocus = false }
            )
        }
    }

    @Composable
    fun RowScope.AssignBottomAction(onAssign: (() -> Unit)?, enabled: Boolean = true) {
        if (enabled && onAssign != null) {
            AppIconButton(
                icon = AppIcon.BookmarkAdd,
                contentDescription = "Für Kinder freigeben",
                onClick = onAssign,
                size = 24.dp,
                modifier = Modifier.focusProperties { canFocus = false }
            )
        }
    }
}
