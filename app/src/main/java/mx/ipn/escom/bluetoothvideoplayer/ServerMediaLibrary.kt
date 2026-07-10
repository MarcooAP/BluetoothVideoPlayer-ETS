package mx.ipn.escom.bluetoothvideoplayer

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.security.MessageDigest

data class ServerMediaSelection(
    val uriString: String,
    val displayName: String,
    val declaredSizeBytes: Long
) {
    val uri: Uri
        get() = Uri.parse(uriString)
}

data class PreparedServerMedia(
    val file: File,
    val displayName: String,
    val totalBytes: Long,
    val sha256: String,
    val totalChunks: Int
) {
    fun delete() {
        runCatching { file.delete() }
    }
}

class ServerMediaLibrary(
    private val context: Context
) {

    companion object {
        private const val PREFS_NAME =
            "server_media_library"

        private const val KEY_URI =
            "selected_uri"

        private const val KEY_NAME =
            "selected_name"

        private const val KEY_SIZE =
            "selected_size"

        /*
         * Evita llenar el almacenamiento temporal por accidente.
         * Para una demostración por Bluetooth conviene usar videos
         * cortos y comprimidos.
         */
        const val MAX_MEDIA_BYTES =
            50L * 1024L * 1024L
    }

    private val resolver =
        context.contentResolver

    private val preferences =
        context.getSharedPreferences(
            PREFS_NAME,
            Context.MODE_PRIVATE
        )

    private val preparationDirectory =
        File(
            context.cacheDir,
            "server_media_prepared"
        ).apply {
            mkdirs()
        }

    fun loadSelection(): ServerMediaSelection? {
        val uriString =
            preferences.getString(
                KEY_URI,
                null
            ) ?: return null

        val displayName =
            preferences.getString(
                KEY_NAME,
                null
            ) ?: "video_seleccionado.mp4"

        val size =
            preferences.getLong(
                KEY_SIZE,
                -1L
            )

        return ServerMediaSelection(
            uriString = uriString,
            displayName = displayName,
            declaredSizeBytes = size
        )
    }

    fun saveSelection(
        uri: Uri
    ): ServerMediaSelection {
        val metadata =
            readMetadata(uri)

        val selection =
            ServerMediaSelection(
                uriString = uri.toString(),
                displayName = metadata.first,
                declaredSizeBytes = metadata.second
            )

        preferences.edit()
            .putString(
                KEY_URI,
                selection.uriString
            )
            .putString(
                KEY_NAME,
                selection.displayName
            )
            .putLong(
                KEY_SIZE,
                selection.declaredSizeBytes
            )
            .apply()

        return selection
    }

    fun clearSelection() {
        preferences.edit()
            .clear()
            .apply()
    }

    /**
     * Copia el URI seleccionado a la caché del servidor mientras
     * calcula SHA-256. Así la transferencia posterior usa un archivo
     * local estable, aunque el proveedor de documentos sea lento.
     */
    fun prepare(
        selection: ServerMediaSelection,
        onProgress: (copiedBytes: Long, expectedBytes: Long) -> Unit =
            { _, _ -> }
    ): PreparedServerMedia {
        preparationDirectory
            .listFiles()
            ?.forEach { staleFile ->
                runCatching { staleFile.delete() }
            }

        val safeName =
            selection.displayName
                .replace(
                    Regex("[^A-Za-z0-9._-]"),
                    "_"
                )
                .ifBlank {
                    "video_seleccionado.mp4"
                }

        val target =
            File(
                preparationDirectory,
                "${System.currentTimeMillis()}_$safeName"
            )

        val digest =
            MessageDigest.getInstance(
                "SHA-256"
            )

        var copiedBytes = 0L
        val expectedBytes =
            selection.declaredSizeBytes

        try {
            resolver.openInputStream(
                selection.uri
            )?.use { rawInput ->
                BufferedInputStream(
                    rawInput
                ).use { input ->
                    BufferedOutputStream(
                        target.outputStream()
                    ).use { output ->
                        val buffer =
                            ByteArray(64 * 1024)

                        while (true) {
                            val read =
                                input.read(buffer)

                            if (read < 0) {
                                break
                            }

                            if (read == 0) {
                                continue
                            }

                            copiedBytes += read

                            if (
                                copiedBytes >
                                MAX_MEDIA_BYTES
                            ) {
                                throw IllegalArgumentException(
                                    "El video supera el límite de " +
                                        "${MAX_MEDIA_BYTES / 1024 / 1024} MB."
                                )
                            }

                            output.write(
                                buffer,
                                0,
                                read
                            )

                            digest.update(
                                buffer,
                                0,
                                read
                            )

                            onProgress(
                                copiedBytes,
                                expectedBytes
                            )
                        }

                        output.flush()
                    }
                }
            } ?: error(
                "No se pudo abrir el video seleccionado."
            )

            if (copiedBytes <= 0L) {
                error(
                    "El video seleccionado está vacío."
                )
            }

            val sha256 =
                digest.digest()
                    .joinToString(
                        separator = ""
                    ) { byte ->
                        "%02X".format(byte)
                    }

            val totalChunks =
                (
                    (
                        copiedBytes +
                            MediaTransferUtils
                                .CHUNK_DATA_SIZE -
                            1L
                        ) /
                        MediaTransferUtils
                            .CHUNK_DATA_SIZE
                    ).toInt()

            return PreparedServerMedia(
                file = target,
                displayName = safeName,
                totalBytes = copiedBytes,
                sha256 = sha256,
                totalChunks = totalChunks
            )
        } catch (error: Exception) {
            runCatching {
                target.delete()
            }

            throw error
        }
    }

    private fun readMetadata(
        uri: Uri
    ): Pair<String, Long> {
        var displayName =
            "video_seleccionado.mp4"

        var sizeBytes =
            -1L

        resolver.query(
            uri,
            arrayOf(
                OpenableColumns.DISPLAY_NAME,
                OpenableColumns.SIZE
            ),
            null,
            null,
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex =
                    cursor.getColumnIndex(
                        OpenableColumns
                            .DISPLAY_NAME
                    )

                val sizeIndex =
                    cursor.getColumnIndex(
                        OpenableColumns.SIZE
                    )

                if (
                    nameIndex >= 0 &&
                    !cursor.isNull(
                        nameIndex
                    )
                ) {
                    displayName =
                        cursor.getString(
                            nameIndex
                        )
                            ?.takeIf {
                                it.isNotBlank()
                            }
                            ?: displayName
                }

                if (
                    sizeIndex >= 0 &&
                    !cursor.isNull(
                        sizeIndex
                    )
                ) {
                    sizeBytes =
                        cursor.getLong(
                            sizeIndex
                        )
                }
            }
        }

        return displayName to sizeBytes
    }
}

fun transferPreparedMedia(
    connectionManager:
        BluetoothConnectionManager,
    preparedMedia:
        PreparedServerMedia,
    onProgress:
        (
            sentBytes: Long,
            totalBytes: Long,
            percent: Int
        ) -> Unit = { _, _, _ -> }
) {
    connectionManager
        .sendMessageAwait(
            BluetoothProtocol.mediaStart(
                fileName =
                    preparedMedia.displayName,
                totalBytes =
                    preparedMedia.totalBytes,
                sha256 =
                    preparedMedia.sha256,
                totalChunks =
                    preparedMedia.totalChunks
            )
        )
        .getOrThrow()

    val buffer =
        ByteArray(
            MediaTransferUtils
                .CHUNK_DATA_SIZE
        )

    var sequence = 0
    var sentBytes = 0L

    preparedMedia.file
        .inputStream()
        .buffered()
        .use { input ->
            while (true) {
                val read =
                    input.read(buffer)

                if (read < 0) {
                    break
                }

                if (read == 0) {
                    continue
                }

                val payload =
                    MediaTransferUtils
                        .wrapChunk(
                            sequence =
                                sequence,
                            source =
                                buffer,
                            length =
                                read
                        )

                connectionManager
                    .sendBinaryAwait(
                        payload
                    )
                    .getOrThrow()

                sentBytes += read
                sequence += 1

                val percent =
                    (
                        sentBytes *
                            100L /
                            preparedMedia
                                .totalBytes
                        )
                        .coerceIn(
                            0L,
                            100L
                        )
                        .toInt()

                onProgress(
                    sentBytes,
                    preparedMedia
                        .totalBytes,
                    percent
                )
            }
        }

    if (
        sequence !=
        preparedMedia.totalChunks
    ) {
        error(
            "La cantidad de bloques enviados no coincide."
        )
    }

    connectionManager
        .sendMessageAwait(
            BluetoothProtocol.mediaEnd()
        )
        .getOrThrow()
}
