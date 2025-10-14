package com.chris.m3usuite.ui.auth

import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.*
import androidx.compose.ui.unit.dp
import com.chris.m3usuite.ui.layout.FishFormButtonRow
import com.chris.m3usuite.ui.layout.FishFormSection
import com.chris.m3usuite.ui.layout.FishFormSwitch
import com.chris.m3usuite.ui.layout.FishFormTextField

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateProfileSheet(
    onDismiss: () -> Unit,
    onCreate: (name: String, isKid: Boolean) -> Unit,
) {
    var name by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("") }
    var isKid by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(16.dp)) {
            Text("Profil anlegen", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(12.dp))

            FishFormSection(title = "Details") {
                FishFormTextField(label = "Name", value = name, onValueChange = { name = it.trimStart() })
                Spacer(Modifier.height(8.dp))
                FishFormSwitch(label = "Kinderprofil", checked = isKid, onCheckedChange = { isKid = it })
            }

            Spacer(Modifier.height(16.dp))
            FishFormButtonRow(
                primaryText = "Erstellen",
                onPrimary = { if (name.isNotBlank()) onCreate(name.trim(), isKid) },
                secondaryText = "Abbrechen",
                onSecondary = onDismiss,
                primaryEnabled = name.isNotBlank()
            )
            Spacer(Modifier.height(8.dp))
        }
    }
}
