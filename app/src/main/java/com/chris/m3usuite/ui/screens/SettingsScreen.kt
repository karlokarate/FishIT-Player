package com.chris.m3usuite.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.chris.m3usuite.prefs.Keys
import com.chris.m3usuite.prefs.SettingsStore
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    store: SettingsStore,
    onBack: () -> Unit,
    onOpenProfiles: (() -> Unit)? = null
) {
    val scope = rememberCoroutineScope()
    val mode by store.playerMode.collectAsState(initial = "ask")
    val pkg by store.preferredPlayerPkg.collectAsState(initial = "")
    val subScale by store.subtitleScale.collectAsState(initial = 0.06f)
    val subFg by store.subtitleFg.collectAsState(initial = 0xF2FFFFFF.toInt())
    val subBg by store.subtitleBg.collectAsState(initial = 0x66000000)
    val subFgOpacity by store.subtitleFgOpacityPct.collectAsState(initial = 90)
    val subBgOpacity by store.subtitleBgOpacityPct.collectAsState(initial = 40)
    val headerCollapsed by store.headerCollapsedDefaultInLandscape.collectAsState(initial = true)
    val rotationLocked by store.rotationLocked.collectAsState(initial = false)
    val autoplayNext by store.autoplayNext.collectAsState(initial = false)
    val hapticsEnabled by store.hapticsEnabled.collectAsState(initial = false)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Einstellungen") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(android.R.drawable.ic_menu_revert), contentDescription = "Zurück")
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (onOpenProfiles != null) {
                TextButton(onClick = onOpenProfiles) { Text("Profile verwalten…") }
            }
            Text("Player", style = MaterialTheme.typography.titleMedium)
            Column {
                Radio("Immer fragen", mode == "ask") { scope.launch { store.setPlayerMode("ask") } }
                Radio("Interner Player", mode == "internal") { scope.launch { store.setPlayerMode("internal") } }
                Radio("Externer Player", mode == "external") { scope.launch { store.setPlayerMode("external") } }
            }

            OutlinedTextField(
                value = pkg,
                onValueChange = { scope.launch { store.set(Keys.PREF_PLAYER_PACKAGE, it) } },
                label = { Text("Bevorzugtes externes Paket (optional)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Divider()

            Text("Untertitel (interner Player)", style = MaterialTheme.typography.titleMedium)
            Text("Größe")
            Slider(value = subScale, onValueChange = { v -> scope.launch { store.setFloat(Keys.SUB_SCALE, v) } },
                valueRange = 0.04f..0.12f, steps = 8)

            Text("Textfarbe")
            ColorRow(
                selected = subFg,
                onPick = { c -> scope.launch { store.setInt(Keys.SUB_FG, c) } },
                palette = textPalette()
            )

            Text("Hintergrund")
            ColorRow(
                selected = subBg,
                onPick = { c -> scope.launch { store.setInt(Keys.SUB_BG, c) } },
                palette = bgPalette()
            )

            // Opacity
            Text("Text-Deckkraft: ${subFgOpacity}%")
            Slider(
                value = subFgOpacity.toFloat(),
                onValueChange = { v -> scope.launch { store.setSubtitleFgOpacityPct(v.toInt().coerceIn(0, 100)) } },
                valueRange = 0f..100f,
                steps = 10
            )
            Text("Hintergrund-Deckkraft: ${subBgOpacity}%")
            Slider(
                value = subBgOpacity.toFloat(),
                onValueChange = { v -> scope.launch { store.setSubtitleBgOpacityPct(v.toInt().coerceIn(0, 100)) } },
                valueRange = 0f..100f,
                steps = 10
            )

            // Vorschau
            OutlinedCard {
                Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                    Text(
                        "Untertitel-Vorschau",
                        color = Color(subFg),
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .clip(MaterialTheme.shapes.small)
                            .background(Color(subBg))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            Divider()

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Landscape: Header standardmäßig eingeklappt", modifier = Modifier.weight(1f))
                Switch(checked = headerCollapsed, onCheckedChange = { v ->
                    scope.launch { store.setBool(Keys.HEADER_COLLAPSED_LAND, v) }
                })
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Rotation in Player sperren (Landscape)", modifier = Modifier.weight(1f))
                Switch(checked = rotationLocked, onCheckedChange = { v ->
                    scope.launch { store.setRotationLocked(v) }
                })
            }

            Divider()
            Text("Wiedergabe", style = MaterialTheme.typography.titleMedium)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Autoplay nächste Folge (Serie)", modifier = Modifier.weight(1f))
                Switch(checked = autoplayNext, onCheckedChange = { v -> scope.launch { store.setAutoplayNext(v) } })
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Haptisches Feedback", modifier = Modifier.weight(1f))
                Switch(checked = hapticsEnabled, onCheckedChange = { v -> scope.launch { store.setHapticsEnabled(v) } })
            }
        }
    }
}

@Composable
private fun Radio(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Spacer(Modifier.width(8.dp))
        Text(label)
    }
}

@Composable
private fun ColorRow(selected: Int, onPick: (Int) -> Unit, palette: List<Int>) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        palette.forEach { c ->
            val border = if (selected == c) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null
            if (border != null) {
                OutlinedCard(border = border, modifier = Modifier.size(36.dp)) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color(c))
                            .selectable(selected = selected == c, role = Role.Button, onClick = { onPick(c) })
                    )
                }
            } else {
                OutlinedCard(modifier = Modifier.size(36.dp)) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color(c))
                            .selectable(selected = selected == c, role = Role.Button, onClick = { onPick(c) })
                    )
                }
            }
        }
    }
}

private fun textPalette(): List<Int> = listOf(
    Color.White.copy(alpha = 0.95f).toArgb(),
    Color.Yellow.copy(alpha = 0.95f).toArgb(),
    Color.Cyan.copy(alpha = 0.95f).toArgb(),
    Color.Black.copy(alpha = 0.95f).toArgb(),
    Color.Red.copy(alpha = 0.95f).toArgb(),
    Color.Green.copy(alpha = 0.95f).toArgb(),
    Color.Blue.copy(alpha = 0.95f).toArgb(),
    Color.Magenta.copy(alpha = 0.95f).toArgb(),
)

private fun bgPalette(): List<Int> = listOf(
    Color.Black.copy(alpha = 0.50f).toArgb(),
    Color.White.copy(alpha = 0.50f).toArgb(),
    Color.Yellow.copy(alpha = 0.50f).toArgb(),
    Color.Cyan.copy(alpha = 0.50f).toArgb(),
    Color.Red.copy(alpha = 0.50f).toArgb(),
    Color.Green.copy(alpha = 0.50f).toArgb(),
    Color.Blue.copy(alpha = 0.50f).toArgb(),
    Color.Magenta.copy(alpha = 0.50f).toArgb(),
)
