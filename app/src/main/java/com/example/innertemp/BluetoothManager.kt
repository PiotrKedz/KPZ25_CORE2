package com.example.innertemp

import android.Manifest
import android.app.Activity
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
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.*

private const val TAG = "InnerTempBluetooth"
private val SERVICE_UUID = UUID.fromString("4fafc201-1fb5-459e-8fcc-c5c9c331914b")
private val CHARACTERISTIC_UUID = UUID.fromString("beb5483e-36e1-4688-b7f5-ea07361b26a8")
private val CLIENT_CHARACTERISTIC_CONFIG = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

class BluetoothManager(private val context: Context) {
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var bluetoothGatt: BluetoothGatt? = null
    private var isMonitoring = false
    private var isPaused = false

    // Callbacks for status updates with updated signatures to match MainActivity
    private var onConnectionStatusChanged: ((Boolean, Int) -> Unit)? = null
    private var onMonitoringStatusChanged: ((Boolean) -> Unit)? = null
    private var onPauseStatusChanged: ((Boolean) -> Unit)? = null
    private var onDataReceived: ((Double, Double, Double, Double) -> Unit)? = null

    private var rssiUpdateInterval = 2000L // Changed from 100ms to 2 seconds
    private var lastRssiValue = -100 // Default poor signal
    private var isUpdatingRssi = false
    private var rssiUpdateRunnable: Runnable? = null

    // Define the mainHandler for RSSI updates
    private val mainHandler = Handler(Looper.getMainLooper())

    // Moved from MainActivity - Activity Result Launchers
    private var enableBtLauncher: ActivityResultLauncher<Intent>? = null
    private var permissionLauncher: ActivityResultLauncher<Array<String>>? = null

    init {
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as android.bluetooth.BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter
    }

    /**
     * Initialize the activity result launchers.
     * Must be called from the Activity's onCreate method before using Bluetooth functions.
     */
    fun initializeLaunchers(activity: Activity) {
        // Create a wrapper for ActivityResultLauncher initialization since it must be done in Activity
        if (activity is MainActivity) {
            setupActivityResultLaunchers(activity)
        } else {
            Log.e(TAG, "Activity must be MainActivity to initialize launchers")
        }
    }

    private fun setupActivityResultLaunchers(activity: MainActivity) {
        // Initialize enableBtLauncher
        enableBtLauncher = activity.registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                Toast.makeText(context, "Bluetooth enabled", Toast.LENGTH_SHORT).show()
                startBleScan()
            } else {
                Toast.makeText(context, "Bluetooth is required", Toast.LENGTH_SHORT).show()
            }
        }

        // Initialize permissionLauncher
        permissionLauncher = activity.registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            val allGranted = permissions.entries.all { it.value }
            if (allGranted) {
                ensureBluetoothEnabled()
            } else {
                Toast.makeText(context, "Bluetooth permissions are required", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * Moved from MainActivity - Called when the activity is created.
     * This function is now adapted to be called from MainActivity's onCreate.
     */
    fun onCreate(savedInstanceState: Bundle?) {
        // Only check permissions if launchers are initialized
        if (enableBtLauncher != null && permissionLauncher != null) {
            checkPermissions()
        } else {
            Log.e(TAG, "Launchers not initialized. Call initializeLaunchers first.")
        }
    }

    /**
     * Moved from MainActivity - Ensures the necessary permissions are granted.
     */
    fun checkPermissions() {
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
            ActivityCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }

        if (allGranted) {
            ensureBluetoothEnabled()
        } else {
            permissionLauncher?.launch(permissions) ?:
            Log.e(TAG, "Permission launcher not initialized")
        }
    }

    /**
     * Moved from MainActivity - Ensures Bluetooth is enabled.
     */
    fun ensureBluetoothEnabled() {
        if (!isBluetoothEnabled()) {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            enableBtLauncher?.launch(enableBtIntent) ?:
            Log.e(TAG, "Bluetooth launcher not initialized")
        } else {
            Log.d(TAG, "Starting BLE scan...")
            startBleScan()
        }
    }

    /**
     * Moved from MainActivity - Called when the activity is destroyed.
     */
    fun onDestroy() {
        closeConnection()
    }

    private fun startRssiUpdates() {
        if (isUpdatingRssi) {
            stopRssiUpdates() // Stop existing updates before starting new ones
        }

        isUpdatingRssi = true

        rssiUpdateRunnable = Runnable {
            updateRssi()
        }

        // Start the first update
        rssiUpdateRunnable?.let { mainHandler.post(it) }

        Log.d(TAG, "RSSI updates started with interval: $rssiUpdateInterval ms")
    }

    private fun stopRssiUpdates() {
        isUpdatingRssi = false
        rssiUpdateRunnable?.let { mainHandler.removeCallbacks(it) }
        rssiUpdateRunnable = null
        Log.d(TAG, "RSSI updates stopped")
    }

    private fun updateRssi() {
        if (!isUpdatingRssi || bluetoothGatt == null) {
            Log.d(TAG, "Skipping RSSI update: isUpdating=$isUpdatingRssi, gatt=${bluetoothGatt != null}")
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ActivityCompat.checkSelfPermission(
                    context,
                    Manifest.permission.BLUETOOTH_CONNECT
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                Log.e(TAG, "Missing BLUETOOTH_CONNECT permission for RSSI update")
                return
            }
        }

        try {
            bluetoothGatt?.readRemoteRssi()
            Log.d(TAG, "Requested RSSI reading")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read RSSI: ${e.message}")
        }

        // Schedule the next update only if still updating
        if (isUpdatingRssi) {
            rssiUpdateRunnable?.let {
                mainHandler.removeCallbacks(it)
                mainHandler.postDelayed(it, rssiUpdateInterval)
            }
        }
    }

    fun setRssiUpdateInterval(intervalMs: Long) {
        if (intervalMs < 1000) {
            Log.w(TAG, "RSSI update interval too short, setting to minimum 1000ms")
            rssiUpdateInterval = 1000L
        } else {
            rssiUpdateInterval = intervalMs
        }

        // Restart updates with new interval if already running
        if (isUpdatingRssi && bluetoothGatt != null) {
            stopRssiUpdates()
            startRssiUpdates()
        }
    }

    fun setOnConnectionStatusChanged(callback: (Boolean, Int) -> Unit) {
        onConnectionStatusChanged = callback
    }

    fun setOnMonitoringStatusChanged(callback: (Boolean) -> Unit) {
        onMonitoringStatusChanged = callback
    }

    fun setOnPauseStatusChanged(callback: (Boolean) -> Unit) {
        onPauseStatusChanged = callback
    }

    fun setOnDataReceived(callback: (Double, Double, Double, Double) -> Unit) {
        onDataReceived = callback
    }

    fun isBluetoothEnabled(): Boolean {
        return bluetoothAdapter?.isEnabled ?: false
    }

    fun startBleScan() {
        val scanner = bluetoothAdapter?.bluetoothLeScanner ?: return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ActivityCompat.checkSelfPermission(
                    context,
                    Manifest.permission.BLUETOOTH_SCAN
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return
            }
        }

        Log.d(TAG, "Starting BLE scan")
        val scanSettings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        scanner.startScan(null, scanSettings, scanCallback)

        // Stop scan after 10 seconds to conserve battery
        Handler(Looper.getMainLooper()).postDelayed({
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (ActivityCompat.checkSelfPermission(
                        context,
                        Manifest.permission.BLUETOOTH_SCAN
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
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
                    if (ActivityCompat.checkSelfPermission(
                            context,
                            Manifest.permission.BLUETOOTH_SCAN
                        ) != PackageManager.PERMISSION_GRANTED
                    ) {
                        return
                    }
                }
                bluetoothAdapter?.bluetoothLeScanner?.stopScan(this)
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
            if (ActivityCompat.checkSelfPermission(
                    context,
                    Manifest.permission.BLUETOOTH_CONNECT
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return
            }
        }

        bluetoothGatt = device.connectGatt(context, false, gattCallback)
    }

    private val gattCallback = object : BluetoothGattCallback() {

        override fun onReadRemoteRssi(gatt: BluetoothGatt?, rssi: Int, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                lastRssiValue = rssi
                Log.d(TAG, "RSSI: $rssi dBm")

                // Update UI with new RSSI value
                mainHandler.post {
                    onConnectionStatusChanged?.invoke(true, lastRssiValue)
                }
            } else {
                Log.e(TAG, "Failed to read RSSI, status: $status")
            }
        }

        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.d(TAG, "Connected to GATT server")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (ActivityCompat.checkSelfPermission(
                            context,
                            Manifest.permission.BLUETOOTH_CONNECT
                        ) != PackageManager.PERMISSION_GRANTED
                    ) {
                        return
                    }
                }
                mainHandler.post {
                    onConnectionStatusChanged?.invoke(true, lastRssiValue)
                }
                gatt.discoverServices()
                // Start RSSI updates after connection established
                startRssiUpdates()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.d(TAG, "Disconnected from GATT server")
                // Ensure RSSI updates are stopped when disconnected
                stopRssiUpdates()
                mainHandler.post {
                    onConnectionStatusChanged?.invoke(false, -100)
                    isMonitoring = false
                    isPaused = false
                    onMonitoringStatusChanged?.invoke(false)
                    onPauseStatusChanged?.invoke(false)
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
                if (ActivityCompat.checkSelfPermission(
                        context,
                        Manifest.permission.BLUETOOTH_CONNECT
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    return
                }
            }
            // Only process data if monitoring is active
            if (isMonitoring) {
                val value = characteristic.value
                handleReceivedData(value)
            }
        }
    }

    private fun scanForCharacteristics(gatt: BluetoothGatt) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ActivityCompat.checkSelfPermission(
                    context,
                    Manifest.permission.BLUETOOTH_CONNECT
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return
            }
        }

        for (service in gatt.services) {
            Log.d(TAG, "Found service: ${service.uuid}")
            for (characteristic in service.characteristics) {
                Log.d(TAG, "  Characteristic: ${characteristic.uuid}")
                val properties = characteristic.properties
                if ((properties and BluetoothGattCharacteristic.PROPERTY_READ) != 0 ||
                    (properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0
                ) {
                    enableNotifications(gatt, characteristic)
                }
            }
        }
    }

    private fun enableNotifications(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ActivityCompat.checkSelfPermission(
                    context,
                    Manifest.permission.BLUETOOTH_CONNECT
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return
            }
        }

        // Enable local notifications
        gatt.setCharacteristicNotification(characteristic, true)

        // Write to descriptor to enable remote notifications
        val descriptor = characteristic.getDescriptor(CLIENT_CHARACTERISTIC_CONFIG)
        if (descriptor != null) {
            descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            gatt.writeDescriptor(descriptor)
            Log.d(TAG, "Notification descriptor set for: ${characteristic.uuid}")
        } else {
            Log.e(TAG, "Could not find CCC descriptor for characteristic: ${characteristic.uuid}")
        }
    }

    private fun handleReceivedData(data: ByteArray) {
        try {
            // Ensure that there are at least 12 bytes in the array (4 bytes for each temperature and battery level)
            if (data.size >= 12) {
                // First 4 bytes for the first temperature (assuming it's a Float)
                val temperatureSkin = ByteBuffer.wrap(data, 0, 4)
                    .order(ByteOrder.LITTLE_ENDIAN)
                    .float.toDouble()

                // Next 4 bytes for the second temperature (assuming it's a Float)
                val temperatureOutside = ByteBuffer.wrap(data, 4, 4)
                    .order(ByteOrder.LITTLE_ENDIAN)
                    .float.toDouble()

                val batteryLevel = ByteBuffer.wrap(data, 8, 4)
                    .order(ByteOrder.LITTLE_ENDIAN)
                    .float.toDouble()

                // Round the values to two decimal places
                val roundedTemperatureSkin = String.format("%.2f", temperatureSkin).toDouble()
                val roundedTemperatureOutside = String.format("%.2f", temperatureOutside).toDouble()
                val roundedBatteryLevel = String.format("%.2f", batteryLevel).toDouble()

                // Calculate core temperature using thermal principles
                // Using the 2-point measurement heat transfer equation with polyurethane foam insulation
                val temperatureCore = calculateCoreTemperature(roundedTemperatureSkin, roundedTemperatureOutside)
                val roundedTemperatureCore = String.format("%.2f", temperatureCore).toDouble()

                mainHandler.post {
                    // Always send battery level when connected
                    onDataReceived?.invoke(
                        if (isMonitoring && !isPaused) roundedTemperatureSkin else 0.0,
                        if (isMonitoring && !isPaused) roundedTemperatureOutside else 0.0,
                        if (isMonitoring && !isPaused) roundedTemperatureCore else 0.0,
                        roundedBatteryLevel  // Always send battery level
                    )
                }
            } else {
                Log.e(TAG, "Data size is insufficient. Expected at least 12 bytes.")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing data: ${e.message}")
        }
    }

    /**
     * Calculate core body temperature using thermal equilibrium principles.
     * This uses the two-point gradient method considering the insulation properties
     * of polyurethane foam (thermal conductivity ~0.023 W/(m*K))
     */
    private fun calculateCoreTemperature(skinTemp: Double, outsideTemp: Double): Double {
        // Constants for human body thermal properties
        val kFoam = 0.023  // Thermal conductivity of polyurethane foam in W/(m*K)
        val kTissue = 0.5  // Approximate thermal conductivity of human tissue in W/(m*K)

        // Estimated distance parameters (in meters)
        val foamThickness = 0.01  // 1 cm insulation
        val estimatedDepth = 0.03  // 3 cm deep to core

        // Heat flux calculation assuming steady state
        val heatFlux = kFoam * (skinTemp - outsideTemp) / foamThickness

        // Temperature gradient from skin to core
        val tempGradient = heatFlux * estimatedDepth / kTissue

        // Core temperature estimate
        return skinTemp + tempGradient
    }

    fun toggleMonitoring() {
        isMonitoring = !isMonitoring

        // If monitoring is being stopped, also reset the pause state
        if (!isMonitoring) {
            isPaused = false
            onPauseStatusChanged?.invoke(false)
        }

        onMonitoringStatusChanged?.invoke(isMonitoring)
    }

    fun togglePause() {
        // Only allow pause/resume if monitoring is active
        if (isMonitoring) {
            isPaused = !isPaused
            onPauseStatusChanged?.invoke(isPaused)
        }
    }

    fun getMonitoringStatus(): Boolean {
        return isMonitoring
    }

    fun closeConnection() {
        stopRssiUpdates()
        isMonitoring = false
        isPaused = false

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ActivityCompat.checkSelfPermission(
                    context,
                    Manifest.permission.BLUETOOTH_CONNECT
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return
            }
        }
        bluetoothGatt?.close()
        bluetoothGatt = null
    }
}