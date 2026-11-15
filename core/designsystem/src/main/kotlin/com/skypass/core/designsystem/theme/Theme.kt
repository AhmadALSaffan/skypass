package com.skypass.core.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf

internal val LocalSkypassColors = staticCompositionLocalOf { LightSkypassColors }
internal val LocalSkypassTypography = staticCompositionLocalOf { SkypassTypography() }

/**
 * Skypass theme: board tokens on top of Material 3. Features read `SkypassTheme.colors` /
 * `SkypassTheme.typography`; the M3 scheme is mapped from the same tokens so stock components
 * (date picker, sheets, snackbars) match.
 */
@Composable
fun SkypassTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) DarkSkypassColors else LightSkypassColors
    val typography = SkypassTypography()
    CompositionLocalProvider(
        LocalSkypassColors provides colors,
        LocalSkypassTypography provides typography,
    ) {
        MaterialTheme(
            colorScheme = colors.toMaterialColorScheme(),
            typography = typography.toMaterialTypography(),
            content = content,
        )
    }
}

object SkypassTheme {
    val colors: SkypassColors
        @Composable @ReadOnlyComposable get() = LocalSkypassColors.current

    val typography: SkypassTypography
        @Composable @ReadOnlyComposable get() = LocalSkypassTypography.current
}

private fun SkypassColors.toMaterialColorScheme(): ColorScheme {
    val base = if (isDark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = ink,
        onPrimary = onInk,
        primaryContainer = mint,
        onPrimaryContainer = brandDeep,
        secondary = brand,
        onSecondary = onBrand,
        tertiary = brand,
        background = bg,
        onBackground = ink,
        surface = surface,
        onSurface = ink,
        surfaceVariant = surfaceSunken,
        onSurfaceVariant = text2,
        surfaceContainerLowest = surface,
        surfaceContainerLow = surface,
        surfaceContainer = surface,
        surfaceContainerHigh = surface,
        surfaceContainerHighest = surfaceSunken,
        outline = borderStrong,
        outlineVariant = border,
        error = danger,
        scrim = scrim,
    )
}

private fun SkypassTypography.toMaterialTypography() = Typography(
    displayLarge = displayLg,
    displayMedium = display,
    displaySmall = headline,
    headlineLarge = headline,
    headlineMedium = figure,
    headlineSmall = titleLg,
    titleLarge = topBar,
    titleMedium = titleSm,
    titleSmall = label,
    bodyLarge = body,
    bodyMedium = bodySm,
    bodySmall = caption,
    labelLarge = label,
    labelMedium = labelSm,
    labelSmall = pill,
)
