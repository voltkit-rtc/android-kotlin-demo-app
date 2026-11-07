package `in`.voltkit.livedemo.kotlin.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import `in`.voltkit.livedemo.kotlin.R

object C {
    val bg = Color(0xFF080610)
    val bgDeep = Color(0xFF050309)
    val plum = Color(0xFF170B2E)
    val plumLight = Color(0xFF241243)
    val purple = Color(0xFF8B5CF6)
    val magenta = Color(0xFFD946EF)
    val pink = Color(0xFFEC4899)
    val gold = Color(0xFFFFD765)
    val goldDeep = Color(0xFFE9A826)
    val blue = Color(0xFF4F8CFF)
    val cyan = Color(0xFF22D3EE)
    val white = Color.White
    val text = Color.White
    val textDim = Color(0xADFFFFFF)
    val textFaint = Color(0x6BFFFFFF)
    val glass = Color(0x12FFFFFF)
    val glassStrong = Color(0x21FFFFFF)
    val glassBorder = Color(0x24FFFFFF)
    val danger = Color(0xFFFB3B66)
    val success = Color(0xFF34D399)
    val ink = Color(0xFF201000)
}

object G {
    val accent = listOf(Color(0xFF8B5CF6), Color(0xFFD946EF), Color(0xFFEC4899))
    val gold = listOf(Color(0xFFFFF1BE), Color(0xFFFFD765), Color(0xFFE9A826))
    val night = listOf(Color(0xFF170B2E), Color(0xFF080610))
    val launch = listOf(Color(0xFF2A1159), Color(0xFF170B2E), Color(0xFF080610))
    val panel = listOf(Color(0x1CFFFFFF), Color(0x0AFFFFFF))
    val video = listOf(Color(0xFF8B5CF6), Color(0xFFEC4899))
    val audio = listOf(Color(0xFF3B82F6), Color(0xFF22D3EE))
    val match = listOf(Color(0xFFF59E0B), Color(0xFFEC4899))
    val danger = listOf(Color(0xFFFB3B66), Color(0xFFEC4899))
    val scrimBottom = listOf(Color(0x00050309), Color(0xEB050309))
}

object R2 {
    val sm = 10.dp
    val md = 16.dp
    val lg = 22.dp
    val xl = 28.dp
    val pill = 999.dp
}

fun diagonal(colors: List<Color>) = Brush.linearGradient(colors)
fun vertical(colors: List<Color>) = Brush.verticalGradient(colors)
fun horizontal(colors: List<Color>) = Brush.horizontalGradient(colors)

val Outfit = FontFamily(
    Font(R.font.outfit_regular, FontWeight.Normal),
    Font(R.font.outfit_medium, FontWeight.Medium),
    Font(R.font.outfit_semibold, FontWeight.SemiBold),
    Font(R.font.outfit_bold, FontWeight.Bold),
    Font(R.font.outfit_extrabold, FontWeight.ExtraBold),
)
val IonFont = FontFamily(Font(R.font.ionicons))

enum class V(val weight: FontWeight, val size: Int, val line: Int) {
    Display(FontWeight.ExtraBold, 30, 36),
    Title(FontWeight.Bold, 22, 28),
    Heading(FontWeight.SemiBold, 17, 23),
    Body(FontWeight.Normal, 15, 21),
    Medium(FontWeight.Medium, 15, 21),
    Small(FontWeight.Medium, 13, 18),
    Tiny(FontWeight.SemiBold, 11, 14),
}

/** Text sizes in dp, not sp: like the other two apps, text does not grow with the system font size, so cards keep their shape. */
@Composable
fun fixedSp(size: Dp): TextUnit = with(LocalDensity.current) { size.toSp() }

@Composable
fun textStyle(v: V, color: Color = C.text, size: Int? = null, spacing: Float? = null): TextStyle {
    val s = size ?: v.size
    return TextStyle(
        fontFamily = Outfit,
        fontWeight = v.weight,
        fontSize = fixedSp(s.dp),
        lineHeight = fixedSp((if (size == null) v.line else (s * v.line / v.size)).dp),
        color = color,
        letterSpacing = if (spacing == null) TextUnit.Unspecified else fixedSp(spacing.dp),
        lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
    )
}
