package com.chris.m3usuite.ui.common

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.focusable
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.material3.IconButton

@Composable
fun AppIconButton(
    icon: AppIcon,
    variant: IconVariant = IconVariant.Primary,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 28.dp,
    tvFocusOverlay: Color = Color.White.copy(alpha = 0.08f),
    tvFocusScale: Float = 1.08f
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) tvFocusScale else 1f, label = "focus-scale")

    IconButton(
        onClick = onClick,
        modifier = modifier
            .focusable()
            .onFocusChanged { focused = it.isFocused }
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .semantics { this.contentDescription = contentDescription }
            .onKeyEvent { ev ->
                val n = ev.nativeKeyEvent
                if (n.action == android.view.KeyEvent.ACTION_UP) {
                    val code = n.keyCode
                    if (code == android.view.KeyEvent.KEYCODE_ENTER || code == android.view.KeyEvent.KEYCODE_DPAD_CENTER) {
                        onClick(); true
                    } else false
                } else false
            }
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (focused) {
                Box(
                    Modifier
                        .matchParentSize()
                        .background(tvFocusOverlay, RoundedCornerShape(12.dp))
                        .alpha(1f)
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
