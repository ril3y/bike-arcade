package com.bikearcade.settings

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.ParcelUuid
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.BluetoothSearching
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.UUID

private const val TAG = "BTPairing"

private val AccentCyan = Color(0xFF4FC3F7)
private val AccentGreen = Color(0xFF66BB6A)
private val AccentOrange = Color(0xFFFFB74D)
private val AccentRed = Color(0xFFEF5350)
private val SurfaceCard = Color(0xFF16162A)
private val TextPrimary = Color(0xFFE0E0E0)
private val TextSecondary = Color(0xFF9E9E9E)
private val CardBorder = Color(0xFF2A2A40)

private val UUID_HEART_RATE = UUID.fromString("0000180d-0000-1000-8000-00805f9b34fb")
private val UUID_HID = UUID.fromString("00001812-0000-1000-8000-00805f9b34fb")
private val UUID_FTMS = UUID.fromString("00001826-0000-1000-8000-00805f9b34fb")

private enum class DeviceCategory(val label: String, val icon: ImageVector, val color: Color) {
    CONTROLLER("Controllers", Icons.Default.Gamepad, AccentCyan),
    HEALTH("Health & Fitness", Icons.Default.FavoriteBorder, AccentRed),
}

private data class BtDevice(
    val device: BluetoothDevice,
    val name: String,
    val address: String,
    val bonded: Boolean,
    val category: DeviceCategory,
)

@SuppressLint("MissingPermission")
private fun classifyDevice(device: BluetoothDevice, scanUuids: List<ParcelUuid>? = null): DeviceCategory? {
    val name = (device.name ?: "").lowercase()

    // Filter out the Bowflex/Nautilus bike console (we ARE the bike)
    if (name.startsWith("c9/") || name.startsWith("c10") || name.contains("nautilus")
        || name.contains("bowflex") || name.contains("velocore")
    ) return null

    val uuids = scanUuids ?: device.uuids?.toList() ?: emptyList()
    for (u in uuids) {
        val uuid = u.uuid
        if (uuid == UUID_HID) return DeviceCategory.CONTROLLER
        if (uuid == UUID_HEART_RATE || uuid == UUID_FTMS) return DeviceCategory.HEALTH
    }

    val btClass = device.bluetoothClass
    if (btClass != null) {
        val major = btClass.majorDeviceClass
        if (major == BluetoothClass.Device.Major.PERIPHERAL) return DeviceCategory.CONTROLLER
        if (major == BluetoothClass.Device.Major.HEALTH) return DeviceCategory.HEALTH
    }
    if (name.contains("controller") || name.contains("gamepad") || name.contains("joystick")
        || name.contains("xbox") || name.contains("8bitdo") || name.contains("pro controller")
        || name.contains("dualshock") || name.contains("dualsense") || name.contains("switch")
    ) return DeviceCategory.CONTROLLER
    if (name.contains("heart") || name.contains(" hr") || name.contains("hrm")
        || name.contains("polar") || name.contains("garmin") || name.contains("wahoo")
        || name.contains("tickr") || name.contains("coospo") || name.contains("scosche")
        || name.contains("cateye") || name.contains("magene") || name.contains("moofit")
        || name.contains("ftms") || name.contains("fitness")
    ) return DeviceCategory.HEALTH

    return null
}

/**
 * Holds mutable scan state outside of Compose state to avoid recomposition loops.
 */
private class ScanState(
    val btAdapter: BluetoothAdapter,
    val allDevices: MutableList<BtDevice>,
) {
    var leScanner: BluetoothLeScanner? = try { btAdapter.bluetoothLeScanner } catch (_: Exception) { null }
    var leScanCallback: ScanCallback? = null

    @SuppressLint("MissingPermission")
    fun stopScan() {
        try { btAdapter.cancelDiscovery() } catch (_: Exception) {}
        leScanCallback?.let { cb ->
            try { leScanner?.stopScan(cb) } catch (_: Exception) {}
        }
        leScanCallback = null
    }

    @SuppressLint("MissingPermission")
    fun addDevice(device: BluetoothDevice, bonded: Boolean, scanUuids: List<ParcelUuid>? = null) {
        val name = device.name ?: "(null)"
        val major = device.bluetoothClass?.majorDeviceClass
        val category = classifyDevice(device, scanUuids)
        Log.d(TAG, "FOUND: $name [${device.address}] major=$major uuids=$scanUuids category=$category bonded=$bonded")
        if (allDevices.any { it.address == device.address }) return
        if (category == null || device.name == null) return
        allDevices.add(BtDevice(device, device.name, device.address, bonded, category))
    }

    @SuppressLint("MissingPermission")
    fun refreshBonded() {
        try {
            allDevices.removeAll { it.bonded }
            btAdapter.bondedDevices?.forEach { d -> addDevice(d, bonded = true) }
        } catch (e: Exception) {
            Log.e(TAG, "refreshBonded failed", e)
        }
    }

    @SuppressLint("MissingPermission")
    fun startScan(): Boolean {
        stopScan()
        allDevices.removeAll { !it.bonded }

        val started = btAdapter.startDiscovery()
        Log.d(TAG, "startDiscovery()=$started")

        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val device = result.device ?: return
                val uuids = result.scanRecord?.serviceUuids
                addDevice(device, bonded = device.bondState == BluetoothDevice.BOND_BONDED, scanUuids = uuids)
            }
            override fun onBatchScanResults(results: List<ScanResult>) {
                results.forEach { onScanResult(0, it) }
            }
        }
        leScanCallback = callback
        try { leScanner?.startScan(callback) } catch (e: Exception) { Log.e(TAG, "BLE scan failed", e) }
        Log.d(TAG, "BLE scan started")

        return started
    }
}

@SuppressLint("MissingPermission")
@Composable
fun BluetoothPairingPanel() {
    val context = LocalContext.current
    val btManager = remember { context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager }
    val btAdapter = remember { btManager?.adapter }

    if (btAdapter == null) {
        Text("Bluetooth not available", color = TextSecondary, fontSize = 13.sp)
        return
    }

    val allDevices = remember { mutableStateListOf<BtDevice>() }
    val scanState = remember { ScanState(btAdapter, allDevices) }
    val scope = rememberCoroutineScope()
    var scanning by remember { mutableStateOf(false) }
    var pairingAddress by remember { mutableStateOf<String?>(null) }
    var showScanDialog by remember { mutableStateOf(false) }
    var unpairTarget by remember { mutableStateOf<BtDevice?>(null) }

    // Register broadcast receiver
    DisposableEffect(btAdapter) {
        scanState.refreshBonded()

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                when (intent.action) {
                    BluetoothDevice.ACTION_FOUND -> {
                        val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                            ?: return
                        scanState.addDevice(device, bonded = device.bondState == BluetoothDevice.BOND_BONDED)
                    }
                    BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                        // Classic discovery done; BLE may still be running
                    }
                    BluetoothDevice.ACTION_BOND_STATE_CHANGED -> {
                        val state = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.BOND_NONE)
                        if (state == BluetoothDevice.BOND_BONDED || state == BluetoothDevice.BOND_NONE) {
                            pairingAddress = null
                            scanState.refreshBonded()
                        }
                    }
                }
            }
        }

        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
            addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
        }
        context.registerReceiver(receiver, filter)

        onDispose {
            scanState.stopScan()
            context.unregisterReceiver(receiver)
        }
    }

    // -- Unpair confirmation dialog --
    unpairTarget?.let { dev ->
        AlertDialog(
            onDismissRequest = { unpairTarget = null },
            title = { Text("Remove Device", color = TextPrimary) },
            text = { Text("Unpair \"${dev.name}\"?", color = TextSecondary) },
            confirmButton = {
                TextButton(onClick = {
                    try {
                        dev.device.javaClass.getMethod("removeBond").invoke(dev.device)
                    } catch (_: Exception) { }
                    unpairTarget = null
                    scanState.refreshBonded()
                }) {
                    Text("Remove", color = AccentRed)
                }
            },
            dismissButton = {
                TextButton(onClick = { unpairTarget = null }) {
                    Text("Cancel", color = TextSecondary)
                }
            },
            containerColor = SurfaceCard,
        )
    }

    // -- Scan modal dialog --
    if (showScanDialog) {
        AlertDialog(
            onDismissRequest = {
                scanState.stopScan()
                scanning = false
                showScanDialog = false
            },
            properties = DialogProperties(usePlatformDefaultWidth = false),
            modifier = Modifier.fillMaxWidth(0.7f),
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (scanning) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = AccentCyan,
                                strokeWidth = 2.dp,
                            )
                            Spacer(Modifier.width(10.dp))
                        }
                        Text(
                            if (scanning) "Scanning..." else "Scan Complete",
                            color = TextPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    IconButton(onClick = {
                        scanState.stopScan()
                        scanning = false
                        showScanDialog = false
                    }) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = TextSecondary)
                    }
                }
            },
            text = {
                val unbonded = allDevices.filter { !it.bonded }
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    if (unbonded.isEmpty()) {
                        Text(
                            text = if (scanning) "Looking for controllers and health devices nearby..."
                            else "No devices found. Make sure your device is in pairing mode.",
                            color = TextSecondary,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(vertical = 16.dp),
                        )
                    } else {
                        for (category in DeviceCategory.entries) {
                            val devices = unbonded.filter { it.category == category }
                            if (devices.isEmpty()) continue
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(bottom = 4.dp, top = 8.dp),
                            ) {
                                Icon(category.icon, null, tint = category.color, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = category.label.uppercase(),
                                    color = category.color,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    fontFamily = FontFamily.Monospace,
                                    letterSpacing = 1.sp,
                                )
                            }
                            devices.forEach { dev ->
                                DeviceRow(
                                    dev = dev,
                                    isPairing = pairingAddress == dev.address,
                                    onTap = {
                                        scanState.stopScan()
                                        scanning = false
                                        pairingAddress = dev.address
                                        dev.device.createBond()
                                    },
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                if (scanning) {
                    OutlinedButton(
                        onClick = {
                            scanState.stopScan()
                            scanning = false
                        },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentOrange),
                    ) {
                        Text("Stop Scan")
                    }
                } else {
                    Button(
                        onClick = {
                            scanning = true
                            scope.launch(Dispatchers.Default) { scanState.startScan() }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AccentCyan, contentColor = Color.White),
                    ) {
                        Text("Scan Again")
                    }
                }
            },
            containerColor = SurfaceCard,
        )
    }

    // -- Main panel (paired devices + scan button) --
    Column {
        val bonded = allDevices.filter { it.bonded }
        if (bonded.isEmpty()) {
            Text(
                text = "No paired controllers or health devices.",
                color = TextSecondary,
                fontSize = 12.sp,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        } else {
            for (category in DeviceCategory.entries) {
                val devices = bonded.filter { it.category == category }
                if (devices.isEmpty()) continue
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 4.dp, top = 4.dp),
                ) {
                    Icon(category.icon, null, tint = category.color, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = category.label.uppercase(),
                        color = category.color,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 1.sp,
                    )
                }
                devices.forEach { dev ->
                    DeviceRow(
                        dev = dev,
                        isPairing = false,
                        onTap = { },
                        onUnpair = { unpairTarget = dev },
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        Button(
            onClick = {
                Log.d(TAG, "Scan button clicked")
                showScanDialog = true
                scanning = true
                scope.launch(Dispatchers.Default) { scanState.startScan() }
            },
            colors = ButtonDefaults.buttonColors(containerColor = AccentCyan, contentColor = Color.White),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Default.BluetoothSearching, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Scan for Devices")
        }
    }
}

@SuppressLint("MissingPermission")
@Composable
private fun DeviceRow(
    dev: BtDevice,
    isPairing: Boolean,
    onTap: () -> Unit,
    onUnpair: (() -> Unit)? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (dev.bonded) AccentGreen.copy(alpha = 0.08f) else Color.Transparent)
            .border(
                width = 1.dp,
                color = if (dev.bonded) AccentGreen.copy(alpha = 0.3f) else CardBorder,
                shape = RoundedCornerShape(8.dp),
            )
            .clickable(enabled = !dev.bonded && !isPairing) { onTap() }
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Icon(
            imageVector = if (dev.bonded) Icons.Default.BluetoothConnected else Icons.Default.Bluetooth,
            contentDescription = null,
            tint = if (dev.bonded) AccentGreen else dev.category.color,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = dev.name,
                color = TextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = dev.address,
                color = TextSecondary,
                fontSize = 11.sp,
            )
        }
        if (dev.bonded && onUnpair != null) {
            IconButton(onClick = onUnpair, modifier = Modifier.size(24.dp)) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Unpair",
                    tint = TextSecondary,
                    modifier = Modifier.size(16.dp),
                )
            }
        } else if (isPairing) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                color = dev.category.color,
                strokeWidth = 2.dp,
            )
        }
    }
}
