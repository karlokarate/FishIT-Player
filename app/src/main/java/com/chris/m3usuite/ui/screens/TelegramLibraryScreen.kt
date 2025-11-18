package com.chris.m3usuite.ui.screens

import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.paging.compose.collectAsLazyPagingItems
import com.chris.m3usuite.core.http.RequestHeadersProvider
import com.chris.m3usuite.core.playback.PlayUrlHelper
import com.chris.m3usuite.data.obx.toMediaItem
import com.chris.m3usuite.model.MediaItem
import com.chris.m3usuite.prefs.SettingsStore
import com.chris.m3usuite.telegram.live.TelegramLiveRepository
import com.chris.m3usuite.telegram.service.TelegramServiceClient
import com.chris.m3usuite.ui.focus.OnPrefetchPaged
import com.chris.m3usuite.ui.home.HomeChromeScaffold
import com.chris.m3usuite.ui.layout.*
import com.chris.m3usuite.ui.theme.CategoryFonts
import com.chris.m3usuite.ui.util.rememberImageHeaders
import kotlinx.coroutines.launch
import java.util.Locale

private enum class TelegramTab { Movies, Series }

/**
 * TelegramLibraryScreen: Dedicated full-screen view for all Telegram content.
 * 
 * Organizes parsed Telegram content (movies and series) from selected chats,
 * displaying them in rows using FishRow/TelegramFishTile patterns consistent
 * with Xtream UI.
 * 
 * Architecture:
 * - Uses centralized TelegramLiveRepository for data access
 * - ObjectBox-based indexing with reusable keys
 * - Coroutine-based TDLib integration for playback
 * - Reuses FishRow/FishTile components for consistency
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TelegramLibraryScreen(
    navController: NavHostController,
    onBack: (() -> Unit)? = null
) {
    LaunchedEffect(Unit) {
        com.chris.m3usuite.metrics.RouteTag.set("telegram_library")
        com.chris.m3usuite.core.debug.GlobalDebug.logTree("telegram_library:root")
    }

    val ctx = LocalContext.current
    val store = remember { SettingsStore(ctx) }
    val telegramRepo = remember { TelegramLiveRepository(ctx) }
    val telegramServiceClient = remember { TelegramServiceClient(ctx.applicationContext) }
    val obxRepo = remember { com.chris.m3usuite.data.repo.XtreamObxRepository(ctx, store) }
    
    val tgChatsCsv by store.tgSelectedChatsCsv.collectAsStateWithLifecycle(initialValue = "")
    val tgEnabled by store.tgEnabled.collectAsStateWithLifecycle(initialValue = false)
    
    val scope = rememberCoroutineScope()
    val imageHeaders = rememberImageHeaders(store)
    
    // Tab selection state
    var selectedTab by remember { mutableStateOf(TelegramTab.Movies) }
    
    // Parse selected chat IDs
    val selectedChatIds = remember(tgChatsCsv) {
        if (tgChatsCsv.isBlank()) emptyList()
        else tgChatsCsv.split(",").mapNotNull { it.trim().toLongOrNull() }
    }

    // Centralized playback launcher
    val playbackLauncher = com.chris.m3usuite.playback.rememberPlaybackLauncher(
        onOpenInternal = { pr ->
            val encoded = PlayUrlHelper.encodeUrl(pr.url)
            val mimeArg = pr.mimeType?.let { Uri.encode(it) } ?: ""
            when (pr.type) {
                "vod" -> navController.navigate("player?url=$encoded&type=vod&mediaId=${pr.mediaId ?: -1}&startMs=${pr.startPositionMs ?: -1}&mime=$mimeArg")
                "series" -> navController.navigate("player?url=$encoded&type=series&seriesId=${pr.seriesId ?: -1}&season=${pr.season ?: -1}&episodeNum=${pr.episodeNum ?: -1}&episodeId=${pr.episodeId ?: -1}&startMs=${pr.startPositionMs ?: -1}&mime=$mimeArg")
                else -> navController.navigate("player?url=$encoded&type=vod&mediaId=${pr.mediaId ?: -1}&startMs=${pr.startPositionMs ?: -1}&mime=$mimeArg")
            }
        }
    )

    val telegramHeaders = remember { RequestHeadersProvider.defaultHeadersBlocking(store) }
    
    // Navigation handlers
    val openTelegramDetail: (MediaItem) -> Unit = remember(navController) {
        { item ->
            val chat = item.tgChatId
            val message = item.tgMessageId
            if (chat != null && message != null) {
                navController.navigate("telegram/$chat/$message")
            }
        }
    }

    val playTelegram: (MediaItem) -> Unit = remember(playbackLauncher, telegramHeaders, telegramServiceClient) {
        { media ->
            scope.launch {
                val chatId = media.tgChatId ?: return@launch
                val messageId = media.tgMessageId ?: return@launch
                try {
                    val uri = PlayUrlHelper.tgPlayUri(
                        chatId = chatId,
                        messageId = messageId,
                        svc = telegramServiceClient
                    )
                    playbackLauncher.launch(
                        com.chris.m3usuite.playback.PlayRequest(
                            type = "vod",
                            url = uri.toString(),
                            mediaId = media.id,
                            headers = telegramHeaders,
                            chooserLabel = media.name
                        )
                    )
                } catch (t: Throwable) {
                    com.chris.m3usuite.core.debug.GlobalDebug.log("TgLibrary", "playTelegram failed: ${t.message}")
                }
            }
        }
    }

    HomeChromeScaffold(
        navController = navController,
        showBottomBar = false,
        topBar = {
            TopAppBar(
                title = { Text("Telegram Bibliothek") },
                navigationIcon = if (onBack != null) {
                    {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Zurück")
                        }
                    }
                } else {
                    {}
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Status message if Telegram is not enabled or no chats selected
            if (!tgEnabled || selectedChatIds.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = if (!tgEnabled) {
                                "Telegram ist nicht aktiviert"
                            } else {
                                "Keine Chats ausgewählt"
                            },
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "Bitte konfigurieren Sie Telegram in den Einstellungen",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                return@HomeChromeScaffold
            }

            // Tab selector
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = selectedTab == TelegramTab.Movies,
                    onClick = { selectedTab = TelegramTab.Movies },
                    label = { Text("Filme") }
                )
                FilterChip(
                    selected = selectedTab == TelegramTab.Series,
                    onClick = { selectedTab = TelegramTab.Series },
                    label = { Text("Serien") }
                )
            }

            // Content based on selected tab
            val listState = rememberLazyListState()
            
            // Prefetcher for images
            val telegramPrefetcher: OnPrefetchPaged = remember(ctx, imageHeaders) {
                { indices, items ->
                    val posters = indices.mapNotNull { idx -> items[idx]?.primaryTelegramPoster() }
                    com.chris.m3usuite.ui.util.AppImageLoader.get(ctx).prefetchImages(posters, imageHeaders)
                }
            }

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                when (selectedTab) {
                    TelegramTab.Movies -> {
                        // Show movies from each selected chat
                        items(selectedChatIds) { chatId ->
                            TelegramChatMovieRow(
                                chatId = chatId,
                                telegramRepo = telegramRepo,
                                onOpenDetails = openTelegramDetail,
                                onPlay = playTelegram,
                                onPrefetchPaged = telegramPrefetcher
                            )
                        }
                    }
                    TelegramTab.Series -> {
                        // Show aggregated series row
                        item {
                            TelegramAggregatedSeriesRow(
                                obxRepo = obxRepo,
                                ctx = ctx,
                                onOpenDetails = openTelegramDetail,
                                onPlay = playTelegram,
                                onPrefetchPaged = telegramPrefetcher
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Row displaying movies from a single Telegram chat
 */
@Composable
private fun TelegramChatMovieRow(
    chatId: Long,
    telegramRepo: TelegramLiveRepository,
    onOpenDetails: (MediaItem) -> Unit,
    onPlay: (MediaItem) -> Unit,
    onPrefetchPaged: OnPrefetchPaged
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var chatTitle by remember(chatId) { mutableStateOf("Chat $chatId") }
    var items by remember(chatId) { mutableStateOf<List<MediaItem>>(emptyList()) }
    var isLoading by remember(chatId) { mutableStateOf(true) }

    // Load chat title and items
    LaunchedEffect(chatId) {
        scope.launch {
            try {
                // Get chat title
                telegramRepo.chatTitle(chatId)?.let { chatTitle = it }
                
                // Load movies from this chat (load first batch directly from search)
                items = telegramRepo.searchAllVideos("", limit = 60)
                    .filter { it.tgChatId == chatId }
                    .map { it.toMediaItem(ctx) }
            } catch (t: Throwable) {
                com.chris.m3usuite.core.debug.GlobalDebug.log("TgLibrary", "Failed to load chat $chatId: ${t.message}")
            } finally {
                isLoading = false
            }
        }
    }

    if (isLoading) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
        return
    }

    if (items.isEmpty()) {
        return
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Header
        Text(
            text = "Telegram – $chatTitle",
            style = MaterialTheme.typography.titleMedium.copy(
                fontFamily = CategoryFonts.primary,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp
            ),
            modifier = Modifier.padding(start = 8.dp)
        )

        // Row of tiles
        FishRow(
            items = items,
            stateKey = "tg_chat_$chatId",
            onPrefetchKeys = { _, _ -> },
            header = null
        ) { media ->
            TelegramFishTile(
                media = media,
                onOpenDetails = onOpenDetails,
                onPlay = onPlay
            )
        }
    }
}

/**
 * Row displaying aggregated Telegram series
 */
@Composable
private fun TelegramAggregatedSeriesRow(
    obxRepo: com.chris.m3usuite.data.repo.XtreamObxRepository,
    ctx: android.content.Context,
    onOpenDetails: (MediaItem) -> Unit,
    onPlay: (MediaItem) -> Unit,
    onPrefetchPaged: OnPrefetchPaged
) {
    var items by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        try {
            items = obxRepo.seriesByProviderKeyNewest("telegram", 0, 120)
                .map { it.toMediaItem(ctx) }
        } catch (t: Throwable) {
            com.chris.m3usuite.core.debug.GlobalDebug.log("TgLibrary", "Failed to load series: ${t.message}")
        } finally {
            isLoading = false
        }
    }

    if (isLoading) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
        return
    }

    if (items.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Keine Serien gefunden",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Header
        Text(
            text = "Telegram Serien",
            style = MaterialTheme.typography.titleMedium.copy(
                fontFamily = CategoryFonts.primary,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp
            ),
            modifier = Modifier.padding(start = 8.dp)
        )

        // Row of tiles
        FishRow(
            items = items,
            stateKey = "tg_series_aggregated",
            onPrefetchKeys = { _, _ -> },
            header = null
        ) { media ->
            SeriesFishTile(
                media = media,
                onOpenDetails = onOpenDetails,
                onPlay = onPlay,
                onAssign = null
            )
        }
    }
}
