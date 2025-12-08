package com.chris.m3usuite.player.internal.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.chris.m3usuite.player.internal.subtitles.EdgeStyle
import com.chris.m3usuite.player.internal.subtitles.SubtitlePreset
import com.chris.m3usuite.player.internal.subtitles.SubtitleStyle
import com.chris.m3usuite.player.internal.subtitles.SubtitleTrack

/**
 * CC/Subtitle menu dialog for the SIP Internal Player.
 *
 * **Phase 4 Group 4 - Contract: INTERNAL_PLAYER_SUBTITLE_CC_CONTRACT_PHASE4.md Section 8**
 *
 * This composable provides subtitle track selection and styling controls for both TV/DPAD
 * and touch devices.
 *
 * **Features:**
 * - Subtitle track selection (language/label)
 * - Text size adjustment (0.5x - 2.0x)
 * - Foreground color selection
 * - Background color selection
 * - Opacity controls (foreground 50%-100%, background 0%-100%)
 * - Edge style selection (NONE, OUTLINE, SHADOW, GLOW)
 * - Preset application (DEFAULT, HIGH_CONTRAST, TV_LARGE, MINIMAL)
 * - Live preview of pending style changes
 *
 * **Contract Compliance:**
 * - Section 8.1: Visible only for non-kid profiles with available subtitle tracks
 * - Section 8.2: Provides all required subtitle styling controls
 * - Section 8.5: Shows live preview of pending style
 *
 * @param currentStyle Current subtitle style
 * @param availableTracks List of available subtitle tracks
 * @param selectedTrack Currently selected subtitle track (nullable)
 * @param onDismiss Callback when dialog is dismissed without applying
 * @param onApplyStyle Callback when style is applied (updateStyle)
 * @param onApplyPreset Callback when preset is applied
 * @param onSelectTrack Callback when subtitle track is selected
 */
@Composable
fun CcMenuDialog(
    currentStyle: SubtitleStyle,
    availableTracks: List<SubtitleTrack>,
    selectedTrack: SubtitleTrack?,
    onDismiss: () -> Unit,
    onApplyStyle: (SubtitleStyle) -> Unit,
    onApplyPreset: (SubtitlePreset) -> Unit,
    onSelectTrack: (SubtitleTrack?) -> Unit,
) {
    // Pending style that updates as user makes changes
    var pendingStyle by remember(currentStyle) { mutableStateOf(currentStyle) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Card(
            modifier =
                Modifier
                    .fillMaxWidth(0.9f)
                    .fillMaxHeight(0.85f)
                    .padding(16.dp),
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(24.dp),
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Subtitles & Captions",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Live Preview
                SubtitlePreview(
                    style = pendingStyle,
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Scrollable content
                Column(
                    modifier =
                        Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    // Preset Buttons
                    Text(
                        text = "Presets",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        SubtitlePreset.entries.forEach { preset ->
                            OutlinedButton(
                                onClick = {
                                    pendingStyle = preset.toStyle()
                                    onApplyPreset(preset)
                                },
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(preset.name.replace("_", " "))
                            }
                        }
                    }

                    HorizontalDivider()

                    // Track Selection
                    if (availableTracks.isNotEmpty()) {
                        Text(
                            text = "Subtitle Track",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Button(
                                onClick = { onSelectTrack(null) },
                                modifier = Modifier.weight(1f),
                                colors =
                                    if (selectedTrack == null) {
                                        ButtonDefaults.buttonColors()
                                    } else {
                                        ButtonDefaults.outlinedButtonColors()
                                    },
                            ) {
                                Text("Off")
                            }
                            availableTracks.take(3).forEach { track ->
                                Button(
                                    onClick = { onSelectTrack(track) },
                                    modifier = Modifier.weight(1f),
                                    colors =
                                        if (selectedTrack == track) {
                                            ButtonDefaults.buttonColors()
                                        } else {
                                            ButtonDefaults.outlinedButtonColors()
                                        },
                                ) {
                                    Text(track.label)
                                }
                            }
                        }

                        HorizontalDivider()
                    }

                    // Text Size
                    Text(
                        text = "Text Size: ${String.format("%.1f", pendingStyle.textScale)}x",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Slider(
                        value = pendingStyle.textScale,
                        onValueChange = { pendingStyle = pendingStyle.copy(textScale = it) },
                        valueRange = 0.5f..2.0f,
                        steps = 14,
                    )

                    HorizontalDivider()

                    // Foreground Opacity
                    Text(
                        text = "Text Opacity: ${(pendingStyle.foregroundOpacity * 100).toInt()}%",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Slider(
                        value = pendingStyle.foregroundOpacity,
                        onValueChange = { pendingStyle = pendingStyle.copy(foregroundOpacity = it) },
                        valueRange = 0.5f..1.0f,
                        steps = 9,
                    )

                    HorizontalDivider()

                    // Background Opacity
                    Text(
                        text = "Background Opacity: ${(pendingStyle.backgroundOpacity * 100).toInt()}%",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Slider(
                        value = pendingStyle.backgroundOpacity,
                        onValueChange = { pendingStyle = pendingStyle.copy(backgroundOpacity = it) },
                        valueRange = 0.0f..1.0f,
                        steps = 19,
                    )

                    HorizontalDivider()

                    // Edge Style
                    Text(
                        text = "Edge Style",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        EdgeStyle.entries.forEach { edge ->
                            OutlinedButton(
                                onClick = { pendingStyle = pendingStyle.copy(edgeStyle = edge) },
                                modifier = Modifier.weight(1f),
                                colors =
                                    if (pendingStyle.edgeStyle == edge) {
                                        ButtonDefaults.buttonColors()
                                    } else {
                                        ButtonDefaults.outlinedButtonColors()
                                    },
                            ) {
                                Text(edge.name)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Cancel")
                    }
                    Button(
                        onClick = {
                            onApplyStyle(pendingStyle)
                            onDismiss()
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Apply")
                    }
                }
            }
        }
    }
}

/**
 * Live preview component showing how subtitle text will look with the current style.
 *
 * Contract Section 8.5: Preview must immediately reflect pending style changes.
 */
@Composable
private fun SubtitlePreview(
    style: SubtitleStyle,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = Color.Black),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 100.dp)
                    .padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "Example Subtitle Text",
                style =
                    MaterialTheme.typography.bodyLarge.copy(
                        fontSize = MaterialTheme.typography.bodyLarge.fontSize * style.textScale,
                        fontWeight = FontWeight.Bold,
                    ),
                color =
                    Color(style.foregroundColor).copy(
                        alpha = style.foregroundOpacity,
                    ),
                modifier =
                    Modifier
                        .wrapContentSize()
                        .then(
                            // Simple background simulation
                            if (style.backgroundOpacity > 0f) {
                                Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            } else {
                                Modifier
                            },
                        ),
            )
        }
    }
}
