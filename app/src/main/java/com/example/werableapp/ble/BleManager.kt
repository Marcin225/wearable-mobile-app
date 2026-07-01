package com.example.werableapp.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import android.util.Log
import com.example.werableapp.data.WearableData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

// Custom service and characteristic UUID
private val SERVICE_UUID = UUID.fromString("11197df9-7de7-4edd-87ca-5e589b3d2e3a")
private val CHARACTERISTIC_UUID = UUID.fromString("3ba2a546-ab9f-4ff4-b7ba-4056bd6a2779")

// Standard Ble descriptor used to enable notifications
private val CLIENT_CHARACTERISTIC_CONFIG_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

/**
 * Handles BLE scanning, connection and incoming wearable notifications
 */
class BleManager(private val context: Context) {

    private val bluetoothManager = context.getSystemService(BluetoothManager::class.java)
    private val bluetoothAdapter = bluetoothManager?.adapter
    private val bluetoothLeScanner = bluetoothAdapter?.bluetoothLeScanner

    private val handler = Handler(Looper.getMainLooper())

    private var scanning = false
    private var bluetoothGatt: BluetoothGatt? = null

    // Connection state exposed to the UI
    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    // Latest data received from the wearable
    private val _data = MutableStateFlow(WearableData(0, 0, 0))
    val data: StateFlow<WearableData> = _data.asStateFlow()

    private val scanCallback = object : ScanCallback() {

        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            Log.d("BLE", "Found ${result.device.name} ${result.device.address}")

            // Stop scanning when the target device is found
            stopScan()
            bluetoothGatt = result.device.connectGatt(context, false, gattCallback)
        }

        override fun onScanFailed(errorCode: Int) {
            Log.e("BLE", "Scan failed: $errorCode")
            scanning = false
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {

        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(
            gatt: BluetoothGatt,
            status: Int,
            newState: Int
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    Log.d("BLE", "Connected to GATT server.")

                    bluetoothGatt = gatt
                    _isConnected.value = true

                    gatt.discoverServices()
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    Log.d("BLE", "Disconnected from GATT server.")

                    _isConnected.value = false
                    gatt.close()
                    bluetoothGatt = null
                }
            } else {
                Log.e("BLE", "Connection failed: $status")

                _isConnected.value = false
                gatt.close()
                bluetoothGatt = null
            }
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                val service = gatt.getService(SERVICE_UUID)
                val characteristic = service?.getCharacteristic(CHARACTERISTIC_UUID)

                if (characteristic != null) {
                    // Enable local notification handling on Android
                    gatt.setCharacteristicNotification(characteristic, true)

                    val descriptor = characteristic.getDescriptor(
                        CLIENT_CHARACTERISTIC_CONFIG_UUID
                    )

                    if (descriptor != null) {
                        // Enable notifications on the BLE peripheral
                        @Suppress("DEPRECATION")
                        descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE

                        @Suppress("DEPRECATION")
                        gatt.writeDescriptor(descriptor)

                        Log.d("BLE", "Subscribed to characteristic notifications.")
                    } else {
                        Log.e("BLE", "CCCD descriptor not found.")
                    }
                } else {
                    Log.e("BLE", "Characteristic not found.")
                }
            } else {
                Log.e("BLE", "onServicesDiscovered received: $status")
            }
        }

        @Deprecated("Deprecated in Java")
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            // Called when the wearable (ESP) sends a notification packet
            if (characteristic.uuid == CHARACTERISTIC_UUID) {
                @Suppress("DEPRECATION")
                val value = characteristic.value

                parseData(value)
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun startScan() {
        if (scanning) return

        val filter = ScanFilter.Builder()
            .setServiceUuid(ParcelUuid(SERVICE_UUID))
            .build()

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        try {
            bluetoothLeScanner?.startScan(
                listOf(filter),
                settings,
                scanCallback
            )

            scanning = true
            Log.d("BLE", "Scan started.")

            // Stop scanning after timeout
            handler.postDelayed({
                if (scanning) {
                    stopScan()
                }
            }, 15_000)

        } catch (e: SecurityException) {
            Log.e("BLE", "No permission to scan for BLE devices", e)
        }
    }

    @SuppressLint("MissingPermission")
    fun stopScan() {
        if (!scanning) return

        try {
            bluetoothLeScanner?.stopScan(scanCallback)
            scanning = false
            Log.d("BLE", "Scan stopped.")
        } catch (e: SecurityException) {
            Log.e("BLE", "No permission to stop BLE scanning", e)
        }
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        try {
            stopScan()

            bluetoothGatt?.disconnect()
            bluetoothGatt?.close()
            bluetoothGatt = null

            _isConnected.value = false

            Log.d("BLE", "Disconnected.")
        } catch (e: SecurityException) {
            Log.e("BLE", "No permission to disconnect", e)
        }
    }

    private fun parseData(value: ByteArray) {
        if (value.size < 3) {
            Log.e("BLE", "Invalid data packet size: ${value.size}")
            return
        }

        // Expected packet format:
        // byte 0 = heart rate
        // byte 1 = SpO2
        // byte 2 = battery level

        val hr = value[0].toInt() and 0xFF
        val spo2 = value[1].toInt() and 0xFF
        val batteryLevel = value[2].toInt() and 0xFF

        _data.value = WearableData(
            heartRate = hr,
            spo2 = spo2,
            batteryLevel = batteryLevel
        )

        Log.d("BLE", "Received data: HR=$hr, SpO2=$spo2, Battery=$batteryLevel")
    }
}