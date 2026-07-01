package com.example.werableapp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp


Header showing BLE connection status, connection action and battery level

@Composable
fun StatusHeader(
    isConnected: Boolean,
    batteryLevel: Int,
    onDisconnectClick: () -> Unit
) {
    Row (
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {

            // BLE connection status indicator
            Box (
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(if (isConnected) Color.Green else Color.Red)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (isConnected) "Connected" else "Disconnected",
                color = Color.White,
                fontSize = 14.sp
            )

            Spacer(modifier = Modifier.width(16.dp))

            // Connection button label
            OutlinedButton(
                onClick = onDisconnectClick,
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
            ) {
                Text(
                    text = if (isConnected) "Disconnect" else "Connect",
                    fontSize = 12.sp
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(

                // Battery label
                imageVector = Icons.Default.BatteryFull,
                contentDescription = "Battery",
                tint = Color.Green,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "$batteryLevel%",
                color = Color.White,
                fontSize = 14.sp
            )
        }
    }
}