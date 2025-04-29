package com.example.innertemp

import android.Manifest
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.app.ActivityCompat
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.*

private const val TAG = "InnerTempBluetooth"
private val SERVICE_UUID = UUID.fromString("4fafc201-1fb5-459e-8fcc-c5c9c331914b")
private val CHARACTERISTIC_UUID = UUID.fromString("beb5483e-36e1-4688-b7f5-ea07361b26a8")

class BluetoothManager(private val context: Context) {
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var bluetoothGatt: BluetoothGatt? = null
    private var isMonitoring = false
    private var isPaused = false

    // Callbacks for status updates
    private var onConnectionStatusChanged: ((Boolean) -> Unit)? = null
    private var onMonitoringStatusChanged: ((Boolean) -> Unit)? = null
    private var onPauseStatusChanged: ((Boolean) -> Unit)? = null
    private var onDataReceived: ((Double, Double, Double, Double) -> Unit)? = null

    init {
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as android.bluetooth.BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter
    }

    fun setOnConnectionStatusChanged(callback: (Boolean) -> Unit) {
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
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) !=
                PackageManager.PERMISSION_GRANTED) {
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
                if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) !=
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
                    if (ActivityCompat.checkSelfPermission(context,
                            Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
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
            if (ActivityCompat.checkSelfPermission(context,
                    Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                return
            }
        }

        bluetoothGatt = device.connectGatt(context, false, gattCallback)
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.d(TAG, "Connected to GATT server")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (ActivityCompat.checkSelfPermission(context,
                            Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                        return
                    }
                }
                onConnectionStatusChanged?.invoke(true)
                gatt.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.d(TAG, "Disconnected from GATT server")
                onConnectionStatusChanged?.invoke(false)
                isMonitoring = false
                isPaused = false
                onMonitoringStatusChanged?.invoke(false)
                onPauseStatusChanged?.invoke(false)
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
            if (isMonitoring) {
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
                if (ActivityCompat.checkSelfPermission(context,
                        Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
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
            if (ActivityCompat.checkSelfPermission(context,
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
            if (ActivityCompat.checkSelfPermission(context,
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
                if (isMonitoring && !isPaused) {
                    onDataReceived?.invoke(
                        roundedTemperatureSkin,
                        roundedTemperatureOutside,
                        roundedTemperatureCore,
                        roundedBatteryLevel
                    )
                }
            } else {
                Log.e(TAG, "Data size is insufficient. Expected at least 12 bytes.")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing data: ${e.message}")
        }
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

    fun getPauseStatus(): Boolean {
        return isPaused
    }

    fun closeConnection() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ActivityCompat.checkSelfPermission(context,
                    Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                return
            }
        }
        bluetoothGatt?.close()
        bluetoothGatt = null
    }
}