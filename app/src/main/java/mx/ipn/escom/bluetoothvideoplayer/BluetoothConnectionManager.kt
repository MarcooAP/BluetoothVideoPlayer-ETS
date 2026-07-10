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
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

class BluetoothConnectionManager(
    private val bluetoothAdapter: BluetoothAdapter
) {

    companion object {
        private const val SERVICE_NAME = "BluetoothVideoPlayerETS"

        val SERVICE_UUID: UUID =
            UUID.fromString("7a51f5d0-86a4-4d8b-aed6-24a06477c721")

        private const val FRAME_MAGIC = 0x42565031
        private const val PROTOCOL_VERSION = 1
        private const val FRAME_TYPE_TEXT = 1
        private const val FRAME_TYPE_BINARY = 2
        private const val MAX_FRAME_SIZE = 512 * 1024
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val sessionCounter = AtomicInteger(0)
    private val outputLock = Any()

    @Volatile
    private var serverSocket: BluetoothServerSocket? = null

    @Volatile
    private var connectedSocket: BluetoothSocket? = null

    @Volatile
    private var connectedInput: DataInputStream? = null

    @Volatile
    private var connectedOutput: DataOutputStream? = null

    @Volatile
    private var connectionThread: Thread? = null

    @Volatile
    private var sendExecutor: ExecutorService? = null

    @SuppressLint("MissingPermission")
    fun startServer(
        onStatusChanged: (String) -> Unit,
        onMessageReceived: (String) -> Unit,
        onBinaryReceived: (ByteArray) -> Unit = {}
    ) {
        val sessionId = beginNewSession()

        connectionThread = thread(
            name = "BluetoothServerThread",
            start = true
        ) {
            try {
                postStatus(
                    sessionId,
                    "Servidor esperando una conexión...",
                    onStatusChanged
                )

                val listeningSocket =
                    bluetoothAdapter.listenUsingRfcommWithServiceRecord(
                        SERVICE_NAME,
                        SERVICE_UUID
                    )

                serverSocket = listeningSocket
                val socket = listeningSocket.accept()

                if (!isCurrentSession(sessionId)) {
                    socket.close()
                    return@thread
                }

                connectedSocket = socket
                runCatching { listeningSocket.close() }
                serverSocket = null

                val input = DataInputStream(
                    BufferedInputStream(socket.inputStream)
                )
                val output = DataOutputStream(
                    BufferedOutputStream(socket.outputStream)
                )

                connectedInput = input
                connectedOutput = output

                postStatus(
                    sessionId,
                    "Conectado con ${remoteDeviceName(socket)}",
                    onStatusChanged
                )

                readFrames(
                    sessionId = sessionId,
                    socket = socket,
                    input = input,
                    onStatusChanged = onStatusChanged,
                    onMessageReceived = onMessageReceived,
                    onBinaryReceived = onBinaryReceived
                )
            } catch (error: Exception) {
                postStatus(
                    sessionId,
                    "Error del servidor: ${error.readableMessage()}",
                    onStatusChanged
                )
            } finally {
                closeResourcesForSession(sessionId)
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun connectToServer(
        deviceAddress: String,
        onStatusChanged: (String) -> Unit,
        onMessageReceived: (String) -> Unit,
        onBinaryReceived: (ByteArray) -> Unit = {}
    ) {
        val sessionId = beginNewSession()

        connectionThread = thread(
            name = "BluetoothClientThread",
            start = true
        ) {
            try {
                postStatus(
                    sessionId,
                    "Preparando conexión...",
                    onStatusChanged
                )

                bluetoothAdapter.cancelDiscovery()

                val device = bluetoothAdapter.getRemoteDevice(deviceAddress)
                val socket =
                    device.createRfcommSocketToServiceRecord(SERVICE_UUID)

                connectedSocket = socket

                postStatus(
                    sessionId,
                    "Conectando con ${device.name ?: device.address}...",
                    onStatusChanged
                )

                socket.connect()

                if (!isCurrentSession(sessionId)) {
                    socket.close()
                    return@thread
                }

                val input = DataInputStream(
                    BufferedInputStream(socket.inputStream)
                )
                val output = DataOutputStream(
                    BufferedOutputStream(socket.outputStream)
                )

                connectedInput = input
                connectedOutput = output

                postStatus(
                    sessionId,
                    "Conectado con ${remoteDeviceName(socket)}",
                    onStatusChanged
                )

                writeFrame(
                    sessionId = sessionId,
                    frameType = FRAME_TYPE_TEXT,
                    payload = BluetoothProtocol
                        .hello("Hola desde el cliente")
                        .toByteArray(StandardCharsets.UTF_8)
                )

                readFrames(
                    sessionId = sessionId,
                    socket = socket,
                    input = input,
                    onStatusChanged = onStatusChanged,
                    onMessageReceived = onMessageReceived,
                    onBinaryReceived = onBinaryReceived
                )
            } catch (error: Exception) {
                postStatus(
                    sessionId,
                    "Error de conexión: ${error.readableMessage()}",
                    onStatusChanged
                )
            } finally {
                closeResourcesForSession(sessionId)
            }
        }
    }

    fun sendMessage(
        message: String,
        onError: (String) -> Unit = {}
    ) {
        sendFrameAsync(
            frameType = FRAME_TYPE_TEXT,
            payload = message.toByteArray(StandardCharsets.UTF_8),
            onError = onError
        )
    }

    fun sendBinary(
        data: ByteArray,
        onError: (String) -> Unit = {}
    ) {
        if (data.isEmpty()) {
            mainHandler.post {
                onError("No se pueden enviar datos binarios vacíos.")
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

    fun close() {
        sessionCounter.incrementAndGet()
        closeResources()
    }

    private fun beginNewSession(): Int {
        close()
        val sessionId = sessionCounter.incrementAndGet()
        sendExecutor = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "BluetoothWriterThread").apply {
                isDaemon = true
            }
        }
        return sessionId
    }

    private fun isCurrentSession(sessionId: Int): Boolean {
        return sessionCounter.get() == sessionId
    }

    private fun readFrames(
        sessionId: Int,
        socket: BluetoothSocket,
        input: DataInputStream,
        onStatusChanged: (String) -> Unit,
        onMessageReceived: (String) -> Unit,
        onBinaryReceived: (ByteArray) -> Unit
    ) {
        try {
            while (isCurrentSession(sessionId) && socket.isConnected) {
                val magic = input.readInt()
                if (magic != FRAME_MAGIC) {
                    throw IOException("Cabecera Bluetooth inválida.")
                }

                val version = input.readUnsignedByte()
                if (version != PROTOCOL_VERSION) {
                    throw IOException(
                        "Versión de protocolo no compatible: $version."
                    )
                }

                val frameType = input.readUnsignedByte()
                val payloadLength = input.readInt()

                if (payloadLength < 0 || payloadLength > MAX_FRAME_SIZE) {
                    throw IOException(
                        "Tamaño de trama inválido: $payloadLength bytes."
                    )
                }

                val payload = ByteArray(payloadLength)
                input.readFully(payload)

                when (frameType) {
                    FRAME_TYPE_TEXT -> {
                        postMessage(
                            sessionId,
                            payload.toString(StandardCharsets.UTF_8),
                            onMessageReceived
                        )
                    }

                    FRAME_TYPE_BINARY -> {
                        postBinary(
                            sessionId,
                            payload,
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
            // El dispositivo remoto cerró la conexión.
        }

        postStatus(
            sessionId,
            "Conexión finalizada",
            onStatusChanged
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
                onError("No existe una conexión Bluetooth activa.")
            }
            return
        }

        val sessionId = sessionCounter.get()
        val executor = sendExecutor

        if (executor == null || executor.isShutdown) {
            mainHandler.post {
                onError("El canal de envío no está disponible.")
            }
            return
        }

        try {
            executor.execute {
                try {
                    writeFrame(
                        sessionId = sessionId,
                        frameType = frameType,
                        payload = payload
                    )
                } catch (error: Exception) {
                    mainHandler.post {
                        onError(
                            "No se pudo enviar: ${error.readableMessage()}"
                        )
                    }
                }
            }
        } catch (_: RejectedExecutionException) {
            mainHandler.post {
                onError("El canal de envío ya fue cerrado.")
            }
        }
    }

    private fun writeFrame(
        sessionId: Int,
        frameType: Int,
        payload: ByteArray
    ) {
        if (!isCurrentSession(sessionId)) {
            throw IOException("La sesión Bluetooth cambió.")
        }

        synchronized(outputLock) {
            val output = connectedOutput
                ?: throw IOException(
                    "El canal de salida no está disponible."
                )

            output.writeInt(FRAME_MAGIC)
            output.writeByte(PROTOCOL_VERSION)
            output.writeByte(frameType)
            output.writeInt(payload.size)
            output.write(payload)
            output.flush()
        }
    }

    @SuppressLint("MissingPermission")
    private fun remoteDeviceName(socket: BluetoothSocket): String {
        val device = socket.remoteDevice
        return device.name
            ?.takeIf { it.isNotBlank() }
            ?: device.address
    }

    private fun postStatus(
        sessionId: Int,
        status: String,
        callback: (String) -> Unit
    ) {
        if (!isCurrentSession(sessionId)) return

        mainHandler.post {
            if (isCurrentSession(sessionId)) {
                callback(status)
            }
        }
    }

    private fun postMessage(
        sessionId: Int,
        message: String,
        callback: (String) -> Unit
    ) {
        if (!isCurrentSession(sessionId)) return

        mainHandler.post {
            if (isCurrentSession(sessionId)) {
                callback(message)
            }
        }
    }

    private fun postBinary(
        sessionId: Int,
        data: ByteArray,
        callback: (ByteArray) -> Unit
    ) {
        if (!isCurrentSession(sessionId)) return

        mainHandler.post {
            if (isCurrentSession(sessionId)) {
                callback(data)
            }
        }
    }

    private fun closeResourcesForSession(sessionId: Int) {
        if (!isCurrentSession(sessionId)) return
        closeResources()
    }

    private fun closeResources() {
        runCatching { serverSocket?.close() }
        runCatching { connectedInput?.close() }
        runCatching { connectedOutput?.close() }
        runCatching { connectedSocket?.close() }
        runCatching { sendExecutor?.shutdownNow() }

        connectionThread?.interrupt()

        serverSocket = null
        connectedInput = null
        connectedOutput = null
        connectedSocket = null
        connectionThread = null
        sendExecutor = null
    }

    private fun Throwable.readableMessage(): String {
        return message
            ?.takeIf { it.isNotBlank() }
            ?: javaClass.simpleName
    }
}
