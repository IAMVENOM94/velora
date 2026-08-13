package app.velora.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val Ink = Color(0xFF09070D)
val Surface = Color(0xFF17131E)
val Glass = Color(0xCC211B2B)
val Pink = Color(0xFFFF4F9A)
val Violet = Color(0xFF9B6CFF)
val Muted = Color(0xFFA9A2B3)
val White = Color(0xFFF9F6FC)

private val colors = darkColorScheme(
    primary = Pink, secondary = Violet, background = Ink, surface = Surface,
    onPrimary = White, onSecondary = White, onBackground = White, onSurface = White,
    outline = Color(0xFF41384C)
)

private val type = Typography(
    displayLarge = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Black, fontSize = 48.sp, letterSpacing = (-2).sp),
    headlineLarge = androidx.compose.ui.text.TextStyle(fontWeight = FontWeight.Bold, fontSize = 30.sp, letterSpacing = (-1).sp),
    headlineMedium = androidx.compose.ui.text.TextStyle(fontWeight = FontWeight.Bold, fontSize = 24.sp),
    titleLarge = androidx.compose.ui.text.TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 20.sp),
    bodyLarge = androidx.compose.ui.text.TextStyle(fontSize = 16.sp, lineHeight = 23.sp),
    bodyMedium = androidx.compose.ui.text.TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    labelLarge = androidx.compose.ui.text.TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
)

@Composable fun VeloraTheme(content: @Composable () -> Unit) = MaterialTheme(colorScheme = colors, typography = type, content = content)
