package com.chris.m3usuite.ui.auth

import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.*
import androidx.compose.ui.unit.dp
import com.chris.m3usuite.ui.skin.focusScaleOnTv

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateProfileSheet(
    onDismiss: () -> Unit,
    onCreate: (name: String, isKid: Boolean) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var isKid by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(16.dp)) {
            Text("Profil anlegen", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") })
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Checkbox(checked = isKid, onCheckedChange = { isKid = it })
                Spacer(Modifier.width(8.dp))
                Text("Kinderprofil")
            }
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = { if (name.isNotBlank()) onCreate(name.trim(), isKid) },
                modifier = Modifier.focusScaleOnTv()
            ) { Text("Erstellen") }
            Spacer(Modifier.height(8.dp))
        }
    }
}

