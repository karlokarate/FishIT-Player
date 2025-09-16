package com.chris.m3usuite.ui.screens

import android.os.Build
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloat
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
// Room access moved behind feature gate to avoid opening DB in OBX-first paths
// Room removed
import com.chris.m3usuite.data.repo.EpgRepository
import com.chris.m3usuite.data.repo.KidContentRepository
import com.chris.m3usuite.player.InternalPlayerScreen
import com.chris.m3usuite.player.PlayerChooser
import com.chris.m3usuite.prefs.SettingsStore
import com.chris.m3usuite.ui.home.HomeChromeScaffold
import com.chris.m3usuite.ui.components.sheets.KidSelectSheet
import com.chris.m3usuite.ui.skin.focusScaleOnTv
import com.chris.m3usuite.ui.skin.tvClickable
import com.chris.m3usuite.ui.util.buildImageRequest
import com.chris.m3usuite.ui.util.rememberAvatarModel
import com.chris.m3usuite.ui.util.rememberImageHeaders
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import io.objectbox.android.AndroidScheduler

@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveDetailScreen(id: Long, onLogo: (() -> Unit)? = null) {
    val ctx = LocalContext.current
    val store = remember { SettingsStore(ctx) }
    // Room removed; OBX-only path
    val headersImg = rememberImageHeaders()
    val scope = rememberCoroutineScope()
    val kidRepo = remember { KidContentRepository(ctx) }
    val haptics = LocalHapticFeedback.current
    val hapticsEnabled by store.hapticsEnabled.collectAsStateWithLifecycle(initialValue = false)

    var title by remember { mutableStateOf("") }
    var logo by remember { mutableStateOf<String?>(null) }
    var url by remember { mutableStateOf<String?>(null) }

    // EPG-Infos
    var epgNow by remember { mutableStateOf("") }
    var epgNext by remember { mutableStateOf("") }
    var nowStartMs by remember { mutableStateOf<Long?>(null) }
    var nowEndMs by remember { mutableStateOf<Long?>(null) }
    var showEpg by rememberSaveable { mutableStateOf(false) } // Overlay behält Zustand bei Rotation

    // --- Interner Player Zustand (Fullscreen) ---
    var showInternal by rememberSaveable { mutableStateOf(false) }
    var internalUrl by rememberSaveable { mutableStateOf<String?>(null) }
    var internalStartMs by rememberSaveable { mutableStateOf<Long?>(null) }
    // Persist UA/Referer to reconstruct headers on rotation
    var internalUa by rememberSaveable { mutableStateOf("") }
    var internalRef by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(id) {
        // Support OBX-encoded IDs and legacy Room IDs
        fun decodeObxLiveId(v: Long): Int? = if (v >= 1_000_000_000_000L && v < 2_000_000_000_000L) (v - 1_000_000_000_000L).toInt() else null
        val obxSid = decodeObxLiveId(id)
        val sid: Int?
        if (obxSid != null) {
            sid = obxSid
            val box = com.chris.m3usuite.data.obx.ObxStore.get(ctx).boxFor(com.chris.m3usuite.data.obx.ObxLive::class.java)
            val row = box.query(com.chris.m3usuite.data.obx.ObxLive_.streamId.equal(sid.toLong())).build().findFirst()
            title = row?.name.orEmpty()
            logo = row?.logo
            // Build play URL via Xtream client
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
            url = client.buildLivePlayUrl(sid)
        } else {
            // No Room fallback; abort gracefully
            sid = null
        }

        if (sid != null) {
            val list = runCatching { EpgRepository(ctx, store).nowNext(sid, 2) }.getOrDefault(emptyList())
            epgNow = list.getOrNull(0)?.title.orEmpty()
            epgNext = list.getOrNull(1)?.title.orEmpty()
            nowStartMs = list.getOrNull(0)?.start?.toLongOrNull()?.let { it * 1000 }
            nowEndMs = list.getOrNull(0)?.end?.toLongOrNull()?.let { it * 1000 }
        } else { epgNow = ""; epgNext = ""; nowStartMs = null; nowEndMs = null }
    }

    // Direct ObjectBox EPG read (fast path) with light polling for updates
    DisposableEffect(id) {
        fun decodeObxLiveId(v: Long): Int? = if (v >= 1_000_000_000_000L && v < 2_000_000_000_000L) (v - 1_000_000_000_000L).toInt() else null
        val obxSid = decodeObxLiveId(id)
        val ch: String? = null
        val sid = obxSid
        val box = com.chris.m3usuite.data.obx.ObxStore.get(ctx).boxFor(com.chris.m3usuite.data.obx.ObxEpgNowNext::class.java)
        val query = when {
            !ch.isNullOrEmpty() -> box.query(com.chris.m3usuite.data.obx.ObxEpgNowNext_.channelId.equal(ch)).build()
            sid != null -> box.query(com.chris.m3usuite.data.obx.ObxEpgNowNext_.streamId.equal(sid.toLong())).build()
            else -> null
        }
        if (query == null) return@DisposableEffect onDispose { }
        // initial state
        query.findFirst()?.let { row ->
            epgNow = row.nowTitle.orEmpty()
            epgNext = row.nextTitle.orEmpty()
            nowStartMs = row.nowStartMs
            nowEndMs = row.nowEndMs
        }
        val sub = query.subscribe().on(AndroidScheduler.mainThread()).observer { results ->
            val row = results.firstOrNull()
            if (row != null) {
                epgNow = row.nowTitle.orEmpty()
                epgNext = row.nextTitle.orEmpty()
                nowStartMs = row.nowStartMs
                nowEndMs = row.nowEndMs
            }
        }
        onDispose { sub.cancel() }
    }

    // Header für den VIDEO-Request (nicht für Bilder)
    suspend fun buildStreamHeaders(): Map<String, String> =
        com.chris.m3usuite.core.http.RequestHeadersProvider.defaultHeaders(store)

    // Playerwahl + Start
    suspend fun chooseAndPlay() {
        val playUrl = url ?: return
        // Gate: deny if profile not allowed
        val allowed = withContext(Dispatchers.IO) {
            val prof = com.chris.m3usuite.data.obx.ObxStore.get(ctx).boxFor(com.chris.m3usuite.data.obx.ObxProfile::class.java).get(store.currentProfileId.first())
            if (prof?.type == "adult") true else com.chris.m3usuite.data.repo.MediaQueryRepository(ctx, store).isAllowed("live", id)
        }
        if (!allowed) {
            android.widget.Toast.makeText(ctx, "Nicht freigegeben", android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        val hdrs = buildStreamHeaders()
        PlayerChooser.start(
            context = ctx,
            store = store,
            url = playUrl,
            headers = hdrs,
            startPositionMs = null, // Live hat kein Resume
            buildInternal = { startMs ->
                internalUrl = playUrl
                internalStartMs = startMs
                internalUa = hdrs["User-Agent"].orEmpty()
                internalRef = hdrs["Referer"].orEmpty()
                showInternal = true
            }
        )
    }

    // --- Wenn interner Player aktiv ist, zeigen wir ihn fullscreen und sonst nichts ---
    if (showInternal) {
        // Nutzt euren vorhandenen InternalPlayerScreen aus dem Projekt.
        val hdrs = buildMap<String, String> {
            if (internalUa.isNotBlank()) put("User-Agent", internalUa)
            if (internalRef.isNotBlank()) put("Referer", internalRef)
        }
        InternalPlayerScreen(
            url = internalUrl.orEmpty(),
            type = "live",
            startPositionMs = internalStartMs,
            headers = hdrs,
            onExit = {
                showInternal = false
            }
        )
        return
    }

    // Adult check
    val profileId by store.currentProfileId.collectAsStateWithLifecycle(initialValue = -1L)
    var isAdult by remember { mutableStateOf(true) }
    LaunchedEffect(profileId) {
        val p = withContext(Dispatchers.IO) { com.chris.m3usuite.data.obx.ObxStore.get(ctx).boxFor(com.chris.m3usuite.data.obx.ObxProfile::class.java).get(profileId) }
        isAdult = p?.type != "kid"
    }

    var showGrantSheet by rememberSaveable { mutableStateOf(false) }
    var showRevokeSheet by rememberSaveable { mutableStateOf(false) }

    // Local KidSelectSheet removed; using shared component via import

    // --- Normale Live-Detail-Ansicht ---
    val snackHost = remember { SnackbarHostState() }
    val listState = rememberLazyListState()
    HomeChromeScaffold(
        title = "Live",
        onSettings = null,
        onSearch = null,
        onProfiles = null,
        onRefresh = null,
        listState = listState,
        onLogo = onLogo,
        bottomBar = {}
    ) { pads ->
    Box(Modifier.fillMaxSize().padding(pads)) {
        val Accent = if (isAdult) com.chris.m3usuite.ui.theme.DesignTokens.Accent else com.chris.m3usuite.ui.theme.DesignTokens.KidAccent
        androidx.compose.foundation.layout.Box(Modifier.matchParentSize().background(Brush.verticalGradient(0f to MaterialTheme.colorScheme.background, 1f to MaterialTheme.colorScheme.surface)))
        androidx.compose.foundation.layout.Box(Modifier.matchParentSize().background(Brush.radialGradient(colors = listOf(Accent.copy(alpha = if (isAdult) 0.12f else 0.20f), Color.Transparent), radius = with(LocalDensity.current) { 640.dp.toPx() })))
        run {
            val rot = rememberInfiniteTransition(label = "fishRot").animateFloat(
                initialValue = 0f,
                targetValue = 360f,
                animationSpec = infiniteRepeatable(animation = tween(5000, easing = LinearEasing)),
                label = "deg"
            )
            com.chris.m3usuite.ui.fx.FishBackground(
                modifier = Modifier.align(Alignment.Center).size(520.dp),
                alpha = 0.05f
            )
        }
    com.chris.m3usuite.ui.common.AccentCard(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        accent = Accent
    ) {
        Column(Modifier.animateContentSize()) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = Accent.copy(alpha = 0.35f))

        // LOGO: 1. Klick -> EPG-Overlay anzeigen, 2. Klick (wenn offen) -> Playerwahl/Abspielen
        com.chris.m3usuite.ui.util.AppAsyncImage(
            url = logo,
            contentDescription = null,
            contentScale = ContentScale.Fit, // Logos nicht beschneiden
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .size(140.dp)
                .clip(CircleShape)
                .border(2.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f), CircleShape)
                .semantics { role = Role.Button }
                .clickable(enabled = url != null) {
                    if (showEpg) {
                        showEpg = false
                        scope.launch { chooseAndPlay() }
                    } else {
                        showEpg = true
                    }
                }
        )

        Spacer(Modifier.height(6.dp))
        // Sendername unter dem runden Sender-Icon
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(horizontal = 12.dp)
        )

        Spacer(Modifier.height(8.dp))
        if (epgNow.isNotBlank() || epgNext.isNotBlank()) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.70f))
                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), CircleShape)
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    if (epgNow.isNotBlank()) {
                        val meta = if (nowStartMs != null && nowEndMs != null && nowEndMs!! > nowStartMs!!) {
                            val fmt = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
                            val range = fmt.format(java.util.Date(nowStartMs!!)) + "–" + fmt.format(java.util.Date(nowEndMs!!))
                            val rem = ((nowEndMs!! - System.currentTimeMillis()).coerceAtLeast(0L) / 60000L).toInt()
                            " $range • noch ${rem}m"
                        } else ""
                        Text(
                            text = "Jetzt: $epgNow$meta",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = Color.White,
                            maxLines = 1,
                        )
                    }
                    if (epgNext.isNotBlank()) {
                        Text(
                            text = "Danach: $epgNext",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))

        // Direkter Play-Button -> Playerwahl (intern/extern/fragen)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                modifier = Modifier.focusScaleOnTv(),
                onClick = { scope.launch { if (hapticsEnabled) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove); chooseAndPlay() } },
                enabled = url != null,
                colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Color.Black)
            ) {
                Text("Abspielen")
            }
            if (isAdult) {
                com.chris.m3usuite.ui.common.AppIconButton(icon = com.chris.m3usuite.ui.common.AppIcon.AddKid, variant = com.chris.m3usuite.ui.common.IconVariant.Solid, contentDescription = "Für Kinder freigeben", onClick = { showGrantSheet = true })
                com.chris.m3usuite.ui.common.AppIconButton(icon = com.chris.m3usuite.ui.common.AppIcon.RemoveKid, variant = com.chris.m3usuite.ui.common.IconVariant.Solid, contentDescription = "Aus Kinderprofil entfernen", onClick = { showRevokeSheet = true })
            }
        }
        }
    }

    // --- EPG Overlay (Dialog) ---
    if (showEpg) {
        AlertDialog(
            onDismissRequest = { showEpg = false },
            title = { Text("EPG") },
            text = {
                Column {
                    if (epgNow.isNotBlank()) Text("Jetzt:  $epgNow")
                    if (epgNext.isNotBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Text("Danach: $epgNext")
                    }
                    if (epgNow.isBlank() && epgNext.isBlank()) {
                        Text("Keine EPG-Daten verfügbar.")
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Tipp: Nochmal auf das Sender-Logo tippen startet den Stream.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            },
            confirmButton = {
                TextButton(
                    modifier = Modifier.focusScaleOnTv(),
                    onClick = {
                        showEpg = false
                        scope.launch { chooseAndPlay() }
                    },
                    enabled = url != null,
                    colors = ButtonDefaults.textButtonColors(contentColor = Accent)
                ) { Text("Jetzt abspielen") }
            },
            dismissButton = {
                TextButton(modifier = Modifier.focusScaleOnTv(), onClick = { showEpg = false }, colors = ButtonDefaults.textButtonColors(contentColor = Accent)) { Text("Schließen") }
            }
        )
    }

    if (showGrantSheet) KidSelectSheet(onConfirm = { kidIds ->
        scope.launch(Dispatchers.IO) { kidIds.forEach { kidRepo.allowBulk(it, "live", listOf(id)) } }
        scope.launch { snackHost.showSnackbar("Sender freigegeben für ${kidIds.size} Kinder") }
        showGrantSheet = false
    }, onDismiss = { showGrantSheet = false })
    if (showRevokeSheet) KidSelectSheet(onConfirm = { kidIds ->
        scope.launch(Dispatchers.IO) { kidIds.forEach { kidRepo.disallowBulk(it, "live", listOf(id)) } }
        scope.launch { snackHost.showSnackbar("Sender aus ${kidIds.size} Kinderprofil(en) entfernt") }
        showRevokeSheet = false
    }, onDismiss = { showRevokeSheet = false })
}}
}
