package com.example.werableapp.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Wearable sensor data received from BLE notifications.
 *
 * Stores the latest heart rate, blood oxygen saturation,
 * battery level and local timestamp of the received packet
 * data is stored in the Room database.
 */

@Entity(tableName = "wearable_metrics")
data class WearableData(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val heartRate: Int,
    val spo2: Int,
    val batteryLevel: Int,
    val timeStamp: Long = System.currentTimeMillis()
)
