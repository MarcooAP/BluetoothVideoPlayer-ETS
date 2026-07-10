package mx.ipn.escom.bluetoothvideoplayer

import android.net.Uri
import android.widget.MediaController
import android.widget.VideoView
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import java.io.File

@Composable
fun LocalVideoPlayer(
    filePath: String,
    modifier: Modifier = Modifier
) {
    AndroidView(
        modifier = modifier
            .fillMaxWidth()
            .height(230.dp),
        factory = { context ->
            VideoView(context).apply {
                val controls = MediaController(context)
                controls.setAnchorView(this)
                setMediaController(controls)
            }
        },
        update = { videoView ->
            if (videoView.tag != filePath) {
                videoView.tag = filePath
                videoView.setVideoURI(
                    Uri.fromFile(File(filePath))
                )
                videoView.setOnPreparedListener {
                    videoView.start()
                }
            }
        }
    )
}
