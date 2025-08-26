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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileManagerScreen(onBack: () -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val db = remember { DbProvider.get(ctx) }
    val scope = rememberCoroutineScope()
    val screenRepo = remember { ScreenTimeRepository(ctx) }

    var kids by remember { mutableStateOf<List<Profile>>(emptyList()) }
    var newKidName by remember { mutableStateOf("") }

    suspend fun load() {
        kids = withContext(Dispatchers.IO) { db.profileDao().all().filter { it.type == "kid" } }
    }

    LaunchedEffect(Unit) { load() }

    Scaffold(topBar = {
        TopAppBar(title = { Text("Profile verwalten") }, navigationIcon = {
            IconButton(onClick = onBack) { Icon(painter = androidx.compose.ui.res.painterResource(android.R.drawable.ic_menu_revert), contentDescription = "Zurück") }
        })
    }) { pad ->
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

            Divider()

            LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                items(kids, key = { it.id }) { kid ->
                    var name by remember(kid.id) { mutableStateOf(kid.name) }
                    var limit by remember(kid.id) { mutableStateOf(60) }
                    OutlinedCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") })
                            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                Text("Tageslimit (Minuten)", modifier = Modifier.weight(1f))
                                Slider(value = limit.toFloat(), valueRange = 0f..240f, onValueChange = { limit = it.toInt() })
                                Text("${limit}")
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = {
                                    scope.launch(Dispatchers.IO) {
                                        db.profileDao().update(kid.copy(name = name, updatedAt = System.currentTimeMillis()))
                                        // apply today's limit
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
