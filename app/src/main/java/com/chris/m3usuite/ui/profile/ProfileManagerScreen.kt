package com.chris.m3usuite.ui.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
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
import com.chris.m3usuite.data.repo.ProfileObxRepository
import com.chris.m3usuite.data.obx.ObxProfile
import com.chris.m3usuite.data.obx.ObxStore
import com.chris.m3usuite.core.util.isAdultCategoryLabel
import com.chris.m3usuite.model.MediaItem
import com.chris.m3usuite.data.obx.ObxKidCategoryAllow_
import com.chris.m3usuite.data.obx.ObxKidContentAllow_
import com.chris.m3usuite.data.obx.ObxKidContentBlock_
import com.chris.m3usuite.data.repo.KidContentRepository
import com.chris.m3usuite.data.repo.ScreenTimeRepository
import com.chris.m3usuite.data.obx.ObxProfilePermissions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import com.chris.m3usuite.ui.home.HomeChromeScaffold
import com.chris.m3usuite.ui.focus.FocusKit
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
import com.chris.m3usuite.ui.focus.focusScaleOnTv
import com.chris.m3usuite.data.obx.ObxKidCategoryAllow
import com.chris.m3usuite.data.obx.ObxKidContentBlock
import com.chris.m3usuite.data.obx.ObxKidContentAllow
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Checkbox
import androidx.compose.foundation.lazy.items
import androidx.compose.animation.core.animateFloat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileManagerScreen(
    onBack: () -> Unit,
    onLogo: (() -> Unit)? = null,
    onGlobalSearch: (() -> Unit)? = null,
    onOpenSettings: (() -> Unit)? = null
) {
    LaunchedEffect(Unit) {
        com.chris.m3usuite.metrics.RouteTag.set("profiles")
        com.chris.m3usuite.core.debug.GlobalDebug.logTree("profiles:root")
    }
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val profileRepo = remember { ProfileObxRepository(ctx) }
    val obx = remember { ObxStore.get(ctx) }
    val screenRepo = remember { ScreenTimeRepository(ctx) }
    val vm: ProfileManagerViewModel = viewModel()
    val kidRepo = remember { KidContentRepository(ctx) }
    var saving by remember { mutableStateOf(false) }
    BackHandler(enabled = saving) { /* block back while saving */ }

    var kids by remember { mutableStateOf<List<ObxProfile>>(emptyList()) }
    var newKidName by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("") }
    var newType by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("kid") } // kid | guest

    suspend fun load() {
        kids = withContext(Dispatchers.IO) { profileRepo.all().filter { it.type != "adult" } }
    }

    LaunchedEffect(Unit) { load() }

    val snack = remember { SnackbarHostState() }
    val listState = com.chris.m3usuite.ui.state.rememberRouteListState("profiles:manager")
    HomeChromeScaffold(
        title = "Profile",
        onSettings = onOpenSettings,
        onSearch = onGlobalSearch,
        onProfiles = null,
        listState = listState,
        onLogo = onLogo,
    ) { pads ->
        Box(Modifier.fillMaxSize().padding(pads)) {
            val Accent = DesignTokens.KidAccent
            Box(Modifier.matchParentSize().background(Brush.verticalGradient(0f to MaterialTheme.colorScheme.background, 1f to MaterialTheme.colorScheme.surface)))
            Box(Modifier.matchParentSize().background(Brush.radialGradient(colors = listOf(Accent.copy(alpha = 0.20f), androidx.compose.ui.graphics.Color.Transparent), radius = with(LocalDensity.current) { 660.dp.toPx() })))
            com.chris.m3usuite.ui.fx.FishBackground(
                modifier = Modifier.align(Alignment.Center).size(540.dp),
                alpha = 0.06f
            )
            Column(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TextButton(modifier = Modifier.focusScaleOnTv(), onClick = onBack) { Text("Zurück") }
                OutlinedTextField(value = newKidName, onValueChange = { newKidName = it }, label = { Text("Neues Profil (Name)") })
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Typ:")
                    FilterChip(
                        modifier = Modifier
                            .graphicsLayer(alpha = com.chris.m3usuite.ui.theme.DesignTokens.BadgeAlpha)
                            .then(FocusKit.run {  Modifier.tvClickable(onClick = { newType = "kid" }, scaleFocused = 1f, scalePressed = 1f, brightenContent = false) }),
                        selected = newType == "kid",
                        onClick = { newType = "kid" },
                        label = { Text("Kind") }
                    )
                    FilterChip(
                        modifier = Modifier
                            .graphicsLayer(alpha = com.chris.m3usuite.ui.theme.DesignTokens.BadgeAlpha)
                            .then(FocusKit.run {  Modifier.tvClickable(onClick = { newType = "guest" }, scaleFocused = 1f, scalePressed = 1f, brightenContent = false) }),
                        selected = newType == "guest",
                        onClick = { newType = "guest" },
                        label = { Text("Gast") }
                    )
                }
                Button(modifier = Modifier.focusScaleOnTv(), onClick = {
                    scope.launch(Dispatchers.IO) {
                        val now = System.currentTimeMillis()
                        profileRepo.insert(ObxProfile(name = newKidName.ifBlank { if (newType == "guest") "Gast" else "Kind" }, type = newType, avatarPath = null, createdAt = now, updatedAt = now))
                        newKidName = ""
                        newType = "kid"
                        load()
                    }
                }, enabled = newKidName.isNotBlank(), colors = ButtonDefaults.buttonColors(containerColor = com.chris.m3usuite.ui.theme.DesignTokens.KidAccent, contentColor = androidx.compose.ui.graphics.Color.Black)) { Text("Anlegen") }

                HorizontalDivider()

                LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                    items(kids, key = { it.id }) { kid ->
                        var name by androidx.compose.runtime.saveable.rememberSaveable(kid.id) { mutableStateOf(kid.name) }
                        var limit by remember(kid.id) { mutableStateOf(60) }
                        var usedToday by remember(kid.id) { mutableStateOf(0) }
                        var remainingToday by remember(kid.id) { mutableStateOf(0) }
                        var avatarPath by remember(kid.id) { mutableStateOf(kid.avatarPath) }

                        // Load today's usage/limit from DB
                        LaunchedEffect(kid.id) {
                            withContext(Dispatchers.IO) {
                                val dayKey = java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.getDefault()).format(java.util.Calendar.getInstance().time)
                                val b = obx.boxFor(com.chris.m3usuite.data.obx.ObxScreenTimeEntry::class.java)
                                val q = b.query(com.chris.m3usuite.data.obx.ObxScreenTimeEntry_.kidProfileId.equal(kid.id).and(com.chris.m3usuite.data.obx.ObxScreenTimeEntry_.dayYyyymmdd.equal(dayKey))).build()
                                val entry = q.findFirst()
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
                                        com.chris.m3usuite.ui.util.AppAsyncImage(
                                            url = model,
                                            contentDescription = null,
                                            modifier = Modifier.size(48.dp).clip(CircleShape),
                                            contentScale = ContentScale.Crop,
                                            crossfade = true,
                                        )
                                    } else {
                                        Icon(painter = androidx.compose.ui.res.painterResource(android.R.drawable.ic_menu_report_image), contentDescription = null)
                                    }
                                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, modifier = Modifier.weight(1f))
                                    AssistChip(modifier = Modifier.graphicsLayer(alpha = com.chris.m3usuite.ui.theme.DesignTokens.BadgeAlpha), onClick = {}, label = { Text(if (kid.type == "guest") "Gast" else "Kind") })
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
                                                // Liste neu laden, damit künftige Recompositionen den DB‑Wert nutzen
                                                scope.launch { withContext(Dispatchers.IO) { load() } }
                                            } else {
                                                scope.launch { snack.showSnackbar("Avatar speichern fehlgeschlagen") }
                                            }
                                        }
                                    }
                                }
                                // Permissions editor (Admin)
                                var showPerms by remember { mutableStateOf(false) }
                                TextButton(modifier = Modifier.focusScaleOnTv(), onClick = { showPerms = true }) { Text("Berechtigungen") }
                                if (showPerms) PermissionsSheet(profile = kid, onClose = { showPerms = false })
                                // Whitelist management (categories + item exceptions)
                                var showManage by remember { mutableStateOf(false) }
                                TextButton(modifier = Modifier.focusScaleOnTv(), onClick = { showManage = true }) { Text("Freigaben verwalten") }
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
                                        TextButton(modifier = Modifier.focusScaleOnTv(), onClick = {
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
                                    TextButton(modifier = Modifier.focusScaleOnTv(), onClick = {
                                        scope.launch(Dispatchers.IO) {
                                            val now = System.currentTimeMillis()
                                            val newType = if (kid.type == "guest") "kid" else "guest"
                                            val b = obx.boxFor(ObxProfile::class.java)
                                            val p = b.get(kid.id) ?: kid
                                            p.type = newType; p.updatedAt = now; b.put(p)
                                            val permBox = obx.boxFor(ObxProfilePermissions::class.java)
                                            val defaults = when (newType) {
                                                "guest" -> ObxProfilePermissions(profileId = kid.id, canOpenSettings = false, canChangeSources = false, canUseExternalPlayer = false, canEditFavorites = false, canSearch = true, canSeeResume = false, canEditWhitelist = false)
                                                else -> ObxProfilePermissions(profileId = kid.id, canOpenSettings = false, canChangeSources = false, canUseExternalPlayer = false, canEditFavorites = false, canSearch = true, canSeeResume = true, canEditWhitelist = false)
                                            }
                                            val ex = permBox.query(com.chris.m3usuite.data.obx.ObxProfilePermissions_.profileId.equal(kid.id)).build().findFirst()
                                            if (ex != null) defaults.id = ex.id
                                            permBox.put(defaults)
                                            withContext(Dispatchers.Main) { load() }
                                        }
                                    }) { Text(if (kid.type == "guest") "Zu Kind wechseln" else "Zu Gast wechseln") }
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(modifier = Modifier.focusScaleOnTv(), onClick = {
                                        scope.launch(Dispatchers.IO) {
                                            val b = obx.boxFor(ObxProfile::class.java)
                                            val p = b.get(kid.id)
                                            if (p != null) { p.name = name; p.updatedAt = System.currentTimeMillis(); b.put(p) }
                                            screenRepo.setDailyLimit(kid.id, limit)
                                            val dayKey = java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.getDefault())
                                                .format(java.util.Calendar.getInstance().time)
                                            val entry = obx.boxFor(com.chris.m3usuite.data.obx.ObxScreenTimeEntry::class.java).query(com.chris.m3usuite.data.obx.ObxScreenTimeEntry_.kidProfileId.equal(kid.id).and(com.chris.m3usuite.data.obx.ObxScreenTimeEntry_.dayYyyymmdd.equal(dayKey))).build().findFirst()
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
                                            val allowBox = obx.boxFor(ObxKidContentAllow::class.java)
                                            allowBox.remove(allowBox.query(ObxKidContentAllow_.kidProfileId.equal(kid.id)).build().find())
                                            val catBox = obx.boxFor(ObxKidCategoryAllow::class.java)
                                            catBox.remove(catBox.query(ObxKidCategoryAllow_.kidProfileId.equal(kid.id)).build().find())
                                            val blockBox = obx.boxFor(ObxKidContentBlock::class.java)
                                            blockBox.remove(blockBox.query(ObxKidContentBlock_.kidProfileId.equal(kid.id)).build().find())
                                            val permBox = obx.boxFor(ObxProfilePermissions::class.java)
                                            permBox.query(com.chris.m3usuite.data.obx.ObxProfilePermissions_.profileId.equal(kid.id)).build().findFirst()?.let { permBox.remove(it) }
                                            obx.boxFor(ObxProfile::class.java).remove(kid)
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
                                        val allowBox = obx.boxFor(ObxKidContentAllow::class.java)
                                        val liveIds = allowBox.query(ObxKidContentAllow_.kidProfileId.equal(kid.id).and(ObxKidContentAllow_.contentType.equal("live"))).build().find().map { it.contentId }
                                        val vodIds = allowBox.query(ObxKidContentAllow_.kidProfileId.equal(kid.id).and(ObxKidContentAllow_.contentType.equal("vod"))).build().find().map { it.contentId }
                                        val serIds = allowBox.query(ObxKidContentAllow_.kidProfileId.equal(kid.id).and(ObxKidContentAllow_.contentType.equal("series"))).build().find().map { it.contentId }
                                        val repo = com.chris.m3usuite.data.repo.MediaQueryRepository(ctx, com.chris.m3usuite.prefs.SettingsStore(ctx))
                                        val liveList = repo.listByTypeFiltered("live", 6000, 0).filter { it.id in liveIds }
                                        val vodList = repo.listByTypeFiltered("vod", 6000, 0).filter { it.id in vodIds }
                                        val serList = repo.listByTypeFiltered("series", 6000, 0).filter { it.id in serIds }
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
                                    TextButton(modifier = Modifier.focusScaleOnTv(), onClick = { scope.launch { if (!expanded) loadWhitelist(); expanded = !expanded } }) { Text(if (expanded) "Schließen" else "Anzeigen") }
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
                                                    TextButton(modifier = Modifier.focusScaleOnTv(), onClick = { scope.launch { kidRepo.disallow(kid.id, "live", mi.id); loadWhitelist() } }) { Text("Entfernen") }
                                                }
                                            }
                                        }
                                        if (vod.isNotEmpty()) {
                                            Spacer(Modifier.height(6.dp)); Text("Filme", style = MaterialTheme.typography.labelLarge)
                                            vod.forEach { mi ->
                                                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                                    Text(mi.name, modifier = Modifier.weight(1f))
                                                    TextButton(modifier = Modifier.focusScaleOnTv(), onClick = { scope.launch { kidRepo.disallow(kid.id, "vod", mi.id); loadWhitelist() } }) { Text("Entfernen") }
                                                }
                                            }
                                        }
                                        if (series.isNotEmpty()) {
                                            Spacer(Modifier.height(6.dp)); Text("Serien", style = MaterialTheme.typography.labelLarge)
                                            series.forEach { mi ->
                                                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                                    Text(mi.name, modifier = Modifier.weight(1f))
                                                    TextButton(modifier = Modifier.focusScaleOnTv(), onClick = { scope.launch { kidRepo.disallow(kid.id, "series", mi.id); loadWhitelist() } }) { Text("Entfernen") }
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
    val profileRepo = remember { ProfileObxRepository(ctx) }
    val obx = remember { ObxStore.get(ctx) }
    val scope = rememberCoroutineScope()
    var tab by remember { mutableStateOf(0) } // 0 live, 1 vod, 2 series
    val types = listOf("live", "vod", "series")
    val titles = listOf("TV", "Filme", "Serien")
    ModalBottomSheet(onDismissRequest = onClose) {
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(max = 520.dp)

                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                titles.forEachIndexed { i, t ->
                    FilterChip(
                        modifier = Modifier
                            .graphicsLayer(alpha = com.chris.m3usuite.ui.theme.DesignTokens.BadgeAlpha)
                            .then(FocusKit.run {  Modifier.tvClickable(onClick = { tab = i }, scaleFocused = 1f, scalePressed = 1f, brightenContent = false) }),
                        selected = tab == i,
                        onClick = { tab = i },
                        label = { Text(t) }
                    )
                }
            }
            val type = types[tab]
            var categories by remember(tab) { mutableStateOf<List<String>>(emptyList()) }
            var allowedCats by remember(tab) { mutableStateOf<Set<String>>(emptySet()) }
            var expanded by remember { mutableStateOf<String?>(null) }
            LaunchedEffect(tab) {
                val result = withContext(Dispatchers.IO) {
                    val obxRepo = com.chris.m3usuite.data.repo.XtreamObxRepository(ctx, com.chris.m3usuite.prefs.SettingsStore(ctx))
                    val cats = obxRepo.categories(type)
                        .mapNotNull { it.categoryName }
                        .filterNot { label -> isAdultCategoryLabel(label) }
                        .distinct()
                    val allowBox = obx.boxFor(ObxKidCategoryAllow::class.java)
                    val allowed = allowBox.query(ObxKidCategoryAllow_.kidProfileId.equal(kidId).and(ObxKidCategoryAllow_.contentType.equal(type))).build().find().map { it.categoryId }.toSet()
                    cats to allowed
                }
                categories = result.first
                allowedCats = result.second
            }
            // Quick actions for current tab (after state init)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(modifier = Modifier.focusScaleOnTv(), onClick = {
                    scope.launch(Dispatchers.IO) {
                        val obxRepo = com.chris.m3usuite.data.repo.XtreamObxRepository(ctx, com.chris.m3usuite.prefs.SettingsStore(ctx))
                        val catsAll = obxRepo.categories(type)
                            .mapNotNull { it.categoryName }
                            .filterNot { label -> isAdultCategoryLabel(label) }
                            .distinct()
                        val catBox = obx.boxFor(ObxKidCategoryAllow::class.java)
                        catsAll.forEach { c -> catBox.put(ObxKidCategoryAllow(kidProfileId = kidId, contentType = type, categoryId = c)) }
                        val allowed = catBox.query(ObxKidCategoryAllow_.kidProfileId.equal(kidId).and(ObxKidCategoryAllow_.contentType.equal(type))).build().find().map { it.categoryId }.toSet()
                        withContext(Dispatchers.Main) { allowedCats = allowed }
                    }
                }) { Text("Alle Kategorien erlauben") }
                TextButton(modifier = Modifier.focusScaleOnTv(), onClick = {
                    scope.launch(Dispatchers.IO) {
                        val catBox = obx.boxFor(ObxKidCategoryAllow::class.java)
                        catBox.remove(catBox.query(ObxKidCategoryAllow_.kidProfileId.equal(kidId).and(ObxKidCategoryAllow_.contentType.equal(type))).build().find())
                        val allowBox = obx.boxFor(ObxKidContentAllow::class.java)
                        allowBox.remove(allowBox.query(ObxKidContentAllow_.kidProfileId.equal(kidId).and(ObxKidContentAllow_.contentType.equal(type))).build().find())
                        val blockBox = obx.boxFor(ObxKidContentBlock::class.java)
                        blockBox.remove(blockBox.query(ObxKidContentBlock_.kidProfileId.equal(kidId).and(ObxKidContentBlock_.contentType.equal(type))).build().find())
                        withContext(Dispatchers.Main) { allowedCats = emptySet(); expanded = null }
                    }
                }) { Text("Whitelist leeren") }
            }
            // Categories as badges with checkbox
            LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(bottom = 40.dp)) {
                items(categories, key = { it }) { cat ->
                    val allowed = cat in allowedCats
                    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                        Row(horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            AssistChip(
                                modifier = Modifier
                                    .graphicsLayer(alpha = com.chris.m3usuite.ui.theme.DesignTokens.BadgeAlpha)
                                    .then(FocusKit.run {  Modifier.tvClickable(onClick = { expanded = if (expanded == cat) null else cat }, scaleFocused = 1f, scalePressed = 1f, brightenContent = false) }),
                                onClick = { expanded = if (expanded == cat) null else cat },
                                label = { Text(cat) }
                            )
                            Switch(checked = allowed, onCheckedChange = { v ->
                                scope.launch(Dispatchers.IO) {
                                    val catBox = obx.boxFor(ObxKidCategoryAllow::class.java)
                                    if (v) catBox.put(ObxKidCategoryAllow(kidProfileId = kidId, contentType = type, categoryId = cat))
                                    else catBox.remove(catBox.query(ObxKidCategoryAllow_.kidProfileId.equal(kidId).and(ObxKidCategoryAllow_.contentType.equal(type)).and(ObxKidCategoryAllow_.categoryId.equal(cat))).build().find())
                                    val allowedNow = catBox.query(ObxKidCategoryAllow_.kidProfileId.equal(kidId).and(ObxKidCategoryAllow_.contentType.equal(type))).build().find().map { it.categoryId }.toSet()
                                    withContext(Dispatchers.Main) { allowedCats = allowedNow }
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
                                    val repo = com.chris.m3usuite.data.repo.MediaQueryRepository(ctx, com.chris.m3usuite.prefs.SettingsStore(ctx))
                                    val lst = repo.byTypeAndCategoryFiltered(type, cat)
                                    val blockBox = obx.boxFor(ObxKidContentBlock::class.java)
                                    val allowBox = obx.boxFor(ObxKidContentAllow::class.java)
                                    val bl = blockBox.query(ObxKidContentBlock_.kidProfileId.equal(kidId).and(ObxKidContentBlock_.contentType.equal(type))).build().find().map { it.contentId }.toSet()
                                    val al = allowBox.query(ObxKidContentAllow_.kidProfileId.equal(kidId).and(ObxKidContentAllow_.contentType.equal(type))).build().find().map { it.contentId }.toSet()
                                    withContext(Dispatchers.Main) { items = lst; blocked = bl; allowedItems = al }
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
                                                val blockBox = obx.boxFor(ObxKidContentBlock::class.java)
                                                if (v) blockBox.remove(blockBox.query(ObxKidContentBlock_.kidProfileId.equal(kidId).and(ObxKidContentBlock_.contentType.equal(type)).and(ObxKidContentBlock_.contentId.equal(id))).build().find())
                                                else blockBox.put(ObxKidContentBlock(kidProfileId = kidId, contentType = type, contentId = id))
                                                val blNow = blockBox.query(ObxKidContentBlock_.kidProfileId.equal(kidId).and(ObxKidContentBlock_.contentType.equal(type))).build().find().map { it.contentId }.toSet()
                                                withContext(Dispatchers.Main) { blocked = blNow }
                                            } else {
                                                val allowBox = obx.boxFor(ObxKidContentAllow::class.java)
                                                if (v) allowBox.put(ObxKidContentAllow(kidProfileId = kidId, contentType = type, contentId = id)) else allowBox.remove(allowBox.query(ObxKidContentAllow_.kidProfileId.equal(kidId).and(ObxKidContentAllow_.contentType.equal(type)).and(ObxKidContentAllow_.contentId.equal(id))).build().find())
                                                val alNow = allowBox.query(ObxKidContentAllow_.kidProfileId.equal(kidId).and(ObxKidContentAllow_.contentType.equal(type))).build().find().map { it.contentId }.toSet()
                                                withContext(Dispatchers.Main) { allowedItems = alNow }
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
                        TextButton(modifier = Modifier.focusScaleOnTv(), onClick = onClose) { Text("Schließen") }
                    }
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun PermissionsSheet(profile: ObxProfile, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val obx = remember { ObxStore.get(ctx) }
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(true) }
    var state by remember {
        mutableStateOf(
            ObxProfilePermissions(
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
            val row = obx.boxFor(ObxProfilePermissions::class.java).query(com.chris.m3usuite.data.obx.ObxProfilePermissions_.profileId.equal(profile.id)).build().findFirst()
            withContext(Dispatchers.Main) {
                if (row != null) state = row
                loading = false
            }
        }
    }
    ModalBottomSheet(onDismissRequest = onClose) {
        val scrollState = rememberScrollState()
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
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
                    TextButton(modifier = Modifier.focusScaleOnTv(), onClick = onClose) { Text("Abbrechen") }
                    Spacer(Modifier.width(8.dp))
                    Button(modifier = Modifier.focusScaleOnTv(), onClick = {
                        scope.launch(Dispatchers.IO) {
                            val box = obx.boxFor(ObxProfilePermissions::class.java)
                            val existing = box.query(com.chris.m3usuite.data.obx.ObxProfilePermissions_.profileId.equal(profile.id)).build().findFirst()
                            if (existing != null) state = state.copy(id = existing.id)
                            box.put(state)
                            withContext(Dispatchers.Main) { onClose() }
                        }
                    }) { Text("Speichern") }
                }
            }
        }
    }
}
