package com.chris.m3usuite.ui.screens

import android.app.Activity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.chris.m3usuite.data.repo.PlaylistRepository
import com.chris.m3usuite.data.repo.XtreamRepository
import com.chris.m3usuite.prefs.Keys
import com.chris.m3usuite.prefs.SettingsStore
import com.chris.m3usuite.work.XtreamEnrichmentWorker
import com.chris.m3usuite.work.XtreamRefreshWorker
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@Composable
fun PlaylistSetupScreen(onDone: () -> Unit) {
    val ctx = LocalContext.current
    val store = remember { SettingsStore(ctx) }
    val scope = rememberCoroutineScope()

    // Deep-Link (VIEW-Intent) als Initialwert
    val initialLink by remember {
        mutableStateOf((ctx as? Activity)?.intent?.dataString.orEmpty())
    }

    var m3u by remember { mutableStateOf(TextFieldValue(initialLink)) }
    var epg by remember { mutableStateOf(TextFieldValue("")) }
    var ua by remember { mutableStateOf(TextFieldValue("IBOPlayer/1.4 (Android)")) }
    var ref by remember { mutableStateOf(TextFieldValue("")) }
    var busy by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf("") }

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

    Column(Modifier.padding(16.dp)) {
        Text("Setup", style = MaterialTheme.typography.titleLarge)
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

                    // Speichern
                    store.set(Keys.M3U_URL, m3u.text.trim())
                    store.set(Keys.EPG_URL, epg.text.trim())
                    store.set(Keys.USER_AGENT, ua.text.trim())
                    store.set(Keys.REFERER, ref.text.trim())

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
                        XtreamRefreshWorker.schedule(ctx)
                        XtreamEnrichmentWorker.schedule(ctx)
                        onDone()
                    } else {
                        msg = "Fehler: ${imported.exceptionOrNull()?.message}"
                    }
                }
            }
        ) {
            Text(if (busy) "Bitte warten…" else "Speichern & Importieren")
        }
    }
}
