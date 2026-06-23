package com.punchlist.pocket.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

// Surface tones not declared in Color.kt.
private val SecondaryContainerLight = Color(0xFFFFE3C2)
private val SecondaryContainerDark = Color(0xFF6B3A00)

private val LightColors = lightColorScheme(
    primary = PunchBlue,
    onPrimary = LightOnPrimary,
    primaryContainer = PunchBlueLight,
    onPrimaryContainer = PunchBlueDark,
    secondary = PunchOrange,
    onSecondary = LightOnSecondary,
    secondaryContainer = SecondaryContainerLight,
    onSecondaryContainer = PunchOrangeDark,
    background = LightBackground,
    onBackground = LightOnBackground,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnBackground,
    outline = LightOutline
)

private val DarkColors = darkColorScheme(
    primary = PunchBlueLight,
    onPrimary = DarkOnPrimary,
    primaryContainer = PunchBlueDark,
    onPrimaryContainer = PunchBlueLight,
    secondary = PunchOrange,
    onSecondary = DarkOnSecondary,
    secondaryContainer = SecondaryContainerDark,
    onSecondaryContainer = SecondaryContainerLight,
    background = DarkBackground,
    onBackground = DarkOnBackground,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnBackground,
    outline = DarkOutline
)

/**
 * App-wide corner radii. Material You dynamic color is intentionally disabled
 * (see MainActivity) so these shapes + the brand palette stay consistent.
 */
val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun PunchListTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Material You dynamic color. Disabled by default so the brand blue palette
    // stays consistent; MainActivity passes false explicitly.
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    // When dynamic color is requested (API 31+) use the system palette; otherwise
    // fall back to the fixed brand scheme.
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }
    val palette = if (darkTheme) DarkStatusPalette else LightStatusPalette

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.surface.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    CompositionLocalProvider(LocalStatusPalette provides palette) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = AppTypography,
            shapes = AppShapes,
            content = content
        )
    }
}
