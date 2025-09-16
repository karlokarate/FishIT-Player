package com.chris.m3usuite.backup

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@Composable
fun BackupRestoreSection() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val mgr = remember { SettingsBackupManager(ctx) }
    var progress by remember { mutableStateOf(0) }
    var status by remember { mutableStateOf("") }

    var pendingExport by remember { mutableStateOf<Pair<String, ByteArray>?>(null) }
    val createDoc = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri: Uri? ->
        val data = pendingExport ?: return@rememberLauncherForActivityResult
        if (uri != null) ctx.contentResolver.openOutputStream(uri)?.use { it.write(data.second) }
        pendingExport = null
    }

    val openDoc = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) scope.launch {
            val bytes = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return@launch
            mgr.importAll(bytes, null, SettingsBackupManager.ImportMode.Merge) { p, s -> progress = p; status = s }
        }
    }

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Backup & Restore", style = MaterialTheme.typography.titleMedium)
        if (status.isNotBlank()) LinearProgressIndicator(progress = { (progress / 100f).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                scope.launch {
                    val res = mgr.exportAll({ p, s -> progress = p; status = s }, passphrase = null)
                    pendingExport = res
                    createDoc.launch(res.first)
                }
            }) { Text("Export als Datei") }
            OutlinedButton(onClick = { openDoc.launch(arrayOf("application/octet-stream", "*/*")) }) { Text("Import aus Datei") }
        }
    }
}
