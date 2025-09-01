package com.chris.m3usuite.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.*
import com.chris.m3usuite.ui.fx.FadeThrough
import androidx.compose.animation.animateContentSize
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import java.io.File
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.Alignment
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import android.os.Build
import android.graphics.RenderEffect
import android.graphics.Shader
import androidx.compose.ui.graphics.asComposeRenderEffect
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
import com.chris.m3usuite.ui.util.buildImageRequest
import com.chris.m3usuite.ui.util.rememberImageHeaders
import com.chris.m3usuite.data.repo.KidContentRepository
import com.chris.m3usuite.data.db.Profile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.CircleShape
import coil3.request.ImageRequest
import com.chris.m3usuite.ui.util.rememberAvatarModel
import com.chris.m3usuite.ui.skin.focusScaleOnTv
import com.chris.m3usuite.ui.skin.tvClickable
import com.chris.m3usuite.ui.home.HomeChromeScaffold
import androidx.compose.foundation.lazy.rememberLazyListState
import kotlinx.coroutines.flow.first

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
                // Build headers like in Live detail
                val hdrs = buildMap<String, String> {
                    val ua = store.userAgent.first()
                    val ref = store.referer.first()
                    if (ua.isNotBlank()) put("User-Agent", ua)
                    if (ref.isNotBlank()) put("Referer", ref)
                }
                PlayerChooser.start(
                    context = ctx,
                    store = store,
                    url = u,
                    headers = hdrs,
                    startPositionMs = startMs
                ) { s -> openInternal?.invoke(u, s) ?: ExternalPlayer.open(context = ctx, url = u, startPositionMs = s) }
            }
        }
    }

    // Adult
    val profileId by store.currentProfileId.collectAsState(initial = -1L)
    var isAdult by remember { mutableStateOf(true) }
    LaunchedEffect(profileId) { isAdult = withContext(Dispatchers.IO) { DbProvider.get(ctx).profileDao().byId(profileId)?.type != "kid" } }

    var showGrantSheet by rememberSaveable { mutableStateOf(false) }
    var showRevokeSheet by rememberSaveable { mutableStateOf(false) }

    @Composable
    fun KidSelectSheet(onConfirm: suspend (kidIds: List<Long>) -> Unit, onDismiss: () -> Unit) {
        var kids by remember { mutableStateOf<List<Profile>>(emptyList()) }
        LaunchedEffect(profileId) { kids = withContext(Dispatchers.IO) { DbProvider.get(ctx).profileDao().all().filter { it.type == "kid" } } }
        var checked by remember { mutableStateOf(setOf<Long>()) }
        ModalBottomSheet(onDismissRequest = onDismiss) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 520.dp),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item { Text("Kinder auswählen") }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { checked = kids.map { it.id }.toSet() }, enabled = kids.isNotEmpty()) { Text("Alle auswählen") }
                        TextButton(onClick = { checked = emptySet() }, enabled = checked.isNotEmpty()) { Text("Keine auswählen") }
                    }
                }
                items(kids, key = { it.id }) { k ->
                    val isC = k.id in checked
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .tvClickable {
                                val v = !isC
                                checked = if (v) checked + k.id else checked - k.id
                            },
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            val model = rememberAvatarModel(k.avatarPath)
                            if (model != null) {
                                AsyncImage(
                                    model = ImageRequest.Builder(ctx)
                                        .data(model)
                                        .build(),
                                    contentDescription = null,
                                    modifier = Modifier.size(32.dp).clip(CircleShape),
                                    contentScale = ContentScale.Crop,
                                    placeholder = androidx.compose.ui.res.painterResource(android.R.drawable.ic_menu_report_image),
                                    error = androidx.compose.ui.res.painterResource(android.R.drawable.ic_menu_report_image),
                                    fallback = androidx.compose.ui.res.painterResource(android.R.drawable.ic_menu_report_image)
                                )
                            }
                            Text(k.name)
                        }
                        Switch(checked = isC, onCheckedChange = { v -> checked = if (v) checked + k.id else checked - k.id })
                    }
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        val Accent = com.chris.m3usuite.ui.theme.DesignTokens.Accent
                        TextButton(modifier = Modifier.weight(1f).focusScaleOnTv(), onClick = onDismiss, colors = ButtonDefaults.textButtonColors(contentColor = Accent)) { Text("Abbrechen") }
                        Button(modifier = Modifier.weight(1f).focusScaleOnTv(), onClick = { scope.launch { onConfirm(checked.toList()); onDismiss() } }, enabled = checked.isNotEmpty(), colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Color.Black)) { Text("OK") }
                    }
                }
            }
        }
    }

    val snackHost = remember { SnackbarHostState() }
    val listState = rememberLazyListState()
    HomeChromeScaffold(
        title = "Details",
        onSettings = null,
        onSearch = null,
        onProfiles = null,
        onRefresh = null,
        listState = listState,
        bottomBar = {}
    ) { pads ->
    Box(modifier = Modifier.fillMaxSize().padding(pads)) {
        val Accent = com.chris.m3usuite.ui.theme.DesignTokens.Accent
        // Background
        Box(
            Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        0f to MaterialTheme.colorScheme.background,
                        1f to MaterialTheme.colorScheme.surface
                    )
                )
        )
        // Radial accent glow + blurred icon
        Box(
            Modifier
                .matchParentSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(Accent.copy(alpha = 0.12f), Color.Transparent),
                        radius = with(LocalDensity.current) { 660.dp.toPx() }
                    )
                )
        )
        Image(
            painter = painterResource(id = com.chris.m3usuite.R.drawable.fisch),
            contentDescription = null,
            modifier = Modifier
                .align(Alignment.Center)
                .size(540.dp)
                .graphicsLayer { alpha = 0.05f; try { if (Build.VERSION.SDK_INT >= 31) renderEffect = android.graphics.RenderEffect.createBlurEffect(36f, 36f, android.graphics.Shader.TileMode.CLAMP).asComposeRenderEffect() } catch (_: Throwable) {} }
        )
        com.chris.m3usuite.ui.common.AccentCard(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            accent = Accent
        ) {
        Column(Modifier.animateContentSize()) {
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

        Column(Modifier.padding(top = 12.dp)) {
            Text(text = title, style = MaterialTheme.typography.titleLarge, maxLines = 2, modifier = Modifier.fillMaxWidth().clickable(enabled = url != null) { play(fromStart = false) })
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = com.chris.m3usuite.ui.theme.DesignTokens.Accent.copy(alpha = 0.35f))

            Spacer(Modifier.height(8.dp))

            val Accent = if (isAdult) com.chris.m3usuite.ui.theme.DesignTokens.Accent else com.chris.m3usuite.ui.theme.DesignTokens.KidAccent
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                rating?.let { Text("★ ${"%.1f".format(it)}  ") }
                duration?.let { Text("• ${it / 60} min") }
                Spacer(Modifier.weight(1f))
                if (isAdult) {
                    com.chris.m3usuite.ui.common.AppIconButton(icon = com.chris.m3usuite.ui.common.AppIcon.AddKid, variant = com.chris.m3usuite.ui.common.IconVariant.Solid, contentDescription = "Für Kinder freigeben", onClick = { showGrantSheet = true })
                    com.chris.m3usuite.ui.common.AppIconButton(icon = com.chris.m3usuite.ui.common.AppIcon.RemoveKid, variant = com.chris.m3usuite.ui.common.IconVariant.Solid, contentDescription = "Aus Kinderprofil entfernen", onClick = { showRevokeSheet = true })
                }
            }

            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (resumeSecs != null) {
                    AssistChip(modifier = Modifier.focusScaleOnTv(), onClick = { play(false) }, label = { Text("Fortsetzen ab ${fmt(resumeSecs!!)}") }, colors = AssistChipDefaults.assistChipColors(containerColor = Accent.copy(alpha = 0.22f)))
                    AssistChip(modifier = Modifier.focusScaleOnTv(), onClick = { clearResume() }, label = { Text("Zurücksetzen") })
                    AssistChip(modifier = Modifier.focusScaleOnTv(), onClick = { setResume(max(0, (resumeSecs ?: 0) - 30)) }, label = { Text("-30s") }, colors = AssistChipDefaults.assistChipColors(containerColor = Accent.copy(alpha = 0.16f)))
                    AssistChip(modifier = Modifier.focusScaleOnTv(), onClick = { setResume((resumeSecs ?: 0) + 30) }, label = { Text("+30s") }, colors = AssistChipDefaults.assistChipColors(containerColor = Accent.copy(alpha = 0.16f)))
                    AssistChip(modifier = Modifier.focusScaleOnTv(), onClick = { setResume((resumeSecs ?: 0) + 300) }, label = { Text("+5m") }, colors = AssistChipDefaults.assistChipColors(containerColor = Accent.copy(alpha = 0.16f)))
                } else {
                    AssistChip(modifier = Modifier.focusScaleOnTv(), onClick = { setResume(0) }, label = { Text("Resume setzen (0:00)") }, colors = AssistChipDefaults.assistChipColors(containerColor = Accent.copy(alpha = 0.20f)))
                }
                AssistChip(modifier = Modifier.focusScaleOnTv(), onClick = { play(true) }, label = { Text("Von Anfang") }, colors = AssistChipDefaults.assistChipColors(containerColor = Accent.copy(alpha = 0.22f)))
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
    }

    if (showGrantSheet) KidSelectSheet(onConfirm = { kidIds ->
        scope.launch(Dispatchers.IO) { kidIds.forEach { kidRepo.allowBulk(it, "vod", listOf(id)) } }
        scope.launch { snackHost.showSnackbar("VOD freigegeben für ${kidIds.size} Kinder") }
        showGrantSheet = false
    }, onDismiss = { showGrantSheet = false })
    if (showRevokeSheet) KidSelectSheet(onConfirm = { kidIds ->
        scope.launch(Dispatchers.IO) { kidIds.forEach { kidRepo.disallowBulk(it, "vod", listOf(id)) } }
        scope.launch { snackHost.showSnackbar("VOD aus ${kidIds.size} Kinderprofil(en) entfernt") }
        showRevokeSheet = false
    }, onDismiss = { showRevokeSheet = false })
}}
}

private fun fmt(totalSecs: Int): String {
    val s = max(0, totalSecs)
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
}
