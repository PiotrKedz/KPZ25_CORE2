package com.example.innertemp

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.ModeNight
import androidx.compose.material.icons.filled.SettingsBrightness
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

enum class ThemeMode {
    SYSTEM, LIGHT, DARK
}

@Composable
fun ThemeModeToggle(
    currentMode: ThemeMode,
    onThemeModeChanged: (ThemeMode) -> Unit,
    modifier: Modifier = Modifier
) {
    val modes = listOf(ThemeMode.SYSTEM, ThemeMode.LIGHT, ThemeMode.DARK)

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .border(
                BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                shape = RoundedCornerShape(8.dp)
            )
    ) {
        modes.forEach { mode ->
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clickable { onThemeModeChanged(mode) }
                    .background(
                        color = if (currentMode == mode)
                            MaterialTheme.colorScheme.primaryContainer
                        else
                            Color.Transparent
                    ),
                contentAlignment = Alignment.Center
            ) {
                val icon = when (mode) {
                    ThemeMode.SYSTEM -> Icons.Default.SettingsBrightness
                    ThemeMode.LIGHT -> Icons.Default.LightMode
                    ThemeMode.DARK -> Icons.Default.ModeNight
                }

                Icon(
                    imageVector = icon,
                    contentDescription = mode.name,
                    tint = if (currentMode == mode)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}