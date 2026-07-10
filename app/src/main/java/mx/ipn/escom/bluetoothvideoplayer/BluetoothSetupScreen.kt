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
import androidx.compose.foundation.layout.Arrangement
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

    val bluetoothAdapter =
        bluetoothManager?.adapter

    val connectionManager =
        remember(bluetoothAdapter) {
            bluetoothAdapter?.let { adapter ->
                BluetoothConnectionManager(adapter)
            }
        }

    val youtubeSearchService =
        remember(context) {
            YouTubeSearchService(
                context.applicationContext
            )
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
        mutableStateOf(
            emptyList<PairedBluetoothDevice>()
        )
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

    var discoverableSecondsRemaining by
    rememberSaveable(role.name) {
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
        mutableStateOf(
            emptyList<YouTubeVideoResult>()
        )
    }

    LaunchedEffect(discoverableSecondsRemaining) {
        if (discoverableSecondsRemaining > 0) {
            delay(1000L)
            discoverableSecondsRemaining -= 1
        }
    }

    DisposableEffect(
        connectionManager,
        role
    ) {
        onDispose {
            connectionManager?.close()
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
            pairedDevices =
                readPairedDevices(bluetoothAdapter)
        }
    }

    val enableBluetoothLauncher =
        rememberLauncherForActivityResult(
            contract =
                ActivityResultContracts
                    .StartActivityForResult()
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
                ActivityResultContracts
                    .RequestMultiplePermissions()
        ) {
            permissionsGranted =
                hasRequiredBluetoothPermissions(
                    context = context,
                    role = role
                )

            permissionDenied =
                !permissionsGranted

            if (permissionsGranted) {
                bluetoothEnabled =
                    bluetoothAdapter?.isEnabled == true

                if (
                    role == BluetoothRole.CLIENT &&
                    bluetoothEnabled &&
                    bluetoothAdapter != null
                ) {
                    pairedDevices =
                        readPairedDevices(
                            bluetoothAdapter
                        )
                }
            }
        }

    val discoverableLauncher =
        rememberLauncherForActivityResult(
            contract =
                ActivityResultContracts
                    .StartActivityForResult()
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
        }
    }

    val isConnected =
        connectionStatus.startsWith("Conectado")

    Scaffold(
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(
                    rememberScrollState()
                )
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
                    MaterialTheme
                        .typography
                        .headlineMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(
                modifier = Modifier.height(24.dp)
            )

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
                        Spacer(
                            modifier =
                                Modifier.height(12.dp)
                        )

                        Text(
                            text =
                                "Los permisos fueron rechazados.",
                            textAlign = TextAlign.Center
                        )
                    }

                    Spacer(
                        modifier = Modifier.height(20.dp)
                    )

                    Button(
                        onClick = {
                            permissionDenied = false

                            permissionLauncher.launch(
                                requiredBluetoothPermissions(
                                    role
                                )
                            )
                        },
                        modifier =
                            Modifier.fillMaxWidth()
                    ) {
                        Text(
                            "Conceder permisos Bluetooth"
                        )
                    }
                }

                !bluetoothEnabled -> {
                    Text(
                        text =
                            "Bluetooth está desactivado."
                    )

                    Spacer(
                        modifier = Modifier.height(20.dp)
                    )

                    Button(
                        onClick = {
                            enableBluetoothLauncher.launch(
                                Intent(
                                    BluetoothAdapter
                                        .ACTION_REQUEST_ENABLE
                                )
                            )
                        },
                        modifier =
                            Modifier.fillMaxWidth()
                    ) {
                        Text("Activar Bluetooth")
                    }
                }

                role == BluetoothRole.SERVER -> {
                    ServerContent(
                        connectionStatus =
                            connectionStatus,
                        receivedMessage =
                            receivedMessage,
                        connectionActive =
                            connectionActive,
                        discoverableSecondsRemaining =
                            discoverableSecondsRemaining,
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

                            discoverableLauncher.launch(
                                intent
                            )
                        },
                        onStartServerClick = {
                            receivedMessage = ""
                            connectionStatus =
                                "Iniciando servidor..."
                            connectionActive = true

                            connectionManager?.startServer(
                                onStatusChanged =
                                    onConnectionStatusChanged,
                                onMessageReceived = { raw ->

                                    when (
                                        val message =
                                            BluetoothProtocol
                                                .parse(raw)
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
                                            val query =
                                                message.query

                                            receivedMessage =
                                                "Búsqueda solicitada: $query"

                                            connectionStatus =
                                                "Consultando YouTube..."

                                            youtubeSearchService
                                                .searchVideos(
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
                                                                    .error(
                                                                        error
                                                                    )
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

                                        is BluetoothMessage.Unknown -> {
                                            receivedMessage =
                                                message.raw
                                        }

                                        else -> Unit
                                    }
                                }
                            )
                        },
                        onStopClick = {
                            connectionManager?.close()
                            connectionActive = false
                            connectionStatus =
                                "Servidor detenido"
                            receivedMessage = ""
                        }
                    )
                }

                else -> {
                    ClientContent(
                        pairedDevices =
                            pairedDevices,
                        selectedDeviceAddress =
                            selectedDeviceAddress,
                        connectionStatus =
                            connectionStatus,
                        receivedMessage =
                            receivedMessage,
                        connectionActive =
                            connectionActive,
                        isConnected =
                            isConnected,
                        searchQuery =
                            searchQuery,
                        searchStatus =
                            searchStatus,
                        searchInProgress =
                            searchInProgress,
                        searchResults =
                            searchResults,
                        onSearchQueryChanged = {
                            searchQuery = it
                        },
                        onDeviceSelected = {
                            selectedDeviceAddress = it
                        },
                        onRefreshClick = {
                            pairedDevices =
                                readPairedDevices(
                                    bluetoothAdapter
                                )
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
                            searchResults =
                                emptyList()
                            searchStatus = ""

                            connectionStatus =
                                "Preparando conexión..."
                            connectionActive = true

                            connectionManager
                                ?.connectToServer(
                                    deviceAddress =
                                        address,
                                    onStatusChanged =
                                        onConnectionStatusChanged,
                                    onMessageReceived = { raw ->

                                        when (
                                            val message =
                                                BluetoothProtocol
                                                    .parse(raw)
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
                                                        message
                                                            .items
                                                            .isEmpty()
                                                    ) {
                                                        "No se encontraron videos."
                                                    } else {
                                                        "${message.items.size} resultados recibidos."
                                                    }
                                            }

                                            is BluetoothMessage.PlayPreparing -> {
                                                searchInProgress =
                                                    false

                                                searchStatus =
                                                    message.message +
                                                            "\nVideo ID: ${message.videoId}"
                                            }

                                            is BluetoothMessage.ErrorMessage -> {
                                                searchInProgress =
                                                    false

                                                searchStatus =
                                                    message.message
                                            }

                                            is BluetoothMessage.Unknown -> {
                                                receivedMessage =
                                                    message.raw
                                            }

                                            else -> Unit
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
                            searchResults =
                                emptyList()
                            searchStatus = ""
                        },
                        onSearchClick = {
                            val query =
                                searchQuery.trim()

                            if (query.isNotBlank()) {
                                searchInProgress = true
                                searchResults =
                                    emptyList()

                                searchStatus =
                                    "Buscando en YouTube..."

                                connectionManager
                                    ?.sendMessage(
                                        message =
                                            BluetoothProtocol
                                                .searchRequest(
                                                    query
                                                ),
                                        onError = { error ->
                                            searchInProgress =
                                                false
                                            searchStatus =
                                                error
                                        }
                                    )
                            }
                        },
                        onPlayClick = { video ->
                            searchStatus =
                                "Solicitando reproducción de:\n${video.title}"

                            connectionManager
                                ?.sendMessage(
                                    message =
                                        BluetoothProtocol
                                            .playRequest(
                                                videoId =
                                                    video.videoId,
                                                title =
                                                    video.title
                                            ),
                                    onError = { error ->
                                        searchStatus =
                                            error
                                    }
                                )
                        }
                    )
                }
            }

            Spacer(
                modifier = Modifier.height(24.dp)
            )

            TextButton(
                onClick = onBackClick
            ) {
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
    onMakeDiscoverableClick: () -> Unit,
    onStartServerClick: () -> Unit,
    onStopClick: () -> Unit
) {
    Text(
        text = "Bluetooth listo",
        style =
            MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold
    )

    Spacer(
        modifier = Modifier.height(16.dp)
    )

    if (discoverableSecondsRemaining > 0) {
        val minutes =
            discoverableSecondsRemaining / 60

        val seconds =
            discoverableSecondsRemaining % 60

        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier =
                    Modifier.padding(16.dp),
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
                            seconds
                                .toString()
                                .padStart(2, '0')
                        }"
                )
            }
        }
    } else {
        OutlinedButton(
            onClick =
                onMakeDiscoverableClick,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                "Hacer visible durante 5 minutos"
            )
        }
    }

    Spacer(
        modifier = Modifier.height(18.dp)
    )

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

        Spacer(
            modifier = Modifier.height(14.dp)
        )
    }

    Text(
        text = connectionStatus,
        textAlign = TextAlign.Center
    )

    Spacer(
        modifier = Modifier.height(18.dp)
    )

    if (!connectionActive) {
        Button(
            onClick = onStartServerClick,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                "Iniciar servidor Bluetooth"
            )
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
        Spacer(
            modifier = Modifier.height(18.dp)
        )

        MessageCard(
            title = "Actividad recibida",
            message = receivedMessage
        )
    }
}

@Composable
private fun ClientContent(
    pairedDevices:
    List<PairedBluetoothDevice>,
    selectedDeviceAddress: String?,
    connectionStatus: String,
    receivedMessage: String,
    connectionActive: Boolean,
    isConnected: Boolean,
    searchQuery: String,
    searchStatus: String,
    searchInProgress: Boolean,
    searchResults:
    List<YouTubeVideoResult>,
    onSearchQueryChanged: (String) -> Unit,
    onDeviceSelected: (String) -> Unit,
    onRefreshClick: () -> Unit,
    onOpenSettingsClick: () -> Unit,
    onConnectClick: (String) -> Unit,
    onDisconnectClick: () -> Unit,
    onSearchClick: () -> Unit,
    onPlayClick:
        (YouTubeVideoResult) -> Unit
) {
    Text(
        text = "Bluetooth listo",
        style =
            MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold
    )

    Spacer(
        modifier = Modifier.height(14.dp)
    )

    Text(
        text = "Dispositivos emparejados",
        fontWeight = FontWeight.Bold
    )

    Spacer(
        modifier = Modifier.height(10.dp)
    )

    if (pairedDevices.isEmpty()) {
        Text(
            text =
                "No hay dispositivos emparejados.",
            textAlign = TextAlign.Center
        )

        Spacer(
            modifier = Modifier.height(12.dp)
        )

        Button(
            onClick = onOpenSettingsClick,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Abrir ajustes Bluetooth")
        }
    } else {
        pairedDevices.forEach { device ->
            val selected =
                device.address ==
                        selectedDeviceAddress

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
                        modifier =
                            Modifier.weight(1f)
                    ) {
                        Text(
                            text = device.name,
                            fontWeight =
                                FontWeight.Bold
                        )

                        Text(
                            text = device.address,
                            style =
                                MaterialTheme
                                    .typography
                                    .bodySmall
                        )
                    }

                    OutlinedButton(
                        onClick = {
                            onDeviceSelected(
                                device.address
                            )
                        },
                        enabled =
                            !connectionActive
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
        Spacer(
            modifier = Modifier.height(12.dp)
        )

        Button(
            onClick = {
                onConnectClick(
                    selectedDeviceAddress
                )
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Conectar con el servidor")
        }
    }

    if (connectionActive) {
        Spacer(
            modifier = Modifier.height(12.dp)
        )

        OutlinedButton(
            onClick = onDisconnectClick,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Desconectar")
        }
    }

    Spacer(
        modifier = Modifier.height(14.dp)
    )

    Text(
        text = connectionStatus,
        textAlign = TextAlign.Center
    )

    if (receivedMessage.isNotBlank()) {
        Spacer(
            modifier = Modifier.height(12.dp)
        )

        MessageCard(
            title = "Servidor",
            message = receivedMessage
        )
    }

    if (isConnected) {
        Spacer(
            modifier = Modifier.height(24.dp)
        )

        Text(
            text = "Buscar videos de YouTube",
            style =
                MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        Spacer(
            modifier = Modifier.height(10.dp)
        )

        OutlinedTextField(
            value = searchQuery,
            onValueChange =
                onSearchQueryChanged,
            label = {
                Text(
                    "Título o palabras clave"
                )
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(
            modifier = Modifier.height(10.dp)
        )

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
            Spacer(
                modifier = Modifier.height(14.dp)
            )

            CircularProgressIndicator()
        }

        if (searchStatus.isNotBlank()) {
            Spacer(
                modifier = Modifier.height(12.dp)
            )

            Text(
                text = searchStatus,
                textAlign = TextAlign.Center
            )
        }

        searchResults.forEach { video ->
            Spacer(
                modifier = Modifier.height(12.dp)
            )

            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier =
                        Modifier.padding(16.dp)
                ) {
                    Text(
                        text = video.title,
                        fontWeight =
                            FontWeight.Bold,
                        style =
                            MaterialTheme
                                .typography
                                .titleMedium
                    )

                    Spacer(
                        modifier =
                            Modifier.height(6.dp)
                    )

                    Text(
                        text =
                            video.channelTitle,
                        style =
                            MaterialTheme
                                .typography
                                .bodyMedium
                    )

                    Spacer(
                        modifier =
                            Modifier.height(4.dp)
                    )

                    Text(
                        text =
                            "ID: ${video.videoId}",
                        style =
                            MaterialTheme
                                .typography
                                .bodySmall
                    )

                    Spacer(
                        modifier =
                            Modifier.height(12.dp)
                    )

                    Button(
                        onClick = {
                            onPlayClick(video)
                        },
                        modifier =
                            Modifier.fillMaxWidth()
                    ) {
                        Text(
                            "Solicitar reproducción"
                        )
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
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = title,
                fontWeight = FontWeight.Bold
            )

            Spacer(
                modifier = Modifier.height(6.dp)
            )

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
                        ?.takeIf {
                            it.isNotBlank()
                        }
                        ?: "Dispositivo sin nombre",
                    address = device.address
                )
            }
            .sortedBy {
                it.name.lowercase()
            }
    } catch (_: SecurityException) {
        emptyList()
    }
}

private fun requiredBluetoothPermissions(
    role: BluetoothRole
): Array<String> {
    return if (
        Build.VERSION.SDK_INT >=
        Build.VERSION_CODES.S
    ) {
        when (role) {
            BluetoothRole.SERVER -> arrayOf(
                Manifest.permission
                    .BLUETOOTH_CONNECT,
                Manifest.permission
                    .BLUETOOTH_ADVERTISE
            )

            BluetoothRole.CLIENT -> arrayOf(
                Manifest.permission
                    .BLUETOOTH_SCAN,
                Manifest.permission
                    .BLUETOOTH_CONNECT
            )
        }
    } else {
        when (role) {
            BluetoothRole.SERVER ->
                emptyArray()

            BluetoothRole.CLIENT ->
                arrayOf(
                    Manifest.permission
                        .ACCESS_FINE_LOCATION
                )
        }
    }
}

private fun hasRequiredBluetoothPermissions(
    context: Context,
    role: BluetoothRole
): Boolean {
    return requiredBluetoothPermissions(
        role
    ).all { permission ->
        ContextCompat.checkSelfPermission(
            context,
            permission
        ) ==
                PackageManager.PERMISSION_GRANTED
    }
}