package com.bionime.blesamplecode.ble

import android.annotation.SuppressLint
import android.bluetooth.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.bionime.blesamplecode.extension.toHexString
import com.bionime.blesamplecode.utils.BgmCommand
import java.nio.charset.StandardCharsets
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.*

@SuppressLint("MissingPermission")
class BleManager(private val bleManagerListener: BleManagerListener) {
    private var broadcastReceiver: BroadcastReceiver
    private var bluetoothGattCallback: BluetoothGattCallback
    private var isOpenPCL = false
    private var isEnabledNotification = false
    private var handler: Handler = Handler(Looper.getMainLooper())
    private var glucoseData = byteArrayOf()
    private var totalCount = 0
    private lateinit var pclChara: BluetoothGattCharacteristic
    private lateinit var notifyChara: BluetoothGattCharacteristic
    private lateinit var writeChara: BluetoothGattCharacteristic
    private lateinit var bluetoothGatt: BluetoothGatt
    private lateinit var currentCommand: BgmCommand
    private lateinit var checkPCLRunnable: Runnable
    private lateinit var checkNotificationRunnable: Runnable

    companion object {
        private val BGM_UUID_SERVICE = UUID.fromString("0000fee0-0000-1000-8000-00805f9b34fb")
        private val BGM_UUID_FEE1 = UUID.fromString("0000fee1-0000-1000-8000-00805f9b34fb")
        private val BGM_UUID_FEE2 = UUID.fromString("0000fee2-0000-1000-8000-00805f9b34fb")
        private val BGM_UUID_FEE3 = UUID.fromString("0000fee3-0000-1000-8000-00805f9b34fb")
        private val CHARACTERISTIC_UPDATE_NOTIFICATION_DESCRIPTOR_UUID =
            UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    }

    init {
        broadcastReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val status = intent?.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, -1)
                Log.d(BleManager::class.java.simpleName, "" + status)
                if (status == BluetoothDevice.BOND_BONDING) {
                    Log.d(BleManager::class.java.simpleName, "Device Bonding.")
                    appendLog("Device Bonding.")
                } else if (status == BluetoothDevice.BOND_BONDED) {
                    Log.d(BleManager::class.java.simpleName, "Device Bonded.")
                    appendLog("Device Bonded.")
                    Log.d(BleManager::class.java.simpleName, "Start Discover Services.")
                    appendLog("Start Discover Services.")
                    bluetoothGatt.discoverServices()
                    context?.unregisterReceiver(this)
                }
            }
        }
        bluetoothGattCallback = object : BluetoothGattCallback() {
            override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
                super.onConnectionStateChange(gatt, status, newState)
                when (newState) {
                    BluetoothGatt.STATE_CONNECTED -> {
                        Log.d(BleManager::class.java.simpleName, "Device Connected.")
                        appendLog("Device Connected.")
                        if (gatt?.device?.bondState == BluetoothDevice.BOND_BONDED) {
                            gatt.discoverServices()
                            handler.postDelayed(checkNotificationRunnable, 3000)
                        }
                    }

                    BluetoothGatt.STATE_DISCONNECTED -> {
                        handler.removeCallbacksAndMessages(null)
                        totalCount = 0
                        isOpenPCL = false
                        isEnabledNotification = false
                        Log.d(BleManager::class.java.simpleName, "Device Disconnected.")
                        appendLog("Device Disconnected.")
                        gatt?.close()
                    }
                }
            }

            override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
                super.onServicesDiscovered(gatt, status)
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    val bluetoothGattService = gatt?.getService(BGM_UUID_SERVICE)
                    bluetoothGattService?.characteristics?.forEach {
                        Log.d(BleManager::class.java.simpleName, it.uuid.toString())
                        appendLog("Characteristics: ${it.uuid}")
                        when (it.uuid) {
                            BGM_UUID_FEE1 -> {
                                pclChara = it
                            }

                            BGM_UUID_FEE2 -> {
                                notifyChara = it
                            }

                            BGM_UUID_FEE3 -> {
                                writeChara = it
                            }
                        }
                    }.also {
                        enableNotifications()
                    }
                }
            }

            override fun onCharacteristicWrite(
                gatt: BluetoothGatt?,
                characteristic: BluetoothGattCharacteristic?,
                status: Int
            ) {
                super.onCharacteristicWrite(gatt, characteristic, status)
                Log.d(
                    BleManager::class.java.simpleName,
                    "Write Command: ${currentCommand.name}\nHex: ${characteristic?.value?.toHexString()}"
                )
                appendLog("Write Command: ${currentCommand.name}\nHex: ${characteristic?.value?.toHexString()}")
            }

            override fun onCharacteristicRead(
                gatt: BluetoothGatt?,
                characteristic: BluetoothGattCharacteristic?,
                status: Int
            ) {
                super.onCharacteristicRead(gatt, characteristic, status)
                Log.d(
                    BleManager::class.java.simpleName,
                    "Read Characteristic: ${characteristic?.uuid}\nHex: ${characteristic?.value?.toHexString()}"
                )
                appendLog("Read Characteristic: ${characteristic?.uuid}\nHex: ${characteristic?.value?.toHexString()}")
                if (currentCommand == BgmCommand.OPEN_PCL) {
                    val data = characteristic?.value
                    if (data?.get(0) ?: 0x01.toByte() == 0x00.toByte()) {
                        Log.d(BleManager::class.java.simpleName, "PCL Opened.")
                        appendLog("PCL Opened.")
                        isOpenPCL = true
                        currentCommand = BgmCommand.GET_MODEL_NAME
                        writeChara.value =
                            byteArrayOf(0xB0.toByte(), 0x00.toByte(), 0xB0.toByte())
                        handler.postDelayed({ gatt?.writeCharacteristic(writeChara) }, 500)
                    } else {
                        Log.d(BleManager::class.java.simpleName, "Retry Open PCL.")
                        appendLog("Retry Open PCL.")
                        currentCommand = BgmCommand.OPEN_PCL
                        pclChara.value = byteArrayOf(0x00.toByte())
                        bluetoothGatt.writeCharacteristic(pclChara)
                        handler.postDelayed(checkPCLRunnable, 1000)
                    }
                }
            }

            override fun onCharacteristicChanged(
                gatt: BluetoothGatt?,
                characteristic: BluetoothGattCharacteristic?
            ) {
                super.onCharacteristicChanged(gatt, characteristic)
                Log.d(
                    BleManager::class.java.simpleName,
                    "Receive Hex: ${characteristic?.value?.toHexString()}"
                )
                appendLog("Receive Hex: ${characteristic?.value?.toHexString()}")
                val data = characteristic?.value
                data?.let {
                    when (currentCommand) {
                        BgmCommand.WAIT_NOTIFY -> {
                            isEnabledNotification = true
                            val meterSN =
                                String(it.copyOfRange(1, it.lastIndex), StandardCharsets.UTF_8)
                            Log.d(BleManager::class.java.simpleName, "Meter SN: $meterSN")
                            appendLog("Meter SN: $meterSN")
                            currentCommand = BgmCommand.OPEN_PCL
                            pclChara.value = byteArrayOf(0x00.toByte())
                            bluetoothGatt.writeCharacteristic(pclChara)
                            handler.postDelayed(checkPCLRunnable, 1000)
                        }

                        BgmCommand.GET_MODEL_NAME -> {
                            val modelName =
                                String(it.copyOfRange(4, it.size - 1), StandardCharsets.UTF_8)
                            Log.d(
                                BleManager::class.java.simpleName,
                                "Model Name: $modelName"
                            )
                            appendLog("Model Name: $modelName")
                            currentCommand = BgmCommand.GET_FIRMWARE_VERSION
                            writeChara.value =
                                byteArrayOf(0xB0.toByte(), 0x01.toByte(), 0xB1.toByte())
                            handler.postDelayed({ gatt?.writeCharacteristic(writeChara) }, 500)
                        }

                        BgmCommand.GET_FIRMWARE_VERSION -> {
                            val firmwareVersion =
                                String(it.copyOfRange(4, it.size - 1), StandardCharsets.UTF_8)
                            Log.d(
                                BleManager::class.java.simpleName,
                                "Firmware Version: $firmwareVersion"
                            )
                            appendLog("Firmware Version: $firmwareVersion")
                            currentCommand = BgmCommand.GET_TOTAL_RECORD_COUNT
                            writeChara.value =
                                byteArrayOf(
                                    0xB0.toByte(),
                                    0x33.toByte(),
                                    0xE3.toByte()
                                )
                            handler.postDelayed({ gatt?.writeCharacteristic(writeChara) }, 500)
                        }

                        BgmCommand.GET_TOTAL_RECORD_COUNT -> {
                            val bytes = it.copyOfRange(4, it.lastIndex - 1)
                            totalCount = getTotalCount(bytes.copyOfRange(4, 6))
                            appendLog("Count: $totalCount")
                            if (totalCount > 0) {
                                currentCommand = BgmCommand.GET_GLUCOSE_RECORD
                                writeChara.value = getOneRecordCmd()
                                handler.postDelayed({ gatt?.writeCharacteristic(writeChara) }, 500)
                            } else {
                                bluetoothGatt.disconnect()
                            }
                        }

                        BgmCommand.GET_GLUCOSE_RECORD -> {
                            val tempBytes = it.copyOfRange(2, it.lastIndex)
                            glucoseData += tempBytes
                            if (it[0] != it[1]) {
                                return
                            }
                            parserOneRecord(glucoseData.copyOfRange(2, glucoseData.lastIndex))
                            glucoseData = byteArrayOf()
                            totalCount -= 1
                            if (totalCount == 0) {
                                currentCommand = BgmCommand.STOP_BROADCAST
                                writeChara.value =
                                    byteArrayOf(0xB0.toByte(), 0x36.toByte(), 0x78.toByte(), 0x5E.toByte())
                                handler.postDelayed({ gatt?.writeCharacteristic(writeChara) }, 500)
                            } else {
                                writeChara.value = getOneRecordCmd()
                                handler.postDelayed({ gatt?.writeCharacteristic(writeChara) }, 500)
                            }
                        }

                        else -> {
                            bluetoothGatt.disconnect()
                        }
                    }
                }
            }
        }
        checkPCLRunnable = Runnable {
            if (isOpenPCL) {
                return@Runnable
            }
            Log.d(BleManager::class.java.simpleName, "Read PCL.")
            appendLog("Read PCL.")
            bluetoothGatt.readCharacteristic(pclChara)
        }
        checkNotificationRunnable = Runnable {
            if (isEnabledNotification) {
                return@Runnable
            }
            Log.d(BleManager::class.java.simpleName, "Retry Enable Notification.")
            appendLog("Retry Enable Notification.")
            enableNotifications()
            handler.postDelayed(checkNotificationRunnable, 1000)
        }
    }

    private fun getOneRecordCmd(): ByteArray {
        val cmd =
            byteArrayOf(0xB0.toByte(), 0x61.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte())
        cmd[2] = totalCount.toUByte().and(0xFF.toUByte()).toByte()
        cmd[3] = totalCount.toUInt().shr(8).toByte()
        cmd[4] = (cmd[0] + cmd[1] + cmd[2] + cmd[3]).toUByte().and(0xFF.toUByte()).toByte()
        return cmd
    }

    private fun getTotalCount(bytes: ByteArray): Int {
        var total = 0
        for (i in bytes.indices) {
            total += bytes[i].toUByte().and(0xFF.toUByte()).toUInt().shl(8 * i).toInt()
        }
        return total
    }

    private fun parserOneRecord(data: ByteArray) {
        appendLog(data.toHexString())
        val record = data.copyOfRange(2, 16)
        val dataByte = record.copyOfRange(0, 6)
        val marker: Int = syncMarker(dataByte)
        val timeZone: Int = syncTimeZone(dataByte)
        val isHi: Boolean = syncHiFlag(dataByte)
        val isCS: Boolean = syncCS(dataByte)
        val isKetone: Boolean = syncKetoneFlag(dataByte)
        val targetMeasurementKey: String
        val targetMeasurementValue: Int
        if (isKetone) {
            targetMeasurementKey = "Ketone"
            targetMeasurementValue = syncKetone(dataByte)
        } else {
            targetMeasurementKey = "Glucose"
            targetMeasurementValue = syncGlucose(dataByte)
        }
        val measureDateTime: String = try {
            syncDateTime(dataByte)
        } catch (e: ParseException) {
            "INVALID"
        }
        appendLog(
            """
                 Sequence => $totalCount
                 Marker ==> $marker
                 $targetMeasurementKey ==> $targetMeasurementValue
                 isHi ==> $isHi
                 isCS ==> $isCS
                 TimeZone ==> $timeZone
                 Measure DateTime ==> $measureDateTime
                 """.trimIndent()
        )
    }

    private fun syncMarker(dataByte: ByteArray): Int {
        return (dataByte[4].toUByte() and 0xFF.toUByte() and 0x38.toUByte()).toInt().shr(3)
    }

    private fun syncTimeZone(dataByte: ByteArray): Int {
        return (dataByte[4].toUByte() and 0xFF.toUByte() and 0xC0.toUByte()).toInt().shr(3) +
            (dataByte[2].toUByte() and 0xFF.toUByte() and 0xC0.toUByte()).toInt().shr(5) +
            (dataByte[1].toUByte() and 0xFF.toUByte() and 0x20.toUByte()).toInt().shr(5)
    }

    private fun syncGlucose(dataByte: ByteArray): Int {
        return (dataByte[4].toUByte() and 0xFF.toUByte() and 0x03.toUByte()).toInt().shl(8) +
            (dataByte[5].toUByte() and 0xFF.toUByte()).toInt()
    }

    private fun syncKetone(dataByte: ByteArray): Int {
        return (((dataByte[4].toUByte() and 0xFF.toUByte() and 0x03.toUByte()).toInt().shl(8) +
            (dataByte[5].toUByte() and 0xFF.toUByte()).toInt()).toDouble() / 10.0 * 10.4).toInt()
    }

    private fun syncKetoneFlag(dataByte: ByteArray): Boolean {
        return (dataByte[0].toUByte() and 0xFF.toUByte() and 0x20.toUByte()).toInt().shr(5) == 1
    }

    private fun syncHiFlag(dataByte: ByteArray): Boolean {
        return (dataByte[3].toUByte() and 0xFF.toUByte() and 0x80.toUByte()).toInt().shr(7) == 1
    }

    private fun syncCS(dataByte: ByteArray): Boolean {
        return (dataByte[4].toUByte() and 0xFF.toUByte() and 0x04.toUByte()).toInt().shr(2) == 1
    }

    @Throws(ParseException::class)
    private fun syncDateTime(dataByte: ByteArray): String {
        val year = (dataByte[3].toUByte() and 0xFF.toUByte()) and 0x7F.toUByte()
        val month =
            (dataByte[1].toUByte() and 0xFF.toUByte() and 0xC0.toUByte()).toInt().shr(4) +
                (dataByte[0].toUByte() and 0xFF.toUByte() and 0xC0.toUByte()).toInt().shr(6) + 1
        val day = (dataByte[0].toUByte() and 0xFF.toUByte() and 0x1F.toUByte()).toInt() + 1
        val hour = dataByte[1].toUByte() and 0xFF.toUByte() and 0x1F.toUByte()
        val min = dataByte[2].toUByte() and 0xFF.toUByte() and 0x3F.toUByte()
        val calendar = Calendar.getInstance().apply {
            this[Calendar.YEAR] = this[Calendar.YEAR] / 100 * 100
        }.also { calendar ->
            val formatter = SimpleDateFormat("yy-MM-dd HH:mm", Locale.ENGLISH).apply {
                set2DigitYearStart(calendar.time)
                isLenient = false
            }
            formatter.parse("$year-$month-$day $hour:$min")?.let {
                calendar.time = it
            }
        }
        return String.format(Locale.ENGLISH, "%1\$tY%1\$tm%1\$td%1\$tH%1\$tM", calendar)
    }

    fun enableNotifications() {
        Log.d(BleManager::class.java.simpleName, "Enable Notifications")
        appendLog("Enable Notifications")
        currentCommand = BgmCommand.WAIT_NOTIFY
        bluetoothGatt.setCharacteristicNotification(notifyChara, true)
        val originalWriteType = notifyChara.writeType
        val descriptor =
            notifyChara.getDescriptor(CHARACTERISTIC_UPDATE_NOTIFICATION_DESCRIPTOR_UUID)
        descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
        notifyChara.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        bluetoothGatt.writeDescriptor(descriptor)
        notifyChara.writeType = originalWriteType
    }

    fun connect(context: Context, bluetoothDevice: BluetoothDevice) {
        if (bluetoothDevice.bondState != BluetoothDevice.BOND_BONDED) {
            context.registerReceiver(
                broadcastReceiver,
                IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
            )
        }
        bluetoothGatt = bluetoothDevice.connectGatt(context, false, bluetoothGattCallback)
    }

    private fun appendLog(log: String) {
        Handler(Looper.getMainLooper()).post {
            bleManagerListener.onLogAppend(log)
        }
    }
}