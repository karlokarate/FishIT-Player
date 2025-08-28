package com.chris.m3usuite.ui.backup

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.chris.m3usuite.backup.BackupSaf
import com.chris.m3usuite.backup.BackupViewModel
import com.chris.m3usuite.backup.SettingsBackupManager
import com.chris.m3usuite.drive.DriveClient

@Composable
fun BackupRestoreSection(
    vm: BackupViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
) {
    val ctx = LocalContext.current
    val state by vm.state.collectAsState()

    var encrypt by remember { mutableStateOf(false) }
    var pass by remember { mutableStateOf("") }
    var mode by remember { mutableStateOf(SettingsBackupManager.ImportMode.Merge) }

    val createLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        val uri = it.data?.data ?: return@rememberLauncherForActivityResult
        vm.exportToFile(uri, if (encrypt) pass.toCharArray() else null)
    }
    val openLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        val uri = it.data?.data ?: return@rememberLauncherForActivityResult
        vm.importFromFile(uri, if (encrypt) pass.toCharArray() else null, mode)
    }

    Column(Modifier.fillMaxWidth().padding(12.dp)) {
        Text("Backup & Restore", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Checkbox(checked = encrypt, onCheckedChange = { encrypt = it })
            Text("Verschlüsseln (AES‑256)")
        }
        if (encrypt) {
            OutlinedTextField(
                value = pass,
                onValueChange = { pass = it },
                label = { Text("Passphrase") },
                modifier = Modifier.fillMaxWidth(),
                visualTransformation = PasswordVisualTransformation()
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { createLauncher.launch(BackupSaf.createExportIntent("m3usuite-settings.msbx")) }) { Text("Export als Datei") }
            Button(onClick = {
                val act = ctx as? Activity
                if (act != null && !DriveClient.isSignedIn(ctx)) DriveClient.signIn(act) { }
                vm.exportToDrive(if (encrypt) pass.toCharArray() else null)
            }) { Text("Export zu Drive") }
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Import‑Modus:")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = mode == SettingsBackupManager.ImportMode.Merge, onClick = { mode = SettingsBackupManager.ImportMode.Merge }, label = { Text("Merge") })
                FilterChip(selected = mode == SettingsBackupManager.ImportMode.Replace, onClick = { mode = SettingsBackupManager.ImportMode.Replace }, label = { Text("Replace") })
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { openLauncher.launch(BackupSaf.createImportIntent()) }) { Text("Import aus Datei") }
            Button(onClick = {
                val act = ctx as? Activity
                if (act != null && !DriveClient.isSignedIn(ctx)) DriveClient.signIn(act) { }
                vm.importFromDrive(if (encrypt) pass.toCharArray() else null, mode)
            }) { Text("Import aus Drive") }
        }
        if (state.running) {
            Spacer(Modifier.height(12.dp))
            LinearProgressIndicator(progress = state.progress / 100f, modifier = Modifier.fillMaxWidth())
            Text(state.stage)
        }
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        state.lastResult?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
    }
}
