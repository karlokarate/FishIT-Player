package com.chris.m3usuite.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.foundation.layout.ColumnScope
import com.chris.m3usuite.prefs.Keys
import com.chris.m3usuite.prefs.SettingsStore
import kotlinx.coroutines.launch
import androidx.compose.runtime.saveable.rememberSaveable
import kotlinx.coroutines.flow.first
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chris.m3usuite.BuildConfig
import com.chris.m3usuite.data.repo.PlaylistRepository
import com.chris.m3usuite.core.xtream.XtreamClient
import com.chris.m3usuite.core.xtream.XtreamConfig
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.chris.m3usuite.ui.home.HomeChromeScaffold
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import com.chris.m3usuite.telegram.TdLibReflection
import com.chris.m3usuite.core.http.HttpClientFactory
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import android.graphics.Bitmap
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    store: SettingsStore,
    onBack: () -> Unit,
    onOpenProfiles: (() -> Unit)? = null,
    onOpenGate: (() -> Unit)? = null,
    onOpenXtreamCfCheck: (() -> Unit)? = null
) {
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    val permRepo = remember(ctx) { com.chris.m3usuite.data.repo.PermissionRepository(ctx, store) }
    var canChangeSources by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) { canChangeSources = permRepo.current().canChangeSources }
    val mode by store.playerMode.collectAsStateWithLifecycle(initialValue = "internal")
    val pkg by store.preferredPlayerPkg.collectAsStateWithLifecycle(initialValue = "")
    val subScale by store.subtitleScale.collectAsStateWithLifecycle(initialValue = 0.06f)
    val subFg by store.subtitleFg.collectAsStateWithLifecycle(initialValue = 0xF2FFFFFF.toInt())
    val subBg by store.subtitleBg.collectAsStateWithLifecycle(initialValue = 0x66000000)
    val subFgOpacity by store.subtitleFgOpacityPct.collectAsStateWithLifecycle(initialValue = 90)
    val subBgOpacity by store.subtitleBgOpacityPct.collectAsStateWithLifecycle(initialValue = 40)
    val headerCollapsed by store.headerCollapsedDefaultInLandscape.collectAsStateWithLifecycle(initialValue = true)
    val rotationLocked by store.rotationLocked.collectAsStateWithLifecycle(initialValue = false)
    val autoplayNext by store.autoplayNext.collectAsStateWithLifecycle(initialValue = false)
    val hapticsEnabled by store.hapticsEnabled.collectAsStateWithLifecycle(initialValue = false)
    val rememberLast by store.rememberLastProfile.collectAsStateWithLifecycle(initialValue = false)
    val pinSet by store.adultPinSet.collectAsStateWithLifecycle(initialValue = false)
    val m3u by store.m3uUrl.collectAsStateWithLifecycle(initialValue = "")
    val epg by store.epgUrl.collectAsStateWithLifecycle(initialValue = "")
    val xtHost by store.xtHost.collectAsStateWithLifecycle(initialValue = "")
    val xtPort by store.xtPort.collectAsStateWithLifecycle(initialValue = 80)
    val xtUser by store.xtUser.collectAsStateWithLifecycle(initialValue = "")
    val xtPass by store.xtPass.collectAsStateWithLifecycle(initialValue = "")
    val xtOut  by store.xtOutput.collectAsStateWithLifecycle(initialValue = "m3u8")
    val ua by store.userAgent.collectAsStateWithLifecycle(initialValue = "IBOPlayer/1.4 (Android)")
    val referer by store.referer.collectAsStateWithLifecycle(initialValue = "")
    val httpLogEnabled by store.httpLogEnabled.collectAsStateWithLifecycle(initialValue = false)
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
        onLogo = onBack,
        snackbarHost = snackHost,
        bottomBar = {}
    ) { pads ->
        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
            val Accent = com.chris.m3usuite.ui.theme.DesignTokens.Accent
            val tfColors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                disabledTextColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                focusedLabelColor = MaterialTheme.colorScheme.onSurface,
                unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                disabledContainerColor = MaterialTheme.colorScheme.surface,
                cursorColor = Accent,
                focusedBorderColor = Accent,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)
            )
            // Background layers
            Box(Modifier.fillMaxSize().padding(pads)) {
                // Gradient base
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
                // Radial accent glow
                Box(
                    Modifier
                        .matchParentSize()
                        .background(
                            Brush.radialGradient(
                                colors = listOf(Accent.copy(alpha = 0.18f), Color.Transparent),
                                radius = with(LocalDensity.current) { 600.dp.toPx() }
                            )
                        )
                )
                // Center rotated app icon
                com.chris.m3usuite.ui.fx.FishBackground(
                    modifier = Modifier.align(Alignment.Center).size(520.dp),
                    alpha = 0.06f
                )
            }
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
                // Button nur zeigen, wenn der aktuelle Nutzer Whitelists bearbeiten darf
                val allowProfiles = remember { mutableStateOf(true) }
                LaunchedEffect(Unit) { allowProfiles.value = permRepo.current().canEditWhitelist }
                if (allowProfiles.value) {
                    TextButton(onClick = onOpenProfiles) { Text("Profile verwalten…") }
                }
            }
            TextButton(onClick = {
                scope.launch {
                    store.setCurrentProfileId(-1)
                    onOpenGate?.invoke()
                }
            }) { Text("Zur Profilwahl…") }

            HorizontalDivider()

            SectionHeader("Profil & Gate")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Letztes Profil merken", modifier = Modifier.weight(1f))
                Switch(checked = rememberLast, onCheckedChange = { v ->
                    scope.launch { store.setRememberLastProfile(v) }
                })
            }
            SectionHeader("Player")
            Column {
                Radio("Immer fragen", mode == "ask") { scope.launch { store.setPlayerMode("ask") } }
                Radio("Interner Player", mode == "internal") { scope.launch { store.setPlayerMode("internal") } }
                Radio("Externer Player", mode == "external") { scope.launch { store.setPlayerMode("external") } }
                Spacer(Modifier.height(8.dp))
                // Preferred external app: value + picker (einmalig hier)
                OutlinedTextField(
                    value = pkg,
                    onValueChange = { scope.launch { store.set(Keys.PREF_PLAYER_PACKAGE, it) } },
                    label = { Text("Bevorzugtes externes Paket (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = tfColors
                )
                ExternalPlayerPickerButton(onPick = { packageName ->
                    scope.launch { store.set(Keys.PREF_PLAYER_PACKAGE, packageName) }
                })
            }

            HorizontalDivider()

            SectionHeader("Untertitel (interner Player)")
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
            SectionHeader("App-PIN")
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
            SectionHeader("Wiedergabe")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Autoplay nächste Folge (Serie)", modifier = Modifier.weight(1f))
                Switch(checked = autoplayNext, onCheckedChange = { v -> scope.launch { store.setAutoplayNext(v) } })
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Haptisches Feedback", modifier = Modifier.weight(1f))
                Switch(checked = hapticsEnabled, onCheckedChange = { v -> scope.launch { store.setHapticsEnabled(v) } })
            }

            // (entfernt) Duplikat "Externer Player" – alles oben unter Player zusammengeführt

            HorizontalDivider()
            SectionHeader("Quelle (M3U/Xtream/EPG)")
            OutlinedTextField(
                value = m3u,
                onValueChange = { if (canChangeSources) scope.launch { store.set(Keys.M3U_URL, it) } },
                label = { Text("M3U / Xtream get.php Link") },
                singleLine = true,
                enabled = canChangeSources,
                modifier = Modifier.fillMaxWidth(),
                colors = tfColors
            )
            OutlinedTextField(
                value = epg,
                onValueChange = { if (canChangeSources) scope.launch { store.set(Keys.EPG_URL, it) } },
                label = { Text("EPG XMLTV URL (optional)") },
                singleLine = true,
                enabled = canChangeSources,
                modifier = Modifier.fillMaxWidth(),
                colors = tfColors
            )
            // EPG behavior
            val favUseXtream by store.epgFavUseXtream.collectAsState(initial = true)
            val favSkipXmltv by store.epgFavSkipXmltvIfXtreamOk.collectAsState(initial = false)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Favoriten-EPG via Xtream (falls konfiguriert)", modifier = Modifier.weight(1f))
                Switch(checked = favUseXtream, onCheckedChange = { v -> scope.launch { store.setEpgFavUseXtream(v) } })
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Aggressiv: XMLTV für Favoriten überspringen, wenn Xtream liefert", modifier = Modifier.weight(1f))
                Switch(checked = favSkipXmltv, onCheckedChange = { v -> scope.launch { store.setEpgFavSkipXmltvIfXtreamOk(v) } })
            }
            if (BuildConfig.SHOW_HEADER_UI) {
                OutlinedTextField(
                    value = ua,
                    onValueChange = { scope.launch { store.set(Keys.USER_AGENT, it) } },
                    label = { Text("User-Agent") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = tfColors
                )
            }
            if (BuildConfig.SHOW_HEADER_UI) {
                OutlinedTextField(
                    value = referer,
                    onValueChange = { scope.launch { store.set(Keys.REFERER, it) } },
                    label = { Text("Referer (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = tfColors
                )
            }
            // Xtream (optional) — can be auto-filled from M3U get.php
            Text("Xtream (optional)", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = xtHost,
                    onValueChange = { scope.launch { store.set(Keys.XT_HOST, it) } },
                    label = { Text("Host") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    colors = tfColors
                )
                OutlinedTextField(
                    value = xtPort.toString(),
                    onValueChange = { s -> s.toIntOrNull()?.let { p -> scope.launch { store.setInt(Keys.XT_PORT, p) } } },
                    label = { Text("Port") },
                    singleLine = true,
                    modifier = Modifier.width(120.dp),
                    colors = tfColors
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = xtUser,
                    onValueChange = { scope.launch { store.set(Keys.XT_USER, it) } },
                    label = { Text("Benutzername") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    colors = tfColors
                )
                OutlinedTextField(
                    value = xtPass,
                    onValueChange = { scope.launch { store.set(Keys.XT_PASS, it) } },
                    label = { Text("Passwort") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.weight(1f),
                    colors = tfColors
                )
            }
            Spacer(Modifier.height(8.dp))
            val xtConfigured = remember(xtHost, xtUser, xtPass) { xtHost.isNotBlank() && xtUser.isNotBlank() && xtPass.isNotBlank() }
            if (xtConfigured) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { onOpenXtreamCfCheck?.invoke() }, colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Color.Black)) { Text("Portal checken (WebView)") }
                    TextButton(onClick = { onOpenXtreamCfCheck?.invoke() }) { Text("Cloudflare lösen (WebView)") }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = xtOut,
                    onValueChange = { scope.launch { store.set(Keys.XT_OUTPUT, it) } },
                    label = { Text("Output (ts|m3u8|mp4)") },
                    singleLine = true,
                    modifier = Modifier.width(200.dp),
                    colors = tfColors
                )
                val ctx = LocalContext.current
                TextButton(onClick = {
                    scope.launch {
                        val m3u = store.m3uUrl.first()
                        val cfg = com.chris.m3usuite.core.xtream.XtreamConfig.fromM3uUrl(m3u)
                        if (cfg != null) {
                            store.setXtHost(cfg.host); store.setXtPort(cfg.port); store.setXtUser(cfg.username); store.setXtPass(cfg.password)
                            cfg.liveExtPrefs.firstOrNull()?.let { store.setXtOutput(it) }
                        }
                    }
                }) { Text("Aus M3U ableiten") }
                // Diagnose: Xtream testen (player_api) + Capabilities anzeigen
                TextButton(onClick = {
                    scope.launch {
                        val hostNow = store.xtHost.first(); val portNow = store.xtPort.first(); val userNow = store.xtUser.first(); val passNow = store.xtPass.first()
                        if (hostNow.isBlank() || userNow.isBlank() || passNow.isBlank()) { snackHost.showSnackbar("Xtream unvollständig."); return@launch }
                        val scheme = if (portNow == 443) "https" else "http"
                        val base = "$scheme://$hostNow:$portNow"
                        val url = "$base/player_api.php?username=${userNow}&password=${passNow}"
                        val client = HttpClientFactory.create(ctx, store)
                        val headers = com.chris.m3usuite.core.http.RequestHeadersProvider.defaultHeaders(store)
                        val startNs = System.nanoTime()
                        val req = okhttp3.Request.Builder().url(url).get().apply { headers.forEach { (k, v) -> header(k, v) } }.build()
                        runCatching {
                            client.newCall(req).execute().use { res ->
                                val dur = (System.nanoTime() - startNs)/1_000_000
                                val code = res.code; val ctype = res.header("Content-Type").orEmpty()
                                val peek = res.peekBody(2048).string()
                                val jsonOk = runCatching { kotlinx.serialization.json.Json.parseToJsonElement(peek); true }.getOrDefault(false)
                                Log.i("XtreamDiag", "ua='${headers["User-Agent"]}' code=${code} type='${ctype}' jsonOk=${jsonOk} durMs=${dur}")
                                snackHost.showSnackbar("Xt: code=${code} json=${jsonOk}")
                            }
                        }.onFailure { e -> Log.w("XtreamDiag", "fail: ${e.message}", e); snackHost.showSnackbar("Xt: Fehler ${e.message}") }
                    }
                }) { Text("Xtream testen") }
                TextButton(onClick = {
                    scope.launch {
                        val hostNow = store.xtHost.first(); val portNow = store.xtPort.first(); val userNow = store.xtUser.first(); val passNow = store.xtPass.first()
                        if (hostNow.isBlank() || userNow.isBlank() || passNow.isBlank()) { snackHost.showSnackbar("Xtream unvollständig."); return@launch }
                        val scheme = if (portNow == 443) "https" else "http"
                        val http = com.chris.m3usuite.core.http.HttpClientFactory.create(ctx, store)
                        val capsStore = com.chris.m3usuite.core.xtream.ProviderCapabilityStore(ctx)
                        val portStore = com.chris.m3usuite.core.xtream.EndpointPortStore(ctx)
                        val disc = com.chris.m3usuite.core.xtream.CapabilityDiscoverer(http, capsStore, portStore)
                        val cfgExplicit = com.chris.m3usuite.core.xtream.XtreamConfig(
                            scheme = scheme,
                            host = hostNow,
                            port = portNow,
                            username = userNow,
                            password = passNow
                        )
                        val caps = runCatching { withContext(Dispatchers.IO) { disc.discover(cfgExplicit, false) } }.getOrElse { e ->
                            Log.w("XtreamCaps", "discover failed: ${e.message}", e); snackHost.showSnackbar("Caps: Fehler ${e.message}"); return@launch
                        }
                        val info = buildString {
                            append("baseUrl=").append(caps.baseUrl).append('\n')
                            append("vodKind=").append(caps.resolvedAliases.vodKind ?: "-").append('\n')
                            append("actions=")
                            append(caps.actions.entries.joinToString { (k,v) -> "$k:${if (v.supported) '1' else '0'}" })
                        }
                        Log.i("XtreamCaps", info)
                        snackHost.showSnackbar("Capabilities: ${caps.resolvedAliases.vodKind ?: "-"}")
                    }
                }) { Text("Capabilities") }
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
                            val obx = com.chris.m3usuite.data.repo.XtreamObxRepository(ctx, store)
                            val sid = withContext(Dispatchers.IO) { obx.livePaged(0, 1).firstOrNull()?.streamId }
                            if (sid == null) {
                                Log.w(tag, "No live streamId found in DB; import might be required")
                                snackHost.showSnackbar("EPG-Test: keine Live-StreamId gefunden")
                                return@launch
                            }
                            val scheme = if (portNow == 443) "https" else "http"
                            val http = com.chris.m3usuite.core.http.HttpClientFactory.create(ctx, store)
                            val caps = com.chris.m3usuite.core.xtream.ProviderCapabilityStore(ctx)
                            val portStore = com.chris.m3usuite.core.xtream.EndpointPortStore(ctx)
                            val client = com.chris.m3usuite.core.xtream.XtreamClient(http)
                            client.initialize(scheme, hostNow, userNow, passNow, basePath = null, store = caps, portStore = portStore, portOverride = portNow)
                            val list = client.fetchShortEpg(sid!!, 2)?.let { json ->
                                val root = kotlinx.serialization.json.Json.parseToJsonElement(json).jsonArray
                                root.mapNotNull { el ->
                                    val obj = el.jsonObject
                                    com.chris.m3usuite.core.xtream.XtShortEPGProgramme(
                                        title = obj["title"]?.jsonPrimitive?.contentOrNull,
                                        start = obj["start"]?.jsonPrimitive?.contentOrNull,
                                        end = obj["end"]?.jsonPrimitive?.contentOrNull,
                                    )
                                }
                            } ?: emptyList()
                            Log.d(tag, "shortEPG result count=${list.size}; entries=${list.map { it.title }}")
                            snackHost.showSnackbar("EPG-Test: ${list.getOrNull(0)?.title ?: "(leer)"}")
                        } catch (t: Throwable) {
                            Log.e(tag, "EPG test failed", t)
                            snackHost.showSnackbar("EPG-Test fehlgeschlagen: ${t.message}")
                        }
                    }
                }) { Text("Test EPG (Debug)") }
            }

            // Make EPG test visible as a full-width button using repository (includes XMLTV fallback)
            val ctxRepo = LocalContext.current
            Button(onClick = {
                scope.launch {
                    val tag = "XtreamEPGTest"
                    try {
                        val hostNow = store.xtHost.first(); val userNow = store.xtUser.first(); val passNow = store.xtPass.first(); val outNow = store.xtOutput.first(); val portNow = store.xtPort.first()
                        Log.d(tag, "Testing EPG via repo with host=${hostNow}:${portNow}, user=${userNow}, output=${outNow}")
                        if (hostNow.isBlank() || userNow.isBlank() || passNow.isBlank()) {
                            Log.w(tag, "Xtream config missing; cannot test EPG")
                            snackHost.showSnackbar("EPG-Test: Xtream-Konfig fehlt")
                            return@launch
                        }
                        val obx = com.chris.m3usuite.data.repo.XtreamObxRepository(ctxRepo, store)
                        val firstLive = withContext(Dispatchers.IO) { obx.livePaged(0, 200).firstOrNull() }
                        val sid = firstLive?.streamId
                        Log.d(tag, "Selected sid=${sid} tvg-id=${firstLive?.epgChannelId} name=${firstLive?.name}")
                        if (sid == null) {
                            Log.w(tag, "No live streamId found in DB; import might be required")
                            snackHost.showSnackbar("EPG-Test: keine Live-StreamId gefunden")
                            return@launch
                        }
                        val repo = com.chris.m3usuite.data.repo.EpgRepository(ctxRepo, store)
                        val list = repo.nowNext(sid, 2)
                        Log.d(tag, "repo EPG count=${list.size}; entries=${list.map { it.title }}")
                        snackHost.showSnackbar("EPG-Test: ${list.getOrNull(0)?.title ?: "(leer)"}")
                    } catch (t: Throwable) {
                        Log.e(tag, "EPG test failed", t)
                        snackHost.showSnackbar("EPG-Test fehlgeschlagen: ${t.message}")
                    }
                }
            }, enabled = canChangeSources, colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Color.Black)) { Text("EPG testen (Debug)") }

            // Debug / Logging
            Spacer(Modifier.height(16.dp))
            Text("Debug", style = MaterialTheme.typography.titleSmall)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("HTTP-Traffic-Log speichern (JSONL)", modifier = Modifier.weight(1f))
                Switch(checked = httpLogEnabled, onCheckedChange = { v -> scope.launch { store.setHttpLogEnabled(v); com.chris.m3usuite.core.http.TrafficLogger.setEnabled(v) } })
            }
            TextButton(onClick = {
                val dir = java.io.File(ctxRepo.filesDir, "http-logs")
                runCatching { dir.listFiles()?.forEach { it.delete() }; dir.delete() }
                scope.launch { snackHost.showSnackbar("HTTP-Logs gelöscht") }
            }) { Text("HTTP-Logs löschen") }
            Button(onClick = {
                scope.launch {
                    val logsDir = java.io.File(ctxRepo.filesDir, "http-logs")
                    val files = logsDir.listFiles()?.filter { it.isFile }?.sortedBy { it.name } ?: emptyList()
                    if (files.isEmpty()) {
                        snackHost.showSnackbar("Keine HTTP-Logs vorhanden")
                        return@launch
                    }
                    val exportDir = java.io.File(ctxRepo.cacheDir, "exports").apply { mkdirs() }
                    val stamp = java.text.SimpleDateFormat("yyyyMMdd-HHmmss", java.util.Locale.US).format(java.util.Date())
                    val zipFile = java.io.File(exportDir, "http-logs-$stamp.zip")
                    runCatching {
                        java.util.zip.ZipOutputStream(java.io.BufferedOutputStream(java.io.FileOutputStream(zipFile))).use { zos ->
                            val buf = ByteArray(16 * 1024)
                            for (f in files) {
                                val entry = java.util.zip.ZipEntry(f.name)
                                entry.time = f.lastModified()
                                zos.putNextEntry(entry)
                                java.io.BufferedInputStream(java.io.FileInputStream(f)).use { ins ->
                                    while (true) {
                                        val n = ins.read(buf)
                                        if (n <= 0) break
                                        zos.write(buf, 0, n)
                                    }
                                }
                                zos.closeEntry()
                            }
                        }
                    }.onFailure {
                        snackHost.showSnackbar("Export fehlgeschlagen: ${it.message ?: "Unbekannt"}")
                        return@launch
                    }
                    // Share/save via chooser
                    val uri = androidx.core.content.FileProvider.getUriForFile(ctxRepo, ctxRepo.packageName + ".fileprovider", zipFile)
                    val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                        type = "application/zip"
                        putExtra(android.content.Intent.EXTRA_SUBJECT, zipFile.name)
                        putExtra(android.content.Intent.EXTRA_STREAM, uri)
                        addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    ctxRepo.startActivity(android.content.Intent.createChooser(send, "HTTP-Logs exportieren"))
                }
            }, colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Color.Black)) { Text("HTTP-Logs exportieren/teilen…") }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val ctx = LocalContext.current
                Button(onClick = {
                    // Run immediate import via OneTime Work; avoid parallel runs by checking the matching unique name
                    scope.launch {
                        val wm = withContext(kotlinx.coroutines.Dispatchers.IO) { androidx.work.WorkManager.getInstance(ctx) }
                        val infosOnce = withContext(kotlinx.coroutines.Dispatchers.IO) {
                            wm.getWorkInfosForUniqueWork("xtream_delta_import_once").get()
                        }
                        val running = infosOnce.any { it.state == androidx.work.WorkInfo.State.RUNNING || it.state == androidx.work.WorkInfo.State.ENQUEUED }
                        if (running) {
                            snackHost.showSnackbar("Import läuft bereits…")
                            return@launch
                        }
                        com.chris.m3usuite.work.SchedulingGateway.triggerXtreamRefreshNow(ctx)
                        snackHost.showSnackbar("Import gestartet")
                        val tag = "ImportUpdate"
                        try {
                            val snap = store.snapshot()
                            android.util.Log.d(tag, "start m3u=\"${snap.m3uUrl}\" epg=\"${snap.epgUrl}\" ua=\"${snap.userAgent}\" ref=\"${snap.referer}\" defaultUa=\"${com.chris.m3usuite.BuildConfig.DEFAULT_UA}\"")
                        } catch (_: Throwable) {}
                        val xtObx = com.chris.m3usuite.data.repo.XtreamObxRepository(ctx, store)
                        val plRepo = PlaylistRepository(ctx, store)
                        val cfg = runCatching { com.chris.m3usuite.core.xtream.XtreamConfig.fromM3uUrl(store.m3uUrl.first()) }.getOrNull()?.also { c ->
                            android.util.Log.d(tag, "xtream.cfg host=${c.host} port=${c.port}")
                            store.setXtHost(c.host); store.setXtPort(c.port); store.setXtUser(c.username); store.setXtPass(c.password)
                        }
                        var result = if (cfg != null) xtObx.importDelta(deleteOrphans = true).map { it.first + it.second + it.third } else plRepo.refreshFromM3U()
                        // If Xtream import failed, try automatic port fallback with common ports and persist the first working one.
                        if (cfg != null && result.isFailure) {
                            val originalPort = store.xtPort.first()
                            val ports = listOf(originalPort, 443, 80, 8080, 8443, 2082, 2083, 2086, 2095, 2096, 8880, 8000, 8081, 9999).distinct()
                            for (p in ports) {
                                if (p == originalPort) continue // already tried
                                snackHost.showSnackbar("Xtream-Import fehlgeschlagen. Probiere Port $p…")
                                store.setInt(Keys.XT_PORT, p)
                                val tryRes = xtObx.importDelta(deleteOrphans = true).map { it.first + it.second + it.third }
                                if (tryRes.isSuccess) {
                                    result = tryRes
                                    snackHost.showSnackbar("Port $p erfolgreich – gespeichert")
                                    store.setXtPortVerified(true)
                                    break
                                }
                            }
                            if (result.isFailure) {
                                // Restore original port if no candidate worked
                                store.setInt(Keys.XT_PORT, originalPort)
                            }
                        }
                        // Room removed: skip legacy DB emptiness checks
                        // Optional: Output fallback if Xtream import succeeded but live playback likely fails (wrong container)
                        // Room removed: skip live URL output testing against DB samples

                        com.chris.m3usuite.work.SchedulingGateway.scheduleXtreamPeriodic(ctx)
                        com.chris.m3usuite.work.SchedulingGateway.scheduleXtreamEnrichment(ctx)
                        val count = result.getOrNull()
                        if (count != null) {
                            android.util.Log.d(tag, "done count=${count}")
                            snackHost.showSnackbar("Import abgeschlossen: $count Einträge")
                        } else {
                            android.util.Log.e(tag, "failed ex=${result.exceptionOrNull()?.message}")
                            snackHost.showSnackbar("Import fehlgeschlagen – wird erneut versucht", withDismissAction = true, duration = androidx.compose.material3.SnackbarDuration.Indefinite)
                        }
                    }
                }, enabled = canChangeSources, colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Color.Black)) { Text("Import aktualisieren") }
                if (onOpenProfiles != null) {
                    TextButton(onClick = onOpenProfiles) { Text("Profile verwalten…") }
                }
            }

            // Quick import (Drive/File) visible in settings too
            // Telegram section (global toggle; default OFF)
            HorizontalDivider()
            SectionHeader("Telegram")
            val tgEnabled by store.tgEnabled.collectAsStateWithLifecycle(initialValue = false)
            val ctx2 = LocalContext.current
            // BuildConfig fallback
            val bcApiId = remember {
                runCatching {
                    val f = Class.forName(ctx2.packageName + ".BuildConfig").getDeclaredField("TG_API_ID"); f.isAccessible = true; (f.get(null) as? Int) ?: 0
                }.getOrDefault(0)
            }
            val bcApiHash = remember {
                runCatching {
                    val f = Class.forName(ctx2.packageName + ".BuildConfig").getDeclaredField("TG_API_HASH"); f.isAccessible = true; (f.get(null) as? String) ?: ""
                }.getOrDefault("")
            }
            val overrideApiId by store.tgApiId.collectAsStateWithLifecycle(initialValue = 0)
            val overrideApiHash by store.tgApiHash.collectAsStateWithLifecycle(initialValue = "")
            val effApiId = if (overrideApiId > 0) overrideApiId else bcApiId
            val effApiHash = if (overrideApiHash.isNotBlank()) overrideApiHash else bcApiHash
            val authRepo = remember(effApiId, effApiHash) { com.chris.m3usuite.data.repo.TelegramAuthRepository(ctx2, effApiId, effApiHash) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Telegram-Integration aktivieren (Beta)", modifier = Modifier.weight(1f))
                Switch(
                    checked = tgEnabled,
                    onCheckedChange = { v ->
                        scope.launch {
                            store.setTelegramEnabled(v)
                            if (v) {
                                if (!authRepo.isAvailable()) {
                                    snackHost.showSnackbar("TDLib nicht verfügbar – Bibliotheken prüfen.")
                                } else if (!authRepo.hasValidKeys()) {
                                    snackHost.showSnackbar("API-Keys fehlen – ID/HASH setzen.")
                                } else {
                                    runCatching { authRepo.bindService(); authRepo.setInBackground(false); authRepo.start() }
                                        .onFailure { snackHost.showSnackbar("Start fehlgeschlagen: ${it.message ?: "Unbekannt"}") }
                                }
                            } else {
                                runCatching { authRepo.setInBackground(true); authRepo.unbindService() }
                            }
                        }
                    }
                )
            }
            val tgChats by store.tgSelectedChatsCsv.collectAsStateWithLifecycle(initialValue = "")
            val tgVodSel by store.tgSelectedVodChatsCsv.collectAsStateWithLifecycle(initialValue = "")
            val tgSeriesSel by store.tgSelectedSeriesChatsCsv.collectAsStateWithLifecycle(initialValue = "")
            val tgCacheGb by store.tgCacheLimitGb.collectAsStateWithLifecycle(initialValue = 2)
            OutlinedTextField(
                value = tgChats,
                onValueChange = { scope.launch { store.setTelegramSelectedChatsCsv(it) } },
                label = { Text("Chat-IDs (CSV), optional") },
                singleLine = true,
                enabled = tgEnabled,
                modifier = Modifier.fillMaxWidth(),
                colors = tfColors
            )
            OutlinedTextField(
                value = tgCacheGb.toString(),
                onValueChange = { s -> s.toIntOrNull()?.let { g -> scope.launch { store.setTelegramCacheLimitGb(g.coerceIn(1, 20)) } } },
                label = { Text("Cache-Limit (GB)") },
                singleLine = true,
                enabled = tgEnabled,
                modifier = Modifier.width(220.dp),
                colors = tfColors
            )
            if (tgEnabled) {
                var showTgDialog by remember { mutableStateOf(false) }
                val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
                val keysValid = effApiId > 0 && effApiHash.isNotBlank()
                // Bind/unbind TDLib service with Settings lifecycle (only when Telegram enabled)
                DisposableEffect(lifecycleOwner, tgEnabled) {
                    val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
                        if (!tgEnabled) return@LifecycleEventObserver
                        when (event) {
                            androidx.lifecycle.Lifecycle.Event.ON_START -> {
                                authRepo.bindService()
                                authRepo.setInBackground(false)
                            }
                            androidx.lifecycle.Lifecycle.Event.ON_STOP -> {
                                authRepo.setInBackground(true)
                                authRepo.unbindService()
                            }
                            else -> {}
                        }
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    if (tgEnabled && lifecycleOwner.lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)) {
                        authRepo.bindService()
                        authRepo.setInBackground(false)
                    }
                    onDispose {
                        lifecycleOwner.lifecycle.removeObserver(observer)
                        if (tgEnabled) runCatching { authRepo.setInBackground(true); authRepo.unbindService() }
                    }
                }
                val authState by authRepo.authState.collectAsStateWithLifecycle(initialValue = com.chris.m3usuite.telegram.TdLibReflection.AuthState.UNKNOWN)
                // Surface service errors as snackbars
                LaunchedEffect(tgEnabled) {
                    if (tgEnabled) {
                        authRepo.errors.collect { em -> snackHost.showSnackbar("Telegram: $em") }
                    }
                }
                var showLogout by remember { mutableStateOf(false) }
                if (tgEnabled && !keysValid) {
                    Spacer(Modifier.height(6.dp))
                    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(imageVector = Icons.Outlined.Info, contentDescription = null)
                            Text("API‑Keys fehlen – bitte API ID und API HASH setzen, um TDLib zu starten.")
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Button(enabled = tgEnabled && keysValid, onClick = {
                        if (!authRepo.isAvailable()) {
                            scope.launch { snackHost.showSnackbar("TDLib nicht verfügbar – bitte Bibliotheken bündeln.") }
                        } else if (!authRepo.hasValidKeys()) {
                            scope.launch { snackHost.showSnackbar("API-Keys fehlen – TG_API_ID/HASH setzen.") }
                        } else {
                            val ok = runCatching { authRepo.start() }.getOrElse { e ->
                                scope.launch { snackHost.showSnackbar("Telegram-Start fehlgeschlagen: ${e.message ?: "Unbekannter Fehler"}") }
                                false
                            }
                            if (ok) showTgDialog = true
                        }
                    }, colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Color.Black)) { Text("Telegram verbinden") }
                    TextButton(enabled = tgEnabled && keysValid, onClick = {
                        if (!authRepo.hasValidKeys()) {
                            scope.launch { snackHost.showSnackbar("API-Keys fehlen – bitte ID/HASH setzen.") }
                        } else {
                            runCatching { authRepo.start() }
                            authRepo.requestQrLogin(); showTgDialog = true
                        }
                    }) { Text("QR‑Login anfordern") }
                    TextButton(onClick = { showLogout = true }) { Text("Abmelden/Reset") }
                    TextButton(onClick = { val st = authRepo.authState.value; scope.launch { snackHost.showSnackbar("Telegram-Status: $st") } }) { Text("Status (Debug)") }
                }
                if (showLogout) {
                    var wipe by remember { mutableStateOf(true) }
                    AlertDialog(
                        onDismissRequest = { showLogout = false },
                        title = { Text("Telegram abmelden") },
                        text = {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Willst du dich von Telegram abmelden?")
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(checked = wipe, onCheckedChange = { wipe = it })
                                    Text("Lokalen Telegram-Cache löschen")
                                }
                            }
                        },
                        confirmButton = {
                            TextButton(onClick = {
                                authRepo.logout()
                                if (wipe) com.chris.m3usuite.work.TelegramCacheCleanupWorker.wipeAll(ctx2)
                                scope.launch { snackHost.showSnackbar("Abmeldung ausgelöst") }
                                showLogout = false
                            }) { Text("Abmelden") }
                        },
                        dismissButton = {
                            TextButton(onClick = { showLogout = false }) { Text("Abbrechen") }
                        }
                    )
                }
                // Statuszeile + Secrets Quick-Check (maskiert)
                val maskedHash = if (effApiHash.isNotBlank()) effApiHash.take(4) + "…" else "(leer)"
                Text("Status: ${authState}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Keys: ID=${effApiId}  HASH=${maskedHash}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                // Optional API override inputs
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = if (overrideApiId > 0) overrideApiId.toString() else "",
                        onValueChange = { s -> s.toIntOrNull()?.let { v -> scope.launch { store.setTelegramApiId(v) } } },
                        label = { Text("API ID (optional)") },
                        singleLine = true,
                        enabled = tgEnabled,
                        modifier = Modifier.width(200.dp)
                    )
                    OutlinedTextField(
                        value = overrideApiHash,
                        onValueChange = { s -> scope.launch { store.setTelegramApiHash(s.trim()) } },
                        label = { Text("API HASH (optional)") },
                        singleLine = true,
                        enabled = tgEnabled,
                        modifier = Modifier.weight(1f)
                    )
                }
                if (showTgDialog) TelegramLoginDialog(onDismiss = { showTgDialog = false }, repo = authRepo)

                // Sync Sources: Filme und Serien – Auswahl per Chat-Picker
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    var showVodPicker by remember { mutableStateOf(false) }
                    var showSeriesPicker by remember { mutableStateOf(false) }
                    Button(onClick = { showVodPicker = true }, colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Color.Black)) { Text("Film Sync") }
                    Button(onClick = { showSeriesPicker = true }, colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Color.Black)) { Text("Serien Sync") }
                    if (tgVodSel.isNotBlank()) {
                        TextButton(onClick = { com.chris.m3usuite.work.SchedulingGateway.scheduleTelegramSync(ctx2, com.chris.m3usuite.work.TelegramSyncWorker.MODE_VOD) }) { Text("Filme synchronisieren") }
                    }
                    if (tgSeriesSel.isNotBlank()) {
                        TextButton(onClick = { com.chris.m3usuite.work.SchedulingGateway.scheduleTelegramSync(ctx2, com.chris.m3usuite.work.TelegramSyncWorker.MODE_SERIES) }) { Text("Serien synchronisieren") }
                    }
                    if (showVodPicker) TelegramChatPickerDialog(onDismiss = { showVodPicker = false }, onSelect = { chatId ->
                        val cur = tgVodSel.split(',').filter { it.isNotBlank() }.toMutableSet()
                        cur.add(chatId.toString())
                        scope.launch { store.setTelegramSelectedVodChatsCsv(cur.joinToString(",")) }
                        showVodPicker = false
                    }, onRequestLogin = { showTgDialog = true })
                    if (showSeriesPicker) TelegramChatPickerDialog(onDismiss = { showSeriesPicker = false }, onSelect = { chatId ->
                        val cur = tgSeriesSel.split(',').filter { it.isNotBlank() }.toMutableSet()
                        cur.add(chatId.toString())
                        scope.launch { store.setTelegramSelectedSeriesChatsCsv(cur.joinToString(",")) }
                        showSeriesPicker = false
                    }, onRequestLogin = { showTgDialog = true })
                }
                // Selected chips (scrollable row)
                if (tgVodSel.isNotBlank() || tgSeriesSel.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text("Quellen:", style = MaterialTheme.typography.labelLarge)
                }
                if (tgVodSel.isNotBlank()) {
                    Text("• Filme: ${tgVodSel}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (tgSeriesSel.isNotBlank()) {
                    Text("• Serien: ${tgSeriesSel}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                // Minimal sync progress UI (poll WorkManager for RUNNING states)
                val wm = remember { androidx.work.WorkManager.getInstance(ctx2) }
                var vodProcessed by remember { mutableStateOf<Int?>(null) }
                var seriesProcessed by remember { mutableStateOf<Int?>(null) }
                LaunchedEffect(tgEnabled) {
                    while (tgEnabled) {
                        try {
                            val vodInfos = withContext(kotlinx.coroutines.Dispatchers.IO) { wm.getWorkInfosForUniqueWork("tg_sync_vod").get() }
                            val vi = vodInfos.firstOrNull { it.state == androidx.work.WorkInfo.State.RUNNING }
                            vodProcessed = vi?.progress?.getInt("processed", -1)?.takeIf { it >= 0 }
                            val seriesInfos = withContext(kotlinx.coroutines.Dispatchers.IO) { wm.getWorkInfosForUniqueWork("tg_sync_series").get() }
                            val si = seriesInfos.firstOrNull { it.state == androidx.work.WorkInfo.State.RUNNING }
                            seriesProcessed = si?.progress?.getInt("processed", -1)?.takeIf { it >= 0 }
                        } catch (_: Throwable) {}
                        kotlinx.coroutines.delay(600)
                    }
                }
                if (vodProcessed != null || seriesProcessed != null) {
                    Spacer(Modifier.height(4.dp))
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    val vp = vodProcessed?.let { "Filme: $it" } ?: ""
                    val sp = seriesProcessed?.let { "Serien: $it" } ?: ""
                    Text("Telegram Sync läuft… ${listOf(vp, sp).filter { it.isNotBlank() }.joinToString("  ")}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            com.chris.m3usuite.backup.QuickImportRow()

            HorizontalDivider()
            com.chris.m3usuite.backup.BackupRestoreSection()

            // --- M3U Export ---
            SectionHeader("M3U Export")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val ctx = LocalContext.current
                Button(onClick = {
                    scope.launch {
                        val dir = java.io.File(ctx.cacheDir, "exports").apply { mkdirs() }
                        val file = java.io.File(dir, "playlist.m3u")
                        java.io.OutputStreamWriter(java.io.FileOutputStream(file), Charsets.UTF_8).use { w ->
                            com.chris.m3usuite.core.m3u.M3UExporter.stream(ctx, store, w)
                        }
                        // Use FileProvider with the authority declared in manifest: ${applicationId}.fileprovider
                        val uri = androidx.core.content.FileProvider.getUriForFile(ctx, ctx.packageName + ".fileprovider", file)
                        val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                            type = "application/x-mpegURL"
                            putExtra(android.content.Intent.EXTRA_SUBJECT, "playlist.m3u")
                            putExtra(android.content.Intent.EXTRA_STREAM, uri)
                            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        ctx.startActivity(android.content.Intent.createChooser(send, "M3U teilen"))
                    }
                }, colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Color.Black)) { Text("Teilen…") }

                Button(onClick = {
                    scope.launch {
                        val dir = java.io.File(ctx.cacheDir, "exports").apply { mkdirs() }
                        val file = java.io.File(dir, "playlist.m3u")
                        java.io.OutputStreamWriter(java.io.FileOutputStream(file), Charsets.UTF_8).use { w ->
                            com.chris.m3usuite.core.m3u.M3UExporter.stream(ctx, store, w)
                        }
                        val uri = androidx.core.content.FileProvider.getUriForFile(ctx, ctx.packageName + ".fileprovider", file)
                        val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                            type = "application/x-mpegURL"
                            putExtra(android.content.Intent.EXTRA_SUBJECT, "playlist.m3u")
                            putExtra(android.content.Intent.EXTRA_STREAM, uri)
                            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        ctx.startActivity(android.content.Intent.createChooser(send, "M3U speichern/teilen"))
                    }
                }, colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Color.Black)) { Text("Als Datei speichern…") }
            }
            }
        }
    }

    // PIN Dialog host
    pinDialogMode?.let { activePinMode ->
        showPinDialog(store = store, mode = activePinMode) { pinDialogMode = null }
    }
}

@Composable
private fun QrCodeBox(data: String, sizeDp: Int = 200) {
    val sizePx = with(LocalDensity.current) { sizeDp.dp.toPx().toInt() }
    val bmp = remember(data, sizePx) { generateQrBitmap(data, sizePx, sizePx) }
    if (bmp != null) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Image(painter = BitmapPainter(bmp.asImageBitmap()), contentDescription = "QR Code", modifier = Modifier.size(sizeDp.dp))
        }
    }
}

private fun generateQrBitmap(text: String, width: Int, height: Int): Bitmap? {
    return try {
        val hints = mapOf(EncodeHintType.MARGIN to 1)
        val bitMatrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, width, height, hints)
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        for (x in 0 until width) {
            for (y in 0 until height) {
                bmp.setPixel(x, y, if (bitMatrix.get(x, y)) 0xFF000000.toInt() else 0xFFFFFFFF.toInt())
            }
        }
        bmp
    } catch (_: Throwable) { null }
}

@Composable
private fun TelegramLoginDialog(onDismiss: () -> Unit, repo: com.chris.m3usuite.data.repo.TelegramAuthRepository) {
    val state by repo.authState.collectAsStateWithLifecycle(initialValue = com.chris.m3usuite.telegram.TdLibReflection.AuthState.UNKNOWN)
    var phone by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var didCheckDbKey by remember { mutableStateOf(false) }
    var qrLink by remember { mutableStateOf<String?>(null) }
    // Collect QR links from service when available
    LaunchedEffect(Unit) {
        repo.qrLinks.collect { link -> qrLink = link }
    }
    val title = when (state) {
        com.chris.m3usuite.telegram.TdLibReflection.AuthState.WAIT_FOR_NUMBER, com.chris.m3usuite.telegram.TdLibReflection.AuthState.UNAUTHENTICATED, com.chris.m3usuite.telegram.TdLibReflection.AuthState.WAIT_ENCRYPTION_KEY -> "Telegram verbinden"
        com.chris.m3usuite.telegram.TdLibReflection.AuthState.WAIT_FOR_CODE -> "Bestätigungscode"
        com.chris.m3usuite.telegram.TdLibReflection.AuthState.WAIT_FOR_PASSWORD -> "Passwort"
        com.chris.m3usuite.telegram.TdLibReflection.AuthState.WAIT_OTHER_DEVICE -> "QR‑Bestätigung"
        com.chris.m3usuite.telegram.TdLibReflection.AuthState.AUTHENTICATED -> "Verbunden"
        else -> "Telegram"
    }
    // Auto-trigger DB key check when TDLib asks for it
    LaunchedEffect(state) {
        when (state) {
            com.chris.m3usuite.telegram.TdLibReflection.AuthState.WAIT_ENCRYPTION_KEY -> {
                if (!didCheckDbKey) {
                    busy = true
                    repo.checkDbKey()
                    didCheckDbKey = true
                }
            }
            com.chris.m3usuite.telegram.TdLibReflection.AuthState.WAIT_FOR_CODE,
            com.chris.m3usuite.telegram.TdLibReflection.AuthState.WAIT_FOR_PASSWORD,
            com.chris.m3usuite.telegram.TdLibReflection.AuthState.AUTHENTICATED -> busy = false
            else -> {}
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                when (state) {
                    com.chris.m3usuite.telegram.TdLibReflection.AuthState.WAIT_FOR_NUMBER, com.chris.m3usuite.telegram.TdLibReflection.AuthState.UNAUTHENTICATED -> {
                        OutlinedTextField(
                            value = phone,
                            onValueChange = { phone = it },
                            label = { Text("Telefonnummer") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                            colors = TextFieldDefaults.colors()
                        )
                        Text("Gib deine Telefonnummer im internationalen Format ein (z. B. +491701234567)", style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.height(6.dp))
                        Text("Oder melde dich per QR an: In der Telegram‑App auf deinem Smartphone zu Einstellungen → Geräte → Gerät verbinden gehen.", style = MaterialTheme.typography.bodySmall)
                    }
                    com.chris.m3usuite.telegram.TdLibReflection.AuthState.WAIT_ENCRYPTION_KEY -> {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        Text("Datenbank‑Schlüssel wird überprüft…", style = MaterialTheme.typography.bodySmall)
                    }
                    com.chris.m3usuite.telegram.TdLibReflection.AuthState.WAIT_FOR_CODE -> {
                        OutlinedTextField(
                            value = code,
                            onValueChange = { code = it },
                            label = { Text("Bestätigungscode") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            colors = TextFieldDefaults.colors()
                        )
                        Text("Den Code aus der Telegram-App eingeben.", style = MaterialTheme.typography.bodySmall)
                    }
                    com.chris.m3usuite.telegram.TdLibReflection.AuthState.WAIT_FOR_PASSWORD -> {
                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it },
                            label = { Text("Passwort") },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            colors = TextFieldDefaults.colors()
                        )
                        Text("Zwei-Faktor-Passwort eingeben (falls aktiviert).", style = MaterialTheme.typography.bodySmall)
                    }
                    com.chris.m3usuite.telegram.TdLibReflection.AuthState.AUTHENTICATED -> {
                        Text("Erfolgreich verbunden.")
                    }
                    com.chris.m3usuite.telegram.TdLibReflection.AuthState.WAIT_OTHER_DEVICE -> {
                        Text("Warten auf Bestätigung auf einem anderen Gerät.", style = MaterialTheme.typography.bodySmall)
                        val link = qrLink
                        if (!link.isNullOrBlank()) {
                            Spacer(Modifier.height(8.dp))
                            QrCodeBox(data = link)
                            Spacer(Modifier.height(6.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                val ctx = LocalContext.current
                                Text(link, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                                TextButton(onClick = {
                                    val clip = android.content.ClipData.newPlainText("tg_login", link)
                                    val cm = ctx.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                    cm.setPrimaryClip(clip)
                                }) { Text("Link kopieren") }
                                TextButton(onClick = { repo.requestQrLogin() }) { Text("Neu laden") }
                            }
                        } else {
                            Text("Öffne Telegram auf deinem Smartphone → Einstellungen → Geräte → QR‑Code scannen.", style = MaterialTheme.typography.bodySmall)
                            Spacer(Modifier.height(6.dp))
                            TextButton(onClick = { repo.requestQrLogin() }) { Text("QR erneut anfordern") }
                        }
                    }
                    else -> {
                        Text("Warte auf Status…")
                    }
                }
                if (busy) {
                    Spacer(Modifier.height(4.dp))
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Text("Warte auf Antwort…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        confirmButton = {
            when (state) {
                com.chris.m3usuite.telegram.TdLibReflection.AuthState.WAIT_FOR_NUMBER, com.chris.m3usuite.telegram.TdLibReflection.AuthState.UNAUTHENTICATED -> {
                    TextButton(onClick = {
                        if (repo.start() && phone.isNotBlank()) { busy = true; repo.sendPhoneNumber(phone) }
                    }) { Text("Weiter") }
                }
                com.chris.m3usuite.telegram.TdLibReflection.AuthState.WAIT_FOR_CODE -> {
                    TextButton(onClick = { if (code.isNotBlank()) { busy = true; repo.sendCode(code) } }) { Text("Bestätigen") }
                }
                com.chris.m3usuite.telegram.TdLibReflection.AuthState.WAIT_FOR_PASSWORD -> {
                    TextButton(onClick = { if (password.isNotBlank()) { busy = true; repo.sendPassword(password) } }) { Text("Bestätigen") }
                }
                com.chris.m3usuite.telegram.TdLibReflection.AuthState.WAIT_ENCRYPTION_KEY -> {
                    TextButton(onClick = { }) { Text("Weiter") }
                }
                com.chris.m3usuite.telegram.TdLibReflection.AuthState.AUTHENTICATED -> {
                    TextButton(onClick = onDismiss) { Text("Schließen") }
                }
                else -> { TextButton(onClick = {}) { Text("OK") } }
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = {
                    // Reset transient inputs
                    phone = ""; code = ""; password = ""; busy = false
                    onDismiss()
                }) { Text("Abbrechen") }
                if (state == com.chris.m3usuite.telegram.TdLibReflection.AuthState.WAIT_OTHER_DEVICE) {
                    TextButton(onClick = { repo.requestQrLogin() }) { Text("Erneut versuchen") }
                }
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TelegramChatPickerDialog(
    onDismiss: () -> Unit,
    onSelect: (Long) -> Unit,
    onRequestLogin: () -> Unit
) {
    val ctx = LocalContext.current
    val authState = remember { mutableStateOf(TdLibReflection.AuthState.UNKNOWN) }
    val chatsMain = remember { mutableStateListOf<Pair<Long, String>>() }
    val chatsArchive = remember { mutableStateListOf<Pair<Long, String>>() }
    var loading by remember { mutableStateOf(true) }
    var folder by remember { mutableStateOf("main") }
    var search by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        loading = true
        val flow = kotlinx.coroutines.flow.MutableStateFlow(TdLibReflection.AuthState.UNKNOWN)
        val client = runCatching { TdLibReflection.getOrCreateClient(ctx, flow) }.getOrNull()
        authState.value = flow.value
        if (client != null) {
            // Ensure authenticated
            val authObj = TdLibReflection.buildGetAuthorizationState()?.let { TdLibReflection.sendForResult(client, it, 1000) }
            val st = TdLibReflection.mapAuthorizationState(authObj)
            authState.value = st
            if (st != TdLibReflection.AuthState.AUTHENTICATED) {
                loading = false
                return@LaunchedEffect
            }
            fun load(listObj: Any?, target: MutableList<Pair<Long, String>>) {
                if (listObj == null) return
                val get = TdLibReflection.buildGetChats(listObj, 200) ?: return
                val res = TdLibReflection.sendForResult(client, get) ?: return
                val ids = TdLibReflection.extractChatsIds(res) ?: longArrayOf()
                for (id in ids) {
                    val q = TdLibReflection.buildGetChat(id) ?: continue
                    val chatObj = TdLibReflection.sendForResult(client, q)
                    val title = chatObj?.let { TdLibReflection.extractChatTitle(it) } ?: id.toString()
                    target.add(id to title)
                }
            }
            load(TdLibReflection.buildChatListMain(), chatsMain)
            load(TdLibReflection.buildChatListArchive(), chatsArchive)
        }
        loading = false
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text("Telegram – Ordner auswählen", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            if (authState.value != TdLibReflection.AuthState.AUTHENTICATED) {
                Text("Bitte zuerst Telegram verbinden.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { onRequestLogin() }, colors = ButtonDefaults.buttonColors(containerColor = com.chris.m3usuite.ui.theme.DesignTokens.Accent, contentColor = Color.Black)) { Text("Jetzt verbinden") }
                    TextButton(onClick = onDismiss) { Text("Schließen") }
                }
                return@ModalBottomSheet
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(label = { Text("Hauptordner") }, onClick = { folder = "main" }, leadingIcon = null, enabled = !loading)
                AssistChip(label = { Text("Archiv") }, onClick = { folder = "archive" }, leadingIcon = null, enabled = !loading)
            }
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                label = { Text("Suchen…") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            if (loading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                Text("Lade Chats…", style = MaterialTheme.typography.bodySmall)
            } else {
                val itemsBase = if (folder == "archive") chatsArchive else chatsMain
                val q = search.trim().lowercase()
                val items = if (q.isBlank()) itemsBase else itemsBase.filter { it.second.lowercase().contains(q) || it.first.toString().contains(q) }
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 520.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(items) { (id, title) ->
                        ElevatedCard(onClick = { onSelect(id) }, modifier = Modifier.fillMaxWidth()) {
                            Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text(title)
                                Text(id.toString(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text("Schließen") }
            }
        }
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
    val list = remember {
        val intents = listOf(
            // Broad video handler
            android.content.Intent(android.content.Intent.ACTION_VIEW).apply { type = "video/*" },
            // Common streaming/container types
            android.content.Intent(android.content.Intent.ACTION_VIEW).apply { setDataAndType(android.net.Uri.parse("http://example.com/sample.m3u8"), "application/vnd.apple.mpegurl") },
            android.content.Intent(android.content.Intent.ACTION_VIEW).apply { setDataAndType(android.net.Uri.parse("http://example.com/sample.m3u8"), "application/x-mpegurl") },
            android.content.Intent(android.content.Intent.ACTION_VIEW).apply { setDataAndType(android.net.Uri.parse("http://example.com/sample.mpd"), "application/dash+xml") },
            android.content.Intent(android.content.Intent.ACTION_VIEW).apply { setDataAndType(android.net.Uri.parse("http://example.com/sample.ts"), "video/MP2T") },
            android.content.Intent(android.content.Intent.ACTION_VIEW).apply { setDataAndType(android.net.Uri.parse("http://example.com/sample.mp4"), "video/mp4") },
            // Data-only fallback (some players accept ACTION_VIEW with just data)
            android.content.Intent(android.content.Intent.ACTION_VIEW).apply { data = android.net.Uri.parse("http://example.com/stream") }
        )
        val flagSets = listOf(0, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY)
        val results = mutableListOf<android.content.pm.ResolveInfo>()
        for (intent in intents) {
            for (flags in flagSets) {
                try {
                    results += pm.queryIntentActivities(intent, flags)
                } catch (_: Throwable) { /* ignore */ }
            }
        }
        results
            .filter { it.activityInfo?.packageName != null && it.activityInfo.packageName != ctx.packageName }
            .distinctBy { it.activityInfo.packageName }
            .sortedBy { it.loadLabel(pm)?.toString() ?: it.activityInfo.packageName }
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

@Composable
private fun SectionHeader(text: String) {
    Column(Modifier.fillMaxWidth()) {
        Text(
            text,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        HorizontalDivider(
            modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
            thickness = 1.dp,
            color = com.chris.m3usuite.ui.theme.DesignTokens.Accent.copy(alpha = 0.35f)
        )
    }
}

@Composable
private fun SectionCard(accent: Color, content: @Composable ColumnScope.() -> Unit) {
    ElevatedCard(
        modifier = Modifier.border(BorderStroke(1.dp, accent.copy(alpha = 0.25f)), shape = MaterialTheme.shapes.medium),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.65f)),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 3.dp)
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            content()
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
    val dlgColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = MaterialTheme.colorScheme.onSurface,
        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
        disabledTextColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
        focusedLabelColor = MaterialTheme.colorScheme.onSurface,
        unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
        focusedContainerColor = MaterialTheme.colorScheme.surface,
        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
        disabledContainerColor = MaterialTheme.colorScheme.surface,
        cursorColor = MaterialTheme.colorScheme.primary,
        focusedBorderColor = MaterialTheme.colorScheme.outline,
        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)
    )
    AlertDialog(
        onDismissRequest = { open = false },
        title = { Text(when (mode) { PinMode.Set -> "PIN festlegen"; PinMode.Change -> "PIN ändern"; PinMode.Clear -> "PIN entfernen" }) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (mode != PinMode.Set) {
                    OutlinedTextField(value = old, onValueChange = { old = it }, label = { Text("Aktuelle PIN") }, singleLine = true, visualTransformation = PasswordVisualTransformation(), colors = dlgColors)
                }
                if (mode != PinMode.Clear) {
                    OutlinedTextField(value = pin, onValueChange = { pin = it }, label = { Text("Neue PIN") }, singleLine = true, visualTransformation = PasswordVisualTransformation(), colors = dlgColors)
                    OutlinedTextField(value = pin2, onValueChange = { pin2 = it }, label = { Text("Wiederholen") }, singleLine = true, visualTransformation = PasswordVisualTransformation(), colors = dlgColors)
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
