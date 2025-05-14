
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.DirectionsRun
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.ui.draw.alpha

enum class ActivityMode {
    TRAINING,
    RACE
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
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
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
        _activityMode.value = ActivityMode.valueOf(savedMode)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        temperatureLogger = TemperatureLogger(this)
        bluetoothManager = BluetoothManager(this)

        bluetoothManager.setOnConnectionStatusChanged { isConnected, signalStrength ->
            _isConnected.value = isConnected

            if (!isConnected) {
                _batteryLevel.value = 0.0
            }

            _connectionQuality.value = if (!isConnected) {
                BluetoothConnectionQuality.DISCONNECTED
            } else {
                when {
                    signalStrength > -60 -> BluetoothConnectionQuality.GOOD
                    signalStrength > -80 -> BluetoothConnectionQuality.MEDIUM
                    else -> BluetoothConnectionQuality.BAD
                }
            }
        }

        bluetoothManager.setOnMonitoringStatusChanged { isMonitoring ->
            _isMonitoring.value = isMonitoring
            if (isMonitoring) {
                temperatureLogger.startNewSession()
                resetAverageTemperature()
            }
        }

        bluetoothManager.setOnPauseStatusChanged { isPaused ->
            _isPaused.value = isPaused
        }

        bluetoothManager.setOnDataReceived { tempSkin, tempOutside, tempCore, battery ->
            _temperatureSkin.value = tempSkin
            _temperatureOutside.value = tempOutside
            _temperatureCore.value = tempCore
            _batteryLevel.value = battery

            if (_isMonitoring.value && !_isPaused.value && tempCore > 0) {
                updateAverageTemperature(tempCore)
                temperatureLogger.logTemperature(tempCore)
            }
        }

        loadThemePreference()
        loadActivityMode()
        loadUserAthleticLevel()

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
                        onPauseToggle = {
                            bluetoothManager.togglePause()
                            Toast.makeText(
                                this,
                                if (_isPaused.value) "Monitoring paused" else "Monitoring resumed",
                                Toast.LENGTH_SHORT
                            ).show()
                        },
                        onMonitoringToggle = {
                            bluetoothManager.toggleMonitoring()
                            Toast.makeText(
                                this,
                                if (_isMonitoring.value) "Monitoring started" else "Monitoring stopped",
                                Toast.LENGTH_SHORT
                            ).show()
                        },
                        onGoToProfile = {
                            val intent = Intent(this, ProfileActivity::class.java)
                            startActivity(intent)
                        },
                        onGoToHistory = {
                            val intent = Intent(this, HistoryActivity::class.java)
                            startActivity(intent)
                        },
                        onActivityModeChange = { mode ->
                            _activityMode.value = mode
                            saveActivityMode(mode)
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

                // Reload athletic level when resuming the activity
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
        onPauseToggle: () -> Unit,
        onMonitoringToggle: () -> Unit,
        onGoToProfile: () -> Unit,
        onGoToHistory: () -> Unit,
        onActivityModeChange: (ActivityMode) -> Unit
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
            onPauseToggle = onPauseToggle,
            onMonitoringToggle = onMonitoringToggle,
            onGoToProfile = onGoToProfile,
            onGoToHistory = onGoToHistory,
            onActivityModeChange = onActivityModeChange
        )
    }
}

// Utility function to determine temperature color ranges based on mode and athletic level
fun getTemperatureColorRanges(mode: ActivityMode, athleticLevel: String): Triple<Double, Double, Double> {
    // Default values
    var lowThreshold = 36.0
    var highThreshold = 38.0

    // Adjust based on athletic level
    when (athleticLevel) {
        "Low" -> {
            lowThreshold = 36.2
            highThreshold = 37.8
        }
        "Medium" -> {
            lowThreshold = 36.0
            highThreshold = 38.0
        }
        "High" -> {
            lowThreshold = 35.8
            highThreshold = 38.2
        }
    }

    // Further adjust based on activity mode
    when (mode) {
        ActivityMode.TRAINING -> {
            // Keep default thresholds for training
        }
        ActivityMode.RACE -> {
            // For race mode, widen the acceptable range (green zone)
            lowThreshold -= 0.3
            highThreshold += 0.3
        }
    }

    // Calculate middle (optimal) temperature
    val optimalTemp = (lowThreshold + highThreshold) / 2

    return Triple(lowThreshold, optimalTemp, highThreshold)
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
    onPauseToggle: () -> Unit,
    onMonitoringToggle: () -> Unit,
    onGoToProfile: () -> Unit,
    onGoToHistory: () -> Unit,
    onActivityModeChange: (ActivityMode) -> Unit
) {
    var showDevMenu by remember { mutableStateOf(false) }
    var homeTitleClickCount by remember { mutableIntStateOf(0) }
    var mockIsConnected by remember { mutableStateOf(isConnected) }
    var mockTemperatureCore by remember { mutableDoubleStateOf(temperatureCore) }
    var mockBatteryLevel by remember { mutableDoubleStateOf(batteryLevel) }
    var showModeMenu by remember { mutableStateOf(false) }

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

    // Get temperature thresholds based on mode and athletic level
    val (lowThreshold, optimalTemp, highThreshold) = getTemperatureColorRanges(activityMode, athleticLevel)

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

    val currentDateStr = remember {
        val dateFormat = SimpleDateFormat("dd MMMM yyyy", Locale.getDefault())
        dateFormat.format(Date())
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Home",
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                homeTitleClickCount++
                                if (homeTitleClickCount >= 5) {
                                    showDevMenu = true
                                    homeTitleClickCount = 0
                                }
                            },
                        textAlign = TextAlign.Center
                    )
                },
                navigationIcon = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onGoToHistory) {
                            Icon(Icons.Default.History, contentDescription = "History")
                        }
                        BluetoothStatusInTopBar(
                            connectionQuality = connectionQuality,
                            modifier = Modifier.align(Alignment.CenterVertically)
                        )
                    }
                },
                actions = {
                    BatteryLevelIcon(
                        batteryLevel = effectiveBatteryLevel,
                        isConnected = effectiveIsConnected,
                    )
                    IconButton(onClick = onGoToProfile) {
                        Icon(Icons.Default.Person, contentDescription = "Profile")
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = currentDateStr,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                ) {
                    BluetoothConnectivityIcon(
                        connectionQuality = connectionQuality,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = when {
                            !effectiveIsConnected -> "Disconnected"
                            connectionQuality == BluetoothConnectionQuality.GOOD -> "Connected (Good signal)"
                            connectionQuality == BluetoothConnectionQuality.MEDIUM -> "Connected (Medium signal)"
                            else -> "Connected (Weak signal)"
                        },
                        color = when {
                            !effectiveIsConnected -> colors.error
                            else -> colors.onSurface
                        }
                    )
                }

                Spacer(modifier = Modifier.height(80.dp))
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Core Temperature",
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = String.format("%.1f°C", effectiveTemperatureCore),
                                fontSize = 32.sp,
                                color = tempColorCore,
                                fontWeight = FontWeight.Bold
                            )

                            // Temperature range indicator
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .padding(top = 4.dp)
                                    .alpha(0.8f)
                            ) {
                                Text(
                                    text = String.format("%.1f°C", lowThreshold),
                                    color = Blue,
                                    fontSize = 12.sp
                                )
                                Text(
                                    text = " - ",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 12.sp
                                )
                                Text(
                                    text = String.format("%.1f°C", highThreshold),
                                    color = Red,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Skin Temperature",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    Text(
                        text = String.format("%.1f°C", temperatureSkin),
                        fontSize = 24.sp,
                        color = tempColorSkin,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Outside Temperature",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    Text(
                        text = String.format("%.1f°C", temperatureOutside),
                        fontSize = 24.sp,
                        color = tempColorOutside,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(32.dp))
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Monitoring Status",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    Text(
                        text = if (isMonitoring) {
                            if (isPaused) "Paused" else "Active"
                        } else "Inactive",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = when {
                            !isMonitoring -> colors.error
                            isPaused -> colors.tertiary
                            else -> Green
                        },
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    // Activity Mode selection - moved here between monitoring status and control buttons
                    Box {
                        val buttonWidth = 240.dp
                        Button(
                            onClick = { showModeMenu = true },
                            modifier = Modifier.width(buttonWidth)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = if (activityMode == ActivityMode.RACE)
                                        Icons.Outlined.EmojiEvents
                                    else
                                        Icons.Outlined.DirectionsRun,
                                    contentDescription = "Activity Mode",
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Activity Mode: ${if (activityMode == ActivityMode.RACE) "Race" else "Training"}"
                                )
                            }
                        }

                        DropdownMenu(
                            expanded = showModeMenu,
                            onDismissRequest = { showModeMenu = false },
                            modifier = Modifier.width(buttonWidth)
                        ) {
                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Outlined.DirectionsRun,
                                            contentDescription = "Training Mode",
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Training")
                                    }
                                },
                                onClick = {
                                    onActivityModeChange(ActivityMode.TRAINING)
                                    showModeMenu = false
                                },
                                leadingIcon = {
                                    if (activityMode == ActivityMode.TRAINING) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = "Selected",
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    } else {
                                        Box(modifier = Modifier.size(24.dp))
                                    }
                                }
                            )

                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Outlined.EmojiEvents,
                                            contentDescription = "Race Mode",
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Race")
                                    }
                                },
                                onClick = {
                                    onActivityModeChange(ActivityMode.RACE)
                                    showModeMenu = false
                                },
                                leadingIcon = {
                                    if (activityMode == ActivityMode.RACE) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = "Selected",
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    } else {
                                        Box(modifier = Modifier.size(24.dp))
                                    }
                                }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = onPauseToggle,
                            enabled = effectiveIsConnected && isMonitoring,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isPaused) Green else colors.tertiary
                            )
                        ) {
                            Text(text = if (isPaused) "Resume" else "Pause")
                        }
                        Button(
                            onClick = onMonitoringToggle,
                            enabled = effectiveIsConnected,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isMonitoring) Red else Green
                            )
                        ) {
                            Text(text = if (isMonitoring) "Stop" else "Start")
                        }
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))
                Text(
                    text = when {
                        !effectiveIsConnected -> "Please connect a temperature sensor"
                        !isMonitoring -> "Press Start to begin monitoring"
                        isPaused -> "Monitoring is paused"
                        else -> "Monitoring in progress"
                    },
                    color = when {
                        !effectiveIsConnected -> colors.error
                        !isMonitoring -> colors.onSurfaceVariant
                        isPaused -> colors.tertiary
                        else -> Green
                    },
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center
                )
            }
            if (showDevMenu) {
                DeveloperMenu(
                    isConnected = mockIsConnected,
                    onIsConnectedChange = { mockIsConnected = it },
                    temperatureCore = mockTemperatureCore,
                    onTemperatureCoreChange = { mockTemperatureCore = it },
                    batteryLevel = mockBatteryLevel,
                    onBatteryLevelChange = { mockBatteryLevel = it },
                    onClose = { showDevMenu = false }
                )
            }
        }
    }
}

@SuppressLint("DefaultLocale")
@Composable
fun DeveloperMenu(
    isConnected: Boolean,
    onIsConnectedChange: (Boolean) -> Unit,
    temperatureCore: Double,
    onTemperatureCoreChange: (Double) -> Unit,
    batteryLevel: Double,
    onBatteryLevelChange: (Double) -> Unit,
    onClose: () -> Unit
) {
    val backgroundColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor)
            .padding(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Developer Menu",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                IconButton(
                    onClick = onClose,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close Developer Menu",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Device Status:",
                    modifier = Modifier.weight(1f)
                )
                Switch(
                    checked = isConnected,
                    onCheckedChange = onIsConnectedChange
                )
                Text(
                    text = if (isConnected) "Connected" else "Disconnected",
                    modifier = Modifier.padding(start = 8.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Core Temperature: ${String.format("%.1f°C", temperatureCore)}",
                modifier = Modifier.padding(bottom = 4.dp)
            )
            Slider(
                value = temperatureCore.toFloat(),
                onValueChange = { onTemperatureCoreChange(it.toDouble()) },
                valueRange = 35f..40f,
                steps = 50
            )

            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Battery Level: ${String.format("%.0f%%", batteryLevel)}",
                modifier = Modifier.padding(bottom = 4.dp)
            )
            Slider(
                value = batteryLevel.toFloat(),
                onValueChange = { onBatteryLevelChange(it.toDouble()) },
                valueRange = 0f..100f,
                steps = 100
            )

            Spacer(modifier = Modifier.height(32.dp))
            Button(
                onClick = onClose,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text(
                    text = "CLOSE DEV MENU",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.onError
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun HomeScreenPreview() {
    InnerTempTheme {
        HomeScreen(
            isConnected = true,
            connectionQuality = BluetoothConnectionQuality.GOOD,
            temperatureCore = 37.2,
            temperatureSkin = 36.8,
            temperatureOutside = 35.5,
            batteryLevel = 78.0,
            isPaused = false,
            isMonitoring = true,
            activityMode = ActivityMode.TRAINING,
            athleticLevel = "Medium",
            onPauseToggle = { },
            onMonitoringToggle = { },
            onGoToProfile = { },
            onGoToHistory = { },
            onActivityModeChange = { }
        )
    }
}