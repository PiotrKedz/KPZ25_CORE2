package com.example.innertemp

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Battery0Bar
import androidx.compose.material.icons.filled.Battery1Bar
import androidx.compose.material.icons.filled.Battery2Bar
import androidx.compose.material.icons.filled.Battery3Bar
import androidx.compose.material.icons.filled.Battery4Bar
import androidx.compose.material.icons.filled.Battery5Bar
import androidx.compose.material.icons.filled.Battery6Bar
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.DirectionsBike
import androidx.compose.material.icons.outlined.DirectionsRun
import androidx.compose.material.icons.outlined.Kayaking
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.innertemp.ui.theme.Blue
import com.example.innertemp.ui.theme.Green
import com.example.innertemp.ui.theme.Red
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    isConnected: Boolean,
    connectionQuality: BluetoothConnectionQuality,
    temperatureCore: Double,
    temperatureSkin: Double,
    temperatureOutside: Double,
    batteryLevel: Double,
    isPaused: Boolean,
    isMonitoring: Boolean,
    activityMode: ActivityMode,
    selectedSport: Sport,
    athleticLevel: String,
    customTopThreshold: Double,
    customBottomThreshold: Double,
    onPauseToggle: () -> Unit,
    onMonitoringToggle: () -> Unit,
    onGoToProfile: () -> Unit,
    onGoToHistory: () -> Unit,
    onActivityModeChange: (ActivityMode) -> Unit,
    onSportChange: (Sport) -> Unit,
    onCustomThresholdsChange: (top: Double, bottom: Double) -> Unit,
    onShowHeatZones: () -> Unit
) {
    var showCustomSettingsDialog by remember { mutableStateOf(false) }
    val effectiveIsConnected = isConnected
    val effectiveTemperatureCore = temperatureCore
    val effectiveBatteryLevel = batteryLevel

    val (lowThreshold, _, highThreshold) = getTemperatureColorRanges(
        mode = activityMode,
        athleticLevel = athleticLevel,
        customTop = if (activityMode == ActivityMode.CUSTOM) customTopThreshold else null,
        customBottom = if (activityMode == ActivityMode.CUSTOM) customBottomThreshold else null
    )

    val tempColorSkin = when {
        temperatureSkin < lowThreshold -> Blue
        temperatureSkin > highThreshold -> Red
        else -> Green
    }
    val tempColorOutside = when {
        temperatureOutside < lowThreshold -> Color(0xFF4D7F86)
        temperatureOutside > highThreshold -> Red
        else -> Green
    }
    val tempColorCore = when {
        effectiveTemperatureCore < lowThreshold -> Blue
        effectiveTemperatureCore > highThreshold -> Red
        else -> Green
    }

    val currentDateStr = remember {
        SimpleDateFormat("dd MMMM HH:mm", Locale.getDefault()).format(Date())
    }

    if (showCustomSettingsDialog) {
        CustomThresholdDialog(
            currentTopThreshold = customTopThreshold,
            currentBottomThreshold = customBottomThreshold,
            onDismissRequest = { showCustomSettingsDialog = false },
            onSave = { top, bottom ->
                onCustomThresholdsChange(top, bottom)
                showCustomSettingsDialog = false
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Home",
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                },
                actions = {
                    IconButton(onClick = onGoToProfile) {
                        Icon(Icons.Default.Person, contentDescription = "Profile")
                    }
                    IconButton(onClick = onGoToHistory) {
                        Icon(Icons.Default.History, contentDescription = "History")
                    }
                    IconButton(onClick = onShowHeatZones) {
                        Icon(Icons.Outlined.BarChart, contentDescription = "Heat Zones")
                    }
                },
                // Explicitly set TopAppBar colors to use standard background
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    actionIconContentColor = MaterialTheme.colorScheme.onSurface
                )
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
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = currentDateStr,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = if (effectiveIsConnected) Icons.Default.BluetoothConnected
                                else Icons.Default.BluetoothDisabled,
                                contentDescription = "Connection Status",
                                tint = if (effectiveIsConnected) Green else Red
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (effectiveIsConnected) "Connected" else "Disconnected",
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (effectiveIsConnected) Green else Red,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        if (effectiveIsConnected) {
                            Text(
                                text = "Signal: ${connectionQuality.name}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TemperatureCard(
                        title = "Skin",
                        temperature = temperatureSkin,
                        color = tempColorSkin,
                        modifier = Modifier.weight(1f)
                    )
                    TemperatureCard(
                        title = "Outer",
                        temperature = temperatureOutside,
                        color = tempColorOutside,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            if (effectiveIsConnected) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = when {
                                        effectiveBatteryLevel >= 90 -> Icons.Default.BatteryFull
                                        effectiveBatteryLevel >= 75 -> Icons.Default.Battery6Bar
                                        effectiveBatteryLevel >= 60 -> Icons.Default.Battery5Bar
                                        effectiveBatteryLevel >= 45 -> Icons.Default.Battery4Bar
                                        effectiveBatteryLevel >= 30 -> Icons.Default.Battery3Bar
                                        effectiveBatteryLevel >= 15 -> Icons.Default.Battery2Bar
                                        effectiveBatteryLevel > 0 -> Icons.Default.Battery1Bar
                                        else -> Icons.Default.Battery0Bar
                                    },
                                    contentDescription = "Battery",
                                    tint = when {
                                        effectiveBatteryLevel >= 50 -> Green
                                        effectiveBatteryLevel >= 25 -> Color(0xFFF59E0B)
                                        else -> Red
                                    }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Battery: ${effectiveBatteryLevel.roundToInt()}%",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            LinearProgressIndicator(
                                progress = (effectiveBatteryLevel / 100.0).toFloat(),
                                modifier = Modifier.fillMaxWidth(),
                                color = when {
                                    effectiveBatteryLevel >= 50 -> Green
                                    effectiveBatteryLevel >= 25 -> Color(0xFFF59E0B)
                                    else -> Red
                                }
                            )
                        }
                    }
                }
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = "Activity Settings",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        // Activity Mode
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            ActivityMode.values().forEach { mode ->
                                FilterChip(
                                    onClick = { onActivityModeChange(mode) },
                                    label = { Text(mode.name.lowercase().replaceFirstChar { it.uppercase() }) },
                                    selected = activityMode == mode,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Sport Selection
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Sport.values().forEach { sport ->
                                FilterChip(
                                    onClick = { onSportChange(sport) },
                                    label = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = when (sport) {
                                                    Sport.RUNNING -> Icons.Outlined.DirectionsRun
                                                    Sport.CYCLING -> Icons.Outlined.DirectionsBike
                                                    Sport.KAYAKING -> Icons.Outlined.Kayaking
                                                },
                                                contentDescription = null,
                                                modifier = Modifier.size(12.dp)
                                            )
                                            Spacer(modifier = Modifier.width(3.dp))
                                            Text(sport.name.lowercase().replaceFirstChar { it.uppercase() })
                                        }
                                    },
                                    selected = selectedSport == sport,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }

                        // Custom Settings Button
                        if (activityMode == ActivityMode.CUSTOM) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(
                                onClick = { showCustomSettingsDialog = true },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Outlined.Tune, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Custom Thresholds")
                            }
                        }

                        // Current thresholds display
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Current Range: ${lowThreshold.toString().take(4)}°C - ${highThreshold.toString().take(4)}°C",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }
                }
            }

            // Control Buttons
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Start/Stop Monitoring
                    Button(
                        onClick = onMonitoringToggle,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary
                        ),
                        enabled = effectiveIsConnected
                    ) {
                        Icon(
                            imageVector = if (isMonitoring) Icons.Default.Stop else Icons.Default.PlayArrow,
                            contentDescription = null
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (isMonitoring) "Stop" else "Start")
                    }

                    // Pause/Resume (only show when monitoring)
                    if (isMonitoring) {
                        Button(
                            onClick = onPauseToggle,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Icon(
                                imageVector = if (isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                                contentDescription = null
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(if (isPaused) "Resume" else "Pause")
                        }
                    }
                }
            }

            // Monitoring Status
            if (isMonitoring || isPaused) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (isPaused) Icons.Default.Pause else Icons.Default.FiberManualRecord,
                                contentDescription = null,
                                tint = if (isPaused) Color(0xFFF59E0B) else Red
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isPaused) "Monitoring Paused" else "Monitoring Active",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold,
                                color = if (isPaused) Color(0xFFF59E0B) else Green
                            )
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
fun TemperatureCard(
    title: String,
    temperature: Double,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = if (temperature > 0) "${String.format("%.1f", temperature)}°C" else "--",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = color,
                fontSize = 18.sp
            )
        }
    }
}