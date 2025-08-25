package com.chris.m3usuite.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
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

@Composable
fun LiveDetailScreen(id: Long) {
    val ctx = LocalContext.current
    val db: AppDatabase = remember { DbProvider.get(ctx) }
    val store = remember { SettingsStore(ctx) }
    val headersImg = rememberImageHeaders()
    val scope = rememberCoroutineScope()

    var title by remember { mutableStateOf("") }
    var logo by remember { mutableStateOf<String?>(null) }
    var url by remember { mutableStateOf<String?>(null) }

    // EPG-Infos
    var epgNow by remember { mutableStateOf("") }
    var epgNext by remember { mutableStateOf("") }
    var showEpg by rememberSaveable { mutableStateOf(false) } // Overlay behält Zustand bei Rotation

    // --- Interner Player Zustand (Fullscreen) ---
    var showInternal by rememberSaveable { mutableStateOf(false) }
    var internalUrl by remember { mutableStateOf<String?>(null) }
    var internalStartMs by remember { mutableStateOf<Long?>(null) }
    var internalHeaders by remember { mutableStateOf<Map<String, String>>(emptyMap()) }

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
                internalHeaders = hdrs
                showInternal = true
            }
        )
    }

    // --- Wenn interner Player aktiv ist, zeigen wir ihn fullscreen und sonst nichts ---
    if (showInternal) {
        // Nutzt euren vorhandenen InternalPlayerScreen aus dem Projekt.
        InternalPlayerScreen(
            url = internalUrl.orEmpty(),
            type = "live",
            startPositionMs = internalStartMs,
            headers = internalHeaders,
            onExit = {
                showInternal = false
            }
        )
        return
    }

    // --- Normale Live-Detail-Ansicht ---
    Column(Modifier.padding(16.dp)) {
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
        Button(onClick = { scope.launch { chooseAndPlay() } }, enabled = url != null) {
            Text("Abspielen")
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
                    onClick = {
                        showEpg = false
                        scope.launch { chooseAndPlay() }
                    },
                    enabled = url != null
                ) { Text("Jetzt abspielen") }
            },
            dismissButton = {
                TextButton(onClick = { showEpg = false }) { Text("Schließen") }
            }
        )
    }
}
