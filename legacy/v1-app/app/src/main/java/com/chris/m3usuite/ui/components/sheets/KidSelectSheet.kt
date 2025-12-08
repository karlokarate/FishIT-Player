package com.chris.m3usuite.ui.components.sheets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.chris.m3usuite.data.obx.ObxProfile
import com.chris.m3usuite.data.repo.ProfileObxRepository
import com.chris.m3usuite.ui.common.TvTextButton
import com.chris.m3usuite.ui.util.AppAsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Reusable Kid selection sheet for granting/revoking permissions.
 *
 * Usage:
 *  KidSelectSheet(
 *    onConfirm = { ids -> /* do something */ },
 *    onDismiss = { /* close */ }
 *  )
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KidSelectSheet(
    onConfirm: suspend (kidIds: List<Long>) -> Unit,
    onDismiss: () -> Unit,
    prechecked: Set<Long> = emptySet(),
    title: String = "Kinder auswählen",
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    var kids by remember { mutableStateOf<List<ObxProfile>>(emptyList()) }
    var checked by remember { mutableStateOf(prechecked) }

    LaunchedEffect(Unit) {
        kids =
            withContext(Dispatchers.IO) {
                ProfileObxRepository(ctx).all().filter { it.type == "kid" }
            }
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item { Text(title) }
            items(kids, key = { it.id }) { k ->
                KidRow(
                    name = k.name,
                    avatar = k.avatarPath,
                    checked = k.id in checked,
                    onToggle = {
                        checked = if (k.id in checked) checked - k.id else checked + k.id
                    },
                )
            }
            item {
                TvTextButton(
                    onClick = {
                        scope.launch {
                            onConfirm(checked.toList())
                            onDismiss()
                        }
                    },
                ) { Text("Übernehmen") }
            }
            item {
                TvTextButton(onClick = onDismiss) { Text("Abbrechen") }
            }
        }
    }
}

@Composable
private fun KidRow(
    name: String,
    avatar: String?,
    checked: Boolean,
    onToggle: () -> Unit,
) {
    androidx.compose.foundation.layout.Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        androidx.compose.foundation.layout.Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (!avatar.isNullOrBlank()) {
                AppAsyncImage(
                    url = avatar,
                    contentDescription = "Avatar",
                    modifier = Modifier.size(40.dp),
                    contentScale = ContentScale.Crop,
                    crossfade = true,
                )
            }
            Text(name)
        }
        Checkbox(checked = checked, onCheckedChange = { onToggle() })
    }
}
