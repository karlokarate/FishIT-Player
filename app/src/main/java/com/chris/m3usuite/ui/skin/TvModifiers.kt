@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
package com.chris.m3usuite.ui.skin

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.focusable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusEvent
import android.graphics.RectF
import android.graphics.Path as AndroidPath
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.DrawStyle
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.launch


private data class TvFocusDecoration(
    val alpha: Float,
    val fillColor: Color,
    val borderColor: Color
)

data class TvFocusColors(
    val focusFill: Color,
    val focusBorder: Color,
    val pressedFill: Color,
    val pressedBorder: Color
) {
    companion object {
        val Default = TvFocusColors(
            focusFill = Color.White.copy(alpha = 0.18f),
            focusBorder = Color.White.copy(alpha = 0.55f),
            pressedFill = Color.White.copy(alpha = 0.24f),
            pressedBorder = Color.White.copy(alpha = 0.75f)
        )

        val Icon = TvFocusColors(
            focusFill = Color.White.copy(alpha = 0.22f),
            focusBorder = Color.White.copy(alpha = 0.78f),
            pressedFill = Color.White.copy(alpha = 0.3f),
            pressedBorder = Color.White.copy(alpha = 0.9f)
        )
    }
}

@Composable
private fun rememberTvFocusDecoration(
    isFocused: Boolean,
    isPressed: Boolean,
    colors: TvFocusColors
): TvFocusDecoration {
    val alpha by animateFloatAsState(
        targetValue = if (isFocused || isPressed) 1f else 0f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "tvFocusAlpha"
    )
    val fillColor by animateColorAsState(
        targetValue = if (isPressed) colors.pressedFill else colors.focusFill,
        label = "tvFocusFill"
    )
    val borderColor by animateColorAsState(
        targetValue = if (isPressed) colors.pressedBorder else colors.focusBorder,
        label = "tvFocusBorder"
    )
    return TvFocusDecoration(alpha, fillColor, borderColor)
}

private inline fun ContentDrawScope.drawWithTvFocus(
    decoration: TvFocusDecoration,
    shape: Shape,
    borderWidthPx: Float,
    brightenContent: Boolean,
    drawContent: () -> Unit
) {
    if (decoration.alpha <= 0f) {
        drawContent()
        return
    }
    val outline = shape.createOutline(size, layoutDirection, this)
    drawOutlineCompat(outline, color = decoration.fillColor, alpha = decoration.alpha)
    if (brightenContent) {
        drawOutlineCompat(
            outline = outline,
            color = Color.White,
            alpha = decoration.alpha * 0.4f,
            style = Fill
        )
    }
    drawContent()
    if (borderWidthPx > 0f) {
        drawOutlineCompat(
            outline = outline,
            color = decoration.borderColor,
            alpha = decoration.alpha,
            style = Stroke(borderWidthPx)
        )
    }
}

private fun ContentDrawScope.drawOutlineCompat(
    outline: Outline,
    color: Color,
    alpha: Float,
    style: DrawStyle = Fill
) {
    when (outline) {
        is Outline.Generic -> drawPath(outline.path, color = color, alpha = alpha, style = style)
        is Outline.Rounded -> {
            val rr = outline.roundRect
            val path = Path().apply {
                val androidPath = asAndroidPath()
                androidPath.reset()
                val rectF = RectF(rr.left, rr.top, rr.right, rr.bottom)
                val radii = floatArrayOf(
                    rr.topLeftCornerRadius.x, rr.topLeftCornerRadius.y,
                    rr.topRightCornerRadius.x, rr.topRightCornerRadius.y,
                    rr.bottomRightCornerRadius.x, rr.bottomRightCornerRadius.y,
                    rr.bottomLeftCornerRadius.x, rr.bottomLeftCornerRadius.y
                )
                androidPath.addRoundRect(rectF, radii, AndroidPath.Direction.CW)
            }
            drawPath(path, color = color, alpha = alpha, style = style)
        }
        is Outline.Rectangle -> {
            val rect = outline.rect
            drawRect(
                color = color,
                topLeft = Offset(rect.left, rect.top),
                size = Size(rect.width, rect.height),
                alpha = alpha,
                style = style
            )
        }
    }
}

/**
 * TV-friendly clickable with focus halo + scale + auto bring-into-view on TV.
 * On phones/tablets, behaves like a plain clickable.
 */
@Composable
fun Modifier.tvClickable(
    enabled: Boolean = true,
    role: Role? = Role.Button,
    scaleFocused: Float = 1.08f,
    scalePressed: Float = 1.12f,
    elevationFocusedDp: Float = 12f,
    autoBringIntoView: Boolean = true,
    shape: Shape = RoundedCornerShape(18.dp),
    focusColors: TvFocusColors = TvFocusColors.Default,
    focusBorderWidth: Dp = 1.5.dp,
    brightenContent: Boolean = true,
    debugTag: String? = null,
    focusRequester: FocusRequester? = null,
    onClick: () -> Unit
): Modifier = composed(
    inspectorInfo = {
        name = "tvClickable"
        properties["enabled"] = enabled
        properties["scaleFocused"] = scaleFocused
        properties["scalePressed"] = scalePressed
        properties["elevationFocusedDp"] = elevationFocusedDp
        properties["autoBringIntoView"] = autoBringIntoView
    }
    ) {
    val isTv = rememberIsTvUi()
    val isTvLow = if (isTv) {
        val ctxLocal = LocalContext.current
        remember(ctxLocal) { com.chris.m3usuite.core.device.DeviceProfile.isTvLowSpec(ctxLocal) }
    } else false
    val scope = rememberCoroutineScope()
    val interactionSource = remember { MutableInteractionSource() }
    val bring = remember { BringIntoViewRequester() }
    val focusReq = focusRequester ?: remember { FocusRequester() }
    val density = LocalDensity.current
    val borderWidthPx = remember(density, focusBorderWidth) { with(density) { focusBorderWidth.toPx() } }
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

    val targetScale = if (isTv) {
        when {
            pressed -> if (isTvLow) 1.01f else scalePressed
            focused -> if (isTvLow) 1.03f else scaleFocused
            else -> 1f
        }
    } else 1f
    val scale by animateFloatAsState(
        targetValue = targetScale,
        animationSpec = spring(stiffness = Spring.StiffnessLow, dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "tvScale"
    )
    val focusDecoration = rememberTvFocusDecoration(focused, pressed, focusColors)

    var out = this
    if (isTv) {
        out = out
            .focusRequester(focusReq)
            .focusable(enabled)
            .semantics { if (role != null) this.role = role }
            .bringIntoViewRequester(bring)
            .onFocusEvent { state ->
                val now = state.isFocused || state.hasFocus
                // Temporarily disable auto bring-into-view
                focused = now
                if (now) {
                    val module = com.chris.m3usuite.metrics.RouteTag.current
                    val comp = role?.toString() ?: "Clickable"
                    com.chris.m3usuite.core.debug.GlobalDebug.logFocusWidget(component = comp, module = module, tag = debugTag ?: "tvClickable")
                }
            }
            .zIndex(if (focused || pressed) 2f else 0f)
            .graphicsLayer {
                if (scale != 1f) {
                    scaleX = scale
                    scaleY = scale
                }
                // TV low-spec: drop shadow elevation entirely
                shadowElevation = if (!isTvLow && focused) elevationFocusedDp.dp.toPx() else 0f
                clip = false
            }
            .drawWithContent {
                drawWithTvFocus(focusDecoration, shape, borderWidthPx, brightenContent) { drawContent() }
            }
    }
    out.clickable(
        enabled = enabled,
        // semantics already sets role on TV; keep null here to avoid duplicate announcements
        role = null,
        interactionSource = interactionSource,
        indication = null,
        onClick = onClick
    )
}

/** Add only the scale/halo (no click). Use on Buttons etc. */
fun Modifier.focusScaleOnTv(
    focusedScale: Float? = null,
    pressedScale: Float? = null,
    shape: Shape = RoundedCornerShape(18.dp),
    focusColors: TvFocusColors = TvFocusColors.Default,
    focusBorderWidth: Dp = 1.5.dp,
    interactionSource: MutableInteractionSource? = null,
    brightenContent: Boolean = true,
    debugTag: String? = null
): Modifier = composed(
    inspectorInfo = {
        name = "focusScaleOnTv"
        properties["focusedScale"] = focusedScale
        properties["pressedScale"] = pressedScale
    }
) {
    val ctx = LocalContext.current
    val cfg = LocalConfiguration.current
    val density = LocalDensity.current
    val sw = cfg.smallestScreenWidthDp
    val isTablet = sw >= 600
    val isTv = isTvDevice(ctx)
    val isTvLow = com.chris.m3usuite.core.device.DeviceProfile.isTvLowSpec(ctx)
    val effFocused = focusedScale ?: when {
        isTv && isTvLow -> 1.03f
        isTv -> 1.08f
        isTablet -> 1.04f
        else -> 1.03f
    }
    val effPressed = pressedScale ?: when {
        isTv && isTvLow -> 0.995f
        isTv -> 0.98f
        else -> 0.99f
    }
    val focusBorderWidthPx = remember(density, focusBorderWidth) { with(density) { focusBorderWidth.toPx() } }
    val source = interactionSource ?: remember { MutableInteractionSource() }
    var focused by remember { mutableStateOf(false) }
    var pressed by remember { mutableStateOf(false) }

    LaunchedEffect(source) {
        source.interactions.collect { i ->
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
    val focusDecoration = rememberTvFocusDecoration(focused, pressed, focusColors)

    this
        .onFocusEvent {
            val now = it.isFocused || it.hasFocus
            focused = now
            if (now) {
                val module = com.chris.m3usuite.metrics.RouteTag.current
                com.chris.m3usuite.core.debug.GlobalDebug.logFocusWidget(component = "Button", module = module, tag = debugTag ?: "focusScaleOnTv")
            }
        }
        .zIndex(if (focused || pressed) 2f else 0f)
        .graphicsLayer { scaleX = scale; scaleY = scale; clip = false }
        .drawWithContent {
            drawWithTvFocus(focusDecoration, shape, focusBorderWidthPx, brightenContent) { drawContent() }
        }
}

@Composable
private fun rememberIsTvUi(): Boolean {
    val ctx = LocalContext.current
    return remember(ctx) { isTvDevice(ctx) }
}

/**
 * Mark an item focusable on TV and persist its focused index per row key.
 * Optionally auto-bring the item into view when it receives focus.
 */
@Composable
fun Modifier.tvFocusableItem(
    stateKey: String,
    index: Int,
    autoBringIntoView: Boolean = true,
    onFocused: () -> Unit = {},
    debugTag: String? = null
): Modifier = composed(
    inspectorInfo = {
        name = "tvFocusableItem"
        properties["stateKey"] = stateKey
        properties["index"] = index
        properties["autoBringIntoView"] = autoBringIntoView
    }
) {
    val isTv = rememberIsTvUi()
    val scope = rememberCoroutineScope()
    val requester = remember { BringIntoViewRequester() }

    this
        .then(if (isTv) Modifier.focusable() else Modifier)
        .bringIntoViewRequester(requester)
        .onFocusEvent { ev ->
            if (ev.isFocused) {
                com.chris.m3usuite.ui.state.writeRowFocus(stateKey, index)
                onFocused()
                // Temporarily disable auto bring-into-view
                val module = com.chris.m3usuite.metrics.RouteTag.current
                com.chris.m3usuite.core.debug.GlobalDebug.logFocusWidget(component = "Item", module = module, tag = debugTag ?: "$stateKey#$index")
            }
        }
}

/**
 * Draw focus visuals (scale + halo) on a stable wrapper regardless of inner content.
 * This reacts to descendant focus (hasFocus) so visuals persist even if the child swaps.
 */
fun Modifier.tvFocusFrame(
    focusedScale: Float = 1.40f,
    pressedScale: Float = 1.40f,
    shape: Shape = RoundedCornerShape(18.dp),
    focusColors: TvFocusColors = TvFocusColors(
        focusFill = Color.White.copy(alpha = 0.28f),
        focusBorder = Color.White.copy(alpha = 0.92f),
        pressedFill = Color.White.copy(alpha = 0.32f),
        pressedBorder = Color.White.copy(alpha = 1.0f)
    ),
    focusBorderWidth: Dp = 2.5.dp,
    brightenContent: Boolean = false
): Modifier = composed(
    inspectorInfo = {
        name = "tvFocusFrame"
        properties["focusedScale"] = focusedScale
    }
) {
    val isTv = rememberIsTvUi()
    if (!isTv) return@composed this

    var focused by remember { mutableStateOf(false) }
    var pressed by remember { mutableStateOf(false) }
    val density = LocalDensity.current
    val borderWidthPx = remember(density, focusBorderWidth) { with(density) { focusBorderWidth.toPx() } }
    val scale by animateFloatAsState(
        targetValue = when {
            pressed -> pressedScale
            focused -> focusedScale
            else -> 1f
        },
        animationSpec = spring(stiffness = Spring.StiffnessLow, dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "tvFocusFrameScale"
    )
    val deco = rememberTvFocusDecoration(focused, pressed, focusColors)

    this
        .onFocusEvent { st ->
            // React to descendant focus as well so visuals persist
            val now = st.isFocused || st.hasFocus
            focused = now
            if (now) {
                val module = com.chris.m3usuite.metrics.RouteTag.current
                com.chris.m3usuite.core.debug.GlobalDebug.logFocusWidget(component = "Frame", module = module, tag = "tvFocusFrame")
            }
        }
        // Uniform scaling; does not affect layout size (draw-time only)
        .graphicsLayer { if (scale != 1f) { scaleX = scale; scaleY = scale }; clip = false }
        .drawWithContent { drawWithTvFocus(deco, shape, borderWidthPx, brightenContent) { drawContent() } }
}
