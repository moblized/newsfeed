package com.moblized.newsfeed.ui

import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val Paper = lightColorScheme(
    primary = Color(0xFFA4282A),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFDAD7),
    onPrimaryContainer = Color(0xFF410006),
    secondary = Color(0xFF5E5C55),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE6E1D6),
    onSecondaryContainer = Color(0xFF1D1C17),
    tertiary = Color(0xFF3E6373),
    onTertiary = Color(0xFFFFFFFF),
    background = Color(0xFFFBF8F2),
    onBackground = Color(0xFF1C1B18),
    surface = Color(0xFFFBF8F2),
    onSurface = Color(0xFF1C1B18),
    surfaceVariant = Color(0xFFEAE5DC),
    onSurfaceVariant = Color(0xFF4A463F),
    outline = Color(0xFF7B766D),
    outlineVariant = Color(0xFFD6D0C4),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF5F1EA),
    surfaceContainer = Color(0xFFF0EBE3),
    surfaceContainerHigh = Color(0xFFEAE5DD),
    surfaceContainerHighest = Color(0xFFE4DFD6),
)

private val Ink = darkColorScheme(
    primary = Color(0xFFFFB3AD),
    onPrimary = Color(0xFF68000F),
    primaryContainer = Color(0xFF85141C),
    onPrimaryContainer = Color(0xFFFFDAD7),
    secondary = Color(0xFFCAC6BC),
    onSecondary = Color(0xFF32302A),
    secondaryContainer = Color(0xFF484640),
    onSecondaryContainer = Color(0xFFE6E2D9),
    tertiary = Color(0xFFA5CCDD),
    onTertiary = Color(0xFF063543),
    background = Color(0xFF141311),
    onBackground = Color(0xFFE7E2DA),
    surface = Color(0xFF141311),
    onSurface = Color(0xFFE7E2DA),
    surfaceVariant = Color(0xFF4A463F),
    onSurfaceVariant = Color(0xFFCDC7BC),
    outline = Color(0xFF969087),
    outlineVariant = Color(0xFF3B3833),
    surfaceContainerLowest = Color(0xFF0F0E0C),
    surfaceContainerLow = Color(0xFF1C1B18),
    surfaceContainer = Color(0xFF211F1C),
    surfaceContainerHigh = Color(0xFF2B2926),
    surfaceContainerHighest = Color(0xFF36342F),
)

private val Serif = FontFamily.Serif

private val AppTypography = Typography().let { t ->
    t.copy(
        displaySmall = t.displaySmall.copy(fontFamily = Serif, fontWeight = FontWeight.Bold),
        headlineMedium = t.headlineMedium.copy(fontFamily = Serif, fontWeight = FontWeight.Bold),
        headlineSmall = t.headlineSmall.copy(fontFamily = Serif, fontWeight = FontWeight.Bold, lineHeight = 30.sp),
        titleLarge = t.titleLarge.copy(fontFamily = Serif, fontWeight = FontWeight.Bold),
        titleMedium = t.titleMedium.copy(fontFamily = Serif, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, lineHeight = 23.sp),
    )
}

@Composable
fun NewsTheme(dark: Boolean, dynamicColor: Boolean, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val colors = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> Ink
        else -> Paper
    }
    MaterialTheme(colorScheme = colors, typography = AppTypography, content = content)
}
