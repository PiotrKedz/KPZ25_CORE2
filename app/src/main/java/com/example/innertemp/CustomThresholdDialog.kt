package com.example.innertemp

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog

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