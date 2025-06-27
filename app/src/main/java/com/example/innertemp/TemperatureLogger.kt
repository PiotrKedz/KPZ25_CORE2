package com.example.innertemp

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileWriter
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*


class TemperatureLogger(private val context: Context) {

    private val TAG = "TemperatureLogger"

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.US)
    private val timestampFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)

    private var currentSessionId = generateSessionId()

    private var currentSport = Sport.RUNNING

    private fun generateSessionId(): String {
        return timestampFormat.format(Date())
    }

    fun startNewSession(sport: Sport = Sport.RUNNING) {
        currentSessionId = generateSessionId()
        currentSport = sport
        Log.d(TAG, "Started new temperature logging session: $currentSessionId for sport: ${sport.name}")
    }

    fun logTemperature(coreTemp: Double) {
        val currentDate = Date()
        val dateString = dateFormat.format(currentDate)
        val timeString = timeFormat.format(currentDate)

        // Add detailed debug logging
        Log.d(TAG, "Logging temperature: $coreTemp°C at $timeString on $dateString (Session: $currentSessionId, Sport: ${currentSport.name})")

        val entry = "$timeString,$coreTemp,${currentSport.name}\n"

        try {
            // Get directory for app's files
            val directory = context.filesDir
            val sessionFile = File(directory, "temp_${dateString}_${currentSport.name.lowercase()}_session_${currentSessionId}.csv")
            if (!sessionFile.exists()) {
                val fileCreated = sessionFile.createNewFile()

                FileWriter(sessionFile, true).use { writer ->
                    writer.append("time,temperature,sport\n")
                    writer.flush()
                }
            }

            FileWriter(sessionFile, true).use { writer ->
                writer.append(entry)
                writer.flush()
            }

        } catch (e: IOException) {
        }
    }

    fun getTemperatureDataForSession(dateString: String, sessionId: String, sport: Sport): List<TemperatureEntry>? {
        val file = File(context.filesDir, "temp_${dateString}_${sport.name.lowercase()}_session_${sessionId}.csv")

        if (!file.exists() || !file.canRead()) {
            return null
        }

        val entries = mutableListOf<TemperatureEntry>()

        try {
            file.bufferedReader().useLines { lines ->
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
                                val sportName = if (parts.size >= 3) parts[2].trim() else sport.name

                                if (temp != null) {
                                    entries.add(TemperatureEntry(time, temp, Sport.valueOf(sportName)))
                                } else {
                                }
                            } else {
                            }
                        } catch (e: Exception) {
                        }
                    }
                }
            }
            return entries
        } catch (e: IOException) {
            return null
        }
    }

    fun getTemperatureDataForDate(year: Int, month: Int, day: Int, sport: Sport? = null): List<TemperatureEntry>? {
        val calendar = Calendar.getInstance()
        calendar.set(year, month - 1, day)
        val dateString = dateFormat.format(calendar.time)

        val allEntries = mutableListOf<TemperatureEntry>()

        val directory = context.filesDir
        val filesForDate = directory.listFiles { file ->
            val matchesDate = file.name.startsWith("temp_${dateString}")
            val matchesSport = sport == null || file.name.contains("_${sport.name.lowercase()}_")
            matchesDate && matchesSport && file.name.endsWith(".csv")
        } ?: return null

        Log.d(TAG, "Found ${filesForDate.size} session files for date $dateString" +
                (sport?.let { " and sport ${it.name}" } ?: ""))

        filesForDate.forEach { file ->
            try {
                var isFirstLine = true
                var lineCount = 0

                val filenamePattern = "temp_.*?_(\\w+)_session_.*?\\.csv".toRegex()
                val matchResult = filenamePattern.find(file.name)
                val fileSport = matchResult?.groupValues?.get(1)?.uppercase()?.let {
                    try {
                        Sport.valueOf(it)
                    } catch (e: Exception) {
                        null
                    }
                }

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
                                    val entrySport = if (parts.size >= 3) {
                                        try {
                                            Sport.valueOf(parts[2].trim())
                                        } catch (e: Exception) {
                                            fileSport ?: Sport.RUNNING
                                        }
                                    } else {
                                        fileSport ?: Sport.RUNNING
                                    }


                                    if (temp != null) {
                                        allEntries.add(TemperatureEntry(time, temp, entrySport))
                                    } else {
                                    }
                                }
                            } catch (e: Exception) {
                            }
                        }
                    }
                }
            } catch (e: IOException) {
            }
        }

        return if (allEntries.isEmpty()) null else allEntries
    }

    fun getTemperatureStatsForDate(year: Int, month: Int, day: Int, sport: Sport? = null): TemperatureStats? {
        val entries = getTemperatureDataForDate(year, month, day, sport) ?: return null

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
            count = entries.size,
            sport = sport
        )
    }

    fun getDaysWithDataInMonth(year: Int, month: Int, sport: Sport? = null): Set<Int> {
        val days = mutableSetOf<Int>()
        val directory = context.filesDir

        val monthStr = String.format("%02d", month)
        val prefix = "temp_$year-$monthStr-"

        val allFiles = directory.listFiles() ?: emptyArray()

        val matchingFiles = allFiles.filter {
            val matchesPrefix = it.name.startsWith(prefix) && it.name.endsWith(".csv")
            val matchesSport = sport == null || it.name.contains("_${sport.name.lowercase()}_")
            matchesPrefix && matchesSport
        }
        Log.d(TAG, "Matching files found: ${matchingFiles.size}")

        matchingFiles.forEach { file ->
            try {
                val dayStr = file.name.substring(prefix.length, prefix.length + 2)
                val day = dayStr.toInt()
                days.add(day)
                Log.d(TAG, "Found data for day: $day (file: ${file.name})")
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing day from filename: ${file.name}", e)
            }
        }

        Log.d(TAG, "Found data for ${days.size} days in $year-$monthStr" +
                (sport?.let { " for sport ${it.name}" } ?: ""))
        return days
    }

    fun getSessionsForDate(year: Int, month: Int, day: Int, sport: Sport? = null): List<SessionInfo> {
        val calendar = Calendar.getInstance()
        calendar.set(year, month - 1, day)  // Month is 0-indexed in Calendar
        val dateString = dateFormat.format(calendar.time)

        val sessions = mutableListOf<SessionInfo>()
        val directory = context.filesDir

        // Pattern: temp_YYYY-MM-DD_SPORT_session_SESSIONID.csv
        val prefix = "temp_${dateString}_"
        val suffix = ".csv"

        val filesForDate = directory.listFiles { file ->
            val matchesDate = file.name.startsWith(prefix) && file.name.endsWith(suffix)
            val matchesSport = sport == null || file.name.contains("_${sport.name.lowercase()}_")
            matchesDate && matchesSport
        } ?: return emptyList()

        filesForDate.forEach { file ->
            try {
                // Pattern: temp_YYYY-MM-DD_SPORT_session_SESSIONID.csv
                val sportPattern = "temp_.*?_(\\w+)_session_(.+?)\\.csv".toRegex()
                val match = sportPattern.find(file.name)

                if (match != null && match.groupValues.size >= 3) {
                    val sportName = match.groupValues[1].uppercase()
                    val sessionId = match.groupValues[2]

                    try {
                        val sportEnum = Sport.valueOf(sportName)
                        if (sport == null || sportEnum == sport) {
                            sessions.add(SessionInfo(sessionId, sportEnum))
                        }
                    } catch (e: IllegalArgumentException) {
                        Log.e(TAG, "Invalid sport name in filename: $sportName", e)
                    }
                } else {
                    val oldFormatPattern = "temp_.*?_session_(.+?)\\.csv".toRegex()
                    val oldMatch = oldFormatPattern.find(file.name)

                    if (oldMatch != null && oldMatch.groupValues.size >= 2) {
                        val sessionId = oldMatch.groupValues[1]
                        if (sport == null || sport == Sport.RUNNING) {  // Default to RUNNING for old format
                            sessions.add(SessionInfo(sessionId, Sport.RUNNING))
                        }
                    } else {
                        Log.e(TAG, "Couldn't extract session ID from filename: ${file.name}")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error extracting session info from filename: ${file.name}", e)
            }
        }

        Log.d(TAG, "Found ${sessions.size} sessions for date $dateString" +
                (sport?.let { " and sport ${it.name}" } ?: ""))
        return sessions
    }

    fun getSportsForDate(year: Int, month: Int, day: Int): List<Sport> {
        val calendar = Calendar.getInstance()
        calendar.set(year, month - 1, day)  // Month is 0-indexed in Calendar
        val dateString = dateFormat.format(calendar.time)

        val sports = mutableSetOf<Sport>()
        val directory = context.filesDir

        val prefix = "temp_${dateString}_"
        val suffix = ".csv"

        val filesForDate = directory.listFiles { file ->
            file.name.startsWith(prefix) && file.name.endsWith(suffix)
        } ?: return emptyList()

        filesForDate.forEach { file ->
            try {
                val sportPattern = "temp_.*?_(\\w+)_session_.*?\\.csv".toRegex()
                val match = sportPattern.find(file.name)

                if (match != null && match.groupValues.size >= 2) {
                    val sportName = match.groupValues[1].uppercase()
                    try {
                        sports.add(Sport.valueOf(sportName))
                    } catch (e: IllegalArgumentException) {
                        Log.e(TAG, "Invalid sport name in filename: $sportName", e)
                    }
                } else {
                    // For old format files, assume RUNNING
                    if (file.name.contains("_session_")) {
                        sports.add(Sport.RUNNING)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error extracting sport from filename: ${file.name}", e)
            }
        }

        return sports.toList()
    }
}

data class TemperatureEntry(
    val time: String,
    val temperature: Double,
    val sport: Sport = Sport.RUNNING
)

data class TemperatureStats(
    val lowest: Double,
    val highest: Double,
    val average: Double,
    val count: Int,
    val sport: Sport? = null
)

data class SessionInfo(
    val id: String,
    val sport: Sport
)