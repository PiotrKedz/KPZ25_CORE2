package com.example.innertemp

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileWriter
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

/**
 * Utility class for logging temperature data to files organized by date and session
 */
class TemperatureLogger(private val context: Context) {

    private val TAG = "TemperatureLogger"

    // IMPORTANT: Set Locale.US to ensure consistent date formatting across devices
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.US)
    private val timestampFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)

    // Current session ID - will be updated each time a new session starts
    private var currentSessionId = generateSessionId()

    /**
     * Generates a unique session ID based on current timestamp
     */
    private fun generateSessionId(): String {
        return timestampFormat.format(Date())
    }

    /**
     * Starts a new logging session with a fresh file
     * Call this method when starting a new recording session
     */
    fun startNewSession() {
        currentSessionId = generateSessionId()
        Log.d(TAG, "Started new temperature logging session: $currentSessionId")
    }

    /**
     * Logs a temperature reading with the current timestamp to the current session file
     * @param coreTemp The core temperature reading
     */
    fun logTemperature(coreTemp: Double) {
        val currentDate = Date()
        val dateString = dateFormat.format(currentDate)
        val timeString = timeFormat.format(currentDate)

        // Add detailed debug logging
        Log.d(TAG, "Logging temperature: $coreTemp°C at $timeString on $dateString (Session: $currentSessionId)")

        val entry = "$timeString,$coreTemp\n"

        try {
            // Get directory for app's files
            val directory = context.filesDir
            val sessionFile = File(directory, "temp_${dateString}_session_${currentSessionId}.csv")

            Log.d(TAG, "File path: ${sessionFile.absolutePath}")

            // Create file if it doesn't exist
            if (!sessionFile.exists()) {
                val fileCreated = sessionFile.createNewFile()
                Log.d(TAG, "Created new file: $fileCreated")

                // Add CSV header
                FileWriter(sessionFile, true).use { writer ->
                    writer.append("time,temperature\n")
                    writer.flush() // Force write to disk
                }
            }

            // Append new temperature data with explicit flush
            FileWriter(sessionFile, true).use { writer ->
                writer.append(entry)
                writer.flush() // Force write to disk
            }

            Log.d(TAG, "Successfully logged temperature: $coreTemp°C")
        } catch (e: IOException) {
            Log.e(TAG, "Error writing temperature data: ${e.message}", e)
        }
    }

    /**
     * Returns all temperature readings for a specific date and session
     * @param dateString The date in "yyyy-MM-dd" format
     * @param sessionId The session identifier
     * @return List of temperature entries or null if no data
     */
    fun getTemperatureDataForSession(dateString: String, sessionId: String): List<TemperatureEntry>? {
        val file = File(context.filesDir, "temp_${dateString}_session_${sessionId}.csv")

        Log.d(TAG, "Checking for temperature data file: ${file.absolutePath}")
        Log.d(TAG, "File exists: ${file.exists()}, readable: ${file.canRead()}, size: ${if (file.exists()) file.length() else 0} bytes")

        if (!file.exists() || !file.canRead()) {
            Log.d(TAG, "No temperature data for session $sessionId on $dateString")
            return null
        }

        val entries = mutableListOf<TemperatureEntry>()

        try {
            file.bufferedReader().useLines { lines ->
                // Skip header line
                var isFirstLine = true
                var lineCount = 0

                lines.forEach { line ->
                    lineCount++
                    if (isFirstLine) {
                        isFirstLine = false
                    } else if (line.isNotEmpty()) {
                        try {
                            val parts = line.split(",")
                            if (parts.size >= 2) {
                                val time = parts[0].trim()
                                val temp = parts[1].trim().toDoubleOrNull()

                                if (temp != null) {
                                    entries.add(TemperatureEntry(time, temp))
                                } else {
                                    Log.w(TAG, "Invalid temperature value at line $lineCount: ${parts[1]}")
                                }
                            } else {
                                Log.w(TAG, "Invalid line format at line $lineCount: $line")
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error parsing line $lineCount: $line", e)
                        }
                    }
                }
            }

            Log.d(TAG, "Retrieved ${entries.size} temperature entries for session $sessionId on $dateString")
            return entries

        } catch (e: IOException) {
            Log.e(TAG, "Error reading temperature data: ${e.message}", e)
            return null
        }
    }

    /**
     * Returns all temperature readings for a specific date
     * @param year The year
     * @param month The month (1-12)
     * @param day The day of month
     * @return List of temperature entries or null if no data
     */
    fun getTemperatureDataForDate(year: Int, month: Int, day: Int): List<TemperatureEntry>? {
        val calendar = Calendar.getInstance()
        calendar.set(year, month - 1, day)  // Month is 0-indexed in Calendar
        val dateString = dateFormat.format(calendar.time)

        // Combine data from all sessions for this date
        val allEntries = mutableListOf<TemperatureEntry>()

        // Get all files for this date
        val directory = context.filesDir
        val filesForDate = directory.listFiles { file ->
            file.name.startsWith("temp_${dateString}") && file.name.endsWith(".csv")
        } ?: return null

        Log.d(TAG, "Found ${filesForDate.size} session files for date $dateString")

        // Process each file
        filesForDate.forEach { file ->
            try {
                var isFirstLine = true
                var lineCount = 0

                file.bufferedReader().useLines { lines ->
                    lines.forEach { line ->
                        lineCount++
                        if (isFirstLine) {
                            isFirstLine = false
                        } else if (line.isNotEmpty()) {
                            try {
                                val parts = line.split(",")
                                if (parts.size >= 2) {
                                    val time = parts[0].trim()
                                    val temp = parts[1].trim().toDoubleOrNull()

                                    if (temp != null) {
                                        allEntries.add(TemperatureEntry(time, temp))
                                    } else {
                                        Log.w(TAG, "Invalid temperature value at line $lineCount: ${parts[1]}")
                                    }
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "Error parsing line $lineCount in file ${file.name}: $line", e)
                            }
                        }
                    }
                }
            } catch (e: IOException) {
                Log.e(TAG, "Error reading file ${file.name}: ${e.message}", e)
            }
        }

        Log.d(TAG, "Retrieved ${allEntries.size} total temperature entries for $dateString")
        return if (allEntries.isEmpty()) null else allEntries
    }

    /**
     * Calculates the min, max, and average temperature for a day across all sessions
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

        // Format month string with leading zero if needed
        val monthStr = String.format("%02d", month)
        val prefix = "temp_$year-$monthStr-"

        Log.d(TAG, "Looking for files with prefix: $prefix in ${directory.absolutePath}")

        // List files and count for debugging
        val allFiles = directory.listFiles() ?: emptyArray()
        Log.d(TAG, "Total files in directory: ${allFiles.size}")

        val matchingFiles = allFiles.filter { it.name.startsWith(prefix) && it.name.endsWith(".csv") }
        Log.d(TAG, "Matching files found: ${matchingFiles.size}")

        // Process matching files
        matchingFiles.forEach { file ->
            try {
                // Extract the day from filename (should be after "yyyy-MM-" prefix)
                val dayStr = file.name.substring(prefix.length, prefix.length + 2)
                val day = dayStr.toInt()
                days.add(day)
                Log.d(TAG, "Found data for day: $day (file: ${file.name})")
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing day from filename: ${file.name}", e)
            }
        }

        Log.d(TAG, "Found data for ${days.size} days in $year-$monthStr")
        return days
    }

    /**
     * Gets a list of all sessions for a specific date
     * @param year The year
     * @param month The month (1-12)
     * @param day The day of month
     * @return List of session IDs or empty list if no data
     */
    fun getSessionsForDate(year: Int, month: Int, day: Int): List<String> {
        val calendar = Calendar.getInstance()
        calendar.set(year, month - 1, day)  // Month is 0-indexed in Calendar
        val dateString = dateFormat.format(calendar.time)

        val sessions = mutableListOf<String>()
        val directory = context.filesDir

        // Pattern: temp_YYYY-MM-DD_session_SESSIONID.csv
        val prefix = "temp_${dateString}_session_"
        val suffix = ".csv"

        val filesForDate = directory.listFiles { file ->
            file.name.startsWith(prefix) && file.name.endsWith(suffix)
        } ?: return emptyList()

        filesForDate.forEach { file ->
            try {
                val sessionId = file.name.substring(prefix.length, file.name.length - suffix.length)
                sessions.add(sessionId)
            } catch (e: Exception) {
                Log.e(TAG, "Error extracting session ID from filename: ${file.name}", e)
            }
        }

        Log.d(TAG, "Found ${sessions.size} sessions for date $dateString")
        return sessions
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