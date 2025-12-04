package com.chris.m3usuite.ui.common

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.chris.m3usuite.R
import com.chris.m3usuite.core.logging.UnifiedLog
import com.chris.m3usuite.ui.debug.safePainter
import com.chris.m3usuite.ui.focus.FocusKit

@Composable
fun AppIconButton(
    icon: AppIcon,
    variant: IconVariant = IconVariant.Primary,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 28.dp,
    tvFocusOverlay: Color = Color.White.copy(alpha = 0.22f),
    tvFocusBorder: Color = Color.White.copy(alpha = 0.75f),
    focusBorderWidth: Dp = 1.5.dp,
) {
    val context = LocalContext.current
    val isTv = remember(context) { FocusKit.isTvDevice(context) }
    val interactionSource = remember { MutableInteractionSource() }
    var hasFocus by remember { mutableStateOf(false) }
    val focusShape = remember { RoundedCornerShape(14.dp) }
    val overlayAlpha by animateFloatAsState(
        targetValue = if (isTv && hasFocus) 1f else 0f,
        label = "tv-icon-focus-alpha",
    )

    val requestedIcon = icon.resId(variant)
    val resolvedIcon =
        if (requestedIcon != 0) {
            requestedIcon
        } else {
            UnifiedLog.log(
                level = UnifiedLog.Level.WARN,
                source = "ui",
                message = "Missing drawable for icon=${icon.name} variant=$variant – falling back to ic_all_primary",
            )
            R.drawable.ic_all_primary
        }

    TvIconButton(
        onClick = onClick,
        modifier =
            modifier
                .semantics { this.contentDescription = contentDescription }
                .focusable()
                .onFocusEvent { st -> hasFocus = st.isFocused || st.hasFocus }
                .onKeyEvent { ev ->
                    if (ev.type == KeyEventType.KeyUp &&
                        (ev.key == Key.Enter || ev.key == Key.NumPadEnter || ev.key == Key.DirectionCenter)
                    ) {
                        com.chris.m3usuite.core.debug.GlobalDebug
                            .logDpad("CENTER", mapOf("button" to icon.name))
                        onClick()
                        return@onKeyEvent true
                    }
                    false
                },
        shape = focusShape,
        interactionSource = interactionSource,
        focusColors = FocusKit.FocusDefaults.IconColors,
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (overlayAlpha > 0f) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = overlayAlpha }
                        .clip(focusShape)
                        .background(tvFocusOverlay)
                        .border(width = focusBorderWidth, color = tvFocusBorder, shape = focusShape),
                )
            }
            Image(
                painter = safePainter(resolvedIcon, label = "AppIconButton/${icon.name}"),
                contentDescription = contentDescription,
                modifier = Modifier.size(size),
            )
        }
    }
}
