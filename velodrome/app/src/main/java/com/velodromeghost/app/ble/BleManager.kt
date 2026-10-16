package com.velodromeghost.app.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import com.velodromeghost.core.ble.BikeSample
import com.velodromeghost.core.ble.CyclingPowerParser
import com.velodromeghost.core.ble.FtmsCommands
import com.velodromeghost.core.ble.GattUuids
import com.velodromeghost.core.ble.HeartRateParser
import com.velodromeghost.core.ble.IndoorBikeDataParser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.UUID

data class FoundDevice(
    val address: String,
    val name: String,
    val rssi: Int,
    val isSmartSpin: Boolean,
    val advertisesFitness: Boolean,
)

sealed interface ConnectionState {
    data object Idle : ConnectionState
    data object Scanning : ConnectionState
    data class Connecting(val name: String) : ConnectionState
    data class Connected(val name: String, val address: String, val canControl: Boolean) : ConnectionState
    data class Failed(val message: String) : ConnectionState
}

data class LiveBikeData(val sample: BikeSample, val receivedAtMs: Long) {
    fun isFresh(nowMs: Long = SystemClock.elapsedRealtime()) = nowMs - receivedAtMs < 3000
}

/**
 * Talks to SmartSpin2k (or any FTMS / Cycling Power trainer) over Bluetooth LE.
 *
 * Android only allows one outstanding GATT operation at a time, so descriptor and
 * characteristic writes go through a small queue. All GATT state is touched on the main thread.
 * Callers must hold the Bluetooth runtime permissions before calling scan/connect.
 */
@SuppressLint("MissingPermission")
class BleManager(context: Context) {
    private val appContext = context.applicationContext
    private val adapter: BluetoothAdapter? =
        (appContext.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
    private val main = Handler(Looper.getMainLooper())

    private val _state = MutableStateFlow<ConnectionState>(ConnectionState.Idle)
    val state: StateFlow<ConnectionState> = _state.asStateFlow()

    private val _devices = MutableStateFlow<List<FoundDevice>>(emptyList())
    val devices: StateFlow<List<FoundDevice>> = _devices.asStateFlow()

    private val _bike = MutableStateFlow<LiveBikeData?>(null)
    val bike: StateFlow<LiveBikeData?> = _bike.asStateFlow()

    private var gatt: BluetoothGatt? = null
    private var controlPoint: BluetoothGattCharacteristic? = null
    private var usingFtmsData = false
    private var connectedName = ""
    private var userDisconnect = false
    private var merged = BikeSample()
    private val powerParser = CyclingPowerParser()

    private sealed interface GattOp {
        data class EnableNotify(val ch: BluetoothGattCharacteristic) : GattOp
        data class Write(val ch: BluetoothGattCharacteristic, val bytes: ByteArray, val replaceable: Boolean) : GattOp
    }

    private val ops = ArrayDeque<GattOp>()
    private var opInFlight = false
    private var opToken = 0
    private var scanning = false

    val hasBluetooth: Boolean get() = adapter != null
    val isEnabled: Boolean get() = adapter?.isEnabled == true

    // ---------------------------------------------------------------- scanning

    private val stopScanRunnable = Runnable { stopScan() }

    fun startScan() {
        val scanner = adapter?.takeIf { it.isEnabled }?.bluetoothLeScanner
        if (scanner == null) {
            _state.value = ConnectionState.Failed("Bluetooth is off")
            return
        }
        if (scanning) return
        _devices.value = emptyList()
        val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
        try {
            scanner.startScan(null, settings, scanCallback)
        } catch (e: SecurityException) {
            _state.value = ConnectionState.Failed("Bluetooth permission missing")
            return
        }
        scanning = true
        if (_state.value !is ConnectionState.Connected) _state.value = ConnectionState.Scanning
        main.removeCallbacks(stopScanRunnable)
        main.postDelayed(stopScanRunnable, SCAN_TIMEOUT_MS)
    }

    fun stopScan() {
        main.removeCallbacks(stopScanRunnable)
        if (!scanning) return
        scanning = false
        try {
            adapter?.bluetoothLeScanner?.stopScan(scanCallback)
        } catch (_: Exception) {
        }
        if (_state.value == ConnectionState.Scanning) _state.value = ConnectionState.Idle
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) = onResult(result)
        override fun onBatchScanResults(results: MutableList<ScanResult>) = results.forEach(::onResult)
        override fun onScanFailed(errorCode: Int) {
            scanning = false
            _state.value = ConnectionState.Failed("Scan failed (error $errorCode)")
        }
    }

    private fun onResult(result: ScanResult) {
        val name = result.scanRecord?.deviceName ?: safeName(result.device) ?: return
        val uuids = result.scanRecord?.serviceUuids?.map { it.uuid }.orEmpty()
        val fitness = GattUuids.FTMS_SERVICE in uuids || GattUuids.CYCLING_POWER_SERVICE in uuids
        val ss2k = name.contains("smartspin", ignoreCase = true) || name.contains("ss2k", ignoreCase = true)
        val found = FoundDevice(result.device.address, name, result.rssi, ss2k, fitness)
        _devices.update { list ->
            val existing = list.indexOfFirst { it.address == found.address }
            if (existing >= 0) list.toMutableList().also { it[existing] = found }
            else (list + found).sortedWith(
                compareByDescending<FoundDevice> { it.isSmartSpin }.thenByDescending { it.advertisesFitness }
            )
        }
    }

    private fun safeName(device: BluetoothDevice): String? = try {
        device.name
    } catch (_: SecurityException) {
        null
    }

    // ---------------------------------------------------------------- connection

    fun connect(address: String, displayName: String? = null) {
        stopScan()
        closeGatt()
        val device = try {
            adapter?.getRemoteDevice(address)
        } catch (_: IllegalArgumentException) {
            null
        }
        if (device == null) {
            _state.value = ConnectionState.Failed("Unknown device")
            return
        }
        connectedName = displayName ?: safeName(device) ?: address
        userDisconnect = false
        _state.value = ConnectionState.Connecting(connectedName)
        gatt = try {
            device.connectGatt(appContext, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
        } catch (e: SecurityException) {
            _state.value = ConnectionState.Failed("Bluetooth permission missing")
            null
        }
    }

    fun disconnect() {
        userDisconnect = true
        stopScan()
        closeGatt()
        _state.value = ConnectionState.Idle
    }

    private fun closeGatt() {
        val g = gatt ?: return
        gatt = null
        controlPoint = null
        ops.clear()
        opInFlight = false
        _bike.value = null
        merged = BikeSample()
        try {
            g.disconnect()
            g.close()
        } catch (_: Exception) {
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            main.post {
                if (g != gatt) return@post
                when {
                    newState == BluetoothProfile.STATE_CONNECTED && status == BluetoothGatt.GATT_SUCCESS -> {
                        if (!g.discoverServices()) fail("Service discovery failed")
                    }
                    newState == BluetoothProfile.STATE_DISCONNECTED -> {
                        val wasUser = userDisconnect
                        closeGatt()
                        _state.value = if (wasUser) ConnectionState.Idle
                        else ConnectionState.Failed("Lost connection to $connectedName")
                    }
                    status != BluetoothGatt.GATT_SUCCESS -> fail("Connection failed (status $status)")
                }
            }
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            main.post {
                if (g != gatt) return@post
                if (status != BluetoothGatt.GATT_SUCCESS) fail("Service discovery failed ($status)") else setUp(g)
            }
        }

        override fun onDescriptorWrite(g: BluetoothGatt, d: BluetoothGattDescriptor, status: Int) {
            main.post { opDone() }
        }

        override fun onCharacteristicWrite(g: BluetoothGatt, c: BluetoothGattCharacteristic, status: Int) {
            main.post { opDone() }
        }

        override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic, value: ByteArray) {
            val copy = value.copyOf()
            main.post { onNotify(c.uuid, copy) }
        }

        @Deprecated("Used on Android 12 and older")
        override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic) {
            @Suppress("DEPRECATION")
            val copy = c.value?.copyOf() ?: return
            main.post { onNotify(c.uuid, copy) }
        }
    }

    private fun fail(message: String) {
        closeGatt()
        _state.value = ConnectionState.Failed(message)
    }

    private fun setUp(g: BluetoothGatt) {
        val ftms = g.getService(GattUuids.FTMS_SERVICE)
        val cps = g.getService(GattUuids.CYCLING_POWER_SERVICE)
        val hr = g.getService(GattUuids.HEART_RATE_SERVICE)

        usingFtmsData = false
        ftms?.getCharacteristic(GattUuids.INDOOR_BIKE_DATA)?.let {
            enqueue(GattOp.EnableNotify(it))
            usingFtmsData = true
        }
        if (!usingFtmsData) {
            cps?.getCharacteristic(GattUuids.CYCLING_POWER_MEASUREMENT)?.let { enqueue(GattOp.EnableNotify(it)) }
                ?: run {
                    fail("$connectedName has no power/cadence service (FTMS or Cycling Power)")
                    return
                }
        }
        ftms?.getCharacteristic(GattUuids.FTMS_CONTROL_POINT)?.let { cp ->
            controlPoint = cp
            enqueue(GattOp.EnableNotify(cp))
            enqueue(GattOp.Write(cp, FtmsCommands.requestControl(), replaceable = false))
            enqueue(GattOp.Write(cp, FtmsCommands.startOrResume(), replaceable = false))
        }
        hr?.getCharacteristic(GattUuids.HEART_RATE_MEASUREMENT)?.let { enqueue(GattOp.EnableNotify(it)) }

        _state.value = ConnectionState.Connected(connectedName, g.device.address, canControl = controlPoint != null)
    }

    private fun onNotify(uuid: UUID, data: ByteArray) {
        val now = SystemClock.elapsedRealtime()
        val sample = when (uuid) {
            GattUuids.INDOOR_BIKE_DATA -> IndoorBikeDataParser.parse(data)
            GattUuids.CYCLING_POWER_MEASUREMENT -> if (usingFtmsData) return else powerParser.parse(data, now)
            GattUuids.HEART_RATE_MEASUREMENT -> BikeSample(heartRate = HeartRateParser.parse(data))
            GattUuids.FTMS_CONTROL_POINT -> {
                val response = FtmsCommands.parseResponse(data)
                if (response != null && response.result == FtmsCommands.RESULT_CONTROL_NOT_PERMITTED) {
                    controlPoint?.let { enqueue(GattOp.Write(it, FtmsCommands.requestControl(), replaceable = false)) }
                }
                Log.d(TAG, "Control point response: $response")
                return
            }
            else -> return
        }
        merged = merged.merge(sample)
        _bike.value = LiveBikeData(merged, now)
    }

    // ---------------------------------------------------------------- trainer control

    /** ERG mode: the trainer holds this power regardless of cadence. */
    fun setTargetPower(watts: Int) = main.post { writeControl(FtmsCommands.setTargetPower(watts)) }

    /** Simulation mode: flat board track, with lower wind resistance while drafting. */
    fun setSimulation(gradePercent: Double, crr: Double, cwKgPerM: Double) =
        main.post { writeControl(FtmsCommands.setSimulation(0.0, gradePercent, crr, cwKgPerM)) }

    private fun writeControl(bytes: ByteArray) {
        val cp = controlPoint ?: return
        // Only the latest target matters; drop stale ones still waiting in the queue.
        ops.removeAll { it is GattOp.Write && it.replaceable }
        enqueue(GattOp.Write(cp, bytes, replaceable = true))
    }

    // ---------------------------------------------------------------- GATT queue

    private fun enqueue(op: GattOp) {
        ops.addLast(op)
        if (!opInFlight) runNext()
    }

    private fun opDone() {
        opInFlight = false
        runNext()
    }

    private fun runNext() {
        val g = gatt ?: return
        val op = ops.removeFirstOrNull() ?: return
        opInFlight = true
        val token = ++opToken
        main.postDelayed({ if (opInFlight && opToken == token) opDone() }, OP_TIMEOUT_MS)
        val started = try {
            when (op) {
                is GattOp.EnableNotify -> enableNotify(g, op.ch)
                is GattOp.Write -> write(g, op.ch, op.bytes)
            }
        } catch (e: SecurityException) {
            false
        }
        if (!started) {
            Log.w(TAG, "GATT op failed to start: $op")
            opInFlight = false
            main.post { runNext() }
        }
    }

    private fun enableNotify(g: BluetoothGatt, ch: BluetoothGattCharacteristic): Boolean {
        if (!g.setCharacteristicNotification(ch, true)) return false
        val cccd = ch.getDescriptor(GattUuids.CCCD) ?: return false
        val value = if (ch.properties and BluetoothGattCharacteristic.PROPERTY_INDICATE != 0) {
            BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
        } else {
            BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
        }
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            g.writeDescriptor(cccd, value) == BluetoothGatt.GATT_SUCCESS
        } else {
            @Suppress("DEPRECATION")
            cccd.value = value
            @Suppress("DEPRECATION")
            g.writeDescriptor(cccd)
        }
    }

    private fun write(g: BluetoothGatt, ch: BluetoothGattCharacteristic, bytes: ByteArray): Boolean {
        val type = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            g.writeCharacteristic(ch, bytes, type) == BluetoothGatt.GATT_SUCCESS
        } else {
            @Suppress("DEPRECATION")
            ch.writeType = type
            @Suppress("DEPRECATION")
            ch.value = bytes
            @Suppress("DEPRECATION")
            g.writeCharacteristic(ch)
        }
    }

    private companion object {
        const val TAG = "BleManager"
        const val SCAN_TIMEOUT_MS = 20_000L
        const val OP_TIMEOUT_MS = 2_000L
    }
}
