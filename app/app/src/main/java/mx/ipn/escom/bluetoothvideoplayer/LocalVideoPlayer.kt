package mx.ipn.escom.bluetoothvideoplayer

import android.net.Uri
import android.widget.MediaController
import android.widget.VideoView
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import java.io.File

@Composable
fun LocalVideoPlayer(
    filePath: String,
    title: String = "",
    privateMode: Boolean = false,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val library = remember(context) {
        PlaybackLibrary(context.applicationContext)
    }

    val displayTitle = title.ifBlank {
        File(filePath).name
            .substringAfter('_')
            .ifBlank {
                "Video recibido"
            }
    }

    val entryKey = PlaybackLibrary.localKey(filePath)

    var favorite by remember(filePath) {
        mutableStateOf(
            library.isFavorite(entryKey)
        )
    }

    var playbackRegistered by remember(filePath) {
        mutableStateOf(false)
    }

    val currentPrivateMode by
        rememberUpdatedState(privateMode)

    Column(
        modifier = modifier.fillMaxWidth()
    ) {
        Text(
            text = displayTitle,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        AndroidView(
            modifier = Modifier
                .fillMaxWidth()
                .height(230.dp),
            factory = { viewContext ->
                VideoView(viewContext).apply {
                    val controls =
                        MediaController(viewContext)

                    controls.setAnchorView(this)
                    setMediaController(controls)
                }
            },
            update = { videoView ->
                if (videoView.tag != filePath) {
                    videoView.tag = filePath

                    videoView.setOnPreparedListener {
                        videoView.start()

                        if (
                            !playbackRegistered &&
                            !currentPrivateMode
                        ) {
                            library.recordPlayback(
                                title = displayTitle,
                                filePath = filePath
                            )

                            playbackRegistered = true
                            favorite =
                                library.isFavorite(entryKey)
                        }
                    }

                    videoView.setVideoURI(
                        Uri.fromFile(File(filePath))
                    )
                }
            }
        )

        Spacer(modifier = Modifier.height(10.dp))

        OutlinedButton(
            onClick = {
                favorite = library.toggleFavorite(
                    key = entryKey,
                    title = displayTitle,
                    subtitle =
                        "Recibido por Bluetooth",
                    filePath = filePath
                )
            },
            enabled = !privateMode,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                if (favorite) {
                    "Quitar de favoritos"
                } else {
                    "Agregar a favoritos"
                }
            )
        }

        if (privateMode) {
            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text =
                    "Modo privado: esta reproducción no se guardará en el historial."
            )
        }
    }
}
