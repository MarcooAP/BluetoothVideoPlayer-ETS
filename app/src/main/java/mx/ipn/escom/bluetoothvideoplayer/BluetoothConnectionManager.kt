package mx.ipn.escom.bluetoothvideoplayer

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.os.Handler
import android.os.Looper
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

        /*
         * Este UUID identifica nuestro servicio Bluetooth.
         * Debe ser exactamente el mismo en servidor y cliente.
         */
        val SERVICE_UUID: UUID =
            UUID.fromString("7a51f5d0-86a4-4d8b-aed6-24a06477c721")
    }

    private val mainHandler = Handler(Looper.getMainLooper())

    /*
     * Permite ignorar eventos pertenecientes a una conexión anterior.
     */
    private val sessionCounter = AtomicInteger(0)

    @Volatile
    private var serverSocket: BluetoothServerSocket? = null

    @Volatile
    private var connectedSocket: BluetoothSocket? = null

    @Volatile
    private var connectionThread: Thread? = null

    /**
     * Inicia el teléfono como servidor.
     *
     * Se queda esperando hasta que otro dispositivo se conecte.
     */
    @SuppressLint("MissingPermission")
    fun startServer(
        onStatusChanged: (String) -> Unit,
        onMessageReceived: (String) -> Unit
    ) {
        val sessionId = beginNewSession()

        connectionThread = thread(
            name = "BluetoothServerThread",
            start = true
        ) {
            try {
                postStatus(
                    sessionId = sessionId,
                    status = "Servidor esperando una conexión...",
                    callback = onStatusChanged
                )

                val newServerSocket =
                    bluetoothAdapter.listenUsingRfcommWithServiceRecord(
                        SERVICE_NAME,
                        SERVICE_UUID
                    )

                serverSocket = newServerSocket

                /*
                 * accept() se bloquea hasta que el cliente se conecta.
                 */
                val socket = newServerSocket.accept()

                if (!isCurrentSession(sessionId)) {
                    socket.close()
                    return@thread
                }

                connectedSocket = socket

                runCatching {
                    newServerSocket.close()
                }

                serverSocket = null

                postStatus(
                    sessionId = sessionId,
                    status = "Conectado con ${remoteDeviceName(socket)}",
                    callback = onStatusChanged
                )

                readMessages(
                    sessionId = sessionId,
                    socket = socket,
                    onStatusChanged = onStatusChanged,
                    onMessageReceived = { message ->
                        postMessage(
                            sessionId = sessionId,
                            message = message,
                            callback = onMessageReceived
                        )

                        /*
                         * Respuesta automática para comprobar
                         * la comunicación en ambos sentidos.
                         */
                        sendInternal(
                            socket = socket,
                            message = "Servidor recibió: $message"
                        )
                    }
                )
            } catch (error: Exception) {
                postStatus(
                    sessionId = sessionId,
                    status = "Error del servidor: ${error.readableMessage()}",
                    callback = onStatusChanged
                )
            } finally {
                closeSocketsForSession(sessionId)
            }
        }
    }

    /**
     * Conecta el cliente con un dispositivo previamente emparejado.
     */
    @SuppressLint("MissingPermission")
    fun connectToServer(
        deviceAddress: String,
        onStatusChanged: (String) -> Unit,
        onMessageReceived: (String) -> Unit
    ) {
        val sessionId = beginNewSession()

        connectionThread = thread(
            name = "BluetoothClientThread",
            start = true
        ) {
            try {
                postStatus(
                    sessionId = sessionId,
                    status = "Preparando conexión...",
                    callback = onStatusChanged
                )

                /*
                 * El descubrimiento reduce la velocidad de conexión,
                 * por lo que se cancela antes de llamar a connect().
                 */
                bluetoothAdapter.cancelDiscovery()

                val device =
                    bluetoothAdapter.getRemoteDevice(deviceAddress)

                val socket =
                    device.createRfcommSocketToServiceRecord(
                        SERVICE_UUID
                    )

                connectedSocket = socket

                postStatus(
                    sessionId = sessionId,
                    status = "Conectando con ${device.name ?: device.address}...",
                    callback = onStatusChanged
                )

                /*
                 * connect() se bloquea hasta conectar o fallar.
                 */
                socket.connect()

                if (!isCurrentSession(sessionId)) {
                    socket.close()
                    return@thread
                }

                postStatus(
                    sessionId = sessionId,
                    status = "Conectado con ${remoteDeviceName(socket)}",
                    callback = onStatusChanged
                )

                /*
                 * Primer mensaje de prueba.
                 */
                sendInternal(
                    socket = socket,
                    message = "Hola desde el cliente"
                )

                readMessages(
                    sessionId = sessionId,
                    socket = socket,
                    onStatusChanged = onStatusChanged,
                    onMessageReceived = { message ->
                        postMessage(
                            sessionId = sessionId,
                            message = message,
                            callback = onMessageReceived
                        )
                    }
                )
            } catch (error: Exception) {
                postStatus(
                    sessionId = sessionId,
                    status = "Error de conexión: ${error.readableMessage()}",
                    callback = onStatusChanged
                )
            } finally {
                closeSocketsForSession(sessionId)
            }
        }
    }

    /**
     * Envía un mensaje por la conexión actualmente abierta.
     */
    fun sendMessage(
        message: String,
        onError: (String) -> Unit = {}
    ) {
        val socket = connectedSocket

        if (socket == null || !socket.isConnected) {
            mainHandler.post {
                onError("No existe una conexión Bluetooth activa.")
            }
            return
        }

        thread(
            name = "BluetoothSendThread",
            start = true
        ) {
            try {
                sendInternal(
                    socket = socket,
                    message = message
                )
            } catch (error: Exception) {
                mainHandler.post {
                    onError(
                        "No se pudo enviar el mensaje: " +
                                error.readableMessage()
                    )
                }
            }
        }
    }

    /**
     * Cierra cualquier conexión abierta.
     */
    fun close() {
        sessionCounter.incrementAndGet()

        runCatching {
            serverSocket?.close()
        }

        runCatching {
            connectedSocket?.close()
        }

        connectionThread?.interrupt()

        serverSocket = null
        connectedSocket = null
        connectionThread = null
    }

    private fun beginNewSession(): Int {
        close()
        return sessionCounter.incrementAndGet()
    }

    private fun isCurrentSession(
        sessionId: Int
    ): Boolean {
        return sessionCounter.get() == sessionId
    }

    private fun readMessages(
        sessionId: Int,
        socket: BluetoothSocket,
        onStatusChanged: (String) -> Unit,
        onMessageReceived: (String) -> Unit
    ) {
        val reader = socket.inputStream.bufferedReader(
            StandardCharsets.UTF_8
        )

        while (
            isCurrentSession(sessionId) &&
            socket.isConnected
        ) {
            val message = reader.readLine() ?: break

            onMessageReceived(message)
        }

        postStatus(
            sessionId = sessionId,
            status = "Conexión finalizada",
            callback = onStatusChanged
        )
    }

    private fun sendInternal(
        socket: BluetoothSocket,
        message: String
    ) {
        val data = "$message\n".toByteArray(
            StandardCharsets.UTF_8
        )

        socket.outputStream.write(data)
        socket.outputStream.flush()
    }

    @SuppressLint("MissingPermission")
    private fun remoteDeviceName(
        socket: BluetoothSocket
    ): String {
        val device = socket.remoteDevice

        return device.name
            ?.takeIf { name -> name.isNotBlank() }
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
        if (!isCurrentSession(sessionId)) {
            return
        }

        mainHandler.post {
            if (isCurrentSession(sessionId)) {
                callback(message)
            }
        }
    }

    private fun closeSocketsForSession(
        sessionId: Int
    ) {
        if (!isCurrentSession(sessionId)) {
            return
        }

        runCatching {
            serverSocket?.close()
        }

        runCatching {
            connectedSocket?.close()
        }

        serverSocket = null
        connectedSocket = null
        connectionThread = null
    }

    private fun Throwable.readableMessage(): String {
        return message
            ?.takeIf { text -> text.isNotBlank() }
            ?: javaClass.simpleName
    }
}