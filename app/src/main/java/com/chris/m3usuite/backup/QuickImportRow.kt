package com.chris.m3usuite.ui.backup

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.chris.m3usuite.backup.BackupSaf
import com.chris.m3usuite.backup.BackupViewModel
import com.chris.m3usuite.backup.SettingsBackupManager
import com.chris.m3usuite.drive.DriveClient

@Composable
fun QuickImportRow(
    vm: BackupViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
) {
    val ctx = LocalContext.current
    val passphrase: CharArray? = null

    val openLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        val uri = it.data?.data ?: return@rememberLauncherForActivityResult
        vm.importFromFile(uri, passphrase, SettingsBackupManager.ImportMode.Merge)
    }

    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
        Text("Einstellungen importieren", style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { openLauncher.launch(BackupSaf.createImportIntent()) }) { Text("Aus Datei…") }
            Button(onClick = {
                val act = ctx as? Activity
                if (act != null && !DriveClient.isSignedIn(ctx)) DriveClient.signIn(act) { }
                vm.importFromDrive(passphrase, SettingsBackupManager.ImportMode.Merge)
            }) { Text("Aus Drive…") }
        }
    }
}
