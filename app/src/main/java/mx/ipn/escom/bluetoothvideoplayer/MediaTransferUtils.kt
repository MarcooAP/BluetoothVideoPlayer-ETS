package mx.ipn.escom.bluetoothvideoplayer

import android.content.Context
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.File
import java.io.OutputStream
import java.nio.ByteBuffer
import java.security.MessageDigest

object MediaTransferUtils {

    const val CHUNK_DATA_SIZE = 32 * 1024
    private const val CHUNK_HEADER_SIZE = 4

    fun sha256(data: ByteArray): String {
        val digest = MessageDigest
            .getInstance("SHA-256")
            .digest(data)

        return digest.joinToString(separator = "") { byte ->
            "%02X".format(byte)
        }
    }

    fun wrapChunk(
        sequence: Int,
        source: ByteArray,
        length: Int
    ): ByteArray {
        require(sequence >= 0) {
            "La secuencia no puede ser negativa."
        }
        require(length in 1..source.size) {
            "Longitud de bloque inválida."
        }

        val payload = ByteArray(CHUNK_HEADER_SIZE + length)
        ByteBuffer.wrap(payload)
            .putInt(sequence)
            .put(source, 0, length)

        return payload
    }

    fun readSequence(payload: ByteArray): Int {
        require(payload.size > CHUNK_HEADER_SIZE) {
            "Bloque multimedia incompleto."
        }

        return ByteBuffer.wrap(payload, 0, CHUNK_HEADER_SIZE)
            .int
    }

    fun chunkData(payload: ByteArray): ByteArray {
        require(payload.size > CHUNK_HEADER_SIZE) {
            "Bloque multimedia incompleto."
        }

        return payload.copyOfRange(
            CHUNK_HEADER_SIZE,
            payload.size
        )
    }
}

data class MediaReceiveProgress(
    val receivedBytes: Long,
    val totalBytes: Long,
    val percent: Int,
    val receivedChunks: Int,
    val totalChunks: Int
)

data class MediaReceiveResult(
    val success: Boolean,
    val file: File?,
    val receivedBytes: Long,
    val sha256: String,
    val message: String
)

class IncomingMediaReceiver(
    context: Context
) {

    private val mediaDirectory = File(
        context.cacheDir,
        "bluetooth_media"
    ).apply {
        mkdirs()
    }

    private var output: OutputStream? = null
    private var targetFile: File? = null
    private var digest: MessageDigest? = null
    private var expectedBytes: Long = 0L
    private var expectedSha256: String = ""
    private var expectedChunks: Int = 0
    private var nextSequence: Int = 0
    private var receivedBytes: Long = 0L

    val isActive: Boolean
        get() = output != null

    fun start(
        fileName: String,
        totalBytes: Long,
        sha256: String,
        totalChunks: Int
    ) {
        require(totalBytes > 0L) {
            "El archivo anunciado está vacío."
        }
        require(totalChunks > 0) {
            "La cantidad de bloques es inválida."
        }

        cancel(deletePartialFile = true)

        val safeName = fileName
            .replace(Regex("[^A-Za-z0-9._-]"), "_")
            .ifBlank { "video_recibido.mp4" }

        val file = File(
            mediaDirectory,
            "${System.currentTimeMillis()}_$safeName"
        )

        targetFile = file
        output = BufferedOutputStream(file.outputStream())
        digest = MessageDigest.getInstance("SHA-256")
        expectedBytes = totalBytes
        expectedSha256 = sha256
        expectedChunks = totalChunks
        nextSequence = 0
        receivedBytes = 0L
    }

    fun append(payload: ByteArray): MediaReceiveProgress {
        val activeOutput = output
            ?: error("No hay una transferencia multimedia activa.")

        val sequence = MediaTransferUtils.readSequence(payload)
        require(sequence == nextSequence) {
            "Se esperaba el bloque $nextSequence y llegó el $sequence."
        }

        val data = MediaTransferUtils.chunkData(payload)
        activeOutput.write(data)
        digest?.update(data)

        receivedBytes += data.size
        nextSequence += 1

        val percent = (
            (receivedBytes * 100L) / expectedBytes
        ).coerceIn(0L, 100L).toInt()

        return MediaReceiveProgress(
            receivedBytes = receivedBytes,
            totalBytes = expectedBytes,
            percent = percent,
            receivedChunks = nextSequence,
            totalChunks = expectedChunks
        )
    }

    fun finish(): MediaReceiveResult {
        val file = targetFile
            ?: return failure("No existe un archivo en recepción.")

        return try {
            output?.flush()
            output?.close()
            output = null

            val actualSha256 = digest
                ?.digest()
                ?.joinToString(separator = "") { byte ->
                    "%02X".format(byte)
                }
                .orEmpty()

            val correctSize = receivedBytes == expectedBytes
            val correctChunks = nextSequence == expectedChunks
            val correctHash = actualSha256.equals(
                expectedSha256,
                ignoreCase = true
            )

            if (correctSize && correctChunks && correctHash) {
                MediaReceiveResult(
                    success = true,
                    file = file,
                    receivedBytes = receivedBytes,
                    sha256 = actualSha256,
                    message =
                        "MP4 recibido y verificado: ${receivedBytes / 1024} KB."
                )
            } else {
                file.delete()

                MediaReceiveResult(
                    success = false,
                    file = null,
                    receivedBytes = receivedBytes,
                    sha256 = actualSha256,
                    message =
                        "El archivo llegó incompleto o no superó la verificación."
                )
            }
        } catch (error: Exception) {
            file.delete()
            failure(
                error.message ?: "Error al cerrar el archivo recibido."
            )
        } finally {
            resetState()
        }
    }

    fun cancel(deletePartialFile: Boolean = true) {
        runCatching { output?.close() }

        if (deletePartialFile) {
            runCatching { targetFile?.delete() }
        }

        resetState()
    }

    private fun failure(message: String): MediaReceiveResult {
        return MediaReceiveResult(
            success = false,
            file = null,
            receivedBytes = receivedBytes,
            sha256 = "",
            message = message
        )
    }

    private fun resetState() {
        output = null
        targetFile = null
        digest = null
        expectedBytes = 0L
        expectedSha256 = ""
        expectedChunks = 0
        nextSequence = 0
        receivedBytes = 0L
    }
}
