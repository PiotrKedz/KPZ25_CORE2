package com.example.wearos.data

import android.content.Context
import android.util.Log
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

private const val TAG = "WearableDataReceiver"

/**
 * Service to listen for data from the mobile device
 */
class WearableDataReceiver(context: Context) : DataClient.OnDataChangedListener {

    // Initialize the DataClient
    private val dataClient = Wearable.getDataClient(context)

    // Data path constants
    companion object {
        const val DATA_PATH = "/innertemp/data"

        // Data keys
        const val KEY_TEMP_CORE = "temp_core"
        const val KEY_TEMP_SKIN = "temp_skin"
        const val KEY_TEMP_OUTSIDE = "temp_outside"
        const val KEY_AVG_TEMP_CORE = "avg_temp_core"
        const val KEY_BATTERY_LEVEL = "battery_level"
        const val KEY_IS_CONNECTED = "is_connected"
        const val KEY_IS_MONITORING = "is_monitoring"
        const val KEY_IS_PAUSED = "is_paused"
    }

    // State holders for all the data we receive
    private val _deviceState = MutableStateFlow(DeviceState())
    val deviceState: StateFlow<DeviceState> = _deviceState.asStateFlow()

    data class DeviceState(
        val tempCore: Double = 0.0,
        val tempSkin: Double = 0.0,
        val tempOutside: Double = 0.0,
        val avgTempCore: Double = 0.0,
        val batteryLevel: Double = 0.0,
        val isConnected: Boolean = false,
        val isMonitoring: Boolean = false,
        val isPaused: Boolean = false,
        val lastUpdated: Long = 0L
    )

    /**
     * Start listening for data events from the mobile app
     */
    fun startListening() {
        dataClient.addListener(this)
        Log.d(TAG, "Started listening for data events")
    }

    /**
     * Stop listening for data events
     */
    fun stopListening() {
        dataClient.removeListener(this)
        Log.d(TAG, "Stopped listening for data events")
    }

    /**
     * Called when data changes on the Data Layer
     */
    override fun onDataChanged(dataEvents: DataEventBuffer) {
        dataEvents.forEach { event ->
            if (event.type == DataEvent.TYPE_CHANGED) {
                val uri = event.dataItem.uri

                // Check if this is the data path we're interested in
                if (uri.path == DATA_PATH) {
                    val dataMapItem = DataMapItem.fromDataItem(event.dataItem)
                    val dataMap = dataMapItem.dataMap

                    // Extract all the data values
                    val tempCore = dataMap.getDouble(KEY_TEMP_CORE, 0.0)
                    val tempSkin = dataMap.getDouble(KEY_TEMP_SKIN, 0.0)
                    val tempOutside = dataMap.getDouble(KEY_TEMP_OUTSIDE, 0.0)
                    val avgTempCore = dataMap.getDouble(KEY_AVG_TEMP_CORE, 0.0)
                    val batteryLevel = dataMap.getDouble(KEY_BATTERY_LEVEL, 0.0)
                    val isConnected = dataMap.getBoolean(KEY_IS_CONNECTED, false)
                    val isMonitoring = dataMap.getBoolean(KEY_IS_MONITORING, false)
                    val isPaused = dataMap.getBoolean(KEY_IS_PAUSED, false)
                    val timestamp = dataMap.getLong("timestamp", System.currentTimeMillis())

                    // Update our state flow with the new values
                    _deviceState.update { currentState ->
                        currentState.copy(
                            tempCore = tempCore,
                            tempSkin = tempSkin,
                            tempOutside = tempOutside,
                            avgTempCore = avgTempCore,
                            batteryLevel = batteryLevel,
                            isConnected = isConnected,
                            isMonitoring = isMonitoring,
                            isPaused = isPaused,
                            lastUpdated = timestamp
                        )
                    }

                    Log.d(TAG, "Received data update: tempCore=$tempCore, isConnected=$isConnected, batteryLevel=$batteryLevel")
                }
            }
        }
    }
}