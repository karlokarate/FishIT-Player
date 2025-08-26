package com.chris.m3usuite.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.chris.m3usuite.data.db.AppDatabase
import com.chris.m3usuite.data.db.DbProvider
import com.chris.m3usuite.data.db.ResumeMark
import com.chris.m3usuite.data.repo.XtreamRepository
import com.chris.m3usuite.player.ExternalPlayer
import com.chris.m3usuite.player.PlayerChooser
import com.chris.m3usuite.prefs.SettingsStore
import kotlinx.coroutines.launch
import kotlin.math.max
import coil3.request.ImageRequest
import com.chris.m3usuite.ui.util.buildImageRequest
import com.chris.m3usuite.ui.util.rememberImageHeaders
import com.chris.m3usuite.data.repo.KidContentRepository
import com.chris.m3usuite.data.db.Profile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VodDetailScreen(
    id: Long,
    // optional: interner Player (url, startMs)
    openInternal: ((url: String, startMs: Long?) -> Unit)? = null
) {
    val ctx = LocalContext.current
    val headers = rememberImageHeaders()

    val db: AppDatabase = remember { DbProvider.get(ctx) }
    val repo: XtreamRepository = remember { XtreamRepository(ctx, SettingsStore(ctx)) }
    val scope = rememberCoroutineScope()
    val kidRepo = remember { KidContentRepository(ctx) }
    val store = remember { SettingsStore(ctx) }
    val haptics = LocalHapticFeedback.current
    val hapticsEnabled by store.hapticsEnabled.collectAsState(initial = false)

    var title by remember { mutableStateOf("") }
    var poster by remember { mutableStateOf<String?>(null) }
    var backdrop by remember { mutableStateOf<String?>(null) }
    var plot by remember { mutableStateOf<String?>(null) }
    var rating by remember { mutableStateOf<Double?>(null) }
    var duration by remember { mutableStateOf<Int?>(null) }
    var url by remember { mutableStateOf<String?>(null) }

    var resumeSecs by rememberSaveable { mutableStateOf<Int?>(null) }

    LaunchedEffect(id) {
        val item = db.mediaDao().byId(id) ?: return@LaunchedEffect
        title = item.name
        poster = item.poster
        backdrop = item.backdrop
        plot = item.plot
        rating = item.rating
        duration = item.durationSecs
        url = item.url

        resumeSecs = db.resumeDao().getVod(id)?.positionSecs

        if (plot.isNullOrBlank() || poster.isNullOrBlank() || duration == null) {
            repo.enrichVodDetailsOnce(id).onSuccess {
                db.mediaDao().byId(id)?.let { upd ->
                    poster = upd.poster
                    backdrop = upd.backdrop
                    plot = upd.plot
                    rating = upd.rating
                    duration = upd.durationSecs
                }
            }
        }
    }

    fun setResume(newSecs: Int) = scope.launch {
        val pos = max(0, newSecs)
        resumeSecs = pos
        db.resumeDao().upsert(
            ResumeMark(
                type = "vod",
                mediaId = id,
                episodeId = null,
                positionSecs = pos,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    fun clearResume() = scope.launch {
        resumeSecs = null
        db.resumeDao().clearVod(id)
    }

    fun play(fromStart: Boolean = false) {
        val startMs: Long? = if (!fromStart) resumeSecs?.toLong()?.times(1000) else null
        url?.let { u ->
            scope.launch {
                PlayerChooser.start(
                    context = ctx,
                    store = store,
                    url = u,
                    headers = emptyMap(),
                    startPositionMs = startMs
                ) { s -> openInternal?.invoke(u, s) ?: ExternalPlayer.open(context = ctx, url = u, startPositionMs = s) }
            }
        }
    }

    // Adult
    val profileId by store.currentProfileId.collectAsState(initial = -1L)
    var isAdult by remember { mutableStateOf(true) }
    LaunchedEffect(profileId) { isAdult = withContext(Dispatchers.IO) { DbProvider.get(ctx).profileDao().byId(profileId)?.type != "kid" } }

    var showGrantSheet by remember { mutableStateOf(false) }
    var showRevokeSheet by remember { mutableStateOf(false) }

    @Composable
    fun KidSelectSheet(onConfirm: suspend (kidIds: List<Long>) -> Unit, onDismiss: () -> Unit) {
        var kids by remember { mutableStateOf<List<Profile>>(emptyList()) }
        LaunchedEffect(profileId) { kids = withContext(Dispatchers.IO) { DbProvider.get(ctx).profileDao().all().filter { it.type == "kid" } } }
        var checked by remember { mutableStateOf(setOf<Long>()) }
        ModalBottomSheet(onDismissRequest = onDismiss) {
            Column(Modifier.padding(16.dp)) {
                Text("Kinder auswählen")
                Spacer(Modifier.height(8.dp))
                kids.forEach { k ->
                    val isC = k.id in checked
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(k.name)
                        Switch(checked = isC, onCheckedChange = { v -> checked = if (v) checked + k.id else checked - k.id })
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onDismiss) { Text("Abbrechen") }
                    Button(onClick = { scope.launch { onConfirm(checked.toList()); onDismiss() } }, enabled = checked.isNotEmpty()) { Text("OK") }
                }
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier.clickable(enabled = url != null) { play(fromStart = false) }
        ) {
            AsyncImage(
                model = buildImageRequest(ctx, backdrop ?: poster, headers),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(260.dp)
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(260.dp)
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Transparent,
                            0.6f to Color(0x66000000),
                            1f to MaterialTheme.colorScheme.background
                        )
                    )
            )
        }

        Column(Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 2,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
                    .clickable(enabled = url != null) { play(fromStart = false) }
            )

            Spacer(Modifier.height(8.dp))

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                rating?.let { Text("★ ${"%.1f".format(it)}  ") }
                duration?.let { Text("• ${it / 60} min") }
                Spacer(Modifier.weight(1f))
                if (isAdult) {
                    TextButton(onClick = { showGrantSheet = true }) { Text("Für Kind(er) freigeben…") }
                    TextButton(onClick = { showRevokeSheet = true }) { Text("Aus Kinderprofil entfernen…") }
                }
            }

            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (resumeSecs != null) {
                    AssistChip(onClick = { play(false) }, label = { Text("Fortsetzen ab ${fmt(resumeSecs!!)}") })
                    AssistChip(onClick = { clearResume() }, label = { Text("Zurücksetzen") })
                    AssistChip(onClick = { setResume(max(0, (resumeSecs ?: 0) - 30)) }, label = { Text("-30s") })
                    AssistChip(onClick = { setResume((resumeSecs ?: 0) + 30) }, label = { Text("+30s") })
                    AssistChip(onClick = { setResume((resumeSecs ?: 0) + 300) }, label = { Text("+5m") })
                } else {
                    AssistChip(onClick = { setResume(0) }, label = { Text("Resume setzen (0:00)") })
                }
                AssistChip(onClick = { play(true) }, label = { Text("Von Anfang") })
            }

            Spacer(Modifier.height(12.dp))

            if (!plot.isNullOrBlank()) {
                Text(
                    plot!!,
                    maxLines = 8,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = url != null) { play(fromStart = false) }
                )
            }

            Spacer(Modifier.height(12.dp))
            Text(
                "Tippe auf Poster oder Titel, um abzuspielen.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary
            )
        }
    }

    if (showGrantSheet) KidSelectSheet(onConfirm = { kidIds ->
        scope.launch(Dispatchers.IO) { kidIds.forEach { kidRepo.allowBulk(it, "vod", listOf(id)) } }
        showGrantSheet = false
    }, onDismiss = { showGrantSheet = false })
    if (showRevokeSheet) KidSelectSheet(onConfirm = { kidIds ->
        scope.launch(Dispatchers.IO) { kidIds.forEach { kidRepo.disallowBulk(it, "vod", listOf(id)) } }
        showRevokeSheet = false
    }, onDismiss = { showRevokeSheet = false })
}

private fun fmt(totalSecs: Int): String {
    val s = max(0, totalSecs)
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
}
