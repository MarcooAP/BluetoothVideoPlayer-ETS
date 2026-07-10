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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
        context.getSystemService(Context.BLUETOOTH_SERVICE)
                as? BluetoothManager
    }

    val bluetoothAdapter = bluetoothManager?.adapter

    val connectionManager = remember(bluetoothAdapter) {
        bluetoothAdapter?.let { adapter ->
            BluetoothConnectionManager(adapter)
        }
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

    /*
     * Cuando se abandona la pantalla se cierran los sockets
     * para no dejar conexiones o hilos abiertos.
     */
    DisposableEffect(connectionManager, role) {
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
            pairedDevices = readPairedDevices(bluetoothAdapter)
        }
    }

    val enableBluetoothLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.StartActivityForResult()
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
            contract = ActivityResultContracts.RequestMultiplePermissions()
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

    /*
     * Los callbacks del administrador llegan al hilo principal.
     * Aquí actualizamos lo que se muestra en pantalla.
     */
    val onConnectionStatusChanged: (String) -> Unit =
        { status ->
            connectionStatus = status

            if (
                status.startsWith("Error") ||
                status == "Conexión finalizada"
            ) {
                connectionActive = false
            }
        }

    val title = when (role) {
        BluetoothRole.SERVER -> "Modo servidor"
        BluetoothRole.CLIENT -> "Modo cliente"
    }

    Scaffold(
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(
                    horizontal = 28.dp,
                    vertical = 24.dp
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(28.dp))

            when {
                bluetoothAdapter == null -> {
                    Text(
                        text = "Este dispositivo no tiene un adaptador Bluetooth compatible.",
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center
                    )
                }

                !permissionsGranted -> {
                    Text(
                        text = when (role) {
                            BluetoothRole.SERVER ->
                                "El servidor necesita permisos para aceptar conexiones Bluetooth."

                            BluetoothRole.CLIENT ->
                                "El cliente necesita permisos para consultar y conectarse con dispositivos Bluetooth."
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center
                    )

                    if (permissionDenied) {
                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = "Los permisos fueron rechazados. Son necesarios para utilizar esta función.",
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

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
                        text = "Bluetooth está desactivado.",
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    Button(
                        onClick = {
                            enableBluetoothLauncher.launch(
                                Intent(
                                    BluetoothAdapter.ACTION_REQUEST_ENABLE
                                )
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Activar Bluetooth")
                    }
                }

                role == BluetoothRole.SERVER -> {
                    ServerConnectionContent(
                        connectionStatus = connectionStatus,
                        receivedMessage = receivedMessage,
                        connectionActive = connectionActive,
                        onStartServerClick = {
                            receivedMessage = ""
                            connectionStatus =
                                "Iniciando servidor..."
                            connectionActive = true

                            connectionManager?.startServer(
                                onStatusChanged =
                                    onConnectionStatusChanged,
                                onMessageReceived = { message ->
                                    receivedMessage = message
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
                    ClientConnectionContent(
                        pairedDevices = pairedDevices,
                        selectedDeviceAddress =
                            selectedDeviceAddress,
                        connectionStatus = connectionStatus,
                        receivedMessage = receivedMessage,
                        connectionActive = connectionActive,
                        onDeviceSelected = { address ->
                            selectedDeviceAddress = address
                        },
                        onRefreshClick = {
                            pairedDevices =
                                readPairedDevices(bluetoothAdapter)
                        },
                        onOpenSettingsClick = {
                            context.startActivity(
                                Intent(
                                    Settings.ACTION_BLUETOOTH_SETTINGS
                                )
                            )
                        },
                        onConnectClick = { address ->
                            receivedMessage = ""
                            connectionStatus =
                                "Preparando conexión..."
                            connectionActive = true

                            connectionManager?.connectToServer(
                                deviceAddress = address,
                                onStatusChanged =
                                    onConnectionStatusChanged,
                                onMessageReceived = { message ->
                                    receivedMessage = message
                                }
                            )
                        },
                        onDisconnectClick = {
                            connectionManager?.close()
                            connectionActive = false
                            connectionStatus =
                                "Conexión detenida"
                            receivedMessage = ""
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            TextButton(
                onClick = onBackClick
            ) {
                Text("Volver al inicio")
            }
        }
    }
}

@Composable
private fun ServerConnectionContent(
    connectionStatus: String,
    receivedMessage: String,
    connectionActive: Boolean,
    onStartServerClick: () -> Unit,
    onStopClick: () -> Unit
) {
    Text(
        text = "Bluetooth listo",
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold
    )

    Spacer(modifier = Modifier.height(20.dp))

    if (
        connectionStatus.contains("esperando", ignoreCase = true) ||
        connectionStatus.contains("iniciando", ignoreCase = true)
    ) {
        CircularProgressIndicator()

        Spacer(modifier = Modifier.height(18.dp))
    }

    Text(
        text = connectionStatus,
        style = MaterialTheme.typography.bodyLarge,
        textAlign = TextAlign.Center
    )

    Spacer(modifier = Modifier.height(20.dp))

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
        Spacer(modifier = Modifier.height(20.dp))

        MessageCard(
            title = "Mensaje recibido",
            message = receivedMessage
        )
    }
}

@Composable
private fun ClientConnectionContent(
    pairedDevices: List<PairedBluetoothDevice>,
    selectedDeviceAddress: String?,
    connectionStatus: String,
    receivedMessage: String,
    connectionActive: Boolean,
    onDeviceSelected: (String) -> Unit,
    onRefreshClick: () -> Unit,
    onOpenSettingsClick: () -> Unit,
    onConnectClick: (String) -> Unit,
    onDisconnectClick: () -> Unit
) {
    Text(
        text = "Bluetooth listo",
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold
    )

    Spacer(modifier = Modifier.height(16.dp))

    Text(
        text = "Dispositivos emparejados",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold
    )

    Spacer(modifier = Modifier.height(12.dp))

    if (pairedDevices.isEmpty()) {
        Text(
            text = "No se encontraron dispositivos emparejados.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = onOpenSettingsClick,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Abrir ajustes Bluetooth")
        }
    } else {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 260.dp),
            verticalArrangement =
                Arrangement.spacedBy(10.dp)
        ) {
            items(
                items = pairedDevices,
                key = { device -> device.address }
            ) { device ->

                val isSelected =
                    device.address == selectedDeviceAddress

                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment =
                            Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = device.name,
                                style =
                                    MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )

                            Spacer(
                                modifier = Modifier.height(4.dp)
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
                                text = if (isSelected) {
                                    "Seleccionado"
                                } else {
                                    "Elegir"
                                }
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

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
        Spacer(modifier = Modifier.height(14.dp))

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
        Spacer(modifier = Modifier.height(14.dp))

        OutlinedButton(
            onClick = onDisconnectClick,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Desconectar")
        }
    }

    Spacer(modifier = Modifier.height(18.dp))

    Text(
        text = connectionStatus,
        style = MaterialTheme.typography.bodyLarge,
        textAlign = TextAlign.Center
    )

    if (receivedMessage.isNotBlank()) {
        Spacer(modifier = Modifier.height(18.dp))

        MessageCard(
            title = "Respuesta del servidor",
            message = receivedMessage
        )
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
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium
            )
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
            .sortedBy { device ->
                device.name.lowercase()
            }
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