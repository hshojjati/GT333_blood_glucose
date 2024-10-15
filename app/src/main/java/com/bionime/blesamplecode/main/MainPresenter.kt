package com.bionime.blesamplecode.main

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.location.LocationManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import android.text.TextUtils
import android.util.Log
import com.bionime.blesamplecode.R
import com.bionime.blesamplecode.ble.BleManager
import com.bionime.blesamplecode.ble.BleManagerListener

class MainPresenter(
    private val view: MainContract.View,
    private val bluetoothAdapter: BluetoothAdapter,
    private val locationManager: LocationManager
) : MainContract.Presenter, BleManagerListener {
    private var deviceList = mutableListOf<BluetoothDevice>()
    private val scanFilter = ScanFilter.Builder()
        .setServiceUuid(ParcelUuid.fromString("0000fee0-0000-1000-8000-00805f9b34fb")).build()
    private val scanFilterList = listOf<ScanFilter>(scanFilter)
    private val scanSettings = ScanSettings.Builder().build()
    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            super.onScanResult(callbackType, result)
            val bluetoothDevice = result?.device
            if (!deviceList.contains(bluetoothDevice)) {
                bluetoothDevice?.let {
                    if (TextUtils.isEmpty(it.name) || TextUtils.isEmpty(it.address)) {
                        return@let
                    }
                    Log.d(MainPresenter::class.java.simpleName, "Device Name: " + it.name)
                    deviceList.add(it)
                }
            }
        }
    }

    override fun startScan() {
        if (!checkBle()) {
            view.onErrorOccur(R.string.not_open_ble)
            return
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S && !checkGps()) {
            view.onErrorOccur(R.string.not_open_gps)
            return
        }
        bluetoothAdapter.bluetoothLeScanner.startScan(scanFilterList, scanSettings, scanCallback)
        view.disabledScan()
        Handler(Looper.getMainLooper()).postDelayed({ stopScan() }, 5000)
    }

    private fun checkBle(): Boolean = bluetoothAdapter.isEnabled

    private fun checkGps(): Boolean =
        locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)

    override fun stopScan() {
        bluetoothAdapter.bluetoothLeScanner.stopScan(scanCallback)
        view.onScanned(deviceList)
        view.enabledScan()
    }

    override fun connect(context: Context, bluetoothDevice: BluetoothDevice) {
        val bleManager = BleManager(this)
        bleManager.connect(context, bluetoothDevice)
    }

    override fun onLogAppend(log: String) {
        view.onAppendLog(log)
    }
}