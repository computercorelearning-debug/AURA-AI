package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = AuraPrimaryDark,
    onPrimary = Color(0xFF1E1B4B),
    primaryContainer = AuraPrimaryContainer,
    onPrimaryContainer = Color(0xFFE0E7FF),
    secondary = AuraSecondaryDark,
    onSecondary = Color(0xFF083344),
    secondaryContainer = AuraSecondaryContainer,
    onSecondaryContainer = Color(0xFFCFFAFE),
    tertiary = AuraTertiaryDark,
    background = AuraDarkBackground,
    onBackground = AuraDarkOnSurface,
    surface = AuraDarkSurface,
    onSurface = AuraDarkOnSurface,
    surfaceVariant = AuraDarkSurfaceVariant,
    onSurfaceVariant = AuraDarkOnSurfaceVariant
)

private val LightColorScheme = lightColorScheme(
    primary = AuraPrimary,
    onPrimary = Color.White,
    primaryContainer = AuraPrimaryContainerLight,
    onPrimaryContainer = Color(0xFF312E81),
    secondary = AuraSecondary,
    onSecondary = Color.White,
    secondaryContainer = AuraSecondaryContainerLight,
    onSecondaryContainer = Color(0xFF164E63),
    tertiary = AuraTertiary,
    background = AuraLightBackground,
    onBackground = AuraLightOnSurface,
    surface = AuraLightSurface,
    onSurface = AuraLightOnSurface,
    surfaceVariant = AuraLightSurfaceVariant,
    onSurfaceVariant = AuraLightOnSurfaceVariant
)

@Composable
fun AuraTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // Keep distinctive brand theme default
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
