package com.example.innertemp

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HeatZoneScreen(
    zoneDataMap: Map<TemperatureZone, ZoneData>,
    activityMode: ActivityMode,
    athleticLevel: String,
    customTopThreshold: Double,
    customBottomThreshold: Double,
    onBack: () -> Unit
) {
    var showInfoDialog by remember { mutableStateOf(false) }

    val (lowThreshold, _, highThreshold) = getTemperatureColorRanges(
        mode = activityMode,
        athleticLevel = athleticLevel,
        customTop = if (activityMode == ActivityMode.CUSTOM) customTopThreshold else null,
        customBottom = if (activityMode == ActivityMode.CUSTOM) customBottomThreshold else null
    )

    // Calculate total time for percentages
    val totalTime = zoneDataMap.values.sumOf { it.timeInSeconds }
    val hasData = totalTime > 0

    if (showInfoDialog) {
        HeatZoneInfoDialog(
            onDismiss = { showInfoDialog = false }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Heat Zones",
                        textAlign = TextAlign.Center
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showInfoDialog = true }) {
                        Icon(Icons.Default.Info, contentDescription = "Zone Info")
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Session Summary Card
            item {
                SessionSummaryCard(
                    totalTime = totalTime,
                    activityMode = activityMode,
                    lowThreshold = lowThreshold,
                    highThreshold = highThreshold,
                    hasData = hasData
                )
            }

            if (hasData) {
                // Zone Progress Overview with Vertical Bars
                item {
                    ZoneProgressOverview(
                        zoneDataMap = zoneDataMap,
                        totalTime = totalTime,
                        lowThreshold = lowThreshold,
                        highThreshold = highThreshold
                    )
                }
            } else {
                // No Data Message
                item {
                    NoDataMessage()
                }
            }

            // Add some bottom padding
            item {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
fun SessionSummaryCard(
    totalTime: Int,
    activityMode: ActivityMode,
    lowThreshold: Double,
    highThreshold: Double,
    hasData: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (hasData)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Session Summary",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(12.dp))

            if (hasData) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    SummaryItem(
                        label = "Total Time",
                        value = formatTime(totalTime)
                    )
                    SummaryItem(
                        label = "Mode",
                        value = activityMode.name.lowercase().replaceFirstChar { it.uppercase() }
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Target Range: ${String.format("%.1f", lowThreshold)}°C - ${String.format("%.1f", highThreshold)}°C",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(
                    text = "Start monitoring to see zone data",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun SummaryItem(
    label: String,
    value: String
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun ZoneProgressOverview(
    zoneDataMap: Map<TemperatureZone, ZoneData>,
    totalTime: Int,
    lowThreshold: Double,
    highThreshold: Double
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Zone Distribution",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Vertical bars chart
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.Bottom
            ) {
                TemperatureZone.values().forEach { zone ->
                    val zoneData = zoneDataMap[zone]
                    val percentage = zoneData?.getPercentage(totalTime) ?: 0.0
                    val hasTime = zoneData != null && zoneData.timeInSeconds > 0

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.weight(1f)
                    ) {
                        // Percentage text at top
                        Text(
                            text = if (hasTime) "${percentage.toDouble().roundToInt()}%" else "0%",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.height(16.dp)
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        // Vertical bar
                        val barHeight = (percentage.toDouble() * 1.2).roundToInt().coerceAtLeast(4)
                        Box(
                            modifier = Modifier
                                .width(32.dp)
                                .height(barHeight.dp)
                                .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                .background(
                                    if (hasTime) {
                                        zoneData?.color ?: MaterialTheme.colorScheme.surfaceVariant
                                    } else {
                                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                                    }
                                )
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // Zone icon
                        Text(
                            text = when (zone) {
                                TemperatureZone.TOO_LOW -> "❄️"
                                TemperatureZone.STEP_LOWER -> "⬇️"
                                TemperatureZone.PERFECT -> "✅"
                                TemperatureZone.STEP_HIGHER -> "⬆️"
                                TemperatureZone.TOO_HIGH -> "🔥"
                            },
                            fontSize = 16.sp
                        )

                        // Zone label
                        Text(
                            text = when (zone) {
                                TemperatureZone.TOO_LOW -> "Too Low"
                                TemperatureZone.STEP_LOWER -> "Lower"
                                TemperatureZone.PERFECT -> "Perfect"
                                TemperatureZone.STEP_HIGHER -> "Higher"
                                TemperatureZone.TOO_HIGH -> "Too High"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )

                        // Time spent
                        if (hasTime) {
                            Text(
                                text = zoneData?.getFormattedTime() ?: "",
                                style = MaterialTheme.typography.bodySmall,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                fontSize = 10.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ZoneDetailCard(
    zoneData: ZoneData,
    totalTime: Int,
    lowThreshold: Double,
    highThreshold: Double
) {
    val percentage = zoneData.getPercentage(totalTime)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = zoneData.color.copy(alpha = 0.1f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Zone indicator
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(zoneData.color),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = zoneData.icon,
                    fontSize = 20.sp,
                    color = Color.White
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Zone details
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = zoneData.label,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = getZoneRange(zoneData.zone, lowThreshold, highThreshold),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "Time: ${zoneData.getFormattedTime()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Text(
                        text = "Share: ${String.format("%.1f", percentage)}%",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun NoDataMessage() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "📊",
                fontSize = 48.sp
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "No Zone Data Yet",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Start monitoring your temperature to see how much time you spend in each heat zone.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun ZoneLegendCard(
    lowThreshold: Double,
    highThreshold: Double
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Zone Legend",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(12.dp))

            TemperatureZone.values().forEach { zone ->
                val zoneData = ZoneData(
                    zone = zone,
                    label = when (zone) {
                        TemperatureZone.TOO_LOW -> "Too Low"
                        TemperatureZone.STEP_LOWER -> "Step Lower"
                        TemperatureZone.PERFECT -> "Perfect"
                        TemperatureZone.STEP_HIGHER -> "Step Higher"
                        TemperatureZone.TOO_HIGH -> "Too High"
                    },
                    color = when (zone) {
                        TemperatureZone.TOO_LOW -> Color(0xFF3B82F6)
                        TemperatureZone.STEP_LOWER -> Color(0xFF06B6D4)
                        TemperatureZone.PERFECT -> Color(0xFF10B981)
                        TemperatureZone.STEP_HIGHER -> Color(0xFFF59E0B)
                        TemperatureZone.TOO_HIGH -> Color(0xFFEF4444)
                    },
                    icon = when (zone) {
                        TemperatureZone.TOO_LOW -> "❄️"
                        TemperatureZone.STEP_LOWER -> "⬇️"
                        TemperatureZone.PERFECT -> "✅"
                        TemperatureZone.STEP_HIGHER -> "⬆️"
                        TemperatureZone.TOO_HIGH -> "🔥"
                    }
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(zoneData.color),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = zoneData.icon,
                            fontSize = 12.sp
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Text(
                            text = zoneData.label,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = getZoneRange(zone, lowThreshold, highThreshold),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun HeatZoneInfoDialog(
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "About Heat Zones",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Heat zones help you understand your thermal regulation during activities:",
                    style = MaterialTheme.typography.bodyMedium
                )

                Spacer(modifier = Modifier.height(12.dp))

                val zoneDescriptions = listOf(
                    "❄️ Too Low: Body temperature below optimal range",
                    "⬇️ Step Lower: Slightly below target temperature",
                    "✅ Perfect: Optimal temperature range for performance",
                    "⬆️ Step Higher: Slightly above target temperature",
                    "🔥 Too High: Risk of overheating"
                )

                zoneDescriptions.forEach { description ->
                    Text(
                        text = "• $description",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Spending more time in the Perfect zone typically indicates better thermal regulation and performance.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("Got it")
                }
            }
        }
    }
}

// Helper function to format time
private fun formatTime(seconds: Int): String {
    return if (seconds < 60) {
        "${seconds}s"
    } else {
        val minutes = seconds / 60
        val remainingSeconds = seconds % 60
        if (remainingSeconds > 0) {
            "${minutes}m ${remainingSeconds}s"
        } else {
            "${minutes}m"
        }
    }
}