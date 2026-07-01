package com.example.werableapp.data

/**
 * Wearable sensor data received from BLE notifications.
 *
 * Stores the latest heart rate, blood oxygen saturation,
 * battery level and local timestamp of the received packet.
 */

data class WearableData(
    val heartRate: Int,
    val spo2: Int,
    val batteryLevel: Int,
    val timeStamp: Long = System.currentTimeMillis()
)
