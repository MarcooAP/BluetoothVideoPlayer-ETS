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
        context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    }

    val bluetoothAdapter = bluetoothManager?.adapter

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
            bluetoothEnabled = bluetoothAdapter?.isEnabled == true

            if (
                role == BluetoothRole.CLIENT &&
                bluetoothEnabled &&
                bluetoothAdapter != null
            ) {
                pairedDevices = readPairedDevices(bluetoothAdapter)
            }
        }

    val permissionLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestMultiplePermissions()
        ) {
            permissionsGranted = hasRequiredBluetoothPermissions(
                context = context,
                role = role
            )

            permissionDenied = !permissionsGranted

            if (permissionsGranted) {
                bluetoothEnabled = bluetoothAdapter?.isEnabled == true

                if (
                    role == BluetoothRole.CLIENT &&
                    bluetoothEnabled &&
                    bluetoothAdapter != null
                ) {
                    pairedDevices = readPairedDevices(bluetoothAdapter)
                }
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
                .padding(horizontal = 28.dp, vertical = 24.dp),
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
                                "El servidor necesita permiso para conectarse y hacerse visible mediante Bluetooth."

                            BluetoothRole.CLIENT ->
                                "El cliente necesita permiso para buscar y conectarse con dispositivos Bluetooth."
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
                                Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Activar Bluetooth")
                    }
                }

                else -> {
                    Text(
                        text = "Bluetooth listo",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    when (role) {
                        BluetoothRole.SERVER -> {
                            CircularProgressIndicator()

                            Spacer(modifier = Modifier.height(24.dp))

                            Text(
                                text = "El dispositivo está preparado para iniciar el servidor Bluetooth.",
                                style = MaterialTheme.typography.bodyLarge,
                                textAlign = TextAlign.Center
                            )
                        }

                        BluetoothRole.CLIENT -> {
                            ClientPairedDevicesContent(
                                pairedDevices = pairedDevices,
                                selectedDeviceAddress = selectedDeviceAddress,
                                onDeviceSelected = { address ->
                                    selectedDeviceAddress = address
                                },
                                onRefreshClick = {
                                    if (bluetoothAdapter != null) {
                                        pairedDevices =
                                            readPairedDevices(bluetoothAdapter)
                                    }
                                },
                                onOpenSettingsClick = {
                                    context.startActivity(
                                        Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
                                    )
                                }
                            )
                        }
                    }
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
private fun ClientPairedDevicesContent(
    pairedDevices: List<PairedBluetoothDevice>,
    selectedDeviceAddress: String?,
    onDeviceSelected: (String) -> Unit,
    onRefreshClick: () -> Unit,
    onOpenSettingsClick: () -> Unit
) {
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
                .heightIn(max = 320.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
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
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = device.name,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            Text(
                                text = device.address,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }

                        OutlinedButton(
                            onClick = {
                                onDeviceSelected(device.address)
                            }
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

        Spacer(modifier = Modifier.height(14.dp))

        OutlinedButton(
            onClick = onRefreshClick,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Actualizar lista")
        }

        if (selectedDeviceAddress != null) {
            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Servidor seleccionado: $selectedDeviceAddress",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
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
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
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