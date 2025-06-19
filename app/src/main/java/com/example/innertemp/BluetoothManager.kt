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

    private var onConnectionStatusChanged: ((Boolean, Int) -> Unit)? = null
    private var onMonitoringStatusChanged: ((Boolean) -> Unit)? = null
    private var onPauseStatusChanged: ((Boolean) -> Unit)? = null
    private var onDataReceived: ((Double, Double, Double, Double) -> Unit)? = null

    private var rssiUpdateInterval = 2000L
    private var lastRssiValue = -100
    private var isUpdatingRssi = false
    private var rssiUpdateRunnable: Runnable? = null

    private val mainHandler = Handler(Looper.getMainLooper())
    private var enableBtLauncher: ActivityResultLauncher<Intent>? = null
    private var permissionLauncher: ActivityResultLauncher<Array<String>>? = null

    init {
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as android.bluetooth.BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter

        if (!context.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Log.e(TAG, "BLE not supported on this device")
        }
    }

    private fun setupActivityResultLaunchers(activity: MainActivity) {
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

    fun checkPermissions() {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        } else {
            arrayOf(
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN,
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

    private fun startRssiUpdates() {
        if (isUpdatingRssi) {
            stopRssiUpdates()
        }

        isUpdatingRssi = true

        rssiUpdateRunnable = Runnable {
            updateRssi()
        }

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

        if (hasBluetoothConnectPermission()) {
            try {
                bluetoothGatt?.readRemoteRssi()
                Log.d(TAG, "Requested RSSI reading")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to read RSSI: ${e.message}")
            }
        }

        if (isUpdatingRssi) {
            rssiUpdateRunnable?.let {
                mainHandler.removeCallbacks(it)
                mainHandler.postDelayed(it, rssiUpdateInterval)
            }
        }
    }

    private fun hasBluetoothScanPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
        } else {
            ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_ADMIN) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun hasBluetoothConnectPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        } else {
            ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH) == PackageManager.PERMISSION_GRANTED
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

        if (!hasBluetoothScanPermission()) {
            Log.e(TAG, "Missing Bluetooth scan permission")
            return
        }

        Log.d(TAG, "Starting BLE scan")
        val scanSettings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        scanner.startScan(null, scanSettings, scanCallback)

        Handler(Looper.getMainLooper()).postDelayed({
            if (hasBluetoothScanPermission()) {
                scanner.stopScan(scanCallback)
                Log.d(TAG, "BLE scan stopped")
            }
        }, 10000)
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device
            Log.d(TAG, "Found device: ${device.name ?: "Unknown"} - ${device.address}")

            if (device.name?.contains("ESP") == true || device.name?.contains("GATT") == true) {
                if (hasBluetoothScanPermission()) {
                    bluetoothAdapter?.bluetoothLeScanner?.stopScan(this)
                    connectToDevice(device)
                }
            }
        }

        override fun onScanFailed(errorCode: Int) {
            Log.e(TAG, "Scan failed with error: $errorCode")
        }
    }

    private fun connectToDevice(device: BluetoothDevice) {
        Log.d(TAG, "Connecting to ${device.address}")
        if (!hasBluetoothConnectPermission()) {
            Log.e(TAG, "Missing Bluetooth connect permission")
            return
        }

        bluetoothGatt = device.connectGatt(context, false, gattCallback)
    }

    private val gattCallback = object : BluetoothGattCallback() {

        override fun onReadRemoteRssi(gatt: BluetoothGatt?, rssi: Int, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                lastRssiValue = rssi
                Log.d(TAG, "RSSI: $rssi dBm")

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
                if (!hasBluetoothConnectPermission()) {
                    Log.e(TAG, "Missing Bluetooth connect permission for service discovery")
                    return
                }
                mainHandler.post {
                    onConnectionStatusChanged?.invoke(true, lastRssiValue)
                }
                gatt.discoverServices()
                startRssiUpdates()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.d(TAG, "Disconnected from GATT server")
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
                Log.d(TAG, "=== SERVICES DISCOVERED ===")
                val service = gatt.getService(SERVICE_UUID)
                if (service != null) {
                    Log.d(TAG, "Found target service: ${service.uuid}")
                    val characteristic = service.getCharacteristic(CHARACTERISTIC_UUID)
                    if (characteristic != null) {
                        Log.d(TAG, "Found target characteristic: ${characteristic.uuid}")
                        enableNotifications(gatt, characteristic)

                        // Also try reading the characteristic once
                        Handler(Looper.getMainLooper()).postDelayed({
                            if (hasBluetoothConnectPermission()) {
                                Log.d(TAG, "Attempting to read characteristic...")
                                gatt.readCharacteristic(characteristic)
                            }
                        }, 1000)
                    } else {
                        Log.e(TAG, "Target characteristic not found")
                    }
                } else {
                    Log.e(TAG, "Target service not found")
                    scanForCharacteristics(gatt)
                }
            } else {
                Log.e(TAG, "Service discovery failed with status: $status")
            }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            Log.d(TAG, "Characteristic changed (new method)")
            handleReceivedData(value)
        }

        @Deprecated("Deprecated in Java")
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            Log.d(TAG, "Characteristic changed (deprecated method)")
            val value = characteristic.value ?: return
            handleReceivedData(value)
        }

        override fun onDescriptorWrite(gatt: BluetoothGatt?, descriptor: BluetoothGattDescriptor?, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(TAG, "Descriptor write successful for ${descriptor?.characteristic?.uuid}")
            } else {
                Log.e(TAG, "Descriptor write failed with status: $status")
            }
        }
    }

    private fun scanForCharacteristics(gatt: BluetoothGatt) {
        if (!hasBluetoothConnectPermission()) {
            return
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
                    break
                }
            }
        }
    }

    private fun enableNotifications(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
        if (!hasBluetoothConnectPermission()) {
            return
        }

        Log.d(TAG, "=== ENABLING NOTIFICATIONS ===")
        Log.d(TAG, "Characteristic: ${characteristic.uuid}")
        Log.d(TAG, "Properties: ${characteristic.properties}")

        // Enable notifications locally
        val success = gatt.setCharacteristicNotification(characteristic, true)
        Log.d(TAG, "setCharacteristicNotification result: $success")

        if (!success) {
            Log.e(TAG, "Failed to enable local notifications")
            return
        }

        // Enable notifications on the remote device
        val descriptor = characteristic.getDescriptor(CLIENT_CHARACTERISTIC_CONFIG)
        if (descriptor != null) {
            Log.d(TAG, "Found CCCD descriptor: ${descriptor.uuid}")

            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    val result = gatt.writeDescriptor(descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                    Log.d(TAG, "writeDescriptor (API 33+) result: $result")
                } else {
                    descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    val result = gatt.writeDescriptor(descriptor)
                    Log.d(TAG, "writeDescriptor (legacy) result: $result")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exception writing descriptor: ${e.message}", e)
            }
        } else {
            Log.e(TAG, "CCCD descriptor not found!")

            // List all descriptors for debugging
            for (desc in characteristic.descriptors) {
                Log.d(TAG, "Available descriptor: ${desc.uuid}")
            }
        }
    }

    private fun handleReceivedData(data: ByteArray) {
        Log.d(TAG, "=== RECEIVED DATA ===")
        Log.d(TAG, "Data size: ${data.size} bytes")
        Log.d(TAG, "Raw bytes: ${data.joinToString { "%02x".format(it) }}")

        try {
            if (data.size >= 12) {
                val temperatureSkin = ByteBuffer.wrap(data, 0, 4)
                    .order(ByteOrder.LITTLE_ENDIAN)
                    .float.toDouble()

                val temperatureOutside = ByteBuffer.wrap(data, 4, 4)
                    .order(ByteOrder.LITTLE_ENDIAN)
                    .float.toDouble()

                val batteryLevel = ByteBuffer.wrap(data, 8, 4)
                    .order(ByteOrder.LITTLE_ENDIAN)
                    .float.toDouble()

                // Calculate core temperature using the existing function
                val coreTemperature = calculateCoreTemperature(temperatureSkin, temperatureOutside)

                Log.d(TAG, "Parsed - Skin: $temperatureSkin, Outside: $temperatureOutside, Core: $coreTemperature, Battery: $batteryLevel")

                // Call the callback with calculated core temperature
                mainHandler.post {
                    onDataReceived?.invoke(temperatureSkin, temperatureOutside, coreTemperature, batteryLevel)
                }
            } else {
                Log.e(TAG, "Data size insufficient: ${data.size} bytes")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing data: ${e.message}", e)
        }
    }

    private fun calculateCoreTemperature(skinTemp: Double, outsideTemp: Double): Double {
        val kFoam = 0.023
        val kTissue = 0.5
        val foamThickness = 0.01
        val estimatedDepth = 0.03

        val heatFlux = kFoam * (skinTemp - outsideTemp) / foamThickness
        val tempGradient = heatFlux * estimatedDepth / kTissue

        return skinTemp + tempGradient
    }

    fun toggleMonitoring() {
        isMonitoring = !isMonitoring

        if (!isMonitoring) {
            isPaused = false
            onPauseStatusChanged?.invoke(false)
        }

        onMonitoringStatusChanged?.invoke(isMonitoring)
    }

    fun togglePause() {
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

        if (hasBluetoothConnectPermission()) {
            bluetoothGatt?.close()
        }
        bluetoothGatt = null
    }
}