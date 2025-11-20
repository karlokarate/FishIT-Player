package com.chris.m3usuite.backup

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.chris.m3usuite.drive.DriveClient
import com.chris.m3usuite.drive.DriveDefaults
import com.chris.m3usuite.ui.common.TvButton
import com.chris.m3usuite.ui.common.TvOutlinedButton
import kotlinx.coroutines.launch

@Composable
fun QuickImportRow() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val mgr = remember { SettingsBackupManager(ctx) }
    var running by remember { mutableStateOf(false) }
    var info by remember { mutableStateOf("") }

    OutlinedCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Schnell-Import", style = MaterialTheme.typography.titleMedium)
            if (running) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TvButton(enabled = !running, onClick = {
                    scope.launch {
                        runCatching {
                            running = true
                            info = "Suche in Drive…"
                            if (!DriveClient.isSignedIn(ctx)) info = "Bitte in Drive anmelden (Einstellungen)"
                            val bytes = DriveClient.downloadLatestByPrefix(ctx, DriveDefaults.DEFAULT_FOLDER_ID, "m3usuite-settings-v1-")
                            if (bytes != null) {
                                mgr.importAll(bytes, null, SettingsBackupManager.ImportMode.Merge) { _, _ -> }
                                info = "Import abgeschlossen"
                            } else {
                                info = "Kein Backup gefunden"
                            }
                        }.onFailure { info = "Fehler: ${it.message}" }
                        running = false
                    }
                }) { Text("Von Drive importieren") }
                TvOutlinedButton(
                    enabled = !running,
                    onClick = { info = "Drive-Import erfordert Anmeldung unter Einstellungen" },
                ) { Text("Hinweis") }
            }
            if (info.isNotBlank()) Text(info, style = MaterialTheme.typography.bodySmall)
        }
    }
}
