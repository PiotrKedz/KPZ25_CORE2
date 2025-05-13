package com.example.innertemp

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun BatteryLevelIcon(
    batteryLevel: Number,
    isConnected: Boolean,
    modifier: Modifier = Modifier,
    showPercentage: Boolean = true,
    batteryWidth: Dp = 40.dp,
    batteryHeight: Dp = 24.dp
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier
    ) {
        if (isConnected) {
            // Connected: Show battery icon
            val batteryLevelInt = batteryLevel.toInt()
            val batteryColor = when {
                batteryLevelInt <= 15 -> Color.Red
                batteryLevelInt <= 30 -> Color(0xFFFF9800) // Orange
                else -> Color(0xFF4CAF50) // Green
            }

            Box(
                contentAlignment = Alignment.CenterStart,
                modifier = Modifier
                    .width(batteryWidth)
                    .height(batteryHeight)
            ) {
                // Battery outline
                Canvas(modifier = Modifier.fillMaxSize()) {
                    // Main battery body
                    drawRoundRect(
                        color = batteryColor.copy(alpha = 0.3f),
                        cornerRadius = CornerRadius(4f, 4f),
                        style = Stroke(width = 2f)
                    )

                    // Battery tip/nub
                    val tipWidth = size.width * 0.05f
                    val tipHeight = size.height * 0.3f
                    val tipLeft = size.width
                    val tipTop = (size.height - tipHeight) / 2

                    drawRect(
                        color = batteryColor.copy(alpha = 0.3f),
                        topLeft = Offset(tipLeft, tipTop),
                        size = Size(tipWidth, tipHeight)
                    )
                }

                // Battery fill based on level
                val fillPercentage = batteryLevel.toFloat() / 100f
                Box(
                    modifier = Modifier
                        .fillMaxHeight(0.8f)
                        .fillMaxWidth(fillPercentage)
                        .padding(start = 3.dp, end = 3.dp)
                        .background(
                            color = batteryColor,
                            shape = RoundedCornerShape(2.dp)
                        )
                        .align(Alignment.CenterStart)
                )
            }

            // Percentage text (optional)
            if (showPercentage) {
                Text(
                    text = "${batteryLevel.toInt()}%",
                    color = MaterialTheme.colorScheme.onBackground,
                    fontSize = 14.sp, // Slightly smaller
                    fontWeight = FontWeight.Medium
                )
            }
        } else {
            // Disconnected: Show crossed circle icon
            val disconnectedColor = Color.Gray
            val iconSize = if (batteryHeight > batteryWidth) batteryHeight else batteryWidth

            Canvas(
                modifier = Modifier.size(iconSize)
            ) {
                val center = Offset(size.width / 2, size.height / 2)
                val radius = size.minDimension / 2.5f

                // Draw circle
                drawCircle(
                    color = disconnectedColor.copy(alpha = 0.3f),
                    radius = radius,
                    center = center,
                    style = Stroke(width = 2f)
                )

                // Draw diagonal line (top-left to bottom-right)
                drawLine(
                    color = disconnectedColor.copy(alpha = 0.6f),
                    start = Offset(
                        center.x - radius * 0.7f,
                        center.y - radius * 0.7f
                    ),
                    end = Offset(
                        center.x + radius * 0.7f,
                        center.y + radius * 0.7f
                    ),
                    strokeWidth = 2f,
                    cap = StrokeCap.Round
                )
            }

            // Disconnected text (optional)
            if (showPercentage) {
                Text(
                    text = "-",  // Simplified display for disconnected state
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun BatteryLevelIconPreview() {
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.padding(16.dp)
    ) {
        BatteryLevelIcon(batteryLevel = 100, isConnected = true)
        BatteryLevelIcon(batteryLevel = 75, isConnected = true)
        BatteryLevelIcon(batteryLevel = 50, isConnected = true)
        BatteryLevelIcon(batteryLevel = 25, isConnected = true)
        BatteryLevelIcon(batteryLevel = 10, isConnected = true)
        BatteryLevelIcon(batteryLevel = 0, isConnected = false)
    }
}