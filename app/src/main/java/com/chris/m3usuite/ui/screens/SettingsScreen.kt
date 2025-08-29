package com.chris.m3usuite.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.input.PasswordVisualTransformation
import com.chris.m3usuite.prefs.Keys
import com.chris.m3usuite.prefs.SettingsStore
import kotlinx.coroutines.launch
import androidx.compose.runtime.saveable.rememberSaveable
import kotlinx.coroutines.flow.first
import com.chris.m3usuite.data.repo.XtreamRepository
import com.chris.m3usuite.data.repo.PlaylistRepository
import com.chris.m3usuite.core.xtream.XtreamClient
import com.chris.m3usuite.core.xtream.XtreamConfig
import com.chris.m3usuite.data.db.DbProvider
import android.util.Log
import com.chris.m3usuite.ui.home.HomeChromeScaffold
import androidx.compose.foundation.lazy.rememberLazyListState
import com.chris.m3usuite.backup.BackupRestoreSection

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    store: SettingsStore,
    onBack: () -> Unit,
    onOpenProfiles: (() -> Unit)? = null,
    onOpenGate: (() -> Unit)? = null
) {
    val scope = rememberCoroutineScope()
    val mode by store.playerMode.collectAsState(initial = "ask")
    val pkg by store.preferredPlayerPkg.collectAsState(initial = "")
    val subScale by store.subtitleScale.collectAsState(initial = 0.06f)
    val subFg by store.subtitleFg.collectAsState(initial = 0xF2FFFFFF.toInt())
    val subBg by store.subtitleBg.collectAsState(initial = 0x66000000)
    val subFgOpacity by store.subtitleFgOpacityPct.collectAsState(initial = 90)
    val subBgOpacity by store.subtitleBgOpacityPct.collectAsState(initial = 40)
    val headerCollapsed by store.headerCollapsedDefaultInLandscape.collectAsState(initial = true)
    val rotationLocked by store.rotationLocked.collectAsState(initial = false)
    val autoplayNext by store.autoplayNext.collectAsState(initial = false)
    val hapticsEnabled by store.hapticsEnabled.collectAsState(initial = false)
    val rememberLast by store.rememberLastProfile.collectAsState(initial = false)
    val pinSet by store.adultPinSet.collectAsState(initial = false)
    val m3u by store.m3uUrl.collectAsState(initial = "")
    val epg by store.epgUrl.collectAsState(initial = "")
    val xtHost by store.xtHost.collectAsState(initial = "")
    val xtPort by store.xtPort.collectAsState(initial = 80)
    val xtUser by store.xtUser.collectAsState(initial = "")
    val xtPass by store.xtPass.collectAsState(initial = "")
    val xtOut  by store.xtOutput.collectAsState(initial = "m3u8")
    val ua by store.userAgent.collectAsState(initial = "IBOPlayer/1.4 (Android)")
    val referer by store.referer.collectAsState(initial = "")
    var pinDialogMode by remember { mutableStateOf<PinMode?>(null) }

    val listState = rememberLazyListState()
    val snackHost = remember { SnackbarHostState() }
    HomeChromeScaffold(
        title = "Einstellungen",
        onSettings = null,
        onSearch = null,
        onProfiles = null,
        onRefresh = null,
        listState = listState,
        snackbarHost = snackHost,
        bottomBar = {}
    ) { pads ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(pads)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            TextButton(onClick = onBack) { Text("Zurück") }
            if (onOpenProfiles != null) {
                TextButton(onClick = onOpenProfiles) { Text("Profile verwalten…") }
            }
            TextButton(onClick = {
                scope.launch {
                    store.setCurrentProfileId(-1)
                    onOpenGate?.invoke()
                }
            }) { Text("Zur Profilwahl…") }

            HorizontalDivider()

            Text("Profil/Gate", style = MaterialTheme.typography.titleMedium)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Letztes Profil merken", modifier = Modifier.weight(1f))
                Switch(checked = rememberLast, onCheckedChange = { v ->
                    scope.launch { store.setRememberLastProfile(v) }
                })
            }
            Text("Player", style = MaterialTheme.typography.titleMedium)
            Column {
                Radio("Immer fragen", mode == "ask") { scope.launch { store.setPlayerMode("ask") } }
                Radio("Interner Player", mode == "internal") { scope.launch { store.setPlayerMode("internal") } }
                Radio("Externer Player", mode == "external") { scope.launch { store.setPlayerMode("external") } }
            }

            OutlinedTextField(
                value = pkg,
                onValueChange = { scope.launch { store.set(Keys.PREF_PLAYER_PACKAGE, it) } },
                label = { Text("Bevorzugtes externes Paket (optional)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            HorizontalDivider()

            Text("Untertitel (interner Player)", style = MaterialTheme.typography.titleMedium)
            Text("Größe")
            Slider(value = subScale, onValueChange = { v -> scope.launch { store.setFloat(Keys.SUB_SCALE, v) } },
                valueRange = 0.04f..0.12f, steps = 8)

            Text("Textfarbe")
            ColorRow(
                selected = subFg,
                onPick = { c -> scope.launch { store.setInt(Keys.SUB_FG, c) } },
                palette = textPalette()
            )

            Text("Hintergrund")
            ColorRow(
                selected = subBg,
                onPick = { c -> scope.launch { store.setInt(Keys.SUB_BG, c) } },
                palette = bgPalette()
            )

            // Opacity
            Text("Text-Deckkraft: ${subFgOpacity}%")
            Slider(
                value = subFgOpacity.toFloat(),
                onValueChange = { v -> scope.launch { store.setSubtitleFgOpacityPct(v.toInt().coerceIn(0, 100)) } },
                valueRange = 0f..100f,
                steps = 10
            )
            Text("Hintergrund-Deckkraft: ${subBgOpacity}%")
            Slider(
                value = subBgOpacity.toFloat(),
                onValueChange = { v -> scope.launch { store.setSubtitleBgOpacityPct(v.toInt().coerceIn(0, 100)) } },
                valueRange = 0f..100f,
                steps = 10
            )

            // Vorschau – näher am Player: Overlay-Style mit Untertitel im Zentrum
            OutlinedCard {
                Box(Modifier.fillMaxWidth().height(180.dp)) {
                    // Simuliertes Videobild (einfacher Verlauf)
                    Box(
                        Modifier
                            .matchParentSize()
                            .background(
                                Brush.verticalGradient(
                                    0f to MaterialTheme.colorScheme.surfaceVariant,
                                    1f to MaterialTheme.colorScheme.surface
                                )
                            )
                    )
                    // Untertitel-Overlay
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        val fg = Color(subFg)
                        val bg = Color(subBg)
                        val textSize = (subScale * 200f).coerceIn(10f, 28f).sp
                        Text(
                            "Untertitel-Vorschau",
                            color = fg.copy(alpha = (subFgOpacity / 100f)),
                            fontWeight = FontWeight.SemiBold,
                            fontSize = textSize,
                            modifier = Modifier
                                .clip(MaterialTheme.shapes.small)
                                .background(bg.copy(alpha = (subBgOpacity / 100f)))
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            HorizontalDivider()

            // App PIN
            Text("App-PIN", style = MaterialTheme.typography.titleMedium)
            if (!pinSet) {
                Text("Es ist kein PIN gesetzt.")
                Button(onClick = { pinDialogMode = PinMode.Set }) { Text("PIN festlegen…") }
            } else {
                Text("PIN ist gesetzt.")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { pinDialogMode = PinMode.Change }) { Text("PIN ändern…") }
                    TextButton(onClick = { pinDialogMode = PinMode.Clear }) { Text("PIN entfernen") }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Landscape: Header standardmäßig eingeklappt", modifier = Modifier.weight(1f))
                Switch(checked = headerCollapsed, onCheckedChange = { v ->
                    scope.launch { store.setBool(Keys.HEADER_COLLAPSED_LAND, v) }
                })
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Rotation in Player sperren (Landscape)", modifier = Modifier.weight(1f))
                Switch(checked = rotationLocked, onCheckedChange = { v ->
                    scope.launch { store.setRotationLocked(v) }
                })
            }

            HorizontalDivider()
            Text("Wiedergabe", style = MaterialTheme.typography.titleMedium)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Autoplay nächste Folge (Serie)", modifier = Modifier.weight(1f))
                Switch(checked = autoplayNext, onCheckedChange = { v -> scope.launch { store.setAutoplayNext(v) } })
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Haptisches Feedback", modifier = Modifier.weight(1f))
                Switch(checked = hapticsEnabled, onCheckedChange = { v -> scope.launch { store.setHapticsEnabled(v) } })
            }

            HorizontalDivider()
            Text("Externer Player", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = pkg,
                onValueChange = { scope.launch { store.set(Keys.PREF_PLAYER_PACKAGE, it) } },
                label = { Text("Bevorzugtes externes Paket (optional)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            ExternalPlayerPickerButton(onPick = { packageName ->
                scope.launch { store.set(Keys.PREF_PLAYER_PACKAGE, packageName) }
            })

            HorizontalDivider()
            Text("Quelle (M3U/Xtream/EPG)", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = m3u,
                onValueChange = { scope.launch { store.set(Keys.M3U_URL, it) } },
                label = { Text("M3U / Xtream get.php Link") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = epg,
                onValueChange = { scope.launch { store.set(Keys.EPG_URL, it) } },
                label = { Text("EPG XMLTV URL (optional)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = ua,
                onValueChange = { scope.launch { store.set(Keys.USER_AGENT, it) } },
                label = { Text("User-Agent") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = referer,
                onValueChange = { scope.launch { store.set(Keys.REFERER, it) } },
                label = { Text("Referer (optional)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            // Xtream (optional) — can be auto-filled from M3U get.php
            Text("Xtream (optional)", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = xtHost,
                    onValueChange = { scope.launch { store.set(Keys.XT_HOST, it) } },
                    label = { Text("Host") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = xtPort.toString(),
                    onValueChange = { s -> s.toIntOrNull()?.let { p -> scope.launch { store.setInt(Keys.XT_PORT, p) } } },
                    label = { Text("Port") },
                    singleLine = true,
                    modifier = Modifier.width(120.dp)
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = xtUser,
                    onValueChange = { scope.launch { store.set(Keys.XT_USER, it) } },
                    label = { Text("Benutzername") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = xtPass,
                    onValueChange = { scope.launch { store.set(Keys.XT_PASS, it) } },
                    label = { Text("Passwort") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.weight(1f)
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = xtOut,
                    onValueChange = { scope.launch { store.set(Keys.XT_OUTPUT, it) } },
                    label = { Text("Output (ts|m3u8|mp4)") },
                    singleLine = true,
                    modifier = Modifier.width(200.dp)
                )
                val ctx = LocalContext.current
                TextButton(onClick = {
                    scope.launch {
                        val xtRepo = XtreamRepository(ctx, store)
                        xtRepo.configureFromM3uUrl()
                    }
                }) { Text("Aus M3U ableiten") }
                TextButton(onClick = {
                    scope.launch {
                        val tag = "XtreamEPGTest"
                        try {
                            val hostNow = store.xtHost.first(); val userNow = store.xtUser.first(); val passNow = store.xtPass.first(); val outNow = store.xtOutput.first(); val portNow = store.xtPort.first()
                            Log.d(tag, "Testing shortEPG with host=${hostNow}:${portNow}, user=${userNow}, output=${outNow}")
                            if (hostNow.isBlank() || userNow.isBlank() || passNow.isBlank()) {
                                Log.w(tag, "Xtream config missing; cannot test shortEPG")
                                snackHost.showSnackbar("EPG-Test: Xtream-Konfig fehlt")
                                return@launch
                            }
                            val db = DbProvider.get(ctx)
                            val sid = withContext(Dispatchers.IO) { db.mediaDao().listByType("live", 1000, 0).firstOrNull { it.streamId != null }?.streamId }
                            if (sid == null) {
                                Log.w(tag, "No live streamId found in DB; import might be required")
                                snackHost.showSnackbar("EPG-Test: keine Live-StreamId gefunden")
                                return@launch
                            }
                            val cfg = XtreamConfig(hostNow, portNow, userNow, passNow, outNow)
                            Log.d(tag, "Portal base: ${cfg.portalBase}")
                            val client = XtreamClient(ctx, store, cfg)
                            val list = client.shortEPG(sid, 2)
                            Log.d(tag, "shortEPG result count=${list.size}; entries=${list.map { it.title }}")
                            snackHost.showSnackbar("EPG-Test: ${list.getOrNull(0)?.title ?: "(leer)"}")
                        } catch (t: Throwable) {
                            Log.e(tag, "EPG test failed", t)
                            snackHost.showSnackbar("EPG-Test fehlgeschlagen: ${t.message}")
                        }
                    }
                }) { Text("Test EPG (Debug)") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val ctx = LocalContext.current
                Button(onClick = {
                    // Run immediate import: prefer Xtream if we can configure from M3U, else M3U parser
                    scope.launch {
                        val xtRepo = XtreamRepository(ctx, store)
                        val plRepo = PlaylistRepository(ctx, store)
                        val cfg = runCatching { xtRepo.configureFromM3uUrl() }.getOrNull()
                        val result = if (cfg != null) xtRepo.importAll() else plRepo.refreshFromM3U()
                        com.chris.m3usuite.work.XtreamRefreshWorker.schedule(ctx)
                        com.chris.m3usuite.work.XtreamEnrichmentWorker.schedule(ctx)
                        val count = result.getOrNull()
                        if (count != null) {
                            snackHost.showSnackbar("Import abgeschlossen: $count Einträge")
                        } else {
                            snackHost.showSnackbar("Import fehlgeschlagen – wird erneut versucht")
                        }
                    }
                }) { Text("Import aktualisieren") }
                if (onOpenProfiles != null) {
                    TextButton(onClick = onOpenProfiles) { Text("Profile verwalten…") }
                }
            }

            // Quick import (Drive/File) visible in settings too
            com.chris.m3usuite.backup.QuickImportRow()

            Divider()
            com.chris.m3usuite.backup.BackupRestoreSection()
        }
    }

    // PIN Dialog host
    pinDialogMode?.let { activePinMode ->
        showPinDialog(store = store, mode = activePinMode) { pinDialogMode = null }
    }
}

// --- External Player Picker UI ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExternalPlayerPickerButton(onPick: (String) -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var show by remember { mutableStateOf(false) }
    TextButton(onClick = { show = true }) { Text("Externen Player auswählen…") }
    if (!show) return
    val pm = ctx.packageManager
    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply { setDataAndType(android.net.Uri.parse("http://example.com/video.m3u8"), "application/vnd.apple.mpegurl") }
    val intent2 = android.content.Intent(android.content.Intent.ACTION_VIEW).apply { setDataAndType(android.net.Uri.parse("http://example.com/video.mp4"), "video/*") }
    val list = remember {
        val a = pm.queryIntentActivities(intent, 0)
        val b = pm.queryIntentActivities(intent2, 0)
        (a + b).distinctBy { it.activityInfo.packageName }
    }
    androidx.compose.material3.ModalBottomSheet(onDismissRequest = { show = false }) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Installierte Player")
            list.forEach { ri ->
                val label = ri.loadLabel(pm)?.toString() ?: ri.activityInfo.packageName
                Button(onClick = { onPick(ri.activityInfo.packageName); show = false }) { Text(label) }
            }
        }
    }
}

// --- PIN dialogs ---
private enum class PinMode { Set, Change, Clear }

@Composable
private fun showPinDialog(store: SettingsStore, mode: PinMode, onDismissed: () -> Unit) {
    var open by rememberSaveable { mutableStateOf(true) }
    var pin by remember { mutableStateOf("") }
    var pin2 by remember { mutableStateOf("") }
    var old by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    if (!open) { onDismissed(); return }
    AlertDialog(
        onDismissRequest = { open = false },
        title = { Text(when (mode) { PinMode.Set -> "PIN festlegen"; PinMode.Change -> "PIN ändern"; PinMode.Clear -> "PIN entfernen" }) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (mode != PinMode.Set) {
                    OutlinedTextField(value = old, onValueChange = { old = it }, label = { Text("Aktuelle PIN") }, singleLine = true, visualTransformation = PasswordVisualTransformation())
                }
                if (mode != PinMode.Clear) {
                    OutlinedTextField(value = pin, onValueChange = { pin = it }, label = { Text("Neue PIN") }, singleLine = true, visualTransformation = PasswordVisualTransformation())
                    OutlinedTextField(value = pin2, onValueChange = { pin2 = it }, label = { Text("Wiederholen") }, singleLine = true, visualTransformation = PasswordVisualTransformation())
                }
                if (error != null) Text(error!!, color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                scope.launch {
                    when (mode) {
                        PinMode.Set -> {
                            if (pin.isBlank() || pin != pin2) { error = "PINs stimmen nicht"; return@launch }
                            val h = sha256(pin)
                            store.setAdultPinHash(h); store.setAdultPinSet(true)
                            open = false
                        }
                        PinMode.Change -> {
                            val current = store.adultPinHash.first()
                            val ok = sha256(old) == current
                            if (!ok) { error = "Falsche aktuelle PIN"; return@launch }
                            if (pin.isBlank() || pin != pin2) { error = "PINs stimmen nicht"; return@launch }
                            val h = sha256(pin)
                            store.setAdultPinHash(h); store.setAdultPinSet(true)
                            open = false
                        }
                        PinMode.Clear -> {
                            val current = store.adultPinHash.first()
                            val ok = sha256(old) == current
                            if (!ok) { error = "Falsche aktuelle PIN"; return@launch }
                            store.setAdultPinHash(""); store.setAdultPinSet(false)
                            open = false
                        }
                    }
                }
            }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = { open = false }) { Text("Abbrechen") } }
    )
}

private fun sha256(s: String): String {
    val md = java.security.MessageDigest.getInstance("SHA-256")
    val dig = md.digest(s.toByteArray())
    return dig.joinToString("") { b -> "%02x".format(b) }
}

@Composable
private fun Radio(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Spacer(Modifier.width(8.dp))
        Text(label)
    }
}

@Composable
private fun ColorRow(selected: Int, onPick: (Int) -> Unit, palette: List<Int>) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        palette.forEach { c ->
            val border = if (selected == c) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null
            if (border != null) {
                OutlinedCard(border = border, modifier = Modifier.size(36.dp)) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color(c))
                            .selectable(selected = selected == c, role = Role.Button, onClick = { onPick(c) })
                    )
                }
            } else {
                OutlinedCard(modifier = Modifier.size(36.dp)) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color(c))
                            .selectable(selected = selected == c, role = Role.Button, onClick = { onPick(c) })
                    )
                }
            }
        }
    }
}

private fun textPalette(): List<Int> = listOf(
    Color.White.copy(alpha = 0.95f).toArgb(),
    Color.Yellow.copy(alpha = 0.95f).toArgb(),
    Color.Cyan.copy(alpha = 0.95f).toArgb(),
    Color.Black.copy(alpha = 0.95f).toArgb(),
    Color.Red.copy(alpha = 0.95f).toArgb(),
    Color.Green.copy(alpha = 0.95f).toArgb(),
    Color.Blue.copy(alpha = 0.95f).toArgb(),
    Color.Magenta.copy(alpha = 0.95f).toArgb(),
)

private fun bgPalette(): List<Int> = listOf(
    Color.Black.copy(alpha = 0.50f).toArgb(),
    Color.White.copy(alpha = 0.50f).toArgb(),
    Color.Yellow.copy(alpha = 0.50f).toArgb(),
    Color.Cyan.copy(alpha = 0.50f).toArgb(),
    Color.Red.copy(alpha = 0.50f).toArgb(),
    Color.Green.copy(alpha = 0.50f).toArgb(),
    Color.Blue.copy(alpha = 0.50f).toArgb(),
    Color.Magenta.copy(alpha = 0.50f).toArgb(),
)
