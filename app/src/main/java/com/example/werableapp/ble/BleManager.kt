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
import com.example.werableapp.data.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

// Custom service and characteristic UUID
private val SERVICE_UUID = UUID.fromString("11197df9-7de7-4edd-87ca-5e589b3d2e3a")
private val CHARACTERISTIC_UUID = UUID.fromString("3ba2a546-ab9f-4ff4-b7ba-4056bd6a2779")

// Standard Ble descriptor used to enable notifications
private val CLIENT_CHARACTERISTIC_CONFIG_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

/**
 * Handles BLE connection, incoming wearable data and local data storage
 */
class BleManager(private val context: Context) {

    private val bluetoothManager = context.getSystemService(BluetoothManager::class.java)
    private val bluetoothAdapter = bluetoothManager?.adapter
    private val bluetoothLeScanner = bluetoothAdapter?.bluetoothLeScanner

    private val handler = Handler(Looper.getMainLooper())

    private var scanning = false
    private var bluetoothGatt: BluetoothGatt? = null

    // connection state exposed to the UI
    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    // error state
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    fun clearError() {
        _errorMessage.value = null
    }

    // Latest data received from the wearable
    private val _data = MutableStateFlow(WearableData(heartRate = 0, spo2 = 0, batteryLevel = 0))
    val data: StateFlow<WearableData> = _data.asStateFlow()

    // database access for wearable measurements
    private val wearableDao by lazy { AppDatabase.getDatabase(context).wearableDao() }
    private val coroutineScope = CoroutineScope(Dispatchers.IO)
    private val dataBuffer = mutableListOf<WearableData>()
    private val BUFFER_LIMIT = 23

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
            _errorMessage.value = "BLE scan failed (error: $errorCode)"
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

                    cleanupOldData() // remove old data (older than 30 days) when a new connection is established

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

                _errorMessage.value = "Failed to connect (status: $status)."
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
                    _errorMessage.value = "Device not found. Please try again."
                }
            }, 10_000)

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

        val newData = WearableData(
            heartRate = hr,
            spo2 = spo2,
            batteryLevel = batteryLevel
        )


        _data.value = newData
        // buffer measurements and save them to the database in batches
        if (hr > 0 || spo2 > 0) {
            dataBuffer.add(newData)

            if (dataBuffer.size >= BUFFER_LIMIT) {
                val batchToSave = dataBuffer.toList()
                dataBuffer.clear()

                coroutineScope.launch {
                    wearableDao.insertBatch(batchToSave)
                    Log.d("DB", "Saved ${batchToSave.size} measurements to the database")
                }
            }
        }

        Log.d("BLE", "Received data: HR=$hr, SpO2=$spo2, Battery=$batteryLevel")
    }

    // removes data measurements older than 30 days
    private fun cleanupOldData() {
        coroutineScope.launch {
            val thirtyDaysInMillis = 30L * 24 * 60 * 60 * 1000
            val cutoffTime = System.currentTimeMillis() - thirtyDaysInMillis

            wearableDao.deleteOlderThan(cutoffTime)
            Log.d("DB", "Checked and cleaned up old data from the database older than 30 days")
        }
    }

    // fills the database with test data for debugging

    fun fillWithMockData() {
        coroutineScope.launch {
            val mockList = mutableListOf<WearableData>()
            val currentTime = System.currentTimeMillis()
            val totalMinutes = 30 * 24 * 60

            for (i in totalMinutes downTo 0) {
                val pastTime = currentTime - (i * 60 * 1000L)
                val fakeHr = (60..105).random()
                val fakeSpo2 = (94..100).random()
                val fakeBattery = 100 - ((i / 15) % 100)

                mockList.add(
                    WearableData(
                        heartRate = fakeHr,
                        spo2 = fakeSpo2,
                        batteryLevel = if (fakeBattery > 0) fakeBattery else 1,
                        timeStamp = pastTime
                    )
                )
            }

            wearableDao.insertBatch(mockList)
            Log.d("DB", "Generated ${mockList.size} mock measurements.")
        }
    }

    fun clearDatabase() {
        coroutineScope.launch {
            wearableDao.clearAllData()
            Log.d("DB", "Wszystkie dane testowe zostały usunięte z bazy.")
        }
    }
}