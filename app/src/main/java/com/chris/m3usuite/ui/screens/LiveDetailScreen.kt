package com.chris.m3usuite.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import java.io.File
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.material3.ExperimentalMaterial3Api
import coil3.compose.AsyncImage
import com.chris.m3usuite.core.xtream.XtreamClient
import com.chris.m3usuite.core.xtream.XtreamConfig
import com.chris.m3usuite.data.db.AppDatabase
import com.chris.m3usuite.data.db.DbProvider
import com.chris.m3usuite.prefs.SettingsStore
import com.chris.m3usuite.player.InternalPlayerScreen
import com.chris.m3usuite.player.PlayerChooser
import com.chris.m3usuite.ui.util.buildImageRequest
import com.chris.m3usuite.ui.util.rememberImageHeaders
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.chris.m3usuite.data.db.Profile
import com.chris.m3usuite.data.repo.KidContentRepository
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import coil3.request.ImageRequest
import com.chris.m3usuite.ui.util.rememberAvatarModel
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.CircleShape
import com.chris.m3usuite.ui.skin.tvClickable
import com.chris.m3usuite.ui.skin.focusScaleOnTv
import com.chris.m3usuite.ui.home.HomeChromeScaffold
import androidx.compose.foundation.lazy.rememberLazyListState

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

        if (item.streamId != null && host.isNotBlank() && user.isNotBlank() && pass.isNotBlank()) {
            runCatching {
                val cfg = XtreamConfig(host, port, user, pass, out)
                val client = XtreamClient(ctx, store, cfg)
                client.shortEPG(item.streamId, 2)
            }.onSuccess { epg ->
                epgNow = epg.getOrNull(0)?.title.orEmpty()
                epgNext = epg.getOrNull(1)?.title.orEmpty()
            }.onFailure {
                epgNow = ""; epgNext = ""
            }
        } else {
            epgNow = ""; epgNext = ""
        }
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
                        TextButton(modifier = Modifier.weight(1f).focusScaleOnTv(), onClick = onDismiss) { Text("Abbrechen") }
                        Button(modifier = Modifier.weight(1f).focusScaleOnTv(), onClick = { scope.launch { onConfirm(checked.toList()); onDismiss() } }, enabled = checked.isNotEmpty()) { Text("OK") }
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
    Column(Modifier.padding(16.dp).padding(pads)) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))

        // LOGO: 1. Klick -> EPG-Overlay anzeigen, 2. Klick (wenn offen) -> Playerwahl/Abspielen
        AsyncImage(
            model = buildImageRequest(ctx, logo, headersImg),
            contentDescription = null,
            contentScale = ContentScale.Fit, // Logos nicht beschneiden
            modifier = Modifier
                .size(120.dp)
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
            Button(modifier = Modifier.focusScaleOnTv(), onClick = { scope.launch { if (hapticsEnabled) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove); chooseAndPlay() } }, enabled = url != null) {
                Text("Abspielen")
            }
            if (isAdult) {
                com.chris.m3usuite.ui.common.AppIconButton(icon = com.chris.m3usuite.ui.common.AppIcon.AddKid, variant = com.chris.m3usuite.ui.common.IconVariant.Solid, contentDescription = "Für Kinder freigeben", onClick = { showGrantSheet = true })
                com.chris.m3usuite.ui.common.AppIconButton(icon = com.chris.m3usuite.ui.common.AppIcon.RemoveKid, variant = com.chris.m3usuite.ui.common.IconVariant.Solid, contentDescription = "Aus Kinderprofil entfernen", onClick = { showRevokeSheet = true })
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
                    enabled = url != null
                ) { Text("Jetzt abspielen") }
            },
            dismissButton = {
                TextButton(modifier = Modifier.focusScaleOnTv(), onClick = { showEpg = false }) { Text("Schließen") }
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
