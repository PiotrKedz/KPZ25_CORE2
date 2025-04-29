package com.example.innertemp

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
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
import androidx.compose.ui.graphics.Color

class HistoryActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()

        setContent {
            InnerTempTheme {
                HistoryScreen(onBack = { finish() })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(onBack: () -> Unit) {
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
        },
        content = { innerPadding ->
            HistoryContent(modifier = Modifier.padding(innerPadding))
        }
    )
}

@Composable
fun HistoryContent(modifier: Modifier) {
    // Get current month and year
    val currentMonth = LocalDate.now().month
    val currentYear = LocalDate.now().year
    val daysInMonth = currentMonth.length(LocalDate.of(currentYear, currentMonth, 1).isLeapYear)
    val firstDayOfMonth = LocalDate.of(currentYear, currentMonth, 1).dayOfWeek.ordinal

    // Simulate historical data for each day
    val dayData = remember {
        mutableStateOf<Map<Int, String>>(mapOf(
            1 to "Temperature: 22°C, Activity: Running",
            5 to "Temperature: 21°C, Activity: Walking",
            10 to "Temperature: 23°C, Activity: Cycling",
            15 to "Temperature: 24°C, Activity: Yoga",
            20 to "Temperature: 25°C, Activity: Jogging"
        ))
    }

    var selectedDay by remember { mutableStateOf<Int?>(null) }

    Column(
        modifier = modifier.fillMaxSize().padding(16.dp)
    ) {
        // Header for current month and year
        Text(
            text = "$currentMonth $currentYear",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Create grid for days of the month
        LazyVerticalGrid(
            columns = GridCells.Fixed(7),
            modifier = Modifier.fillMaxWidth()
        ) {
            // Add empty spaces before the first day of the month
            items(firstDayOfMonth + daysInMonth) { index ->
                if (index < firstDayOfMonth) {
                    // Empty space — matches day size for alignment
                    Box(
                        modifier = Modifier
                            .padding(4.dp)
                            .width(40.dp)
                            .height(40.dp)
                    )
                } else {
                    val day = index - firstDayOfMonth + 1
                    DayCell(day = day, onClick = {
                        selectedDay = day
                    })
                }
            }


        }

        Spacer(modifier = Modifier.height(16.dp))

        // Show the data for the selected day
        if (selectedDay != null) {
            val dataForSelectedDay = dayData.value[selectedDay]
            if (dataForSelectedDay != null) {
                Text(
                    text = "Data for day $selectedDay: $dataForSelectedDay",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            } else {
                Text(
                    text = "No data available for day $selectedDay",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            }
        }
    }
}

@Composable
fun DayCell(day: Int, onClick: (Int) -> Unit) {
    Button(
        onClick = { onClick(day) },
        modifier = Modifier
            .padding(4.dp)
            .size(40.dp),
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
    ) {
        Text(
            text = day.toString(),
            color = Color.White // Or use: MaterialTheme.colorScheme.onPrimary
        )
    }
}





@Preview(showBackground = true)
@Composable
fun PreviewHistoryScreen() {
    InnerTempTheme {
        HistoryScreen(onBack = {})
    }
}
