package com.smartspend.ui.theme

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
    primary = PrimaryLight,
    secondary = SecondaryLight,
    background = BackgroundLight,
    surface = SurfaceLight,
)

private val DarkColors = darkColorScheme(
    primary = PrimaryDark,
    secondary = SecondaryDark,
    background = BackgroundDark,
    surface = SurfaceDark,
)

@Composable
fun SmartSpendTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        darkTheme -> DarkColors
        else -> LightColors
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        content = content
    )
}

/** Status color helper used across Budget and Bill modules */
@Composable
fun statusColor(status: com.smartspend.data.models.BudgetProgress.Status) = when (status) {
    com.smartspend.data.models.BudgetProgress.Status.SAFE -> StatusSafe
    com.smartspend.data.models.BudgetProgress.Status.WARNING -> StatusWarning
    com.smartspend.data.models.BudgetProgress.Status.DANGER -> StatusDanger
    com.smartspend.data.models.BudgetProgress.Status.OVER -> StatusOver
}
