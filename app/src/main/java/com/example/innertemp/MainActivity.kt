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
import androidx.compose.foundation.background
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
import androidx.lifecycle.LifecycleEventObserver
import com.example.innertemp.ui.theme.Blue
import com.example.innertemp.ui.theme.Green
import com.example.innertemp.ui.theme.InnerTempTheme
import com.example.innertemp.ui.theme.Red
import java.text.SimpleDateFormat
import java.util.*

private const val TAG = "InnerTemp"

class MainActivity : ComponentActivity() {
    private lateinit var bluetoothManager: BluetoothManager
    private lateinit var temperatureLogger: TemperatureLogger // Add TemperatureLogger
    private val _isConnected = mutableStateOf(false)
    private val _temperatureCore = mutableStateOf(0.0)
    private val _temperatureSkin = mutableStateOf(0.0)
    private val _temperatureOutside = mutableStateOf(0.0)
    private val _batteryLevel = mutableStateOf(0.0)
    private val _isPaused = mutableStateOf(false)
    private val _isMonitoring = mutableStateOf(false)
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

    private fun loadThemePreference() {
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        loadThemePreference()

        // Initialize the temperature logger
        temperatureLogger = TemperatureLogger(this)

        // Initialize the Bluetooth manager
        bluetoothManager = BluetoothManager(this)

        // Set up callbacks with additional logging
        bluetoothManager.setOnConnectionStatusChanged { isConnected ->
            Log.d(TAG, "Connection status changed: $isConnected")
            _isConnected.value = isConnected
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

            // Update running average for core temperature if we're monitoring and not paused
            if (_isMonitoring.value && !_isPaused.value && tempCore > 0) {
                updateAverageTemperature(tempCore)

                // Log the temperature to the file system
                temperatureLogger.logTemperature(tempCore)
            }
        }

        setContent {
            InnerTempTheme(darkTheme = useDarkTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppContent(
                        isConnected = _isConnected,
                        temperatureCore = _temperatureCore,
                        temperatureSkin = _temperatureSkin,
                        temperatureOutside = _temperatureOutside,
                        batteryLevel = _batteryLevel,
                        isPaused = _isPaused,
                        isMonitoring = _isMonitoring,
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
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    override fun onDestroy() {
        super.onDestroy()
        bluetoothManager.closeConnection()
    }

    @Composable
    fun AppContent(
        isConnected: State<Boolean>,
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
    ) {
        HomeScreen(
            isConnected = isConnected.value,
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
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    isConnected: Boolean,
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
    onGoToHistory: () -> Unit
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

    // Get current date for UI display
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
                    IconButton(onClick = onGoToHistory) {
                        Icon(Icons.Default.History, contentDescription = "History")
                    }
                },
                actions = {
                    IconButton(onClick = onGoToProfile) {
                        Icon(Icons.Default.Person, contentDescription = "Profile")
                    }
                }
            )
        },
        content = { paddingValues ->
            Box(
                modifier = Modifier
                    .padding(paddingValues)
                    .fillMaxSize()
                    .background(colors.background)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Show current date
                    Text(
                        text = currentDateStr,
                        color = colors.onBackground,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Normal,
                        modifier = Modifier.padding(bottom = 24.dp)
                    )

                    Text(
                        text = "Device Status:",
                        color = colors.onBackground,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (isConnected) "Connected" else "Disconnected",
                        color = colors.onBackground,
                        fontSize = 20.sp
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "Battery Level:",
                        color = colors.onBackground,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (isConnected) "${batteryLevel}%" else "-",
                        color = colors.onBackground,
                        fontSize = 20.sp
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    Text(
                        text = "Real-Time Temperature:",
                        color = colors.onBackground,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (!isConnected) "-"
                        else if (!isMonitoring) "Monitoring Stopped"
                        else if (isPaused) "Paused"
                        else String.format("%.1f°C", temperatureCore),
                        color = tempColorCore,
                        fontSize = 36.sp,
                        fontWeight = FontWeight.Bold
                    )

                    // Display average core temperature when monitoring
                    if (isConnected && isMonitoring) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Average Core Temperature:",
                            color = colors.onBackground,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (averageTemperatureCore <= 0) "Calculating..."
                            else String.format("%.1f°C", averageTemperatureCore),
                            color = when {
                                averageTemperatureCore < 36.0 -> Blue
                                averageTemperatureCore > 38.0 -> Red
                                else -> Green
                            },
                            fontSize = 28.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    // Debug sensor readings
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Debug Sensor Readings",
                        color = colors.onBackground,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        text = "Skin: ${String.format("%.1f°C", temperatureSkin)} | " +
                                "Outside: ${String.format("%.1f°C", temperatureOutside)}",
                        color = colors.onBackground.copy(alpha = 0.7f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Normal
                    )

                    Spacer(modifier = Modifier.weight(1f))  // Push buttons toward the bottom

                    if (isConnected) {
                        // Action buttons
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 24.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            // Start/Stop Button
                            Button(
                                onClick = onMonitoringToggle,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isMonitoring) Red else Green
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(end = 8.dp)
                                    .height(56.dp)
                            ) {
                                Text(
                                    text = if (isMonitoring) "Stop" else "Start",
                                    color = colors.onPrimary,
                                    fontSize = 18.sp
                                )
                            }

                            // Pause Button (only enabled when monitoring is active)
                            Button(
                                onClick = onPauseToggle,
                                enabled = isMonitoring,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = colors.primary,
                                    disabledContainerColor = colors.primary.copy(alpha = 0.5f)
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(start = 8.dp)
                                    .height(56.dp)
                            ) {
                                Text(
                                    text = if (isPaused) "Resume" else "Pause",
                                    color = if (isMonitoring) colors.onPrimary
                                    else colors.onPrimary.copy(alpha = 0.5f),
                                    fontSize = 18.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    )
}

@Preview(showBackground = true)
@Composable
fun HomeScreenPreview() {
    InnerTempTheme {
        HomeScreen(
            isConnected = true,
            temperatureCore = 36.2,
            temperatureSkin = 36.2,
            temperatureOutside = 36.2,
            averageTemperatureCore = 36.4,
            batteryLevel = 75.0,
            isPaused = false,
            isMonitoring = true,
            onPauseToggle = {},
            onMonitoringToggle = {},
            onGoToProfile = {},
            onGoToHistory = {},
        )
    }
}