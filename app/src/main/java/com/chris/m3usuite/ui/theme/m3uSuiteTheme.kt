package com.chris.m3usuite.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Shapes
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.shape.RoundedCornerShape


private val DarkFancy = darkColorScheme(
    primary = Color(0xFF8AB4FF),
    onPrimary = Color(0xFF001B3C),
    secondary = Color(0xFF9BE1FF),
    background = Color(0xFF0D121A),
    surface = Color(0xFF111827),
    onSurface = Color(0xFFE7EEF7)
)

private val LightFancy = lightColorScheme(
    primary = Color(0xFF2154FF),
    onPrimary = Color.White,
    secondary = Color(0xFF006D83),
    background = Color(0xFFF7FAFF),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF0F172A)
)

private val GlossShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(18.dp),
    large  = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val dark = isSystemInDarkTheme()
    val colorScheme =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        } else {
            if (dark) DarkFancy else LightFancy
        }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography(),
        shapes = GlossShapes,
        content = content
    )
}
