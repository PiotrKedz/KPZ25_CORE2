package com.example.innertemp

import android.annotation.SuppressLint
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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
            Slider(value = batteryLevel.toFloat(), onValueChange = { onBatteryLevelChange(it.toDouble()) }, valueRange = 0f..100f, steps = 100 -1 )
            Spacer(Modifier.height(32.dp))
            Button(onClick = onClose, modifier = Modifier.fillMaxWidth().height(56.dp), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) {
                Text("CLOSE DEV MENU", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = MaterialTheme.colorScheme.onError)
            }
        }
    }
}