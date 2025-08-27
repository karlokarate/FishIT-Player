package com.chris.m3usuite.ui.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.chris.m3usuite.data.db.DbProvider
import com.chris.m3usuite.data.db.Profile
import com.chris.m3usuite.data.repo.ScreenTimeRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.PickVisualMediaRequest
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import android.graphics.Bitmap
import java.io.File
import java.io.FileOutputStream

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileManagerScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val db = remember { DbProvider.get(ctx) }
    val scope = rememberCoroutineScope()
    val screenRepo = remember { ScreenTimeRepository(ctx) }

    var kids by remember { mutableStateOf<List<Profile>>(emptyList()) }
    var newKidName by remember { mutableStateOf("") }

    suspend fun load() {
        kids = withContext(Dispatchers.IO) { db.profileDao().all().filter { it.type == "kid" } }
    }

    LaunchedEffect(Unit) { load() }

    val snack = remember { SnackbarHostState() }
    Scaffold(topBar = {
        TopAppBar(title = { Text("Profile verwalten") }, navigationIcon = {
            IconButton(onClick = onBack) { Icon(painter = androidx.compose.ui.res.painterResource(android.R.drawable.ic_menu_revert), contentDescription = "Zurück") }
        })
    }, snackbarHost = { SnackbarHost(snack) }) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(value = newKidName, onValueChange = { newKidName = it }, label = { Text("Neues Kinderprofil") })
            Button(onClick = {
                scope.launch(Dispatchers.IO) {
                    val now = System.currentTimeMillis()
                    db.profileDao().insert(Profile(name = newKidName.ifBlank { "Kind" }, type = "kid", avatarPath = null, createdAt = now, updatedAt = now))
                    newKidName = ""
                    load()
                }
            }, enabled = newKidName.isNotBlank()) { Text("Anlegen") }

            HorizontalDivider()

            LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                items(kids, key = { it.id }) { kid ->
                    var name by remember(kid.id) { mutableStateOf(kid.name) }
                    var limit by remember(kid.id) { mutableStateOf(60) }
                    var avatarPath by remember(kid.id) { mutableStateOf(kid.avatarPath) }

                    fun persistBitmapAsJpeg(bitmap: Bitmap, dest: File): Boolean {
                        return try {
                            dest.parentFile?.mkdirs()
                            FileOutputStream(dest).use { out -> bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out) }
                            true
                        } catch (_: Throwable) { false }
                    }
                    suspend fun persistAvatarFromUri(uri: android.net.Uri): Boolean {
                        return withContext(Dispatchers.IO) {
                            try {
                                val dest = File(ctx.filesDir, "avatars/${kid.id}/avatar.jpg")
                                dest.parentFile?.mkdirs()
                                ctx.contentResolver.openInputStream(uri)?.use { input ->
                                    FileOutputStream(dest).use { output -> input.copyTo(output) }
                                }
                                true
                            } catch (_: Throwable) { false }
                        }
                    }

                    val pickLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
                        if (uri != null) {
                            scope.launch {
                                val ok = persistAvatarFromUri(uri)
                                if (ok) {
                                    val abs = File(ctx.filesDir, "avatars/${kid.id}/avatar.jpg").absolutePath
                                    val stored = "file://$abs"
                                    withContext(Dispatchers.IO) { db.profileDao().update(kid.copy(avatarPath = stored, updatedAt = System.currentTimeMillis())) }
                                    avatarPath = stored
                                    snack.showSnackbar("Avatar aktualisiert")
                                } else snack.showSnackbar("Avatar konnte nicht gespeichert werden")
                            }
                        }
                    }
                    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bmp ->
                        if (bmp != null) {
                            scope.launch(Dispatchers.IO) {
                                val dest = File(ctx.filesDir, "avatars/${kid.id}/avatar.jpg")
                                val ok = persistBitmapAsJpeg(bmp, dest)
                                withContext(Dispatchers.Main) {
                                    if (ok) {
                                        val abs = dest.absolutePath
                                        val stored = "file://$abs"
                                        withContext(Dispatchers.IO) { db.profileDao().update(kid.copy(avatarPath = stored, updatedAt = System.currentTimeMillis())) }
                                        avatarPath = stored
                                        snack.showSnackbar("Foto gespeichert")
                                    } else snack.showSnackbar("Foto konnte nicht gespeichert werden")
                                }
                            }
                        }
                    }

                    OutlinedCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                if (!avatarPath.isNullOrBlank()) {
                                    AsyncImage(
                                        model = ImageRequest.Builder(ctx).data(avatarPath).build(),
                                        contentDescription = null,
                                        modifier = Modifier.size(48.dp).clip(CircleShape),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Icon(painter = androidx.compose.ui.res.painterResource(android.R.drawable.ic_menu_report_image), contentDescription = null)
                                }
                                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, modifier = Modifier.weight(1f))
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(onClick = { pickLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) { Text("Avatar wählen") }
                                TextButton(onClick = { cameraLauncher.launch(null) }) { Text("Foto aufnehmen") }
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Tageslimit (Minuten)", modifier = Modifier.weight(1f))
                                Slider(value = limit.toFloat(), valueRange = 0f..240f, onValueChange = { limit = it.toInt() })
                                Text("${limit}")
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = {
                                    scope.launch(Dispatchers.IO) {
                                        db.profileDao().update(kid.copy(name = name, updatedAt = System.currentTimeMillis()))
                                        screenRepo.setDailyLimit(kid.id, limit)
                                        load()
                                    }
                                }) { Text("Speichern") }
                                TextButton(onClick = {
                                    scope.launch(Dispatchers.IO) {
                                        db.profileDao().delete(kid)
                                        load()
                                    }
                                }) { Text("Löschen") }
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }
}
