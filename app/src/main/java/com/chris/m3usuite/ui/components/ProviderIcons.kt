package com.chris.m3usuite.ui.components

import android.content.res.Resources
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Image
import androidx.compose.material3.Text
import com.chris.m3usuite.ui.util.AppAsyncImage
import com.chris.m3usuite.ui.debug.safePainter
import androidx.compose.ui.layout.ContentScale

/**
 * ProviderIconFor – zeigt ein kleines Icon/Badge für einen normalisierten Provider-Key.
 *
 * Falls ein Drawable `ic_provider_<key>` existiert, wird dieses verwendet.
 * Sonst wird ein runder Badge mit Markenfarbe und Kurztext gerendert.
 */
@Composable
fun ProviderIconFor(key: String, label: String, sizeDp: Int = 24) {
    val ctx = LocalContext.current
    val pkg = ctx.packageName
    val res: Resources = ctx.resources
    val resName = "ic_provider_" + key.lowercase()
    val resId = remember(key) { res.getIdentifier(resName, "drawable", pkg) }
    if (resId != 0) {
        Image(
            painter = safePainter(resId, label = "ProviderIcon/$key"),
            contentDescription = label,
            modifier = Modifier.size(sizeDp.dp)
        )
        return
    }
    // Try runtime-loaded provider icon (Clearbit domain logos) with Coil cache.
    val candidates = remember(key) { candidateIconUrls(key) }
    val failed = remember(key) { mutableStateOf(false) }
    if (!failed.value && candidates.isNotEmpty()) {
        AppAsyncImage(
            url = candidates.first(),
            contentDescription = label,
            modifier = Modifier.size(sizeDp.dp),
            contentScale = ContentScale.Fit,
            crossfade = false,
            onError = { failed.value = true }
        )
        if (!failed.value) return
    }

    // Fallback: Badge mit Kurzlabel und Farbcode
    val (short, color) = remember(label, key) { shortLabelAndColor(key, label) }
    Box(
        modifier = Modifier
            .size(sizeDp.dp)
            .background(color, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = short,
            color = Color.White,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            lineHeight = 10.sp,
        )
    }
}

private fun shortLabelAndColor(key: String, label: String): Pair<String, Color> {
    val k = key.lowercase()
    return when {
        "apple" in k -> "A+" to Color(0xFF111111)
        "netflix" in k -> "N" to Color(0xFFE50914)
        "disney" in k -> "D+" to Color(0xFF113CCF)
        "amazon" in k || "prime" in k -> "P" to Color(0xFF00A8E1)
        "paramount" in k -> "P+" to Color(0xFF0A4BE1)
        "max" == k || ("max" in k) -> "Max" to Color(0xFF1B1F3B)
        "sky" in k || "wow" in k -> "WOW" to Color(0xFF00FFB6)
        "discovery" in k -> "d+" to Color(0xFFEB6F1A)
        "mubi" in k -> "M" to Color(0xFF222222)
        else ->
            run {
                val t = label.trim()
                val short = when {
                    t.contains('+') -> t.split('+')[0].take(1) + "+"
                    t.length >= 2 -> t.substring(0, 2).uppercase()
                    t.isNotEmpty() -> t.substring(0, 1).uppercase()
                    else -> "?"
                }
                short to Color(0xFF777777)
            }
    }
}

private fun candidateIconUrls(key: String): List<String> {
    val k = key.lowercase()
    fun clearbit(domain: String) = "https://logo.clearbit.com/$domain?size=64"
    val domains = when {
        "apple" in k -> listOf("tv.apple.com", "apple.com")
        "netflix" in k -> listOf("netflix.com")
        "disney" in k -> listOf("disneyplus.com", "disney.com")
        "amazon" in k || "prime" in k -> listOf("primevideo.com", "amazon.com")
        "paramount" in k -> listOf("paramountplus.com", "paramount.com")
        k == "max" || "max" in k || "hbo" in k -> listOf("max.com", "hbomax.com", "hbo.com")
        "sky" in k || "wow" in k -> listOf("wowtv.de", "sky.de", "sky.com")
        "discovery" in k -> listOf("discoveryplus.com", "discovery.com")
        "mubi" in k -> listOf("mubi.com")
        else -> emptyList()
    }
    return domains.map { clearbit(it) }
}
