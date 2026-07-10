package mx.ipn.escom.bluetoothvideoplayer.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import mx.ipn.escom.bluetoothvideoplayer.AppAppearance
import mx.ipn.escom.bluetoothvideoplayer.AppPalette

private val GuindaLightColorScheme = lightColorScheme(
    primary = GuindaLightPrimary,
    onPrimary = GuindaLightOnPrimary,
    primaryContainer = GuindaLightPrimaryContainer,
    onPrimaryContainer = GuindaLightOnPrimaryContainer,
    secondary = GuindaLightSecondary,
    background = GuindaLightBackground,
    surface = GuindaLightSurface
)

private val GuindaDarkColorScheme = darkColorScheme(
    primary = GuindaDarkPrimary,
    onPrimary = GuindaDarkOnPrimary,
    primaryContainer = GuindaDarkPrimaryContainer,
    onPrimaryContainer = GuindaDarkOnPrimaryContainer,
    secondary = GuindaDarkSecondary,
    background = GuindaDarkBackground,
    surface = GuindaDarkSurface
)

private val AzulLightColorScheme = lightColorScheme(
    primary = AzulLightPrimary,
    onPrimary = AzulLightOnPrimary,
    primaryContainer = AzulLightPrimaryContainer,
    onPrimaryContainer = AzulLightOnPrimaryContainer,
    secondary = AzulLightSecondary,
    background = AzulLightBackground,
    surface = AzulLightSurface
)

private val AzulDarkColorScheme = darkColorScheme(
    primary = AzulDarkPrimary,
    onPrimary = AzulDarkOnPrimary,
    primaryContainer = AzulDarkPrimaryContainer,
    onPrimaryContainer = AzulDarkOnPrimaryContainer,
    secondary = AzulDarkSecondary,
    background = AzulDarkBackground,
    surface = AzulDarkSurface
)

@Composable
fun BluetoothVideoPlayerTheme(
    palette: AppPalette = AppPalette.GUINDA,
    appearance: AppAppearance = AppAppearance.SYSTEM,
    content: @Composable () -> Unit
) {
    val useDarkTheme = when (appearance) {
        AppAppearance.SYSTEM ->
            isSystemInDarkTheme()

        AppAppearance.LIGHT ->
            false

        AppAppearance.DARK ->
            true
    }

    val colorScheme = when (palette) {
        AppPalette.GUINDA -> {
            if (useDarkTheme) {
                GuindaDarkColorScheme
            } else {
                GuindaLightColorScheme
            }
        }

        AppPalette.AZUL -> {
            if (useDarkTheme) {
                AzulDarkColorScheme
            } else {
                AzulLightColorScheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
