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
import com.chris.m3usuite.ui.common.AppIcon
import com.chris.m3usuite.ui.common.AppIconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import com.chris.m3usuite.ui.skin.focusScaleOnTv

@Composable
fun AvatarCaptureAndPickButtons(
    onPicked: (Uri) -> Unit
) {
    val context = LocalContext.current

    val tempDir = remember { File(context.cacheDir, "images").apply { mkdirs() } }
    var tempFile by remember { mutableStateOf<File?>(null) }
    val authority = "${context.packageName}.fileprovider"

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val f = tempFile
        if (success && f != null && f.exists()) {
            onPicked(Uri.fromFile(f))
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
        if (uri != null) onPicked(uri)
    }
    val legacyPickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) onPicked(uri)
    }

    AppIconButton(icon = AppIcon.Camera, contentDescription = "Foto aufnehmen", onClick = { launchCameraWithPermission() })
    AppIconButton(icon = AppIcon.Gallery, contentDescription = "Bild aus Galerie wählen", onClick = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) pickerLauncher13.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
        ) else legacyPickerLauncher.launch("image/*")
    })
}

private fun Context.findActivity(): Activity {
    var ctx: Context = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    throw IllegalStateException("Activity not found from context")
}
