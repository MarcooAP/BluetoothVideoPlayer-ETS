package mx.ipn.escom.bluetoothvideoplayer

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
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

    LaunchedEffect(
        bluetoothAdapter,
        permissionsGranted
    ) {
        bluetoothEnabled =
            bluetoothAdapter != null &&
                    permissionsGranted &&
                    bluetoothAdapter.isEnabled
    }

    val enableBluetoothLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.StartActivityForResult()
        ) {
            bluetoothEnabled = bluetoothAdapter?.isEnabled == true
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
                .padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(32.dp))

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
                            val enableIntent = Intent(
                                BluetoothAdapter.ACTION_REQUEST_ENABLE
                            )

                            enableBluetoothLauncher.launch(enableIntent)
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

                    if (role == BluetoothRole.SERVER) {
                        CircularProgressIndicator()

                        Spacer(modifier = Modifier.height(24.dp))

                        Text(
                            text = "El dispositivo está preparado para iniciar el servidor Bluetooth.",
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.Center
                        )
                    } else {
                        Text(
                            text = "El dispositivo está preparado para buscar y conectarse con el servidor.",
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            TextButton(
                onClick = onBackClick
            ) {
                Text("Volver al inicio")
            }
        }
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