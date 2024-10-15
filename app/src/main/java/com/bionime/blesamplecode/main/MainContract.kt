package com.bionime.blesamplecode.main

import android.bluetooth.BluetoothDevice
import android.content.Context

interface MainContract {
    interface Presenter {
        fun startScan()
        fun stopScan()
        fun connect(context: Context, bluetoothDevice: BluetoothDevice)
    }

    interface View {
        fun onScanned(deviceList: List<BluetoothDevice>)
        fun onErrorOccur(errorRes: Int)
        fun disabledScan()
        fun enabledScan()
        fun onAppendLog(log: String)
    }
}