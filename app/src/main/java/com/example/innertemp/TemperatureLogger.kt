package com.example.innertemp

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileWriter
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

/**
 * Utility class for logging temperature data to files organized by date
 */
class TemperatureLogger(private val context: Context) {

    private val TAG = "TemperatureLogger"
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    /**
     * Logs a temperature reading with the current timestamp
     * @param coreTemp The core temperature reading
     */
    fun logTemperature(coreTemp: Double) {
        val currentDate = Date()
        val dateString = dateFormat.format(currentDate)
        val timeString = timeFormat.format(currentDate)

        val entry = "$timeString,$coreTemp\n"

        try {
            // Get directory for app's files
            val directory = context.filesDir
            val dateFile = File(directory, "temp_$dateString.csv")

            // Create file if it doesn't exist
            if (!dateFile.exists()) {
                dateFile.createNewFile()
                // Add CSV header
                FileWriter(dateFile, true).use { writer ->
                    writer.append("time,temperature\n")
                }
            }

            // Append new temperature data
            FileWriter(dateFile, true).use { writer ->
                writer.append(entry)
            }

            Log.d(TAG, "Logged temperature: $coreTemp°C at $timeString")
        } catch (e: IOException) {
            Log.e(TAG, "Error writing temperature data: ${e.message}")
        }
    }

    /**
     * Returns all temperature readings for a specific date
     * @param year The year
     * @param month The month (1-12)
     * @param day The day of month
     * @return Map of time to temperature readings or null if no data
     */
    fun getTemperatureDataForDate(year: Int, month: Int, day: Int): List<TemperatureEntry>? {
        val calendar = Calendar.getInstance()
        calendar.set(year, month - 1, day)  // Month is 0-indexed in Calendar
        val dateString = dateFormat.format(calendar.time)

        val file = File(context.filesDir, "temp_$dateString.csv")

        if (!file.exists()) {
            Log.d(TAG, "No temperature data for $dateString")
            return null
        }

        val entries = mutableListOf<TemperatureEntry>()

        try {
            file.bufferedReader().useLines { lines ->
                // Skip header line
                var isFirstLine = true

                lines.forEach { line ->
                    if (isFirstLine) {
                        isFirstLine = false
                    } else if (line.isNotEmpty()) {
                        val parts = line.split(",")
                        if (parts.size >= 2) {
                            val time = parts[0]
                            val temp = parts[1].toDoubleOrNull() ?: return@forEach
                            entries.add(TemperatureEntry(time, temp))
                        }
                    }
                }
            }

            Log.d(TAG, "Retrieved ${entries.size} temperature entries for $dateString")
            return entries

        } catch (e: IOException) {
            Log.e(TAG, "Error reading temperature data: ${e.message}")
            return null
        }
    }

    /**
     * Calculates the min, max, and average temperature for a day
     */
    fun getTemperatureStatsForDate(year: Int, month: Int, day: Int): TemperatureStats? {
        val entries = getTemperatureDataForDate(year, month, day) ?: return null

        if (entries.isEmpty()) return null

        var min = Double.MAX_VALUE
        var max = Double.MIN_VALUE
        var sum = 0.0

        entries.forEach { entry ->
            if (entry.temperature < min) min = entry.temperature
            if (entry.temperature > max) max = entry.temperature
            sum += entry.temperature
        }

        val avg = sum / entries.size

        return TemperatureStats(
            lowest = min,
            highest = max,
            average = avg,
            count = entries.size
        )
    }

    /**
     * Gets a list of all days that have temperature data in the given month
     */
    fun getDaysWithDataInMonth(year: Int, month: Int): Set<Int> {
        val days = mutableSetOf<Int>()
        val directory = context.filesDir

        // Format like "temp_2025-04-01.csv"
        val monthPrefix = String.format("temp_%04d-%02d-", year, month)

        directory.listFiles()?.forEach { file ->
            val fileName = file.name
            if (fileName.startsWith(monthPrefix) && fileName.endsWith(".csv")) {
                try {
                    // Extract the day from filename (temp_2025-04-XX.csv)
                    val day = fileName.substringAfter(monthPrefix).substringBefore(".").toInt()
                    days.add(day)
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing day from filename: $fileName")
                }
            }
        }

        return days
    }
}

/**
 * Represents a single temperature reading with time
 */
data class TemperatureEntry(
    val time: String,
    val temperature: Double
)

/**
 * Represents temperature statistics for a day
 */
data class TemperatureStats(
    val lowest: Double,
    val highest: Double,
    val average: Double,
    val count: Int
)