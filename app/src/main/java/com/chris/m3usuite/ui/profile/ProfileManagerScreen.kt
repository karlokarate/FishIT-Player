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
import com.chris.m3usuite.data.db.MediaItem
import com.chris.m3usuite.data.repo.KidContentRepository
import com.chris.m3usuite.data.repo.ScreenTimeRepository
import com.chris.m3usuite.data.db.ProfilePermissions
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
import com.chris.m3usuite.data.db.KidCategoryAllow
import com.chris.m3usuite.data.db.KidContentBlock
import com.chris.m3usuite.data.db.KidContentItem
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Checkbox
import androidx.compose.foundation.lazy.items

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileManagerScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val db = remember { DbProvider.get(ctx) }
    val scope = rememberCoroutineScope()
    val screenRepo = remember { ScreenTimeRepository(ctx) }
    val vm: ProfileManagerViewModel = viewModel()
    val kidRepo = remember { KidContentRepository(ctx) }
    var saving by remember { mutableStateOf(false) }
    BackHandler(enabled = saving) { /* block back while saving */ }

    var kids by remember { mutableStateOf<List<Profile>>(emptyList()) }
    var newKidName by remember { mutableStateOf("") }
    var newType by remember { mutableStateOf("kid") } // kid | guest

    suspend fun load() {
        kids = withContext(Dispatchers.IO) { db.profileDao().all().filter { it.type != "adult" } }
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
            OutlinedTextField(value = newKidName, onValueChange = { newKidName = it }, label = { Text("Neues Profil (Name)") })
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Typ:")
                FilterChip(selected = newType == "kid", onClick = { newType = "kid" }, label = { Text("Kind") })
                FilterChip(selected = newType == "guest", onClick = { newType = "guest" }, label = { Text("Gast") })
            }
            Button(modifier = Modifier.focusScaleOnTv(), onClick = {
                scope.launch(Dispatchers.IO) {
                    val now = System.currentTimeMillis()
                    db.profileDao().insert(Profile(name = newKidName.ifBlank { if (newType == "guest") "Gast" else "Kind" }, type = newType, avatarPath = null, createdAt = now, updatedAt = now))
                    newKidName = ""
                    newType = "kid"
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
                                AssistChip(onClick = {}, label = { Text(if (kid.type == "guest") "Gast" else "Kind") })
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
                            // Permissions editor (Admin)
                            var showPerms by remember { mutableStateOf(false) }
                            TextButton(onClick = { showPerms = true }) { Text("Berechtigungen") }
                            if (showPerms) PermissionsSheet(profile = kid, onClose = { showPerms = false })
                            // Whitelist management (categories + item exceptions)
                            var showManage by remember { mutableStateOf(false) }
                            TextButton(onClick = { showManage = true }) { Text("Freigaben verwalten") }
                            if (showManage) {
                                ManageWhitelistSheet(kid.id, onClose = { showManage = false })
                            }
                            var limitActive by remember(kid.id) { mutableStateOf(false) }
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("Zeitlimit aktiv", modifier = Modifier.weight(1f))
                                    Switch(checked = limitActive, onCheckedChange = { v ->
                                        limitActive = v
                                        scope.launch(Dispatchers.IO) { screenRepo.setDailyLimit(kid.id, if (v) limit else 0) }
                                    })
                                }
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
                                    },
                                    onValueChangeFinished = {
                                        if (limitActive) scope.launch(Dispatchers.IO) { screenRepo.setDailyLimit(kid.id, limit) }
                                    },
                                    enabled = limitActive
                                )
                                Row(horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        "Heute genutzt: ${usedToday} min  •  Verbleibend: ${remainingToday} min",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.secondary,
                                        modifier = Modifier.weight(1f)
                                    )
                                    TextButton(onClick = {
                                        scope.launch {
                                            screenRepo.resetToday(kid.id)
                                            usedToday = 0
                                            remainingToday = (limit - usedToday).coerceAtLeast(0)
                                        }
                                    }) { Text("Heute zurücksetzen") }
                                }
                            }
                            // Typ wechseln (Kind ↔ Gast)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                TextButton(onClick = {
                                    scope.launch(Dispatchers.IO) {
                                        val now = System.currentTimeMillis()
                                        val newType = if (kid.type == "guest") "kid" else "guest"
                                        db.profileDao().update(kid.copy(type = newType, updatedAt = now))
                                        // Default-Rechte für neuen Typ setzen
                                        val ppDao = db.profilePermissionsDao()
                                        val defaults = when (newType) {
                                            "guest" -> com.chris.m3usuite.data.db.ProfilePermissions(
                                                profileId = kid.id,
                                                canOpenSettings = false,
                                                canChangeSources = false,
                                                canUseExternalPlayer = false,
                                                canEditFavorites = false,
                                                canSearch = true,
                                                canSeeResume = false,
                                                canEditWhitelist = false
                                            )
                                            else -> com.chris.m3usuite.data.db.ProfilePermissions(
                                                profileId = kid.id,
                                                canOpenSettings = false,
                                                canChangeSources = false,
                                                canUseExternalPlayer = false,
                                                canEditFavorites = false,
                                                canSearch = true,
                                                canSeeResume = true,
                                                canEditWhitelist = false
                                            )
                                        }
                                        ppDao.upsert(defaults)
                                        withContext(Dispatchers.Main) { load() }
                                    }
                                }) { Text(if (kid.type == "guest") "Zu Kind wechseln" else "Zu Gast wechseln") }
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
                                        // Orphan-Cleanup: remove whitelist/blocks/permissions for this profile
                                        try {
                                            db.kidContentDao().listForKidAndType(kid.id, "live").forEach { db.kidContentDao().disallow(kid.id, "live", it.contentId) }
                                            db.kidContentDao().listForKidAndType(kid.id, "vod").forEach { db.kidContentDao().disallow(kid.id, "vod", it.contentId) }
                                            db.kidContentDao().listForKidAndType(kid.id, "series").forEach { db.kidContentDao().disallow(kid.id, "series", it.contentId) }
                                        } catch (_: Throwable) {}
                                        try {
                                            db.kidCategoryAllowDao().listForKidAndType(kid.id, "live").forEach { db.kidCategoryAllowDao().disallow(kid.id, "live", it.categoryId) }
                                            db.kidCategoryAllowDao().listForKidAndType(kid.id, "vod").forEach { db.kidCategoryAllowDao().disallow(kid.id, "vod", it.categoryId) }
                                            db.kidCategoryAllowDao().listForKidAndType(kid.id, "series").forEach { db.kidCategoryAllowDao().disallow(kid.id, "series", it.categoryId) }
                                        } catch (_: Throwable) {}
                                        try {
                                            db.kidContentBlockDao().listForKidAndType(kid.id, "live").forEach { db.kidContentBlockDao().unblock(kid.id, "live", it.contentId) }
                                            db.kidContentBlockDao().listForKidAndType(kid.id, "vod").forEach { db.kidContentBlockDao().unblock(kid.id, "vod", it.contentId) }
                                            db.kidContentBlockDao().listForKidAndType(kid.id, "series").forEach { db.kidContentBlockDao().unblock(kid.id, "series", it.contentId) }
                                        } catch (_: Throwable) {}
                                        try { db.profilePermissionsDao().deleteByProfile(kid.id) } catch (_: Throwable) {}
                                        db.profileDao().delete(kid)
                                        load()
                                    }
                                }, colors = ButtonDefaults.textButtonColors(contentColor = com.chris.m3usuite.ui.theme.DesignTokens.KidAccent)) { Text("Löschen") }
                            }

                            // Freigaben (Whitelist) – ausklappbar
                            var expanded by remember(kid.id) { mutableStateOf(false) }
                            var loading by remember(kid.id) { mutableStateOf(false) }
                            var live by remember(kid.id) { mutableStateOf<List<MediaItem>>(emptyList()) }
                            var vod by remember(kid.id) { mutableStateOf<List<MediaItem>>(emptyList()) }
                            var series by remember(kid.id) { mutableStateOf<List<MediaItem>>(emptyList()) }

                            suspend fun loadWhitelist() {
                                loading = true
                                withContext(Dispatchers.IO) {
                                    val dao = db.kidContentDao()
                                    val mediaDao = db.mediaDao()
                                    val liveIds = dao.listForKidAndType(kid.id, "live").map { it.contentId }
                                    val vodIds = dao.listForKidAndType(kid.id, "vod").map { it.contentId }
                                    val serIds = dao.listForKidAndType(kid.id, "series").map { it.contentId }
                                    val liveList = if (liveIds.isNotEmpty()) mediaDao.byIds(liveIds) else emptyList()
                                    val vodList = if (vodIds.isNotEmpty()) mediaDao.byIds(vodIds) else emptyList()
                                    val serList = if (serIds.isNotEmpty()) mediaDao.byIds(serIds) else emptyList()
                                    withContext(Dispatchers.Main) {
                                        live = liveList
                                        vod = vodList
                                        series = serList
                                        loading = false
                                    }
                                }
                            }

                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                Text("Freigaben (${live.size + vod.size + series.size})", style = MaterialTheme.typography.titleMedium)
                                TextButton(onClick = { scope.launch { if (!expanded) loadWhitelist(); expanded = !expanded } }) { Text(if (expanded) "Schließen" else "Anzeigen") }
                            }
                            if (expanded) {
                                if (loading) {
                                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                                } else {
                                    if (live.isNotEmpty()) {
                                        Text("TV", style = MaterialTheme.typography.labelLarge)
                                        live.forEach { mi ->
                                            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                                Text(mi.name, modifier = Modifier.weight(1f))
                                                TextButton(onClick = { scope.launch { kidRepo.disallow(kid.id, "live", mi.id); loadWhitelist() } }) { Text("Entfernen") }
                                            }
                                        }
                                    }
                                    if (vod.isNotEmpty()) {
                                        Spacer(Modifier.height(6.dp)); Text("Filme", style = MaterialTheme.typography.labelLarge)
                                        vod.forEach { mi ->
                                            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                                Text(mi.name, modifier = Modifier.weight(1f))
                                                TextButton(onClick = { scope.launch { kidRepo.disallow(kid.id, "vod", mi.id); loadWhitelist() } }) { Text("Entfernen") }
                                            }
                                        }
                                    }
                                    if (series.isNotEmpty()) {
                                        Spacer(Modifier.height(6.dp)); Text("Serien", style = MaterialTheme.typography.labelLarge)
                                        series.forEach { mi ->
                                            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                                Text(mi.name, modifier = Modifier.weight(1f))
                                                TextButton(onClick = { scope.launch { kidRepo.disallow(kid.id, "series", mi.id); loadWhitelist() } }) { Text("Entfernen") }
                                            }
                                        }
                                    }
                                    if (live.isEmpty() && vod.isEmpty() && series.isEmpty()) {
                                        Text("Keine Freigaben", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                                    }
                                }
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

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun ManageWhitelistSheet(kidId: Long, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val db = remember { DbProvider.get(ctx) }
    val scope = rememberCoroutineScope()
    var tab by remember { mutableStateOf(0) } // 0 live, 1 vod, 2 series
    val types = listOf("live", "vod", "series")
    val titles = listOf("TV", "Filme", "Serien")
    ModalBottomSheet(onDismissRequest = onClose) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                titles.forEachIndexed { i, t ->
                    FilterChip(selected = tab == i, onClick = { tab = i }, label = { Text(t) })
                }
            }
            val type = types[tab]
            var categories by remember(tab) { mutableStateOf<List<String>>(emptyList()) }
            var allowedCats by remember(tab) { mutableStateOf<Set<String>>(emptySet()) }
            var expanded by remember { mutableStateOf<String?>(null) }
            LaunchedEffect(tab) {
                withContext(Dispatchers.IO) {
                    categories = db.mediaDao().categoriesByType(type).mapNotNull { it }.distinct()
                    allowedCats = db.kidCategoryAllowDao().listForKidAndType(kidId, type).map { it.categoryId }.toSet()
                }
            }
            // Quick actions for current tab (after state init)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = {
                    scope.launch(Dispatchers.IO) {
                        val catsAll = db.mediaDao().categoriesByType(type).mapNotNull { it }.distinct()
                        catsAll.forEach { c -> db.kidCategoryAllowDao().insert(KidCategoryAllow(kidProfileId = kidId, contentType = type, categoryId = c)) }
                        allowedCats = db.kidCategoryAllowDao().listForKidAndType(kidId, type).map { it.categoryId }.toSet()
                    }
                }) { Text("Alle Kategorien erlauben") }
                TextButton(onClick = {
                    scope.launch(Dispatchers.IO) {
                        db.kidCategoryAllowDao().listForKidAndType(kidId, type).forEach { db.kidCategoryAllowDao().disallow(kidId, type, it.categoryId) }
                        db.kidContentDao().listForKidAndType(kidId, type).forEach { db.kidContentDao().disallow(kidId, type, it.contentId) }
                        db.kidContentBlockDao().listForKidAndType(kidId, type).forEach { db.kidContentBlockDao().unblock(kidId, type, it.contentId) }
                        withContext(Dispatchers.Main) { allowedCats = emptySet(); expanded = null }
                    }
                }) { Text("Whitelist leeren") }
            }
            // Categories as badges with checkbox
            LazyColumn(contentPadding = PaddingValues(bottom = 40.dp)) {
                items(categories, key = { it }) { cat ->
                    val allowed = cat in allowedCats
                    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                        Row(horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            AssistChip(onClick = { expanded = if (expanded == cat) null else cat }, label = { Text(cat) })
                            Switch(checked = allowed, onCheckedChange = { v ->
                                scope.launch(Dispatchers.IO) {
                                    if (v) db.kidCategoryAllowDao().insert(KidCategoryAllow(kidProfileId = kidId, contentType = type, categoryId = cat))
                                    else db.kidCategoryAllowDao().disallow(kidId, type, cat)
                                    allowedCats = db.kidCategoryAllowDao().listForKidAndType(kidId, type).map { it.categoryId }.toSet()
                                }
                            })
                        }
                        if (expanded == cat) {
                            // Item-level exceptions (lazy load)
                            var items by remember(cat) { mutableStateOf<List<MediaItem>>(emptyList()) }
                            var blocked by remember(cat) { mutableStateOf<Set<Long>>(emptySet()) }
                            var allowedItems by remember(cat) { mutableStateOf<Set<Long>>(emptySet()) }
                            LaunchedEffect(cat) {
                                withContext(Dispatchers.IO) {
                                    items = db.mediaDao().byTypeAndCategory(type, cat)
                                    blocked = db.kidContentBlockDao().listForKidAndType(kidId, type).map { it.contentId }.toSet()
                                    allowedItems = db.kidContentDao().listForKidAndType(kidId, type).map { it.contentId }.toSet()
                                }
                            }
                            // If category is allowed: checking an item removes block; unchecking adds block.
                            // If category is not allowed: checking an item adds explicit allow; unchecking removes allow.
                            items.forEach { mi ->
                                val id = mi.id
                                val checked = if (allowed) id !in blocked else id in allowedItems
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Text(mi.name, modifier = Modifier.weight(1f), maxLines = 1)
                                    Checkbox(checked = checked, onCheckedChange = { v ->
                                        scope.launch(Dispatchers.IO) {
                                            if (allowed) {
                                                if (v) db.kidContentBlockDao().unblock(kidId, type, id) else db.kidContentBlockDao().insert(KidContentBlock(kidProfileId = kidId, contentType = type, contentId = id))
                                                blocked = db.kidContentBlockDao().listForKidAndType(kidId, type).map { it.contentId }.toSet()
                                            } else {
                                                if (v) db.kidContentDao().insert(KidContentItem(kidProfileId = kidId, contentType = type, contentId = id)) else db.kidContentDao().disallow(kidId, type, id)
                                                allowedItems = db.kidContentDao().listForKidAndType(kidId, type).map { it.contentId }.toSet()
                                            }
                                        }
                                    })
                                }
                            }
                        }
                    }
                }
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = onClose) { Text("Schließen") }
                    }
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun PermissionsSheet(profile: Profile, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val db = remember { DbProvider.get(ctx) }
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(true) }
    var state by remember {
        mutableStateOf(
            ProfilePermissions(
                profileId = profile.id,
                canOpenSettings = profile.type == "adult",
                canChangeSources = profile.type == "adult",
                canUseExternalPlayer = profile.type == "adult",
                canEditFavorites = profile.type == "adult",
                canSearch = true,
                canSeeResume = profile.type != "guest",
                canEditWhitelist = profile.type == "adult"
            )
        )
    }
    LaunchedEffect(profile.id) {
        withContext(Dispatchers.IO) {
            val row = db.profilePermissionsDao().byProfile(profile.id)
            withContext(Dispatchers.Main) {
                if (row != null) state = row
                loading = false
            }
        }
    }
    ModalBottomSheet(onDismissRequest = onClose) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Berechtigungen – ${profile.name}", style = MaterialTheme.typography.titleMedium)
            if (loading) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            } else {
                @Composable
                fun row(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(label, modifier = Modifier.weight(1f))
                        Switch(checked = checked, onCheckedChange = onChange)
                    }
                }
                row("Einstellungen öffnen", state.canOpenSettings) { v -> state = state.copy(canOpenSettings = v) }
                row("Quellen ändern (M3U/Xtream)", state.canChangeSources) { v -> state = state.copy(canChangeSources = v) }
                row("Externen Player nutzen", state.canUseExternalPlayer) { v -> state = state.copy(canUseExternalPlayer = v) }
                row("Favoriten bearbeiten", state.canEditFavorites) { v -> state = state.copy(canEditFavorites = v) }
                row("Suche erlauben", state.canSearch) { v -> state = state.copy(canSearch = v) }
                row("Weiter schauen anzeigen", state.canSeeResume) { v -> state = state.copy(canSeeResume = v) }
                row("Whitelist bearbeiten", state.canEditWhitelist) { v -> state = state.copy(canEditWhitelist = v) }

                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onClose) { Text("Abbrechen") }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = {
                        scope.launch(Dispatchers.IO) {
                            db.profilePermissionsDao().upsert(state)
                            withContext(Dispatchers.Main) { onClose() }
                        }
                    }) { Text("Speichern") }
                }
            }
        }
    }
}
