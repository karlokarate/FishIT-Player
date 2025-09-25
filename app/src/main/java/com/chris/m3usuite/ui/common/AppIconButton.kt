package com.chris.m3usuite.ui.common

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.chris.m3usuite.ui.skin.TvFocusColors
import com.chris.m3usuite.ui.skin.isTvDevice

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
    focusBorderWidth: Dp = 1.5.dp
) {
    val context = LocalContext.current
    val isTv = remember(context) { isTvDevice(context) }
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val focusShape = remember { RoundedCornerShape(14.dp) }
    val overlayAlpha by animateFloatAsState(
        targetValue = if (isTv && isFocused) 1f else 0f,
        label = "tv-icon-focus-alpha"
    )

    TvIconButton(
        onClick = onClick,
        modifier = modifier
            .semantics { this.contentDescription = contentDescription }
            .onKeyEvent { ev ->
                val n = ev.nativeKeyEvent
                if (n.action == android.view.KeyEvent.ACTION_UP) {
                    val code = n.keyCode
                    if (code == android.view.KeyEvent.KEYCODE_ENTER || code == android.view.KeyEvent.KEYCODE_DPAD_CENTER) {
                        onClick(); true
                    } else false
                } else false
            },
        shape = focusShape,
        interactionSource = interactionSource,
        focusColors = TvFocusColors.Icon
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (overlayAlpha > 0f) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = overlayAlpha }
                        .clip(focusShape)
                        .background(tvFocusOverlay)
                        .border(width = focusBorderWidth, color = tvFocusBorder, shape = focusShape)
                )
            }
            Image(
                painter = painterResource(icon.resId(variant)),
                contentDescription = contentDescription,
                modifier = Modifier.size(size)
            )
        }
    }
}
