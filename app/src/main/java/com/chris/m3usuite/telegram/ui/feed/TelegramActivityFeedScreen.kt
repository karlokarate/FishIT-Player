package com.chris.m3usuite.telegram.ui.feed

import android.app.Application
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.chris.m3usuite.model.MediaItem
import com.chris.m3usuite.ui.focus.focusScaleOnTv
import com.chris.m3usuite.ui.layout.FishTelegramContent
import java.text.SimpleDateFormat
import java.util.*

/**
 * Telegram Activity Feed Screen (Compose).
 *
 * Shows recent Telegram activity:
 * - New messages
 * - Downloads
 * - Parse results
 * - Recent media items
 *
 * Full TV DPAD navigation and touch support.
 */
@Composable
fun TelegramActivityFeedScreen(
    onItemClick: (MediaItem) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val viewModel: TelegramActivityFeedViewModel =
        viewModel(
            factory = TelegramActivityFeedViewModel.factory(context.applicationContext as Application),
        )

    val feedState by viewModel.feedState.collectAsStateWithLifecycle()
    val activityLog by viewModel.activityLog.collectAsStateWithLifecycle()

    // Mark feed as viewed when screen is displayed
    LaunchedEffect(Unit) {
        viewModel.markFeedAsViewed()
    }

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .padding(16.dp),
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Telegram Activity Feed",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )

            // Refresh button
            TextButton(
                onClick = { viewModel.refreshFeed() },
                modifier = Modifier.focusScaleOnTv(),
            ) {
                Text("Aktualisieren")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Content
        val listState = rememberLazyListState()

        LazyColumn(
            state = listState,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 16.dp),
        ) {
            // Recent activity log
            if (activityLog.isNotEmpty()) {
                item {
                    Text(
                        text = "Letzte Aktivitäten",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }

                items(activityLog.take(10)) { entry ->
                    ActivityLogCard(entry = entry)
                }

                item {
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }

            // Recent media items
            if (feedState.feedItems.isNotEmpty()) {
                item {
                    Text(
                        text = "Neue Medien",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }

                items(feedState.feedItems.take(20)) { item ->
                    FishTelegramContent(
                        mediaItem = item,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(180.dp)
                                .focusScaleOnTv(),
                        showNew = true,
                        onClick = { onItemClick(item) },
                    )
                }
            } else {
                item {
                    Card(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 24.dp),
                    ) {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(32.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "Keine neuen Aktivitäten",
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Activity log card displaying a single activity entry.
 */
@Composable
private fun ActivityLogCard(
    entry: ActivityLogEntry,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier =
            modifier
                .fillMaxWidth()
                .focusScaleOnTv(),
    ) {
        Row(
            modifier =
                Modifier
                    .padding(12.dp)
                    .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = entry.description,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            Text(
                text = formatTimestamp(entry.timestamp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Format timestamp for display.
 */
private fun formatTimestamp(timestamp: Long): String {
    val formatter = SimpleDateFormat("HH:mm", Locale.getDefault())
    return formatter.format(Date(timestamp))
}
