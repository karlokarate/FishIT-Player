package com.chris.m3usuite.ui.detail

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.chris.m3usuite.ui.actions.MediaAction
import com.chris.m3usuite.ui.actions.MediaActionBar
import com.chris.m3usuite.ui.util.AppPosterImage

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DetailHeader(
    title: String,
    subtitle: String? = null,
    heroUrl: Any?,
    posterUrl: Any?,
    actions: List<MediaAction>,
    meta: DetailMeta? = null,
    showHeroScrim: Boolean = true,
    headerExtras: @Composable ColumnScope.() -> Unit = {},
    collapsibleMeta: Boolean = false
) {
    Box(Modifier.fillMaxWidth()) {
        if (showHeroScrim) HeroScrim(url = heroUrl)
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = MaterialTheme.shapes.medium, color = Color.White.copy(alpha = 0.04f)) {
                    AppPosterImage(
                        url = posterUrl,
                        contentDescription = null,
                        modifier = Modifier.size(width = 120.dp, height = 180.dp),
                        crossfade = true,
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop
                    )
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
                    subtitle?.let { Text(it, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    meta?.let { MetaChips(it, compact = false, collapsible = collapsibleMeta) }
                    MediaActionBar(actions = actions, requestInitialFocus = false)
                }
            }
            headerExtras()
        }
    }
}
