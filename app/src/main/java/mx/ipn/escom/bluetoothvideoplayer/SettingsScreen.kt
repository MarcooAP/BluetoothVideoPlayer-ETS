package mx.ipn.escom.bluetoothvideoplayer

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
fun SettingsScreen(
    palette: AppPalette,
    appearance: AppAppearance,
    privateMode: Boolean,
    onPaletteChanged: (AppPalette) -> Unit,
    onAppearanceChanged: (AppAppearance) -> Unit,
    onPrivateModeChanged: (Boolean) -> Unit,
    onBackClick: () -> Unit
) {
    Scaffold(
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment =
                Alignment.CenterHorizontally
        ) {
            Text(
                text = "Ajustes",
                style =
                    MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(24.dp))

            SettingCard(
                title = "Color de la aplicación",
                description =
                    "Selecciona la identidad visual principal."
            ) {
                ChoiceButton(
                    text = "Guinda IPN",
                    selected =
                        palette == AppPalette.GUINDA,
                    onClick = {
                        onPaletteChanged(
                            AppPalette.GUINDA
                        )
                    }
                )

                Spacer(modifier = Modifier.height(10.dp))

                ChoiceButton(
                    text = "Azul",
                    selected =
                        palette == AppPalette.AZUL,
                    onClick = {
                        onPaletteChanged(
                            AppPalette.AZUL
                        )
                    }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            SettingCard(
                title = "Apariencia",
                description =
                    "Elige el modo claro, oscuro o el del sistema."
            ) {
                ChoiceButton(
                    text = "Usar configuración del sistema",
                    selected =
                        appearance == AppAppearance.SYSTEM,
                    onClick = {
                        onAppearanceChanged(
                            AppAppearance.SYSTEM
                        )
                    }
                )

                Spacer(modifier = Modifier.height(10.dp))

                ChoiceButton(
                    text = "Modo claro",
                    selected =
                        appearance == AppAppearance.LIGHT,
                    onClick = {
                        onAppearanceChanged(
                            AppAppearance.LIGHT
                        )
                    }
                )

                Spacer(modifier = Modifier.height(10.dp))

                ChoiceButton(
                    text = "Modo oscuro",
                    selected =
                        appearance == AppAppearance.DARK,
                    onClick = {
                        onAppearanceChanged(
                            AppAppearance.DARK
                        )
                    }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(18.dp)
                ) {
                    Text(
                        text = "Modo privado",
                        style =
                            MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text =
                            "Al activarlo, las reproducciones nuevas no se guardan en el historial y se deshabilita el guardado de favoritos durante esa sesión."
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment =
                            Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (privateMode) {
                                "Activado"
                            } else {
                                "Desactivado"
                            },
                            modifier =
                                Modifier.fillMaxWidth(0.75f),
                            fontWeight = FontWeight.Bold
                        )

                        Switch(
                            checked = privateMode,
                            onCheckedChange =
                                onPrivateModeChanged
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            TextButton(
                onClick = onBackClick
            ) {
                Text("Volver al inicio")
            }
        }
    }
}

@Composable
private fun SettingCard(
    title: String,
    description: String,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(18.dp)
        ) {
            Text(
                text = title,
                style =
                    MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = description,
                textAlign = TextAlign.Start
            )

            Spacer(modifier = Modifier.height(14.dp))

            content()
        }
    }
}

@Composable
private fun ChoiceButton(
    text: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    if (selected) {
        Button(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("$text ✓")
        }
    } else {
        OutlinedButton(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text)
        }
    }
}
