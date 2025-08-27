package com.chris.m3usuite.ui.auth

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.chris.m3usuite.data.db.DbProvider
import com.chris.m3usuite.data.db.Profile
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

@Composable
fun ProfileGate(
    onEnter: () -> Unit,
) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val store = remember { SettingsStore(ctx) }
    val db = remember { DbProvider.get(ctx) }
    val scope = rememberCoroutineScope()

    var adult by remember { mutableStateOf<Profile?>(null) }
    var kids by remember { mutableStateOf<List<Profile>>(emptyList()) }

    // PIN UI state (persist across rotation)
    var showPin by rememberSaveable { mutableStateOf(false) }
    var setPin by rememberSaveable { mutableStateOf(false) }
    var pin by rememberSaveable { mutableStateOf("") }
    var pin2 by rememberSaveable { mutableStateOf("") }
    var pinError by rememberSaveable { mutableStateOf<String?>(null) }
    val pinSet by store.adultPinSet.collectAsState(initial = false)

    LaunchedEffect(Unit) {
        val list = withContext(Dispatchers.IO) { db.profileDao().all() }
        adult = list.firstOrNull { it.type == "adult" }
        kids = list.filter { it.type == "kid" }
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
                TextButton(onClick = {
                    if (setPin) {
                        if (pin.isBlank() || pin != pin2) { pinError = "PINs stimmen nicht"; return@TextButton }
                        val h = sha256(pin)
                        scope.launch { 
                            store.setAdultPinHash(h); store.setAdultPinSet(true)
                            // ensure we set a valid adult id
                            val a = adult ?: withContext(Dispatchers.IO) { db.profileDao().all().firstOrNull { it.type == "adult" } }
                            store.setCurrentProfileId(a?.id ?: -1)
                            pin = ""; pin2 = ""; showPin = false; onEnter() 
                        }
                    } else {
                        scope.launch {
                            val ok = sha256(pin) == store.adultPinHash.first()
                            if (ok) {
                                val a = adult ?: withContext(Dispatchers.IO) { db.profileDao().all().firstOrNull { it.type == "adult" } }
                                store.setCurrentProfileId(a?.id ?: -1)
                                pin = ""; showPin = false; onEnter() 
                            } else pinError = "Falscher PIN"
                        }
                    }
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } }
        )
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Wer bist du?", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(12.dp))
        // Adult
        OutlinedCard(Modifier.fillMaxWidth().clickable {
            if (!pinSet) { setPin = true; pin = ""; pin2 = ""; pinError = null; showPin = true }
            else { setPin = false; pin = ""; pinError = null; showPin = true }
        }) { ListItem(headlineContent = { Text("Ich bin Erwachsen") }, supportingContent = { Text("Mit PIN geschützt") }) }
        Spacer(Modifier.height(16.dp))
        Text("Ich bin ein Kind", style = MaterialTheme.typography.titleMedium)
        LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
            items(kids, key = { it.id }) { k ->
                OutlinedCard(Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable {
                    scope.launch { store.setCurrentProfileId(k.id); onEnter() }
                }) {
                    ListItem(
                        headlineContent = { Text(k.name) },
                        supportingContent = { Text("Kinderprofil") },
                        leadingContent = {
                            if (!k.avatarPath.isNullOrBlank()) {
                                val src = if (k.avatarPath!!.startsWith("/")) "file://${k.avatarPath}" else k.avatarPath
                                AsyncImage(
                                    model = ImageRequest.Builder(ctx).data(src).build(),
                                    contentDescription = null,
                                    modifier = Modifier.size(40.dp).clip(CircleShape)
                                )
                            } else {
                                Icon(painter = androidx.compose.ui.res.painterResource(android.R.drawable.ic_menu_report_image), contentDescription = null)
                            }
                        }
                    )
                }
            }
        }
    }

    if (showPin) PinDialog(onDismiss = { showPin = false })
}
