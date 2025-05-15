package com.example.innertemp

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresPermission
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.lifecycle.Lifecycle
import com.example.innertemp.ui.theme.Blue
import com.example.innertemp.ui.theme.Green
import com.example.innertemp.ui.theme.Red
import com.example.innertemp.ui.theme.InnerTempTheme
import java.text.SimpleDateFormat
import java.util.*
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.outlined.DirectionsRun
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.window.Dialog

enum class ActivityMode {
    TRAINING,
    RACE,
    CUSTOM
}

class MainActivity : ComponentActivity() {
    private lateinit var bluetoothManager: BluetoothManager
    private lateinit var temperatureLogger: TemperatureLogger
    private val _isConnected = mutableStateOf(false)
    private val _temperatureCore = mutableStateOf(0.0)
    private val _temperatureSkin = mutableStateOf(0.0)
    private val _temperatureOutside = mutableStateOf(0.0)
    private val _batteryLevel = mutableStateOf(0.0)
    private val _isPaused = mutableStateOf(false)
    private val _isMonitoring = mutableStateOf(false)
    private val _connectionQuality = mutableStateOf(BluetoothConnectionQuality.DISCONNECTED)
    private var useDarkTheme by mutableStateOf(false)
    private val _averageTemperatureCore = mutableStateOf(0.0)
    private var tempCoreSamples = mutableListOf<Double>()
    private var totalSamples = 0
    private val _activityMode = mutableStateOf(ActivityMode.TRAINING)
    private val _athleticLevel = mutableStateOf("")

    private val _customTopTemperatureThreshold = mutableStateOf(38.5)
    private val _customBottomTemperatureThreshold = mutableStateOf(36.5)

    private lateinit var notificationsManager: NotificationsManager

    private val enableBtLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                Toast.makeText(this, "Bluetooth enabled", Toast.LENGTH_SHORT).show()
                bluetoothManager.startBleScan()
            } else {
                Toast.makeText(this, "Bluetooth is required", Toast.LENGTH_SHORT).show()
            }
        }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (allGranted) {
            ensureBluetoothEnabled()
        } else {
            Toast.makeText(this, "Bluetooth permissions are required", Toast.LENGTH_SHORT).show()
        }
    }

    private fun ensureBluetoothEnabled() {
        if (!bluetoothManager.isBluetoothEnabled()) {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            enableBtLauncher.launch(enableBtIntent)
        } else {
            bluetoothManager.startBleScan()
        }
    }

    private fun checkPermissions() {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            mutableListOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.ACCESS_FINE_LOCATION
            ).apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    add(Manifest.permission.POST_NOTIFICATIONS)
                }
            }.toTypedArray() // Convert back to Array
        } else {
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        }

        val allGranted = permissions.all {
            ActivityCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }

        if (allGranted) {
            ensureBluetoothEnabled()
        } else {
            permissionLauncher.launch(permissions)
        }
    }

    private fun updateAverageTemperature(newValue: Double) {
        if (newValue <= 0) return

        tempCoreSamples.add(newValue)
        totalSamples++

        val sum = tempCoreSamples.sum()
        _averageTemperatureCore.value = String.format("%.1f", sum / totalSamples).toDouble()

        if (tempCoreSamples.size > 100) {
            tempCoreSamples.removeAt(0)
        }
    }

    private fun resetAverageTemperature() {
        tempCoreSamples.clear()
        totalSamples = 0
        _averageTemperatureCore.value = 0.0
    }

    private fun loadUserAthleticLevel() {
        val sharedPref = getSharedPreferences("user_profile", Context.MODE_PRIVATE)
        _athleticLevel.value = sharedPref.getString("athletic_level", "Medium") ?: "Medium"
    }

    private fun saveActivityMode(mode: ActivityMode) {
        val sharedPref = getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        with(sharedPref.edit()) {
            putString("activity_mode", mode.name)
            apply()
        }
    }

    private fun loadActivityMode() {
        val sharedPref = getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        val savedMode = sharedPref.getString("activity_mode", ActivityMode.TRAINING.name) ?: ActivityMode.TRAINING.name
        _activityMode.value = try {
            ActivityMode.valueOf(savedMode)
        } catch (e: IllegalArgumentException) {
            ActivityMode.TRAINING
        }
    }

    private fun saveCustomThresholds(top: Double, bottom: Double) {
        val sharedPref = getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        with(sharedPref.edit()) {
            putFloat("custom_top_threshold", top.toFloat())
            putFloat("custom_bottom_threshold", bottom.toFloat())
            apply()
        }
    }

    private fun loadCustomThresholds() {
        val sharedPref = getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        _customTopTemperatureThreshold.value = sharedPref.getFloat("custom_top_threshold", 38.5f).toDouble()
        _customBottomTemperatureThreshold.value = sharedPref.getFloat("custom_bottom_threshold", 36.5f).toDouble()
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        notificationsManager = NotificationsManager(applicationContext)

        temperatureLogger = TemperatureLogger(this)
        bluetoothManager = BluetoothManager(this)

        bluetoothManager.setOnConnectionStatusChanged { isConnected, signalStrength ->
            _isConnected.value = isConnected
            if (!isConnected) _batteryLevel.value = 0.0
            _connectionQuality.value = if (!isConnected) BluetoothConnectionQuality.DISCONNECTED
            else when {
                signalStrength > -60 -> BluetoothConnectionQuality.GOOD
                signalStrength > -80 -> BluetoothConnectionQuality.MEDIUM
                else -> BluetoothConnectionQuality.BAD
            }
        }

        bluetoothManager.setOnMonitoringStatusChanged { isMonitoring ->
            _isMonitoring.value = isMonitoring
            if (isMonitoring) {
                temperatureLogger.startNewSession()
                resetAverageTemperature()
            }
        }

        bluetoothManager.setOnPauseStatusChanged { isPaused -> _isPaused.value = isPaused }

        bluetoothManager.setOnDataReceived { tempSkin, tempOutside, tempCore, battery ->
            _temperatureSkin.value = tempSkin
            _temperatureOutside.value = tempOutside
            _temperatureCore.value = tempCore
            _batteryLevel.value = battery

            if (_isMonitoring.value && !_isPaused.value) {
                updateAverageTemperature(tempCore)
                temperatureLogger.logTemperature(tempCore)

                // Get thresholds (similar to how you do it for UI)
                val (lowThreshold, _, highThreshold) = getTemperatureColorRanges(
                    mode = _activityMode.value,
                    athleticLevel = _athleticLevel.value,
                    customTop = if (_activityMode.value == ActivityMode.CUSTOM) _customTopTemperatureThreshold.value else null,
                    customBottom = if (_activityMode.value == ActivityMode.CUSTOM) _customBottomTemperatureThreshold.value else null
                )

                if (tempCore > 0) { // Ensure valid temperature reading
                    if (tempCore > highThreshold) {
                        notificationsManager.showHighTemperatureNotification(tempCore, highThreshold)
                    } else if (tempCore < lowThreshold) {
                        notificationsManager.showLowTemperatureNotification(tempCore, lowThreshold)
                    } else {
                        // Optional: If temperature is back in normal range, reset timers
                        notificationsManager.resetTemperatureNotificationTimers()
                    }
                }
            }

            // Battery notifications (example for integer percentage)
            val batteryPercent = battery.toInt()
            if (batteryPercent == 10) {
                notificationsManager.showBatteryLowNotification(batteryPercent)
            } else if (batteryPercent == 5) {
                notificationsManager.showBatteryCriticalNotification(batteryPercent)
            }
        }

        loadThemePreference()
        loadActivityMode()
        loadUserAthleticLevel()
        loadCustomThresholds()

        setContent {
            InnerTempTheme(darkTheme = useDarkTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppContent(
                        isConnected = _isConnected,
                        connectionQuality = _connectionQuality,
                        temperatureCore = _temperatureCore,
                        temperatureSkin = _temperatureSkin,
                        temperatureOutside = _temperatureOutside,
                        batteryLevel = _batteryLevel,
                        isPaused = _isPaused,
                        isMonitoring = _isMonitoring,
                        activityMode = _activityMode,
                        athleticLevel = _athleticLevel,
                        customTopThreshold = _customTopTemperatureThreshold,
                        customBottomThreshold = _customBottomTemperatureThreshold,
                        onPauseToggle = {
                            bluetoothManager.togglePause()
                            Toast.makeText(this, if (_isPaused.value) "Monitoring paused" else "Monitoring resumed", Toast.LENGTH_SHORT).show()
                        },
                        onMonitoringToggle = {
                            bluetoothManager.toggleMonitoring()
                            Toast.makeText(this, if (_isMonitoring.value) "Monitoring started" else "Monitoring stopped", Toast.LENGTH_SHORT).show()
                        },
                        onGoToProfile = { startActivity(Intent(this, ProfileActivity::class.java)) },
                        onGoToHistory = { startActivity(Intent(this, HistoryActivity::class.java)) },
                        onActivityModeChange = { mode ->
                            _activityMode.value = mode
                            saveActivityMode(mode)
                        },
                        onCustomThresholdsChange = { top, bottom ->
                            _customTopTemperatureThreshold.value = top
                            _customBottomTemperatureThreshold.value = bottom
                            saveCustomThresholds(top, bottom)
                        }
                    )
                }
            }
        }

        checkPermissions()

        lifecycle.addObserver(LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val sharedPref = getSharedPreferences("app_settings", Context.MODE_PRIVATE)
                val currentTheme = sharedPref.getString("theme", "Light") ?: "Light"
                val shouldUseDarkTheme = currentTheme == "Dark"
                if (shouldUseDarkTheme != useDarkTheme) {
                    useDarkTheme = shouldUseDarkTheme
                    recreate()
                }
                loadUserAthleticLevel()
            }
        })
    }

    private fun loadThemePreference() {
        val sharedPref = getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        val savedTheme = sharedPref.getString("theme", "Light") ?: "Light"
        useDarkTheme = savedTheme == "Dark"
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    override fun onDestroy() {
        super.onDestroy()
        bluetoothManager.closeConnection()
    }

    @Composable
    fun AppContent(
        isConnected: State<Boolean>,
        connectionQuality: State<BluetoothConnectionQuality>,
        temperatureCore: State<Double>,
        temperatureSkin: State<Double>,
        temperatureOutside: State<Double>,
        batteryLevel: State<Double>,
        isPaused: State<Boolean>,
        isMonitoring: State<Boolean>,
        activityMode: State<ActivityMode>,
        athleticLevel: State<String>,
        customTopThreshold: State<Double>,
        customBottomThreshold: State<Double>,
        onPauseToggle: () -> Unit,
        onMonitoringToggle: () -> Unit,
        onGoToProfile: () -> Unit,
        onGoToHistory: () -> Unit,
        onActivityModeChange: (ActivityMode) -> Unit,
        onCustomThresholdsChange: (top: Double, bottom: Double) -> Unit
    ) {
        HomeScreen(
            isConnected = isConnected.value,
            connectionQuality = connectionQuality.value,
            temperatureOutside = temperatureOutside.value,
            temperatureSkin = temperatureSkin.value,
            temperatureCore = temperatureCore.value,
            batteryLevel = batteryLevel.value,
            isPaused = isPaused.value,
            isMonitoring = isMonitoring.value,
            activityMode = activityMode.value,
            athleticLevel = athleticLevel.value,
            customTopThreshold = customTopThreshold.value,
            customBottomThreshold = customBottomThreshold.value,
            onPauseToggle = onPauseToggle,
            onMonitoringToggle = onMonitoringToggle,
            onGoToProfile = onGoToProfile,
            onGoToHistory = onGoToHistory,
            onActivityModeChange = onActivityModeChange,
            onCustomThresholdsChange = onCustomThresholdsChange
        )
    }
}

fun getTemperatureColorRanges(
    mode: ActivityMode,
    athleticLevel: String,
    customTop: Double? = null,
    customBottom: Double? = null
): Triple<Double, Double, Double> {
    var lowThreshold = 36.0
    var highThreshold = 38.0

    if (mode == ActivityMode.CUSTOM && customBottom != null && customTop != null) {
        lowThreshold = customBottom
        highThreshold = customTop
    } else {
        when (athleticLevel) {
            "Low" -> {
                if (mode == ActivityMode.RACE) {
                    lowThreshold = 37.0
                    highThreshold = 38.4
                } else {
                    lowThreshold = 37.0
                    highThreshold = 38.2
                }
            }
            "Medium" -> {
                if (mode == ActivityMode.RACE) {
                    lowThreshold = 37.2
                    highThreshold = 39.0
                } else {
                    lowThreshold = 37.2
                    highThreshold = 38.7
                }
            }
            "High" -> {
                if (mode == ActivityMode.RACE) {
                    lowThreshold = 37.5
                    highThreshold = 39.4
                } else {
                    lowThreshold = 37.5
                    highThreshold = 39.0
                }
            }
            else -> {
                if (mode == ActivityMode.RACE) {
                    lowThreshold = 37.2
                    highThreshold = 39.0
                } else {
                    lowThreshold = 37.2
                    highThreshold = 38.7
                }
            }
        }
    }
    val optimalTemp = (lowThreshold + highThreshold) / 2
    return Triple(lowThreshold, optimalTemp, highThreshold)
}


@Composable
fun CustomThresholdDialog(
    currentTopThreshold: Double,
    currentBottomThreshold: Double,
    onDismissRequest: () -> Unit,
    onSave: (top: Double, bottom: Double) -> Unit
) {
    var tempTopThreshold by remember { mutableStateOf(currentTopThreshold.toFloat()) }
    var tempBottomThreshold by remember { mutableStateOf(currentBottomThreshold.toFloat()) }

    LaunchedEffect(tempTopThreshold) { if (tempBottomThreshold > tempTopThreshold) tempBottomThreshold = tempTopThreshold }
    LaunchedEffect(tempBottomThreshold) { if (tempTopThreshold < tempBottomThreshold) tempTopThreshold = tempBottomThreshold }

    Dialog(onDismissRequest = onDismissRequest) {
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp).fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Set Custom Thresholds", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(bottom = 16.dp))
                Text("Top Temperature Threshold: ${String.format("%.1f", tempTopThreshold)}°C")
                Slider(value = tempTopThreshold, onValueChange = { tempTopThreshold = String.format("%.1f", it).toFloat() }, valueRange = 35.0f..42.0f, steps = ((42.0f - 35.0f) * 10).toInt() - 1)
                Spacer(modifier = Modifier.height(16.dp))
                Text("Bottom Temperature Threshold: ${String.format("%.1f", tempBottomThreshold)}°C")
                Slider(value = tempBottomThreshold, onValueChange = { tempBottomThreshold = String.format("%.1f", it).toFloat() }, valueRange = 35.0f..42.0f, steps = ((42.0f - 35.0f) * 10).toInt() - 1)
                Spacer(modifier = Modifier.height(24.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismissRequest) { Text("Cancel") }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(onClick = {
                        val finalBottom = if (tempBottomThreshold > tempTopThreshold) tempTopThreshold else tempBottomThreshold
                        onSave(tempTopThreshold.toDouble(), finalBottom.toDouble())
                        onDismissRequest()
                    }) { Text("Save") }
                }
            }
        }
    }
}


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
    athleticLevel: String,
    customTopThreshold: Double,
    customBottomThreshold: Double,
    onPauseToggle: () -> Unit,
    onMonitoringToggle: () -> Unit,
    onGoToProfile: () -> Unit,
    onGoToHistory: () -> Unit,
    onActivityModeChange: (ActivityMode) -> Unit,
    onCustomThresholdsChange: (top: Double, bottom: Double) -> Unit
) {
    var showDevMenu by remember { mutableStateOf(false) }
    var homeTitleClickCount by remember { mutableIntStateOf(0) }
    var mockIsConnected by remember { mutableStateOf(isConnected) }
    var mockTemperatureCore by remember { mutableDoubleStateOf(temperatureCore) }
    var mockBatteryLevel by remember { mutableDoubleStateOf(batteryLevel) }
    var showModeMenu by remember { mutableStateOf(false) }
    var showCustomSettingsDialog by remember { mutableStateOf(false) }

    LaunchedEffect(isConnected, temperatureCore, batteryLevel) {
        if (!showDevMenu) {
            mockIsConnected = isConnected
            mockTemperatureCore = temperatureCore
            mockBatteryLevel = batteryLevel
        }
    }
    val effectiveIsConnected = mockIsConnected
    val effectiveTemperatureCore = mockTemperatureCore
    val effectiveBatteryLevel = mockBatteryLevel
    val colors = MaterialTheme.colorScheme

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
        temperatureOutside < lowThreshold -> Blue
        temperatureOutside > highThreshold -> Red
        else -> Green
    }
    val tempColorCore = when {
        effectiveTemperatureCore < lowThreshold -> Blue
        effectiveTemperatureCore > highThreshold -> Red
        else -> Green
    }

    val currentDateStr = remember { SimpleDateFormat("dd MMMM yyyy", Locale.getDefault()).format(Date()) }

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
                title = { Text("Home", modifier = Modifier.fillMaxWidth().clickable { homeTitleClickCount++; if (homeTitleClickCount >= 5) { showDevMenu = true; homeTitleClickCount = 0 } }, textAlign = TextAlign.Center) },
                navigationIcon = { Row(verticalAlignment = Alignment.CenterVertically) { IconButton(onClick = onGoToHistory) { Icon(Icons.Default.History, "History") }; BluetoothStatusInTopBar(connectionQuality, Modifier.align(Alignment.CenterVertically)) } },
                actions = { BatteryLevelIcon(effectiveBatteryLevel, effectiveIsConnected); IconButton(onClick = onGoToProfile) { Icon(Icons.Default.Person, "Profile") } }
            )
        }
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(currentDateStr, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                    BluetoothConnectivityIcon(connectionQuality, Modifier.size(24.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = when { !effectiveIsConnected -> "Disconnected"; connectionQuality == BluetoothConnectionQuality.GOOD -> "Connected (Good signal)"; connectionQuality == BluetoothConnectionQuality.MEDIUM -> "Connected (Medium signal)"; else -> "Connected (Weak signal)" },
                        color = if (!effectiveIsConnected) colors.error else colors.onSurface
                    )
                }
                Spacer(Modifier.height(80.dp)) // Reduced Spacer, was 80.dp
                Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Core Temperature", fontWeight = FontWeight.Bold, fontSize = 20.sp, modifier = Modifier.padding(bottom = 8.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(String.format("%.1f°C", effectiveTemperatureCore), fontSize = 32.sp, color = tempColorCore, fontWeight = FontWeight.Bold)
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp).alpha(0.8f)) {
                                Text(String.format("%.1f°C", lowThreshold), color = Blue, fontSize = 12.sp)
                                Text(" - ", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                                Text(String.format("%.1f°C", highThreshold), color = Red, fontSize = 12.sp)
                            }
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    Text("Skin Temperature", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text(String.format("%.1f°C", temperatureSkin), fontSize = 24.sp, color = tempColorSkin, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(16.dp))
                    Text("Outside Temperature", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text(String.format("%.1f°C", temperatureOutside), fontSize = 24.sp, color = tempColorOutside, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(32.dp)) // Was 32.dp
                Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Monitoring Status", fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.padding(bottom = 12.dp))
                    Text(
                        text = if (isMonitoring) if (isPaused) "Paused" else "Active" else "Inactive",
                        fontSize = 16.sp, fontWeight = FontWeight.Medium,
                        color = when { !isMonitoring -> colors.error; isPaused -> colors.tertiary; else -> Green },
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    // Activity Mode selection Button
                    Box { // Needed to anchor the DropdownMenu correctly
                        val buttonTargetWidth = 220.dp // Adjusted for potentially longer text or just preference
                        Button(
                            onClick = { if (!isMonitoring) showModeMenu = true },
                            modifier = Modifier.width(buttonTargetWidth),
                            enabled = !isMonitoring,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isMonitoring) colors.secondary else colors.primary,
                                disabledContainerColor = colors.secondary.copy(alpha = 0.7f)
                            ),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = when (activityMode) {
                                        ActivityMode.RACE -> Icons.Outlined.EmojiEvents
                                        ActivityMode.TRAINING -> Icons.Outlined.DirectionsRun
                                        ActivityMode.CUSTOM -> Icons.Outlined.Tune
                                    },
                                    contentDescription = "Activity Mode Icon",
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Mode: ${
                                        when (activityMode) {
                                            ActivityMode.RACE -> "Race"
                                            ActivityMode.TRAINING -> "Training"
                                            ActivityMode.CUSTOM -> "Custom"
                                        }
                                    }"
                                )
                            }
                        }
                        DropdownMenu(
                            expanded = showModeMenu && !isMonitoring,
                            onDismissRequest = { showModeMenu = false },
                            modifier = Modifier.width(buttonTargetWidth)
                        ) {
                            DropdownMenuItem(
                                text = { Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Outlined.DirectionsRun, "Training Icon", Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)); Text("Training") } },
                                onClick = { onActivityModeChange(ActivityMode.TRAINING); showModeMenu = false },
                                leadingIcon = { if (activityMode == ActivityMode.TRAINING) Icon(Icons.Default.Check, "Selected", tint = MaterialTheme.colorScheme.primary) else Box(Modifier.size(24.dp)) }
                            )
                            DropdownMenuItem(
                                text = { Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Outlined.EmojiEvents, "Race Icon", Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)); Text("Race") } },
                                onClick = { onActivityModeChange(ActivityMode.RACE); showModeMenu = false },
                                leadingIcon = { if (activityMode == ActivityMode.RACE) Icon(Icons.Default.Check, "Selected", tint = MaterialTheme.colorScheme.primary) else Box(Modifier.size(24.dp)) }
                            )
                            DropdownMenuItem(
                                text = { Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Outlined.Tune, "Custom Icon", Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)); Text("Custom") } },
                                onClick = { onActivityModeChange(ActivityMode.CUSTOM); showModeMenu = false },
                                leadingIcon = { if (activityMode == ActivityMode.CUSTOM) Icon(Icons.Default.Check, "Selected", tint = MaterialTheme.colorScheme.primary) else Box(Modifier.size(24.dp)) }
                            )
                        }
                    }

                    // Conditionally visible "Edit Custom Mode Settings" Button
                    if (activityMode == ActivityMode.CUSTOM && !isMonitoring) {
                        Spacer(modifier = Modifier.height(8.dp)) // Space above the edit button
                        Button(
                            onClick = { showCustomSettingsDialog = true },
                            modifier = Modifier.width(220.dp), // Match the activity mode button width or adjust as needed
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer, // Example color
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        ) {
                            Text("Edit Custom Mode Settings")
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp)) // Space before Start/Stop/Pause buttons
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = onPauseToggle, enabled = effectiveIsConnected && isMonitoring, colors = ButtonDefaults.buttonColors(containerColor = if (isPaused) Green else colors.tertiary)) { Text(if (isPaused) "Resume" else "Pause") }
                        Button(onClick = onMonitoringToggle, enabled = effectiveIsConnected, colors = ButtonDefaults.buttonColors(containerColor = if (isMonitoring) Red else Green)) { Text(if (isMonitoring) "Stop" else "Start") }
                    }
                }
                Spacer(modifier = Modifier.height(32.dp))
                Text(
                    text = when { !effectiveIsConnected -> "Please connect a temperature sensor"; !isMonitoring -> "Press Start to begin monitoring"; isPaused -> "Monitoring is paused"; else -> "Monitoring in progress" },
                    color = when { !effectiveIsConnected -> colors.error; !isMonitoring -> colors.onSurfaceVariant; isPaused -> colors.tertiary; else -> Green },
                    fontSize = 14.sp, textAlign = TextAlign.Center
                )
            }
            if (showDevMenu) {
                DeveloperMenu(
                    isConnected = mockIsConnected, onIsConnectedChange = { mockIsConnected = it },
                    temperatureCore = mockTemperatureCore, onTemperatureCoreChange = { mockTemperatureCore = it },
                    batteryLevel = mockBatteryLevel, onBatteryLevelChange = { mockBatteryLevel = it },
                    onClose = { showDevMenu = false }
                )
            }
        }
    }
}

@SuppressLint("DefaultLocale")
@Composable
fun DeveloperMenu(
    isConnected: Boolean, onIsConnectedChange: (Boolean) -> Unit,
    temperatureCore: Double, onTemperatureCoreChange: (Double) -> Unit,
    batteryLevel: Double, onBatteryLevelChange: (Double) -> Unit,
    onClose: () -> Unit
) {
    val backgroundColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
    Box(modifier = Modifier.fillMaxSize().background(backgroundColor).padding(16.dp)) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Developer Menu", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                IconButton(onClick = onClose, modifier = Modifier.size(48.dp)) { Icon(Icons.Default.Close, "Close Developer Menu", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(32.dp)) }
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Device Status:", modifier = Modifier.weight(1f))
                Switch(checked = isConnected, onCheckedChange = onIsConnectedChange)
                Text(if (isConnected) "Connected" else "Disconnected", modifier = Modifier.padding(start = 8.dp))
            }
            Spacer(Modifier.height(16.dp))
            Text("Core Temperature: ${String.format("%.1f°C", temperatureCore)}", modifier = Modifier.padding(bottom = 4.dp))
            Slider(value = temperatureCore.toFloat(), onValueChange = { onTemperatureCoreChange(it.toDouble()) }, valueRange = 35f..40f, steps = ( (40f-35f) / 0.1f ).toInt() - 1 )
            Spacer(Modifier.height(16.dp))
            Text("Battery Level: ${String.format("%.0f%%", batteryLevel)}", modifier = Modifier.padding(bottom = 4.dp))
            Slider(value = batteryLevel.toFloat(), onValueChange = { onBatteryLevelChange(it.toDouble()) }, valueRange = 0f..100f, steps = 100 -1 ) // 100 steps means 99 divisions
            Spacer(Modifier.height(32.dp))
            Button(onClick = onClose, modifier = Modifier.fillMaxWidth().height(56.dp), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) {
                Text("CLOSE DEV MENU", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = MaterialTheme.colorScheme.onError)
            }
        }
    }
}

@Preview(showBackground = true, name = "Home Screen Custom Mode (Not Monitoring)")
@Composable
fun HomeScreenCustomModePreview() {
    InnerTempTheme {
        HomeScreen(
            isConnected = true, connectionQuality = BluetoothConnectionQuality.GOOD,
            temperatureCore = 37.2, temperatureSkin = 36.8, temperatureOutside = 35.5,
            batteryLevel = 78.0, isPaused = false, isMonitoring = false, // Not monitoring to show edit button
            activityMode = ActivityMode.CUSTOM, athleticLevel = "Medium",
            customTopThreshold = 39.0, customBottomThreshold = 36.0,
            onPauseToggle = {}, onMonitoringToggle = {}, onGoToProfile = {}, onGoToHistory = {},
            onActivityModeChange = {}, onCustomThresholdsChange = { _, _ -> }
        )
    }
}

@Preview(showBackground = true, name = "Home Screen Training Mode (Monitoring)")
@Composable
fun HomeScreenTrainingModePreview() {
    InnerTempTheme {
        HomeScreen(
            isConnected = true, connectionQuality = BluetoothConnectionQuality.GOOD,
            temperatureCore = 37.2, temperatureSkin = 36.8, temperatureOutside = 35.5,
            batteryLevel = 78.0, isPaused = false, isMonitoring = true,
            activityMode = ActivityMode.TRAINING, athleticLevel = "Medium",
            customTopThreshold = 39.0, customBottomThreshold = 36.0,
            onPauseToggle = {}, onMonitoringToggle = {}, onGoToProfile = {}, onGoToHistory = {},
            onActivityModeChange = {}, onCustomThresholdsChange = { _, _ -> }
        )
    }
}

@Preview(showBackground = true)
@Composable
fun CustomThresholdDialogPreview() {
    InnerTempTheme {
        CustomThresholdDialog(
            currentTopThreshold = 38.5, currentBottomThreshold = 36.5,
            onDismissRequest = {}, onSave = { _, _ -> }
        )
    }
}