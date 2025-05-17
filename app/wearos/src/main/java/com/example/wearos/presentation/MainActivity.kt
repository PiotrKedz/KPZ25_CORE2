package com.example.wearos.presentation

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import com.example.wearos.data.WearableDataReceiver
import com.example.wearos.presentation.theme.InnerTempTheme

class MainActivity : ComponentActivity() {
    private lateinit var wearableDataReceiver: WearableDataReceiver

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        wearableDataReceiver = WearableDataReceiver(this)

        lifecycle.addObserver(LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> wearableDataReceiver.startListening()
                Lifecycle.Event.ON_PAUSE -> wearableDataReceiver.stopListening()
                else -> {}
            }
        })

        setTheme(android.R.style.Theme_DeviceDefault)

        setContent {
            WearApp(wearableDataReceiver)
        }
    }
}

@Composable
fun WearApp(wearableDataReceiver: WearableDataReceiver) {
    InnerTempTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colors.background),
            contentAlignment = Alignment.Center
        ) {
            TimeText()
            HomeScreen(wearableDataReceiver)
        }
    }
}

@Composable
fun HomeScreen(wearableDataReceiver: WearableDataReceiver) {
    val deviceState by wearableDataReceiver.deviceState.collectAsState()

    val temperatureText = remember(deviceState) {
        when {
            !deviceState.isConnected -> "-"
            !deviceState.isMonitoring -> "Stopped"
            deviceState.isPaused -> "Paused"
            deviceState.tempCore > 0 -> String.format("%.1f°C", deviceState.tempCore)
            else -> "-"
        }
    }

    val temperatureColor = remember(deviceState.tempCore) {
        when {
            deviceState.tempCore < 36.0 -> Color(0xFF03A9F4)
            deviceState.tempCore > 38.0 -> Color(0xFFF44336)
            else -> Color(0xFF4CAF50)
        }
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
            color = Color.White,
            style = TextStyle(fontSize = 13.sp),
            fontWeight = FontWeight.Bold,
            text = "Device Status:"
        )
        Text(
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
            color = Color.White,
            style = TextStyle(fontSize = 13.sp),
            text = if (deviceState.isConnected) "Connected" else "Disconnected"
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
            color = Color.White,
            style = TextStyle(fontSize = 16.sp),
            fontWeight = FontWeight.Bold,
            text = "Real-Time Temperature:"
        )
        Text(
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
            color = temperatureColor,
            style = TextStyle(fontSize = 32.sp),
            text = temperatureText
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
            color = Color.White,
            style = TextStyle(fontSize = 13.sp),
            fontWeight = FontWeight.Bold,
            text = "Battery Level:"
        )
        Text(
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
            color = Color.White,
            style = TextStyle(fontSize = 13.sp),
            text = if (deviceState.batteryLevel > 0) "${deviceState.batteryLevel.toInt()}%" else "-"
        )
    }
}