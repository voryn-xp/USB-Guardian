package com.vorynxp.usbguardian.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = NeonBlue,
    secondary = AccentCyan,
    background = MidnightBlue,
    surface = DeepNavy,
    onPrimary = MidnightBlue,
    onSecondary = MidnightBlue,
    onBackground = IceWhite,
    onSurface = IceWhite,
    error = CrimsonRed
)

private val LightColorScheme = lightColorScheme(
    primary = OceanBlue,
    secondary = AccentCyan,
    background = IceWhite,
    surface = PureWhite,
    onPrimary = PureWhite,
    onSecondary = PureWhite,
    onBackground = DarkNavy,
    onSurface = DarkNavy,
    error = LightDanger
)

@Composable
fun USBGuardianTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true, // Material You dynamic colors
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

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val context = view.context
            if (context is Activity) {
                val window = context.window
                window.statusBarColor = colorScheme.background.toArgb()
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
