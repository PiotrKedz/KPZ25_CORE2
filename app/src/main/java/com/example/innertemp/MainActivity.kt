package com.example.innertemp

import android.Manifest
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.outlined.DirectionsRun
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.DirectionsBike
import androidx.compose.material.icons.outlined.Kayaking
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.graphics.Color
import kotlin.math.roundToInt
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material.icons.filled.Battery1Bar
import androidx.compose.material.icons.filled.Battery2Bar
import androidx.compose.material.icons.filled.Battery3Bar
import androidx.compose.material.icons.filled.Battery4Bar
import androidx.compose.material.icons.filled.Battery5Bar
import androidx.compose.material.icons.filled.Battery6Bar
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.Battery0Bar
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Slider
import androidx.compose.material3.TextButton

enum class TemperatureZone {
    TOO_LOW,
    STEP_LOWER,
    PERFECT,
    STEP_HIGHER,
    TOO_HIGH
}

data class ZoneData(
    val zone: TemperatureZone,
    val label: String,
    val color: Color,
    val icon: String,
    var timeInSeconds: Int = 0
) {
    fun getPercentage(totalTime: Int): Float {
        return if (totalTime > 0) (timeInSeconds.toFloat() / totalTime) * 100f else 0f
    }

    fun getFormattedTime(): String {
        return if (timeInSeconds < 60) "${timeInSeconds}s"
        else {
            val minutes = timeInSeconds / 60
            val seconds = timeInSeconds % 60
            if (seconds > 0) "${minutes}m ${seconds}s" else "${minutes}m"
        }
    }
}

fun getTemperatureZone(temp: Double, lowThreshold: Double, highThreshold: Double): TemperatureZone {
    val optimalTemp = (lowThreshold + highThreshold) / 2
    val stepSize = (highThreshold - lowThreshold) / 4

    return when {
        temp < lowThreshold - stepSize -> TemperatureZone.TOO_LOW
        temp < lowThreshold -> TemperatureZone.STEP_LOWER
        temp <= optimalTemp + stepSize/2 -> TemperatureZone.PERFECT
        temp <= highThreshold -> TemperatureZone.STEP_HIGHER
        else -> TemperatureZone.TOO_HIGH
    }
}

fun getZoneRange(zone: TemperatureZone, lowThreshold: Double, highThreshold: Double): String {
    val optimalTemp = (lowThreshold + highThreshold) / 2
    val stepSize = (highThreshold - lowThreshold) / 4

    return when (zone) {
        TemperatureZone.TOO_LOW -> "< ${String.format("%.1f", lowThreshold - stepSize)}°C"
        TemperatureZone.STEP_LOWER -> "${String.format("%.1f", lowThreshold - stepSize)} - ${String.format("%.1f", lowThreshold)}°C"
        TemperatureZone.PERFECT -> "${String.format("%.1f", lowThreshold)} - ${String.format("%.1f", optimalTemp + stepSize/8)}°C"
        TemperatureZone.STEP_HIGHER -> "${String.format("%.1f", optimalTemp + stepSize/8)} - ${String.format("%.1f", highThreshold)}°C"
        TemperatureZone.TOO_HIGH -> "> ${String.format("%.1f", highThreshold)}°C"
    }
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
    private val _selectedSport = mutableStateOf(Sport.RUNNING)
    private val _athleticLevel = mutableStateOf("")
    private val _customTopTemperatureThreshold = mutableStateOf(38.5)
    private val _customBottomTemperatureThreshold = mutableStateOf(36.5)
    private lateinit var notificationsManager: NotificationsManager

    private val _zoneDataMap = mutableStateMapOf<TemperatureZone, ZoneData>()
    private val _sessionStartTime = mutableStateOf(0L)
    private val _lastZoneUpdateTime = mutableStateOf(0L)
    private val _currentZone = mutableStateOf(TemperatureZone.PERFECT)

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
            }.toTypedArray()
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

    private fun initializeZoneData() {
        _zoneDataMap.clear()
        _zoneDataMap[TemperatureZone.TOO_LOW] = ZoneData(
            TemperatureZone.TOO_LOW, "Too Low", Color(0xFF3B82F6), "❄️"
        )
        _zoneDataMap[TemperatureZone.STEP_LOWER] = ZoneData(
            TemperatureZone.STEP_LOWER, "Step Lower", Color(0xFF06B6D4), "⬇️"
        )
        _zoneDataMap[TemperatureZone.PERFECT] = ZoneData(
            TemperatureZone.PERFECT, "Perfect", Color(0xFF10B981), "✅"
        )
        _zoneDataMap[TemperatureZone.STEP_HIGHER] = ZoneData(
            TemperatureZone.STEP_HIGHER, "Step Higher", Color(0xFFF59E0B), "⬆️"
        )
        _zoneDataMap[TemperatureZone.TOO_HIGH] = ZoneData(
            TemperatureZone.TOO_HIGH, "Too High", Color(0xFFEF4444), "🔥"
        )
    }

    private fun updateZoneTracking(temperature: Double) {
        if (!_isMonitoring.value || _isPaused.value) return

        val currentTime = System.currentTimeMillis()
        val (lowThreshold, _, highThreshold) = getTemperatureColorRanges(
            mode = _activityMode.value,
            athleticLevel = _athleticLevel.value,
            customTop = if (_activityMode.value == ActivityMode.CUSTOM) _customTopTemperatureThreshold.value else null,
            customBottom = if (_activityMode.value == ActivityMode.CUSTOM) _customBottomTemperatureThreshold.value else null
        )

        val newZone = getTemperatureZone(temperature, lowThreshold, highThreshold)

        if (_lastZoneUpdateTime.value > 0) {
            val timeSpentInSeconds = ((currentTime - _lastZoneUpdateTime.value) / 1000).toInt()
            _zoneDataMap[_currentZone.value]?.let { zoneData ->
                zoneData.timeInSeconds += timeSpentInSeconds
                _zoneDataMap[_currentZone.value] = zoneData
            }
        }

        _currentZone.value = newZone
        _lastZoneUpdateTime.value = currentTime
    }

    private fun resetZoneTracking() {
        initializeZoneData()
        _sessionStartTime.value = System.currentTimeMillis()
        _lastZoneUpdateTime.value = System.currentTimeMillis()
        _currentZone.value = TemperatureZone.PERFECT
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

    private fun saveSelectedSport(sport: Sport) {
        val sharedPref = getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        with(sharedPref.edit()) {
            putString("selected_sport", sport.name)
            apply()
        }
    }

    private fun loadSelectedSport() {
        val sharedPref = getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        val savedSport = sharedPref.getString("selected_sport", Sport.RUNNING.name) ?: Sport.RUNNING.name
        _selectedSport.value = try {
            Sport.valueOf(savedSport)
        } catch (e: IllegalArgumentException) {
            Sport.RUNNING
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

        initializeZoneData()

        bluetoothManager.setOnConnectionStatusChanged { isConnected, signalStrength ->
            _isConnected.value = isConnected
            if (!isConnected) _batteryLevel.value = 0.0
            _connectionQuality.value = if (!isConnected) BluetoothConnectionQuality.DISCONNECTED
            else when {
                signalStrength > -90 -> BluetoothConnectionQuality.GOOD
                signalStrength > -120 -> BluetoothConnectionQuality.MEDIUM
                else -> BluetoothConnectionQuality.BAD
            }
        }

        bluetoothManager.setOnMonitoringStatusChanged { isMonitoring ->
            _isMonitoring.value = isMonitoring
            if (isMonitoring) {
                temperatureLogger.startNewSession(_selectedSport.value)
                resetAverageTemperature()
                resetZoneTracking()
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
                temperatureLogger.logTemperature(tempSkin)
                updateZoneTracking(tempCore)

                val (lowThreshold, _, highThreshold) = getTemperatureColorRanges(
                    mode = _activityMode.value,
                    athleticLevel = _athleticLevel.value,
                    customTop = if (_activityMode.value == ActivityMode.CUSTOM) _customTopTemperatureThreshold.value else null,
                    customBottom = if (_activityMode.value == ActivityMode.CUSTOM) _customBottomTemperatureThreshold.value else null
                )

                if (tempCore > 0) {
                    if (tempCore > highThreshold) {
                        notificationsManager.showHighTemperatureNotification(tempCore, highThreshold)
                    } else if (tempCore < lowThreshold) {
                        notificationsManager.showLowTemperatureNotification(tempCore, lowThreshold)
                    } else {
                        notificationsManager.resetTemperatureNotificationTimers()
                    }
                }
            }

            val batteryPercent = battery.toInt()
            if (batteryPercent == 10) {
                notificationsManager.showBatteryLowNotification(batteryPercent)
            } else if (batteryPercent == 5) {
                notificationsManager.showBatteryCriticalNotification(batteryPercent)
            }
        }

        loadThemePreference()
        loadActivityMode()
        loadSelectedSport()
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
                        selectedSport = _selectedSport,
                        athleticLevel = _athleticLevel,
                        customTopThreshold = _customTopTemperatureThreshold,
                        customBottomThreshold = _customBottomTemperatureThreshold,
                        zoneDataMap = _zoneDataMap,
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
                        onSportChange = { sport ->
                            _selectedSport.value = sport
                            saveSelectedSport(sport)
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
        selectedSport: State<Sport>,
        athleticLevel: State<String>,
        customTopThreshold: State<Double>,
        customBottomThreshold: State<Double>,
        zoneDataMap: Map<TemperatureZone, ZoneData>,
        onPauseToggle: () -> Unit,
        onMonitoringToggle: () -> Unit,
        onGoToProfile: () -> Unit,
        onGoToHistory: () -> Unit,
        onActivityModeChange: (ActivityMode) -> Unit,
        onSportChange: (Sport) -> Unit,
        onCustomThresholdsChange: (top: Double, bottom: Double) -> Unit
    ) {
        var showHeatZones by remember { mutableStateOf(false) }

        if (showHeatZones) {
            HeatZoneScreen(
                zoneDataMap = zoneDataMap,
                activityMode = activityMode.value,
                athleticLevel = athleticLevel.value,
                customTopThreshold = customTopThreshold.value,
                customBottomThreshold = customBottomThreshold.value,
                currentTemperature = temperatureSkin.value, // if you have this variable
                onBack = { showHeatZones = false }
            )

        } else {
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
                selectedSport = selectedSport.value,
                athleticLevel = athleticLevel.value,
                customTopThreshold = customTopThreshold.value,
                customBottomThreshold = customBottomThreshold.value,
                onPauseToggle = onPauseToggle,
                onMonitoringToggle = onMonitoringToggle,
                onGoToProfile = onGoToProfile,
                onGoToHistory = onGoToHistory,
                onActivityModeChange = onActivityModeChange,
                onSportChange = onSportChange,
                onCustomThresholdsChange = onCustomThresholdsChange,
                onShowHeatZones = { showHeatZones = true }
            )
        }
    }
}


