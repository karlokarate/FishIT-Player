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
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import android.os.Build
import android.graphics.RenderEffect
import android.graphics.Shader
import com.chris.m3usuite.ui.theme.DesignTokens
import com.chris.m3usuite.data.db.DbProvider
import com.chris.m3usuite.data.db.Profile
import com.chris.m3usuite.data.repo.ScreenTimeRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import com.chris.m3usuite.ui.home.HomeChromeScaffold
import androidx.compose.foundation.lazy.rememberLazyListState
import com.chris.m3usuite.ui.profile.AvatarCaptureAndPickButtons
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.chris.m3usuite.ui.util.rememberAvatarModel
import java.io.File
import java.io.FileOutputStream
import com.chris.m3usuite.ui.skin.focusScaleOnTv

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileManagerScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val db = remember { DbProvider.get(ctx) }
    val scope = rememberCoroutineScope()
    val screenRepo = remember { ScreenTimeRepository(ctx) }
    val vm: ProfileManagerViewModel = viewModel()
    var saving by remember { mutableStateOf(false) }
    BackHandler(enabled = saving) { /* block back while saving */ }

    var kids by remember { mutableStateOf<List<Profile>>(emptyList()) }
    var newKidName by remember { mutableStateOf("") }

    suspend fun load() {
        kids = withContext(Dispatchers.IO) { db.profileDao().all().filter { it.type == "kid" } }
    }

    LaunchedEffect(Unit) { load() }

    val snack = remember { SnackbarHostState() }
    val listState = rememberLazyListState()
    HomeChromeScaffold(
        title = "Profile",
        onSettings = null,
        onSearch = null,
        onProfiles = null,
        onRefresh = null,
        listState = listState,
        bottomBar = {}
    ) { pads ->
        Box(Modifier.fillMaxSize().padding(pads)) {
            val Accent = DesignTokens.KidAccent
            Box(Modifier.matchParentSize().background(Brush.verticalGradient(0f to MaterialTheme.colorScheme.background, 1f to MaterialTheme.colorScheme.surface)))
            Box(Modifier.matchParentSize().background(Brush.radialGradient(colors = listOf(Accent.copy(alpha = 0.20f), androidx.compose.ui.graphics.Color.Transparent), radius = with(LocalDensity.current) { 660.dp.toPx() })))
            Image(painter = painterResource(id = com.chris.m3usuite.R.drawable.fisch), contentDescription = null, modifier = Modifier.align(Alignment.Center).size(540.dp).graphicsLayer { alpha = 0.06f; try { if (Build.VERSION.SDK_INT >= 31) renderEffect = android.graphics.RenderEffect.createBlurEffect(34f, 34f, android.graphics.Shader.TileMode.CLAMP).asComposeRenderEffect() } catch (_: Throwable) {} })
        Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            TextButton(onClick = onBack) { Text("Zurück") }
            OutlinedTextField(value = newKidName, onValueChange = { newKidName = it }, label = { Text("Neues Kinderprofil") })
            Button(modifier = Modifier.focusScaleOnTv(), onClick = {
                scope.launch(Dispatchers.IO) {
                    val now = System.currentTimeMillis()
                    db.profileDao().insert(Profile(name = newKidName.ifBlank { "Kind" }, type = "kid", avatarPath = null, createdAt = now, updatedAt = now))
                    newKidName = ""
                    load()
                }
            }, enabled = newKidName.isNotBlank(), colors = ButtonDefaults.buttonColors(containerColor = com.chris.m3usuite.ui.theme.DesignTokens.KidAccent, contentColor = androidx.compose.ui.graphics.Color.Black)) { Text("Anlegen") }

            HorizontalDivider()

            LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                items(kids, key = { it.id }) { kid ->
                    var name by remember(kid.id) { mutableStateOf(kid.name) }
                    var limit by remember(kid.id) { mutableStateOf(60) }
                    var usedToday by remember(kid.id) { mutableStateOf(0) }
                    var remainingToday by remember(kid.id) { mutableStateOf(0) }
                    var avatarPath by remember(kid.id) { mutableStateOf(kid.avatarPath) }

                    // Load today's usage/limit from DB
                    LaunchedEffect(kid.id) {
                        // Read current entry; if missing, repo will create it on first write, so we fallback to 0/0
                        withContext(Dispatchers.IO) {
                            // Get remaining via repo, and limit via DAO
                            val dayKey = java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.getDefault())
                                .format(java.util.Calendar.getInstance().time)
                            val entry = DbProvider.get(ctx).screenTimeDao().getForDay(kid.id, dayKey)
                            val u = entry?.usedMinutes ?: 0
                            val l = entry?.limitMinutes ?: 0
                            usedToday = u
                            limit = if (l > 0) l else limit
                            remainingToday = (limit - u).coerceAtLeast(0)
                        }
                    }
                    // old capture/pick logic removed; using AvatarCaptureAndPickButtons below

                    OutlinedCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                val model = rememberAvatarModel(avatarPath)
                                if (model != null) {
                                    AsyncImage(
                                        model = model,
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
                                AvatarCaptureAndPickButtons { uri ->
                                    saving = true
                                    vm.saveAvatar(kid.id, uri) { ok, file ->
                                        saving = false
                                        if (ok && file != null) {
                                            avatarPath = file.absolutePath
                                            // Snackbar kann zeigen, auch wenn man direkt zurück geht
                                            scope.launch { snack.showSnackbar("Avatar aktualisiert") }
                                        } else {
                                            scope.launch { snack.showSnackbar("Avatar speichern fehlgeschlagen") }
                                        }
                                    }
                                }
                            }
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("Tageslimit (Minuten)", modifier = Modifier.weight(1f))
                                    Text("${limit}")
                                }
                                Slider(
                                    value = limit.toFloat(),
                                    valueRange = 0f..240f,
                                    onValueChange = {
                                        limit = it.toInt()
                                        remainingToday = (limit - usedToday).coerceAtLeast(0)
                                    }
                                )
                                Text(
                                    "Heute genutzt: ${usedToday} min  •  Verbleibend: ${remainingToday} min",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(modifier = Modifier.focusScaleOnTv(), onClick = {
                                    scope.launch(Dispatchers.IO) {
                                        db.profileDao().update(kid.copy(name = name, updatedAt = System.currentTimeMillis()))
                                        screenRepo.setDailyLimit(kid.id, limit)
                                        val dayKey = java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.getDefault())
                                            .format(java.util.Calendar.getInstance().time)
                                        val entry = DbProvider.get(ctx).screenTimeDao().getForDay(kid.id, dayKey)
                                        val u = entry?.usedMinutes ?: 0
                                        withContext(Dispatchers.Main) {
                                            usedToday = u
                                            remainingToday = (limit - u).coerceAtLeast(0)
                                        }
                                        load()
                                    }
                                }, colors = ButtonDefaults.buttonColors(containerColor = com.chris.m3usuite.ui.theme.DesignTokens.KidAccent, contentColor = androidx.compose.ui.graphics.Color.Black)) { Text("Speichern") }
                                TextButton(modifier = Modifier.focusScaleOnTv(), onClick = {
                                    scope.launch(Dispatchers.IO) {
                                        db.profileDao().delete(kid)
                                        load()
                                    }
                                }, colors = ButtonDefaults.textButtonColors(contentColor = com.chris.m3usuite.ui.theme.DesignTokens.KidAccent)) { Text("Löschen") }
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }
}
}
