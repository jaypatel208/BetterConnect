package dev.jay.betterconnect.core.designsystem.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext

private val LightColors = lightColorScheme(
    primary = Palette.Amber40,
    onPrimary = androidx.compose.ui.graphics.Color.White,
    primaryContainer = Palette.Amber90,
    onPrimaryContainer = Palette.Amber10,
    secondary = Palette.Teal40,
    secondaryContainer = Palette.Teal90,
    error = Palette.Red40,
    errorContainer = Palette.Red90,
    background = Palette.Neutral99,
    surface = Palette.Neutral99,
    surfaceVariant = Palette.Neutral95,
)

private val DarkColors = darkColorScheme(
    primary = Palette.Amber80,
    onPrimary = Palette.Amber20,
    primaryContainer = Palette.Amber30,
    onPrimaryContainer = Palette.Amber90,
    secondary = Palette.Teal80,
    secondaryContainer = Palette.Teal40,
    error = Palette.Red80,
    background = Palette.Neutral10,
    surface = Palette.Neutral10,
    surfaceVariant = Palette.Neutral20,
)

@Composable
fun BetterConnectTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
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

    MaterialTheme(
        colorScheme = colorScheme,
        typography = BetterConnectTypography,
    ) {
        // Without this, screens paint theme-correct text onto whatever the window background
        // happens to be - which for this Activity's XML theme is permanently white, so dark
        // mode's light `onSurface` text was invisible. This is the actual screen paint.
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            content()
        }
    }
}
