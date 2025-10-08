package com.chris.m3usuite.ui.layout

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.chris.m3usuite.ui.focus.FocusKit

/**
 * Generic resume tile for both VOD and Series episodes.
 * Shows a two-line title, subtitle (e.g., progress), and Play/Clear actions passed by the caller.
 */
@Composable
fun FishResumeTile(
    title: String,
    subtitle: String?,
    onPlay: () -> Unit,
    onClear: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val shape: Shape = RoundedCornerShape(14.dp)
    var focused by remember { mutableStateOf(false) }
    Card(
        modifier = modifier
            .onFocusEvent { focused = it.isFocused || it.hasFocus }
            .then(
                FocusKit.run {
                    Modifier
                        .focusScaleOnTv(
                            focusedScale = null, // defaults per device profile
                            pressedScale = null,
                            focusBorderWidth = 2.5.dp
                        )
                }
            )
            .then(
                FocusKit.run { Modifier.tvFocusGlow(focused = focused, shape = shape, ringWidth = 5.dp) }
            )
            .width(200.dp)
            .height(140.dp)
            .then(
                FocusKit.run { Modifier.tvClickable(onClick = onPlay) }
            ),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = shape
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(Modifier.fillMaxWidth()) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2
                )
                subtitle?.let {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                com.chris.m3usuite.ui.common.AppIconButton(
                    icon = com.chris.m3usuite.ui.common.AppIcon.PlayCircle,
                    contentDescription = "Abspielen",
                    onClick = onPlay,
                    size = 22.dp
                )
                if (onClear != null) {
                    com.chris.m3usuite.ui.common.AppIconButton(
                        icon = com.chris.m3usuite.ui.common.AppIcon.RemoveKid, // reuse icon; consider a dedicated clear icon
                        contentDescription = "Entfernen",
                        onClick = onClear,
                        size = 22.dp
                    )
                }
            }
        }
    }
}
