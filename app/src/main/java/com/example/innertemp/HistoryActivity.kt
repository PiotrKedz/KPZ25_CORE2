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
import androidx.compose.ui.tooling.preview.Preview
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
import com.github.mikephil.charting.components.YAxis
import com.github.mikephil.charting.formatter.ValueFormatter
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.*
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.border
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.clickable

class HistoryActivity : AppCompatActivity() {
    private lateinit var temperatureLogger: TemperatureLogger

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()

        // Initialize the temperature logger
        temperatureLogger = TemperatureLogger(this)

        setContent {
            InnerTempTheme {
                HistoryScreen(
                    onBack = { finish() },
                    temperatureLogger = temperatureLogger
                )
            }
        }
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
    // Get current month and year
    val currentDate = LocalDate.now()
    var selectedMonth by remember { mutableStateOf(currentDate.monthValue) }
    var selectedYear by remember { mutableStateOf(currentDate.year) }

    // Get days in the selected month
    val localDate = LocalDate.of(selectedYear, selectedMonth, 1)
    val daysInMonth = localDate.month.length(localDate.isLeapYear)
    val firstDayOfMonth = localDate.dayOfWeek.value % 7  // Sunday = 0, Monday = 1, etc.

    // Get data for days with temperature readings
    val daysWithData = remember(selectedMonth, selectedYear) {
        temperatureLogger.getDaysWithDataInMonth(selectedYear, selectedMonth).toList()
    }

    var selectedDay by remember { mutableStateOf<Int?>(null) }
    var selectedDayData by remember { mutableStateOf<List<TemperatureEntry>?>(null) }
    var selectedDayStats by remember { mutableStateOf<TemperatureStats?>(null) }

    // Update selected day data when day changes
    LaunchedEffect(selectedDay, selectedMonth, selectedYear) {
        selectedDay?.let { day ->
            selectedDayData =
                temperatureLogger.getTemperatureDataForDate(selectedYear, selectedMonth, day)
            selectedDayStats =
                temperatureLogger.getTemperatureStatsForDate(selectedYear, selectedMonth, day)
        }
    }

    Column(
        modifier = modifier.padding(16.dp)
    ) {
        // Header for month and year navigation (not scrollable)
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
                selectedDayData = null
                selectedDayStats = null
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
                selectedDayData = null
                selectedDayStats = null
            }) {
                Text(">", style = MaterialTheme.typography.titleLarge)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Scrollable content
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            // Days of week header
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

            // Create grid for days of the month
            LazyVerticalGrid(
                columns = GridCells.Fixed(7),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(((daysInMonth / 7 + 1) * 48).dp),
                userScrollEnabled = false
            ) {
                // Add empty spaces before the first day of the month
                items(firstDayOfMonth + daysInMonth) { index ->
                    if (index < firstDayOfMonth) {
                        // Empty space for alignment
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
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Show temperature chart and details for selected day
            selectedDay?.let { day ->
                val tempData = selectedDayData
                if (tempData != null && tempData.isNotEmpty()) {
                    // Display temperature chart
                    Text(
                        text = "Temperature Data for ${Month.of(selectedMonth).name} $day, $selectedYear",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    TemperatureChart(
                        temperatureEntries = tempData,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .padding(vertical = 8.dp)
                    )

                    // Display temperature statistics
                    selectedDayStats?.let { stats ->
                        Spacer(modifier = Modifier.height(8.dp))

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
                                    text = "Temperature Statistics",
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
                                            text = String.format("%.1f°C", stats.lowest),
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = androidx.compose.ui.graphics.Color.Blue
                                        )
                                    }

                                    Column {
                                        Text(
                                            text = "Average",
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                        Text(
                                            text = String.format("%.1f°C", stats.average),
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = androidx.compose.ui.graphics.Color.Green
                                        )
                                    }

                                    Column {
                                        Text(
                                            text = "Highest",
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                        Text(
                                            text = String.format("%.1f°C", stats.highest),
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = androidx.compose.ui.graphics.Color.Red
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                Text(
                                    text = "Total readings: ${stats.count}",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                } else {
                    // No data available for selected day
                    Text(
                        text = "No temperature data available for ${Month.of(selectedMonth).name} $day, $selectedYear",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(vertical = 16.dp)
                    )
                }
            } ?: run {
                // No day selected
                Text(
                    text = "Select a day to view temperature data",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier
                        .padding(vertical = 16.dp)
                        .fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
            }

            // Add bottom padding to ensure content at the bottom is visible when scrolled
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

/**
 * DayCell component for displaying individual calendar days
 */
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
                    else -> androidx.compose.ui.graphics.Color.Transparent
                },
                shape = CircleShape
            )
            .border(
                width = if (isSelected) 2.dp else 0.dp,
                color = if (isSelected) MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.Transparent,
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

/**
 * TemperatureChart component for visualizing temperature data
 */
@Composable
fun TemperatureChart(
    temperatureEntries: List<TemperatureEntry>,
    modifier: Modifier = Modifier
) {
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

                // Configure axes
                xAxis.apply {
                    position = XAxis.XAxisPosition.BOTTOM
                    setDrawGridLines(false)
                    granularity = 1f
                    labelCount = 5
                    valueFormatter = object : ValueFormatter() {
                        override fun getFormattedValue(value: Float): String {
                            // Time is already formatted as a string in our TemperatureEntry
                            return temperatureEntries.getOrNull(value.toInt())?.time ?: ""
                        }
                    }
                }

                axisLeft.apply {
                    setDrawGridLines(true)
                    granularity = 0.5f
                    axisMinimum = 35f  // Minimum temperature
                    axisMaximum = 42f  // Maximum temperature
                }

                axisRight.isEnabled = false
            }
        },
        update = { chart ->
            // Create entries with index as X and temperature as Y
            val entries = temperatureEntries.mapIndexed { index, entry ->
                Entry(index.toFloat(), entry.temperature.toFloat())
            }

            val dataSet = LineDataSet(entries, "Temperature (°C)").apply {
                color = androidx.compose.ui.graphics.Color.Blue.toArgb()
                setCircleColor(androidx.compose.ui.graphics.Color.Blue.toArgb())
                lineWidth = 2f
                circleRadius = 3f
                setDrawValues(false)
                mode = LineDataSet.Mode.CUBIC_BEZIER
                cubicIntensity = 0.2f

                // Fill color under the line
                setDrawFilled(true)
                fillColor = androidx.compose.ui.graphics.Color.Blue.copy(alpha = 0.3f).toArgb()
            }

            val lineData = LineData(dataSet)
            chart.data = lineData
            chart.invalidate() // Refresh chart
        }
    )
}