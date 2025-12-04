package com.chris.m3usuite.telegram.ui.examples

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.chris.m3usuite.telegram.image.TelegramThumbKey
import com.chris.m3usuite.telegram.image.ThumbKind
import com.chris.m3usuite.ui.util.AppImageLoader

/**
 * Example Composable demonstrating lazy, viewport-driven thumbnail loading.
 *
 * This example shows how to use TelegramThumbKey with Coil 3's AsyncImage
 * for on-demand thumbnail loading from TDLib. Thumbnails are only downloaded
 * when rows enter or approach the viewport.
 *
 * **Key Features:**
 * - Lazy loading: thumbnails download only when needed
 * - Stable cache keys: based on remoteId (survives app restarts)
 * - Automatic caching: Coil handles disk and memory caching
 * - Concurrency limits: Semaphore prevents TDLib overload during fast scrolling
 *
 * **Usage:**
 * ```kotlin
 * TelegramChatMessageList(
 *     messages = viewModel.messages.collectAsState().value
 * )
 * ```
 */
@Composable
fun TelegramChatMessageList(
    messages: List<TelegramMessageUi>,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    LazyColumn(modifier = modifier.fillMaxSize()) {
        items(
            items = messages,
            key = { it.messageId },
        ) { message ->
            TelegramMessageRow(
                message = message,
                context = context,
            )
        }
    }
}

/**
 * Lightweight UI model for a Telegram message.
 *
 * Contains only the data needed for display, avoiding holding full TDLib objects in memory.
 */
data class TelegramMessageUi(
    val messageId: Long,
    val chatId: Long,
    val remoteId: String?,
    val caption: String?,
    val hasThumbnail: Boolean,
)

/**
 * Row displaying a single Telegram message with lazy thumbnail loading.
 */
@Composable
private fun TelegramMessageRow(
    message: TelegramMessageUi,
    context: android.content.Context,
) {
    Box(
        modifier = Modifier.size(200.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (message.hasThumbnail && message.remoteId != null) {
            // Use AsyncImage with TelegramThumbKey for lazy loading
            AsyncImage(
                model =
                    ImageRequest
                        .Builder(context)
                        .data(
                            TelegramThumbKey(
                                remoteId = message.remoteId,
                                kind = ThumbKind.CHAT_MESSAGE,
                                sizeBucket = 256, // 256px size bucket
                            ),
                        ).crossfade(true)
                        .build(),
                contentDescription = message.caption ?: "Telegram message thumbnail",
                imageLoader = AppImageLoader.get(context),
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            // Placeholder when no thumbnail
            Text("No thumbnail")
        }
    }
}

/**
 * Example showing how to trigger full history sync for a chat.
 *
 * This should typically be called from a ViewModel when a chat is added or refreshed.
 *
 * ```kotlin
 * viewModelScope.launch {
 *     val repository = TelegramContentRepository(context, settingsStore)
 *     val serviceClient = T_TelegramServiceClient.getInstance(context)
 *
 *     // Ensure TelegramServiceClient is started
 *     serviceClient.ensureStarted(context, settingsStore)
 *
 *     // Wait for auth to be ready
 *     if (!serviceClient.awaitAuthReady(timeoutMs = 30_000L)) {
 *         // Handle auth not ready
 *         return@launch
 *     }
 *
 *     // Sync full chat history (runs in background, may take time for large chats)
 *     val result = repository.syncFullChatHistory(
 *         chatId = 123456789L,
 *         chatTitle = "My Chat", // Optional, will be resolved if null
 *         serviceClient = serviceClient
 *     )
 *
 *     when {
 *         result.isSuccess -> {
 *             val itemsPersisted = result.getOrNull() ?: 0
 *             Log.i("Sync", "Synced $itemsPersisted items for chat")
 *         }
 *         result.isFailure -> {
 *             val error = result.exceptionOrNull()
 *             Log.e("Sync", "Sync failed: ${error?.message}", error)
 *         }
 *     }
 * }
 * ```
 */
object TelegramSyncExample
