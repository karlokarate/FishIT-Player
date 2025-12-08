package com.chris.m3usuite.player.internal.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.chris.m3usuite.playback.PlaybackError
import com.chris.m3usuite.ui.focus.FocusKit

/**
 * Error overlay composable for the SIP player.
 * Phase 8 Task 6: Soft error UI with Retry/Close buttons.
 */
@Composable
fun PlaybackErrorOverlay(
    error: PlaybackError?,
    isKidMode: Boolean,
    onRetry: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = error != null,
        enter = fadeIn() + slideInVertically { it / 2 },
        exit = fadeOut() + slideOutVertically { it / 2 },
        modifier = modifier.fillMaxWidth(),
    ) {
        error?.let { err ->
            Surface(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.95f),
                tonalElevation = 8.dp,
                shadowElevation = 4.dp,
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onErrorContainer,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Playback Error",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = if (isKidMode) err.toKidsFriendlyMessage() else err.toUserFriendlyMessage(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.85f),
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                    ) {
                        val retryModifier =
                            FocusKit.run {
                                Modifier
                                    .focusScaleOnTv(debugTag = "error:retry")
                                    .tvClickable(enabled = true, debugTag = "error:retry", onClick = onRetry)
                            }
                        Button(
                            onClick = onRetry,
                            modifier = retryModifier,
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        ) {
                            Icon(imageVector = Icons.Filled.Refresh, contentDescription = null)
                            Spacer(Modifier.width(4.dp))
                            Text("Retry")
                        }
                        val closeModifier =
                            FocusKit.run {
                                Modifier
                                    .focusScaleOnTv(debugTag = "error:close")
                                    .tvClickable(enabled = true, debugTag = "error:close", onClick = onClose)
                            }
                        OutlinedButton(onClick = onClose, modifier = closeModifier) {
                            Icon(imageVector = Icons.Filled.Close, contentDescription = null)
                            Spacer(Modifier.width(4.dp))
                            Text("Close")
                        }
                    }
                }
            }
        }
    }
}

/**
 * Compact error badge for MiniPlayer.
 * Phase 8 Task 6: MiniPlayer error indication.
 */
@Composable
fun MiniPlayerErrorBadge(
    error: PlaybackError?,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = error != null,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier,
    ) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.error.copy(alpha = 0.9f),
            contentColor = MaterialTheme.colorScheme.onError,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Filled.Warning,
                    contentDescription = "Playback error",
                    modifier = Modifier.height(16.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = "Error",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}
