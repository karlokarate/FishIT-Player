package com.chris.m3usuite.ui.screens

import android.app.Activity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import android.os.Build
import android.graphics.RenderEffect
import android.graphics.Shader
import com.chris.m3usuite.ui.theme.DesignTokens
import androidx.compose.ui.Modifier
import androidx.compose.foundation.background
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.unit.dp
import com.chris.m3usuite.data.repo.PlaylistRepository
import com.chris.m3usuite.data.repo.XtreamRepository
import com.chris.m3usuite.prefs.Keys
import com.chris.m3usuite.prefs.SettingsStore
import com.chris.m3usuite.work.SchedulingGateway
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import com.chris.m3usuite.backup.QuickImportRow

@Composable
fun PlaylistSetupScreen(onDone: () -> Unit) {
    val ctx = LocalContext.current
    val store = remember { SettingsStore(ctx) }
    val scope = rememberCoroutineScope()

    // Deep-Link (VIEW-Intent) als Initialwert
    val initialLink by remember {
        mutableStateOf((ctx as? Activity)?.intent?.dataString.orEmpty())
    }

    var m3u by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue(initialLink)) }
    var epg by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue("")) }
    var ua by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue("IBOPlayer/1.4 (Android)")) }
    var ref by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue("")) }
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
        Image(painter = painterResource(id = com.chris.m3usuite.R.drawable.fisch), contentDescription = null, modifier = Modifier.align(Alignment.Center).size(520.dp).graphicsLayer { alpha = 0.05f; try { if (Build.VERSION.SDK_INT >= 31) renderEffect = android.graphics.RenderEffect.createBlurEffect(34f, 34f, android.graphics.Shader.TileMode.CLAMP).asComposeRenderEffect() } catch (_: Throwable) {} })
    com.chris.m3usuite.ui.common.AccentCard(modifier = Modifier.padding(16.dp)) {
        Text("Setup", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))

        // Quick import (Drive/File) so users can pull settings before entering URLs
        QuickImportRow()
        Spacer(Modifier.height(8.dp))

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
        OutlinedTextField(
            value = ua, onValueChange = { ua = it },
            label = { Text("User-Agent") },
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = ref, onValueChange = { ref = it },
            label = { Text("Referer (optional)") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(8.dp))
        if (msg.isNotBlank()) {
            Text(msg, color = MaterialTheme.colorScheme.secondary)
            Spacer(Modifier.height(8.dp))
        }

        Button(
            enabled = !busy && m3u.text.isNotBlank(),
            onClick = {
                scope.launch {
                    busy = true
                    msg = "Import läuft…"

                    // Speichern (batch)
                    store.setSources(m3u.text.trim(), epg.text.trim(), ua.text.trim(), ref.text.trim())

                    // Import: Xtream bevorzugt, sonst M3U-Parser
                    val xtRepo = XtreamRepository(ctx, store)
                    val cfg = xtRepo.configureFromM3uUrl()
                    val imported = if (cfg != null) {
                        xtRepo.importAll()
                    } else {
                        val pl = PlaylistRepository(ctx, store)
                        pl.refreshFromM3U()
                    }

                    busy = false
                    if (imported.isSuccess) {
                        msg = "Fertig: ${imported.getOrThrow()} Einträge"
                        // Hintergrund-Updates einplanen
                        SchedulingGateway.scheduleXtreamPeriodic(ctx)
                        SchedulingGateway.scheduleXtreamEnrichment(ctx)
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
