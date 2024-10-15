package com.bionime.blesamplecode.main

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ScrollView
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.bionime.blesamplecode.R
import com.bionime.blesamplecode.adapter.BluetoothDeviceAdapter
import com.bionime.blesamplecode.databinding.ActivityMainBinding
import com.bionime.blesamplecode.utils.Status
import permissions.dispatcher.*

@RuntimePermissions
class MainActivity : AppCompatActivity(), MainContract.View, View.OnClickListener,
    BluetoothDeviceAdapter.OnItemClickListener {
    private lateinit var presenter: MainPresenter
    private lateinit var binding: ActivityMainBinding
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        initView()
        initParam()
    }

    private fun initView() {
        binding.textMainActivityStatus.text = getString(R.string.status, "")
        binding.btnMainActivityScan.setOnClickListener(this)
    }

    private fun initParam() {
        presenter = MainPresenter(
            this,
            (getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter,
            getSystemService(Context.LOCATION_SERVICE) as LocationManager
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        onRequestPermissionsResult(requestCode, grantResults)
    }

    override fun onScanned(deviceList: List<BluetoothDevice>) {
        Log.d(MainActivity::class.java.simpleName, "Device List Size: " + deviceList.size)
        if (deviceList.isEmpty()) {
            AlertDialog.Builder(this)
                .setMessage(R.string.not_find_device)
                .setCancelable(false)
                .setPositiveButton(R.string.ok, null)
                .show()
        } else {
            binding.recyclerMainActivityDeviceList.visibility = View.VISIBLE
            binding.recyclerMainActivityDeviceList.layoutManager = LinearLayoutManager(this)
            binding.recyclerMainActivityDeviceList.adapter = BluetoothDeviceAdapter(deviceList, this)
        }
    }

    override fun onErrorOccur(errorRes: Int) {
        AlertDialog.Builder(this)
            .setMessage(errorRes)
            .setPositiveButton(R.string.ok, null)
            .setCancelable(false)
            .show()
    }

    @RequiresApi(Build.VERSION_CODES.S)
    @NeedsPermission(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN)
    fun startScanForAndroidR() {
        presenter.startScan()
    }

    @RequiresApi(Build.VERSION_CODES.S)
    @OnPermissionDenied(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN)
    fun onBluetoothDenied() {
        Toast.makeText(this, R.string.permission_bluetooth_denied, Toast.LENGTH_SHORT).show()

    }

    @RequiresApi(Build.VERSION_CODES.S)
    @OnShowRationale(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN)
    fun showRationaleForBluetooth(request: PermissionRequest) {
        showRationaleDialog(R.string.permission_bluetooth_rationale, request)
    }

    @RequiresApi(Build.VERSION_CODES.S)
    @OnNeverAskAgain(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN)
    fun onBluetoothNeverAskAgain() {
        Toast.makeText(this, R.string.permission_bluetooth_never_ask_again, Toast.LENGTH_SHORT)
            .show()
    }

    @NeedsPermission(Manifest.permission.ACCESS_FINE_LOCATION)
    fun startScan() {
        presenter.startScan()
    }

    @OnPermissionDenied(Manifest.permission.ACCESS_FINE_LOCATION)
    fun onLocationDenied() {
        Toast.makeText(this, R.string.permission_location_denied, Toast.LENGTH_SHORT).show()
    }

    @OnShowRationale(Manifest.permission.ACCESS_FINE_LOCATION)
    fun showRationaleForLocation(request: PermissionRequest) {
        showRationaleDialog(R.string.permission_location_rationale, request)
    }

    @OnNeverAskAgain(Manifest.permission.ACCESS_FINE_LOCATION)
    fun onLocationNeverAskAgain() {
        Toast.makeText(this, R.string.permission_location_never_ask_again, Toast.LENGTH_SHORT)
            .show()
    }

    private fun showRationaleDialog(@StringRes messageResId: Int, request: PermissionRequest) {
        AlertDialog.Builder(this)
            .setPositiveButton(R.string.allow) { _, _ -> request.proceed() }
            .setNegativeButton(R.string.deny) { _, _ -> request.cancel() }
            .setCancelable(false)
            .setMessage(messageResId)
            .show()
    }

    override fun disabledScan() {
        binding.recyclerMainActivityDeviceList.visibility = View.GONE
        binding.scrollMainActivityLog.visibility = View.GONE
        binding.textMainActivityLog.text = ""
        binding.btnMainActivityScan.isEnabled = false
        binding.textMainActivityStatus.text = getString(R.string.status, Status.SCANNING.value)
        binding.includeProgressDialog.root.visibility = View.VISIBLE
        binding.includeProgressDialog.textProgressDialogMsg.text = getString(R.string.scanning)
    }

    override fun enabledScan() {
        binding.btnMainActivityScan.isEnabled = true
        binding.textMainActivityStatus.text = getString(R.string.status, Status.SCANNED.value)
        binding.includeProgressDialog.root.visibility = View.INVISIBLE
    }

    override fun onAppendLog(log: String) {
        if (log.contains("Connected")) {
            binding.textMainActivityStatus.text = getString(R.string.status, Status.CONNECTED.value)
        } else if (log.contains("Disconnected")) {
            binding.textMainActivityStatus.text = getString(R.string.status, Status.DISCONNECTED.value)
            binding.btnMainActivityScan.isEnabled = true
        }
        val s = binding.textMainActivityLog.text.toString() + log + "\n\n"
        binding.textMainActivityLog.text = s
        binding.scrollMainActivityLog.post { binding.scrollMainActivityLog.fullScroll(ScrollView.FOCUS_DOWN) }
    }

    override fun onClick(v: View?) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            startScanForAndroidRWithPermissionCheck()
        } else {
            startScanWithPermissionCheck()
        }
    }

    override fun onItemClicked(device: BluetoothDevice) {
        binding.btnMainActivityScan.isEnabled = false
        presenter.connect(this, device)
        binding.recyclerMainActivityDeviceList.visibility = View.GONE
        binding.textMainActivityLog.text = ("Device ${Status.CONNECTING.value}\n\n")
        binding.scrollMainActivityLog.visibility = View.VISIBLE
        binding.textMainActivityStatus.text = getString(R.string.status, Status.CONNECTING.value)
    }
}
