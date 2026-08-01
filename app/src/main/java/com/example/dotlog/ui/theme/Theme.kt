package com.example.dotlog.ui.theme

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
    primary = Primary80,
    onPrimary = PrimaryContainer30,
    primaryContainer = PrimaryContainer30,
    onPrimaryContainer = PrimaryContainer90,
    secondary = Secondary80,
    onSecondary = SecondaryContainer30,
    secondaryContainer = SecondaryContainer30,
    onSecondaryContainer = SecondaryContainer90,
    tertiary = Tertiary80,
    onTertiary = TertiaryContainer30,
    tertiaryContainer = TertiaryContainer30,
    onTertiaryContainer = TertiaryContainer90,
    error = Error80,
    onError = Error40,
    errorContainer = Error40,
    onErrorContainer = Error80,
    background = Neutral10,
    onBackground = Neutral90,
    surface = Neutral10,
    onSurface = Neutral90,
    surfaceVariant = NeutralVariant30,
    onSurfaceVariant = NeutralVariant90,
    outline = NeutralVariant30
)

private val LightColorScheme = lightColorScheme(
    primary = Primary40,
    onPrimary = Color.White,
    primaryContainer = PrimaryContainer90,
    onPrimaryContainer = PrimaryContainer30,
    secondary = Secondary40,
    onSecondary = Color.White,
    secondaryContainer = SecondaryContainer90,
    onSecondaryContainer = SecondaryContainer30,
    tertiary = Tertiary40,
    onTertiary = Color.White,
    tertiaryContainer = TertiaryContainer90,
    onTertiaryContainer = TertiaryContainer30,
    error = Error40,
    onError = Color.White,
    errorContainer = Error80,
    onErrorContainer = Error40,
    background = Neutral90,
    onBackground = Neutral10,
    surface = Color.White,
    onSurface = Neutral10,
    surfaceVariant = NeutralVariant90,
    onSurfaceVariant = NeutralVariant30,
    outline = NeutralVariant30
)

@Composable
fun DotlogTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
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
