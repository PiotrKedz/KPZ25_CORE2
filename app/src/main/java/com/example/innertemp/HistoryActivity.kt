package com.example.innertemp

import android.graphics.Color
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.innertemp.ui.theme.InnerTempTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.shape.CircleShape
import java.time.LocalDate
import java.time.Month
import androidx.compose.ui.viewinterop.AndroidView
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.formatter.ValueFormatter
import java.text.SimpleDateFormat
import java.util.*
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.border
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.clickable
import androidx.compose.ui.graphics.Color as ComposeColor
import android.content.Context
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.innertemp.ui.theme.Blue
import com.example.innertemp.ui.theme.Green
import com.example.innertemp.ui.theme.Red
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Tab


class HistoryActivity : AppCompatActivity() {
    private lateinit var temperatureLogger: TemperatureLogger
    private var useDarkTheme by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()

        temperatureLogger = TemperatureLogger(this)
        loadThemePreference()

        setContent {
            InnerTempTheme(darkTheme = useDarkTheme) {
                HistoryScreen(
                    onBack = { finish() },
                    temperatureLogger = temperatureLogger
                )
            }
        }

        lifecycle.addObserver(LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val sharedPref = getSharedPreferences("app_settings", Context.MODE_PRIVATE)
                val currentTheme = sharedPref.getString("theme", "Light") ?: "Light"
                val shouldUseDarkTheme = currentTheme == "Dark"

                if (shouldUseDarkTheme != useDarkTheme) {
                    useDarkTheme = shouldUseDarkTheme
                    recreate()
                }
            }
        })
    }

    private fun loadThemePreference() {
        val sharedPref = getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        val savedTheme = sharedPref.getString("theme", "Light") ?: "Light"
        useDarkTheme = savedTheme == "Dark"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    onBack: () -> Unit,
    temperatureLogger: TemperatureLogger
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Activity History")
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {}) {
                        Icon(
                            imageVector = Icons.Default.ArrowForward,
                            contentDescription = null,
                            tint = ComposeColor.Transparent
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowForward,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            HistoryContent(
                modifier = Modifier.fillMaxSize(),
                temperatureLogger = temperatureLogger
            )
        }
    }
}

@Composable
fun HistoryContent(
    modifier: Modifier,
    temperatureLogger: TemperatureLogger
) {
    val currentDate = LocalDate.now()
    var selectedMonth by remember { mutableStateOf(currentDate.monthValue) }
    var selectedYear by remember { mutableStateOf(currentDate.year) }
    val localDate = LocalDate.of(selectedYear, selectedMonth, 1)
    val daysInMonth = localDate.month.length(localDate.isLeapYear)
    val firstDayOfMonth = localDate.dayOfWeek.value % 7  // Sunday = 0, Monday = 1, etc.

    val daysWithData = remember(selectedMonth, selectedYear) {
        temperatureLogger.getDaysWithDataInMonth(selectedYear, selectedMonth).toList()
    }

    var selectedDay by remember { mutableStateOf<Int?>(null) }
    var selectedDayActivities by remember { mutableStateOf<List<String>>(emptyList()) }
    var selectedDayDataByActivity by remember { mutableStateOf<Map<String, List<TemperatureEntry>>?>(null) }
    var selectedDayStatsByActivity by remember { mutableStateOf<Map<String, TemperatureStats>?>(null) }
    var selectedDaySessions by remember { mutableStateOf<List<SessionInfo>>(emptyList()) }
    var selectedSession by remember { mutableStateOf<String?>(null) }
    var selectedSessionData by remember { mutableStateOf<List<TemperatureEntry>?>(null) }

    var selectedTab by remember { mutableStateOf(0) }

    // Update selected day data when day changes
    LaunchedEffect(selectedDay, selectedMonth, selectedYear) {
        selectedDay?.let { day ->
            // Get sports for this day (adapting to use Sports instead of activities)
            val sports = temperatureLogger.getSportsForDate(selectedYear, selectedMonth, day)
            selectedDayActivities = sports.map { it.name }

            // Get data grouped by activity/sport
            val dataByActivity = mutableMapOf<String, List<TemperatureEntry>>()
            val statsByActivity = mutableMapOf<String, TemperatureStats>()

            sports.forEach { sport ->
                temperatureLogger.getTemperatureDataForDate(selectedYear, selectedMonth, day, sport)?.let { data ->
                    dataByActivity[sport.name] = data
                }

                temperatureLogger.getTemperatureStatsForDate(selectedYear, selectedMonth, day, sport)?.let { stats ->
                    statsByActivity[sport.name] = stats
                }
            }

            selectedDayDataByActivity = dataByActivity
            selectedDayStatsByActivity = statsByActivity

            // Get sessions for this day
            selectedDaySessions = temperatureLogger.getSessionsForDate(selectedYear, selectedMonth, day)

            selectedSession = null
            selectedSessionData = null

            // Reset tab selection
            selectedTab = 0
        }
    }

    LaunchedEffect(selectedSession) {
        if (selectedDay != null && selectedSession != null) {
            val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val calendar = Calendar.getInstance()
            calendar.set(selectedYear, selectedMonth - 1, selectedDay!!)
            val dateString = dateFormat.format(calendar.time)

            // Find the session info to get the sport
            val sessionInfo = selectedDaySessions.find { it.id == selectedSession }

            // If found, get data for this session with the appropriate sport
            sessionInfo?.let { info ->
                selectedSessionData = temperatureLogger.getTemperatureDataForSession(
                    dateString,
                    selectedSession!!,
                    info.sport
                )
            }
        }
    }

    Column(
        modifier = modifier.padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = {
                if (selectedMonth == 1) {
                    selectedMonth = 12
                    selectedYear--
                } else {
                    selectedMonth--
                }
                selectedDay = null
                selectedDayActivities = emptyList()
                selectedDayDataByActivity = null
                selectedDayStatsByActivity = null
                selectedSession = null
                selectedSessionData = null
            }) {
                Text("<", style = MaterialTheme.typography.titleLarge)
            }

            Text(
                text = "${Month.of(selectedMonth).name} $selectedYear",
                style = MaterialTheme.typography.titleLarge
            )

            IconButton(onClick = {
                if (selectedMonth == 12) {
                    selectedMonth = 1
                    selectedYear++
                } else {
                    selectedMonth++
                }
                selectedDay = null
                selectedDayActivities = emptyList()
                selectedDayDataByActivity = null
                selectedDayStatsByActivity = null
                selectedSession = null
                selectedSessionData = null
            }) {
                Text(">", style = MaterialTheme.typography.titleLarge)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                val daysOfWeek = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
                daysOfWeek.forEach { day ->
                    Text(
                        text = day,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            val rows = (daysInMonth + firstDayOfMonth + 6) / 7
            val gridHeight = (rows * 48 + 20).dp

            LazyVerticalGrid(
                columns = GridCells.Fixed(7),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(gridHeight),
                userScrollEnabled = false
            ) {
                items(firstDayOfMonth + daysInMonth) { index ->
                    if (index < firstDayOfMonth) {
                        Box(
                            modifier = Modifier
                                .padding(4.dp)
                                .aspectRatio(1f)
                        )
                    } else {
                        val day = index - firstDayOfMonth + 1
                        val hasData = day in daysWithData
                        val isSelectedDay = selectedDay == day

                        DayCell(
                            day = day,
                            hasData = hasData,
                            isSelected = isSelectedDay,
                            onClick = {
                                selectedDay = if (isSelectedDay) null else day
                                selectedSession = null
                                selectedSessionData = null
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            selectedDay?.let { day ->
                Text(
                    text = "Data for ${Month.of(selectedMonth).name} $day, $selectedYear",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                if (selectedDayActivities.isNotEmpty()) {
                    // Tab row for activities
                    val tabs = selectedDayActivities + "All Activities"
                    if (selectedTab >= tabs.size) selectedTab = 0

                    ScrollableTabRow(
                        selectedTabIndex = selectedTab,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        tabs.forEachIndexed { index, title ->
                            Tab(
                                selected = selectedTab == index,
                                onClick = { selectedTab = index },
                                text = { Text(title) }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Display content based on selected tab
                    val selectedActivity = if (selectedTab < selectedDayActivities.size)
                        selectedDayActivities[selectedTab] else null

                    // Filter sessions by selected activity if needed
                    val filteredSessions = if (selectedActivity != null) {
                        selectedDaySessions.filter { it.sport.name == selectedActivity }
                    } else {
                        selectedDaySessions
                    }

                    Text(
                        text = if (selectedActivity != null)
                            "Sessions for $selectedActivity (${filteredSessions.size})"
                        else
                            "All Sessions (${filteredSessions.size})",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                    ) {
                        filteredSessions.forEachIndexed { index, sessionInfo ->
                            val formattedTime = try {
                                val timeStamp = sessionInfo.id.substringAfter("_")
                                val hour = timeStamp.substring(0, 2)
                                val minute = timeStamp.substring(2, 4)
                                "$hour:$minute"
                            } catch (e: Exception) {
                                sessionInfo.id
                            }

                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .clickable {
                                        selectedSession = if (selectedSession == sessionInfo.id) null else sessionInfo.id
                                    },
                                colors = CardDefaults.cardColors(
                                    containerColor = if (selectedSession == sessionInfo.id)
                                        MaterialTheme.colorScheme.primaryContainer
                                    else
                                        MaterialTheme.colorScheme.surface
                                )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Session $formattedTime - ${sessionInfo.sport.name}",
                                        style = MaterialTheme.typography.bodyLarge
                                    )

                                    Icon(
                                        imageVector = if (selectedSession == sessionInfo.id)
                                            Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
                                        contentDescription = if (selectedSession == sessionInfo.id)
                                            "Collapse" else "Expand"
                                    )
                                }
                            }

                            if (selectedSession == sessionInfo.id) {
                                val tempData = selectedSessionData
                                if (tempData != null && tempData.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(8.dp))

                                    TemperatureChart(
                                        temperatureEntries = tempData,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(200.dp)
                                            .padding(vertical = 8.dp)
                                    )

                                    var min = Double.MAX_VALUE
                                    var max = Double.MIN_VALUE
                                    var sum = 0.0

                                    tempData.forEach { entry ->
                                        if (entry.temperature < min) min = entry.temperature
                                        if (entry.temperature > max) max = entry.temperature
                                        sum += entry.temperature
                                    }

                                    val avg = sum / tempData.size

                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 8.dp)
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(16.dp)
                                        ) {
                                            Text(
                                                text = "Session Statistics",
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold
                                            )

                                            Spacer(modifier = Modifier.height(8.dp))

                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Column {
                                                    Text(
                                                        text = "Lowest",
                                                        style = MaterialTheme.typography.bodyMedium
                                                    )
                                                    Text(
                                                        text = String.format("%.1f°C", min),
                                                        style = MaterialTheme.typography.bodyLarge,
                                                        color = Blue
                                                    )
                                                }

                                                Column {
                                                    Text(
                                                        text = "Average",
                                                        style = MaterialTheme.typography.bodyMedium
                                                    )
                                                    Text(
                                                        text = String.format("%.1f°C", avg),
                                                        style = MaterialTheme.typography.bodyLarge,
                                                        color = Green
                                                    )
                                                }

                                                Column {
                                                    Text(
                                                        text = "Highest",
                                                        style = MaterialTheme.typography.bodyMedium
                                                    )
                                                    Text(
                                                        text = String.format("%.1f°C", max),
                                                        style = MaterialTheme.typography.bodyLarge,
                                                        color = Red
                                                    )
                                                }
                                            }

                                            Spacer(modifier = Modifier.height(8.dp))

                                            Text(
                                                text = "Readings: ${tempData.size}",
                                                style = MaterialTheme.typography.bodyMedium
                                            )
                                        }
                                    }
                                } else {
                                    Text(
                                        text = "No data available for this session",
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.padding(8.dp)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Show daily summary for the selected activity or all activities
                    Text(
                        text = if (selectedActivity != null)
                            "$selectedActivity Summary" else "Daily Summary",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )

                    val stats = if (selectedActivity != null)
                        selectedDayStatsByActivity?.get(selectedActivity)
                    else {
                        // Calculate overall stats from all activities
                        var lowest = Double.MAX_VALUE
                        var highest = Double.MIN_VALUE
                        var totalSum = 0.0
                        var totalCount = 0

                        selectedDayStatsByActivity?.values?.forEach { activityStats ->
                            if (activityStats.lowest < lowest) lowest = activityStats.lowest
                            if (activityStats.highest > highest) highest = activityStats.highest
                            totalSum += activityStats.average * activityStats.count
                            totalCount += activityStats.count
                        }

                        if (totalCount > 0) {
                            TemperatureStats(
                                lowest = lowest,
                                highest = highest,
                                average = totalSum / totalCount,
                                count = totalCount
                            )
                        } else null
                    }

                    stats?.let { statsData ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column {
                                        Text(
                                            text = "Lowest",
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                        Text(
                                            text = String.format("%.1f°C", statsData.lowest),
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = Blue
                                        )
                                    }

                                    Column {
                                        Text(
                                            text = "Average",
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                        Text(
                                            text = String.format("%.1f°C", statsData.average),
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = Green
                                        )
                                    }

                                    Column {
                                        Text(
                                            text = "Highest",
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                        Text(
                                            text = String.format("%.1f°C", statsData.highest),
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = Red
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                val sessionCount = if (selectedActivity != null)
                                    filteredSessions.size
                                else
                                    selectedDaySessions.size

                                Text(
                                    text = "Total readings: ${statsData.count} across $sessionCount sessions",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }

                } else {
                    Text(
                        text = "No temperature data available for ${Month.of(selectedMonth).name} $day, $selectedYear",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(vertical = 16.dp)
                    )
                }
            } ?: run {
                Text(
                    text = "Select a day to view temperature data",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier
                        .padding(vertical = 16.dp)
                        .fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
fun DayCell(
    day: Int,
    hasData: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .padding(4.dp)
            .aspectRatio(1f)
            .background(
                color = when {
                    isSelected -> MaterialTheme.colorScheme.primaryContainer
                    hasData -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                    else -> ComposeColor.Transparent
                },
                shape = CircleShape
            )
            .border(
                width = if (isSelected) 2.dp else 0.dp,
                color = if (isSelected) MaterialTheme.colorScheme.primary else ComposeColor.Transparent,
                shape = CircleShape
            )
            .clickable(enabled = hasData, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = day.toString(),
            style = MaterialTheme.typography.bodyMedium,
            color = when {
                isSelected -> MaterialTheme.colorScheme.onPrimaryContainer
                hasData -> MaterialTheme.colorScheme.onSecondaryContainer
                else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            }
        )
    }
}

@Composable
fun TemperatureChart(
    temperatureEntries: List<TemperatureEntry>,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme

    AndroidView(
        modifier = modifier,
        factory = { context ->
            LineChart(context).apply {
                description.isEnabled = false
                legend.isEnabled = true
                setTouchEnabled(true)
                setScaleEnabled(true)
                setPinchZoom(true)
                setDrawGridBackground(false)

                legend.textColor = colors.onSurface.toArgb()

                xAxis.apply {
                    position = XAxis.XAxisPosition.BOTTOM
                    setDrawGridLines(false)
                    granularity = 1f
                    labelCount = 5

                    textColor = colors.onSurface.toArgb()

                    valueFormatter = object : ValueFormatter() {
                        override fun getFormattedValue(value: Float): String {
                            return temperatureEntries.getOrNull(value.toInt())?.time ?: ""
                        }
                    }
                }

                axisLeft.apply {
                    setDrawGridLines(true)
                    granularity = 0.5f
                    axisMinimum = 35f
                    axisMaximum = 39f

                    textColor = colors.onSurface.toArgb()

                    val lowerLimit = com.github.mikephil.charting.components.LimitLine(36.5f)
                    lowerLimit.lineColor = Color.GREEN
                    lowerLimit.lineWidth = 1f

                    val upperLimit = com.github.mikephil.charting.components.LimitLine(38.0f)
                    upperLimit.lineColor = Color.GREEN
                    upperLimit.lineWidth = 1f
                }
                axisRight.isEnabled = false
            }
        },
        update = { chart ->
            val entries = temperatureEntries.mapIndexed { index, entry ->
                Entry(index.toFloat(), entry.temperature.toFloat())
            }

            val dataSet = LineDataSet(entries, "Temperature (°C)").apply {
                color = colors.primary.toArgb()
                setCircleColor(colors.primary.toArgb())
                lineWidth = 2f
                circleRadius = 3f
                setDrawValues(false)
                mode = LineDataSet.Mode.CUBIC_BEZIER
                cubicIntensity = 0.2f

                valueTextColor = colors.onSurface.toArgb()

                setDrawFilled(true)
                fillColor = colors.primary.copy(alpha = 0.3f).toArgb()
            }

            val lineData = LineData(dataSet)
            chart.data = lineData
            chart.invalidate()
        }
    )
}