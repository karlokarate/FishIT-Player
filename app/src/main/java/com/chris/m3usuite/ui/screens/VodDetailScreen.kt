package com.chris.m3usuite.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloat
import coil3.compose.AsyncImage
import com.chris.m3usuite.data.repo.ResumeRepository
// XtreamRepository not needed for OBX-only flow
import com.chris.m3usuite.player.ExternalPlayer
import com.chris.m3usuite.player.PlayerChooser
import com.chris.m3usuite.player.InternalPlayerScreen
import com.chris.m3usuite.prefs.SettingsStore
import kotlinx.coroutines.launch
import kotlin.math.max
import com.chris.m3usuite.ui.util.buildImageRequest
import com.chris.m3usuite.ui.util.rememberImageHeaders
import com.chris.m3usuite.data.repo.KidContentRepository
// Room removed
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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.geometry.Offset
import androidx.compose.runtime.derivedStateOf
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VodDetailScreen(
    id: Long,
    // optional: interner Player (url, startMs)
    openInternal: ((url: String, startMs: Long?) -> Unit)? = null,
    onLogo: (() -> Unit)? = null
) {
    val ctx = LocalContext.current
    val headers = rememberImageHeaders()
    val store = remember { SettingsStore(ctx) }
    // OBX-only (Room removed)
    val tgRepo: com.chris.m3usuite.data.repo.TelegramRepository = remember { com.chris.m3usuite.data.repo.TelegramRepository(ctx, SettingsStore(ctx)) }
    val scope = rememberCoroutineScope()
    val kidRepo = remember { KidContentRepository(ctx) }
    val mediaRepo = remember { com.chris.m3usuite.data.repo.MediaQueryRepository(ctx, store) }
    val haptics = LocalHapticFeedback.current
    val hapticsEnabled by store.hapticsEnabled.collectAsStateWithLifecycle(initialValue = false)

    var title by remember { mutableStateOf("") }
    var poster by remember { mutableStateOf<String?>(null) }
    var backdrop by remember { mutableStateOf<String?>(null) }
    var plot by remember { mutableStateOf<String?>(null) }
    var rating by remember { mutableStateOf<Double?>(null) }
    var duration by remember { mutableStateOf<Int?>(null) }
    var url by remember { mutableStateOf<String?>(null) }

    var resumeSecs by rememberSaveable { mutableStateOf<Int?>(null) }
    val resumeRepo = remember { ResumeRepository(ctx) }

    LaunchedEffect(id) {
        fun decodeObxVodId(v: Long): Int? = if (v >= 2_000_000_000_000L && v < 3_000_000_000_000L) (v - 2_000_000_000_000L).toInt() else null
        val obxVid = decodeObxVodId(id)
        if (obxVid != null) {
            val box = com.chris.m3usuite.data.obx.ObxStore.get(ctx).boxFor(com.chris.m3usuite.data.obx.ObxVod::class.java)
            val row = box.query(com.chris.m3usuite.data.obx.ObxVod_.vodId.equal(obxVid.toLong())).build().findFirst()
            title = (row?.name ?: "").substringAfter(" - ", row?.name ?: "")
            poster = row?.poster
            backdrop = row?.imagesJson?.let { runCatching { kotlinx.serialization.json.Json.parseToJsonElement(it).jsonArray.firstOrNull()?.jsonPrimitive?.content }.getOrNull() }
            plot = row?.plot
            rating = row?.rating
            duration = null
            // Build play URL
            val scheme = if (store.xtPort.first() == 443) "https" else "http"
            val http = com.chris.m3usuite.core.http.HttpClientFactory.create(ctx, store)
            val client = com.chris.m3usuite.core.xtream.XtreamClient(http)
            val caps = com.chris.m3usuite.core.xtream.ProviderCapabilityStore(ctx)
            val ports = com.chris.m3usuite.core.xtream.EndpointPortStore(ctx)
            client.initialize(
                scheme = scheme,
                host = store.xtHost.first(),
                username = store.xtUser.first(),
                password = store.xtPass.first(),
                basePath = null,
                store = caps,
                portStore = ports,
                portOverride = store.xtPort.first()
            )
            url = client.buildVodPlayUrl(obxVid, row?.containerExt)
            // resume not available for OBX-backed ids via Room; leave null
        } else {
            // No Room fallback available
            return@LaunchedEffect
        }
    }

    fun setResume(newSecs: Int) = scope.launch {
        val pos = max(0, newSecs)
        resumeSecs = pos
        withContext(Dispatchers.IO) { resumeRepo.setVodResume(id, pos) }
    }

    fun clearResume() = scope.launch {
        resumeSecs = null
        withContext(Dispatchers.IO) { resumeRepo.clearVod(id) }
    }

    // --- Interner Player Zustand (Fullscreen) ---
    var showInternal by rememberSaveable { mutableStateOf(false) }
    var internalUrl by rememberSaveable { mutableStateOf<String?>(null) }
    var internalStartMs by rememberSaveable { mutableStateOf<Long?>(null) }
    var internalUa by rememberSaveable { mutableStateOf("") }
    var internalRef by rememberSaveable { mutableStateOf("") }

    // Wenn interner Player aktiv ist, fullscreen anzeigen
    if (showInternal) {
        val hdrs = buildMap<String, String> {
            if (internalUa.isNotBlank()) put("User-Agent", internalUa)
            if (internalRef.isNotBlank()) put("Referer", internalRef)
        }
        InternalPlayerScreen(
            url = internalUrl.orEmpty(),
            type = "vod",
            mediaId = id,
            startPositionMs = internalStartMs,
            headers = hdrs,
            onExit = { showInternal = false }
        )
        return
    }

    var contentAllowed by remember { mutableStateOf(true) }
    LaunchedEffect(id) {
        val isAdultNow = withContext(Dispatchers.IO) { com.chris.m3usuite.data.obx.ObxStore.get(ctx).boxFor(com.chris.m3usuite.data.obx.ObxProfile::class.java).get(store.currentProfileId.first())?.type == "adult" }
        contentAllowed = if (isAdultNow) true else mediaRepo.isAllowed("vod", id)
    }

    fun play(fromStart: Boolean = false) {
        val startMs: Long? = if (!fromStart) resumeSecs?.toLong()?.times(1000) else null
        url?.let { u ->
            scope.launch {
                if (!contentAllowed) {
                    if (hapticsEnabled) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    android.widget.Toast.makeText(ctx, "Nicht freigegeben", android.widget.Toast.LENGTH_SHORT).show()
                    return@launch
                }
                val hdrs = com.chris.m3usuite.core.http.RequestHeadersProvider.defaultHeaders(store)
                PlayerChooser.start(
                    context = ctx,
                    store = store,
                    url = u,
                    headers = hdrs,
                    startPositionMs = startMs
                ) { s ->
                    if (openInternal != null) {
                        openInternal(u, s)
                    } else {
                        internalUrl = u
                        internalStartMs = s
                        internalUa = hdrs["User-Agent"].orEmpty()
                        internalRef = hdrs["Referer"].orEmpty()
                        showInternal = true
                    }
                }
            }
        }
    }

    // Adult
    val profileId by store.currentProfileId.collectAsStateWithLifecycle(initialValue = -1L)
    var isAdult by remember { mutableStateOf(true) }
    LaunchedEffect(profileId) { isAdult = withContext(Dispatchers.IO) { com.chris.m3usuite.data.obx.ObxStore.get(ctx).boxFor(com.chris.m3usuite.data.obx.ObxProfile::class.java).get(profileId)?.type != "kid" } }

    var showGrantSheet by rememberSaveable { mutableStateOf(false) }
    var showRevokeSheet by rememberSaveable { mutableStateOf(false) }

    @Composable
    fun KidSelectSheet(onConfirm: suspend (kidIds: List<Long>) -> Unit, onDismiss: () -> Unit) {
        var kids by remember { mutableStateOf<List<com.chris.m3usuite.data.obx.ObxProfile>>(emptyList()) }
        LaunchedEffect(profileId) {
            kids = withContext(Dispatchers.IO) { com.chris.m3usuite.data.repo.ProfileObxRepository(ctx).all().filter { it.type == "kid" } }
        }
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
        onLogo = onLogo,
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
        com.chris.m3usuite.ui.fx.FishBackground(
            modifier = Modifier.align(Alignment.Center).size(540.dp),
            alpha = 0.05f
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
            val AccentDyn = if (isAdult) com.chris.m3usuite.ui.theme.DesignTokens.Accent else com.chris.m3usuite.ui.theme.DesignTokens.KidAccent
            val badgeColor = if (!isAdult) AccentDyn.copy(alpha = 0.26f) else AccentDyn.copy(alpha = 0.20f)
            val badgeColorDarker = if (!isAdult) AccentDyn.copy(alpha = 0.32f) else AccentDyn.copy(alpha = 0.26f)
            Surface(shape = androidx.compose.foundation.shape.RoundedCornerShape(50), color = badgeColor, contentColor = Color.White, modifier = Modifier.graphicsLayer(alpha = com.chris.m3usuite.ui.theme.DesignTokens.BadgeAlpha)) {
                Text(text = title, style = MaterialTheme.typography.titleLarge, maxLines = 2, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp).clickable(enabled = url != null) { play(fromStart = false) })
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = AccentDyn.copy(alpha = 0.35f))

            Spacer(Modifier.height(8.dp))

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
                val label = if (resumeSecs != null) "Fortsetzen ab ${fmt(resumeSecs!!)}" else "Abspielen"
                AssistChip(
                    modifier = Modifier.focusScaleOnTv().graphicsLayer(alpha = com.chris.m3usuite.ui.theme.DesignTokens.BadgeAlpha),
                    onClick = { if (resumeSecs != null) play(false) else play(true) },
                    label = { Text(label) },
                    colors = AssistChipDefaults.assistChipColors(containerColor = AccentDyn.copy(alpha = 0.22f))
                )
            }

            // Thin progress pill across full width (minus 5% margins)
            if ((duration ?: 0) > 0 && (resumeSecs ?: 0) > 0) {
                BoxWithConstraints(Modifier.fillMaxWidth().padding(top = 6.dp)) {
                    val total = duration ?: 0
                    val prog = (resumeSecs ?: 0).toFloat() / total.toFloat()
                    val clamped = prog.coerceIn(0f, 1f)
                    val errorColor = MaterialTheme.colorScheme.error
                    Canvas(Modifier.fillMaxWidth().height(8.dp)) {
                        val w = size.width
                        val h = size.height
                        val y = h / 2f
                        val margin = w * 0.05f
                        val start = Offset(margin, y)
                        val end = Offset(w - margin, y)
                        val fillEnd = Offset(start.x + (end.x - start.x) * clamped, y)
                        drawLine(color = Color.White.copy(alpha = 0.35f), start = start, end = end, strokeWidth = 3f, cap = androidx.compose.ui.graphics.StrokeCap.Round)
                        drawLine(color = errorColor, start = start, end = fillEnd, strokeWidth = 3.5f, cap = androidx.compose.ui.graphics.StrokeCap.Round)
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            if (!plot.isNullOrBlank()) {
                var expanded by remember { mutableStateOf(false) }
                Surface(
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                    color = badgeColorDarker,
                    contentColor = Color.White,
                    modifier = Modifier.fillMaxWidth().graphicsLayer(alpha = com.chris.m3usuite.ui.theme.DesignTokens.BadgeAlpha)
                ) {
                    Text(
                        plot!!,
                        maxLines = if (expanded) Int.MAX_VALUE else 8,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp)
                            .clickable(enabled = url != null) { play(fromStart = false) }
                    )
                }
                TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "Weniger anzeigen" else "Mehr anzeigen") }
            }

            Spacer(Modifier.height(12.dp))
            Text(
                "Tippe auf Poster oder Titel, um abzuspielen.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary
            )
        }
        }
        // Overlay sticky-like floating title badge when scrolled
        val showPinned by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 120 } }
        if (showPinned) {
            val AccentDyn2 = if (isAdult) com.chris.m3usuite.ui.theme.DesignTokens.Accent else com.chris.m3usuite.ui.theme.DesignTokens.KidAccent
            val badgeColorSticky = if (!isAdult) AccentDyn2.copy(alpha = 0.26f) else AccentDyn2.copy(alpha = 0.20f)
            Box(Modifier.fillMaxSize()) {
                Row(
                    Modifier
                        .align(Alignment.TopStart)
                        .padding(start = 24.dp, top = 20.dp)
                ) {
                    Surface(shape = androidx.compose.foundation.shape.RoundedCornerShape(50), color = badgeColorSticky, contentColor = Color.White, modifier = Modifier.graphicsLayer(alpha = com.chris.m3usuite.ui.theme.DesignTokens.BadgeAlpha)) {
                        Text(title, modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp))
                    }
                }
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
