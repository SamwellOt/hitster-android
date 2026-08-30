package com.hitster.mobile.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.hitster.mobile.R

// ---- brand palette (dark, neon – like the physical box)
val Ink = Color(0xFF0B0B10)
val Surface1 = Color(0xFF15151D)
val Surface2 = Color(0xFF1F1F2A)
val Surface3 = Color(0xFF2A2A38)
val Outline = Color(0xFF363646)
val TextPrimary = Color(0xFFF7F7FA)
val TextSecondary = Color(0xFFA9A9BC)
val TextTertiary = Color(0xFF8A8AA0) // ≥4.8:1 on Surface2 – hints and section labels stay readable

val NeonPink = Color(0xFFFF2D8F)
val NeonOrange = Color(0xFFFF6B2B)
val NeonYellow = Color(0xFFFFD23F)
val NeonPurple = Color(0xFF8E44FF)
val NeonCyan = Color(0xFF00E5FF)
val NeonGreen = Color(0xFF23C36B)
val Danger = Color(0xFFFF4757)

val NeonBrush = Brush.linearGradient(listOf(NeonPink, NeonOrange, NeonYellow))
val NeonBrushVertical = Brush.verticalGradient(listOf(NeonPink, NeonOrange))
val PurpleBrush = Brush.linearGradient(listOf(NeonPurple, NeonPink))

/** Card colour by decade – mirrors the solution side of the physical cards. */
fun decadeColor(year: Int?): Color = when {
    year == null -> Surface3
    year < 1960 -> Color(0xFF7A3E9D)
    year < 1970 -> Color(0xFF9B59B6)
    year < 1980 -> Color(0xFF6C4BE0)
    year < 1990 -> Color(0xFF2D7DF6)
    year < 2000 -> Color(0xFFF5C542)
    year < 2010 -> Color(0xFFFF7A1A)
    year < 2020 -> Color(0xFF23C36B)
    else -> Color(0xFFFF2D8F)
}

fun onDecadeColor(year: Int?): Color = if (year != null && year in 1990..2009) Color(0xFF14110A) else Color.White

fun parseHex(hex: String?, fallback: Color = NeonPink): Color {
    if (hex.isNullOrBlank()) return fallback
    return try { Color(android.graphics.Color.parseColor(hex)) } catch (_: Exception) { fallback }
}

private val scheme = darkColorScheme(
    primary = NeonPink,
    onPrimary = Color.White,
    secondary = NeonYellow,
    onSecondary = Ink,
    tertiary = NeonCyan,
    background = Ink,
    onBackground = TextPrimary,
    surface = Surface1,
    onSurface = TextPrimary,
    surfaceVariant = Surface2,
    onSurfaceVariant = TextSecondary,
    outline = Outline,
    error = Danger,
)

/** Display face for the wordmark, years and big numbers (Righteous, OFL). */
val DisplayFont = FontFamily(Font(R.font.righteous_regular, FontWeight.Normal))

/** Body/UI face (Poppins, OFL). */
val BodyFont = FontFamily(
    Font(R.font.poppins_regular, FontWeight.Normal),
    Font(R.font.poppins_medium, FontWeight.Medium),
    Font(R.font.poppins_semibold, FontWeight.SemiBold),
    Font(R.font.poppins_bold, FontWeight.Bold),
    Font(R.font.poppins_black, FontWeight.Black),
)

val HitsterTypography = Typography(
    displayLarge = TextStyle(fontFamily = DisplayFont, fontWeight = FontWeight.Normal, fontSize = 56.sp, lineHeight = 60.sp),
    displayMedium = TextStyle(fontFamily = DisplayFont, fontWeight = FontWeight.Normal, fontSize = 40.sp, lineHeight = 44.sp),
    displaySmall = TextStyle(fontFamily = DisplayFont, fontWeight = FontWeight.Normal, fontSize = 30.sp, lineHeight = 34.sp),
    headlineLarge = TextStyle(fontFamily = BodyFont, fontWeight = FontWeight.Black, fontSize = 30.sp, lineHeight = 36.sp),
    headlineMedium = TextStyle(fontFamily = BodyFont, fontWeight = FontWeight.Black, fontSize = 24.sp, lineHeight = 30.sp),
    headlineSmall = TextStyle(fontFamily = BodyFont, fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 26.sp),
    titleLarge = TextStyle(fontFamily = BodyFont, fontWeight = FontWeight.Bold, fontSize = 18.sp, lineHeight = 24.sp),
    titleMedium = TextStyle(fontFamily = BodyFont, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp),
    titleSmall = TextStyle(fontFamily = BodyFont, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontFamily = BodyFont, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = BodyFont, fontSize = 14.sp, lineHeight = 21.sp),
    bodySmall = TextStyle(fontFamily = BodyFont, fontSize = 12.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontFamily = BodyFont, fontWeight = FontWeight.Bold, fontSize = 14.sp, letterSpacing = 0.5.sp),
    labelMedium = TextStyle(fontFamily = BodyFont, fontWeight = FontWeight.Bold, fontSize = 12.sp, letterSpacing = 0.8.sp),
    labelSmall = TextStyle(fontFamily = BodyFont, fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 1.sp),
)

@Composable
fun HitsterTheme(content: @Composable () -> Unit) {
    // The game is always dark – the neon identity does not work on a light background.
    isSystemInDarkTheme()
    MaterialTheme(colorScheme = scheme, typography = HitsterTypography, content = content)
}
