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
import com.example.innertemp.ui.theme.Blue
import com.example.innertemp.ui.theme.Green
import com.example.innertemp.ui.theme.Red
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.Text
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material.icons.filled.History



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
    private val _isMonitoring = mutableStateOf(false) // New state for monitoring status

    // Core temperature average tracking
    private val _averageTemperatureCore = mutableStateOf(0.0)
    private var tempCoreSamples = mutableListOf<Double>()
    private var totalSamples = 0



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
                    _isMonitoring.value = false // Reset monitoring state on disconnect
                    _isPaused.value = false     // Reset pause state on disconnect
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
            if (_isMonitoring.value) {
                handleReceivedData(value)
            }
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
            // Only process data if monitoring is active
            if (_isMonitoring.value) {
                val value = characteristic.value
                handleReceivedData(value)
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
            // Ensure that there are at least 12 bytes in the array (4 bytes for each temperature and battery level)
            if (data.size >= 12) {
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

                // Round the values to two decimal places
                val roundedTemperatureSkin = String.format("%.2f", temperatureSkin).toDouble()
                val roundedTemperatureOutside = String.format("%.2f", temperatureOutside).toDouble()
                val roundedBatteryLevel = String.format("%.2f", batteryLevel).toDouble()
                val temperatureCore = roundedTemperatureSkin + roundedTemperatureOutside
                val roundedTemperatureCore = String.format("%.2f", temperatureCore).toDouble()

                // Only update UI if monitoring is active and not paused
                if (_isMonitoring.value && !_isPaused.value) {
                    runOnUiThread {
                        _temperatureSkin.value = roundedTemperatureSkin
                        _temperatureOutside.value = roundedTemperatureOutside
                        _temperatureCore.value = roundedTemperatureCore
                        _batteryLevel.value = roundedBatteryLevel

                        // Update running average for core temperature
                        updateAverageTemperature(roundedTemperatureCore)
                    }
                }
            } else {
                Log.e(TAG, "Data size is insufficient. Expected at least 12 bytes.")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing data: ${e.message}")
        }
    }

    private fun toggleMonitoring() {
        _isMonitoring.value = !_isMonitoring.value

        // If monitoring is being stopped, also reset the pause state
        if (!_isMonitoring.value) {
            _isPaused.value = false
        } else {
            // Reset average when starting a new monitoring session
            resetAverageTemperature()
        }

        // Optionally provide user feedback
        Toast.makeText(
            this,
            if (_isMonitoring.value) "Monitoring started" else "Monitoring stopped",
            Toast.LENGTH_SHORT
        ).show()
    }

    /**
     * Updates the running average of core temperature
     * Uses a cumulative moving average approach
     */
    private fun updateAverageTemperature(newValue: Double) {
        if (newValue <= 0) return  // Skip invalid readings

        tempCoreSamples.add(newValue)
        totalSamples++

        // Calculate running average
        val sum = tempCoreSamples.sum()
        _averageTemperatureCore.value = String.format("%.2f", sum / totalSamples).toDouble()


        // Optionally, limit the number of samples stored to prevent excessive memory usage
        // This is important for long monitoring sessions
        if (tempCoreSamples.size > 100) {  // Keep only the most recent 100 samples
            tempCoreSamples.removeAt(0)
        }

        Log.d(TAG, "Average core temp: ${_averageTemperatureCore.value}°C (from $totalSamples samples)")
    }

    /**
     * Resets the temperature average calculation
     */
    private fun resetAverageTemperature() {
        tempCoreSamples.clear()
        totalSamples = 0
        _averageTemperatureCore.value = 0.0
        Log.d(TAG, "Average temperature tracking reset")
    }

    private fun togglePause() {
        // Only allow pause/resume if monitoring is active
        if (_isMonitoring.value) {
            _isPaused.value = !_isPaused.value
            Toast.makeText(
                this,
                if (_isPaused.value) "Monitoring paused" else "Monitoring resumed",
                Toast.LENGTH_SHORT
            ).show()
        } else {
            Toast.makeText(this, "Start monitoring first", Toast.LENGTH_SHORT).show()
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
                        isMonitoring = _isMonitoring,
                        onPauseToggle = { togglePause() },
                        onMonitoringToggle = { toggleMonitoring() },
                        onGoToProfile = {
                            val intent = Intent(this, ProfileActivity::class.java)
                            startActivity(intent)
                            overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)},
                        onGoToHistory = {
                            val intent = Intent(this, HistoryActivity::class.java)
                            startActivity(intent)
                            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)},
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
        isMonitoring: State<Boolean>,
        onPauseToggle: () -> Unit,
        onMonitoringToggle: () -> Unit,
        onGoToProfile: () -> Unit,
        onGoToHistory: () -> Unit,
    ) {
        HomeScreen(
            isConnected = isConnected.value,
            temperatureOutside = temperatureOutside.value,
            temperatureSkin = temperatureSkin.value,
            temperatureCore = temperatureCore.value,
            averageTemperatureCore = _averageTemperatureCore.value,
            batteryLevel = batteryLevel.value,
            isPaused = isPaused.value,
            isMonitoring = isMonitoring.value,
            onPauseToggle = onPauseToggle,
            onMonitoringToggle = onMonitoringToggle,
            onGoToProfile = onGoToProfile,
            onGoToHistory = onGoToHistory,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    isConnected: Boolean,
    temperatureCore: Double,
    temperatureSkin: Double,
    temperatureOutside: Double,
    averageTemperatureCore: Double,
    batteryLevel: Double,
    isPaused: Boolean,
    isMonitoring: Boolean,
    onPauseToggle: () -> Unit,
    onMonitoringToggle: () -> Unit,
    onGoToProfile: () -> Unit,
    onGoToHistory: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val tempColorSkin = when {
        temperatureSkin < 36.0 -> Blue
        temperatureSkin > 38.0 -> Red
        else -> Green
    }

    val tempColorOutside = when {
        temperatureOutside < 36.0 -> Blue
        temperatureOutside > 38.0 -> Red
        else -> Green
    }
    val tempColorCore = when {
        temperatureCore < 36.0 -> Blue
        temperatureCore > 38.0 -> Red
        else -> Green
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Home",
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onGoToHistory) {
                        Icon(Icons.Default.History, contentDescription = "History")
                    }
                },
                actions = {
                    IconButton(onClick = onGoToProfile) {
                        Icon(Icons.Default.Person, contentDescription = "Profile")
                    }
                }
            )
        },
        content = { paddingValues ->
            Box(
                modifier = Modifier
                    .padding(paddingValues)
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
                        text = if (!isConnected) "-"
                        else if (!isMonitoring) "Monitoring Stopped"
                        else if (isPaused) "Paused"
                        else "${temperatureCore}°C",
                        color = tempColorCore,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold
                    )

                    // Display average core temperature when monitoring
                    if (isConnected && isMonitoring) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Average Core Temperature:",
                            color = colors.onBackground,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (averageTemperatureCore <= 0) "Calculating..."
                            else "${averageTemperatureCore}°C",
                            color = when {
                                averageTemperatureCore < 36.0 -> Blue
                                averageTemperatureCore > 38.0 -> Red
                                else -> Green
                            },
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    // TEMP DEBUG HERE
                    Text(
                        text = "Sensor reading skin:",
                        color = colors.onBackground,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        text = if (!isConnected) "-"
                        else if (!isMonitoring) "Monitoring Stopped"
                        else if (isPaused) "Paused"
                        else "${temperatureSkin}°C",
                        color = tempColorSkin,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold
                    )

                    Text(
                        text = "Sensor reading outside:",
                        color = colors.onBackground,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        text = if (!isConnected) "-"
                        else if (!isMonitoring) "Monitoring Stopped"
                        else if (isPaused) "Paused"
                        else "${temperatureOutside}°C",
                        color = tempColorOutside,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold
                    )

                    if (isConnected) {
                        Spacer(modifier = Modifier.height(24.dp))

                        // Start/Stop Button
                        Button(
                            onClick = onMonitoringToggle,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isMonitoring) Red else Green
                            ),
                            modifier = Modifier
                                .padding(top = 16.dp)
                                .defaultMinSize(minWidth = 120.dp)
                        ) {
                            Text(
                                text = if (isMonitoring) "Stop" else "Start",
                                color = colors.onPrimary
                            )
                        }

                        // Pause Button (only enabled when monitoring is active)
                        Button(
                            onClick = onPauseToggle,
                            enabled = isMonitoring,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = colors.primary,
                                disabledContainerColor = colors.primary.copy(alpha = 0.5f)
                            ),
                            modifier = Modifier
                                .padding(top = 8.dp)
                                .defaultMinSize(minWidth = 120.dp)
                        ) {
                            Text(
                                text = if (isPaused) "Resume" else "Pause",
                                color = if (isMonitoring) colors.onPrimary
                                else colors.onPrimary.copy(alpha = 0.5f)
                            )
                        }
                    }
                }
            }
        }
    )
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
            averageTemperatureCore = 36.4,
            batteryLevel = 75.0,
            isPaused = false,
            isMonitoring = true,
            onPauseToggle = {},
            onMonitoringToggle = {},
            onGoToProfile = {},
            onGoToHistory = {},
        )
    }
}