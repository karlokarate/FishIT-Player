package com.chris.m3usuite.backup

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.chris.m3usuite.drive.DriveClient
import com.chris.m3usuite.drive.DriveDefaults
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class BackupViewModel(app: Application) : AndroidViewModel(app) {

    data class UiState(
        val running: Boolean = false,
        val progress: Int = 0,
        val stage: String = "",
        val lastResult: String? = null,
        val error: String? = null
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state

    fun exportToFile(dest: Uri, passphrase: CharArray?) {
        val ctx = getApplication<Application>()
        viewModelScope.launch {
            try {
                _state.value = UiState(running = true, progress = 0, stage = "Exportiere…")
                val mgr = SettingsBackupManager(ctx)
                val (name, bytes) = mgr.exportAll({ p, s -> _state.value = _state.value.copy(progress = p, stage = s) }, passphrase)
                ctx.contentResolver.openOutputStream(dest)?.use { it.write(bytes) } ?: error("Kann nicht schreiben")
                _state.value = UiState(running = false, progress = 100, stage = "Gespeichert", lastResult = name)
            } catch (e: Exception) {
                _state.value = UiState(running = false, error = e.message)
            }
        }
    }

    fun exportToDrive(passphrase: CharArray?) {
        val ctx = getApplication<Application>()
        viewModelScope.launch {
            try {
                _state.value = UiState(running = true, progress = 0, stage = "Exportiere…")
                val mgr = SettingsBackupManager(ctx)
                val (name, bytes) = mgr.exportAll({ p, s -> _state.value = _state.value.copy(progress = p, stage = s) }, passphrase)
                _state.value = _state.value.copy(stage = "Lade zu Google Drive hoch…", progress = 80)
                val id = DriveClient.uploadBytes(ctx, DriveDefaults.DEFAULT_FOLDER_ID, name, "application/octet-stream", bytes)
                _state.value = UiState(running = false, progress = 100, stage = "Hochgeladen", lastResult = "Drive ID: $id")
            } catch (e: Exception) {
                _state.value = UiState(running = false, error = e.message)
            }
        }
    }

    fun importFromFile(src: Uri, passphrase: CharArray?, mode: SettingsBackupManager.ImportMode) {
        val ctx = getApplication<Application>()
        viewModelScope.launch {
            try {
                _state.value = UiState(running = true, progress = 0, stage = "Lese Datei…")
                val bytes = ctx.contentResolver.openInputStream(src)?.use { it.readBytes() } ?: error("Kann nicht lesen")
                val mgr = SettingsBackupManager(ctx)
                val rep = mgr.importAll(bytes, passphrase, mode) { p, s -> _state.value = _state.value.copy(progress = p, stage = s) }
                _state.value = UiState(running = false, progress = 100, lastResult = "Importiert: ${rep.profiles} Profile, ${rep.resumeVod + rep.resumeEpisodes} Weiterschauen")
            } catch (e: Exception) {
                _state.value = UiState(running = false, error = e.message)
            }
        }
    }

    fun importFromDrive(passphrase: CharArray?, mode: SettingsBackupManager.ImportMode) {
        val ctx = getApplication<Application>()
        viewModelScope.launch {
            try {
                _state.value = UiState(running = true, progress = 0, stage = "Lade von Google Drive…")
                val bytes = DriveClient.downloadLatestByPrefix(ctx, DriveDefaults.DEFAULT_FOLDER_ID, "m3usuite-settings") ?: error("Keine Datei gefunden")
                val mgr = SettingsBackupManager(ctx)
                val rep = mgr.importAll(bytes, passphrase, mode) { p, s -> _state.value = _state.value.copy(progress = p, stage = s) }
                _state.value = UiState(running = false, progress = 100, lastResult = "Importiert: ${rep.profiles} Profile")
            } catch (e: Exception) {
                _state.value = UiState(running = false, error = e.message)
            }
        }
    }
}
