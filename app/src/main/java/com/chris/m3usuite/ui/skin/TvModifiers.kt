package com.chris.m3usuite.ui.skin

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawWithContent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * TV-friendly clickable with focus scale + press bounce + auto bring-into-view.
 * Use on list tiles, cards, rows. Keeps LocalIndication so TvFocusIndication applies.
 */
fun Modifier.tvClickable(
    enabled: Boolean = true,
    role: Role? = Role.Button,
    scaleFocused: Float = 1.08f,
    scalePressed: Float = 1.12f,
    elevationFocusedDp: Float = 12f,
    onClick: () -> Unit
): Modifier = composed {
    val scope = rememberCoroutineScope()
    val interactionSource = remember { MutableInteractionSource() }
    val bring = remember { BringIntoViewRequester() }
    val focusReq = remember { FocusRequester() }
    var focused by remember { mutableStateOf(false) }
    var pressed by remember { mutableStateOf(false) }

    LaunchedEffect(interactionSource) {
        interactionSource.interactions.collect { i ->
            when (i) {
                is PressInteraction.Press -> pressed = true
                is PressInteraction.Release, is PressInteraction.Cancel -> pressed = false
            }
        }
    }

    val targetScale = when {
        pressed -> scalePressed
        focused -> scaleFocused
        else -> 1f
    }
    val scale by animateFloatAsState(
        targetValue = targetScale,
        animationSpec = spring(stiffness = Spring.StiffnessLow, dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "tvScale"
    )

    this
        .focusRequester(focusReq)
        .bringIntoViewRequester(bring)
        .onFocusChanged { state ->
            val now = state.isFocused || state.hasFocus
            if (now && !focused) scope.launch { bring.bringIntoView() }
            focused = now
        }
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
            shadowElevation = if (focused) elevationFocusedDp.dp.toPx() else 0f
        }
        .drawWithContent {
            drawContent()
            val alpha = when {
                pressed -> 0.28f
                focused -> 0.18f
                else -> 0f
            }
            if (alpha > 0f) drawRect(color = Color(0xFF00E0FF), alpha = alpha)
        }
        .clickable(
            enabled = enabled,
            role = role,
            interactionSource = interactionSource,
            indication = null,
            onClick = onClick
        )
}

/** Add only the scale/bounce (no click). Use on Buttons etc. */
fun Modifier.focusScaleOnTv(
    focusedScale: Float? = null,
    pressedScale: Float? = null
): Modifier = composed {
    val ctx = LocalContext.current
    val cfg = LocalConfiguration.current
    val sw = cfg.smallestScreenWidthDp
    val isTablet = sw >= 600
    val isTv = isTvDevice(ctx)
    val effFocused = focusedScale ?: when {
        isTv -> 1.08f
        isTablet -> 1.04f
        else -> 1.03f
    }
    val effPressed = pressedScale ?: when {
        isTv -> 0.98f
        else -> 0.99f
    }
    val interactionSource = remember { MutableInteractionSource() }
    var focused by remember { mutableStateOf(false) }
    var pressed by remember { mutableStateOf(false) }

    LaunchedEffect(interactionSource) {
        interactionSource.interactions.collect { i ->
            when (i) {
                is PressInteraction.Press -> pressed = true
                is PressInteraction.Release, is PressInteraction.Cancel -> pressed = false
            }
        }
    }
    val scale by animateFloatAsState(
        targetValue = when {
            pressed -> effPressed
            focused -> effFocused
            else -> 1f
        },
        animationSpec = spring(stiffness = Spring.StiffnessMedium, dampingRatio = Spring.DampingRatioLowBouncy),
        label = "btnTvScale"
    )
    this
        .onFocusChanged { focused = it.isFocused || it.hasFocus }
        .graphicsLayer { scaleX = scale; scaleY = scale }
}
