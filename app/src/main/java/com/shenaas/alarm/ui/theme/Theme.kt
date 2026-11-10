package com.shenaas.alarm.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.shenaas.alarm.R
import com.shenaas.alarm.model.Difficulty

/** Shenaas brand palette (shenaas.com), pushed into a dark, aggressive alarm look. */
object Brand {
    val Orange = Color(0xFFF34500)
    val OrangeSoft = Color(0xFFF26A35)
    val ElectricBlue = Color(0xFF1F51FF)
    val BlueSoft = Color(0xFF6E8BFF)
    val HellRed = Color(0xFFFF1F3D)
    val Blood = Color(0xFFB3001B)
    val Green = Color(0xFF61CE70)

    val Night = Color(0xFF0D1014)
    val Surface = Color(0xFF15181D)
    val SurfaceHigh = Color(0xFF1C2027)
    val SurfaceHighest = Color(0xFF262B34)
    val Outline = Color(0xFF2B303A)

    val Text = Color(0xFFFFFFFF)
    val TextSecondary = Color(0xFFA3AAB6)
    val TextMuted = Color(0xFF6B7280)

    val Hellfire = Brush.linearGradient(listOf(HellRed, Orange))
    val HellfireVertical = Brush.verticalGradient(listOf(Orange, HellRed))
    val Electric = Brush.linearGradient(listOf(ElectricBlue, Color(0xFF00C2FF)))

    fun difficulty(d: Difficulty): Color = when (d) {
        Difficulty.EASY -> ElectricBlue
        Difficulty.NORMAL -> Orange
        Difficulty.HARD -> HellRed
        Difficulty.NIGHTMARE -> Blood
    }
}

val Jakarta = FontFamily(
    Font(R.font.plus_jakarta_sans_regular, FontWeight.Normal),
    Font(R.font.plus_jakarta_sans_medium, FontWeight.Medium),
    Font(R.font.plus_jakarta_sans_semibold, FontWeight.SemiBold),
    Font(R.font.plus_jakarta_sans_bold, FontWeight.Bold),
    Font(R.font.plus_jakarta_sans_extrabold, FontWeight.ExtraBold),
)

private fun style(size: Int, weight: FontWeight, line: Int, tracking: Float = 0f) = TextStyle(
    fontFamily = Jakarta,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = line.sp,
    letterSpacing = tracking.em,
)

private val AppTypography = Typography(
    displayLarge = style(64, FontWeight.ExtraBold, 68, -0.04f),
    displayMedium = style(48, FontWeight.ExtraBold, 52, -0.03f),
    displaySmall = style(36, FontWeight.ExtraBold, 40, -0.02f),
    headlineLarge = style(30, FontWeight.ExtraBold, 36, -0.02f),
    headlineMedium = style(26, FontWeight.ExtraBold, 32, -0.01f),
    headlineSmall = style(22, FontWeight.Bold, 28),
    titleLarge = style(20, FontWeight.Bold, 26),
    titleMedium = style(16, FontWeight.Bold, 22),
    titleSmall = style(14, FontWeight.Bold, 20),
    bodyLarge = style(16, FontWeight.Normal, 24),
    bodyMedium = style(14, FontWeight.Normal, 20),
    bodySmall = style(12, FontWeight.Normal, 16),
    labelLarge = style(14, FontWeight.Bold, 20, 0.02f),
    labelMedium = style(12, FontWeight.SemiBold, 16, 0.04f),
    labelSmall = style(11, FontWeight.SemiBold, 14, 0.06f),
)

/** Big clock digits that don't jitter as they change. */
val ClockStyle = TextStyle(
    fontFamily = Jakarta,
    fontWeight = FontWeight.ExtraBold,
    letterSpacing = (-0.04).em,
    fontFeatureSettings = "tnum",
)

private val AppColors = darkColorScheme(
    primary = Brand.Orange,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF3A1405),
    onPrimaryContainer = Brand.OrangeSoft,
    secondary = Brand.ElectricBlue,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF0B1B57),
    onSecondaryContainer = Brand.BlueSoft,
    tertiary = Brand.HellRed,
    onTertiary = Color.White,
    background = Brand.Night,
    onBackground = Brand.Text,
    surface = Brand.Surface,
    onSurface = Brand.Text,
    surfaceVariant = Brand.SurfaceHigh,
    onSurfaceVariant = Brand.TextSecondary,
    surfaceContainerLowest = Brand.Night,
    surfaceContainerLow = Brand.Surface,
    surfaceContainer = Brand.Surface,
    surfaceContainerHigh = Brand.SurfaceHigh,
    surfaceContainerHighest = Brand.SurfaceHighest,
    outline = Brand.Outline,
    outlineVariant = Brand.Outline,
    error = Brand.HellRed,
    onError = Color.White,
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

@Composable
fun ShenaasTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = AppColors, typography = AppTypography, shapes = AppShapes, content = content)
}
