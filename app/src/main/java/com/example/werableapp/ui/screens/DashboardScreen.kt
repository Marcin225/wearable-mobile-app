package com.example.werableapp.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.werableapp.ui.components.MetricCard
import com.example.werableapp.ble.BleManager
import com.example.werableapp.ui.components.StatusHeader
import androidx.compose.runtime.getValue

/**
 * Main dashboard screen showing BLE connection status and wearable metrics
 */
@Composable
fun DashboardScreen(bleManager: BleManager) {

    val wearableData by bleManager.data.collectAsState()
    val isConnected by bleManager.isConnected.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
    ) {
        StatusHeader(
            isConnected = isConnected,
            batteryLevel = wearableData.batteryLevel,
            onDisconnectClick = {
                if (isConnected) {
                    bleManager.disconnect()
                } else {
                    bleManager.startScan()
                }
            }
        )

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            MetricCard(
                title = "Heart Rate",
                value = wearableData.heartRate.toString(),
                unit = "bpm",
                icon = Icons.Default.Favorite,
                iconTint = Color(0xFFF95B59),
                modifier = Modifier.weight(1f)
            )

            MetricCard(
                title = "SpO2",
                value = wearableData.spo2.toString(),
                unit = "%",
                icon = Icons.Default.WaterDrop,
                iconTint = Color(0xFF53A1FF),
                modifier = Modifier.weight(1f)
            )
        }
    }
}