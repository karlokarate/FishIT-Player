@file:Suppress("FunctionName")
package com.chris.m3usuite.ui.focus

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.key
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

// Bring core primitives into one, easy import surface.
// Screens can: `import com.chris.m3usuite.ui.focus.FocusKit.*` and use these consistently.

import com.chris.m3usuite.ui.compat.focusGroup as compatFocusGroup
import com.chris.m3usuite.ui.skin.tvClickable as skinTvClickable
import com.chris.m3usuite.ui.skin.tvFocusableItem as skinTvFocusableItem
import com.chris.m3usuite.ui.skin.tvFocusFrame as skinTvFocusFrame
import com.chris.m3usuite.ui.skin.focusScaleOnTv as skinFocusScaleOnTv
import com.chris.m3usuite.ui.fx.tvFocusGlow as fxTvFocusGlow
import com.chris.m3usuite.model.MediaItem
import com.chris.m3usuite.ui.components.rows.RowConfig
import com.chris.m3usuite.ui.components.rows.MediaRowCore
import com.chris.m3usuite.ui.components.rows.MediaRowCorePaged
import com.chris.m3usuite.ui.components.rows.OnPrefetchKeys
import com.chris.m3usuite.ui.components.rows.OnPrefetchPaged
import androidx.paging.compose.LazyPagingItems
import androidx.compose.ui.platform.LocalContext

typealias FocusColors = com.chris.m3usuite.ui.skin.TvFocusColors

object FocusDefaults {
    val Colors: FocusColors = com.chris.m3usuite.ui.skin.TvFocusColors.Default
}

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
): Modifier = this.skinTvClickable(
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

fun Modifier.tvFocusFrame(
    focusedScale: Float = 1.40f,
    pressedScale: Float = 1.40f,
    shape: androidx.compose.ui.graphics.Shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp),
    focusColors: FocusColors = FocusDefaults.Colors,
    focusBorderWidth: androidx.compose.ui.unit.Dp = 2.5.dp,
    brightenContent: Boolean = false
): Modifier = this.skinTvFocusFrame(
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
): Modifier = this.skinTvFocusableItem(
    stateKey = stateKey,
    index = index,
    autoBringIntoView = autoBringIntoView,
    onFocused = onFocused,
    debugTag = debugTag
)

fun Modifier.focusGroup(): Modifier = this.compatFocusGroup()

fun Modifier.focusScaleOnTv(
    focusedScale: Float? = null,
    pressedScale: Float? = null,
    shape: androidx.compose.ui.graphics.Shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp),
    focusColors: FocusColors = FocusDefaults.Colors,
    focusBorderWidth: androidx.compose.ui.unit.Dp = 1.5.dp,
    interactionSource: androidx.compose.foundation.interaction.MutableInteractionSource? = null,
    brightenContent: Boolean = true
): Modifier = this.skinFocusScaleOnTv(
    focusedScale = focusedScale,
    pressedScale = pressedScale,
    shape = shape,
    focusColors = focusColors,
    focusBorderWidth = focusBorderWidth,
    interactionSource = interactionSource,
    brightenContent = brightenContent
)

// Centralized bring-into-view on focus for top-level wrappers.
// We opt-in here so call sites don't need to depend on experimental APIs.
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
    ): Modifier = this@tvClickable.skinTvClickable(
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

    fun Modifier.tvFocusFrame(
        focusedScale: Float = 1.40f,
        pressedScale: Float = 1.40f,
        shape: androidx.compose.ui.graphics.Shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp),
        focusColors: FocusColors = FocusDefaults.Colors,
        focusBorderWidth: androidx.compose.ui.unit.Dp = 2.5.dp,
        brightenContent: Boolean = false
    ): Modifier = this@tvFocusFrame.skinTvFocusFrame(
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
    ): Modifier = this@tvFocusableItem.skinTvFocusableItem(
        stateKey = stateKey,
        index = index,
        autoBringIntoView = autoBringIntoView,
        onFocused = onFocused,
        debugTag = debugTag
    )

    fun Modifier.focusGroup(): Modifier = this@focusGroup.compatFocusGroup()

    fun Modifier.focusScaleOnTv(
        focusedScale: Float? = null,
        pressedScale: Float? = null,
        shape: androidx.compose.ui.graphics.Shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp),
        focusColors: FocusColors = FocusDefaults.Colors,
        focusBorderWidth: androidx.compose.ui.unit.Dp = 1.5.dp,
        interactionSource: androidx.compose.foundation.interaction.MutableInteractionSource? = null,
        brightenContent: Boolean = true
    ): Modifier = this@focusScaleOnTv.skinFocusScaleOnTv(
        focusedScale = focusedScale,
        pressedScale = pressedScale,
        shape = shape,
        focusColors = focusColors,
        focusBorderWidth = focusBorderWidth,
        interactionSource = interactionSource,
        brightenContent = brightenContent
    )

    // Focus glow (halo rings) re-export
    @Composable
    fun Modifier.tvFocusGlow(
        focused: Boolean,
        shape: androidx.compose.ui.graphics.Shape,
        ringWidth: androidx.compose.ui.unit.Dp = 2.dp
    ): Modifier = this.fxTvFocusGlow(focused = focused, shape = shape, ringWidth = ringWidth)

    // Bring-into-view helper
    @OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
    fun Modifier.focusBringIntoViewOnFocus(): Modifier = composed {
        val requester = remember { BringIntoViewRequester() }
        val scope = rememberCoroutineScope()
        this
            .bringIntoViewRequester(requester)
            .onFocusEvent { st -> if (st.isFocused) scope.launch { requester.bringIntoView() } }
    }

    // DPAD helpers (TV-first; on phone/tablet they are no-ops by default)
    fun Modifier.onDpadAdjustLeftRight(
        onLeft: () -> Unit,
        onRight: () -> Unit
    ): Modifier = composed {
        val isTv = com.chris.m3usuite.ui.skin.isTvDevice(LocalContext.current)
        if (!isTv) this else this.onPreviewKeyEvent {
            when (it.key) {
                Key.DirectionLeft -> { onLeft(); true }
                Key.DirectionRight -> { onRight(); true }
                else -> false
            }
        }
    }

    fun Modifier.onDpadAdjustUpDown(
        onUp: () -> Unit,
        onDown: () -> Unit
    ): Modifier = composed {
        val isTv = com.chris.m3usuite.ui.skin.isTvDevice(LocalContext.current)
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
        itemContent: @Composable (MediaItem) -> Unit
    ) {
        MediaRowCore(
            items = items,
            config = config,
            leading = leading,
            onPrefetchKeys = onPrefetchKeys,
            itemKey = itemKey,
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
}

// Convenience to use member extension modifiers without explicit `with(...)` at call sites,
// mirroring the skin PackageScope pattern:
@Composable
inline fun <T> run(block: @Composable FocusKit.() -> T): T = FocusKit.block()
