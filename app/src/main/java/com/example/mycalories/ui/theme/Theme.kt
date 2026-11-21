package com.example.mycalories.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val LightColors = lightColorScheme(
    primary = Green40, onPrimary = androidx.compose.ui.graphics.Color.White,
    primaryContainer = Green90, onPrimaryContainer = Green10,
    secondary = Teal40, onSecondary = androidx.compose.ui.graphics.Color.White,
    secondaryContainer = Teal90, onSecondaryContainer = Green10,
    tertiary = Sand40, tertiaryContainer = Sand90,
    background = Neutral98, onBackground = Neutral10,
    surface = Neutral98, onSurface = Neutral10,
    surfaceVariant = NeutralVariant90, onSurfaceVariant = NeutralVariant30,
    surfaceContainerLowest = androidx.compose.ui.graphics.Color.White,
    surfaceContainerLow = Color98(),
    surfaceContainer = Neutral95,
    outline = NeutralVariant50, outlineVariant = NeutralVariant80,
)

private fun Color98() = androidx.compose.ui.graphics.Color(0xFFF0F4EF)

private val DarkColors = darkColorScheme(
    primary = Green80, onPrimary = Green20,
    primaryContainer = Green30, onPrimaryContainer = Green90,
    secondary = Teal80, secondaryContainer = Teal40,
    tertiary = Sand80,
    background = Neutral10, onBackground = Neutral90,
    surface = Neutral10, onSurface = Neutral90,
    surfaceVariant = NeutralVariant30, onSurfaceVariant = NeutralVariant80,
    surfaceContainer = Neutral20,
    outline = NeutralVariant50, outlineVariant = NeutralVariant30,
)

/**
 * Material 3 theme. Brand colours by default; pass [dynamicColor] = true to follow the
 * wallpaper colours on Android 12+.
 */
@Composable
fun MyCaloriesTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
