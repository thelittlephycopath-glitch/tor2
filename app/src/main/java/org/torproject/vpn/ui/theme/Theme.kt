package org.torproject.vpn.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import org.torproject.vpn.R

/**
 * Default color scheme for Tor VPN
 */
private val colorScheme = darkColorScheme(
    primary = primary,
    onPrimary = onPrimary,
    primaryContainer = primaryContainer,
    onPrimaryContainer = onPrimaryContainer,
    secondary = secondary,
    onSecondary = onSecondary,
    secondaryContainer = secondaryContainer,
    onSecondaryContainer = onSecondaryContainer,
    tertiary = tertiary,
    onTertiary = onTertiary,
    tertiaryContainer = tertiaryContainer,
    onTertiaryContainer = onTertiaryContainer,
    error = error,
    onError = onError,
    errorContainer = errorContainer,
    onErrorContainer = onErrorContainer,
    background = background,
    onBackground = onBackground,
    surface = surface,
    onSurface = onSurface,
    surfaceVariant = surfaceVariant,
    onSurfaceVariant = onSurfaceVariant,
    outline = outline,
    outlineVariant = outlineVariant,
    scrim = scrim,
    inverseSurface = inverseSurface,
    inverseOnSurface = inverseOnSurface,
    inversePrimary = inversePrimary,
    surfaceContainerLowest = surfaceContainerLowest,
    surfaceContainerLow = surfaceContainerLow,
    surfaceContainer = surfaceContainer,
    surfaceContainerHigh = surfaceContainerHigh,
    surfaceContainerHighest = surfaceContainerHighest
)

private val appFontFamily = FontFamily(Font(R.font.inter_variable))

private val appTypography = Typography().let { type ->
    type.copy(
        displayLarge = type.displayLarge.copy(fontFamily = appFontFamily),
        displayMedium = type.displayMedium.copy(fontFamily = appFontFamily),
        displaySmall = type.displaySmall.copy(fontFamily = appFontFamily),
        headlineLarge = type.headlineLarge.copy(fontFamily = appFontFamily),
        headlineMedium = type.headlineMedium.copy(fontFamily = appFontFamily),
        headlineSmall = type.headlineSmall.copy(fontFamily = appFontFamily),
        titleLarge = type.titleLarge.copy(fontFamily = appFontFamily),
        titleMedium = type.titleMedium.copy(fontFamily = appFontFamily),
        titleSmall = type.titleSmall.copy(fontFamily = appFontFamily),
        bodyLarge = type.bodyLarge.copy(fontFamily = appFontFamily),
        bodyMedium = type.bodyMedium.copy(fontFamily = appFontFamily),
        bodySmall = type.bodySmall.copy(fontFamily = appFontFamily),
        labelLarge = type.labelLarge.copy(fontFamily = appFontFamily),
        labelMedium = type.labelMedium.copy(fontFamily = appFontFamily),
        labelSmall = type.labelSmall.copy(fontFamily = appFontFamily)
    )
}

/**
 * Default app theme for TorVPN
 * @param content Composable content to show to the user
 */
@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = colorScheme,
        typography = appTypography,
        content = content
    )
}
