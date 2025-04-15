package com.example.innertemp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.innertemp.ui.theme.InnerTempTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            InnerTempTheme {
                HomeScreen()
            }
        }
    }
}

@Composable
fun HomeScreen() {
    var isConnected by remember { mutableStateOf(true) } // device connection status
    var rttemp by remember { mutableDoubleStateOf(36.2) } // real time temp
    var batlvl by remember { mutableStateOf(100) } // battery level
    var isPaused by remember { mutableStateOf(false) } // paused temp measurements
    var showDevMenu by remember { mutableStateOf(false) }
    val showDevButton by remember { mutableStateOf(true) } // dev button

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Device Status:",
                color = Color.Black,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = if (isConnected) "Connected" else "Disconnected",
                color = Color.Black,
                fontSize = 20.sp
            )
            Text(
                text = "Battery Level:",
                color = Color.Black,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = if (isConnected) "${batlvl}%" else "-",
                color = Color.Black,
                fontSize = 20.sp
            )
            Text(
                text = "Real-Time Temperature:",
                color = Color.Black,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = if (!isConnected) "-" else if (isPaused) "Paused" else "${rttemp}°C",
                color = Color.Black,
                fontSize = 20.sp
            )
            if (isConnected) {
                Spacer(modifier = Modifier.height(20.dp))
                Button(
                    onClick = { isPaused = !isPaused },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Black),
                    modifier = Modifier
                        .padding(top = 16.dp)
                        .defaultMinSize(minWidth = 120.dp)
                ) {
                    Text(text = if (isPaused) "Resume" else "Pause", color = Color.White)
                }
            }
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.End
        ) {
            if (showDevButton) {
                Button(
                    onClick = { showDevMenu = !showDevMenu },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Black),
                    modifier = Modifier.defaultMinSize(minWidth = 60.dp, minHeight = 40.dp)
                ) {
                    Text(text = "DEV", color = Color.White)
                }
            }
            if (showDevMenu) {
                Card(
                    modifier = Modifier.padding(top = 8.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(8.dp)
                    ) {
                        Text(text = "Connected", color = Color.Black, fontSize = 16.sp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Switch(
                            checked = isConnected,
                            onCheckedChange = { isConnected = it }
                        )
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun HomeScreenPreview() {
    InnerTempTheme {
        HomeScreen()
    }
}
