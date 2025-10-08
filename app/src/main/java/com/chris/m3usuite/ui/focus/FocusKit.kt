@file:Suppress("FunctionName")
package com.chris.m3usuite.ui.focus

import android.app.UiModeManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
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
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

// Bring core primitives into one, easy import surface.
// Screens can: `import com.chris.m3usuite.ui.focus.FocusKit.*` and use these consistently.

import androidx.paging.compose.LazyPagingItems
import com.chris.m3usuite.core.debug.GlobalDebug
import com.chris.m3usuite.metrics.RouteTag
import com.chris.m3usuite.model.MediaItem
import com.chris.m3usuite.ui.compat.focusGroup as compatFocusGroup
import com.chris.m3usuite.ui.fx.tvFocusGlow as fxTvFocusGlow
import com.chris.m3usuite.ui.state.writeRowFocus

// Modifier extensions (centralized)

@Composable
fun Modifier.tvClickable(
    enabled: Boolean = true,
    role: Role? = Role.Button,
    scaleFocused: Float = 1.08f,
    scalePressed: Float = 1.12f,
    elevationFocusedDp: Float = 12f,
    autoBringIntoView: Boolean = true,
    shape: androidx.compose.ui.graphics.Shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp),
    focusColors: FocusColors = FocusDefaults.Colors,
    focusBorderWidth: androidx.compose.ui.unit.Dp = 1.5.dp,
    brightenContent: Boolean = true,
    debugTag: String? = null,
    focusRequester: androidx.compose.ui.focus.FocusRequester? = null,
    onClick: () -> Unit
): Modifier = this.focusKitTvClickable(
    enabled = enabled,
    role = role,
    scaleFocused = scaleFocused,
    scalePressed = scalePressed,
    elevationFocusedDp = elevationFocusedDp,
    autoBringIntoView = autoBringIntoView,
    shape = shape,
    focusColors = focusColors,
    focusBorderWidth = focusBorderWidth,
    brightenContent = brightenContent,
    debugTag = debugTag,
    focusRequester = focusRequester,
    onClick = onClick
)

@Composable
fun Modifier.tvFocusFrame(
    focusedScale: Float = 1.40f,
    pressedScale: Float = 1.40f,
    shape: androidx.compose.ui.graphics.Shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp),
    focusColors: FocusColors = FocusDefaults.Colors,
    focusBorderWidth: androidx.compose.ui.unit.Dp = 2.5.dp,
    brightenContent: Boolean = false
): Modifier = this.focusKitTvFocusFrame(
    focusedScale = focusedScale,
    pressedScale = pressedScale,
    shape = shape,
    focusColors = focusColors,
    focusBorderWidth = focusBorderWidth,
    brightenContent = brightenContent
)

@Composable
fun Modifier.tvFocusableItem(
    stateKey: String,
    index: Int,
    autoBringIntoView: Boolean = true,
    onFocused: () -> Unit = {},
    debugTag: String? = null
): Modifier = this.focusKitTvFocusableItem(
    stateKey = stateKey,
    index = index,
    autoBringIntoView = autoBringIntoView,
    onFocused = onFocused,
    debugTag = debugTag
)

fun Modifier.focusGroup(): Modifier = this.compatFocusGroup()

@Composable
fun Modifier.focusScaleOnTv(
    focusedScale: Float? = null,
    pressedScale: Float? = null,
    shape: androidx.compose.ui.graphics.Shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp),
    focusColors: FocusColors = FocusDefaults.Colors,
    focusBorderWidth: androidx.compose.ui.unit.Dp = 1.5.dp,
    interactionSource: androidx.compose.foundation.interaction.MutableInteractionSource? = null,
    brightenContent: Boolean = true,
    debugTag: String? = null
): Modifier = this.focusKitFocusScaleOnTv(
    focusedScale = focusedScale,
    pressedScale = pressedScale,
    shape = shape,
    focusColors = focusColors,
    focusBorderWidth = focusBorderWidth,
    interactionSource = interactionSource,
    brightenContent = brightenContent,
    debugTag = debugTag
)

// Centralized bring-into-view on focus for top-level wrappers.
// We opt-in here so call sites don't need to depend on experimental APIs.
@Composable
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
fun Modifier.focusBringIntoViewOnFocus(): Modifier = composed {
    val requester = remember { BringIntoViewRequester() }
    val scope = rememberCoroutineScope()
    this
        .bringIntoViewRequester(requester)
        .onFocusEvent { st -> if (st.isFocused) scope.launch { requester.bringIntoView() } }
}

// Row helpers (centralized)

@Composable
fun <T> TvRow(
    items: List<T>,
    key: (T) -> Any,
    listState: LazyListState,
    modifier: Modifier = Modifier,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.spacedBy(12.dp),
    contentPadding: PaddingValues = PaddingValues(0.dp),
    item: @Composable (index: Int, value: T, itemModifier: Modifier) -> Unit
) {
    com.chris.m3usuite.ui.tv.TvFocusRow(
        items = items,
        key = key,
        listState = listState,
        modifier = modifier,
        horizontalArrangement = horizontalArrangement,
        contentPadding = contentPadding,
        item = item
    )
}

@Composable
fun TvRow(
    stateKey: String,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp),
    itemSpacing: Dp = 12.dp,
    itemCount: Int,
    itemKey: ((Int) -> Any)? = null,
    itemContent: @Composable (index: Int) -> Unit
) {
    com.chris.m3usuite.ui.tv.TvFocusRow(
        stateKey = stateKey,
        modifier = modifier,
        contentPadding = contentPadding,
        itemSpacing = itemSpacing,
        itemCount = itemCount,
        itemKey = itemKey,
        itemContent = itemContent
    )
}

// Final, centralized FocusKit facade for all UIs (TV, phone, tablet)
// Provides a single import surface: FocusKit.*
object FocusKit {
    object FocusDefaults {
        val Colors: FocusColors
            @Composable
            get() = com.chris.m3usuite.ui.focus.FocusDefaults.Colors
        val IconColors: FocusColors
            @Composable
            get() = com.chris.m3usuite.ui.focus.FocusDefaults.IconColors
    }

    // Primitives (wrappers to ensure a single import path)
    @Composable
    fun Modifier.tvClickable(
        enabled: Boolean = true,
        role: Role? = Role.Button,
        scaleFocused: Float = 1.08f,
        scalePressed: Float = 1.12f,
        elevationFocusedDp: Float = 12f,
        autoBringIntoView: Boolean = true,
        shape: androidx.compose.ui.graphics.Shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp),
        focusColors: FocusColors = FocusDefaults.Colors,
        focusBorderWidth: androidx.compose.ui.unit.Dp = 1.5.dp,
        brightenContent: Boolean = true,
        debugTag: String? = null,
        focusRequester: FocusRequester? = null,
        onClick: () -> Unit
    ): Modifier = this@tvClickable.focusKitTvClickable(
        enabled = enabled,
        role = role,
        scaleFocused = scaleFocused,
        scalePressed = scalePressed,
        elevationFocusedDp = elevationFocusedDp,
        autoBringIntoView = autoBringIntoView,
        shape = shape,
        focusColors = focusColors,
        focusBorderWidth = focusBorderWidth,
        brightenContent = brightenContent,
        debugTag = debugTag,
        focusRequester = focusRequester,
        onClick = onClick
    )

    @Composable
    fun Modifier.tvFocusFrame(
        focusedScale: Float = 1.40f,
        pressedScale: Float = 1.40f,
        shape: androidx.compose.ui.graphics.Shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp),
        focusColors: FocusColors = FocusDefaults.Colors,
        focusBorderWidth: androidx.compose.ui.unit.Dp = 2.5.dp,
        brightenContent: Boolean = false
    ): Modifier = this@tvFocusFrame.focusKitTvFocusFrame(
        focusedScale = focusedScale,
        pressedScale = pressedScale,
        shape = shape,
        focusColors = focusColors,
        focusBorderWidth = focusBorderWidth,
        brightenContent = brightenContent
    )

    @Composable
    fun Modifier.tvFocusableItem(
        stateKey: String,
        index: Int,
        autoBringIntoView: Boolean = true,
        onFocused: () -> Unit = {},
        debugTag: String? = null
    ): Modifier = this@tvFocusableItem.focusKitTvFocusableItem(
        stateKey = stateKey,
        index = index,
        autoBringIntoView = autoBringIntoView,
        onFocused = onFocused,
        debugTag = debugTag
    )

    fun Modifier.focusGroup(): Modifier = this@focusGroup.compatFocusGroup()

    @Composable
    fun Modifier.focusScaleOnTv(
        focusedScale: Float? = null,
        pressedScale: Float? = null,
        shape: androidx.compose.ui.graphics.Shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp),
        focusColors: FocusColors = FocusDefaults.Colors,
        focusBorderWidth: androidx.compose.ui.unit.Dp = 1.5.dp,
        interactionSource: androidx.compose.foundation.interaction.MutableInteractionSource? = null,
        brightenContent: Boolean = true,
        debugTag: String? = null
    ): Modifier = this@focusScaleOnTv.focusKitFocusScaleOnTv(
        focusedScale = focusedScale,
        pressedScale = pressedScale,
        shape = shape,
        focusColors = focusColors,
        focusBorderWidth = focusBorderWidth,
        interactionSource = interactionSource,
        brightenContent = brightenContent,
        debugTag = debugTag
    )

    // Focus glow (halo rings) re-export
    @Composable
    fun Modifier.tvFocusGlow(
        focused: Boolean,
        shape: androidx.compose.ui.graphics.Shape,
        ringWidth: androidx.compose.ui.unit.Dp = 2.dp
    ): Modifier = this.fxTvFocusGlow(focused = focused, shape = shape, ringWidth = ringWidth)

    // Bring-into-view helper
    @Composable
    @OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
    fun Modifier.focusBringIntoViewOnFocus(): Modifier = composed {
        val requester = remember { BringIntoViewRequester() }
        val scope = rememberCoroutineScope()
        this
            .bringIntoViewRequester(requester)
            .onFocusEvent { st -> if (st.isFocused) scope.launch { requester.bringIntoView() } }
    }

    // DPAD helpers (TV-first; on phone/tablet they are no-ops by default)
    @Composable
    fun Modifier.onDpadAdjustLeftRight(
        onLeft: () -> Unit,
        onRight: () -> Unit
    ): Modifier = composed {
        val isTv = isTvDevice(LocalContext.current)
        if (!isTv) this else this.onPreviewKeyEvent {
            when (it.key) {
                Key.DirectionLeft -> { onLeft(); true }
                Key.DirectionRight -> { onRight(); true }
                else -> false
            }
        }
    }

    @Composable
    fun Modifier.onDpadAdjustUpDown(
        onUp: () -> Unit,
        onDown: () -> Unit
    ): Modifier = composed {
        val isTv = isTvDevice(LocalContext.current)
        if (!isTv) this else this.onPreviewKeyEvent {
            when (it.key) {
                Key.DirectionUp -> { onUp(); true }
                Key.DirectionDown -> { onDown(); true }
                else -> false
            }
        }
    }

    // Directional neighbor mapping (grids, keypads)
    fun Modifier.focusNeighbors(
        up: FocusRequester? = null,
        down: FocusRequester? = null,
        left: FocusRequester? = null,
        right: FocusRequester? = null
    ): Modifier = this.focusProperties {
        if (up != null) this.up = up
        if (down != null) this.down = down
        if (left != null) this.left = left
        if (right != null) this.right = right
    }

    // Row facades (single entry points)
    @Composable
    fun TvRowLight(
        stateKey: String,
        itemCount: Int,
        itemKey: ((Int) -> Any)? = null,
        itemSpacing: Dp = 12.dp,
        contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp),
        itemContent: @Composable (index: Int) -> Unit
    ) {
        com.chris.m3usuite.ui.tv.TvFocusRow(
            stateKey = stateKey,
            contentPadding = contentPadding,
            itemSpacing = itemSpacing,
            itemCount = itemCount,
            itemKey = itemKey,
            itemContent = itemContent
        )
    }

    @Composable
    fun TvRowMedia(
        items: List<MediaItem>,
        config: RowConfig = RowConfig(),
        leading: (@Composable (() -> Unit))? = null,
        onPrefetchKeys: OnPrefetchKeys? = null,
        itemKey: (MediaItem) -> Long = { it.id },
        itemModifier: @Composable (index: Int, absoluteIndex: Int, media: MediaItem, base: Modifier, state: LazyListState) -> Modifier = { _, _, _, base, _ -> base },
        itemContent: @Composable (MediaItem) -> Unit
    ) {
        MediaRowCore(
            items = items,
            config = config,
            leading = leading,
            onPrefetchKeys = onPrefetchKeys,
            itemKey = itemKey,
            itemModifier = itemModifier,
            itemContent = itemContent
        )
    }

    @Composable
    fun TvRowPaged(
        items: LazyPagingItems<MediaItem>,
        config: RowConfig = RowConfig(),
        leading: (@Composable (() -> Unit))? = null,
        onPrefetchPaged: OnPrefetchPaged? = null,
        shimmerRefreshCount: Int = 10,
        shimmerAppendCount: Int = 6,
        itemKey: (index: Int) -> Long = { idx -> items[idx]?.id ?: idx.toLong() },
        itemModifier: @Composable (index: Int, absoluteIndex: Int, media: MediaItem, base: Modifier, state: LazyListState) -> Modifier = { _, _, _, base, _ -> base },
        itemContent: @Composable (index: Int, MediaItem) -> Unit
    ) {
        MediaRowCorePaged(
            items = items,
            config = config,
            leading = leading,
            onPrefetchPaged = onPrefetchPaged,
            shimmerRefreshCount = shimmerRefreshCount,
            shimmerAppendCount = shimmerAppendCount,
            itemKey = itemKey,
            itemModifier = itemModifier,
            itemContent = itemContent
        )
    }

    // Buttons re-export (TV focus visuals on all devices as appropriate)
    @Composable
    fun TvButton(
        onClick: () -> Unit,
        modifier: Modifier = Modifier,
        enabled: Boolean = true,
        shape: androidx.compose.ui.graphics.Shape = androidx.compose.material3.ButtonDefaults.shape,
        colors: androidx.compose.material3.ButtonColors = androidx.compose.material3.ButtonDefaults.buttonColors(),
        elevation: androidx.compose.material3.ButtonElevation? = androidx.compose.material3.ButtonDefaults.buttonElevation(),
        border: androidx.compose.foundation.BorderStroke? = null,
        contentPadding: PaddingValues = androidx.compose.material3.ButtonDefaults.ContentPadding,
        interactionSource: androidx.compose.foundation.interaction.MutableInteractionSource = androidx.compose.runtime.remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
        focusColors: FocusColors = FocusDefaults.Colors,
        content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit
    ) = com.chris.m3usuite.ui.common.TvButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = shape,
        colors = colors,
        elevation = elevation,
        border = border,
        contentPadding = contentPadding,
        interactionSource = interactionSource,
        focusColors = focusColors,
        content = content
    )

    @Composable
    fun TvTextButton(
        onClick: () -> Unit,
        modifier: Modifier = Modifier,
        enabled: Boolean = true,
        shape: androidx.compose.ui.graphics.Shape = androidx.compose.material3.ButtonDefaults.textShape,
        colors: androidx.compose.material3.ButtonColors = androidx.compose.material3.ButtonDefaults.textButtonColors(),
        contentPadding: PaddingValues = androidx.compose.material3.ButtonDefaults.TextButtonContentPadding,
        interactionSource: androidx.compose.foundation.interaction.MutableInteractionSource = androidx.compose.runtime.remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
        focusColors: FocusColors = FocusDefaults.Colors,
        content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit
    ) = com.chris.m3usuite.ui.common.TvTextButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = shape,
        colors = colors,
        contentPadding = contentPadding,
        interactionSource = interactionSource,
        focusColors = focusColors,
        content = content
    )

    @Composable
    fun TvOutlinedButton(
        onClick: () -> Unit,
        modifier: Modifier = Modifier,
        enabled: Boolean = true,
        shape: androidx.compose.ui.graphics.Shape = androidx.compose.material3.ButtonDefaults.outlinedShape,
        colors: androidx.compose.material3.ButtonColors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(),
        elevation: androidx.compose.material3.ButtonElevation? = null,
        border: androidx.compose.foundation.BorderStroke? = androidx.compose.material3.ButtonDefaults.outlinedButtonBorder(enabled),
        contentPadding: PaddingValues = androidx.compose.material3.ButtonDefaults.ContentPadding,
        interactionSource: androidx.compose.foundation.interaction.MutableInteractionSource = androidx.compose.runtime.remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
        focusColors: FocusColors = FocusDefaults.Colors,
        content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit
    ) = com.chris.m3usuite.ui.common.TvOutlinedButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = shape,
        colors = colors,
        elevation = elevation,
        border = border,
        contentPadding = contentPadding,
        interactionSource = interactionSource,
        focusColors = focusColors,
        content = content
    )

    @Composable
    fun TvIconButton(
        onClick: () -> Unit,
        modifier: Modifier = Modifier,
        enabled: Boolean = true,
        shape: androidx.compose.ui.graphics.Shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp),
        interactionSource: androidx.compose.foundation.interaction.MutableInteractionSource = androidx.compose.runtime.remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
        focusColors: FocusColors = FocusDefaults.Colors,
        content: @Composable () -> Unit
    ) = com.chris.m3usuite.ui.common.TvIconButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = shape,
        interactionSource = interactionSource,
        focusColors = focusColors,
        content = content
    )

    fun isTvDevice(context: Context): Boolean = com.chris.m3usuite.ui.focus.isTvDevice(context)
}

// Convenience to use member extension modifiers without explicit `with(...)` at call sites,
// mirroring the skin PackageScope pattern:
@Composable
inline fun <T> run(block: @Composable FocusKit.() -> T): T = FocusKit.block()

@Immutable
data class FocusColors(
    val halo: Color,
    val border: Color,
    val contentTint: Color = Color.White.copy(alpha = 0.08f)
)

object FocusDefaults {
    val Colors: FocusColors
        @Composable
        get() {
            val scheme = MaterialTheme.colorScheme
            return FocusColors(
                halo = scheme.primary.copy(alpha = 0.35f),
                border = scheme.primary.copy(alpha = 0.9f),
                contentTint = Color.White.copy(alpha = 0.1f)
            )
        }

    val IconColors: FocusColors
        @Composable
        get() {
            val scheme = MaterialTheme.colorScheme
            return FocusColors(
                halo = scheme.secondary.copy(alpha = 0.32f),
                border = scheme.secondary.copy(alpha = 0.88f),
                contentTint = Color.White.copy(alpha = 0.12f)
            )
        }
}

@Composable
private fun Modifier.focusKitTvClickable(
    enabled: Boolean,
    role: Role?,
    scaleFocused: Float,
    scalePressed: Float,
    elevationFocusedDp: Float,
    autoBringIntoView: Boolean,
    shape: Shape,
    focusColors: FocusColors,
    focusBorderWidth: Dp,
    brightenContent: Boolean,
    debugTag: String?,
    focusRequester: FocusRequester?,
    onClick: () -> Unit
): Modifier = composed {
    val context = LocalContext.current
    val semanticsModifier = role?.let { Modifier.semantics { this.role = it } } ?: Modifier
    if (!isTvDevice(context)) {
        return@composed this.then(semanticsModifier).clickable(
            enabled = enabled,
            role = role,
            onClick = onClick
        )
    }

    val density = LocalDensity.current
    val interactionSource = remember { MutableInteractionSource() }
    var hasFocus by remember { mutableStateOf(false) }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = when {
            pressed -> scalePressed
            hasFocus -> scaleFocused
            else -> 1f
        },
        label = "tvClickableScale"
    )
    val focusFraction by animateFloatAsState(
        targetValue = if (hasFocus) 1f else 0f,
        label = "tvClickableFocus"
    )
    LaunchedEffect(hasFocus) {
        if (hasFocus) logFocus("Clickable", debugTag)
    }
    val focusRequesterModifier = focusRequester?.let { Modifier.focusRequester(it) } ?: Modifier
    val bringModifier = if (autoBringIntoView) focusBringIntoViewOnFocus() else Modifier
    val elevationPx = with(density) { elevationFocusedDp.dp.toPx() }

    this
        .then(focusRequesterModifier)
        .then(semanticsModifier)
        .focusable(enabled)
        .onFocusEvent { state -> hasFocus = state.isFocused || state.hasFocus }
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
            shadowElevation = if (hasFocus) elevationPx else 0f
        }
        .applyFocusDecoration(
            focusFraction = focusFraction,
            shape = shape,
            focusColors = focusColors,
            focusBorderWidth = focusBorderWidth,
            brightenContent = brightenContent
        )
        .then(bringModifier)
        .clickable(
            enabled = enabled,
            interactionSource = interactionSource,
            indication = null,
            role = role,
            onClick = onClick
        )
}

@Suppress("UNUSED_PARAMETER")
@Composable
private fun Modifier.focusKitTvFocusFrame(
    focusedScale: Float,
    pressedScale: Float,
    shape: Shape,
    focusColors: FocusColors,
    focusBorderWidth: Dp,
    brightenContent: Boolean
): Modifier = composed {
    val context = LocalContext.current
    if (!isTvDevice(context)) {
        return@composed this
    }
    var hasFocus by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (hasFocus) focusedScale else 1f,
        label = "tvFocusFrameScale"
    )
    val focusFraction by animateFloatAsState(
        targetValue = if (hasFocus) 1f else 0f,
        label = "tvFocusFrameFocus"
    )
    LaunchedEffect(hasFocus) {
        if (hasFocus) logFocus("Frame", null)
    }
    this
        .onFocusEvent { state -> hasFocus = state.isFocused || state.hasFocus }
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .applyFocusDecoration(
            focusFraction = focusFraction,
            shape = shape,
            focusColors = focusColors,
            focusBorderWidth = focusBorderWidth,
            brightenContent = brightenContent
        )
}

@Composable
private fun Modifier.focusKitTvFocusableItem(
    stateKey: String,
    index: Int,
    autoBringIntoView: Boolean,
    onFocused: () -> Unit,
    debugTag: String?
): Modifier = composed {
    val context = LocalContext.current
    if (!isTvDevice(context)) {
        return@composed this
    }
    var hasFocus by remember { mutableStateOf(false) }
    LaunchedEffect(hasFocus) {
        if (hasFocus) {
            writeRowFocus(stateKey, index)
            onFocused()
            logFocus("Item", debugTag ?: "$stateKey#$index")
        }
    }
    val bringModifier = if (autoBringIntoView) focusBringIntoViewOnFocus() else Modifier
    this
        .then(bringModifier)
        .onFocusEvent { state -> hasFocus = state.isFocused || state.hasFocus }
}

@Composable
private fun Modifier.focusKitFocusScaleOnTv(
    focusedScale: Float?,
    pressedScale: Float?,
    shape: Shape,
    focusColors: FocusColors,
    focusBorderWidth: Dp,
    interactionSource: MutableInteractionSource?,
    brightenContent: Boolean,
    debugTag: String?
): Modifier = composed {
    val context = LocalContext.current
    if (!isTvDevice(context)) {
        return@composed this
    }
    val source = interactionSource ?: remember { MutableInteractionSource() }
    var hasFocus by remember { mutableStateOf(false) }
    val pressed by source.collectIsPressedAsState()
    val targetScale = when {
        pressed && pressedScale != null -> pressedScale
        hasFocus && focusedScale != null -> focusedScale
        else -> 1f
    }
    val scale by animateFloatAsState(targetValue = targetScale, label = "tvFocusScale")
    val focusFraction by animateFloatAsState(targetValue = if (hasFocus) 1f else 0f, label = "tvFocusScaleFraction")
    LaunchedEffect(hasFocus) {
        if (hasFocus) logFocus("Widget", debugTag)
    }
    this
        .onFocusEvent { state -> hasFocus = state.isFocused || state.hasFocus }
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .applyFocusDecoration(
            focusFraction = focusFraction,
            shape = shape,
            focusColors = focusColors,
            focusBorderWidth = focusBorderWidth,
            brightenContent = brightenContent
        )
}

private fun Modifier.applyFocusDecoration(
    focusFraction: Float,
    shape: Shape,
    focusColors: FocusColors,
    focusBorderWidth: Dp,
    brightenContent: Boolean
): Modifier = drawWithContent {
    drawContent()
    if (focusFraction <= 0f) return@drawWithContent

    if (brightenContent || focusColors.contentTint.alpha > 0f) {
        val tintAlpha = if (brightenContent) 0.12f else focusColors.contentTint.alpha
        drawRect(
            color = Color.White.copy(alpha = tintAlpha * focusFraction)
        )
    }

    val outline = shape.createOutline(size, layoutDirection, this)
    val borderWidthPx = focusBorderWidth.toPx()
    if (borderWidthPx > 0f) {
        drawOutlineCompat(
            outline = outline,
            color = focusColors.halo,
            style = Stroke(width = borderWidthPx * 1.9f),
            alpha = 0.45f * focusFraction
        )
        drawOutlineCompat(
            outline = outline,
            color = focusColors.border,
            style = Stroke(width = borderWidthPx),
            alpha = focusFraction
        )
    }
}

private fun DrawScope.drawOutlineCompat(
    outline: Outline,
    color: Color,
    style: Stroke,
    alpha: Float
) {
    when (outline) {
        is Outline.Rectangle -> drawRect(
            color = color,
            topLeft = outline.rect.topLeft,
            size = outline.rect.size,
            style = style,
            alpha = alpha
        )
        is Outline.Rounded -> {
            val path = Path().apply { addRoundRect(outline.roundRect) }
            drawPath(
                path = path,
                color = color,
                style = style,
                alpha = alpha
            )
        }
        is Outline.Generic -> drawPath(
            path = outline.path,
            color = color,
            style = style,
            alpha = alpha
        )
    }
}

private fun logFocus(component: String, tag: String?) {
    GlobalDebug.logFocusWidget(component = component, module = RouteTag.current, tag = tag)
}

internal fun isTvDevice(context: Context): Boolean {
    val uiModeManager = context.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
    val pm = context.packageManager
    val modeTv = uiModeManager?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION
    val hasLeanback = pm.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
    val hasTelevision = pm.hasSystemFeature(PackageManager.FEATURE_TELEVISION)
    return modeTv || hasLeanback || hasTelevision
}
