package com.chris.m3usuite.ui.auth

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.Alignment
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloat
import com.chris.m3usuite.ui.theme.DesignTokens
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.chris.m3usuite.data.repo.ProfileObxRepository
import com.chris.m3usuite.data.obx.ObxProfile
import com.chris.m3usuite.data.obx.ObxStore
import com.chris.m3usuite.prefs.SettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import java.security.MessageDigest
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.chris.m3usuite.ui.util.rememberAvatarModel
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.ContentScale
import com.chris.m3usuite.ui.skin.tvClickable
import com.chris.m3usuite.ui.auth.CreateProfileSheet
import com.chris.m3usuite.ui.skin.focusScaleOnTv
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester

@Composable
fun ProfileGate(
    onEnter: () -> Unit,
) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val store = remember { SettingsStore(ctx) }
    val profileRepo = remember { ProfileObxRepository(ctx) }
    val scope = rememberCoroutineScope()

    var adult by remember { mutableStateOf<ObxProfile?>(null) }
    var kids by remember { mutableStateOf<List<ObxProfile>>(emptyList()) }

    // PIN UI state (persist across rotation)
    var showPin by rememberSaveable { mutableStateOf(false) }
    var setPin by rememberSaveable { mutableStateOf(false) }
    var pin by rememberSaveable { mutableStateOf("") }
    var pin2 by rememberSaveable { mutableStateOf("") }
    var pinError by rememberSaveable { mutableStateOf<String?>(null) }
    val pinSet by store.adultPinSet.collectAsState(initial = false)

    LaunchedEffect(Unit) {
        val list = withContext(Dispatchers.IO) { profileRepo.all() }
        adult = list.firstOrNull { it.type == "adult" }
        kids = list.filter { it.type != "adult" } // show Kid + Guest
        // Skip only if user opted in to remember last profile
        val remember = store.rememberLastProfile.first()
        val cur = store.currentProfileId.first()
        if (remember && cur > 0) onEnter()
    }

    fun sha256(s: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val dig = md.digest(s.toByteArray())
        return dig.joinToString("") { b -> "%02x".format(b) }
    }

    @Composable
    fun PinDialog(onDismiss: () -> Unit) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(if (setPin) "PIN festlegen" else "PIN eingeben") },
            text = {
                Column {
                    OutlinedTextField(value = pin, onValueChange = { pin = it }, label = { Text("PIN") }, singleLine = true, visualTransformation = PasswordVisualTransformation())
                    if (setPin) {
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(value = pin2, onValueChange = { pin2 = it }, label = { Text("PIN wiederholen") }, singleLine = true, visualTransformation = PasswordVisualTransformation())
                    }
                    if (pinError != null) {
                        Text(pinError!!, color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            confirmButton = {
                TextButton(modifier = Modifier.focusScaleOnTv(), onClick = {
                    if (setPin) {
                        if (pin.isBlank() || pin != pin2) { pinError = "PINs stimmen nicht"; return@TextButton }
                        val h = sha256(pin)
                        scope.launch { 
                            store.setAdultPinHash(h); store.setAdultPinSet(true)
                            // ensure we have an adult profile; create if missing
                            val existing = withContext(Dispatchers.IO) { profileRepo.all().firstOrNull { it.type == "adult" } }
                            if (existing != null) {
                                store.setCurrentProfileId(existing.id)
                            } else {
                                val now = System.currentTimeMillis()
                                val p = ObxProfile(name = "Erwachsen", type = "adult", avatarPath = null, createdAt = now, updatedAt = now)
                                val newId = withContext(Dispatchers.IO) { profileRepo.insert(p) }
                                store.setCurrentProfileId(newId)
                            }
                            pin = ""; pin2 = ""; showPin = false; onEnter() 
                        }
                    } else {
                        scope.launch {
                            val ok = sha256(pin) == store.adultPinHash.first()
                            if (ok) {
                                // ensure adult profile exists and select it
                                val existing = withContext(Dispatchers.IO) { profileRepo.all().firstOrNull { it.type == "adult" } }
                                if (existing != null) {
                                    store.setCurrentProfileId(existing.id)
                                } else {
                                    val now = System.currentTimeMillis()
                                    val p = ObxProfile(name = "Erwachsen", type = "adult", avatarPath = null, createdAt = now, updatedAt = now)
                                    val newId = withContext(Dispatchers.IO) { profileRepo.insert(p) }
                                    store.setCurrentProfileId(newId)
                                }
                                pin = ""; showPin = false; onEnter() 
                            } else pinError = "Falscher PIN"
                        }
                    }
                }, colors = ButtonDefaults.textButtonColors(contentColor = com.chris.m3usuite.ui.theme.DesignTokens.KidAccent)) { Text("OK") }
            },
            dismissButton = { TextButton(modifier = Modifier.focusScaleOnTv(), onClick = onDismiss, colors = ButtonDefaults.textButtonColors(contentColor = com.chris.m3usuite.ui.theme.DesignTokens.KidAccent)) { Text("Abbrechen") } }
        )
    }

    var showCreate by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        val Accent = DesignTokens.KidAccent
        Box(Modifier.matchParentSize().background(Brush.verticalGradient(0f to MaterialTheme.colorScheme.background, 1f to MaterialTheme.colorScheme.surface)))
        Box(Modifier.matchParentSize().background(Brush.radialGradient(colors = listOf(Accent.copy(alpha = 0.24f), androidx.compose.ui.graphics.Color.Transparent), radius = with(LocalDensity.current) { 640.dp.toPx() })))
        run {
            com.chris.m3usuite.ui.fx.FishBackground(
                modifier = Modifier.align(Alignment.Center).size(540.dp),
                alpha = 0.06f
            )
        }
        Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Wer bist du?", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(12.dp))
        // Adult
        val adultFocus = remember { FocusRequester() }
        LaunchedEffect(Unit) { adultFocus.requestFocus() }
        OutlinedCard(
            Modifier
                .fillMaxWidth()
                .focusRequester(adultFocus)
                .tvClickable {
            if (!pinSet) { setPin = true; pin = ""; pin2 = ""; pinError = null; showPin = true }
            else { setPin = false; pin = ""; pinError = null; showPin = true }
        }
        ) {
            ListItem(
                headlineContent = { Text("Ich bin Erwachsener") },
                supportingContent = { Text("Mit PIN geschützt") }
            )
        }
        Spacer(Modifier.height(16.dp))
        Text("Ich bin ein Kind / Gast", style = MaterialTheme.typography.titleMedium)
        // Add tile
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedCard(
                Modifier
                    .weight(1f)
                    .tvClickable { showCreate = true }
            ) { ListItem(headlineContent = { Text("Neues Profil hinzufügen") }) }
        }
        val listState = com.chris.m3usuite.ui.state.rememberRouteListState("profiles:gate")
        LazyColumn(state = listState, contentPadding = PaddingValues(vertical = 8.dp)) {
            items(kids, key = { it.id }) { k ->
                var used by remember(k.id) { mutableStateOf<Int?>(null) }
                var limit by remember(k.id) { mutableStateOf<Int?>(null) }
                LaunchedEffect(k.id) {
                    withContext(Dispatchers.IO) {
                        val dayKey = java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.getDefault()).format(java.util.Calendar.getInstance().time)
                        val b = ObxStore.get(ctx).boxFor(com.chris.m3usuite.data.obx.ObxScreenTimeEntry::class.java)
                        val q = b.query(com.chris.m3usuite.data.obx.ObxScreenTimeEntry_.kidProfileId.equal(k.id).and(com.chris.m3usuite.data.obx.ObxScreenTimeEntry_.dayYyyymmdd.equal(dayKey))).build()
                        val entry = q.findFirst()
                        withContext(kotlinx.coroutines.Dispatchers.Main) { used = entry?.usedMinutes ?: 0; limit = entry?.limitMinutes ?: 0 }
                    }
                }
                OutlinedCard(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .tvClickable {
                            scope.launch { store.setCurrentProfileId(k.id); onEnter() }
                        }
                ) {
                    ListItem(
                        headlineContent = { Text(k.name) },
                        supportingContent = {
                            Column {
                                Text(if (k.type == "guest") "Gastprofil" else "Kinderprofil")
                                if (used != null && limit != null) {
                                    val u = used ?: 0
                                    val l = limit ?: 0
                                    if (l > 0) {
                                        val rem = (l - u).coerceAtLeast(0)
                                        Text("Heute: ${u} min • verbleibend: ${rem} min", style = MaterialTheme.typography.bodySmall)
                                    } else {
                                        Text("Heute: ${u} min • kein Limit", style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                        },
                        leadingContent = {
                            val model = rememberAvatarModel(k.avatarPath)
                            if (model != null) {
                                com.chris.m3usuite.ui.util.AppAsyncImage(
                                    url = model,
                                    contentDescription = null,
                                    modifier = Modifier.size(40.dp).clip(CircleShape),
                                    contentScale = ContentScale.Crop,
                                    crossfade = true,
                                    preferRgb565 = true
                                )
                            } else {
                                Icon(painter = painterResource(android.R.drawable.ic_menu_report_image), contentDescription = null)
                            }
                        }
                    )
                }
            }
        }
        }
    }

    if (showPin) PinDialog(onDismiss = { showPin = false })
    if (showCreate) CreateProfileSheet(
        onDismiss = { showCreate = false },
        onCreate = { name, kid ->
            scope.launch(Dispatchers.IO) {
                val now = System.currentTimeMillis()
                val p = com.chris.m3usuite.data.obx.ObxProfile(name = name, type = if (kid) "kid" else "adult", avatarPath = null, createdAt = now, updatedAt = now)
                profileRepo.insert(p)
                val list = profileRepo.all()
                withContext(Dispatchers.Main) {
                    adult = list.firstOrNull { it.type == "adult" }
                    kids = list.filter { it.type != "adult" }
                    showCreate = false
                }
            }
        }
    )
}
