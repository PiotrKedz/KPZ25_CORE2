package com.example.innertemp

import android.util.Log
import androidx.compose.runtime.mutableStateOf

/**
 * Handles temperature calculations and tracking for the InnerTemp application.
 * Centralizes temperature-related functionality outside of the UI layer.
 */
class TemperatureCalc {
    companion object {
        private const val TAG = "TemperatureCalc"
    }

    // Core temperature average tracking
    val averageTemperatureCore = mutableStateOf(0.0)
    private var tempCoreSamples = mutableListOf<Double>()
    private var totalSamples = 0

    /**
     * Updates the running average of core temperature
     * Uses a cumulative moving average approach
     */
    fun updateAverageTemperature(newValue: Double) {
        if (newValue <= 0) return  // Skip invalid readings

        tempCoreSamples.add(newValue)
        totalSamples++

        // Calculate running average
        val sum = tempCoreSamples.sum()
        averageTemperatureCore.value = String.format("%.1f", sum / totalSamples).toDouble()

        // Optionally, limit the number of samples stored to prevent excessive memory usage
        if (tempCoreSamples.size > 100) {  // Keep only the most recent 100 samples
            tempCoreSamples.removeAt(0)
        }

        Log.d(TAG, "Average core temp: ${averageTemperatureCore.value}°C (from $totalSamples samples)")
    }

    /**
     * Resets the temperature average calculation
     */
    fun resetAverageTemperature() {
        tempCoreSamples.clear()
        totalSamples = 0
        averageTemperatureCore.value = 0.0
        Log.d(TAG, "Average temperature tracking reset")
    }

    /**
     * Determines the appropriate color for a temperature value
     * @return Color identifier based on temperature range
     */
    fun getTemperatureColor(temperature: Double): TemperatureColor {
        return when {
            temperature < 36.0 -> TemperatureColor.COOL
            temperature > 38.0 -> TemperatureColor.HOT
            else -> TemperatureColor.NORMAL
        }
    }
}

/**
 * Enum to represent temperature color ranges
 */
enum class TemperatureColor {
    COOL,
    NORMAL,
    HOT
}