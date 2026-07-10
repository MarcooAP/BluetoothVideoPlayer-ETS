package mx.ipn.escom.bluetoothvideoplayer

import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class LibrarySection {
    HISTORY,
    FAVORITES
}

@Composable
fun LibraryScreen(
    privateMode: Boolean,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val library = remember(context) {
        PlaybackLibrary(context.applicationContext)
    }

    var selectedSection by rememberSaveable {
        mutableStateOf(
            LibrarySection.HISTORY.name
        )
    }

    var revision by remember {
        mutableStateOf(0)
    }

    var selectedVideoPath by rememberSaveable {
        mutableStateOf<String?>(null)
    }

    var selectedVideoTitle by rememberSaveable {
        mutableStateOf("")
    }

    val showingFavorites =
        selectedSection ==
            LibrarySection.FAVORITES.name

    val entries = remember(
        selectedSection,
        revision
    ) {
        if (showingFavorites) {
            library.favorites()
        } else {
            library.history()
        }
    }

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
                text = "Biblioteca",
                style =
                    MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text =
                    "Historial y favoritos guardados en este teléfono.",
                textAlign = TextAlign.Center
            )

            if (privateMode) {
                Spacer(modifier = Modifier.height(12.dp))

                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text =
                            "El modo privado está activo. Puedes consultar lo ya guardado, pero las reproducciones nuevas no se registrarán.",
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            if (!showingFavorites) {
                Button(
                    onClick = {
                        selectedSection =
                            LibrarySection.HISTORY.name
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Historial")
                }

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedButton(
                    onClick = {
                        selectedSection =
                            LibrarySection.FAVORITES.name
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Favoritos")
                }
            } else {
                OutlinedButton(
                    onClick = {
                        selectedSection =
                            LibrarySection.HISTORY.name
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Historial")
                }

                Spacer(modifier = Modifier.height(10.dp))

                Button(
                    onClick = {
                        selectedSection =
                            LibrarySection.FAVORITES.name
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Favoritos")
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            if (entries.isEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = if (showingFavorites) {
                            "Todavía no hay videos favoritos."
                        } else {
                            "Todavía no hay reproducciones en el historial."
                        },
                        modifier = Modifier.padding(18.dp),
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                entries.forEach { entry ->
                    LibraryEntryCard(
                        entry = entry,
                        privateMode = privateMode,
                        onPlayClick = {
                            selectedVideoPath =
                                entry.filePath
                            selectedVideoTitle =
                                entry.title
                        },
                        onFavoriteClick = {
                            library.toggleFavorite(
                                key = entry.key,
                                title = entry.title,
                                subtitle = entry.subtitle,
                                filePath = entry.filePath
                            )
                            revision += 1
                        },
                        onRemoveClick = {
                            library.remove(entry.key)

                            if (
                                selectedVideoPath ==
                                entry.filePath
                            ) {
                                selectedVideoPath = null
                                selectedVideoTitle = ""
                            }

                            revision += 1
                        }
                    )

                    Spacer(modifier = Modifier.height(12.dp))
                }
            }

            if (entries.isNotEmpty()) {
                OutlinedButton(
                    onClick = {
                        if (showingFavorites) {
                            library.clearFavorites()
                        } else {
                            library.clearHistory()
                        }

                        selectedVideoPath = null
                        selectedVideoTitle = ""
                        revision += 1
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        if (showingFavorites) {
                            "Vaciar favoritos"
                        } else {
                            "Vaciar historial"
                        }
                    )
                }
            }

            val playablePath =
                selectedVideoPath
                    ?.takeIf {
                        File(it).exists()
                    }

            if (playablePath != null) {
                Spacer(modifier = Modifier.height(24.dp))

                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = "Reproducción local",
                            style =
                                MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        LocalVideoPlayer(
                            filePath = playablePath,
                            title = selectedVideoTitle,
                            privateMode = privateMode
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
private fun LibraryEntryCard(
    entry: PlaybackEntry,
    privateMode: Boolean,
    onPlayClick: () -> Unit,
    onFavoriteClick: () -> Unit,
    onRemoveClick: () -> Unit
) {
    val playable =
        entry.filePath.isNotBlank() &&
            File(entry.filePath).exists()

    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = entry.title,
                style =
                    MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            if (entry.subtitle.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = entry.subtitle,
                    style =
                        MaterialTheme.typography.bodyMedium
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = if (entry.playedAt > 0L) {
                    "Última reproducción: ${formatEntryDate(entry.playedAt)}"
                } else {
                    "Guardado: ${formatEntryDate(entry.updatedAt)}"
                },
                style = MaterialTheme.typography.bodySmall
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = when {
                    playable ->
                        "Archivo disponible en este teléfono."

                    entry.filePath.isBlank() ->
                        "Favorito guardado desde una búsqueda de YouTube."

                    else ->
                        "El archivo temporal ya no está disponible."
                },
                style = MaterialTheme.typography.bodySmall
            )

            if (playable) {
                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = onPlayClick,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Reproducir")
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedButton(
                onClick = onFavoriteClick,
                enabled = !privateMode,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    if (entry.favorite) {
                        "Quitar de favoritos"
                    } else {
                        "Agregar a favoritos"
                    }
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            TextButton(
                onClick = onRemoveClick,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Eliminar de la biblioteca")
            }
        }
    }
}

private fun formatEntryDate(
    timestamp: Long
): String {
    if (timestamp <= 0L) {
        return "sin fecha"
    }

    return SimpleDateFormat(
        "dd/MM/yyyy HH:mm",
        Locale.getDefault()
    ).format(Date(timestamp))
}
