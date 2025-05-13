package com.example.innertemp

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresPermission
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.lifecycle.Lifecycle
import com.example.innertemp.ui.theme.Blue
import com.example.innertemp.ui.theme.Green
import com.example.innertemp.ui.theme.InnerTempTheme
import com.example.innertemp.ui.theme.Red
import java.text.SimpleDateFormat
import java.util.*
import androidx.lifecycle.LifecycleEventObserver

private const val TAG = "InnerTemp"

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
    private val _themeMode = mutableStateOf(ThemeMode.SYSTEM)
    private var useDarkTheme by mutableStateOf(false)

    // Core temperature average tracking
    private val _averageTemperatureCore = mutableStateOf(0.0)
    private var tempCoreSamples = mutableListOf<Double>()
    private var totalSamples = 0

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
            Log.d(TAG, "Starting BLE scan...")
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

    /**
     * Updates the running average of core temperature
     * Uses a cumulative moving average approach
     */
    private fun updateAverageTemperature(newValue: Double) {
        if (newValue <= 0) return  // Skip invalid readings

        tempCoreSamples.add(newValue)
        totalSamples++

        // Calculate running average
        val sum = tempCoreSamples.sum()
        _averageTemperatureCore.value = String.format("%.1f", sum / totalSamples).toDouble()

        // Optionally, limit the number of samples stored to prevent excessive memory usage
        if (tempCoreSamples.size > 100) {  // Keep only the most recent 100 samples
            tempCoreSamples.removeAt(0)
        }

        Log.d(TAG, "Average core temp: ${_averageTemperatureCore.value}°C (from $totalSamples samples)")
    }

    /**
     * Resets the temperature average calculation
     */
    private fun resetAverageTemperature() {
        tempCoreSamples.clear()
        totalSamples = 0
        _averageTemperatureCore.value = 0.0
        Log.d(TAG, "Average temperature tracking reset")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Initialize the temperature logger
        temperatureLogger = TemperatureLogger(this)

        // Initialize the Bluetooth manager
        bluetoothManager = BluetoothManager(this)

        // Set up callbacks with additional logging
        bluetoothManager.setOnConnectionStatusChanged { isConnected, signalStrength ->
            Log.d(TAG, "Connection status changed: $isConnected, Signal strength: $signalStrength")
            _isConnected.value = isConnected

            // Update battery level to 0 if disconnected
            if (!isConnected) {
                _batteryLevel.value = 0.0
            }

            // Update connection quality based on signal strength
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
            Log.d(TAG, "Monitoring status changed: $isMonitoring")
            _isMonitoring.value = isMonitoring
            if (isMonitoring) {
                // Start a new logging session when monitoring begins
                temperatureLogger.startNewSession()
                resetAverageTemperature()
            }
        }

        bluetoothManager.setOnPauseStatusChanged { isPaused ->
            Log.d(TAG, "Pause status changed: $isPaused")
            _isPaused.value = isPaused
        }

        bluetoothManager.setOnDataReceived { tempSkin, tempOutside, tempCore, battery ->
            Log.d(TAG, "Received data - Core: $tempCore, Skin: $tempSkin, Outside: $tempOutside, Battery: $battery")

            _temperatureSkin.value = tempSkin
            _temperatureOutside.value = tempOutside
            _temperatureCore.value = tempCore
            _batteryLevel.value = battery

            if (_isConnected.value && battery > 0) {
                Log.d(TAG, "Battery level updated: $battery")
            }

            // Update running average for core temperature if we're monitoring and not paused
            if (_isMonitoring.value && !_isPaused.value && tempCore > 0) {
                updateAverageTemperature(tempCore)

                // Log the temperature to the file system
                temperatureLogger.logTemperature(tempCore)
            }
        }

        setContent {
            val themeMode by _themeMode
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
                        themeMode = _themeMode,
                        onThemeModeChange = { newMode ->
                            _themeMode.value = newMode
                            // Optional: Save theme preference
                            saveThemePreference(newMode)
                        },
                        onPauseToggle = {
                            Log.d(TAG, "User requested pause toggle")
                            bluetoothManager.togglePause()
                            Toast.makeText(
                                this,
                                if (_isPaused.value) "Monitoring paused" else "Monitoring resumed",
                                Toast.LENGTH_SHORT
                            ).show()
                        },
                        onMonitoringToggle = {
                            Log.d(TAG, "User requested monitoring toggle")
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
                            overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
                        },
                        onGoToHistory = {
                            val intent = Intent(this, HistoryActivity::class.java)
                            startActivity(intent)
                            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
                        },
                    )
                }
            }
        }
        checkPermissions()
        lifecycle.addObserver(LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val currentTheme = getSharedPreferences("app_settings", Context.MODE_PRIVATE)
                    .getString("theme", "Light") ?: "Light"
                val shouldUseDarkTheme = currentTheme == "Dark"

                if (shouldUseDarkTheme != useDarkTheme) {
                    useDarkTheme = shouldUseDarkTheme
                    onThemeChanged()
                }
            }
        })


        // Load saved theme preference
        loadThemePreference()
    }

    private fun saveThemePreference(mode: ThemeMode) {
        val prefs = getPreferences(Context.MODE_PRIVATE)
        prefs.edit().putString("theme_mode", mode.name).apply()
    }

    private fun loadThemePreference() {
        val prefs = getPreferences(Context.MODE_PRIVATE)
        val savedMode = prefs.getString("theme_mode", ThemeMode.SYSTEM.name)
        _themeMode.value = ThemeMode.valueOf(savedMode ?: ThemeMode.SYSTEM.name)
        val sharedPref = getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        val savedTheme = sharedPref.getString("theme", "Light") ?: "Light"
        useDarkTheme = savedTheme == "Dark"
        Log.d(TAG, "Loaded theme preference: $savedTheme")
    }

    private fun onThemeChanged() {
        Log.d(TAG, "Theme changed, recreating activity")
        finish()
        startActivity(intent)
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
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
        onPauseToggle: () -> Unit,
        onMonitoringToggle: () -> Unit,
        onGoToProfile: () -> Unit,
        onGoToHistory: () -> Unit,
        themeMode: MutableState<ThemeMode>,
        onThemeModeChange: (ThemeMode) -> Unit,
    ) {
        HomeScreen(
            isConnected = isConnected.value,
            connectionQuality = connectionQuality.value,
            temperatureOutside = temperatureOutside.value,
            temperatureSkin = temperatureSkin.value,
            temperatureCore = temperatureCore.value,
            averageTemperatureCore = _averageTemperatureCore.value,
            batteryLevel = batteryLevel.value,
            isPaused = isPaused.value,
            isMonitoring = isMonitoring.value,
            onPauseToggle = onPauseToggle,
            onMonitoringToggle = onMonitoringToggle,
            onGoToProfile = onGoToProfile,
            onGoToHistory = onGoToHistory,
            themeMode = themeMode.value,
            onThemeModeChange = onThemeModeChange,
        )
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
    averageTemperatureCore: Double,
    batteryLevel: Double,
    isPaused: Boolean,
    isMonitoring: Boolean,
    onPauseToggle: () -> Unit,
    onMonitoringToggle: () -> Unit,
    onGoToProfile: () -> Unit,
    onGoToHistory: () -> Unit,
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val tempColorSkin = when {
        temperatureSkin < 36.0 -> Blue
        temperatureSkin > 38.0 -> Red
        else -> Green
    }

    val tempColorOutside = when {
        temperatureOutside < 36.0 -> Blue
        temperatureOutside > 38.0 -> Red
        else -> Green
    }
    val tempColorCore = when {
        temperatureCore < 36.0 -> Blue
        temperatureCore > 38.0 -> Red
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
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                },
                navigationIcon = {
                    Row {
                        IconButton(onClick = onGoToHistory) {
                            Icon(Icons.Default.History, contentDescription = "History")
                        }
                        IconButton(onClick = onGoToProfile) {
                            Icon(Icons.Default.Person, contentDescription = "Profile")
                        }
                    }
                },
                actions = {
                    BluetoothStatusInTopBar(
                        connectionQuality = connectionQuality
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    BatteryLevelIcon(
                        batteryLevel = batteryLevel,
                        isConnected = isConnected,
                    )

                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Date display (no bottom padding)
            Text(
                text = currentDateStr,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )

            // Connection status (right under date, remove padding)
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
                        !isConnected -> "Disconnected"
                        connectionQuality == BluetoothConnectionQuality.GOOD -> "Connected (Good signal)"
                        connectionQuality == BluetoothConnectionQuality.MEDIUM -> "Connected (Medium signal)"
                        else -> "Connected (Weak signal)"
                    },
                    color = when {
                        !isConnected -> colors.error
                        else -> colors.onSurface
                    }
                )
            }

            Spacer(modifier = Modifier.height(100.dp))
            // Temperature displays
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Core Temperature
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
                            text = String.format("%.1f°C", temperatureCore),
                            fontSize = 32.sp,
                            color = tempColorCore,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Skin Temperature
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

                // Outside Temperature
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

            // Monitoring Status
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

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onPauseToggle,
                        enabled = isConnected && isMonitoring,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isPaused) Green else colors.tertiary
                        )
                    ) {
                        Text(text = if (isPaused) "Resume" else "Pause")
                    }

                    Button(
                        onClick = onMonitoringToggle,
                        enabled = isConnected,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isMonitoring) Red else Green
                        )
                    ) {
                        Text(text = if (isMonitoring) "Stop" else "Start")
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Status message at bottom
            Text(
                text = when {
                    !isConnected -> "Please connect a temperature sensor"
                    !isMonitoring -> "Press Start to begin monitoring"
                    isPaused -> "Monitoring is paused"
                    else -> "Monitoring in progress"
                },
                color = when {
                    !isConnected -> colors.error
                    !isMonitoring -> colors.onSurfaceVariant
                    isPaused -> colors.tertiary
                    else -> Green
                },
                fontSize = 14.sp,
                textAlign = TextAlign.Center
            )
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
            averageTemperatureCore = 37.1,
            batteryLevel = 78.0,
            isPaused = false,
            isMonitoring = true,
            onPauseToggle = { },
            onMonitoringToggle = { },
            onGoToProfile = { },
            onGoToHistory = { },
            themeMode = ThemeMode.SYSTEM,
            onThemeModeChange = { }
        )
    }
}