package com.chris.m3usuite.ui.home

import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.chris.m3usuite.playback.PlaybackSession
import com.chris.m3usuite.ui.focus.FocusKit
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@OptIn(UnstableApi::class)
@Composable
fun MiniPlayerHost(
    focusEnabled: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isTv = remember { FocusKit.isTvDevice(context) }
    if (!isTv) return

    val descriptorState by MiniPlayerState.descriptor.collectAsState()
    val visibleState by MiniPlayerState.visible.collectAsState()
    val resumeHandler = LocalMiniPlayerResume.current
    val player = PlaybackSession.current()
    val shouldShow = visibleState && descriptorState != null && player != null

    val resumeFocus = remember { FocusRequester() }
    val stopFocus = remember { FocusRequester() }
    var pendingFocus by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        MiniPlayerState.focusRequests.collect {
            if (!shouldShow) {
                pendingFocus = true
            } else if (focusEnabled) {
                val target = if (resumeHandler != null) resumeFocus else stopFocus
                runCatching { target.requestFocus() }
                pendingFocus = false
            } else {
                pendingFocus = true
            }
        }
    }

    LaunchedEffect(shouldShow, focusEnabled, resumeHandler) {
        if (shouldShow && focusEnabled && pendingFocus) {
            val target = if (resumeHandler != null) resumeFocus else stopFocus
            runCatching { target.requestFocus() }
            pendingFocus = false
        }
    }

    var progress by remember { mutableFloatStateOf(0f) }
    var durationMs by remember { mutableStateOf(0L) }
    LaunchedEffect(shouldShow, player) {
        if (!shouldShow) return@LaunchedEffect
        while (isActive && MiniPlayerState.visible.value) {
            val dur = player.duration.takeIf { it > 0 } ?: 0L
            val pos = player.currentPosition
            durationMs = dur
            progress = if (dur > 0) (pos.toFloat() / dur.toFloat()).coerceIn(0f, 1f) else 0f
            delay(500)
        }
    }

    AnimatedVisibility(
        visible = shouldShow,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        Box(
            modifier
                .fillMaxSize(),
            contentAlignment = Alignment.BottomEnd
        ) {
            val descriptor = descriptorState ?: return@AnimatedVisibility
            Surface(
                tonalElevation = 8.dp,
                shadowElevation = 12.dp,
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceColorAtElevation(12.dp),
                modifier = Modifier
                    .padding(end = 24.dp, bottom = 24.dp)
                    .wrapContentWidth()
            ) {
                Column(
                    Modifier
                        .then(FocusKit.run { Modifier.focusGroup() })
                        .then(
                            if (focusEnabled) Modifier else Modifier.focusProperties { canFocus = false }
                        )
                        .padding(16.dp)
                ) {
                    AndroidView(
                        modifier = Modifier
                            .fillMaxWidth()
                            .size(width = 320.dp, height = 180.dp),
                        factory = { ctx ->
                            PlayerView(ctx).apply {
                                useController = false
                                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                            }
                        },
                        update = { view ->
                            view.player = PlaybackSession.current()
                        }
                    )

                    Text(
                        text = descriptor.title ?: "Wiedergabe",
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 12.dp)
                    )
                    descriptor.subtitle?.takeIf { it.isNotBlank() }?.let { sub ->
                        Text(
                            text = sub,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }

                    if (durationMs > 0) {
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 12.dp)
                        )
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier
                            .padding(top = 16.dp)
                    ) {
                        val resumeModifierBase = FocusKit.run {
                            Modifier
                                .focusScaleOnTv(debugTag = "mini:resume")
                                .tvClickable(
                                    enabled = resumeHandler != null && focusEnabled,
                                    debugTag = "mini:resume",
                                    onClick = {
                                        val active = PlaybackSession.current()
                                        if (resumeHandler != null && active != null) {
                                            val snapshot = MiniPlayerSnapshot(
                                                descriptor = descriptor,
                                                positionMs = active.currentPosition,
                                                durationMs = active.duration.takeIf { it > 0 } ?: 0L
                                            )
                                            MiniPlayerState.hide()
                                            resumeHandler.invoke(snapshot)
                                        }
                                    }
                                )
                                .focusRequester(resumeFocus)
                        }
                        val resumeModifier = if (resumeHandler != null && focusEnabled) {
                            resumeModifierBase
                        } else {
                            resumeModifierBase.focusProperties { canFocus = false }
                        }
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                            modifier = resumeModifier
                        ) {
                            Text(
                                text = "Zum Player",
                                modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp),
                                style = MaterialTheme.typography.labelLarge
                            )
                        }

                        val stopModifierBase = FocusKit.run {
                            Modifier
                                .focusScaleOnTv(debugTag = "mini:stop")
                                .tvClickable(
                                    enabled = focusEnabled,
                                    debugTag = "mini:stop",
                                    onClick = { MiniPlayerState.stopAndRelease() }
                                )
                                .focusRequester(stopFocus)
                        }
                        val stopModifier = if (focusEnabled) stopModifierBase else stopModifierBase.focusProperties { canFocus = false }
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = MaterialTheme.colorScheme.surfaceColorAtElevation(4.dp),
                            contentColor = MaterialTheme.colorScheme.onSurface,
                            modifier = stopModifier
                        ) {
                            Text(
                                text = "Stop", // keep short for button width
                                modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp),
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                }
            }
        }
    }
}
