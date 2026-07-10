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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
    CLIENT
}

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            BluetoothVideoPlayerTheme {
                BluetoothVideoPlayerApp()
            }
        }
    }
}

@Composable
private fun BluetoothVideoPlayerApp() {
    var currentScreen by rememberSaveable {
        mutableStateOf(AppScreen.HOME.name)
    }

    when (currentScreen) {
        AppScreen.SERVER.name -> {
            ServerScreen(
                onBackClick = {
                    currentScreen = AppScreen.HOME.name
                }
            )
        }

        AppScreen.CLIENT.name -> {
            ClientScreen(
                onBackClick = {
                    currentScreen = AppScreen.HOME.name
                }
            )
        }

        else -> {
            MainScreen(
                onServerClick = {
                    currentScreen = AppScreen.SERVER.name
                },
                onClientClick = {
                    currentScreen = AppScreen.CLIENT.name
                }
            )
        }
    }
}

@Composable
private fun MainScreen(
    onServerClick: () -> Unit,
    onClientClick: () -> Unit
) {
    Scaffold(
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Bluetooth Video Player",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Reproductor de video mediante conexión Bluetooth",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(40.dp))

            Button(
                onClick = onServerClick,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Iniciar como servidor")
            }

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedButton(
                onClick = onClientClick,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Iniciar como cliente")
            }
        }
    }
}

@Composable
private fun ServerScreen(
    onBackClick: () -> Unit
) {
    BluetoothSetupScreen(
        role = BluetoothRole.SERVER,
        onBackClick = onBackClick
    )
}

@Composable
private fun ClientScreen(
    onBackClick: () -> Unit
) {
    BluetoothSetupScreen(
        role = BluetoothRole.CLIENT,
        onBackClick = onBackClick
    )
}


@Preview(showBackground = true)
@Composable
private fun MainScreenPreview() {
    BluetoothVideoPlayerTheme {
        MainScreen(
            onServerClick = {},
            onClientClick = {}
        )
    }
}