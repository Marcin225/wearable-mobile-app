package com.example.werableapp.ui.screens

import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.key
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import com.example.werableapp.R
import com.example.werableapp.ui.components.TimeRange
import com.example.werableapp.ui.components.MetricCard
import com.example.werableapp.ble.BleManager
import com.example.werableapp.ui.components.StatusHeader
import com.example.werableapp.ui.components.ChartCard
import com.example.werableapp.ui.components.ChartSummary
import com.example.werableapp.ui.components.ChartMetric
import com.example.werableapp.data.ChartRepository

/**
 * Main dashboard screen that displays real-time device data and historical charts
 */
@Composable
fun DashboardScreen(bleManager: BleManager, isDarkTheme: Boolean) {

    // observe BLE data streams. Whenever new sensor readings arrive
    // the UI will automatically refresh to show the latest values
    val wearableData by bleManager.data.collectAsState()
    val isConnected by bleManager.isConnected.collectAsState()
    val errorMessage by bleManager.errorMessage.collectAsState()
    val context = LocalContext.current
    val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    val bluetoothAdapter = bluetoothManager.adapter

    // handles turning on Bluetooth and displaying connection errors
    val enableBluetoothLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            bleManager.startScan()
        } else {
            Toast.makeText(context, "Bluetooth is required to connect to the device.", Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(errorMessage) {
        errorMessage?.let { errorMsg ->
            Toast.makeText(context, errorMsg, Toast.LENGTH_LONG).show()
            bleManager.clearError()
        }
    }

    val currentSpo2Icon = if (isDarkTheme) {
        R.drawable.spo2_icon_light
    } else {
        R.drawable.spo2_icon_dark
    }

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
                    if (bluetoothAdapter?.isEnabled == true) {
                        bleManager.startScan()
                    }else {
                        val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                        enableBluetoothLauncher.launch(enableBtIntent)
                    }
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
                value = if (wearableData.heartRate > 0) wearableData.heartRate.toString() else "--",
                unit = "bpm",
                iconVector = Icons.Default.Favorite,
                iconRes = null,
                iconTint = Color(0xFFF95B59),
                modifier = Modifier.weight(1f)
            )

            MetricCard(
                title = "SpO₂",
                value = if (wearableData.spo2 > 0) wearableData.spo2.toString() else "--",
                unit = "%",
                iconVector = null,
                iconRes = currentSpo2Icon,
                iconTint = Color(0xFF53A1FF),
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        var selectedTimeRange by remember { mutableStateOf(TimeRange.HOUR_1) }
        var selectedMetric by remember { mutableStateOf(ChartMetric.HEART_RATE) }

        TabRow(
            selectedTabIndex = selectedMetric.ordinal,
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onSurface,
            indicator = { tabPositions ->
                TabRowDefaults.Indicator(
                    modifier = Modifier.tabIndicatorOffset(tabPositions[selectedMetric.ordinal]),
                    color = if (selectedMetric == ChartMetric.HEART_RATE) Color(0xFFF95B59) else Color(0xFF53A1FF)
                )
            },
            modifier = Modifier.padding(bottom = 16.dp)
        ) {
            Tab(
                selected = selectedMetric == ChartMetric.HEART_RATE,
                onClick = { selectedMetric = ChartMetric.HEART_RATE },
                text = { Text("Heart Rate") },
                selectedContentColor = Color(0xFFF95B59),
                unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Tab(
                selected = selectedMetric == ChartMetric.SPO2,
                onClick = { selectedMetric = ChartMetric.SPO2 },
                text = { Text("SpO₂") },
                selectedContentColor = Color(0xFF53A1FF),
                unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        val context = LocalContext.current
        val chartRepository = remember { ChartRepository(context) }
        var chartSummary by remember { mutableStateOf(ChartSummary()) }


        // fetch chart data from the database
        // this block runs automatically whenever the user changes the metric or the selected time range

        LaunchedEffect(selectedMetric, selectedTimeRange) {
            chartSummary = chartRepository.getChartSummary(selectedMetric, selectedTimeRange)
        }

        val chartTitle = if (selectedMetric == ChartMetric.HEART_RATE) "Heart Rate" else "SpO₂"
        val chartIconVector = if (selectedMetric == ChartMetric.HEART_RATE) Icons.Default.Favorite else null
        val chartIconRes = if (selectedMetric == ChartMetric.SPO2) currentSpo2Icon else null
        val chartTint = if (selectedMetric == ChartMetric.HEART_RATE) Color(0xFFF95B59) else Color(0xFF53A1FF)
        val chartUnit = if (selectedMetric == ChartMetric.HEART_RATE) "bpm" else "%"
        val chartStep = if (selectedMetric == ChartMetric.HEART_RATE) 10 else 5

        key(selectedMetric, selectedTimeRange) {
            ChartCard(
                title = chartTitle,
                iconVector = chartIconVector,
                iconRes = chartIconRes,
                iconTint = chartTint,
                unit = chartUnit,
                selectedRange = selectedTimeRange,
                onRangeSelected = { selectedTimeRange = it },
                summary = chartSummary,
                yAxisStep = chartStep
            )
        }
    }
}