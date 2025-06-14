package com.example.innertemp

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HeatZoneScreen(
    zoneDataMap: Map<TemperatureZone, ZoneData>, // Direct map instead of State
    activityMode: ActivityMode,
    athleticLevel: String,
    customTopThreshold: Double,
    customBottomThreshold: Double,
    currentTemperature: Double? = null, // Direct value instead of State
    onBack: () -> Unit
) {
    var showInfoDialog by remember { mutableStateOf(false) }

    // Use the direct values
    val currentZoneDataMap = zoneDataMap
    val currentTemp = currentTemperature

    val (lowThreshold, _, highThreshold) = getTemperatureColorRanges(
        mode = activityMode,
        athleticLevel = athleticLevel,
        customTop = if (activityMode == ActivityMode.CUSTOM) customTopThreshold else null,
        customBottom = if (activityMode == ActivityMode.CUSTOM) customBottomThreshold else null
    )

    // Calculate total time for percentages
    val totalTime = currentZoneDataMap.values.sumOf { it.timeInSeconds }
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
            // Enhanced Session Summary Card
            item {
                EnhancedSessionSummaryCard(
                    totalTime = totalTime,
                    activityMode = activityMode,
                    lowThreshold = lowThreshold,
                    highThreshold = highThreshold,
                    hasData = hasData,
                    currentTemp = currentTemp,
                    optimalTemp = (lowThreshold + highThreshold) / 2
                )
            }

            if (hasData) {
                // Enhanced Zone Progress Overview with Vertical Bars
                item {
                    EnhancedZoneProgressOverview(
                        zoneDataMap = currentZoneDataMap,
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

// Alternative version using StateFlow (recommended for real-time data)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HeatZoneScreenWithStateFlow(
    zoneDataMapFlow: kotlinx.coroutines.flow.StateFlow<Map<TemperatureZone, ZoneData>>,
    currentTemperatureFlow: kotlinx.coroutines.flow.StateFlow<Double?>,
    activityMode: ActivityMode,
    athleticLevel: String,
    customTopThreshold: Double,
    customBottomThreshold: Double,
    onBack: () -> Unit
) {
    // Collect StateFlow as Compose State
    val zoneDataMap by zoneDataMapFlow.collectAsState()
    val currentTemperature by currentTemperatureFlow.collectAsState()

    var showInfoDialog by remember { mutableStateOf(false) }

    val (lowThreshold, _, highThreshold) = getTemperatureColorRanges(
        mode = activityMode,
        athleticLevel = athleticLevel,
        customTop = if (activityMode == ActivityMode.CUSTOM) customTopThreshold else null,
        customBottom = if (activityMode == ActivityMode.CUSTOM) customBottomThreshold else null
    )

    // Calculate total time for percentages - now reactive
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
            // Enhanced Session Summary Card
            item {
                EnhancedSessionSummaryCard(
                    totalTime = totalTime,
                    activityMode = activityMode,
                    lowThreshold = lowThreshold,
                    highThreshold = highThreshold,
                    hasData = hasData,
                    currentTemp = currentTemperature,
                    optimalTemp = (lowThreshold + highThreshold) / 2
                )
            }

            if (hasData) {
                // Enhanced Zone Progress Overview with Vertical Bars
                item {
                    EnhancedZoneProgressOverview(
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
fun EnhancedSessionSummaryCard(
    totalTime: Int,
    activityMode: ActivityMode,
    lowThreshold: Double,
    highThreshold: Double,
    hasData: Boolean,
    currentTemp: Double? = null,
    optimalTemp: Double? = null
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (hasData) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Session Summary",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                // Add live indicator
                if (hasData) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(Color.Green)
                        )
                        Text(
                            text = "LIVE",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (hasData) {
                // Current and optimal temperature display (if available)
                if (currentTemp != null || optimalTemp != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        if (currentTemp != null) {
                            TemperatureDisplay(
                                label = "Current",
                                temperature = currentTemp,
                                isOptimal = false
                            )
                        }
                        if (optimalTemp != null) {
                            TemperatureDisplay(
                                label = "Optimal",
                                temperature = optimalTemp,
                                isOptimal = true
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    EnhancedSummaryItem(
                        label = "Total Time",
                        value = formatTime(totalTime),
                        icon = "⏱️"
                    )
                    EnhancedSummaryItem(
                        label = "Mode",
                        value = activityMode.name.lowercase().replaceFirstChar { it.uppercase() },
                        icon = "🎯"
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                            RoundedCornerShape(8.dp)
                        )
                        .padding(12.dp)
                ) {
                    Text(
                        text = "Target Range: ${String.format("%.1f", lowThreshold)}°C - ${String.format("%.1f", highThreshold)}°C",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            } else {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "📊",
                        fontSize = 48.sp
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "Start monitoring to see zone data",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
fun TemperatureDisplay(
    label: String,
    temperature: Double,
    isOptimal: Boolean
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .background(
                    if (isOptimal) {
                        Color(0xFFDFF0D8) // Light green background
                    } else {
                        MaterialTheme.colorScheme.surface
                    },
                    RoundedCornerShape(12.dp)
                )
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Text(
                text = "${String.format("%.1f", temperature)}°C",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = if (isOptimal) {
                    Color(0xFF2E7D32) // Dark green
                } else {
                    MaterialTheme.colorScheme.onSurface
                }
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun EnhancedSummaryItem(
    label: String,
    value: String,
    icon: String
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = icon,
                fontSize = 16.sp
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun EnhancedZoneProgressOverview(
    zoneDataMap: Map<TemperatureZone, ZoneData>,
    totalTime: Int,
    lowThreshold: Double,
    highThreshold: Double
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Text(
                text = "Zone Distribution",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Enhanced vertical bars chart
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
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
                        // Percentage text with better styling
                        Text(
                            text = if (hasTime) "${percentage.toInt()}%" else "0%",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.height(20.dp)
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // Enhanced vertical bar with gradient effect
                        val maxBarHeight = 140
                        val barHeight = if (hasTime && zoneData != null && zoneData.timeInSeconds > 0) {
                            val calculatedHeight = (percentage.toInt() * maxBarHeight / 100)
                            if (calculatedHeight < 8) 8 else calculatedHeight
                        } else {
                            8
                        }

                        Box(
                            modifier = Modifier
                                .width(36.dp)
                                .height(barHeight.dp)
                                .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                                .background(
                                    if (hasTime) {
                                        // Create gradient effect
                                        Brush.verticalGradient(
                                            colors = listOf(
                                                getZoneColor(zone).copy(alpha = 0.8f),
                                                getZoneColor(zone)
                                            )
                                        )
                                    } else {
                                        Brush.verticalGradient(
                                            colors = listOf(
                                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                            )
                                        )
                                    }
                                )
                                // Add subtle shadow/elevation effect
                                .let { modifier ->
                                    if (hasTime) {
                                        modifier.shadow(
                                            elevation = 2.dp,
                                            shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp)
                                        )
                                    } else modifier
                                }
                        ) {
                            // Add a subtle highlight at the top of bars with data
                            if (hasTime) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(2.dp)
                                        .background(
                                            getZoneColor(zone).copy(alpha = 0.6f),
                                            RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp)
                                        )
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Enhanced zone icon with background
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(
                                    if (hasTime) {
                                        getZoneColor(zone).copy(alpha = 0.15f)
                                    } else {
                                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                                    }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = getZoneIcon(zone),
                                fontSize = 16.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        // Zone label with better typography
                        Text(
                            text = getZoneLabel(zone),
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1
                        )

                        // Time spent with improved formatting
                        if (hasTime) {
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = zoneData?.getFormattedTime() ?: "",
                                style = MaterialTheme.typography.labelSmall,
                                textAlign = TextAlign.Center,
                                color = getZoneColor(zone).copy(alpha = 0.8f),
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }

            // Add target range info at the bottom
            Spacer(modifier = Modifier.height(16.dp))

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant,
                thickness = 0.5.dp
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "Target Range: ${String.format("%.1f", lowThreshold)}°C - ${String.format("%.1f", highThreshold)}°C",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium
                )
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

// Helper functions for consistent styling
private fun getZoneColor(zone: TemperatureZone): Color {
    return when (zone) {
        TemperatureZone.TOO_LOW -> Color(0xFF3B82F6)        // Blue
        TemperatureZone.STEP_LOWER -> Color(0xFF06B6D4)     // Cyan
        TemperatureZone.PERFECT -> Color(0xFF10B981)        // Green
        TemperatureZone.STEP_HIGHER -> Color(0xFFF59E0B)    // Amber
        TemperatureZone.TOO_HIGH -> Color(0xFFEF4444)       // Red
    }
}

private fun getZoneIcon(zone: TemperatureZone): String {
    return when (zone) {
        TemperatureZone.TOO_LOW -> "❄️"
        TemperatureZone.STEP_LOWER -> "⬇️"
        TemperatureZone.PERFECT -> "✅"
        TemperatureZone.STEP_HIGHER -> "⬆️"
        TemperatureZone.TOO_HIGH -> "🔥"
    }
}

private fun getZoneLabel(zone: TemperatureZone): String {
    return when (zone) {
        TemperatureZone.TOO_LOW -> "Too Low"
        TemperatureZone.STEP_LOWER -> "Lower"
        TemperatureZone.PERFECT -> "Perfect"
        TemperatureZone.STEP_HIGHER -> "Higher"
        TemperatureZone.TOO_HIGH -> "Too High"
    }
}

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