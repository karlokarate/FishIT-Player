package com.chris.m3usuite.ui.profile

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

@Composable
fun AvatarCaptureAndPickButtons(
    onAvatarSaved: (File) -> Unit
) {
    val context = LocalContext.current

    val tempDir = remember { File(context.cacheDir, "images").apply { mkdirs() } }
    var tempFile by remember { mutableStateOf<File?>(null) }
    val authority = "${context.packageName}.fileprovider"

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val f = tempFile
        if (success && f != null && f.exists()) {
            saveAvatarPersistently(context, Uri.fromFile(f)) { saved ->
                if (saved != null) onAvatarSaved(saved) else Toast.makeText(context, "Konnte Avatar nicht speichern", Toast.LENGTH_LONG).show()
            }
        } else Toast.makeText(context, "Foto abgebrochen", Toast.LENGTH_SHORT).show()
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            val f = File.createTempFile("avatar_", ".jpg", tempDir)
            tempFile = f
            val uri = FileProvider.getUriForFile(context, authority, f)
            cameraLauncher.launch(uri)
        } else Toast.makeText(context, "Kamerazugriff verweigert", Toast.LENGTH_LONG).show()
    }

    fun launchCameraWithPermission() {
        when {
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED -> {
                val f = File.createTempFile("avatar_", ".jpg", tempDir)
                tempFile = f
                val uri = FileProvider.getUriForFile(context, authority, f)
                cameraLauncher.launch(uri)
            }
            ActivityCompat.shouldShowRequestPermissionRationale(context.findActivity(), Manifest.permission.CAMERA) -> {
                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
            else -> cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    val pickerLauncher13 = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            saveAvatarPersistently(context, uri) { saved ->
                if (saved != null) onAvatarSaved(saved) else Toast.makeText(context, "Konnte Avatar nicht speichern", Toast.LENGTH_LONG).show()
            }
        }
    }
    val legacyPickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            saveAvatarPersistently(context, uri) { saved ->
                if (saved != null) onAvatarSaved(saved) else Toast.makeText(context, "Konnte Avatar nicht speichern", Toast.LENGTH_LONG).show()
            }
        }
    }

    Button(onClick = { launchCameraWithPermission() }) { Text("Foto aufnehmen") }
    Button(onClick = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) pickerLauncher13.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
        ) else legacyPickerLauncher.launch("image/*")
    }) { Text("Bild aus Galerie wählen") }
}

private fun saveAvatarPersistently(context: Context, source: Uri, onDone: (File?) -> Unit) {
    GlobalScope.launch(Dispatchers.Main) {
        val result = withContext(Dispatchers.IO) {
            try {
                val dir = File(context.filesDir, "avatars").apply { mkdirs() }
                val out = File(dir, "avatar_${System.currentTimeMillis()}.jpg")
                context.contentResolver.openInputStream(source).use { input ->
                    FileOutputStream(out).use { output ->
                        if (input == null) return@withContext null
                        input.copyTo(output)
                    }
                }
                out
            } catch (t: Throwable) { null }
        }
        onDone(result)
    }
}

private fun Context.findActivity(): Activity {
    var ctx: Context = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    throw IllegalStateException("Activity not found from context")
}

