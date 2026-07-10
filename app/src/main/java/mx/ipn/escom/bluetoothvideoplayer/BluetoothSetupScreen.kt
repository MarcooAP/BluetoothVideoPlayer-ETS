package mx.ipn.escom.bluetoothvideoplayer

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay

enum class BluetoothRole {
    SERVER,
    CLIENT
}

data class PairedBluetoothDevice(
    val name: String,
    val address: String
)

@SuppressLint("MissingPermission")
@Composable
fun BluetoothSetupScreen(
    role: BluetoothRole,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current

    val bluetoothManager = remember(context) {
        context.getSystemService(
            Context.BLUETOOTH_SERVICE
        ) as? BluetoothManager
    }

    val bluetoothAdapter = bluetoothManager?.adapter

    val connectionManager = remember(bluetoothAdapter) {
        bluetoothAdapter?.let { adapter ->
            BluetoothConnectionManager(adapter)
        }
    }

    val youtubeSearchService = remember(context) {
        YouTubeSearchService(context.applicationContext)
    }

    var permissionsGranted by rememberSaveable(role.name) {
        mutableStateOf(
            hasRequiredBluetoothPermissions(
                context = context,
                role = role
            )
        )
    }

    var permissionDenied by rememberSaveable(role.name) {
        mutableStateOf(false)
    }

    var bluetoothEnabled by rememberSaveable(role.name) {
        mutableStateOf(false)
    }

    var pairedDevices by remember {
        mutableStateOf(emptyList<PairedBluetoothDevice>())
    }

    var selectedDeviceAddress by rememberSaveable(role.name) {
        mutableStateOf<String?>(null)
    }

    var connectionStatus by rememberSaveable(role.name) {
        mutableStateOf("Sin conexión")
    }

    var receivedMessage by rememberSaveable(role.name) {
        mutableStateOf("")
    }

    var connectionActive by rememberSaveable(role.name) {
        mutableStateOf(false)
    }

    var discoverableSecondsRemaining by rememberSaveable(role.name) {
        mutableStateOf(0)
    }

    var searchQuery by rememberSaveable {
        mutableStateOf("")
    }

    var searchStatus by rememberSaveable {
        mutableStateOf("")
    }

    var searchInProgress by rememberSaveable {
        mutableStateOf(false)
    }

    var searchResults by remember {
        mutableStateOf(emptyList<YouTubeVideoResult>())
    }

    var binaryTestStatus by rememberSaveable {
        mutableStateOf("")
    }

    var binaryTestInProgress by rememberSaveable {
        mutableStateOf(false)
    }

    var expectedBinarySize by rememberSaveable {
        mutableStateOf(0)
    }

    var expectedBinarySha256 by rememberSaveable {
        mutableStateOf("")
    }

    val incomingMediaReceiver = remember(context) {
        IncomingMediaReceiver(context.applicationContext)
    }

    var mediaStatus by rememberSaveable {
        mutableStateOf("")
    }

    var mediaInProgress by rememberSaveable {
        mutableStateOf(false)
    }

    var mediaProgress by rememberSaveable {
        mutableStateOf(0)
    }

    var receivedVideoPath by rememberSaveable {
        mutableStateOf<String?>(null)
    }

    LaunchedEffect(discoverableSecondsRemaining) {
        if (discoverableSecondsRemaining > 0) {
            delay(1000L)
            discoverableSecondsRemaining -= 1
        }
    }

    DisposableEffect(connectionManager, role) {
        onDispose {
            connectionManager?.close()
            incomingMediaReceiver.cancel()
        }
    }

    LaunchedEffect(
        bluetoothAdapter,
        permissionsGranted
    ) {
        bluetoothEnabled =
            bluetoothAdapter != null &&
                    permissionsGranted &&
                    bluetoothAdapter.isEnabled
    }

    LaunchedEffect(
        role,
        bluetoothEnabled,
        permissionsGranted,
        bluetoothAdapter
    ) {
        if (
            role == BluetoothRole.CLIENT &&
            bluetoothEnabled &&
            permissionsGranted &&
            bluetoothAdapter != null
        ) {
            pairedDevices = readPairedDevices(bluetoothAdapter)
        }
    }

    val enableBluetoothLauncher =
        rememberLauncherForActivityResult(
            contract =
                ActivityResultContracts.StartActivityForResult()
        ) {
            bluetoothEnabled =
                bluetoothAdapter?.isEnabled == true

            if (
                role == BluetoothRole.CLIENT &&
                bluetoothEnabled &&
                bluetoothAdapter != null
            ) {
                pairedDevices =
                    readPairedDevices(bluetoothAdapter)
            }
        }

    val permissionLauncher =
        rememberLauncherForActivityResult(
            contract =
                ActivityResultContracts.RequestMultiplePermissions()
        ) {
            permissionsGranted =
                hasRequiredBluetoothPermissions(
                    context = context,
                    role = role
                )

            permissionDenied = !permissionsGranted

            if (permissionsGranted) {
                bluetoothEnabled =
                    bluetoothAdapter?.isEnabled == true

                if (
                    role == BluetoothRole.CLIENT &&
                    bluetoothEnabled &&
                    bluetoothAdapter != null
                ) {
                    pairedDevices =
                        readPairedDevices(bluetoothAdapter)
                }
            }
        }

    val discoverableLauncher =
        rememberLauncherForActivityResult(
            contract =
                ActivityResultContracts.StartActivityForResult()
        ) { result ->
            discoverableSecondsRemaining =
                if (result.resultCode > 0) {
                    result.resultCode
                } else {
                    0
                }

            bluetoothEnabled =
                bluetoothAdapter?.isEnabled == true
        }

    val onConnectionStatusChanged:
                (String) -> Unit = { status ->

        connectionStatus = status

        if (
            status.startsWith("Error") ||
            status == "Conexión finalizada"
        ) {
            connectionActive = false
            searchInProgress = false
            binaryTestInProgress = false
            mediaInProgress = false
            incomingMediaReceiver.cancel()
        }
    }

    val isConnected =
        connectionStatus.startsWith("Conectado")

    fun resetBinaryTest() {
        binaryTestStatus = ""
        binaryTestInProgress = false
        expectedBinarySize = 0
        expectedBinarySha256 = ""
    }

    fun resetMediaTransfer(
        clearPlayer: Boolean = false
    ) {
        incomingMediaReceiver.cancel()
        mediaStatus = ""
        mediaInProgress = false
        mediaProgress = 0

        if (clearPlayer) {
            receivedVideoPath = null
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
                .padding(
                    horizontal = 24.dp,
                    vertical = 24.dp
                ),
            horizontalAlignment =
                Alignment.CenterHorizontally
        ) {
            Text(
                text = when (role) {
                    BluetoothRole.SERVER ->
                        "Modo servidor"

                    BluetoothRole.CLIENT ->
                        "Modo cliente"
                },
                style =
                    MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(24.dp))

            when {
                bluetoothAdapter == null -> {
                    Text(
                        text =
                            "Este dispositivo no tiene Bluetooth compatible.",
                        textAlign = TextAlign.Center
                    )
                }

                !permissionsGranted -> {
                    Text(
                        text =
                            "Se necesitan permisos Bluetooth para continuar.",
                        textAlign = TextAlign.Center
                    )

                    if (permissionDenied) {
                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text =
                                "Los permisos fueron rechazados.",
                            textAlign = TextAlign.Center
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Button(
                        onClick = {
                            permissionDenied = false

                            permissionLauncher.launch(
                                requiredBluetoothPermissions(role)
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Conceder permisos Bluetooth")
                    }
                }

                !bluetoothEnabled -> {
                    Text(
                        text = "Bluetooth está desactivado."
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    Button(
                        onClick = {
                            enableBluetoothLauncher.launch(
                                Intent(
                                    BluetoothAdapter
                                        .ACTION_REQUEST_ENABLE
                                )
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Activar Bluetooth")
                    }
                }

                role == BluetoothRole.SERVER -> {
                    ServerContent(
                        connectionStatus = connectionStatus,
                        receivedMessage = receivedMessage,
                        connectionActive = connectionActive,
                        discoverableSecondsRemaining =
                            discoverableSecondsRemaining,
                        binaryTestStatus = binaryTestStatus,
                        mediaStatus = mediaStatus,
                        onMakeDiscoverableClick = {
                            val intent =
                                Intent(
                                    BluetoothAdapter
                                        .ACTION_REQUEST_DISCOVERABLE
                                ).apply {
                                    putExtra(
                                        BluetoothAdapter
                                            .EXTRA_DISCOVERABLE_DURATION,
                                        300
                                    )
                                }

                            discoverableLauncher.launch(intent)
                        },
                        onStartServerClick = {
                            receivedMessage = ""
                            resetBinaryTest()
                            resetMediaTransfer(clearPlayer = true)
                            connectionStatus =
                                "Iniciando servidor..."
                            connectionActive = true

                            connectionManager?.startServer(
                                onStatusChanged =
                                    onConnectionStatusChanged,
                                onMessageReceived = { raw ->
                                    when (
                                        val message =
                                            BluetoothProtocol.parse(raw)
                                    ) {
                                        is BluetoothMessage.Hello -> {
                                            receivedMessage =
                                                message.text

                                            connectionManager
                                                .sendMessage(
                                                    BluetoothProtocol
                                                        .helloAck(
                                                            "Servidor listo para búsquedas"
                                                        )
                                                )
                                        }

                                        is BluetoothMessage.SearchRequest -> {
                                            val query = message.query

                                            receivedMessage =
                                                "Búsqueda solicitada: $query"

                                            connectionStatus =
                                                "Consultando YouTube..."

                                            youtubeSearchService.searchVideos(
                                                query = query,
                                                onSuccess = { results ->
                                                    connectionManager
                                                        .sendMessage(
                                                            BluetoothProtocol
                                                                .searchResults(
                                                                    results
                                                                )
                                                        )

                                                    connectionStatus =
                                                        "Conectado - ${results.size} resultados enviados"
                                                },
                                                onError = { error ->
                                                    connectionManager
                                                        .sendMessage(
                                                            BluetoothProtocol
                                                                .error(error)
                                                        )

                                                    connectionStatus =
                                                        "Conectado - error de búsqueda"
                                                }
                                            )
                                        }

                                        is BluetoothMessage.PlayRequest -> {
                                            receivedMessage =
                                                "Solicitud de reproducción:\n" +
                                                        "${message.title}\n" +
                                                        "ID: ${message.videoId}"

                                            connectionStatus =
                                                "Preparando video solicitado..."

                                            connectionManager
                                                .sendMessage(
                                                    BluetoothProtocol
                                                        .playPreparing(
                                                            videoId =
                                                                message.videoId,
                                                            message =
                                                                "El servidor recibió la solicitud de reproducción."
                                                        )
                                                )

                                            connectionStatus =
                                                "Conectado - solicitud de reproducción recibida"
                                        }

                                        is BluetoothMessage.BinaryTestRequest -> {
                                            val requestedSize =
                                                message.sizeBytes.coerceIn(
                                                    1,
                                                    BinaryTransferUtils
                                                        .MAX_TEST_SIZE
                                                )

                                            val payload =
                                                BinaryTransferUtils
                                                    .createTestPayload(
                                                        requestedSize
                                                    )

                                            val sha256 =
                                                BinaryTransferUtils
                                                    .sha256(payload)

                                            binaryTestStatus =
                                                "Enviando ${requestedSize / 1024} KB al cliente..."

                                            connectionManager.sendMessage(
                                                BluetoothProtocol
                                                    .binaryTestInfo(
                                                        sizeBytes =
                                                            requestedSize,
                                                        sha256 = sha256
                                                    ),
                                                onError = { error ->
                                                    binaryTestStatus = error
                                                }
                                            )

                                            connectionManager.sendBinary(
                                                payload,
                                                onError = { error ->
                                                    binaryTestStatus = error
                                                }
                                            )

                                            binaryTestStatus =
                                                "Prueba enviada; esperando verificación del cliente."
                                        }

                                        is BluetoothMessage.BinaryTestResult -> {
                                            binaryTestStatus =
                                                message.message

                                            connectionStatus =
                                                if (message.success) {
                                                    "Conectado - enlace binario verificado"
                                                } else {
                                                    "Conectado - falló la verificación binaria"
                                                }
                                        }

                                        BluetoothMessage.SampleMediaRequest -> {
                                            val sampleBytes =
                                                context.resources
                                                    .openRawResource(
                                                        R.raw.sample_video
                                                    )
                                                    .use { input ->
                                                        input.readBytes()
                                                    }

                                            val sha256 =
                                                MediaTransferUtils.sha256(
                                                    sampleBytes
                                                )

                                            val chunkSize =
                                                MediaTransferUtils
                                                    .CHUNK_DATA_SIZE

                                            val totalChunks =
                                                (
                                                    sampleBytes.size +
                                                        chunkSize - 1
                                                ) / chunkSize

                                            mediaStatus =
                                                "Enviando MP4 de prueba: " +
                                                    "${sampleBytes.size / 1024} KB..."

                                            connectionManager.sendMessage(
                                                BluetoothProtocol.mediaStart(
                                                    fileName =
                                                        "video_prueba.mp4",
                                                    totalBytes =
                                                        sampleBytes.size
                                                            .toLong(),
                                                    sha256 = sha256,
                                                    totalChunks =
                                                        totalChunks
                                                ),
                                                onError = { error ->
                                                    mediaStatus = error
                                                }
                                            )

                                            var offset = 0
                                            var sequence = 0

                                            while (
                                                offset < sampleBytes.size
                                            ) {
                                                val length =
                                                    minOf(
                                                        chunkSize,
                                                        sampleBytes.size -
                                                            offset
                                                    )

                                                val chunk =
                                                    sampleBytes.copyOfRange(
                                                        offset,
                                                        offset + length
                                                    )

                                                connectionManager.sendBinary(
                                                    MediaTransferUtils
                                                        .wrapChunk(
                                                            sequence =
                                                                sequence,
                                                            source = chunk,
                                                            length =
                                                                chunk.size
                                                        ),
                                                    onError = { error ->
                                                        mediaStatus = error
                                                    }
                                                )

                                                offset += length
                                                sequence += 1
                                            }

                                            connectionManager.sendMessage(
                                                BluetoothProtocol.mediaEnd(),
                                                onError = { error ->
                                                    mediaStatus = error
                                                }
                                            )

                                            mediaStatus =
                                                "MP4 enviado; esperando " +
                                                    "verificación del cliente."
                                        }

                                        is BluetoothMessage.MediaResult -> {
                                            mediaStatus = message.message

                                            connectionStatus =
                                                if (message.success) {
                                                    "Conectado - MP4 recibido y verificado"
                                                } else {
                                                    "Conectado - falló la transferencia MP4"
                                                }
                                        }

                                        is BluetoothMessage.Unknown -> {
                                            receivedMessage = message.raw
                                        }

                                        else -> Unit
                                    }
                                },
                                onBinaryReceived = {
                                    binaryTestStatus =
                                        "El servidor recibió datos binarios inesperados."
                                }
                            )
                        },
                        onStopClick = {
                            connectionManager?.close()
                            connectionActive = false
                            connectionStatus =
                                "Servidor detenido"
                            receivedMessage = ""
                            resetBinaryTest()
                            resetMediaTransfer(clearPlayer = true)
                        }
                    )
                }

                else -> {
                    ClientContent(
                        pairedDevices = pairedDevices,
                        selectedDeviceAddress =
                            selectedDeviceAddress,
                        connectionStatus = connectionStatus,
                        receivedMessage = receivedMessage,
                        connectionActive = connectionActive,
                        isConnected = isConnected,
                        searchQuery = searchQuery,
                        searchStatus = searchStatus,
                        searchInProgress = searchInProgress,
                        searchResults = searchResults,
                        binaryTestStatus = binaryTestStatus,
                        binaryTestInProgress =
                            binaryTestInProgress,
                        mediaStatus = mediaStatus,
                        mediaInProgress = mediaInProgress,
                        mediaProgress = mediaProgress,
                        receivedVideoPath = receivedVideoPath,
                        onSearchQueryChanged = {
                            searchQuery = it
                        },
                        onDeviceSelected = {
                            selectedDeviceAddress = it
                        },
                        onRefreshClick = {
                            pairedDevices =
                                readPairedDevices(bluetoothAdapter)
                        },
                        onOpenSettingsClick = {
                            context.startActivity(
                                Intent(
                                    Settings
                                        .ACTION_BLUETOOTH_SETTINGS
                                )
                            )
                        },
                        onConnectClick = { address ->
                            receivedMessage = ""
                            searchResults = emptyList()
                            searchStatus = ""
                            resetBinaryTest()
                            resetMediaTransfer(clearPlayer = true)

                            connectionStatus =
                                "Preparando conexión..."
                            connectionActive = true

                            connectionManager
                                ?.connectToServer(
                                    deviceAddress = address,
                                    onStatusChanged =
                                        onConnectionStatusChanged,
                                    onMessageReceived = { raw ->
                                        when (
                                            val message =
                                                BluetoothProtocol.parse(
                                                    raw
                                                )
                                        ) {
                                            is BluetoothMessage.HelloAck -> {
                                                receivedMessage =
                                                    message.text
                                            }

                                            is BluetoothMessage.SearchResults -> {
                                                searchResults =
                                                    message.items
                                                searchInProgress =
                                                    false

                                                searchStatus =
                                                    if (
                                                        message.items
                                                            .isEmpty()
                                                    ) {
                                                        "No se encontraron videos."
                                                    } else {
                                                        "${message.items.size} resultados recibidos."
                                                    }
                                            }

                                            is BluetoothMessage.PlayPreparing -> {
                                                searchInProgress = false

                                                searchStatus =
                                                    message.message +
                                                            "\nVideo ID: ${message.videoId}"
                                            }

                                            is BluetoothMessage.BinaryTestInfo -> {
                                                expectedBinarySize =
                                                    message.sizeBytes
                                                expectedBinarySha256 =
                                                    message.sha256
                                                binaryTestStatus =
                                                    "Recibiendo ${message.sizeBytes / 1024} KB..."
                                            }

                                            is BluetoothMessage.MediaStart -> {
                                                resetBinaryTest()
                                                receivedVideoPath = null
                                                mediaInProgress = true
                                                mediaProgress = 0

                                                try {
                                                    incomingMediaReceiver.start(
                                                        fileName =
                                                            message.fileName,
                                                        totalBytes =
                                                            message.totalBytes,
                                                        sha256 =
                                                            message.sha256,
                                                        totalChunks =
                                                            message.totalChunks
                                                    )

                                                    mediaStatus =
                                                        "Recibiendo " +
                                                            "${message.totalBytes / 1024} KB..."
                                                } catch (error: Exception) {
                                                    mediaInProgress = false
                                                    mediaStatus =
                                                        error.message
                                                            ?: "No se pudo iniciar la recepción."
                                                }
                                            }

                                            BluetoothMessage.MediaEnd -> {
                                                val result =
                                                    incomingMediaReceiver
                                                        .finish()

                                                mediaInProgress = false
                                                mediaProgress =
                                                    if (result.success) {
                                                        100
                                                    } else {
                                                        0
                                                    }
                                                mediaStatus =
                                                    result.message
                                                receivedVideoPath =
                                                    result.file
                                                        ?.absolutePath

                                                connectionManager.sendMessage(
                                                    BluetoothProtocol
                                                        .mediaResult(
                                                            success =
                                                                result.success,
                                                            receivedBytes =
                                                                result.receivedBytes,
                                                            sha256 =
                                                                result.sha256,
                                                            message =
                                                                result.message
                                                        ),
                                                    onError = { error ->
                                                        mediaStatus = error
                                                    }
                                                )
                                            }

                                            is BluetoothMessage.ErrorMessage -> {
                                                searchInProgress = false
                                                binaryTestInProgress = false
                                                mediaInProgress = false
                                                searchStatus =
                                                    message.message
                                                mediaStatus =
                                                    message.message
                                            }

                                            is BluetoothMessage.Unknown -> {
                                                receivedMessage =
                                                    message.raw
                                            }

                                            else -> Unit
                                        }
                                    },
                                    onBinaryReceived = { data ->
                                        if (
                                            incomingMediaReceiver.isActive
                                        ) {
                                            try {
                                                val progress =
                                                    incomingMediaReceiver
                                                        .append(data)

                                                mediaProgress =
                                                    progress.percent

                                                mediaStatus =
                                                    "Recibiendo MP4: " +
                                                        "${progress.percent}% " +
                                                        "(${progress.receivedChunks}/" +
                                                        "${progress.totalChunks} bloques)"
                                            } catch (error: Exception) {
                                                incomingMediaReceiver.cancel()
                                                mediaInProgress = false
                                                mediaProgress = 0
                                                mediaStatus =
                                                    error.message
                                                        ?: "Error al recibir el MP4."
                                            }
                                        } else {
                                            val actualSha256 =
                                                BinaryTransferUtils.sha256(
                                                    data
                                                )

                                            val success =
                                                data.size ==
                                                    expectedBinarySize &&
                                                    actualSha256.equals(
                                                        expectedBinarySha256,
                                                        ignoreCase = true
                                                    )

                                            binaryTestInProgress = false

                                            binaryTestStatus =
                                                if (success) {
                                                    "Correcto: ${data.size / 1024} KB recibidos sin alteraciones."
                                                } else {
                                                    "Falló la verificación: se esperaban $expectedBinarySize bytes y llegaron ${data.size}."
                                                }

                                            connectionManager.sendMessage(
                                                BluetoothProtocol
                                                    .binaryTestResult(
                                                        success = success,
                                                        receivedBytes =
                                                            data.size,
                                                        sha256 =
                                                            actualSha256,
                                                        message =
                                                            binaryTestStatus
                                                    ),
                                                onError = { error ->
                                                    binaryTestStatus =
                                                        error
                                                }
                                            )
                                        }
                                    }
                                )
                        },
                        onDisconnectClick = {
                            connectionManager?.close()
                            connectionActive = false
                            connectionStatus =
                                "Conexión detenida"
                            receivedMessage = ""
                            searchResults = emptyList()
                            searchStatus = ""
                            resetBinaryTest()
                            resetMediaTransfer(clearPlayer = true)
                        },
                        onSearchClick = {
                            val query = searchQuery.trim()

                            if (query.isNotBlank()) {
                                searchInProgress = true
                                searchResults = emptyList()
                                searchStatus =
                                    "Buscando en YouTube..."

                                connectionManager
                                    ?.sendMessage(
                                        BluetoothProtocol
                                            .searchRequest(query),
                                        onError = { error ->
                                            searchInProgress = false
                                            searchStatus = error
                                        }
                                    )
                            }
                        },
                        onPlayClick = { video ->
                            searchStatus =
                                "Solicitando reproducción de:\n${video.title}"

                            connectionManager
                                ?.sendMessage(
                                    BluetoothProtocol
                                        .playRequest(
                                            videoId = video.videoId,
                                            title = video.title
                                        ),
                                    onError = { error ->
                                        searchStatus = error
                                    }
                                )
                        },
                        onBinaryTestClick = {
                            binaryTestInProgress = true
                            binaryTestStatus =
                                "Solicitando prueba binaria al servidor..."

                            connectionManager?.sendMessage(
                                BluetoothProtocol.binaryTestRequest(
                                    BinaryTransferUtils
                                        .DEFAULT_TEST_SIZE
                                ),
                                onError = { error ->
                                    binaryTestInProgress = false
                                    binaryTestStatus = error
                                }
                            )
                        },
                        onSampleMediaClick = {
                            mediaInProgress = true
                            mediaProgress = 0
                            mediaStatus =
                                "Solicitando MP4 de prueba al servidor..."
                            receivedVideoPath = null

                            connectionManager?.sendMessage(
                                BluetoothProtocol
                                    .sampleMediaRequest(),
                                onError = { error ->
                                    mediaInProgress = false
                                    mediaStatus = error
                                }
                            )
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            TextButton(onClick = onBackClick) {
                Text("Volver al inicio")
            }
        }
    }
}

@Composable
private fun ServerContent(
    connectionStatus: String,
    receivedMessage: String,
    connectionActive: Boolean,
    discoverableSecondsRemaining: Int,
    binaryTestStatus: String,
    mediaStatus: String,
    onMakeDiscoverableClick: () -> Unit,
    onStartServerClick: () -> Unit,
    onStopClick: () -> Unit
) {
    Text(
        text = "Bluetooth listo",
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold
    )

    Spacer(modifier = Modifier.height(16.dp))

    if (discoverableSecondsRemaining > 0) {
        val minutes =
            discoverableSecondsRemaining / 60
        val seconds =
            discoverableSecondsRemaining % 60

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment =
                    Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Servidor visible",
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text =
                        "Tiempo restante: $minutes:${
                            seconds.toString().padStart(2, '0')
                        }"
                )
            }
        }
    } else {
        OutlinedButton(
            onClick = onMakeDiscoverableClick,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Hacer visible durante 5 minutos")
        }
    }

    Spacer(modifier = Modifier.height(18.dp))

    if (
        connectionStatus.contains(
            "esperando",
            ignoreCase = true
        ) ||
        connectionStatus.contains(
            "consultando",
            ignoreCase = true
        ) ||
        connectionStatus.contains(
            "preparando",
            ignoreCase = true
        )
    ) {
        CircularProgressIndicator()
        Spacer(modifier = Modifier.height(14.dp))
    }

    Text(
        text = connectionStatus,
        textAlign = TextAlign.Center
    )

    Spacer(modifier = Modifier.height(18.dp))

    if (!connectionActive) {
        Button(
            onClick = onStartServerClick,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Iniciar servidor Bluetooth")
        }
    } else {
        OutlinedButton(
            onClick = onStopClick,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Detener servidor")
        }
    }

    if (receivedMessage.isNotBlank()) {
        Spacer(modifier = Modifier.height(18.dp))

        MessageCard(
            title = "Actividad recibida",
            message = receivedMessage
        )
    }

    if (binaryTestStatus.isNotBlank()) {
        Spacer(modifier = Modifier.height(18.dp))

        MessageCard(
            title = "Prueba binaria",
            message = binaryTestStatus
        )
    }

    if (mediaStatus.isNotBlank()) {
        Spacer(modifier = Modifier.height(18.dp))

        MessageCard(
            title = "Transferencia MP4",
            message = mediaStatus
        )
    }
}

@Composable
private fun ClientContent(
    pairedDevices: List<PairedBluetoothDevice>,
    selectedDeviceAddress: String?,
    connectionStatus: String,
    receivedMessage: String,
    connectionActive: Boolean,
    isConnected: Boolean,
    searchQuery: String,
    searchStatus: String,
    searchInProgress: Boolean,
    searchResults: List<YouTubeVideoResult>,
    binaryTestStatus: String,
    binaryTestInProgress: Boolean,
    mediaStatus: String,
    mediaInProgress: Boolean,
    mediaProgress: Int,
    receivedVideoPath: String?,
    onSearchQueryChanged: (String) -> Unit,
    onDeviceSelected: (String) -> Unit,
    onRefreshClick: () -> Unit,
    onOpenSettingsClick: () -> Unit,
    onConnectClick: (String) -> Unit,
    onDisconnectClick: () -> Unit,
    onSearchClick: () -> Unit,
    onPlayClick: (YouTubeVideoResult) -> Unit,
    onBinaryTestClick: () -> Unit,
    onSampleMediaClick: () -> Unit
) {
    Text(
        text = "Bluetooth listo",
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold
    )

    Spacer(modifier = Modifier.height(14.dp))

    Text(
        text = "Dispositivos emparejados",
        fontWeight = FontWeight.Bold
    )

    Spacer(modifier = Modifier.height(10.dp))

    if (pairedDevices.isEmpty()) {
        Text(
            text = "No hay dispositivos emparejados.",
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = onOpenSettingsClick,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Abrir ajustes Bluetooth")
        }
    } else {
        pairedDevices.forEach { device ->
            val selected =
                device.address == selectedDeviceAddress

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 5.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment =
                        Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = device.name,
                            fontWeight = FontWeight.Bold
                        )

                        Text(
                            text = device.address,
                            style =
                                MaterialTheme.typography.bodySmall
                        )
                    }

                    OutlinedButton(
                        onClick = {
                            onDeviceSelected(device.address)
                        },
                        enabled = !connectionActive
                    ) {
                        Text(
                            if (selected) {
                                "Seleccionado"
                            } else {
                                "Elegir"
                            }
                        )
                    }
                }
            }
        }

        OutlinedButton(
            onClick = onRefreshClick,
            enabled = !connectionActive,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Actualizar lista")
        }
    }

    if (
        selectedDeviceAddress != null &&
        !connectionActive
    ) {
        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = {
                onConnectClick(selectedDeviceAddress)
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Conectar con el servidor")
        }
    }

    if (connectionActive) {
        Spacer(modifier = Modifier.height(12.dp))

        OutlinedButton(
            onClick = onDisconnectClick,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Desconectar")
        }
    }

    Spacer(modifier = Modifier.height(14.dp))

    Text(
        text = connectionStatus,
        textAlign = TextAlign.Center
    )

    if (receivedMessage.isNotBlank()) {
        Spacer(modifier = Modifier.height(12.dp))

        MessageCard(
            title = "Servidor",
            message = receivedMessage
        )
    }

    if (isConnected) {
        Spacer(modifier = Modifier.height(22.dp))

        Text(
            text = "Diagnóstico del enlace",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(10.dp))

        Button(
            onClick = onBinaryTestClick,
            enabled = !binaryTestInProgress,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Probar transferencia binaria (256 KB)")
        }

        if (binaryTestInProgress) {
            Spacer(modifier = Modifier.height(12.dp))
            CircularProgressIndicator()
        }

        if (binaryTestStatus.isNotBlank()) {
            Spacer(modifier = Modifier.height(12.dp))

            MessageCard(
                title = "Resultado de la prueba",
                message = binaryTestStatus
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Prueba de reproducción MP4",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(10.dp))

        Button(
            onClick = onSampleMediaClick,
            enabled = !mediaInProgress,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Solicitar y reproducir MP4 de prueba")
        }

        if (mediaInProgress) {
            Spacer(modifier = Modifier.height(12.dp))
            CircularProgressIndicator()

            Spacer(modifier = Modifier.height(8.dp))
            Text("$mediaProgress%")
        }

        if (mediaStatus.isNotBlank()) {
            Spacer(modifier = Modifier.height(12.dp))

            MessageCard(
                title = "Transferencia multimedia",
                message = mediaStatus
            )
        }

        if (receivedVideoPath != null) {
            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "Video recibido por Bluetooth",
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(10.dp))

            LocalVideoPlayer(
                filePath = receivedVideoPath
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Buscar videos de YouTube",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(10.dp))

        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChanged,
            label = {
                Text("Título o palabras clave")
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(10.dp))

        Button(
            onClick = onSearchClick,
            enabled =
                searchQuery.isNotBlank() &&
                        !searchInProgress,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Buscar en YouTube")
        }

        if (searchInProgress) {
            Spacer(modifier = Modifier.height(14.dp))
            CircularProgressIndicator()
        }

        if (searchStatus.isNotBlank()) {
            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = searchStatus,
                textAlign = TextAlign.Center
            )
        }

        searchResults.forEach { video ->
            Spacer(modifier = Modifier.height(12.dp))

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = video.title,
                        fontWeight = FontWeight.Bold,
                        style =
                            MaterialTheme.typography.titleMedium
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = video.channelTitle,
                        style =
                            MaterialTheme.typography.bodyMedium
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "ID: ${video.videoId}",
                        style =
                            MaterialTheme.typography.bodySmall
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = {
                            onPlayClick(video)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Solicitar reproducción")
                    }
                }
            }
        }
    }
}

@Composable
private fun MessageCard(
    title: String,
    message: String
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = title,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(6.dp))
            Text(text = message)
        }
    }
}

@SuppressLint("MissingPermission")
private fun readPairedDevices(
    bluetoothAdapter: BluetoothAdapter
): List<PairedBluetoothDevice> {
    return try {
        bluetoothAdapter.bondedDevices
            .map { device ->
                PairedBluetoothDevice(
                    name = device.name
                        ?.takeIf { it.isNotBlank() }
                        ?: "Dispositivo sin nombre",
                    address = device.address
                )
            }
            .sortedBy { it.name.lowercase() }
    } catch (_: SecurityException) {
        emptyList()
    }
}

private fun requiredBluetoothPermissions(
    role: BluetoothRole
): Array<String> {
    return if (
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    ) {
        when (role) {
            BluetoothRole.SERVER -> arrayOf(
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_ADVERTISE
            )

            BluetoothRole.CLIENT -> arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT
            )
        }
    } else {
        when (role) {
            BluetoothRole.SERVER -> emptyArray()

            BluetoothRole.CLIENT -> arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        }
    }
}

private fun hasRequiredBluetoothPermissions(
    context: Context,
    role: BluetoothRole
): Boolean {
    return requiredBluetoothPermissions(role).all { permission ->
        ContextCompat.checkSelfPermission(
            context,
            permission
        ) == PackageManager.PERMISSION_GRANTED
    }
}
