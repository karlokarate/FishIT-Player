package com.chris.m3usuite.ui.debug

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chris.m3usuite.BuildConfig
import com.chris.m3usuite.core.debug.GlobalDebug
import com.chris.m3usuite.tv.input.DefaultTvInputDebugSink
import com.chris.m3usuite.tv.input.TvInputEventSnapshot
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * TV Input Inspector Overlay.
 *
 * This debug-only UI overlay displays real-time TV input events for debugging
 * the global TV input pipeline. It shows:
 * - Last 5 key events with timestamps
 * - Resolved TvKeyRole and TvAction
 * - Current screen ID and focus zone
 * - Whether each event was handled
 *
 * ## Usage
 *
 * Add this composable at the root of your app's scaffold:
 *
 * ```kotlin
 * Box {
 *     // Main app content
 *     MainContent()
 *
 *     // Debug overlay (only visible when enabled)
 *     TvInputInspectorOverlay()
 * }
 * ```
 *
 * The overlay is automatically hidden in release builds and when
 * [GlobalDebug.isTvInputInspectorEnabled] returns false.
 *
 * Contract Reference: INTERNAL_PLAYER_TV_INPUT_CONTRACT_PHASE6.md Section 7
 *
 * Phase 6 Task 6:
 * - Debug-only TV Input Inspector overlay
 * - Shows real-time TV input events from DefaultTvInputDebugSink
 */
@Composable
fun TvInputInspectorOverlay(modifier: Modifier = Modifier) {
    // Only show in debug builds and when inspector is enabled
    if (!BuildConfig.DEBUG) return
    if (!GlobalDebug.isTvInputInspectorEnabled()) return

    val history by DefaultTvInputDebugSink.history.collectAsState()

    // Position at bottom-right corner with semi-transparent background
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp)
            .background(
                color = Color.Black.copy(alpha = 0.75f),
                shape = RoundedCornerShape(8.dp),
            )
            .padding(12.dp),
    ) {
        // Header
        Text(
            text = "📺 TV Input Inspector",
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Event list
        if (history.isEmpty()) {
            Text(
                text = "No events captured yet...",
                color = Color.Gray,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
            )
        } else {
            // Show last 5 events (most recent at top)
            history.takeLast(5).reversed().forEach { event ->
                TvInputEventRow(event)
                Spacer(modifier = Modifier.height(4.dp))
            }
        }
    }
}

@Composable
private fun TvInputEventRow(event: TvInputEventSnapshot) {
    val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
    val formattedTime = timeFormat.format(Date(event.timestamp))

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = if (event.handled) Color(0xFF1B5E20).copy(alpha = 0.3f)
                else Color(0xFFB71C1C).copy(alpha = 0.3f),
                shape = RoundedCornerShape(4.dp),
            )
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Time
        Text(
            text = formattedTime,
            color = Color.Gray,
            fontSize = 9.sp,
            fontFamily = FontFamily.Monospace,
        )

        Spacer(modifier = Modifier.width(8.dp))

        // Key info
        Column(modifier = Modifier.weight(1f)) {
            // Line 1: KeyCode → Role → Action
            Row {
                Text(
                    text = event.keyCodeName.removePrefix("KEYCODE_"),
                    color = Color.Cyan,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                )
                Text(
                    text = " → ",
                    color = Color.Gray,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                )
                Text(
                    text = event.role?.name ?: "?",
                    color = Color.Yellow,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                )
                Text(
                    text = " → ",
                    color = Color.Gray,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                )
                Text(
                    text = event.action?.name ?: "null",
                    color = if (event.action != null) Color.Green else Color.Red,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                )
            }

            // Line 2: Screen + Zone + Handled
            Row {
                Text(
                    text = "screen=",
                    color = Color.Gray,
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace,
                )
                Text(
                    text = event.screenId.name,
                    color = Color.White,
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "zone=",
                    color = Color.Gray,
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace,
                )
                Text(
                    text = event.focusZone?.name ?: "none",
                    color = Color.Magenta,
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (event.handled) "✓" else "✗",
                    color = if (event.handled) Color.Green else Color.Red,
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }
    }
}
