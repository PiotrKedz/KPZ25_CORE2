package com.example.innertemp

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Enum representing different Bluetooth connection quality levels
 */
enum class BluetoothConnectionQuality {
    DISCONNECTED,    // No connection
    BAD,             // Poor connection quality
    MEDIUM,          // Medium connection quality
    GOOD             // Excellent connection quality
}

/**
 * A composable that displays a Bluetooth icon with visual indication of connection quality
 *
 * @param connectionQuality The current Bluetooth connection quality
 * @param modifier Modifier to be applied to the component
 * @param iconSize Size of the Bluetooth icon
 * @param showLabel Whether to display a text label below the icon
 */
@Composable
fun BluetoothConnectivityIcon(
    connectionQuality: BluetoothConnectionQuality,
    modifier: Modifier = Modifier,
    iconSize: Dp = 24.dp,
    showLabel: Boolean = false
) {
    // Determine color based on connection quality
    val iconColor by animateColorAsState(
        targetValue = when (connectionQuality) {
            BluetoothConnectionQuality.DISCONNECTED -> Color.Gray
            BluetoothConnectionQuality.BAD -> Color.Red
            BluetoothConnectionQuality.MEDIUM -> Color.Yellow
            BluetoothConnectionQuality.GOOD -> Color.Green
        },
        animationSpec = tween(durationMillis = 300, easing = LinearEasing),
        label = "iconColorAnimation"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
    ) {
        Box(
            contentAlignment = Alignment.Center
        ) {
            // Background indicator circle
            Box(
                modifier = Modifier
                    .size(iconSize * 1.2f)
                    .background(iconColor.copy(alpha = 0.2f), CircleShape)
            )

            // Display appropriate Bluetooth icon
            Icon(
                imageVector = if (connectionQuality == BluetoothConnectionQuality.DISCONNECTED)
                    Icons.Default.BluetoothDisabled else Icons.Default.Bluetooth,
                contentDescription = "Bluetooth Connection: ${connectionQuality.name}",
                tint = iconColor,
                modifier = Modifier.size(iconSize)
            )
        }

        // Optional text label
        if (showLabel) {
            Text(
                text = when (connectionQuality) {
                    BluetoothConnectionQuality.DISCONNECTED -> "Disconnected"
                    BluetoothConnectionQuality.BAD -> "Poor Signal"
                    BluetoothConnectionQuality.MEDIUM -> "Medium Signal"
                    BluetoothConnectionQuality.GOOD -> "Good Signal"
                },
                color = iconColor,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}

/**
 * Example usage in TopAppBar
 */
@Composable
fun BluetoothStatusInTopBar(
    connectionQuality: BluetoothConnectionQuality,
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.padding(end = 8.dp)
    ) {
        BluetoothConnectivityIcon(
            connectionQuality = connectionQuality,
            iconSize = 22.dp
        )
    }
}