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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.graphics.asComposeRenderEffect
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
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.chris.m3usuite.data.db.AppDatabase
import com.chris.m3usuite.data.db.DbProvider
import com.chris.m3usuite.data.db.Profile
import com.chris.m3usuite.data.repo.EpgRepository
import com.chris.m3usuite.data.repo.KidContentRepository
import com.chris.m3usuite.player.InternalPlayerScreen
import com.chris.m3usuite.player.PlayerChooser
import com.chris.m3usuite.prefs.SettingsStore
import com.chris.m3usuite.ui.home.HomeChromeScaffold
import com.chris.m3usuite.ui.skin.focusScaleOnTv
import com.chris.m3usuite.ui.skin.tvClickable
import com.chris.m3usuite.ui.util.buildImageRequest
import com.chris.m3usuite.ui.util.rememberAvatarModel
import com.chris.m3usuite.ui.util.rememberImageHeaders
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveDetailScreen(id: Long) {
    val ctx = LocalContext.current
    val db: AppDatabase = remember { DbProvider.get(ctx) }
    val store = remember { SettingsStore(ctx) }
    val headersImg = rememberImageHeaders()
    val scope = rememberCoroutineScope()
    val kidRepo = remember { KidContentRepository(ctx) }
    val haptics = LocalHapticFeedback.current
    val hapticsEnabled by store.hapticsEnabled.collectAsState(initial = false)

    var title by remember { mutableStateOf("") }
    var logo by remember { mutableStateOf<String?>(null) }
    var url by remember { mutableStateOf<String?>(null) }

    // EPG-Infos
    var epgNow by remember { mutableStateOf("") }
    var epgNext by remember { mutableStateOf("") }
    var showEpg by rememberSaveable { mutableStateOf(false) } // Overlay behält Zustand bei Rotation

    // --- Interner Player Zustand (Fullscreen) ---
    var showInternal by rememberSaveable { mutableStateOf(false) }
    var internalUrl by rememberSaveable { mutableStateOf<String?>(null) }
    var internalStartMs by rememberSaveable { mutableStateOf<Long?>(null) }
    // Persist UA/Referer to reconstruct headers on rotation
    var internalUa by rememberSaveable { mutableStateOf("") }
    var internalRef by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(id) {
        val item = db.mediaDao().byId(id) ?: return@LaunchedEffect
        title = item.name
        logo = item.logo ?: item.poster
        url = item.url

        val host = store.xtHost.first()
        val port = store.xtPort.first()
        val user = store.xtUser.first()
        val pass = store.xtPass.first()
        val out  = store.xtOutput.first()

        if (item.streamId != null) {
            val list = runCatching { EpgRepository(ctx, store).nowNext(item.streamId!!, 2) }.getOrDefault(emptyList())
            epgNow = list.getOrNull(0)?.title.orEmpty()
            epgNext = list.getOrNull(1)?.title.orEmpty()
        } else { epgNow = ""; epgNext = "" }
    }

    // Header für den VIDEO-Request (nicht für Bilder)
    suspend fun buildStreamHeaders(): Map<String, String> {
        val ua = store.userAgent.first()
        val ref = store.referer.first()
        val map = mutableMapOf<String, String>()
        if (ua.isNotBlank()) map["User-Agent"] = ua
        if (ref.isNotBlank()) map["Referer"] = ref
        // Falls du extra JSON-Header im Store hältst, könntest du sie hier zusätzlich mappen.
        return map
    }

    // Playerwahl + Start
    suspend fun chooseAndPlay() {
        val playUrl = url ?: return
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
    val profileId by store.currentProfileId.collectAsState(initial = -1L)
    var isAdult by remember { mutableStateOf(true) }
    LaunchedEffect(profileId) {
        val p = withContext(Dispatchers.IO) { db.profileDao().byId(profileId) }
        isAdult = p?.type != "kid"
    }

    var showGrantSheet by rememberSaveable { mutableStateOf(false) }
    var showRevokeSheet by rememberSaveable { mutableStateOf(false) }

    @Composable
    fun KidSelectSheet(onConfirm: suspend (kidIds: List<Long>) -> Unit, onDismiss: () -> Unit) {
        var kids by remember { mutableStateOf<List<Profile>>(emptyList()) }
        LaunchedEffect(profileId) {
            kids = withContext(Dispatchers.IO) { db.profileDao().all().filter { it.type == "kid" } }
        }
        var checked by remember { mutableStateOf(setOf<Long>()) }
        ModalBottomSheet(onDismissRequest = onDismiss) {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().heightIn(max = 520.dp),
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
        bottomBar = {}
    ) { pads ->
    Box(Modifier.fillMaxSize().padding(pads)) {
        val Accent = if (isAdult) com.chris.m3usuite.ui.theme.DesignTokens.Accent else com.chris.m3usuite.ui.theme.DesignTokens.KidAccent
        androidx.compose.foundation.layout.Box(Modifier.matchParentSize().background(Brush.verticalGradient(0f to MaterialTheme.colorScheme.background, 1f to MaterialTheme.colorScheme.surface)))
        androidx.compose.foundation.layout.Box(Modifier.matchParentSize().background(Brush.radialGradient(colors = listOf(Accent.copy(alpha = if (isAdult) 0.12f else 0.20f), Color.Transparent), radius = with(LocalDensity.current) { 640.dp.toPx() })))
        androidx.compose.foundation.Image(painter = painterResource(id = com.chris.m3usuite.R.drawable.fisch), contentDescription = null, modifier = Modifier.align(Alignment.Center).size(520.dp).graphicsLayer { alpha = 0.05f; try { if (Build.VERSION.SDK_INT >= 31) renderEffect = android.graphics.RenderEffect.createBlurEffect(34f, 34f, android.graphics.Shader.TileMode.CLAMP).asComposeRenderEffect() } catch (_: Throwable) {} })
    com.chris.m3usuite.ui.common.AccentCard(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        accent = Accent
    ) {
        Column(Modifier.animateContentSize()) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = Accent.copy(alpha = 0.35f))

        // LOGO: 1. Klick -> EPG-Overlay anzeigen, 2. Klick (wenn offen) -> Playerwahl/Abspielen
        AsyncImage(
            model = buildImageRequest(ctx, logo, headersImg),
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

        Spacer(Modifier.height(8.dp))
        if (epgNow.isNotBlank()) Text("Jetzt: $epgNow")
        if (epgNext.isNotBlank()) Text("Danach: $epgNext")
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
