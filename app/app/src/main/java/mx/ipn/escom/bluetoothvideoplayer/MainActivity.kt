package mx.ipn.escom.bluetoothvideoplayer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import mx.ipn.escom.bluetoothvideoplayer.ui.theme.BluetoothVideoPlayerTheme

private enum class AppScreen {
    HOME,
    SERVER,
    CLIENT,
    LIBRARY,
    SETTINGS
}

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val preferences = remember {
                AppPreferences(applicationContext)
            }

            var paletteName by rememberSaveable {
                mutableStateOf(
                    preferences.palette.name
                )
            }

            var appearanceName by rememberSaveable {
                mutableStateOf(
                    preferences.appearance.name
                )
            }

            var privateMode by rememberSaveable {
                mutableStateOf(
                    preferences.privateMode
                )
            }

            val palette = runCatching {
                AppPalette.valueOf(paletteName)
            }.getOrDefault(AppPalette.GUINDA)

            val appearance = runCatching {
                AppAppearance.valueOf(
                    appearanceName
                )
            }.getOrDefault(AppAppearance.SYSTEM)

            BluetoothVideoPlayerTheme(
                palette = palette,
                appearance = appearance
            ) {
                BluetoothVideoPlayerApp(
                    palette = palette,
                    appearance = appearance,
                    privateMode = privateMode,
                    onPaletteChanged = { value ->
                        preferences.palette = value
                        paletteName = value.name
                    },
                    onAppearanceChanged = { value ->
                        preferences.appearance = value
                        appearanceName = value.name
                    },
                    onPrivateModeChanged = { value ->
                        preferences.privateMode = value
                        privateMode = value
                    }
                )
            }
        }
    }
}

@Composable
private fun BluetoothVideoPlayerApp(
    palette: AppPalette,
    appearance: AppAppearance,
    privateMode: Boolean,
    onPaletteChanged: (AppPalette) -> Unit,
    onAppearanceChanged: (AppAppearance) -> Unit,
    onPrivateModeChanged: (Boolean) -> Unit
) {
    var currentScreen by rememberSaveable {
        mutableStateOf(AppScreen.HOME.name)
    }

    val returnHome = {
        currentScreen = AppScreen.HOME.name
    }

    when (currentScreen) {
        AppScreen.SERVER.name -> {
            BluetoothSetupScreen(
                role = BluetoothRole.SERVER,
                privateMode = privateMode,
                onBackClick = returnHome
            )
        }

        AppScreen.CLIENT.name -> {
            BluetoothSetupScreen(
                role = BluetoothRole.CLIENT,
                privateMode = privateMode,
                onBackClick = returnHome
            )
        }

        AppScreen.LIBRARY.name -> {
            LibraryScreen(
                privateMode = privateMode,
                onBackClick = returnHome
            )
        }

        AppScreen.SETTINGS.name -> {
            SettingsScreen(
                palette = palette,
                appearance = appearance,
                privateMode = privateMode,
                onPaletteChanged =
                    onPaletteChanged,
                onAppearanceChanged =
                    onAppearanceChanged,
                onPrivateModeChanged =
                    onPrivateModeChanged,
                onBackClick = returnHome
            )
        }

        else -> {
            MainScreen(
                privateMode = privateMode,
                onServerClick = {
                    currentScreen =
                        AppScreen.SERVER.name
                },
                onClientClick = {
                    currentScreen =
                        AppScreen.CLIENT.name
                },
                onLibraryClick = {
                    currentScreen =
                        AppScreen.LIBRARY.name
                },
                onSettingsClick = {
                    currentScreen =
                        AppScreen.SETTINGS.name
                }
            )
        }
    }
}

@Composable
private fun MainScreen(
    privateMode: Boolean,
    onServerClick: () -> Unit,
    onClientClick: () -> Unit,
    onLibraryClick: () -> Unit,
    onSettingsClick: () -> Unit
) {
    Scaffold(
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 32.dp),
            verticalArrangement =
                Arrangement.Center,
            horizontalAlignment =
                Alignment.CenterHorizontally
        ) {
            Text(
                text = "Bluetooth Video Player",
                style =
                    MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text =
                    "Reproductor de video mediante conexión Bluetooth",
                style =
                    MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center
            )

            if (privateMode) {
                Spacer(modifier = Modifier.height(18.dp))

                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text =
                            "Modo privado activado: las reproducciones nuevas no se guardarán.",
                        modifier = Modifier.padding(14.dp),
                        textAlign = TextAlign.Center
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = onServerClick,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Iniciar como servidor")
            }

            Spacer(modifier = Modifier.height(14.dp))

            OutlinedButton(
                onClick = onClientClick,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Iniciar como cliente")
            }

            Spacer(modifier = Modifier.height(14.dp))

            OutlinedButton(
                onClick = onLibraryClick,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Historial y favoritos")
            }

            Spacer(modifier = Modifier.height(14.dp))

            OutlinedButton(
                onClick = onSettingsClick,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Ajustes y privacidad")
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun MainScreenPreview() {
    BluetoothVideoPlayerTheme {
        MainScreen(
            privateMode = false,
            onServerClick = {},
            onClientClick = {},
            onLibraryClick = {},
            onSettingsClick = {}
        )
    }
}
