package com.chris.m3usuite.ui.components.chips

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

data class ChipStyle(
    val textColor: Color,
    val bg: Brush,
    val borderColor: Color,
    val bold: Boolean = true,
    val drip: Boolean = false, // simple drip decoration for Horror
    val caution: Boolean = false, // caution tape bands (for Adults)
)

private fun gradient(
    a: Color,
    b: Color,
) = Brush.linearGradient(listOf(a, b))

@Composable
fun CategoryChip(
    key: String,
    label: String,
    modifier: Modifier = Modifier,
) {
    val style =
        remember(key) {
            if (key.startsWith("adult")) styles["adult"]!! else styles[key] ?: styles["other"]!!
        }
    val font =
        when {
            key.startsWith("adult") -> com.chris.m3usuite.ui.theme.CategoryFonts.Staatliches
            else ->
                when (key) {
                    "horror" -> com.chris.m3usuite.ui.theme.CategoryFonts.Nosifer
                    "action" -> com.chris.m3usuite.ui.theme.CategoryFonts.Bangers
                    "sci_fi" -> com.chris.m3usuite.ui.theme.CategoryFonts.Orbitron
                    "classic", "collection" -> com.chris.m3usuite.ui.theme.CategoryFonts.Cinzel
                    "western" -> com.chris.m3usuite.ui.theme.CategoryFonts.Rye
                    "christmas" -> com.chris.m3usuite.ui.theme.CategoryFonts.MountainsOfChristmas
                    "romance" -> com.chris.m3usuite.ui.theme.CategoryFonts.Parisienne
                    "kids", "family" -> com.chris.m3usuite.ui.theme.CategoryFonts.Fredoka
                    "drama" -> com.chris.m3usuite.ui.theme.CategoryFonts.PlayfairDisplay
                    "documentary" -> com.chris.m3usuite.ui.theme.CategoryFonts.Merriweather
                    "thriller", "war" -> com.chris.m3usuite.ui.theme.CategoryFonts.Teko
                    "adventure" -> com.chris.m3usuite.ui.theme.CategoryFonts.AdventPro
                    "comedy" -> com.chris.m3usuite.ui.theme.CategoryFonts.Baloo2
                    "anime" -> com.chris.m3usuite.ui.theme.CategoryFonts.MPlusRounded1c
                    "bollywood" -> com.chris.m3usuite.ui.theme.CategoryFonts.YatraOne
                    "martial_arts", "4k" -> com.chris.m3usuite.ui.theme.CategoryFonts.RussoOne
                    "year_2025_2024" -> com.chris.m3usuite.ui.theme.CategoryFonts.Orbitron
                    "show" -> com.chris.m3usuite.ui.theme.CategoryFonts.Oswald
                    "new", "recent" -> com.chris.m3usuite.ui.theme.CategoryFonts.Inter
                    else -> com.chris.m3usuite.ui.theme.CategoryFonts.Inter
                }
        }
    val shape = RoundedCornerShape(16.dp)
    val adultBorderAccent =
        remember(key) {
            if (key.startsWith("adult_")) adultAccentFor(key) else null
        }
    val borderColor = adultBorderAccent ?: style.borderColor
    Surface(
        color = Color.Transparent,
        contentColor = style.textColor,
        shape = shape,
        border = BorderStroke(1.dp, borderColor),
        modifier =
            modifier
                .drawBehind {
                    if (style.drip) {
                        // very light drip accents at the bottom (3 semicircles)
                        val y = size.height
                        val r = 3.dp.toPx()
                        val dripColor = borderColor.copy(alpha = 0.35f)
                        drawCircle(
                            color = dripColor,
                            radius = r,
                            center =
                                androidx.compose.ui.geometry
                                    .Offset(size.width * 0.25f, y),
                        )
                        drawCircle(
                            color = dripColor,
                            radius = r * 1.2f,
                            center =
                                androidx.compose.ui.geometry
                                    .Offset(size.width * 0.5f, y),
                        )
                        drawCircle(
                            color = dripColor,
                            radius = r * 0.9f,
                            center =
                                androidx.compose.ui.geometry
                                    .Offset(size.width * 0.75f, y),
                        )
                    }
                    if (style.caution) {
                        val bandH = 6.dp.toPx()
                        val step = 12.dp.toPx()
                        val yellow = Color(0xFFFFC107)
                        val black = Color(0xFF000000)

                        fun drawBand(y: Float) {
                            withTransform({
                                translate(left = 0f, top = y)
                                rotate(degrees = -20f)
                            }) {
                                var x = -size.width
                                var flag = true
                                while (x < size.width * 2) {
                                    drawRect(
                                        color = if (flag) yellow else black,
                                        topLeft =
                                            androidx.compose.ui.geometry
                                                .Offset(x, 0f),
                                        size =
                                            androidx.compose.ui.geometry
                                                .Size(step, bandH),
                                    )
                                    x += step
                                    flag = !flag
                                }
                            }
                        }
                        drawBand(0f)
                        drawBand(size.height - bandH)
                    }
                },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier =
                Modifier
                    .background(style.bg, shape)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
        ) {
            Text(
                text = label,
                style =
                    if (style.bold) {
                        MaterialTheme.typography.labelLarge.copy(
                            color = style.textColor,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = font,
                        )
                    } else {
                        MaterialTheme.typography.labelLarge.copy(color = style.textColor, fontFamily = font)
                    },
            )
        }
    }
}

// Color palette (material-ish)
private val Gold = Color(0xFFD4AF37)
private val Pine = Color(0xFF2E7D32)
private val PineDark = Color(0xFF1B5E20)
private val HotOrange = Color(0xFFFF6A00)
private val HotOrangeDark = Color(0xFFD64E00)
private val Navy = Color(0xFF0F1A3D)
private val NavyDark = Color(0xFF0B1533)
private val Burgund = Color(0xFF7A1333)
private val Amethyst = Color(0xFF7C3AED)
private val Blood = Color(0xFFB00020)
private val Cyan = Color(0xFF00B8D9)
private val CyanDark = Color(0xFF071317)
private val GrayMetLight = Color(0xFFDCDCDC)
private val GrayMetDark = Color(0xFFA6A6A6)
private val Midnight = Color(0xFF0B1026)
private val ElectricIndigo = Color(0xFF312E81)

private val styles: Map<String, ChipStyle> =
    mapOf(
        // meta
        "recent" to
            ChipStyle(textColor = Color(0xFFF59E0B), bg = gradient(Color(0xFF1B1B1B), Color(0xFF111111)), borderColor = Color(0x42F59E0B)),
        "new" to ChipStyle(textColor = Cyan, bg = gradient(Color(0xFF0B1E22), CyanDark), borderColor = Color(0x4000B8D9)),
        // core curated
        "adventure" to ChipStyle(textColor = Color(0xFFE6F3E7), bg = gradient(Pine, Color(0xFF1F5A22)), borderColor = Color(0x4DA5D6A7)),
        "action" to ChipStyle(textColor = Color.White, bg = gradient(HotOrange, HotOrangeDark), borderColor = Color(0x59FF6A00)),
        "anime" to
            ChipStyle(textColor = Color(0xFF3A0A21), bg = gradient(Color(0xFFFFDEF0), Color(0xFFFFC7E6)), borderColor = Color(0x59FF4DA6)),
        "bollywood" to
            ChipStyle(textColor = Color(0xFF432000), bg = gradient(Color(0xFFFFD580), Color(0xFFFFAA00)), borderColor = Color(0x59FFAA00)),
        "classic" to
            ChipStyle(
                textColor = Color(0xFFEADAA6),
                bg = gradient(Color(0xFF1A1A1A), Color(0xFF101010)),
                borderColor = Gold.copy(alpha = 0.45f),
            ),
        "documentary" to ChipStyle(textColor = Color(0xFFDCE6FF), bg = gradient(Navy, NavyDark), borderColor = Color(0x591E3A8A)),
        "drama" to
            ChipStyle(
                textColor = Color(0xFFFFE6EF),
                bg = gradient(Color(0xFF4A0F24), Color(0xFF340A19)),
                borderColor = Burgund.copy(alpha = 0.3f),
            ),
        "family" to
            ChipStyle(textColor = Color(0xFF3E2A00), bg = gradient(Color(0xFFFFE5A3), Color(0xFFF6C453)), borderColor = Color(0x59F6C453)),
        "fantasy" to ChipStyle(textColor = Color(0xFFF3E8FF), bg = gradient(Color(0xFF4B1FA4), Amethyst), borderColor = Color(0x59B794F4)),
        "horror" to
            ChipStyle(
                textColor = Blood,
                bg = gradient(Color(0xFF1B0A0D), Color(0xFF110507)),
                borderColor = Blood.copy(alpha = 0.4f),
                drip = true,
            ),
        "kids" to
            ChipStyle(
                textColor = Color(0xFF08343B),
                bg = gradient(Color(0xFFA5F3FC), Color(0xFF67E8F9)),
                borderColor = Color(0x5906B6D4),
            ),
        "comedy" to
            ChipStyle(textColor = Color(0xFF3A2900), bg = gradient(Color(0xFFFFE08A), Color(0xFFFFC400)), borderColor = Color(0x59FFC400)),
        "war" to
            ChipStyle(textColor = Color(0xFFF2F2F2), bg = gradient(Color(0xFF4B5320), Color(0xFF6B8E23)), borderColor = Color(0x52A4B87A)),
        "martial_arts" to
            ChipStyle(textColor = Color.White, bg = gradient(Color(0xFFB71C1C), Color(0xFFD32F2F)), borderColor = Color(0x51E57373)),
        "romance" to
            ChipStyle(textColor = Color.White, bg = gradient(Color(0xFFEC407A), Color(0xFFC2185B)), borderColor = Color(0x55F48FB1)),
        "sci_fi" to ChipStyle(textColor = Color(0xFFEDEDED), bg = gradient(GrayMetLight, GrayMetDark), borderColor = Color(0x66C0C0C0)),
        "show" to ChipStyle(textColor = Color.White, bg = gradient(Color(0xFFD81B60), Color(0xFF880E4F)), borderColor = Color(0x59F06292)),
        "thriller" to
            ChipStyle(textColor = Color(0xFFEAEAEA), bg = gradient(Color(0xFF1F2937), Color(0xFF111827)), borderColor = Color(0x48C62828)),
        "christmas" to ChipStyle(textColor = Color.White, bg = gradient(Pine, PineDark), borderColor = Color(0x4DE53935)),
        "western" to
            ChipStyle(textColor = Color(0xFFF9E7D0), bg = gradient(Color(0xFF8B4513), Color(0xFF5D3111)), borderColor = Color(0x59CFA277)),
        // specials
        "4k" to
            ChipStyle(textColor = Color(0xFFC6FF00), bg = gradient(Color(0xFF212121), Color(0xFF0F0F0F)), borderColor = Color(0x66C6FF00)),
        "collection" to
            ChipStyle(textColor = Color(0xFFE8EBFF), bg = gradient(Color(0xFF1F2A57), Color(0xFF3F51B5)), borderColor = Color(0x5990A4F4)),
        "year_2025_2024" to
            ChipStyle(
                textColor = Color(0xFF7EE7FF),
                bg = gradient(Midnight, ElectricIndigo),
                borderColor = Color(0x407EE7FF),
                bold = false,
            ),
        "other" to
            ChipStyle(textColor = Color(0xFF2E2E2E), bg = gradient(Color(0xFFE0E0E0), Color(0xFFCFCFCF)), borderColor = Color(0x729E9E9E)),
        // Adults umbrella style
        "adult" to
            ChipStyle(
                textColor = Color(0xFFEDE7F6),
                bg = gradient(Color(0xFF0F0F10), Color(0xFF18121F)),
                borderColor = Color(0x558E24AA),
                caution = true,
            ),
    )

private fun adultAccentFor(key: String): Color? {
    val k = key.removePrefix("adult_")
    return when (k) {
        "milf" -> Color(0xFFE91E63)
        "amateur" -> Color(0xFFFB8C00)
        "full_hd", "fullhd", "fhd" -> Color(0xFF42A5F5)
        "anal" -> Color(0xFFEF6C00)
        "black" -> Color(0xFF424242)
        "blacked" -> Color(0xFF7B1FA2)
        "classic" -> Color(0xFFA1887F)
        "czech" -> Color(0xFF7CB342)
        "group_sex" -> Color(0xFF8D6E63)
        "hardcore" -> Color(0xFFC62828)
        "lesbian" -> Color(0xFFEC407A)
        "movies" -> Color(0xFF5C6BC0)
        "shemale_gay", "shemale__gay", "shemale___gay" -> Color(0xFF7E57C2)
        "tushy" -> Color(0xFFAB47BC)
        "vixen" -> Color(0xFF8E24AA)
        "tr_altyazi", "tr_altyazı" -> Color(0xFF26A69A)
        "step_family", "stepfamily" -> Color(0xFF607D8B)
        else -> null
    }
}
