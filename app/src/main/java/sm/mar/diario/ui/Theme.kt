package sm.mar.diario.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import sm.mar.diario.data.ActType

@Immutable
data class Palette(
    val muted: Color, val line: Color, val warn: Color, val warnSoft: Color, val ok: Color,
    val call: Color, val mailIn: Color, val mailOut: Color, val wa: Color, val meet: Color, val note: Color,
)

private val LightPalette = Palette(
    muted = Color(0xFF5D6B74), line = Color(0xFFD5DCE1), warn = Color(0xFFB4570D), warnSoft = Color(0xFFFBE9D6), ok = Color(0xFF2F7A4B),
    call = Color(0xFF1F5F7A), mailIn = Color(0xFF6B4FA0), mailOut = Color(0xFF2F7A4B), wa = Color(0xFF128C4B), meet = Color(0xFF9A6A12), note = Color(0xFF5D6B74),
)
private val DarkPalette = Palette(
    muted = Color(0xFF93A3AD), line = Color(0xFF2B3943), warn = Color(0xFFF0A35C), warnSoft = Color(0xFF3A2A18), ok = Color(0xFF6CC58F),
    call = Color(0xFF5FA9C8), mailIn = Color(0xFFA98FE0), mailOut = Color(0xFF6CC58F), wa = Color(0xFF4FD38A), meet = Color(0xFFE2B45A), note = Color(0xFF93A3AD),
)

val LocalPalette = staticCompositionLocalOf { LightPalette }

private val lightScheme = lightColorScheme(
    primary = Color(0xFF1F5F7A), onPrimary = Color.White,
    primaryContainer = Color(0xFFDBE9EF), onPrimaryContainer = Color(0xFF0E2E3B),
    secondary = Color(0xFFB4570D), onSecondary = Color.White,
    secondaryContainer = Color(0xFFFBE9D6), onSecondaryContainer = Color(0xFF4A2405),
    background = Color(0xFFEEF1F3), onBackground = Color(0xFF17232B),
    surface = Color(0xFFFBFCFC), onSurface = Color(0xFF17232B),
    surfaceVariant = Color(0xFFE3E8EB), onSurfaceVariant = Color(0xFF5D6B74),
    surfaceContainer = Color(0xFFF4F6F7), surfaceContainerHigh = Color(0xFFFBFCFC),
    outline = Color(0xFFB9C3CA), outlineVariant = Color(0xFFD5DCE1),
    error = Color(0xFFA8322B),
)
private val darkScheme = darkColorScheme(
    primary = Color(0xFF5FA9C8), onPrimary = Color(0xFF0D1B22),
    primaryContainer = Color(0xFF1C3644), onPrimaryContainer = Color(0xFFD5EAF3),
    secondary = Color(0xFFF0A35C), onSecondary = Color(0xFF2A1604),
    secondaryContainer = Color(0xFF3A2A18), onSecondaryContainer = Color(0xFFFBE0C4),
    background = Color(0xFF11181D), onBackground = Color(0xFFE3EAEE),
    surface = Color(0xFF18222A), onSurface = Color(0xFFE3EAEE),
    surfaceVariant = Color(0xFF1D2830), onSurfaceVariant = Color(0xFF93A3AD),
    surfaceContainer = Color(0xFF151E24), surfaceContainerHigh = Color(0xFF1C2730),
    outline = Color(0xFF3A4A55), outlineVariant = Color(0xFF2B3943),
    error = Color(0xFFEF7A70),
)

private val condensed = FontFamily.SansSerif

private val typography = Typography(
    headlineSmall = TextStyle(fontFamily = condensed, fontWeight = FontWeight.SemiBold, fontSize = 24.sp, lineHeight = 30.sp),
    titleLarge = TextStyle(fontFamily = condensed, fontWeight = FontWeight.SemiBold, fontSize = 21.sp, lineHeight = 26.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 23.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 12.5.sp, lineHeight = 17.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.Medium, fontSize = 14.sp),
    labelSmall = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 11.sp, letterSpacing = 0.8.sp),
)

@Composable
fun DiarioTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    androidx.compose.runtime.CompositionLocalProvider(LocalPalette provides if (dark) DarkPalette else LightPalette) {
        MaterialTheme(colorScheme = if (dark) darkScheme else lightScheme, typography = typography, content = content)
    }
}

@Composable
fun typeColor(t: ActType): Color {
    val p = LocalPalette.current
    return when (t) {
        ActType.CALL -> p.call
        ActType.EMAIL_IN -> p.mailIn
        ActType.EMAIL_OUT -> p.mailOut
        ActType.WHATSAPP -> p.wa
        ActType.MEETING -> p.meet
        ActType.NOTE -> p.note
    }
}
