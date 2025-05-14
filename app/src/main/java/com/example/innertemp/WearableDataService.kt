package com.example.innertemp

import android.content.Context
import android.util.Log
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.Node
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TAG = "WearableDataService"

/**
 * Service for handling communication with the WearOS device.
 */
class WearableDataService(private val context: Context) {
    private val messageClient = Wearable.getMessageClient(context)
    private val nodeClient = Wearable.getNodeClient(context)
    private val dataClient = Wearable.getDataClient(context)
    private val scope = CoroutineScope(Dispatchers.IO)

    companion object {
        // Data paths
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

    /**
     * Send the sensor data and device status to connected wearable devices.
     */
    fun sendDataToWearable(
        tempCore: Double,
        tempSkin: Double,
        tempOutside: Double,
        avgTempCore: Double,
        batteryLevel: Double,
        isConnected: Boolean,
        isMonitoring: Boolean,
        isPaused: Boolean
    ) {
        scope.launch {
            try {
                // Create a data map with all our values
                val dataMap = PutDataMapRequest.create(DATA_PATH).apply {
                    dataMap.putDouble(KEY_TEMP_CORE, tempCore)
                    dataMap.putDouble(KEY_TEMP_SKIN, tempSkin)
                    dataMap.putDouble(KEY_TEMP_OUTSIDE, tempOutside)
                    dataMap.putDouble(KEY_AVG_TEMP_CORE, avgTempCore)
                    dataMap.putDouble(KEY_BATTERY_LEVEL, batteryLevel)
                    dataMap.putBoolean(KEY_IS_CONNECTED, isConnected)
                    dataMap.putBoolean(KEY_IS_MONITORING, isMonitoring)
                    dataMap.putBoolean(KEY_IS_PAUSED, isPaused)
                    // Add a timestamp to ensure the DataItem changes
                    dataMap.putLong("timestamp", System.currentTimeMillis())
                }

                // Send the data to all connected nodes
                val request = dataMap.asPutDataRequest()
                request.setUrgent()

                val result = Tasks.await(dataClient.putDataItem(request))
                Log.d(TAG, "Data sent to wearable: ${result.uri}")
            } catch (e: Exception) {
                Log.e(TAG, "Error sending data to wearable", e)
            }
        }
    }

    /**
     * Get a list of connected nodes (wearable devices)
     */
    suspend fun getConnectedNodes(): List<Node> = withContext(Dispatchers.IO) {
        try {
            val nodes = Tasks.await(nodeClient.connectedNodes)
            Log.d(TAG, "Connected nodes: ${nodes.size}")
            return@withContext nodes
        } catch (e: Exception) {
            Log.e(TAG, "Error getting connected nodes", e)
            return@withContext emptyList()
        }
    }
}