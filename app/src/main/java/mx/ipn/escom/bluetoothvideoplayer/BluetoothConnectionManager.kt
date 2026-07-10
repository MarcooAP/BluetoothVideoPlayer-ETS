package mx.ipn.escom.bluetoothvideoplayer

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.os.Handler
import android.os.Looper
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

class BluetoothConnectionManager(
    private val bluetoothAdapter: BluetoothAdapter
) {

    companion object {
        private const val SERVICE_NAME =
            "BluetoothVideoPlayerETS"

        val SERVICE_UUID: UUID =
            UUID.fromString(
                "7a51f5d0-86a4-4d8b-aed6-24a06477c721"
            )

        /*
         * BVP1 = Bluetooth Video Player, versión 1.
         * Sirve para detectar datos dañados o incompatibles.
         */
        private const val FRAME_MAGIC =
            0x42565031

        private const val PROTOCOL_VERSION =
            1

        private const val FRAME_TYPE_TEXT =
            1

        private const val FRAME_TYPE_BINARY =
            2

        /*
         * Cada fragmento individual puede medir como máximo
         * 512 KB. Para video usaremos bloques mucho menores.
         */
        private const val MAX_FRAME_SIZE =
            512 * 1024
    }

    private val mainHandler =
        Handler(Looper.getMainLooper())

    private val sessionCounter =
        AtomicInteger(0)

    private val outputLock =
        Any()

    @Volatile
    private var serverSocket:
            BluetoothServerSocket? = null

    @Volatile
    private var connectedSocket:
            BluetoothSocket? = null

    @Volatile
    private var connectedInput:
            DataInputStream? = null

    @Volatile
    private var connectedOutput:
            DataOutputStream? = null

    @Volatile
    private var connectionThread:
            Thread? = null

    /**
     * Inicia el dispositivo como servidor RFCOMM.
     *
     * onMessageReceived recibe mensajes JSON o texto.
     * onBinaryReceived recibirá posteriormente bloques de video.
     */
    @SuppressLint("MissingPermission")
    fun startServer(
        onStatusChanged: (String) -> Unit,
        onMessageReceived: (String) -> Unit,
        onBinaryReceived: (ByteArray) -> Unit = {}
    ) {
        val sessionId =
            beginNewSession()

        connectionThread = thread(
            name = "BluetoothServerThread",
            start = true
        ) {
            try {
                postStatus(
                    sessionId = sessionId,
                    status =
                        "Servidor esperando una conexión...",
                    callback = onStatusChanged
                )

                val newServerSocket =
                    bluetoothAdapter
                        .listenUsingRfcommWithServiceRecord(
                            SERVICE_NAME,
                            SERVICE_UUID
                        )

                serverSocket =
                    newServerSocket

                /*
                 * accept() es bloqueante y por eso se ejecuta
                 * dentro de este hilo secundario.
                 */
                val socket =
                    newServerSocket.accept()

                if (!isCurrentSession(sessionId)) {
                    socket.close()
                    return@thread
                }

                connectedSocket =
                    socket

                runCatching {
                    newServerSocket.close()
                }

                serverSocket =
                    null

                val input =
                    DataInputStream(
                        BufferedInputStream(
                            socket.inputStream
                        )
                    )

                val output =
                    DataOutputStream(
                        BufferedOutputStream(
                            socket.outputStream
                        )
                    )

                connectedInput =
                    input

                connectedOutput =
                    output

                postStatus(
                    sessionId = sessionId,
                    status =
                        "Conectado con ${
                            remoteDeviceName(socket)
                        }",
                    callback = onStatusChanged
                )

                readFrames(
                    sessionId = sessionId,
                    socket = socket,
                    input = input,
                    onStatusChanged =
                        onStatusChanged,
                    onMessageReceived =
                        onMessageReceived,
                    onBinaryReceived =
                        onBinaryReceived
                )
            } catch (error: Exception) {
                postStatus(
                    sessionId = sessionId,
                    status =
                        "Error del servidor: ${
                            error.readableMessage()
                        }",
                    callback = onStatusChanged
                )
            } finally {
                closeResourcesForSession(
                    sessionId
                )
            }
        }
    }

    /**
     * Conecta al cliente con un dispositivo emparejado.
     */
    @SuppressLint("MissingPermission")
    fun connectToServer(
        deviceAddress: String,
        onStatusChanged: (String) -> Unit,
        onMessageReceived: (String) -> Unit,
        onBinaryReceived: (ByteArray) -> Unit = {}
    ) {
        val sessionId =
            beginNewSession()

        connectionThread = thread(
            name = "BluetoothClientThread",
            start = true
        ) {
            try {
                postStatus(
                    sessionId = sessionId,
                    status =
                        "Preparando conexión...",
                    callback = onStatusChanged
                )

                /*
                 * El descubrimiento hace más lenta e inestable
                 * una conexión RFCOMM.
                 */
                bluetoothAdapter.cancelDiscovery()

                val device =
                    bluetoothAdapter.getRemoteDevice(
                        deviceAddress
                    )

                val socket =
                    device
                        .createRfcommSocketToServiceRecord(
                            SERVICE_UUID
                        )

                connectedSocket =
                    socket

                postStatus(
                    sessionId = sessionId,
                    status =
                        "Conectando con ${
                            device.name
                                ?: device.address
                        }...",
                    callback = onStatusChanged
                )

                /*
                 * connect() también es bloqueante.
                 */
                socket.connect()

                if (!isCurrentSession(sessionId)) {
                    socket.close()
                    return@thread
                }

                val input =
                    DataInputStream(
                        BufferedInputStream(
                            socket.inputStream
                        )
                    )

                val output =
                    DataOutputStream(
                        BufferedOutputStream(
                            socket.outputStream
                        )
                    )

                connectedInput =
                    input

                connectedOutput =
                    output

                postStatus(
                    sessionId = sessionId,
                    status =
                        "Conectado con ${
                            remoteDeviceName(socket)
                        }",
                    callback = onStatusChanged
                )

                /*
                 * Conservamos el saludo inicial utilizado
                 * por la interfaz actual.
                 */
                writeFrame(
                    frameType = FRAME_TYPE_TEXT,
                    payload =
                        BluetoothProtocol
                            .hello(
                                "Hola desde el cliente"
                            )
                            .toByteArray(
                                StandardCharsets.UTF_8
                            )
                )

                readFrames(
                    sessionId = sessionId,
                    socket = socket,
                    input = input,
                    onStatusChanged =
                        onStatusChanged,
                    onMessageReceived =
                        onMessageReceived,
                    onBinaryReceived =
                        onBinaryReceived
                )
            } catch (error: Exception) {
                postStatus(
                    sessionId = sessionId,
                    status =
                        "Error de conexión: ${
                            error.readableMessage()
                        }",
                    callback = onStatusChanged
                )
            } finally {
                closeResourcesForSession(
                    sessionId
                )
            }
        }
    }

    /**
     * Envía JSON o texto UTF-8.
     *
     * La búsqueda y las solicitudes de reproducción
     * continúan utilizando este método.
     */
    fun sendMessage(
        message: String,
        onError: (String) -> Unit = {}
    ) {
        val payload =
            message.toByteArray(
                StandardCharsets.UTF_8
            )

        sendFrameAsync(
            frameType = FRAME_TYPE_TEXT,
            payload = payload,
            onError = onError
        )
    }

    /**
     * Envía datos binarios sin Base64 y sin convertirlos a texto.
     *
     * Se usará para bloques de archivos y video.
     */
    fun sendBinary(
        data: ByteArray,
        onError: (String) -> Unit = {}
    ) {
        if (data.isEmpty()) {
            mainHandler.post {
                onError(
                    "No se pueden enviar datos binarios vacíos."
                )
            }
            return
        }

        sendFrameAsync(
            frameType = FRAME_TYPE_BINARY,
            payload = data.copyOf(),
            onError = onError
        )
    }

    fun isConnected(): Boolean {
        return connectedSocket?.isConnected == true &&
                connectedInput != null &&
                connectedOutput != null
    }

    /**
     * Cierra servidor, conexión, streams e hilo actual.
     */
    fun close() {
        sessionCounter.incrementAndGet()
        closeResources()
    }

    private fun beginNewSession(): Int {
        closeResources()
        return sessionCounter.incrementAndGet()
    }

    private fun isCurrentSession(
        sessionId: Int
    ): Boolean {
        return sessionCounter.get() ==
                sessionId
    }

    /**
     * Lee continuamente las tramas recibidas.
     *
     * Formato:
     * MAGIC       4 bytes
     * VERSION     1 byte
     * TYPE        1 byte
     * LENGTH      4 bytes
     * PAYLOAD     N bytes
     */
    private fun readFrames(
        sessionId: Int,
        socket: BluetoothSocket,
        input: DataInputStream,
        onStatusChanged: (String) -> Unit,
        onMessageReceived: (String) -> Unit,
        onBinaryReceived: (ByteArray) -> Unit
    ) {
        try {
            while (
                isCurrentSession(sessionId) &&
                socket.isConnected
            ) {
                val magic =
                    input.readInt()

                if (magic != FRAME_MAGIC) {
                    throw IOException(
                        "Cabecera Bluetooth inválida."
                    )
                }

                val version =
                    input.readUnsignedByte()

                if (
                    version !=
                    PROTOCOL_VERSION
                ) {
                    throw IOException(
                        "Versión de protocolo no compatible: $version."
                    )
                }

                val frameType =
                    input.readUnsignedByte()

                val payloadLength =
                    input.readInt()

                if (
                    payloadLength < 0 ||
                    payloadLength >
                    MAX_FRAME_SIZE
                ) {
                    throw IOException(
                        "Tamaño de trama inválido: $payloadLength bytes."
                    )
                }

                val payload =
                    ByteArray(payloadLength)

                input.readFully(payload)

                when (frameType) {
                    FRAME_TYPE_TEXT -> {
                        val message =
                            payload.toString(
                                StandardCharsets.UTF_8
                            )

                        postMessage(
                            sessionId = sessionId,
                            message = message,
                            callback =
                                onMessageReceived
                        )
                    }

                    FRAME_TYPE_BINARY -> {
                        postBinary(
                            sessionId = sessionId,
                            data = payload,
                            callback =
                                onBinaryReceived
                        )
                    }

                    else -> {
                        throw IOException(
                            "Tipo de trama desconocido: $frameType."
                        )
                    }
                }
            }
        } catch (_: EOFException) {
            /*
             * El dispositivo remoto cerró la conexión.
             */
        }

        postStatus(
            sessionId = sessionId,
            status =
                "Conexión finalizada",
            callback = onStatusChanged
        )
    }

    private fun sendFrameAsync(
        frameType: Int,
        payload: ByteArray,
        onError: (String) -> Unit
    ) {
        if (payload.size > MAX_FRAME_SIZE) {
            mainHandler.post {
                onError(
                    "La trama supera el máximo de $MAX_FRAME_SIZE bytes."
                )
            }
            return
        }

        if (!isConnected()) {
            mainHandler.post {
                onError(
                    "No existe una conexión Bluetooth activa."
                )
            }
            return
        }

        thread(
            name = "BluetoothSendThread",
            start = true
        ) {
            try {
                writeFrame(
                    frameType = frameType,
                    payload = payload
                )
            } catch (error: Exception) {
                mainHandler.post {
                    onError(
                        "No se pudo enviar: ${
                            error.readableMessage()
                        }"
                    )
                }
            }
        }
    }

    /**
     * synchronized evita que dos envíos mezclen sus bytes.
     */
    private fun writeFrame(
        frameType: Int,
        payload: ByteArray
    ) {
        synchronized(outputLock) {
            val output =
                connectedOutput
                    ?: throw IOException(
                        "El canal de salida no está disponible."
                    )

            output.writeInt(
                FRAME_MAGIC
            )

            output.writeByte(
                PROTOCOL_VERSION
            )

            output.writeByte(
                frameType
            )

            output.writeInt(
                payload.size
            )

            output.write(
                payload
            )

            output.flush()
        }
    }

    @SuppressLint("MissingPermission")
    private fun remoteDeviceName(
        socket: BluetoothSocket
    ): String {
        val device =
            socket.remoteDevice

        return device.name
            ?.takeIf {
                it.isNotBlank()
            }
            ?: device.address
    }

    private fun postStatus(
        sessionId: Int,
        status: String,
        callback: (String) -> Unit
    ) {
        if (!isCurrentSession(sessionId)) {
            return
        }

        mainHandler.post {
            if (
                isCurrentSession(sessionId)
            ) {
                callback(status)
            }
        }
    }

    private fun postMessage(
        sessionId: Int,
        message: String,
        callback: (String) -> Unit
    ) {
        if (!isCurrentSession(sessionId)) {
            return
        }

        mainHandler.post {
            if (
                isCurrentSession(sessionId)
            ) {
                callback(message)
            }
        }
    }

    private fun postBinary(
        sessionId: Int,
        data: ByteArray,
        callback: (ByteArray) -> Unit
    ) {
        if (!isCurrentSession(sessionId)) {
            return
        }

        mainHandler.post {
            if (
                isCurrentSession(sessionId)
            ) {
                callback(data)
            }
        }
    }

    private fun closeResourcesForSession(
        sessionId: Int
    ) {
        if (!isCurrentSession(sessionId)) {
            return
        }

        closeResources()
    }

    private fun closeResources() {
        runCatching {
            serverSocket?.close()
        }

        runCatching {
            connectedInput?.close()
        }

        runCatching {
            connectedOutput?.close()
        }

        runCatching {
            connectedSocket?.close()
        }

        connectionThread?.interrupt()

        serverSocket =
            null

        connectedInput =
            null

        connectedOutput =
            null

        connectedSocket =
            null

        connectionThread =
            null
    }

    private fun Throwable.readableMessage():
            String {
        return message
            ?.takeIf {
                it.isNotBlank()
            }
            ?: javaClass.simpleName
    }
}