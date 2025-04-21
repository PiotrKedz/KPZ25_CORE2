package com.example.innertemp

import android.Manifest
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresPermission
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.background
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import com.example.innertemp.ui.theme.InnerTempTheme
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.*

private const val TAG = "InnerTemp"
private val SERVICE_UUID = UUID.fromString("4fafc201-1fb5-459e-8fcc-c5c9c331914b")
private val CHARACTERISTIC_UUID = UUID.fromString("beb5483e-36e1-4688-b7f5-ea07361b26a8")

class MainActivity : ComponentActivity() {
    private var bluetoothEnabled = false
    private lateinit var bluetoothAdapter: BluetoothAdapter
    private var bluetoothGatt: BluetoothGatt? = null
    private val _isConnected = mutableStateOf(false)
    private val _temperatureCore = mutableStateOf(0.0)
    private val _temperatureSkin = mutableStateOf(0.0)
    private val _temperatureOutside = mutableStateOf(0.0)
    private val _batteryLevel = mutableStateOf(0.0)
    private val _isPaused = mutableStateOf(false)



    private val enableBtLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                bluetoothEnabled = true
                Toast.makeText(this, "Bluetooth enabled", Toast.LENGTH_SHORT).show()
                startBleScan()
            } else {
                bluetoothEnabled = false
                Toast.makeText(this, "Bluetooth is required", Toast.LENGTH_SHORT).show()
            }
        }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (allGranted) {
            ensureBluetoothEnabled()
        } else {
            Toast.makeText(this, "Bluetooth permissions are required", Toast.LENGTH_SHORT).show()
        }
    }

    private fun ensureBluetoothEnabled() {
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter


        if (!bluetoothAdapter.isEnabled) {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            enableBtLauncher.launch(enableBtIntent)
        } else {
            bluetoothEnabled = true
            startBleScan()
        }
    }

    private fun checkPermissions() {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        } else {
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        }

        val allGranted = permissions.all {
            ActivityCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }

        if (allGranted) {
            ensureBluetoothEnabled()
        } else {
            permissionLauncher.launch(permissions)
        }
    }

    private fun startBleScan() {
        val scanner = bluetoothAdapter.bluetoothLeScanner

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) !=
                PackageManager.PERMISSION_GRANTED) {
                return
            }
        }

        Log.d(TAG, "Starting BLE scan")
        val scanSettings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        // For ESP32 devices, you might need to filter by name or service UUID
        scanner.startScan(null, scanSettings, scanCallback)

        // Stop scan after 10 seconds to conserve battery
        Handler(Looper.getMainLooper()).postDelayed({
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) !=
                    PackageManager.PERMISSION_GRANTED) {
                    return@postDelayed
                }
            }
            scanner.stopScan(scanCallback)
            Log.d(TAG, "BLE scan stopped")
        }, 10000)
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device
            Log.d(TAG, "Found device: ${device.name ?: "Unknown"} - ${device.address}")


            if (device.name?.contains("ESP") == true || device.name?.contains("GATT") == true) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (ActivityCompat.checkSelfPermission(this@MainActivity,
                            Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                        return
                    }
                }
                bluetoothAdapter.bluetoothLeScanner.stopScan(this)
                connectToDevice(device)
            }
        }

        override fun onScanFailed(errorCode: Int) {
            Log.e(TAG, "Scan failed with error: $errorCode")
        }
    }

    private fun connectToDevice(device: BluetoothDevice) {
        Log.d(TAG, "Connecting to ${device.address}")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ActivityCompat.checkSelfPermission(this,
                    Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                return
            }
        }

        bluetoothGatt = device.connectGatt(this, false, gattCallback)
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.d(TAG, "Connected to GATT server")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (ActivityCompat.checkSelfPermission(this@MainActivity,
                            Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                        return
                    }
                }
                runOnUiThread {
                    _isConnected.value = true
                }
                gatt.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.d(TAG, "Disconnected from GATT server")
                runOnUiThread {
                    _isConnected.value = false
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(TAG, "Services discovered")
                val service = gatt.getService(SERVICE_UUID)
                if (service != null) {
                    val characteristic = service.getCharacteristic(CHARACTERISTIC_UUID)
                    if (characteristic != null) {
                        enableNotifications(gatt, characteristic)
                    } else {
                        Log.e(TAG, "Characteristic not found")
                    }
                } else {
                    Log.e(TAG, "Service not found")
                    scanForCharacteristics(gatt)
                }
            }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            handleReceivedData(value)
        }

        // Support for older Android versions
        @Deprecated("Deprecated in Java")
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (ActivityCompat.checkSelfPermission(this@MainActivity,
                        Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                    return
                }
            }
        }
    }

    private fun scanForCharacteristics(gatt: BluetoothGatt) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ActivityCompat.checkSelfPermission(this,
                    Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                return
            }
        }

        for (service in gatt.services) {
            Log.d(TAG, "Found service: ${service.uuid}")
            for (characteristic in service.characteristics) {
                Log.d(TAG, "  Characteristic: ${characteristic.uuid}")
                val properties = characteristic.properties
                if ((properties and BluetoothGattCharacteristic.PROPERTY_READ) != 0 ||
                    (properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0) {
                    enableNotifications(gatt, characteristic)
                }
            }
        }
    }

    private fun enableNotifications(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ActivityCompat.checkSelfPermission(this,
                    Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                return
            }
        }
        gatt.setCharacteristicNotification(characteristic, true)
    }


    private fun handleReceivedData(data: ByteArray) {
        try {
            // Ensure that there are at least 8 bytes in the array (4 bytes for each temperature)
            if (data.size >= 8) {
                // First 4 bytes for the first temperature (assuming it's a Float)
                val temperatureSkin = ByteBuffer.wrap(data, 0, 4)
                    .order(ByteOrder.LITTLE_ENDIAN) // Use LITTLE_ENDIAN or BIG_ENDIAN based on your data format
                    .float.toDouble()  // Convert Float to Double

                // Next 4 bytes for the second temperature (assuming it's a Float)
                val temperatureOutside = ByteBuffer.wrap(data, 4, 4)
                    .order(ByteOrder.LITTLE_ENDIAN) // Use LITTLE_ENDIAN or BIG_ENDIAN based on your data format
                    .float.toDouble()  // Convert Float to Double

                val batteryLevel = ByteBuffer.wrap(data, 8, 4)
                    .order(ByteOrder.LITTLE_ENDIAN) // Use LITTLE_ENDIAN or BIG_ENDIAN based on your data format
                    .float.toDouble()  // Convert Float to Double

                val temperatureCore = temperatureSkin + temperatureOutside


                if (!_isPaused.value) {
                    runOnUiThread {

                        _temperatureSkin.value = temperatureSkin
                        _temperatureOutside.value = temperatureOutside
                        _temperatureCore.value = temperatureCore
                        _batteryLevel.value = batteryLevel
                    }
                }
            } else {
                Log.e(TAG, "Data size is insufficient. Expected at least 8 bytes.")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing data: ${e.message}")
        }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            InnerTempTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppContent(
                        isConnected = _isConnected,
                        temperatureCore = _temperatureCore,
                        temperatureSkin = _temperatureSkin,
                        temperatureOutside = _temperatureOutside,
                        batteryLevel = _batteryLevel,
                        isPaused = _isPaused,
                    )
                }
            }
        }
        checkPermissions()
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    override fun onDestroy() {
        super.onDestroy()
        bluetoothGatt?.close()
    }

    @Composable
    fun AppContent(
        isConnected: State<Boolean>,
        temperatureCore: State<Double>,
        temperatureSkin: State<Double>,
        temperatureOutside: State<Double>,
        batteryLevel: State<Double>,
        isPaused: State<Boolean>,

        ) {

        HomeScreen(
            isConnected = isConnected.value,
            temperatureOutside = temperatureOutside.value,
            temperatureSkin = temperatureSkin.value,
            temperatureCore = temperatureCore.value,
            batteryLevel = batteryLevel.value,
            isPaused = isPaused.value,
            onPauseToggle = { _isPaused.value = !_isPaused.value },
        )
    }
}

@Composable
fun HomeScreen(
    isConnected: Boolean,
    temperatureCore: Double,
    temperatureSkin: Double,
    temperatureOutside: Double,
    batteryLevel: Double,
    isPaused: Boolean,
    onPauseToggle: () -> Unit
) {

    val colors = MaterialTheme.colorScheme

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Device Status:",
                color = colors.onBackground,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = if (isConnected) "Connected" else "Disconnected",
                color = colors.onBackground,
                fontSize = 20.sp
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Battery Level:",
                color = colors.onBackground,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = if (isConnected) "${batteryLevel}%" else "-",
                color = colors.onBackground,
                fontSize = 20.sp
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Real-Time Temperature:",
                color = colors.onBackground,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = if (!isConnected) "-" else if (isPaused) "Paused" else "${temperatureCore}°C",
                color = colors.onBackground,
                fontSize = 20.sp
            )
            
            // TEMP DEBUG HERE

            Text(
                text = "Sensor reading skin:",
                color = colors.onBackground,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = if (!isConnected) "-" else if (isPaused) "Paused" else "${temperatureSkin}°C",
                color = colors.onBackground,
                fontSize = 10.sp
            )
            Text(
                text = "Sensor reading outside:",
                color = colors.onBackground,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = if (!isConnected) "-" else if (isPaused) "Paused" else "${temperatureOutside}°C",
                color = colors.onBackground,
                fontSize = 10.sp
            )

            if (isConnected) {
                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = onPauseToggle,
                    colors = ButtonDefaults.buttonColors(containerColor = colors.primary),
                    modifier = Modifier
                        .padding(top = 16.dp)
                        .defaultMinSize(minWidth = 120.dp)
                ) {
                    Text(
                        text = if (isPaused) "Resume" else "Pause",
                        color = colors.onPrimary
                    )
                }
            }
        }
    }
}


@Preview(showBackground = true)
@Composable
fun HomeScreenPreview() {
    InnerTempTheme {
        HomeScreen(
            isConnected = true,
            temperatureCore = 36.2,
            temperatureSkin = 36.2,
            temperatureOutside = 36.2,
            batteryLevel = 75.0,
            isPaused = false,
            onPauseToggle = {}
        )
    }
}