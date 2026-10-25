package com.mattyai.assistant

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class BluetoothActivity : Activity() {
    private lateinit var list: LinearLayout
    private lateinit var status: TextView
    private var adapter: BluetoothAdapter? = null
    private val found = linkedMapOf<String, BluetoothDevice>()
    private val requestNearby = 601
    private var receiverRegistered = false

    private val discoveryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                BluetoothDevice.ACTION_FOUND -> {
                    val device = if (Build.VERSION.SDK_INT >= 33) {
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    }
                    if (device != null) {
                        try {
                            found[device.address] = device
                            renderDevices()
                        } catch (_: SecurityException) {
                            status.text = "Allow Nearby devices permission to show scan results."
                        }
                    }
                }
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> status.text =
                    "Scan finished. Tap an unpaired device to pair, or open Bluetooth settings to connect a paired device."
                BluetoothDevice.ACTION_BOND_STATE_CHANGED -> {
                    loadPairedDevices()
                    status.text = "Bluetooth pairing state updated."
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val manager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
        adapter = manager.adapter
        buildUi()
        if (adapter == null) {
            status.text = "This phone does not support Bluetooth."
            return
        }
        requestBluetoothPermissionsThenLoad()
    }

    private fun buildUi() {
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(18), dp(20), dp(24))
            background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(Color.rgb(247, 248, 255), Color.WHITE))
        }
        page.addView(TextView(this).apply {
            text = "Nearby Bluetooth"; textSize = 28f; setTextColor(Color.rgb(20, 27, 45))
            typeface = Typeface.DEFAULT_BOLD
        }, params(bottom = 8))
        page.addView(TextView(this).apply {
            text = "Find nearby devices and request pairing. Android asks before pairing; device connection depends on its Bluetooth profile."
            textSize = 14f; setTextColor(Color.rgb(103, 116, 140))
        }, params(bottom = 14))
        status = TextView(this).apply {
            text = "Checking Bluetooth…"; textSize = 13f; setTextColor(Color.rgb(103, 116, 140))
        }
        page.addView(status, params(bottom = 12))
        val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        actions.addView(Button(this).apply {
            text = "Scan nearby"; isAllCaps = false
            setOnClickListener { requestBluetoothPermissionsThenScan() }
        }, LinearLayout.LayoutParams(0, dp(48), 1f).apply { rightMargin = dp(8) })
        actions.addView(Button(this).apply {
            text = "Bluetooth settings"; isAllCaps = false
            setOnClickListener { openSystemBluetoothSettings() }
        }, LinearLayout.LayoutParams(0, dp(48), 1f))
        page.addView(actions, params(bottom = 14))
        page.addView(TextView(this).apply {
            text = "DISCOVERED / PAIRED DEVICES"; textSize = 11f; typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.rgb(103, 80, 232))
        }, params(bottom = 8))
        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        page.addView(list, params())
        val scroll = ScrollView(this).apply { isFillViewport = true; addView(page) }
        setContentView(scroll)
    }

    private fun requestBluetoothPermissionsThenLoad() {
        val missing = requiredPermissions().filter {
            checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            requestPermissions(missing.toTypedArray(), requestNearby)
        } else {
            loadPairedDevices()
            startScan()
        }
    }

    private fun requestBluetoothPermissionsThenScan() {
        val missing = requiredPermissions().filter {
            checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) requestPermissions(missing.toTypedArray(), requestNearby)
        else startScan()
    }

    private fun requiredPermissions(): Array<String> =
        if (Build.VERSION.SDK_INT >= 31) arrayOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT
        ) else arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != requestNearby) return
        if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
            loadPairedDevices()
            startScan()
        } else {
            status.text = "Nearby devices permission is required to scan and inspect paired devices. You can still open Android Bluetooth settings."
        }
    }

    private fun loadPairedDevices() {
        val bt = adapter ?: return
        try {
            bt.bondedDevices.forEach { found[it.address] = it }
            renderDevices()
        } catch (_: SecurityException) {
            status.text = "Allow Nearby devices permission to list paired devices."
        }
    }

    private fun startScan() {
        val bt = adapter ?: return
        try {
            if (!bt.isEnabled) {
                status.text = "Turn Bluetooth on in Android settings, then return and scan again."
                openSystemBluetoothSettings()
                return
            }
            if (!receiverRegistered) {
                val filter = IntentFilter().apply {
                    addAction(BluetoothDevice.ACTION_FOUND)
                    addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
                    addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
                }
                // Android's Bluetooth stack is a privileged system app, so this receiver must
                // be exported to receive its discovery and bond-state broadcasts on Android 13+.
                if (Build.VERSION.SDK_INT >= 33) registerReceiver(discoveryReceiver, filter, Context.RECEIVER_EXPORTED)
                else {
                    @Suppress("DEPRECATION")
                    registerReceiver(discoveryReceiver, filter)
                }
                receiverRegistered = true
            }
            if (bt.isDiscovering) bt.cancelDiscovery()
            found.clear()
            loadPairedDevices()
            status.text = "Scanning for nearby Bluetooth devices… Keep this screen open."
            val started = bt.startDiscovery()
            if (!started) status.text = "Android could not start discovery. Try turning Bluetooth off and on, then scan again."
        } catch (_: SecurityException) {
            status.text = "Nearby devices permission is required to scan."
        } catch (_: Exception) {
            status.text = "Bluetooth scanning failed on this phone. Try Android Bluetooth settings."
        }
    }

    private fun renderDevices() {
        if (!::list.isInitialized) return
        list.removeAllViews()
        if (found.isEmpty()) {
            list.addView(TextView(this).apply {
                text = "No devices found yet. Keep scanning, wake the other device, and make it discoverable if required."
                textSize = 13f; setTextColor(Color.rgb(103, 116, 140))
            }, params(bottom = 10))
            return
        }
        found.values.forEach { device ->
            val paired = try { device.bondState == BluetoothDevice.BOND_BONDED } catch (_: SecurityException) { false }
            val name = try { device.name?.takeIf { it.isNotBlank() } ?: "Unnamed Bluetooth device" }
                catch (_: SecurityException) { "Bluetooth device" }
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL; setPadding(dp(14), dp(12), dp(14), dp(12))
                background = rounded(Color.rgb(245, 246, 252), 16)
            }
            row.addView(TextView(this).apply {
                text = name; textSize = 16f; typeface = Typeface.DEFAULT_BOLD; setTextColor(Color.rgb(20, 27, 45))
            }, params(bottom = 3))
            row.addView(TextView(this).apply {
                text = "${device.address} · ${if (paired) "Paired" else "Not paired"}"
                textSize = 12f; setTextColor(Color.rgb(103, 116, 140))
            }, params(bottom = 8))
            row.addView(Button(this).apply {
                text = if (paired) "Connect / manage in Bluetooth settings" else "Pair device"
                isAllCaps = false
                setOnClickListener {
                    if (paired) {
                        openSystemBluetoothSettings()
                    } else {
                        try {
                            val initiated = device.createBond()
                            status.text = if (initiated) "Pairing request sent. Confirm the pairing dialog if Android shows one."
                                else "Could not start pairing. Try selecting the device in Android Bluetooth settings."
                            Toast.makeText(this@BluetoothActivity, status.text, Toast.LENGTH_LONG).show()
                        } catch (_: SecurityException) {
                            status.text = "Allow Nearby devices permission, then try pairing again."
                        } catch (_: Exception) {
                            status.text = "Pairing failed. Try Android Bluetooth settings."
                        }
                    }
                }
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44)))
            list.addView(row, params(bottom = 10))
        }
    }

    private fun openSystemBluetoothSettings() {
        try {
            startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
        } catch (_: Exception) {
            try { startActivity(Intent(Settings.ACTION_SETTINGS)) }
            catch (_: Exception) { status.text = "Open Bluetooth from your phone's Settings app." }
        }
    }

    private fun params(top: Int = 0, bottom: Int = 0) =
        LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(top); bottomMargin = dp(bottom)
        }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun rounded(color: Int, radius: Int) = GradientDrawable().apply {
        setColor(color); cornerRadius = dp(radius).toFloat()
    }

    override fun onDestroy() {
        try { adapter?.takeIf { it.isDiscovering }?.cancelDiscovery() } catch (_: Exception) { }
        if (receiverRegistered) try { unregisterReceiver(discoveryReceiver) } catch (_: Exception) { }
        receiverRegistered = false
        super.onDestroy()
    }
}
