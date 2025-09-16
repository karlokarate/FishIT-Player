package com.chris.m3usuite.ui.screens

import android.app.Activity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalDensity
import com.chris.m3usuite.ui.theme.DesignTokens
import androidx.compose.ui.Modifier
import androidx.compose.foundation.background
import androidx.compose.ui.Alignment
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloat
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.unit.dp
import com.chris.m3usuite.data.repo.PlaylistRepository
import com.chris.m3usuite.prefs.SettingsStore
import com.chris.m3usuite.work.SchedulingGateway
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import com.chris.m3usuite.backup.QuickImportRow
import com.chris.m3usuite.BuildConfig
import com.chris.m3usuite.core.http.HttpClientFactory
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import kotlinx.coroutines.withContext

// Setup mode for first-run screen (top-level enum; local enums are not allowed)
enum class SetupMode { M3U, XTREAM }

@Composable
fun PlaylistSetupScreen(onDone: () -> Unit) {
    val ctx = LocalContext.current
    val store = remember { SettingsStore(ctx) }
    val scope = rememberCoroutineScope()

    // Deep-Link (VIEW-Intent) als Initialwert
    val initialLink by remember {
        mutableStateOf((ctx as? Activity)?.intent?.dataString.orEmpty())
    }

    // Mode: M3U vs Xtream
    var mode by rememberSaveable { mutableStateOf(SetupMode.M3U) }

    var m3u by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue(initialLink)) }
    var epg by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue("")) }
    var ua by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue("IBOPlayer/1.4 (Android)")) }
    var ref by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue("")) }
    // Xtream fields (for XTREAM mode)
    var xtHost by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue("")) }
    var xtPort by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue("80")) }
    var xtHttps by rememberSaveable { mutableStateOf(false) }
    var xtUser by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue("")) }
    var xtPass by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue("")) }
    var xtOut  by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue("m3u8")) }
    var busy by rememberSaveable { mutableStateOf(false) }
    var msg by rememberSaveable { mutableStateOf("") }

    // Gespeicherte Werte beim Öffnen vorbefüllen (nutzt die Flows aus SettingsStore)
    LaunchedEffect(Unit) {
        // Falls dein SettingsStore andere Property-Namen nutzt, passe sie hier 1:1 an.
        val savedM3u  = runCatching { store.m3uUrl.first() }.getOrDefault("")
        val savedEpg  = runCatching { store.epgUrl.first() }.getOrDefault("")
        val savedUa   = runCatching { store.userAgent.first() }.getOrDefault("")
        val savedRef  = runCatching { store.referer.first() }.getOrDefault("")

        // Nur überschreiben, wenn gespeichert war — sonst Deep-Link übernehmen
        if (savedM3u.isNotBlank()) m3u = TextFieldValue(savedM3u)
        epg = TextFieldValue(savedEpg)
        ua  = TextFieldValue(savedUa.ifBlank { "IBOPlayer/1.4 (Android)" })
        ref = TextFieldValue(savedRef)
    }

    Box(Modifier.fillMaxSize()) {
        val Accent = DesignTokens.Accent
        Box(Modifier.matchParentSize().background(Brush.verticalGradient(0f to MaterialTheme.colorScheme.background, 1f to MaterialTheme.colorScheme.surface)))
        Box(Modifier.matchParentSize().background(Brush.radialGradient(colors = listOf(Accent.copy(alpha = 0.12f), androidx.compose.ui.graphics.Color.Transparent), radius = with(LocalDensity.current) { 640.dp.toPx() })))
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
    com.chris.m3usuite.ui.common.AccentCard(modifier = Modifier.padding(16.dp)) {
        Text("Setup", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))

        // Quick import (Drive/File) so users can pull settings before entering URLs
        QuickImportRow()
        Spacer(Modifier.height(8.dp))

        // Mode selector
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            FilterChip(selected = mode == SetupMode.M3U, onClick = { mode = SetupMode.M3U }, label = { Text("M3U Link") })
            FilterChip(selected = mode == SetupMode.XTREAM, onClick = { mode = SetupMode.XTREAM }, label = { Text("Xtream Login") })
        }
        Spacer(Modifier.height(8.dp))
        if (mode == SetupMode.M3U) {
            OutlinedTextField(
                value = m3u, onValueChange = { m3u = it },
                label = { Text("M3U / Xtream get.php Link") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = epg, onValueChange = { epg = it },
                label = { Text("EPG XMLTV URL (optional)") },
                modifier = Modifier.fillMaxWidth()
            )
        } else {
            OutlinedTextField(
                value = xtHost, onValueChange = { xtHost = it },
                label = { Text("Xtream Host (ohne Schema)") },
                modifier = Modifier.fillMaxWidth()
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = xtPort, onValueChange = { xtPort = it },
                    label = { Text("Port (80/443/8080…)") },
                    modifier = Modifier.weight(1f)
                )
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Checkbox(checked = xtHttps, onCheckedChange = { v -> xtHttps = v; if (v && xtPort.text == "80") xtPort = TextFieldValue("443") })
                    Text("HTTPS")
                }
            }
            OutlinedTextField(
                value = xtUser, onValueChange = { xtUser = it },
                label = { Text("Benutzername") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = xtPass, onValueChange = { xtPass = it },
                label = { Text("Passwort") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = xtOut, onValueChange = { xtOut = it },
                label = { Text("Output (ts|m3u8|mp4)") },
                modifier = Modifier.fillMaxWidth()
            )
        }
        if (BuildConfig.SHOW_HEADER_UI) {
            OutlinedTextField(
                value = ua, onValueChange = { ua = it },
                label = { Text("User-Agent") },
                modifier = Modifier.fillMaxWidth()
            )
        }
        if (BuildConfig.SHOW_HEADER_UI) {
            OutlinedTextField(
                value = ref, onValueChange = { ref = it },
                label = { Text("Referer (optional)") },
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(Modifier.height(8.dp))
        if (msg.isNotBlank()) {
            Text(msg, color = MaterialTheme.colorScheme.secondary)
            Spacer(Modifier.height(8.dp))
        }

        Button(
            enabled = !busy && (
                (mode == SetupMode.M3U && m3u.text.isNotBlank()) ||
                (mode == SetupMode.XTREAM && xtHost.text.isNotBlank() && xtUser.text.isNotBlank() && xtPass.text.isNotBlank())
            ),
            onClick = {
                scope.launch {
                    busy = true
                    msg = "Import läuft…"

                    var imported = if (mode == SetupMode.M3U) {
                        // M3U-Modus: speichern und wie bisher importieren (mit Xtream-Ableitung aus get.php, falls vorhanden)
                        store.setSources(m3u.text.trim(), epg.text.trim(), ua.text.trim(), ref.text.trim())
                        val cfg = com.chris.m3usuite.core.xtream.XtreamConfig.fromM3uUrl(m3u.text.trim())?.also { c ->
                            store.setXtHost(c.host); store.setXtPort(c.port); store.setXtUser(c.username); store.setXtPass(c.password)
                        }
                        if (cfg != null) com.chris.m3usuite.data.repo.XtreamObxRepository(ctx, store).importDelta(deleteOrphans = true)
                        else PlaylistRepository(ctx, store).refreshFromM3U()
                    } else {
                        // Xtream-Modus: aus Login get.php + xmltv.php ableiten, alles speichern, dann Xtream import
                        val host = xtHost.text.trim()
                        val port = xtPort.text.trim().toIntOrNull() ?: if (xtHttps) 443 else 80
                        val scheme = if (xtHttps || port == 443) "https" else "http"
                        val user = xtUser.text.trim()
                        val pass = xtPass.text.trim()
                        val out  = xtOut.text.trim().ifBlank { "m3u8" }
                        val portal = "$scheme://$host:$port"
                        val m3uUrl = "$portal/get.php?username=$user&password=$pass&output=$out"
                        val epgUrl = "$portal/xmltv.php?username=$user&password=$pass"
                        store.setSources(m3uUrl, epgUrl, ua.text.trim(), ref.text.trim())
                        // Persist Xtream creds (encrypted pass)
                        store.setXtHost(host); store.setXtPort(port); store.setXtUser(user); store.setXtPass(pass); store.setXtOutput(out)
                        // Import via Xtream, mit M3U-Fallback für VOD/Serien (greift bereits in Settings/Worker)
                        com.chris.m3usuite.data.repo.XtreamObxRepository(ctx, store).importDelta(deleteOrphans = true)
                    }

                    // Port fallback when Xtream import failed (interactive, with Snackbars)
                    if (mode == SetupMode.XTREAM && imported.isFailure) {
                        // Try common ports with OBX delta import
                        val originalPort = store.xtPort.first()
                        val ports = listOf(originalPort, 443, 80, 8080, 8000, 8081).distinct()
                        for (p in ports) {
                            if (p == originalPort) continue
                            msg = "Xtream-Import fehlgeschlagen. Probiere Port $p…"
                            store.setXtPort(p)
                            val tryRes = com.chris.m3usuite.data.repo.XtreamObxRepository(ctx, store).importDelta(deleteOrphans = true)
                            if (tryRes.isSuccess) {
                                imported = tryRes
                                msg = "Port $p erfolgreich – gespeichert"
                                break
                            }
                        }
                        if (imported.isFailure) {
                            // Restore original port if no candidate worked
                            store.setXtPort(originalPort)
                        }
                    }

                    busy = false
                    if (imported.isSuccess) {
                        // Optional: Output fallback (Xtream mode) if current live URLs are not reachable with chosen output
                        if (mode == SetupMode.XTREAM) {
                            try {
                                val roomEnabled = withContext(kotlinx.coroutines.Dispatchers.IO) { store.roomEnabled.first() }
                                val sid: Int? = if (roomEnabled) {
                                // Room removed: skip DB sampling
                                null
                                } else null
                                if (sid != null) {
                                    suspend fun testUrl(u: String): Boolean {
                                        val client = HttpClientFactory.create(ctx, store)
                                        return runCatching {
                                            val httpUrl = u.toHttpUrlOrNull() ?: return@runCatching false
                                            client.newCall(okhttp3.Request.Builder().url(httpUrl).header("Range", "bytes=0-0").build()).execute().use { res ->
                                                res.isSuccessful || res.code == 206
                                            }
                                        }.getOrDefault(false)
                                    }
                                    val currentUrl = null
                                    var ok = !currentUrl.isNullOrBlank() && testUrl(currentUrl!!)
                                    if (!ok) {
                                        val snap = store.snapshot()
                                        val outputs = listOf("m3u8", "ts", "mp4").distinct()
                                        for (o in outputs) {
                                            val scheme2 = if (snap.xtPort == 443) "https" else "http"
                                            val cfg2 = com.chris.m3usuite.core.xtream.XtreamConfig(
                                                scheme = scheme2,
                                                host = snap.xtHost,
                                                port = snap.xtPort,
                                                username = snap.xtUser,
                                                password = snap.xtPass,
                                                pathKinds = com.chris.m3usuite.core.xtream.PathKinds(),
                                                basePath = null,
                                                liveExtPrefs = listOf(o),
                                                vodExtPrefs = listOf("mp4", "mkv", "avi"),
                                                seriesExtPrefs = listOf("mp4", "mkv", "avi")
                                            )
                                            val url = cfg2.liveUrl(sid)
                                            msg = "Teste Stream-Output $o …"
                                            if (testUrl(url)) {
                                                msg = "Output $o erfolgreich – gespeichert"
                                                store.setXtOutput(o)
                                                imported = com.chris.m3usuite.data.repo.XtreamObxRepository(ctx, store).importDelta(deleteOrphans = true)
                                                break
                                            }
                                        }
                                    }
                                }
                            } catch (_: Throwable) { /* ignore tests */ }
                        }
                        val done = imported.getOrNull()
                        msg = when (done) {
                            is kotlin.Triple<*,*,*> -> "Fertig: live=${done.first} vod=${done.second} series=${done.third}"
                            is kotlin.Int -> "Fertig: $done"
                            else -> "Fertig"
                        }
                        // Hintergrund-Updates einplanen
                        SchedulingGateway.scheduleAll(ctx)
                        onDone()
                    } else {
                        msg = "Fehler: ${imported.exceptionOrNull()?.message}"
                    }
                }
            }
        , colors = ButtonDefaults.buttonColors(containerColor = com.chris.m3usuite.ui.theme.DesignTokens.Accent, contentColor = androidx.compose.ui.graphics.Color.Black)) {
            Text(if (busy) "Bitte warten…" else "Speichern & Importieren")
        }
    }
}
}
